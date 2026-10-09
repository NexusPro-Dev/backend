# PLAN — `RF-AC-042` Consultar las clases en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-042` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

Una sentencia con las tres cuentas como subconsultas y otra para el total, con el predicado escrito por partes —sin parámetros nulos sin tipo, como `RF-MV-008`—. **Dos sentencias fijas.** La forma la comparte con el listado del instructor (`RF-AC-048`), que añade la propiedad al predicado. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:list`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/repository | `LiveSessionQueryRepository.search` y `count` con `LiveSessionFilter` |
| domain/service | **`ListLiveSessionsService`** |
| application | `ListLiveSessionsRequest`, `LiveSessionItem` |
| interfaces | `LiveSessionController` — `GET /api/v1/live-sessions` |

## 4. Contrato de API

`GET /api/v1/live-sessions` — `live-sessions:list`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:list')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Un estado `TERMINADA` guardado** | Habría que escribirlo con un proceso programado y podría mentir si falla; la hora de fin ya lo dice |

## 9. Estrategia de prueba

Integración de API (`LiveSessionListIT`) con el doble de Zoom: `CA-AC-273`, `CA-AC-274`, `CA-AC-275`.
