# PLAN — `RF-SP-073` Reverificar el segundo factor antes de una operación sensible

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-073` |
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

**La prueba viaja en el token, y nada se guarda en el servidor.** Reverificar emite un token de acceso nuevo con el claim `mfa` en el instante actual —con los mismos `roles`, `mcp` y `mer` que el que llega—, y el refresh token no se toca. Es el mismo razonamiento que `mcp` ([`security.md`](../../../security.md) §5.2): leer la base en cada petición sensible para saber cuándo se verificó es la consulta por petición que D-08 existe para evitar.

**Quién exige la prueba: un `HandlerInterceptor`, no un filtro.** La spec exige que **la falta de permiso gane** (`EX-002`), y en Spring el permiso de la operación se evalúa en `@PreAuthorize`, **en el proxy del controlador**, después de toda la cadena de filtros y después de los interceptores. Un filtro no sabe qué operación es; un interceptor sí —recibe el `HandlerMethod—, pero corre **antes** que `@PreAuthorize`. Por eso `RecentMfaInterceptor` comprueba **él mismo** la autoridad antes de mirar la prueba:

1. Lee el permiso de la operación de su `@PreAuthorize`, con el mismo análisis que ya hace `RequiredPermissionCustomizer` para el contrato.
2. Si el permiso no es sensible, o el usuario **no tiene esa autoridad**, deja pasar: `@PreAuthorize` decidirá, y si falta el permiso el `403` será el de siempre.
3. Si la tiene y la autenticación es un `Jwt` sin `mfa` o con uno de más de cinco minutos, responde `403` `reverificacion-requerida`.

[`security.md`](../../../security.md) §3.3 decía «un tercer filtro, `RecentMfaFilter`»; se corrige en el mismo cambio.

**Qué es sensible se lee una vez, al arrancar.** El catálogo solo cambia por migración, y una migración solo entra con un despliegue: `SensitivePermissions` carga los códigos con `requires_recent_mfa` al iniciar el contexto y los guarda en un conjunto inmutable. Consultar la tabla en cada petición no compraría nada.

---

## 2. Cambios de esquema

**Ninguno propio.** `permissions.requires_recent_mfa` y sus dieciocho marcas los crea `V75` ([`071` · `plan.md`](../071-activar-segundo-factor/plan.md) §2).

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `shared/security` | `RecentMfa` | Nacido en `RF-SP-071` | `esReciente(Authentication, ahora)`; ventana en `nexus.security.mfa.recent-window` (`PT5M`) |
| `shared/security` | `SensitivePermissions` | Nuevo | El conjunto cargado al arrancar |
| `shared/security` | `PreAuthorizePermission` | Extraído de `RequiredPermissionCustomizer` | El análisis de `hasAuthority('…')`, compartido por el contrato y por el interceptor |
| `shared/security` | `RecentMfaInterceptor` | Nuevo, registrado en `WebMvcConfigurer` | §1 |
| `shared/error` | `ProblemKind` | Gana `REVERIFICACION_REQUERIDA` (`403`, `reverificacion-requerida`) | Con `verificationPath` como extensión |
| `modules/system/auth/domain/service` | `MfaVerificationService` | Nuevo | Factor bloqueado, `SecondFactorVerifier` de `RF-SP-072`, `CredentialFailures` |
| `modules/system/auth/domain/service` | `CredentialFailures` | Si el fallo bloquea **y** viene de aquí, revoca todas las sesiones con `SessionRevoker` | `EX-005` |
| `modules/system/auth/application` | `MfaVerificationRequest`, `MfaVerificationResponse` | Nuevos | |
| `modules/system/auth/interfaces` | `AuthController` | `POST /auth/mfa/verification` | Con las demás rutas de sesión |
| `modules/system/permissions/application` | `PermissionResponse`, `PermissionItem` | Ganan `requiresRecentMfa` | Enmienda `RF-SP-010` y `RF-SP-015` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/auth/mfa/verification` | `users:verify-own-mfa` |

**Cuerpo**: `{ "code" }` o `{ "recoveryCode" }`. **Respuesta `200`**: `{ "accessToken", "tokenType", "expiresIn", "mfaValidUntil", "recoveryCodesRemaining" }`, con `Cache-Control: no-store`.

| Código | Cuándo |
|---|---|
| `200` | Verificado |
| `400` | `VAL-001` |
| `401` | Sin token |
| `403` | Sin `users:verify-own-mfa` |
| `409` | `VAL-002`: sin factor activo |
| `422` | `VAL-003`: código inválido, con `remainingAttempts` |
| `423` | La cuenta quedó bloqueada (`EX-005`) |

**`422` y no `401` para el código inválido**: la credencial que autentica la petición —el token— es válida. Un `401` haría que todo cliente bien escrito intentara renovar la sesión o la cerrase, que es lo contrario de lo que se quiere.

**Toda operación sensible** gana en su documentación el `403` `reverificacion-requerida`. Lo añade `RequiredPermissionCustomizer` de forma automática a las operaciones cuyo permiso está en `SensitivePermissions`, con la extensión `x-requires-recent-mfa: true`, para que la prosa no dependa de que alguien se acuerde de escribirla en dieciocho `@Operation`.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('users:verify-own-mfa')")`; entra en `PERMISO_DE_CADA_OPERACION`.

**Una restricción nueva que vigila `EndpointPermissionsIT`**: toda operación con permiso sensible lo declara con un `hasAuthority` simple. Una expresión compuesta —`hasAnyAuthority`, un `or`— no la sabría leer el interceptor, y la operación quedaría sin protección sin que nada lo dijera.

**Las peticiones sin `Jwt` no se comprueban**, como en `MustChangePasswordFilter`: en producción toda petición autenticada lleva uno; en las pruebas, `with(user(...))` no, y es lo que mantiene las sesenta y una suites que lo usan sin tocarse. Las pruebas de este requerimiento usan tokens reales.

---

## 6. Auditoría

| Hecho | Evento |
|---|---|
| Reverificación correcta | **Ninguno** (`CA-SP-851`, `security.md` §8.1) |
| Código rechazado | `MFA_VERIFICATION_FAILED` (`MEDIA`), `{ "stage": "STEP_UP" }`; si bloquea, `ACCOUNT_LOCKED` |
| Código de recuperación usado | `MFA_RECOVERY_CODE_USED` (`ALTA`), `{ "remaining": n }` |
| Operación rechazada por prueba vencida | **Ninguno**, como la retención: no es un intento de saltarse nada |

---

## 7. Transaccionalidad

Una transacción con `noRollbackFor` para la contabilidad del fallo, como `RF-SP-072`. Orden de bloqueo: **cuenta → factor**. La revocación de sesiones de `EX-005` va en la misma transacción que el bloqueo.

---

## 8. Impacto sobre otros módulos

**`MV` y `CM`**: sus ocho operaciones sensibles empiezan a exigir la prueba **sin cambiar una línea**: lo hacen el interceptor y la marca. Sus pruebas que usan `with(user(...))` no se enteran; **las que usen tokens reales contra esas rutas** necesitarán uno con `mfa`, y se buscan en `T-08`.

**El frontend**: debe tratar el `403` `reverificacion-requerida` pidiendo el código y repitiendo la petición, o pedirlo antes leyendo `requiresRecentMfa` del catálogo.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| El código en una cabecera de cada operación sensible | El código vale una vez: dos operaciones seguidas obligarían a esperar treinta segundos (`requirements/sp.md` `RF-SP-073`) |
| Guardar la última verificación en la sesión del servidor | Una lectura de base por petición sensible, y estado que el token ya puede llevar firmado |
| Un filtro en la cadena | No sabe qué operación es (§1) |
| La lista de sensibles en el código | La marca en el permiso la publica el catálogo y la ve el frontend (`requirements/sp.md` §10.1) |
| Renovar la prueba al renovar la sesión | Convertiría la ventana de cinco minutos en siete días |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Una operación sensible con una expresión compuesta queda sin proteger | `EndpointPermissionsIT` (§5) |
| Un permiso marcado después, por migración, sin reiniciar | No ocurre: las migraciones entran con el despliegue, que reinicia |
| Desfase de reloj entre réplicas | La ventana se compara con el reloj del servidor que emitió y del que comprueba; con una sola réplica (`deployment.md` §2.1) no hay dos relojes |

---

## 11. Estrategia de prueba

**Integración**, `MfaStepUpIT`: `CA-SP-839` a `CA-SP-852`, con tokens emitidos por la API —inicio de sesión con factor sembrado y código calculado— y una operación sensible real, `POST /roles/{id}/permissions`, que es la que el responsable del proyecto nombró. `CA-SP-850` contra `GET /permissions` y `GET /permissions/{id}`. `CA-SP-849` con un `Clock` que avanza seis minutos y un refresco.
