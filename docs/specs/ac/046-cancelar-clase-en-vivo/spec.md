# SPEC — `RF-AC-046` Cancelar una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-046` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que la clase no ocurra.

## 2. Contexto

**Cancelar borra la reunión en Zoom** —así nadie entra aunque guarde su enlace— y **conserva la fila** con fecha y motivo (`RN-AC-029`). **No se deshace**: para repetirla se programa otra. Una reunión que ya no existe en Zoom —la borraron a mano— no impide cancelar.

## 3. Actores

| Actor | Permiso |
|---|---|
| Administrador | `live-sessions:cancel` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-026` | La reunión se borra |
| `RN-AC-029` | Con motivo, sin deshacer |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `POST /api/v1/live-sessions/{id}/cancellation` | — | Identificadores `uuid` mal formados: `400` |
| `reason` | Sí | 1 a 500 caracteres |

### 5.2 Salida

El detalle de `RF-AC-043`, `CANCELADA`.

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | La clase no existe | `404` |
| `EX-003` | Ya está cancelada o terminó | `409` |
| `EX-004` | Zoom falla (salvo «no existe») | `503`, nada cambia |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-293` | Cancela: Zoom recibe el borrado de la reunión, la fila queda `CANCELADA` con fecha y motivo, se audita |
| `CA-AC-294` | Sin motivo, `400`; ya cancelada o terminada, `409` |
| `CA-AC-295` | La reunión ya no existe en Zoom: se cancela igual; Zoom falla de otra forma: `503` y la clase sigue programada |
| `CA-AC-296` | Una clase cancelada **no se ofrece al alumno** y entrar responde `404`; sin `live-sessions:cancel`, `403` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
