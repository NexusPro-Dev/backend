# PLAN — `RF-CM-001` Registrar una tasa de comisión por rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-001` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 1.0.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

Este plan **funda la mecánica del módulo** y los demás la heredan sin repetirla. Lo que decide aquí sobre el esquema, la frontera con otros módulos y la traducción de errores rige para `RF-CM-002` a `RF-CM-008`.

---

## 1. Enfoque

Alta corriente contra una tabla propia, con **una sola verificación de fuera** —que el rol sea vendedor— y ninguna comprobación de concurrencia.

Lo que este plan tiene que explicar no es el alta sino lo que la rodea: **por qué `V49` rehace una tabla que ya existía y borra sus filas**, **por qué el alta más simple del módulo devuelve un campo calculado** que ninguna otra respuesta de alta del proyecto devuelve, y **dónde vive la regla de que una tasa declara una forma y solo una** (`RN-CM-016`) — la primera del módulo que rige sobre **dos agregados a la vez**.

## 2. Cambios de esquema

Dos migraciones, y las dos ya aplicadas.

### 2.1 `V49` — la que rehace el módulo entero

Toca las tres tablas y es **la única del proyecto que borra datos a propósito**.

Sobre `commission_rates`: suelta la restricción de exclusión, la de vigencia y las dos claves foráneas hacia `products` y `users`; **deja caer `product_id`, `user_id`, `valid_from` y `valid_to`**; y añade dos cosas:

| Añadido | Para qué |
|---|---|
| `uq_commission_rates_id_role` — único sobre `(id, role_id)` | **Redundante con la clave primaria, y esa es toda su función.** PostgreSQL exige que el destino de una clave foránea **compuesta** sea una restricción única sobre exactamente esas columnas, y sin ella la de `product_commission_rates` —que es lo que impide que el rol copiado diverja— no se puede declarar |
| `idx_commission_rates_role`, parcial sobre las vivas | El listado filtra por rol y es su única consulta |

**No hay unicidad sobre `role_id`**, y es deliberado: varias tasas del mismo rol son legítimas (`spec.md` `FA-001`). Lo que no puede repetirse es un rol sobre el mismo producto, y eso lo cierra la clave primaria de la asociación.

**Por qué borra las filas.** Ninguna de las cuatro formas del modelo anterior tiene traducción al nuevo: una tarifa de persona no es una tasa de rol —perdió el rol y ganó tabla propia—; una de producto necesitaría una asociación que nadie declaró; y una por omisión regía sobre todo el catálogo, que es justo lo que `RN-CM-012` deja de permitir.

Conservarlas dejándolas caer a «tasa de rol» las convertiría en **filas plausibles y falsas**: seguirían ahí, con su porcentaje, sin asociación, y sin que nada dijera que significan otra cosa que el día que se escribieron. **Se borran para que la pérdida sea visible en vez de silenciosa.**

Fue admisible porque el sistema no está en producción y esa tabla solo tenía datos de desarrollo y de prueba. **El día que lo esté, esta migración no se podría repetir**, y conviene que quede escrito.

### 2.2 `V50` — el valor fijo

**Una sola migración para las dos tablas.** `commission_rates` y `user_commission_rates` reciben exactamente los mismos tres cambios, y separarlas habría dejado un estado intermedio en el que una de las dos piezas admite el valor fijo y la otra no — un estado que **nadie querría desplegar** y que sin embargo existiría en el historial.

Sobre cada una de las dos:

| Cambio | Detalle |
|---|---|
| `rate_type varchar(20) NOT NULL` | La forma declarada |
| `percentage` pasa a **admitir nulo** | Solo la lleva la forma proporción |
| `fixed_amount numeric(14,4)`, nula | La misma forma que `products.price`, argumentada en `cm.md` §7.1 |

Y tres restricciones nuevas por tabla —`ck_*_type`, `ck_*_forma`, `ck_*_fixed`—, cuyo contenido fija `cm.md` §7.4 y no se repite aquí.

**`ck_*_percentage` hubo que rehacerla, y es el error fácil de esta migración.** La que existía desde `V44` decía `percentage >= 0 AND percentage <= 100`. Con la columna ya nula, ese `CHECK` **evalúa a nulo y por tanto acepta la fila**, de modo que seguiría estando ahí sin comprobar nada de las filas de tipo `FIJO`. No es un fallo: es lo que se quiere. Pero **si no se reescribe explícitamente, nadie que la lea sabrá si eso se decidió o se pasó por alto**, y se reescribió como `percentage IS NULL OR (percentage >= 0 AND percentage <= 100)` con la rama nula **delante**.

!!! danger "`rate_type` se añadió con `DEFAULT 'PORCENTAJE'` y el valor por defecto se RETIRA acto seguido"

    El relleno es **exacto y no una suposición**: hasta `V50` la única forma que existía era el porcentaje, de modo que toda fila anterior es de tipo `PORCENTAJE` por definición.

    Lo que no puede quedarse es el valor por defecto. Si permanece, una inserción que **omita la forma** obtiene `PORCENTAJE` en silencio — que es exactamente lo que `spec.md` §6.1 decide que no debe pasar: la forma se **declara**, y una petición que no la declare tiene que ser rechazada, no completada.

    `ALTER COLUMN rate_type DROP DEFAULT` es, por eso, **la línea más importante de `V50`**, y la que un despliegue apresurado se dejaría. La cubre una prueba propia — ver §11.

## 3. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `domain/models` | `CommissionValue` | La forma y su valor, **juntos y validados a la vez**. Ver §3.1 |
| `domain/models` | `CommissionRateType` | `PORCENTAJE` \| `FIJO` |
| `domain/models` | `CommissionRate` | El agregado: rol y **un `CommissionValue`** |
| `domain/models` | `RateSource` | De cuál de las dos piezas salió una comisión resuelta. Lo usa `RF-CM-005` |
| `domain/repository` | `CommissionRateRepository` y su adaptador | Escritura |
| `domain/repository` | `CommissionRateQueryRepository` y su adaptador | El listado, con la cuenta de asociaciones |
| `domain/repository` | `CommissionRows` | Las conversiones de tipos del driver, compartidas por los tres adaptadores de consulta |
| `domain/service` | `RegisterCommissionRateService` | Caso de uso |
| `application` | `RegisterCommissionRateRequest`, `CommissionRateResponse` | Entrada y salida |
| `interfaces` | `CommissionRateController` | El recurso de tasas, con las tres operaciones de asociación colgando de él (`RF-CM-007`, `RF-CM-008`) |

**El puerto de escritura no tiene bloqueo ni consulta de solapamiento**, y no es una simplificación gratuita: **sin vigencia no hay nada que pueda solaparse**. Dos altas simultáneas del mismo rol producen dos tasas distintas del catálogo, que es legítimo. La única regla del módulo que dos peticiones pueden burlar vive con la vigencia, en `RF-CM-006`.

**`CommissionRows` existe porque las conversiones que agrupa nacieron de un defecto vivido** —el driver devuelve los instantes de tres formas distintas— y una copia que se quedara atrás volvería a producirlo en la única consulta que nadie hubiera corregido.

### 3.1 `CommissionValue`: por qué la forma y el valor son **un** objeto

`RN-CM-016` es la única regla del módulo que **rige sobre dos agregados**: la tasa de rol y la personalizada la cumplen igual, y las dos podrían romperla por separado. La respuesta es un **objeto embebido** que las dos incrustan, con la comprobación en su constructor.

**El motivo no es ahorrar código.** Es que la regla dice «**exactamente uno** de los dos, **y el que corresponda al tipo**», y esa frase **no se puede evaluar mirando un campo**: hacen falta los tres a la vez. Repartidos por el agregado como tres atributos sueltos, cada uno se puede asignar por su cuenta y **la regla solo es cierta entre asignaciones**; dentro de un objeto que se construye entero o no se construye, **no hay ningún instante en que sea falsa**.

De ahí salen tres consecuencias que valen la pena:

1. **`RF-CM-003` hereda la regla sin escribirla.** Corregir es construir un `CommissionValue` nuevo y sustituir el viejo, de modo que una corrección **no puede** dejar una tasa con las dos formas llenas — ni siquiera si alguien lo intentara.
2. **`RF-CM-005` devuelve un objeto en lugar de un número.** La comisión resuelta es «esta forma, este valor», que es lo único que significa algo.
3. **La instantánea de auditoría gana su campo sin que nadie lo decida dos veces**, porque la arma el agregado a partir del mismo objeto.

!!! warning "Lanza `ValidationException` y no `BusinessRuleException`, aunque viva en el dominio"

    `spec.md` §12 exige que `VAL-011` sea `400`, y en este proyecto `BusinessRuleException` es `409`.

    **No es una excepción a la arquitectura: es el precedente del propio módulo.** `VAL-005` —el orden de la vigencia— también es una comprobación entre campos, también vive en el agregado (`UserCommissionRate`) y también lanza `ValidationException` con su `FieldError`. Se hace igual, por lo mismo: lo que el actor envió está **mal formado**, no en conflicto con el estado del sistema.

**No se duplica en la petición con una anotación de Bean Validation**, aunque se podría. Una restricción de clase sobre `RegisterCommissionRateRequest` diría lo mismo en otro sitio, y habría que escribirla **cuatro veces** —las dos altas y las dos correcciones—. Lo que sí se queda en la petición son las comprobaciones que miran **un solo campo** (`VAL-003`, `VAL-012`), porque ahí una anotación no puede desincronizarse de nada.

## 4. Contrato de API

`POST /api/v1/commission-rates` · `201 Created`, con `Location`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-003`, `VAL-011`, `VAL-012`, y `EX-001`: el rol no es vendedor |
| `403` | Sin el permiso `commissions:create` |
| `422` | `EX-002`: el rol no existe |

**`EX-001` es `400` y `EX-002` es `422`**, y la distinción es la del proyecto: un dato **mal formado o que no vale para esto** es `400`; un dato bien formado que **no resuelve** contra otro módulo es `422`.

**El cuerpo exige `rateType`, y `percentage` no es obligatorio.** Fue un cambio **incompatible** del contrato publicado —una petición con solo `roleId` y `percentage` pasó a devolver `400`—, y se rompió a propósito.

!!! warning "Se rompió en lugar de dejar que la forma ausente significara porcentaje"

    La alternativa compatible existía y era tentadora: sin `rateType`, suponer `PORCENTAJE`. Nadie habría tenido que cambiar nada.

    **Y reintroduce exactamente el defecto que `V50` se molesta en quitar del esquema** (§2.2): la forma volvería a deducirse en lugar de declararse, y una petición equivocada —la que quiso enviar un importe y se equivocó de campo— quedaría **aceptada como porcentaje** en vez de rechazada.

    El coste de romper fue bajo y acotado: el módulo no tiene consumidores fuera del propio proyecto, y el `400` **dice qué falta**. El coste de no romper es una petición mal escrita que se registra como una tasa válida y **se descubre liquidando**.

**`fixedAmount` no lleva moneda en el contrato**, y la documentación publicada lo dice. Es el único importe de dinero de toda la API que se declara sin su moneda; sin una frase que lo explique, quien lea el contrato supondrá que falta un campo.

**La respuesta lleva `associatedProducts`, que aquí vale siempre cero.** Es el único campo calculado de un alta en todo el proyecto, y está por lo que dice `spec.md` §2: sin él, la respuesta de una tasa que paga y la de una que no serían idénticas.

## 5. Autorización

Permiso `commissions:create`. Alcance global explícito (D-22 abierta).

**No se estrena ningún permiso**: los cuatro `commissions:` los sembró `V45` el 28-08-2026. Sus descripciones mencionan «por producto y por persona», que era el modelo anterior — **no se corrigen**, porque una migración aplicada no se edita y el texto no gobierna nada.

## 6. Auditoría

Registro de **cambios**, acción de creación, con la instantánea completa: rol, forma y valor. La arma el agregado y no el caso de uso, por lo mismo que en `PM` — si cada caso de uso armara su mapa, dos registros describirían la misma tasa con claves distintas y compararlos dejaría de ser posible.

**Esa instantánea pesa más en este módulo que en los demás.** Sin vigencia, la tabla no conserva historial, de modo que los registros de auditoría son **el único rastro** de qué valores existieron. `RF-CM-003` lleva esa consecuencia al extremo.

## 7. Transaccionalidad

`@Transactional`. Un `INSERT` y un registro de auditoría.

## 8. Impacto sobre otros módulos

**Ninguno en el código.** `SP` no cambia: se consume `RoleCatalog`, que ya publica. Y **no lo hay a pesar de tratarse de dinero**: un importe fijo de comisión **no consulta `currencies`** ni resuelve nada contra `PM`, precisamente porque no lleva moneda (`RN-CM-017`). La frontera de D-25 no se toca.

**Lo que sí crece es la deuda con la liquidación**, y crece fuera de este módulo: será ella quien tenga que juntar el importe con la moneda del producto.

**En la documentación, seis enmiendas aplicadas** —cuatro con el rediseño y dos con el valor fijo—, todas ya en sus documentos:

| Documento | Enmienda |
|---|---|
| `requirements/cm.md` v0.5.0 y v0.7.0 | `RN-CM-015`; §1.1.1 «Las dos formas de declarar una comisión»; `RN-CM-016` a `RN-CM-018`; §7.1, §7.2 y §7.4 con las tres columnas y las seis restricciones |
| `requirements.md` v0.87.0 | Las ocho filas de la matriz, con sus tres deudas |
| `modules.md` v0.17.0 | `CM` es dueño de **tres** tablas |
| `modelo-datos.md` v0.16.0 y v0.17.0 | Veintiuna tablas escritas; después, las cuatro columnas del valor fijo **diseñadas y sin escribir**, la asimetría con `RN-PM-007`, y qué le hace a `RN-CM-008` que la copia pase de un número a tres cosas |

**El orden en que se aplicaron es el que fijó el responsable del proyecto el 02-09-2026**: primero el documento del módulo, después el modelo de datos, después las tripletas, y el código al final.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Migrar las filas antiguas** en vez de borrarlas | Ninguna de las cuatro formas tiene traducción. Dejarlas caer a «tasa de rol» produce filas plausibles y falsas. Ver §2.1 |
| Unicidad sobre `role_id` | Prohibiría el caso legítimo de dos tasas del mismo rol para productos distintos, que es lo que hace reutilizable el catálogo |
| Conservar la vigencia en las tasas de rol | Decisión del responsable del proyecto (`cm.md` v0.4.0). Su coste —perder el historial— está declarado y aceptado en §1.4 de ese documento |
| No devolver `associatedProducts` | Deja la respuesta de una tasa que rige indistinguible de la de una que no, que es el malentendido central del módulo |
| **Deducir la forma de qué campo venga lleno**, sin `rate_type` | Funciona mientras llegue exactamente uno. Con los dos llenos o ninguno **no se puede decir cuál de los dos errores es**, y el `CHECK` no podría nombrarlo |
| **Dejar el `DEFAULT 'PORCENTAJE'`** después del relleno de `V50` | Una inserción que omita la forma la obtendría en silencio, que es justo lo que se decidió que no pase |
| Suponer `PORCENTAJE` cuando falta `rateType` **en la API** | La misma deducción, un nivel más arriba. Compra compatibilidad al precio de aceptar como válida una petición equivocada |
| **Un solo campo `value` más el tipo**, en vez de dos columnas | Obligaría a `numeric(14,4)` para un porcentaje que cabe en `numeric(5,2)`, y **perdería el `CHECK` de cero a cien**: no se puede acotar una columna cuyo rango depende del valor de otra |
| Tres atributos sueltos en el agregado, sin `CommissionValue` | La regla solo sería cierta **entre asignaciones**. Ver §3.1 |
| **Una moneda propia en la tasa**, con coincidencia exigida al asociar | Descartada en `cm.md` v0.7.0: **la tasa personalizada no tiene producto con el que coincidir**, y se quedaría sin forma de expresarse |
| Un tope superior para `fixed_amount` | Sería un número inventado. El único tope defendible es el de la venta, y ese lo comprobará la liquidación. `RN-CM-018` |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien registre tasas y dé por hecho que ya se paga por ellas** | Es el riesgo principal del módulo. Se mitiga con `associatedProducts` en la respuesta y en el listado, y con `RN-CM-012` declarada. **No se elimina**: el sistema no puede saber si una tasa sin asociar está a medio configurar o mal configurada |
| 2 | `V49` se aplique sobre un entorno con datos que importen | El sistema no está en producción. Queda escrito en §2.1 que esta migración **no se podría repetir** el día que lo esté |
| 3 | La descripción de los permisos de `V45` mencione un modelo que ya no existe | Aceptado: una migración aplicada no se edita, y el texto no gobierna ninguna comprobación |
| 4 | **Se registre un importe fijo pensando en una moneda y se cobre en otra** | **No se mitiga, y no es un descuido.** `RN-CM-017` lo declara y `cm.md` §1.1.1 lo acepta. Lo único que se hace es no ocultarlo: la respuesta devuelve la forma, y el contrato publicado dice que el importe **no lleva moneda** |
| 5 | **`ALTER COLUMN rate_type DROP DEFAULT` se quede fuera de `V50`** | Es el fallo silencioso de esa migración: todo seguiría funcionando y la forma habría dejado de ser obligatoria. Lo cubre una prueba propia — ver §11 |
| 6 | **`ck_*_percentage` se quede como estaba** y deje de comprobar nada sin que nadie lo note | Se reescribió explícitamente con la rama nula delante. Ver §2.2 |
| 7 | Un cliente del contrato se rompa al dejar de bastar `percentage` | Aceptado y buscado. El módulo no tiene consumidores externos y el `400` dice qué falta |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Alta y rol resuelto | Integración | `CA-CM-001` |
| **Nace sin regir** | Integración | `CA-CM-002`: `associatedProducts` es cero **y** la tabla de asociación está vacía |
| **Lo que la respuesta no lleva** | Integración | `CA-CM-003`: producto, persona, vigencia y grado **ausentes**. Es la prueba que delataría un modelo a medio migrar |
| Varias tasas por rol | Integración | `CA-CM-004` |
| Porcentaje cero | Integración | `CA-CM-005` |
| Rol no vendedor e inexistente | Integración | `CA-CM-006`, `CA-CM-007`: `400` frente a `422` |
| Validaciones de entrada | Integración | `CA-CM-008` |
| El agregado, sin base de datos | Unitaria | Rango del porcentaje, instantánea y no idempotencia del retiro |
| **Alta en valor fijo** | Integración | `CA-CM-079`: la respuesta trae **la forma junto al valor** |
| **Las dos formas, ninguna, y la equivocada** | Integración | `CA-CM-080`, `CA-CM-081`: las tres dan `400` con `VAL-011` |
| Valor fijo negativo y valor fijo cero | Integración | `CA-CM-082`, `CA-CM-083` |
| **Un importe mayor que cualquier precio** | Integración | `CA-CM-084`: **`201`**, y es la prueba que afirma que nadie lo vigila |
| `CommissionValue`, sin base de datos | Unitaria | Las cuatro combinaciones de forma y valor, construidas directamente |
| **`rate_type` no tiene valor por defecto** | Integración | Un `INSERT` directo que omita la forma **debe fallar** |

**Esa última prueba es la del riesgo 5, y ninguna de las anteriores la cubre**: todas pasan por la API, que **siempre envía la forma**, de modo que ninguna se entera de si la columna conserva su `DEFAULT`. La comprobación tiene que ser **contra el esquema**.

Es una prueba fea —habla `SQL` en lugar de negocio— y es la única capaz de fallar cuando la línea que importa se cae de la migración. El módulo tiene precedente: `CA-CM-075` comprueba algo que el esquema **habría aceptado sin ello**.

**No hay prueba concurrente en este requerimiento, y su ausencia es una afirmación**: no queda ninguna regla que dos altas simultáneas puedan burlar. `RN-CM-016` tampoco es una de ellas — **es una regla dentro de una fila**, no entre filas, y por eso un `CHECK` la cierra del todo. Las que sí quedan se prueban en `RF-CM-006` y `RF-CM-007`.
