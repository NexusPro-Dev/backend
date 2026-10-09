# TASKS — `RF-AC-044` Programar una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-044` |
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
| `T-01` | `V94`: las cuatro tablas y los trece permisos, con sus guardas | — | La migración aplica; catálogo 232 | Pendiente |
| `T-02` | `shared/zoom`: puerto, cliente, ajustes y excepción; `application.yml` y `docker-compose.yml` | — | `ZoomApiClientTest` | Pendiente |
| `T-03` | `LiveSessionSchedule` | — | `LiveSessionScheduleTest` | Pendiente |
| `T-04` | `LiveSessionRepository`, `LiveSessionQueryRepository.findDetail`, `LiveSessionKeys` | `T-01` | Integración por `T-07` | Pendiente |
| `T-05` | `ScheduleLiveSessionService` con la compensación | `T-02` a `T-04` | — | Pendiente |
| `T-06` | `LiveSessionController`: `POST /api/v1/live-sessions` | `T-05` | Entra en `PERMISO_DE_CADA_OPERACION` | Pendiente |
| `T-07` | El doble de Zoom para las pruebas y `ScheduleLiveSessionIT` | `T-06` | `CA-AC-263` a `CA-AC-268` | Pendiente |
| `T-08` | Las suites que cuentan el catálogo y los permisos de `ADMIN` | `T-01` | Suite completa en verde | Pendiente |
| `T-09` | OpenAPI, `docs/api/index.md`, la matriz y `.env.example` | `T-07` | El contrato declara `201`, `400`, `401`, `403`, `422`, `503` | Pendiente |

## 2. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-263`, `CA-AC-264` | `T-02`, `T-05`, `T-07` |
| `CA-AC-265` | `T-03`, `T-07` |
| `CA-AC-266`, `CA-AC-267`, `CA-AC-268` | `T-04`, `T-05`, `T-07` |

## 3. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Las credenciales de la app Server-to-Server de Zoom, para probar contra Zoom de verdad. **No bloquea la construcción**: las pruebas usan el doble | 09-10-2026 | Responsable del proyecto | Abierto |

## 4. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
