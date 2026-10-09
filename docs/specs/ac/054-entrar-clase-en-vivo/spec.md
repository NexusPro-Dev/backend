# SPEC — `RF-AC-054` Entrar a una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-054` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que quien tiene acceso entre a la reunión de Zoom, y nadie más.

## 2. Contexto

Es lo que el responsable del proyecto pidió: «que solo los que tienen el acceso a dicho evento puedan verlo». **Entrar es registrarse** (`RN-AC-027`): la plataforma comprueba en ese momento que la clase está programada, no terminó y **se le abre** a quien pide; lo registra en Zoom con su nombre y su correo, y le devuelve **su enlace personal**. **La segunda vez devuelve el mismo sin llamar a Zoom.** Quien no tiene acceso recibe un `403` con lo que abre la clase, como una lección cerrada. El enlace personal **sirve a un dispositivo a la vez** y reenviarlo no se puede impedir (§5.2.15).

## 3. Actores

| Actor | Permiso |
|---|---|
| Alumno | `live-sessions:join` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-025` | El acceso |
| `RN-AC-027` | Registrar y devolver el enlace personal |
| `RN-AC-029` | Programada y sin terminar |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `POST /api/v1/live-sessions/available/{id}/registration` | — | Identificadores `uuid` mal formados: `400` |

### 5.2 Salida

`{ joinUrl, startsAt, endsAt, registeredAt }`; `201` la primera vez y `200` las siguientes.

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | No existe, está cancelada o terminó | `404` |
| `EX-002` | No se le abre | `403` con `memberships` y `products` |
| `EX-003` | Zoom falla o no responde | `503`, sin registro |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-279` | Con acceso: Zoom recibe el registro con el nombre y el correo de quien pide, la fila queda en `live_session_registrations` y responde `201` con **su** enlace |
| `CA-AC-280` | **La segunda vez**: `200` con el mismo enlace y **Zoom no recibe nada** |
| `CA-AC-281` | Sin acceso: `403` `EX-002` con `memberships` y `products`, sin llamar a Zoom ni dejar fila; una clase sin llaves se abre a todos |
| `CA-AC-282` | Cancelada, terminada o inexistente: `404` con el mismo mensaje; **el acceso se comprueba en cada petición**: quien ya está registrado y perdió la membresía recibe `403` y no su enlace |
| `CA-AC-283` | Zoom falla: `503` y ninguna fila; no se audita |
| `CA-AC-284` | Sin `live-sessions:join`, `403` aunque porte `live-sessions:learn` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
