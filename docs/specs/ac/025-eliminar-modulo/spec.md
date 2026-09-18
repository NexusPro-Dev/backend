# SPEC — `RF-AC-025` Eliminar módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-025` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Retirar una parte del curso, **con motivo**, llevándose sus lecciones.

## 2. Contexto

Es `RF-AC-013` un nivel más abajo y con la misma forma de arrastre: retirar un módulo **retira sus lecciones vivas** con el mismo motivo, en la misma transacción y con un registro de eliminación por fila (`RN-AC-018`), sobre el mismo colaborador que el retiro del curso (`CourseTreeRetirement`, `RF-AC-013` plan §1). **Se hace con `courses:update` y no con `courses:delete`**: retirar una parte es corregir el curso; retirar el curso es otra cosa (`ac.md` §7).

**Y no toca el curso**: si el módulo era el último activo, el curso sigue `ACTIVO` y no ofrecible (`RN-AC-009`, `RN-AC-015`).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Retira el módulo |

## 4. Alcance

### 4.1 Incluye

- Retirar lógicamente un módulo vivo del curso de la ruta, en cualquier estado, con motivo.
- **Arrastrar** sus lecciones vivas, con el mismo motivo y un registro cada una.
- Registrar la baja con la instantánea del módulo y los identificadores de sus lecciones.

### 4.2 No incluye

- **Tocar el curso** ni sus otros módulos.
- **Borrar la portada** del módulo: la fila retirada la sigue señalando.
- **Revivir** un módulo retirado.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-009`, `RN-AC-015` | El curso que se queda sin módulo activo sigue activo y no se ofrece | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Retiro lógico con motivo y registro por fila; **retirar un módulo arrastra sus lecciones** | `requirements/ac.md` §5.1 |
| `RN-AC-019` | Una lección no existe fuera de su módulo; por eso se arrastra | `requirements/ac.md` §5.1 |
| Art. V.13 | El motivo es obligatorio y viaja con la instantánea | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso y módulo | Sí | Cuál | Ruta. Módulo **vivo** de ese curso |
| `reason` | Sí | Por qué | Con contenido tras recortar; hasta 500 |

### 6.2 Salida

`204`. El módulo retirado se ve en el detalle del curso, marcado y con sus lecciones marcadas.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; módulo vivo del curso de la ruta; motivo con contenido.

**Postcondiciones:** `deleted_at` en el módulo —y nada más de su fila cambia, `status` incluido— y en cada lección viva; `audit_deletion_log` con una fila `LOGICAL` del módulo y una por lección arrastrada; el curso intacto.

## 8. Flujo principal

1. Llega la petición con el motivo.
2. El sistema valida el motivo **antes de cualquier consulta** (`VAL-002`, `VAL-003`).
3. El sistema resuelve el módulo del curso de la ruta **en cualquier estado**, bloqueándolo: si no existe o no es de ese curso, `EX-001`; si ya está retirado, `EX-002`.
4. El sistema toma la instantánea —módulo e identificadores de sus lecciones vivas—, marca `deleted_at`, y registra la baja.
5. El sistema resuelve las lecciones vivas, las marca con el mismo instante y registra la baja de cada una con el mismo motivo.
6. Devuelve `204`.

## 9. Flujos alternativos

### FA-001 — El módulo es el último activo de un curso activo y ofrecido

**Comportamiento:** se retira. El curso sigue `ACTIVO`, sale del aula, y su detalle dice «sin módulo activo con lección activa».

### FA-002 — El módulo no tiene lecciones vivas

**Comportamiento:** un solo registro, el del módulo.

## 10. Excepciones

### EX-001 — El módulo no existe o no es de ese curso

**Respuesta del sistema:** `404` — *«No existe un módulo con ese identificador en este curso.»*

### EX-002 — El módulo ya está retirado

**Respuesta del sistema:** `409` — *«El módulo ya está retirado.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Motivo presente y con contenido | El motivo de la eliminación es obligatorio. |
| `VAL-003` | Motivo de hasta 500 caracteres | El motivo no puede exceder 500 caracteres. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-114` | El sistema retira el módulo con `204`; la fila conserva `status` y `cover_image_id`; **sus lecciones vivas quedan retiradas** con el mismo instante, una fila de auditoría cada una con el mismo motivo, y las ya retiradas no se tocan |
| `CA-AC-115` | El sistema rechaza con `400` un motivo ausente, vacío o largo **sin consultar nada**; responde `404` al inexistente y al de otro curso, y `409` al ya retirado |
| `CA-AC-116` | `audit_deletion_log` tiene la fila `LOGICAL` del módulo con el motivo, el actor y la instantánea con los identificadores de sus lecciones, y `deleted_at` nulo dentro |
| `CA-AC-117` | Retirar el **último módulo activo** de un curso activo deja el curso `ACTIVO` con `offerable: false`; el detalle del curso enseña el módulo y sus lecciones marcados; el título del módulo **queda libre** en el curso |
| `CA-AC-118` | Dos retiros simultáneos dejan un `204`, un `409` y **una** fila del módulo |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Retirar mientras otro registra una lección en el módulo | El bloqueo del módulo los ordena; si el retiro gana, el alta recibe `404` |
| Retirar mientras el curso se retira | El retiro del curso bloquea curso → módulos; este bloquea el módulo. Quien llegue segundo al módulo encuentra `deleted_at` y responde `409` — o, si es el arrastre del curso, lo salta por ya retirado |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿`courses:update` o `courses:delete`? | **`courses:update`** (`ac.md` §7): el módulo es parte del curso, y quien puede armarlo puede desarmarlo. `courses:delete` retira el curso entero |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. `RF-AC-013` un nivel abajo, sobre `CourseTreeRetirement`; con `courses:update`; no toca el curso. | Responsable técnico |
