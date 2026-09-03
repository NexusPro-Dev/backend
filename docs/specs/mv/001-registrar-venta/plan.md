# PLAN — `RF-MV-001` Registrar una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-001` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

Este plan **funda la mecánica del módulo** y los demás la heredan sin repetirla: el esquema, las lecturas cruzadas hacia `SP` y `PM`, la traducción de errores y el generador del código de comprobante rigen para `RF-MV-002` a `RF-MV-009`.

---

## 1. Enfoque

Un alta con **muchas verificaciones contra dos módulos ajenos** y ninguna comprobación de concurrencia.

Lo que este plan tiene que explicar no es el `INSERT` —que es corriente— sino tres cosas: **de dónde salen los datos que la petición no envía** (precio, vigencia, moneda y vendedor), **cómo se pregunta por la oferta sin volver a calcularla aquí**, y **por qué esta operación no bloquea nada** pese a que dos peticiones simultáneas puedan vender dos veces el mismo upgrade.

## 2. Cambios de esquema

**Dos migraciones y no una, y el reparto no es el que este plan escribió el 02-09-2026.**

`V51__seed_movements_permissions.sql` — **ya aplicada**. Estrena los cuatro permisos y **nada más**: ninguna tabla.

`V53` — **la migración que funda el módulo**. Crea las cuatro tablas y siembra dos catálogos.

**Por qué se separaron.** Los permisos se adelantaron porque su tarea no depende de ninguna otra, y adelantar una tabla habría sido distinto: un permiso sin endpoint que lo exija no rompe nada —el catálogo es datos, y su único efecto es poder concederse—, mientras que una tabla sin el caso de uso que la escribe es un esquema que promete algo que no existe.

**Y por qué el número saltó de `51` a `52`.** El `50` estaba reservado por `RN-SP-025` y el `51` por este requerimiento, y **ninguna de las dos estaba escrita**. El número lo toma quien se aplica primero ([`modelo-datos.md` §1](../../../modelo-datos.md)): Flyway deja fuera una migración con número por debajo del último aplicado, de modo que reservar por adelantado y aplicar después es exactamente lo que no se puede hacer.

### 2.1 Las cuatro tablas

Su forma la fija [`requirements/mv.md` §7](../../../requirements/mv.md) y no se repite aquí. Lo que sí decide este plan es el orden y tres detalles:

| Tabla | Detalle |
|---|---|
| `movement_types` | Se crea **y se siembra en la misma migración**, con una sola fila: `VENTA`, prefijo `VTA`. Una tabla de tipos vacía deja el módulo sin poder registrar nada, y separar la siembra permitiría desplegar ese estado |
| `payment_methods` | Sembrada con **dos filas**: `EFECTIVO` y `TRANSFERENCIA`. Nada más — `PUNTOS` es de la etapa 3 y sembrarlo hoy ofrecería un método con el que no se puede pagar |
| `movements` | **Sin `updated_at` ni `deleted_at`** (`RN-MV-001`). Es la única tabla del sistema que no los lleva, y por eso el gestor de auditoría de la aplicación no puede tratarla como a las demás |
| `movement_details` | Clave foránea a `movements` **con borrado en cascada**, que aquí no significa nada porque nada borra ventas: está para que el esquema no admita líneas huérfanas |

**Los índices que se crean son dos y solo dos**: `movement_details(movement_id)` —que se recorre siempre entero al leer una venta— y `movements(client_id)`, que es el filtro de `RF-MV-008`. El resto se añadirá cuando `RF-MV-006` decida por qué se lista.

### 2.2 Los permisos, en su propia migración y solo para `SUPERADMIN`

`movements:read`, `movements:create`, `movements:confirm` y `movements:void` se siembran en `V51` y se asocian ahí mismo **únicamente a `SUPERADMIN`**.

**Esto no es lo que hizo `RF-PM-001` ni lo que [`security.md` §4.4](../../../security.md) obliga**, que es asociar también a `ADMIN`. Es una **decisión del responsable del proyecto del 02-09-2026**, recogida en [`mv.md` §6.1](../../../requirements/mv.md) y en la propia §4.4, y **cambia lo que este requerimiento entrega**: por `RN-SEG-003`, con `ADMIN` fuera ningún rol de la fuerza comercial —que cuelga entera de él— podrá declarar `movements:create`. El endpoint que este plan diseña queda construido y **operable solo por el superadministrador** hasta que la reserva se levante, que son dos `INSERT`.

**Olvidar la asociación a `SUPERADMIN` sí rompe la migración**, y a propósito: `V51` termina con una guarda que aborta si alguna de las cuatro filas no se insertó. `RN-SEG-007` acota la raíz por el catálogo completo, y un permiso sembrado y no asociado la dejaría por detrás de sus propios hijos — un defecto que sin la guarda se descubriría mucho después y en otro sitio, con `RN-SEG-003` rechazando un rol sin decir que lo que falta es una siembra.

### 2.3 `ck_movements_confirmed` se declara ahora aunque la use `RF-MV-003`

La restricción que ata `confirmed_at` al estado `CONFIRMADA` no la ejercita este requerimiento: aquí toda venta nace pendiente y con la fecha vacía. Se declara igual, porque **la coherencia entre una fecha y un estado es del esquema**, y añadirla después obligaría a comprobar antes que ninguna fila la incumpla ya.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `Movement` | Nuevo | El agregado: cabecera, líneas, estado y totales. **Calcula el total él mismo** |
| `domain/models` | `MovementLine` | Nuevo | Producto, cantidad y **lo copiado**: precio unitario, vigencia e importe de línea |
| `domain/models` | `MovementStatus` | Nuevo | `PENDIENTE` \| `CONFIRMADA` \| `RECHAZADA` \| `ANULADA` |
| `domain/models` | `MovementCode` | Nuevo | El código de comprobante y su composición (`RN-MV-016`) |
| `domain/repository` | `MovementRepository` y su adaptador | Nuevos | Guardar la venta con sus líneas |
| `domain/service` | `RegisterSaleService` | Nuevo | Caso de uso: resuelve, verifica, arma el agregado y lo guarda |
| `application` | `RegisterSaleRequest`, `SaleResponse`, `SaleLineResponse` | Nuevos | Entrada y salida |
| `interfaces` | `MovementController` | Nuevo | `POST /api/v1/movements` |
| `modules/products/application` | `ProductCatalog` | **Modificado** | Gana la vista de venta y la consulta de oferta. Ver §3.2 |
| `modules/system/users/application` | `ClientCatalog` | **Nuevo, dentro de `SP`** | El estado del cliente, su vendedor y su nivel vigente. Ver §3.2 |

### 3.1 El total lo calcula el agregado, no el caso de uso

`RN-MV-013` dice que el total es la suma de las líneas y **se congela**. Si lo sumara el caso de uso, la venta podría construirse con un total que no corresponde a sus líneas —y nada lo impediría—, de modo que la regla solo sería cierta si nadie se equivoca.

Construido por el agregado a partir de sus líneas, **no hay ningún instante en que el total no sea la suma**. Es el mismo argumento con el que `CM` metió la forma y el valor en un solo objeto (`RF-CM-001` · `plan.md` §3.1).

**Y la instantánea de auditoría la arma también el agregado**, por lo mismo que en `PM`: si cada caso de uso armara su mapa, dos registros describirían la misma venta con claves distintas y compararlos dejaría de ser posible.

### 3.2 Las lecturas cruzadas: qué se pregunta y a quién

Cuatro datos que la petición no trae, y **ninguno se calcula aquí** ([`architecture.md` §15.2](../../../architecture.md): la interfaz la declara el módulo dueño del dato).

| Qué hace falta | Quién lo publica | Por qué no lo resuelve `MV` |
|---|---|---|
| Precio, moneda, tipo, vigencia y membresía destino de cada producto | `PM` — `ProductCatalog` gana una **vista de venta** | Son datos suyos. La vista actual solo lleva código, nombre y si está retirado, y **no se amplía**: se añade un método con su propio registro, para que `CM`, que ya consume el existente, no cambie |
| **Si el producto está en la oferta de esa persona** | `PM` — la misma interfaz | Es la decisión que `RF-PM-007` ya toma. Recalcularla aquí crearía **dos definiciones de «lo que alguien puede comprar»**, y el día que una cambiara la otra seguiría vendiendo lo que la primera ya no ofrece |
| El estado del cliente y **de qué vendedor cuelga** | `SP` — `ClientCatalog`, nuevo | `users` y `user_supervisors` son suyas, y el vendedor de un cliente es su superior comercial con la rama de consumidor de `RN-SP-020` |
| El nivel de membresía vigente del cliente | `SP` — el mismo | `user_memberships` es suya |

**Las dos consultas a `PM` se hacen por lote y no por línea.** Una venta de cinco productos que preguntara cinco veces cruzaría la frontera cinco veces para lo mismo: es una `N+1` que no se ve porque cada llamada es un método Java, y que aparece entera en el registro de sentencias.

!!! warning "La comprobación de nivel se escribe aunque hoy la oferta ya la garantice"

    `RF-PM-007` ofrece solo lo que está por encima del nivel actual, de modo que `RN-MV-007` —el producto está en la oferta— **hoy implica** `RN-MV-006` —el upgrade sube—. Comprobar las dos parece redundante, y lo es.

    **Se escriben las dos igualmente**, y el motivo es de quién manda sobre qué. La oferta es una decisión de `PM` y puede ampliarse —el día que se vendan renovaciones del mismo nivel, por ejemplo—; que **una venta no baje a nadie de nivel** es una regla de `MV`, y no puede depender de que otro módulo siga tomando la misma decisión que hoy.

    Es lo que mantiene `EX-005` alcanzable: hoy no se llega por la oferta, y se llegaría el día siguiente a que `PM` la ampliara.

## 4. Contrato de API

`POST /api/v1/movements` · `201 Created`, con `Location`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-007`: lo que se ve **mirando la petición** — falta el cliente, no hay líneas, cantidad no positiva, producto repetido, fecha futura |
| `403` | Sin el permiso `movements:create` |
| `409` | Lo que solo se sabe **después de resolver**: cuenta en `FTD_PENDIENTE` (`EX-002`), cliente sin vendedor (`EX-003`), producto fuera de la oferta (`EX-004`), upgrade que no sube (`EX-005`), dos upgrades (`EX-006`), monedas distintas (`EX-008`), cantidad en un upgrade (`EX-009`), método inactivo (`EX-010`) |
| `422` | `EX-001`, `EX-011` y el método inexistente: un dato **bien formado que no resuelve** contra otro módulo |

**El criterio de reparto es el del proyecto, y aquí se aplica a rajatabla**: `400` es forma, `422` es referencia que no existe, `409` es conflicto con el estado del sistema. Lo que empuja tres excepciones al `409` que un lector pondría en `400` —dos upgrades, monedas distintas, cantidad en un upgrade— es que **ninguna de las tres se puede decidir sin haber leído el catálogo**: la petición es idéntica en forma a una correcta, y lo que la hace inválida es qué son esos productos.

**El endpoint es `/movements` y no `/sales`**, aunque este requerimiento solo registre ventas. La tabla es el libro y los depósitos entran por aquí en la etapa 2; un recurso llamado `sales` obligaría a inventar otro para el mismo objeto o a renombrar el publicado.

## 5. Autorización

Permiso `movements:create`, estrenado por `V51` (§2.2). Alcance global explícito (D-22 abierta): quien lo tiene registra ventas de cualquier cliente.

**Hoy solo lo tiene `SUPERADMIN`**, por la reserva de §2.2: mientras siga en pie, no hay ningún otro rol al que se le pueda conceder.

## 6. Auditoría

Registro de **cambios**, acción de creación, con la instantánea completa: cliente, **vendedor congelado**, método, importes, código y las líneas con lo copiado.

**El vendedor tiene que estar en la instantánea**, y es lo único de esta sección que no es rutina: es un dato que el actor no envió y que determina **a quién se le va a pagar**. Sin él en el registro, la pregunta «¿por qué esta venta se le atribuyó a esta persona?» solo se puede responder reconstruyendo cómo estaba la estructura comercial ese día.

## 7. Transaccionalidad

`@Transactional`. La cabecera, sus líneas y el registro de auditoría, o nada.

**Las lecturas a `SP` y `PM` ocurren dentro de la misma transacción** y son de solo lectura. No hay bloqueo sobre nada suyo: si el catálogo cambia justo después, la venta ya copió lo que necesitaba.

## 8. Impacto sobre otros módulos

**En el código, sí lo hay, y es el riesgo mayor de este requerimiento**: dos módulos ajenos ganan interfaces publicadas.

| Módulo | Qué gana | Quién lo escribe |
|---|---|---|
| `PM` | La vista de venta y la consulta de oferta en `ProductCatalog` | Tareas de este requerimiento, aunque el código viva en `modules/products`. Es el precedente de `RF-PM-001` y `RF-PM-007`, que escribieron puertos dentro de `SP` |
| `SP` | `ClientCatalog`: estado, vendedor y nivel vigente | Igual |

**Su definición de terminado exige que las suites de `PM` y de `SP` sigan en verde sin cambios.** Lo que se añade son métodos nuevos sobre interfaces existentes y una interfaz nueva; nada se modifica.

**En la documentación, tres enmiendas ya aplicadas** en el mismo pase que este plan:

| Documento | Enmienda |
|---|---|
| `architecture.md` v0.28.0 | §15.2 registra **tres lecturas cruzadas nuevas** —dos hacia `PM` y una hacia `SP`—, las primeras de `MV`, y deja dicho que **la oferta se pregunta y no se recalcula** |
| `modelo-datos.md` v0.19.0 | Las cuatro tablas de `MV` entran como **diseñadas y sin escribir**, **sin copiar su forma**: la fuente es `requirements/mv.md` §7, y este plan añade el número de migración que allí no está. Se recoge además que la tabla que su §4.1 exigía **ya existe en papel y acepta sus dos condiciones** |
| `requirements.md` v0.92.0 | Las filas de `RF-MV-001` y `RF-MV-002` pasan a `Tasks en revisión` con su rama, y con el bloqueo de `RF-SP-045` anotado |

**D-26 no bloquea este requerimiento.** Registrar una venta **no escribe en `SP`**: solo lee. La escritura aparece al confirmar, y es `RF-MV-003` quien no puede terminarse sin esa decisión.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Aceptar el precio en la petición** | Es un descuento sin llamarlo así, y sin autorización ni rastro. `spec.md` §2 lo argumenta como decisión de negocio |
| **Ampliar `ProductView`** en lugar de añadir una vista nueva | Un registro compartido que crece por cada consumidor acaba llevando campos que a la mitad no le sirven, y obliga a `CM` a recompilar por algo que no usa |
| **Calcular la oferta dentro de `MV`** | Dos definiciones de lo mismo. La segunda divergiría en silencio y seguiría vendiendo lo que `PM` ya no ofrece |
| Preguntar el producto **línea a línea** | Una `N+1` que no parece una porque cada llamada es un método Java |
| **Bloquear al cliente** para impedir dos ventas simultáneas del mismo upgrade | No hay nada que proteger: **ninguna de las dos ventas concede nada**. El conflicto es de `RF-MV-003`, que sí aplica el nivel, y bloquear aquí daría la impresión de que está resuelto |
| Un recurso `/sales` | La tabla es el libro; los depósitos entran por el mismo sitio en la etapa 2 |
| Guardar el total **solo en las líneas** y sumarlo al leer | `RN-MV-013`: recalcular al leer hace que un cambio de redondeo reescriba comprobantes ya entregados |
| **Un consecutivo sin huecos** en lugar del aleatorio | Una `SEQUENCE` deja huecos por diseño en cada transacción revertida, y prometer una serie completa obligaría a renumerar. `requirements/mv.md` §7.2.1 |
| Sembrar `PUNTOS` como método de pago desde ya | Ofrecería un método con el que no se puede pagar hasta la etapa 3 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Los cuatro permisos se siembren sin asociarse a `SUPERADMIN` y `ADMIN`** | La migración se aplicaría sin quejarse. Lo cubre una prueba de la siembra, no una lectura del `SQL` |
| 2 | **Colisión del código aleatorio** | Treinta y dos elevado a seis por tipo y día hace la colisión improbable y no imposible. Se resuelve con la unicidad en el esquema y **reintento acotado** en el caso de uso: tres intentos y falla. Sin la unicidad, la colisión produciría dos comprobantes iguales sin que nada avisara |
| 3 | **La venta se registre con el vendedor equivocado** porque la estructura comercial estaba mal | No se mitiga aquí: `MV` copia lo que `SP` dice. Lo que sí se hace es **devolverlo en la respuesta** y guardarlo en la auditoría, para que el error sea visible el mismo día y no el de liquidar |
| 4 | El gestor de auditoría de la aplicación **espere `updated_at`** en `movements` | Es la primera tabla sin esas columnas. Se comprueba al escribir el adaptador, y falla al compilar o en la primera prueba de integración |
| 5 | **Dos ventas simultáneas del mismo upgrade** | Aceptado y declarado en `spec.md` §13. Ninguna concede nada; el conflicto es de `RF-MV-003` |
| 6 | Las interfaces nuevas de `PM` y `SP` **rompan sus suites** | Se añaden métodos, no se modifican. Su definición de terminado exige las dos suites en verde sin cambios |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Alta de una venta, pendiente y con código | Integración | `CA-MV-001` |
| **El vendedor resuelto** | Integración | `CA-MV-002`: el que el actor no envió |
| **La copia sobrevive a corregir el producto** | Integración | `CA-MV-003`: se corrige el precio **después** y la venta no cambia. Comparar al registrar no probaría nada |
| Totales y descuento cero | Integración | `CA-MV-004` |
| Varias líneas, con y sin upgrade | Integración | `CA-MV-005` |
| Formato del código | Integración | `CA-MV-006`: prefijo, día del hecho y alfabeto sin `I`, `L`, `O`, `U` |
| **La venta no cambia el nivel de nadie** | Integración | `CA-MV-007`: la membresía del cliente es la misma después |
| Las diez negativas | Integración | `CA-MV-008` a `CA-MV-017`, cada una con su código y su distinción |
| Auditoría con el vendedor dentro | Integración | `CA-MV-018` |
| El agregado, sin base de datos | Unitaria | El total como suma de líneas, la composición del código, y que un `Movement` no se puede construir sin líneas |
| **La siembra de permisos** | Integración | Los cuatro existen **y están asociados a `SUPERADMIN` y `ADMIN`** — riesgo 1 |
| Reintento del código | Unitaria | Tres intentos y falla, con el generador forzado a colisionar |

**No hay prueba concurrente en este requerimiento, y su ausencia es una afirmación**: ninguna regla de las que aquí se comprueban puede burlarse con dos peticiones simultáneas, porque **ninguna venta registrada produce efecto alguno**. Las que sí lo pueden ser se prueban en `RF-MV-003`.
