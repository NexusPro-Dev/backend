# SPEC — `RF-MV-003` Confirmar una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-003` |
| Módulo | `MV` — Movimientos |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

!!! warning "Enmendada el 26-09-2026 — se confirma el PAGO pendiente, y la venta con él"

    La etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 §4.3, `RN-MV-039`) saca el método de la venta y lo pone en **cada intento de pago**, con sus propios estados. Por decisión del responsable del proyecto, confirmar **no cambia de ruta ni de forma**, y sí de objeto: **confirma el pago pendiente de la venta y, en el mismo acto, la venta**. Todo lo demás de esta spec —la entrega, `RN-MV-029`, la atomicidad— sigue igual.

    **Aparece un caso nuevo**: una venta **pendiente sin pago pendiente**, porque el último se rechazó (`RF-MV-004`) y el comprador todavía no ha vuelto a pagar (`RF-MV-018`). **No se puede confirmar**: no hay ningún cobro que dar por entrado. Responde conflicto y no cambia nada. La respuesta gana **los pagos** de la venta (`RN-MV-047`). Criterios `CA-MV-218` y `CA-MV-219`; lo construye `RF-MV-018` · `tasks.md` `T-08`.

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien concilia pagos **dé por pagada** una venta pendiente y que, en ese mismo acto, **lo comprado se entregue**: la membresía pasa a estar vigente si el producto es un upgrade que se entrega solo, y cada línea queda diciendo si se entregó, si espera a alguien o si no se entregará.

---

## 2. Contexto

**Hoy una venta nace pendiente y se queda ahí.** `RF-MV-001`, `RF-MV-002` y `RF-MV-012` la registran; `RF-MV-006` y `RF-MV-008` la muestran. Nadie puede decir que el dinero entró, y por eso **nadie recibe nunca lo que compró**: `RN-MV-004` es explícita en que una venta pendiente no concede nada, y su contraparte —`RN-MV-020`, «confirmar tiene que conceder»— estaba escrita desde el 04-09-2026 **sin ninguna operación que la cumpliera**. Este requerimiento es esa operación.

**Estuvo bloqueado por D-26 hasta el 17-09-2026.** Conceder una membresía es escribir en datos de `SP`, y hasta hoy ningún módulo escribía en otro. El responsable del proyecto decidió ese día la forma —`SP` publica la operación de conceder, y `MV` la invoca dentro de su propia transacción (`requirements/mv.md` §3, `architecture.md` §15.2.1)— y con eso el bloqueo desaparece. **Lo que este requerimiento decide es *si* se concede; *cómo* se concede lo decide `SP`.**

**Mientras no exista la pasarela, confirma una persona.** Alguien mira el extracto y dice que el dinero entró; el sistema le cree. Está escrito así en `requirements/mv.md` §4.1 y conviene no perderlo de vista: **lo que se construye aquí es el camino que la pasarela usará mañana**, y por eso se especifica como si el que confirma pudiera repetirse, llegar dos veces o llegar tarde — que es lo que una pasarela hace.

### 2.1 Las tres cosas que confirmar decide, y la que no

| Decisión | Qué se decidió | Dónde está escrito |
|---|---|---|
| **Confirmar dos veces concede una vez** | La transición es atómica y condicionada al estado anterior; la segunda confirmación encuentra la venta confirmada y no hace nada | `RN-MV-005`, §5.4 de `requirements/mv.md` |
| **Confirmar no baja de nivel a nadie** | Si la membresía comprada es inferior a la vigente en ese instante, la venta cobra y la línea queda **retenida**, con motivo y a la vista | `RN-MV-029` — decisión del responsable del 17-09-2026 |
| **La entrega es de la línea** | Cada producto de la venta se entrega, espera o se retiene por su cuenta; la venta confirma una vez | `RN-MV-030` |
| **Qué se hace con una venta cobrada que no entregó** | **Nada, todavía.** Devolver o entregar a mano son operaciones que no existen (`requirements/mv.md` §5.3). Lo que sí se hace es que **se vea** | Pregunta abierta, §14 |

**Lo comprado que no es una membresía se entrega marcándolo entregado, y nada más.** Un bot, o cualquier producto que no sea un upgrade, no tiene hoy ninguna otra representación en el sistema que su línea de venta: «entregarlo» es dejar constancia de **desde cuándo** la persona lo tiene, que es lo que `RF-MV-014` lee para decir si está activo y hasta cuándo. Lo que el producto haga fuera del sistema no es de este módulo.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:confirm` | Confirma cualquier venta pendiente del sistema. Hoy, el superadministrador (`requirements/mv.md` §6.1); mañana, la pasarela por este mismo camino |

**No hay confirmación propia.** Quien compró no puede confirmar su propia venta ni con permiso ni sin él por otra vía: confirmar es afirmar que el dinero **entró**, y eso lo sabe quien lo recibe.

---

## 4. Alcance

### 4.1 Incluye

- Pasar una venta de **pendiente a confirmada**, dejando constancia de **cuándo**.
- **Entregar cada línea** según su implementación: la de un upgrade automático concede la membresía; las demás automáticas quedan entregadas; las manuales quedan pendientes de autorización.
- **Retener** la línea de un upgrade que bajaría de nivel, con el motivo escrito.
- Devolver la venta como queda, con la fecha de confirmación y el estado de entrega de cada línea.
- Auditar el cambio.

### 4.2 No incluye

- **Rechazar** (`RF-MV-004`) ni **anular** (`RF-MV-005`): son las otras dos salidas de la misma revisión y tienen su requerimiento.
- **Autorizar la entrega de lo manual** → `RF-MV-010`. Aquí solo queda pendiente.
- **Habilitar una cuenta en `FTD_PENDIENTE`.** Eso lo hace el primer depósito (`RN-SP-026`, etapa 2). Una venta confirmada a una cuenta que no opera **la deja como estaba**.
- **Devengar comisiones** (etapa 5) ni **mover puntos** (etapa 3).
- **Adjuntar o exigir un comprobante.** `RF-MV-007` lo declara y no existe.
- **Corregir una venta confirmada por error** (`requirements/mv.md` §5.3).
- **Confirmar en lote.** Una venta por operación; la pasarela también notifica de una en una.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-001` | La venta no se toca: ningún importe, ninguna copia, ningún vendedor cambian. Lo que cambia es su **estado** y el **cumplimiento** de cada línea, que es la excepción acotada de `RN-MV-030` |
| `RN-MV-004` | Es la razón de ser: hasta este acto, la venta no ha concedido nada |
| `RN-MV-005` | Solo se confirma lo **pendiente**; de confirmada no se sale; y **confirmar dos veces concede una vez**, que exige que la transición sea atómica |
| `RN-MV-020` | Un upgrade **automático** concede la membresía destino del producto, con la vigencia copiada en la línea contada **desde la confirmación** |
| `RN-MV-021` | Un producto **manual** no se entrega: queda pendiente para `RF-MV-010`. La venta confirma igual |
| `RN-MV-029` | Un upgrade que **bajaría** de nivel se **retiene**; renovar el mismo nivel concede |
| `RN-MV-030` | Cada línea termina en `ENTREGADA` con su instante, en `PENDIENTE` si es manual, o en `RETENIDA` con motivo |
| `RN-SP-014`, `RN-SP-018` | Las aplica `SP` al conceder: cierra la vigente, abre la comprada, el suelo sigue existiendo. Este requerimiento **no las reimplementa** |

**Ninguna regla nueva además de las dos que `requirements/mv.md` v0.24.0 ya declara** (`RN-MV-029`, `RN-MV-030`).

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| La venta | Sí | Cuál se confirma, por su identificador |

**Nada más, y es deliberado.** No se envía el importe que entró —es el de la venta o no se confirma—, ni la fecha —es ahora—, ni el método —ya está en la venta—, ni un comprobante —no existe—. Confirmar es **un hecho**, no un formulario, y todo dato que se admitiera aquí sería una forma de confirmar «a medias» que `RN-MV-005` no admite.

### 6.2 Salida

**La venta, tal como queda**: la misma forma que devuelven registrar y consultar, con dos cosas que hasta hoy no se veían porque no existían:

| Dato | Descripción |
|---|---|
| Cuándo se confirmó | El instante del acto, que es desde el que corre la vigencia de lo automático |
| En cada línea: **implementación, estado de entrega, desde cuándo, y motivo** | Automática o manual; entregada, pendiente o retenida; el instante de la entrega; y, si se retuvo, por qué |

**El motivo de una retención se escribe para una persona**, no como código: «La membresía comprada (VIP) es inferior a la vigente (PLATINO)». Es lo que quien mire esa venta dentro de un año necesita leer sin buscar nada.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:confirm`; la venta existe y está pendiente |
| Postcondición | La venta está confirmada con su instante; cada línea está entregada, pendiente o retenida; si se concedió una membresía, la persona **la tiene vigente** y la anterior quedó cerrada; el cambio está auditado |

**Todo o nada.** Si conceder la membresía falla por lo que sea, la venta **no queda confirmada** y ninguna línea queda entregada. Una venta confirmada cuya membresía no se concedió sería la avería que nadie reporta.

**Se audita dos veces, y son dos hechos.** El cambio de estado de la venta, con el resultado de cada línea, lo audita este módulo; la membresía concedida la audita `SP`, como cualquier cambio de membresía. Quien reconstruya el día verá los dos, con la misma correlación.

---

## 8. Flujo principal

1. El actor indica qué venta confirma.
2. El sistema pasa la venta de pendiente a confirmada **en un solo acto que solo acierta si seguía pendiente**, y anota el instante.
3. Para cada línea, en la misma operación:
    - si el producto es **manual**, la deja pendiente de autorización;
    - si es **automático y no es un upgrade**, la marca entregada en ese instante;
    - si es **automático y es un upgrade**: si la membresía comprada es **inferior** a la que la persona tiene vigente en ese instante, la **retiene** con el motivo; si no, **pide a `SP` que la conceda** con la vigencia copiada en la línea contada desde ese instante, y la marca entregada.
4. Audita el cambio, con el resultado de cada línea.
5. Devuelve la venta como queda.

---

## 9. Flujos alternativos

### FA-001 — La venta no lleva ningún upgrade

Confirma, entrega sus líneas automáticas y **no toca ninguna membresía**. Es el caso común, no el especial.

### FA-002 — La venta lleva líneas manuales

Confirma igual; esas líneas quedan **pendientes** y lo demás se entrega. Una venta con un upgrade automático y un bot manual concede el upgrade y deja el bot esperando a `RF-MV-010`.

### FA-003 — El upgrade es del mismo nivel que el vigente (renovación)

Concede: **se cierra el periodo vigente y se abre uno nuevo** desde la confirmación, con la vigencia comprada. Los días que quedaban del anterior **no se suman ni se descuentan** (`requirements/mv.md` §5.4, decisión 2); `closed_at` deja constancia de cuántos se perdieron.

### FA-004 — La persona no tiene membresía vigente (la suya venció)

Concede. No hay nada por debajo de lo que bajar: lo comprado es lo que pasa a estar vigente.

### FA-005 — La membresía comprada es inferior a la vigente

La venta **se confirma y cobra**; la línea del upgrade queda **retenida** con el motivo, y la membresía de la persona **no se toca** (`RN-MV-029`). Las demás líneas se entregan con normalidad.

### FA-006 — Lo comprado no caduca

La membresía se concede **sin fecha de fin** (`RN-PM-015`); la línea queda entregada con su instante y sin «hasta».

### FA-007 — Es una venta de paquete

Cada línea sigue su propia regla. El paquete no cambia nada de este flujo: es una venta con varias líneas.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La venta no existe | No encontrado |
| `EX-002` | La venta **no está pendiente** —ya confirmada, rechazada o anulada— | Conflicto, diciendo en qué estado está. **Nada cambia**: una venta ya confirmada no concede otra vez |
| `EX-003` | Dos confirmaciones **simultáneas** de la misma venta | Una acierta; la otra recibe `EX-002`. Nunca conceden las dos |
| `EX-004` | Conceder la membresía **falla** por un error del sistema | Fallo del sistema; **la venta no queda confirmada** y ninguna línea entregada |
| `EX-005` | Quien pregunta no tiene `movements:confirm` | Prohibido |

**`EX-002` es la que la pasarela va a producir todos los días**, y por eso responde con el estado en lugar de con un rechazo genérico: un reintento del mismo pago tiene que poder saber que ya se procesó.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El identificador es un identificador válido |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-083` | Una venta pendiente pasa a **confirmada** con su instante, y la respuesta lo trae |
| `CA-MV-084` | Confirmarla **por segunda vez** responde conflicto, **no cambia nada** y la membresía no se concede dos veces |
| `CA-MV-085` | Una venta **rechazada o anulada** responde conflicto diciendo su estado |
| `CA-MV-086` | Una venta que **no existe** responde no encontrado |
| `CA-MV-087` | Sin `movements:confirm` responde **prohibido**; sin autenticar, **`401`** |
| `CA-MV-088` | Un upgrade **automático** deja a la persona con la membresía comprada **vigente**, la anterior **cerrada** en el instante de la confirmación, y la línea **entregada** en ese mismo instante |
| `CA-MV-089` | La vigencia de la membresía concedida es **los días copiados en la línea contados desde la confirmación**, no desde la venta |
| `CA-MV-090` | Un upgrade **sin vigencia** concede una membresía **sin fecha de fin** |
| `CA-MV-091` | Renovar el **mismo nivel** cierra el periodo vigente y abre uno nuevo desde la confirmación |
| `CA-MV-092` | Un upgrade **inferior al vigente** deja la venta confirmada, la línea **retenida con motivo legible**, y la membresía **intacta** |
| `CA-MV-093` | Una línea **manual** queda **pendiente** tras confirmar, y la venta confirma igual |
| `CA-MV-094` | Una línea automática que **no es upgrade** queda **entregada** y no toca ninguna membresía |
| `CA-MV-095` | Una venta de **paquete** entrega cada línea por su regla y concede su upgrade una vez |
| `CA-MV-096` | El cambio queda **auditado** en este módulo con el resultado de cada línea, y la membresía concedida queda auditada por `SP` |
| `CA-MV-097` | Dos confirmaciones **simultáneas** producen **una** venta confirmada y **una** membresía concedida |
| `CA-MV-098` | El **detalle** de la venta —propio y de administración— muestra el instante de confirmación y el estado de entrega de cada línea |
| `CA-MV-218` | Confirmar deja **el pago pendiente confirmado**, con el mismo instante que la venta, y la respuesta trae los pagos (26-09-2026) |
| `CA-MV-219` | Una venta pendiente **cuyo último pago se rechazó** responde conflicto —no tiene pago pendiente— y **no cambia nada** (26-09-2026) |

**`CA-MV-084` y `CA-MV-097` son los que sostienen el requerimiento**: son la forma en que una pasarela que reentrega no concede dos veces.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El comprador fue **eliminado** después de registrar | La venta confirma —el dinero entró— y conceder la membresía es un **fallo del sistema** (`EX-004`), no un caso de negocio: conceder a una cuenta eliminada no está definido y **no se decide aquí**. Queda declarado en §14 |
| La venta se registró con la implementación **automática** y el producto se corrigió a **manual** después | Manda **lo copiado en la línea** (`RN-MV-021`, `RN-MV-030`): se entrega. Al revés, se espera. Lo vendido se entrega como se vendió |
| La persona subió de nivel **entre registrar y confirmar** | `FA-005` si lo comprado quedó por debajo; concede si quedó igual o por encima |
| Una venta de **importe cero** (`GRATIS`) | Se confirma como cualquier otra: el «pago» es que no había nada que pagar, y lo comprado se entrega igual |
| La venta pendiente **más antigua del sistema** | Se confirma igual; no hay caducidad de lo pendiente. Anular lo que no se va a pagar es `RF-MV-005` |

---

## 14. Preguntas abiertas

**Qué se hace con una línea retenida.** `RN-MV-029` deja una venta cobrada que no entregó, y la deja **a la vista**. Devolver el dinero, entregar a mano o compensar son operaciones que no existen; cuál de ellas y quién la ejecuta es una decisión pendiente del responsable del proyecto. Hasta entonces, la retención se ve y no se corrige.

**Conceder a una cuenta eliminada.** Hoy es un fallo del sistema, que es la salida más segura mientras no se decida si una venta a una cuenta eliminada debería poder confirmarse.

**El comprobante.** `RF-MV-007` lo declara y no existe. Cuando exista, habrá que decidir si confirmar lo exige.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Primera versión. **El requerimiento estaba declarado desde el 02-09-2026** y bloqueado por **D-26**, que el responsable del proyecto cerró este día —`SP` publica la operación de conceder, `MV` la invoca en su transacción— junto con el caso que `requirements/mv.md` §5.4 dejó abierto: **confirmar no baja de nivel a nadie** (`RN-MV-029`), la salida segura con su coste **a la vista** en lugar de en silencio. Lo que la spec carga: **confirmar es un hecho y no un formulario** (§6.1), la transición es **atómica** para que una pasarela que reentrega conceda una vez (`EX-002`, `EX-003`), la entrega es **de la línea** (`RN-MV-030`) para que una venta con productos que se entregan distinto confirme una vez, y **todo o nada** con la membresía (§7). Lo que deja fuera lo deja a propósito: `FTD`, comisiones, comprobante, autorización manual y corregir lo confirmado. | Responsable del proyecto |
| 0.2.0 | 26-09-2026 | **Se confirma el pago pendiente, y la venta con él** (`requirements/mv.md` v0.44.0, `RN-MV-039`; Art. I.7 sobre un requerimiento construido), por decisión del responsable del proyecto. La ruta y la forma no cambian; una venta pendiente sin pago pendiente no se confirma. `CA-MV-218` y `CA-MV-219`. | Responsable del proyecto |
