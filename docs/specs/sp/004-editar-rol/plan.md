# PLAN — `RF-SP-004` Editar rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-004` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 20-08-2026 |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobado por | — |
| Fecha de aprobación | — |

---

## 1. Enfoque

Actualización parcial de dos campos de un agregado que ya existe. La complejidad no está en escribir, sino en tres puntos que deciden si el resultado es correcto:

1. **Distinguir «campo ausente» de «campo vaciado».** Es el problema clásico de una actualización parcial, y la spec exige ambas cosas: enviar solo el nombre debe dejar la descripción intacta (`VAL-001`), y borrar la descripción debe ser posible.
2. **Detectar que no hubo cambio efectivo**, porque `CA-SP-030` exige no registrar evento en ese caso.
3. **Calcular el diff** para la auditoría, ya que `CA-SP-029` exige registrar solo los campos que cambiaron, con su antes y su después.

Los tres se resuelven en la capa `application`, comparando el estado leído con el solicitado antes de escribir.

## 2. Cambios de esquema

**Ninguno.** La tabla `roles`, sus índices únicos parciales y sus restricciones se crean en `V4__create_roles.sql` (`RF-SP-001`). Este requerimiento solo escribe sobre columnas existentes.

**No se añade columna de versión.** La spec resolvió la concurrencia como «gana el último en escribir», de modo que no hay bloqueo optimista. Es una decisión consciente y su consecuencia está registrada: un cambio puede perderse en silencio, y la vía de recuperación es la auditoría, que conserva ambas ediciones.

> Si esa decisión se revirtiera, la columna debe añadirse a `V4` **antes** del primer despliegue. Después obliga a migrar la tabla en uso.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Role` | Modificado | Método `rename(name, description)` que aplica el cambio y devuelve qué campos mutaron. No conoce Spring ni JPA |
| `domain` | `RoleChanges` | Nuevo | Resultado de `rename`: conjunto de campos modificados con su valor anterior y el nuevo. Es lo que alimenta el diff de auditoría |
| `domain` | `RoleRepository` | Sin cambios | Puerto definido en `RF-SP-001` |
| `application` | `UpdateRoleService` | Nuevo | Caso de uso. `@Transactional`, orquesta las verificaciones y emite la auditoría |
| `application` | `UpdateRoleCommand` | Nuevo | Entrada del caso de uso. Los campos opcionales se modelan de forma que distingan ausencia de vaciado (§4) |
| `application` | `AuthenticatedActor` | Sin cambios | Puerto definido en `RF-SP-001`. Aquí se usa para `RN-SEG-011` |
| `application` | `RoleChangeAuditor` | Sin cambios | Puerto definido en `RF-SP-001` |
| `infrastructure` | `JpaRoleRepository` | Modificado | Añade la traducción de la violación de `uq_roles_name` a la excepción de duplicado |
| `infrastructure` | `RoleEntity`, `RoleJpaMapper` | Sin cambios | Definidos en `RF-SP-001` |
| `api` | `RoleController` | Modificado | Añade `PATCH /api/v1/roles/{id}` |
| `api` | `UpdateRoleRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-002`, `VAL-004`) |
| `api` | `RoleResponse` | Sin cambios | Definido en `RF-SP-001`. Se reutiliza tal cual |

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

Como la ausencia de una propiedad y su presencia con valor nulo son indistinguibles en un objeto Java corriente, los campos opcionales se modelan con un envoltorio que conserva esa diferencia. Sin él, `{"name":"X"}` borraría la descripción, que es exactamente lo que `CA-SP-024` prohíbe.

**Respuesta `200`** — el rol actualizado, con el mismo cuerpo que devuelve `RF-SP-003` en su versión resumida.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Ningún campo modificable informado, nombre vacío o longitud excedida | `VAL-001`, `VAL-002`, `VAL-004` |
| `403` | El actor no posee `roles:update`, o el rol está entre los suyos (`EX-002`) | `RN-SEG-011` |
| `404` | El rol no existe o está eliminado (`EX-004`) | `EX-004` |
| `409` | Nombre ya usado por otro rol vigente (`EX-003`) | `RN-SEG-001` |
| `409` | El rol es de sistema (`EX-001`) | `RN-SEG-012` |

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

El token de acceso transporta los **códigos** de rol del actor (`security.md` §5.2), mientras que la petición identifica el rol por su identificador. La comparación se resuelve con el código del rol ya leído en el paso 2, sin consulta adicional: el rol se cargó de todas formas, y su código viene con él.

No se recorre la jerarquía. Es lo que la spec resolvió, y evita un recorrido de ancestros en cada escritura.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Edición efectiva | `audit_change_log` | `action = UPDATE`, con `changes` conteniendo **solo** los campos que mutaron, cada uno con su antes y su después |
| Edición efectiva | `audit_security_log` | Evento de modificación de rol, severidad Alta |
| Edición sin cambio | — | **Ningún evento**, en ninguno de los dos registros |

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
| Verificar `RN-SEG-011` consultando los roles del actor en la base de datos | El token ya transporta sus códigos de rol y el rol editado ya está cargado. La consulta no aportaría nada salvo latencia, y quedaría desalineada con la resolución de permisos, que también parte del token |
| Añadir bloqueo optimista pese a la decisión | La spec lo resolvió como «gana el último». Introducirlo por iniciativa propia contradiría una decisión tomada y añadiría una columna y un código de error que nadie pidió |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Un cambio se pierde por edición concurrente | Bajo | Aceptado de forma consciente. La auditoría conserva ambas ediciones, de modo que el cambio perdido es reconstruible |
| El envoltorio de campos opcionales se serializa mal y borra descripciones | **Alto** | Prueba de API explícita para los cuatro cuerpos de §4. Es el defecto más probable de este requerimiento y el más silencioso |
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

`CA-SP-151` merece atención: la clasificación no está en el DTO, así que el rechazo debe venir de la configuración que prohíbe propiedades desconocidas. Sin ella el campo se ignoraría en silencio y el criterio pasaría sin comprobar nada.
