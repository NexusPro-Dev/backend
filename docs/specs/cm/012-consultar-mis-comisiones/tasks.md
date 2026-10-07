# TASKS — `RF-CM-012` Consultar mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-012` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 28-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 28-09-2026 |
| Enmendadas | 29-09-2026 — `T-05` por **la clase de cada comisión** (`RN-CM-044`) |
| Enmendadas | 29-09-2026 — `T-06` por **la fuente `DIRECTA`** (`RN-CM-045`) |
| Enmendadas | 30-09-2026 — `T-07` por **lo revertido y lo retirado** (`RN-CM-046`, `RN-CM-047`) |
| Enmendadas | 07-10-2026 — lo revertido deja de verse: lo hace `RF-CM-024` `T-09` y `T-10` (`RN-CM-047`) |
| Issue | Pendiente de crear |
| Rama | `feature/devengo-de-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | La variante propia de los dos servicios de `RF-CM-010` | `RF-CM-010` `T-04` | — | **Hecha** — 28-09-2026 |
| `T-02` | `GET /mine` y `GET /mine/{id}`, en `PERMISO_DE_CADA_OPERACION` | `T-01` | `EndpointPermissionsIT` | **Hecha** — 28-09-2026 |
| `T-03` | `MyCommissionBatchesIT`: `CA-CM-197` a `CA-CM-202` | `T-02` | Dos vendedores en cadena | **Hecha** — 28-09-2026 |
| `T-04` | Contrato OpenAPI y `requirements.md` | `T-03` | | **Hecha** — 28-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-CM-010`**: `T-01` → `T-02` → `T-03` → `T-04`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-197` a `CA-CM-202` | `T-01`, `T-02`, `T-03` |

---

## 3.1 Desviaciones respecto del plan

- **`T-05`**: `CA-CM-263` se prueba en `AfftrackSettlementIT`.

- **La variante propia es un parámetro del servicio de `RF-CM-010`**, no un servicio aparte; `MyCommissionBatchesRequest` no declara la persona.
- **Las pruebas viven en `CommissionBatchesIT`**, junto a las de `RF-CM-010`, y no en `MyCommissionBatchesIT`.

## 4. Bloqueos

**`RF-CM-010`**.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los seis criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

## 6. La clase de cada comisión — enmienda del 29-09-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-05` | `CA-CM-263` en `CommissionBatchesIT` | `RF-CM-010` `T-07` | | **Hecha** — 29-09-2026 |

Rama: `feature/comision-afftrack`.

## 7. La fuente `DIRECTA` — enmienda del 29-09-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-06` | La prosa de la `@Operation` y `CA-CM-272` | `RF-CM-010` `T-08` | `MyCommissionBatchesIT` | Hecha |

Rama: `feature/comision-venta-directa`.

**`CA-CM-272` vive en `CommissionBatchesIT`**, junto a `CA-CM-271` y a las demás pruebas de «mis lotes»: `MyCommissionBatchesIT` no existe (29-09-2026).

## 8. Lo revertido y lo retirado — enmienda del 30-09-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-07` | La prosa de las `@Operation` y `CA-CM-303` en `CommissionBatchesIT` | `RF-CM-010` `T-09` | | **Hecha** — 30-09-2026 |

Rama: `feature/corregir-vendedor-y-mover-comisiones`.
