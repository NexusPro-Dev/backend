# SPEC — `RF-AC-030` Cambiar el estado de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-030` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Publicar una lección cuando tiene **contenido**, y despublicarla sin condiciones.

## 2. Contexto

Es `RF-AC-024` en la hoja del árbol: la única condición de `RN-AC-009` para la lección es **tener contenido** —una URL si es `VIDEO`, un texto si es `TEXTO`—. Desactivar nunca se rechaza, **aunque sea la última lección activa de un módulo activo**: el módulo queda `ACTIVO` y no ofrecible, y con él el curso si no tiene otro. Es el primer cambio de estado que **cambia la duración** del módulo y del curso: solo suman lecciones activas (`RN-AC-017`).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Publica o despublica la lección |

## 4. Alcance

### 4.1 Incluye

- Cambiar el estado de una lección viva entre `ACTIVO` e `INACTIVO`.
- Al activar, comprobar el contenido.
- Devolver la lección.

### 4.2 No incluye

- **Activar el módulo o el curso** en cascada, ni desactivarlos.
- **Exigir `open`** ni nada más que el contenido.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-008`, `RN-AC-009` | Los dos estados; activar exige contenido; desactivar nunca se rechaza | `requirements/ac.md` §5.1 |
| `RN-AC-015`, `RN-AC-017` | El módulo sin lección activa sigue activo y no se ofrece; la duración suma activas | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Lo retirado no cambia de estado | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso, módulo y lección | Sí | Cuál | Ruta. Lección **viva** de ese módulo de ese curso |
| `status` | Sí | `ACTIVO` o `INACTIVO` | Dominio cerrado |

### 6.2 Salida

`200` con la lección. Mismo estado: `200` sin escribir ni auditar.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; lección viva de la ruta; para activar, contenido no vacío.

**Postcondiciones:** el estado nuevo, `updated_at` avanzado si cambió, fila `UPDATE`; la duración y la ofrecibilidad del módulo y del curso cambian en su siguiente lectura.

## 8. Flujo principal

1. Llega la petición con el estado.
2. El sistema valida su forma (`VAL-002`).
3. El sistema resuelve la lección **viva** de la ruta, bloqueándola (`EX-001`).
4. Si el estado es el actual, `200` sin escribir.
5. Si es `ACTIVO`, comprueba el contenido (`EX-002`).
6. Escribe, audita y devuelve la lección.

## 9. Flujos alternativos

### FA-001 — Activar la primera lección de un módulo `INACTIVO`

**Comportamiento:** se activa; el módulo sigue inactivo y desde ahora **puede** activarse.

### FA-002 — Desactivar la última lección activa de un módulo activo

**Comportamiento:** se desactiva. El módulo sigue `ACTIVO` y no ofrecible; el curso, si no tiene otro módulo ofrecible, tampoco.

## 10. Excepciones

### EX-001 — La lección no existe, está retirada o no es de ese módulo y curso

**Respuesta del sistema:** `404` — *«No existe una lección viva con ese identificador en este módulo.»*

### EX-002 — Activar sin contenido

**Respuesta del sistema:** `409` — *«La lección no tiene contenido: no se publica lo que está vacío.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | `status` presente y en el dominio | El estado es obligatorio y debe ser ACTIVO o INACTIVO. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-110` | El sistema activa una lección con contenido —de los dos tipos— y devuelve `ACTIVO`; sin contenido, `409` |
| `CA-AC-111` | Desactivar no exige nada; desactivar la **última activa de un módulo activo** deja el módulo `ACTIVO` con `offerable: false`, y **la duración del módulo y del curso bajan** en su siguiente lectura |
| `CA-AC-112` | Mismo estado: `200` sin auditar; cambio real: fila `UPDATE` |
| `CA-AC-113` | Una lección retirada, inexistente o de otro módulo o curso responde `404`; un `status` fuera de dominio, `400` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Activar una lección `open` en un curso que no se ofrece | Se activa; la demostración no se ve hasta que el curso se ofrezca (`RN-AC-014`) |
| Vaciar el contenido después de activar | Se admite por `RF-AC-029`; la lección sigue activa y **deja de ofrecerse** (`RF-AC-029` §14.1, desde el 18-09-2026) |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| — | Ninguna propia; la lección activa y vacía se resolvió en `RF-AC-029` §14.1 el 18-09-2026 | |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-024` en la hoja: activar exige contenido; desactivar la última no toca el módulo, y es el primer cambio de estado que mueve la duración. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—. El caso límite de vaciar después de activar pasa a decir «deja de ofrecerse». | Responsable técnico |
| 0.3.0 | 19-09-2026 | **Construida** (`LessonStatusIT` (4)). La condición la lanza `Lesson.activate` sin leer nada: dos sentencias y la relectura. | Responsable técnico |
