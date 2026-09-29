# TASKS — `RF-CM-019` Registrar la comisión afftrack de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-019` |
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
| `T-01` | `UserAfftrackRate` y su repositorio, con `vigentesEn` y la traducción de `23P01`/`40P01` | `RF-CM-015` `T-01` | | **Hecha** — 29-09-2026 |
| `T-02` | Alta: servicio, `record`s, `POST` | `T-01`, `RF-CM-015` `T-04`, `T-06` | | **Hecha** — 29-09-2026 |
| `T-03` | Listado: repositorio de consulta, servicio, `GET` | `T-01` | | **Hecha** — 29-09-2026 |
| `T-04` | Corrección y retiro: servicios, `PATCH` y `POST …/deletion` | `T-01` | | **Hecha** — 29-09-2026 |
| `T-05` | Las cuatro rutas en `PERMISO_DE_CADA_OPERACION` | `T-02`–`T-04` | `EndpointPermissionsIT` | **Hecha** — 29-09-2026 |
| `T-06` | `UserAfftrackRatesIT`: `CA-CM-231` a `CA-CM-240` | `T-05` | `CA-CM-233` con dos hilos; número de sentencias en `CA-CM-240` | **Hecha** — 29-09-2026 |
| `T-07` | Contrato OpenAPI y `requirements.md` | `T-06` | El diff del `openapi.json` sin esquemas fundidos | **Hecha** — 29-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-CM-015`**: `T-01` → `T-02`, `T-03`, `T-04` → `T-05` → `T-06` → `T-07`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-231` a `CA-CM-235` | `T-02`, `T-06` |
| `CA-CM-236` | `T-03`, `T-06` |
| `CA-CM-237` a `CA-CM-239` | `T-04`, `T-06` |
| `CA-CM-240` | `T-05`, `T-06` |

---

## 3.1 Desviaciones respecto del plan

- **El predicado de vigencia vive en `AfftrackSql.VIGENTE_EN`**, una constante que usan el listado con `onDate` y la escala del cierre (`RF-CM-020`).
- **La suite es `UserAfftrackRatesIT`**.

---

## 4. Bloqueos

**`RF-CM-015`**: migración, `ftdProductIds` e importe.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los diez criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
