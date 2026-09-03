# PLAN — `RF-CM-003` Corregir el valor de una tasa

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-003` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 03-09-2026 |
| Versión | 0.4.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 03-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. La mecánica común la fijó el plan de [`RF-CM-001`](../001-registrar-tasa-comision-rol/plan.md) y **este documento la hereda sin repetirla**.

---

## 1. Enfoque

Corrección parcial contra el agregado, con la distinción de **tres estados** en cada campo —ausente, presente en vacío, presente con valor— que el proyecto ya usa en `RF-SP-027` y `RF-PM-004`.

**Lo propio de este plan es que la corrección se quedó sin lo que la hacía interesante y ganó otra cosa.** Perdió el fin de vigencia, y con él la carrera contra la restricción de no solapamiento que obligaba a un bloqueo, a un volcado explícito y a traducir una violación del motor. **Todo eso desaparece de aquí y reaparece en `RF-CM-006`**, que es donde queda la vigencia.

Lo que gana es un peso que no tenía: **el registro de auditoría del cambio pasa a ser la única copia del valor anterior en todo el sistema.**

## 2. Cambios de esquema

**Ninguno propio.** `V49` deja la tabla como esta operación la necesitaba, y `V50` le añade la forma — pero esa migración es de `RF-CM-001` §2.3 y se hereda entera.

**Lo que sí conviene decir aquí es qué le hacen los `CHECK` de `V50` a esta operación**: `ck_commission_rates_forma` vigila que el tipo y el valor concuerden **en cada `UPDATE`, no solo en el `INSERT`**. De modo que una corrección que dejara la fila descuadrada **la rechaza el motor** aunque la aplicación se despistara. Es una red, no la regla: el motor no puede dar el mensaje de `VAL-011`.

**Y tampoco añade nada `RN-CM-019` desde el 03-09-2026**, por el mismo motivo que en `RF-CM-007` §2.4: la suma no se guarda, se calcula en cada corrección.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `CommissionRate` | **Rehecho** | `update(...)` con un solo campo corregible, que **devuelve qué cambió de verdad** |
| `domain/service` | `UpdateCommissionRateService` | **Rehecho, y de nuevo desde v0.4.0** | Caso de uso; pierde el bloqueo y el volcado defensivo de la v0.1.0, y gana desde v0.4.0 dos dependencias: `ProductCommissionRateQueryRepository` y `ProductCommissionCapGuard` |
| `domain/service` | `ProductCommissionCapGuard` | **No, reutilizado de `RF-CM-007`** | La misma suma y el mismo bloqueo, aplicados aquí a cada producto de la tasa en lugar de a uno solo |
| `application` | `UpdateCommissionRateRequest` | **Rehecho** | Un campo corregible y **un inmutable declarado a propósito** |
| `interfaces` | `CommissionRateController` | **Modificado** | `PATCH /api/v1/commission-rates/{id}` |

**El inmutable se declara aunque se rechace**, y esa es la decisión con nombre de este plan. `roleId` no se corrige, pero figura en la petición: sin él, quien intentara cambiarlo leería «propiedad desconocida» y **creería que se equivocó de nombre** en lugar de entender que lo que pide no se puede hacer. Es el criterio de `RF-PM-004` con el tipo y el código de un producto.

**`update(...)` devuelve el mapa de lo que cambió y no un booleano**, porque ese mapa **es** el evento de auditoría: construirlo en el caso de uso obligaría a leer el estado anterior por separado, y ahí es donde se pierde.

### 3.1 La comparación pasa de un número a un objeto, y ahí está el defecto que hay que evitar

`update(...)` decide si hubo cambio comparando el valor viejo con el nuevo. Hasta la v0.2.0 eso era `BigDecimal.compareTo`, y **estaba bien**: `10.00` y `10.0000` son el mismo porcentaje, y compararlos como iguales es lo que evita llenar la auditoría de cambios que no cambian nada (`spec.md` `FA-002`).

**Con dos formas, `compareTo` sobre la cifra se convierte en un defecto silencioso.** `10 %` y `10` de importe fijo dan `compareTo == 0` y **no son ni remotamente el mismo valor**.

De modo que la comparación pasa a ser **del `CommissionValue` entero**, y tiene que cumplir dos cosas a la vez:

| Caso | Qué debe decidir | Por qué |
|---|---|---|
| Misma forma, `10.00` frente a `10.0000` | **No hay cambio** | `FA-002` — la escala no es información |
| Distinta forma, `10` frente a `10` | **Hay cambio** | `FA-004` — la forma sí lo es |

**Es decir: `equals` no sirve y `compareTo` tampoco.** El primero distingue escalas que deberían dar igual; el segundo iguala formas que no lo son. La igualdad de `CommissionValue` es **el tipo por identidad y la cifra por `compareTo`**, y hay que escribirla a mano — un `record` la genera con `equals` y sería incorrecta.

!!! danger "Un `record` de Java con `BigDecimal` genera la comparación equivocada, y no avisa"

    `CommissionValue` es candidato natural a `record`. Si se declara como tal y nadie sobreescribe `equals`, **`FA-002` se rompe**: `10.00` frente a `10.0000` pasaría a contarse como cambio y cada corrección inocua escribiría un evento.

    Y si alguien lo «arregla» comparando solo las cifras con `compareTo`, **se rompe `FA-004`** en la dirección contraria y esta vez en silencio.

    Las dos pruebas van juntas por eso: `CA-CM-024` y `CA-CM-091` son la misma decisión mirada desde sus dos lados, y **cualquiera de las dos sola se puede satisfacer rompiendo la otra**.

### 3.2 Un producto por vez, en un orden que no cambia

`ProductCommissionCapGuard.verificar(...)` (§3) espera **un** producto. Corregir una tasa puede regir sobre veinte, así que `UpdateCommissionRateService` recorre la lista que devuelve `ProductCommissionRateQueryRepository.findByRate(id)` y llama al guardián **una vez por producto**, con el valor **nuevo** de la tasa.

**El orden de ese recorrido no es cualquiera.** `AssociateProductService` también puede estar tomando el mismo bloqueo consultivo sobre uno de esos productos al mismo tiempo (`RF-CM-007` `plan.md` §4.1); si dos transacciones bloquean varios productos en órdenes distintos, cada una puede acabar esperando un bloqueo que la otra ya tiene. Se recorre **ordenado por `productId`**, el mismo criterio en las dos operaciones, para que dos transacciones que se cruzan siempre lo hagan en la misma dirección.

**El rechazo es del primero que falla, y no de todos a la vez.** No hace falta acumular los veinte resultados: en cuanto uno supera cien, la corrección entera se rechaza y los bloqueos de esa transacción se sueltan al hacer rollback. Los productos que sí cabían no llegan a escribirse porque **nada se escribe hasta el paso 8** — la suma se comprueba antes de tocar la fila de la tasa.

## 4. Contrato de API

`PATCH /api/v1/commission-rates/{id}` · `200 OK`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-002`, `VAL-003`, `VAL-009`, `VAL-010`, `VAL-011`, `VAL-012` |
| `403` | Sin el permiso `commissions:update` |
| `404` | `EX-001`: no existe, o está retirada |
| `409` | `EX-006`, desde v0.4.0: la corrección haría pasar de cien a algún producto asociado |

**Vuelve a haber `409`, y no por la misma razón que en la v0.1.0.** Aquella era la vigencia solapada, y esa columna desapareció con la v0.2.0 — de ahí que la nota original dijera que esta operación no podía entrar en conflicto con ninguna fila. `RN-CM-019` reabre esa puerta desde otro lado: el conflicto no es con otra fila de esta tabla, es con la suma que otras filas de `product_commission_rates` ya ocupan.

**`PATCH` y no `PUT`**, y desde la v0.3.0 el argumento hay que rehacerlo. Decía «se corrige un campo, no se sustituye la tasa», y ahora ese campo **es una pareja que viaja entera** (`spec.md` §6.1) — que es justo lo que un `PUT` hace.

Sigue siendo `PATCH` por lo que **no** viaja: el rol. Un `PUT` obligaría a enviarlo para no borrarlo, y **el rol es inmutable**, de modo que el actor tendría que mandar un dato que el sistema va a rechazar si difiere y a ignorar si coincide. El cuerpo seguiría siendo parcial con nombre de completo.

!!! warning "El cuerpo tiene ahora dos regímenes, y hay que escribirlo así en el contrato publicado"

    `rateType` con `percentage` o `fixedAmount` son **una unidad**: van los tres o no va ninguno, y enviar uno suelto es `VAL-011`.

    No es como el resto de las correcciones del proyecto ni como la de una tasa personalizada, donde el fin de vigencia **sí** se parchea solo. La asimetría es deliberada y está argumentada en `spec.md` §6.1; el contrato tiene que decirla, porque un cliente que asuma la costumbre del proyecto escribirá el `PATCH` equivocado y recibirá un `400` que le parecerá injusto.

## 5. Autorización

Permiso `commissions:update`. Alcance global explícito.

Es el mismo permiso que gobierna **asociar y desasociar** (`cm.md` §6), y esa decisión está declarada allí como discutible: asociar cambia lo que se paga tanto como corregir un porcentaje.

## 6. Auditoría

Registro de **cambios**, acción de actualización, con `before` y `after` de cada campo que cambió de verdad.

!!! danger "Este registro dejó de ser un complemento"

    Con vigencia, el valor anterior seguía existiendo en la fila cerrada y la auditoría solo lo acompañaba. **Sin vigencia, la fila se sobrescribe y este registro es el único sitio del sistema donde queda lo que había.**

    De modo que **si esta escritura fallara en silencio, el dato se perdería sin rastro**. No se degrada a mejor esfuerzo: va dentro de la misma transacción que la corrección.

    **Y desde la v0.3.0 tiene que guardar dos cosas, no una.** Un `before` que dijera `10` sin decir que era un porcentaje **no conserva nada**: quien lo lea dentro de un año no podrá saber si esa tasa pagaba una décima parte de la venta o diez unidades de dinero. La instantánea la arma el agregado a partir del `CommissionValue` entero (`RF-CM-001` §3.1), de modo que esto sale solo — pero **sale solo únicamente si el mapa de cambios trata el valor como un campo y no como dos**.

**Solo se emite si hubo cambio.** Una petición que declara lo que la tasa ya dice no produce evento, porque no lo hubo.

## 7. Transaccionalidad

`@Transactional`. La entidad está gestionada y el `UPDATE` sale con la confirmación.

**Se conserva el volcado explícito antes de auditar**, aunque aquí ya no haya ninguna violación que traducir. No es inercia: fija **el orden** entre la escritura de la tasa y la de la auditoría, en lugar de dejarlo a lo que decida el proveedor de persistencia. Con el registro convertido en la única copia del valor anterior, ese orden importa.

## 8. Impacto sobre otros módulos

**Ninguno hasta la v0.3.0. Desde la v0.4.0, el mismo que declara `RF-CM-007` `plan.md` §9**: se consume `ProductCatalog.findPrice`, que `PM` publica, a través de `ProductCommissionCapGuard` — esta operación no llama a `PM` directamente.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Conservar la operación «cambiar a partir de una fecha»** | No hay dónde escribir la fecha. Mantenerla exigiría devolver la vigencia al catálogo, que es la decisión que el responsable del proyecto tomó al revés |
| Ignorar el rol si llega, en vez de rechazarlo | Haría creer que el cambio se aplicó, y quien lo pidió seguiría creyendo que la tasa paga a otro rol |
| No declarar el rol en la petición | Quien intentara cambiarlo leería «propiedad desconocida» y buscaría el error en el nombre del campo |
| Permitir cambiar el rol | La tasa se convierte en otra y **arrastra sus asociaciones** a un rol que nadie eligió. Registrar una tasa nueva es la vía correcta |
| Tratar el campo ausente y el vacío igual | Son dos peticiones distintas y la segunda pide algo imposible. Fundirlas obliga a elegir un comportamiento que miente sobre la otra |
| Comparar los porcentajes como texto | `10.00` y `10.0000` son el mismo porcentaje. Llenaría la auditoría de cambios que no cambian nada |
| **Seguir comparando solo la cifra** con `compareTo` | `10 %` y `10` fijos darían igual, y el cambio de forma **se perdería en silencio con éxito devuelto**. Ver §3.1 |
| Dejar `CommissionValue` como `record` **sin tocar `equals`** | Rompe `FA-002` en la dirección contraria: cada corrección de escala escribiría un evento. Ver §3.1 |
| **Parchear `rateType`, `percentage` y `fixedAmount` por separado**, como el resto de correcciones | Un importe suelto sobre una tasa de porcentaje no permite distinguir un cambio de forma de una equivocación de campo. `spec.md` §6.1 |
| Pasar a `PUT`, ya que el valor viaja entero | Obligaría a enviar el rol, que es inmutable: un cuerpo parcial con nombre de completo. Ver §4 |
| **Hacer la forma inmutable**, junto al rol | Decisión del responsable del proyecto en contra (02-09-2026). Obligaría a desasociar cada producto, retirar y volver a asociar — y **esos productos no comisionan mientras tanto**. `spec.md` §14 |
| Permitir el cambio de forma **solo en tasas sin asociaciones** | Descartado con lo anterior: el caso en que hace falta corregir es precisamente aquel en que la tasa ya está en uso |
| Acotar el importe fijo por arriba **al corregir**, ya que aquí sí se conocen las asociaciones | Era la postura hasta la v0.3.0. **Se revierte en la v0.4.0** (`cm.md` v0.8.0, decisión del responsable del proyecto, 03-09-2026): se acepta que el tope quede calculado contra el precio de hoy y pueda desactualizarse si `RF-PM-004` lo cambia después, en lugar de no comprobar nada |
| **Impedir corregir una tasa asociada** | Sería coherente con `RN-CM-015`, y es la restricción equivocada: retirar destruye la tasa, corregir la mantiene. Obligaría a desasociar veinte productos para arreglar una errata |
| Aplicar los productos que caben y rechazar solo los que no | Convierte una corrección en una decisión repartida: la tasa terminaría diciendo una cosa para unos productos y otra para otros, sin que nadie lo haya pedido. `spec.md` §4.1 lo exige entero o nada |
| Construir un guardián propio en vez de reutilizar `ProductCommissionCapGuard` de `RF-CM-007` | Duplicaría la suma, la conversión del valor fijo y el bloqueo consultivo en dos sitios que tienen que decidir exactamente lo mismo sobre la misma tabla |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Corregir reescriba lo ya pagado sin dejar rastro** | **No se mitiga en este módulo.** Depende de `RN-CM-008` y del módulo de liquidación, que no existe. Declarado en `spec.md` y en `cm.md` §1.4 |
| 2 | Una corrección cambie en silencio lo que pagan veinte productos | La respuesta devuelve **cuántos son**, de modo que el efecto es visible en el momento de hacerlo |
| 3 | Alguien busque «cerrar la vigencia» y no la encuentre | `spec.md` §13 lo declara: la operación no existe, y la alternativa tampoco conserva el historial |
| 4 | **Un cambio de forma con la misma cifra se pierda devolviendo éxito** | Es el riesgo propio de la v0.3.0 y **el único de esta lista que falla en silencio**. La igualdad de `CommissionValue` (§3.1) y las dos pruebas enfrentadas, `CA-CM-024` y `CA-CM-091` |
| 5 | La instantánea guarde el número sin la forma | Sale bien solo si el mapa de cambios trata el valor como **un** campo. §6 |
| 6 | Un cliente parchee `fixedAmount` suelto, por costumbre del proyecto | `400` con `VAL-011`, y el contrato publicado tiene que explicar por qué. §4 |
| 7 | Veinte productos cambien de **forma** de pago sin aviso | La respuesta devuelve cuántos son, igual que con el número. Es lo que se aceptó al decidir que la forma se corrige (`spec.md` §14) |
| 8 | Corregir una tasa deje pasado de cien a un producto asociado | `RN-CM-019`, desde v0.4.0: `EX-006` lo rechaza entero antes de escribir |
| 9 | Dos peticiones —una corrección y una asociación de `RF-CM-007`— bloqueando el mismo conjunto de productos en órdenes distintos | El mismo criterio de orden en las dos: por `productId` (§3.2) |
| 10 | El precio de un producto cambia después de corregir, y la suma comprobada queda desactualizada | No se cierra desde aquí. Aceptado a conciencia, igual que en `RF-CM-007` `spec.md` §13 |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Corrección y respuesta resuelta | Integración | `CA-CM-021` |
| **El valor anterior solo sobrevive en la auditoría** | Integración | `CA-CM-022`: se comprueba **en la tabla** que el 10 ya no está, y **en el registro** que sí |
| Petición que no cambia nada | Integración | `CA-CM-023`: la marca de modificación **no se mueve** |
| Escala distinta, mismo porcentaje | Unitaria | `CA-CM-024`: no produce cambio |
| Rechazo del inmutable | Integración | `CA-CM-025`: `400`, y **el rol en base sigue siendo el de antes** |
| Vaciar el porcentaje | Integración y unitaria | `CA-CM-026` |
| Petición vacía | Integración | `CA-CM-027` |
| Tasa retirada | Integración | `CA-CM-028`: `404` |

| **Cambio de forma** | Integración | `CA-CM-090`: de porcentaje a importe fijo, y la tasa queda en importe fijo |
| **La misma cifra en la otra forma** | Integración y unitaria | `CA-CM-091`: **sí hay cambio**. Ver abajo |
| El evento lleva la forma, no solo el número | Integración | `CA-CM-092`: el `before` dice `PORCENTAJE 10`, no `10` |
| El valor sin la forma | Integración | `CA-CM-093`: `400` con `VAL-011`, y **la tasa intacta** |
| El tope que solo existe en una forma | Integración | `CA-CM-094`: `150` rechazado en porcentaje, aceptado en importe fijo |
| Los asociados pasan a la forma nueva | Integración | `CA-CM-095`, resolviendo |
| `RN-CM-019`, suma que cabe y suma que se pasa | Integración | `CA-CM-110`, `CA-CM-111`: el rechazo **no deja ningún producto afectado**, ni los que sí cabían |
| `RN-CM-019` sobre veinte productos | Integración | `CA-CM-112`: uno solo que se pase de cien rechaza los veinte |
| `RN-CM-019` con valor fijo | Integración | `CA-CM-113`: convertido contra el precio de cada producto |
| Tasa sin asociaciones | Integración | `CA-CM-114`: se comporta igual que antes de esta versión |

**`CA-CM-022` es la prueba que justifica este requerimiento**, y su forma importa: no basta con verificar que el registro se escribió. Comprueba **las dos mitades** —que el valor anterior desapareció de la tabla y que sobrevivió en la auditoría—, porque lo que se está afirmando es que ese registro es la única copia.

!!! danger "`CA-CM-024` y `CA-CM-091` tienen que ejecutarse como pareja, y ninguna de las dos vale sola"

    Usan **los mismos números** y esperan **lo contrario**:

    | Prueba | Petición | Espera |
    |---|---|---|
    | `CA-CM-024` | `10.0000 %` sobre una tasa de `10.00 %` | **Sin cambio**: ni evento ni marca movida |
    | `CA-CM-091` | `10` fijo sobre una tasa de `10.00 %` | **Cambio**: evento con el antes y el después |

    Cualquier implementación **puede satisfacer una rompiendo la otra**, y las dos maneras de romperlo son las dos maneras naturales de escribir la comparación: `equals` pasa `CA-CM-091` y falla `CA-CM-024`; `compareTo` sobre la cifra pasa `CA-CM-024` y falla `CA-CM-091`.

    Por eso `CA-CM-091` se prueba **también en unitaria**, sobre `CommissionValue` directamente: por la API el fallo se ve como un `200` con la tasa sin cambiar, que **es indistinguible de `FA-001` funcionando bien**.
