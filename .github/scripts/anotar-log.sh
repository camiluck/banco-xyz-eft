#!/usr/bin/env bash
# Publica un resumen de errores del log como anotación del check (visible en la pestaña Checks / API).
# Uso: anotar-log.sh <archivo-log> <titulo>
LOG=$1; TITULO=$2
[[ -f "$LOG" ]] || exit 0
RESUMEN=$( { grep -E "Tests run:|<<< FAIL" "$LOG" | head -60; grep -E "\[ERROR\]|FAIL|Tests run:.*Fail|Caused by|Exception|expected|but was|BUILD|Reactor Summary|SUCCESS \[|FAILURE \[|SKIPPED" "$LOG" \
  | grep -vE "^\s*at " | head -250; } | cut -c1-400 )
RESUMEN=${RESUMEN:0:60000}
RESUMEN="${RESUMEN//'%'/'%25'}"
RESUMEN="${RESUMEN//$'\r'/}"
RESUMEN="${RESUMEN//$'\n'/'%0A'}"
echo "::error title=${TITULO}::${RESUMEN}"
