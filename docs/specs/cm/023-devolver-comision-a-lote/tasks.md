# TASKS — `RF-CM-023` Devolver a su lote pendiente una comisión retirada

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-023` |
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
| `T-01` | `lockBatches` ordenado en el repositorio de lotes | `RF-CM-022` `T-03` | — | Pendiente |
| `T-02` | `ReturnCommissionService`, con auditoría | `T-01` | — | Pendiente |
| `T-03` | `POST /{id}/commissions/{commissionId}/return`, en `PERMISO_DE_CADA_OPERACION` | `T-02` | `EndpointPermissionsIT` | Pendiente |
| `T-04` | `ReturnCommissionIT`: `CA-CM-282` a `CA-CM-289` | `T-03`, `RF-CM-022` `T-05`, `RF-CM-011` `T-06` | `CA-CM-288` con dos hilos | Pendiente |
| `T-05` | Contrato OpenAPI; `requirements.md` | `T-04` | Diff del `json` | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-CM-022`**: `T-01` → `T-02` → `T-03` → `T-04` → `T-05`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-282` a `CA-CM-287` | `T-02`, `T-03`, `T-04` |
| `CA-CM-288` | `T-01`, `T-04` |
| `CA-CM-289` | `T-03`, `T-04` |

---

## 4. Bloqueos

**`RF-CM-022`**: el esquema (`V59`) y los retiros sobre los que probar.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
