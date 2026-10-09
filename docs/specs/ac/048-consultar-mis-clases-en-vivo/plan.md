# PLAN — `RF-AC-048` Consultar mis clases en vivo como instructor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-048` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

El `LiveSessionFilter` de `RF-AC-042` con **`instructorId`** = el actor, que añade `c.instructor_id = :instructor` al predicado. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:list-own`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/service | `ListLiveSessionsService.listOwn` |
| interfaces | `LiveSessionController` — `GET /api/v1/live-sessions/mine` |

## 4. Contrato de API

`GET /api/v1/live-sessions/mine` — `live-sessions:list-own`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:list-own')")`; la propiedad, dentro (`RN-AC-028`). Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una ruta con filtro por instructor y el permiso de administración** | `RN-SEG-014`: lo propio y lo ajeno se conceden por separado |

## 9. Estrategia de prueba

Integración de API (`OwnLiveSessionsIT`) con el doble de Zoom: `CA-AC-300`, `CA-AC-301`.
