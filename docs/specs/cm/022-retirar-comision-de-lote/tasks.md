# TASKS — `RF-CM-022` Retirar una comisión de un lote pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-022` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/corregir-vendedor-y-mover-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V59`**: las tres columnas de `commissions`, sus dos `CHECK` y dos claves, `uq_commissions_detail_user` como índice único parcial con el mismo nombre, `ix_commissions_withdrawn_from`, y los dos permisos a `SUPERADMIN` y `ADMIN` (`plan.md` §2) | — | `mvn clean verify` aplica la migración desde cero | Pendiente |
| `T-02` | Los recuentos del catálogo, 169 → 171 y `ADMIN` 167 → 169 (`plan.md` §10) | `T-01` | Las suites de siembra en verde | Pendiente |
| `T-03` | `lockCommission`, `moveCommission` y `adjustTotal` en el repositorio de lotes | `T-01` | — | Pendiente |
| `T-04` | `WithdrawCommissionService`, con el orden de bloqueos de `plan.md` §1 y la auditoría | `T-03` | — | Pendiente |
| `T-05` | `POST /{id}/commissions/{commissionId}/withdrawal`, en `PERMISO_DE_CADA_OPERACION` | `T-04`, `RF-CM-010` `T-09` | `EndpointPermissionsIT` | Pendiente |
| `T-06` | `WithdrawCommissionIT`: `CA-CM-273` a `CA-CM-281` | `T-05` | `CA-CM-280` con dos hilos | Pendiente |
| `T-07` | Contrato OpenAPI —esquema y prosa de la `@Operation`—; `requirements.md` | `T-06` | Diff del `json` | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` primero: **la migración cambia la unicidad que usa el devengo**, y conviene ver `CommissionAccrualIT` en verde con el esquema solo. Después `T-03` → `T-04` → `T-05` → `T-06` → `T-07`. **`V59` es la primera tarea de las tres tripletas del cambio**: `RF-CM-023`, `RF-CM-024` y las enmiendas la dan por hecha.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-273` a `CA-CM-279` | `T-04`, `T-05`, `T-06` |
| `CA-CM-280` | `T-04`, `T-06` |
| `CA-CM-281` | `T-02`, `T-05`, `T-06` |

---

## 4. Bloqueos

**Ninguno.** Toda la liquidación está construida.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
