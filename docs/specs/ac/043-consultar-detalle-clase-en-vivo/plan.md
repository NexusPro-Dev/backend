# PLAN — `RF-AC-043` Consultar el detalle de una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-043` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

Una lectura sobre `live_sessions` con el curso, dos sobre las listas —las de `CourseQueryRepository` para el curso, con `live_session_id`— y una sobre `live_session_registrations`; la identidad de los registrados, en una llamada a `UserCatalog.findAll`. **Cinco sentencias fijas y un puerto**. Es la respuesta que devuelven también programar y corregir. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:read`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/repository | `LiveSessionQueryRepository.findDetail`, `findMembershipsOf`, `findProductsOf`, `findRegistrationsOf` |
| domain/service | **`GetLiveSessionService`** (y `LiveSessionDetailReader`, que comparten las escrituras) |
| application | `LiveSessionDetailResponse` |
| interfaces | `LiveSessionController` — `GET /api/v1/live-sessions/{id}` |

## 4. Contrato de API

`GET /api/v1/live-sessions/{id}` — `live-sessions:read`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:read')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

No audita: es una lectura.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Publicar el enlace general a administración** | Basta con que una persona lo copie para que la lista deje de valer (`RN-AC-026`); administración entra como anfitrión (`RF-AC-047`) |

## 9. Estrategia de prueba

Integración de API (`LiveSessionDetailIT`) con el doble de Zoom: `CA-AC-271`, `CA-AC-272`.
