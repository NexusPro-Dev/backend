# SPEC — `RF-CM-025` Pagar varios lotes de una vez

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-025` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que Finanzas, tras revisar los lotes de un cierre, **pague de una vez los que elija**, sin una petición por lote, y sepa **cuáles se pagaron y cuáles no**.

---

## 2. Contexto

**Hasta hoy los lotes se pagan de uno en uno** (`RF-CM-011`). El plan de ese requerimiento descartó pagar varios en una llamada porque «un fallo en uno obligaría a decidir si revierte los demás; no se ha pedido». **El 01-10-2026 se pidió**, y el responsable del proyecto tomó esa decisión y tres más (`requirements/cm.md` v0.28.0, `RN-CM-049`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió | Lo que se descartó |
|---|---|---|
| **Qué se paga** | **Los lotes que Finanzas elige**, por su identificador | *Todos los pendientes*: pagaría lotes que nadie revisó. *Los de un cierre*: Finanzas puede querer dejar alguno para después |
| **Si uno no se puede pagar** | **Los demás se pagan igual**, y la respuesta dice cuál no y por qué | *Todo o nada*: un lote vacío o pagado a la vez por otra persona detendría la nómina entera |
| **Cuántos** | **Sin tope** | *Cien por petición*: se propuso para que una petición no tardara minutos, y el responsable del proyecto lo retiró: partir la nómina en varias llamadas es peor que esperar. Cada lote sigue siendo una transacción corta |
| **Cómo se paga cada uno** | **Exactamente como `RF-CM-011`**: mismas condiciones, mismo abono, misma constancia | Una segunda forma de pagar, que algún día divergiría de la primera |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Finanzas o administración | Paga la selección (`commission-batches:pay-batches`) |

---

## 4. Alcance

### 4.1 Incluye

- Pagar, de una lista de lotes, cada uno que se pueda pagar, abonándolo en la billetera de su persona.
- Decir, por cada lote de la lista, si se pagó —con el importe abonado y el movimiento— o por qué no.

### 4.2 No incluye

- **Elegir los lotes**: la lista la arma quien llama, normalmente desde el listado de lotes pendientes (`RF-CM-010`).
- **Pagar parcialmente un lote**, o deshacer un pago.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-049` | Cada lote por su cuenta; lo que falla en uno no frena a los demás |
| `RN-CM-030` | Solo un lote pendiente se paga, y pagar es abonar |
| `RN-CM-048` | Un lote sin comisiones vivas no se paga |
| `RN-MV-044` | `MV` redondea y abona |

---

## 6. Datos

### 6.1 Entrada

| Dato | Descripción |
|---|---|
| Lotes | Sus identificadores: al menos uno, sin repetir |

### 6.2 Salida

**Una fila por lote pedido, en el orden en que se pidió**, con su identificador y:

- **pagado**: su código, el importe abonado y el movimiento del abono;
- **no pagado**: el motivo, con el mismo código y mensaje que daría pagarlo solo (`RF-CM-011`).

Y **cuántos se pagaron y cuántos no**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La lista es válida |
| Postcondición | Cada lote que podía pagarse está pagado y abonado; los demás, como estaban; cada pago, auditado como uno suelto |

---

## 8. Flujo principal

1. Finanzas pide pagar una lista de lotes.
2. Se comprueba la lista: no vacía y sin repetidos.
3. Para cada lote, en el orden pedido, **se intenta pagarlo como si se pagara solo**.
4. Si se paga, se anota pagado; si no, se anota el motivo y se sigue con el siguiente.
5. Se devuelve el resultado de cada uno.

---

## 9. Flujos alternativos

### FA-001 — Ninguno se puede pagar

La respuesta es la misma, con todos como no pagados y su motivo. **No es un error de la petición**: la lista era válida.

### FA-002 — Otra persona paga uno de la lista a la vez

Ese lote sale **no pagado, porque ya estaba pagado**; se abona **una sola vez**.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Un fallo inesperado en un lote | Ese lote, no pagado con un motivo genérico; los demás siguen |

**Los motivos de no pagar un lote no son excepciones de la petición**: son los de `RF-CM-011` —no existe, está abierto, ya está pagado, no tiene comisiones vivas, el abono falla—, y viajan en su fila.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La lista existe y no está vacía |
| ~~`VAL-002`~~ | ~~Como mucho cien~~ **Retirada el 01-10-2026**: no hay tope. El número queda consumido |
| `VAL-003` | Sin identificadores repetidos |

**Los errores de la lista salen juntos.**

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-306` | Pagar una lista de lotes **pendientes** los deja **todos pagados**, cada uno abonado en la billetera de su persona con su movimiento, y la respuesta trae cada uno con su importe abonado |
| `CA-CM-307` | En una lista con un lote **abierto**, uno **ya pagado**, uno **sin comisiones vivas** y uno que **no existe**, se pagan los demás y esos cuatro salen **no pagados** con el código y el mensaje de `RF-CM-011` |
| `CA-CM-308` | Si el **abono de uno falla**, ese lote sigue **pendiente**, sin movimiento, y los demás **se pagan** |
| `CA-CM-309` | La respuesta trae **una fila por lote, en el orden pedido**, y cuántos se pagaron y cuántos no |
| `CA-CM-310` | Una lista **vacía** o con **repetidos** se rechaza **con todos los errores juntos**, y no se paga nada; una de **más de cien** se acepta |
| `CA-CM-311` | **Dos pagos de listas que comparten un lote**, a la vez: el lote se abona **una sola vez**, y en una de las respuestas sale no pagado por ya estar pagado |
| `CA-CM-312` | Una lista en la que **ninguno** se puede pagar responde **éxito**, con todos no pagados y su motivo |
| `CA-CM-313` | Cada lote pagado queda **auditado como un pago suelto** |
| `CA-CM-314` | Sin `commission-batches:pay-batches`, se rechaza —**también con `commission-batches:pay`**— |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una lista de un solo lote | Igual que pagarlo solo, con la forma de esta respuesta |
| Dos lotes de la misma persona | Cada uno su abono y su movimiento |
| Un lote que redondea a cero con una comisión viva | Se paga, como en `RF-CM-011` |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.28.0, `RN-CM-049`), por petición del responsable del proyecto: una lista que elige Finanzas, cada lote por su cuenta, permiso propio y **sin tope** —el de cien que se propuso lo retiró el responsable el mismo día—. Criterios `CA-CM-306` a `CA-CM-314`. | Responsable del proyecto |
