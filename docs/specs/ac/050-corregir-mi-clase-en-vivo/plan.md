# PLAN — `RF-AC-050` Corregir una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-050` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

`UpdateLiveSessionService.updateOwn`: carga la clase con el instructor de su curso; si no es el actor, `404`; valida el curso nuevo si viene; y sigue el camino de `RF-AC-045`. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:update-own`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/service | `UpdateLiveSessionService.updateOwn` |
| interfaces | `LiveSessionController` — `PATCH /api/v1/live-sessions/mine/{id}` |

## 4. Contrato de API

`PATCH /api/v1/live-sessions/mine/{id}` — `live-sessions:update-own`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:update-own')")`; la propiedad, dentro (`RN-AC-028`). Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

La de `RF-AC-045`.

## 7. Transaccionalidad

La de `RF-AC-045`.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`403` para la clase ajena** | Diría que existe |

## 9. Estrategia de prueba

Integración de API (`OwnLiveSessionsIT`) con el doble de Zoom: `CA-AC-302`, `CA-AC-303`.
