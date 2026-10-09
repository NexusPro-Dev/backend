# PLAN — `RF-AC-045` Corregir una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-045` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

Lee la clase con bloqueo de fila, aplica los cambios sobre una copia, valida con `LiveSessionSchedule` y `LiveSessionKeys`, **llama a Zoom solo si cambió algo que Zoom conoce**, y escribe y audita. **Zoom dentro de la transacción esta vez**: la fila ya existe y está bloqueada, y si Zoom falla no se escribe nada; si Zoom acepta y la escritura falla, la reunión queda con los datos nuevos y la siguiente corrección la alinea — se acepta. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:update`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/service | **`UpdateLiveSessionService`** |
| application | `UpdateLiveSessionRequest` |
| domain/repository | `LiveSessionRepository.update`, `replaceMemberships`, `replaceProducts` |
| interfaces | `LiveSessionController` — `PATCH /api/v1/live-sessions/{id}` |

## 4. Contrato de API

`PATCH /api/v1/live-sessions/{id}` — `live-sessions:update`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:update')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

`UPDATE` de `live_sessions`, con las listas en el antes y el después.

## 7. Transaccionalidad

`@Transactional`, con la llamada a Zoom antes de escribir.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una ruta por lista** (dar y quitar) | Seis requerimientos más para una pantalla que edita la clase entera |
| **Des-registrar en Zoom a quien sale de la lista** | Ver §2 |

## 9. Estrategia de prueba

Integración de API (`LiveSessionUpdateIT`) con el doble de Zoom: `CA-AC-288`, `CA-AC-289`, `CA-AC-290`, `CA-AC-291`, `CA-AC-292`.
