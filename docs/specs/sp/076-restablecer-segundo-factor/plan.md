# PLAN — `RF-SP-076` Restablecer el segundo factor de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-076` |
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

**La forma de `RF-SP-038`** —una operación de administración sobre la credencial de otra persona, con `RN-SP-017` para uno mismo—, **más la contención que aquel no tiene**: `RN-SP-065` se comprueba con `PrivilegeContainment`, el mismo componente con que `RF-SP-030` aplica `RN-SEG-010` al asignar roles. Los permisos efectivos de la persona deben estar contenidos en los del actor; si no, `403` sin decir cuál falta —decirlo enseñaría qué puede hacer la persona—.

**El orden de las comprobaciones**: uno mismo → la persona existe → contención → tiene factor. La contención va **antes** que «tiene factor» para que la respuesta a un administrador sin privilegio suficiente no le revele si el superadministrador tiene el factor activo.

**Cerrar las sesiones**: el `SessionRevoker` que usan `RF-SP-028` y `RF-SP-038`, con el motivo `ACCESO_RETIRADO`.

---

## 2. Cambios de esquema

**Ninguno propio**: `V75` ([`071` · `plan.md`](../071-activar-segundo-factor/plan.md) §2).

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `modules/system/auth/domain/service` | `MfaResetService` | Nuevo |
| `modules/system/users/domain/security` | `PrivilegeContainment` | Se publica a `auth` por la interfaz que ya usa `RF-SP-030`, o se mueve a `shared/security` si `LayerRulesTest` no deja cruzar — lo que diga la prueba de capas |
| `modules/system/auth/application` | `MfaResetRequest` | Nuevo: `{ reason }` |
| `modules/system/auth/interfaces` | `MfaController` | `POST /users/{id}/mfa/reset` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/users/{id}/mfa/reset` | `users:reset-mfa` · sensible |

Cuerpo `{ "reason" }`. **`POST` a una subruta** por lo mismo que `RF-SP-038` (`/password-reset`) y `RF-SP-029` (`/deletion`): lleva un cuerpo con el motivo.

| Código | Cuándo |
|---|---|
| `204` | Restablecido |
| `400` | `VAL-001` |
| `401` | Sin token |
| `403` | Sin el permiso; `reverificacion-requerida`; `VAL-002` (uno mismo); `VAL-003` (contención) |
| `404` | La persona no existe o está eliminada |
| `409` | `VAL-004`: sin factor |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('users:reset-mfa')")`; entra en `PERMISO_DE_CADA_OPERACION`. Solo `SUPERADMIN` y `ADMIN` lo reciben en `V75`.

---

## 6. Auditoría

| Registro | Contenido |
|---|---|
| `audit_security_log` | `MFA_RESET`, `ALTA`, `SUCCESS`, `target_user_id` la persona |
| `audit_deletion_log` | Sobre `user_mfa_factors`, con el motivo y la instantánea del factor **sin** el secreto. Uno por factor retirado: el activo y, si lo hay, el pendiente |

---

## 7. Transaccionalidad

Una transacción: retirar los factores y revocar las sesiones, o nada. Los factores se bloquean (`FOR UPDATE`) para que una confirmación concurrente de `RF-SP-071` no deje un factor activo recién nacido después del restablecimiento.

---

## 8. Impacto sobre otros módulos

Ninguno fuera de `SP`. Dentro, `auth` pasa a leer `PrivilegeContainment` de `users` (§3).

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Sin contención, como `RF-SP-038` | Toma de cuenta del superadministrador (`spec.md` §2.1) |
| Restablecer contraseña y factor en una sola operación | Revelaría una credencial al restablecer el factor (`spec.md` §2.1) |
| Desactivar sin cerrar sesiones | Una sesión abierta en el teléfono perdido la tiene otro |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| El último superadministrador se queda fuera | Procedimiento por base de datos en `deployment.md` (`spec.md` §13) |
| `RN-SP-065` no se confirma | Se retira del servicio y de `CA-SP-874`; nada más depende de ella |

---

## 11. Estrategia de prueba

**Integración**, `MfaResetIT`: `CA-SP-869` a `CA-SP-879`. `CA-SP-874` con un `ADMIN` real contra el superadministrador sembrado; `CA-SP-871` con dos sesiones abiertas de la persona; `CA-SP-872` con un rol de prueba con `requires_mfa`.
