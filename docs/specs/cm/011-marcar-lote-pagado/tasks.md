# TASKS — `RF-CM-011` Marcar un lote como pagado

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-011` |
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
| `T-01` | `lockById` y `markPaid` en el repositorio de lotes | `RF-CM-013` `T-09` | — | Pendiente |
| `T-02` | `PayCommissionBatchService`, con auditoría | `T-01` | — | Pendiente |
| `T-03` | `POST /{id}/payment`, en `PERMISO_DE_CADA_OPERACION` | `T-02`, `RF-CM-010` `T-04` | `EndpointPermissionsIT` | Pendiente |
| `T-04` | `PayCommissionBatchIT`: `CA-CM-189` a `CA-CM-196` | `T-03`, `RF-CM-009` `T-03` | `CA-CM-192` con dos hilos | Pendiente |
| `T-05` | Contrato OpenAPI; `requirements.md`; `T-05` de `RF-MV-024` a **Hecha** | `T-04` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-CM-013`, `RF-CM-009` y `RF-CM-010`**: `T-01` → `T-02` → `T-03` → `T-04` → `T-05`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-189` a `CA-CM-196` | `T-02`, `T-03`, `T-04` |

---

## 4. Bloqueos

**`RF-CM-013`** (el esquema), **`RF-CM-009`** (lotes pendientes para probar) y **`RF-CM-010`** (la forma del detalle).

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
