# Guion del video (5 a 7 minutos, MP4, con webcam)

**Antes de grabar:**
- `docker compose up -d --build` y esperar a que todo esté *healthy*.
- Abrir pestañas con Eureka (http://localhost:8761), Kafka UI (http://localhost:8085), el repositorio en GitHub y una terminal con los tokens ya obtenidos (`instrucciones.md` §3.1).
- Grabar en Kaltura con la webcam visible en una esquina durante todo el video.

| Tiempo | Sección | Qué mostrar | Qué decir (idea) |
|---|---|---|---|
| 0:00–0:20 | Presentación | Webcam | "Hola, soy ___. Les presento la modernización del backend del Banco XYZ para Desarrollo Backend III." |
| **0:20–1:20** | **1. Resumen ejecutivo** | Diagrama de arquitectura (readme) | El banco usaba COBOL y Shell en mainframe; eso traía problemas de escalabilidad, costo e integración. Objetivo: migrar a microservicios en la nube. Alcance: 3 procesos batch con Spring Batch, 3 BFF (web, móvil, cajeros) y 3 microservicios (cuentas, pagos, clientes) con Spring Cloud, OAuth2, Resilience4j y Kafka, todo en Docker y listo para AWS. Los 5 procesos clave y los 3 requerimientos de negocio: disponibilidad, escalabilidad y seguridad |
| **1:20–3:50** | **2. Resultados y comparación con el legacy** | | |
| | Infraestructura | `docker compose ps` + Eureka con 2 réplicas de ms-cuentas y ms-pagos | "Todo corre en contenedores; los servicios se registran en Eureka y el gateway balancea entre réplicas." |
| | Batch | `POST …/transaccionesDiariasJob`, luego `GET …/ejecuciones/{id}` (leídos, escritos, omitidos por partición) y `resultados/rechazos` | "El legacy fallaba en silencio; aquí cada registro malo queda auditado con su motivo. Los archivos se procesan en paralelo." Mostrar la tabla de equivalencia del readme: mismos totales que la referencia |
| | Batch con fallo | Lanzar con `simularFalloTransitorio=true` y mostrar en el log los reintentos y el COMPLETED final | "Si se cae la BD, reintenta solo; si hay una falla crítica, el job se reinicia desde el último commit." |
| | BFF | Resumen web vs. inicio móvil (comparar los bytes con `-w %{size_download}`) | "Cada canal recibe sólo lo que necesita: el móvil pesa mucho menos y usa ETag." |
| | Seguridad | Token web en el BFF móvil → 401; terminal falso → 403; retiro repetido con la misma Idempotency-Key → mismo comprobante | "Un token de un canal no sirve en otro; el cajero no cobra dos veces." |
| | Kafka | Transferencia → mensaje en Kafka UI → aviso en `/movil/clientes/2/avisos` | "Los servicios se comunican por eventos en tiempo real." |
| | Comparación | Tabla "Comparación con el sistema legacy" del readme | Recorrer 4 o 5 filas: escalabilidad, fallos, batch, seguridad y canales |
| **3:50–5:20** | **3. Desafíos y soluciones** | Código: `FechaParser`, `PagoService` (saga), `PoliticaOmision` | (1) Datos legacy con errores: 4 formatos de fecha, duplicados, montos negativos → normalizar, marcar anomalías u omitir con auditoría. (2) Transferencias entre servicios sin transacción distribuida → saga con compensación e idempotencia. (3) Resiliencia: `docker compose stop ms-clientes` en vivo → la web responde 503 controlado y el móvil, datos parciales; luego `start`. (4) Probar la equivalencia sin COBOL → referencia en Python y tests |
| **5:20–6:30** | **4. Mejoras y próximos pasos** | Sección 12 del informe | Outbox para los eventos, IdP corporativo con MFA y rotación de llaves, rate limiting, Grafana/Prometheus, batch remoto con S3, ECS Fargate con auto scaling (despliegue.md) |
| 6:30–6:50 | Cierre | Webcam + GitHub Actions en verde | "El código, las instrucciones, la guía de despliegue y el informe están en el repositorio. Gracias." |

**Tips:**
- Ensaya una vez con cronómetro: el límite es de **5 a 7 minutos**.
- Ten los comandos listos en un archivo para copiar y pegar (sin errores de tipeo en vivo).
- Usa una letra grande en la terminal (Ctrl + `+`).
- Si una demo tarda, sigue hablando mientras carga.
