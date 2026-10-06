# Instrucciones para ejecutar y probar cada componente

## 0. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Docker Desktop (o Docker Engine + Compose v2) | 24 o superior | Levantar todo el sistema |
| Java JDK | 21 | Compilar y ejecutar sin Docker |
| Maven | 3.9 o superior | Compilar y ejecutar las pruebas |
| `curl` y `jq` | cualquiera | Probar las APIs desde la terminal |

> Memoria: el sistema completo usa unos 6 GB de RAM. Si tu equipo tiene menos, mira la sección 7.

```bash
git clone https://github.com/camiluck/banco-xyz-eft.git
cd banco-xyz-eft
```

---

## 1. Compilar y ejecutar las pruebas automáticas

```bash
mvn clean verify
```

Compila los 11 módulos y ejecuta las pruebas de cada uno. Todas deben pasar (`BUILD SUCCESS`). Algunas de las más importantes:

| Módulo | Prueba | Qué verifica |
|---|---|---|
| batch-service | `EquivalenciaLegacyTests` | Resultados idénticos a la referencia legacy, procesamiento paralelo, retry y reejecución automática |
| ms-pagos | `PagosSagaTests` | Transferencia OK, saldo insuficiente, compensación, servicio caído e idempotencia |
| ms-cuentas | `CuentasApiTests` | 401/403, movimientos idempotentes, saldo insuficiente y bloqueo de PIN |
| bff-* | `Bff*Tests` | Token de otro canal rechazado, respuestas por canal y degradación |

---

## 2. Levantar todo el sistema con Docker

```bash
docker compose up -d --build
docker compose ps          # esperar a que todos digan "healthy" (2 a 4 minutos)
```

| Componente | URL |
|---|---|
| Eureka (servicios registrados) | http://localhost:8761 |
| Config Server (ej. config de ms-pagos) | http://localhost:8888/ms-pagos/default |
| Auth Server (llaves públicas) | http://localhost:9000/oauth2/jwks |
| API Gateway | http://localhost:8080 |
| BFF Web / Móvil / Cajeros | https://localhost:8443 · https://localhost:8444 · https://localhost:8445 |
| Batch | http://localhost:8090 |
| Kafka UI (tópicos y mensajes) | http://localhost:8085 |

> Los BFF usan un certificado autofirmado de desarrollo. Con `curl` agrega `-k`; en el navegador acepta la advertencia.

### Prueba automática de punta a punta

```bash
./scripts/prueba-humo.sh
```

Recorre la seguridad, los 3 BFF, Kafka, la resiliencia (detiene y reinicia ms-clientes) y los 3 jobs batch. Termina con `TODAS LAS PRUEBAS OK`.

---

## 3. Probar paso a paso

### 3.1 Obtener tokens (uno por canal)

```bash
TOKEN_WEB=$(curl -s -u frontend-web:web-secret -d grant_type=client_credentials -d scope=canal.web \
  http://localhost:9000/oauth2/token | jq -r .access_token)
TOKEN_MOVIL=$(curl -s -u frontend-movil:movil-secret -d grant_type=client_credentials -d scope=canal.movil \
  http://localhost:9000/oauth2/token | jq -r .access_token)
TOKEN_ATM=$(curl -s -u cajero-atm:atm-secret -d grant_type=client_credentials -d scope=canal.atm \
  http://localhost:9000/oauth2/token | jq -r .access_token)
TOKEN_BATCH=$(curl -s -u batch-admin:batch-secret -d grant_type=client_credentials -d scope=batch.admin \
  http://localhost:9000/oauth2/token | jq -r .access_token)
```

Puedes ver el contenido de un token en https://jwt.io: tiene `aud` (el canal), `scope` y `exp`. El token del cajero dura sólo 2 minutos.

**Datos de demo:**

| Cliente | Nombre | Cuentas (saldo inicial) |
|---|---|---|
| 1 | Juan Pérez (12.345.678-5) | 1: ahorro $1.500.000 · 2: corriente $350.000 |
| 2 | María González (15.678.432-K) | 3: corriente $820.000 |
| 3 | Pedro Muñoz (9.876.543-3) | 4: ahorro $50.000 · 5: vista $0 |

PIN de todas las tarjetas: **1234**. Terminales de cajero válidos: `ATM-STGO-001`, `ATM-STGO-002` y `ATM-VALPO-001`.

### 3.2 BFF Web (datos completos)

```bash
# Dashboard: perfil + cuentas con movimientos + operaciones + notificaciones (en paralelo)
curl -sk -H "Authorization: Bearer $TOKEN_WEB" https://localhost:8443/web/clientes/1/resumen | jq

# Transferencia (Idempotency-Key evita duplicados si se reintenta)
curl -sk -H "Authorization: Bearer $TOKEN_WEB" -H "Content-Type: application/json" -H "Idempotency-Key: trx-001" \
  -d '{"cuentaOrigenId":1,"cuentaDestinoId":3,"monto":25000,"descripcion":"Arriendo"}' \
  https://localhost:8443/web/transferencias | jq

# Pago de servicio
curl -sk -H "Authorization: Bearer $TOKEN_WEB" -H "Content-Type: application/json" \
  -d '{"cuentaOrigenId":2,"monto":18990,"empresa":"Enel","numeroCliente":"123456"}' \
  https://localhost:8443/web/pagos-servicios | jq

# Movimientos completos de una cuenta
curl -sk -H "Authorization: Bearer $TOKEN_WEB" "https://localhost:8443/web/cuentas/1/movimientos?limite=50" | jq
```

### 3.3 BFF Móvil (respuestas livianas)

```bash
curl -sk -H "Authorization: Bearer $TOKEN_MOVIL" https://localhost:8444/movil/clientes/1/inicio | jq
curl -sk -H "Authorization: Bearer $TOKEN_MOVIL" https://localhost:8444/movil/cuentas/1/saldo
curl -sk -H "Authorization: Bearer $TOKEN_MOVIL" -H "Content-Type: application/json" \
  -d '{"origen":1,"destino":3,"monto":5000,"glosa":"Almuerzo"}' https://localhost:8444/movil/transferencias | jq
curl -sk -H "Authorization: Bearer $TOKEN_MOVIL" https://localhost:8444/movil/clientes/2/avisos | jq

# Compara el tamaño de la respuesta con la del BFF web
curl -sk -o /dev/null -w "web: %{size_download} bytes\n"   -H "Authorization: Bearer $TOKEN_WEB"   https://localhost:8443/web/clientes/1/resumen
curl -sk -o /dev/null -w "movil: %{size_download} bytes\n" -H "Authorization: Bearer $TOKEN_MOVIL" https://localhost:8444/movil/clientes/1/inicio

# ETag: la segunda consulta sin cambios devuelve 304 (sin cuerpo)
curl -sk -i -H "Authorization: Bearer $TOKEN_MOVIL" https://localhost:8444/movil/cuentas/1/saldo | grep -i etag
curl -sk -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN_MOVIL" -H 'If-None-Match: "<etag-anterior>"' \
  https://localhost:8444/movil/cuentas/1/saldo
```

### 3.4 BFF Cajeros automáticos

```bash
# Consulta de saldo
curl -sk -H "Authorization: Bearer $TOKEN_ATM" -H "X-Terminal-Id: ATM-STGO-001" -H "Content-Type: application/json" \
  -d '{"cuentaId":1,"pin":"1234"}' https://localhost:8445/atm/consulta-saldo | jq

# Retiro (repite el mismo comando: el comprobante es el mismo y no se descuenta dos veces)
curl -sk -H "Authorization: Bearer $TOKEN_ATM" -H "X-Terminal-Id: ATM-STGO-001" -H "Idempotency-Key: 0001" \
  -H "Content-Type: application/json" -d '{"cuentaId":1,"pin":"1234","monto":40000}' https://localhost:8445/atm/retiros | jq

# Casos de seguridad
#  - terminal no registrado -> 403 + alerta en Kafka
curl -sk -H "Authorization: Bearer $TOKEN_ATM" -H "X-Terminal-Id: ATM-FALSO" -H "Content-Type: application/json" \
  -d '{"cuentaId":1,"pin":"1234"}' https://localhost:8445/atm/consulta-saldo
#  - 3 PIN incorrectos -> la cuenta 4 se bloquea (423) + alerta PIN_BLOQUEADO
for i in 1 2 3 4; do curl -sk -H "Authorization: Bearer $TOKEN_ATM" -H "X-Terminal-Id: ATM-STGO-001" \
  -H "Content-Type: application/json" -d '{"cuentaId":4,"pin":"0000"}' https://localhost:8445/atm/consulta-saldo; echo; done
#  - monto no dispensable -> 422
curl -sk -H "Authorization: Bearer $TOKEN_ATM" -H "X-Terminal-Id: ATM-STGO-001" -H "Idempotency-Key: 0002" \
  -H "Content-Type: application/json" -d '{"cuentaId":1,"pin":"1234","monto":12345}' https://localhost:8445/atm/retiros
#  - token web en el cajero -> 401
curl -sk -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN_WEB" -H "X-Terminal-Id: ATM-STGO-001" \
  -H "Content-Type: application/json" -d '{"cuentaId":1,"pin":"1234"}' https://localhost:8445/atm/consulta-saldo
```

### 3.5 Kafka (eventos en tiempo real)

1. Abre http://localhost:8085 → *Topics*. Verás `transacciones-completadas`, `alertas-seguridad` y `cuentas-eventos`, cada uno con 3 particiones.
2. Haz una transferencia (3.2) y revisa el mensaje en `transacciones-completadas`.
3. Comprueba que el consumidor (ms-clientes) generó la notificación:
   ```bash
   curl -sk -H "Authorization: Bearer $TOKEN_MOVIL" https://localhost:8444/movil/clientes/2/avisos | jq
   ```
4. Los intentos de PIN y de terminal no autorizado aparecen en `alertas-seguridad`.

### 3.6 Resiliencia (Resilience4j y fallback)

```bash
docker compose stop ms-clientes
# Web: el perfil es esencial -> 503 claro e inmediato (no un timeout)
curl -sk -H "Authorization: Bearer $TOKEN_WEB" https://localhost:8443/web/clientes/1/resumen | jq
# Móvil: la pantalla de inicio sigue funcionando con datos parciales
curl -sk -H "Authorization: Bearer $TOKEN_MOVIL" https://localhost:8444/movil/clientes/1/inicio | jq
# Las transferencias y los cajeros siguen funcionando (no dependen de ms-clientes)
docker compose start ms-clientes

# Estado de los circuit breakers
curl -s http://localhost:8080/actuator/health | jq
```

Saga con servicio caído: detén `ms-cuentas` (`docker compose stop ms-cuentas`) y haz una transferencia. La respuesta será `202 PENDIENTE`. Vuelve a levantarlo (`docker compose start ms-cuentas`): en menos de 1 minuto el planificador de ms-pagos la completa sola.

### 3.7 Escalabilidad horizontal y balanceo

```bash
docker compose up -d --scale ms-pagos=4 --scale ms-cuentas=3
```

Abre http://localhost:8761: aparecen 4 instancias de MS-PAGOS. Haz varias transferencias y revisa los logs:

```bash
docker compose logs -f ms-pagos | grep "registrado"
```

Las peticiones se reparten entre las réplicas.

### 3.8 Procesos batch

```bash
# Lanzar los 3 jobs (asíncrono: devuelve el id de la ejecución)
curl -s -X POST -H "Authorization: Bearer $TOKEN_BATCH" \
  "http://localhost:8090/api/batch/jobs/transaccionesDiariasJob?dataset=semana_3" | jq
curl -s -X POST -H "Authorization: Bearer $TOKEN_BATCH" \
  "http://localhost:8090/api/batch/jobs/interesesMensualesJob?dataset=semana_3&periodo=2024-12" | jq
curl -s -X POST -H "Authorization: Bearer $TOKEN_BATCH" \
  "http://localhost:8090/api/batch/jobs/estadosCuentaAnualesJob?dataset=semana_3&anio=2024" | jq

# Estado: leídos, escritos, omitidos, commits y rollbacks por paso y por partición
curl -s -H "Authorization: Bearer $TOKEN_BATCH" http://localhost:8090/api/batch/ejecuciones/1 | jq

# Resultados de negocio (usa el instanciaId de la respuesta)
curl -s -H "Authorization: Bearer $TOKEN_BATCH" http://localhost:8090/api/batch/resultados/1/transacciones | jq
curl -s -H "Authorization: Bearer $TOKEN_BATCH" http://localhost:8090/api/batch/resultados/2/intereses | jq
curl -s -H "Authorization: Bearer $TOKEN_BATCH" http://localhost:8090/api/batch/resultados/3/estados | jq
curl -s -H "Authorization: Bearer $TOKEN_BATCH" http://localhost:8090/api/batch/resultados/1/rechazos | jq
curl -s -H "Authorization: Bearer $TOKEN_BATCH" "http://localhost:8090/api/batch/resultados/1/rechazos?detalle=true" | jq '.[0:5]'

# Reportes CSV generados
docker compose exec batch-service ls -l /app/output
docker compose exec batch-service cat /app/output/reporte_transacciones_diarias_1.csv
```

**Parámetros:**

| Parámetro | Valores | Para qué |
|---|---|---|
| `dataset` | `semana_1`, `semana_2`, `semana_3` o `*` | `*` procesa las 3 semanas en paralelo |
| `periodo` | `yyyy-MM` | Mes de los intereses |
| `anio` | `yyyy` | Año de los estados de cuenta |
| `simularFalloTransitorio=true` | | Las 2 primeras escrituras fallan; se ve el *retry* y el job termina `COMPLETED` |
| `simularFalloCritico=true` | | La primera ejecución termina `FAILED` y a los 30 s se reejecuta sola y termina `COMPLETED` |

Para ver los reintentos y la reejecución en los logs:

```bash
docker compose logs -f batch-service
```

### 3.9 Verificar la equivalencia con el legacy

```bash
python3 herramientas/referencia_legacy.py batch-service/data semana_3
```

Compara esos números con los resultados de la API (3.8) o con `EquivalenciaLegacyTests`.

---

## 4. Ejecutar sin Docker (desarrollo)

Cada servicio usa H2 en memoria por defecto. Kafka es opcional: sin él los servicios funcionan, pero no publican eventos.

```bash
mvn -q -DskipTests package
java -jar config-server/target/config-server.jar &
java -jar eureka-server/target/eureka-server.jar &
java -jar auth-server/target/auth-server.jar &
java -jar ms-cuentas/target/ms-cuentas.jar &
java -jar ms-pagos/target/ms-pagos.jar &
java -jar ms-clientes/target/ms-clientes.jar &
java -jar api-gateway/target/api-gateway.jar &
java -jar bff-web/target/bff-web.jar &
java -jar batch-service/target/batch-service.jar     # ejecutar desde la carpeta batch-service o definir BATCH_INPUT_DIR
```

Para tener Kafka sólo con Docker: `docker compose up -d kafka kafka-ui`, y luego exporta `KAFKA_BOOTSTRAP=localhost:29092` en cada servicio.

## 5. Monitoreo

| Qué | Dónde |
|---|---|
| Salud de cada servicio | `/actuator/health` |
| Métricas (Prometheus) | `/actuator/prometheus`, por ejemplo http://localhost:8080/actuator/prometheus |
| Trazabilidad | Cada línea de log tiene `[servicio,traceId,spanId]` |
| Logs de todo el sistema | `docker compose logs -f` |

## 6. Detener

```bash
docker compose down        # mantiene los datos
docker compose down -v     # borra también las bases de datos
```

## 7. Equipos con poca memoria

Usa una réplica por servicio y omite Kafka UI:

```bash
docker compose up -d --build --scale ms-cuentas=1 --scale ms-pagos=1
docker compose stop kafka-ui
```

## 8. Solución de problemas

| Síntoma | Causa | Solución |
|---|---|---|
| `503 SERVICIO_NO_DISPONIBLE` justo después de levantar | Eureka aún no propaga las instancias | Esperar 30 a 60 s |
| `401` con un token recién pedido | El token es de otro canal o expiró (el de cajero dura 2 min) | Pedir el token del canal correcto |
| Un contenedor se reinicia | Falta memoria | Ver la sección 7 |
| `curl: (60) SSL certificate problem` | El certificado es autofirmado | Agregar `-k` |
