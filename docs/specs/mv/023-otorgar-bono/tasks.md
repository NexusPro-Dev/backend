# TASKS — `RF-MV-023` Otorgar un bono

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-023` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 26-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 26-09-2026 salvo la última, de documentación y contrato, en curso |
| Issue | Pendiente de crear |
| Rama | `feature/pagos-y-saldos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración: `movements.idempotency_key` con su índice único parcial; permiso `movements:grant-bonus` | `RF-MV-019` `T-01` | Dos movimientos con la misma clave los rechaza el esquema | **Hecha** — 26-09-2026 |
| `T-02` | `Movement.bono(...)`, `BonusConcept` | — | Unitarias | **Hecha** — 26-09-2026 |
| `T-03` | `GrantBonusService`, `GrantBonusRequest`, `BonusResponse` | `T-01`, `T-02`, `RF-MV-019` `T-04` | La validación va antes de tocar nada | **Hecha** — 26-09-2026 |
| `T-04` | `MovementController`: `POST /bonuses` con `Idempotency-Key` | `T-03` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 26-09-2026 |
| `T-05` | `GrantBonusIT`: `CA-MV-260` a `CA-MV-268` | `T-04`, `RF-MV-022` `T-04` | `CA-MV-262` con dos hilos | **Hecha** — 26-09-2026 |
| `T-06` | `PermissionIT`, `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md`, `security.md` | `T-05` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-MV-019`** —y de `RF-MV-022` para `CA-MV-267`—: `T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-260` a `CA-MV-263` | `T-01`, `T-03`, `T-05` |
| `CA-MV-264`, `CA-MV-265` | `T-03`, `T-05` |
| `CA-MV-266` a `CA-MV-268` | `T-04`, `T-05` |

---

## 3.1 Desviaciones respecto del plan

**`movements.idempotency_key` y el permiso los trae `V49`.** El bono y el abono de lotes viven en un solo servicio (`CreditService`): la única diferencia es de qué cuenta de la empresa sale el dinero. La suite es `BalancesAndBonusIT`. **Dos peticiones simultáneas con la misma clave** abonan una vez; la segunda puede responder `500` en vez de `200` si llega a insertar antes de ver la primera —la clave única la para—, y se acepta.

## 4. Bloqueos

**`RF-MV-019`**, que trae el `Ledger`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
