# Requerimientos del Módulo — `CM` Comisiones

| Campo | Valor |
|---|---|
| Módulo | `CM` — Comisiones |
| Paquete | `modules/commissions` |
| Prefijos de permiso | `commissions:` |
| Versión | 0.7.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 28-08-2026 |
| Última actualización | 02-09-2026 |

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

| | Qué es | Vigencia | ¿Se asocia a productos? |
|---|---|---|---|
| **Tasa de rol** | Un **catálogo**: «un `AGENTE` gana el 10 %» | **No tiene** | **Sí**, y solo rige por esa asociación |
| **Tasa personalizada** | «**esta persona** gana el 12 %» | **Sí**, y solo una vigente | **No** |

**La tasa de rol es catálogo y no configuración aplicada.** Existir no la pone en vigor: **rige únicamente sobre los productos a los que se la asocia** (`RN-CM-012`). Una tasa recién creada y sin asociar no paga nada a nadie, y esa es la diferencia con el modelo anterior, donde la ausencia de producto significaba «todos».

**La personalizada ignora el producto.** Quien tiene una gana lo mismo venda lo que venda — no se acota, no se asocia y **gana siempre sobre la de su rol** (`RN-CM-004`). Es una excepción, no un grado más.

**Y no lleva rol**, por decisión del responsable del proyecto: es de la persona y punto. Lo que eso cuesta está en §5.3.

### 1.1.1 Las dos formas de declarar una comisión

Desde el 02-09-2026, y por decisión del responsable del proyecto, **cualquiera de las dos piezas puede declararse de dos formas**:

| Forma | Qué dice | Qué la acota |
|---|---|---|
| **Porcentaje** | «gana el 10 % de la venta» | `RN-CM-007`: de cero a cien |
| **Valor fijo** | «gana 10.000 por venta» | **Nada.** Ver §5.3 |

**Una tasa declara una forma y solo una** (`RN-CM-016`). No se suman: no existe «5 % más 10.000». El tipo manda, y el campo de la otra forma va vacío.

!!! danger "El valor fijo no lleva moneda, y esa decisión tiene consecuencias que hay que aceptar a la vez"

    El importe **toma la moneda del producto que se está vendiendo** (`RN-CM-017`). La tasa no la declara.

    Lo que eso significa: **la misma fila paga cosas distintas según a qué producto se aplique.** Una tasa de «10.000 fijos» asociada a un producto en pesos y a otro en dólares no es un error del sistema — es exactamente lo que declara.

    **Y en la tasa personalizada el efecto es mayor**, porque no se asocia a nada: rige sobre **todos** los productos, de modo que su importe se interpreta en tantas monedas como haya en el catálogo.

    Se acepta a conciencia (§8, v0.7.0). Se descartó que la tasa declarara su propia moneda y se exigiera coincidencia al asociar, porque habría dejado la personalizada sin forma de expresarse — no tiene producto con el que coincidir.

### 1.2 Objetivo

Hoy el sistema sabe **qué se vende** (`PM`) y **quién vende** —los roles de tipo `VENDEDOR` y la estructura comercial de `SP`—, y **no sabe cuánto se le paga a quien vende**. Ese dato no existe en ningún sitio: ni un porcentaje, ni una excepción, ni un lugar donde declararlos. Este módulo pone ese objeto en el sistema, que es el paso sin el cual el cálculo de comisiones —cuando exista la venta— no tiene sobre qué operar.

### 1.3 Alcance

**Incluye**

- Registrar y mantener el **catálogo de tasas por rol**, **en porcentaje o en valor fijo**.
- **Asociar** una tasa de rol a un producto, que es lo único que la pone en vigor.
- Registrar la **tasa personalizada** de una persona, con su vigencia, **también en cualquiera de las dos formas**.
- Consultar unas y otras.
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
| Tasas | El catálogo por rol y las excepciones por persona | `commission_rates`, `user_commission_rates` |
| Asociación | Qué tasa rige sobre qué producto | `product_commission_rates` |
| Resolución | Qué le corresponde a una persona por un producto **en una fecha** | Las tres |

**Por qué la asociación es un submódulo y no un campo.** Porque una tasa de rol **rige sobre varios productos** y un producto **tiene una tasa por cada rol** de la cadena. Es una relación de muchos a muchos, y meterla como columna obligaría a duplicar la tasa una vez por producto — con el resultado previsible de que corregir un porcentaje exigiera corregir cincuenta filas y una se quedara atrás.

---

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| `SP` | Consume | **Roles** (`RN-CM-001`): validar que el rol existe y que es de tipo `VENDEDOR` |
| `SP` | Consume | **Usuarios**: validar que la persona de una tasa personalizada existe, y conocer su rol vendedor al resolver |
| `PM` | Consume | **Productos** (`RN-CM-002`): validar que el producto al que se asocia una tasa existe y no está retirado |

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
| `RF-CM-006` | Registrar la tasa personalizada de una persona | Tasas | `commissions:create` |
| `RF-CM-007` | Asociar una tasa de rol a un producto | Asociación | `commissions:update` |
| `RF-CM-008` | Retirar la asociación de una tasa con un producto | Asociación | `commissions:update` |

!!! success "Los ocho están construidos (02-09-2026)"

    `RF-CM-001` a `RF-CM-005` estaban implementados desde el 28-08-2026 con 45 pruebas, y este modelo cambió la forma de `commission_rates`. **Se rehicieron**, y con ellos nacieron los tres nuevos: `V48` reconstruye el esquema y la suite pasa de 45 a **75 pruebas**.

    **`RF-CM-003` y `RF-CM-004` valen para las dos clases de tasa** —la de rol y la personalizada—, cada una en su recurso. No son cuatro requerimientos porque corregir un porcentaje y retirar una tasa son la misma operación sobre dos tablas; lo que **sí** difiere está declarado: en la de rol corregir **borra el pasado**, y en la personalizada no.

    Los tres nuevos se construyeron **sin tripleta previa**, que es una excepción al Art. I.1: sin `RF-CM-007` el módulo entero no paga nada, de modo que rehacer los cinco primeros sin él habría dejado un `CM` que no se puede probar de punta a punta.

    **Las ocho tripletas quedaron escritas ese mismo día** —las cinco primeras rehechas, las tres nuevas de cero—, y **cada una declara en cabecera que se redactó después del código**. La excepción no se borra por haberla pagado: queda registrada aquí y en `requirements.md` v0.88.0, porque lo que se invirtió fue el orden de las compuertas del Art. I.6 y eso no se deshace escribiendo el documento más tarde.

**El alta se parte en dos** —`RF-CM-001` para el rol y `RF-CM-006` para la persona—, al revés que en la versión anterior, donde era una sola con campos opcionales. Ahora **no son la misma operación**: una escribe en un catálogo sin fechas y la otra registra una excepción con vigencia y con la exigencia de que no haya otra viva. Fundirlas obligaría a un endpoint cuyas validaciones dependen de qué campo llegó.

**La asociación tiene sus dos operaciones propias** porque es lo único que pone una tasa en vigor: sin `RF-CM-007` el catálogo entero no paga nada, y `RF-CM-008` es la única forma de dejar de pagar sin retirar la tasa.

---

## 5. Reglas de negocio

### 5.1 Catálogo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-CM-001` | Solo comisionan los roles vendedores | Al registrar una tasa de rol | El rol debe existir y ser de tipo **`VENDEDOR`** (`ck_roles_type`). Un rol funcionario o consumidor se rechaza | Crítica |
| `RN-CM-002` | El producto asociado debe existir | Al asociar | El producto debe existir en `PM`. Se declara además como clave foránea | Alta |
| `RN-CM-004` | **La personalizada gana siempre** | Al resolver | Si la persona tiene una tasa personalizada **vigente en la fecha**, es esa — **sin mirar el producto**. Si no la tiene, la que su **rol vendedor** tenga asociada a ese producto. Si no hay asociación, **no hay tarifa**. Dos niveles, no cuatro | **Crítica** |
| `RN-CM-005` | La tasa no desaparece | Al retirar | La eliminación es **lógica y con motivo** (Art. V.13). La fila permanece para que una liquidación pasada siga resolviendo con qué porcentaje se pagó | Crítica |
| `RN-CM-006` | Una sola tasa personalizada vigente por persona | Al registrar y al corregir una personalizada | **Ningún día puede estar cubierto por dos tasas personalizadas vivas de la misma persona.** Sí pueden existir varias consecutivas: son el historial. Se declara **en el motor** con un `EXCLUDE`, porque es la única regla del módulo que dos peticiones simultáneas pueden burlar | **Crítica** |
| `RN-CM-007` | El porcentaje va de cero a cien | Al registrar y al corregir **una tasa de porcentaje** | Se admite el **cero**, que significa «esto no comisiona» y **no es lo mismo que no tener tasa**: es la forma de asociar un producto a un rol declarando que no paga nada. **No dice nada del valor fijo**, que no está acotado por arriba | Alta |
| `RN-CM-008` | **La liquidación conserva el porcentaje, y es la única defensa del pasado** | Siempre | Las tasas de rol **no tienen vigencia**: corregir un porcentaje **reescribe lo que rigió siempre**. De modo que quien liquide **debe copiar el porcentaje que aplicó**, o cambiar una tasa reescribirá lo ya pagado sin dejar rastro. Es obligación de la liquidación futura, no de estas tablas (§1.4) | **Crítica** |
| `RN-CM-009` | Toda tasa personalizada declara desde cuándo rige | Al registrar una personalizada | El inicio de vigencia es **obligatorio**; el fin es opcional y su ausencia significa **indefinidamente**. Un fin anterior al inicio se rechaza. **Las de rol no llevan fechas** | Alta |
| `RN-CM-010` | No se configura lo que ya no se vende | Al asociar | No se admite asociar una tasa a un producto **retirado**: sería configurar algo que nadie puede vender. Las asociaciones que ya existían **permanecen**, por `RN-CM-005` | Media |
| `RN-CM-011` | Una venta comisiona a **toda la cadena** | Al liquidar | **Override**: cada persona de la cadena comercial gana **su propio porcentaje sobre el mismo importe**. La tasa se resuelve **una vez por nivel** con `RF-CM-005`. **El tope de la suma no vive aquí** —depende de tantas filas como niveles tenga la cadena, y este módulo solo ve una— y **queda sin dueño** hasta que exista quien aplique las tarifas: `60 + 30 + 20` paga el 110 % de la venta y nada lo impide | **Crítica** |
| `RN-CM-012` | Una tasa de rol **no rige hasta que se asocia** | Siempre | Existir en el catálogo no la pone en vigor. Sin asociación no paga nada a nadie, y **no hay tarifa por omisión del rol**: la ausencia ya no significa «todos los productos», significa «ninguno» | **Crítica** |
| `RN-CM-013` | Un solo porcentaje por rol y producto | Al asociar | Dos tasas del mismo rol sobre el mismo producto harían **indeterminada** la resolución, y la elección quedaría a criterio del plan de ejecución. Se declara en el esquema | **Crítica** |
| `RN-CM-014` | Solo las tasas de **rol** se asocian a productos | Al asociar | Una tasa personalizada **no se acota a un producto**: quien la tiene gana lo mismo venda lo que venda. El esquema lo impide porque la asociación apunta al catálogo de rol y las personalizadas viven en otra tabla | Alta |
| `RN-CM-015` | **Una tasa asociada no se retira** | Al retirar una tasa de rol | Si sigue asociada a algún producto, el retiro se **rechaza**: hay que desasociarla primero. La asociación **no tiene retiro lógico** y sobreviviría apuntando a una fila que la resolución ya no mira, de modo que **el producto dejaría de comisionar sin que nada lo dijera** | **Crítica** |
| `RN-CM-016` | **Una tasa declara una forma y solo una** | Al registrar y al corregir | O porcentaje o valor fijo, **nunca las dos ni ninguna**. No se suman. Se declara **en el esquema**: el tipo manda y el campo de la otra forma va vacío | **Crítica** |
| `RN-CM-017` | El valor fijo **no lleva moneda** | Al liquidar | Toma la del **producto que se vende**. La tasa no la declara, de modo que **la misma fila paga importes distintos** en productos de monedas distintas — y en una personalizada, sobre todo el catálogo. Consecuencia aceptada, no defecto (§1.1.1) | Alta |
| `RN-CM-018` | **El valor fijo no está acotado por arriba** | Siempre | `RN-CM-007` acota el porcentaje a cien; **nada acota el importe**. Una sola tasa fija puede superar el precio de la venta, sin necesidad de cadena. **Queda sin dueño**, junto al tope de `RN-CM-011` | **Crítica** |

### 5.2 Por qué las críticas son críticas

**`RN-CM-004` — la precedencia.** Es lo que hace que las dos piezas signifiquen algo, y ahora es mucho más simple que antes: **una pregunta y una respuesta de reserva**. Vive **en un solo sitio** (`RF-CM-005`) y no en cada consumidor: reimplementar una comparación de precedencia es el defecto que devuelve resultados plausibles durante meses.

**`RN-CM-006` — una sola vigente.** Es la que sostiene a `RN-CM-004`: con dos personalizadas cubriendo el mismo día, la resolución deja de ser determinista. **Y es la única regla del módulo que sigue en el motor**, porque es la única que dos peticiones simultáneas pueden burlar — es exactamente el defecto que `RN-SP-018` tuvo y que se corrigió el 26-08-2026.

**`RN-CM-008` — el pasado depende de otro módulo.** Antes era una condición prudente; ahora es **lo único** que impide que cambiar una tasa reescriba lo ya pagado. Y ese otro módulo no existe: **hoy, cambiar un porcentaje borra el pasado y no queda forma de saberlo**.

**`RN-CM-012` — el catálogo no es configuración.** El cambio de significado respecto al modelo anterior es total y hay que leerlo dos veces: **la ausencia de producto pasó de significar «todos» a significar «ninguno»**. Una tasa creada y no asociada parece configurada y no paga nada — y eso no falla: se descubre liquidando.

**`RN-CM-013` — un porcentaje por rol y producto.** Sin ella, asociar dos veces el mismo rol al mismo producto produce dos respuestas válidas y **la base elige**. Se declara en el esquema y no en el caso de uso.

**`RN-CM-015` — el retiro por la puerta de atrás.** Es la única regla del módulo que **no se dedujo del diseño sino de construirlo** (02-09-2026), y cubre la misma silenciosidad que `RN-CM-012` describe, llegando por otro camino: la asociación sobrevive al retiro de su tasa, la resolución filtra las retiradas, y el resultado es un producto que **deja de pagar sin que nadie lo haya decidido**. Las otras dos salidas eran peores — borrar las asociaciones en cascada destruye configuración que nadie pidió destruir. El coste es **dos operaciones donde había una**, y se paga a la vista.

### 5.3 Lo que este módulo NO decide, y lo que perdió al simplificarse

**Quién puede ver las comisiones de quién.** Es **alcance de datos**, depende de **D-22** —abierta, issue #28— y los requerimientos se especifican con **alcance global explícito**: quien tiene el permiso ve todo.

**Que la persona de una tasa personalizada sea vendedora.** El modelo anterior lo exigía —la tasa decía «esta persona, **en este rol**»— y con ello impedía que una excepción **sobreviviera a que la persona dejara de vender**. Al quitarle el rol (01-09-2026), **esa protección desaparece**: una tasa personalizada sigue viva aunque su titular pase a un rol que no comisiona. No falla — se queda callada hasta que alguien la mira.

**El tope de la cadena, y desde el 02-09-2026 también el de una sola tasa.** `RN-CM-011` reparte, `RN-CM-007` acota **cada porcentaje** a cien, y **nadie acota la suma**.

Con el valor fijo el agujero **cambia de tamaño y de forma**. Antes hacían falta tres niveles para pasarse del importe de la venta; ahora **basta uno**: una tasa de «10.000 fijos» sobre un producto de 8.000 paga más de lo que se cobró, y **ninguna regla lo impide** (`RN-CM-018`).

Y no puede impedirlo este módulo: **una tasa no conoce el precio del producto**. El catálogo por rol ni siquiera sabe sobre qué productos rige hasta que se lo asocian, y la personalizada rige sobre todos. **El único sitio donde se pueden comparar el importe de la venta y lo que se paga es la liquidación**, que no existe.

Cuando exista, hereda **dos deudas y no una**, y debe resolver las dos igual: **rechazar y no recortar** — recortar decidiría en silencio a quién se le quita.

---

## 6. Permisos

| Código | Recurso | Acción | Para qué |
|---|---|---|---|
| `commissions:read` | `commissions` | `read` | Consultar tasas, asociaciones y resolver la comisión efectiva |
| `commissions:create` | `commissions` | `create` | Registrar una tasa, de rol o personalizada |
| `commissions:update` | `commissions` | `update` | Corregir un porcentaje, y **asociar o desasociar** productos |
| `commissions:delete` | `commissions` | `delete` | Retirar una tasa |

**Asociar reutiliza `commissions:update` y no estrena permiso propio**, aunque sea lo único que pone una tasa en vigor. Es una decisión discutible y queda escrita: asociar **cambia lo que se paga** tanto como corregir un porcentaje, de modo que quien puede lo uno debería poder lo otro. Separarlos tendría sentido el día que alguien deba poder revisar tarifas sin poder activarlas.

---

## 7. Modelo de datos

### 7.1 `commission_rates` — el catálogo por rol

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `id` | `uuid` | No | — |
| `role_id` | `uuid` | No | `roles` |
| `rate_type` | `varchar(20)` | No | `PORCENTAJE` \| `FIJO` |
| `percentage` | `numeric(5,2)` | **Sí** | Presente solo si `rate_type = 'PORCENTAJE'` |
| `fixed_amount` | `numeric(14,4)` | **Sí** | Presente solo si `rate_type = 'FIJO'` |
| `created_at` | `timestamptz` | No | — |
| `updated_at` | `timestamptz` | No | — |
| `deleted_at` | `timestamptz` | **Sí** | Retiro lógico |

**Sin fechas de vigencia y sin producto ni persona.** Es lo que la distingue de la versión anterior: aquí solo vive **qué gana un rol**, y cuándo y sobre qué lo gana se responde en otra tabla o no se responde.

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
| `rate_type` | `varchar(20)` | No | `PORCENTAJE` \| `FIJO` |
| `percentage` | `numeric(5,2)` | **Sí** | Presente solo si `rate_type = 'PORCENTAJE'` |
| `fixed_amount` | `numeric(14,4)` | **Sí** | Presente solo si `rate_type = 'FIJO'` |
| `valid_from` | `date` | No | — |
| `valid_to` | `date` | **Sí** | Nulo = indefinidamente |
| `created_at` | `timestamptz` | No | — |
| `updated_at` | `timestamptz` | No | — |
| `deleted_at` | `timestamptz` | **Sí** | Retiro lógico |

**Sin `role_id`**, por decisión del responsable del proyecto: la tasa es de la persona y no de la persona en un rol. Lo que eso cuesta está en §5.3.

**La vigencia se mide en `date` y no en `timestamptz`**, por lo mismo que en la versión anterior: una comisión cambia «a partir del día 1», no a partir de las 00:00:00.000 de una zona horaria concreta, y declararla con instante obligaría a decidir en qué zona se corta el día — decisión que no tiene que tomar quien declara una tasa.

**Es la única tabla del módulo con vigencia**, y por tanto la única que conserva historial: sus filas cerradas dicen qué ganó esa persona y hasta cuándo.

**Lleva las dos formas, igual que el catálogo por rol**, por decisión del responsable del proyecto (02-09-2026): «esta persona gana 15.000 por venta» es tan negociable como un porcentaje, y la asimetría contraria habría habido que explicarla cada vez que alguien la encontrara.

**Y aquí el valor fijo sin moneda pesa más que en el catálogo**, porque esta tasa **no se asocia a nada**: rige sobre todos los productos, de modo que su importe se interpreta en tantas monedas como haya. Declarado en §1.1.1.

### 7.3 `product_commission_rates` — qué tasa rige sobre qué producto

| Columna | Tipo | Nula | Referencia |
|---|---|---|---|
| `product_id` | `uuid` | No | `products` |
| `commission_rate_id` | `uuid` | No | `commission_rates` |
| `role_id` | `uuid` | No | **Copiado** de la tasa |
| `created_at` | `timestamptz` | No | — |

**`role_id` está aquí a propósito, y no es la desnormalización que parece.** Existe para que `RN-CM-013` —un solo porcentaje por rol y producto— **pueda declararse en el esquema**: sin él, la unicidad tendría que unir dos tablas y ningún índice lo hace.

Y **no puede divergir**, porque la clave foránea es **compuesta**: `(commission_rate_id, role_id)` apunta a `commission_rates(id, role_id)`. Copiar un rol distinto del que la tasa declara es imposible, no improbable.

### 7.4 Restricciones exigidas en el esquema

| Restricción | Sobre | Regla que implementa |
|---|---|---|
| `ck_commission_rates_type` | `rate_type IN ('PORCENTAJE', 'FIJO')` | `RN-CM-016` |
| `ck_commission_rates_forma` | **Exactamente uno** de `percentage` y `fixed_amount` presente, y **el que corresponda al tipo** | `RN-CM-016`. Es la restricción nueva más fácil de escribir a medias: comprobar solo que **uno** esté presente admitiría una fila de tipo `FIJO` con el porcentaje lleno |
| `ck_commission_rates_percentage` | `percentage IS NULL OR (percentage >= 0 AND percentage <= 100)` | `RN-CM-007`. El cero **se admite**. La rama `IS NULL` va delante y explícita, porque ahora la columna **puede** estar vacía |
| `ck_commission_rates_fixed` | `fixed_amount IS NULL OR fixed_amount >= 0` | Solo acota **por abajo**. `RN-CM-018`: por arriba **no lo acota nada**, y no puede — esta tabla no conoce el precio del producto |
| `fk_commission_rates_role` | `role_id` → `roles(id)` | `RN-CM-001` |
| `uq_commission_rates_id_role` | Único sobre `(id, role_id)` | **Existe solo para que la clave foránea compuesta de §7.3 pueda apuntar ahí.** Es redundante con la clave primaria y esa es toda su función |
| `ck_user_commission_rates_type` | `rate_type IN ('PORCENTAJE', 'FIJO')` | `RN-CM-016` |
| `ck_user_commission_rates_forma` | **Exactamente uno**, y el que corresponda al tipo | `RN-CM-016`, igual que en el catálogo |
| `ck_user_commission_rates_percentage` | `percentage IS NULL OR (percentage >= 0 AND percentage <= 100)` | `RN-CM-007` |
| `ck_user_commission_rates_fixed` | `fixed_amount IS NULL OR fixed_amount >= 0` | `RN-CM-018` |
| `ck_user_commission_rates_vigencia` | `valid_to IS NULL OR valid_to >= valid_from` | `RN-CM-009`. La rama `IS NULL` va **delante y explícita**: un `CHECK` que evalúa a `NULL` **acepta** la fila |
| `uq_user_commission_rates_vigente` | `EXCLUDE USING gist` sobre `user_id` **y** `daterange(valid_from, valid_to, '[]')`, `WHERE deleted_at IS NULL` | `RN-CM-006`. **Es un `EXCLUDE` y no un `UNIQUE`** porque lo que no debe repetirse no es un valor sino un **intervalo**. Requiere `btree_gist`, ya declarada |
| `fk_user_commission_rates_user` | `user_id` → `users(id)` | §7.2 |
| `pk_product_commission_rates` | `(product_id, role_id)` | `RN-CM-013`. **La clave primaria ES la regla**: un solo porcentaje por rol y producto |
| `fk_product_commission_rates_product` | `product_id` → `products(id)` | `RN-CM-002` |
| `fk_product_commission_rates_rate` | **Compuesta**: `(commission_rate_id, role_id)` → `commission_rates(id, role_id)` | §7.3. Impide que el rol copiado diverja del que la tasa declara |

**Lo que NO se puede declarar en el esquema, y por eso vive en el dominio:** que el rol sea de tipo `VENDEDOR` (`RN-CM-001`), que el producto no esté retirado (`RN-CM-010`) y la precedencia de `RN-CM-004`. Un `CHECK` no consulta otra tabla.

!!! success "El no solapamiento vuelve a caber en una sola tabla"

    En el modelo anterior, `RN-CM-006` cubría `(rol, producto, persona)` más el rango de fechas, todo en una fila. Al sacar el producto a una tabla de asociación, ese `EXCLUDE` habría tenido que **cruzar dos tablas**, y ninguno lo hace.

    Lo resuelve que **solo las personalizadas tengan vigencia**: el intervalo y la persona viven juntos en `user_commission_rates`, y el `EXCLUDE` sigue en el motor. Para las de rol el problema desaparece por otro lado — sin fechas no hay solapamiento temporal, y el solapamiento de alcance lo cierra una **clave primaria**.

---
## 8. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Creación del módulo `CM` con sus **cinco requerimientos** y **ocho reglas propias**. Registra los **cuatro grados** con los que se declara una comisión y su orden de precedencia, y deja fuera el **cálculo y la liquidación**, que no se aplazan por reparto sino porque no existe ninguna tabla de ventas sobre la que calcular. Nace con **dos condiciones declaradas hacia fuera**: `PM` deberá publicar una interfaz de lectura de productos que hoy no tiene, y la liquidación futura deberá **guardar el porcentaje que aplicó** en lugar de leerlo de la tarifa. Y con **una imposición sobre `SP`** que se registra allí: una persona no puede tener dos roles de tipo `VENDEDOR` (`RN-SP-025`). | Responsable del proyecto |
| 0.2.0 | 28-08-2026 | **Cuatro decisiones del responsable del proyecto, y una va contra la recomendación escrita.** (1) **La tarifa gana vigencia** —`valid_from` obligatorio y `valid_to` opcional—, y con ella la tabla deja de ser la foto de lo que se paga hoy para ser **el historial de lo que se pagó**: se puede reconstruir qué regía en cualquier fecha y programar un cambio con antelación. Se había recomendado no ponerla todavía, por no predecir cómo se liquidará; se pone. El precio es `RN-CM-006`, que pasa de «una viva por combinación» a **«ningún día cubierto dos veces»** — de una unicidad a una **exclusión**, que es otra restricción y otro índice, y la más difícil de declarar del módulo. Nace además `RN-CM-009`, y `RF-CM-003` pasa a corregir también el fin de vigencia. (2) **El cero pasa a ser un porcentaje válido**: era la única forma de exceptuar un producto a un rol con tarifa por omisión sin enumerar todos los demás. La consecuencia es que **«tarifa de cero» y «sin tarifa» dejan de ser lo mismo**, y `RF-CM-005` tiene que poder distinguirlas. (3) **La resolución es solo administrativa** por ahora: que un vendedor consulte la suya es otro actor y depende de D-22. (4) Nace **`RN-CM-010`**: no se registran tarifas nuevas sobre un producto retirado, y las que existían permanecen — el pasado se conserva y el futuro no se configura. | Responsable del proyecto |
| 0.3.0 | 01-09-2026 | **Se decide que la comisión es de override**, por decisión del responsable del proyecto: una venta comisiona a **toda la cadena** —el vendedor, su superior y el de este—, cada uno con **su propio porcentaje sobre el mismo importe**, y no solo a quien la hizo. Nace `RN-CM-011`. Este documento no lo decía **ni una vez** y `RF-CM-005` resuelve en singular, de modo que el multinivel estaba implícito en tener tarifas por rol y explícito en ninguna parte. **`RF-CM-005` no cambia**: se le llama una vez por nivel, que es exactamente lo que ya sabe responder — la resolución por persona resultó ser la pieza correcta sin tocarla. **Y la decisión destapa un agujero que ninguna regla de este documento cubría**: `RN-CM-007` acota **cada** porcentaje a cien, pero **la suma de la cadena no está acotada por nada**, de modo que `60 + 30 + 20` paga el 110 % de la venta y nada lo impide. El tope **no puede vivir aquí** —depende de tantas filas como niveles tenga la cadena, y esta tabla solo ve una— y **queda sin dueño hasta que exista quien aplique las tarifas**. Cuando lo tenga, debe **rechazar y no recortar**: recortar decidiría en silencio a quién se le quita.  | Responsable del proyecto |
| 0.4.0 | 01-09-2026 | **El módulo se rehace entero**, por decisión del responsable del proyecto, y el cambio **invalida la implementación**: `RF-CM-001` a `RF-CM-005` están construidos desde el 28-08-2026 con 45 pruebas, y este modelo cambia la forma de `commission_rates`. **Los cuatro grados desaparecen y quedan dos piezas que no se parecen**: un **catálogo de tasas por rol** —sin vigencia, sin producto y sin persona— y una **excepción por persona** con vigencia y sin rol. Tres tablas donde había una. **El producto sale a una tabla de asociación** (§7.3), porque una tasa rige sobre varios productos y un producto tiene una tasa por rol: meterlo como columna obligaría a duplicar la tasa una vez por producto y a corregir cincuenta filas al cambiar un porcentaje. **Y el significado de la ausencia se invierte** (`RN-CM-012`): antes una tasa sin producto valía para **todos**; ahora **no rige hasta que se la asocia**, de modo que una tasa creada y no asociada parece configurada y no paga nada. **La precedencia pasa de cuatro grados a dos**: la personalizada gana siempre y **sin mirar el producto**; si no la hay, la del rol asociada a ese producto. **`RN-CM-003` desaparece** —la personalizada ya no lleva rol— y con ella la protección que impedía que una excepción **sobreviviera a que la persona dejara de vender**; queda declarado en §5.3. **`RN-CM-006` se reduce a las personalizadas** y con eso el `EXCLUDE` vuelve a caber en una sola tabla, que era el problema que abría sacar el producto fuera; para las de rol el solapamiento lo cierra una **clave primaria** `(product_id, role_id)`. **El valor directo se aplaza**: obliga a decidir su moneda y **no está acotado por nada**, mientras que un porcentaje lo acota `RN-CM-007`. **Y la consecuencia más grave se acepta a conciencia**: sin vigencia en las tasas de rol, **corregir un porcentaje reescribe lo que rigió siempre**, de modo que `RN-CM-008` deja de ser una condición prudente y pasa a ser **la única defensa del pasado** — y como la liquidación no existe todavía, **hoy cambiar una tasa borra el pasado sin dejar rastro**. Tres requerimientos nuevos: `RF-CM-006` —la tasa personalizada, que deja de ser el mismo alta que la de rol— y `RF-CM-007` y `RF-CM-008`, asociar y desasociar, que son lo único que pone una tasa en vigor. | Responsable del proyecto |
| 0.5.0 | 02-09-2026 | **El módulo se construye entero sobre el modelo de v0.4.0**, y la implementación devuelve dos cosas que el diseño no había visto. `V48` reconstruye el esquema: vacía `commission_rates` —**ninguna de las cuatro formas anteriores tiene traducción**, y dejarlas caer a «tasa de rol» las habría convertido en filas plausibles y falsas—, le quita el producto, la persona y la vigencia, y crea `user_commission_rates` y `product_commission_rates`. Los ocho requerimientos quedan construidos y la suite pasa de **45 a 75 pruebas**. **Nace `RN-CM-015`, y es la única regla del módulo deducida de construirlo y no de diseñarlo**: una tasa asociada **no se retira**, porque la asociación no tiene retiro lógico y sobreviviría apuntando a una fila que la resolución ya no mira — el producto dejaría de comisionar **sin que nada lo dijera**, que es la silenciosidad de `RN-CM-012` llegando por la puerta de atrás. **Y la resolución resultó distinguir un caso que la prosa no nombraba**: quien **no porta rol vendedor pero tiene tasa personalizada viva** ahora **cobra** —la personalizada se consulta antes que el rol, tal como el diagrama de flujo la dibujaba—, de modo que lo que §5.3 llamaba «se queda callada» es en realidad «sigue pagando», y `roleId` puede llegar nulo junto a `RESUELTA`. **`RF-CM-006`, `RF-CM-007` y `RF-CM-008` se construyeron sin tripleta previa** (excepción al Art. I.1, §4): sin la asociación el catálogo no paga nada, y rehacer los cinco primeros sin ella habría dejado un módulo imposible de probar de punta a punta. | Responsable técnico |
| 0.6.0 | 02-09-2026 | **Las ocho tripletas quedan escritas** —cinco rehechas y tres de cero— y §4 lo recoge sin borrar la excepción al Art. I.1: lo que se invirtió fue el orden de las compuertas, y **eso no se deshace escribiendo el documento más tarde**. Redactarlas hizo visibles tres cosas que el código ya tenía y ningún documento decía. **La clave primaria de la asociación no incluye la tasa**, y solo por eso `RN-CM-013` se sostiene — con la tasa dentro, dos tasas distintas del mismo rol sobre el mismo producto cabrían las dos, y la resolución volvería a ser indeterminada. **`RF-CM-008` endurece `ck_deletion_reason`**: el esquema **exime** de motivo a las eliminaciones de asociación y ese caso de uso lo exige igual, porque al no quedar fila ese texto es la única constancia de que el producto pagaba a ese rol — y quitar la validación **no rompería ninguna restricción del motor**, de modo que la regla depende de que nadie la borre por parecer redundante. **Y `RF-CM-005` depende de dos reglas ajenas para ser determinista** —`RN-SP-025` y `RN-CM-006`—, ninguna de las cuales se comprueba allí; su plan las nombra para que quien las toque sepa qué se lleva por delante. | Responsable técnico |
| 0.7.0 | 02-09-2026 | **Vuelve el valor directo**, por decisión del responsable del proyecto, y con ello se **revierte el aplazamiento** que v0.4.0 había declarado. Cualquiera de las dos piezas —la tasa de rol y la personalizada— puede declararse **en porcentaje o en valor fijo**, nunca en las dos: **no se suman** (`RN-CM-016`), y el tipo se declara en una columna propia en lugar de deducirse de qué campo esté lleno — sin ella, una fila con los dos vacíos no permitiría saber **cuál** de las dos formas quiso declarar quien la insertó. **Y los dos motivos por los que se aplazó siguen siendo ciertos: se aceptan en lugar de resolverse.** (1) **La moneda**: el importe toma la del **producto que se vende** y la tasa no la declara (`RN-CM-017`), de modo que **la misma fila paga cosas distintas** en productos de monedas distintas — y en una personalizada, que no se asocia a nada, sobre **todo el catálogo**. Se descartó que la tasa llevara moneda propia con coincidencia exigida al asociar, porque **la personalizada no tiene producto con el que coincidir**. (2) **El tope**: `RN-CM-007` acota el porcentaje a cien y **nada acota el importe** (`RN-CM-018`). El agujero de v0.3.0 **cambia de tamaño**: antes hacían falta tres niveles para pasarse del importe de la venta, ahora **basta uno** — una tasa de 10.000 fijos sobre un producto de 8.000 paga más de lo que se cobró. **Y no puede vivir aquí**: una tasa no conoce el precio del producto, y la personalizada ni siquiera sabe sobre cuáles rige. La liquidación hereda ahora **dos deudas y no una**, y las dos se resuelven igual — **rechazar y no recortar**. `fixed_amount` se declara `numeric(14,4)`, la misma forma que `products.price`, porque la comparación entre lo que se paga y lo que se cobró es exactamente la que la liquidación tendrá que hacer, y escalas distintas obligarían a redondear justo ahí. | Responsable del proyecto |
