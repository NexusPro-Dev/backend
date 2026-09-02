# SPEC — `RF-CM-003` Corregir el porcentaje de una tasa

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-003` |
| Módulo | `CM` — Comisiones |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

!!! danger "Esta operación BORRA EL PASADO, y es lo primero que hay que saber de ella"

    Las tasas de rol **no tienen vigencia** desde el rediseño del 01-09-2026. No hay dos filas contando cada una su parte de la historia: hay **una que ahora dice otra cosa**.

    Pasar un `AGENTE` de 10 a 12 **borra el 10**. Y lo borra **de todo el sistema**, no solo de la vista: nada más lo guardaba.

    Lo único que preservaría lo ya pagado es que la liquidación haya copiado el porcentaje que aplicó (`RN-CM-008`) — y **esa liquidación no existe todavía**. La v0.1.0 de este documento distinguía «corregir» de «cambiar a partir de una fecha»; **esa distinción desapareció con la vigencia** y solo queda reescribir.

---

## 1. Objetivo

Corregir el **porcentaje** de una tasa de comisión mal declarada.

## 2. Contexto

Un porcentaje se teclea mal, o se aprueba uno y se registra otro. Corregirlo no debería exigir retirar la tasa y declararla de nuevo — sobre todo porque retirarla obliga antes a desasociarla de cada producto sobre el que rige (`RN-CM-015`), y volver a asociarla después.

**Lo que esta operación NO puede hacer, y la v0.1.0 sí podía**, es cambiar lo que se paga **a partir de una fecha**. Con vigencia, eso eran dos operaciones —cerrar la vigente y registrar otra— y las dos existían. Sin vigencia **no hay «desde cuándo»**: solo hay un número, y corregirlo lo cambia hacia atrás y hacia delante a la vez.

Quien necesite conservar qué se pagó antes tiene **una sola vía, y está fuera de este módulo**: que la liquidación copie el porcentaje en el momento de aplicarlo.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Corrige el porcentaje de una tasa ya registrada |

## 4. Alcance

### 4.1 Incluye

- Corregir el **porcentaje** de una tasa viva.
- **Rechazar** el intento de cambiar el rol, en lugar de ignorarlo.
- Rechazar una petición que no informa nada corregible.
- Dejar en la auditoría de cambios **el valor anterior y el nuevo**.

### 4.2 No incluye

- **Cambiar el rol de la tasa.** No corrige la tasa: la convierte en otra, y arrastraría consigo todas sus asociaciones a un rol que nadie eligió.
- **Cambiar lo que se paga a partir de una fecha.** No existe la operación. Ver §13.
- **Cambiar sobre qué productos rige la tasa.** Eso es asociar y desasociar (`RF-CM-007`, `RF-CM-008`).
- **Corregir una tasa personalizada.** Es la misma operación sobre otra tabla y otro recurso, y **allí sí conserva vigencia**: ver `RF-CM-006` §4.
- **Retirar la tasa**, que es `RF-CM-004`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-007` | El porcentaje va de cero a cien | `requirements/cm.md` §5.1 |
| `RN-CM-008` | La liquidación conserva el porcentaje, y es la única defensa del pasado | `requirements/cm.md` §5.1 |

**`RN-CM-008` no la cumple este requerimiento: la necesita.** Es la única regla del módulo dirigida a un módulo que no existe, y esta operación es exactamente la que la hace imprescindible.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador de la tasa | Sí | Cuál se corrige | Debe existir y **no estar retirada** |
| Porcentaje | Sí | El valor correcto | De **cero a cien** (`RN-CM-007`). **Vaciarlo se rechaza**: una tasa sin porcentaje no significa nada |

**Se distingue el campo ausente del campo enviado en vacío**, y no es un tecnicismo: son dos peticiones distintas y la segunda pide algo imposible. Enviarlo vacío se rechaza con su propio mensaje en lugar de tratarse como «no se envió».

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tasa | La tasa corregida, con el rol resuelto y el número de productos sobre los que rige |

**La respuesta incluye sobre cuántos productos rige**, y aquí ese número tiene una lectura propia: dice **a cuántas ventas futuras afecta la corrección**. Si es cero, la corrección no cambia nada para nadie.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de corrección de tasas.
- La tasa existe y está viva.

**Postcondiciones**

- La tasa declara el porcentaje corregido.
- **El porcentaje anterior ya no existe en la tabla**, y el registro de auditoría del cambio es el único sitio donde queda escrito.
- Si la petición no cambió nada, **la marca de última modificación no se mueve**: una petición que no cambia nada no es un cambio, y moverla haría creer que alguien tocó la tasa.

## 8. Flujo principal

1. El actor envía el identificador de la tasa y el porcentaje corregido.
2. El sistema comprueba que la petición **no trae el rol**, y la rechaza si lo trae.
3. El sistema comprueba que la petición informa al menos un campo corregible.
4. El sistema comprueba que la tasa existe y está viva.
5. El sistema comprueba que el porcentaje está entre cero y cien.
6. Si el porcentaje declarado difiere del que tiene, el sistema lo cambia y emite el evento de auditoría con el **antes y el después**.
7. El sistema devuelve la tasa, con el rol resuelto y el número de productos asociados.

**Los pasos 2 y 3 van antes de buscar la tasa**, y es deliberado: no cuesta una consulta enterarse de que la petición pedía algo que no se puede hacer.

## 9. Flujos alternativos

### FA-001 — La corrección no cambia nada

**Cuándo ocurre:** el porcentaje enviado es el que la tasa ya tenía.

1. El sistema **no emite ningún evento de auditoría** y **no mueve** la marca de modificación.
2. Devuelve la tasa tal como está, con éxito. **No es un error**: pedir lo que ya es cierto no falla.

### FA-002 — El mismo porcentaje con otra escala

**Cuándo ocurre:** se envía `10.0000` sobre una tasa que declara `10.00`.

1. El sistema los trata como **el mismo porcentaje** y se comporta como `FA-001`.
2. Compararlos como texto llenaría el registro de auditoría de cambios que no cambian nada.

### FA-003 — La tasa no rige sobre ningún producto

**Cuándo ocurre:** la tasa está en el catálogo y nadie la asoció.

1. La corrección se aplica con normalidad.
2. **No afecta a nada**, y la respuesta lo dice devolviendo cero productos asociados.

## 10. Excepciones

### EX-001 — La tasa no existe o está retirada

**Condición:** el identificador no corresponde a ninguna tasa viva.
**Respuesta del sistema:** rechaza la corrección diciendo que la tasa indicada no existe. **Una tasa retirada se trata como inexistente**: lo que se retiró debe quedar como estaba, para que lo que la referencie siga diciendo la verdad.

### EX-002 — Se intenta cambiar el rol

**Condición:** la petición trae el rol de la tasa.
**Respuesta del sistema:** rechaza la corrección diciendo que el rol no se puede corregir. **Se rechaza y no se ignora**: ignorarlo haría creer que el cambio se aplicó, y quien lo pidió seguiría creyendo que la tasa paga a otro rol.

### EX-003 — La petición no informa nada

**Condición:** no se envía ningún campo corregible.
**Respuesta del sistema:** rechaza la corrección diciendo que debe enviarse al menos un campo corregible.

### EX-004 — Se intenta vaciar el porcentaje

**Condición:** el porcentaje se envía explícitamente vacío.
**Respuesta del sistema:** rechaza la corrección diciendo que el porcentaje no puede vaciarse.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-002` | El porcentaje no se vacía | El porcentaje no puede vaciarse. |
| `VAL-003` | Rango del porcentaje | El porcentaje debe estar entre cero y cien. |
| `VAL-009` | Campo no corregible | El rol de una tasa de comisión no se puede corregir. |
| `VAL-010` | Petición vacía | Debe enviarse al menos un campo corregible. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-021` | El sistema corrige el porcentaje y devuelve la tasa con el rol resuelto y sus productos asociados |
| `CA-CM-022` | **El porcentaje anterior desaparece de la tasa**, y solo el registro de auditoría lo conserva, con el antes y el después |
| `CA-CM-023` | Una corrección que no cambia nada **no mueve** la marca de modificación |
| `CA-CM-024` | `10.0000` sobre una tasa de `10.00` no se registra como cambio |
| `CA-CM-025` | El sistema **rechaza** el intento de cambiar el rol, y la tasa conserva el suyo |
| `CA-CM-026` | El sistema rechaza vaciar el porcentaje |
| `CA-CM-027` | El sistema rechaza una petición que no informa nada corregible |
| `CA-CM-028` | El sistema trata una tasa retirada como inexistente |

## 13. Casos límite

- **Corregir una tasa que rige sobre veinte productos:** los veinte pasan a pagar el porcentaje nuevo, **inmediatamente y sin aviso**. Es el comportamiento previsto —una tasa se reutiliza precisamente para eso— y la respuesta lo hace visible devolviendo cuántos son.
- **Querer cambiar el porcentaje a partir del mes que viene:** **no se puede.** La alternativa es registrar una tasa nueva y reasociar cada producto el día que corresponda, y **tampoco conserva** desde cuándo rigió cada una. Es la pérdida aceptada en `cm.md` v0.4.0.
- **Corregir hacia abajo lo que ya se pagó:** el sistema lo admite y **no puede detectarlo**, porque no sabe qué se pagó. Es `RN-CM-008` mirándonos de frente.
- **Corregir una tasa del cero por ciento:** se corrige como cualquier otra. El cero no es un estado especial, es un porcentaje.
- **Dos correcciones simultáneas de la misma tasa:** ninguna regla lo impide y ninguna hace falta. La última escritura gana, las dos quedan en la auditoría, y no hay ninguna invariante entre filas que pueda romperse — al revés que en `RF-CM-006`.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**La que habría que hacerse ya está respondida y por eso no figura aquí:** si el módulo debería impedir corregir una tasa que rige sobre productos vendidos. No puede, porque **no existe ninguna venta**. El día que exista, esta especificación tendrá que revisarse.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **Reescrita sobre el modelo de `cm.md` v0.4.0**, y después de construirse el código. La corrección **pierde el fin de vigencia** y se queda con un solo campo, y con ello **desaparece la distinción entre corregir y cambiar**: sin vigencia no hay «desde cuándo», solo un número que se reescribe hacia atrás y hacia delante a la vez. El documento se reordena alrededor de esa consecuencia — el aviso de cabecera dice que esta operación **borra el pasado de todo el sistema**, y §5 declara que `RN-CM-008` no es una regla que este requerimiento cumpla sino **una que necesita**, dirigida a un módulo que no existe. Los inmutables pasan de cuatro a uno, y §6.2 estrena una lectura del número de productos asociados que el alta no tiene: dice **a cuántas ventas futuras afecta la corrección**. §13 recoge que corregir una tasa compartida cambia lo que pagan veinte productos **sin aviso**, y que corregir hacia abajo lo ya pagado **el sistema no puede detectarlo**. | Responsable técnico |
