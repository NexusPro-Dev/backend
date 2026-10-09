# PLAN — `RF-AC-041` Consultar el progreso de un alumno en un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-041` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

**El alcance, en un componente que comparten las dos consultas.** **`ProgressAudience`** (`domain/service`) traduce `CommercialReach` a «a quién alcanza este actor»: `EVERYTHING` → todos; `NETWORK` → la red **y los clientes principales de alguien de la red**; `OWN` → él. A eso se suma, por curso, **ser su instructor**. Es la traducción que `SalesScopeResolver` hace en `IN`, con otra pregunta, y **la red se lee de `SP`** —`AC` no conoce `user_supervisors` ni `client_sellers`—.

**`SP` publica una lectura más**: `CommercialReach.principalClientsOf(Set<UUID> sellers)` —los clientes cuyo vendedor principal (`client_sellers`, `origin = 'REGISTRO'`) está en el conjunto—. Es la misma unión que `RF-SP-056` hace para las cuentas de broker de un equipo, publicada como puerto. **La pide `RF-AC-040`** por el orden de §6.1 de `ac.md`, pero la usa primero esta consulta, que se construye antes; se construye aquí.

**Tres sentencias y el puerto de identidad.** El curso (`findDetail`, ya existe, con retirados); el árbol de módulos y lecciones del curso **con sus entradas de ofrecibilidad** (`findModulesOf` y `findLessonsOfModules`, ya existen); y las filas de `lesson_progress` del alumno en las lecciones del curso (nueva). La identidad del alumno, por `UserCatalog.find`. **La ofrecibilidad la deciden los objetos de siempre** —`ModuleOfferability` y la de la lección—, no un `WHERE` nuevo.

## 2. Cambios de esquema

Ninguno: `V92` (`RF-AC-039` · `T-01`) siembra `courses:read-progress`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `users/application` | `CommercialReach`: **`principalClientsOf(Set<UUID>)`**; `JpaCommercialReach` la implementa | `SP` |
| `domain/service` | **`ProgressAudience`**: `of(actor)` → `Audience {everyone, people}` y `reaches(audience, student, instructorOfCourse)` | `AC` |
| `domain/repository` | `LessonProgressRepository.findOfUserInCourse(userId, courseId)` | `AC` |
| `domain/service` | **`GetStudentCourseProgressService`** | `AC` |
| `application` | **`StudentCourseProgressResponse`** con sus registros anidados | `AC` |
| `interfaces` | **`CourseProgressController`** — `GET /api/v1/courses/{courseId}/progress/{userId}` | `AC` |

## 4. Contrato de API

`GET /api/v1/courses/{courseId}/progress/{userId}` — `courses:read-progress`.

```json
{
  "student": { "id": "…", "username": "ana", "fullName": "Ana Ruiz" },
  "course": { "id": "…", "title": "Velas", "status": "ACTIVO", "deleted": false },
  "progress": { "percent": 47, "completedLessons": 2, "lessonCount": 5,
                "watchedSeconds": 512, "totalSeconds": 1090,
                "firstOpenedAt": "…", "lastActivityAt": "…" },
  "modules": [ { "id": "…", "title": "Básico", "displayOrder": 0, "lessons": [
      { "id": "…", "title": "Los cuerpos", "type": "VIDEO", "durationSeconds": 300,
        "displayOrder": 0, "watchedSeconds": 300, "percent": 100, "completed": true,
        "completedAt": "…", "firstOpenedAt": "…", "lastOpenedAt": "…" } ] } ],
  "notOffered": []
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:read-progress` |
| `404` | `EX-001`: curso o alumno inexistente, o fuera del alcance |

**Bajo `/courses/{courseId}/progress/…` y no `/users/{id}/…`**: el progreso es de `AC`, y la ruta vive en el módulo dueño. `/courses/progress` (`RF-AC-040`) es un segmento literal que Spring prefiere a `/{id}`.

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:read-progress')")`; el alcance, dentro. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

No audita: es una lectura, como `RF-AC-010`.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Impacto sobre otros módulos y documentos

- **`SP`**: un método más en `CommercialReach`, sin cambio para `MV` ni `IN`. `requirements/sp.md` §8 lo anota como interfaz publicada.
- `ac.md` §3 ya lo declara.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`403` fuera del alcance** | Diría que el alumno existe (`RN-AC-024`) |
| **Leer `client_sellers` desde `AC`** | `modules.md` §7: la tabla es de `SP` |
| **Esconder lo no ofrecido** | Lo que el alumno vio es historia; `notOffered` la enseña sin mezclarla con el avance |
| **Un puerto de `SP` que reciba el alumno y diga sí o no** | Sirve al detalle y no al listado, que necesita el conjunto |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El instructor ve alumnos de otro curso** | El instructor se mira contra **este** curso; `CA-AC-255` |
| 2 | **El `404` del alcance se distingue del de inexistencia** | Un solo mensaje y una sola excepción; `CA-AC-256` compara cuerpos |
| 3 | **La red de un vendedor grande** | Un conjunto en `IN (…)`, como `JpaAfftrackSettlementRepository`; aquí solo se pregunta si contiene al alumno |

## 11. Estrategia de prueba

- **Integración de API** (`StudentCourseProgressIT`): `CA-AC-253` a `CA-AC-257`, con la red armada por `user_supervisors` y `client_sellers`.
- `CommercialReachIT` gana el caso de `principalClientsOf`: solo `REGISTRO`, solo los vendedores dados.
