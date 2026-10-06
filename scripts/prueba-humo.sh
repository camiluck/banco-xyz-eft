#!/usr/bin/env bash
# =============================================================================
#  Prueba de punta a punta del sistema levantado con docker compose.
#  Uso:  ./scripts/prueba-humo.sh            (requiere curl y jq)
#  Recorre: tokens por canal, BFF web/móvil/ATM, Kafka, resiliencia y batch.
# =============================================================================
set -uo pipefail

AUTH=${AUTH:-http://localhost:9000}
WEB=${WEB:-https://localhost:8443}
MOVIL=${MOVIL:-https://localhost:8444}
ATM=${ATM:-https://localhost:8445}
BATCH=${BATCH:-http://localhost:8090}
EUREKA=${EUREKA:-http://localhost:8761}
FALLAS=0

verde() { printf "\033[32m✔ %s\033[0m\n" "$*"; }
rojo()  { printf "\033[31m✘ %s\033[0m\n" "$*"; FALLAS=$((FALLAS+1)); }
titulo(){ printf "\n\033[1m== %s ==\033[0m\n" "$*"; }

esperar() { # esperar <descripcion> <comando>
  local desc=$1; shift
  for i in $(seq 1 90); do
    if eval "$@" >/dev/null 2>&1; then verde "$desc"; return 0; fi
    sleep 2
  done
  rojo "$desc (timeout)"; return 1
}

token() { # token <cliente> <secreto> <scope>
  curl -s -u "$1:$2" -d grant_type=client_credentials -d "scope=$3" "$AUTH/oauth2/token" | jq -r .access_token
}

comprobar() { # comprobar <descripcion> <valor> <esperado>
  if [[ "$2" == "$3" ]]; then verde "$1 ($2)"; else rojo "$1: se esperaba '$3' y llegó '$2'"; fi
}

instancias() { curl -s -H 'Accept: application/json' "$EUREKA/eureka/apps/$1" | jq '[.application.instance[] | select(.status=="UP")] | length' 2>/dev/null || echo 0; }

titulo "1. Servicios registrados en Eureka"
esperar "ms-cuentas con 2 réplicas"  '[[ $(instancias MS-CUENTAS) -ge 2 ]]'
esperar "ms-pagos con 2 réplicas"    '[[ $(instancias MS-PAGOS) -ge 2 ]]'
esperar "ms-clientes registrado"     '[[ $(instancias MS-CLIENTES) -ge 1 ]]'
esperar "api-gateway registrado"     '[[ $(instancias API-GATEWAY) -ge 1 ]]'
sleep 10 # tiempo para que los clientes refresquen su caché de Eureka

titulo "2. Tokens OAuth2 por canal"
T_WEB=$(token frontend-web web-secret canal.web)
T_MOVIL=$(token frontend-movil movil-secret canal.movil)
T_ATM=$(token cajero-atm atm-secret canal.atm)
T_BATCH=$(token batch-admin batch-secret batch.admin)
[[ ${#T_WEB} -gt 50 && ${#T_MOVIL} -gt 50 && ${#T_ATM} -gt 50 ]] && verde "tokens emitidos" || rojo "no se obtuvieron tokens"

titulo "3. Seguridad"
comprobar "sin token -> 401" "$(curl -sk -o /dev/null -w '%{http_code}' $WEB/web/clientes/1/resumen)" "401"
comprobar "token web en BFF móvil -> 401 (audiencia incorrecta)" \
  "$(curl -sk -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $T_WEB" $MOVIL/movil/cuentas/1/saldo)" "401"

titulo "4. BFF Web"
esperar "resumen del cliente 1 disponible" \
  '[[ $(curl -sk -H "Authorization: Bearer $T_WEB" $WEB/web/clientes/1/resumen | jq -r .cliente.rut) == "12345678-5" ]]'
RESUMEN=$(curl -sk -H "Authorization: Bearer $T_WEB" $WEB/web/clientes/1/resumen)
comprobar "resumen trae 2 cuentas" "$(echo "$RESUMEN" | jq '.cuentas | length')" "2"
TRX=$(curl -sk -H "Authorization: Bearer $T_WEB" -H 'Content-Type: application/json' -H "Idempotency-Key: humo-$RANDOM" \
  -d '{"cuentaOrigenId":1,"cuentaDestinoId":3,"monto":10000,"descripcion":"Prueba humo"}' $WEB/web/transferencias)
comprobar "transferencia web" "$(echo "$TRX" | jq -r .estado)" "COMPLETADO"
SALDO_INSUF=$(curl -sk -H "Authorization: Bearer $T_WEB" -H 'Content-Type: application/json' \
  -d '{"cuentaOrigenId":4,"cuentaDestinoId":1,"monto":99999999}' -o /dev/null -w '%{http_code}' $WEB/web/transferencias)
comprobar "transferencia sin saldo -> 422" "$SALDO_INSUF" "422"

titulo "5. BFF Móvil"
INICIO=$(curl -sk -H "Authorization: Bearer $T_MOVIL" $MOVIL/movil/clientes/1/inicio)
comprobar "inicio móvil: nombre" "$(echo "$INICIO" | jq -r .nombre)" "Juan"
comprobar "inicio móvil: número enmascarado" "$(echo "$INICIO" | jq -r '.cuentas[0].num')" "****0001"
ETAG=$(curl -sk -D - -o /dev/null -H "Authorization: Bearer $T_MOVIL" $MOVIL/movil/cuentas/1/saldo | grep -i '^etag' | cut -d' ' -f2 | tr -d '\r')
comprobar "ETag -> 304 si no hay cambios" \
  "$(curl -sk -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $T_MOVIL" -H "If-None-Match: $ETAG" $MOVIL/movil/cuentas/1/saldo)" "304"

titulo "6. BFF Cajeros"
comprobar "terminal no registrado -> 403" "$(curl -sk -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $T_ATM" \
  -H 'X-Terminal-Id: ATM-PIRATA' -H 'Content-Type: application/json' -d '{"cuentaId":1,"pin":"1234"}' $ATM/atm/consulta-saldo)" "403"
SALDO=$(curl -sk -H "Authorization: Bearer $T_ATM" -H 'X-Terminal-Id: ATM-STGO-001' -H 'Content-Type: application/json' \
  -d '{"cuentaId":1,"pin":"1234"}' $ATM/atm/consulta-saldo)
comprobar "consulta de saldo (cuenta enmascarada)" "$(echo "$SALDO" | jq -r .cuenta)" "****0001"
KEY="humo-$RANDOM"
R1=$(curl -sk -H "Authorization: Bearer $T_ATM" -H 'X-Terminal-Id: ATM-STGO-001' -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' -d '{"cuentaId":1,"pin":"1234","monto":20000}' $ATM/atm/retiros)
R2=$(curl -sk -H "Authorization: Bearer $T_ATM" -H 'X-Terminal-Id: ATM-STGO-001' -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' -d '{"cuentaId":1,"pin":"1234","monto":20000}' $ATM/atm/retiros)
comprobar "retiro" "$(echo "$R1" | jq -r .estado)" "COMPLETADO"
comprobar "reintento del cajero no duplica el retiro" "$(echo "$R2" | jq -r .comprobante)" "$(echo "$R1" | jq -r .comprobante)"
comprobar "PIN incorrecto -> 401" "$(curl -sk -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $T_ATM" -H 'X-Terminal-Id: ATM-STGO-001' \
  -H 'Content-Type: application/json' -d '{"cuentaId":3,"pin":"9999"}' $ATM/atm/consulta-saldo)" "401"

titulo "7. Eventos Kafka (transacción -> notificación al cliente destino)"
esperar "cliente 2 recibió la notificación de la transferencia" \
  '[[ $(curl -sk -H "Authorization: Bearer $T_WEB" $WEB/web/clientes/2/resumen | jq "[.notificaciones[] | select(.tipo==\"TRANSACCION\")] | length") -ge 1 ]]'

titulo "8. Resiliencia (se detiene ms-clientes)"
if command -v docker >/dev/null && [[ "${SIN_DOCKER:-0}" != "1" ]]; then
  docker compose stop ms-clientes >/dev/null 2>&1
  sleep 5
  comprobar "web: perfil no disponible -> 503 controlado" \
    "$(curl -sk -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $T_WEB" $WEB/web/clientes/1/resumen)" "503"
  comprobar "móvil: inicio responde con datos parciales" \
    "$(curl -sk -H "Authorization: Bearer $T_MOVIL" $MOVIL/movil/clientes/1/inicio | jq -r .datosParciales)" "true"
  docker compose start ms-clientes >/dev/null 2>&1
  esperar "ms-clientes vuelve a estar disponible" \
    '[[ $(curl -sk -H "Authorization: Bearer $T_WEB" $WEB/web/clientes/1/resumen | jq -r .cliente.rut) == "12345678-5" ]]'
fi

titulo "9. Procesos batch"
for JOB in transaccionesDiariasJob interesesMensualesJob estadosCuentaAnualesJob; do
  PARAMS="dataset=semana_3&periodo=2024-12&anio=2024"
  EID=$(curl -s -X POST -H "Authorization: Bearer $T_BATCH" "$BATCH/api/batch/jobs/$JOB?$PARAMS" | jq -r .ejecucionId)
  esperar "$JOB (ejecución $EID) COMPLETED" \
    '[[ $(curl -s -H "Authorization: Bearer $T_BATCH" $BATCH/api/batch/ejecuciones/'"$EID"' | jq -r .estado) == "COMPLETED" ]]'
done
EID=$(curl -s -X POST -H "Authorization: Bearer $T_BATCH" "$BATCH/api/batch/jobs/interesesMensualesJob?dataset=semana_1&simularFalloTransitorio=true" | jq -r .ejecucionId)
esperar "falla transitoria simulada: reintenta y termina COMPLETED" \
  '[[ $(curl -s -H "Authorization: Bearer $T_BATCH" $BATCH/api/batch/ejecuciones/'"$EID"' | jq -r .estado) == "COMPLETED" ]]'

titulo "Resultado"
if [[ $FALLAS -eq 0 ]]; then verde "TODAS LAS PRUEBAS OK"; else rojo "$FALLAS prueba(s) fallaron"; fi
exit $FALLAS
