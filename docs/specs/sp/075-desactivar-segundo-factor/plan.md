# PLAN — `RF-SP-075` Desactivar el propio segundo factor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-075` |
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

**La forma de `ChangeOwnPasswordService` (`RF-SP-037`)**: verifica la contraseña vigente con `PasswordHasher`, cuenta el fallo con `CredentialFailures` —que nació en `RF-SP-072`— y revoca las demás sesiones con el mismo puerto que usa el cambio de contraseña, conservando la familia de la sesión actual.

**Qué sesión es la actual**: la familia del refresh token no viaja en el token de acceso. `RF-SP-037` resolvió lo mismo; se reutiliza su mecanismo tal cual, sin decidir nada nuevo aquí.

**El orden de las comprobaciones**: factor activo → rol que lo exige → contraseña. La contraseña va **la última** para que los dos conflictos —que no dependen de ella— no consuman intentos de la cuenta.

---

## 2. Cambios de esquema

**Ninguno propio**: `V75` ([`071` · `plan.md`](../071-activar-segundo-factor/plan.md) §2).

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `domain/models` | `MfaFactor` | `retirar(DESACTIVADO, ahora)`, nacido en `RF-SP-071` |
| `domain/service` | `MfaDeactivationService` | Nuevo |
| `domain/repository` | `AuthUser` | `requiereSegundoFactor`, nacido en `RF-SP-072` |
| `application` | `MfaDeactivationRequest` | Nuevo: `{ currentPassword }` |
| `interfaces` | `MfaController` | `POST /users/me/mfa/deactivation` |

**Los códigos no se tocan**: cuelgan del factor, y un código de un factor retirado no casa ([`requirements/sp.md`](../../../requirements/sp.md) §10.23).

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/users/me/mfa/deactivation` | `users:disable-own-mfa` · sensible |

**`POST` a una subruta y no `DELETE /users/me/mfa`**: lleva la contraseña en el cuerpo, y un cuerpo en `DELETE` no tiene semántica en RFC 9110 —es el criterio de `architecture.md` §6.6.3 para las eliminaciones con motivo—.

| Código | Cuándo |
|---|---|
| `204` | Desactivado |
| `400` | `VAL-001` |
| `401` | Sin token |
| `403` | Sin el permiso; o `reverificacion-requerida` |
| `409` | `VAL-003` (su rol lo exige) o `VAL-004` (sin factor) |
| `422` | `VAL-002`: contraseña incorrecta |
| `423` | La cuenta quedó bloqueada |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('users:disable-own-mfa')")`; entra en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

`MFA_DISABLED`, `ALTA`, `SUCCESS`. Un `ChangeEvent` sobre `user_mfa_factors`, `UPDATE`, con el estado anterior y el nuevo, **sin** el secreto. La contraseña incorrecta, como en `RF-SP-037`.

---

## 7. Transaccionalidad

Una transacción con `noRollbackFor` para la contabilidad del fallo. Orden de bloqueo: **cuenta → factor**, el de `RF-SP-073`.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Solo la verificación reciente, sin contraseña | Es la operación que más baja la seguridad: se piden los dos factores (`spec.md` §2.1) |
| Borrar la fila del factor | Se perdería cuándo tuvo factor la persona |
| Cerrar también la sesión actual | La persona acaba de probar los dos factores: echarla no protege nada |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| La prueba reciente sobrevive cinco minutos sin factor | Aceptado y declarado (`spec.md` §13) |

---

## 11. Estrategia de prueba

**Integración**, `MfaDeactivationIT`: `CA-SP-859` a `CA-SP-868`, con tokens reales, dos sesiones abiertas de la misma persona para `CA-SP-861`, y un rol de prueba con `requires_mfa` para `CA-SP-862`.
