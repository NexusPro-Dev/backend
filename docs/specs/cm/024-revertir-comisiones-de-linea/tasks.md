# TASKS — `RF-CM-024` Revertir las comisiones de una línea cuyo vendedor se corrige

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-024` |
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
| `T-01` | `CommissionedLineRelease` y `ReleaseOutcome` en `movements.application`, con su Javadoc | — | Compila | Pendiente |
| `T-02` | `hasCountedFtd`, `lockLiveCommissionsOf`, `revert` y `deleteOutcome` en el repositorio de desenlaces | `RF-CM-022` `T-01` | — | Pendiente |
| `T-03` | `ReleaseCommissionedLineService` implementa el puerto, `MANDATORY`, con el bloqueo de la línea y la auditoría | `T-01`, `T-02`, `RF-CM-023` `T-01` | — | Pendiente |
| `T-04` | La aserción de ArchUnit: nada de `movements` depende de `commissions` | — | La suite de arquitectura en verde | Pendiente |
| `T-05` | `ReleaseCommissionedLineIT`: `CA-CM-290` a `CA-CM-299`, por la ruta de `RF-MV-016` | `T-03`, `RF-MV-016` `T-14` | `CA-CM-298` con dos hilos | Pendiente |
| `T-06` | `requirements.md` | `T-05` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-CM-022` y `RF-CM-023`**: `T-01` → `T-02` → `T-03`; entonces **`RF-MV-016` `T-13` y `T-14`**, que invocan el puerto; y `T-04`, `T-05`, `T-06`. **`T-01` es de `MV` y vive aquí** porque el puerto existe para este requerimiento, igual que `RF-CM-013` escribió `CommissionableLinesEvent`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-290`, `CA-CM-292`, `CA-CM-295`, `CA-CM-296`, `CA-CM-299` | `T-02`, `T-03`, `T-05` |
| `CA-CM-291` | `T-03`, `T-05`, `RF-CM-013` `T-18` |
| `CA-CM-293`, `CA-CM-294`, `CA-CM-297` | `T-03`, `T-05`, `RF-MV-016` `T-13` |
| `CA-CM-298` | `T-03`, `T-05` |

---

## 4. Bloqueos

**`RF-CM-022`** (el esquema) y **`RF-MV-016`** (la ruta por la que se prueba).

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los diez criterios de aceptación con prueba.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
