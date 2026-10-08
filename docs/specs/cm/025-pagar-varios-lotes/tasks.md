# TASKS — `RF-CM-025` Pagar varios lotes de una vez

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-025` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 01-10-2026 |
| Enmendadas | 08-10-2026 — `T-07` y `T-08` porque **al final se borran los pendientes vacíos** (`RN-CM-052`) |
| Issue | Pendiente de crear |
| Rama | `feature/pagar-varios-lotes` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V60`**: el permiso a `SUPERADMIN` y `ADMIN`; los recuentos del catálogo 171 → 172 y `ADMIN` 169 → 170 | `RF-CM-022` `T-01` | Las suites de siembra en verde | **Hecha** — 01-10-2026 |
| `T-02` | `PayCommissionBatchesRequest` y `CommissionBatchesPaymentResponse`, con `@Schema(name)` | — | — | **Hecha** — 01-10-2026 |
| `T-03` | `PayCommissionBatchesService`, sin `@Transactional` (`plan.md` §1) | `T-02` | — | **Hecha** — 01-10-2026 |
| `T-04` | `POST /payments`, en `PERMISO_DE_CADA_OPERACION` | `T-03` | `EndpointPermissionsIT` | **Hecha** — 01-10-2026 |
| `T-05` | `PayCommissionBatchesIT`: `CA-CM-306` a `CA-CM-314` | `T-04` | `CA-CM-311` con dos hilos | **Hecha** — 01-10-2026 |
| `T-06` | Contrato OpenAPI; `requirements.md` | `T-05` | Diff del `json` | **Hecha** — 01-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-306` a `CA-CM-313` | `T-03`, `T-04`, `T-05` |
| `CA-CM-314` | `T-01`, `T-04`, `T-05` |

---

## 4. Bloqueos

**`RF-CM-022`**: la rama de este requerimiento está **apilada** sobre `feature/corregir-vendedor-y-mover-comisiones`, porque `V60` sigue a `V59` y el catálogo parte de 171. Su PR se reapunta a `develop` cuando aquella se integre.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

## 6. Al final se borran los pendientes vacíos — enmienda del 08-10-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-07` | `payAll` llama a `EmptyBatchesAfterPayment.run()` si pagó alguno (`plan.md` §12); prosa de la `@Operation` | `RF-CM-011` `T-07` | Compila | **Hecha** — 08-10-2026 |
| `T-08` | `CA-CM-380` en `DeleteEmptyBatchesIT` | `T-07` | La suite en verde | **Hecha** — 08-10-2026 |

Rama: `develop`.
