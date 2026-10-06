# PLAN — `RF-SP-071` Activar el segundo factor con una app autenticadora

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-071` |
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

**Es el primero de los siete y pone el suelo de todos.** Una sola migración, `V75`, trae el esquema entero del segundo factor —las tres tablas, las tres columnas, los siete permisos, la marca de las dieciocho operaciones sensibles, los siete eventos y los dos roles obligados—, y este plan la declara. Los seis planes siguientes la **citan** y no la repiten: repartir el esquema en siete migraciones obligaría a que `RF-SP-072` esperase a `RF-SP-077` para tener `roles.requires_mfa`, y la retención la necesita desde el primer inicio de sesión.

**El código TOTP se escribe a mano, sin librería.** RFC 6238 son unas cuarenta líneas sobre `javax.crypto.Mac` con `HmacSHA1`: el truncado dinámico de RFC 4226 §5.3 y un módulo `10^6`. Una dependencia para eso añade superficie —y un QR que no hace falta, porque lo pinta el frontend— a cambio de nada, y los vectores de prueba de RFC 6238 Apéndice B la verifican entera.

**El cifrado se saca de `MV` a `shared`.** `AesGcmShopSecrets` ya hace AES-256-GCM con nonce aleatorio, versión en el prefijo y un `UUID` como dato asociado; el factor necesita **lo mismo con otra llave**. Se extrae `shared/crypto/AesGcmCipher` —la mecánica, sin llave propia— y las dos clases pasan a usarla, cada una con su llave y su mensaje de error. Dos copias del mismo cifrado son dos sitios donde equivocarse con el nonce.

---

## 2. Cambios de esquema

**`V75__sp_segundo_factor.sql`** — `V74` es de `IN` (`V74__in_permisos_de_indicadores.sql`, 06-10-2026).

| Elemento | Definición | Por qué |
|---|---|---|
| `user_mfa_factors` | Las columnas de [`requirements/sp.md`](../../../requirements/sp.md) §10.22. `secret_ciphertext` es `text` con el formato `v1:<base64(nonce‖cifrado)>` de `AesGcmCipher`, **no `bytea`** | El mismo formato que la clave de la tienda, legible en un volcado sin herramientas. `requirements/sp.md` §10.22 decía `bytea` y se corrige en el mismo cambio |
| `ck_user_mfa_factors_status` | `status IN ('PENDIENTE', 'ACTIVO', 'RETIRADO')` | |
| `ck_user_mfa_factors_type` | `factor_type IN ('TOTP')` | WebAuthn entrará ampliándolo |
| `ck_user_mfa_factors_retirado` | `(status = 'RETIRADO') = (retired_at IS NOT NULL AND retired_reason IS NOT NULL)` | Retirado sin motivo no se explica |
| `ck_user_mfa_factors_retired_reason` | `retired_reason IN ('REEMPLAZADO', 'DESACTIVADO', 'RESTABLECIDO', 'CADUCADO')` | |
| `ck_user_mfa_factors_activo` | `status <> 'ACTIVO' OR confirmed_at IS NOT NULL` | |
| `ck_user_mfa_factors_pendiente` | `status <> 'PENDIENTE' OR pending_expires_at IS NOT NULL` | |
| `uq_user_mfa_factors_activo` | Único parcial `(user_id) WHERE status = 'ACTIVO'` | `RN-SP-058` en el motor |
| `uq_user_mfa_factors_pendiente` | Único parcial `(user_id) WHERE status = 'PENDIENTE'` | |
| `fk_user_mfa_factors_user` | → `users(id)` `ON DELETE CASCADE` | Como `refresh_tokens`: sin persona no hay factor |
| `mfa_recovery_codes` | Columnas de §10.23; `fk` → `user_mfa_factors(id)` `ON DELETE CASCADE`; índice `(factor_id) WHERE used_at IS NULL AND superseded_at IS NULL` | Se buscan los vigentes de un factor |
| `mfa_challenges` | Columnas de §10.24; `uq_mfa_challenges_hash` único; `ck_mfa_challenges_attempts` `failed_attempts BETWEEN 0 AND 5` | Lo usa `RF-SP-072` |
| `roles.requires_mfa` | `boolean NOT NULL DEFAULT false`, y `true` en `SUPERADMIN` y `ADMIN` | `RN-SP-062` |
| `ck_roles_root_requires_mfa` | `parent_role_id IS NOT NULL OR requires_mfa` | La raíz lo exige siempre |
| `permissions.requires_recent_mfa` | `boolean NOT NULL DEFAULT false`, y `true` en los dieciocho de [`security.md`](../../../security.md) §4.4 | `RN-SP-063` |
| `refresh_tokens.mfa_verified_at` | `timestamptz NULL` | `security.md` §5.2 |
| Permisos | Los siete de `security.md` §4.4. Los cinco de alcance propio **a todo rol por su tipo**, como `V31`; `users:reset-mfa` y `roles:require-mfa`, a `SUPERADMIN` y `ADMIN` explícito | Catálogo 189 → **196** |
| `ck_audit_security_log_event_type` | Se rehace con los veintiocho literales | Los siete de `security.md` §8.1 |

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `shared/crypto` | `AesGcmCipher` | Nuevo | Extraído de `AesGcmShopSecrets`, sin llave propia |
| `modules/movements/infrastructure` | `AesGcmShopSecrets` | Usa `AesGcmCipher` | Mismo comportamiento: lo vigilan sus pruebas actuales |
| `shared/security` | `Totp` | Nuevo | `codigo(secreto, periodo)` y `periodoQueCasa(secreto, codigo, ahora, ultimoUsado)`, que devuelve el periodo aceptado o vacío. Tolerancia ±1 |
| `modules/system/auth/domain/models` | `MfaFactor` | Nueva entidad | `iniciar`, `confirmar`, `retirar(motivo)`, `vigente(ahora)` |
| `modules/system/auth/domain/models` | `RecoveryCode` | Nueva entidad | |
| `modules/system/auth/domain/repository` | `MfaFactorRepository`, `RecoveryCodeRepository` | Nuevos | `findActivo(userId)`, `findPendiente(userId)`, ambos con variante `FOR UPDATE` |
| `modules/system/auth/infrastructure` | `MfaSecrets` | Nuevo | Cifra y descifra el secreto con `MFA_ENCRYPTION_KEY` y el `user_id` como dato asociado. **Sin llave, el contexto no arranca** (Art. IX.5): al contrario que la de PayRetailers, que se tolera vacía porque la pasarela puede no estar contratada, sin esta no hay forma segura de guardar el factor de nadie |
| `modules/system/auth/domain/service` | `MfaEnrollmentService` | Nuevo | `iniciar(actor)` y `confirmar(actor, codigo)` |
| `modules/system/auth/domain/service` | `RecoveryCodeIssuer` | Nuevo | Genera diez, los resume con `PasswordHasher` y devuelve los claros. Lo reutiliza `RF-SP-074` |
| `modules/system/auth/application` | `MfaEnrollmentResponse`, `MfaConfirmationRequest`, `MfaConfirmationResponse` | Nuevos | Con `@Schema(name)` propio |
| `modules/system/auth/interfaces` | `MfaController` | Nuevo | Las rutas de `/users/me/mfa/**`. **No** en `UserController`: el segundo factor es del submódulo de credenciales, como la contraseña |
| `shared/audit` | `AuditEnums.SecurityEventType` | Gana los siete | |
| `shared/security` | `MustChangePasswordFilter` | Sin cambios aquí | La retención por factor llega con `RF-SP-072` |

**Formato de los códigos de recuperación:** diez caracteres del alfabeto Crockford Base32 sin ambiguos, presentados como `XXXXX-XXXXX`; se normalizan —mayúsculas, sin guion— antes de resumir y de comparar. Cincuenta bits por código: con cinco intentos por desafío, adivinarlo no es un ataque.

**El nombre que verá la app**: `otpauth://totp/NEXUS:<username>?secret=<base32>&issuer=NEXUS&algorithm=SHA1&digits=6&period=30`. El emisor vive en configuración (`nexus.security.mfa.issuer`, por defecto `NEXUS`) y **se fija antes del primer despliegue**: cambiarlo después deja las entradas ya escaneadas con el nombre viejo.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/users/me/mfa/totp` | `users:start-own-mfa` |
| `POST` | `/api/v1/users/me/mfa/totp/confirmation` | `users:confirm-own-mfa` |

**Iniciar** — sin cuerpo. Respuesta `201`: `{ "otpauthUri", "secret", "expiresAt" }`.

**Confirmar** — cuerpo `{ "code": "123456" }`. Respuesta `200`: `{ "enabledAt", "replacedPrevious", "recoveryCodes": [ …diez… ] }`, con `Cache-Control: no-store`.

| Código | Cuándo |
|---|---|
| `201` / `200` | Iniciado / confirmado |
| `400` | `VAL-001` |
| `401` | Sin token |
| `403` | Sin el permiso; o, al iniciar con un factor activo, sin verificación reciente (`EX-003`, `type` `reverificacion-requerida` de `RF-SP-073`) |
| `422` | `VAL-002`: sin pendiente, caducado o código inválido |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('users:start-own-mfa')")` y `…('users:confirm-own-mfa')`. Las dos entran en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`.

**`EX-003` lo comprueba el servicio y no la marca del catálogo**: iniciar solo es sensible cuando ya hay un factor activo, y eso es estado de la persona. Lee el claim `mfa` con el mismo componente que `RF-SP-073` usa para las operaciones marcadas (`RecentMfa`), de modo que la ventana es una sola.

---

## 6. Auditoría

`MFA_ENABLED`, `ALTA`, `SUCCESS`, al confirmar, con `{ "replacedPrevious": true|false }` en el detalle y el actor como objeto. Un `ChangeEvent` sobre `user_mfa_factors` con la instantánea **sin** `secret_ciphertext`. El secreto y los códigos nunca entran en un detalle, en un `ChangeEvent` ni en un log: el enmascarador de `security.md` §7.3 añade `secret`, `otpauthUri`, `recoveryCodes` y `code` a su lista.

---

## 7. Transaccionalidad

**Iniciar**: una transacción que bloquea el pendiente anterior (`FOR UPDATE`), lo marca `RETIRADO`/`CADUCADO` y crea el nuevo.

**Confirmar**: una transacción que bloquea el pendiente y el activo, comprueba el código, escribe `last_used_step`, retira el activo (`REEMPLAZADO`), activa el pendiente y emite los diez códigos. Si dos confirmaciones compiten, la segunda topa con `uq_user_mfa_factors_activo` o encuentra el pendiente ya activo: **un `409` en lugar de dos factores**.

---

## 8. Impacto sobre otros módulos

**`MV`**: `AesGcmShopSecrets` cambia por dentro y no por fuera. Ningún otro.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Librería TOTP (`dev.samstevens.totp`, `googleauth`) | Cuarenta líneas no justifican una dependencia, y su QR lo pinta el frontend (§1) |
| Guardar el secreto resumido, como la contraseña | Imposible: hay que recalcular el código (`requirements/sp.md` §10.22) |
| Activar al iniciar, sin confirmar | Un escaneo fallido dejaría a la persona fuera de su cuenta |
| Códigos de recuperación de seis dígitos | Veinte bits: con diez vigentes, adivinar uno es mil veces más fácil que adivinar el código del teléfono |
| Una migración por requerimiento | La retención de `RF-SP-072` necesita `roles.requires_mfa` antes de que exista `RF-SP-077` (§1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Perder `MFA_ENCRYPTION_KEY` deja a todo el mundo sin factor | `security.md` §7.1; se respalda con `JWT_SECRET`. Sin llave el contexto no arranca, así que el olvido se ve al desplegar |
| Dos factores activos por concurrencia | `uq_user_mfa_factors_activo`; `CA-SP-817` |
| El secreto termina en un log | Enmascarador y `CA-SP-818` |

---

## 11. Estrategia de prueba

**Unitarias**, `TotpTest`: los vectores de RFC 6238 Apéndice B en SHA-1, la tolerancia ±1 y el rechazo de ±2 y del periodo ya usado. `AesGcmCipherTest`: ida y vuelta, dato asociado distinto falla, nonce distinto en cada cifrado.

**Integración**, `MfaEnrollmentIT`: `CA-SP-807` a `CA-SP-819`. Los códigos se calculan en la prueba con `Totp` y un `Clock` fijo inyectado; `CA-SP-817` usa `ConcurrencyHarness`.
