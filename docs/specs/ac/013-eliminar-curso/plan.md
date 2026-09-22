# PLAN — `RF-AC-013` Eliminar curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-013` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`DeleteCourseCategoryService` con arrastre.** Los cinco pasos del retiro son los mismos; lo que se añade es un sexto que recorre los módulos vivos del curso y sus lecciones vivas, los marca con el **mismo instante** y registra una baja por cada uno con el **mismo motivo**. **El arrastre es un colaborador propio, `CourseTreeRetirement`**, que `RF-AC-025` reutilizará para retirar un módulo con sus lecciones: una sola forma de arrastrar, escrita una vez.

**Hoy el colaborador no tiene nada que recorrer** —las tablas no existen— y el requerimiento se construye con él vacío y con la nota de qué escribe el bloque 3. La instantánea del curso lleva las cuatro listas de identificadores, que hoy son vacías por lo mismo.

## 2. Cambios de esquema

**Ninguno.** Sin `ON DELETE CASCADE` en ninguna clave: no se borra nada.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `DeleteCourseRequest` — `reason` | `AC` |
| `domain/models` | `Course.delete(ahora)`, `estaRetirado()`, `instantanea(categoryIds, membershipIds, recommendedIds, moduleIds)` | `AC` |
| `domain/repository` | `CourseQueryRepository`: las lecturas de identificadores de relaciones (de `RF-AC-010`) y **`findAliveModulesForUpdate(courseId)`** con sus lecciones —vacío hasta `RF-AC-022`— | `AC` |
| `domain/service` | **`CourseTreeRetirement`** —marca módulos y lecciones y registra una baja por fila—; `DeleteCourseService` | `AC` |
| `interfaces` | `CourseController` — `POST /api/v1/courses/{id}/deletion` | `AC` |
| `shared/audit` | `DeletionReason`, `AuditWriter`, sin cambio | `shared` |

## 4. Contrato de API

`POST /api/v1/courses/{id}/deletion` — `courses:delete`. `{ "reason": "…" }` → `204`. **`POST` y no `DELETE`**, por lo mismo de siempre.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001`, `VAL-002`, `VAL-003` |
| `401` / `403` | Sin sesión / sin `courses:delete` |
| `404` | `EX-001` |
| `409` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:delete')")`.

## 6. Auditoría

`DeletionEvent` `LOGICAL` de `courses` con el motivo y la instantánea —curso y `category_ids`, `membership_ids`, `recommended_course_ids`, `module_ids`—; y **uno por módulo y uno por lección arrastrados** (`course_modules`, `lessons`), con el mismo motivo y la instantánea de cada uno, todos en la misma transacción.

## 7. Transaccionalidad

`@Transactional`. El curso con `FOR UPDATE` en cualquier estado; los identificadores de relaciones para la instantánea; el `UPDATE` y la baja del curso; los módulos vivos con `FOR UPDATE` y sus lecciones; un `UPDATE` por tabla arrastrada y una baja por fila. **Hoy: cuatro sentencias**; el bloque 3 declara las suyas.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Un solo registro de eliminación con el árbol dentro** | `spec.md` §14.1: el Art. V.13 pide registro por entidad, y una lección tiene que encontrarse por su identificador |
| **`ON DELETE CASCADE`** | No se borra nada; el arrastre es lógico y audita |
| **Dejar módulos y lecciones vivos bajo el curso retirado** | Entidades vivas colgando de nada (`RN-AC-019`), que el aula tendría que filtrar por el abuelo |
| **Borrar las recomendaciones que lo tenían de previo** | Se conservan y el aula no las enseña (`spec.md` §14.2) |
| **Rechazar si está `ACTIVO`** | El motivo es la barrera; exigir desactivar antes destruiría el dato de si estaba publicado |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El arrastre marca las lecciones y olvida sus bajas**, o al revés | `CourseTreeRetirement` hace las dos cosas por fila en un solo método; `CA-AC-074` cuenta filas de auditoría contra filas marcadas |
| 2 | **La instantánea se toma después de marcar** | El orden está escrito; `CA-AC-073` comprueba `deleted_at` nulo dentro |
| 3 | **`RF-AC-025` reescribe el arrastre en lugar de reutilizarlo** | El colaborador nace aquí con la firma que aquel necesita, y su plan lo cita |

## 11. Estrategia de prueba

- **Integración de API** (`CourseDeletionIT`): `CA-AC-070` a `CA-AC-073` y `CA-AC-075`; **`CA-AC-074` se escribe deshabilitado con motivo** y el bloque 3 lo habilita.
- **Concurrencia**: dos retiros → un `204`, un `409`, una fila del curso.
