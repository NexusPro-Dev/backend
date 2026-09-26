# TASKS — `RF-MV-022` Consultar mis saldos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-022` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 26-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/pagos-y-saldos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración: `movements:read-own-balances` y `movements:list-own-entries`, por tipo de rol | `RF-MV-019` `T-01` | El catálogo cuenta dos más | Pendiente |
| `T-02` | `LedgerRepository.saldosDe` e `historialDe` | `RF-MV-019` `T-03` | Una sola sentencia por página | Pendiente |
| `T-03` | `MyBalancesService`, `MyEntriesService`, `EntryResponse`, `MyEntriesRequest` | `T-02` | Los `400` salen juntos | Pendiente |
| `T-04` | `MovementController`: `GET /mine/balances` y `GET /mine/balances/entries` | `T-03` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-05` | `MyBalancesIT`: `CA-MV-251` a `CA-MV-259` | `T-04`, `RF-MV-019` `T-07`, `RF-MV-020` `T-05`, `RF-MV-021` `T-05` | Estadísticas de Hibernate | Pendiente |
| `T-06` | `PermissionIT`, `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md`, `security.md` | `T-05` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-MV-019` a `RF-MV-021`**: `T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-251` a `CA-MV-254` | `T-02`, `T-03`, `T-05` |
| `CA-MV-255` a `CA-MV-258` | `T-02`, `T-03`, `T-05` |
| `CA-MV-259` | `T-04`, `T-05` |

---

## 4. Bloqueos

**`RF-MV-019`**, que crea las cuentas y los asientos.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
