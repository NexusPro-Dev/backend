# TASKS — `RF-MV-039` Consultar las cuentas de cobro de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-039` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/cuentas-de-cobro` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PayoutAccountRepository.of(userId, incluirBajas)` con el orden de `plan.md` §1 | `RF-MV-036` · `T-01` | Una sentencia | Pendiente |
| `T-02` | `PayoutAccountService.listOf` y `deletedAt` en `PayoutAccountResponse` | `T-01` | `404` por la lectura del titular | Pendiente |
| `T-03` | `GET /movements/users/{userId}/payout-accounts` | `T-02` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-04` | `ListUserPayoutAccountsIT`: `CA-MV-410` a `CA-MV-414` | `T-03` | Cada criterio afirmado en el cuerpo de la prueba | Pendiente |
| `T-05` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-04` | | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05`. Después de `RF-MV-036`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-410`, `CA-MV-411` | `T-01`, `T-04` |
| `CA-MV-412` | `T-02`, `T-04` |
| `CA-MV-413`, `CA-MV-414` | `T-03`, `T-04` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los cinco criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
