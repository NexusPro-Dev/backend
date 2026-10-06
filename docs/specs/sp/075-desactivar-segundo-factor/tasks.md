# TASKS — `RF-SP-075` Desactivar el propio segundo factor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-075` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-04` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `MfaDeactivationService`, con la revocación de las demás sesiones de `RF-SP-037` | `RF-SP-072` `T-01`, `T-02` | | **Hecha** — 06-10-2026 |
| `T-02` | `POST /users/me/mfa/deactivation` y `MfaDeactivationRequest` | `T-01` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 06-10-2026 |
| `T-03` | `MfaDeactivationIT`: `CA-SP-859` a `CA-SP-868` | `T-02`, `RF-SP-073` | | **Hecha** — 06-10-2026 |
| `T-04` | `EndpointPermissionsIT`; contrato regenerado con la prosa releída; `requirements.md` | `T-03` | Suite completa en verde | **Hecha** — 06-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`. Va **después** de `RF-SP-073`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-859` a `CA-SP-868` | `T-01`, `T-02`, `T-03` |

---

## 3.1 Desviaciones respecto del plan

| # | Plan | Construido | Por qué |
|---|---|---|---|
| 1 | Revocar **las demás** sesiones con el mecanismo de `RF-SP-037`, conservando la actual; responde `204` | **Se cierran todas y la respuesta trae una sesión nueva** (`200`, `SessionResponse`) | `RF-SP-037` no conserva la actual: el token de acceso no dice de qué sesión viene, y por eso aquel las cierra todas. Devolver una sesión nueva consigue lo que `CA-SP-861` pide —este dispositivo sigue y los demás quedan fuera— sin añadir un claim de sesión al token |
| 2 | — | **No se publica el corte del token de acceso** al desactivar; sí al bloquear | El corte se anota tras el commit apuntando al segundo siguiente, y cortaría también el token de la sesión nueva. Los demás dispositivos conservan su token de acceso hasta que caduque —quince minutos como mucho— y ya no pueden renovarlo: la garantía de D-08 |
| 3 | La contraseña incorrecta, «como en `RF-SP-037`» | `CredentialFailures.registrarFallo`, generalizado de `rechazarCodigo`, con el evento `MFA_DISABLED` en `FAILURE` | Un solo sitio cuenta los fallos de quien ya está autenticado; `RF-SP-037` sigue con su copia, que no se toca aquí |
| 4 | — | Si la contraseña incorrecta **bloquea**, se cierran todas las sesiones y se publica el corte (`423`) | `spec.md` `EX-003`, con la misma forma que `RF-SP-073` `EX-005` |

**Pruebas**: `MfaDeactivationIT` (10). Suite completa: en verde, 561 unitarias y 2547 de integración, junto con `RF-SP-076` (`./mvnw clean verify`, 06-10-2026).

## 4. Bloqueos

Ninguno, salvo `RF-SP-073`.

---

## 5. Definición de terminado

- [x] `./mvnw verify` en verde.
- [x] Los diez criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [x] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
| — | 06-10-2026 | `T-01` a `T-04` `Hecha`; cuatro desviaciones en §3.1, la primera cambia la respuesta de `204` a `200` con una sesión nueva. | Responsable técnico |
