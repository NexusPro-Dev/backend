# TASKS — `RF-AC-052` Iniciar como anfitrión una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-052` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 09-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `HostLiveSessionService.hostOwn`; `LiveSessionController` — `POST /api/v1/live-sessions/mine/{id}/host-link` | `RF-AC-044` · `T-01` a `T-04` | — | Pendiente |
| `T-02` | `OwnLiveSessionsIT` | `T-01` | `CA-AC-305` | Pendiente |
| `T-03` | OpenAPI con la prosa, `docs/api/index.md` y la matriz | `T-02` | La ruta entra en `PERMISO_DE_CADA_OPERACION` | Pendiente |

## 2. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno |  |  |  |

## 3. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
