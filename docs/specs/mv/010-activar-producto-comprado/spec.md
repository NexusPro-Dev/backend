# SPEC — `RF-MV-010` Activar un producto comprado de implementación manual

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-010` |
| Módulo | `MV` — Movimientos |
| Versión | 0.3.0 |
| Estado | **Aprobada** |
| Enmendada el | 05-10-2026 — **la línea del alta gratuita no la activa el comprador, la activa el primer depósito** ([`requirements/mv.md`](../../../requirements/mv.md) v0.76.0, `RN-MV-075`): `EX-006`, `CA-MV-583` y `CA-MV-584` |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que **quien compró un producto de implementación manual** pueda **activarlo cuando quiera**, y que activarlo lo entregue: desde ese instante lo tiene y su vigencia empieza a correr.

---

## 2. Contexto

**El requerimiento existe desde el 07-09-2026 y cambia de naturaleza el 28-09-2026.** Nació para que un funcionario **autorizara** la entrega de lo manual ([`requirements/mv.md`](../../../requirements/mv.md) v0.9.0): sin él, un producto manual se cobraba y **no se aplicaba nunca**. No llegó a especificarse, y el hueco seguía abierto: hoy una línea manual de una venta confirmada queda pendiente para siempre.

**El 28-09-2026 el responsable del proyecto decidió quién la entrega**: «creemos un endpoint para activar el producto; ojo, solo lo puede activar quien lo compró» ([`requirements/mv.md`](../../../requirements/mv.md) v0.48.0, `RN-MV-048`). Lo manual deja de significar «alguien de la empresa tiene que revisarlo» y pasa a significar **«se entrega cuando su comprador lo pida»**. La consecuencia que importa es la vigencia: **corre desde la activación**, de modo que quien compra treinta días los recibe enteros aunque tarde en empezar a usarlos.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo quien compró** | El sujeto de la venta. **Ningún permiso abre la línea de otra persona**, tampoco los de administración |
| **Lo ajeno no existe** | La línea de otro responde igual que una inexistente: un «prohibido» confirmaría que existe |
| **Activar es entregar** | Lo mismo que confirmar hace con una línea automática: se escribe lo que la persona pasa a tener, y la de un upgrade concede el nivel |
| **La vigencia corre desde la activación** | No desde la compra ni desde la confirmación |
| **No baja de nivel a nadie** | Si al activar un upgrade lo comprado es inferior a lo vigente, la línea se retiene con su motivo, como al confirmar |
| **Activar dos veces entrega una vez** | La segunda responde conflicto |
| **No hay forma de deshacerlo** | Activar es una entrega, y de una entrega no se sale |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien compró, con `movements:activate-own-product` | Activa las líneas manuales de **sus** ventas confirmadas |

---

## 4. Alcance

### 4.1 Incluye

- Activar **una línea** —un producto de una venta—, no la venta entera: una venta puede llevar varias líneas manuales, y cada una se activa cuando su comprador quiera.
- Entregarla: la posesión, la vigencia y, si es un upgrade, el nivel.
- Devolver el producto comprado como queda, con la misma forma que el registro de lo comprado.

### 4.2 No incluye

- **Que la empresa active por la persona.** Si hace falta, será otro requerimiento con su permiso.
- **Desactivar, pausar o devolver** lo activado.
- **Elegir la fecha de inicio**: se activa ahora.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-048` | Solo el sujeto de la venta; solo lo manual, pendiente y de una venta confirmada; activar entrega una vez |
| `RN-MV-021` | Lo manual no se entregó al confirmar: espera a esta operación |
| `RN-MV-030` | La línea pasa de pendiente a entregada, con el instante |
| `RN-MV-036` | Entregar escribe la posesión, con la vigencia copiada en la línea contada desde la activación |
| `RN-MV-020`, `RN-MV-029` | El upgrade concede el nivel, salvo que baje: entonces se retiene con motivo |
| `RN-MV-032` | Desde que se pagó, la línea publica los enlaces de entrega —cupón del bot y descarga— si el producto los declara; **antes de activarla ya los trae** (enmendada el 28-09-2026) |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| La línea | Sí | Qué producto de qué compra, por su identificador |

**Sin cuerpo.** Quién activa sale de la credencial.

### 6.2 Salida

**El producto comprado**, con su estado ya calculado —activo, o retenido con su motivo—, desde cuándo lo tiene, hasta cuándo y el cupón si lo hay.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:activate-own-product`; la línea existe **y es de una venta a su nombre**; la venta está confirmada; la línea es manual y está pendiente |
| Postcondición | La línea está entregada desde ahora —o retenida con su motivo—; la persona tiene la posesión con su vigencia; el nivel, si era un upgrade que sube; todo está auditado |

---

## 8. Flujo principal

1. El actor indica qué línea activa.
2. El sistema busca la línea **entre las de sus compras**, y la reserva para que nadie más la toque mientras tanto.
3. Comprueba que la venta está confirmada, que la línea es manual y que sigue pendiente.
4. La entrega: si es un upgrade que bajaría de nivel, la retiene con su motivo; si no, escribe la posesión —y el nivel, si lo concede— y la marca entregada ahora.
5. Audita y devuelve el producto como queda.

---

## 9. Flujos alternativos

### FA-001 — Dos activaciones de la misma línea a la vez

La segunda espera a la primera, encuentra la línea entregada y responde conflicto. Se entrega una vez.

### FA-002 — Un upgrade que bajaría de nivel

Entre la compra y la activación la persona subió por otra vía. La línea queda **retenida** con el motivo, la respuesta lo dice y el nivel no cambia (`RN-MV-029`).

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La línea no existe **o es de una venta de otra persona** | No encontrado. Las dos son la misma respuesta |
| `EX-002` | La venta no está confirmada —pendiente de pago, rechazada o anulada— | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-003` | La línea no es manual: se entrega sola al confirmar | Conflicto. Nada cambia |
| `EX-004` | La línea ya no está pendiente: entregada o retenida | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-005` | Quien pregunta no tiene `movements:activate-own-product` | Prohibido |
| `EX-006` | **La cuenta del comprador espera su primer depósito** (`FTD_PENDIENTE`): lo que compró al registrarse se activa con ese depósito y no a mano (`RN-MV-075`). Se comprueba **antes** que el estado de la línea | Conflicto, con el mensaje «Lo que compraste se activa al confirmarse tu primer depósito.». Nada cambia |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El identificador es válido |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-275` | Una línea manual pendiente de una venta **propia y confirmada** se activa: la respuesta la trae **activa**, con «desde» = el instante de la activación y «hasta» = ese instante más la vigencia comprada; la persona **tiene** el producto |
| `CA-MV-276` | La vigencia corre **desde la activación y no desde la confirmación**: una línea confirmada antes y activada después vence contando desde la activación |
| `CA-MV-277` | Un **upgrade** manual concede la membresía al activarse; si en ese instante la comprada es **inferior** a la vigente, la línea queda **retenida** con su motivo y el nivel no cambia |
| `CA-MV-278` | La línea de una venta **de otra persona** responde no encontrado, **igual que una inexistente**, también para quien tiene todos los permisos de administración; y la línea sigue pendiente |
| `CA-MV-279` | Una línea manual de una venta **pendiente de pago** o **anulada** responde conflicto, con el estado en el mensaje, y sigue pendiente |
| `CA-MV-280` | Una línea **automática** responde conflicto: no se activa lo que se entrega solo |
| `CA-MV-281` | Activar **por segunda vez** responde conflicto y **no escribe una segunda posesión** |
| `CA-MV-282` | La línea de un producto que declara el **cupón del bot** lo trae resuelto **antes y después de activarla**: la venta está pagada, y el cupón sirve para activar (enmendado el 28-09-2026; hasta entonces, «antes de activarla, no») |
| `CA-MV-283` | Sin `movements:activate-own-product` responde prohibido —**también con `movements:read-own-products`**—; sin autenticar, `401`; un identificador malformado, rechazo. La activación queda auditada |
| `CA-MV-583` | Con el comprador en **`FTD_PENDIENTE`**, activar una línea de su venta del alta responde **conflicto** (`EX-006`), aunque la línea sea manual, pendiente y de una venta confirmada, y **nada cambia**: ni posesión, ni `delivered_at`, ni auditoría (05-10-2026) |
| `CA-MV-584` | La activación por el **primer depósito** (`PublishedFirstDepositActivation`, `RN-SP-057`) entrega **todas** las líneas pendientes de la venta del alta —también las automáticas—, escribe la posesión con la línea y `delivered_at`, y **publica el aviso de líneas comisionables** con ellas. **Una segunda llamada no entrega nada ni publica nada** (05-10-2026) |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una venta con dos líneas manuales | Se activan por separado; activar una no toca la otra |
| Un producto sin vigencia | Activo, sin «hasta» (`RN-PM-015`) |
| El producto se corrigió a automático después de la venta | Manda la copia de la línea (`RN-MV-021`): sigue siendo manual y se activa |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 28-09-2026 | Primera versión, a petición del responsable del proyecto —«creemos un endpoint para activar el producto; ojo, solo lo puede activar quien lo compró»— ([`requirements/mv.md`](../../../requirements/mv.md) v0.48.0, `RN-MV-048`). **El requerimiento deja de ser una autorización de un funcionario** y pasa a ser la activación de quien compró; **lo ajeno responde como inexistente**; **activar es entregar**, con la vigencia desde la activación. Criterios `CA-MV-275` a `CA-MV-283`. | Responsable del proyecto |
| 0.2.0 | 28-09-2026 | **El cupón llega antes de activar** (`RN-MV-032`, [`requirements/mv.md`](../../../requirements/mv.md) v0.51.0): desde que se pagó, la línea trae todos los enlaces del producto, por decisión del responsable del proyecto. Se enmienda `CA-MV-282`. Activar sigue siendo lo que entrega y hace correr la vigencia | Responsable del proyecto |
| 0.3.0 | 05-10-2026 | **La línea del alta gratuita la activa el primer depósito, no el comprador** ([`requirements/mv.md`](../../../requirements/mv.md) v0.76.0, `RN-MV-075`; `RN-MV-048` precisada), por decisión del responsable del proyecto. Mientras la cuenta está en `FTD_PENDIENTE`, activar responde conflicto (`EX-006`, `CA-MV-583`). Y **nace una segunda vía de entrega** que no es este endpoint: la que `SP` dispara al confirmarse el depósito, que entrega las líneas pendientes de esa venta sean automáticas o manuales (`CA-MV-584`). La versión de la cabecera decía 0.1.0 aunque la tabla iba en 0.2.0; queda en 0.3.0. | Responsable del proyecto |
