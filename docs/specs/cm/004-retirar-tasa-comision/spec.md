# SPEC — `RF-CM-004` Retirar una tasa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-004` |
| Módulo | `CM` — Comisiones |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

!!! warning "Esta especificación se reescribió después de construirse, y ganó una regla por hacerlo"

    `RN-CM-015` —una tasa asociada no se retira— **no salió del diseño: salió de construir el módulo**. Es la única regla de `CM` con ese origen, y su motivo está en §2.

---

## 1. Objetivo

Retirar del catálogo una tasa de comisión **que no debió existir**, dejando constancia de por qué.

## 2. Contexto

Se retira lo que fue un error: una tasa duplicada, una declarada sobre el rol equivocado, una que nació de una decisión que se revirtió. **La fila permanece** (`RN-CM-005`), porque una liquidación pasada tiene que poder seguir explicando con qué porcentaje se pagó.

**Retirar ya no es una de dos formas de dejar de pagar, sino la más destructiva de dos.** Con el modelo nuevo hay otra: **desasociar** la tasa del producto (`RF-CM-008`). La tasa sigue en el catálogo, disponible para otros productos, y ese producto deja de comisionar a ese rol. Retirar destruye la tasa; desasociar solo deja de aplicarla.

**Y hay un orden entre las dos que el sistema impone.** Una tasa que rige sobre algún producto **no se puede retirar** (`RN-CM-015`). El motivo es que la asociación **no tiene retiro lógico**: su fila sobreviviría apuntando a una tasa muerta, la resolución dejaría de encontrarla, y **el producto pasaría a no comisionar sin que nada lo indicara**. Es la silenciosidad que `RN-CM-012` describe, llegando por la puerta de atrás.

Esto no lo vio el diseño. Se descubrió construyendo el módulo el 02-09-2026, y la alternativa —borrar las asociaciones en cascada— se descartó porque destruiría configuración que nadie pidió destruir.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Retira la tasa e indica el motivo |

## 4. Alcance

### 4.1 Incluye

- Retirar una tasa de comisión **con motivo obligatorio**.
- **Rechazar el retiro de una tasa que todavía rige** sobre algún producto.
- Dejar en la auditoría de eliminaciones quién, cuándo, por qué y **la instantánea** de lo retirado.
- Hacer que la tasa retirada **deje de resolver** y desaparezca de los listados salvo que se pidan.

### 4.2 No incluye

- **Dejar de pagar sin destruir la tasa.** Eso es `RF-CM-008`, y es la operación que casi siempre se busca.
- **Deshacer el retiro.** Un retiro con motivo es un hecho registrado. Si la tasa vuelve a hacer falta, se declara de nuevo.
- **Borrar la fila.** La eliminación es lógica (`RN-CM-005`).
- **Retirar una tasa personalizada.** Es la misma operación sobre otra tabla y otro recurso: ver `RF-CM-006` §4.
- **Retirar en cascada las asociaciones.** Se consideró y se descartó. Ver §2 y `plan.md` §9.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-005` | La tasa no desaparece | `requirements/cm.md` §5.1 |
| `RN-CM-015` | Una tasa asociada no se retira | `requirements/cm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador de la tasa | Sí | Cuál se retira | Debe existir, **no estar ya retirada** y **no regir sobre ningún producto** |
| Motivo | Sí | Por qué se retira | No puede estar en blanco ni exceder la longitud admitida (Art. V.13) |

### 6.2 Salida

**Ninguna.** La operación no devuelve cuerpo: lo que había que decir sobre la tasa retirada está en el registro de eliminación, no en la respuesta.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de eliminación de tasas.
- La tasa existe, está viva y **no rige sobre ningún producto**.

**Postcondiciones**

- La tasa queda marcada como retirada y **la fila permanece**.
- La auditoría de eliminaciones contiene un evento con el motivo y la instantánea de lo retirado.
- La tasa **deja de resolver** y no aparece en los listados salvo que se pidan las retiradas.
- **Ningún producto cambia lo que paga por esta operación**, porque ninguno podía estar asociado.

## 8. Flujo principal

1. El actor envía el identificador de la tasa y el motivo.
2. El sistema comprueba que el motivo no está vacío ni excede la longitud admitida.
3. El sistema comprueba que la tasa existe.
4. El sistema comprueba que no está ya retirada.
5. El sistema comprueba que **no rige sobre ningún producto**.
6. El sistema toma la instantánea de la tasa **tal como está**, la marca como retirada y emite el evento de eliminación.

**El motivo se verifica el primero de todo**, y no es un detalle de orden: el Art. V.13 exige rechazar la eliminación sin motivo **antes de ejecutarla**, y hacerlo primero significa además que un motivo vacío no cuesta ni una consulta.

**La instantánea se toma antes de retirar**, porque debe describir la tasa tal como estaba.

## 9. Flujos alternativos

### FA-001 — La tasa nunca se asoció a nada

**Cuándo ocurre:** la tasa está en el catálogo y nadie la puso en vigor.

1. La comprobación del paso 5 pasa sin más.
2. Es **el caso normal** de este requerimiento, y conviene decirlo: retirar es la forma de limpiar el catálogo de lo que se declaró y nunca se usó.

## 10. Excepciones

### EX-001 — La tasa no existe

**Condición:** el identificador no corresponde a ninguna tasa.
**Respuesta del sistema:** rechaza el retiro diciendo que la tasa indicada no existe.

### EX-002 — La tasa ya estaba retirada

**Condición:** la tasa existe y ya fue retirada.
**Respuesta del sistema:** rechaza el retiro diciendo que ya estaba retirada. **No es un «no encontrado»**: la tasa existe, y decir que no existe escondería que el retiro **ya ocurrió**, que es justo lo que quien repite la operación necesita saber.

**No es idempotente a propósito:** retirar dos veces con dos motivos distintos dejaría el segundo escrito sobre un hecho anterior, y el registro pasaría a mentir sobre por qué se retiró.

### EX-003 — La tasa rige sobre algún producto

**Condición:** la tasa está asociada a uno o más productos.
**Respuesta del sistema:** rechaza el retiro diciendo que hay que retirar primero esas asociaciones, **y explicando por qué**: de otro modo el producto dejaría de comisionar sin que nada lo indicara.

**Es la excepción que evita el defecto silencioso de este requerimiento**, y la que obliga a dos operaciones donde parecía haber una. Ese coste se paga a la vista.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-007` | Motivo obligatorio | El motivo del retiro es obligatorio. |
| `VAL-008` | Longitud del motivo | El motivo no puede exceder 500 caracteres. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-029` | El sistema retira la tasa con motivo, y **la fila permanece** |
| `CA-CM-030` | El registro de eliminación lleva el motivo y la instantánea de lo retirado |
| `CA-CM-031` | La tasa retirada **deja de resolver** y no sale en el listado salvo que se pidan las retiradas |
| `CA-CM-032` | El sistema **rechaza** retirar una tasa que rige sobre algún producto, y la tasa **sigue viva** |
| `CA-CM-033` | Desasociada primero, la misma tasa **sí se retira** |
| `CA-CM-034` | El sistema rechaza el retiro sin motivo, o con el motivo en blanco, **sin retirar nada** |
| `CA-CM-035` | Retirar dos veces devuelve conflicto, y no «no encontrado» |
| `CA-CM-036` | El sistema rechaza retirar una tasa inexistente |
| `CA-CM-037` | Una tasa retirada **no se puede asociar** a ningún producto |

## 13. Casos límite

- **Retirar la tasa que rige hoy sobre veinte productos:** no se puede. Hay que desasociar los veinte primero, y en cada paso se está decidiendo explícitamente que ese producto deja de comisionar. **Es un coste deliberado**: la alternativa era que una sola operación lo hiciera en silencio.
- **Una tasa retirada que se quiere reutilizar:** se declara de nuevo. No hay operación de reactivación, y no se echa en falta: una tasa son dos campos.
- **Retirar la única tasa de un rol:** se admite. El rol se queda sin nada que asociar, y eso es una decisión legítima de quien administra — el sistema no puede saber si es un descuido.
- **Retirar la tasa que una asociación acaba de dejar libre:** funciona, y es la secuencia normal. Desasociar borra la fila de la asociación de verdad, de modo que la comprobación del paso 5 la deja de ver inmediatamente.
- **Dos retiros simultáneos de la misma tasa:** uno queda y el otro recibe el conflicto. Lo que no puede ocurrir es que se escriban **dos registros de eliminación** con dos motivos distintos sobre un mismo hecho.
- **La asociación se crea mientras se está retirando la tasa:** la comprobación y el retiro ocurren en la misma transacción, y asociar exige que la tasa esté viva. Las dos operaciones no pueden dejar el sistema en el estado que `RN-CM-015` evita.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Reescrita sobre el modelo de `cm.md` v0.4.0**, y después de construirse el código. El requerimiento **gana una regla que el diseño no había visto**: `RN-CM-015` —una tasa asociada no se retira—, la única de `CM` nacida de construirlo. Su motivo entra en §2 y su rechazo es `EX-003`: la asociación no tiene retiro lógico y sobreviviría apuntando a una tasa muerta, de modo que **el producto dejaría de comisionar sin que nada lo indicara**. La cascada se descartó porque destruye configuración que nadie pidió destruir. **Y el requerimiento cambia de sitio en el módulo**: deja de ser «la forma de dejar de pagar» para ser **la más destructiva de dos**, porque `RF-CM-008` ahora hace lo otro sin destruir nada. Desaparece toda la argumentación sobre la vigencia —la tabla ya no la tiene—, y con ella el criterio de no cerrarla al retirar, que **se conserva en `RF-CM-006`**, donde sigue habiendo vigencia que no tocar. §13 recoge el coste declarado: retirar una tasa que rige sobre veinte productos exige veinte decisiones explícitas antes. | Responsable técnico |
