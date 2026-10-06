#!/usr/bin/env python3
"""
Implementación de REFERENCIA de las reglas del sistema legacy (independiente de Java).

Sirve para demostrar que los procesos Spring Batch producen resultados EQUIVALENTES:
los tests de batch-service (EquivalenciaLegacyTests) comparan sus totales con los
valores que imprime este script.

Uso:  python3 herramientas/referencia_legacy.py [carpeta_data] [dataset]
      python3 herramientas/referencia_legacy.py batch-service/data semana_3
"""
import csv
import hashlib
import sys
import unicodedata
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP, InvalidOperation
from pathlib import Path

FORMATOS = [("%Y-%m-%d", False), ("%Y/%m/%d", True), ("%d-%m-%Y", True), ("%d/%m/%Y", True)]
TASAS = {"ahorro": Decimal("0.030"), "prestamo": Decimal("0.120"), "hipoteca": Decimal("0.048")}


def fecha(txt):
    for fmt, _ in FORMATOS:
        try:
            return datetime.strptime(txt.strip(), fmt).date()
        except ValueError:
            pass
    return None


def num(txt):
    try:
        return Decimal(txt.strip()) if txt.strip() else None
    except InvalidOperation:
        return None


def entero(txt):
    try:
        return int(txt.strip())
    except ValueError:
        return None


def filas(ruta, columnas):
    with open(ruta, encoding="utf-8", newline="") as f:
        lector = csv.reader(f)
        next(lector)
        for fila in lector:
            if len(fila) != columnas:
                yield None
            else:
                yield fila


def transacciones(ruta):
    validas = anomalias = rechazadas = 0
    creditos = debitos = Decimal(0)
    for f in filas(ruta, 4):
        if f is None:
            rechazadas += 1
            continue
        i, fe, mo, ti = f
        m = num(mo)
        if entero(i) is None or fecha(fe) is None or m is None:
            rechazadas += 1
            continue
        ti = ti.strip().lower()
        if m <= 0 or ti not in ("debito", "credito"):
            anomalias += 1
            continue
        validas += 1
        if ti == "credito":
            creditos += m
        else:
            debitos += m
    return dict(escritos=validas + anomalias, anomalias=anomalias, rechazados=rechazadas,
                creditos=creditos, debitos=debitos)


def intereses(ruta):
    vistos, total, escritos, rechazados = set(), Decimal(0), 0, 0
    for f in filas(ruta, 5):
        if f is None:
            rechazados += 1
            continue
        cid, nombre, sa, ed, ti = f
        s, e, t = num(sa), entero(ed) if ed.strip() else None, ti.strip().lower()
        if entero(cid) is None or s is None or s < 0 or e is None or not (18 <= e <= 99) or t not in TASAS:
            rechazados += 1
            continue
        clave = hashlib.sha256(f"{int(cid)}|{nombre.strip()}|{s}|{e}|{t}".encode()).hexdigest()
        if clave in vistos:
            rechazados += 1
            continue
        vistos.add(clave)
        mensual = (TASAS[t] / 12).quantize(Decimal("0.00000001"), ROUND_HALF_UP)
        total += (s * mensual).quantize(Decimal("0.01"), ROUND_HALF_UP)
        escritos += 1
    return dict(escritos=escritos, rechazados=rechazados, interes_total=total)


def estados(ruta, anio):
    cuentas, escritos, rechazados, filtrados = {}, 0, 0, 0
    for f in filas(ruta, 5):
        if f is None:
            rechazados += 1
            continue
        cid, fe, tr, mo, de = f
        d = fecha(fe)
        if entero(cid) is None or d is None:
            rechazados += 1
            continue
        if d.year != anio:
            filtrados += 1
            continue
        tr = unicodedata.normalize("NFD", tr.strip().lower())
        tr = "".join(c for c in tr if unicodedata.category(c) != "Mn")
        m = num(mo)
        if tr not in ("deposito", "retiro", "compra", "pago") or m is None or m == 0:
            rechazados += 1
            continue
        if m < 0:
            if tr == "deposito":
                rechazados += 1
                continue
            m = -m
        c = cuentas.setdefault(int(cid), Decimal(0))
        cuentas[int(cid)] = c + (m if tr == "deposito" else -m)
        escritos += 1
    return dict(escritos=escritos, rechazados=rechazados, filtrados=filtrados, cuentas=len(cuentas),
                saldo_neto_total=sum(cuentas.values(), Decimal(0)))


if __name__ == "__main__":
    base = Path(sys.argv[1] if len(sys.argv) > 1 else "batch-service/data")
    dataset = sys.argv[2] if len(sys.argv) > 2 else "semana_3"
    d = base / dataset
    print("transacciones:", transacciones(d / "movimientos_financieros_diarios.csv"))
    print("intereses:    ", intereses(d / "intereses_trimestrales.csv"))
    print("estados 2024: ", estados(d / "estados_financieros_anuales.csv", 2024))
