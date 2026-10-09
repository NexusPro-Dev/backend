# SPEC — `RF-AC-050` Corregir una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-050` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que el instructor corrija una clase de sus cursos.

## 2. Contexto

`RF-AC-045` con la propiedad delante (`RN-AC-028`): una clase que no es de sus cursos responde **`404`**, como si no existiera; y **no puede moverla a un curso que no dicta** ni dejarla suelta (`422`).

Hereda de su par de administración todo lo que no es la propiedad, y no lo repite.

## 3. Actores

| Actor | Permiso |
|---|---|
| Instructor | `live-sessions:update-own` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-025` | Las listas |
| `RN-AC-026` | Zoom primero |
| `RN-AC-028` | Solo las suyas |
| `RN-AC-029` | Estado, inicio y fin |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `PATCH /api/v1/live-sessions/mine/{id}` | — | Identificadores `uuid` mal formados: `400` |

### 5.2 Salida

El detalle de `RF-AC-043`.

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | No existe o no es de sus cursos | `404` |
| `EX-002` | Mover a un curso que no dicta, o dejarla suelta | `422` |
| `EX-003` | Cancelada o terminada | `409` |
| `EX-004` | Zoom falla | `503` |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-302` | Corrige una clase de su curso igual que `RF-AC-045`; una de otro curso, `404` |
| `CA-AC-303` | Moverla a un curso que no dicta o a ninguno, `422`; sin `live-sessions:update-own`, `403` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
