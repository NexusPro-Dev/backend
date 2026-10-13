# SPEC — `RF-AC-057` Consultar los cursos que dicto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-057` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

!!! warning "Retirado el 13-10-2026"

    `RF-AC-057` se retiró el mismo día que nació, con `courses:list-own` (`V105`): las clases en vivo dejaron de colgar de un curso ([`requirements/ac.md`](../../../requirements/ac.md) §5.2.18, `RN-AC-032`), y ya no hay curso que elegir al programar. Ver [`RF-AC-058`](../058-portada-de-clase-en-vivo/spec.md). Este documento se conserva como historia.

---

## 1. Objetivo

Que el instructor sepa **de qué cursos puede programar clases en vivo** (`RF-AC-049`).

## 2. Contexto

[`requirements/ac.md`](../../../requirements/ac.md) §5.2.17. Programar una clase propia exige `courseId` de un curso que dicta, y `GET /courses` pide `courses:read`, que es de administración y abre el catálogo entero. **Una lista corta y propia**, no el catálogo.

## 3. Actores

| Actor | Permiso |
|---|---|
| Instructor | `courses:list-own` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-006` | El instructor del curso es quien pregunta |
| `RN-AC-028` | Los mismos cursos en los que puede programar: no retirados y suyos |

## 5. Datos

**Entrada**: `GET /api/v1/courses/mine`, sin parámetros. **Salida**: una lista sin paginar de `{ id, title, status }`, por `display_order` y título.

## 6. Excepciones

Ninguna propia: sin cursos, una lista vacía.

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-315` | Devuelve **los cursos no retirados que dicta quien pregunta**, activos e inactivos, con `id`, `title` y `status`, por orden de presentación; ni los de otros instructores ni los retirados |
| `CA-AC-316` | Sin cursos, `[]`; sin `courses:list-own`, `403` aunque porte `courses:teach`; `V104` lo siembra donde está `courses:teach` |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 13-10-2026 | Nace (`ac.md` v0.26.0 §5.2.17), a raíz de la pantalla del instructor. | Responsable técnico |
