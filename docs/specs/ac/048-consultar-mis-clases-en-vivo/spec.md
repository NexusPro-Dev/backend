# SPEC — `RF-AC-048` Consultar mis clases en vivo como instructor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-048` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que el instructor vea las clases de los cursos que dicta.

## 2. Contexto

`RF-AC-042` con la regla de propiedad delante (`RN-AC-028`): **solo las clases que cuelgan de un curso cuyo instructor es quien pregunta**. Las sueltas no salen: las gobierna administración. Si un curso cambia de instructor, sus clases pasan con él, porque la propiedad se lee del curso.

Hereda de su par de administración todo lo que no es la propiedad, y no lo repite.

## 3. Actores

| Actor | Permiso |
|---|---|
| Instructor | `live-sessions:list-own` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-028` | Solo las de sus cursos |
| `RN-AC-029` | El estado y `ended` |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `GET /api/v1/live-sessions/mine` | — | Identificadores `uuid` mal formados: `400` |

### 5.2 Salida

La página de `RF-AC-042`, con los mismos filtros.

## 6. Excepciones

Ninguna propia.

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-300` | Solo las clases de los cursos que dicta quien pregunta; ni las sueltas ni las de otros cursos; al cambiar el instructor del curso, pasan al nuevo |
| `CA-AC-301` | Sin `live-sessions:list-own`, `403` aunque porte `live-sessions:list` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
