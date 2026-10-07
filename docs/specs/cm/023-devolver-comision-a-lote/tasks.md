# TASKS — `RF-CM-023` Devolver a su lote pendiente una comisión retirada

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-023` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 30-09-2026 |
| Enmendadas | 07-10-2026 — sin la comisión revertida: lo hace `RF-CM-024` `T-09` y `T-10` (`RN-CM-047`) |
| Issue | Pendiente de crear |
| Rama | `feature/corregir-vendedor-y-mover-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `lockBatches` ordenado en el repositorio de lotes | `RF-CM-022` `T-03` | — | **Hecha** — 30-09-2026 |
| `T-02` | `ReturnCommissionService`, con auditoría | `T-01` | — | **Hecha** — 30-09-2026 |
| `T-03` | `POST /{id}/commissions/{commissionId}/return`, en `PERMISO_DE_CADA_OPERACION` | `T-02` | `EndpointPermissionsIT` | **Hecha** — 30-09-2026 |
| `T-04` | `ReturnCommissionIT`: `CA-CM-282` a `CA-CM-289` | `T-03`, `RF-CM-022` `T-05`, `RF-CM-011` `T-06` | `CA-CM-288` con dos hilos | **Hecha** — 30-09-2026 |
| `T-05` | Contrato OpenAPI; `requirements.md` | `T-04` | Diff del `json` | **Hecha** — 30-09-2026 |

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

## 3.1 Desviaciones respecto del plan

- **`CA-CM-300`** (`RF-CM-009`) **se prueba aquí**: un abierto vacío se consigue devolviendo lo único que tenía, que es esta ruta.
- **`lockBatches` es de `RF-CM-022`**, no de esta tripleta: el retiro lo necesitó primero para bloquear el pendiente.
## 4. Bloqueos

**`RF-CM-022`**: el esquema (`V59`) y los retiros sobre los que probar.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
