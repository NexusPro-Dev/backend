# TASKS — `RF-CM-030` Consultar mi avance de FTD en el periodo abierto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-030` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 17-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 17-10-2026. `T-01` a `T-06` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V117`**: `afftrack-settlements:read-own-progress` a todo rol que porte `commission-batches:list-own`, con sus guardas; los recuentos 252 → 253 y `ADMIN` 250 → 251 en las suites de `plan.md` §10, y `SystemRolesSeedIT` y `RoleDetailIT` | — | Las suites de siembra en verde | **Hecha** — 17-10-2026 |
| `T-02` | `AfftrackTierPicker.siguiente` y sus casos en `AfftrackTierPickerTest` | — | `CA-CM-400`, `CA-CM-401` | **Hecha** — 17-10-2026 |
| `T-03` | `GetOwnAfftrackProgressService` y `AfftrackProgressResponse` con `@Schema(name)` | `T-02` | Compila | **Hecha** — 17-10-2026 |
| `T-04` | `GET /afftrack-settlements/mine/progress`, en `PERMISO_DE_CADA_OPERACION` | `T-01`, `T-03` | `EndpointPermissionsIT` | **Hecha** — 17-10-2026 |
| `T-05` | `AfftrackProgressIT`: `CA-CM-399` a `CA-CM-409` | `T-04` | — | **Hecha** — 17-10-2026 |
| `T-06` | Contrato OpenAPI, `api/index.md`, `security.md` (sembrado, en el bloque), `requirements/cm.md` y `requirements.md` | `T-05` | Diff del `json`; `OpenApiContractIT` | **Hecha** — 17-10-2026 |

### 1.1 Lo que la construcción cambió respecto del plan

- **`AfftrackTierPickerTest` ya existía** (la escribió `RF-CM-020`): gana los tres casos de `siguiente` en lugar de nacer.
- **`CA-CM-407` prueba por HTTP solo el próximo cierre nulo**: la suite apaga el cierre programado, como en `RF-CM-028`; el cálculo de `nextClosingAt` es el de `ClosingSchedule.next`, que ya prueba `ClosingScheduleTest`.

---

## 2. Orden de ejecución

`T-01` y `T-02` → `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-399`, `CA-CM-402` a `CA-CM-408` | `T-03`, `T-05` |
| `CA-CM-400`, `CA-CM-401` | `T-02`, `T-05` |
| `CA-CM-409` | `T-01`, `T-04`, `T-05` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde. Las suites tocadas, en verde el 17-10-2026 (148 pruebas); la suite completa queda para el CI.
- [x] Los once criterios de aceptación con prueba.
- [x] Contrato y `requirements.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto.**
