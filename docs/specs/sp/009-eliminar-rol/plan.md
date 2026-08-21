# PLAN — `RF-SP-009` Eliminar rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-009` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Enfoque

Una eliminación lógica con dos verificaciones que miran hacia fuera del rol: no puede tener roles hijos vigentes, y no puede tener usuarios asignados. La segunda cruza el límite del módulo, porque ese dato pertenece a `USR`.

Eso convierte lo que parece un `UPDATE` de una columna en la operación con más superficie de fallo del submódulo:

1. **La verificación de usuarios depende de otro módulo** que todavía no existe, y una respuesta indisponible **no puede tratarse como cero**.
2. **La verificación y la escritura tienen que ser atómicas frente a una asignación concurrente**, o el rol se elimina mientras alguien se lo asigna.
3. **El motivo es obligatorio** y debe rechazarse *antes* de ejecutar nada (Art. V.13).

No hay operación de restauración. El borrado lógico existe para que la auditoría resuelva qué rol era, no como papelera.

## 2. Cambios de esquema

**Ninguno.** La columna `deleted_at` y los índices únicos parciales que dependen de ella se crean en `V5__create_roles.sql` (`RF-SP-001`).

Sí hay un ajuste **fuera** de este requerimiento: `architecture.md` §6.6.3 relajó `ck_deletion_reason` de exigir diez caracteres a exigir solo contenido no vacío. Ese cambio va en la migración de las tablas de auditoría, `V4__create_audit_logs.sql`, y debe aplicarse **antes** del primer despliegue: modificar una restricción con la tabla en uso es mucho más caro.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Role` | Modificado | Método `delete(String motivo)`, que valida el motivo y marca el borrado. Devuelve el estado previo para el registro de eliminación |
| `domain` | `RoleRepository` | Modificado | Puerto definido en `RF-SP-001`, que vive en `domain` porque devuelve el agregado. Añade el bloqueo exclusivo de la fila del rol y la búsqueda de hijos vigentes |
| `application` | `DeleteRoleService` | Nuevo | Caso de uso. `@Transactional`, toma el bloqueo, verifica hijos y usuarios, y emite la auditoría |
| `application` | `RoleAssignmentCounter` | Sin cambios | Puerto hacia `USR` definido en `RF-SP-003`. Aquí es donde su tipo de retorno importa (§5) |
| `application` | `RoleDeletionAuditor` | Sin cambios | Puerto definido en `RF-SP-006` |
| `application` | `RolePermissionCacheInvalidator` | Sin cambios | Puerto definido en `RF-SP-005`. El rol eliminado deja de conceder, y su entrada en la caché queda obsoleta (§8) |
| `infrastructure` | `JpaRoleRepository` | Modificado | Implementa el bloqueo exclusivo sobre la fila |
| `api` | `RoleController` | Modificado | Añade `POST /api/v1/roles/{id}/deletion` |
| `api` | `DeleteRoleRequest` | Nuevo | DTO con el motivo. Único campo |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/roles/{id}/deletion` | Elimina lógicamente el rol |

**Petición** — el motivo es obligatorio y viaja en el cuerpo:

```json
{ "reason": "Rol duplicado tras la fusión de las áreas de cobranza." }
```

**Por qué `POST` sobre un subrecurso y no `DELETE` con cuerpo.** El borrador proponía `DELETE /api/v1/roles/{id}` con el motivo en el cuerpo. Se descartó el 21-08-2026, por la misma razón que en `RF-SP-006` y con más motivo: RFC 9110 no define semántica para el cuerpo de un `DELETE` y un intermediario puede descartarlo. Allí perder el cuerpo dejaba una revocación sin permisos; **aquí convierte la petición en un rechazo por motivo ausente**, un error que el actor no puede entender ni corregir, porque él sí escribió el motivo. Tampoco sirve pasarlo por *query string*: el motivo acabaría escrito en los logs de acceso de proxies y servidores, donde no debe estar.

`deletion` es un subrecurso: la petición **crea** la eliminación del rol, con su motivo, que es lo que el Art. V.13 exige registrar.

**Respuesta `204`** — sin cuerpo.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Motivo ausente o vacío tras recortar (`EX-001`) | `VAL-001`, `VAL-002` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `roles:delete` | `AUTH-002` |
| `403` | El rol está entre los del actor (`EX-005`) | `RN-SEG-011` |
| `404` | El rol no existe o ya está eliminado (`EX-006`) | `EX-006` |
| `409` | El rol es de sistema o es el rol raíz (`EX-004`) | `RN-SEG-012`, `RN-SEG-007` |
| `409` | El rol tiene roles hijos vigentes (`EX-002`) | `RN-SEG-008` |
| `409` | El rol tiene usuarios asignados (`EX-003`) | `RN-SEG-008` |
| `500` | Fallo no controlado | `ERR-500` |
| `503` | No se pudo determinar si tiene usuarios (§5) | `INT-001` |

`EX-006` se añadió a `spec.md` el 21-08-2026, al aprobar este plan: la especificación no declaraba la excepción del rol inexistente y este documento la referenciaba con `EX-004`, que es el rol de sistema o el rol raíz —el mismo código que la fila de `409`— (Art. I.7).

Los dos `403` son distintos y no deben fusionarse: el primero lo produce la capa de seguridad compartida antes de entrar al caso de uso; el segundo, el caso de uso con el rol ya cargado.

`INT-001` pertenece a la serie de fallos de integración que `architecture.md` §7.3 abrió el 21-08-2026 y que estrena `RF-SP-003`. Allí acompaña a una respuesta degradada con `200`; aquí, a un rechazo con `503`, porque una eliminación no puede degradarse (§5).

Los cuerpos de `409` por `RN-SEG-008` deben decir **cuántos** y **cuáles**: los roles hijos por su código, y los usuarios por su número. Sin ese detalle el actor no sabe si le faltan dos reasignaciones o doscientas.

**Orden de verificación**

1. Motivo presente y con contenido. **Primero de todo**, porque el Art. V.13 exige rechazar la eliminación sin motivo *antes* de ejecutarla.
2. Rol existente, vigente, no de sistema, no raíz.
3. El actor no tiene el rol asignado.
4. **Bloqueo exclusivo de la fila del rol.**
5. Sin roles hijos vigentes.
6. Sin usuarios asignados.

## 5. Autorización y coordinación con `USR`

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/roles/{id}/deletion` | `roles:delete` |

**El bloqueo de fila** resuelve la carrera entre eliminar y asignar. La eliminación toma un bloqueo exclusivo sobre la fila de `roles`; la asignación en `USR` debe tomar uno compartido sobre la misma fila antes de insertar en `user_roles`. Así ambas se serializan sobre ese registro y sobre nada más.

Es un contrato que `SP` **publica y `USR` debe respetar**, y no puede garantizarse desde aquí. Debe quedar escrito en la interfaz que `SP` expone, junto al conteo de `RF-SP-003`: una asignación que no tome el bloqueo puede insertar mientras la eliminación decide, y el rol quedaría eliminado con usuarios apuntando a él.

**El conteo indisponible no es cero.** El puerto `RoleAssignmentCounter` de `RF-SP-003` devuelve `Known` o `Unavailable`, y este requerimiento es la razón por la que se diseñó así:

| Respuesta de `USR` | Qué hace la eliminación |
|---|---|
| `Known(0)` | Continúa |
| `Known(n > 0)` | Rechaza con `409` |
| `Unavailable(...)` | **Rechaza con `503`** |

Degradar aquí como hace `RF-SP-003` sería incorrecto: allí se devuelve una consulta incompleta, aquí se borraría un rol sin saber a quién afecta. Un `orElse(0L)` en este punto incumple `RN-SEG-008` sin que nada falle, y el tipo sellado existe precisamente para que ese descuido no compile.

**Mientras `USR` no exista**, el adaptador nulo responde `Unavailable(NOT_IMPLEMENTED)` y la eliminación **siempre se rechaza** con `503`. Es lo correcto —sin poder saber si un rol tiene usuarios, no se puede eliminar— y se aceptó de forma explícita el 21-08-2026 con su consecuencia declarada: **`RF-SP-009` queda implementado, probado y desplegado, pero inoperante hasta que exista `USR`**. Se prefiere a las dos alternativas: reordenar la implementación para construirlo después de `USR` rompería el orden aprobado en `requirements/sp.md` §6.1 por un requerimiento que sí puede escribirse ya; y permitir el borrado cuando el adaptador nulo está activo incumpliría `RN-SEG-008` en cuanto `USR` existiera y alguien desplegara mal la configuración, que es justo el fallo que `nexus.usr.assignment-counter.required` (`RF-SP-003` §10) está puesto para evitar.

**`RN-SEG-011`** se verifica contra los **roles vigentes del actor leídos de la base de datos**, no contra los códigos del token, igual que en `RF-SP-004` a `RF-SP-008` (`RF-SP-004` §5).

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Eliminación | `audit_deletion_log` | `deletion_type = LOGICAL`, `reason` declarado por el actor, estado conservado con el rol completo: código, nombre, descripción, clasificación, rol padre, estado y sus permisos declarados |
| Eliminación | `audit_security_log` | Eliminación de rol, severidad **Alta** |
| Rechazo por `EX-002` a `EX-006` | `audit_error_log` | `resource = 'roles'`, `operation` con método y ruta, `error_code` de la tabla de §4, `error_type = 'BUSINESS_RULE'`, `http_status`, `severity` y `message` saneado. Severidad **Alta** para `RN-SEG-008` y `RN-SEG-011`; **Media** para el resto |
| Rechazo `503` por conteo indisponible | `audit_error_log` | `error_code = 'INT-001'`, `error_type = 'INTEGRATION'`, `http_status = 503`, `severity = 'ALTA'`. **Solo cuando `USR` existe y falla**: que no esté construido es un hecho de despliegue y no se audita, con el mismo criterio de `RF-SP-003` §6 |
| Rechazo `400` de formato | — | **No se audita** (`architecture.md` §6.6.4). El motivo ausente entra aquí: es validación, no regla incumplida |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

El estado conservado incluye **los permisos declarados**, no solo los campos de la fila. Es lo que permite responder qué concedía ese rol, que es la pregunta que se hace al revisar por qué alguien tenía cierto acceso. Sin ellos, las filas de `role_permissions` siguen existiendo pero apuntan a un rol que ya no aparece en ninguna consulta.

Pasa por el mismo enmascaramiento que cualquier contenido persistido (Art. XV.5), aunque un rol no contenga datos sensibles hoy.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Bloqueo de la fila del rol | Adquirido dentro de la transacción, liberado al terminarla |
| Marca de borrado y su evento en `audit_deletion_log` | **La misma** (Art. V.14) |
| Consulta a `USR` | **Fuera** de la transacción de escritura |
| Invalidación de la caché de permisos | Tras el commit |
| Evento en `audit_security_log` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |

Hay una tensión que conviene declarar: el conteo de usuarios se consulta fuera de la transacción para que un fallo de `USR` no la marque para revertir, pero el bloqueo debe estar tomado antes de decidir. La secuencia es adquirir el bloqueo, consultar a `USR`, y confirmar o revertir según la respuesta. El bloqueo se mantiene durante la llamada externa, de modo que **una indisponibilidad de `USR` retiene la fila** el tiempo del intento. Es aceptable porque el bloqueo afecta a una sola fila y la operación es rara, pero exige un tiempo de espera acotado en la llamada.

## 8. Impacto sobre otros módulos

- **`USR`** debe tomar el bloqueo compartido al asignar un rol. Es el contrato que hace correcta esta operación y no puede imponerse desde `SP`.
- **`shared/security`**: se invalida la resolución del rol eliminado, que deja de conceder.
- **`RF-SP-001`** puede reutilizar el código y el nombre liberados, gracias a los índices únicos parciales. Es la contrapartida deliberada de no tener restauración.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Ofrecer restauración del rol eliminado | Al eliminarlo se libera su código y su nombre, de modo que otro rol pudo tomarlos; y su rol padre pudo cambiar o desaparecer. Restaurar exigiría revalidar código, nombre, padre vigente y contención, y decidir qué hacer ante cada colisión: es otro requerimiento, no una variante de este |
| Tratar el conteo indisponible como cero | Elimina un rol sin saber a quién afecta, incumpliendo `RN-SEG-008` sin que nada falle. El tipo sellado del puerto existe para impedir precisamente este descuido |
| Ignorar a los usuarios inactivos | Reactivar a esa persona la dejaría con un rol inexistente. Mismo criterio que en `RF-SP-006` con los roles hijos inactivos |
| Eliminación física | Perdería el estado del rol para la auditoría, y las filas de `audit_change_log` anteriores quedarían apuntando a un identificador irresoluble |
| Reasignar automáticamente los usuarios a otro rol | Concede accesos que nadie decidió conceder. Reasignar es una operación de `USR` y debe hacerse antes, de forma explícita |
| Aceptar la carrera y detectarla luego | Dejaría usuarios apuntando a un rol eliminado hasta que una revisión lo detecte, y alguien tendría que decidir entonces qué hacer con ellos |
| Reutilizar el bloqueo global de `RF-SP-008` | Serializaría también las asignaciones de rol a usuario, que son mucho más frecuentes que las reubicaciones y no tienen por qué esperarse entre sí |
| Usar `DELETE` con el motivo en el cuerpo | RFC 9110 no define semántica para el cuerpo de un `DELETE` y un intermediario puede descartarlo sin avisar; el actor recibiría entonces un rechazo por motivo ausente que él sí escribió, y no tendría forma de corregirlo. Pasarlo por *query string* lo dejaría escrito en los logs de acceso |
| Exigir diez caracteres al motivo | Se decidió no imponer esa fricción. La contrapartida, declarada en `architecture.md` §6.6.3, es que la garantía queda formal: obliga a escribir algo, no a que informe |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `USR` asigna el rol sin tomar el bloqueo compartido | **Alto** | Contrato publicado junto a la interfaz de conteo. No es verificable desde `SP`, de modo que debe probarse al implementar `USR` |
| El conteo indisponible se trata como cero | **Crítico** | El tipo sellado obliga a distinguirlo. `CA-SP-067` debe incluir un caso con `USR` sin responder |
| El bloqueo retiene la fila durante una indisponibilidad de `USR` | Medio | Tiempo de espera acotado en la llamada externa |
| ~~El cuerpo del `DELETE` lo descarta un intermediario~~ | — | **Resuelto el 21-08-2026:** el contrato pasó a `POST /roles/{id}/deletion` (§4). Sin cuerpo en `DELETE` no hay ambigüedad de transporte ni prueba pendiente contra el proxy |
| Ninguna eliminación puede completarse hasta que exista `USR` | Medio | **Aceptado el 21-08-2026** con su consecuencia declarada (§5): el endpoint responde `503` a toda petición mientras el adaptador nulo esté activo. Es preferible a borrar sin saber a quién afecta. El frontend debe mostrar ese `503` como «no disponible todavía», no como fallo del sistema |
| El estado conservado omite los permisos declarados | Medio | Sin ellos no puede responderse qué concedía el rol eliminado, que es la pregunta habitual en una revisión de accesos |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-064` | Integración | `deleted_at` queda informado y el rol desaparece de las consultas |
| `CA-SP-065` | API | Motivo ausente o en blanco devuelve `400` **sin haber tocado la fila** |
| `CA-SP-066` | Integración | Con un rol hijo vigente, devuelve `409` citándolo |
| `CA-SP-067` | Integración | Con usuarios asignados devuelve `409`; con `USR` indisponible devuelve `503`, **nunca** elimina |
| `CA-SP-068` | API | Rol raíz y rol de sistema devuelven `409` |
| `CA-SP-069` | Integración | Tras eliminar, `RF-SP-001` admite crear un rol con el mismo código y nombre |
| `CA-SP-070` | Integración | El rol no aparece en el listado por defecto |
| `CA-SP-071` | Integración | La fila de `audit_deletion_log` conserva motivo, campos del rol y permisos declarados |
| `CA-SP-072` | Integración | Fila en `audit_security_log` con severidad Alta |
| `CA-SP-163` | Integración | Un usuario **inactivo** con el rol asignado provoca `409` |
| `CA-SP-164` | API | No existe endpoint de restauración |
| `CA-SP-165` | **Integración concurrente** | Eliminar y asignar a la vez: una de las dos falla y no queda usuario apuntando a un rol eliminado |
| `CA-SP-176` | API | Eliminar un rol inexistente o ya eliminado devuelve `404` con `EX-006`, distinto del `409` con `RN-SEG-012` de un rol de sistema |

`CA-SP-067` es la prueba que protege el invariante más importante de este requerimiento. Debe incluir explícitamente el caso de `USR` indisponible, porque es el único en el que una implementación descuidada elimina un rol que sí tenía usuarios.
