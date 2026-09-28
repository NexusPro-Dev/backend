# TASKS — `RF-CM-010` Consultar los lotes de comisión, y el detalle de uno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-010` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 28-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/devengo-de-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `ix_commission_batches_periodo` en `V51` | `RF-CM-013` `T-01` | Migración desde cero | Pendiente |
| `T-02` | `CommissionableLines.describe` en `MV` | `RF-CM-013` `T-07` | Integración de `MV` | Pendiente |
| `T-03` | `CommissionBatchQueryRepository` y adaptador | `T-01` | — | Pendiente |
| `T-04` | Los dos servicios, los `record`s con `@Schema(name)` y las dos rutas en `PERMISO_DE_CADA_OPERACION` | `T-02`, `T-03` | `EndpointPermissionsIT` | Pendiente |
| `T-05` | `ListCommissionBatchesIT`, `GetCommissionBatchIT`: `CA-CM-181` a `CA-CM-188` | `T-04`, `RF-CM-009` `T-03` | Número de sentencias en `CA-CM-188` | Pendiente |
| `T-06` | Contrato OpenAPI y `requirements.md` | `T-05` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-CM-013` y `RF-CM-009`**: `T-01` → `T-02` y `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-181` a `CA-CM-187` | `T-04`, `T-05` |
| `CA-CM-188` | `T-02`, `T-05` |

---

## 4. Bloqueos

**`RF-CM-013`** y, para las pruebas, **`RF-CM-009`**.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
