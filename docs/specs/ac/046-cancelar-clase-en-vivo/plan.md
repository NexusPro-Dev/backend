# PLAN — `RF-AC-046` Cancelar una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-046` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

Bloquea la fila, comprueba el estado, **borra en Zoom** —un `404` de Zoom cuenta como borrado— y escribe la cancelación y la auditoría. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:cancel`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/service | **`CancelLiveSessionService`** |
| application | `CancelLiveSessionRequest` |
| domain/repository | `LiveSessionRepository.cancel` |
| interfaces | `LiveSessionController` — `POST /api/v1/live-sessions/{id}/cancellation` |

## 4. Contrato de API

`POST /api/v1/live-sessions/{id}/cancellation` — `live-sessions:cancel`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:cancel')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

`UPDATE` de `live_sessions` (de `PROGRAMADA` a `CANCELADA`, con el motivo): **no es una baja lógica** —la fila no se retira—, y por eso no va al registro de eliminaciones.

## 7. Transaccionalidad

`@Transactional`.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`DELETE /live-sessions/{id}`** | Diría que la clase desaparece, y se conserva |
| **Cancelar en la plataforma y dejar la reunión** | Quien guardó su enlace entraría igual |

## 9. Estrategia de prueba

Integración de API (`LiveSessionCancelIT`) con el doble de Zoom: `CA-AC-293`, `CA-AC-294`, `CA-AC-295`, `CA-AC-296`.
