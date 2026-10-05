# TASKS — `RF-MV-054` Consultar los saldos de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-054` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 05-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Permiso en `V73` | `RF-MV-053` `T-01` | | Pendiente |
| `T-02` | `BalanceService.balancesOf(persona)`; «mis saldos» lo reutiliza | — | `BalancesAndBonusIT` sigue en verde | Pendiente |
| `T-03` | `GET /users/{userId}/balances` | `T-02` | Documentado | Pendiente |
| `T-04` | Pruebas `CA-MV-656` a `CA-MV-659` | `T-03` | | Pendiente |

---

## 2. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los cuatro criterios con prueba.
- [ ] Contrato OpenAPI regenerado, con la prosa releída.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
