# SPEC — `RF-CM-024` Revertir las comisiones de una línea cuyo vendedor se corrige

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-024` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que corregir a quién se atribuye una línea de venta **ya comisionada** no deje cobrando a la cadena equivocada: **si nada de su comisión se ha pagado**, la cadena vieja deja de contar, y la nueva se devenga como si la línea se acabara de atribuir.

---

## 2. Contexto

**El responsable del proyecto lo pidió el 30-09-2026**: «permitamos que se pueda actualizar el vendedor de una línea siempre y cuando esta comisión de la venta no se haya pagado» (`requirements/cm.md` v0.26.0 §5.10). Hasta ese día el vendedor de una línea quedaba congelado al confirmarse la venta (`RN-MV-035`), precisamente para que su comisión pudiera nacer sin que nadie la deshiciera.

**Una línea no tiene una comisión: tiene una por cada nivel de la cadena** (`RN-CM-011`), y cada una está en el lote de otra persona. «No se ha pagado» tiene que ser verdad **para todas**: si el superior de nivel `2` ya cobró, deshacer su parte sería quitarle dinero de la billetera, y esa operación no existe.

**Quien corrige el vendedor es `MV`** (`RF-MV-016`), y **quien sabe si se pagó es este módulo**. `MV` pregunta antes de escribir y este requerimiento responde (`RN-MV-053`): que sí, y ya revirtió; o que no, y por qué.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Pagado es cualquier nivel** | Si alguna comisión de la línea está en un lote pagado, no se libera (`RN-CM-047`) |
| **Un FTD contado tampoco** | Su conteo pagó escalones a toda la cadena vieja |
| **Revertir no es borrar** | Cada comisión queda en su lote, marcada, y fuera del total (`RN-CM-029`) |
| **La cadena nueva la devenga el devengo** | Con la tasa y la cadena del día de la venta, en el lote **abierto** de cada persona (`RN-CM-033`); aquí no se calcula nada |
| **Todo o nada** | Si `MV` rechaza la corrección por otra razón después de preguntar, la reversión se deshace con ella |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| `MV`, al corregir el vendedor de una línea de una venta confirmada | Pregunta si la línea puede cambiar de dueño |
| Quien administra las ventas | Es quien corrigió, y la reversión se le atribuye |

**No tiene ruta ni permiso propio**: la operación de la API es `RF-MV-016`, con `movements:assign-sellers`.

---

## 4. Alcance

### 4.1 Incluye

- Decidir si una línea comisionada puede cambiar de vendedor.
- Si puede, revertir todas sus comisiones vivas —estén en un lote abierto o pendiente, retiradas o no—, rebajar el total de cada lote y borrar el desenlace de la línea.
- Si no puede, decir por qué sin cambiar nada.

### 4.2 No incluye

- **Devengar la cadena nueva**: lo hace `RF-CM-013`, con el aviso de `MV` después de su commit.
- **Revertir lo pagado.** No existe operación que saque dinero de una billetera por esta vía.
- **Revertir sin corregir el vendedor**: no hay otro camino que llegue aquí.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-047` | Cuándo se permite y qué se revierte |
| `RN-CM-029` | Nada se borra: la comisión revertida queda en su lote |
| `RN-CM-027` | La unicidad cuenta solo las vivas: quien está en las dos cadenas puede cobrar la nueva |
| `RN-CM-040` | Un FTD contado no se libera |
| `RN-CM-048` | Un lote que se queda sin comisiones vivas no se cierra ni se paga |
| `RN-MV-053` | Quién pregunta, cuándo, y qué hace con la respuesta |

---

## 6. Datos

### 6.1 Entrada

| Dato | Descripción |
|---|---|
| Línea | La línea de venta cuyo vendedor se corrige |
| Quién | La persona que corrige |

### 6.2 Salida

**Una respuesta, no un error**: la línea **liberada**, o **negada** porque alguna comisión está pagada o porque es un FTD contado. Qué le dice `MV` a quien corrige lo decide `MV`.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | `MV` tiene la venta bloqueada y ya comprobó todo lo suyo |
| Postcondición, liberada | Ninguna comisión viva de la línea; cada lote afectado rebajó su total; la línea no tiene desenlace; queda auditado |
| Postcondición, negada | Nada cambió |

---

## 8. Flujo principal

1. `MV` pregunta si la línea puede cambiar de vendedor.
2. Se toma la línea en exclusiva frente al devengo.
3. Si es un FTD ya contado, se niega.
4. Se toman sus comisiones vivas y los lotes en que están.
5. Si alguno está pagado, se niega.
6. Se marca cada comisión como revertida, por quién y cuándo, y se rebaja el total de su lote.
7. Se borra el desenlace de la línea.
8. Se audita y se responde que la línea está liberada.

Después, cuando `MV` confirma su transacción con el vendedor nuevo, **`RF-CM-013` devenga la línea** como a cualquier otra recién atribuida.

---

## 9. Flujos alternativos

### FA-001 — La línea no tenía comisiones

Porque ningún nivel tenía tasa, o porque se rechazó por pasar del 100 %. **Se libera**, se borra su desenlace, y la cadena nueva se intenta como una línea nueva.

### FA-002 — Un FTD todavía no contado

**Se libera sin revertir nada**: no devengó por venta. El siguiente cierre lo contará para el vendedor nuevo y su cadena.

### FA-003 — Una comisión de la línea estaba retirada

Está en el lote abierto de su persona, con su origen anotado. **Se revierte donde está**, y deja de poder devolverse.

### FA-004 — La reversión y un pago a la vez

Si el pago llega antes, la línea se niega; si la reversión llega antes, el pago abona el total rebajado.

### FA-005 — `MV` rechaza la corrección después de preguntar

Por ejemplo, porque otra línea de la misma petición se negó. **La reversión no queda**: la pregunta y la corrección son un solo acto.

---

## 10. Excepciones

**No hay excepciones de negocio**: las dos negativas son respuestas (`§6.2`). Un fallo inesperado deshace la corrección entera, que es lo que `MV` necesita para no escribir un vendedor con la cadena vieja cobrando.

---

## 11. Validaciones

**Ninguna de entrada**: la línea la nombra `MV`, que ya la validó.

---

## 12. Criterios de aceptación

**Se prueban por la ruta de `RF-MV-016`**, que es la única que llega aquí.

| ID | Criterio |
|---|---|
| `CA-CM-290` | Corregir el vendedor de una línea con comisiones **en lotes abiertos o pendientes** revierte **todas**: cada una queda en su lote, marcada con quién y cuándo, y **fuera** de su total; la línea pierde su desenlace |
| `CA-CM-291` | Tras la corrección, la **cadena nueva** tiene sus comisiones en el lote **abierto** de cada persona, con la tasa y la cadena **del día de la venta**, y la línea queda devengada otra vez |
| `CA-CM-292` | Una persona que está **en las dos cadenas** acaba con una comisión revertida y una viva de la misma línea |
| `CA-CM-293` | Si **alguna** comisión de la línea está en un lote **pagado** —aunque sea de un superior—, la corrección responde conflicto y **nada cambia**: ni el vendedor ni ninguna comisión |
| `CA-CM-294` | Una línea **FTD ya contada** no se corrige; una FTD **aún no contada** sí, y el siguiente cierre la cuenta para el vendedor nuevo |
| `CA-CM-295` | Una línea **sin comisión** o **rechazada** se corrige, y la cadena nueva se intenta como una línea nueva |
| `CA-CM-296` | Una comisión de la línea que estaba **retirada** al abierto se revierte allí, y ya no puede devolverse |
| `CA-CM-297` | Una petición que corrige **dos líneas**, una liberable y otra con una comisión pagada, **no cambia nada**: tampoco la reversión de la primera |
| `CA-CM-298` | **Corregir y pagar** un lote de la cadena a la vez: o se niega la corrección con el lote pagado, o el pago abona el total **sin** la comisión revertida |
| `CA-CM-299` | Queda **auditado** en `CM`: la línea, cada comisión revertida con su lote e importe, y quién corrigió |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La cadena vieja y la nueva son la misma persona salvo el nivel `0` | Sus superiores quedan con una revertida y una viva cada uno, del mismo importe si su tasa no cambió |
| El nuevo vendedor se elige y el pago de un lote de la cadena vieja llega un segundo después | El pago abona el total ya rebajado: la reversión se confirmó antes |
| Un pendiente se queda sin comisiones vivas por la reversión | Sigue pendiente, y no se paga (`RN-CM-048`) |
| El evento del devengo se pierde tras la corrección | La línea no tiene desenlace, y **el barrido del siguiente cierre la recoge** (`RN-CM-034`) |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.26.0 §5.10, `RN-CM-047`; [`requirements/mv.md`](../../../requirements/mv.md) v0.58.0, `RN-MV-053`), por decisión del responsable del proyecto: el vendedor de una línea de una venta confirmada se corrige **mientras ninguna comisión de su cadena esté pagada**, y la cadena vieja se revierte sin borrarse. Criterios `CA-CM-290` a `CA-CM-299`. | Responsable del proyecto |
