# SPEC — `RF-AC-043` Consultar el detalle de una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-043` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Ver una clase entera: su acceso, su reunión y quién se registró para entrar.

## 2. Contexto

Es la vista con la que administración revisa una clase antes y después de dictarla. **Trae la lista de registrados** —quién pulsó «Entrar» y cuándo (`RN-AC-027`)—, que es lo más parecido a una asistencia que la plataforma puede saber sin preguntar a Zoom. **Del lado de Zoom solo trae el identificador de la reunión**: el enlace general no existe en la plataforma y los enlaces personales son de cada registrado (`RN-AC-026`).

## 3. Actores

| Actor | Permiso |
|---|---|
| Administrador | `live-sessions:read` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-025` | Las dos listas, resueltas |
| `RN-AC-027` | Los registrados |
| `RN-AC-029` | El estado y si terminó |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `GET /api/v1/live-sessions/{id}` | — | Identificadores `uuid` mal formados: `400` |

### 5.2 Salida

`id`, `title`, `description`, `course {id, title}` o nulo, `startsAt`, `endsAt`, `status`, `ended`, `cancelledAt`, `cancellationReason`, `zoomMeetingId`, `memberships [{id, code, name, color}]`, `products [{id, code, name}]`, `registrations [{userId, username, fullName, registeredAt}]`, `createdBy`, `createdAt`, `updatedAt`. **Sin `joinUrl` de nadie.**

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | La clase no existe | `404` |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-271` | Trae la clase con sus dos listas resueltas, su curso, `ended` calculado y los registrados con su identidad y fecha, por fecha de registro |
| `CA-AC-272` | No trae ningún enlace —ni el general ni los personales—; una cancelada trae fecha y motivo; inexistente, `404`; sin `live-sessions:read`, `403` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
