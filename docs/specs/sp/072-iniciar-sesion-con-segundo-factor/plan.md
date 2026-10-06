# PLAN — `RF-SP-072` Iniciar sesión con el segundo factor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-072` |
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

**`LoginService` no cambia de orden: cambia lo que hace después del paso 5.** Tras releer la cuenta con su fila bloqueada, si tiene factor activo **no** llama a `registrarEntrada` ni abre sesión: crea un `MfaChallenge` y devuelve el desafío. Todo lo que `RF-SP-034` defiende —resumen de descarte, estado después de la contraseña, la carrera de la eliminación— queda delante y sigue igual.

**El segundo paso es un servicio nuevo, `MfaLoginService`, y comparte con `LoginService` la contabilidad del fallo.** Lo que hoy es `LoginService.rechazar` —incrementar `failed_attempts`, calcular el bloqueo con `LockoutPolicy`, auditar, construir el `401` con `remainingAttempts`— se extrae a `CredentialFailures`, y los dos servicios lo llaman. Escrito dos veces, el segundo paso acabaría con otro umbral o sin techo, que es lo que `LockoutPolicy` ya se extrajo para evitar.

**La retención es un filtro hermano de `MustChangePasswordFilter`.** `MfaEnrollmentFilter` va **detrás** de él en la cadena, lee el claim `mer` y responde `403` con su propio `type`. Ir detrás es lo que hace que la contraseña mande (`CA-SP-837`): `MustChangePasswordFilter` no deja pasar las rutas del factor, de modo que con las dos marcas el primero en cortar es él.

---

## 2. Cambios de esquema

**Ninguno propio.** `mfa_challenges`, `refresh_tokens.mfa_verified_at` y `roles.requires_mfa` los crea `V75` ([`071` · `plan.md`](../071-activar-segundo-factor/plan.md) §2).

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `MfaChallenge` | Nueva entidad | La forma de `PasswordResetPermit`: `emitir`, `vigente(ahora)`, `fallar()`, `consumir(ahora)` |
| `domain/repository` | `MfaChallengeRepository` | Nuevo | `findByHashForUpdate` |
| `domain/repository` | `AuthUser` | Gana `requiereSegundoFactor` y `tieneSegundoFactor` | La consulta de la proyección: algún rol `ACTIVO` con `requires_mfa`, y si existe factor `ACTIVO` |
| `domain/models` | `RefreshToken` | `abrirSesion` y `rotar` llevan `mfaVerifiedAt`; `rotar` **lo copia** | `CA-SP-833` |
| `domain/service` | `CredentialFailures` | Extraído de `LoginService` | Fallo, bloqueo, auditoría y el `401` con los intentos |
| `domain/service` | `LoginService` | Tras el paso 5: desafío si hay factor; `mer` en el token si no lo hay y el rol lo exige | |
| `domain/service` | `MfaLoginService` | Nuevo | El segundo paso entero |
| `domain/service` | `SecondFactorVerifier` | Nuevo | Recibe el factor bloqueado y el código; prueba TOTP con `Totp` o, si tiene forma de código de recuperación, contra los vigentes. Devuelve qué casó. Lo reutiliza `RF-SP-073` |
| `domain/service` | `SessionService` | `refresh` recalcula `mer` y copia `mfa_verified_at` | Como recalcula `mcp` |
| `shared/security` | `AccessTokenIssuer` | `emitir` gana `mer` y `mfaVerifiedAt`; claims `mer` y `mfa` | `mfa` se omite si es nulo |
| `shared/security` | `MfaEnrollmentFilter` | Nuevo, detrás de `MustChangePasswordFilter` | Deja pasar `POST /users/me/mfa/totp`, `…/confirmation`, `GET /users/me` y las tres rutas públicas de sesión |
| `shared/error` | `ProblemKind` | Gana `ACTIVACION_DE_SEGUNDO_FACTOR_REQUERIDA` (`403`, `activacion-de-segundo-factor-requerida`) | Con `enrollmentPath` como extensión, como `changePasswordPath` |
| `shared/security/ratelimit` | `RateLimitFilter` | Cota `login-mfa`: 10 por origen y minuto | `security.md` §5.5.1 |
| `application` | `LoginResponse` | Nuevo | Ver §4 |
| `application` | `SessionResponse` | Gana `mfaEnrollmentRequired` y `recoveryCodesRemaining` (nulo salvo `FA-001`) | |
| `application` | `MfaLoginRequest` | Nuevo | `{ challengeToken, code, recoveryCode }` |
| `interfaces` | `AuthController` | `POST /auth/login` devuelve `LoginResponse`; nace `POST /auth/login/mfa` | |
| `shared/security` | `SecurityConfig`, `MustChangePasswordFilter` | `/auth/login/mfa` en `RUTAS_PUBLICAS` y en las rutas de sesión que la retención deja pasar | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/auth/login` | — (enmendado) |
| `POST` | `/api/v1/auth/login/mfa` | — (público) |

**`POST /auth/login`** responde **`LoginResponse`**, que lleva los miembros de `SessionResponse` más tres:

| Miembro | Sin factor | Con factor |
|---|---|---|
| `mfaRequired` | `false` | `true` |
| `challengeToken`, `challengeExpiresIn` | `null` | El desafío y sus segundos |
| `accessToken`, `refreshToken`, `tokenType`, `expiresIn` | Como hoy | `null` |
| `mustChangePassword`, `mfaEnrollmentRequired` | Como corresponda | `false` |

**Un solo esquema con un discriminador y no `oneOf`**: un cliente que hoy lee `accessToken` sigue funcionando con las cuentas sin factor sin cambiar una línea, y uno nuevo mira `mfaRequired` antes de nada. Un `oneOf` rompería a todo cliente generado.

**`POST /auth/login/mfa`** — cuerpo `{ "challengeToken", "code" }` o `{ "challengeToken", "recoveryCode" }`. Responde `SessionResponse`.

| Código | Cuándo |
|---|---|
| `200` | Sesión abierta |
| `400` | `VAL-001`, `VAL-002` (`EX-005`) |
| `401` | `EX-001`, `EX-002`, `EX-004` sin bloqueo: **un solo cuerpo**, el de `VAL-003` de `RF-SP-034`, con `remainingAttempts` cuando el desafío identifica una cuenta |
| `423` | La cuenta está bloqueada |
| `429` | `EX-006` |

**`EX-001` no lleva `remainingAttempts`** y `EX-002` sí: es la única diferencia observable entre un desafío muerto y un código mal tecleado, y **no revela nada** que el desafío no diga ya —quien lo presenta lo recibió al acertar la contraseña—.

---

## 5. Autorización

`/auth/login/mfa` es la **decimoquinta ruta pública**: entra en `RUTAS_PUBLICAS` de `SecurityConfig` y en `PUBLICAS` de `EndpointPermissionsIT`. La autoriza el desafío.

**`MfaEnrollmentFilter`**, sobre lo que llega con un `Jwt` con `mer` en verdadero: lo que no esté en su lista responde `403` con `enrollmentPath: "/api/v1/users/me/mfa/totp"`. **No se audita**, por lo mismo que la retención por contraseña (`RF-SP-034` · `spec.md` `FA-002`).

---

## 6. Auditoría

| Hecho | Evento |
|---|---|
| Primer paso con factor | **Ninguno.** El intento no ha terminado; si fracasa, lo dirá el segundo paso |
| Sesión abierta en el segundo paso | `LOGIN_SUCCESS` con `"mfa": true` y, si fue con código de recuperación, `"recoveryCode": true` |
| Código de recuperación usado | Además, `MFA_RECOVERY_CODE_USED` (`ALTA`) con `{ "remaining": n }` |
| Código rechazado | `MFA_VERIFICATION_FAILED` (`MEDIA`), con `{ "stage": "LOGIN" }`; si bloquea, `ACCOUNT_LOCKED` en su lugar, como hoy |
| Desafío muerto | `LOGIN_FAILURE` sin objeto, como un identificador sin cuenta |

---

## 7. Transaccionalidad

**Segundo paso**: una transacción con `noRollbackFor` para `UnauthorizedException` y `BlockedAccountException`, por el motivo de `LoginService` (el rechazo es el resultado, y su contabilidad tiene que confirmarse). Orden de bloqueo: **desafío → cuenta → factor**, siempre el mismo, para que dos segundos pasos concurrentes no se traben. Los eventos `REQUIRES_NEW`, como todos los de seguridad.

---

## 8. Impacto sobre otros módulos

**Ninguno.** Los demás módulos ven un token con dos claims más que no leen.

**El frontend sí**, y es el impacto que hay que avisar: el inicio de sesión de las cuentas con factor deja de devolver tokens en el primer paso. Las cuentas sin factor no notan nada.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Código y contraseña en la misma petición | Obliga a pedir el código a todo el mundo antes de saber si lo tiene, o revela qué cuentas lo tienen |
| Desafío como JWT firmado, sin tabla | No se podría consumir ni contar intentos: un JWT no tiene estado |
| Contador de fallos propio del factor | Daría cinco intentos más de los que tiene la contraseña; el atacante que ya la tiene sumaría los dos (`RN-SP-059`) |
| Rechazar a quien está obligado y no tiene factor | No tendría forma de activarlo (`spec.md` §2.1) |
| `oneOf` en la respuesta del login | Rompe a todo cliente generado (§4) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Las suites que entran por la API como `SUPERADMIN` o `ADMIN`** quedan retenidas en cuanto `V75` los marca | Son once archivos. `IntegrationTestBase` gana `sinSegundoFactorObligatorio(jdbc)`, que pone `requires_mfa` en falso para los roles de sistema salvo la raíz y lo repone al terminar; el superadministrador de esas suites pasa a entrar con un factor sembrado por la prueba. Las que usan `with(user(...))` no llevan `Jwt` y no se ven afectadas, igual que con `mcp` |
| El superadministrador de desarrollo queda retenido tras desplegar `V75` | Es lo esperado: lo activa en su primer inicio. Se avisa en [`deployment.md`](../../../deployment.md) |
| Un segundo paso concurrente con el mismo código | `last_used_step` se escribe con el factor bloqueado: el segundo no casa |

---

## 11. Estrategia de prueba

**Integración**, `MfaLoginIT`: `CA-SP-820` a `CA-SP-833`, con un factor sembrado por la prueba y los códigos calculados con `Totp` y el `Clock` fijo. `MfaEnrollmentRetentionIT`: `CA-SP-834` a `CA-SP-838`, sobre un rol de prueba con `requires_mfa`. Las de `RF-SP-034` siguen en verde sin tocarse, porque sus cuentas no tienen factor (`CA-SP-821`).
