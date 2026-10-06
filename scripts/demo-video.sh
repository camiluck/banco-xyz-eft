#!/usr/bin/env bash
# =============================================================================
#  Demo para grabar el video de la EFT. Ejecuta las demostraciones por escena,
#  muestra cada comando antes de correrlo y espera ENTER entre pasos.
#
#  Uso:   ./scripts/demo-video.sh            (todas las escenas)
#         ./scripts/demo-video.sh 3          (sólo la escena 3)
#  Requiere: el sistema levantado (docker compose up -d), curl y jq.
# =============================================================================
AUTH=http://localhost:9000; WEB=https://localhost:8443; MOVIL=https://localhost:8444
ATM=https://localhost:8445; BATCH=http://localhost:8090
SOLO=${1:-}

azul()  { printf "\n\033[1;34m%s\033[0m\n" "$*"; }
gris()  { printf "\033[0;90m$ %s\033[0m\n" "$*"; }
pausa() { printf "\n\033[0;33m[ENTER para continuar]\033[0m"; read -r _; }
correr(){ gris "$1"; eval "$1"; echo; }
escena(){ [[ -z "$SOLO" || "$SOLO" == "$1" ]]; }
token() { curl -s -u "$1:$2" -d grant_type=client_credentials -d "scope=$3" $AUTH/oauth2/token | jq -r .access_token; }

T_WEB=$(token frontend-web web-secret canal.web)
T_MOVIL=$(token frontend-movil movil-secret canal.movil)
T_ATM=$(token cajero-atm atm-secret canal.atm)
T_BATCH=$(token batch-admin batch-secret batch.admin)
if [[ -z "$T_WEB" || "$T_WEB" == "null" ]]; then echo "No se pudo obtener token: ¿está levantado el sistema? (docker compose ps)"; exit 1; fi

# ---------------------------------------------------------------------------
if escena 1; then
  azul "ESCENA 1 · El sistema corriendo en contenedores"
  correr "docker compose ps --format 'table {{.Name}}\t{{.Status}}'"
  echo "👉 Ahora muestra Eureka en el navegador: http://localhost:8761 (2 réplicas de MS-CUENTAS y MS-PAGOS)"
  pausa
fi

# ---------------------------------------------------------------------------
if escena 2; then
  azul "ESCENA 2 · Proceso batch: Reporte de Transacciones Diarias (1.000 registros legacy)"
  RESP=$(curl -s -X POST -H "Authorization: Bearer $T_BATCH" "$BATCH/api/batch/jobs/transaccionesDiariasJob?dataset=semana_3")
  gris "curl -X POST $BATCH/api/batch/jobs/transaccionesDiariasJob?dataset=semana_3"
  echo "$RESP" | jq
  EID=$(echo "$RESP" | jq -r .ejecucionId); INS=$(echo "$RESP" | jq -r .instanciaId)
  printf "Procesando"; for i in $(seq 1 60); do
    E=$(curl -s -H "Authorization: Bearer $T_BATCH" $BATCH/api/batch/ejecuciones/$EID | jq -r .estado)
    [[ "$E" == "COMPLETED" || "$E" == "FAILED" ]] && break; printf "."; sleep 1; done; echo
  pausa
  azul "Resultado por paso y por partición (leídos / escritos / omitidos)"
  correr "curl -s -H \"Authorization: Bearer \$T_BATCH\" $BATCH/api/batch/ejecuciones/$EID | jq '{estado, salida, pasos: [.pasos[] | {paso, salida, leidos, escritos, omitidos, commits}]}'"
  pausa
  azul "Registros rechazados: cada uno queda auditado con su motivo (el legacy fallaba en silencio)"
  correr "curl -s -H \"Authorization: Bearer \$T_BATCH\" $BATCH/api/batch/resultados/$INS/rechazos | jq"
  correr "curl -s -H \"Authorization: Bearer \$T_BATCH\" \"$BATCH/api/batch/resultados/$INS/rechazos?detalle=true\" | jq '.[0:3]'"
  pausa
  azul "Resumen diario generado (primeros 5 días)"
  correr "curl -s -H \"Authorization: Bearer \$T_BATCH\" $BATCH/api/batch/resultados/$INS/transacciones | jq '.[0:5]'"
  pausa
  azul "Tolerancia a fallos: se simula la caída de la base de datos durante el job"
  RESP=$(curl -s -X POST -H "Authorization: Bearer $T_BATCH" "$BATCH/api/batch/jobs/interesesMensualesJob?dataset=semana_1&simularFalloTransitorio=true")
  gris "curl -X POST .../interesesMensualesJob?dataset=semana_1&simularFalloTransitorio=true"
  EID=$(echo "$RESP" | jq -r .ejecucionId); sleep 6
  correr "docker compose logs --since 30s batch-service | grep -E 'SIMULACION|rollbacks|terminó' | cut -c1-220"
  correr "curl -s -H \"Authorization: Bearer \$T_BATCH\" $BATCH/api/batch/ejecuciones/$EID | jq '{estado, pasos: [.pasos[] | {paso, escritos, rollbacks}]}'"
  pausa
fi

# ---------------------------------------------------------------------------
if escena 3; then
  azul "ESCENA 3 · BFF: cada canal recibe sólo lo que necesita"
  correr "curl -sk -H \"Authorization: Bearer \$T_MOVIL\" $MOVIL/movil/clientes/1/inicio | jq"
  pausa
  correr "curl -sk -H \"Authorization: Bearer \$T_WEB\" $WEB/web/clientes/1/resumen | jq '{cliente: .cliente.nombres, saldoTotal, cuentas: (.cuentas|length), operaciones: (.operacionesRecientes|length), notificaciones: (.notificaciones|length)}'"
  azul "Tamaño de la respuesta de cada canal"
  correr "curl -sk -o /dev/null -w 'WEB:   %{size_download} bytes\n' -H \"Authorization: Bearer \$T_WEB\" $WEB/web/clientes/1/resumen"
  correr "curl -sk -o /dev/null -w 'MOVIL: %{size_download} bytes\n' -H \"Authorization: Bearer \$T_MOVIL\" $MOVIL/movil/clientes/1/inicio"
  pausa
fi

# ---------------------------------------------------------------------------
if escena 4; then
  azul "ESCENA 4 · Seguridad por canal"
  echo "Token del canal WEB usado en el BFF MÓVIL:"
  correr "curl -sk -o /dev/null -w 'HTTP %{http_code}\n' -H \"Authorization: Bearer \$T_WEB\" $MOVIL/movil/cuentas/1/saldo"
  echo "Cajero desde un terminal NO registrado:"
  correr "curl -sk -H \"Authorization: Bearer \$T_ATM\" -H 'X-Terminal-Id: ATM-FALSO' -H 'Content-Type: application/json' -d '{\"cuentaId\":1,\"pin\":\"1234\"}' $ATM/atm/consulta-saldo; echo"
  echo "PIN incorrecto:"
  correr "curl -sk -H \"Authorization: Bearer \$T_ATM\" -H 'X-Terminal-Id: ATM-STGO-001' -H 'Content-Type: application/json' -d '{\"cuentaId\":3,\"pin\":\"9999\"}' $ATM/atm/consulta-saldo | jq"
  pausa
  azul "Retiro en cajero, y el MISMO retiro reenviado (corte de red): no se cobra dos veces"
  KEY="video-$RANDOM"
  correr "curl -sk -H \"Authorization: Bearer \$T_ATM\" -H 'X-Terminal-Id: ATM-STGO-001' -H 'Idempotency-Key: $KEY' -H 'Content-Type: application/json' -d '{\"cuentaId\":1,\"pin\":\"1234\",\"monto\":20000}' $ATM/atm/retiros | jq"
  correr "curl -sk -H \"Authorization: Bearer \$T_ATM\" -H 'X-Terminal-Id: ATM-STGO-001' -H 'Idempotency-Key: $KEY' -H 'Content-Type: application/json' -d '{\"cuentaId\":1,\"pin\":\"1234\",\"monto\":20000}' $ATM/atm/retiros | jq"
  echo "👉 Fíjate: mismo comprobante y mismo saldo."
  pausa
fi

# ---------------------------------------------------------------------------
if escena 5; then
  azul "ESCENA 5 · Kafka: una transferencia genera eventos en tiempo real"
  correr "curl -sk -H \"Authorization: Bearer \$T_MOVIL\" -H 'Content-Type: application/json' -d '{\"origen\":1,\"destino\":3,\"monto\":15000,\"glosa\":\"Demo video\"}' $MOVIL/movil/transferencias | jq"
  sleep 3
  echo "Aviso que recibió María (cliente 2, dueña de la cuenta destino):"
  correr "curl -sk -H \"Authorization: Bearer \$T_MOVIL\" $MOVIL/movil/clientes/2/avisos | jq '.[0:2]'"
  echo "👉 Ahora muestra Kafka UI: http://localhost:8085 → Topics → transacciones-completadas → Messages"
  pausa
fi

# ---------------------------------------------------------------------------
if escena 6; then
  azul "ESCENA 6 · Resiliencia: se apaga el microservicio de clientes"
  correr "docker compose stop ms-clientes"
  sleep 3
  echo "Web: el perfil es esencial → error controlado e inmediato (no un timeout):"
  correr "curl -sk -H \"Authorization: Bearer \$T_WEB\" $WEB/web/clientes/1/resumen | jq"
  echo "Móvil: la app sigue funcionando con datos parciales:"
  correr "curl -sk -H \"Authorization: Bearer \$T_MOVIL\" $MOVIL/movil/clientes/1/inicio | jq"
  echo "Las transferencias siguen funcionando (no dependen de clientes):"
  correr "curl -sk -H \"Authorization: Bearer \$T_MOVIL\" -H 'Content-Type: application/json' -d '{\"origen\":1,\"destino\":3,\"monto\":1000}' $MOVIL/movil/transferencias | jq"
  pausa
  correr "docker compose start ms-clientes"
  echo "ms-clientes vuelve en ~30-60 s y el circuito se cierra solo."
fi

azul "Fin de la demo ✔"
