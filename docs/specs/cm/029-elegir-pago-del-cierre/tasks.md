# TASKS — `RF-CM-029` Elegir si el pago del próximo cierre es automático o manual

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-029` |
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
| `T-01` | `upsert` y `lockTurn` en `PaymentChoiceRepository`; `existsScheduled` en `CommissionClosingRepository` | `RF-CM-028` `T-03` | Compila | **Hecha** — 08-10-2026 |
| `T-02` | `PaymentModeRequest`; `ChoosePaymentModeService` con su auditoría (`plan.md` §1, §6) | `T-01` | — | **Hecha** — 08-10-2026 |
| `T-03` | `PUT /next/payment-mode`, en `PERMISO_DE_CADA_OPERACION` | `T-02` | `EndpointPermissionsIT` | **Hecha** — 08-10-2026 |
| `T-04` | `ChoosePaymentModeIT`: `CA-CM-386` a `CA-CM-392` | `T-03`, `RF-CM-009` `T-12` | `CA-CM-389` con dos hilos | **Hecha** — 08-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03`; `RF-CM-009` `T-12`; después `T-04`. El contrato va en `RF-CM-028` `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-386` a `CA-CM-388` | `T-02`, `T-04` |
| `CA-CM-389` | `T-01`, `T-02`, `T-04`, `RF-CM-009` `T-12` |
| `CA-CM-390`, `CA-CM-391` | `T-02`, `T-04` |
| `CA-CM-392` | `T-03`, `T-04` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los siete criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
