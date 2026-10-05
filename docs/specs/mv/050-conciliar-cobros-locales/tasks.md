# TASKS — `RF-MV-050` Conciliar los cobros pendientes con la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-050` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 05-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PaymentRepository.localChargesToReconcile` | `RF-MV-048` `T-01` | Elige solo pendientes con cobro local y más de la espera | Pendiente |
| `T-02` | `LocalChargeSweep` con el bloqueo consultivo y la configuración | `T-01`, `RF-MV-049` `T-01` | Apagada no hace nada | Pendiente |
| `T-03` | `LocalChargeSweepIT`: `CA-MV-620` a `CA-MV-624` | `T-02` | Cada criterio afirmado en el cuerpo de su prueba | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-620` a `CA-MV-624` | `T-02`, `T-03` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] Las suites afectadas en verde.
- [ ] Los cinco criterios de aceptación con prueba.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
