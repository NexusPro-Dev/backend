# SPEC — `RF-XXX-NNN` [Nombre de la funcionalidad]

| Campo | Valor |
|---|---|
| Requerimiento | `RF-XXX-NNN` |
| Módulo | `XXX` — [Nombre del módulo] |
| Estado | Borrador · En revisión · **Aprobada** |
| Autor | [Nombre] |
| Aprobada por | [Nombre] |
| Fecha de aprobación | [DD-MM-AAAA] |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

    Debe poder leerlo alguien del negocio y entenderlo completo. Es la primera compuerta del Art. I.6: hasta que no esté aprobada, no se escribe `plan.md`.

---

## 1. Objetivo

[Una o dos frases: qué necesidad resuelve esta funcionalidad. Si no puedes escribirlo sin mencionar tecnología, el objetivo todavía no está claro.]

## 2. Contexto

[Por qué se necesita ahora, qué existe hoy y qué problema concreto tiene. Quien lea esto dentro de un año debe entender la decisión sin preguntar.]

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| [Actor] | [Qué hace] |

## 4. Alcance

### 4.1 Incluye

- [Comportamiento]

### 4.2 No incluye

- [Comportamiento que deliberadamente queda fuera, y a qué requerimiento pertenece]

> El «no incluye» es tan importante como el «incluye»: es lo que evita que el alcance crezca durante la implementación.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-XXX-NNN` | [Enunciado] | [Documento donde está definida] |

Las reglas **no se redefinen aquí**: se referencian. Si la funcionalidad exige una regla nueva, primero se agrega al documento del módulo.

## 6. Datos

Descritos en lenguaje de negocio. El tipo de columna, la longitud y el nombre físico se deciden en `plan.md`.

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| [Dato] | Sí / No | [Qué significa] | [Qué valores admite y por qué] |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| [Dato] | [Qué significa] |

## 7. Flujo principal

1. El actor [acción].
2. El sistema [respuesta].
3. El sistema [validación].
4. El sistema [resultado].
5. El sistema informa [qué].

## 8. Flujos alternativos

### FA-001 — [Nombre]

**Cuándo ocurre:** [condición]

1. [Paso]
2. [Resultado]

## 9. Excepciones

### EX-001 — [Nombre]

**Condición:** [qué la provoca]
**Respuesta del sistema:** [qué hace y qué informa]

## 10. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | [Qué se valida] | [Mensaje al usuario, en español] |

## 11. Criterios de aceptación

Cada criterio debe ser observable y verificable. Si contiene «rápido», «fácil» o «intuitivo», no es un criterio (Art. II.2).

| ID | Criterio |
|---|---|
| `CA-XXX-001` | El sistema permite [acción verificable] |
| `CA-XXX-002` | El sistema rechaza [condición inválida] con [respuesta concreta] |
| `CA-XXX-003` | El sistema registra el evento de auditoría correspondiente |

Cada uno tendrá al menos una prueba automatizada asociada (Art. II.4).

## 12. Casos límite

- [Qué pasa con el valor vacío, el máximo, la concurrencia, el registro inexistente, el actor sin permiso…]

Los casos límite que no se enumeren aquí se descubrirán en producción.

## 13. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | [Pregunta] | [Quién decide] | Abierta / Resuelta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
