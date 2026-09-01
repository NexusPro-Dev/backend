# Requerimientos del Módulo — `CM` Comisiones

| Campo | Valor |
|---|---|
| Módulo | `CM` — Comisiones |
| Paquete | `modules/commissions` |
| Prefijos de permiso | `commissions:` |
| Versión | 0.3.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 28-08-2026 |
| Última actualización | 28-08-2026 |

!!! info "Qué va en este documento"

    El catálogo de requerimientos del módulo: qué debe hacer, bajo qué reglas y con qué permisos.

    El comportamiento detallado de cada requerimiento —flujos, validaciones, criterios de aceptación y casos límite— vive en su tripleta, en `docs/specs/cm/`. Aquí no se repite.

!!! warning "Documento en Borrador: dos decisiones lo condicionan"

    1. **El código `CM`.** Un código, en cuanto aparece en un identificador, no se cambia jamás ([`modules.md` §2.1](../modules.md#21-regla-de-decision)). En cuanto exista `RF-CM-001`, estas dos letras quedan fijadas para siempre, y `modules.md` §6 advierte que los códigos de los módulos candidatos no deberían fijarse hasta conocer el alcance completo del producto. Se procede por decisión del responsable del proyecto, como ya se hizo con `PM`.
    2. **La frontera del alcance** (§1.3): este módulo **declara cuánto se paga**; no calcula, no liquida y no paga. El motivo, en §1.4.

---

## 1. Información del módulo

### 1.1 Descripción

`CM` es dueño de **cuánto se le paga a quien vende**. Una **tarifa de comisión** asocia un rol de tipo `VENDEDOR` con un porcentaje, opcionalmente acotada a un producto y opcionalmente acotada a una persona.

De ahí salen los cuatro grados de precisión con los que se puede declarar una comisión, del más general al más específico:

| Fila | Rol | Producto | Persona | Qué significa |
|---|---|---|---|---|
| Tarifa por omisión del rol | Sí | — | — | Lo que gana cualquiera con ese rol por cualquier producto |
| Tarifa del rol para un producto | Sí | Sí | — | Lo que gana cualquiera con ese rol **por ese producto** |
| Excepción de una persona | Sí | — | Sí | Lo que gana **esa persona** por cualquier producto |
| Excepción de una persona para un producto | Sí | Sí | Sí | El caso más específico |

**La ausencia es la que da el alcance, y por eso no hay un campo que diga «para todos»:** una tarifa sin persona es la de todos los de ese rol, y una sin producto es la de todo el catálogo. Un campo aparte podría contradecir a la clave —«para todos» con una persona declarada— y esa contradicción no la detecta nada.

**Y toda tarifa rige durante un periodo**, por decisión del responsable del proyecto: declara **desde cuándo** y opcionalmente **hasta cuándo**. Sin fin, rige indefinidamente. Eso convierte a esta tabla en el **historial de lo que se pagó**, y no solo en la foto de lo que se paga hoy: se puede reconstruir qué porcentaje regía en cualquier fecha pasada, y se puede **programar** un cambio con antelación en lugar de tener que hacerlo el día que entra en vigor.

**Cambiar una comisión no es corregirla, y la diferencia importa.** Corregir es arreglar lo que se declaró mal —un 12 donde iba un 2—: reescribe lo que esa tarifa dice que rigió. Cambiar la comisión a partir de una fecha es **cerrar la vigente y registrar otra**, y entonces las dos siguen contando su parte de la historia. Confundirlas es lo que borra el pasado sin querer.

### 1.2 Objetivo

Hoy el sistema sabe **qué se vende** (`PM`) y **quién vende** —los roles de tipo `VENDEDOR` y la estructura comercial de `SP`—, y **no sabe cuánto se le paga a quien vende**. Ese dato no existe en ningún sitio: ni un porcentaje, ni una excepción, ni un lugar donde declararlos. Este módulo pone ese objeto en el sistema, que es el paso sin el cual el cálculo de comisiones —cuando exista la venta— no tiene sobre qué operar.

### 1.3 Alcance

**Incluye**

- Registrar una tarifa de comisión, en cualquiera de los cuatro grados de §1.1, con su vigencia.
- Consultar las tarifas, con filtros por rol, producto, persona y fecha.
- Corregir el **porcentaje** y el **fin de vigencia** de una tarifa.
- Retirar una tarifa por eliminación lógica y con motivo.
- **Resolver la comisión efectiva**: dada una persona, un producto y una **fecha**, qué porcentaje le corresponde y **por qué tarifa**.

**No incluye**

- **El cálculo y la liquidación de comisiones**, que es la otra mitad del área ([`modules.md` §6](../modules.md#6-alcance-por-inventariar)). Y no se aplaza por reparto: **no hay sobre qué calcular**. Ver §1.4.
- **El pago de lo liquidado.** Retiros, balances y egresos son del área de **Finanzas**.
- **Los FTDs.** Pertenecen al área y dependen de la venta, que no existe.
- **Quién puede ver las comisiones de quién.** Es alcance de datos y depende de **D-22**, abierta. Ver §5.3.
- **La atribución de la venta.** A qué vendedor se le apunta una venta concreta es una decisión de la venta, no de la tarifa. **Desde el 01-09-2026 esa venta existe**: es un movimiento de `MV`, y lleva el vendedor **congelado** para que reasignar un cliente no cambie a quién se le pagó por una venta pasada.

### 1.4 La frontera, y por qué está donde está

**Una tarifa no calcula nada.** Declara un porcentaje; quien lo aplica es la liquidación, que no existe todavía. La tentación es cerrar el círculo aquí mismo —tarifa, cálculo y liquidación en un solo módulo— y hay dos razones para no hacerlo:

1. **No hay tabla de ventas.** Un cálculo de comisión necesita un importe vendido, una fecha y un vendedor atribuido. Ninguna de las tres cosas existe en el sistema. Escribir hoy el cálculo produciría código que no se puede probar contra nada real.
2. **Liquidar sin cobrar es pagar sobre una venta que no ocurrió.** Es el mismo argumento que `PM` §1.4 usó para no registrar la compra antes del cobro, y por el mismo motivo: produce un objeto que dice que alguien ganó algo cuando nadie verificó que se vendiera.

Lo que este documento sí deja resuelto es que **las tarifas estén diseñadas para esa continuación**, y en dos sentidos. La tarifa **no desaparece nunca** (`RN-CM-005`), de modo que una liquidación futura siempre podrá resolver con qué porcentaje se pagó. Y **cada liquidación guardará el porcentaje que aplicó**, en lugar de leerlo de la tarifa: es una condición que este módulo **impone a uno que todavía no existe**, porque sin ella corregir una tarifa pasaría a reescribir lo ya pagado. Es exactamente la condición que `PM` impuso con el precio y la vigencia.

---

## 2. Submódulos

Según [`modules.md` §5](../modules.md#5-fichas-de-modulo).

| Submódulo | Responsabilidad | Requerimientos |
|---|---|---|
| Tarifas | Alta, consulta, corrección y retiro de las tarifas | `RF-CM-001` a `RF-CM-004` |
| Resolución | Qué porcentaje le corresponde a una persona por un producto **en una fecha** | `RF-CM-005` |

**Por qué la resolución es un submódulo y no una consulta más.** Responde una pregunta distinta y con otra mecánica: el listado devuelve **filas tal como se declararon**; la resolución devuelve **una** tarifa que puede no existir como fila pensada para ese caso, elegida por el orden de precedencia de `RN-CM-004`. Separarlas evita el error que consiste en que cada consumidor reimplemente la precedencia por su cuenta — que es el defecto que `architecture.md` §15.2 llama «la regla se queda con su dueño».

---

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| `SP` | Consume | **Roles** (`RN-CM-001`): validar que el rol existe y que es de tipo `VENDEDOR` |
| `SP` | Consume | **Usuarios** (`RN-CM-003`): validar que la persona de una excepción existe, y conocer su rol vendedor al resolver |
| `PM` | Consume | **Productos** (`RN-CM-002`): validar que el producto al que se acota una tarifa existe |
| `SP` | Consume | Autorización, auditoría, paginación y jerarquía de errores, que son infraestructura compartida y no una dependencia de negocio |

La dependencia es **acíclica**: `CM` → `PM` → `SP`, y ninguno de los dos consume a `CM`. Es el **primer módulo del sistema que depende de dos**.

!!! info "`PM` tendrá que publicar una interfaz que hoy no tiene"

    La norma es la de **D-25** y no cambia por ser el tercer módulo: el dueño del dato publica **interfaces de aplicación de solo lectura** y el consumidor las importa ([`architecture.md` §15.2](../architecture.md#152-como-consume-un-modulo-los-datos-de-otro-cierre-de-d-25)). `SP` ya publica las suyas; `PM` **no publica ninguna**, porque hasta hoy nadie lo consumía.

    Esa ampliación de `PM` pertenece a los requerimientos de `CM` que la necesiten —`RF-CM-001` y `RF-CM-005`— y **no a un requerimiento nuevo de `PM`**: es el mismo reparto que se decidió al cerrar D-25, y por la misma razón, que ningún actor pide «publicar una interfaz» como comportamiento.

---

## 4. Requerimientos funcionales

| ID | Nombre | Submódulo | Permiso |
|---|---|---|---|
| `RF-CM-001` | Registrar una tarifa de comisión | Tarifas | `commissions:create` |
| `RF-CM-002` | Consultar las tarifas de comisión | Tarifas | `commissions:read` |
| `RF-CM-003` | Corregir una tarifa: su porcentaje y su fin de vigencia | Tarifas | `commissions:update` |
| `RF-CM-004` | Retirar una tarifa de comisión | Tarifas | `commissions:delete` |
| `RF-CM-005` | Consultar la comisión efectiva de una persona sobre un producto en una fecha | Resolución | `commissions:read` |

**Cinco y no seis: el alta es UNA, no dos.** Registrar la tarifa de un rol y registrar la excepción de una persona son el mismo caso de uso con un campo más, exactamente como `PM` decidió que registrar un upgrade y registrar un bot fueran un solo endpoint. Dos endpoints serían dos sitios donde la unicidad de `RN-CM-006` podría comprobarse distinto.

**No hay requerimiento para cambiar el rol, el producto o la persona de una tarifa.** Cambiarlos no corrige una tarifa: crea otra. Lo corregible es el **porcentaje** —«nos equivocamos al declararlo»— y el **fin de vigencia** —«esta tarifa deja de regir tal día»—, que es el mismo criterio con el que `RF-PM-004` dejó fuera el tipo y el código de un producto.

**Tampoco hay requerimiento para «cambiar la comisión a partir de una fecha»**, y no es un olvido: eso son **dos operaciones que ya existen** —cerrar la vigente con `RF-CM-003` y registrar la nueva con `RF-CM-001`—, y `RN-CM-006` obliga a hacerlas en ese orden porque no admite solapamiento. Un endpoint que hiciera las dos ahorraría una llamada y escondería que la primera es la que decide **hasta cuándo rigió lo anterior**, que es el dato que la liquidación va a leer.

---

## 5. Reglas de negocio

### 5.1 Catálogo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-CM-001` | Solo comisionan los roles vendedores | Al registrar | El rol de una tarifa debe existir y ser de tipo **`VENDEDOR`** (`ck_roles_type`). Un rol funcionario o consumidor se rechaza | Crítica |
| `RN-CM-002` | El producto acotado debe existir | Al registrar una tarifa con producto | El producto debe existir en `PM`. Se declara además como clave foránea | Alta |
| `RN-CM-003` | La persona de una excepción debe existir y portar el rol | Al registrar una tarifa con persona | La persona debe existir y **tener asignado el rol de la tarifa**. Sin esa comprobación, una excepción puede declararse sobre un rol que esa persona no ejerce, y no se aplicaría nunca | Crítica |
| `RN-CM-004` | Gana la tarifa más específica **vigente en la fecha** | Al resolver | Entre las que rigen esa fecha, el orden es **persona + producto**, luego **persona**, luego **rol + producto**, luego **rol**. La primera que exista es la que se aplica | Crítica |
| `RN-CM-005` | La tarifa no desaparece | Al retirar | La eliminación es **lógica y con motivo** (Art. V.13). La fila permanece para que una liquidación pasada siga resolviendo con qué porcentaje se pagó. **Retirar no es cerrar la vigencia**: se retira lo que no debió existir, se cierra lo que dejó de regir | Crítica |
| `RN-CM-006` | Dos tarifas del mismo caso no se solapan en el tiempo | Al registrar y al corregir la vigencia | Para una misma combinación de **rol, producto y persona** —contando la ausencia de cualquiera de los dos últimos como un valor más—, **ningún día puede estar cubierto por dos tarifas vivas**. Sí pueden existir varias consecutivas: son el historial | Crítica |
| `RN-CM-007` | El porcentaje va de cero a cien | Al registrar y al corregir | Se admite el **cero**, que significa «esto no comisiona» y **no es lo mismo que no tener tarifa**: es la única forma de exceptuar un producto a un rol que sí tiene tarifa por omisión. Por encima de cien se rechaza, porque pagaría más de lo vendido; por debajo de cero, porque no es una comisión | Alta |
| `RN-CM-008` | Corregir una tarifa no reescribe lo liquidado | Siempre | Corregir el porcentaje **reescribe lo que esa tarifa dice que rigió**. Lo ya liquidado conserva el porcentaje con el que se pagó, y garantizarlo es obligación de la liquidación futura, no de esta tabla (§1.4) | Crítica |
| `RN-CM-009` | Toda tarifa declara desde cuándo rige | Al registrar | El inicio de vigencia es **obligatorio**; el fin es opcional y su ausencia significa **indefinidamente**. Un fin anterior al inicio se rechaza | Alta |
| `RN-CM-011` | Una venta comisiona a **toda la cadena**, no solo a quien vendió | Al liquidar | **Override**, por decisión del responsable del proyecto (01-09-2026): cada persona de la cadena comercial —el vendedor, su superior y el de este— gana **su propio porcentaje sobre el mismo importe**. La tarifa se resuelve **una vez por nivel** con `RF-CM-005`, que no cambia: ya responde por persona. **Lo que esta regla trae y no estaba**: `RN-CM-007` acota **cada** porcentaje a cien, y **la suma de la cadena no está acotada por nada** — `60 + 30 + 20` paga el 110 % de la venta. El tope lo impone quien aplica (`RN-MV-022`), no esta tabla, porque depende de tantas filas como niveles tenga la cadena | **Crítica** |
| `RN-CM-010` | No se configura lo que ya no se vende | Al registrar una tarifa con producto | No se admite una tarifa **nueva** sobre un producto **retirado**: sería configurar algo que nadie puede vender. Las que ya existían **permanecen**, por `RN-CM-005` | Media |

### 5.2 Por qué las críticas son críticas

**`RN-CM-001` — solo los vendedores.** Sin esta regla, una tarifa puede colgarse de un rol administrativo o de `ESTUDIANTE`, y el defecto no se ve al declararla: se ve el día que la liquidación paga a quien no vende. La clasificación ya existe y es un dominio cerrado en el esquema, de modo que la regla es comprobable y no una convención.

**`RN-CM-003` — la persona porta el rol.** Es la mitad que se olvida. Una excepción es «esta persona, en este rol, cobra distinto»; si la persona no tiene ese rol, la fila **nunca se aplicará** y nadie se enterará, porque no falla: se queda callada. Es el mismo tipo de defecto que `RN-PM-002` evita en su segunda mitad — no falla, promete.

**`RN-CM-004` — la precedencia.** Es la regla que hace que los cuatro grados de §1.1 signifiquen algo. Vive **en un solo sitio** (`RF-CM-005`) y no en cada consumidor: reimplementar una comparación de precedencia es el defecto que devuelve resultados plausibles durante meses, que es exactamente lo que `architecture.md` §15.2 previene al exigir que la regla se quede con su dueño.

**`RN-CM-005` — la tarifa no desaparece.** Lo mismo que `RN-PM-010` para el producto, y por lo mismo: lo que se pagó tiene que seguir explicándose.

**`RN-CM-006` — sin solapamiento.** Es la regla que sostiene a `RN-CM-004`: si dos tarifas del mismo caso cubrieran el mismo día, la resolución dejaría de ser determinista y la elección quedaría a criterio del plan de ejecución. **Y es la más difícil de declarar de todo el módulo**, por dos motivos que se suman:

1. **En PostgreSQL dos `NULL` no son iguales**, de modo que un `UNIQUE` corriente admitiría dos veces la misma tarifa por omisión — el producto y la persona son nulables por diseño (§1.1).
2. **Lo que no debe repetirse no es un valor, es un intervalo.** «No dos iguales» es una unicidad; «ningún día cubierto dos veces» es una **exclusión**, que es otra restricción y otro índice.

La salida previsible es una restricción `EXCLUDE` con `btree_gist` sobre la combinación y el rango de fechas, normalizando las ausencias. El proyecto ya declara extensiones en `V1` —`unaccent` y `pg_trgm`—, así que no es un precedente nuevo. **La forma concreta la decide el `plan.md`**, y lo que este documento fija es que **tiene que estar en el motor**: comprobarlo solo en el caso de uso lo dejaría a merced de dos peticiones simultáneas, que es el defecto que `RN-SP-018` ya tuvo.

**`RN-CM-007` — el cero es un valor, no la ausencia.** Sin él quedaba un hueco real: un rol con 10% por omisión no podía exceptuar un producto sin enumerar todos los demás. Con él, **«tarifa de cero» y «sin tarifa» dejan de ser lo mismo** y hay que tratarlas distinto en la resolución: la primera es una respuesta —no comisiona—, la segunda es la ausencia de respuesta, y `RF-CM-005` tiene que poder decir cuál de las dos ocurrió.

**`RN-CM-010` — no se configura lo que ya no se vende.** Es la mitad prohibitiva; la permisiva es `RN-CM-005`, que conserva las tarifas que ya existían. Las dos juntas dicen lo mismo desde los dos lados: **el pasado se conserva y el futuro no se configura**.

**`RN-CM-008` — no reescribir lo liquidado.** Es la condición que este módulo impone al que todavía no existe. Se escribe hoy porque el día que se escriba la liquidación será tarde: quien la construya leerá el porcentaje de la tarifa si nadie le dijo que no.

### 5.3 Lo que este módulo NO decide, y por qué

**Quién puede ver las comisiones de quién.** Un manager que consulta las tarifas ¿ve las de su equipo, las de todos, o solo la suya? Es **alcance de datos**, es el eje ortogonal al permiso, y depende de **D-22**, que sigue abierta ([`security.md` §12](../security.md#12), issue #28). Los cinco requerimientos se especifican con **alcance global explícito** —quien tiene el permiso ve todo— y quedan en la lista de los que hay que revisar el día que D-22 se cierre.

**Esto no es una excepción a la advertencia de `ADR-005`**, que dice que los requerimientos de comisiones no deberían especificarse antes de resolver el alcance. Se procede porque lo que aquí se especifica es **la tarifa como dato de configuración**, no quién la ve; y queda declarado que **la consulta** (`RF-CM-002`) es la que puede tener que cambiar, no la tabla.

---

## 6. Permisos

| Código | Recurso | Acción | Para qué |
|---|---|---|---|
| `commissions:read` | `commissions` | `read` | Consultar las tarifas y resolver la comisión efectiva |
| `commissions:create` | `commissions` | `create` | Registrar una tarifa |
| `commissions:update` | `commissions` | `update` | Corregir el porcentaje |
| `commissions:delete` | `commissions` | `delete` | Retirar una tarifa |

Cuatro permisos y no uno por grado: el grado —rol, producto, persona— es un dato de la tarifa, no una operación distinta. Distinguirlo en el permiso obligaría a mantener sincronizados el modelo de permisos y la forma de la tabla.

---

## 7. Modelo de datos

### 7.1 `commission_rates`

| Columna | Tipo | PK | FK | Nula | Por omisión | Referencia |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | UUID v7 | — |
| `role_id` | `uuid` | No | Sí | No | — | `roles` |
| `product_id` | `uuid` | No | Sí | **Sí** | — | `products` |
| `user_id` | `uuid` | No | Sí | **Sí** | — | `users` |
| `percentage` | `numeric(5,2)` | No | No | No | — | — |
| `valid_from` | `date` | No | No | No | — | — |
| `valid_to` | `date` | No | No | **Sí** | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

**`role_id` es obligatorio incluso en una excepción de persona**, y no es redundante: la tarifa dice «esta persona, **en este rol**, cobra esto». Sin el rol, una excepción sobreviviría a que la persona dejara de ser vendedora y seguiría aplicándose. Con él, `RN-CM-003` es comprobable.

**`percentage` se declara `numeric(5,2)`**: hasta `999.99` por la precisión, y `RN-CM-007` lo acota a `(0, 100]`. No se usa un entero de puntos básicos —que es la otra forma habitual— porque el dato que el negocio declara y lee es un porcentaje, y convertirlo en las dos direcciones es una fuente de errores de escala que ninguna prueba de camino feliz detecta.

**La vigencia se mide en `date` y no en `timestamptz`.** Una comisión cambia «a partir del día 1», no a partir de las 00:00:00.000 de una zona horaria concreta; declararla con instante obligaría a decidir en qué zona se corta el día, y esa decisión no la tiene que tomar quien declara una tarifa. Es la excepción justificada al criterio general del proyecto, que persiste instantes con zona.

**`valid_to` nulo significa «indefinidamente», no «se desconoce».** Es el estado normal de la tarifa que rige hoy.

**Sin columna de estado.** Una tarifa está viva o retirada, y eso lo dice `deleted_at`. No hay un caso intermedio como el de `products`, donde `INACTIVO` significa «existe y no se ofrece». **Y una tarifa vencida tampoco es un estado**: es una fila con `valid_to` en el pasado, que sigue viva porque sigue explicando lo que se pagó entonces.

### 7.2 Restricciones exigidas en el esquema

| Restricción | Sobre | Regla que implementa |
|---|---|---|
| `ck_commission_rates_percentage` | `percentage >= 0 AND percentage <= 100` | `RN-CM-007`. El cero **se admite**: es «no comisiona», y no lo mismo que no tener tarifa |
| `ck_commission_rates_vigencia` | `valid_to IS NULL OR valid_to >= valid_from` | `RN-CM-009`. La rama `IS NULL` va **delante y explícita**: un `CHECK` que evalúa a `NULL` **acepta** la fila |
| `fk_commission_rates_role` | `role_id` → `roles(id)` | `RN-CM-001` |
| `fk_commission_rates_product` | `product_id` → `products(id)` | `RN-CM-002` |
| `fk_commission_rates_user` | `user_id` → `users(id)` | `RN-CM-003` |
| No solapamiento de `RN-CM-006` | Rol, producto y persona —con la ausencia contando como valor— **y el rango de fechas** | `RN-CM-006`. **Tiene que estar en el motor**, y la forma concreta la decide el `plan.md`: la salida previsible es un `EXCLUDE` con `btree_gist`, porque lo que no debe repetirse no es un valor sino un **intervalo**, y porque un `UNIQUE` corriente admitiría dos tarifas por omisión idénticas —en PostgreSQL dos `NULL` no son iguales— |

**Lo que NO se puede declarar en el esquema, y por eso vive en el dominio:** que el rol sea de tipo `VENDEDOR` (`RN-CM-001`), que la persona porte ese rol (`RN-CM-003`) y que el producto no esté retirado (`RN-CM-010`). Un `CHECK` no consulta otra tabla — el mismo límite que `PM` encontró con los decimales de la moneda.

**Por qué el no solapamiento sí y las otras tres no.** No es incoherencia: `RN-CM-006` se puede declarar porque solo mira **esta** tabla, y **debe** declararse porque es la única de las cuatro que dos peticiones simultáneas pueden burlar — es exactamente el defecto que `RN-SP-018` tuvo y que se corrigió el 26-08-2026. Las otras tres miran filas de otras tablas que no cambian durante la operación.

---

## 8. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Creación del módulo `CM` con sus **cinco requerimientos** y **ocho reglas propias**. Registra los **cuatro grados** con los que se declara una comisión y su orden de precedencia, y deja fuera el **cálculo y la liquidación**, que no se aplazan por reparto sino porque no existe ninguna tabla de ventas sobre la que calcular. Nace con **dos condiciones declaradas hacia fuera**: `PM` deberá publicar una interfaz de lectura de productos que hoy no tiene, y la liquidación futura deberá **guardar el porcentaje que aplicó** en lugar de leerlo de la tarifa. Y con **una imposición sobre `SP`** que se registra allí: una persona no puede tener dos roles de tipo `VENDEDOR` (`RN-SP-025`). | Responsable del proyecto |
| 0.2.0 | 28-08-2026 | **Cuatro decisiones del responsable del proyecto, y una va contra la recomendación escrita.** (1) **La tarifa gana vigencia** —`valid_from` obligatorio y `valid_to` opcional—, y con ella la tabla deja de ser la foto de lo que se paga hoy para ser **el historial de lo que se pagó**: se puede reconstruir qué regía en cualquier fecha y programar un cambio con antelación. Se había recomendado no ponerla todavía, por no predecir cómo se liquidará; se pone. El precio es `RN-CM-006`, que pasa de «una viva por combinación» a **«ningún día cubierto dos veces»** — de una unicidad a una **exclusión**, que es otra restricción y otro índice, y la más difícil de declarar del módulo. Nace además `RN-CM-009`, y `RF-CM-003` pasa a corregir también el fin de vigencia. (2) **El cero pasa a ser un porcentaje válido**: era la única forma de exceptuar un producto a un rol con tarifa por omisión sin enumerar todos los demás. La consecuencia es que **«tarifa de cero» y «sin tarifa» dejan de ser lo mismo**, y `RF-CM-005` tiene que poder distinguirlas. (3) **La resolución es solo administrativa** por ahora: que un vendedor consulte la suya es otro actor y depende de D-22. (4) Nace **`RN-CM-010`**: no se registran tarifas nuevas sobre un producto retirado, y las que existían permanecen — el pasado se conserva y el futuro no se configura. | Responsable del proyecto |
| 0.3.0 | 01-09-2026 | **Se decide que la comisión es de override**, por decisión del responsable del proyecto: una venta comisiona a **toda la cadena** —el vendedor, su superior y el de este—, cada uno con **su propio porcentaje sobre el mismo importe**, y no solo a quien la hizo. Nace `RN-CM-011`. Este documento no lo decía **ni una vez** y `RF-CM-005` resuelve en singular, de modo que el multinivel estaba implícito en tener tarifas por rol y explícito en ninguna parte. **`RF-CM-005` no cambia**: se le llama una vez por nivel, que es exactamente lo que ya sabe responder — la resolución por persona resultó ser la pieza correcta sin tocarla. **Y la decisión destapa un agujero que ninguna regla de este documento cubría**: `RN-CM-007` acota **cada** porcentaje a cien, pero **la suma de la cadena no está acotada por nada**, de modo que `60 + 30 + 20` paga el 110 % de la venta y nada lo impide. El tope no puede vivir aquí —depende de tantas filas como niveles tenga la cadena, y esta tabla solo ve una— y lo impone quien aplica: `RN-MV-022`, que **rechaza y no recorta**, porque recortar decidiría en silencio a quién se le quita. §5.3 se actualiza: la **atribución de la venta** ya no es una decisión pendiente de un módulo inexistente — es un movimiento de `MV` con el vendedor congelado. | Responsable del proyecto |
