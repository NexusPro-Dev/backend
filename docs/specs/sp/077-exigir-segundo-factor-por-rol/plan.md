# PLAN — `RF-SP-077` Exigir el segundo factor a los portadores de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-077` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 06-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**La forma de `ChangeRoleStatusService` (`RF-SP-007`)**: un `PATCH` sobre una subruta del rol, que no escribe nada si el valor no cambia. Lo que lo separa de aquel es **cuál de las guardas de `RoleWriteAccess` aplica**: `RN-SEG-011` —el actor no porta el rol— sí; `RN-SEG-012` —los roles de sistema no se tocan— **no**, como en `GrantRolePermissionsService`. Se reutiliza la misma combinación de guardas que la asignación de permisos, sin inventar una tercera.

**La raíz la defienden dos sitios**: el servicio responde `VAL-002` con su mensaje, y `ck_roles_root_requires_mfa` de `V75` impide que una escritura que se salte el servicio deje la raíz sin obligación.

**El efecto no lo produce este requerimiento**: la marca la leen el inicio de sesión y la renovación al calcular `mer` (`RF-SP-072`). Aquí solo se escribe.

**Las dos cifras de la respuesta** se cuentan con una sentencia sobre `user_roles` ⋈ `users` (activos, no eliminados), con un `EXISTS` sobre `user_mfa_factors` activo para la segunda. Se calculan **después** de escribir, en la misma transacción.

---

## 2. Cambios de esquema

**Ninguno propio**: `roles.requires_mfa`, su valor en `SUPERADMIN` y `ADMIN` y `ck_roles_root_requires_mfa` los crea `V75` ([`071` · `plan.md`](../071-activar-segundo-factor/plan.md) §2).

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `modules/system/roles/domain/models` | `Role` | Gana `requiresMfa` y `exigirSegundoFactor(boolean)`, que rechaza el `false` en la raíz | |
| `modules/system/roles/domain/service` | `RequireRoleMfaService` | Nuevo | Guardas de `RoleWriteAccess` sin `RN-SEG-012` |
| `modules/system/roles/domain/repository` | `RoleRepository` | `contarPortadores(roleId)` → activos y sin factor | |
| `modules/system/roles/application` | `RoleMfaRequirementRequest`, `RoleMfaRequirementResponse` | Nuevos | |
| `modules/system/roles/application` | `RoleListItem`, `RoleDetailResponse` | Ganan `requiresMfa` | Enmienda `RF-SP-002` y `RF-SP-003` |
| `modules/system/roles/interfaces` | `RoleController` | `PATCH /roles/{id}/mfa-requirement` | |
| `modules/system/users/application` | `OwnProfileResponse` | Gana `mfa: { enabled, enabledAt, required }` | Enmienda `RF-SP-039` |
| `modules/system/auth/application` | `MfaStatusLookup` | Nuevo puerto que publica `auth` | El perfil vive en `users` y el factor en `auth`; `users` lo pregunta por una interfaz, como ya pregunta por otras cosas de `auth`. `LayerRulesTest` dice si cabe así |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `PATCH` | `/api/v1/roles/{id}/mfa-requirement` | `roles:require-mfa` · sensible |

**Cuerpo**: `{ "required": true }`. **Respuesta `200`**: `{ "id", "code", "requiresMfa", "activeHolders", "holdersWithoutMfa" }`.

| Código | Cuándo |
|---|---|
| `200` | Cambiado, o ya tenía ese valor (`FA-001`) |
| `400` | `VAL-001` |
| `401` | Sin token |
| `403` | Sin el permiso; `reverificacion-requerida`; `VAL-003` |
| `404` | El rol no existe o está eliminado |
| `422` | `VAL-002`: desmarcar la raíz |

**Enmiendas de lectura**: `GET /roles` y `GET /roles/{id}` publican `requiresMfa`; `GET /users/me` publica `mfa`. Ninguna cambia de ruta ni de permiso.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('roles:require-mfa')")`; entra en `PERMISO_DE_CADA_OPERACION`. Solo `SUPERADMIN` y `ADMIN` lo reciben en `V75`; por `RN-SEG-011`, `ADMIN` no puede tocar la marca de `ADMIN`.

---

## 6. Auditoría

| Registro | Contenido |
|---|---|
| `audit_security_log` | `ROLE_MFA_REQUIREMENT_CHANGED`, `ALTA`, con `{ "roleCode", "required" }` |
| `audit_change_log` | `UPDATE` sobre `roles`, `requires_mfa` antes y después |

Nada en `FA-001`.

---

## 7. Transaccionalidad

Una transacción que bloquea el rol con el cerrojo de jerarquía que ya usan las escrituras de roles (`RoleHierarchyLock`), escribe, cuenta y audita.

---

## 8. Impacto sobre otros módulos

**Ninguno fuera de `SP`.** El frontend gana la marca en la administración de roles y el estado del factor en el perfil, que es lo que necesita para ofrecer activarlo o avisar que es obligatorio.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un campo más de `PATCH /roles/{id}` (`RF-SP-004`) | Ese permiso no es sensible y `RN-SEG-012` lo prohíbe sobre roles de sistema, que son justo los que hay que marcar (`spec.md` §2.1) |
| Revocar las sesiones de los portadores al marcar | Retendría a todo el rol a la vez (`spec.md` §2.1) |
| Obligación por persona | No se ha pedido; el rol es la unidad de privilegio del sistema |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Marcar un rol masivo —`CLIENTE`— por error retiene a miles | La respuesta dice a cuántos afecta (`CA-SP-888`), y se deshace con la misma operación: la retención se levanta en la siguiente renovación |

---

## 11. Estrategia de prueba

**Integración**, `RoleMfaRequirementIT`: `CA-SP-880` a `CA-SP-891` y `CA-SP-893`, con tokens reales para `CA-SP-880`, `CA-SP-881` y `CA-SP-887` —una sesión abierta, marcar y refrescar—. `CA-SP-892` en la suite del perfil propio.
