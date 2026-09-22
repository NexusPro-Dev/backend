# PLAN — `RF-AC-012` Cambiar el estado de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-012` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`ChangePackageStatusService` con otra entidad y tres condiciones en lugar de dos.** Las dos primeras las conoce el agregado —tiene las descripciones—; la tercera mira a otra tabla y vive en el caso de uso, que cuenta los módulos activos vivos por una lectura del repositorio de consultas. **Los tres `409` se recogen en una lista y se lanzan juntos**, como en `RF-PM-021`.

**La cuenta de módulos activos es un método del repositorio que hoy devuelve cero**, con la nota de qué sentencia lo sustituye (`RF-AC-022`). Es lo que hace que todo lo demás del requerimiento se construya ya y que `CA-AC-064` sea lo único que espera.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `ChangeCourseStatusRequest` — `status` | `AC` |
| `domain/models` | `Course.activate(ahora)`, `deactivate(ahora)` que devuelven si hubo cambio; `tieneDescripcionCorta()`, `tieneDescripcionLarga()` | `AC` |
| `domain/repository` | `CourseQueryRepository.countActiveModulesOf(courseId)` — **cero hasta `RF-AC-022`** | `AC` |
| `domain/service` | `ChangeCourseStatusService` | `AC` |
| `interfaces` | `CourseController` — `PATCH /api/v1/courses/{id}/status` | `AC` |

## 4. Contrato de API

`PATCH /api/v1/courses/{id}/status` — `courses:update`. `{ "status": "ACTIVO" }` → `200` con `CourseDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001`, `VAL-002` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-002`, `EX-003`, `EX-004`, juntos los que apliquen |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` con `status` antes y después. Sin fila si no cambió.

## 7. Transaccionalidad

`@Transactional`. El curso vivo con `FOR UPDATE`; al activar, la cuenta de módulos; escritura y auditoría si cambió; relectura del detalle.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Exigir una membresía para activar** | Un curso activo sin lista es legítimo y el detalle lo dice (`RN-AC-012`); exigirla obligaría a dar la membresía antes de revisar el curso publicado |
| **Desactivar en cascada al retirar el último módulo** | Cambia el estado que alguien decidió por un hecho (`RN-AC-009`, `RN-PM-040`) |
| **Comprobar la lección activa del módulo** | Tercera copia de la regla que `RF-AC-024` ya aplica (`spec.md` §14.1) |
| **Activar los módulos en cascada** | Cada módulo tiene su condición y su auditoría |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se olvida sustituir el cero** de `countActiveModulesOf` | La nota en el código y `CA-AC-064` bloqueado en la matriz hasta `RF-AC-024` |
| 2 | **Los tres `409` se lanzan uno a uno** | La lista de errores como en `RF-PM-021`; `CA-AC-065` con las tres faltando |

## 11. Estrategia de prueba

- **Unitaria**: `Course.activate`/`deactivate` sin cambio y con cambio; los dos `tieneDescripcion…`.
- **Integración de API** (`CourseStatusIT`): `CA-AC-065` a `CA-AC-069`; **`CA-AC-064` se escribe deshabilitado con el motivo** y `RF-AC-024` lo habilita.
- **Concurrencia**: dos activaciones → una audita.
