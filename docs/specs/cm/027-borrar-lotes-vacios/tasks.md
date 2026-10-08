# TASKS — `RF-CM-027` Borrar los lotes vacíos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-027` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 08-10-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 08-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V86`**: el permiso a `SUPERADMIN` y `ADMIN`, con la guarda; los recuentos del catálogo 206 → 207 y `ADMIN` 204 → 205 | — | Las suites de siembra en verde | **Hecha** — 08-10-2026 |
| `T-02` | `findEmptyUnpaidBatchIds` y `lockWithdrawnFrom` en el repositorio de lotes; `lockLatestUnpaidBatch` vuelve a buscar si el candidato desapareció | — | Compila | **Hecha** — 08-10-2026 |
| `T-03` | `EmptyBatchRemoval` cita `RF-CM-027` en su motivo; `DeleteEmptyBatchesService` (`plan.md` §1) | `T-02` | — | **Hecha** — 08-10-2026 |
| `T-04` | `EmptyBatchesDeletionResponse` con `@Schema(name)`; `DELETE /empty`, en `PERMISO_DE_CADA_OPERACION` | `T-03` | `EndpointPermissionsIT` | **Hecha** — 08-10-2026 |
| `T-05` | `DeleteEmptyBatchesIT`: `CA-CM-368` a `CA-CM-375` | `T-04`, `RF-CM-022` `T-13`, `RF-CM-023` `T-08` | `CA-CM-374` con dos hilos | **Hecha** — 08-10-2026 |
| `T-06` | Contrato OpenAPI, `api/index.md` y `requirements.md` | `T-05`, `RF-CM-024` `T-18` | Diff del `json`; `OpenApiContractIT` | **Hecha** — 08-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`; las enmiendas de `RF-CM-022`, `RF-CM-023` y `RF-CM-024`; después `T-05` y `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-368` a `CA-CM-374` | `T-02`, `T-03`, `T-04`, `T-05` |
| `CA-CM-375` | `T-01`, `T-04`, `T-05` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
