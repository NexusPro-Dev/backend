# TASKS — `RF-MV-052` Ajustar los puntos de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-052` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 05-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V72`: `external_reference`, `ck_movements_points` rehecha, evento `AJUSTE`, tipo `AJUSTE_PUNTOS`, permiso `movements:adjust-points` | — | Puntos con signo sin tasa entran; una tasa sin puntos no | Pendiente |
| `T-02` | `EntryEvent.AJUSTE`, `Movement.ajusteDePuntos(...)` | — | | Pendiente |
| `T-03` | `MovementRepository`: `external_reference` al insertar, `findPointsAdjustment` | `T-01`, `T-02` | | Pendiente |
| `T-04` | `PointsAdjustmentService`, `PointsRequests.Adjustment`, `PointsAdjustmentResponse` | `T-03` | La validación va antes de tocar nada; la resta la frena el `Ledger` | Pendiente |
| `T-05` | `PointsController`: `POST /points-adjustments` con `Idempotency-Key` | `T-04` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-06` | `PointsAdjustmentIT`: `CA-MV-636` a `CA-MV-646` | `T-05` | | Pendiente |
| `T-07` | Recuentos del catálogo (183, `ADMIN` 181), `EndpointPermissionsIT`; contrato regenerado con la prosa releída; `requirements.md` | `T-06` | Suite completa en verde | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-636` a `CA-MV-639` | `T-01`, `T-04`, `T-06` |
| `CA-MV-640` a `CA-MV-643` | `T-04`, `T-06` |
| `CA-MV-644` a `CA-MV-646` | `T-05`, `T-06` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los once criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
