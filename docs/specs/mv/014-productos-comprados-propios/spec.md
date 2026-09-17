# SPEC — `RF-MV-014` Consultar los productos comprados propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-014` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que cualquier persona autenticada vea **los productos que compró**, uno por uno, con **en qué estado está cada uno y hasta cuándo lo tiene**, sin abrir venta por venta.

---

## 2. Contexto

**Lo pide el responsable del proyecto el 17-09-2026** —«quiero tener un registro de yo como usuario los productos que he comprado»— el mismo día que se especifica confirmar el pago, y no es casualidad: hasta que existe `RF-MV-003`, «lo que compré» y «lo que tengo» son la misma lista vacía. Desde que confirmar entrega, dejan de serlo, y la pregunta que una persona se hace no es «¿qué movimientos tengo?» sino **«¿ya tengo el bot que pagué?»**.

**`RF-MV-008` no la responde, y no hay que estirarlo para que lo haga.** Aquel lista **movimientos** —una fila por venta, sin líneas— y su detalle abre **una** venta. Para saber qué productos tiene, la persona tendría que abrir cada venta y mirar cada línea; y si un mismo producto lo compró dos veces, sumarlo ella. Este requerimiento responde por **productos**, que es otra pregunta con otra fila.

**Se deriva del libro y no se guarda.** Una línea de venta ya dice qué se compró, cuándo, por cuántos días y —desde `RN-MV-030`— si se entregó y desde cuándo. Una tabla de «productos de la persona» sería una copia que hay que mantener en cada confirmación, cada autorización y cada vencimiento, y que se desincroniza sin que nada falle. Es el mismo criterio con el que `requirements/mv.md` §4.2 decidió que el saldo de puntos **se deriva y no se guarda**.

### 2.1 Un estado por producto, calculado de lo que ya está escrito

| Estado | Cuándo |
|---|---|
| `PENDIENTE_PAGO` | La venta está pendiente |
| `RECHAZADO` / `ANULADO` | La venta terminó así. **Aparecen**, porque «lo compré y no se pagó» es parte de la respuesta |
| `PENDIENTE_AUTORIZACION` | La venta está confirmada y la línea es manual y nadie la ha autorizado (`RN-MV-021`) |
| `ACTIVO` | La línea está entregada y su vigencia no ha pasado —o no caduca (`RN-PM-015`)— |
| `VENCIDO` | La línea está entregada y su vigencia pasó |
| `RETENIDO` | La venta se confirmó y la línea no se entregará (`RN-MV-029`) |

**Lista todo lo comprado, diga lo que diga la venta** (decisión del responsable, 17-09-2026, sobre la alternativa de listar solo lo pagado). Una sola lista responde «qué tengo y qué me falta»; con solo lo pagado, quien acaba de comprar vería la lista vacía y no sabría si la compra existió.

**La vigencia corre desde la entrega, no desde la compra.** Es la decisión 1 de `requirements/mv.md` §5.4: quien pagó treinta días recibe treinta días de uso. Un producto comprado el sábado y confirmado el lunes está activo hasta treinta días **después del lunes**.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquiera autenticado | Ve los productos de las ventas **a su nombre**. Sin permiso, como `RF-MV-008` y por lo mismo |

**«Propio» aquí es un solo papel: el sujeto.** Al revés que `RF-MV-008`, no se incluye lo que la persona **vendió**: un vendedor no «tiene» los bots que colocó. Lo que vendió se sigue viendo allí, con papel `SELLER`.

---

## 4. Alcance

### 4.1 Incluye

- El **listado paginado** de los productos de las ventas a nombre del actor, del más reciente al más antiguo.
- En cada fila: el producto, de qué venta viene, cuántos, cuándo se compró, la implementación, **el estado**, **desde cuándo** se tiene y **hasta cuándo**.
- Un **filtro por estado**.

### 4.2 No incluye

- **Los productos de otras personas.** Ni con permiso: es una consulta sobre uno mismo.
- **Lo vendido** → `RF-MV-008`.
- **Descargar, activar o usar** el producto. El sistema dice que se tiene; qué se hace con ello no es de este módulo.
- **Agrupar** dos compras del mismo producto en una fila. Cada compra es una fila con su propia vigencia; agruparlas obligaría a decidir cuál vigencia manda, y esa decisión no existe.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-001` | Solo lee |
| `RN-MV-004` | Lo pendiente de pago **no se tiene**: aparece con ese estado y sin vigencia |
| `RN-MV-021`, `RN-MV-029`, `RN-MV-030` | Son de donde salen `PENDIENTE_AUTORIZACION`, `RETENIDO` y el instante de la entrega |
| `RN-PM-015` | Sin vigencia, **no caduca**: `ACTIVO` sin «hasta» |

**Ninguna regla nueva.** Este requerimiento no decide nada: **lee lo que `RF-MV-003` y `RF-MV-010` deciden**.

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página, tamaño | No | Como todo listado |
| Estado | No | Uno de los seis de §2.1. Uno que no exista es un error |

**Sobre quién NO es un dato de entrada**, como en `RF-MV-008`.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Producto | Identificador, código y **el nombre tal como se compró** (la copia de la línea, `RN-MV-002`) |
| La venta | Identificador y código, para abrirla en `RF-MV-008` |
| Cantidad | |
| Cuándo se compró | La fecha de la venta |
| Implementación | Automática o manual |
| **Estado** | Uno de los seis |
| Desde cuándo | El instante de la entrega; ausente si no se ha entregado |
| Hasta cuándo | La entrega más la vigencia comprada; ausente si no se ha entregado **o si no caduca** |
| Motivo | Solo en `RETENIDO` |

**«Hasta cuándo» viaja calculado**, aunque la persona pudiera sumarlo: sumar días a un instante lo haría cada cliente de la API a su manera, y con distinta zona horaria.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado |
| Postcondición | Ninguna. No se escribe ni se audita |

---

## 8. Flujo principal

1. El actor pide sus productos.
2. El sistema resuelve quién es por su credencial.
3. Toma las líneas de las ventas **a su nombre**, calcula el estado de cada una con la venta, la entrega y la vigencia, y si se indicó un estado se queda con esas.
4. Ordena de la compra más reciente a la más antigua y devuelve la página.

---

## 9. Flujos alternativos

### FA-001 — No compró nada

Página vacía, no un error.

### FA-002 — Compró dos veces el mismo producto

Dos filas, cada una con su venta, su estado y su vigencia.

### FA-003 — La venta es de un paquete

Una fila por producto del paquete, como cualquier otra. El paquete no es un producto que se tenga.

---

## 10. Excepciones

Ninguna propia.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La paginación es válida |
| `VAL-002` | El estado, si viene, es uno de los seis |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-099` | Aparecen **solo** los productos de las ventas a nombre del actor; lo que vendió a otros **no** |
| `CA-MV-100` | Una línea de una venta **pendiente** aparece como `PENDIENTE_PAGO`, sin «desde» ni «hasta» |
| `CA-MV-101` | Una línea **entregada** con vigencia aparece `ACTIVO` con «hasta» = entrega + días, y pasado ese instante aparece `VENCIDO` |
| `CA-MV-102` | Una línea entregada **sin vigencia** aparece `ACTIVO` sin «hasta» |
| `CA-MV-103` | Una línea **manual** de una venta confirmada aparece `PENDIENTE_AUTORIZACION` |
| `CA-MV-104` | Una línea **retenida** aparece `RETENIDO` con su motivo |
| `CA-MV-105` | Una línea de una venta **rechazada** o **anulada** aparece con ese estado |
| `CA-MV-106` | El filtro por estado devuelve **solo** ese estado; uno inexistente es `400` |
| `CA-MV-107` | Va **paginado**, del más reciente al más antiguo, y **una fila por línea** aunque el producto se repita |
| `CA-MV-108` | Cada fila trae el nombre **copiado en la línea**, no el del catálogo |
| `CA-MV-109` | Responde a cualquier autenticado sin permiso; sin autenticar, `401` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Vigencia que vence **exactamente** en el instante consultado | Ya está `VENCIDO`: el borde es el mismo que `SP` fija para la membresía vigente —una fecha igual al instante ya no está vigente— |
| El producto fue **retirado del catálogo** después | Aparece igual, con el nombre copiado. `RN-PM-010` garantiza que la fila del producto sigue existiendo para el código |
| Cantidad mayor que uno | Una fila, con su cantidad. No se multiplica |

---

## 14. Preguntas abiertas

**Si «activo» debería mirar algo más que la vigencia.** Hoy un producto entregado está activo hasta que vence; el día que exista retirar un nivel o revocar un producto, este estado tendrá que leerlo. No es de este requerimiento.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Primera versión, a petición del responsable del proyecto. **Responde por productos y no por movimientos**, que es lo que `RF-MV-008` no puede hacer sin estirarse; **se deriva del libro y no se guarda**, por el mismo criterio que el saldo de puntos; y **lista todo lo comprado con su estado** (decisión del responsable, sobre listar solo lo pagado), con seis estados calculados de lo que la venta, la entrega y la vigencia ya dicen. La vigencia corre desde la entrega. | Responsable del proyecto |
