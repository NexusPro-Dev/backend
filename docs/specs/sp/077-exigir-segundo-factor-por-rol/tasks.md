# TASKS — `RF-SP-077` Exigir el segundo factor a los portadores de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-077` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-07` `Hecha` (`T-05` con `RF-SP-072`) |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `Role.requiresMfa` y `exigirSegundoFactor`; `contarPortadores` | `RF-SP-071` `T-01` | | **Hecha** — 06-10-2026 |
| `T-02` | `RequireRoleMfaService` | `T-01` | | **Hecha** — 06-10-2026 |
| `T-03` | `PATCH /roles/{id}/mfa-requirement` con su petición y su respuesta | `T-02` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 06-10-2026 |
| `T-04` | `requiresMfa` en `RoleListItem` y `RoleDetailResponse` | `RF-SP-071` `T-01` | | **Hecha** — 06-10-2026 |
| `T-05` | `MfaStatusLookup` y `mfa` en `OwnProfileResponse` | `RF-SP-071` `T-05` | `LayerRulesTest` en verde | **Hecha** — 06-10-2026, **adelantada en `RF-SP-072` `T-10`** a petición del responsable del proyecto |
| `T-06` | `RoleMfaRequirementIT` (`CA-SP-880` a `CA-SP-891`, `CA-SP-893`); `CA-SP-892` en la suite del perfil | `T-03`, `T-04`, `T-05`, `RF-SP-072`, `RF-SP-073` | | **Hecha** — 06-10-2026 |
| `T-07` | `EndpointPermissionsIT`; contrato regenerado con la prosa releída —tres respuestas de lectura cambian—; `requirements.md` | `T-06` | Suite completa en verde | **Hecha** — 06-10-2026 |

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

## 3.1 Desviaciones respecto del plan

| # | Plan | Construido | Por qué |
|---|---|---|---|
| 1 | `MfaStatusLookup`, un puerto que `auth` publica a `users` para el perfil | **No hizo falta**: el perfil lee el estado del factor con una consulta propia de `users` (`UserQueryRepository.mfaOf`) | Se construyó con `RF-SP-072` `T-10`, adelantada; las dos tablas son de `SP` y la consulta es de solo lectura |
| 2 | `RoleHierarchyLock` para serializar la escritura | **El bloqueo de la fila del rol** de `RoleWriteAccess.cargarConPermisosModificables` | Esta operación no toca la jerarquía: basta con la fila |
| 3 | Desmarcar la raíz, `VAL-002` | `Role.exigirSegundoFactor` lo rechaza y el servicio lo traduce a `422`; `ck_roles_root_requires_mfa` lo sostiene en el esquema | Como decía el plan; se anota que la regla vive en el modelo y no solo en el servicio |

**Pruebas**: `RoleMfaRequirementIT` (13); `CA-SP-892` en `MfaLoginIT`. Suite completa: en verde, 562 unitarias y 2563 de integración (`./mvnw clean verify`, 06-10-2026).

## 4. Bloqueos

Ninguno, salvo `RF-SP-072` y `RF-SP-073`.

---

## 5. Definición de terminado

- [x] `./mvnw verify` en verde.
- [x] Los catorce criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [x] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
| — | 06-10-2026 | `T-05` —el estado del factor en el perfil, `CA-SP-892`— se construyó con `RF-SP-072`, a petición del responsable del proyecto. | Responsable técnico |
| — | 06-10-2026 | `T-01` a `T-07` `Hecha`; tres desviaciones de detalle en §3.1. | Responsable técnico |
