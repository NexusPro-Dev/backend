# TASKS — `RF-MV-051` Pagar por la pasarela local un pago pendiente propio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-051` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 05-10-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 05-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `payments.checkout_url` en `V69` y en `ck_payments_cobro_local` | `RF-MV-048` `T-01` | | **Hecha** — 05-10-2026 |
| `T-02` | `LocalPayment.retomar` y `POST /movements/mine/{id}/local-charge` | `T-01`, `RF-MV-048` `T-04` | | **Hecha** — 05-10-2026 |
| `T-03` | `CA-MV-625` a `CA-MV-629` en `LocalChargeIT`; `EndpointPermissionsIT` | `T-02` | Cada criterio afirmado en el cuerpo de su prueba | **Hecha** — 05-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-625` a `CA-MV-629` | `T-02`, `T-03` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [x] Las suites afectadas en verde.
- [x] Los cinco criterios de aceptación con prueba.
- [x] Contrato regenerado, con la prosa releída.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
