# PLAN — `RF-SP-006` Revocar permisos de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-006` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Enfoque

Es la operación simétrica de `RF-SP-005`, pero **no** es su imagen especular. Al conceder, el conjunto de permisos del rol crece y ningún descendiente puede quedar fuera de su cota. Al revocar, el conjunto **encoge**, y cualquier rol hijo que declarase ese permiso quedaría de pronto excediendo a su padre.

De ahí la verificación que este requerimiento tiene y el anterior no: **mirar hacia abajo** antes de escribir.

Tres decisiones dan forma al plan:

1. **Se rechaza, no se revoca en cascada.** Una cascada silenciosa quitaría privilegios que nadie pidió quitar, y el efecto se descubriría cuando alguien dejara de poder trabajar.
2. **Un hijo inactivo bloquea igual que uno activo.** El invariante de contención vale siempre, no solo mientras el rol concede permisos. Si no fuera así, reactivar ese rol produciría uno que excede a su padre sin que ninguna operación hubiera violado `RN-SEG-003`.
3. **La eliminación es física** y se audita como eliminación de asociación, sin motivo (Art. V.13, excepción de asociaciones).

## 2. Cambios de esquema

**Ninguno.** `role_permissions` se crea en `V6__create_role_permissions.sql` (`RF-SP-001`).

La verificación de descendencia consulta los hijos de un rol en cada revocación, y se apoya en `ix_roles_parent_role_id`, que `V5__create_roles.sql` sí crea (plan de `RF-SP-001` §2, verificado el 21-08-2026). Sin ese índice cada revocación sería un recorrido completo de la tabla de roles.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Role` | Modificado | Método `revokePermissions(Set<PermissionCode>, List<Role> children)`. Contiene `RN-SEG-005` y devuelve qué permisos se retiraron realmente |
| `domain` | `PermissionRevocationBlocked` | Nuevo | Resultado del rechazo: qué roles hijos lo impiden y con qué permisos. Es lo que permite a la API decir **cuáles** |
| `domain` | `RoleRepository` | Modificado | Puerto definido en `RF-SP-001`, que vive en `domain` porque devuelve el agregado. Añade la búsqueda de hijos directos de un rol, **sin filtrar por estado** |
| `application` | `RevokeRolePermissionsService` | Nuevo | Caso de uso. `@Transactional`, carga la descendencia directa y emite la auditoría |
| `application` | `RoleDeletionAuditor` | Nuevo | Puerto hacia `shared/audit` para el registro de eliminación |
| `application` | `RolePermissionCacheInvalidator` | Sin cambios | Puerto definido en `RF-SP-005` |
| `infrastructure` | `JpaRoleRepository` | Modificado | Elimina físicamente las filas de `role_permissions` |
| `api` | `RoleController` | Modificado | Añade `POST /api/v1/roles/{id}/permissions/revocations` |
| `api` | `RevokePermissionsRequest` | Nuevo | DTO de entrada. **No lleva motivo** |
| `api` | `RoleResponse` | Sin cambios | Definido en `RF-SP-001` |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/roles/{id}/permissions/revocations` | Retira permisos del rol |

**Petición** — el cuerpo indica qué permisos se retiran:

```json
{
  "permissionIds": [
    "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01"
  ]
}
```

**Por qué `POST` sobre una subruta y no `DELETE` con cuerpo.** El borrador de este plan proponía `DELETE /api/v1/roles/{id}/permissions` con la lista en el cuerpo, que es la forma más expresiva. Se descartó el 21-08-2026: el cuerpo en `DELETE` es admisible en OpenAPI 3.1 y Spring lo soporta, pero RFC 9110 no le define semántica y un intermediario puede descartarlo sin avisar —la advertencia ya estaba registrada en `architecture.md` §6.6.3—. La revocación llegaría entonces sin permisos, y el fallo sería silencioso. La alternativa de pasarlos por *query string* tampoco sirve: con cien UUID la URL supera los límites habituales de longitud de proxies y servidores.

`revocations` es un subrecurso: cada petición **crea** una revocación sobre el rol, que es exactamente lo que `POST` significa. El precio es un contrato menos elegante como REST; la ganancia es que no depende de qué haya delante en cada entorno y no deja una prueba pendiente antes de implementar.

**No se solicita motivo.** Es una asociación, no una entidad de negocio.

**Respuesta `200`** — `RoleResponse`, definido en `RF-SP-001`: el rol con su lista de permisos actualizada y su rol padre. No se devuelve `RoleDetailResponse`, que arrastraría una llamada a `USR` a un camino de escritura (`RF-SP-004` §4).

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Lista vacía o identificador malformado | `VAL-001`, `VAL-002` |
| `400` | Más de 100 permisos en la petición | `VAL-004` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `roles:update` | `AUTH-002` |
| `403` | El rol está entre los del actor (`EX-003`) | `RN-SEG-011` |
| `404` | El rol no existe o está eliminado (`EX-004`) | `EX-004` |
| `409` | El rol es de sistema (`EX-002`) | `RN-SEG-012` |
| `409` | Un rol hijo declara el permiso (`EX-001`) | `RN-SEG-005` |
| `500` | Fallo no controlado | `ERR-500` |

Los dos `403` son distintos y no deben fusionarse: el primero lo produce la capa de seguridad compartida antes de entrar al caso de uso; el segundo, el caso de uso con el rol ya cargado.

El cuerpo del `409` por `RN-SEG-005` **debe enumerar qué roles lo impiden y con qué permisos**. Sin ese detalle el actor no sabe qué corregir, y como no hay cascada, corregirlo a mano es el único camino que le queda.

El límite de 100 es el mismo que en `RF-SP-005`: dos operaciones sobre el mismo recurso con límites distintos serían una trampa. El borrador lo aplicaba sin respaldo en la especificación; el 21-08-2026 se añadió a `spec.md` como `VAL-004`, con su mensaje y su criterio `CA-SP-174` (Art. I.7).

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/roles/{id}/permissions/revocations` | `roles:update` |

Retirar permisos exige el mismo permiso que concederlos. No se define uno propio: quien puede ampliar un rol puede reducirlo, y separarlos crearía un rol capaz de conceder pero no de corregirse.

`RN-SEG-011` se verifica igual que en `RF-SP-004` y `RF-SP-005`: contra los **roles vigentes del actor leídos de la base de datos**, no contra los códigos del token, y solo sobre los asignados directamente. La justificación está en `RF-SP-004` §5.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Permisos retirados | `audit_deletion_log` | `deletion_type = ASSOCIATION`, `reason` **vacío**, estado conservado con los identificadores **y los códigos** de rol y permiso |
| Permisos retirados | `audit_security_log` | Cambio de permisos de un rol, severidad **Alta** |
| Ninguno retirado | — | **Ningún evento**: si ninguno estaba asociado, nada cambió |
| Rechazo por `EX-001` a `EX-004` | `audit_error_log` | `resource = 'roles'`, `operation` con método y ruta, `error_code` de la tabla de §4, `error_type = 'BUSINESS_RULE'`, `http_status`, `severity` y `message` saneado. Severidad **Alta** para `RN-SEG-005` y `RN-SEG-011`; **Media** para el resto |
| Rechazo `400` de formato | — | **No se audita** (`architecture.md` §6.6.4) |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

`RN-SEG-005` se audita con severidad Alta aunque no sea una escalada: un intento de retirar un permiso que un rol hijo declara indica que alguien está reorganizando privilegios sin ver la jerarquía completa, y esa es una señal que conviene poder buscar.

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
| ~~El cuerpo del `DELETE` lo descarta un intermediario~~ | — | **Resuelto el 21-08-2026:** el contrato pasó a `POST /roles/{id}/permissions/revocations` (§4). Sin cuerpo en `DELETE` no hay ambigüedad de transporte ni prueba pendiente contra el proxy |
| ~~Falta índice en `roles(parent_role_id)`~~ | — | **Verificado el 21-08-2026:** `ix_roles_parent_role_id` sí se crea en `V5__create_roles.sql` (plan de `RF-SP-001` §2). No hay nada que añadir |
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
| `CA-SP-174` | API | Una petición con 101 permisos devuelve `400` con `VAL-004` |

`CA-SP-155` y `CA-SP-048` son las dos pruebas que no deben faltar. La primera protege el invariante de contención en el estado donde es más fácil olvidarlo; la segunda es la que distingue un permiso revocado de uno que sigue concediéndose desde la caché.
