# TASKS — `RF-CM-028` Consultar el próximo cierre y cómo se pagará

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-028` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 08-10-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 08-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V87`**: `commission_payment_choices`, las tres columnas de `commission_closings` con su `CHECK` y el relleno, y los dos permisos a `SUPERADMIN` y `ADMIN`; los recuentos del catálogo 207 → 209 y `ADMIN` 205 → 207 en las siete suites | — | Las suites de siembra en verde | **Hecha** — 08-10-2026 |
| `T-02` | `PaymentMode`, `ClosingSchedule` y `ClosingScheduleTest` | — | `CA-CM-383` | **Hecha** — 08-10-2026 |
| `T-03` | `PaymentChoiceRepository`; `GetNextClosingService`; `NextClosingResponse` con `@Schema(name)` | `T-01`, `T-02` | Compila | **Hecha** — 08-10-2026 |
| `T-04` | `GET /next`, en `PERMISO_DE_CADA_OPERACION` | `T-03` | `EndpointPermissionsIT` | **Hecha** — 08-10-2026 |
| `T-05` | `NextClosingIT`: `CA-CM-381`, `CA-CM-382`, `CA-CM-384` y `CA-CM-385` | `T-04` | — | **Hecha** — 08-10-2026 |
| `T-06` | Contrato OpenAPI, `api/index.md` y `requirements.md` | `T-05`, `RF-CM-029` `T-04` | Diff del `json`; `OpenApiContractIT` | **Hecha** — 08-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05`; `T-06` junto con el de `RF-CM-029`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-381`, `CA-CM-382` | `T-03`, `T-05` |
| `CA-CM-383` | `T-02` |
| `CA-CM-384` | `T-03`, `T-05` |
| `CA-CM-385` | `T-01`, `T-04`, `T-05` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los cinco criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
