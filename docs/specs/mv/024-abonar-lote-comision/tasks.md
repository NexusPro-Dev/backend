# TASKS — `RF-MV-024` Abonar el pago de un lote de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-024` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 26-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/pagos-y-saldos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CommissionPayout`, `PayoutOrder`, `PayoutResult` en `modules/movements/application`, con su Javadoc | — | ArchUnit en verde | Pendiente |
| `T-02` | `Movement.pagoDeComision(...)` | — | Unitarias | Pendiente |
| `T-03` | `CommissionPayoutService`: redondeo, clave, `INSERT`, `Ledger` si no es cero, auditoría, `MANDATORY` | `T-01`, `T-02`, `RF-MV-019` `T-04`, `RF-MV-023` `T-01` | Unitarias del redondeo: `0.005` → `0.01` | Pendiente |
| `T-04` | `CommissionPayoutIT`: `CA-MV-269` a `CA-MV-274`, y la llamada sin transacción | `T-03`, `RF-MV-022` `T-04` | `CA-MV-270` con dos hilos | Pendiente |
| `T-05` | `requirements.md`; aviso en `requirements/cm.md` de que la operación existe para `RF-CM-011` | `T-04` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-MV-019` y `RF-MV-023`**: `T-01` → `T-02` → `T-03` → `T-04` → `T-05`. **`RF-CM-011` la usará cuando se construya.**

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-269`, `CA-MV-271` | `T-03`, `T-04` |
| `CA-MV-270`, `CA-MV-272` | `T-03`, `T-04` |
| `CA-MV-273`, `CA-MV-274` | `T-03`, `T-04` |

---

## 4. Bloqueos

**`RF-MV-019`** (el `Ledger` y el tipo) y **`RF-MV-023`** (la clave en `movements`).

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los seis criterios de aceptación con prueba.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
