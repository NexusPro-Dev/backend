# SPEC — `RF-CM-026` Consultar todas mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-026` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 07-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que cada persona que cobra comisiones vea **todas sus comisiones en una sola lista**, sin abrir sus lotes uno a uno: de qué venta y de qué cliente sale cada una, cuánto vale, y **en qué lote está y en qué estado** —abierto, pendiente de pago o pagado—.

---

## 2. Contexto

**Lo pidió el responsable del proyecto el 07-10-2026**: «existe un endpoint para consultar mis comisiones, no por lotes, sino ver todos los detalles». Hasta hoy la única forma era [`RF-CM-012`](../012-consultar-mis-comisiones/spec.md): listar los lotes propios y abrir cada uno. Para responder «¿cuánto me ha dejado este cliente?» o «¿qué me falta por cobrar de este producto?» había que recorrerlos todos.

**No hay datos nuevos.** Cada fila es la comisión que el detalle de `RF-CM-012` ya muestra, con lo que el lote dice de ella —su código y su estado—, su moneda y el cliente de la venta. Lo que cambia es **el corte**: por comisión y no por lote.

**Solo lo propio, y sin D-22**, por lo mismo que `RF-CM-012` (§2 de aquella spec): cada quien ve lo suyo, filtrado por su propia identidad.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Una fila por comisión** | No por línea de venta: si la persona cobra dos niveles de la misma línea —no puede, `RN-CM-027`— serían dos filas. Lo que se lista es lo que se cobra |
| **Cada fila dice su lote y su estado** | El estado **es** el de la comisión: está por cobrar mientras su lote esté abierto o pendiente, y cobrada cuando se pagó |
| **Las dos clases** | Las `POR_VENTA` y las `POR_AFFTRACK` (`RN-CM-044`) salen juntas, cada una con su clase; una `POR_AFFTRACK` **no tiene venta, cliente ni nivel** |
| **Lo retirado, donde está** | Una comisión retirada a su abierto (`RN-CM-046`) sale **una vez**, en el lote en que está, diciendo de cuál salió |
| **Lo borrado no sale** | La comisión de una línea cuyo vendedor se corrigió **ya no existe** (`RN-CM-047`, 07-10-2026) y no se lista |
| **El orden** | La más reciente primero, por el momento en que nació la comisión |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona que cobre comisiones | Lista todas las suyas (`commission-batches:list-own-commissions`) |

---

## 4. Alcance

### 4.1 Incluye

- Todas mis comisiones, de todos mis lotes, monedas y estados.
- Filtros por estado del lote, moneda, producto, clase y un rango de fechas.

### 4.2 No incluye

- Las comisiones de mi red: depende de **D-22**.
- Totales por moneda o por estado: los da cada lote (`RF-CM-012`), y sumar importes de monedas distintas no tiene sentido.
- El detalle de una comisión suelta: la fila ya lo trae entero.
- El remanente de FTD: `RF-CM-021`, sin lectura propia todavía (`requirements/cm.md` §6).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-008` | Lo copiado al devengar, no lo que dice hoy la tasa |
| `RN-CM-033` | El abierto crece con cada venta: la comisión de hoy ya sale |
| `RN-CM-044` | Las dos clases, cada una con la suya |
| `RN-CM-046` | Lo retirado dice de qué lote salió |
| `RN-CM-047` | Lo borrado al corregir un vendedor no sale |
| `RN-SEG-015` | Ver lo propio también exige permiso |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Qué hace |
|---|---|---|
| Página y tamaño | No | Los de siempre |
| Estado del lote | No | `ABIERTO`, `PENDIENTE` o `PAGADO`: solo las comisiones que están en lotes de ese estado |
| Moneda | No | Solo las de lotes en esa moneda |
| Producto | No | Solo las de ese producto —el de la línea, o el FTD de una afftrack— |
| Clase | No | `POR_VENTA` o `POR_AFFTRACK` |
| Desde, hasta | No | Sobre el momento en que nació la comisión, los dos extremos incluidos |

**No hay filtro de persona**: la persona es quien pregunta.

### 6.2 Salida

Una página de comisiones. Cada una con **lo que el detalle de un lote ya muestra** —venta, línea, producto, nivel, fuente, tasa copiada, base, importe, fecha de resolución, momento del devengo, clase, liquidación afftrack y lote del que se retiró—, y además:

- **el lote en que está**: su identificador, su código y su estado;
- **la moneda** del lote;
- **el cliente de la venta**, en una `POR_VENTA`.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso de la operación |
| Postcondición | Ninguna: no escribe |

---

## 8. Flujo principal

1. La persona pide sus comisiones, con o sin filtros.
2. Se devuelven **las suyas**, la más reciente primero, cada una con su lote, su estado, su moneda y su cliente.

---

## 9. Flujos alternativos

**Ninguno**: quien no ha ganado nada recibe una lista vacía.

---

## 10. Excepciones

**Ninguna** propia: no se pide nada por identificador.

---

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-001` | Estado | `ABIERTO`, `PENDIENTE` o `PAGADO` |
| `VAL-001` | Clase | `POR_VENTA` o `POR_AFFTRACK` |
| `VAL-002` | Desde | No posterior a hasta |

**Todos los errores salen juntos**, como en `RF-CM-010`.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-347` | El listado devuelve **solo** las comisiones del actor, de todos sus lotes, monedas y estados, la más reciente primero |
| `CA-CM-348` | Cada comisión trae **su lote** —identificador, código y estado—, **su moneda** y, si es `POR_VENTA`, **el cliente de la venta**, con la forma de la comisión del detalle de `RF-CM-012` |
| `CA-CM-349` | Un superior ve la comisión que cobra por la venta de su subordinado, **con su nivel y el cliente de esa venta**; el subordinado ve la suya y **no** la del superior |
| `CA-CM-350` | Con estado, solo salen las de lotes en ese estado: tras pagarse un lote, sus comisiones salen como `PAGADO` y ya no como `PENDIENTE` |
| `CA-CM-351` | Los filtros de moneda, producto y clase se combinan; una `POR_AFFTRACK` sale sin venta, cliente ni nivel |
| `CA-CM-352` | Desde y hasta acotan por el momento del devengo, los dos incluidos; un estado o una clase que no existen, y desde posterior a hasta, responden error de validación **con todos los errores juntos** |
| `CA-CM-353` | Una comisión retirada a su abierto sale **una sola vez**, en el lote abierto, diciendo de qué pendiente salió |
| `CA-CM-354` | Tras corregirse el vendedor de una línea, la comisión vieja **deja de salir** en la lista de quien la cobraba y la nueva sale en la de quien la cobra ahora |
| `CA-CM-355` | Sin el permiso de la operación, se rechaza; **todo rol que ve sus lotes** (`commission-batches:list-own`) lo porta desde su siembra |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un funcionario que no cobra comisiones pide las suyas | Lista vacía, si porta el permiso |
| Una comisión de importe cero | Sale: es una comisión |
| Un filtro de producto que no existe | Lista vacía, no error: es un filtro, no una búsqueda por identificador |
| La misma persona con lotes en dos monedas | Salen las de las dos, cada una con la suya |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 07-10-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.36.0), a petición del responsable del proyecto: todas mis comisiones en una lista, cada una con su lote, su estado, su moneda y su cliente. Criterios `CA-CM-347` a `CA-CM-355`. | Responsable del proyecto |
