# TASKS — `RF-CM-018` Retirar una comisión afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-018` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 29-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/comision-afftrack` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `AfftrackRate.retirar`, el servicio, el `record` con `@Schema(name)`, la ruta y su entrada en `PERMISO_DE_CADA_OPERACION` | `RF-CM-015` `T-05` | `EndpointPermissionsIT` | Pendiente |
| `T-02` | `DeleteAfftrackRateIT`: `CA-CM-229` y `CA-CM-230` | `T-01` | | Pendiente |
| `T-03` | `CA-CM-227` y `CA-CM-228`, con un cierre | `T-02`, `RF-CM-020` | | Pendiente |
| `T-04` | Contrato OpenAPI y `requirements.md` | `T-03` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-CM-015`**: `T-01` → `T-02`; `T-03` **después de `RF-CM-020`**; `T-04`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-229`, `CA-CM-230` | `T-01`, `T-02` |
| `CA-CM-227`, `CA-CM-228` | `T-03` |

---

## 4. Bloqueos

**`RF-CM-015`**; y **`RF-CM-020`** para `CA-CM-227` y `CA-CM-228`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los cuatro criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
