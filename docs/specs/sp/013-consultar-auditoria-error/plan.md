# PLAN — `RF-SP-013` Consultar auditoría de error

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-013` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, filtros y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. La mecánica común a los cuatro registros la fijó el plan de [`RF-SP-011`](../011-consultar-auditoria-cambios/plan.md) y **este documento la hereda sin repetirla**. Aquí se decide solo lo propio: qué índice sostiene el diagnóstico de una ráfaga de fallos, y cómo se garantiza que este registro no recoja lo que no le corresponde.

---

## 1. Enfoque

Es el más simple de los cuatro en cuanto a consulta: se hereda íntegro el §4 y el §7 de `RF-SP-011` —proyección única, orden `occurred_at DESC, id DESC`, conteo acotado, rango semiabierto sobre instantes con zona, `readOnly = true`, sin `domain`— y cambian solo los filtros y las columnas devueltas.

Lo propio son dos cosas, y ninguna está en el camino de la consulta:

1. **Los dos criterios negativos.** `CA-SP-100` y `CA-SP-101` exigen que las denegaciones de autorización y las validaciones de formato **no aparezcan** aquí. Eso no se cumple consultando bien: se cumple escribiendo bien. Y como no hay código en este requerimiento que pueda garantizarlo, se lleva al esquema, donde sí se garantiza (§2).
2. **El acceso que necesita el diagnóstico real.** `spec.md` §13 describe el caso que importa: una caída de un tercero genera miles de eventos con el mismo código, y hay que poder verlos por código y por rango de fechas. Esa consulta no la sostiene ninguno de los índices existentes.

`FA-001` —localizar un fallo por el identificador de correlación que el usuario leyó en la respuesta— es el flujo para el que la especificación dice que existe esta consulta, y **ya está resuelto**: `ix_audit_error_log_correlation_id` lo crea `V4` (`architecture.md` §6.6.6). No hay nada que añadir para él.

## 2. Cambios de esquema

**Migración:** `V11__create_audit_error_log_indexes.sql`

La tabla `audit_error_log` y sus cuatro índices mínimos los crea `V4__create_audit_logs.sql` (`RF-SP-001`). Esta migración añade dos accesos.

| Tabla | Cambio | Detalle |
|---|---|---|
| `audit_error_log` | Altera (índice) | `ix_audit_error_log_occurred_at` sobre `(occurred_at DESC, id DESC)` |
| `audit_error_log` | Altera (índice) | `ix_audit_error_log_error_code` sobre `(error_code, occurred_at DESC)` |

**El índice de línea de tiempo** es el mismo caso de `RF-SP-011` §2, por las mismas razones y sin repetir el argumento: ninguno de los índices existentes sirve al listado sin filtros ordenado por fecha.

**El índice por código de error existe por un caso concreto de la especificación.** `spec.md` §13 lo describe: «una caída de un tercero puede generar miles de eventos iguales», y §14 pregunta 1 resolvió no agruparlos, porque agrupar borra cuándo empezó la caída y cuándo terminó, que es lo primero que se pregunta al diagnosticarla. La consecuencia directa es que la consulta de diagnóstico es **por código y por rango de fechas**, sobre un conjunto que puede tener miles de filas. El índice compuesto la resuelve entera: acota por `error_code` y devuelve ya ordenado por fecha, sin paso de ordenamiento.

Es la diferencia con los filtros que en `RF-SP-011` §2 se rechazaron. `module` tiene tantos valores como módulos y `action` exactamente dos; `error_code` tiene uno por cada regla de negocio y por cada código del contrato —decenas ya hoy, y creciendo con el sistema—. Y el coste de escritura es asumible: esta tabla recibe una fila por rechazo de regla y por fallo no controlado, no una por cada escritura de negocio como `audit_change_log`.

**Qué índices no se crean.** `error_type` tiene tres valores y `severity` dos: dividir la tabla en tres partes no acota nada, y ambos se aplican barato sobre el conjunto que ya redujeron el código, el recurso o el rango. El filtro por `resource` lo sirve `ix_audit_error_log_resource` —`(resource, entity_id, occurred_at DESC)`, creado en `V4`— por su columna de prefijo. Y el de correlación tiene el suyo. El criterio sigue siendo el de `RF-SP-011` §2: el mínimo que sostiene las consultas reales.

### La restricción que hace verificables `CA-SP-100` y `CA-SP-101`

`architecture.md` §6.6.4 declara qué entra en este registro y qué no: quedan fuera la validación de formato (`400`), el `401`, el `404` y la denegación de autorización (`403`), que va a `audit_security_log` porque no es un fallo del sistema sino el sistema funcionando.

Hasta ahora eso era una convención que dependía de que cada requerimiento la respetara al **escribir**. Al aprobar este plan se lleva al esquema, en `V4__create_audit_logs.sql`:

```sql
CONSTRAINT ck_audit_error_log_status CHECK (http_status NOT IN (400, 401, 403, 404))
```

Tres consecuencias:

- **`CA-SP-100` y `CA-SP-101` dejan de ser criterios que solo se pueden comprobar por ausencia.** Un `403` escrito por descuido en el registro de errores ya no es un dato incorrecto que nadie nota: es un `INSERT` que falla, en el momento y en el lugar del error.
- **Los estados admitidos no se enumeran a propósito.** Se admite todo lo que no está prohibido: `409` y `422` de regla de negocio, `5xx` no controlados, y los de un eventual fallo de integración con un sistema externo, que puede acompañar tanto a una respuesta degradada como a un rechazo (`architecture.md` §7.3). Una lista blanca obligaría a alterar la restricción cada vez que un requerimiento estrenara un estado legítimo.
- **Tenía que declararse ahora.** Va en `V4`, la migración que crea la tabla, no en esta: añadir una restricción a una tabla en uso obliga a validar todas las filas existentes y a decidir qué hacer con las que no cumplen. Los planes de `RF-SP-001` y `architecture.md` §6.6.4 quedaron actualizados el 21-08-2026.

**Lo que la restricción no cubre.** `CA-SP-101` menciona también los recursos inexistentes; el `404` está en la lista, así que queda cubierto. Pero un rechazo de negocio que devolviera `409` con un mensaje de validación de formato sí cabría en la tabla: la restricción acota por estado, no por intención. Esa parte sigue siendo convención de quien escribe, y se verifica de extremo a extremo en §11.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. El reparto es el de `RF-SP-011` §3, con los nombres de este registro.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `spec.md` §5 no declara ninguna regla |
| `application` | `ListErrorAuditService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)` |
| `application` | `ErrorAuditQuery` | Nuevo | Criterios ya validados y normalizados |
| `application` | `ErrorAuditItem` | Nuevo | Modelo de lectura |
| `application` | `ErrorAuditQueryRepository` | Nuevo | Puerto de consulta |
| `application` | `ErrorType` | Nuevo | Enum cerrado `BUSINESS_RULE`, `INTEGRATION`, `UNHANDLED` |
| `application` | `ErrorSeverity` | Nuevo | Enum cerrado `MEDIA`, `ALTA` |
| `infrastructure` | `JpaErrorAuditQueryRepository` | Nuevo | Adaptador. Predicado, proyección y conteo acotado |
| `infrastructure` | `AuditErrorLogEntity` | Nuevo | Mapeo JPA. Solo como metamodelo |
| `api` | `AuditController` | Modificado | Añade `GET /api/v1/audit/errors` con **su** permiso |
| `api` | `ListErrorAuditRequest` | Nuevo | Parámetros de consulta con Bean Validation (`VAL-001` a `VAL-003`) |
| `api` | `ErrorAuditItemResponse` | Nuevo | DTO de salida de cada fila |
| `shared/api` | `PageResponse<T>`, `BoundedCount` | Sin cambios | Definidos en `RF-SP-011` |

**`ErrorSeverity` tiene dos valores y `audit_security_log` usa tres** —`INFORMATIVA`, `MEDIA`, `ALTA`—. Son enums distintos y no se comparten: el `CHECK` de cada tabla declara su propio dominio, y unificarlos permitiría escribir aquí una severidad que el esquema rechaza. Es la misma razón por la que `DeletionType` no se compartió en `RF-SP-012` §3.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/audit/errors` | Listado paginado de fallos no controlados, rechazos por regla de negocio y fallos de integración |

**Petición**

```
GET /api/v1/audit/errors?page=0&size=20
                        &errorType=INTEGRATION
                        &severity=ALTA
                        &errorCode=INT-001
                        &resource=roles
                        &actorId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d99
                        &from=2026-08-01T00:00:00Z
                        &to=2026-09-01T00:00:00Z
                        &correlationId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9daa
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `page`, `size` | entero | `0`, `20` | Igual que en `RF-SP-011` §4 |
| `errorType` | enum | — | `BUSINESS_RULE`, `INTEGRATION` o `UNHANDLED`. Otro → `VAL-003` |
| `severity` | enum | — | `MEDIA` o `ALTA`. Otro → `VAL-003` |
| `errorCode` | texto | — | Coincidencia **exacta**. Un código inexistente devuelve colección vacía |
| `resource` | texto | — | Coincidencia exacta |
| `actorId` | UUID | — | Quien sufrió el fallo. No se valida que exista |
| `from` / `to` | instante ISO-8601 | — | Rango semiabierto `[from, to)`. `from` posterior a `to` → `VAL-001` |
| `correlationId` | UUID | — | El flujo de `FA-001`: localizar el fallo que un usuario reportó |

- **`errorCode` filtra por igualdad, no por contención.** Es un código de un catálogo —`RN-SEG-003`, `INT-001`, `ERR-500`—, no texto libre. Una coincidencia por fragmento haría que `RN-SEG-01` devolviera también `RN-SEG-010` a `RN-SEG-013`, que es justo la confusión que un diagnóstico no puede permitirse. Y es lo que permite usar el índice de `V11`.
- **No se busca por texto sobre `message`.** `spec.md` §6.1 no lo pide, y el mensaje es un texto saneado y a menudo repetido: la pregunta útil se hace por código, que es estable, y no por la redacción del mensaje, que puede cambiar entre versiones sin que nada avise. Es la asimetría deliberada con `RF-SP-012`, donde el motivo **sí** es texto libre escrito por una persona y buscarlo es la pregunta principal.
- **No hay `sort`**, por lo dicho en `RF-SP-011` §4.

**Respuesta `200`**

```json
{
  "content": [
    {
      "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01",
      "occurredAt": "2026-08-21T14:32:11.482Z",
      "actorId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d99",
      "resource": "roles",
      "entityId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10",
      "operation": "POST /api/v1/roles/{id}/permissions",
      "errorCode": "RN-SEG-003",
      "errorType": "BUSINESS_RULE",
      "httpStatus": 409,
      "severity": "ALTA",
      "message": "Uno o más permisos no están entre los del rol padre.",
      "correlationId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9daa",
      "ipAddress": "190.85.12.7",
      "userAgent": "Mozilla/5.0 …"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 412,
  "totalPages": 21,
  "totalIsExact": true
}
```

- **`message` se devuelve tal como se almacenó, sin reinterpretar.** `CA-SP-099` exige que no contenga trazas, sentencias, rutas ni versiones, y esa garantía la da el saneamiento **al escribir** (Art. VI.5, `architecture.md` §6.6.4). No se sanea otra vez al leer: dos saneadores divergen, y el de lectura no protegería a quien consulte la base directamente. Es el mismo argumento de `RF-SP-011` §10 y `RF-SP-012` §10, y se verifica sobre el camino de escritura (§11).
- **`entityId` es nulable** y se devuelve como `null` sin omitirse: hay fallos que no se refieren a un registro concreto (`architecture.md` §6.6.4).
- **`actorId` nulo significa que el fallo ocurrió sin actor** —un proceso interno o una tarea programada (`spec.md` §13)—, no que se perdiera el dato.
- **`correlationId` e `ipAddress` son nulos a la vez o ninguno lo es**, por `ck_audit_error_log_origen`.
- **No se devuelve la traza técnica.** `spec.md` §4.2 la deja fuera de forma explícita: vive en el registro de aplicación y se alcanza por `correlationId`. Esta tabla responde a quién le falló qué, no en qué línea.
- **No se devuelven agregados por tipo ni por severidad** (`spec.md` §14, pregunta 2). No hay `GROUP BY` en la sentencia, que es lo único que lo hace verificable.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | `from` posterior a `to` (`EX-001`) | `VAL-001` | `from` |
| `400` | `page` negativa o `size` fuera de `[1, 100]` (`EX-002`) | `VAL-002` | `page` o `size` |
| `400` | `errorType` o `severity` fuera de su dominio | `VAL-003` | El parámetro afectado |
| `400` | Un UUID o un instante malformado | `VAL-003` | El parámetro afectado |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `audit:read-errors` | `AUTH-002` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

Sin `404` ni `422`, por lo dicho en `RF-SP-011` §4.

**Un detalle circular que conviene tener presente:** un `500` de este endpoint escribe su propia fila en `audit_error_log`, que es la tabla que el endpoint consulta. No es un problema —la escritura va en transacción independiente (`REQUIRES_NEW`) y no puede realimentarse, porque el fallo de consulta no vuelve a consultar—, pero explica el caso límite de `spec.md` §13: un fallo al escribir la auditoría no debe provocar una cadena de eventos, y no la provoca precisamente porque la escritura de auditoría no se audita a sí misma.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/audit/errors` | `audit:read-errors` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`).
- **Este es el permiso que mejor ilustra por qué la auditoría se lee por tipo y no en bloque.** `security.md` §4.4 lo dice con este ejemplo exacto: soporte técnico recibe `audit:read-errors` y **nada más**, de modo que puede diagnosticar una incidencia sin ver quién editó qué ni quién intentó entrar y falló. Con un único `audit:read`, dar soporte a un usuario obligaría a conceder la actividad de autenticación de toda la organización.
- `CA-SP-102` verifica lo contrario: un actor con los otros tres permisos de auditoría, pero sin este, recibe `403`. Compartir `AuditController` no lo relaja, porque el permiso se declara por método.
- **No hay filtrado por alcance de datos.** Quien puede diagnosticar, diagnostica todo.
- La resolución del permiso puede usar la caché de `security.md` §4.5: solo se decide acceso.

## 6. Auditoría

La de `RF-SP-011` §6, sin variaciones: la consulta exitosa **no se audita**, los `400` tampoco, el `403` lo registra la capa de seguridad en `audit_security_log`, y un `5xx` va a `audit_error_log` con `resource = 'audit_error_log'` y `operation = 'GET /api/v1/audit/errors'`.

Dos precisiones propias:

- **Este endpoint no escribe en la tabla que lee** por ningún camino de negocio. La única fila que puede generar es la de su propio `500`, y la emite el manejador global, no el caso de uso.
- **`RF-SP-014` no debe heredar esta sección**, por la razón ya anotada dos veces: allí consultar es en sí mismo un evento de seguridad.

## 7. Transaccionalidad

La de `RF-SP-011` §7, sin variaciones: una sola transacción `readOnly = true` para datos y conteo; `audit_error_log` y `audit_security_log` en transacción independiente con `REQUIRES_NEW`; `request_log` fuera de toda transacción.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `RF-SP-001` | Su `V4__create_audit_logs.sql` gana `ck_audit_error_log_status`. El plan quedó actualizado el 21-08-2026 |
| `architecture.md` | §6.6.4 pasa a declarar esa restricción junto a la tabla de qué entra y qué no, para que documento y esquema no diverjan (Art. XII.3) |
| `shared/audit` | **Todo escritor de `audit_error_log` queda sujeto a la restricción.** Quien intente registrar un `400`, `401`, `403` o `404` recibirá un fallo de integridad, no un dato aceptado en silencio. Es lo pretendido, y conviene que quien implemente el escritor lo sepa antes de encontrárselo |
| `shared/api` | Ninguno. `PageResponse<T>` y `BoundedCount` se reutilizan tal cual |
| `SP` (`RF-SP-014`) | Hereda la mecánica de `RF-SP-011`, debe añadir su propio índice de línea de tiempo, y **no** hereda §6 |
| Observabilidad (D-10) | `spec.md` §14, pregunta 3, deja la retención fuera de esta especificación. Este plan tampoco la resuelve: la consulta se comporta igual con noventa días que con dos años, y el conteo acotado impide que el volumen degrade la respuesta. Lo que no mitiga es el crecimiento del almacenamiento |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Dejar `CA-SP-100` y `CA-SP-101` como convención verificada solo por prueba | La prueba comprueba que hoy nadie escribe un `403` aquí; la restricción impide que alguien lo escriba mañana. Y el coste de declararla es una línea en la migración que crea la tabla |
| Declarar una lista blanca de estados admitidos en lugar de una lista negra | Obligaría a alterar la restricción cada vez que un requerimiento estrenara un estado legítimo. Ya son cuatro los que se usan hoy —`200`, `409`, `422`, `503`, más los `5xx`— y ninguno estaba previsto al diseñar la tabla |
| Añadir la restricción en `V11` en lugar de en `V4` | Sobre una tabla en uso, añadir un `CHECK` obliga a validar todas las filas existentes y a decidir qué hacer con las que no cumplen. Nada está desplegado: declararla al crear la tabla no cuesta nada y después cuesta una migración de datos |
| Índice sobre `error_type` o `severity` | Tres valores y dos valores. Dividir la tabla en tres partes no acota nada, y ambos filtros se aplican barato sobre lo que ya redujeron el código o el rango |
| No crear el índice por `error_code` | Deja sin sostener la consulta que `spec.md` §13 describe como el caso a resolver: miles de eventos con el mismo código durante una caída. `error_code` tiene cardinalidad real, a diferencia de `module` o `action` en `RF-SP-011` |
| Búsqueda por texto sobre `message` | El mensaje es texto saneado y a menudo repetido, y su redacción puede cambiar entre versiones sin que nada avise. La pregunta estable se hace por código. Exigiría además un índice de trigramas sobre una tabla que recibe una fila por cada rechazo de regla |
| Agrupar los fallos repetidos y devolver un contador | `spec.md` §14, pregunta 1: borra cuándo empezó la caída y cuándo terminó, que es lo primero que se pregunta al diagnosticarla |
| Devolver conteos por tipo y severidad | `spec.md` §14, pregunta 2: detectar una degradación en curso es observabilidad, y añadir agregados aquí produciría un panel de métricas peor que el panel de métricas |
| Incluir la traza técnica en la respuesta | `spec.md` §4.2 la deja fuera. Vive en el registro de aplicación y se alcanza por `correlationId`; traerla aquí multiplicaría el tamaño de la tabla y expondría rutas y versiones que Art. VI.5 prohíbe devolver |
| Sanear `message` otra vez al leerlo | Dos saneadores divergen, y el de lectura no protege a quien consulte la base directamente. La garantía vive donde se escribe |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Un escritor intenta registrar un estado prohibido y la operación de negocio falla por ello | Medio | La escritura de auditoría de error va siempre en transacción independiente `REQUIRES_NEW` (Art. V.14), de modo que su fallo **no revierte la operación de negocio**. Se registra en el log de aplicación con su `correlation_id`. Es el comportamiento correcto: un evento mal formado no debe tumbar una petición, pero tampoco debe guardarse |
| Un dato sensible llega a `message` y esta consulta lo publica | **Alto** | Saneamiento al escribir (Art. VI.5). `CA-SP-099` se verifica sobre el camino de escritura (§11) |
| Una ráfaga de fallos idénticos llena la tabla y degrada toda consulta | Medio | El conteo acotado impide que el listado degrade, y el índice por código sostiene la consulta de diagnóstico. Lo que no mitiga es el crecimiento del almacenamiento, que corresponde a la retención (D-10, fuera de alcance) |
| Un rechazo de negocio con `409` transporta en `message` algo que en realidad era una validación de formato | Bajo | La restricción acota por estado, no por intención (§2). Queda como convención de quien escribe, verificada de extremo a extremo en §11 |
| La paginación profunda degrada | Medio | Heredado de `RF-SP-011` §10, con la misma respuesta: filtrar |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V11` aplicada) y **API**. Sin nivel de dominio.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-096` | Integración + API | Los fallos se devuelven paginados y ordenados de más reciente a más antiguo |
| `CA-SP-097` | Integración + API | Se provoca un fallo real, se toma el `correlationId` de la respuesta de error y se recupera el evento filtrando por él. Es `FA-001`, el flujo para el que existe la consulta |
| `CA-SP-098` | Integración + API | Cada filtro por separado y todos combinados devuelven solo las filas que cumplen |
| `CA-SP-099` | Integración | Se provoca un fallo no controlado con una excepción cuyo mensaje contiene una ruta y una sentencia, y se comprueba que ni la fila ni la respuesta las contienen. Se verifica **sobre el camino de escritura** |
| `CA-SP-100` | Integración + API | Se provoca un `403` real: **no** deja fila en `audit_error_log` y sí en `audit_security_log`. Además, un `INSERT` directo con `http_status = 403` es rechazado por `ck_audit_error_log_status` |
| `CA-SP-101` | Integración + API | Se provoca un `400` de validación y un `404`: ninguno deja fila. Un `INSERT` directo con cada uno de los cuatro estados prohibidos es rechazado por la restricción |
| `CA-SP-102` | API | Un actor con los otros tres permisos de auditoría, pero sin `audit:read-errors`, recibe `403` y queda la denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Estados admitidos | Integración | `ck_audit_error_log_status` **acepta** `200`, `409`, `422`, `500` y `503`. Sin esta prueba, una restricción demasiado estrecha se descubriría en producción, al rechazar el registro de un fallo legítimo |
| Fallo sin actor | Integración | Un evento emitido por un proceso interno devuelve `actorId` nulo, con el campo presente y no omitido |
| Fallo sin registro concreto | Integración | Un evento con `entityId` nulo se devuelve sin omitir el campo |
| Ráfaga de fallos idénticos | Integración | Mil eventos con el mismo `errorCode` se devuelven como mil filas, no agrupadas ni con contador, y conservan sus instantes distintos |
| Uso efectivo del índice por código | Integración | El `EXPLAIN` de una consulta por `errorCode` más rango muestra el recorrido de `ix_audit_error_log_error_code`, sin paso de ordenamiento |
| Uso efectivo del índice de línea de tiempo | Integración | El `EXPLAIN` del listado sin filtros muestra `ix_audit_error_log_occurred_at` |
| El fallo de escritura no arrastra al negocio | Integración | Con un escritor que intenta registrar un estado prohibido, la operación de negocio **se confirma igual** y queda constancia del fallo en el log de aplicación. Es la prueba del caso límite de `spec.md` §13 |
| Rango semiabierto y fecha sin zona | API | Igual que en `RF-SP-011` §11 |
| Conteo acotado | Integración | Con el techo configurado en 10 y 25 eventos, `totalElements` vale 10, `totalIsExact` es `false` y la página 2 sigue devolviendo contenido |
| Ausencia de agregados | Integración | La traza de sentencias de la petición no contiene ningún `GROUP BY`, y el cuerpo no trae conteos por tipo ni por severidad |
| Número de sentencias por petición | Integración | **Dos** como máximo —datos y conteo— |
| Ausencia de escritura | API | `POST`, `PUT`, `PATCH` y `DELETE` sobre `/api/v1/audit/errors` devuelven `405` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo, incluida esta tabla.
