# TASKS — `RF-CM-021` Consultar las liquidaciones afftrack

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-021` |
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
| `T-01` | Repositorio de consulta, servicio, `record`s con `@Schema(name)`, controlador y la ruta en `PERMISO_DE_CADA_OPERACION` | `RF-CM-015` `T-01` | `EndpointPermissionsIT` | **Hecha** — 29-09-2026 |
| `T-02` | `AfftrackSettlementsIT`: `CA-CM-255` a `CA-CM-259` | `T-01`, `RF-CM-020` `T-07` | Número de sentencias en `CA-CM-259` | **Hecha** — 29-09-2026 |
| `T-03` | Contrato OpenAPI y `requirements.md` | `T-02` | El diff del `openapi.json` sin esquemas fundidos | **Hecha** — 29-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-CM-020`**: `T-01` → `T-02` → `T-03`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-255` a `CA-CM-259` | `T-01`, `T-02` |

---

## 3.1 Desviaciones respecto del plan

- **La suite es `AfftrackSettlementIT`**, que ya tiene los cierres.
- **`VAL-001` es solo el orden de las fechas**; un identificador malformado lo rechaza el enlazado con `400`.

---

## 4. Bloqueos

**`RF-CM-020`**: sin liquidaciones no hay nada que leer.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los cinco criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
