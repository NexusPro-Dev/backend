# TASKS — `RF-SP-076` Restablecer el segundo factor de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-076` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-04` y `T-06` `Hecha` el mismo día; `T-05` pendiente |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PrivilegeContainment` alcanzable desde `auth` | — | `LayerRulesTest` en verde | **Hecha** — 06-10-2026 |
| `T-02` | `MfaResetService` | `T-01`, `RF-SP-071` `T-05` | | **Hecha** — 06-10-2026 |
| `T-03` | `POST /users/{id}/mfa/reset` y `MfaResetRequest` | `T-02` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 06-10-2026 |
| `T-04` | `MfaResetIT`: `CA-SP-869` a `CA-SP-879` | `T-03`, `RF-SP-073` | | **Hecha** — 06-10-2026 |
| `T-05` | Procedimiento de recuperación del último superadministrador en `deployment.md` | — | Escrito y probado una vez contra una base local | Pendiente |
| `T-06` | `EndpointPermissionsIT`; contrato regenerado con la prosa releída; `requirements.md` | `T-04` | Suite completa en verde | **Hecha** — 06-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-06`; `T-05` en cualquier momento. Va **después** de `RF-SP-073`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-869` a `CA-SP-879` | `T-02`, `T-03`, `T-04` |

---

## 3.1 Desviaciones respecto del plan

| # | Plan | Construido | Por qué |
|---|---|---|---|
| 1 | `PrivilegeContainment` publicado a `auth` o movido a `shared/security` | **Gana `abarca(actor, persona)`** y `auth` lo llama desde `users.domain.security`; `LayerRulesTest` lo admite | La contención compara roles concedidos; `RN-SP-065` compara permisos efectivos de dos personas. Un método más en la misma clase mantiene la contención en un solo sitio |
| 2 | La ruta en `MfaController` | **`MfaAdministrationController`**, mapeado a `/api/v1/users` | `MfaController` cuelga de `/users/me/mfa`; esta ruta es de `/users/{id}` |
| 3 | — | Los `403` de uno mismo y de la contención salen por `ForbiddenException`: se auditan como `AUTHORIZATION_DENIED` | Lo hace el manejador global de toda denegación; es lo que `plan.md` §6 de `RF-SP-038` pedía para la contención |

**`T-05` —el procedimiento de recuperación del último superadministrador en `deployment.md`— sigue `Pendiente`**: es documentación de operación y se escribe con el cierre del segundo factor.

**Pruebas**: `MfaResetIT` (12, incluidos `CA-SP-894` y `CA-SP-895` de `RF-SP-038`). Suite completa: en verde, 561 unitarias y 2547 de integración, junto con `RF-SP-075` (`./mvnw clean verify`, 06-10-2026).

## 4. Bloqueos

Ninguno, salvo `RF-SP-073`. `RN-SP-065` quedó confirmada el 06-10-2026.

---

## 5. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los once criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `deployment.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| 0.2.0 | 06-10-2026 | Sin bloqueos: `RN-SP-065` confirmada. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
| — | 06-10-2026 | `T-01` a `T-04` y `T-06` `Hecha`; `T-05` pendiente; tres desviaciones de detalle en §3.1. | Responsable técnico |
