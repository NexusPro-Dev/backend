# SPEC — `RF-MV-055` Consultar mis movimientos de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-055` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que cada persona vea **de dónde le salen sus puntos** en un solo sitio: **sus compras de puntos y los ajustes que administración le hizo**, mezclados y en orden; el **detalle** de cualquiera de ellos; y **el comprobante** de un ajuste, si lo tiene.

---

## 2. Contexto

Hasta hoy la persona tenía «mis compras de puntos» (`RF-MV-031`), que no enseña los ajustes, y el historial de saldos (`RF-MV-022`), que enseña asientos y no movimientos: un ajuste se veía como una línea de la cuenta, sin motivo ni comprobante. El responsable del proyecto pidió el 06-10-2026 agrupar las dos cosas ([`requirements/mv.md`](../../../requirements/mv.md) v0.88.0 §4.12). **Este requerimiento sustituye al listado de `RF-MV-031`**, que se retira.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Lo gastado también** (0.2.0) | Una venta pagada con puntos es una fila más, de tipo `GASTO_PUNTOS`: **los puntos en negativo, los que se descontaron** —leídos de lo que se descontó, no recalculados—, el importe de la venta y, como motivo, los productos comprados. No hay devolución: una venta confirmada no se anula |
| **Una fila común** | Compra y ajuste comparten fila; el tipo dice cuál es, y lo que no aplica a uno va vacío —una compra no tiene motivo; un ajuste no tiene importe— |
| **Los puntos con su signo** | La compra suma siempre; el ajuste suma o resta. Una compra pendiente o rechazada enseña los puntos que daría o que habría dado |
| **Quién hizo el ajuste no se enseña** | La persona ve qué le hicieron, por qué y con qué soporte, no qué administrador lo registró (`requirements/mv.md` §4.12) |
| **Un detalle ajeno no existe** | Pedir el de otro responde lo mismo que pedir uno que no existe: no se confirma que exista |
| **Tres permisos** | Listar, ver el detalle y descargar el comprobante, uno por consulta (`RN-SEG-015`) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:list-own-points-movements` | Consulta su lista |
| Quien tiene `movements:read-own-points-movement` | Consulta el detalle de uno suyo |
| Quien tiene `movements:download-own-points-receipt` | Descarga el comprobante de un ajuste suyo |

---

## 4. Alcance

### 4.1 Incluye

- La lista paginada de las compras de puntos y los ajustes de quien consulta, los más recientes primero.
- Filtros por tipo, estado, moneda, periodo y sentido, y una búsqueda libre.
- El detalle de uno: lo de la fila, y además la tasa, los pagos y el motivo del rechazo de una compra, o los datos del comprobante de un ajuste.
- Descargar el comprobante de un ajuste propio.

### 4.2 No incluye

- Comprar puntos (`RF-MV-027`) ni pagar con ellos (`RF-MV-030`).
- Los movimientos de puntos de otras personas (`RF-MV-056`).
- Los pagos hechos **con** puntos: son ventas, y se ven en «mis compras» (`RF-MV-008`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-051` | Qué es una compra de puntos y qué guarda |
| `RN-MV-076` | Qué es un ajuste y qué guarda |
| `RN-MV-077` | Quién ve el comprobante, y cómo se descarga |
| `RN-SEG-015` | Un permiso por consulta, también sobre lo propio |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página y tamaño | No | Como en los demás listados |
| Orden | No | Fecha (por omisión, descendente), puntos o comprobante, ascendente o descendente |
| Tipo | No | `COMPRA_PUNTOS`, `AJUSTE_PUNTOS` o `GASTO_PUNTOS` |
| Estado | No | `PENDIENTE`, `CONFIRMADA` o `RECHAZADA` |
| Moneda | No | Los de una moneda |
| Desde, hasta | No | Sobre cuándo ocurrió; desde inclusive, hasta exclusive |
| Sentido | No | `SUMA` o `RESTA` |
| Búsqueda | No | Fragmento del comprobante, el motivo o la referencia; sin distinguir acentos ni mayúsculas |
| Movimiento | En el detalle y la descarga | Cuál |

### 6.2 Salida

**La fila**: identificador, comprobante, tipo, estado, la persona, la moneda, **los puntos con su signo**, el importe pagado (solo compra), el motivo y la referencia (solo ajuste), cuándo ocurrió, se confirmó y se rechazó, **si tiene comprobante**, y quién hizo el ajuste —**siempre vacío en este alcance**—.

**El detalle**: la fila, más la tasa con que se compró, los pagos y el motivo del rechazo (compra); del comprobante su nombre, tipo, tamaño, resumen y cuándo se subió (ajuste); y **las líneas de la venta** —producto, cantidad e importe— y sus pagos (gasto).

**La descarga**: el archivo, con el tipo con que se guardó, como adjunto.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso de la consulta |
| Postcondición | Nada cambia |

---

## 8. Flujo principal

1. El actor pide su lista, con los filtros y el orden que quiera.
2. El sistema valida todo junto y devuelve la página de **sus** movimientos de puntos.
3. El actor pide el detalle de uno de ellos y, si es un ajuste con comprobante, lo descarga.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Página, tamaño, orden, tipo, estado, sentido o periodo inválidos | Rechazo, con todos los problemas juntos |
| `EX-002` | El movimiento no existe, no es de puntos o es de otra persona | No encontrado |
| `EX-003` | El ajuste no tiene comprobante, o el movimiento es una compra | No encontrado |
| `EX-004` | Sin el permiso de la consulta | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Página y tamaño dentro de los límites de los listados |
| `VAL-002` | Orden por un campo admitido y en un sentido válido |
| `VAL-003` | Sentido `SUMA` o `RESTA` |
| `VAL-004` | Desde no posterior a hasta |
| `VAL-005` | Tipo y estado entre los admitidos |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-662` | La lista trae **las compras y los ajustes de quien consulta**, mezclados, **los más recientes primero**, y nada de otras personas |
| `CA-MV-663` | Una compra sale con su importe, sus puntos positivos y su estado; un ajuste, con sus puntos con signo, su motivo y su referencia, **sin quién lo hizo** |
| `CA-MV-664` | Tipo, estado, moneda y periodo filtran, y se combinan |
| `CA-MV-665` | `SUMA` devuelve compras y ajustes positivos; `RESTA`, solo ajustes negativos |
| `CA-MV-666` | La búsqueda encuentra por comprobante, motivo y referencia, sin distinguir acentos ni mayúsculas |
| `CA-MV-667` | Se ordena por fecha, puntos o comprobante, en los dos sentidos |
| `CA-MV-668` | Un tipo, estado, orden, sentido o periodo inválidos, o una página fuera de límites, responden `400` con todos los problemas juntos |
| `CA-MV-669` | La fila dice si el ajuste tiene comprobante |
| `CA-MV-670` | El detalle de una compra trae la tasa, los pagos y, si se rechazó, el motivo; el de un ajuste, los datos del comprobante |
| `CA-MV-671` | El detalle de un movimiento de otra persona, de uno que no es de puntos o de uno inexistente responde `404`, igual en los tres casos |
| `CA-MV-672` | La descarga devuelve **exactamente el archivo subido**, con su tipo, como adjunto; sin comprobante, de otra persona o sobre una compra, `404` |
| `CA-MV-696` | **Una venta pagada con puntos sale como `GASTO_PUNTOS`**, con los puntos descontados en negativo, el importe de la venta y sus productos como motivo; una venta pagada con otro método no sale |
| `CA-MV-697` | `type=GASTO_PUNTOS` trae solo los gastos, y `RESTA` trae gastos y ajustes negativos |
| `CA-MV-698` | El detalle de un gasto trae las líneas de la venta y su pago; descargar un comprobante sobre un gasto, `404` |
| `CA-MV-673` | Sin el permiso de cada consulta responde prohibido; sin autenticar, `401`. **La ruta de `RF-MV-031` ya no existe** |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Quien no tiene movimientos de puntos | Página vacía |
| Una compra pendiente | Sale con los puntos que dará, sin fecha de confirmación |
| Un ajuste de antes del comprobante | Sale sin comprobante |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión ([`requirements/mv.md`](../../../requirements/mv.md) v0.88.0 §4.12, `RN-MV-077`), con las decisiones del responsable del proyecto: compras y ajustes en una sola lista, con detalle y comprobante; sustituye el listado de `RF-MV-031`. Criterios `CA-MV-662` a `CA-MV-673`. | Responsable del proyecto |
| 0.2.0 | 06-10-2026 | **Lo gastado también** ([`requirements/mv.md`](../../../requirements/mv.md) v0.89.0 §4.12), a petición del responsable del proyecto: un tercer tipo, `GASTO_PUNTOS`. Criterios `CA-MV-696` a `CA-MV-698`. | Responsable del proyecto |
