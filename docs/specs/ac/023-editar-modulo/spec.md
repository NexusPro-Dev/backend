# SPEC — `RF-AC-023` Editar módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-023` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Corregir lo que se declaró de un módulo —título, descripciones, video y orden— sin tocar lo que no se pidió.

## 2. Contexto

Es `RF-AC-011` sin instructor ni dificultad, y con la unicidad del título **dentro del curso**: parcial, nulo explícito como orden donde el vacío es legítimo, auditoría de lo que cambió, respuesta en la forma del detalle del módulo. **El curso no se corrige** (`RN-AC-019`): no hay campo, y enviarlo es un campo desconocido. Las descripciones y el video **se vacían en cualquier estado**, por lo mismo que en el curso.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Corrige el módulo |

## 4. Alcance

### 4.1 Incluye

- Corregir **título, descripción corta, descripción larga, video de presentación y orden**, por separado o juntos.
- Vaciar las descripciones y el video con nulo explícito.
- Devolver el módulo en la forma de su detalle.

### 4.2 No incluye

- **El curso.** `RN-AC-019`.
- **El estado, la portada, las lecciones.** `RF-AC-024`, `RF-AC-026`/`027`, `RF-AC-028` en adelante.
- **Corregir un retirado.**

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-001` | El título nuevo no lo usa otro módulo vivo **del mismo curso** | `requirements/ac.md` §5.1 |
| `RN-AC-002` | El orden es un entero ≥ 0 dentro del curso | `requirements/ac.md` §5.1 |
| `RN-AC-005` | El video se corrige y se vacía | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Lo retirado no se corrige | `requirements/ac.md` §5.1 |
| `RN-AC-019` | El módulo no cambia de curso | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y módulo | Sí | Cuál | Ruta. Módulo **vivo** de ese curso |
| `title` | No | Título nuevo | Hasta 150; único en el curso; **no admite nulo** |
| `shortDescription`, `longDescription` | No | Descripciones nuevas | Sus topes; **nulo explícito las vacía** |
| `presentationVideoUrl` | No | Video nuevo | Forma de `RN-AC-005`; **nulo explícito lo vacía** |
| `displayOrder` | No | Orden nuevo | Entero ≥ 0; **no admite nulo** |

**Al menos uno de los cinco.**

### 6.2 Salida

`200` con el módulo en la forma de su detalle, `updatedAt` avanzado solo si algo cambió.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; módulo vivo del curso de la ruta; si viene título, libre entre los otros vivos del curso.

**Postcondiciones:** la fila refleja los campos corregidos y solo esos; fila `UPDATE` con antes y después, o ninguna.

## 8. Flujo principal

1. Llega la petición con uno o más campos.
2. El sistema valida la forma de lo que viene, **juntos**, y que venga al menos uno (§11).
3. El sistema resuelve el módulo **vivo** del curso de la ruta, bloqueándolo (`EX-002`).
4. Si viene el título, comprueba que no lo usa **otro** vivo del curso (`EX-001`).
5. Aplica, escribe y audita si algo cambió; devuelve `200`.

## 9. Flujos alternativos

### FA-001 — Vaciar una descripción de un módulo `ACTIVO`

**Comportamiento:** se admite y no cambia el estado: la activación del módulo no exige descripciones (`RN-AC-009`), solo una lección activa.

### FA-002 — Mismos valores

**Comportamiento:** `200` sin avanzar `updatedAt` ni auditar.

## 10. Excepciones

### EX-001 — El título ya lo usa otro módulo vivo del curso

**Respuesta del sistema:** `409` — *«Ya existe un módulo con ese título en este curso.»*

### EX-002 — El módulo no existe, está retirado o no es de ese curso

**Respuesta del sistema:** `404` — *«No existe un módulo vivo con ese identificador en este curso.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | El título no admite vaciarse y cabe en 150 | El título del módulo no puede quedar vacío ni superar los 150 caracteres. |
| `VAL-003` | El orden no admite vaciarse y es ≥ 0 | El orden del módulo no puede quedar vacío y debe ser un entero mayor o igual que cero. |
| `VAL-004` | Descripciones dentro de su tope y video con la forma admitida, si vienen con valor | Los de `RF-AC-008` |
| `VAL-005` | Al menos un campo corregible | Debe informar al menos uno de los campos corregibles. |
| `VAL-006` | Ningún campo desconocido — `courseId`, `status`, `lessons`, `coverImageUrl` | El cuerpo de la petición contiene campos no admitidos. |

Todas **juntas**, antes de cualquier consulta.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-094` | El sistema corrige los cinco campos por separado y juntos, y devuelve el detalle del módulo con `updatedAt` avanzado |
| `CA-AC-095` | El nulo explícito **vacía** las descripciones y el video —también en un módulo `ACTIVO`, que sigue `ACTIVO`— y **se rechaza** en título y orden, juntos |
| `CA-AC-096` | El sistema rechaza con `400` un cuerpo vacío y uno con `courseId`, `status`, `lessons` o `coverImageUrl` |
| `CA-AC-097` | El sistema rechaza con `409` un título que ya usa **otro** vivo del curso, admite el de un retirado, el mismo título en otro curso y cambiar la caja del propio; y con `404` un módulo retirado, inexistente o **de otro curso** |
| `CA-AC-098` | Un cuerpo sin cambios responde `200` sin auditar; uno con cambios deja la fila `UPDATE` con solo lo que cambió, y **el detalle del curso enseña el módulo en su orden nuevo** |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Corregir el orden a uno que ya usa otro módulo del curso | Se admite; desempata el identificador |
| Corregir por la ruta de otro curso | `404`: la ruta afirma la pertenencia |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna. Todo lo que aquí se decide viene de `RF-AC-011` y `RF-AC-004` | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Hereda `RF-AC-011` sin instructor ni dificultad; el curso no se corrige y la unicidad es dentro del curso. | Responsable técnico |
| 0.2.0 | 19-09-2026 | **Construida** (`CourseModuleUpdateIT` (5)). **Una precisión**: la carrera sobre el título la traduce `JpaCourseModuleRepository` con el código del alta (`EX-002`), como la categoría; la comprobación previa responde `EX-001`. Un repositorio no sabe desde qué operación lo llaman. | Responsable técnico |
| 0.3.0 | 25-09-2026 | **Enmienda por decisión del responsable del proyecto** (`ac.md` §5.2.11, `RN-AC-005` reescrita): `VAL-004` rechaza un video de presentación que no sea de YouTube o de Vimeo. Los ya guardados de otros dominios se conservan hasta que alguien los corrija. | Responsable técnico |
