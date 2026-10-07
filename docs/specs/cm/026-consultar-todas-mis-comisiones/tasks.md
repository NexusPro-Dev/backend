# TASKS — `RF-CM-026` Consultar todas mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-026` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 07-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V81`**: el permiso a todo rol que porte `commission-batches:list-own`, `ix_commissions_user`, y las guardas; el catálogo 204 → 205 | — | Las suites de siembra y `PermissionIT` en verde | Pendiente |
| `T-02` | `searchOwn` y `countOwn` en el repositorio de consulta, reutilizando `COMISION` y `comision(f)` | `T-01` | — | Pendiente |
| `T-03` | `MyCommissionsRequest`, `MyCommissionItem` y `MyCommissionPageResponse`, con `@Schema(name)` | — | — | Pendiente |
| `T-04` | `CommissionBatchQueryService.listOwnCommissions`: los `400` juntos | `T-02`, `T-03` | — | Pendiente |
| `T-05` | `GET /mine/commissions`, en `PERMISO_DE_CADA_OPERACION` | `T-04` | `EndpointPermissionsIT` | Pendiente |
| `T-06` | `MyCommissionsIT`: `CA-CM-347` a `CA-CM-355` | `T-05` | — | Pendiente |
| `T-07` | Contrato OpenAPI (`api/index.md`); `security.md` (sembrado) y `requirements.md` | `T-06` | Diff del `json` | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-03` → `T-02` → `T-04` → `T-05` → `T-06` → `T-07`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-347` a `CA-CM-354` | `T-02`, `T-04`, `T-06` |
| `CA-CM-355` | `T-01`, `T-05`, `T-06` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato, `security.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
