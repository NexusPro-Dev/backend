# SPEC — `RF-AC-051` Cancelar una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-051` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que el instructor cancele una clase de sus cursos.

## 2. Contexto

`RF-AC-046` con la propiedad delante (`RN-AC-028`): una clase que no es de sus cursos responde `404`.

Hereda de su par de administración todo lo que no es la propiedad, y no lo repite.

## 3. Actores

| Actor | Permiso |
|---|---|
| Instructor | `live-sessions:cancel-own` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-026` | La reunión se borra |
| `RN-AC-028` | Solo las suyas |
| `RN-AC-029` | Con motivo |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `POST /api/v1/live-sessions/mine/{id}/cancellation` | — | Identificadores `uuid` mal formados: `400` |
| `reason` | Sí | 1 a 500 caracteres |

### 5.2 Salida

El detalle de `RF-AC-043`, `CANCELADA`.

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | No existe o no es de sus cursos | `404` |
| `EX-003` | Ya cancelada o terminada | `409` |
| `EX-004` | Zoom falla | `503` |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-304` | Cancela una clase de su curso igual que `RF-AC-046`; una de otro curso o suelta, `404`; sin `live-sessions:cancel-own`, `403` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
