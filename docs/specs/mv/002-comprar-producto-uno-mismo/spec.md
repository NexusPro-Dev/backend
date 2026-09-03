# SPEC — `RF-MV-002` Comprar un producto para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-002` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

!!! abstract "Esta especificación hereda de `RF-MV-001` y no la repite"

    La venta que produce esta operación es **exactamente la misma**: mismo tipo, mismo estado inicial, mismas reglas, mismo vendedor congelado, mismo comprobante. Lo que cambia es **quién la pide y sobre quién**.

    Todo lo que no aparezca aquí es idéntico a [`RF-MV-001`](../001-registrar-venta/spec.md) — sus flujos, sus once excepciones y sus casos límite valen tal cual. Este documento recoge **solo las cuatro diferencias**, y las argumenta.

---

## 1. Objetivo

Que **un cliente se compre a sí mismo** lo que la plataforma le ofrece, sin que haga falta que alguien se lo registre.

## 2. Contexto

`RF-MV-001` deja la venta en manos de un funcionario. Eso basta para operar y **no basta para vender**: obliga a que alguien esté disponible para que un cliente pueda pagar, y convierte cada compra en una gestión.

**Es la misma decisión que el sistema ya tomó dos veces.** `RF-SP-039` dejó que cada quien consulte su propio perfil y `RF-PM-007` que cada quien vea su propia oferta, las dos **sin permiso**, con el mismo argumento: exigirlo obligaría a concederlo a todos los clientes, que es la forma de que un permiso deje de significar nada. Esta operación cierra ese camino — se puede ver la oferta y ahora se puede comprar de ella.

**La compra sigue naciendo pendiente**, igual que la del funcionario. No hay pasarela todavía: quien confirma que el dinero entró es una persona (`RF-MV-003`), venga la venta de donde venga.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cliente | **Es el actor y es el sujeto.** Compra para sí mismo, y no puede hacerlo para nadie más |
| Vendedor | El suyo, deducido y congelado igual que en `RF-MV-001`. **Cobrará por una venta en la que no intervino** |

!!! warning "El vendedor cobra por una compra que no hizo, y está decidido a conciencia"

    Cuando un cliente compra solo, la comisión sigue yendo a **quien lo trajo** y a toda su cadena (`RN-MV-003`, y `RN-CM-011` para el override).

    No es un efecto colateral: es lo que significa que un cliente **tenga** vendedor. Quien lo captó cobra por lo que ese cliente compre, lo teclee quien lo teclee, y si eso deja de quererse **lo que hay que cambiar es la atribución**, no esta operación.

## 4. Las cuatro diferencias

### 4.1 El cliente no se envía: es quien pide

La operación actúa **siempre sobre la cuenta del actor**. No hay forma de comprar a nombre de otro, ni siquiera indicándolo: el dato no existe en la petición.

De ahí sale la única excepción que este requerimiento pierde respecto de su hermano —`EX-001`, el cliente no existe—: **el actor existe por definición**, porque está autenticado.

**`EX-002` no se pierde, y conviene subrayarlo.** Una cuenta en `FTD_PENDIENTE` **autentica** (`RN-SP-026`): puede entrar, puede mirar y **no puede comprar**. Es exactamente el caso para el que ese estado existe, y es aquí donde más se va a topar con él.

### 4.2 La fecha del hecho no se admite

`RF-MV-001` la acepta opcional, para que un funcionario registre el lunes lo que se cerró el sábado. **Aquí no**: la compra ocurre cuando el cliente la hace, y siempre es ahora.

**Admitirla sería dejar que quien compra decida la fecha de su propia venta**, y esa fecha no es cosmética — sale impresa en el código del comprobante (`RN-MV-016`) y, el día que existan las comisiones, determina **en qué periodo se causan**. Un campo que permite elegir el mes en que se cobra no es un campo de conveniencia.

### 4.3 La respuesta no lleva el vendedor

En `RF-MV-001` el vendedor se devuelve porque **quien registra no lo eligió** y necesita ver a quién se atribuyó lo que acaba de vender.

Aquí no hay nada que verificar: el cliente no eligió vendedor, no puede cambiarlo y **a quién se le paga por su compra no es información suya**. La respuesta lleva lo que compró, cuánto debe y su comprobante.

**Sigue congelándose igual**, y sigue estando en la auditoría. Lo que cambia es a quién se le enseña.

### 4.4 No hace falta ningún permiso

Basta con estar autenticado, como `RF-SP-039` y `RF-PM-007`. Lo que acota lo que se puede comprar **no es un permiso sino la oferta** (`RN-MV-007`): quien no tiene nivel del que partir no tiene nada que comprar, y ahí se acaba.

## 5. Reglas de negocio aplicables

**Las mismas quince de `RF-MV-001`**, sin excepción y sin matices. Ninguna se relaja por que compre el interesado: si acaso, es aquí donde más importa que se cumplan, porque **no hay nadie mirando la pantalla que pueda darse cuenta de que algo va mal**.

Las dos que cambian de significado práctico:

| Regla | Qué cambia |
|---|---|
| `RN-MV-003` | El vendedor se deduce igual. Lo que cambia es que **el cliente no lo ve** |
| `RN-MV-008` | Pasa de ser una comprobación rara a **el rechazo más frecuente de esta operación**: la cuenta recién registrada por enlace puede entrar y viene a comprar |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Método de pago | Sí | Con qué va a pagar | Debe existir y estar activo |
| Líneas | Sí | Qué compra | Igual que en `RF-MV-001` |

**Dos campos, y los dos ya existían.** Lo que define esta entrada es lo que **no** admite: ni cliente, ni fecha, ni precio, ni vendedor.

### 6.2 Salida

La de `RF-MV-001` **menos el vendedor** (§4.3): la venta con su código, su estado pendiente, sus líneas con lo copiado, su moneda y sus importes.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado. **No necesita ningún permiso.**
- El actor **no está en `FTD_PENDIENTE`** y cuelga de un vendedor.
- Tiene al menos un producto en su oferta.

**Postcondiciones**

Las de `RF-MV-001`, sin cambios. **Nadie ha subido de nivel y nadie ha cobrado**: la compra queda pendiente de que alguien confirme que el dinero entró.

## 8. Flujo principal

El de `RF-MV-001` §8, con dos pasos menos: no hay que comprobar que el cliente existe —es el actor— ni resolver la fecha del hecho.

1. El actor envía el método de pago y las líneas.
2. El sistema comprueba que **su propia cuenta puede comprar**: ni eliminada, ni en `FTD_PENDIENTE`.
3. En adelante, idéntico: vendedor, composición, oferta, moneda, copia, totales, código, alta pendiente y auditoría.

## 9. Flujos alternativos

Los cinco de `RF-MV-001` valen igual, salvo `FA-005` —registrar una venta de fecha anterior—, que **aquí no existe** (§4.2).

### FA-006 — Un funcionario usa esta operación para comprarse algo

**Cuándo ocurre:** quien pide no es un cliente sino alguien de la fuerza comercial o de administración.

1. La operación **no lo rechaza por su rol**: no hay ninguna comprobación de rol en esta especificación.
2. Lo rechaza **la oferta**. Solo los consumidores tienen membresía (`RN-SP-018`), de modo que quien no la tiene no tiene oferta y **todo producto que intente comprar cae en `EX-004`**.
3. Se enumera porque el mensaje que verá hablará de la oferta y no de su rol, y porque **el día que un funcionario tenga membresía, podrá comprar** — y eso sería correcto.

## 10. Excepciones

**Las de `RF-MV-001` menos `EX-001`** (§4.1). Las diez restantes valen tal cual, con la misma condición y la misma respuesta.

`EX-002` cambia de redacción: el mensaje ya no habla de «esa cuenta» sino de **la del propio actor**, y tiene que decirle qué le falta —su depósito— porque es la única persona que puede hacer algo al respecto.

## 11. Validaciones

Las de `RF-MV-001` menos `VAL-001` —cliente obligatorio, que ya no se envía— y menos `VAL-007` —fecha no futura, que no se admite—. Las cinco restantes, idénticas.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-019` | Un cliente autenticado **sin ningún permiso** compra un producto de su oferta y la venta nace pendiente |
| `CA-MV-020` | La venta queda atribuida **a su vendedor**, comprobable en la auditoría, y **la respuesta no lo devuelve** |
| `CA-MV-021` | La compra queda **a nombre del actor** y de nadie más: no hay forma de indicar otro cliente |
| `CA-MV-022` | La petición **no admite fecha del hecho**, y la venta lleva la de ahora |
| `CA-MV-023` | El sistema rechaza la compra de un actor en `FTD_PENDIENTE`, diciéndole **que le falta su depósito** |
| `CA-MV-024` | El sistema rechaza un producto fuera de **su** oferta, y un upgrade igual o inferior a su nivel |
| `CA-MV-025` | Un funcionario sin membresía que intente comprar es rechazado **por la oferta**, no por su rol |
| `CA-MV-026` | La venta creada por esta operación y la creada por `RF-MV-001` son **indistinguibles** una vez registradas |

**`CA-MV-026` es el criterio que sostiene las dos operaciones.** Si la venta del cliente y la del funcionario fueran distintas en algo —un campo, un estado, un tipo—, todo lo que venga después tendría que saber por dónde entró cada una: confirmarla, listarla, comisionarla. Que sean iguales es lo que permite que `RF-MV-003` a `RF-MV-008` no se enteren de que existen dos entradas.

## 13. Casos límite

Los de `RF-MV-001`, más dos propios:

- **El cliente compra mientras un funcionario le está registrando la misma venta:** se registran **las dos**, y ninguna de las dos concede nada. Es el mismo caso límite que `RF-MV-001` §13 declara para dos ventas simultáneas del mismo upgrade, y se resuelve en el mismo sitio: al confirmar.
- **El actor se queda sin vendedor entre que mira la oferta y compra:** la compra se rechaza por `EX-003`. Es raro y es correcto: una venta que no se puede atribuir no debe existir, y menos si la pidió quien no puede arreglarlo.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarado el mismo bloqueo de implementación que `RF-MV-001`**: sin `RF-SP-045` no hay clientes que cuelguen de un vendedor, y esta operación **solo la pueden usar clientes**.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 02-09-2026 | Redacción inicial, sin preguntas abiertas. Se escribe **por diferencias** con `RF-MV-001` en lugar de repetirlo, y las diferencias son cuatro: el cliente es el actor, **no se admite la fecha del hecho** —porque elegirla es elegir el periodo en que se comisiona—, la respuesta **no devuelve el vendedor** —el cliente no lo eligió y no es información suya— y no hace falta permiso. Lo que **no** cambia es la venta: `CA-MV-026` exige que las dos operaciones produzcan algo indistinguible, que es lo que permite que los siete requerimientos siguientes no se enteren de que hay dos entradas. Queda declarado que **el vendedor cobra por una compra que no hizo**, y por qué eso es lo que significa que un cliente tenga vendedor. | Responsable técnico |
