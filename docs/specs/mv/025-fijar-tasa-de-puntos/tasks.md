# TASKS — `RF-MV-025` Fijar la tasa de puntos de una moneda

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-025` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/compra-de-puntos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración de la etapa 3 (`plan.md` §2): `points_rates`, las dos columnas de `movements` con `ck_movements_points`, `ck_accounts_kind` y `ck_movement_entries_event` ampliados, el tipo `COMPRA_PUNTOS` con `REGISTRADO`, los seis permisos y sus guardas | — | Aplicada sobre la base de desarrollo; las guardas pasan | Pendiente |
| `T-02` | Recuentos del catálogo 163 → 169 en todas las suites que lo cuentan | `T-01` | `grep -rnE "\b163\b" src/test` sin restos | Pendiente |
| `T-03` | `PointsRate`, los códigos nuevos de `AccountKind`, `EntryEvent` y el tipo | — | Unitarias de `VAL-002` | Pendiente |
| `T-04` | `PointsRateRepository` y `PointsRates.vigente` | `T-01`, `T-03` | La vigente es la de `valid_from` más reciente no futura | Pendiente |
| `T-05` | `SetPointsRateService`, `SetPointsRateRequest`, `PointsRateResponse` | `T-04` | La validación va antes de tocar nada; `FA-001` no escribe | Pendiente |
| `T-06` | `PointsRateController`: `POST /movements/points-rates` | `T-05` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-07` | `SetPointsRateIT`: `CA-MV-293` a `CA-MV-300`; prueba de esquema de los tres `CHECK` | `T-06` | | Pendiente |
| `T-08` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md`, `security.md` | `T-07` | | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07` → `T-08`. **Es el primero de la etapa**: `RF-MV-026`, `RF-MV-027` y `RF-MV-030` dependen de `T-01` y `T-04`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-293` a `CA-MV-295`, `CA-MV-300` | `T-04`, `T-05`, `T-07` |
| `CA-MV-296`, `CA-MV-297` | `T-03`, `T-05`, `T-07` |
| `CA-MV-298`, `CA-MV-299` | `T-06`, `T-07` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba** y no solo citado en un rango.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
