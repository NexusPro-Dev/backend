# SPEC — `RF-AC-049` Programar una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-049` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que el instructor programe una clase de un curso que dicta.

## 2. Contexto

`RF-AC-044` con la propiedad delante (`RN-AC-028`): **`courseId` es obligatorio** y tiene que ser un curso cuyo instructor es quien pide; si no, `422`. Todo lo demás —Zoom, listas, validaciones, auditoría— es lo de `RF-AC-044`.

Hereda de su par de administración todo lo que no es la propiedad, y no lo repite.

## 3. Actores

| Actor | Permiso |
|---|---|
| Instructor | `live-sessions:create-own` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-025` | Las listas |
| `RN-AC-026` | La reunión |
| `RN-AC-028` | Un curso suyo |
| `RN-AC-029` | Inicio y fin |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `POST /api/v1/live-sessions/mine` | — | Identificadores `uuid` mal formados: `400` |

### 5.2 Salida

El detalle de `RF-AC-043`.

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | Sin `courseId`, o el curso no existe, está retirado o no lo dicta | `422` |
| `EX-002` | Membresía o producto que no valen | `422` |
| `EX-003` | Zoom falla | `503` |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-297` | Con un curso suyo, programa igual que `RF-AC-044` |
| `CA-AC-298` | Sin `courseId` o con un curso que no dicta: `422` y Zoom no recibe nada |
| `CA-AC-299` | Sin `live-sessions:create-own`, `403` aunque porte `live-sessions:create` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
