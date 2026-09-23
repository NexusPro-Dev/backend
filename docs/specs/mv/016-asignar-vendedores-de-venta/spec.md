# SPEC — `RF-MV-016` Asignar los vendedores de una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-016` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 23-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que **toda venta diga si ya se sabe a quién se le atribuye**, y que la venta de un cliente con **varios vendedores** no se atribuya en silencio a uno de ellos: nace **pendiente de validar sus comisiones**, sin vendedor en sus líneas, y quien administra elige **entre los vendedores de ese cliente** a quién corresponde cada línea.

---

## 2. Contexto

**Hasta hoy una venta se atribuía sola, y siempre al mismo.** `RN-MV-003` tomaba el **vendedor principal** de quien compra —quien lo registró— y lo congelaba en cada línea. Desde que existen los vínculos por hotlink (`RN-SP-049`, 16-09-2026) un cliente puede tener **varios vendedores**, y la compra que hace desde la tienda o la que registra un funcionario **no dice cuál de ellos la trajo**. Seguir eligiendo el principal es decidir a quién se le paga **sin que nadie lo haya decidido**.

**La decisión del responsable del proyecto (23-09-2026)**: cada tipo de movimiento tiene **sus propios estados**, y la venta tiene dos —**Validar comisiones** y **Validado**—. Con un solo vendedor, la venta nace validada y la línea lo lleva; con más de uno, nace por validar y la línea queda **sin vendedor** hasta que se asigne desde administración. Cuando todas las líneas tienen vendedor, la venta pasa **sola** a validada.

### 2.1 Lo que este requerimiento decide, y lo que no

| Decide | No decide |
|---|---|
| Que los estados son **por tipo** y un **eje aparte** del pago y de la entrega (`RN-MV-033`) | Qué hace `CM` con una venta validada: la comisión es la etapa 5 y **no existe** |
| El estado inicial de cada venta según **cuántos vendedores** tiene quien compra (`RN-MV-034`) | Que un cliente pueda **dejar** de tener un vendedor: los vínculos no se cierran (`RN-SP-049`) |
| Quién puede asignar, **entre quiénes** y **hasta cuándo** se puede corregir (`RN-MV-035`) | Qué ve cada persona de las ventas de otros: sigue siendo `RN-MV-031` |
| Que los listados de administración publiquen y filtren el estado | Que el comprador lo vea: a quien compra no le concierne a quién se paga la comisión |

---

## 3. Actores

| Actor | Qué hace |
|---|---|
| Quien administra las ventas | Asigna o corrige el vendedor de las líneas. Necesita `movements:assign-sellers` |
| Quien compra, o quien registra la venta por él | No elige nada: el estado inicial lo decide el sistema al registrar |

---

## 4. Alcance

### 4.1 Incluye

- Un catálogo de **estados por tipo de movimiento**, con los dos de la venta, y que toda venta lleve uno.
- El **estado inicial** de toda venta, por cualquier entrada: registro por un funcionario, compra propia de un paquete, venta del alta por enlace y —cuando se construyan— las compras por hotlink.
- **Asignar** el vendedor de una o varias líneas de una venta, y **corregirlo** mientras la venta no esté confirmada.
- El paso **automático** a validada cuando no queda ninguna línea sin vendedor.
- Publicar el estado en la respuesta del registro, del detalle y de los dos listados de administración, y **filtrar** esos listados por él.

### 4.2 No incluye

- Quitar el vendedor de una línea, o devolver una venta validada a «por validar».
- Asignar vendedores a movimientos que no sean ventas: hoy no existe ningún otro tipo.
- La comisión.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-001` | Enmendada: el vendedor de la línea y el estado del tipo son la **segunda excepción acotada** a «no se edita», junto a la entrega |
| `RN-MV-003` | Enmendada: la línea puede nacer **sin vendedor**, y el vendedor se congela **al confirmar** y no al registrar |
| `RN-MV-004` | Una venta por validar **se confirma igual** y entrega lo comprado; lo que espera es la comisión |
| `RN-MV-005` | El estado del pago no cambia por asignar, y decide lo que se puede asignar |
| `RN-MV-025` | La compra por hotlink se atribuye al dueño del enlace, y por eso nace validada |
| **`RN-MV-033`** | **Nueva.** Cada tipo tiene sus estados; la venta, `VALIDAR_COMISIONES` y `VALIDADO` |
| **`RN-MV-034`** | **Nueva.** El estado inicial lo deciden los vendedores de quien compra |
| **`RN-MV-035`** | **Nueva.** Se asigna entre los del cliente; lo asignado se corrige hasta confirmar; la venta se valida sola |
| `RN-SP-049` | Los vendedores de un cliente son sus vínculos, y no se cierran |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| La venta | Sí | Cuál, por su identificador |
| Las asignaciones | **Sí**, al menos una | Parejas **línea → vendedor**. La línea se nombra por **el producto**, que no se repite dentro de una venta (`RN-MV-011`) |

### 6.2 Salida

**La venta, tal como queda** —la misma forma que confirmar, anular y el detalle—, con su **estado del tipo** y el vendedor de cada línea.

**El estado del tipo viaja también** en la respuesta de registrar una venta, de comprar un paquete, y en cada fila de los dos listados de administración.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:assign-sellers`; la venta existe, no está rechazada ni anulada; cada línea nombrada es de la venta; cada vendedor es uno de los de quien compra |
| Postcondición | Cada línea nombrada tiene el vendedor elegido; si ninguna queda sin vendedor, la venta está validada; nada más cambió; el cambio está auditado |

---

## 8. Flujo principal

1. El actor indica la venta y, para una o varias de sus líneas, el vendedor que le corresponde.
2. El sistema comprueba que la petición está bien formada: al menos una asignación, ninguna incompleta, **ninguna línea repetida**.
3. Toma la venta **en exclusiva**, para que nada la confirme ni la reasigne mientras decide.
4. Comprueba que la venta no está rechazada ni anulada.
5. Comprueba, para cada asignación, que la línea es de la venta, que puede cambiarse —sin vendedor, o con vendedor en una venta no confirmada— y que el vendedor es **uno de los de quien compra**.
6. Escribe los vendedores. Si ninguna línea queda sin vendedor, pasa la venta a **validada**.
7. Audita el cambio: qué tenía cada línea, qué tiene ahora, y el estado antes y después.
8. Devuelve la venta como queda.

**Si cualquier comprobación falla, no cambia nada**: ninguna asignación de la petición se escribe.

---

## 9. Flujos alternativos

### FA-001 — Se asigna solo una parte

Las líneas nombradas quedan con su vendedor; las demás siguen sin él, y la venta **sigue por validar**. Otra petición completará el resto.

### FA-002 — La venta ya está confirmada

Se asignan **las líneas que no tienen vendedor**, y la venta pasa a validada si no queda ninguna. **Una línea que ya tiene vendedor no se corrige**: confirmada, lo atribuido queda congelado.

### FA-003 — La venta ya está validada y no confirmada

Se puede **corregir** el vendedor de cualquier línea. La venta sigue validada: ninguna línea quedó sin vendedor.

### FA-004 — Asignar y confirmar llegan a la vez

Una espera a la otra. Si confirmar llegó antes, la asignación ve la venta confirmada y aplica FA-002; si llegó después, confirma una venta cuyas líneas ya tienen el vendedor asignado.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La venta no existe | No encontrado |
| `EX-002` | La venta está **rechazada o anulada** | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-003` | Una línea que **ya tiene vendedor**, en una venta **confirmada** | Conflicto, nombrando la línea. Nada cambia |
| `EX-004` | Una línea nombrada **no es de la venta** | Rechazo, nombrando cuál. Nada cambia |
| `EX-005` | Un vendedor **no es de los de quien compra** | Rechazo, nombrando la línea. Nada cambia |
| `EX-006` | Quien pregunta no tiene `movements:assign-sellers` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El identificador de la venta es válido |
| `VAL-002` | Hay al menos una asignación |
| `VAL-003` | Cada asignación nombra un producto y un vendedor |
| `VAL-004` | Ningún producto se nombra dos veces |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-143` | Una venta registrada a nombre de un cliente con **un solo vendedor** nace **validada**, con ese vendedor en cada línea |
| `CA-MV-144` | Una venta registrada a nombre de un cliente con **varios vendedores** nace **por validar**, con **todas** sus líneas sin vendedor, y la respuesta lo dice |
| `CA-MV-145` | La **compra propia de un paquete** sigue la misma regla que el registro: varios vendedores, por validar y líneas sin vendedor |
| `CA-MV-146` | Quien compra **sin ser cliente de nadie** conserva la atribución de siempre —su superior, o él mismo— y la venta nace validada |
| `CA-MV-147` | La venta del **alta por enlace** nace validada, atribuida a quien registró al cliente |
| `CA-MV-148` | Las ventas **anteriores a este requerimiento** quedan validadas |
| `CA-MV-149` | Asignar **todas** las líneas deja la venta **validada** en la misma respuesta, con cada vendedor en su línea |
| `CA-MV-150` | Asignar **una parte** deja la venta por validar: las asignadas con su vendedor, las demás sin él |
| `CA-MV-151` | Un vendedor que **no es de los del cliente** responde rechazo, y **ninguna** asignación de la petición se escribe |
| `CA-MV-152` | Una línea que **no es de la venta** responde rechazo, y nada cambia |
| `CA-MV-153` | En una venta **confirmada** se asignan las líneas sin vendedor —y pasa a validada—, pero **corregir** una que ya lo tiene responde conflicto |
| `CA-MV-154` | En una venta **validada y pendiente** se puede corregir el vendedor de una línea, y sigue validada |
| `CA-MV-155` | Una venta **rechazada o anulada** responde conflicto diciendo su estado |
| `CA-MV-156` | Una venta que **no existe** responde no encontrado |
| `CA-MV-157` | Sin asignaciones, con una incompleta o con un producto repetido responde rechazo, **y la venta no cambia** |
| `CA-MV-158` | Sin `movements:assign-sellers` responde prohibido —**también con `movements:confirm` y `movements:create`**—; sin autenticar, `401` |
| `CA-MV-159` | El cambio queda **auditado** con el vendedor de cada línea antes y después, y el estado del tipo antes y después |
| `CA-MV-160` | **Confirmar** una venta por validar se admite y entrega lo comprado, y la venta **sigue por validar** |
| `CA-MV-161` | Los dos listados de administración publican el estado del tipo de cada fila y **filtran** por él; un estado que no existe es un error, no una página vacía |
| `CA-MV-162` | «Mis compras» **no** publica el estado del tipo |

**`CA-MV-151` es el que sostiene el requerimiento**: si se pudiera elegir a cualquiera, validar sería atribuir la venta a quien uno quisiera, que es justo lo que se quería dejar de hacer en silencio.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El cliente gana un vendedor **después** de la venta | La venta no cambia de estado. Al asignar, el nuevo ya es elegible: los vendedores se leen **al asignar** |
| Se asigna a una línea **el mismo** vendedor que ya tenía | Se admite y no cambia nada, salvo en una confirmada, donde también se admite porque no corrige nada |
| Una venta **por validar** vista por un vendedor en «las ventas de mi alcance» | No aparece: ninguna línea es suya todavía. Aparece en cuanto se le asigna una |
| Un cliente cuyo único vendedor es **de hotlink** | Tiene un vendedor, y la venta nace validada con él |
| La compra por hotlink de un cliente que **ya tenía otro vendedor** | Nace validada con el dueño del enlace, aunque el cliente quede con dos |

---

## 14. Preguntas abiertas

**Cuándo se devenga la comisión de una venta que se valida después de confirmarse.** No hay comisiones todavía (etapa 5); lo único que queda escrito es que no se devengará sobre una venta que no esté validada.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 23-09-2026 | Primera versión, por decisión del responsable del proyecto: «agregar estados por tipo de movimiento; para las ventas tendrán dos estados, Validar comisiones y Validado. Si tengo más de un vendedor en `client_seller`, la compra se guarda con estado Validar comisiones y en la línea el vendedor estaría null; si tengo un solo vendedor, se guarda Validado y en la línea se le asigna el vendedor». Sus respuestas del mismo día fijan el resto: **columna aparte** y no sustituir el estado del pago; la validación la hace **el front al asignar**, y la venta pasa sola a validada cuando no falta ninguna línea; **confirmar no espera**, la comisión sí; **todo cliente tiene un vendedor**; el **hotlink** nace validado con el dueño del enlace; se elige **solo entre los del cliente**; y lo asignado **se corrige mientras la venta no esté confirmada**. | Responsable del proyecto |
