# PLAN — `RF-AC-056` Consultar el detalle de una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-056` |
| Especificación | [`spec.md`](spec.md), aprobada el 13-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

---

## 1. Enfoque

`ListLiveSessionsService.getOwn(id)`: lee la clase, aplica `LiveSessionSupport.exigirPropia` —que ya responde el `404` de lo ajeno para corregir, cancelar e iniciar— y devuelve `apoyo.detalle(id)`. Nada nuevo en los repositorios: la argumentación de la propiedad es la de [`RF-AC-050`](../050-corregir-mi-clase-en-vivo/plan.md).

## 2. Cambios de esquema

`V104`: el permiso `live-sessions:read-own`, sembrado donde está `courses:teach` (junto con el de `RF-AC-057`), con sus guardas.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/service | `ListLiveSessionsService.getOwn` |
| interfaces | `LiveSessionController` — `GET /api/v1/live-sessions/mine/{id}` |

## 4. Contrato de API

`GET /api/v1/live-sessions/mine/{id}` — `live-sessions:read-own`. `200`, `400`, `401`, `403`, `404`.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:read-own')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

Ninguna: es una lectura, como `RF-AC-043`.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Meter el detalle en cada fila del listado propio** | Un listado paginado con listas y registrados por fila; y el formulario pide una clase |
| **Reusar `live-sessions:list-own`** | Un permiso por ruta (`RN-SEG-014`); el responsable eligió dos permisos nuevos |

## 9. Estrategia de prueba

Integración de API en `OwnLiveSessionsIT`: `CA-AC-312` a `CA-AC-314`; `EndpointPermissionsIT` y las suites que cuentan el catálogo.
