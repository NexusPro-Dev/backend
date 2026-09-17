# PLAN — `RF-AC-005` Eliminar categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-005` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 17-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

---

## 1. Enfoque

**`DeletePackageService` con otra entidad y una instantánea más estrecha.** Los cinco pasos son los mismos —motivo antes que nada, fila bloqueada en cualquier estado, instantánea antes de marcar, marca sin tocar nada más, registro en la misma transacción—; la instantánea lleva la categoría y **los identificadores de sus cursos vivos**, que es lo que dice `spec.md` §14.1. **Es el retiro más simple del módulo** y por eso conviene construirlo primero: `RF-AC-013` y `RF-AC-025` heredarán su forma y le añadirán el arrastre.

**`DeletionReason` se mueve a `shared/`.** Hoy vive en `PM` —el objeto de valor que recorta, exige contenido y acota a 500—; `CM` hace la misma comprobación **en línea** en su servicio de retiro, y `SP` y `MV` tienen sus parientes con otro nombre (`ChangeReason`, `VoidReason`). Este plan lo lleva a `shared/audit`, donde está el resto de la baja lógica, y `PM` lo importa de allí. Es una refactorización sin cambio de comportamiento, con las suites de los tres módulos en verde como definición de terminado, y es el primer código compartido que `AC` provoca —antes que el detector de imágenes de `RF-AC-006`—.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `DeleteCourseCategoryRequest` — `reason` | `AC` |
| `domain/models` | `CourseCategory.delete(ahora)`, `estaRetirada()`, `instantanea(courseIds)` | `AC` |
| `domain/repository` | `CourseCategoryQueryRepository.findAliveCourseIdsOf(categoryId)` — vacío hasta `RF-AC-016` | `AC` |
| `domain/service` | `DeleteCourseCategoryService` | `AC` |
| `interfaces` | `CourseCategoryController` — `POST /api/v1/course-categories/{id}/deletion` | `AC` |
| `shared/audit` | **`DeletionReason`**, movido desde `PM`; `PM` lo importa de aquí | `shared`, `PM` |

## 4. Contrato de API

`POST /api/v1/course-categories/{id}/deletion` — `course-categories:delete`. `{ "reason": "…" }` → `204`. **`POST` y no `DELETE`**, por lo mismo que el producto: el cuerpo lleva el motivo y la RFC 9110 no garantiza el cuerpo de un `DELETE`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001`, `VAL-002` |
| `401` / `403` | Sin sesión / sin `course-categories:delete` |
| `404` | `EX-001` |
| `409` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('course-categories:delete')")`.

## 6. Auditoría

`DeletionEvent` `LOGICAL` de `course_categories`, con el motivo y la instantánea —la categoría y `courseIds: […]`—.

## 7. Transaccionalidad

`@Transactional`. La categoría con `FOR UPDATE` en cualquier estado, los identificadores de sus cursos para la instantánea, el `UPDATE`, la auditoría. **Cuatro sentencias**; tres sin cursos, porque la lectura de identificadores se cortocircuita cuando `courseCount` es cero.

## 8. Impacto sobre otros módulos

**`PM` cambia un `import`**: `DeletionReason` pasa a `shared/audit`. Sin cambio de comportamiento; `ProductDeletionIT`, `PackageDeletionIT` y `ProductCommentDeletionIT` en verde son la prueba.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Borrar las filas de clasificación al retirar** | La retirada dejaría de decir qué contenía (`spec.md` §14.1) |
| **Rechazar si tiene cursos** | Es un filtro; el motivo es la barrera (`spec.md` §14.2) |
| **Desclasificar los cursos con una fila de auditoría cada uno** | Nada cambia en el curso: sigue clasificado en una categoría que ya no se enseña. Auditar en el curso un cambio que no ocurrió en su fila sería mentir |
| **Un `DeletionReason` propio de `AC`**, o la comprobación en línea como `CM` | Una copia más del mismo objeto de valor; la baja lógica es transversal (Art. V.13) y su motivo también |
| **`DELETE` con cuerpo** | La RFC 9110 no garantiza el cuerpo |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La instantánea se toma después de marcar** | El orden está escrito y `CA-AC-031` comprueba `deleted_at` nulo dentro de la instantánea |
| 2 | **Mover `DeletionReason` rompe `PM`** | Solo cambia el paquete; las tres suites de retiro de `PM` en verde antes de seguir (`T-01`) |
| 3 | **Alguien «limpia» las filas de clasificación de las retiradas** | `CA-AC-028` cuenta las filas después del retiro |

## 11. Estrategia de prueba

- **Integración de API** (`CourseCategoryDeletionIT`): los seis criterios; **la que define el requerimiento es `CA-AC-032`**, la del curso que se sigue ofreciendo — trivial hasta el aula (`RF-AC-033`), que la enmienda.
- **Concurrencia**: dos retiros → un `204` y un `409`, una fila de auditoría (`CA-AC-033`).
- **Refactorización**: las suites de retiro de `PM` en verde con `DeletionReason` en `shared/`.
