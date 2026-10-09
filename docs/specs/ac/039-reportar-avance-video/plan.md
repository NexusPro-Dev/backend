# PLAN — `RF-AC-039` Reportar el avance de un video

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-039` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

**Las puertas de `RF-AC-035`, reutilizadas y no copiadas.** `GetClassroomLessonService` se parte: la comprobación «se ofrece y se abre» pasa a un componente propio, **`ClassroomLessonGate`**, que devuelve la fila de la lección o lanza el `404`/`403` de siempre. `RF-AC-035` y este requerimiento lo llaman los dos; así **no puede haber dos definiciones de «se le abre»**.

**Una sentencia que escribe y devuelve.** `LessonProgressRepository.report(user, lesson, position, duration)` es un `INSERT … ON CONFLICT (user_id, lesson_id) DO UPDATE` con `GREATEST` para los segundos y `COALESCE` para la fecha de completitud, y `RETURNING` de la fila. **No se lee antes**: el máximo lo calcula el motor, y dos reportes simultáneos no se pisan (`spec.md` §13).

**La regla, en un objeto puro.** `LessonProgress` (`domain/models`) dice cuánto se acota, cuándo completa y cuánto vale una lección para el avance (`RN-AC-021`, `RN-AC-023`). El SQL recibe ya la posición acotada y el umbral calculado en segundos, para que **el 90 % viva en un solo sitio**.

## 2. Cambios de esquema

**`V92__ac_progreso_de_lecciones.sql`**: crea `lesson_progress` (`ac.md` §8.7.1) con sus restricciones e índice, y **siembra los tres permisos** del progreso —este y los de `RF-AC-040` y `RF-AC-041`—: `lessons:track-progress` en todo rol que porte `lessons:learn`; `courses:list-progress` y `courses:read-progress` en `SUPERADMIN`, `ADMIN`, todo rol de tipo `VENDEDOR` y todo rol que porte `courses:teach`. Con un bloque `DO` que comprueba que ningún rol con `lessons:learn` se queda sin `lessons:track-progress`. El catálogo pasa de 216 a **219**.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | **`LessonProgress`**: umbral, acotar, completa, crédito y porcentaje | `AC` |
| `domain/repository` | **`LessonProgressRepository`** + `JpaLessonProgressRepository`: `open`, `report` | `AC` |
| `domain/service` | **`ClassroomLessonGate`** (sale de `GetClassroomLessonService`); **`ReportLessonProgressService`** | `AC` |
| `application` | **`ReportLessonProgressRequest`** (`positionSeconds`), **`LessonProgressResponse`** | `AC` |
| `interfaces` | `ClassroomController` — `PUT /api/v1/courses/available/{courseId}/lessons/{lessonId}/progress` | `AC` |

## 4. Contrato de API

`PUT /api/v1/courses/available/{courseId}/lessons/{lessonId}/progress` — `lessons:track-progress`.

```json
{ "positionSeconds": 312 }
```

`200`:

```json
{
  "lessonId": "…", "watchedSeconds": 312, "durationSeconds": 340, "percent": 91,
  "completed": true, "completedAt": "2026-10-09T15:02:11Z",
  "firstOpenedAt": "2026-10-09T14:55:40Z", "lastOpenedAt": "2026-10-09T15:02:11Z"
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001`, `VAL-002` |
| `401` / `403` | Sin sesión / sin `lessons:track-progress` (`AUTH-002`) |
| `403` | `EX-002`, con `memberships` y `products` |
| `404` | `EX-001` |
| `422` | `EX-003`, lección `TEXTO` |

**`PUT` y no `POST`**: el mismo reporte dos veces deja lo mismo.

## 5. Autorización

`@PreAuthorize("hasAuthority('lessons:track-progress')")`; las llaves, dentro, por `ClassroomLessonGate`. La ruta entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

**Ninguna**, por la excepción aprobada (`spec.md` §2.3). La fila lleva sus tres fechas.

## 7. Transaccionalidad

`@Transactional`: la puerta (una sentencia para la abierta y la gratuita, tres más los puertos para la cerrada) y el `INSERT … RETURNING`.

## 8. Impacto sobre otros módulos y documentos

- **`RF-AC-035`**: su servicio se parte en la puerta y la entrega (Art. I.7, sin cambio de comportamiento salvo `RN-AC-022`).
- `security.md` §4.4 (catálogo 219), `modelo-datos.md` §4.2 y §5, la matriz de `requirements.md`, `docs/api/index.md` y el contrato.
- Las suites que cuentan el catálogo (`PermissionIT` y las que fijan `216L`) suben a `219L`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Leer la fila, comparar en Java y escribir** | Dos reportes simultáneos podrían bajar el máximo; el `GREATEST` del motor no |
| **Una tabla de reportes y el máximo al leer** | Miles de filas por alumno para responder una cifra |
| **`POST` con un reporte por fila** | Lo mismo, y el reporte no es un recurso que alguien consulte |
| **Copiar las puertas en el servicio nuevo** | Dos definiciones de «se le abre» |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El reporte deja rastro de una lección cerrada** | La puerta va antes del `INSERT`; `CA-AC-247` cuenta filas |
| 2 | **El 90 % se calcula distinto en Java y en SQL** | El SQL recibe el umbral en segundos calculado por `LessonProgress` |
| 3 | **Alguien añade auditoría «por coherencia»** | `CA-AC-249` comprueba que `audit_log` no crece |

## 11. Estrategia de prueba

- **Unitarias** (`LessonProgressTest`): acotar, 89/90 %, crédito de completada, porcentaje redondeado hacia abajo y con duración cero.
- **Integración de API** (`LessonProgressReportIT`): `CA-AC-243` a `CA-AC-249`.
