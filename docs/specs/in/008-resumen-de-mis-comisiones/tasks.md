# TASKS — `RF-IN-008` Consultar el resumen de mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-008` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 07-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V83`**: `indicators:read-own-commissions-summary` (`…-5e7ad8000008`) a todo rol que porte `commission-batches:list-own`; guardas 206 / los mismos roles / contención. Los recuentos del catálogo, `SystemRolesSeedIT` y `RoleDetailIT` | — | Recuento a 206 | Pendiente |
| `T-02` | `CommissionBatchFigures.commissionsByStatus`, `CommissionFilter` y `CommissionTotals`; `JpaCommissionBatchFigures` con la sentencia de `plan.md` §4.3 | — | La persona es obligatoria | Pendiente |
| `T-03` | `OwnCommissionsSummaryResponse`; `GetOwnCommissionsSummaryService` —periodo, persona del token, los tres estados siempre, un estado desconocido al total—; `GetOwnCommissionsSummaryServiceTest` | `T-02` | Los tres bloques presentes con ceros | Pendiente |
| `T-04` | `CommissionIndicatorsController`: `GET /api/v1/indicators/commissions/mine/summary`, documentado | `T-03` | La ruta en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-05` | `OwnCommissionsSummaryIT`: `CA-IN-090` a `CA-IN-096`, limpiando lo que siembra | `T-01`, `T-04` | Una sentencia por lectura | Pendiente |
| `T-06` | Contrato regenerado; `api/index.md`, `requirements/cm.md` §3, `security.md` (sembrado), `requirements/in.md` y matriz | `T-05` | Solo altas | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-090`, `CA-IN-092` | `T-02`, `T-03`, `T-05` |
| `CA-IN-091` | `T-03`, `T-05` |
| `CA-IN-093` a `CA-IN-095` | `T-02`, `T-05` |
| `CA-IN-096` | `T-01`, `T-04`, `T-05` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Bloqueos declarados

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los siete criterios de aceptación con prueba (`OwnCommissionsSummaryIT`).
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**: solo altas.
- [ ] `requirements/in.md`, `requirements/cm.md`, `security.md`, `requirements.md` y `api/index.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
