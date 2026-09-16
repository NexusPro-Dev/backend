# Requerimientos del Módulo — `CM` Comisiones

| Campo | Valor |
|---|---|
| Módulo | `CM` — Comisiones |
| Paquete | `modules/commissions` |
| Prefijos de permiso | `commissions:` |
| Versión | 0.15.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 28-08-2026 |
| Última actualización | 14-09-2026 |

!!! info "Qué va en este documento"

    El catálogo de requerimientos del módulo: qué debe hacer, bajo qué reglas y con qué permisos.

    El comportamiento detallado de cada requerimiento —flujos, validaciones, criterios de aceptación y casos límite— vive en su tripleta, en `docs/specs/cm/`. Aquí no se repite.

!!! warning "Documento en Borrador: dos decisiones lo condicionan"

    1. **El código `CM`.** Un código, en cuanto aparece en un identificador, no se cambia jamás ([`modules.md` §2.1](../modules.md#21-regla-de-decision)). En cuanto exista `RF-CM-001`, estas dos letras quedan fijadas para siempre, y `modules.md` §6 advierte que los códigos de los módulos candidatos no deberían fijarse hasta conocer el alcance completo del producto. Se procede por decisión del responsable del proyecto, como ya se hizo con `PM`.
    2. **La frontera del alcance** (§1.3): este módulo **declara cuánto se paga**; no calcula, no liquida y no paga. El motivo, en §1.4.

---

## 1. Información del módulo

### 1.1 Descripción

`CM` es dueño de **cuánto se le paga a quien vende**, y lo resuelve con **dos piezas que no se parecen**:

| | Qué es | Vigencia | ¿Con qué producto? |
|---|---|---|---|
| **Tasa de rol** | «Un `AGENTE` gana el 10 % **por este producto**» | **No tiene** | **Nace con él** y rige sobre él desde el alta (15-09-2026) |
| **Tasa personalizada** | «**esta persona** gana el 12 % **por este producto**» | **Sí**, y una sola vigente por persona **y producto** | **Nace con él** y rige sobre él desde el alta (16-09-2026) |

**Toda tasa nace con su producto, y solo rige sobre él** (`RN-CM-021`). La de rol desde el 15-09-2026 y la personalizada desde el 16-09-2026, las dos por decisión del responsable del proyecto. **Ninguna es catálogo**: hasta esas fechas se registraban sin producto y regían únicamente donde se las asociaba (`RN-CM-012`), de modo que una tasa creada y sin asociar no pagaba nada. Hoy no existe una tasa sin producto —`product_id` es obligatorio y no se cambia—, y **rige desde que existe**, sin paso de asociación. Lo que se pierde y lo que se gana está en §5.4 y §5.5.

**La personalizada conserva lo que la distingue: la vigencia.** Es de una persona, sobre un producto, **desde una fecha y hasta otra** —o indefinidamente—, y varias consecutivas sobre el mismo producto son su historial. Donde esa persona tiene una vigente gana sobre la de su rol; donde no, cobra por su rol como cualquier otra. **Lo que ya no tiene es asociación**: del 11-09-2026 al 16-09-2026 se creaba primero y se asociaba después a uno o varios productos; hoy una excepción que abarque varios productos son varias tasas, una por producto.

**La asociación desapareció del módulo entero**, y con ella la asimetría que el 15-09-2026 se había aceptado a conciencia. Aquel día la de rol adoptó el producto como columna y la personalizada siguió asociándose, «porque una excepción de una persona sí abarca varios productos»; el 16-09-2026 el responsable del proyecto decidió que **también la personalizada fuera una por persona y producto**. Las dos clases vuelven a decir lo mismo de la misma manera, y `RN-CM-006` **vuelve al motor**: con la persona, el producto y la vigencia en una sola fila, el `EXCLUDE` que el 11-09-2026 hubo que quitar cabe otra vez.

**Y no lleva rol**, por decisión del responsable del proyecto: es de la persona y punto. Lo que eso cuesta está en §5.3.

### 1.1.1 Las dos formas de declarar una comisión

Desde el 02-09-2026, y por decisión del responsable del proyecto, **cualquiera de las dos piezas puede declararse de dos formas**:

| Forma | Qué dice | Qué la acota |
|---|---|---|
| **Porcentaje** | «gana el 10 % de la venta» | `RN-CM-007`: de cero a cien por su cuenta; `RN-CM-019` acota **la suma** con sus hermanas del mismo producto |
| **Valor fijo** | «gana 10.000 por venta» | **El precio de su producto**, desde el alta: una de rol entra en la suma que `RN-CM-019` acota, convertida contra el precio de **ese** producto; una personalizada tiene su propio tope, individual, contra el precio del suyo. Y en las dos el importe **cabe en los decimales de la moneda del producto** (`RN-CM-017`). `RN-CM-018` ya no tiene a quién alcanzar: ninguna tasa desconoce el precio de nada |

**Una tasa declara una forma y solo una** (`RN-CM-016`). No se suman: no existe «5 % más 10.000». El tipo manda, y el campo de la otra forma va vacío.

!!! danger "El valor fijo no lleva moneda, y esa decisión tiene consecuencias que hay que aceptar a la vez"

    El importe **toma la moneda del producto que se está vendiendo** (`RN-CM-017`). La tasa no la declara.

    Lo que eso significa: **la misma fila paga cosas distintas según a qué producto se aplique.** Una tasa de «10.000 fijos» asociada a un producto en pesos y a otro en dólares no es un error del sistema — es exactamente lo que declara.

    **En la tasa personalizada el efecto era mayor hasta el 11-09-2026**, porque no se asociaba a nada: regía sobre **todos** los productos, de modo que su importe se interpretaba en tantas monedas como hubiera en el catálogo. Desde esa fecha se ató a productos, y **desde el 16-09-2026 nace con uno solo**, y con él con una sola moneda: de las tres consecuencias de no declarar moneda, esta se cerró **en las dos clases**, y el importe fijo se valida contra los decimales de esa moneda al registrar y al corregir (§5.4, §5.5).

    Se acepta a conciencia (§8, v0.7.0). Se descartó que la tasa declarara su propia moneda porque sería un dato que puede mentir: la moneda es la del producto, y el producto ya está en la fila.

### 1.2 Objetivo

Hoy el sistema sabe **qué se vende** (`PM`) y **quién vende** —los roles de tipo `VENDEDOR` y la estructura comercial de `SP`—, y **no sabe cuánto se le paga a quien vende**. Ese dato no existe en ningún sitio: ni un porcentaje, ni una excepción, ni un lugar donde declararlos. Este módulo pone ese objeto en el sistema, que es el paso sin el cual el cálculo de comisiones —cuando exista la venta— no tiene sobre qué operar.

### 1.3 Alcance

**Incluye**

- Registrar y mantener las **tasas de rol de cada producto** —qué paga ese producto a cada rol vendedor—, **en porcentaje o en valor fijo**. Desde el 15-09-2026 **la tasa nace con su producto** y no hay paso de asociación.
- Registrar la **tasa personalizada** de una persona **sobre un producto**, con su vigencia, **también en cualquiera de las dos formas**. Desde el 16-09-2026 **nace con su producto** y no hay paso de asociación.
- Consultar unas y otras: **el listado de todas las tasas de rol** y **el de todas las personalizadas**, cada una con su producto y filtrables por él; y los roles que cobran por un producto. «Quién tiene excepción en este producto» se responde **filtrando las personalizadas por producto**.
- **Resolver la comisión efectiva**: dada una persona, un producto y una **fecha**, **qué le corresponde** —un porcentaje o un importe— y **por qué tasa**.

**No incluye**

- **El cálculo y la liquidación de comisiones.** No se aplaza por reparto: **no hay sobre qué calcular**, porque ninguna tabla de ventas existe. Ver §1.4.
- **El pago de lo liquidado.** Retiros, balances y egresos son del área de **Finanzas**.
- **Los FTDs.** Pertenecen al área y dependen de la venta, que no existe.
- **Quién puede ver las comisiones de quién.** Es alcance de datos y depende de **D-22**, abierta. Ver §5.3.
- **La atribución de la venta.** A qué vendedor se le apunta una venta concreta es una decisión de la venta, no de la tarifa.

### 1.4 La frontera, y por qué está donde está

**Una tasa no calcula nada.** Declara un porcentaje; quien lo aplica es la liquidación, que no existe todavía. La tentación es cerrar el círculo aquí mismo —tasa, cálculo y liquidación en un solo módulo— y hay dos razones para no hacerlo:

1. **No hay tabla de ventas.** Un cálculo de comisión necesita un importe vendido, una fecha y un vendedor atribuido. Ninguna de las tres cosas existe en el sistema. Escribir hoy el cálculo produciría código que no se puede probar contra nada real.
2. **Liquidar sin cobrar es pagar sobre una venta que no ocurrió.** Es el mismo argumento que `PM` §1.4 usa para no registrar la compra antes del cobro.

!!! danger "Y desde el 01-09-2026 este módulo depende de esa liquidación para algo más grave"

    Las tasas de rol **no tienen vigencia**: son un catálogo de lo que se paga **hoy**. Cambiar un porcentaje de 10 a 12 **no deja rastro del 10** — no hay dos filas contando su parte de la historia, hay una que ahora dice otra cosa.

    De modo que **`RN-CM-008` deja de ser una condición prudente y pasa a ser la única defensa del pasado**: si la liquidación no copia el porcentaje que aplicó, cambiar una tasa **reescribe lo que ya se pagó y no queda forma de saberlo**.

    Se acepta a conciencia (§8, v0.4.0). Y mientras esa liquidación no exista, **cambiar una tasa borra el pasado sin dejar rastro**, porque no hay nada que lo haya copiado.

---

## 2. Submódulos

| Submódulo | Responsabilidad | Entidades principales |
|---|---|---|
| Tasas | Las tasas de rol de cada producto y las excepciones por persona sobre cada producto | `commission_rates`, `user_commission_rates` |
| ~~Asociación~~ | ~~Qué tasa rige sobre qué producto~~ **Retirado el 16-09-2026**: ninguna tasa se asocia; las dos nacen con su producto (`RN-CM-021`) | ~~`product_commission_rates`~~ (`V94`), ~~`user_commission_rate_products`~~ (`V10`) |
| Resolución | Qué le corresponde a una persona por un producto **en una fecha** | Las dos |

**Por qué la asociación dejó de ser un submódulo.** Nació el 02-09-2026 porque una tasa de rol **regía sobre varios productos**, y el 11-09-2026 se extendió a la personalizada «con el mismo mecanismo». El 15-09-2026 la tasa de rol pasó a **ser de un producto** (`RN-CM-021`) y «un producto tiene una tasa por cada rol de la cadena» se declaró en la propia tabla; la personalizada conservó su asociación un día más, porque una excepción de una persona sí abarcaba varios productos. El 16-09-2026 el responsable del proyecto decidió que **también fuera una por persona y producto** (§5.5), y lo que aquel argumento temía —corregir un porcentaje en cincuenta filas y que una se quedara atrás— es lo que se acepta en las dos clases: **cada producto se configura por su cuenta**, y una tasa que quiera repetirse en cincuenta productos son cincuenta tasas.

---

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| `SP` | Consume | **Roles** (`RN-CM-001`): validar que el rol existe y que es de tipo `VENDEDOR` |
| `SP` | Consume | **Usuarios**: validar que la persona de una tasa personalizada existe, y conocer su rol vendedor al resolver |
| `PM` | Consume | **Productos** (`RN-CM-002`): validar que el producto de una tasa —de rol o personalizada— existe y no está retirado; su **precio** (`RN-CM-019`), para convertir un valor fijo a su porcentaje equivalente al comprobar el tope; y su **moneda** (`RN-CM-017`), para acotar los decimales de un importe fijo |

La dependencia es **acíclica**: `CM` → `PM` → `SP`. Es el **primer módulo del sistema que depende de dos**, y los consume por las interfaces que cada uno publica (**D-25**).

---

## 4. Requerimientos funcionales

| ID | Nombre | Submódulo | Permiso |
|---|---|---|---|
| `RF-CM-001` | Registrar una tasa de comisión por rol | Tasas | `commissions:create` |
| `RF-CM-002` | Consultar las tasas de comisión | Tasas | `commissions:read` |
| `RF-CM-003` | Corregir el valor de una tasa | Tasas | `commissions:update` |
| `RF-CM-004` | Retirar una tasa de comisión | Tasas | `commissions:delete` |
| `RF-CM-005` | Consultar la comisión efectiva de una persona sobre un producto en una fecha | Resolución | `commissions:read` |
| `RF-CM-006` | Registrar la tasa personalizada de una persona **sobre un producto** | Tasas | `commissions:create` |
| ~~`RF-CM-007`~~ | ~~Asociar una tasa de rol a un producto~~ **Descartado el 15-09-2026** (`RN-CM-021`): la tasa de rol nace con su producto. El número queda consumido | — | — |
| ~~`RF-CM-008`~~ | ~~Retirar la asociación de una tasa con un producto~~ **Descartado el 15-09-2026** (`RN-CM-021`): sin asociación de rol no hay nada que desasociar; el producto deja de pagar a un rol retirando la tasa (`RF-CM-004`). El número queda consumido | — | — |

!!! info "Seis vivos y dos descartados desde el 15-09-2026"

    `RF-CM-007` y `RF-CM-008` existieron desde el 02-09-2026 hasta el 15-09-2026, construidos y con tripleta. Se descartan porque la tasa de rol **nace con su producto** (`RN-CM-021`, §5.4) y la asociación de rol deja de existir; sus rutas —`POST /commission-rates/{id}/products`, `GET /commission-rates/{id}/products` y `POST /commission-rates/{id}/products/{productId}/deletion`— **se retiran del contrato**. Sus tripletas se conservan con el estado `Descartado` en cabecera: son la historia de por qué la asociación existió y por qué dejó de hacer falta. `GET /api/v1/product-commission-rates?productId=` **se conserva**: sigue respondiendo «qué paga este producto y a qué rol», ahora leyendo las tasas del producto y no una asociación.

!!! success "Los ocho están construidos (02-09-2026)"

    `RF-CM-001` a `RF-CM-005` estaban implementados desde el 28-08-2026 con 45 pruebas, y este modelo cambió la forma de `commission_rates`. **Se rehicieron**, y con ellos nacieron los tres nuevos: `V49` reconstruye el esquema y la suite pasa de 45 a **75 pruebas**.

    **`RF-CM-003` y `RF-CM-004` valen para las dos clases de tasa** —la de rol y la personalizada—, cada una en su recurso. No son cuatro requerimientos porque corregir un porcentaje y retirar una tasa son la misma operación sobre dos tablas; lo que **sí** difiere está declarado: en la de rol corregir **borra el pasado**, y en la personalizada no.

    Los tres nuevos se construyeron **sin tripleta previa**, que es una excepción al Art. I.1: sin `RF-CM-007` el módulo entero no paga nada, de modo que rehacer los cinco primeros sin él habría dejado un `CM` que no se puede probar de punta a punta.

    **Las ocho tripletas quedaron escritas ese mismo día** —las cinco primeras rehechas, las tres nuevas de cero—, y **cada una declara en cabecera que se redactó después del código**. La excepción no se borra por haberla pagado: queda registrada aquí y en `requirements.md` v0.88.0, porque lo que se invirtió fue el orden de las compuertas del Art. I.6 y eso no se deshace escribiendo el documento más tarde.

**El alta se parte en dos** —`RF-CM-001` para el rol y `RF-CM-006` para la persona—, al revés que en la versión anterior, donde era una sola con campos opcionales. Ahora **no son la misma operación**: una escribe la configuración de un producto, sin fechas, y la otra registra una excepción con vigencia y con solapamiento que vigilar. **Las dos nacen con su producto** (`RN-CM-021`).

**La asociación tuvo sus operaciones propias** —`RF-CM-007` y `RF-CM-008` para la de rol, y dentro de `RF-CM-006` para la personalizada— mientras era lo único que ponía una tasa en vigor. Desde el 16-09-2026 no queda ninguna: registrar es poner en vigor, y retirar (`RF-CM-004`) es la única forma de dejar de pagar.

---

## 5. Reglas de negocio

### 5.1 Catálogo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-CM-001` | Solo comisionan los roles vendedores | Al registrar una tasa de rol | El rol debe existir y ser de tipo **`VENDEDOR`** (`ck_roles_type`). Un rol funcionario o consumidor se rechaza | Crítica |
| `RN-CM-002` | El producto debe existir | Al registrar cualquier tasa | El producto debe existir en `PM`. Se declara además como clave foránea, en las dos tablas: `commission_rates` (15-09-2026) y `user_commission_rates` (16-09-2026). Alcanzó a la personalizada el 11-09-2026, que hasta entonces no nombraba ninguno | Alta |
| `RN-CM-004` | **La personalizada gana siempre, sobre su producto** | Al resolver | Si la persona tiene una tasa personalizada **vigente en la fecha sobre ese producto**, es esa. Si no, la que su **rol vendedor** tenga sobre ese producto. Si no hay ninguna de las dos, **no hay tarifa**. Siguen siendo dos niveles, no cuatro, y **los dos miran el producto**: desde el 11-09-2026 por la asociación, y desde el 15-09-2026 y el 16-09-2026 por la columna `product_id` de cada tabla. Hasta el 11-09-2026 el primero no lo miraba y la personalizada ganaba **vendiera lo que vendiera**, de modo que tapaba el catálogo entero de su titular y el segundo nivel no llegaba a consultarse nunca | **Crítica** |
| `RN-CM-005` | La tasa no desaparece | Al retirar | La eliminación es **lógica y con motivo** (Art. V.13). La fila permanece para que una liquidación pasada siga resolviendo con qué porcentaje se pagó | Crítica |
| `RN-CM-006` | Una sola tasa personalizada vigente por persona **y producto** | Al registrar una personalizada y al corregir su vigencia | **Ningún día puede estar cubierto por dos tasas personalizadas vivas de la misma persona sobre el mismo producto.** Sí pueden existir varias consecutivas —son el historial— y varias simultáneas **sobre productos distintos**. **Desde el 16-09-2026 VUELVE AL MOTOR**: con la persona, el producto y la vigencia en la misma fila (`RN-CM-021`), se declara como `EXCLUDE` sobre `(user_id, product_id, daterange)` entre las vivas, y el caso de uso la comprueba antes solo para dar el mensaje. Del 11-09-2026 al 16-09-2026 vivió en el caso de uso con un bloqueo consultivo por persona, porque la asociación la hacía cruzar dos tablas; hoy es otra vez la garantía que no depende de que nadie se acuerde de comprobarla | **Crítica** |
| `RN-CM-007` | El porcentaje va de cero a cien | Al registrar y al corregir **una tasa de porcentaje** | Se admite el **cero**, que significa «esto no comisiona» y **no es lo mismo que no tener tasa**: es la forma de asociar un producto a un rol declarando que no paga nada. **No dice nada del valor fijo**, que no está acotado por arriba | Alta |
| `RN-CM-008` | **La liquidación conserva el porcentaje, y es la única defensa del pasado** | Siempre | Las tasas de rol **no tienen vigencia**: corregir un porcentaje **reescribe lo que rigió siempre**. De modo que quien liquide **debe copiar el porcentaje que aplicó**, o cambiar una tasa reescribirá lo ya pagado sin dejar rastro. Es obligación de la liquidación futura, no de estas tablas (§1.4) | **Crítica** |
| `RN-CM-009` | Toda tasa personalizada declara desde cuándo rige | Al registrar una personalizada | El inicio de vigencia es **obligatorio**; el fin es opcional y su ausencia significa **indefinidamente**. Un fin anterior al inicio se rechaza. **Las de rol no llevan fechas** | Alta |
| `RN-CM-010` | No se configura lo que ya no se vende | Al registrar cualquier tasa | No se admite una tasa sobre un producto **retirado**: sería configurar algo que nadie puede vender. Lo que ya existía **permanece**, por `RN-CM-005`. Alcanza a la personalizada desde el 11-09-2026, y desde el 16-09-2026 se comprueba **al registrarla** | Media |
| `RN-CM-011` | Una venta comisiona a **toda la cadena** | Al liquidar | **Override**: cada persona de la cadena comercial gana **su propio porcentaje sobre el mismo importe**. La tasa se resuelve **una vez por nivel** con `RF-CM-005`. **El tope de la suma de la cadena sigue sin dueño**: depende de tantas filas como niveles tenga la cadena, y este módulo, al liquidar, solo ve una a la vez. `RN-CM-019` cierra desde el 03-09-2026 el sub-caso resoluble **antes** de liquidar: cuando la cadena se resuelve entera por tasas de rol asociadas al mismo producto, ese producto ya no puede haberse configurado por encima de cien. **El 11-09-2026 el hueco se encogió pero NO se cerró**: la personalizada ya se ata a un producto y por tanto ya tiene tope individual, de modo que ninguna fila de la cadena puede pasarse por su cuenta — pero **la suma sigue sin comprobarse**, porque saber cuánto paga la cadena exige saber **quiénes la componen**, y eso no se sabe al configurar. `60 + 30 + 20` sigue pagando el 110 % aunque ninguno de los tres se pase por separado | **Crítica** |
| `RN-CM-012` | **No hay tarifa por omisión: sin tasa sobre el producto no se paga nada** | Siempre | **Reescrita por segunda vez el 16-09-2026.** Nació diciendo que ninguna tasa regía hasta asociarse; hoy ninguna se asocia (`RN-CM-021`) y lo que queda de ella es lo que siempre quiso decir: **una persona cobra por un producto solo si existe una tasa —suya o de su rol— sobre ese producto**. La ausencia significa «no se paga», nunca «se paga lo de todos». Lo que la regla costaba —una tasa creada y no asociada parecía configurada y no pagaba— **ya no puede ocurrir en ninguna de las dos clases** | **Crítica** |
| `RN-CM-013` | Un solo porcentaje por rol y producto | Al registrar una tasa de rol | Dos tasas del mismo rol sobre el mismo producto harían **indeterminada** la resolución, y la elección quedaría a criterio del plan de ejecución. Se declara en el esquema: **desde el 15-09-2026 en la propia `commission_rates`**, como índice único parcial sobre `(product_id, role_id)` entre las vivas; hasta entonces era la clave primaria de la asociación | **Crítica** |
| ~~`RN-CM-014`~~ | ~~Solo la personalizada se asocia a productos~~ | — | **Retirada el 16-09-2026.** Nació diciendo que solo las de rol se asociaban; el 11-09-2026, que las dos; el 15-09-2026, que solo la personalizada. Hoy **ninguna se asocia**: las dos nacen con su producto (`RN-CM-021`), y la tabla `user_commission_rate_products` se retira en `V10`. El número queda consumido | — |
| ~~`RN-CM-015`~~ | ~~Una tasa asociada no se retira~~ | — | **Retirada el 16-09-2026.** Existía porque la asociación **sobrevivía** al retiro de su tasa y el producto dejaba de pagar en silencio. Sin asociación no hay nada que sobreviva: retirar una tasa —de rol o personalizada— es exactamente la forma de que deje de aplicarse, a la vista, con motivo e instantánea (`RN-CM-005`). Salió de la de rol el 15-09-2026 y de la personalizada el 16-09-2026. El número queda consumido | — |
| `RN-CM-016` | **Una tasa declara una forma y solo una** | Al registrar y al corregir | O porcentaje o valor fijo, **nunca las dos ni ninguna**. No se suman. Se declara **en el esquema**: el tipo manda y el campo de la otra forma va vacío | **Crítica** |
| `RN-CM-017` | El valor fijo **no lleva moneda: es la de su producto** | Al registrar, al corregir y al liquidar | Toma la del **producto de la tasa**. La tasa no la declara porque no hace falta: desde que toda tasa nace con su producto (`RN-CM-021`) tiene **una sola moneda**, y el importe fijo **se valida contra sus decimales** al registrar y al corregir, igual que `RN-PM-007` valida un precio. Hasta el 15-09-2026 (rol) y el 16-09-2026 (personalizada) la misma fila podía pagar importes distintos en productos de monedas distintas; eso ya no puede pasar (§1.1.1) | Alta |
| ~~`RN-CM-018`~~ | ~~El valor fijo no está acotado por arriba mientras la tasa no conoce ningún precio~~ | — | **Retirada el 16-09-2026.** Decía que el importe, por su cuenta, no tenía un número que lo acotara —cien lo tiene el porcentaje— y que una tasa que no conocía el precio de nada quedaba sin tope. Hoy **ninguna tasa desconoce el precio de nada**: las dos nacen con su producto, y `RN-CM-019` y `RN-CM-020` las acotan al registrarlas. Lo que sigue siendo cierto —que el cuerpo de la petición no acota el importe por sí mismo— queda dicho en `RN-CM-019`. El número queda consumido | — |
| `RN-CM-019` | **Un producto no puede configurarse para pagar más del 100 % de sí mismo** | Al registrar o corregir cualquier tasa | La suma de lo que un producto paga a **todas** sus tasas de rol vivas —cada porcentaje tal cual, cada valor fijo convertido a `fixed_amount ÷ precio × 100`, contra el precio de **ese** producto— no puede superar cien. Se comprueba **al registrar** la tasa de rol (contando la nueva) y **al corregirla** (contra su único producto). **La tasa personalizada tiene su propio tope**, y es **individual y no una suma**: ninguna puede pagar más del 100 % del precio de su producto, y desde el 16-09-2026 se comprueba **al registrarla** y al corregirla. **No entra en la suma de las de rol**, y es deliberado: las personalizadas de personas distintas sobre el mismo producto son **alternativas entre sí**, no cosas que se paguen a la vez, y sumarlas rechazaría configuraciones legítimas. El tope se calcula contra el precio **de hoy**: si el producto cambia de precio después (`RF-PM-004`), nadie vuelve a comprobarlo. **Con precio CERO no aplica** (14-09-2026): un producto gratuito no tiene «cien por ciento» del que pasarse, y qué puede pagar lo dice `RN-CM-020`. **Entre el 08-09-2026 y el 14-09-2026 decía lo contrario** —que cualquier fijo mayor que cero sobre precio cero era «más del 100 %» y se rechazaba—, y se invirtió por decisión del responsable del proyecto (§5.2) | **Crítica** |
| `RN-CM-020` | **Un producto gratuito comisiona solo por importe fijo** | Al registrar o corregir cualquier tasa | Sobre un producto de **precio cero** (`RN-PM-006`) se admite **cualquier** tasa de **valor fijo**, **sin tope** —no hay cien por ciento de cero—, y **se rechaza toda tasa de porcentaje**: un porcentaje de nada es nada, y registrarlo configura algo que no paga. Se comprueba **al registrar** (`RF-CM-001` desde el 15-09-2026, `RF-CM-006` desde el 16-09-2026) y **al corregir** (`RF-CM-003`): corregir hacia porcentaje una tasa de un producto gratuito se rechaza entera, como el tope. **Se evalúa contra el precio de hoy y nadie vuelve a mirarlo** (§5.2): un porcentaje sobre un producto que después baja a cero **pasa a pagar cero**, y un fijo sobre un gratuito que después sube de precio **no se vuelve a acotar**. Es el mismo hueco temporal que `RN-CM-019` acepta, y se acepta por lo mismo | Alta |
| `RN-CM-021` | **Toda tasa nace con su producto, y no lo cambia** | Al registrar y en toda corrección de cualquier tasa | **Nace el 15-09-2026** para la de rol y **se extiende a la personalizada el 16-09-2026**, las dos veces por decisión del responsable del proyecto. Toda tasa declara **un producto**, obligatorio, y rige **solo sobre él** desde el alta. El producto **no se corrige**: cambiar de producto es retirar la tasa y registrar otra, porque lo que se pagó por el primero tiene que seguir resolviendo la misma fila. **Una tasa que se quiera repetir en varios productos son varias tasas** — es el precio de que cada producto se configure por su cuenta (§5.4, §5.5). La personalizada además tiene vigencia, y por eso su unicidad es «una **vigente** por persona y producto» (`RN-CM-006`) y no «una viva» | **Crítica** |

### 5.2 Por qué las críticas son críticas

**`RN-CM-004` — la precedencia.** Es lo que hace que las dos piezas signifiquen algo, y ahora es mucho más simple que antes: **una pregunta y una respuesta de reserva**. Vive **en un solo sitio** (`RF-CM-005`) y no en cada consumidor: reimplementar una comparación de precedencia es el defecto que devuelve resultados plausibles durante meses.

**`RN-CM-006` — una sola vigente por persona y producto, otra vez en el motor.** Es la que sostiene a `RN-CM-004`: con dos personalizadas de la misma persona cubriendo el mismo día **sobre el mismo producto**, la resolución deja de ser determinista. El 11-09-2026 **salió del motor** —el `EXCLUDE` cabía porque la persona, la vigencia y el producto vivían en la misma fila, y al pasar el producto a una tabla de asociación la regla cruzaba dos tablas— y pasó al caso de uso con un bloqueo consultivo por persona. **El 16-09-2026 vuelve**: el producto vuelve a la fila (`RN-CM-021`), y el `EXCLUDE` sobre `(user_id, product_id, daterange)` la declara donde ninguna omisión la burla. El caso de uso sigue comprobándola antes, pero solo para dar el mensaje; y el adaptador traduce la violación a `409` **por estado SQL y no por nombre**, porque una exclusión no lo trae — y son dos estados, el conflicto y el interbloqueo, que significan lo mismo: alguien llegó primero.

**`RN-CM-008` — el pasado depende de otro módulo.** Antes era una condición prudente; ahora es **lo único** que impide que cambiar una tasa reescriba lo ya pagado. Y ese otro módulo no existe: **hoy, cambiar un porcentaje borra el pasado y no queda forma de saberlo**.

**`RN-CM-012` — no hay tarifa por omisión.** El cambio de significado respecto al modelo del 28-08-2026 es total y hay que leerlo dos veces: **la ausencia de tasa sobre un producto significa «no se paga», nunca «se paga lo de todos»**. Del 01-09-2026 al 16-09-2026 costaba además que una tasa creada y no asociada pareciera configurada y no pagara nada; con las dos clases naciendo con su producto ese silencio ya no existe, y lo que queda de la regla es la frase de arriba.

**`RN-CM-013` — un porcentaje por rol y producto.** Sin ella, asociar dos veces el mismo rol al mismo producto produce dos respuestas válidas y **la base elige**. Se declara en el esquema y no en el caso de uso.

**`RN-CM-019` — el único tope que la aplicación calcula en lugar de heredar.** Es distinto de las demás reglas críticas del módulo porque exige una cuenta, no solo una comparación: sumar todas las hermanas de un producto y, para las que son valor fijo, convertirlas primero contra un precio que viene de otro módulo. Sin ella, asociar o corregir una tasa de rol podía dejar un producto pagando más de lo que cobra, y nadie lo veía hasta que existiera una liquidación que ya no existe. Con ella, ese caso concreto —el que no necesita ninguna tabla de ventas para detectarse— se cierra hoy; el caso general de la cadena, que sí necesita ver todas las filas de todos los niveles a la vez, sigue esperando esa liquidación (`RN-CM-011`).

!!! danger "El precio cero: una garantía de la que este módulo dependía por escrito, y que dejó de ser cierta"

    `ProductCommissionCapGuard` decía, en el Javadoc del método que hace la cuenta, que «el precio nunca es cero — `ck_products_price_positive` lo garantiza desde `V39`, y esta clase confía en esa garantía en lugar de defenderse de un estado que el propio esquema hace imposible».

    **El 08-09-2026 ese estado dejó de ser imposible**: `RN-PM-006` pasa de «mayor que cero» a «no negativo» para admitir la renovación de una membresía gratuita ([`requirements/pm.md` §5.2.4](pm.md)), y `V67` relaja la restricción que se citaba. Con un producto de precio cero, `fixed_amount ÷ precio` es una **división por cero**: un `500` en una comprobación de negocio.

    **Se resuelve sin regla nueva**, porque es lo que `RN-CM-019` ya dice llevado al límite: si el producto no cobra nada, **cualquier importe fijo mayor que cero es más del 100 % de lo que cobra**, y el rechazo es el mismo que el de cualquier otro exceso, con el mismo mensaje. Un importe fijo de cero ocupa cero por ciento y pasa.

    Lo que hay que leer de esto no es el arreglo: es que **una clase de `CM` dependía de una restricción de `PM`**, lo dijo por escrito, y aun así el cambio pudo llegar a producción sin que nada fallara al compilar. Lo que lo destapó fue leer el comentario, no una prueba.

!!! success "El precio cero, por segunda vez: el producto gratuito SÍ comisiona, y solo por importe fijo — 14-09-2026"

    **Decisión del responsable del proyecto**, que invierte lo que la caja de arriba resolvió el 08-09-2026. Aquel día el producto gratuito no podía pagar ningún importe fijo mayor que cero, porque `RN-CM-019` llevada al límite decía que cualquier importe era «más del 100 % de cero». Era aritméticamente impecable y comercialmente inútil: **un producto gratuito existe para captar**, y quien lo coloca cobra por colocarlo — con un importe, porque un porcentaje de cero es cero.

    **Nace `RN-CM-020`**, con tres respuestas preguntadas antes de escribir:

    1. **El porcentaje sobre un producto gratuito se rechaza**, en las dos clases de tasa y en las dos operaciones que comprueban el tope. Se descartó admitirlo pagando cero: dejaría configuraciones que no pagan sin que nadie lo dijera, que es exactamente el silencio que `RN-CM-012` existe para evitar.
    2. **El importe fijo sobre un producto gratuito no tiene tope.** Se descartó un tope propio porque no existe hoy ningún dato del que salga «cuánto puede pagar un producto gratis»; sería una columna nueva para una pregunta que nadie ha hecho.
    3. **Si el precio cambia después, nada**: `RN-CM-019` ya se calcula solo en el instante de asociar o corregir, y `RN-CM-020` hereda el mismo hueco a conciencia. Rechazar el cambio de precio desde `PM` invertiría la dependencia `CM → PM` y cerraría el ciclo que `modules.md` §7 prohíbe.

    **Lo que cambia en `ProductCommissionCapGuard`**: la rama del precio cero deja de devolver «más de cien» y pasa a **decidir por la forma** — porcentaje, rechazo con mensaje propio; fijo, paso sin sumar nada—. Y **el precio se lee siempre**, también cuando todas las tasas son de porcentaje: hasta hoy se ahorraba esa lectura cuando nada dividía, y ahora hace falta para saber si el producto es gratuito. **`CA-CM-115`, `CA-CM-116` y `CA-CM-117` se reescriben** en lugar de borrarse, para que quede escrito que la misma prueba dijo dos cosas opuestas en seis días y por qué.

**`RN-CM-015` — el retiro por la puerta de atrás, que ya no existe.** Fue la única regla del módulo que **no se dedujo del diseño sino de construirlo** (02-09-2026): la asociación sobrevivía al retiro de su tasa, la resolución filtraba las retiradas, y el resultado era un producto que **dejaba de pagar sin que nadie lo hubiera decidido**. Se retira el 16-09-2026 con la última asociación: sin fila que sobreviva, retirar es dejar de pagar a la vista.

**`RN-CM-021` — el producto es de la tasa, y la asociación desaparece.** Lo que la hace crítica es lo que deja sin red: hasta el 15-09-2026 (rol) y el 16-09-2026 (personalizada) una tasa podía existir sin pagar, y ponerla en vigor era un acto aparte que alguien podía olvidar; hoy **registrarla es ponerla en vigor**. El error posible cambia de signo —ya no es «configuré y no paga» sino «registré y paga desde ya»— y se acepta porque es el que se ve: cada tasa se lee junto a su producto en su listado (`RF-CM-002`). En la personalizada, «desde ya» es «desde su inicio de vigencia», que puede ser futuro.

### 5.3 Lo que este módulo NO decide, y lo que perdió al simplificarse

**Quién puede ver las comisiones de quién.** Es **alcance de datos**, depende de **D-22** —abierta, issue #28— y los requerimientos se especifican con **alcance global explícito**: quien tiene el permiso ve todo.

**Que la persona de una tasa personalizada sea vendedora.** El modelo anterior lo exigía —la tasa decía «esta persona, **en este rol**»— y con ello impedía que una excepción **sobreviviera a que la persona dejara de vender**. Al quitarle el rol (01-09-2026), **esa protección desaparece**: una tasa personalizada sigue viva aunque su titular pase a un rol que no comisiona. No falla — se queda callada hasta que alguien la mira.

**El tope de la cadena, parcialmente cerrado desde el 03-09-2026.** `RN-CM-011` reparte, `RN-CM-007` acota **cada porcentaje** a cien, y hasta el 02-09-2026 **nadie acotaba la suma**. `RN-CM-019` cierra el sub-caso que este módulo sí puede ver sin liquidación: cuando lo que paga un producto sale entero de tasas de rol asociadas a él, la suma no puede superar cien, y se comprueba **al configurar**, no al vender.

Lo que `RN-CM-019` **no** alcanza sigue siendo la suma de la cadena, aunque desde el 11-09-2026 por un motivo más estrecho. Hasta esa fecha una **tasa personalizada** no se ataba a ningún producto, de modo que quedaba fuera de cualquier cuenta que se pudiera hacer antes de la venta. Hoy nace con el suyo y **tiene su propio tope**: ninguna fila de la cadena puede pasarse por su cuenta. Lo que falta es sumarlas, y para eso hay que saber **quiénes componen la cadena** — un dato que no existe al configurar, solo al vender. `60 + 30 + 20` sigue pagando el 110 % aunque los tres sean legítimos por separado.

Con el valor fijo el agujero **cambió de tamaño y de forma** al volver en v0.7.0, y desde el 16-09-2026 se cierra del todo para lo que se puede cerrar antes de vender: **toda tasa conoce el precio de su producto desde el alta**, y el tope se comprueba ahí. Del 11-09-2026 al 16-09-2026 la frase era «sin asociar no hay tope», y antes la personalizada era la excepción permanente, porque nunca llegaba a atarse a nada.

**Y queda un hueco temporal que `RN-CM-019` acepta a conciencia, igual que el resto del módulo acepta los suyos**: el tope se calcula contra el precio del producto **en el instante de registrar o corregir**. Si el precio cambia después (`RF-PM-004`), nada vuelve a sumar — `PM` no conoce `CM`, y revisarlo en cada cambio de precio queda fuera de este alcance. La suma pudo ser válida el día que se configuró y dejar de serlo sin que nadie lo haya decidido.

**El único sitio donde se puede cerrar el resto —la cadena completa, con personalizadas incluidas, y el precio en el instante exacto de la venta— es la liquidación**, que no existe. Cuando exista, hereda **dos deudas y no una** para lo que `RN-CM-019` no llega a cubrir, y debe resolver las dos igual: **rechazar y no recortar** — recortar decidiría en silencio a quién se le quita.

---

### 5.4 La tasa de rol nace con su producto — 15-09-2026

**Decisión del responsable del proyecto**: «cambiaremos el funcionamiento de las comisiones generales: se les agregará el id del producto, es decir, solo se podrán crear desde el producto y solo para ese producto». Tres cosas se preguntaron antes de escribir, y las tres quedaron decididas por él:

| Pregunta | Decisión | Lo que se descartó, y por qué |
|---|---|---|
| **¿Por dónde se crea y se consulta?** | **Por la ruta de siempre**, `POST /api/v1/commission-rates`, con `productId` **obligatorio en el cuerpo**; y `GET /api/v1/commission-rates` lista **todas** las tasas de rol con su producto, filtrable por él | *Un recurso hijo del producto* (`/products/{id}/commission-rates`) — dejaba el listado repartido producto a producto, y lo que se quiere ver es **todas las comisiones que se han configurado**, en una sola lista |
| **¿Qué pasa con las tasas de rol que ya existen?** | **Se vacía y se empieza de cero** (`V94`): las tasas de rol y sus asociaciones se borran, y administración las vuelve a registrar desde cada producto | *Clonar una tasa por cada asociación y retirar las no asociadas* — conservaba lo que cada producto pagaba, y se descartó por lo mismo que `V49`: ninguna de las formas anteriores es exactamente la nueva, y una copia plausible pero decidida por una migración es peor que una lista vacía que administración rellena sabiendo lo que hace |
| **¿La personalizada cambia también?** | **No** ese día: siguió siendo de la persona, con vigencia, asociada a productos. **Duró un día**: el 16-09-2026 el responsable del proyecto decidió que también naciera con su producto (§5.5) | *Que también naciera desde el producto* — se descartó el 15-09-2026 por ser otro rediseño encima de este, y es lo que se hizo al día siguiente |

#### Lo que la decisión deshace, y lo que arrastra

**Deshace la simetría del 11-09-2026.** Aquel día las dos clases pasaron a asociarse «con el mismo mecanismo», y el argumento contra «el producto como columna de la tasa» fue que obligaba a aprender dos maneras de decir lo mismo. Hoy la tasa de rol adopta exactamente esa columna, y las dos clases **vuelven a decirlo de dos maneras**: la de rol es configuración **de un producto** —qué paga a cada rol—, y la personalizada es una excepción **de una persona** que puede abarcar varios. Se acepta porque son dos preguntas distintas, y porque lo que el responsable del proyecto quiere leer es la primera: **la lista de lo que cada producto paga**.

**Arrastra tres reglas y dos requerimientos.** `RN-CM-012` deja de alcanzar a la de rol; `RN-CM-013` pasa de la clave primaria de la asociación a un índice único de la propia tabla; `RN-CM-015` y `RN-CM-018` quedan solo para la personalizada; `RN-CM-019` y `RN-CM-020` se comprueban **al registrar** la de rol, porque ya conoce su producto; nace `RN-CM-021`. **`RF-CM-007` y `RF-CM-008` se descartan** —asociar y desasociar una tasa de rol— y sus números quedan consumidos; **`product_commission_rates` se retira** (`V94`), y con ella la clave foránea compuesta y `uq_commission_rates_id_role`, que existían solo para sostenerla.

**Y cierra una de las tres consecuencias del importe sin moneda.** `RN-CM-017` decía que el valor fijo toma la moneda del producto que se vende y que, por no declararla, «la misma fila paga cosas distintas según a qué producto se aplique». Para la de rol eso **ya no puede pasar**: tiene un solo producto, y por tanto una sola moneda, de modo que **el importe fijo se valida contra los decimales de esa moneda al registrarlo**, como `RN-PM-007` hace con el precio. La personalizada conserva la consecuencia, porque sigue sin producto hasta que se asocia.

!!! warning "Lo que ya no protege a nadie: la tasa de rol paga desde que se registra"

    Hasta hoy, registrar una tasa de rol no cambiaba lo que se pagaba; asociarla sí. Desde hoy **registrar es poner en vigor**. Quien registre una tasa «para probar» sobre un producto activo está configurando una comisión real. No hay borrador: lo que hay es el retiro (`RF-CM-004`), con motivo e instantánea.

### 5.5 La personalizada también nace con su producto — 16-09-2026

**Decisión del responsable del proyecto**: «modifica también las comisiones personalizadas para que también sea una sola comisión personalizada por usuario y producto». Un día antes se había decidido lo contrario (§5.4, tercera fila), y se preguntó una sola cosa antes de escribir:

| Pregunta | Decisión | Lo que se descartó, y por qué |
|---|---|---|
| **¿Qué pasa con la vigencia?** | **Se mantiene**: una vigente por persona, producto **y día**. Las fechas y el historial siguen; con el producto en la fila, `RN-CM-006` **vuelve al motor** como `EXCLUDE` sobre `(user_id, product_id, daterange)` | *Quitarla y dejarla exactamente como la de rol* —una viva por persona y producto, sin fechas—. Era más simple y perdía lo único que distingue a la excepción de una persona: que se negocia **por un periodo**, y que las consecutivas cuentan la historia |

**Lo que cambia, en una lista.** `user_commission_rates` gana `product_id`, obligatorio y sin corrección (`V10`); `user_commission_rate_products` **se borra**, y con ella asociar, desasociar y «los productos de una personalizada» de `RF-CM-006` y `RF-CM-002`; el listado de personalizadas **deja de contar** asociaciones y **dice cuál** es el producto, con su precio y su moneda, como el de rol; todo lo que se comprobaba al asociar —producto vivo (`RN-CM-002`, `RN-CM-010`), solapamiento (`RN-CM-006`), tope y gratuito (`RN-CM-019`, `RN-CM-020`)— se comprueba **al registrar**, y el importe fijo gana los decimales de la moneda del producto (`RN-CM-017`); el retiro pierde su condición (`RN-CM-015`); la resolución (`RF-CM-005`) lee `user_commission_rates.product_id` en vez de la asociación. **Las personalizadas que existían se borran** con su tabla de asociación, por lo mismo que las de rol en `V94`: ninguna tenía un producto honesto que ponerle — y el esquema se reconstruye desde cero ese mismo día.

**Lo que deshace.** El 15-09-2026 se aceptó a conciencia que las dos clases dijeran lo mismo de dos maneras; hoy vuelven a decirlo de una. Y **`RN-CM-006` recupera la garantía que perdió el 11-09-2026**: con la persona, el producto y la vigencia en la misma fila, el no solapamiento es otra vez una restricción del motor y no una comprobación que alguien puede olvidar. Es el único cambio de este rediseño que **devuelve** algo en lugar de quitarlo.

!!! warning "Registrar una personalizada es ponerla en vigor desde su inicio"

    Como en la de rol (§5.4): no hay paso de asociación ni borrador. Una personalizada registrada con `validFrom` de hoy o anterior **paga desde ya** sobre su producto; con `validFrom` futuro, desde ese día. Lo que hay es el retiro, con motivo e instantánea, o cerrar la vigencia corrigiendo `validTo`.

## 6. Permisos

| Código | Recurso | Acción | Para qué |
|---|---|---|---|
| `commissions:read` | `commissions` | `read` | Consultar tasas —de rol y personalizadas, cada una con su producto— y resolver la comisión efectiva |
| `commissions:create` | `commissions` | `create` | Registrar una tasa, de rol o personalizada, para un producto |
| `commissions:update` | `commissions` | `update` | Corregir un valor, o la vigencia de una personalizada |
| `commissions:delete` | `commissions` | `delete` | Retirar una tasa |

**Asociar reutilizaba `commissions:update` y no estrenó permiso propio** mientras existió (02-09-2026 a 16-09-2026), y la razón sigue valiendo para lo que queda: **registrar es poner en vigor** (`RN-CM-021`), y `commissions:create` cambia lo que se paga tanto como `commissions:update`. Separar «revisar tarifas» de «activarlas» tendría sentido el día que alguien deba poder lo uno sin lo otro.

---

## 7. Modelo de datos

### 7.1 `commission_rates` — las tasas de rol de cada producto

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `product_id` | `uuid` | No | `products` — **desde el 15-09-2026** (`V94`), y **no se corrige** (`RN-CM-021`) |
| `role_id` | `uuid` | No | `roles` |
| `rate_type` | `varchar(20)` | No | `PORCENTAJE` \| `FIJO` |
| `percentage` | `numeric(5,2)` | **Sí** | Presente solo si `rate_type = 'PORCENTAJE'` |
| `fixed_amount` | `numeric(14,4)` | **Sí** | Presente solo si `rate_type = 'FIJO'` |
| `created_at` | `timestamptz` | No | — |
| `updated_at` | `timestamptz` | No | — |
| `deleted_at` | `timestamptz` | **Sí** | Retiro lógico |

**Sin fechas de vigencia y sin persona, y CON producto desde el 15-09-2026.** Hasta esa fecha aquí solo vivía «qué gana un rol», y sobre qué lo ganaba se respondía en `product_commission_rates`; hoy la fila dice **qué paga este producto a este rol**, y no hay otra tabla que consultar. Fue el catálogo hasta `V94`, que lo vació —ninguna fila anterior tenía producto, y no había ninguno honesto que inventarle— y lo convirtió en configuración de productos (§5.4).

**`rate_type` existe aunque parezca deducible de qué columna está llena**, y es deliberado. Sin él, «una forma y solo una» sería una propiedad emergente de dos nulos, y un `CHECK` que la vigilara no podría decir **cuál** de las dos formas quiso declarar quien insertó una fila con las dos vacías. Con la columna, `RN-CM-016` se comprueba contra algo que el negocio declaró.

**`percentage` se declara `numeric(5,2)`** y `RN-CM-007` lo acota a `[0, 100]`. No se usa un entero de puntos básicos —que es la otra forma habitual— porque el dato que el negocio declara y lee es un porcentaje, y convertirlo en las dos direcciones es una fuente de errores de escala que ninguna prueba de camino feliz detecta.

**`fixed_amount` se declara `numeric(14,4)`, exactamente como `products.price`**, y la razón es más fuerte que la simetría. `products.price` tiene esa forma porque **la escala real la decide la moneda**: `currencies.decimal_places` va de 0 a 4, y `RN-PM-007` la valida en el dominio porque un `CHECK` no consulta otra tabla.

Un importe fijo de comisión **es dinero en la misma moneda que el producto** (`RN-CM-017`). Con menos decimales, una comisión en una moneda de cuatro no se podría expresar; con otra escala, la comparación que la liquidación tendrá que hacer —lo que se paga contra lo que se cobró— obligaría a redondear justo ahí, que es donde un redondeo se convierte en dinero.

**Lo que esto arrastra, y hay que decirlo:** `RN-PM-007` valida los decimales de un precio contra su moneda. **El valor fijo no puede validarse igual**, porque en el momento de declararlo **no se sabe en qué moneda se pagará** — depende del producto, que en el catálogo por rol todavía no está asociado y en la personalizada no existe. La coherencia entre decimales y moneda **queda sin comprobar**, y se suma a lo que hereda la liquidación.

**Y no lleva moneda** (`RN-CM-017`): la toma del producto que se vende. Las consecuencias, en §1.1.1.

### 7.2 `user_commission_rates` — la excepción por persona

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `user_id` | `uuid` | No | `users` |
| `product_id` | `uuid` | No | `products` — **desde el 16-09-2026** (`V10`), y **no se corrige** (`RN-CM-021`) |
| `rate_type` | `varchar(20)` | No | `PORCENTAJE` \| `FIJO` |
| `percentage` | `numeric(5,2)` | **Sí** | Presente solo si `rate_type = 'PORCENTAJE'` |
| `fixed_amount` | `numeric(14,4)` | **Sí** | Presente solo si `rate_type = 'FIJO'` |
| `valid_from` | `date` | No | — |
| `valid_to` | `date` | **Sí** | Nulo = indefinidamente |
| `created_at` | `timestamptz` | No | — |
| `updated_at` | `timestamptz` | No | — |
| `deleted_at` | `timestamptz` | **Sí** | Retiro lógico |

**Sin `role_id`**, por decisión del responsable del proyecto: la tasa es de la persona y no de la persona en un rol. Lo que eso cuesta está en §5.3.

**Y CON `product_id` desde el 16-09-2026** (§5.5): la fila dice **qué gana esta persona por este producto, y desde cuándo hasta cuándo**. Del 11-09-2026 al 16-09-2026 el producto vivía en `user_commission_rate_products`, y `V10` la borra con todas las personalizadas que había, por lo mismo que `V94` vació las de rol.

**La vigencia se mide en `date` y no en `timestamptz`**, por lo mismo que en la versión anterior: una comisión cambia «a partir del día 1», no a partir de las 00:00:00.000 de una zona horaria concreta, y declararla con instante obligaría a decidir en qué zona se corta el día — decisión que no tiene que tomar quien declara una tasa.

**Es la única tabla del módulo con vigencia**, y por tanto la única que conserva historial: sus filas cerradas dicen qué ganó esa persona y hasta cuándo.

**Lleva las dos formas, igual que el catálogo por rol**, por decisión del responsable del proyecto (02-09-2026): «esta persona gana 15.000 por venta» es tan negociable como un porcentaje, y la asimetría contraria habría habido que explicarla cada vez que alguien la encontrara.

**Y aquí el valor fijo tiene moneda desde el 16-09-2026, como en el catálogo**: la de su producto, y se valida contra sus decimales al registrar y al corregir (`RN-CM-017`). Hasta el 11-09-2026 esta tasa no se asociaba a nada y su importe se interpretaba en tantas monedas como productos hubiera; declarado en §1.1.1.

### 7.3 Las dos tablas de asociación, retiradas

**~~`product_commission_rates`~~ — retirada el 15-09-2026.** Existió desde `V49` (02-09-2026) hasta `V94` (15-09-2026): `(product_id, commission_rate_id, role_id copiado, created_at)`, con la clave primaria `(product_id, role_id)` sosteniendo `RN-CM-013` y una clave foránea compuesta que impedía que el rol copiado divergiera del de la tasa. **Dejó de hacer falta cuando la tasa de rol pasó a llevar su producto como columna** (`RN-CM-021`, §5.4): lo que esta tabla decía —qué tasa rige sobre qué producto— lo dice hoy `commission_rates.product_id`, y la unicidad por rol y producto vive en la propia tabla. `V94` la **borró**, y con ella `uq_commission_rates_id_role`, que existía solo para que su clave foránea compuesta pudiera apuntar ahí.

**~~`user_commission_rate_products`~~ — retirada el 16-09-2026.** Existió desde `V85` (11-09-2026) hasta `V10` del esquema consolidado (16-09-2026): `(user_commission_rate_id, product_id, created_at)`, con la pareja como clave primaria. Era la gemela de la anterior sin el rol copiado, y sobrevivió un día a su gemela «porque una excepción de una persona sí abarca varios productos» (§5.4); el 16-09-2026 el responsable del proyecto decidió que **también la personalizada fuera una por persona y producto** (§5.5), y lo que esta tabla decía lo dice hoy `user_commission_rates.product_id`. `V10` la **borra**, y con ella las personalizadas que había.

### 7.4 Restricciones exigidas en el esquema

| Restricción | Sobre | Regla que implementa |
|---|---|---|
| `ck_commission_rates_type` | `rate_type IN ('PORCENTAJE', 'FIJO')` | `RN-CM-016` |
| `ck_commission_rates_forma` | **Exactamente uno** de `percentage` y `fixed_amount` presente, y **el que corresponda al tipo** | `RN-CM-016`. Es la restricción nueva más fácil de escribir a medias: comprobar solo que **uno** esté presente admitiría una fila de tipo `FIJO` con el porcentaje lleno |
| `ck_commission_rates_percentage` | `percentage IS NULL OR (percentage >= 0 AND percentage <= 100)` | `RN-CM-007`. El cero **se admite**. La rama `IS NULL` va delante y explícita, porque ahora la columna **puede** estar vacía |
| `ck_commission_rates_fixed` | `fixed_amount IS NULL OR fixed_amount >= 0` | Solo acota **por abajo**. `RN-CM-018`: por arriba **no lo acota nada aquí**, y este `CHECK` no puede — evalúa una fila sola y `RN-CM-019` necesita sumar sus hermanas y leer el precio de otro módulo. Vive en el dominio |
| `fk_commission_rates_role` | `role_id` → `roles(id)` | `RN-CM-001` |
| `fk_commission_rates_product` | `product_id` → `products(id)`, **sin `ON DELETE`** | `RN-CM-002` (15-09-2026). El producto no se borra físicamente nunca (`RN-PM-010`) |
| `uq_commission_rates_product_role` | Índice único **parcial** sobre `(product_id, role_id)`, `WHERE deleted_at IS NULL` | `RN-CM-013` (15-09-2026). Parcial porque una tasa retirada no estorba a la que la sustituye; y **por parcial no admite `DEFERRABLE`**: dos altas simultáneas del mismo rol sobre el mismo producto muerden en el segundo `INSERT`, y el plan de `RF-CM-001` las traduce ahí en `409` |
| ~~`uq_commission_rates_id_role`~~ | ~~Único sobre `(id, role_id)`~~ | **Retirada en `V94`**: existía solo para la clave foránea compuesta de `product_commission_rates`, que ya no existe |
| `ck_user_commission_rates_type` | `rate_type IN ('PORCENTAJE', 'FIJO')` | `RN-CM-016` |
| `ck_user_commission_rates_forma` | **Exactamente uno**, y el que corresponda al tipo | `RN-CM-016`, igual que en el catálogo |
| `ck_user_commission_rates_percentage` | `percentage IS NULL OR (percentage >= 0 AND percentage <= 100)` | `RN-CM-007` |
| `ck_user_commission_rates_fixed` | `fixed_amount IS NULL OR fixed_amount >= 0` | `RN-CM-018` |
| `ck_user_commission_rates_vigencia` | `valid_to IS NULL OR valid_to >= valid_from` | `RN-CM-009`. La rama `IS NULL` va **delante y explícita**: un `CHECK` que evalúa a `NULL` **acepta** la fila |
| `uq_user_commission_rates_vigente` | `EXCLUDE USING gist` sobre `user_id`, **`product_id`** y `daterange(valid_from, valid_to, '[]')`, `WHERE deleted_at IS NULL` | `RN-CM-006`. **Es un `EXCLUDE` y no un `UNIQUE`** porque lo que no debe repetirse no es un valor sino un **intervalo**. Requiere `btree_gist`, ya declarada. **Existió sin el producto hasta `V85`** (11-09-2026), que la quitó porque la regla cruzaba dos tablas; **vuelve en `V10`** (16-09-2026) con el producto en la fila. Una violación de exclusión **no trae nombre de restricción**: el adaptador la traduce por estado SQL —`23P01`, y `40P01` cuando dos altas simultáneas se esperan la una a la otra— |
| `fk_user_commission_rates_user` | `user_id` → `users(id)` | §7.2 |
| `fk_user_commission_rates_product` | `product_id` → `products(id)`, **sin `ON DELETE`** | `RN-CM-002` (16-09-2026). El producto no se borra físicamente nunca (`RN-PM-010`) |
| ~~`pk_product_commission_rates`~~, ~~`fk_product_commission_rates_product`~~, ~~`fk_product_commission_rates_rate`~~ | ~~La asociación de la tasa de rol~~ | **Retiradas con la tabla en `V94`** (15-09-2026). `RN-CM-013` pasa a `uq_commission_rates_product_role` |
| ~~`pk_user_commission_rate_products`~~, ~~`fk_user_commission_rate_products_rate`~~, ~~`fk_user_commission_rate_products_product`~~ | ~~La asociación de la personalizada~~ | **Retiradas con la tabla en `V10`** (16-09-2026). `RN-CM-006` vuelve a `uq_user_commission_rates_vigente` |

**Lo que NO se puede declarar en el esquema, y por eso vive en el dominio:** que el rol sea de tipo `VENDEDOR` (`RN-CM-001`), que el producto no esté retirado (`RN-CM-010`), la precedencia de `RN-CM-004`, la suma del `RN-CM-019` y la forma admitida sobre un producto gratuito (`RN-CM-020`, que necesita el precio de `products`). Un `CHECK` no consulta otra tabla, y menos aún **suma** las filas que encuentra en ella — `RN-CM-019` además lee el precio de `PM`, que ninguna restricción de este esquema puede alcanzar.

!!! success "El no solapamiento vuelve a caber en una sola tabla — por segunda vez"

    `RN-CM-006` vivió en el motor del 28-08-2026 al 11-09-2026 sobre `(persona, rango)`; aquel día el producto salió a una tabla de asociación y el `EXCLUDE` habría tenido que **cruzar dos tablas**, cosa que ninguno hace, de modo que pasó al caso de uso con un bloqueo consultivo. **El 16-09-2026 el producto vuelve a la fila** (`RN-CM-021`, §5.5) y la restricción vuelve con él, ahora sobre `(persona, producto, rango)`.

    Para las de rol el problema desaparece por otro lado — sin fechas no hay solapamiento temporal, y el solapamiento de alcance lo cierra un **índice único parcial** sobre `(producto, rol)`.

---
## 8. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Creación del módulo `CM` con sus **cinco requerimientos** y **ocho reglas propias**. Registra los **cuatro grados** con los que se declara una comisión y su orden de precedencia, y deja fuera el **cálculo y la liquidación**, que no se aplazan por reparto sino porque no existe ninguna tabla de ventas sobre la que calcular. Nace con **dos condiciones declaradas hacia fuera**: `PM` deberá publicar una interfaz de lectura de productos que hoy no tiene, y la liquidación futura deberá **guardar el porcentaje que aplicó** en lugar de leerlo de la tarifa. Y con **una imposición sobre `SP`** que se registra allí: una persona no puede tener dos roles de tipo `VENDEDOR` (`RN-SP-025`). | Responsable del proyecto |
| 0.2.0 | 28-08-2026 | **Cuatro decisiones del responsable del proyecto, y una va contra la recomendación escrita.** (1) **La tarifa gana vigencia** —`valid_from` obligatorio y `valid_to` opcional—, y con ella la tabla deja de ser la foto de lo que se paga hoy para ser **el historial de lo que se pagó**: se puede reconstruir qué regía en cualquier fecha y programar un cambio con antelación. Se había recomendado no ponerla todavía, por no predecir cómo se liquidará; se pone. El precio es `RN-CM-006`, que pasa de «una viva por combinación» a **«ningún día cubierto dos veces»** — de una unicidad a una **exclusión**, que es otra restricción y otro índice, y la más difícil de declarar del módulo. Nace además `RN-CM-009`, y `RF-CM-003` pasa a corregir también el fin de vigencia. (2) **El cero pasa a ser un porcentaje válido**: era la única forma de exceptuar un producto a un rol con tarifa por omisión sin enumerar todos los demás. La consecuencia es que **«tarifa de cero» y «sin tarifa» dejan de ser lo mismo**, y `RF-CM-005` tiene que poder distinguirlas. (3) **La resolución es solo administrativa** por ahora: que un vendedor consulte la suya es otro actor y depende de D-22. (4) Nace **`RN-CM-010`**: no se registran tarifas nuevas sobre un producto retirado, y las que existían permanecen — el pasado se conserva y el futuro no se configura. | Responsable del proyecto |
| 0.3.0 | 01-09-2026 | **Se decide que la comisión es de override**, por decisión del responsable del proyecto: una venta comisiona a **toda la cadena** —el vendedor, su superior y el de este—, cada uno con **su propio porcentaje sobre el mismo importe**, y no solo a quien la hizo. Nace `RN-CM-011`. Este documento no lo decía **ni una vez** y `RF-CM-005` resuelve en singular, de modo que el multinivel estaba implícito en tener tarifas por rol y explícito en ninguna parte. **`RF-CM-005` no cambia**: se le llama una vez por nivel, que es exactamente lo que ya sabe responder — la resolución por persona resultó ser la pieza correcta sin tocarla. **Y la decisión destapa un agujero que ninguna regla de este documento cubría**: `RN-CM-007` acota **cada** porcentaje a cien, pero **la suma de la cadena no está acotada por nada**, de modo que `60 + 30 + 20` paga el 110 % de la venta y nada lo impide. El tope **no puede vivir aquí** —depende de tantas filas como niveles tenga la cadena, y esta tabla solo ve una— y **queda sin dueño hasta que exista quien aplique las tarifas**. Cuando lo tenga, debe **rechazar y no recortar**: recortar decidiría en silencio a quién se le quita.  | Responsable del proyecto |
| 0.4.0 | 01-09-2026 | **El módulo se rehace entero**, por decisión del responsable del proyecto, y el cambio **invalida la implementación**: `RF-CM-001` a `RF-CM-005` están construidos desde el 28-08-2026 con 45 pruebas, y este modelo cambia la forma de `commission_rates`. **Los cuatro grados desaparecen y quedan dos piezas que no se parecen**: un **catálogo de tasas por rol** —sin vigencia, sin producto y sin persona— y una **excepción por persona** con vigencia y sin rol. Tres tablas donde había una. **El producto sale a una tabla de asociación** (§7.3), porque una tasa rige sobre varios productos y un producto tiene una tasa por rol: meterlo como columna obligaría a duplicar la tasa una vez por producto y a corregir cincuenta filas al cambiar un porcentaje. **Y el significado de la ausencia se invierte** (`RN-CM-012`): antes una tasa sin producto valía para **todos**; ahora **no rige hasta que se la asocia**, de modo que una tasa creada y no asociada parece configurada y no paga nada. **La precedencia pasa de cuatro grados a dos**: la personalizada gana siempre y **sin mirar el producto**; si no la hay, la del rol asociada a ese producto. **`RN-CM-003` desaparece** —la personalizada ya no lleva rol— y con ella la protección que impedía que una excepción **sobreviviera a que la persona dejara de vender**; queda declarado en §5.3. **`RN-CM-006` se reduce a las personalizadas** y con eso el `EXCLUDE` vuelve a caber en una sola tabla, que era el problema que abría sacar el producto fuera; para las de rol el solapamiento lo cierra una **clave primaria** `(product_id, role_id)`. **El valor directo se aplaza**: obliga a decidir su moneda y **no está acotado por nada**, mientras que un porcentaje lo acota `RN-CM-007`. **Y la consecuencia más grave se acepta a conciencia**: sin vigencia en las tasas de rol, **corregir un porcentaje reescribe lo que rigió siempre**, de modo que `RN-CM-008` deja de ser una condición prudente y pasa a ser **la única defensa del pasado** — y como la liquidación no existe todavía, **hoy cambiar una tasa borra el pasado sin dejar rastro**. Tres requerimientos nuevos: `RF-CM-006` —la tasa personalizada, que deja de ser el mismo alta que la de rol— y `RF-CM-007` y `RF-CM-008`, asociar y desasociar, que son lo único que pone una tasa en vigor. | Responsable del proyecto |
| 0.5.0 | 02-09-2026 | **El módulo se construye entero sobre el modelo de v0.4.0**, y la implementación devuelve dos cosas que el diseño no había visto. `V49` reconstruye el esquema: vacía `commission_rates` —**ninguna de las cuatro formas anteriores tiene traducción**, y dejarlas caer a «tasa de rol» las habría convertido en filas plausibles y falsas—, le quita el producto, la persona y la vigencia, y crea `user_commission_rates` y `product_commission_rates`. Los ocho requerimientos quedan construidos y la suite pasa de **45 a 75 pruebas**. **Nace `RN-CM-015`, y es la única regla del módulo deducida de construirlo y no de diseñarlo**: una tasa asociada **no se retira**, porque la asociación no tiene retiro lógico y sobreviviría apuntando a una fila que la resolución ya no mira — el producto dejaría de comisionar **sin que nada lo dijera**, que es la silenciosidad de `RN-CM-012` llegando por la puerta de atrás. **Y la resolución resultó distinguir un caso que la prosa no nombraba**: quien **no porta rol vendedor pero tiene tasa personalizada viva** ahora **cobra** —la personalizada se consulta antes que el rol, tal como el diagrama de flujo la dibujaba—, de modo que lo que §5.3 llamaba «se queda callada» es en realidad «sigue pagando», y `roleId` puede llegar nulo junto a `RESUELTA`. **`RF-CM-006`, `RF-CM-007` y `RF-CM-008` se construyeron sin tripleta previa** (excepción al Art. I.1, §4): sin la asociación el catálogo no paga nada, y rehacer los cinco primeros sin ella habría dejado un módulo imposible de probar de punta a punta. | Responsable técnico |
| 0.6.0 | 02-09-2026 | **Las ocho tripletas quedan escritas** —cinco rehechas y tres de cero— y §4 lo recoge sin borrar la excepción al Art. I.1: lo que se invirtió fue el orden de las compuertas, y **eso no se deshace escribiendo el documento más tarde**. Redactarlas hizo visibles tres cosas que el código ya tenía y ningún documento decía. **La clave primaria de la asociación no incluye la tasa**, y solo por eso `RN-CM-013` se sostiene — con la tasa dentro, dos tasas distintas del mismo rol sobre el mismo producto cabrían las dos, y la resolución volvería a ser indeterminada. **`RF-CM-008` endurece `ck_deletion_reason`**: el esquema **exime** de motivo a las eliminaciones de asociación y ese caso de uso lo exige igual, porque al no quedar fila ese texto es la única constancia de que el producto pagaba a ese rol — y quitar la validación **no rompería ninguna restricción del motor**, de modo que la regla depende de que nadie la borre por parecer redundante. **Y `RF-CM-005` depende de dos reglas ajenas para ser determinista** —`RN-SP-025` y `RN-CM-006`—, ninguna de las cuales se comprueba allí; su plan las nombra para que quien las toque sepa qué se lleva por delante. | Responsable técnico |
| 0.7.0 | 02-09-2026 | **Vuelve el valor directo**, por decisión del responsable del proyecto, y con ello se **revierte el aplazamiento** que v0.4.0 había declarado. Cualquiera de las dos piezas —la tasa de rol y la personalizada— puede declararse **en porcentaje o en valor fijo**, nunca en las dos: **no se suman** (`RN-CM-016`), y el tipo se declara en una columna propia en lugar de deducirse de qué campo esté lleno — sin ella, una fila con los dos vacíos no permitiría saber **cuál** de las dos formas quiso declarar quien la insertó. **Y los dos motivos por los que se aplazó siguen siendo ciertos: se aceptan en lugar de resolverse.** (1) **La moneda**: el importe toma la del **producto que se vende** y la tasa no la declara (`RN-CM-017`), de modo que **la misma fila paga cosas distintas** en productos de monedas distintas — y en una personalizada, que no se asocia a nada, sobre **todo el catálogo**. Se descartó que la tasa llevara moneda propia con coincidencia exigida al asociar, porque **la personalizada no tiene producto con el que coincidir**. (2) **El tope**: `RN-CM-007` acota el porcentaje a cien y **nada acota el importe** (`RN-CM-018`). El agujero de v0.3.0 **cambia de tamaño**: antes hacían falta tres niveles para pasarse del importe de la venta, ahora **basta uno** — una tasa de 10.000 fijos sobre un producto de 8.000 paga más de lo que se cobró. **Y no puede vivir aquí**: una tasa no conoce el precio del producto, y la personalizada ni siquiera sabe sobre cuáles rige. La liquidación hereda ahora **dos deudas y no una**, y las dos se resuelven igual — **rechazar y no recortar**. `fixed_amount` se declara `numeric(14,4)`, la misma forma que `products.price`, porque la comparación entre lo que se paga y lo que se cobró es exactamente la que la liquidación tendrá que hacer, y escalas distintas obligarían a redondear justo ahí. | Responsable del proyecto |
| 0.8.0 | 03-09-2026 | **Nace `RN-CM-019`**, por decisión del responsable del proyecto: cierra el sub-caso del tope de cien que **sí** se puede comprobar sin liquidación —cuando lo que paga un producto sale entero de tasas de rol asociadas a él—, dejando explícito lo que sigue sin dueño. Se comprueba **al asociar** (`RF-CM-007`) y **al corregir** (`RF-CM-003`), en los dos casos sumando el porcentaje ocupado de cada tasa de rol asociada al producto —el valor fijo se convierte a `fixed_amount ÷ precio × 100` contra el precio de **ese** producto— y rechazando si la suma pasaría de cien; al corregir, se revisan **todos** los productos donde la tasa corregida está asociada y la corrección se rechaza entera si cualquiera se pasaría. **No cierra `RN-CM-011`**: una tasa **personalizada** no se ata a ningún producto y queda fuera de esta suma, de modo que una cadena con un nivel personalizado sigue pudiendo pasar de cien. **Se acepta a conciencia un hueco temporal nuevo**: el tope se calcula contra el precio de **hoy**, y si el producto cambia de precio después (`RF-PM-004`) nadie vuelve a comprobarlo, porque `PM` no conoce `CM`. **La suma agregada no se puede declarar en el esquema** —ningún `CHECK` ni `EXCLUDE` de Postgres suma filas hermanas ni lee el precio de otra tabla—, así que vive en el dominio, y la carrera que eso abre —dos asociaciones concurrentes al mismo producto, cada una dentro del tope por separado— se cierra con un bloqueo consultivo de Postgres por `product_id`, sin precedente hasta ahora en el módulo. **No se necesita migración**: no nace ninguna columna, la suma se calcula en cada comprobación. `ProductCatalog` gana `findPrice`, un método nuevo y no un cambio de `ProductView`, siguiendo la costumbre que la propia interfaz declaraba desde que `PM` la publicó: quien necesite el importe pide su propia lectura. | Responsable del proyecto |
| 0.9.0 | 08-09-2026 | **El precio de un producto puede ser cero, y este módulo dividía por él.** No es un cambio de `CM`: `RN-PM-006` pasa de «mayor que cero» a «no negativo» ([`requirements/pm.md`](pm.md) v0.21.0) para admitir la renovación de una membresía gratuita, y con ello se relaja la restricción que `ProductCommissionCapGuard` citaba **por escrito** como garantía de que la división `fixed_amount ÷ precio` era segura. `RN-CM-019` gana la resolución sin necesitar una regla nueva, porque es lo que ya decía llevado al límite: **sobre un producto que no cobra nada, cualquier valor fijo mayor que cero paga más del 100 % de sí mismo** y se rechaza con el mismo mensaje que cualquier otro exceso; uno de cero ocupa cero. §5.2 recoge el caso entero, y con él la lección que vale más que el arreglo: una clase de este módulo dependía de una restricción de otro, lo tenía escrito, y el cambio pudo llegar sin que nada fallara al compilar. | Responsable del proyecto |
| 0.10.0 | 11-09-2026 | **Corregida el mismo día por v0.11.0, y se conserva para que el cambio quede a la vista.** Declaró que la tasa personalizada dejaba de regir sobre todo el catálogo —eso se mantiene— y lo resolvió dándole una **columna `product_id` en la propia tasa**, fijada al crearla e inmutable. El responsable del proyecto corrigió **la forma, no el fondo**: la personalizada debe asociarse a productos **con el mismo mecanismo que la de rol**, no con uno propio. Lo construido ese día \(`V84`\) se deshace en `V85`. | Responsable del proyecto |
| 0.11.0 | 11-09-2026 | **La tasa personalizada se asocia a productos con EL MISMO MECANISMO que la de rol**, por decisión del responsable del proyecto, y con ello el módulo pasa a tener **una sola manera de decir sobre qué rige una tasa**. Nace `user_commission_rate_products` \(`V85`\), gemela de `product_commission_rates`: la tasa se crea primero y **se asocia después**, a uno o a varios productos, con su operación de **desasociar**. Se descarta la columna `product_id` de v0.10.0 — resolvía lo mismo y obligaba a aprender dos formas. **`RN-CM-012` deja de tener excepción**: ninguna tasa rige hasta que se asocia, y eso alcanza ya a la personalizada, que hasta hoy pagaba desde el primer día. **`RN-CM-015` también la alcanza**: una personalizada asociada no se retira sin desasociarla antes. **`RN-CM-018` queda en una sola frase para las dos clases**: sin asociar no hay tope; asociada, lo pone `RN-CM-019` contra el precio de ese producto. **Y `RN-CM-006` SALE DEL MOTOR**, que es lo que esto cuesta y el único movimiento del módulo en esa dirección: el `EXCLUDE` cabía porque persona, vigencia y producto vivían en una fila, y con la asociación fuera la regla **cruza dos tablas** — ningún índice hace eso. Pasa a comprobarse **al asociar**, con un **bloqueo consultivo por persona**, el mismo patrón que `RN-CM-019` ya usaba por producto. Se pierde la única garantía del módulo que no dependía de que alguien se acordara de comprobarla; se gana que las dos piezas se configuren igual. | Responsable del proyecto |
| 0.12.0 | 12-09-2026 | **La asociación de la personalizada se puede LEER** (`RF-CM-002` v1.1.0). El día anterior `RN-CM-014` le dio a la personalizada su tabla de asociación con sus dos escrituras y **sin ninguna lectura**: la única forma de saber sobre qué regía una excepción era la respuesta de asociarla. Lo destapó el responsable del proyecto al preguntar si existía un endpoint para consultar las comisiones personalizadas por producto. Entran tres cosas que calcan lo que la de rol ya tenía: `GET /api/v1/user-commission-rates` **filtra por `productId`** —combinable con persona y fecha— y **cuenta los productos asociados** de cada fila, con el cero significando «no paga nada» también para la excepción; y nace `GET /api/v1/user-commission-rates/{id}/products`, con la misma forma que devuelven asociar y desasociar. **La lectura por producto sigue devolviendo solo roles**, a propósito: mezclar filas con rol y con persona exige un discriminador que nadie necesita, y el filtro del listado responde lo mismo paginado. Sin cambio de esquema. Nacen `CA-CM-126` a `CA-CM-129`. | Responsable del proyecto |
| 0.13.0 | 14-09-2026 | **Un producto gratuito SÍ comisiona, y solo por importe fijo: nace `RN-CM-020`**, por decisión del responsable del proyecto, e invierte lo que v0.9.0 resolvió seis días antes. Entonces `RN-CM-019` llevada al límite rechazaba cualquier importe fijo mayor que cero sobre un producto de precio cero —«más del 100 % de cero»—; era aritméticamente impecable y comercialmente inútil, porque un producto gratuito existe para captar y quien lo coloca cobra por colocarlo. Tres respuestas preguntadas antes de escribir: **el porcentaje sobre un gratuito se rechaza** —un porcentaje de nada es nada, y admitirlo pagando cero dejaría configuraciones mudas—; **el importe fijo no tiene tope** —no hay cien por ciento de cero, y un tope propio sería una columna para una pregunta que nadie ha hecho—; y **si el precio cambia después, nada**, el mismo hueco temporal que `RN-CM-019` ya acepta. `RN-CM-019` deja de aplicar al precio cero y lo dice; `ProductCommissionCapGuard` decide por la forma en esa rama y **lee el precio siempre**. Enmienda las specs de `RF-CM-003`, `RF-CM-006` y `RF-CM-007` (Art. I.7): `CA-CM-115` a `CA-CM-117` se reescriben —la misma prueba dijo dos cosas opuestas en seis días, y queda escrito por qué— y nacen `CA-CM-130` a `CA-CM-135`. | Responsable del proyecto |
| 0.14.0 | 15-09-2026 | **La tasa de rol nace con su producto, y solo rige sobre él: nace `RN-CM-021`** (§5.4), por decisión del responsable del proyecto —«las comisiones generales llevarán el id del producto: solo se podrán crear desde el producto y solo para ese producto»— con tres respuestas preguntadas antes de escribir: **la ruta de siempre** con `productId` obligatorio en el cuerpo, y el listado con **todas** las tasas y su producto; **vaciar y empezar de cero** (`V94`), como `V49`; y **la personalizada no cambia**. `commission_rates` gana `product_id` obligatorio e inmutable, `fk_commission_rates_product` y `uq_commission_rates_product_role` (parcial); **`product_commission_rates` se retira** con su clave foránea compuesta y `uq_commission_rates_id_role`. **`RF-CM-007` y `RF-CM-008` se descartan**, números consumidos; `GET /product-commission-rates?productId=` se conserva sobre la tabla nueva. Reglas: `RN-CM-012` deja de alcanzar a la de rol; `RN-CM-013` vive en la propia tabla; `RN-CM-014` invertida por segunda vez —solo la personalizada se asocia—; `RN-CM-015` y `RN-CM-018` quedan para la personalizada; `RN-CM-019` y `RN-CM-020` se comprueban **al registrar** la de rol; y `RN-CM-017` cierra para la de rol una de sus tres consecuencias: el importe fijo **se valida contra los decimales de la moneda de su producto**. Lo que ya no protege a nadie queda escrito: **registrar una tasa de rol es ponerla en vigor**. | Responsable del proyecto |
| 0.14.1 | 15-09-2026 | **El producto de cada tasa de rol se lee con su precio y su moneda** (`RF-CM-002` v1.3.0), a petición del responsable del proyecto: quien mira «qué paga cada producto» necesita saber sobre qué precio y en qué moneda, porque un porcentaje es una parte del precio y un importe fijo es dinero en la moneda del producto. Sin cambio de reglas ni de esquema. | Responsable del proyecto |
| 0.15.0 | 16-09-2026 | **La personalizada también nace con su producto: `RN-CM-021` alcanza a las dos clases** (§5.5), por decisión del responsable del proyecto —«modifica también las comisiones personalizadas para que también sea una sola comisión personalizada por usuario y producto»—, con una respuesta preguntada: **la vigencia se mantiene** (una vigente por persona, producto y día). `user_commission_rates` gana `product_id` obligatorio e inmutable y **`user_commission_rate_products` se retira** con las personalizadas que había (`V10` del esquema consolidado). **`RN-CM-006` vuelve al motor** como `EXCLUDE` sobre `(user_id, product_id, daterange)`; **`RN-CM-014`, `RN-CM-015` y `RN-CM-018` se retiran** —ninguna tasa se asocia, ningún retiro tiene condición, ninguna tasa desconoce un precio—; `RN-CM-002`, `RN-CM-004`, `RN-CM-010`, `RN-CM-012`, `RN-CM-017`, `RN-CM-019` y `RN-CM-020` se reescriben para las dos clases; el submódulo Asociación desaparece (§2). `RF-CM-006` pierde asociar y desasociar; `RF-CM-002` pierde «los productos de una personalizada» y gana el producto —con precio y moneda— en cada fila; `RF-CM-003` corrige contra el producto de la tasa; `RF-CM-004` retira sin condición; `RF-CM-005` resuelve por `user_commission_rates.product_id`. | Responsable del proyecto |
