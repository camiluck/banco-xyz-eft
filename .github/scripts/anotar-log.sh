#!/usr/bin/env bash
# Publica un resumen de errores del log como anotación del check (visible en la pestaña Checks / API).
# Uso: anotar-log.sh <archivo-log> <titulo>
LOG=$1; TITULO=$2
[[ -f "$LOG" ]] || exit 0
RESUMEN=$( {
  grep -E "^\[ERROR\]" "$LOG" | grep -vE "^\[ERROR\]\s*$|^\[ERROR\] *(->|Re-run|To see|For more|Help)" | head -80
  grep -E "<<< FAIL|Caused by|expected|but was|Expecting" "$LOG" | awk '!s[$0]++' | head -60
  grep -E "Reactor Summary" -A 20 "$LOG" | head -20
  grep -E "ERROR|Exception|unhealthy|✘" "$LOG" | grep -vE "^\[ERROR\]|^\s*at " | awk '!s[$0]++' | head -40
} | cut -c1-500 )
RESUMEN=${RESUMEN:0:60000}
RESUMEN="${RESUMEN//'%'/'%25'}"
RESUMEN="${RESUMEN//$'\r'/}"
RESUMEN="${RESUMEN//$'\n'/'%0A'}"
echo "::error title=${TITULO}::${RESUMEN}"
