# PLAN — `RF-AC-054` Entrar a una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-054` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

Bloquea la fila de la clase para que dos clics simultáneos de la misma persona no registren dos veces, comprueba estado y acceso —**las mismas llaves que el aula**, `StudentKeys` y `StudentAccess`—, busca el registro existente y, si no lo hay, pide a `ZoomMeetings.register` y guarda la fila. **El correo y el nombre salen de `SP`** (`UserCatalog` gana el correo, o `ClientCatalog` lo tiene). Zoom responde con el mismo registro si el correo ya estaba registrado, de modo que un reintento tras un fallo local no duplica. La infraestructura —el puerto de Zoom, las tablas, los permisos y los dos repositorios— la estrena `RF-AC-044` ([plan](../044-programar-clase-en-vivo/plan.md)), y aquí no se repite.

## 2. Cambios de esquema

Ninguno: `V94` (`RF-AC-044` · `T-01`) siembra `live-sessions:join`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/service | **`JoinLiveSessionService`** |
| domain/repository | `LiveSessionRepository.saveRegistration`, `LiveSessionQueryRepository.findRegistration` |
| application | `JoinLiveSessionResponse` |
| interfaces | `LiveClassroomController` — `POST /api/v1/live-sessions/available/{id}/registration` |
| users/application (SP) | El correo de la persona para registrarla, por la interfaz que ya publica la identidad |

## 4. Contrato de API

`POST /api/v1/live-sessions/available/{id}/registration` — `live-sessions:join`. Los códigos, los de [`spec.md`](spec.md) §6, más `401` sin sesión y `403` sin el permiso.

## 5. Autorización

`@PreAuthorize("hasAuthority('live-sessions:join')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

No audita: la fila de registro es el rastro.

## 7. Transaccionalidad

`@Transactional`; la llamada a Zoom con la fila bloqueada.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Dar el enlace general a quien tiene acceso** | Una vez dado, vale para todos (`RN-AC-026`) |
| **Exigir que el alumno inicie sesión en Zoom** | Cierra el reenvío a cambio de obligar a cada alumno a tener cuenta de Zoom (§5.2.15) |

## 9. Estrategia de prueba

Integración de API (`LiveClassroomIT`) con el doble de Zoom: `CA-AC-279`, `CA-AC-280`, `CA-AC-281`, `CA-AC-282`, `CA-AC-283`, `CA-AC-284`.
