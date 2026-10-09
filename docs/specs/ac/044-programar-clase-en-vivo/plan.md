# PLAN — `RF-AC-044` Programar una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-044` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

**Zoom detrás de un puerto.** `shared/zoom` publica **`ZoomMeetings`** —crear, corregir, borrar, registrar y pedir el enlace de anfitrión— y su implementación **`ZoomApiClient`** sobre `RestClient`, como `shared/video` con los proveedores de video: dirección fija y configurable, plazo corto, y **`ZoomUnavailableException`** para todo lo que no sea una respuesta buena. El token de **Server-to-Server OAuth** (`POST /oauth/token?grant_type=account_credentials`) se pide con las credenciales y **se reutiliza hasta un minuto antes de caducar**. Las pruebas reemplazan el puerto por **un doble en memoria** que anota lo que recibe y puede fallar a voluntad.

**La regla en un objeto puro.** `LiveSessionSchedule` (`domain/models`) normaliza inicio y fin —la zona por omisión—, valida `RN-AC-029` contra un `Clock` y calcula la duración en minutos para Zoom.

**El servicio en tres pasos**: validar todo —campos, curso, membresías y productos, con los puertos de `SP` y `PM` que ya existen (`MembershipCatalog`, `ProductCatalog`)—; **crear la reunión**; y escribir clase, listas y auditoría **en una transacción**. Si la transacción falla, un bloque de compensación **borra la reunión** y relanza.

**Las escrituras, en SQL nativo** (`LiveSessionRepository`), como `lesson_progress`: no hay entidad con comportamiento que justifique JPA, y reemplazar listas es borrar e insertar.

## 2. Cambios de esquema

**`V94__ac_clases_en_vivo.sql`**: las cuatro tablas de `ac.md` §8.10 a §8.13 con sus restricciones e índice, y **los trece permisos**: los seis de administración a `SUPERADMIN` y `ADMIN`; los cinco propios a todo rol que porte `courses:teach`; los dos del alumno a todo rol que porte `courses:learn`. Guardas: catálogo **232**, `SUPERADMIN` 232, `ADMIN` 230, contención de `RN-SEG-003`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `shared/zoom` | **`ZoomMeetings`**, **`ZoomApiClient`**, **`ZoomSettings`**, **`ZoomUnavailableException`** | `shared` |
| `domain/models` | **`LiveSessionSchedule`** | `AC` |
| `domain/repository` | **`LiveSessionRepository`** (escrituras) y **`LiveSessionQueryRepository`** (lecturas) | `AC` |
| `domain/service` | **`ScheduleLiveSessionService`**, con la validación de referencias en **`LiveSessionKeys`** | `AC` |
| `application` | **`ScheduleLiveSessionRequest`**, **`LiveSessionDetailResponse`** | `AC` |
| `interfaces` | **`LiveSessionController`** — `POST /api/v1/live-sessions` | `AC` |
| configuración | `application.yml` (`nexus.zoom.*`), `docker-compose.yml` | — |

## 4. Contrato de API

`POST /api/v1/live-sessions` — `live-sessions:create`.

```json
{ "title": "Análisis de velas en vivo", "description": "…", "courseId": "…",
  "startsAt": "2026-10-15T19:00:00-05:00", "endsAt": "2026-10-15T20:30:00-05:00",
  "membershipIds": ["…"], "productIds": [] }
```

`201` con `LiveSessionDetailResponse` (`RF-AC-043`). `400`, `401`, `403`, `422`, `503`.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:create')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

`CREATE` de `live_sessions` con la clase y sus listas en el estado nuevo, por el servicio de auditoría de cambios que usan las demás altas de `AC`.

## 7. Transaccionalidad

La reunión **fuera** de la transacción —una llamada externa no se mete en una transacción de base—, la escritura **dentro**, y la compensación si la transacción no confirma.

## 8. Impacto sobre otros módulos y documentos

`shared` gana un paquete; `ac.md`, `modelo-datos.md`, `security.md` y `architecture.md` ya están enmendados (v0.23.1, 0.112.0, 0.129.0, 0.50.0). Las suites que cuentan el catálogo pasan a `232L` y las que cuentan los permisos de `ADMIN`, a 230.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Guardar primero y crear la reunión después** | Una clase sin reunión que el alumno ve y no puede abrir |
| **Llamar a Zoom dentro de la transacción** | Mantiene una conexión de base abierta mientras se espera a un tercero |
| **El SDK de Zoom** | No hay uno oficial para Java en el servidor; son cinco llamadas REST |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Una reunión huérfana** si la compensación también falla | Se registra como error con el identificador de la reunión; administración la borra en Zoom |
| 2 | **El enlace general se cuela en una respuesta** | `CA-AC-264` busca la URL del doble en la respuesta y en la base |
| 3 | **El token caduca a mitad** | Se renueva un minuto antes; un `401` de Zoom invalida el guardado y reintenta una vez |

## 11. Estrategia de prueba

- **Unitarias**: `LiveSessionScheduleTest` (zona, límites); `ZoomApiClientTest` contra un servidor HTTP simulado (cuerpo de la reunión, token reutilizado, fallos).
- **Integración** (`ScheduleLiveSessionIT`): `CA-AC-263` a `CA-AC-268` con el doble.
