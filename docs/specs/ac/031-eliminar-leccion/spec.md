# SPEC — `RF-AC-031` Eliminar lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-031` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Retirar una lección, **con motivo**, sin perder lo que decía.

## 2. Contexto

Es `RF-AC-005` en la hoja del árbol: no arrastra nada, porque no hay nada debajo, y **no toca el módulo ni el curso**: si era la última lección activa, el módulo sigue `ACTIVO` y no ofrecible (`RN-AC-009`). Lo único propio es la instantánea: **es la única de Academia que lleva el contenido entero** (`RF-AC-028` §14.1), porque es la última copia — la fila retirada lo conserva, pero el registro de eliminación tiene que poder decir qué se retiró sin depender de que nadie borre la fila algún día.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Retira la lección |

## 4. Alcance

### 4.1 Incluye

- Retirar lógicamente una lección viva de la ruta, en cualquier estado, con motivo.
- Registrar la baja con la instantánea **con el contenido entero**.

### 4.2 No incluye

- **Tocar el módulo ni el curso.**
- **Revivir** una lección retirada. Se registra otra; el título queda libre en el módulo.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-009`, `RN-AC-015`, `RN-AC-017` | El módulo que se queda sin lección activa sigue activo y no se ofrece; la duración baja | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Retiro lógico con motivo y registro; no arrastra | `requirements/ac.md` §5.1 |
| Art. V.13 | El motivo es obligatorio y viaja con la instantánea | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso, módulo y lección | Sí | Cuál | Ruta. Lección **viva** de ese módulo de ese curso |
| `reason` | Sí | Por qué | Con contenido tras recortar; hasta 500 |

### 6.2 Salida

`204`.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; lección viva de la ruta; motivo con contenido.

**Postcondiciones:** `deleted_at` puesto y nada más de la fila cambia; `audit_deletion_log` con `LOGICAL`, el motivo y la instantánea con el contenido; módulo y curso intactos.

## 8. Flujo principal

1. Llega la petición con el motivo.
2. El sistema valida el motivo **antes de cualquier consulta**.
3. El sistema resuelve la lección de la ruta **en cualquier estado**, bloqueándola: `EX-001` si no existe o no es de ahí; `EX-002` si ya está retirada.
4. El sistema toma la instantánea, marca `deleted_at`, registra la baja. Devuelve `204`.

## 9. Flujos alternativos

### FA-001 — Era la última lección activa de un módulo activo

**Comportamiento:** se retira. El módulo sigue `ACTIVO` y no ofrecible; la duración del módulo y del curso bajan.

## 10. Excepciones

### EX-001 — La lección no existe o no es de ese módulo y curso

**Respuesta del sistema:** `404` — *«No existe una lección con ese identificador en este módulo.»*

### EX-002 — La lección ya está retirada

**Respuesta del sistema:** `409` — *«La lección ya está retirada.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Motivo presente y con contenido | El motivo de la eliminación es obligatorio. |
| `VAL-003` | Motivo de hasta 500 caracteres | El motivo no puede exceder 500 caracteres. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-119` | El sistema retira la lección con `204`; la fila conserva `status`, `content` y `open`; el módulo y el curso no cambian de estado |
| `CA-AC-120` | El sistema rechaza con `400` un motivo inválido **sin consultar nada**; `404` a la inexistente y a la de otro módulo o curso; `409` a la ya retirada |
| `CA-AC-121` | `audit_deletion_log` tiene la fila `LOGICAL` con el motivo, el actor y la instantánea **con el contenido entero** —también un Markdown largo— y `deleted_at` nulo dentro |
| `CA-AC-122` | Retirar la **última lección activa** deja el módulo `ACTIVO` con `offerable: false` y la duración del módulo y del curso bajan; el detalle del curso enseña la lección marcada; su título **queda libre** en el módulo |
| `CA-AC-123` | Dos retiros simultáneos dejan un `204`, un `409` y **una** fila |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Retirar una lección `open` | Se retira igual; deja de ser demostración |
| Retirar mientras el módulo se retira | Los dos bloquean la lección —el arrastre del módulo, después del módulo—; el segundo la ve retirada |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La instantánea lleva el contenido entero aunque sea largo? | **Sí.** Es la última copia y ocurre una vez por lección; lo que `RF-AC-028` §14.1 evita es multiplicarlo por cada corrección |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-005` en la hoja: sin arrastre, sin tocar el módulo, y **la única instantánea de Academia con el contenido entero**. | Responsable técnico |
