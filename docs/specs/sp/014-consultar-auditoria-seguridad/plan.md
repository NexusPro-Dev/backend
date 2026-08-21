# PLAN — `RF-SP-014` Consultar auditoría de seguridad

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-014` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, filtros y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. La mecánica común a los cuatro registros la fijó el plan de [`RF-SP-011`](../011-consultar-auditoria-cambios/plan.md) y **este documento la hereda sin repetirla, con una excepción declarada**: la §6, que allí advertía expresamente que no debía copiarse hacia aquí. Lo propio son tres cosas: el catálogo cerrado de tipos de evento, el evento que la propia consulta emite, y qué hace verificable que un intento de acceso no delate la existencia de una cuenta.

---

## 1. Enfoque

De los cuatro registros, este es el único cuya consulta **modifica el estado del sistema**: leerlo deja una fila en la tabla que se está leyendo (`CA-SP-167`). Todo lo demás se hereda íntegro de `RF-SP-011`: proyección única sin `JOIN`, orden `occurred_at DESC, id DESC`, conteo acotado con `BoundedCount`, rango semiabierto sobre instantes con zona, y ninguna participación de `domain`, porque `spec.md` §5 no declara ninguna regla `RN-…`.

Tres decisiones sostienen el resto, y ninguna está en el camino de la consulta:

1. **`event_type` deja de ser prosa y pasa a ser un catálogo de códigos.** `security.md` §8.1 enumera once eventos en lenguaje natural y `V4__create_audit_logs.sql` declara un `CHECK` «sobre el catálogo cerrado de `security.md` §8.1» sin fijar los literales. Este requerimiento es el primero que **expone ese dominio en un contrato de API** —`spec.md` §6.1 lo declara filtro y `VAL-003` exige que sea cerrado—, de modo que le corresponde fijarlo (§2).
2. **La consulta se audita a sí misma**, y eso obliga a decidir qué ocurre cuando esa escritura falla (§6 y §7).
3. **`CA-SP-109` no se cumple consultando bien, se cumple escribiendo bien**, exactamente igual que `CA-SP-100` y `CA-SP-101` en `RF-SP-013`. Aquí no hay restricción de esquema que pueda garantizarlo, así que se convierte en una obligación declarada sobre `RF-SP-034` (§8) y en una prueba sobre el camino de escritura (§11).

## 2. Cambios de esquema

**Migración:** `V12__create_audit_security_log_indexes.sql`

La tabla `audit_security_log`, sus columnas propias (`security.md` §8.2) y sus cuatro índices mínimos los crea `V4__create_audit_logs.sql` (`RF-SP-001`). Esta migración añade el acceso que falta.

| Tabla | Cambio | Detalle |
|---|---|---|
| `audit_security_log` | Altera (índice) | `ix_audit_security_log_occurred_at` sobre `(occurred_at DESC, id DESC)` |

```sql
CREATE INDEX ix_audit_security_log_occurred_at
    ON audit_security_log (occurred_at DESC, id DESC);
```

**Es el mismo caso de `RF-SP-011` §2**, por las mismas razones y sin repetir el argumento: ninguno de los cuatro índices mínimos de `architecture.md` §6.6.6 sirve al listado sin filtros ordenado por fecha, y el desempate por `id` es gratis y sigue siendo cronológico por ser UUID v7.

**Qué índices no se crean.** `event_type` tiene dieciséis valores, pero su distribución está muy sesgada —`LOGIN_SUCCESS` será con holgura el más numeroso— y filtrar por el valor dominante no acota nada; `severity` tiene tres valores y `outcome` dos. Los tres se aplican barato sobre el conjunto que ya redujeron el rango de fechas, el actor o el usuario afectado. El criterio sigue siendo el de `RF-SP-011` §2: el mínimo que sostiene las consultas reales, porque cada índice de una tabla de auditoría se paga en la transacción que emite el evento.

`FA-001` —la investigación de una cuenta, que es el flujo para el que existe esta consulta— **ya está sostenido**: `ix_audit_security_log_target_user_id` sobre `(target_user_id, occurred_at DESC)` lo crea `V4` y resuelve entero el filtro por usuario afectado más rango, sin paso de ordenamiento.

### Dos cambios que van en `V4`, no en esta migración

Por la razón que `RF-SP-013` §2 ya estableció: nada está desplegado, y declararlo en la migración que crea la tabla cuesta una edición documental, mientras que alterarla después cuesta una migración de datos.

**1. El índice por origen pasa a ser compuesto.** `architecture.md` §6.6.6 declara `(ip_address)` como índice mínimo de las cuatro tablas. En esta —y solo en esta— se declara así:

```sql
CREATE INDEX ix_audit_security_log_ip_address
    ON audit_security_log (ip_address, occurred_at DESC);
```

`security.md` §8.2 explica por qué aquí y no en las otras tres: «un intento de fuerza bruta se reconoce por el origen, no por el nombre de usuario». Esa investigación **siempre** lleva rango de fechas —interesa la ráfaga, no todo lo que esa dirección hizo en dos años—, y el índice compuesto la resuelve ya ordenada. Sigue sirviendo la consulta por IP a secas, porque `ip_address` es su columna de prefijo, de modo que el mínimo de `architecture.md` §6.6.6 se cumple; ese documento pasa a recoger el refinamiento (§8).

**2. El `CHECK` de `event_type` enumera los dieciséis códigos.** `V4` lo declaraba por referencia a `security.md` §8.1, que está escrito en prosa. Un dominio cerrado que ningún literal fija no es un dominio cerrado: cada requerimiento que emitiera un evento inventaría su propia forma de escribirlo —`LOGIN_FAILED`, `login_failure`, `FALLO_LOGIN`— y el filtro de este endpoint devolvería resultados incompletos sin que nada fallara.

| Código | Evento de `security.md` §8.1 | Severidad | `outcome` | Lo emite |
|---|---|---|---|---|
| `LOGIN_SUCCESS` | Inicio de sesión exitoso | `INFORMATIVA` | `SUCCESS` | `RF-SP-034` |
| `LOGIN_FAILURE` | Inicio de sesión fallido | `MEDIA` | `FAILURE` | `RF-SP-034` |
| `ACCOUNT_LOCKED` | Bloqueo de cuenta por intentos fallidos | `ALTA` | `FAILURE` | `RF-SP-034` |
| `REFRESH_TOKEN_REUSE` | Reutilización de un refresh token revocado | `ALTA` | `FAILURE` | `RF-SP-035` |
| `LOGOUT` | Cierre de sesión | `INFORMATIVA` | `SUCCESS` | `RF-SP-036` |
| `AUTHORIZATION_DENIED` | Denegación de autorización (`403`) | `MEDIA` | `FAILURE` | Capa de seguridad |
| `ROLE_CREATED` | Creación de un rol | `ALTA` | `SUCCESS` | `RF-SP-001` |
| `ROLE_UPDATED` | Modificación de un rol | `ALTA` | `SUCCESS` | `RF-SP-004`, `RF-SP-007`, `RF-SP-008` |
| `ROLE_DELETED` | Eliminación de un rol | `ALTA` | `SUCCESS` | `RF-SP-009` |
| `ROLE_PERMISSIONS_CHANGED` | Cambio de permisos de un rol | `ALTA` | `SUCCESS` | `RF-SP-005`, `RF-SP-006` |
| `USER_ROLES_ASSIGNED` | Asignación de roles a un usuario | `ALTA` | `SUCCESS` | `RF-SP-030` |
| `USER_ROLES_REVOKED` | Retiro de roles a un usuario | `ALTA` | `SUCCESS` | `RF-SP-031` |
| `USER_STATUS_CHANGED` | Cambio de estado de un usuario | `ALTA` | `SUCCESS` | `RF-SP-028`, `RF-SP-029` |
| `PASSWORD_CHANGED` | Cambio de contraseña | `ALTA` | `SUCCESS` | `RF-SP-037` |
| `PASSWORD_RESET` | Restablecimiento de contraseña | `ALTA` | `SUCCESS` | `RF-SP-038` |
| `SECURITY_AUDIT_READ` | **Nuevo** — consulta de este registro | `INFORMATIVA` | `SUCCESS` | `RF-SP-014` |

```sql
CONSTRAINT ck_audit_security_log_event_type CHECK (event_type IN (
    'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'REFRESH_TOKEN_REUSE',
    'LOGOUT', 'AUTHORIZATION_DENIED',
    'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'ROLE_PERMISSIONS_CHANGED',
    'USER_ROLES_ASSIGNED', 'USER_ROLES_REVOKED', 'USER_STATUS_CHANGED',
    'PASSWORD_CHANGED', 'PASSWORD_RESET',
    'SECURITY_AUDIT_READ'
))
```

Cuatro decisiones sobre esa tabla:

- **Se desdoblan las filas de `security.md` §8.1 que agrupan operaciones distintas.** «Creación, modificación o eliminación de un rol» es una sola fila allí y son tres códigos aquí; lo mismo con la asignación y el retiro de roles, y con el cambio y el restablecimiento de contraseña. El criterio es la pregunta que se hace al filtrar: un responsable de seguridad busca «quién eliminó roles» o «a quién le restablecieron la contraseña», no «quién tocó roles». Con un código único, esa distinción quedaría en `detail` y exigiría filtrar sobre `jsonb` para responder la pregunta más frecuente del registro.
- **La severidad y el `outcome` no se ligan al tipo de evento en el esquema.** El `CHECK` acota los tres dominios por separado. Ligarlos parecería más seguro y sería falso: la denegación de autorización es `MEDIA` en general, pero la de un endpoint de seguridad podría querer registrarse como `ALTA`, y una restricción cruzada obligaría a alterar el esquema para eso. La correspondencia de la tabla se prueba (§11), no se declara.
- **Se codifican también los eventos de requerimientos aún sin plan** —autenticación y usuarios—. Es el criterio con el que `V3__seed_permissions.sql` sembró los permisos de `users:` (`RF-SP-010` §2): el coste de declararlos hoy es cero, porque un valor que nadie escribe es inerte, y el de no hacerlo es una migración por cada requerimiento que llegue, más el riesgo de que cada uno invente su literal. Lo que **no** se hace es inventar eventos que `security.md` §8.1 no declare.
- **`SECURITY_AUDIT_READ` es el único código nuevo**, y nace de la pregunta 2 de `spec.md` §14. `security.md` §8.1 se enmienda para recogerlo (§8): un catálogo cerrado que no lista un evento que el sistema emite deja de ser el catálogo.

**Recordatorios de la plantilla que no aplican:** esta migración no crea tablas, así que no hay clave primaria UUID v7 que declarar, ni `created_at`/`updated_at` —los registros de auditoría no los llevan a propósito (`RF-SP-001` §2)—, ni integridad declarativa que añadir.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. El reparto es el de `RF-SP-011` §3, con los nombres de este registro y **un componente que los otros tres no tienen**.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `spec.md` §5 no declara ninguna regla `RN-…` |
| `application` | `ListSecurityAuditService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)` para la consulta; emite el evento propio **fuera** de esa transacción (§7) |
| `application` | `SecurityAuditQuery` | Nuevo | Criterios ya validados y normalizados |
| `application` | `SecurityAuditItem` | Nuevo | Modelo de lectura |
| `application` | `SecurityAuditQueryRepository` | Nuevo | Puerto de consulta |
| `application` | `SecurityEventType` | Nuevo | Enum cerrado con los dieciséis códigos de §2 |
| `application` | `SecuritySeverity` | Nuevo | Enum cerrado `INFORMATIVA`, `MEDIA`, `ALTA` |
| `application` | `SecurityOutcome` | Nuevo | Enum cerrado `SUCCESS`, `FAILURE` |
| `infrastructure` | `JpaSecurityAuditQueryRepository` | Nuevo | Adaptador. Predicado, proyección y conteo acotado |
| `infrastructure` | `AuditSecurityLogEntity` | Nuevo | Mapeo JPA. Solo como metamodelo |
| `api` | `AuditController` | Modificado | Añade `GET /api/v1/audit/security` con **su** permiso |
| `api` | `ListSecurityAuditRequest` | Nuevo | Parámetros de consulta con Bean Validation (`VAL-001` a `VAL-003`) |
| `api` | `SecurityAuditItemResponse` | Nuevo | DTO de salida de cada fila |
| `shared/audit` | `SecurityAuditWriter` | **Modificado** | Gana el evento `SECURITY_AUDIT_READ`. Es el mismo puerto que ya usan `RF-SP-001` y la capa de seguridad; no se crea uno propio |
| `shared/api` | `PageResponse<T>`, `BoundedCount` | Sin cambios | Definidos en `RF-SP-011` |

Dos decisiones de reparto:

**`SecurityEventType` es un enum de `application`, no de `shared`.** Lo escriben requerimientos de todo el módulo y lo lee solo este, pero eso no lo hace transversal: vive donde vive el resto del vocabulario de este registro, y `shared/audit` lo recibe como valor. Subirlo a `shared` obligaría a que la capa compartida conociera un dominio de negocio para poder escribirlo, que es justamente la dependencia que `architecture.md` §5.3 evita.

**El evento propio se emite por el mismo puerto que usa la capa de seguridad**, no por un camino nuevo. Que la consulta escriba en la tabla que lee no la convierte en escritora de auditoría: sigue habiendo un solo componente que escribe en `audit_security_log`, y este requerimiento es uno más de sus clientes. Es lo que impide que la garantía de `readOnly = true` (§7) se pierda por tener dos caminos de escritura.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/audit/security` | Listado paginado de eventos de autenticación, autorización y cambio de privilegios |

**Petición**

```
GET /api/v1/audit/security?page=0&size=20
                          &eventType=LOGIN_FAILURE
                          &severity=ALTA
                          &outcome=FAILURE
                          &actorId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d99
                          &targetUserId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d77
                          &ipAddress=190.85.12.7
                          &from=2026-08-01T00:00:00Z
                          &to=2026-09-01T00:00:00Z
                          &correlationId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9daa
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `page`, `size` | entero | `0`, `20` | Igual que en `RF-SP-011` §4 |
| `eventType` | enum | — | Uno de los dieciséis de §2. Otro → `VAL-003` |
| `severity` | enum | — | `INFORMATIVA`, `MEDIA` o `ALTA`. Otro → `VAL-003` |
| `outcome` | enum | — | `SUCCESS` o `FAILURE`. Otro → `VAL-003` |
| `actorId` | UUID | — | Quien ejecutó la acción. No se valida que exista |
| `targetUserId` | UUID | — | Sobre quién recayó. El filtro de `FA-001` |
| `ipAddress` | texto | — | Dirección IPv4 o IPv6 **exacta**, no rango ni prefijo. Malformada → `VAL-003` |
| `from` / `to` | instante ISO-8601 | — | Rango semiabierto `[from, to)`. `from` posterior a `to` → `VAL-001` |
| `correlationId` | UUID | — | Enlace con la petición |

- **`ipAddress` se compara por igualdad sobre la columna `inet`, no como texto.** La columna es `inet` (`architecture.md` §6.6.1) y el parámetro se convierte antes de comparar; así `190.85.012.7` y `190.85.12.7` son la misma dirección y no dos cadenas distintas. Un valor que no sea una dirección válida produce `VAL-003`, no una búsqueda vacía: quien filtra por origen y escribe mal la dirección debe enterarse.
- **No se admite filtrar por rango ni por prefijo de red.** `spec.md` §6.1 pide «filtro por origen», y una máscara CIDR es una pregunta distinta —«todo lo que vino de esta red»— que además no aprovecharía el índice. Si la investigación de fuerza bruta llegara a necesitarla, es un filtro aditivo y una decisión propia.
- **No hay `sort`**, por lo dicho en `RF-SP-011` §4.
- **No se filtra por el contenido de `detail`.** Es `jsonb` sin índice y su forma varía con el tipo de evento; ofrecerlo como filtro convertiría un contrato estable en uno que depende de qué escribió cada requerimiento. La consecuencia se declara abajo y se anota en §10.

**Respuesta `200`**

```json
{
  "content": [
    {
      "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01",
      "occurredAt": "2026-08-21T14:32:11.482Z",
      "actorId": null,
      "eventType": "LOGIN_FAILURE",
      "severity": "MEDIA",
      "outcome": "FAILURE",
      "targetUserId": null,
      "detail": { "attemptedUsername": "wbonilla" },
      "correlationId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9daa",
      "ipAddress": "190.85.12.7",
      "userAgent": "Mozilla/5.0 …"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 4128,
  "totalPages": 207,
  "totalIsExact": true
}
```

- **`detail` viaja como objeto JSON, no como cadena**, por lo mismo que `changes` en `RF-SP-011` §4: la columna es `jsonb` y serializarla como texto obligaría al cliente a un segundo `parse`. **Este endpoint no la interpreta ni la uniforma**: su forma la fija el requerimiento que emitió el evento.
- **`actorId` nulo en un evento de autenticación significa que no había identidad probada todavía**, no que se perdiera el dato (`spec.md` §13). En un `LOGIN_FAILURE` es siempre nulo, y entonces la dirección de red es el único identificador disponible: es la razón del índice compuesto de §2.
- **`targetUserId` es la columna que distingue «quién lo hizo» de «a quién se lo hicieron»** (`security.md` §8.2). Se devuelve como `null` sin omitirse cuando el evento no recae sobre nadie —la creación de un rol, por ejemplo, que lleva el rol afectado en `detail`—.
- **`correlationId` e `ipAddress` son nulos a la vez o ninguno lo es**, por `ck_audit_security_log_origen`.
- **No se devuelve el nombre del actor ni el del usuario afectado**, por lo dicho en `RF-SP-011` §3: el identificador es el dato probatorio y el nombre es una foto del momento de la consulta, no del evento.
- **Ninguna credencial aparece en ninguna forma** (`CA-SP-106`). No hay columna que pueda contenerla, y `detail` está sujeta al enmascaramiento de `security.md` §7.3. La garantía vive **al escribir** (Art. IV.8, `security.md` §8.3) y esta consulta no la reproduce, por el mismo argumento de `RF-SP-011` §10 y `RF-SP-013` §4: dos saneadores divergen, y el de lectura no protegería a quien consulte la base directamente.

### El evento de acceso fallido no dice si la cuenta existía

`CA-SP-109` y el primer caso límite de `spec.md` §13 lo exigen: un intento con un usuario inexistente debe registrarse **sin revelar si el usuario existía**. Eso no se resuelve en la consulta, que devuelve lo que hay; se resuelve fijando ahora la forma del evento que emitirá `RF-SP-034`:

| Campo | En `LOGIN_FAILURE` | Por qué |
|---|---|---|
| `actor_id` | Siempre `NULL` | No hay identidad probada |
| `target_user_id` | **Siempre `NULL`**, exista o no la cuenta | Es el único campo que podría delatarla |
| `detail` | `{ "attemptedUsername": "…" }`, tal como lo escribió el cliente | Es lo que permite investigar una ráfaga |
| `severity`, `outcome` | `MEDIA`, `FAILURE`, invariables | Una severidad distinta según el caso lo delataría igual |

Así, dos eventos —uno contra una cuenta real y otro contra una inventada— son **indistinguibles campo por campo** salvo por el texto que escribió el cliente. Eso es lo que hace verificable a `CA-SP-109` (§11), y es una obligación que este plan impone a `RF-SP-034` (§8).

**Consecuencia que hay que aceptar, y que se declara aquí porque afecta a `FA-001`.** Filtrar por `targetUserId` devuelve la actividad de privilegio de esa cuenta —bloqueos, cambios de rol, restablecimientos de contraseña, cierres de sesión— pero **no sus intentos fallidos**, que no llevan el campo. Para verlos, el auditor filtra `eventType=LOGIN_FAILURE` con el rango de fechas y lee el identificador intentado en el `detail` de cada fila; es una lectura sobre un conjunto ya acotado por tipo y por fecha, no un recorrido de la tabla. Se elige esta asimetría y no la contraria porque el criterio de aceptación es explícito y el flujo alternativo no lo es. Anotado en §10, con la corrección si llegara a ser un uso habitual.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | `from` posterior a `to` (`EX-001`) | `VAL-001` | `from` |
| `400` | `page` negativa o `size` fuera de `[1, 100]` (`EX-002`) | `VAL-002` | `page` o `size` |
| `400` | `eventType`, `severity` u `outcome` fuera de su dominio | `VAL-003` | El parámetro afectado |
| `400` | `ipAddress` no es una dirección válida | `VAL-003` | `ipAddress` |
| `400` | Un UUID o un instante malformado | `VAL-003` | El parámetro afectado |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `audit:read-security` | `AUTH-002` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

Sin `404` ni `422`, por lo dicho en `RF-SP-011` §4. Los `400` se evalúan juntos y se devuelven juntos en `errors`.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/audit/security` | `audit:read-security` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`).
- **Es el permiso más restringido del módulo, y el único que `ADMIN` no tiene.** `V7__seed_system_roles.sql` da a `ADMIN` el catálogo completo **salvo este** (`RF-SP-001` §2): la lectura de la auditoría de seguridad es la reserva propia de `SUPERADMIN`, precisamente porque este es el registro donde quedan los intentos de escalada de privilegios. Un `ADMIN` que pudiera leerlo podría comprobar si su propio intento quedó registrado.
- `CA-SP-110` verifica lo contrario: un actor con los otros tres permisos de auditoría, pero sin este, recibe `403`. Compartir `AuditController` no lo relaja, porque el permiso se declara por método.
- **No hay filtrado por alcance de datos.** Quien audita el control de acceso lo audita entero.
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y **deja su propia fila en esta misma tabla** con `event_type = 'AUTHORIZATION_DENIED'`. Es la peculiaridad de este endpoint: un intento denegado de leer el registro de seguridad queda **en el registro de seguridad**, visible para quien sí pueda leerlo.

## 6. Auditoría

**Esta sección no se hereda de `RF-SP-011` §6**, que lo advirtió expresamente. Aquí la consulta exitosa **sí** emite evento.

| Operación | Registro | Contenido relevante |
|---|---|---|
| **Consulta exitosa** | `audit_security_log` | `event_type = 'SECURITY_AUDIT_READ'`, `severity = 'INFORMATIVA'`, `outcome = 'SUCCESS'`, `actor_id` de quien consulta, `target_user_id = NULL`, `detail` con los filtros aplicados y la página solicitada |
| Rechazo `400` | — | **No se audita**: son validaciones de formato (`architecture.md` §6.6.4) |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'audit_security_log'`, `operation = 'GET /api/v1/audit/security'`, `error_code = 'ERR-500'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera información de negocio |
| — | `audit_deletion_log` | No aplica |

Cuatro decisiones:

- **Los filtros van en `detail` como objeto, no como texto libre.** `CA-SP-167` exige registrar «los filtros usados», y guardarlos estructurados —`{"eventType":"LOGIN_FAILURE","from":"…","to":"…","page":0,"size":20}`— permite responder después «quién estuvo mirando los intentos fallidos de agosto» sin analizar una cadena. Los filtros ausentes **se omiten** en lugar de escribirse como `null`: una consulta sin filtros deja un `detail` que dice exactamente eso, que se listó todo.
- **El evento se emite solo si la consulta tuvo éxito.** Un `400` no llega a consultar nada y no hay lectura que registrar; un `403` ya deja su propio evento, de otro tipo. Registrar los tres casos con el mismo código impediría distinguir quién leyó de quién lo intentó.
- **Que el evento aparezca en el registro que se está consultando es correcto y deliberado** (`spec.md` §13). No hay recursión posible: la escritura no consulta, de modo que una consulta produce exactamente un evento, y ese evento solo aparecerá en la **siguiente** consulta, nunca en la que lo generó, porque se emite después de cerrar la transacción de lectura (§7). Se distingue de la actividad de acceso por su `event_type`, que es exactamente lo que el caso límite pide.
- **Un fallo al escribir este evento no hace fallar la consulta.** Se desarrolla en §7.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Consulta de datos y conteo acotado | **Una sola**, `@Transactional(readOnly = true)` sobre `ListSecurityAuditService` |
| `audit_security_log` de la consulta exitosa | **Independiente**, `REQUIRES_NEW`, emitida **después** de cerrar la transacción de lectura |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

Dos matices que este requerimiento estrena y que ningún otro de los cuatro tiene:

**Por qué el evento va fuera de la transacción de lectura y no dentro.** `readOnly = true` marca la transacción como de solo lectura en PostgreSQL: cualquier escritura dentro de ella falla. Esa garantía es la que impide que un defecto escriba en un registro de auditoría desde un camino de consulta (`RF-SP-011` §7), y **no se relaja aquí**. El evento se emite después, en su propia transacción, que es además lo que el Art. V.14 exige para los eventos de seguridad. La consecuencia deseable es que la fila que la consulta genera nunca puede aparecer en su propio resultado.

**Qué ocurre si esa escritura falla.** La consulta ya respondió; el fallo no se propaga y se registra como `ERROR` en el log de aplicación con su `correlation_id`, como en `RF-SP-001` §7. Es la aplicación del Art. XV.7: un fallo al registrar no debe provocar el fallo de la operación, y la excepción que ese artículo contempla —«operaciones donde la auditoría constituya un requisito legal declarado en la especificación»— no está declarada en `spec.md`. La alternativa contraria está en §9 y el riesgo residual en §10; la petición queda de todos modos en `request_log` con su actor.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `RF-SP-001` | Su `V4__create_audit_logs.sql` gana los dieciséis literales de `ck_audit_security_log_event_type` y el índice compuesto por origen. **Además, su evento de alta de rol pasa a llamarse `ROLE_CREATED`**, que hasta ahora era «`event_type` de creación de rol» sin literal. El plan queda anotado |
| `security.md` | §8.1 incorpora la columna de código y la fila de `SECURITY_AUDIT_READ`, para que documento y esquema no diverjan (Art. XII.3). §8.2 recoge que el índice por origen de esta tabla es compuesto |
| `architecture.md` | §6.6.6 declara que el índice mínimo `(ip_address)` se refina a `(ip_address, occurred_at DESC)` en `audit_security_log` |
| **`RF-SP-034`** | Obligación declarada: `LOGIN_FAILURE` lleva `target_user_id` **siempre nulo**, exista o no la cuenta, y el identificador intentado en `detail`. Es lo que hace cumplible `CA-SP-109`, y no puede decidirse allí sin contradecir este plan |
| `RF-SP-035` a `RF-SP-038` | Emiten los códigos que §2 les asigna. Ninguno inventa el suyo |
| `RF-SP-004` a `RF-SP-009`, `RF-SP-028` a `RF-SP-031` | Ídem. `ROLE_UPDATED` cubre la edición, el cambio de estado y el cambio de padre: los tres son «modificación de un rol» en `security.md` §8.1, y qué cambió se responde en `audit_change_log`, no aquí |
| `shared/audit` | Recibe el enum de tipos de evento como valor y gana un cliente más. Sin cambios estructurales |
| `shared/api` | Ninguno. `PageResponse<T>` y `BoundedCount` se reutilizan tal cual |
| Retención (D-10) | `spec.md` §14, pregunta 4, deja fuera que este registro se conserve más tiempo que los demás. Este plan tampoco lo resuelve: la consulta se comporta igual con cualquier retención. Lo que sí aporta es un argumento para D-10, porque el Art. XV.8 ya prohíbe purgarlo sin decisión documentada |
| Lista de proxies confiables (D-21) | `spec.md` §14, pregunta 3, la deja abierta. El filtro por `ipAddress` de §4 vale exactamente lo que valga esa lista: si la dirección se toma de una cabecera no validada, el filtro devuelve lo que un atacante quiera que devuelva (Art. V.15). No bloquea este requerimiento y se anota en §10 |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Dejar `event_type` sin literales fijados, como estaba en `V4` | Un dominio cerrado que ningún literal fija no es cerrado: cada requerimiento inventaría su forma de escribirlo y el filtro de este endpoint devolvería resultados incompletos sin que nada fallara |
| Un solo código para «creación, modificación o eliminación de un rol», como agrupa `security.md` §8.1 | La pregunta que se hace al filtrar es «quién eliminó roles», no «quién tocó roles». Con un código único, la distinción quedaría en `detail` y exigiría filtrar sobre `jsonb` para la pregunta más frecuente del registro |
| Enumerar solo los códigos de los requerimientos ya planificados | Obligaría a una migración por cada requerimiento que llegue, y mientras tanto cada uno inventaría su literal. Mismo criterio con el que `V3` sembró los permisos de `users:` |
| Ligar en el esquema cada `event_type` con su severidad | Parece más seguro y es falso: la denegación de autorización sobre un endpoint de seguridad podría querer registrarse como `ALTA`, y una restricción cruzada obligaría a alterar el esquema para eso |
| No auditar la consulta, como los otros tres registros | `spec.md` §14, pregunta 2, lo resolvió al revés: en una auditoría de acceso, quién revisó los accesos ajenos es en sí mismo información de seguridad. `CA-SP-167` lo exige |
| Emitir el evento **dentro** de la transacción de lectura | Obligaría a renunciar a `readOnly = true`, que es la garantía de que ningún defecto escriba en un registro de auditoría desde un camino de consulta. Se pierde mucho más de lo que se gana, y el Art. V.14 exige de todos modos transacción independiente |
| Hacer que un fallo al escribir el evento haga fallar la consulta | Negaría al responsable de seguridad la lectura del registro por no poder escribir su rastro, que es el peor momento posible para dejarlo ciego. El Art. XV.7 lo admitiría solo si la especificación declarara la auditoría como requisito legal, y no lo hace |
| Registrar también con `SECURITY_AUDIT_READ` las consultas rechazadas con `400` | No hay lectura que registrar: la petición no llegó a consultar nada. Y mezclarlas impediría distinguir quién leyó de quién lo intentó |
| Rellenar `target_user_id` en `LOGIN_FAILURE` cuando la cuenta existe | Es exactamente lo que `CA-SP-109` prohíbe: la presencia del campo revelaría que la cuenta existía. Se acepta a cambio la asimetría de `FA-001` declarada en §4 |
| Filtrar por prefijo de red (CIDR) además de por dirección exacta | `spec.md` §6.1 pide filtro por origen, no por red. Responde una pregunta distinta y no aprovecharía el índice; es aditivo y puede llegar como requerimiento propio |
| Ofrecer filtro sobre el contenido de `detail` | Es `jsonb` sin índice y su forma varía con el tipo de evento: convertiría un contrato estable en uno que depende de qué escribió cada requerimiento |
| Índice sobre `event_type`, `severity` u `outcome` | Dieciséis valores muy sesgados, tres y dos. Filtrar por el valor dominante no acota nada, y cada índice se paga en la transacción que emite el evento |
| Alertar automáticamente ante severidad `ALTA` | `spec.md` §14, pregunta 1: exige decidir a quién se avisa, por qué canal y con qué umbral antes de que el ruido lo vuelva inútil. Es observabilidad con reglas propias |
| Devolver el nombre del actor y del usuario afectado | `spec.md` §6.2 no lo pide, y el nombre es una foto del momento de la consulta, no del evento. Mismo criterio de `RF-SP-011` §3 |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Un requerimiento futuro emite un evento con un literal que el `CHECK` no admite, y la operación de negocio falla por ello | Medio | La escritura va siempre en transacción independiente `REQUIRES_NEW` (Art. V.14), de modo que su fallo **no revierte la operación de negocio**; queda en el log de aplicación con su `correlation_id`. Es el comportamiento correcto: un evento mal formado no debe tumbar una petición, pero tampoco guardarse. La tabla de §2 es la referencia y `security.md` §8.1 la recoge |
| El fallo al escribir `SECURITY_AUDIT_READ` deja una lectura sin rastro y `CA-SP-167` se incumple en silencio | Medio | Se registra como `ERROR` en el log de aplicación, y la petición queda igualmente en `request_log` con su actor y su `correlation_id`, de modo que el rastro no desaparece del todo. La ausencia de eventos de este tipo se monitorea junto con el resto (`RF-SP-001` §10) |
| La dirección de red no es confiable porque D-21 sigue abierta, y el filtro por origen devuelve lo que un atacante quiera | **Alto** | El Art. V.15 exige tomarla de la cadena de proxies declarada como confiable. Mientras D-21 no se cierre, el caso límite de `spec.md` §13 ya advierte que una dirección falsificable no sirve como evidencia. **Debe cerrarse antes del primer despliegue expuesto a Internet**: este es el registro donde más importa |
| Los intentos fallidos contra una cuenta no se localizan filtrando por `targetUserId` | Medio | Consecuencia aceptada de `CA-SP-109` (§4). Se localizan por `eventType` más rango, sobre un conjunto acotado. Si se volviera un uso habitual, la corrección es un índice GIN sobre `detail` y un filtro propio, y es un requerimiento aparte |
| Una credencial llega a `detail` y esta consulta la publica | **Alto** | Prohibido por el Art. IV.8 y por `security.md` §8.3, y garantizado por el enmascaramiento **al escribir** (`security.md` §7.3). `CA-SP-106` se verifica sobre el camino de escritura (§11). Esta consulta no sanea de nuevo, por el argumento de `RF-SP-011` §10 |
| `LOGIN_SUCCESS` domina el volumen y desplaza los eventos que importan | Bajo | El filtro por `eventType` y por `severity` los aísla, y el conteo acotado impide que el volumen degrade la respuesta. Lo que no mitiga es el crecimiento del almacenamiento, que corresponde a la retención (D-10) |
| La consulta de eventos de seguridad por rol depende de un filtro sobre `detail` | Bajo | Ya anotado en `RF-SP-001` §10. Este plan lo confirma y no lo resuelve: `audit_security_log` no tiene columna para la entidad afectada, solo `target_user_id`. Si llegara a ser habitual, se resuelve con un índice GIN, no añadiendo columnas al esquema de `security.md` §8.2 |
| La paginación profunda degrada | Medio | Heredado de `RF-SP-011` §10, con la misma respuesta: filtrar |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V12` aplicada) y **API**. Sin nivel de dominio.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-103` | Integración + API | Los eventos se devuelven paginados y ordenados de más reciente a más antiguo |
| `CA-SP-104` | Integración + API | Filtrando por `targetUserId` y rango se recupera la actividad de privilegio de esa cuenta: bloqueos, cambios de rol y de estado, cierres de sesión. Es `FA-001` |
| `CA-SP-105` | Integración + API | Cada filtro por separado y todos combinados devuelven solo las filas que cumplen, incluido `ipAddress` sobre la columna `inet` |
| `CA-SP-106` | Integración | Se ejecutan un inicio de sesión fallido y un restablecimiento de contraseña reales, y se comprueba que ni la fila ni la respuesta contienen la credencial en ninguna forma. Se verifica **sobre el camino de escritura** |
| `CA-SP-107` | Integración + API | Un intento fallido real aparece con `outcome = 'FAILURE'` y `severity = 'MEDIA'`; el bloqueo posterior, con `ACCOUNT_LOCKED` y `severity = 'ALTA'` |
| `CA-SP-108` | Integración + API | Se provoca un `403` real: deja fila **aquí** con `AUTHORIZATION_DENIED` y **ninguna** en `audit_error_log`, que además la rechazaría por `ck_audit_error_log_status` |
| `CA-SP-109` | Integración | Dos intentos fallidos, uno contra una cuenta existente y otro contra una inventada, producen filas **indistinguibles campo por campo** salvo por `detail.attemptedUsername`: ambas con `actor_id` y `target_user_id` nulos, mismo tipo, misma severidad y mismo `outcome` |
| `CA-SP-110` | API | Un actor con los otros tres permisos de auditoría, pero sin `audit:read-security`, recibe `403`, no obtiene evento alguno y queda la denegación en esta misma tabla |
| `CA-SP-167` | Integración + API | Tras una consulta con filtros existe una fila con `SECURITY_AUDIT_READ`, el `actor_id` de quien consultó, su instante y los filtros aplicados en `detail`; una consulta sin filtros deja `detail` sin claves de filtro, no con nulos |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| El evento de la consulta no aparece en su propia respuesta | Integración | Dos consultas consecutivas: la primera no contiene su propio evento; la segunda sí contiene el de la primera. Es lo que verifica que se emite fuera de la transacción de lectura |
| Fallo al escribir el evento de la consulta | Integración | Con el escritor forzado a fallar, la consulta **responde `200` igual** y queda constancia del fallo en el log de aplicación |
| Catálogo de tipos de evento | Integración | `ck_audit_security_log_event_type` acepta los dieciséis literales de §2 y rechaza cualquier otro, incluidas variantes de capitalización |
| Correspondencia de severidad | Integración | Cada evento que el sistema emite lleva la severidad y el `outcome` que §2 le asigna. Sin esta prueba, la correspondencia queda solo en la documentación |
| Reutilización de refresh token revocado | Integración | Se registra con `REFRESH_TOKEN_REUSE` y `severity = 'ALTA'`, y se localiza filtrando por severidad. Es el caso límite de mayor gravedad de `spec.md` §13 |
| Ráfaga de intentos fallidos | Integración | Cien intentos producen cien eventos, no uno agrupado; el bloqueo produce uno propio, y todos se recuperan filtrando por `ipAddress` y rango |
| Uso efectivo del índice por origen | Integración | El `EXPLAIN` de una consulta por `ipAddress` más rango muestra el recorrido de `ix_audit_security_log_ip_address`, sin paso de ordenamiento |
| Uso efectivo del índice de línea de tiempo | Integración | El `EXPLAIN` del listado sin filtros muestra `ix_audit_security_log_occurred_at` |
| Dirección de red malformada | API | `ipAddress=190.85.12` devuelve `400` con `VAL-003`, **no** una colección vacía |
| Formas equivalentes de una dirección | Integración | `190.85.012.7` y `190.85.12.7` devuelven el mismo resultado: la comparación es sobre `inet`, no sobre texto |
| Actor sin autenticar | Integración | Un evento con `actor_id` nulo se devuelve con el campo presente y no omitido, y la dirección de red sigue permitiendo localizarlo |
| Rango semiabierto y fecha sin zona | API | Igual que en `RF-SP-011` §11 |
| Conteo acotado | Integración | Con el techo configurado en 10 y 25 eventos, `totalElements` vale 10, `totalIsExact` es `false` y la página 2 sigue devolviendo contenido |
| Número de sentencias por petición | Integración | **Dos** para la consulta —datos y conteo— más **una** de escritura del evento, en su propia transacción |
| Ausencia de escritura por la API | API | `POST`, `PUT`, `PATCH` y `DELETE` sobre `/api/v1/audit/security` devuelven `405`. El único camino de escritura a esta tabla es `shared/audit` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo, incluida esta tabla.
