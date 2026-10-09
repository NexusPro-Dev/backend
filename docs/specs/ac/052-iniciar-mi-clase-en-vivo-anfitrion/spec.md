# SPEC — `RF-AC-052` Iniciar como anfitrión una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-052` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que el instructor entre como anfitrión a una clase de sus cursos.

## 2. Contexto

`RF-AC-047` con la propiedad delante (`RN-AC-028`). **Es el caso para el que existe `RN-AC-030`**: el instructor dicta la clase con la cuenta del negocio sin tener él licencia de Zoom.

Hereda de su par de administración todo lo que no es la propiedad, y no lo repite.

## 3. Actores

| Actor | Permiso |
|---|---|
| Instructor | `live-sessions:host-own` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-028` | Solo las suyas |
| `RN-AC-029` | Programada y sin terminar |
| `RN-AC-030` | El enlace de anfitrión |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `POST /api/v1/live-sessions/mine/{id}/host-link` | — | Identificadores `uuid` mal formados: `400` |

### 5.2 Salida

`{ startUrl, startsAt, endsAt }`.

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | No existe o no es de sus cursos | `404` |
| `EX-003` | Cancelada o terminada | `409` |
| `EX-004` | Zoom falla | `503` |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-305` | Devuelve el enlace de inicio de una clase de su curso y audita la entrega; una de otro curso, `404`; sin `live-sessions:host-own`, `403` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
