# PLAN — `RF-AC-024` Cambiar el estado de un módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-024` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`ChangeCourseStatusService` con una condición y otra entidad.** `CourseModule.activate`/`deactivate`; la cuenta de lecciones activas por `countActiveLessonsOf` (de `RF-AC-028`); un solo `409`. Y **la tarea que cierra el bloque 2**: habilitar `CA-AC-064` de `RF-AC-012`, que estaba escrito y deshabilitado.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `ChangeCourseModuleStatusRequest` | `AC` |
| `domain/models` | `CourseModule.activate`, `deactivate` | `AC` |
| `domain/service` | `ChangeCourseModuleStatusService` | `AC` |
| `interfaces` | `CourseModuleController` — `PATCH /api/v1/courses/{courseId}/modules/{moduleId}/status` | `AC` |
| pruebas | `CourseStatusIT.CA-AC-064` habilitado | `AC` |

## 4. Contrato de API

`PATCH /api/v1/courses/{courseId}/modules/{moduleId}/status` — `courses:update`. `{ "status": "ACTIVO" }` → `200` con `CourseModuleDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001`, `VAL-002` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` de `course_modules` con `status` antes y después. Sin fila si no cambió.

## 7. Transaccionalidad

`@Transactional`. El módulo del curso con `FOR UPDATE`; al activar, la cuenta; escritura y auditoría si cambió; relectura.

## 8. Impacto sobre otros módulos

**Ninguno.** Dentro de `AC`, `RF-AC-012` gana su criterio habilitado.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Desactivar el curso en cascada** | `RN-AC-009`: el estado es lo que alguien decidió |
| **Bloquear el módulo desde el cambio de estado de la lección** | `spec.md` §14.1 |
| **Exigir contenido en la lección activa** | Una lección activa tuvo contenido al activarse (`RF-AC-030`); repetirlo aquí es la tercera copia |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`CA-AC-064` se queda deshabilitado** | `T-05` lo habilita y la definición de terminado lo exige |

## 11. Estrategia de prueba

- **Unitaria**: `CourseModule.activate`/`deactivate`.
- **Integración de API** (`CourseModuleStatusIT`): `CA-AC-105` a `CA-AC-108`; **`CA-AC-109`** en `CourseStatusIT`, habilitando `CA-AC-064`.
