# ADR-003 — Retención de `request_log` y de los cuatro registros de auditoría

| Campo | Valor |
|---|---|
| Estado | **Propuesta — pendiente de decisión** |
| Fecha | 27-08-2026 |
| Decide | Responsable del proyecto |
| Redacta | Bonilla Diaz William Steven |
| Cerraría | **D-10** — Retención concreta, en días, de cada registro por separado |
| Issue | #29 |
| Documentos afectados | `architecture.md` §6.6 y §16 · `security.md` §8.3 · `application.yml` |

---

## Contexto

**Cinco tablas *append-only* y ninguna se purga.** `request_log` y los cuatro registros de auditoría —cambios, eliminación, error y seguridad— crecen de forma monótona desde que el sistema recibe su primera petición, y ningún proceso las reduce.

El síntoma visible hoy es menor: `nexus.audit.request-log-retention-days` está declarada en `application.yml` **sin valor y sin lector**. Ninguna clase Java la usa. El problema real no es la variable.

**Qué se paga por no decidirlo:**

| Coste | Detalle |
|---|---|
| El **tamaño** | El conteo acotado que `RF-SP-011` a `RF-SP-014` estrenaron mantiene el coste de *consulta* bajo control con independencia del volumen. No reduce el volumen |
| Los **índices** | Los de línea de tiempo de `V33` se pagan en **cada operación de negocio del sistema**, porque cada una emite su evento en la misma transacción. Crecen con la tabla |
| El **riesgo legal** | Conservar indefinidamente la IP y el actor de cada petición es una decisión sobre datos personales que nadie ha tomado. Guardar «por si acaso» es una postura, y hoy es la postura por omisión |

**Y esto es lo que hace que no sea una decisión técnica:** la retención es **cuánto tiempo el sistema debe poder responder «quién hizo esto»**. Eso no lo decide quien escribe la purga.

## Por qué son cinco decisiones y no una

El registro de peticiones y el de seguridad no tienen por qué durar lo mismo, y tratarlos igual es el error que este ADR existe para evitar:

| Registro | Qué contiene | Qué se pierde al purgarlo |
|---|---|---|
| `request_log` | **Toda** petición, con su IP, su duración y su estado | El volumen es de otro orden de magnitud: es el que fuerza la decisión. Se pierde la capacidad de reconstruir un barrido de rutas o medir el p95 hacia atrás |
| `audit_change_log` | Qué cambió, con su antes y su después | Es el historial del negocio: la línea de tiempo de un rol o de una persona **deja de existir**, no se degrada |
| `audit_deletion_log` | Qué se eliminó, con motivo y `snapshot` | Es lo único que queda de lo borrado. Purgarlo es **la segunda eliminación**, y esta sin rastro |
| `audit_error_log` | Rechazos por regla y fallos no controlados | Valor operativo, no probatorio. Es el candidato natural al plazo más corto |
| `audit_security_log` | Autenticación y autorización, incluidos los intentos | Es el de más valor probatorio y el que más suele tener plazo impuesto desde fuera |

## Opciones

### A · Un plazo único para las cinco

**Coste:** una variable, una tarea, un `DELETE` por tabla. Es lo más barato de construir.

**Lo que se paga:** el plazo acaba siendo el del registro más exigente aplicado a todos —conservar `request_log` un año por si acaso, con el volumen que eso implica— o el del más barato aplicado al probatorio, que es peor. Convierte cinco preguntas distintas en una respuesta de compromiso que no es la correcta para ninguna.

### B · Cinco plazos, purga por borrado (recomendada)

Cada registro con su plazo y su motivo escrito. Una tarea programada por tabla, con el mismo cerrojo consultivo que ya usa `PurgeExpiredTokensService` para que con varias instancias purgue una sola.

**Coste:** cinco variables, una tarea parametrizada, y **su propio evento de auditoría** — una purga que elimina evidencia sin registrar cuánta eliminó no es auditable, y su ausencia sería indistinguible de una que nunca corrió. Es el mismo criterio que `security.md` §5.5.2 ya fijó para la purga de sesiones.

**Lo que se paga:** lo borrado no vuelve. Si el plazo se queda corto, se descubre el día que hace falta.

### C · Cinco plazos, con archivado frío del probatorio

Como B, pero los dos registros con valor probatorio —eliminación y seguridad— se exportan antes de borrarse.

**Coste:** un destino que hoy no existe, con su propia decisión de acceso, cifrado y retención. Depende de infraestructura que ADR-002 no aporta.

**Lo que se paga:** es la opción correcta a largo plazo y **no se puede construir todavía**. Proponerla ahora es aplazar la purga otra vez.

## Recomendación

**B**, con estos plazos como punto de partida a confirmar:

| Registro | Plazo propuesto | Por qué |
|---|---|---|
| `request_log` | **30 días** | Es el que fuerza el problema y el de menor valor individual. Treinta días cubren la investigación de un incidente reciente y el cálculo de percentiles |
| `audit_error_log` | **90 días** | Valor operativo: sirve para ver tendencias de rechazo, no para probar nada |
| `audit_change_log` | **Sin purga por ahora** | Es el historial del negocio y su volumen es el de las escrituras, no el de las peticiones. Purgarlo exige antes decidir si el historial de un rol puede desaparecer, que es otra conversación |
| `audit_deletion_log` | **Sin purga por ahora** | Ídem, y más fuerte: es lo único que queda de lo borrado |
| `audit_security_log` | **Sin purga por ahora** | El de más valor probatorio. Su plazo suele venir impuesto desde fuera, y no hay nadie que lo haya impuesto todavía |

**«Sin purga por ahora» es una decisión, no un aplazamiento**, y por eso se escribe: tres de las cinco tablas seguirán creciendo, y lo que se compra es que las dos que crecen deprisa dejen de hacerlo. Revisar los tres restantes cuando haya volumen real es más barato que fijar hoy un plazo sobre datos que nadie ha visto.

## Qué hace falta para cerrarla

1. **Confirmar o corregir los cinco plazos.** Es la parte que no puede decidir quien implementa.
2. Saber si hay una **obligación externa** —cliente, sector, contrato— sobre el registro de seguridad. Si la hay, manda sobre todo lo anterior.
3. Decidir si la purga **emite evento** (recomendado: sí, sin identidades, como la de sesiones).

Con eso, la implementación es directa y no tiene decisiones dentro.
