# PLAN — `RF-SP-006` Revocar permisos de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-006` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobado por | — |
| Fecha de aprobación | — |

---

## 1. Enfoque

Es la operación simétrica de `RF-SP-005`, pero **no** es su imagen especular. Al conceder, el conjunto de permisos del rol crece y ningún descendiente puede quedar fuera de su cota. Al revocar, el conjunto **encoge**, y cualquier rol hijo que declarase ese permiso quedaría de pronto excediendo a su padre.

De ahí la verificación que este requerimiento tiene y el anterior no: **mirar hacia abajo** antes de escribir.

Tres decisiones dan forma al plan:

1. **Se rechaza, no se revoca en cascada.** Una cascada silenciosa quitaría privilegios que nadie pidió quitar, y el efecto se descubriría cuando alguien dejara de poder trabajar.
2. **Un hijo inactivo bloquea igual que uno activo.** El invariante de contención vale siempre, no solo mientras el rol concede permisos. Si no fuera así, reactivar ese rol produciría uno que excede a su padre sin que ninguna operación hubiera violado `RN-SEG-003`.
3. **La eliminación es física** y se audita como eliminación de asociación, sin motivo (Art. V.13, excepción de asociaciones).

## 2. Cambios de esquema

**Ninguno.** `role_permissions` se crea en `V5__create_role_permissions.sql` (`RF-SP-001`).

Conviene comprobar, sin embargo, que existe índice sobre `roles(parent_role_id)`: la verificación de descendencia consulta los hijos de un rol en cada revocación, y sin ese índice es un recorrido completo de la tabla. El plan de `RF-SP-003` ya lo asume para contar hijos; si no estuviera en `V4`, hay que añadirlo allí antes del primer despliegue.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Role` | Modificado | Método `revokePermissions(Set<PermissionCode>, List<Role> children)`. Contiene `RN-SEG-005` y devuelve qué permisos se retiraron realmente |
| `domain` | `PermissionRevocationBlocked` | Nuevo | Resultado del rechazo: qué roles hijos lo impiden y con qué permisos. Es lo que permite a la API decir **cuáles** |
| `application` | `RevokeRolePermissionsService` | Nuevo | Caso de uso. `@Transactional`, carga la descendencia directa y emite la auditoría |
| `application` | `RoleRepository` | Modificado | Añade la búsqueda de hijos directos de un rol, **sin filtrar por estado** |
| `application` | `RoleDeletionAuditor` | Nuevo | Puerto hacia `shared/audit` para el registro de eliminación |
| `application` | `RolePermissionCacheInvalidator` | Sin cambios | Puerto definido en `RF-SP-005` |
| `infrastructure` | `JpaRoleRepository` | Modificado | Elimina físicamente las filas de `role_permissions` |
| `api` | `RoleController` | Modificado | Añade `DELETE /api/v1/roles/{id}/permissions` |
| `api` | `RevokePermissionsRequest` | Nuevo | DTO de entrada. **No lleva motivo** |
| `api` | `RoleResponse` | Sin cambios | Definido en `RF-SP-001` |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `DELETE` | `/api/v1/roles/{id}/permissions` | Retira permisos del rol |

**Petición** — un `DELETE` con cuerpo, porque hay que indicar qué permisos se retiran:

```json
{
  "permissionIds": [
    "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01"
  ]
}
```

Aplica aquí la advertencia ya registrada en `architecture.md` §6.6.3: el cuerpo en `DELETE` es admisible en OpenAPI 3.1 y Spring lo soporta, pero RFC 9110 no le define semántica y un intermediario podría descartarlo. **Debe probarse contra el proxy real antes de darlo por bueno.** Si resultara inviable, la alternativa es `POST /roles/{id}/permissions/revocations`, menos elegante pero sin ambigüedad de transporte.

**No se solicita motivo.** Es una asociación, no una entidad de negocio.

**Respuesta `200`** — el rol con su lista de permisos actualizada.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Lista vacía, identificador malformado o más de 100 elementos | `VAL-001`, `VAL-002` |
| `403` | Sin `roles:update`, o el rol está entre los del actor (`EX-003`) | `RN-SEG-011` |
| `404` | El rol no existe o está eliminado (`EX-004`) | `EX-004` |
| `409` | Un rol hijo declara el permiso (`EX-001`) | `RN-SEG-005` |
| `409` | El rol es de sistema (`EX-002`) | `RN-SEG-012` |

El cuerpo del `409` por `RN-SEG-005` **debe enumerar qué roles lo impiden y con qué permisos**. Sin ese detalle el actor no sabe qué corregir, y como no hay cascada, corregirlo a mano es el único camino que le queda.

El límite de 100 se hereda de `RF-SP-005` por coherencia, aunque la spec no lo exija: dos operaciones sobre el mismo recurso con límites distintos serían una trampa.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `DELETE /api/v1/roles/{id}/permissions` | `roles:update` |

Retirar permisos exige el mismo permiso que concederlos. No se define uno propio: quien puede ampliar un rol puede reducirlo, y separarlos crearía un rol capaz de conceder pero no de corregirse.

`RN-SEG-011` se verifica igual que en `RF-SP-004` y `RF-SP-005`: contra los códigos de rol del token, solo los asignados directamente.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Permisos retirados | `audit_deletion_log` | `deletion_type = ASSOCIATION`, `reason` **vacío**, estado conservado con los identificadores **y los códigos** de rol y permiso |
| Permisos retirados | `audit_security_log` | Cambio de permisos de un rol, severidad **Alta** |
| Ninguno retirado | — | **Ningún evento**: si ninguno estaba asociado, nada cambió |

El estado conservado lleva los códigos legibles y no solo los identificadores. Es lo que resolvió la spec, y la razón es la misma que sostiene el Art. V.13: dentro de un año, resolver dos referencias puede ser imposible si el rol o el permiso ya no existen.

```json
{
  "roleId": "018f3a2b-…", "roleCode": "CONTABILIDAD",
  "permissionId": "018f3a2c-…", "permissionCode": "roles:create"
}
```

**Una fila de auditoría por permiso retirado**, a diferencia de `RF-SP-005`. Aquí cada fila del registro de eliminación documenta la desaparición de una asociación concreta, y el estado conservado es el de esa asociación: agruparlas dejaría un evento cuyo `snapshot` no correspondería a ningún registro eliminado.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Eliminación de las filas y sus eventos en `audit_deletion_log` | **La misma** (Art. V.14) |
| Invalidación de la caché de permisos | Tras el commit, nunca antes |
| Evento en `audit_security_log` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |

## 8. Impacto sobre otros módulos

- **`shared/security`**: se invalida la resolución de permisos del rol, con el mismo puerto que `RF-SP-005`. Aquí es más urgente que allí: al conceder, una caché obsoleta retrasa un privilegio nuevo; al revocar, **mantiene vivo uno que ya se quitó**.
- **Los roles hijos no se modifican.** O bloquean la operación, o no declaraban el permiso y nada les afecta.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Revocar en cascada sobre los descendientes | Quitaría privilegios que nadie pidió quitar, y el efecto se descubriría cuando alguien dejara de poder trabajar. Además cada rol afectado necesitaría su propio evento de auditoría y su verificación, que es un requerimiento distinto |
| Ignorar los roles hijos inactivos | Rompería el invariante de contención mientras el rol está inactivo, y reactivarlo produciría un rol que excede a su padre sin que ninguna operación hubiera violado `RN-SEG-003`. Habría que añadir la verificación a `RF-SP-007`, desplazando el problema en vez de resolverlo |
| Verificar toda la descendencia, no solo los hijos directos | Innecesario por transitividad: si el hijo no declara el permiso, ningún nieto puede declararlo, porque el nieto está acotado por el hijo. Recorrer el árbol añadiría consultas sin cambiar el resultado |
| Eliminación lógica de la asociación | Una asociación no tiene ciclo de vida propio: existe o no existe. Un borrado lógico obligaría a filtrar por él en cada resolución de permisos, que es el camino más caliente del sistema |
| Exigir motivo, como en cualquier otra eliminación | El Art. V.13 fue enmendado precisamente para esto: en una asociación el porqué ya está en el propio evento —qué se desvinculó, de qué, quién y cuándo—, y un texto libre se rellenaría con ruido |
| Un permiso propio, `roles:revoke` | Separaría conceder de retirar, creando un rol capaz de ampliar pero no de corregir lo que amplió. El riesgo está en conceder, no en quitar |
| Agrupar todos los permisos retirados en un solo evento | El estado conservado de ese evento no correspondería a ningún registro eliminado concreto, y el registro de eliminación existe justamente para conservar lo que desapareció |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La caché conserva un permiso ya revocado | **Alto** | Invalidación enganchada al commit. Es más grave que en `RF-SP-005`: mantiene vivo un privilegio retirado |
| El cuerpo del `DELETE` lo descarta un intermediario | Medio | Probar contra el proxy real antes de dar el contrato por bueno. Alternativa ya identificada en §4 |
| Falta índice en `roles(parent_role_id)` | Medio | Verificar en `V4`. Sin él, cada revocación recorre la tabla de roles entera |
| El rechazo no dice qué roles bloquean | Medio | Sin cascada, es la única información con la que el actor puede avanzar. Cubierto por `CA-SP-042` |
| Se copia de `RF-SP-005` y se omite la verificación de descendencia | **Alto** | Es la diferencia esencial entre ambos requerimientos, señalada en §1 y en el plan de `RF-SP-005` |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-041` | Integración | Las filas desaparecen de `role_permissions` |
| `CA-SP-042` | Unitaria + API | El dominio identifica los roles bloqueantes; la API devuelve `409` citándolos con sus permisos |
| `CA-SP-043` | Integración | Tras un rechazo, ningún rol descendiente perdió permisos |
| `CA-SP-044` | Integración | Retirar un permiso que el rol no declaraba no produce error |
| `CA-SP-045` | Integración | La fila de `audit_deletion_log` tiene `reason` vacío y no falla la restricción del esquema |
| `CA-SP-046` | Integración | La fila desaparece de la tabla; no queda marcada |
| `CA-SP-047` | API | Rol de sistema y rol propio devuelven el error correspondiente |
| `CA-SP-048` | Integración | Una resolución de permisos posterior ya no concede el permiso retirado |
| `CA-SP-155` | Integración | Con un rol hijo **inactivo** que declara el permiso, la revocación se rechaza |
| `CA-SP-156` | Integración | El estado conservado contiene los códigos de rol y permiso, no solo los identificadores |

`CA-SP-155` y `CA-SP-048` son las dos pruebas que no deben faltar. La primera protege el invariante de contención en el estado donde es más fácil olvidarlo; la segunda es la que distingue un permiso revocado de uno que sigue concediéndose desde la caché.
