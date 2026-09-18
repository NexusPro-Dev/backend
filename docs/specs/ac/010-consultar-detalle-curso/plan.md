# PLAN — `RF-AC-010` Consultar el detalle de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-010` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`CourseDetailReader` bajo una transacción de solo lectura, y nada más.** La escalera —curso con instructor, categorías, recomendaciones, membresías, módulos con lecciones, ofrecibilidad, motivo de retiro— nace en `RF-AC-008` porque la comparten nueve operaciones, y este requerimiento le pone la ruta. **Cada peldaño es una sentencia fija que hoy no se ejecuta**: los métodos del repositorio de relaciones y de árbol devuelven vacío hasta que su requerimiento los escriba, y `CourseDetailReader` los llama ya en el orden definitivo, de modo que llenarlos no cambie su forma.

**El árbol se lee en dos sentencias y no en una con dos `LEFT JOIN`**: módulos del curso en orden, y lecciones de esos módulos en orden, agrupadas en Java. Un `JOIN` doble multiplicaría la fila del curso —con su descripción larga— por módulo y por lección, y habría que desduplicar tres niveles.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `CourseQueryRepository`: `findDetail(id)` (de `RF-AC-008`), y las lecturas que hoy devuelven vacío y que cada requerimiento estrena —`findCategoriesOf`, `findRecommendedOf`, `findMembershipsOf`, `findModulesOf`, `findLessonsOfModules`— | `AC` |
| `domain/service` | `CourseDetailReader` (de `RF-AC-008`): junta las lecturas, suma duraciones y cuenta lecciones sobre lo vivo, decide `CourseOfferability` del curso y de cada módulo, lee el motivo si retirado; `GetCourseService` | `AC` |
| `application` | `CourseDetailResponse` (de `RF-AC-008`) con `CategoryRef`, `RecommendedCourseRef`, `MembershipRef`, `ModuleDetail` y `LessonSummary`; `deletedAt` y `deletionReason` `NON_NULL` | `AC` |
| `interfaces` | `CourseController` — `GET /api/v1/courses/{id}` | `AC` |
| `shared/audit` | `DeletionReasonReader`, sin cambio | `shared` |

**`MembershipRef` se resuelve por `JOIN memberships` en la sentencia de `findMembershipsOf`**, cuando exista: es el precedente de `RF-PM-002` con las membresías, y son cuatro columnas —`id`, `code`, `name`, `color`— que `SP` ya publica por su puerto. El `JOIN` es de lectura; **dar** la membresía (`RF-AC-020`) sí cruza por `MembershipCatalog`.

## 4. Contrato de API

`GET /api/v1/courses/{id}` — `courses:read`. `200` con `CourseDetailResponse` (la forma está en `RF-AC-008` §4, con las listas que aquí se describen).

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:read` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:read')")`.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Una sentencia** hoy, **una más** con motivo de retiro; cada requerimiento que llene una lista añade la suya —y las de módulos y lecciones se cortocircuitan cuando no hay módulos—. `CA-AC-054` cuenta lo de hoy y cada enmienda actualiza el número.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Un `LEFT JOIN` doble para el árbol** | Multiplica la fila del curso por módulo y lección; tres niveles que desduplicar |
| **Traer el contenido de las lecciones** | `spec.md` §14.1 |
| **Calcular `offerable` por módulo en SQL** | Una sola cuenta, en `CourseOfferability`, para el curso y sus módulos |
| **Un detalle «ligero» sin árbol y otro con él** | Dos formas para una entidad; el frontend tendría dos pantallas |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Una enmienda llena una lista y olvida contar su sentencia** | `CA-AC-054` fija el número y cada requerimiento que la enmiende lo actualiza en su spec |
| 2 | **La duración total suma lecciones retiradas** | La suma es sobre lo vivo, escrita en `CourseDetailReader` y probada por `RF-AC-028` |

## 11. Estrategia de prueba

- **Integración de API** (`CourseDetailIT`): los seis criterios; **la que define el requerimiento es `CA-AC-052`**, el orden de los motivos con lo que hoy existe.
- **De sentencias**: `CA-AC-054`, con y sin motivo.
- **De forma**: `CA-AC-056` compara el JSON del alta y del detalle campo a campo.
