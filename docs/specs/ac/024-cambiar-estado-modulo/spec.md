# SPEC — `RF-AC-024` Cambiar el estado de un módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-024` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Publicar una parte del curso cuando tiene con qué —**al menos una lección activa**— y despublicarla sin condiciones.

## 2. Contexto

Es `RF-AC-012` un nivel más abajo, con una sola condición: `RN-AC-009` exige para el módulo **una lección `ACTIVA` no retirada**, y nada más —ni descripciones ni portada—. Desactivar nunca se rechaza, **aunque sea el último módulo activo de un curso activo**: el curso queda `ACTIVO` y no ofrecible, y el detalle lo dice (`RN-AC-015`). Es el requerimiento que **habilita `CA-AC-064` de `RF-AC-012`**: con un módulo activo, un curso puede activarse.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Publica o despublica el módulo |

## 4. Alcance

### 4.1 Incluye

- Cambiar el estado de un módulo vivo entre `ACTIVO` e `INACTIVO`.
- Al activar, comprobar la lección activa.
- Devolver el módulo en la forma de su detalle.
- **Habilitar** `CA-AC-064` de `RF-AC-012`.

### 4.2 No incluye

- **Activar las lecciones en cascada**, ni el curso.
- **Desactivar el curso** al desactivar su último módulo.
- **Exigir descripciones o portada.** Son del curso, no del módulo (`RN-AC-009`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-008`, `RN-AC-009` | Los dos estados; activar exige una lección activa; desactivar nunca se rechaza | `requirements/ac.md` §5.1 |
| `RN-AC-015` | El curso activo sin módulo activo sigue activo y no se ofrece | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Lo retirado no cambia de estado | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y módulo | Sí | Cuál | Ruta. Módulo **vivo** de ese curso |
| `status` | Sí | `ACTIVO` o `INACTIVO` | Dominio cerrado |

### 6.2 Salida

`200` con el detalle del módulo. Mismo estado: `200` sin escribir ni auditar.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; módulo vivo del curso de la ruta; para activar, una lección activa viva.

**Postcondiciones:** el estado nuevo, `updated_at` avanzado si cambió, fila `UPDATE`; la ofrecibilidad del curso cambia en su siguiente lectura.

## 8. Flujo principal

1. Llega la petición con el estado.
2. El sistema valida su forma (`VAL-002`).
3. El sistema resuelve el módulo **vivo** del curso, bloqueándolo (`EX-001`).
4. Si el estado es el actual, `200` sin escribir.
5. Si es `ACTIVO`, cuenta las lecciones activas vivas (`EX-002`).
6. Escribe, audita y devuelve el detalle.

## 9. Flujos alternativos

### FA-001 — Activar el primer módulo de un curso `INACTIVO`

**Comportamiento:** se activa. El curso sigue inactivo hasta que `RF-AC-012` lo active; desde ahora **puede**.

### FA-002 — Desactivar el último módulo activo de un curso activo y ofrecido

**Comportamiento:** se desactiva. El curso sigue `ACTIVO`, sale del aula, y su detalle dice «sin módulo activo con lección activa con contenido».

## 10. Excepciones

### EX-001 — El módulo no existe, está retirado o no es de ese curso

**Respuesta del sistema:** `404` — *«No existe un módulo vivo con ese identificador en este curso.»*

### EX-002 — Activar sin una lección activa

**Respuesta del sistema:** `409` — *«El módulo no tiene ninguna lección activa: no se publica lo que está vacío.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | `status` presente y en el dominio | El estado es obligatorio y debe ser ACTIVO o INACTIVO. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-105` | El sistema activa un módulo con una lección activa y devuelve el detalle `ACTIVO` con `offerable: true`; con lecciones **solo inactivas** o **solo retiradas**, `409` |
| `CA-AC-106` | Desactivar no exige nada, no toca las lecciones, y desactivar el **último módulo activo de un curso activo** deja el curso `ACTIVO` con `offerable: false` por el último motivo |
| `CA-AC-107` | Mismo estado: `200` sin avanzar `updatedAt` ni auditar; cambio real: fila `UPDATE` |
| `CA-AC-108` | Un módulo retirado, inexistente o de otro curso responde `404`; un `status` fuera de dominio, `400` |
| `CA-AC-109` | **Habilita `CA-AC-064` de `RF-AC-012`**: un curso con descripciones y un módulo activo **se activa** |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Activar mientras otro desactiva la única lección activa | El bloqueo del módulo los ordena: la desactivación de la lección (`RF-AC-030`) bloquea la lección y no el módulo, de modo que la activación puede ganar con la cuenta vieja. **Se acepta**: el resultado es un módulo activo con lección inactiva, que `RN-AC-015` enseña como no ofrecible y que es el mismo estado que produce desactivar la lección un segundo después |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Bloquear el módulo al cambiar de estado una lección, para cerrar el caso límite? | **No.** Cerraría una carrera cuyo resultado es un estado legítimo y que ya tiene salida, al precio de que toda escritura sobre una lección bloquee su módulo — y con `RF-AC-013` bloqueando curso → módulos, la cadena se alargaría sin ganar nada |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-012` con una sola condición —una lección activa—; desactivar el último módulo activo no toca el curso. **Habilita `CA-AC-064`.** El caso límite de la carrera con la lección se acepta y queda escrito. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—. Activar no cambia —una lección activa tuvo contenido al activarse—; el motivo del curso pasa a nombrarse «con contenido» y a ser el último de cinco. | Responsable técnico |
