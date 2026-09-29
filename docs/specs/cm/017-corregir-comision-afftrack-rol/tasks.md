# TASKS — `RF-CM-017` Corregir el límite o el valor de una comisión afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-017` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 29-09-2026 |
| Estado | **En revisión** — tareas `Hecha` el 29-09-2026 |
| Issue | Pendiente de crear |
| Rama | `feature/comision-afftrack` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `AfftrackRate.corregir`, el servicio, el `record` con `@Schema(name)`, el `PATCH` y la ruta en `PERMISO_DE_CADA_OPERACION` | `RF-CM-015` `T-05`, `T-06` | `EndpointPermissionsIT` | **Hecha** — 29-09-2026 |
| `T-02` | `UpdateAfftrackRateIT`: `CA-CM-221`, `CA-CM-223` a `CA-CM-226` | `T-01` | | **Hecha** — 29-09-2026 |
| `T-03` | `CA-CM-222`: corregir tras un cierre no cambia lo pagado | `T-02`, `RF-CM-020` | | **Hecha** — 29-09-2026 |
| `T-04` | Contrato OpenAPI y `requirements.md` | `T-03` | | **Hecha** — 29-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-CM-015`**: `T-01` → `T-02`; `T-03` **después de `RF-CM-020`**; `T-04`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-221`, `CA-CM-223` a `CA-CM-226` | `T-01`, `T-02` |
| `CA-CM-222` | `T-03` |

---

## 3.1 Desviaciones respecto del plan

- **`CA-CM-222` se prueba en `AfftrackSettlementIT`**, que es donde hay cierres; los demás, en `AfftrackRatesIT`.

---

## 4. Bloqueos

**`RF-CM-015`**; y **`RF-CM-020`** para `CA-CM-222`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los seis criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
