# TASKS — `RF-SP-081` Eliminar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-081` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 08-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `ManageBrokerAccountsService.delete` y sus dos rutas (`plan.md` §1 y §3) | `RF-SP-053` `T-04`, `T-05` | | Pendiente |
| `T-02` | `ManageBrokerAccountsIT`: `CA-SP-931` a `CA-SP-936` | `T-01` | Cada criterio afirmado en el cuerpo | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-931` a `CA-SP-936` | `T-01`, `T-02` |

## 3. Bloqueos

Ninguno.
