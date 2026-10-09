# PLAN — `RF-AC-053` Consultar las clases en vivo como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-053` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

Una sentencia de clases vigentes con las listas como arreglos agregados, otra para los registros de quien mira, y `StudentKeys` para las llaves —los dos puertos de `SP`—; `StudentAccess` decide. Reutiliza todo lo del aula. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:learn`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/repository | `LiveSessionQueryRepository.findUpcoming`, `registeredOf` |
| domain/service | **`GetAvailableLiveSessionsService`** |
| application | `AvailableLiveSessionsRequest`, `AvailableLiveSessionItem` |
| interfaces | **`LiveClassroomController`** — `GET /api/v1/live-sessions/available` |

## 4. Contrato de API

`GET /api/v1/live-sessions/available` — `live-sessions:learn`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:learn')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Esconder las que no se abren** | Rompe la vitrina que invita a subir de nivel (`ac.md` §1.4) |

## 9. Estrategia de prueba

Integración de API (`AvailableLiveSessionsIT`) con el doble de Zoom: `CA-AC-276`, `CA-AC-277`, `CA-AC-278`.
