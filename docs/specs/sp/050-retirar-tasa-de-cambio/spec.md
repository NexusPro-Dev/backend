# SPEC — `RF-SP-050` Retirar una tasa de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-050` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |

---

## 1. Objetivo

Sacar de circulación una tasa **que no debió existir**, dejando constancia de por qué.

## 2. Contexto

**Retirar no es suspender, y la diferencia es de intención.** Suspender (`RF-SP-049`) dice «esta tasa no rige ahora» y admite volver atrás; retirar dice «esta tasa fue un error» y no admite volver. Las dos liberan el periodo —el `EXCLUDE` es parcial sobre las dos condiciones—, y aun así son dos operaciones porque responden dos preguntas distintas que quien lea la auditoría dentro de un año querrá poder separar.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Retira la tasa y declara el motivo |

## 4. Alcance

### 4.1 Incluye

- Retirar una tasa **exigiendo motivo** (Art. V.13).
- Registrar el motivo y la **instantánea** de lo retirado en el registro de eliminación.
- **Liberar el periodo** que la tasa ocupaba.

### 4.2 No incluye

- **Eliminar la fila.** La eliminación es lógica: `RN-SP-033`.
- **Reactivar lo retirado.** Sin operación inversa. Ver §14, resolución 2.
- **Retirar en bloque.** Una a una.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-032` | Dos tasas vigentes del mismo par no se solapan | `requirements/sp.md` §5.2 |
| `RN-SP-033` | La tasa no desaparece: retiro lógico con motivo | `requirements/sp.md` §5.1 |
| Art. V.13 | Toda eliminación exige motivo | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál se retira | Debe existir y **no estar ya retirada** |
| Motivo | **Sí** | Por qué se retira | No vacío, con longitud acotada |

**El motivo va en el cuerpo y no en la URL**, y por eso la operación es un `POST` sobre un subrecurso y no un `DELETE`: la RFC 9110 **no define semántica para el cuerpo de un `DELETE`** y un intermediario puede descartarlo, con lo que la petición llegaría **sin el motivo** que el Art. V.13 exige. Es la misma forma que usan `RF-SP-009`, `RF-SP-029` y `RF-PM-006`.

### 6.2 Salida

Confirmación del retiro, con **desde cuándo** lo está. **El motivo no vuelve en la respuesta**: quien lo acaba de escribir ya lo sabe, y devolverlo invitaría a tratarlo como un dato del recurso en vez de una entrada de auditoría.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y porta `exchange-rates:delete`.
- La tasa existe y **no está ya retirada**.

**Postcondiciones**

- La fila tiene `deleted_at`, y **sigue existiendo**.
- El registro de eliminación tiene el motivo y la **instantánea** de la fila.
- **El periodo queda libre**: otra tasa del mismo par puede cubrir esos días.
- **El estado NO se toca.** Ver §13.

## 8. Flujo principal

1. El actor envía el identificador y el motivo.
2. El sistema valida el motivo.
3. El sistema **bloquea la fila** y comprueba que existe y no está ya retirada.
4. El sistema **captura la instantánea antes de marcar**.
5. El sistema marca `deleted_at` y escribe el registro de eliminación, en la misma transacción.

## 9. Flujos alternativos

### FA-001 — La tasa estaba suspendida

**Comportamiento:** se retira igual. Suspendida y retirada no son excluyentes: una dice que no rige, la otra que no debió existir.

### FA-002 — La tasa ya venció

**Comportamiento:** se retira igual. Que su periodo haya pasado no la hace correcta.

## 10. Excepciones

### EX-001 — Tasa inexistente o ya retirada

**Respuesta del sistema:** responde que el recurso no existe. **Retirar dos veces no es idempotente y no se finge que lo sea**: el segundo motivo se escribiría sobre un hecho que ocurrió antes y por otra razón.

### EX-002 — Motivo ausente o vacío

**Respuesta del sistema:** rechaza la operación **sin retirar nada**. El motivo es lo único que distingue esta operación de un borrado a ciegas.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Motivo obligatorio | El motivo del retiro es obligatorio. |
| `VAL-003` | Longitud del motivo | El motivo no puede exceder la longitud admitida. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-565` | El sistema retira la tasa y **la fila permanece**, marcada y con la fecha del retiro |
| `CA-SP-566` | El sistema **exige motivo** y rechaza la operación sin él, **sin retirar nada** |
| `CA-SP-567` | El sistema registra el motivo y **la instantánea de la fila** en el registro de eliminación |
| `CA-SP-568` | El sistema **libera el periodo**: después de retirar, otra tasa del mismo par puede cubrir esos mismos días |
| `CA-SP-569` | El sistema **no toca el estado**: una tasa activa se retira **activa** |
| `CA-SP-570` | El sistema rechaza retirar una tasa **ya retirada** |
| `CA-SP-571` | El sistema rechaza el retiro a un actor sin `exchange-rates:delete`, y no retira nada |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| **El estado no se toca al retirar** | Y no es un olvido: `CA-SP-569` lo exige. Si retirar desactivara «de paso», **todas** las instantáneas del registro de eliminación dirían `is_active = false` y ese dato dejaría de significar nada — la salvaguarda habría destruido justo la evidencia que protege. Es la misma decisión que `RF-PM-006` tomó con el estado de un producto |
| **La instantánea se captura ANTES de marcar** | Capturarla después guardaría la fila ya retirada, y el registro diría que se retiró algo que ya estaba retirado. El sistema funcionaría y el registro mentiría |
| Retirar la única tasa vigente de un par | **Se admite.** El par se queda sin tasa, y eso es un estado legítimo: `RF-SP-048` devolverá la colección vacía para ese día (`CA-SP-547`) y **no rellenará con la más cercana** |
| Dos retiros simultáneos de la misma tasa | El **bloqueo** los serializa: uno retira y el otro recibe `404`, porque para el segundo la tasa ya no está viva |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se puede retirar una tasa que ya se usó para convertir? | **Hoy la pregunta no se puede hacer**: nadie convierte. Y la respuesta queda decidida por adelantado — **sí se podrá**, porque la fila permanece (`RN-SP-033`) y porque la conversión tendrá que **copiar la tasa aplicada** en su propia fila, condición que `RF-SP-049` §13 ya impone sobre esa operación futura |
| 2 | ¿Existe una operación inversa, «restaurar»? | **No.** Retirar declara que la tasa **fue un error**, y deshacerlo convertiría el registro de eliminación en un borrador. Quien se equivoque al retirar **registra otra tasa** con los mismos valores: cuesta una operación y deja las dos cosas escritas — el error y la enmienda |
| 3 | ¿Por qué no reutilizar la suspensión de `RF-SP-049` para esto? | Porque **responden dos preguntas distintas**. Suspender dice «no rige ahora» y admite volver; retirar dice «no debió existir» y no. Fundirlas ahorraría un endpoint y borraría la única señal que separa una decisión comercial de una corrección de error |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 07-09-2026 | Redacción inicial. **Las dos decisiones que carga son las que este proyecto ya ha pagado antes**: la instantánea se captura **antes** de marcar —después guardaría la fila ya retirada y el registro mentiría— y **el estado no se toca**, porque desactivar «de paso» haría que todas las instantáneas dijeran lo mismo y ese dato dejaría de significar algo. Las dos las estrenó `RF-PM-006`. **Retirar y suspender no se funden**, aunque las dos liberen el periodo: responden «no debió existir» y «no rige ahora», y fundirlas borraría la única señal que separa una corrección de error de una decisión comercial. **Sin operación inversa**: quien se equivoque registra otra tasa, y quedan escritas las dos cosas. | Responsable del proyecto |
