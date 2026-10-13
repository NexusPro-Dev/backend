# SPEC — `RF-AC-056` Consultar el detalle de una clase en vivo de mi curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-056` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

---

## 1. Objetivo

Que el instructor vea **una clase de sus cursos entera**: descripción, listas de acceso, repetición y quién se registró.

## 2. Contexto

[`requirements/ac.md`](../../../requirements/ac.md) §5.2.17. El listado propio (`RF-AC-048`) trae la fila con sus cuentas, y el detalle de `RF-AC-043` es de administración: el formulario de corregir del instructor no tenía de dónde sacar la descripción ni las listas. **Es el detalle de `RF-AC-043` con la regla de propiedad delante** (`RN-AC-028`), como las otras cinco propias.

## 3. Actores

| Actor | Permiso |
|---|---|
| Instructor | `live-sessions:read-own` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-026` | Sin enlace general ni contraseña |
| `RN-AC-028` | Solo las clases de los cursos que dicta; lo ajeno, `404` |
| `RN-AC-031` | La repetición y la fecha siguiente |

## 5. Datos

**Entrada**: `GET /api/v1/live-sessions/mine/{id}`. **Salida**: `LiveSessionDetailResponse`, el de `RF-AC-043`.

## 6. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `VAL-001` | Identificador mal formado | `400` |
| `EX-001` | No existe, **o no es de un curso que dicta**, o es suelta | `404`, el mismo mensaje |

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-312` | El instructor ve **el detalle entero** de una clase de su curso —descripción, membresías, servicios, registrados, repetición—, también cancelada o terminada, sin enlaces de Zoom |
| `CA-AC-313` | Una clase de un curso que no dicta, una suelta o una inexistente: **`404`** con el mismo mensaje |
| `CA-AC-314` | Sin `live-sessions:read-own`, `403` aunque porte `live-sessions:read` o `live-sessions:list-own`; `V104` lo siembra donde está `courses:teach` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 13-10-2026 | Nace (`ac.md` v0.26.0 §5.2.17), a raíz de la pantalla del instructor. | Responsable técnico |
