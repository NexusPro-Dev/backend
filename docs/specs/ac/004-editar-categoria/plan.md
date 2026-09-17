# PLAN — `RF-AC-004` Editar categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-004` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 17-09-2026 |
| Estado | **Aprobado** — construido el 17-09-2026 |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

---

## 1. Enfoque

**`RF-PM-020` con cinco campos y ningún inmutable.** Se hereda entera la mecánica —`Patchable` con sus tres estados, la unicidad del nombre con `existsAliveNameForOther`, el bloqueo de fila, la auditoría solo de lo que cambió, la relectura del detalle— y se quita la mitad que rechaza inmutables, porque no los hay. **Lo único que este plan decide** es que el color se normaliza **antes** de comparar con el que había, para que `1e88e5` sobre `1E88E5` sea «sin cambio» y no un `UPDATE` que audita un antes y un después idénticos.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `UpdateCourseCategoryRequest` — `Patchable<String> name`, `Patchable<String> description`, `Patchable<String> color`, `Patchable<String> icon`, `Patchable<Integer> displayOrder`; `informaAlgo()` | `AC` |
| `domain/models` | `CourseCategory.update(...)` que devuelve el mapa de cambios `{campo: {before, after}}` y avanza `updatedAt` solo si no está vacío; valida cada campo que viene con el mensaje de su `VAL-` y **normaliza el color antes de comparar** | `AC` |
| `domain/repository` | `CourseCategoryRepository.existsAliveNameForOther(name, id)` | `AC` |
| `domain/service` | `UpdateCourseCategoryService` | `AC` |
| `interfaces` | `CourseCategoryController` — `PATCH /api/v1/course-categories/{id}` | `AC` |
| `shared/patch` | `Patchable` y sus deserializadores, **sin cambio**: `PatchableDeserializer` genérico cubre `Integer` | `shared` |

## 4. Contrato de API

`PATCH /api/v1/course-categories/{id}` — `course-categories:update`. Cuerpo con cualquiera de `name`, `description`, `color`, `icon`, `displayOrder`; `200` con `CourseCategoryDetailResponse`.

- `description: null` **vacía**; `name: null`, `color: null`, `icon: null` y `displayOrder: null` son `400`, **juntos** con lo demás.
- Cuerpo vacío: `400` con `VAL-006`. Campo desconocido: `400` con `VAL-007`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-007` |
| `401` / `403` | Sin sesión / sin `course-categories:update` |
| `404` | `EX-002` |
| `409` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('course-categories:update')")`.

## 6. Auditoría

`ChangeEvent` `UPDATE` con solo los campos que cambiaron. Sin fila si no cambió nada.

## 7. Transaccionalidad

`@Transactional`. La categoría viva con `FOR UPDATE`; el nombre contra otros vivos solo si viene; escritura y auditoría solo si algo cambió; relectura del detalle.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Admitir vaciar el icono cuando hay portada**, como el upgrade | La categoría se pinta siempre con color e icono (`RN-AC-003`); la regla cruzada de `RN-PM-034` existe porque el upgrade no tiene otra cosa, y aquí sobra |
| **Comparar el color sin normalizar** | Auditaría un cambio de caja como si fuera un cambio de color |
| **Reordenar en bloque** | `spec.md` §14.1 |
| **`PUT` con el recurso entero** | Obligaría al cliente a reenviar lo que no quiere tocar, y «no vino» dejaría de distinguirse de «vacíalo» |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El nulo del color se trata como ausente** | `Patchable` con su deserializador; `CA-AC-022` |
| 2 | **La unicidad del nombre choca consigo misma** al cambiar la caja | `existsAliveNameForOther` excluye el propio identificador; `CA-AC-024` |
| 3 | **Un cambio de caja del color se audita** | Normalización antes de comparar; `CA-AC-026` con `1e88e5` sobre `1E88E5` |

## 11. Estrategia de prueba

- **Unitaria**: `CourseCategory.update` — cada campo, el nulo de la descripción, los cuatro nulos rechazados, sin cambios, y el color en minúsculas sobre el mismo en mayúsculas → sin cambio.
- **Integración de API** (`CourseCategoryUpdateIT`): los siete criterios; **la que define el requerimiento es `CA-AC-027`**, porque une la corrección con el listado ordenado.
