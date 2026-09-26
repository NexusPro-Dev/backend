# SPEC — `RF-MV-018` Volver a pagar una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-018` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien compró algo y **no pudo pagarlo** —la aplicación se cayó, o quien cobra lo rechazó— pueda **intentar pagarlo otra vez**, con el mismo método o con otro, **sin registrar otra venta** y sin que un reintento pueda cobrarle dos veces.

---

## 2. Contexto

**Hasta el 26-09-2026 una venta llevaba su método de pago escrito en ella**, y un cobro fallido solo tenía una salida: registrar otra venta. La primera quedaba pendiente para siempre, o alguien la anulaba a mano, y el comprador veía dos compras de lo mismo en su registro. **El responsable del proyecto decidió** ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 §4.3, `RN-MV-039`) que **el método es de cada intento de pago y no de la venta**, y que el intento tiene sus propios estados: pendiente, confirmado o rechazado.

**Este requerimiento es el que da sentido a esa decisión**, y por eso carga con ella entera: es el primero que necesita que una venta pueda tener más de un pago, y el que hace que las demás operaciones sobre la venta pasen a trabajar sobre sus pagos (§2.2).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo el comprador, y solo lo suyo** | Vuelve a pagar quien compró, sobre su propia venta. Una venta ajena responde **no encontrado**, como en el detalle propio (`RF-MV-008`): responder «prohibido» confirmaría que existe |
| **Solo una venta pendiente sin pago pendiente** | Si la venta está confirmada o anulada, no hay nada que pagar. Si tiene un pago **pendiente**, ese pago puede estar entrando ahora mismo: abrir otro en paralelo es exactamente el doble cobro que `RN-MV-039` impide. Para reintentar, el pago pendiente tiene que haberse **rechazado** antes (`RF-MV-004`, o quien cobra) |
| **El método lo elige el comprador, con las reglas de registrar** | Tiene que existir y estar activo (`RN-MV-018`), y la regla de lo gratuito vale en los dos sentidos (`RN-MV-022`): una venta de importe cero no declara método —lo pone el sistema— y una cobrada no puede declarar el gratuito |
| **La clave de idempotencia es obligatoria** | Es lo que convierte «la aplicación se cayó» en un caso inofensivo (`RN-MV-040`): la misma petición repetida devuelve **el pago que ya creó**, en lugar de crear otro. Sin clave no hay forma de distinguir un reintento de un segundo pago, y aquí la diferencia es cobrar una o dos veces |
| **La misma clave con otra petición es un error** | Si la clave ya se usó para otra venta o con otro método, no es un reintento: es un cliente que reutiliza claves, y responder con el pago de otra petición sería mentirle |

### 2.2 Lo que cambia en las demás operaciones, y lo que no

**El contrato de la venta no se rompe** (`requirements/mv.md` §4.3), y es deliberado: el frontend sigue enviando y leyendo el método donde lo hacía.

| Operación | Qué cambia |
|---|---|
| **Registrar y comprar** (`RF-MV-001`, `RF-MV-002`, `RF-MV-011`, `RF-MV-012`, `RF-MV-013`, y la venta del alta de `RF-SP-045`) | La venta nace con **su primer pago, pendiente**, con el método que se indicó. Admiten una clave de idempotencia **opcional**: si llega, se guarda en ese primer pago; si no, la pone el sistema. **Opcional y no obligatoria** porque exigirla hoy rompería a todos los clientes de esas rutas, y ahí el daño de un reintento es una venta pendiente de más —que se anula—, no un cobro |
| **Confirmar** (`RF-MV-003`) | Confirma **el pago pendiente** de la venta, y la venta con él. Una venta pendiente **sin** pago pendiente —porque el último se rechazó— no se puede confirmar: no hay nada que confirmar. Ver su `spec.md` v0.2.0 |
| **Anular** (`RF-MV-005`) | Cierra el pago pendiente como **rechazado**, con el motivo de la anulación. Ver su `spec.md` v0.2.0 |
| **Rechazar** (`RF-MV-004`) | Nace: rechaza el pago pendiente y deja la venta pendiente, que es lo que abre la puerta a este requerimiento |
| **Los listados** (`RF-MV-006`, `RF-MV-008`, `RF-MV-015`) | Siguen publicando y filtrando por el método, que pasa a ser el del **último pago** de la venta: el confirmado, si lo hay, porque después de él no hay más intentos |
| **El detalle** | Publica además **los pagos** de la venta, del más antiguo al más reciente (`RN-MV-047`) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien compró, con `movements:retry-payment` | Vuelve a pagar una venta suya pendiente |

---

## 4. Alcance

### 4.1 Incluye

- Abrir **un nuevo pago pendiente** sobre una venta propia pendiente cuyo último pago se rechazó, con el método elegido.
- Reconocer la **misma petición repetida** y devolver lo que ya creó.
- Que la venta nazca con su primer pago, que confirmar y anular trabajen sobre los pagos, y que las respuestas publiquen el método del último pago y la lista de pagos (§2.2).
- Mover a los pagos el método de **todas las ventas que ya existen**, sin perder ninguno.

### 4.2 No incluye

- **Cobrar.** Abrir un pago no mueve dinero: el pago queda pendiente hasta que alguien —hoy una persona, mañana una pasarela— lo confirme o lo rechace.
- **Que un funcionario vuelva a pagar la venta de otro.** No se ha pedido, y exigiría decidir qué permiso lo autoriza.
- **Pagar una venta en varias partes.** Cada pago es por el importe entero de la venta.
- **Rechazar un pago** (`RF-MV-004`), que es un requerimiento propio.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-039` | A lo sumo un pago pendiente y a lo sumo uno confirmado por venta; un pago rechazado no cambia la venta; el importe del pago es lo que la venta cobra |
| `RN-MV-040` | La clave de idempotencia; la misma petición no crea dos pagos |
| `RN-MV-018` | El método tiene que estar activo **hoy**; uno desactivado no invalida los pagos que ya lo usaron |
| `RN-MV-022` | Importe cero y método gratuito son lo mismo, en los dos sentidos, **en cada intento** |
| `RN-MV-023` | El comprador no elige un método interno; el gratuito lo pone el sistema |
| `RN-MV-005` | Solo se paga lo pendiente; una venta confirmada o anulada no admite pagos |
| `RN-MV-001` | La venta no se toca: ni su importe ni sus líneas. Lo que se crea es un pago |

**Ninguna regla nueva**: las dos que carga este requerimiento nacieron el 26-09-2026 con la etapa 6.

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| La venta | Sí | Cuál se vuelve a pagar, por su identificador |
| Método de pago | **Condicional** | Obligatorio si la venta cobra algo; **prohibido** si su importe es cero (`RN-MV-022`) |
| Clave de idempotencia | **Sí** | Un texto que el cliente genera **una vez por intento** y repite tal cual si tiene que reenviar la petición |

### 6.2 Salida

**La venta, con sus pagos** —la misma forma que el detalle propio—, donde el último es el que se acaba de abrir.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:retry-payment`; la venta existe, es suya, es una venta y está pendiente; no tiene ningún pago pendiente; el método es válido para su importe; la clave tiene forma válida |
| Postcondición | La venta tiene un pago pendiente más, con el método elegido y el importe de la venta; la venta no cambió; el pago está auditado |

---

## 8. Flujo principal

1. El comprador indica qué venta vuelve a pagar, con qué método y con qué clave.
2. El sistema comprueba la forma de la clave y del método, **antes de mirar la venta**.
3. Si la clave **ya existe**, responde según §9 `FA-001` o `EX-006`, y termina.
4. Comprueba que la venta existe, es suya, es una venta y está pendiente.
5. Resuelve el método con las reglas de registrar (`RN-MV-018`, `RN-MV-022`, `RN-MV-023`).
6. Abre el pago **pendiente**, por el importe de la venta, **en un acto que falla si ya hay otro pendiente**.
7. Audita el pago.
8. Devuelve la venta con sus pagos.

---

## 9. Flujos alternativos

### FA-001 — La misma petición llega dos veces

La aplicación se cayó después de enviar y antes de recibir. **La segunda petición, con la misma clave, la misma venta y el mismo método, recibe la venta con el pago que abrió la primera**, y no se crea nada. Da igual en qué estado esté ese pago ahora: si entretanto se rechazó, la respuesta lo dice, y el cliente sabe que tiene que abrir **otro** intento con **otra** clave.

### FA-002 — Dos peticiones distintas llegan a la vez

Dos claves distintas para la misma venta —el comprador pulsó en dos pestañas—. **Una abre el pago y la otra recibe conflicto**: ya hay un pago pendiente. Lo decide el esquema y no una lectura previa, porque dos lecturas simultáneas verían las dos que no hay ninguno.

### FA-003 — La venta es de importe cero

El comprador no indica método; el pago se abre con el gratuito. Es el caso raro —una venta gratuita cuyo pago se rechazó— y se trata igual que al registrar.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La venta no existe, **o no es del actor** | No encontrado. Las dos, igual |
| `EX-002` | La venta no es una venta —un retiro, un bono— | No encontrado: por esta ruta solo se ven ventas propias (`RN-MV-047`) |
| `EX-003` | La venta no está pendiente | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-004` | La venta **tiene un pago pendiente** | Conflicto: hay un pago en curso y hay que esperar a que se resuelva. Nada cambia |
| `EX-005` | El método no existe, está desactivado, o no casa con el importe | Las respuestas de registrar (`RF-MV-001` `EX-010`, `RN-MV-022`) |
| `EX-006` | La clave ya se usó **para otra venta o con otro método** | Conflicto: la clave no es de esta petición. Nada cambia |
| `EX-007` | Falta la clave, o no tiene forma válida | Rechazo, antes de tocar nada |
| `EX-008` | Quien pregunta no tiene `movements:retry-payment` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El identificador de la venta es válido |
| `VAL-002` | La clave está presente, tiene entre 8 y 80 caracteres y solo lleva caracteres visibles sin espacios |
| `VAL-003` | El método está presente si la venta cobra algo, y ausente si no |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-206` | Sobre una venta propia pendiente cuyo último pago se rechazó, se abre un **pago pendiente nuevo** con el método elegido y el importe de la venta; la venta **sigue pendiente** y la respuesta trae los dos pagos, el nuevo el último |
| `CA-MV-207` | El nuevo pago puede llevar **otro método** que el rechazado |
| `CA-MV-208` | La **misma petición repetida** —misma clave, misma venta, mismo método— devuelve el mismo pago y **no crea otro** |
| `CA-MV-209` | La misma clave con **otra venta** o con **otro método** responde conflicto y no crea nada |
| `CA-MV-210` | Con un pago **pendiente** en la venta responde conflicto y no crea nada; **dos peticiones simultáneas** con claves distintas abren **un solo** pago |
| `CA-MV-211` | Una venta **confirmada** o **anulada** responde conflicto diciendo su estado |
| `CA-MV-212` | Una venta **ajena** responde no encontrado, igual que una que no existe |
| `CA-MV-213` | Un método desactivado, inexistente o que no casa con el importe responde como al registrar; en una venta de **importe cero**, sin método, el pago se abre con el gratuito |
| `CA-MV-214` | Sin clave, o con una clave malformada, responde rechazo antes de tocar la venta |
| `CA-MV-215` | Sin `movements:retry-payment` responde prohibido; sin autenticar, `401` |
| `CA-MV-216` | **Registrar** una venta —por cualquiera de sus entradas— la deja con **un pago pendiente** con el método indicado; con clave de idempotencia la guarda, y **repetir** la compra con la misma clave devuelve la misma venta sin registrar otra |
| `CA-MV-217` | Los listados publican como método **el del último pago**, y el filtro por método busca sobre él: una venta pagada al segundo intento con otro método aparece bajo el segundo y no bajo el primero |

**`CA-MV-210` con dos peticiones simultáneas es el que sostiene el requerimiento**: sin él, «volver a pagar» sería una forma nueva de cobrar dos veces.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El pago rechazado **y** el nuevo usan el mismo método | Se admite: que la tarjeta fallara una vez no dice que vaya a fallar otra |
| Una venta de **paquete** | Se vuelve a pagar entera, por lo que cobra la venta; el paquete no se relee |
| El producto se **retiró** del catálogo entre la venta y el reintento | Se paga igual: lo vendido está copiado en la venta (`RN-MV-002`) y no se vuelve a validar la oferta. Lo que se paga es **esa** venta |
| El método elegido se **desactivó** después del pago rechazado | Se rechaza el nuevo intento con ese método; el rechazado lo sigue mostrando |
| Una venta **anterior al 26-09-2026** | Tiene su pago desde la migración, con el método que tenía y el estado que le corresponde, y se trata igual que cualquier otra |
| Muchos intentos rechazados sobre la misma venta | Se admiten todos. No hay tope: ninguno cobró, y el que quiera cortar lo hace anulando la venta |

---

## 14. Preguntas abiertas

**Si un pago pendiente caduca solo.** Hoy un pago pendiente espera hasta que alguien lo resuelve, y mientras tanto la venta no admite otro. Cuando llegue la pasarela habrá que decidir si un pago sin respuesta **en X minutos** se da por rechazado; es una regla de la integración y no se inventa aquí.

**Si el comprador puede rechazar su propio pago pendiente** para reintentar sin esperar. No se ha pedido.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 26-09-2026 | Primera versión, con la etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 y v0.45.0). Lo que la spec carga: **el método es de cada pago y no de la venta**, y por eso **vuelve a pagar el comprador sobre la misma venta**, solo cuando no hay un pago pendiente; **la clave de idempotencia es obligatoria aquí y opcional al comprar**, porque aquí la diferencia entre un reintento y un segundo pago es un cobro; y **el contrato de la venta no se rompe** (§2.2). Criterios `CA-MV-206` a `CA-MV-217`. | Responsable del proyecto |
