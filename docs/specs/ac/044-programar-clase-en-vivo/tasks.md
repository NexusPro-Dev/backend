# TASKS — `RF-AC-044` Programar una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-044` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 09-10-2026 |
| Estado | **En desarrollo** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V94`: las cuatro tablas y los trece permisos, con sus guardas | — | La migración aplica; catálogo 232 | Hecha |
| `T-02` | `shared/zoom`: puerto, cliente, ajustes y excepción; `application.yml` y `docker-compose.yml` | — | `ZoomApiClientTest` | Hecha |
| `T-03` | `LiveSessionSchedule` | — | `LiveSessionScheduleTest` | Hecha |
| `T-04` | `LiveSessionRepository`, `LiveSessionQueryRepository.findDetail`, `LiveSessionKeys` | `T-01` | Integración por `T-07` | Hecha |
| `T-05` | `ScheduleLiveSessionService` con la compensación | `T-02` a `T-04` | — | Hecha |
| `T-06` | `LiveSessionController`: `POST /api/v1/live-sessions` | `T-05` | Entra en `PERMISO_DE_CADA_OPERACION` | Hecha |
| `T-07` | El doble de Zoom para las pruebas y `LiveSessionAdminIT` | `T-06` | `CA-AC-263` a `CA-AC-268` | Hecha |
| `T-08` | Las suites que cuentan el catálogo y los permisos de `ADMIN` | `T-01` | Suite completa en verde | Hecha |
| `T-09` | OpenAPI, `docs/api/index.md`, la matriz y `.env.example` | `T-07` | El contrato declara `201`, `400`, `401`, `403`, `422`, `503` | Hecha |

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

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.

## Desviaciones respecto del plan

- **Menos clases que las que el plan nombra, con el mismo reparto**: la validación de referencias, la propiedad, el estado y el detalle viven en `LiveSessionSupport` (en lugar de `LiveSessionKeys` y un lector aparte); `ListLiveSessionsService` sirve también el detalle; y `LiveClassroomService` junta la vitrina y entrar. Las dos interfaces del repositorio las implementa `JpaLiveSessionRepository`.
- **Tres suites y no una por requerimiento**: `LiveSessionAdminIT` (`RF-AC-042` a `RF-AC-047`), `OwnLiveSessionsIT` (`RF-AC-048` a `RF-AC-052`) y `LiveClassroomIT` (`RF-AC-053`, `RF-AC-054`), con el doble `FakeZoomMeetings` —un bean `@Primary` de la suite, no un `@MockitoBean`, para no abrir un contexto por suite—.
- **El correo para registrar en Zoom** lo publica `SP` con una interfaz propia, `UserContactLookup`, y no `UserCatalog`, cuya identidad se enseña en listados.
- **`.env.example` no se tocó en este commit**: tenía cambios locales de otra sesión sin confirmar. Las cuatro variables `ZOOM_*` están en `application.yml`, `docker-compose.yml` y `architecture.md` §11; añadirlas al ejemplo queda pendiente.
