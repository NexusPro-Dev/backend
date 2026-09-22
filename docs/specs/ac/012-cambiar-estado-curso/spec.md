# SPEC — `RF-AC-012` Cambiar el estado de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-012` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Publicar un curso cuando tiene con qué, y despublicarlo cuando haga falta, sin condiciones.

## 2. Contexto

Es `RF-PM-021` para cursos, y hereda su forma: `ACTIVO` ↔ `INACTIVO`, activar exige condiciones **que se comprueban juntas** y desactivar no exige nada; pedir el estado que ya tiene responde `200` sin escribir. **Lo que exige activar lo dice `RN-AC-009`**: descripción corta, descripción larga y **al menos un módulo `ACTIVO` no retirado**. No se exige una membresía: un curso activo sin lista **existe y no se ofrece**, y el detalle lo dice (`RN-AC-012`, `RN-AC-015`) — es la misma decisión que `PM` tomó con el alcance `NINGUNO`.

**Y lo que después se vacía no desactiva nada** (`RN-AC-009`): retirar el último módulo activo de un curso activo lo deja `ACTIVO` y no ofrecible. El estado es lo que alguien decidió; lo que lo detiene es un hecho que se enseña en lugar de copiarse.

**Hasta que existan los módulos (`RF-AC-022`, `RF-AC-024`), ningún curso puede activarse** por el tercer motivo. Se escribe igual: la regla es de este requerimiento y el bloque 3 solo le da con qué cumplirla. Queda como bloqueo explícito en `tasks.md`.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Publica o despublica el curso |

## 4. Alcance

### 4.1 Incluye

- Cambiar el estado de un curso vivo entre `ACTIVO` e `INACTIVO`.
- Al activar, comprobar **juntas** las tres condiciones de `RN-AC-009`.
- Devolver el curso en la forma del detalle.

### 4.2 No incluye

- **Exigir membresías, categorías o portada** para activar. Ninguna condiciona el estado (`RN-AC-009`, `RN-AC-012`).
- **Desactivar por un hecho** —quedarse sin módulos, sin descripción—. La ofrecibilidad lo enseña.
- **Activar los módulos o las lecciones** en cascada. Cada uno se activa con su operación.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-008` | Los dos estados | `requirements/ac.md` §5.1 |
| `RN-AC-009` | **No se publica lo que está vacío**: descripciones y un módulo activo, al activar y solo al activar; desactivar nunca se rechaza | `requirements/ac.md` §5.1 |
| `RN-AC-012` | Activar no exige membresías; sin ellas no se ofrece | `requirements/ac.md` §5.1 |
| `RN-AC-015` | El detalle dice por qué no se ofrece un curso activo | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Lo retirado no cambia de estado | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta. Curso **vivo** |
| `status` | Sí | `ACTIVO` o `INACTIVO` | Dominio cerrado |

### 6.2 Salida

`200` con el detalle. Si el estado pedido es el que ya tiene, `200` sin escribir ni auditar.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; para activar, las dos descripciones y al menos un módulo `ACTIVO` no retirado.

**Postcondiciones:** el estado nuevo, `updated_at` avanzado si cambió, fila `UPDATE` en la auditoría con antes y después.

## 8. Flujo principal

1. Llega la petición con el estado.
2. El sistema valida su forma (`VAL-002`).
3. El sistema resuelve el curso **vivo**, bloqueándolo (`EX-001`).
4. Si el estado es el actual, devuelve `200` sin escribir.
5. Si es `ACTIVO`, comprueba descripción corta (`EX-002`), descripción larga (`EX-003`) y módulo activo (`EX-004`) — **juntos**: si faltan las tres cosas, lo dice de una vez.
6. Escribe, audita y devuelve el detalle.

## 9. Flujos alternativos

### FA-001 — Activar sin membresías

**Comportamiento:** **se activa.** El detalle devuelve `offerable: false` diciendo «sin membresías», y el aula no lo enseña hasta que `RF-AC-020` le dé una.

### FA-002 — Activar con un módulo activo cuyas lecciones están todas inactivas

**Comportamiento:** **se activa**: la condición es un módulo `ACTIVO`, y un módulo activo tuvo al menos una lección activa al activarse (`RF-AC-024`). Si después se quedó sin ella, el detalle devuelve `offerable: false` por el último motivo de `RN-AC-015`. Es el mismo trato que `PM` da a activar un paquete con un producto inactivo dentro.

### FA-003 — Desactivar

**Comportamiento:** sin condiciones. Sale del aula; sus módulos, lecciones y relaciones no cambian.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — Activar sin descripción corta

**Respuesta del sistema:** `409` — *«El curso no tiene descripción corta: no se publica lo que no se explica.»*

### EX-003 — Activar sin descripción larga

**Respuesta del sistema:** `409` — *«El curso no tiene descripción larga: no se publica lo que no se explica.»*

### EX-004 — Activar sin un módulo activo

**Respuesta del sistema:** `409` — *«El curso no tiene ningún módulo activo: no se publica lo que está vacío.»*

Los tres van **juntos** en la misma respuesta cuando ocurren a la vez.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | `status` presente y en el dominio | El estado es obligatorio y debe ser ACTIVO o INACTIVO. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-064` | El sistema activa un curso con las dos descripciones y un módulo activo, y devuelve el detalle `ACTIVO` con `updatedAt` avanzado — **bloqueado hasta `RF-AC-024`** |
| `CA-AC-065` | El sistema rechaza con `409` activar sin descripción corta, sin descripción larga y sin módulo activo, y cuando faltan **varias** la respuesta trae **todos** los motivos |
| `CA-AC-066` | El sistema **activa** un curso sin membresías, y el detalle lo devuelve `offerable: false` diciendo «sin membresías» |
| `CA-AC-067` | Desactivar no exige nada y no toca módulos ni relaciones; y **vaciar una descripción o retirar el último módulo activo** después **no** cambia el estado |
| `CA-AC-068` | Pedir el estado que ya tiene responde `200` sin avanzar `updatedAt` ni auditar; un cambio real deja la fila `UPDATE` con antes y después |
| `CA-AC-069` | Un curso retirado responde `404`; un `status` fuera de dominio o ausente, `400` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Activar mientras otro retira el único módulo activo | El bloqueo del curso ordena las dos operaciones; la que llegue segunda ve el estado que dejó la primera. Si el retiro gana, la activación recibe `409` |
| Dos activaciones simultáneas | Una escribe y la otra encuentra el estado ya puesto: `200` sin auditar |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se exige un módulo con lección activa, o basta el módulo activo? | **El módulo activo.** Un módulo solo se activa con una lección activa (`RF-AC-024`), de modo que exigirlo aquí es exigirlo dos veces; y si después se queda sin ella, `RN-AC-015` lo enseña. Repetir la cuenta de lecciones aquí sería una tercera copia de la misma regla |
| 2 | ¿Se puede construir antes del bloque 3? | **Sí, y se construye**: desactivar, los `409` de descripciones, el `200` sin cambio y el `404` no dependen de módulos. Solo `CA-AC-064` espera |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Hereda `RF-PM-021` —condiciones juntas al activar, nada al desactivar, mismo estado sin escribir— con las tres condiciones de `RN-AC-009`. **No exige membresías** y **no desactiva por hechos**. Declara que `CA-AC-064` queda bloqueado hasta `RF-AC-024`. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—. Activar no cambia —`RN-AC-009` ya exigía las dos descripciones—; `FA-002` pasa a decir «el último motivo», que ahora es el quinto. | Responsable técnico |
| 0.3.0 | 18-09-2026 | **Construida** (`CourseStatusIT` (5)) **con `CA-AC-064` bloqueado**: hoy ningún curso se activa por la API porque `countActiveModulesOf` devuelve cero hasta `RF-AC-022`; los tres `409` llegan juntos como `errors[]` bajo el código del primero, y `CA-AC-066`/`067` se prueban sobre un curso activo por siembra. | Responsable técnico |
