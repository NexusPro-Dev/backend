# TASKS — `RF-SP-077` Exigir el segundo factor a los portadores de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-077` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `Role.requiresMfa` y `exigirSegundoFactor`; `contarPortadores` | `RF-SP-071` `T-01` | | Pendiente |
| `T-02` | `RequireRoleMfaService` | `T-01` | | Pendiente |
| `T-03` | `PATCH /roles/{id}/mfa-requirement` con su petición y su respuesta | `T-02` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-04` | `requiresMfa` en `RoleListItem` y `RoleDetailResponse` | `RF-SP-071` `T-01` | | Pendiente |
| `T-05` | `MfaStatusLookup` y `mfa` en `OwnProfileResponse` | `RF-SP-071` `T-05` | `LayerRulesTest` en verde | Pendiente |
| `T-06` | `RoleMfaRequirementIT` (`CA-SP-880` a `CA-SP-891`, `CA-SP-893`); `CA-SP-892` en la suite del perfil | `T-03`, `T-04`, `T-05`, `RF-SP-072`, `RF-SP-073` | | Pendiente |
| `T-07` | `EndpointPermissionsIT`; contrato regenerado con la prosa releída —tres respuestas de lectura cambian—; `requirements.md` | `T-06` | Suite completa en verde | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03`; `T-04` y `T-05` en paralelo → `T-06` → `T-07`. Es el **último** de los siete.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-880` a `CA-SP-890`, `CA-SP-893` | `T-02`, `T-03`, `T-06` |
| `CA-SP-891` | `T-04`, `T-06` |
| `CA-SP-892` | `T-05`, `T-06` |

---

## 4. Bloqueos

Ninguno, salvo `RF-SP-072` y `RF-SP-073`.

---

## 5. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los catorce criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
