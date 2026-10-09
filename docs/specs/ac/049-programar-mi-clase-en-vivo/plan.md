# PLAN — `RF-AC-049` Programar una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-049` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

`ScheduleLiveSessionService.scheduleOwn`: comprueba la propiedad del curso y delega en el mismo camino que `RF-AC-044`. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:create-own`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/service | `ScheduleLiveSessionService.scheduleOwn` |
| interfaces | `LiveSessionController` — `POST /api/v1/live-sessions/mine` |

## 4. Contrato de API

`POST /api/v1/live-sessions/mine` — `live-sessions:create-own`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:create-own')")`; la propiedad, dentro (`RN-AC-028`). Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

La de `RF-AC-044`.

## 7. Transaccionalidad

La de `RF-AC-044`.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Dejar al instructor programar clases sueltas** | No habría de qué leer su propiedad (`RN-AC-028`) |

## 9. Estrategia de prueba

Integración de API (`OwnLiveSessionsIT`) con el doble de Zoom: `CA-AC-297`, `CA-AC-298`, `CA-AC-299`.
