# PLAN — `RF-AC-047` Iniciar una clase en vivo como anfitrión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-047` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

Lee la clase, comprueba el estado y pide a `ZoomMeetings.hostLink` el enlace de inicio. **`POST` y no `GET`**: produce algo nuevo cada vez y deja un registro. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:host`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/service | **`HostLiveSessionService`** |
| application | `HostLinkResponse` |
| interfaces | `LiveSessionController` — `POST /api/v1/live-sessions/{id}/host-link` |

## 4. Contrato de API

`POST /api/v1/live-sessions/{id}/host-link` — `live-sessions:host`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:host')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

Un evento de seguridad de severidad media —«entrega del enlace de anfitrión»— con la clase y quien lo pidió, **sin el enlace**.

## 7. Transaccionalidad

`@Transactional` para la auditoría; Zoom antes.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Hacer anfitrión alternativo al instructor** | Exige que el instructor tenga licencia en la cuenta (`ac.md` §5.2.15) |
| **Guardar el enlace de inicio** | Caduca, y guardado es una llave de la reunión en una tabla |

## 9. Estrategia de prueba

Integración de API (`LiveSessionAdminIT`) con el doble de Zoom: `CA-AC-285`, `CA-AC-286`, `CA-AC-287`.
