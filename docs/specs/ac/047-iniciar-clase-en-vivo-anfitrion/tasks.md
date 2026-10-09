# TASKS — `RF-AC-047` Iniciar una clase en vivo como anfitrión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-047` |
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
| `T-01` | **`HostLiveSessionService`**; `HostLinkResponse`; `LiveSessionController` — `POST /api/v1/live-sessions/{id}/host-link` | `RF-AC-044` · `T-01` a `T-04` | — | Hecha |
| `T-02` | `LiveSessionAdminIT` | `T-01` | `CA-AC-285`, `CA-AC-286`, `CA-AC-287` | Hecha |
| `T-03` | OpenAPI con la prosa, `docs/api/index.md` y la matriz | `T-02` | La ruta entra en `PERMISO_DE_CADA_OPERACION` | Hecha |

## 2. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno |  |  |  |

## 3. Definición de terminado

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
