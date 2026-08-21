# PLAN — `RF-SP-004` Editar rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-004` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 20-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Enfoque

Actualización parcial de dos campos de un agregado que ya existe. La complejidad no está en escribir, sino en tres puntos que deciden si el resultado es correcto:

1. **Distinguir «campo ausente» de «campo vaciado».** Es el problema clásico de una actualización parcial, y la spec exige ambas cosas: enviar solo el nombre debe dejar la descripción intacta (`VAL-001`), y borrar la descripción debe ser posible.
2. **Detectar que no hubo cambio efectivo**, porque `CA-SP-030` exige no registrar evento en ese caso.
3. **Calcular el diff** para la auditoría, ya que `CA-SP-029` exige registrar solo los campos que cambiaron, con su antes y su después.

Los tres se resuelven en la capa `application`, comparando el estado leído con el solicitado antes de escribir.

## 2. Cambios de esquema

**Ninguno.** La tabla `roles`, sus índices únicos parciales y sus restricciones se crean en `V5__create_roles.sql` (`RF-SP-001`). Este requerimiento solo escribe sobre columnas existentes.

**No se añade columna de versión.** La spec resolvió la concurrencia como «gana el último en escribir», de modo que no hay bloqueo optimista. Es una decisión consciente y su consecuencia está registrada: un cambio puede perderse en silencio, y la vía de recuperación es la auditoría, que conserva ambas ediciones.

> Si esa decisión se revirtiera, la columna debe añadirse a `V5` **antes** del primer despliegue. Después obliga a migrar la tabla en uso.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Role` | Modificado | Método `rename(name, description)` que aplica el cambio y devuelve qué campos mutaron. No conoce Spring ni JPA |
| `domain` | `RoleChanges` | Nuevo | Resultado de `rename`: conjunto de campos modificados con su valor anterior y el nuevo. Es lo que alimenta el diff de auditoría |
| `domain` | `RoleRepository` | Sin cambios | Puerto definido en `RF-SP-001` |
| `application` | `UpdateRoleService` | Nuevo | Caso de uso. `@Transactional`, orquesta las verificaciones y emite la auditoría |
| `application` | `UpdateRoleCommand` | Nuevo | Entrada del caso de uso. Los campos opcionales se modelan de forma que distingan ausencia de vaciado (§4) |
| `application` | `AuthenticatedActor` | Modificado | Puerto definido en `RF-SP-001`. Se amplía con los **roles vigentes del actor leídos de la base de datos**, que es lo que `RN-SEG-011` necesita (§5) |
| `application` | `RoleChangeAuditor` | Sin cambios | Puerto definido en `RF-SP-001` |
| `infrastructure` | `JpaRoleRepository` | Modificado | Añade la traducción de la violación de `uq_roles_name` a la excepción de duplicado |
| `infrastructure` | `RoleEntity`, `RoleJpaMapper` | Sin cambios | Definidos en `RF-SP-001` |
| `api` | `RoleController` | Modificado | Añade `PATCH /api/v1/roles/{id}` |
| `api` | `UpdateRoleRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-002`, `VAL-004`) |
| `api` | `RoleResponse` | Sin cambios | Definido en `RF-SP-001`. Se reutiliza tal cual |
| `shared/api` | `Patchable<T>` | Nuevo | Envoltorio de tres estados —ausente, nulo, con valor— y su deserializador. Es lo que hace distinguible «no envié el campo» de «lo envié vacío» (§4). Vive en `shared/api` junto a `PageResponse` porque toda actualización parcial posterior del sistema lo necesita |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/roles/{id}` | Modifica el nombre y la descripción de un rol |

Se usa `PATCH` y no `PUT` porque la operación es explícitamente parcial: `PUT` obligaría a enviar el recurso completo, incluidos los campos que este requerimiento **no** puede modificar, y habría que decidir qué hacer si llegan con valores distintos.

**Petición**

```json
{
  "name": "Supervisor de zona",
  "description": "Supervisa la operación de una zona comercial"
}
```

**Distinción entre ausente y vaciado.** Es la decisión de contrato más delicada:

| Cuerpo | Significado |
|---|---|
| `{ "name": "X" }` | Cambia el nombre. **La descripción no se toca** |
| `{ "description": null }` | **Borra** la descripción. El nombre no se toca |
| `{ "description": "" }` | Equivale a borrarla: se recorta y queda vacía |
| `{}` | Rechazado por `VAL-001` |

Como la ausencia de una propiedad y su presencia con valor nulo son indistinguibles en un objeto Java corriente, los campos opcionales se modelan con `Patchable<T>`, el envoltorio de tres estados que este requerimiento estrena en `shared/api` (§3). Sin él, `{"name":"X"}` borraría la descripción, que es exactamente lo que `CA-SP-024` prohíbe. Se descarta `JsonNullable` de `jackson-databind-nullable`, que resolvería lo mismo a cambio de meter una dependencia de terceros en el contrato de la API por un tipo de tres estados; y se descarta deserializar a `JsonNode` y preguntar qué claves llegaron, que renuncia a Bean Validation sobre los campos y deja la validación de formato escrita a mano.

**Respuesta `200`** — `RoleResponse`, el mismo cuerpo que devuelve el alta en `RF-SP-001`: el rol con sus permisos y su rol padre. **No** se devuelve `RoleDetailResponse` de `RF-SP-003`: arrastraría `assignedUsers`, es decir, una llamada a `USR` en un camino de escritura que hoy no la tiene, con la pregunta añadida de qué responder cuando ese módulo está indisponible (advertido en el plan de `RF-SP-003` §8). El mismo criterio rige en `RF-SP-005` a `RF-SP-008`.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Ningún campo modificable informado, nombre vacío o longitud excedida | `VAL-001`, `VAL-002`, `VAL-004` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `roles:update` | `AUTH-002` |
| `403` | El rol está entre los del actor (`EX-002`) | `RN-SEG-011` |
| `404` | El rol no existe o está eliminado (`EX-004`) | `EX-004` |
| `409` | El rol es de sistema (`EX-001`) | `RN-SEG-012` |
| `409` | Nombre ya usado por otro rol vigente (`EX-003`) | `RN-SEG-001` |
| `500` | Fallo no controlado | `ERR-500` |

Los dos `403` son distintos y no deben fusionarse: el primero lo produce la capa de seguridad compartida **antes** de entrar al caso de uso, y es ella quien emite su evento de `audit_security_log`; el segundo lo produce el caso de uso con el rol ya cargado. Comparten estado HTTP y no `error_code`.

`RN-SEG-011` produce `403` y no `409` porque es una prohibición sobre **quién** ejecuta, no sobre el estado del recurso: el mismo cuerpo enviado por otro actor sería válido.

**Orden de verificación**

1. Formato y obligatoriedad, todas juntas.
2. Rol existente y vigente.
3. Rol no de sistema.
4. El actor no tiene el rol asignado.
5. Unicidad del nombre.

La unicidad va al final porque es la única que consulta otra fila; las anteriores se resuelven con el rol ya leído.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `PATCH /api/v1/roles/{id}` | `roles:update` |

**Verificación de `RN-SEG-011`.** Requiere saber si el rol editado está entre los del actor, y el alcance aprobado es **solo los asignados directamente**.

**Los roles del actor se leen de la base de datos, no del token.** Es la misma decisión que `RF-SP-001` §5 tomó para `RN-SEG-010`, y se toma aquí por la misma razón. El token de acceso transporta los códigos de rol del actor (`security.md` §5.2), pero vive hasta quince minutos: si al actor le asignaron el rol *después* de emitirlo, su token no lo trae y la edición se permitiría. `security.md` §4.5 lo dice de forma explícita —los cambios de rol de un usuario tardan hasta la expiración del token— y esa latencia es tolerable para *conceder* acceso, no para levantar una prohibición. Es una operación de escritura poco frecuente: una consulta indexada por petición es un precio que se paga sin discusión frente a permitir una edición que debía rechazarse.

El puerto `AuthenticatedActor` se amplía con esa lectura (§3), de modo que ni el controlador ni el dominio conocen de dónde sale el dato. La comparación se hace contra el identificador del rol ya cargado en el paso 2.

No se recorre la jerarquía. Es lo que la spec resolvió, y evita un recorrido de ancestros en cada escritura. La misma verificación, con el mismo origen, rige en `RF-SP-005` a `RF-SP-009`.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Edición efectiva | `audit_change_log` | `action = UPDATE`, con `changes` conteniendo **solo** los campos que mutaron, cada uno con su antes y su después |
| Edición efectiva | `audit_security_log` | Evento de modificación de rol, severidad Alta |
| Edición sin cambio | — | **Ningún evento**, en ninguno de los dos registros |
| Rechazo por `EX-001` a `EX-004` | `audit_error_log` | `resource = 'roles'`, `operation` con método y ruta, `error_code` de la tabla de §4, `error_type = 'BUSINESS_RULE'`, `http_status`, `severity` y `message` saneado. Severidad **Alta** para `RN-SEG-011`, que es un intento de eludir una prohibición sobre el propio actor; **Media** para el resto, que son errores de operación |
| Rechazo `400` de formato | — | **No se audita** (`architecture.md` §6.6.4): es ruido de formulario y `request_log` ya lo cubre |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

La convención de auditar los rechazos de regla de negocio es la que fijó `RF-SP-001` §6 y rige en todo el módulo; se explicita aquí para que las tablas de los nueve requerimientos se lean igual.

El diff lo produce el dominio, no la capa de persistencia: `Role.rename` devuelve qué campos mutaron. Un listener de JPA registraría la fila entera y no sabría distinguir un cambio efectivo de una escritura idéntica.

Ejemplo de `changes`:

```json
{ "name": { "before": "Supervisor", "after": "Supervisor de zona" } }
```

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Actualización del rol y su evento en `audit_change_log` | **La misma** (Art. V.14) |
| Evento en `audit_security_log` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |

El evento de seguridad se emite **después** de que la transacción de negocio confirme, igual que en `RF-SP-001`. Emitirlo antes dejaría constancia de una edición que pudo revertirse.

## 8. Impacto sobre otros módulos

Ninguno. La edición no altera permisos ni estado, de modo que **no invalida la caché de resolución** de `security.md` §4.5: el nombre de un rol no interviene en la autorización.

Es la diferencia con `RF-SP-005`, `RF-SP-006` y `RF-SP-007`, que sí deben invalidarla. Conviene no copiar de aquí a esos.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Usar `PUT` con el recurso completo | Obligaría a enviar código, clasificación, estado y rol padre, que este requerimiento no puede modificar, y a decidir qué hacer si llegan con valores distintos: ignorarlos en silencio o rechazar. `PATCH` evita la pregunta |
| Modelar los campos opcionales como `String` corriente | Hace indistinguible «no envié el campo» de «lo envié vacío». Con esa ambigüedad, `{"name":"X"}` borraría la descripción y `CA-SP-024` fallaría |
| Detectar el cambio efectivo comparando en la base de datos | El dominio ya tiene el estado anterior cargado; ir a la base de datos añade una consulta para responder algo que está en memoria. Además dejaría la decisión fuera del dominio, donde no puede probarse sin PostgreSQL (Art. VI.3) |
| Calcular el diff con un listener de JPA | El listener ve la entidad, no la intención: registraría la fila completa y no sabría que una escritura idéntica no es un cambio. `CA-SP-029` y `CA-SP-030` fallarían los dos |
| Verificar `RN-SEG-011` con los códigos de rol que transporta el token | Es más barato y no exige consulta, pero el token vive hasta quince minutos: un rol asignado al actor después de emitirlo no aparece en él, y la edición se permitiría. `security.md` §4.5 declara esa latencia de forma explícita. Sirve para conceder acceso, no para levantar una prohibición |
| Modelar los campos opcionales con `JsonNullable` de `jackson-databind-nullable` | Resuelve lo mismo que `Patchable<T>` a cambio de una dependencia de terceros en el contrato de la API por un tipo de tres estados que cabe en un archivo |
| Añadir bloqueo optimista pese a la decisión | La spec lo resolvió como «gana el último». Introducirlo por iniciativa propia contradiría una decisión tomada y añadiría una columna y un código de error que nadie pidió |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Un cambio se pierde por edición concurrente | Bajo | Aceptado de forma consciente. La auditoría conserva ambas ediciones, de modo que el cambio perdido es reconstruible |
| `Patchable<T>` se serializa mal y borra descripciones | **Alto** | Prueba de API explícita para los cuatro cuerpos de §4, y prueba unitaria del deserializador en `shared/api`. Es el defecto más probable de este requerimiento y el más silencioso, y desde ahora afecta a toda actualización parcial del sistema |
| Se invalida la caché de permisos por copiar de otro requerimiento | Bajo | Anotado en §8. Invalidar de más solo cuesta rendimiento, no corrección |
| La violación de `uq_roles_name` no se traduce y sale como `500` | Medio | El adaptador distingue por nombre de restricción, igual que en `RF-SP-001` |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-023` | Integración | Nombre y descripción quedan modificados en la base de datos |
| `CA-SP-024` | Integración | Código, permisos, estado y rol padre siguen idénticos tras la edición |
| `CA-SP-025` | API | Un rol de sistema devuelve `409` con `RN-SEG-012` |
| `CA-SP-026` | API | Editar un rol propio del actor devuelve `403` con `RN-SEG-011` |
| `CA-SP-027` | Integración | Nombre duplicado devuelve `409`, incluso ante escritura concurrente |
| `CA-SP-028` | Integración | El nombre de un rol eliminado lógicamente se admite |
| `CA-SP-029` | Unitaria + Integración | `Role.rename` devuelve solo los campos mutados; la fila de auditoría contiene solo esos |
| `CA-SP-030` | Integración | Enviar los valores actuales no genera fila en ninguno de los dos registros |
| `CA-SP-151` | API | Un cuerpo con la clasificación es rechazado, no ignorado |
| `CA-SP-152` | API | Editar un rol ancestro del propio devuelve `200` |

Caso límite añadido al aprobar este plan: **al actor se le asigna el rol editado después de emitirse su token**. La edición debe devolver `403` con `RN-SEG-011`, no `200`. Es la única prueba que distingue leer los roles del actor de la base de datos de leerlos del token, y sin ella la decisión de §5 no está verificada.

`CA-SP-151` merece atención: la clasificación no está en el DTO, así que el rechazo debe venir de la configuración que prohíbe propiedades desconocidas. Sin ella el campo se ignoraría en silencio y el criterio pasaría sin comprobar nada.
