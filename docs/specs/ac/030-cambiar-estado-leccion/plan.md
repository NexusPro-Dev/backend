# PLAN — `RF-AC-030` Cambiar el estado de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-030` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`RF-AC-024` con la condición dentro del agregado.** La única condición —contenido— la conoce `Lesson`, de modo que `activate` la comprueba sin ninguna lectura más: es el único cambio de estado del módulo cuya condición no mira a otra tabla.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `ChangeLessonStatusRequest` | `AC` |
| `domain/models` | `Lesson.activate` —lanza el `409` si no hay contenido—, `deactivate` | `AC` |
| `domain/service` | `ChangeLessonStatusService` | `AC` |
| `interfaces` | `LessonController` — `PATCH …/lessons/{lessonId}/status` | `AC` |

## 4. Contrato de API

`PATCH /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/status` — `courses:update`. `{ "status": "ACTIVO" }` → `200` con `LessonResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001`, `VAL-002` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` de `lessons` con `status` antes y después. Sin fila si no cambió.

## 7. Transaccionalidad

`@Transactional`. La lección de su módulo y curso con `FOR UPDATE`; escritura y auditoría si cambió; relectura. **Dos sentencias** y la relectura: la condición no lee nada.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Desactivar el módulo en cascada** | `RN-AC-009` |
| **Bloquear el módulo** | `RF-AC-024` §14.1: la carrera con la activación del módulo tiene un resultado legítimo |
| **La condición en el caso de uso** | El agregado tiene el contenido; sacarla sería la única condición de estado fuera de donde vive el dato |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La duración del módulo no baja** al desactivar | Se suma sobre activas en los lectores, y `CA-AC-111` lee el módulo y el curso después |

## 11. Estrategia de prueba

- **Unitaria**: `Lesson.activate` con y sin contenido; `deactivate`.
- **Integración de API** (`LessonStatusIT`): los cuatro criterios; **la que define el requerimiento es `CA-AC-111`**, la duración que baja.
