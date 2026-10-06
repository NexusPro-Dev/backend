# PLAN — `RF-SP-074` Regenerar los propios códigos de recuperación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-074` |
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

**`RecoveryCodeIssuer` de `RF-SP-071`, precedido de un `UPDATE`.** Con el factor activo bloqueado (`FOR UPDATE`), se marca `superseded_at = now()` en todos sus códigos con `used_at` y `superseded_at` nulos, y se emiten diez nuevos. **Se marcan solo los vigentes**: un código usado ya tiene su historia y no hay que reescribirla.

**La verificación reciente no la comprueba el servicio**: `users:regenerate-own-recovery-codes` está marcado en `V75`, y `RecentMfaInterceptor` (`RF-SP-073`) responde antes de llegar aquí.

---

## 2. Cambios de esquema

**Ninguno propio**: `V75` ([`071` · `plan.md`](../071-activar-segundo-factor/plan.md) §2).

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `domain/repository` | `RecoveryCodeRepository` | `supersedeVigentes(factorId, ahora)` |
| `domain/service` | `RecoveryCodeRegenerationService` | Nuevo |
| `application` | `RecoveryCodesResponse` | Nuevo: `{ generatedAt, recoveryCodes }` |
| `interfaces` | `MfaController` | `POST /users/me/mfa/recovery-codes` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/users/me/mfa/recovery-codes` | `users:regenerate-own-recovery-codes` · sensible |

Sin cuerpo. Respuesta `201` con `Cache-Control: no-store`.

| Código | Cuándo |
|---|---|
| `201` | Regenerados |
| `401` | Sin token |
| `403` | Sin el permiso; o `reverificacion-requerida` |
| `409` | `VAL-001`: sin factor activo |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('users:regenerate-own-recovery-codes')")`; entra en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

`MFA_RECOVERY_CODES_REGENERATED`, `ALTA`, `SUCCESS`, sin detalle: cuántos se anularon no responde ninguna pregunta que importe, y cuáles no se puede decir.

---

## 7. Transaccionalidad

Una transacción: el `UPDATE` y los diez `INSERT`, o nada. Con el factor bloqueado, dos regeneraciones simultáneas se ordenan y la segunda anula los de la primera, que es lo que diría hacerlas una detrás de otra.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Borrar los códigos anteriores | Se perdería cuándo se usó cada uno, que es lo que se mira si alguien entró sin teléfono |
| Anular un código suelto | Si uno se expuso, los demás pudieron exponerse con él (`spec.md` §2.1) |

---

## 10. Riesgos

Ninguno nuevo.

---

## 11. Estrategia de prueba

**Integración**, `RecoveryCodeRegenerationIT`: `CA-SP-853` a `CA-SP-858`, con tokens reales y un factor sembrado. `CA-SP-854` prueba un código anterior en `/auth/login/mfa` y en `/auth/mfa/verification`.
