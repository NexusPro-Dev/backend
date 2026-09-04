# TASKS — `RF-MV-001` Registrar una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-001` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.2.0 |
| Estado | **En curso** — `T-01` a `T-18` `Hecha`; `CA-MV-008` queda **sin prueba** hasta `RF-SP-045` |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 04-09-2026 |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

!!! success "Construido el 04-09-2026, y el bloqueo era MÁS PEQUEÑO de lo que este documento decía"

    La versión 0.1.0 daba por bloqueadas `T-07`, `T-11` y `T-15` hasta que `RF-SP-045` existiera. Al construirlas se comprobó que **solo una cosa falta de verdad**: el estado `FTD_PENDIENTE`, que ese requerimiento estrena y que `ck_users_status` todavía no admite.

    Todo lo demás **ya existía**. `user_supervisors` está desde `V21` y `UserRepository.findActiveSupervisor` desde `RF-SP-041`, de modo que `ClientCatalog` se pudo escribir entero y `EX-003` —el cliente sin vendedor— es **alcanzable y está probado**. Lo que `RF-SP-045` traerá no es la capacidad de colgar clientes: es el camino público que los cuelga solo.

    **Queda exactamente un criterio sin prueba, `CA-MV-008`**, y su rama de código sí está escrita. Ver §4.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V54`**: `movement_types` y `payment_methods`, **creadas y sembradas en la misma migración** — `VENTA`/`VTA`, y los tres medios de pago `CREDIT_CARD`, `PSE` y `TRANSFERENCIA` | — | Ninguna versión del esquema tiene la tabla de tipos vacía | **Hecha** — 04-09-2026 |
| `T-02` | **`V54`**: `movements`, **sin `updated_at` ni `deleted_at`**, con `ck_movements_status`, `ck_movements_payable`, `ck_movements_amounts`, `ck_movements_confirmed` y `uq_movements_code` | `T-01` | Los cuatro `CHECK` declarados; `uq_movements_code` es además lo que hace posible el reintento de `T-10`, probado en `MovementCodeRetryIT` | **Hecha** — 04-09-2026 |
| `T-03` | **`V54`**: `movement_details`, con `uq_movement_details_producto`, `ck_movement_details_quantity` y `ck_movement_details_validity` **con la rama nula delante** | `T-02` | Declaradas. La repetición la rechaza además la aplicación antes de llegar al motor (`VAL-006`), y las dos capas se prueban por separado | **Hecha** — 04-09-2026 |
| `T-04` | `V51`: sembrar los cuatro permisos `movements:` **y asociarlos SOLO a `SUPERADMIN`** — la reserva de [`mv.md` §6.1](../../../requirements/mv.md), que se aparta de `security.md` §4.4 | — | Cubierta por `T-16`, que es una prueba y no una lectura del `SQL` | **Hecha** — 02-09-2026 |
| `T-05` | `PM`: `ProductCatalog` gana la **vista de venta por lote** —precio, moneda, tipo, vigencia y membresía destino— sin tocar la vista existente | — | `ProductCatalog.saleViewOf`. La suite de `PM` sigue en verde **sin un solo cambio**, y `CM` no recompila: `ProductView` no se tocó | **Hecha** — 04-09-2026 |
| `T-06` | `PM`: `ProductCatalog` gana la **consulta de oferta por lote**, que reutiliza lo que `RF-PM-007` ya resuelve | `T-05` | `ProductCatalog.offeredTo` **no escribe ningún `SELECT` propio**: llama al mismo `findOffer` de `RF-PM-007` y se queda con la intersección. Ver §2 | **Hecha** — 04-09-2026 |
| `T-07` | `SP`: `ClientCatalog` nuevo — **estado y vendedor vigente** del cliente | — | La suite de `SP` sigue en verde sin cambios. **Publica dos lecturas y no tres**: el nivel se pide a `CurrentMembershipLookup`, que ya existe. Ver §2 | **Hecha** — 04-09-2026 |
| `T-08` | `Movement`, `MovementLine` y `MovementStatus`, con **el total calculado por el agregado** y la instantánea de auditoría armada por él | — | `MovementTest`: no existe forma de construir una venta cuyo total no sea la suma de sus líneas, ni una sin líneas | **Hecha** — 04-09-2026 |
| `T-09` | `MovementCode`: prefijo, día de **la fecha del hecho en `America/Bogota`** y seis caracteres del alfabeto de Crockford **sin `I`, `L`, `O` ni `U`** | — | `MovementCodeTest`: una venta de las 23:30 en Bogotá lleva **su** día y no el siguiente | **Hecha** — 04-09-2026 |
| `T-10` | `MovementRepository` y su adaptador: cabecera y líneas en una sola transacción, con **reintento acotado** ante colisión de código | `T-02`, `T-03`, `T-09` | `MovementCodeRetryIT`: con el comprobante ya tomado reintenta una vez y entra; con el generador forzado a colisionar siempre, **el tercer intento falla** en lugar de reintentar sin fin | **Hecha** — 04-09-2026 |
| `T-11` | `RegisterSaleService`: el orden de `spec.md` §8 — cliente, vendedor, composición, oferta, moneda, copia, totales, código | `T-05`–`T-10` | Una petición con producto repetido **no llega a consultar la oferta**: `VAL-006` se comprueba sobre la petición, antes de la primera lectura del catálogo | **Hecha** — 04-09-2026 |
| `T-12` | DTOs de entrada y salida, con **el vendedor resuelto** y el descuento en cero | `T-11` | `CA-MV-002`: la respuesta lleva el vendedor que el actor no envió, con su nombre | **Hecha** — 04-09-2026 |
| `T-13` | `POST /api/v1/movements`, con el reparto de códigos de `plan.md` §4 | `T-11`, `T-12` | `201` con `Location`; `400`, `403`, `409` y `422` cada uno en su caso, probados uno a uno | **Hecha** — 04-09-2026 |
| `T-14` | Registro de auditoría de creación, **con el vendedor congelado dentro** | `T-11` | `CA-MV-018`: la instantánea contiene `seller_id`, el estado, los tres importes y las líneas con lo copiado | **Hecha** — 04-09-2026 |
| `T-15` | Pruebas de los criterios de `spec.md` §12 | `T-13` | `CA-MV-001` a `CA-MV-018`, **salvo `CA-MV-008`**. Ver §4 | **Hecha con una ausencia declarada** — 04-09-2026 |
| `T-16` | **Prueba de la siembra**: los cuatro permisos existen, **están asociados a `SUPERADMIN`** y **ninguno lo está a `ADMIN`** | `T-04` | Es la única tarea que delata que la asociación se cayó de `V51`, y la única que fija que la reserva sea entera y no a medias | **Hecha** — 02-09-2026 |
| `T-17` | Documentación OpenAPI: que **el precio no se envía** y que la venta **nace pendiente y no concede nada** | `T-13` | El contrato publicado dice las dos cosas, y enumera los cuatro códigos de rechazo con el criterio que los reparte | **Hecha** — 04-09-2026 |
| `T-18` | **Comprobar que las tres enmiendas de `plan.md` §8 siguen valiendo** tras el código, y llevar la matriz al estado final | `T-15` | Las cuatro tablas dejan de estar «diseñadas y sin escribir» en `modelo-datos.md`, y las lecturas de `architecture.md` §15.2 existen — **una menos de las tres previstas**, por la enmienda de §2 | **Hecha** — 04-09-2026 |

### 1.1 Lo que se añadió y no estaba en la tabla

| ID | Tarea | Por qué |
|---|---|---|
| `T-19` | **Regla de ArchUnit**: `movements` no depende de `system..domain..` ni de `products..domain..` | `MV` es el módulo que más fronteras cruza, y el primero que se apoya en una **decisión** de otro (`RF-PM-007`) en lugar de en un dato suyo. Sin la regla, «pregunta la oferta, no la recalcules» es una frase de un documento: un `SELECT` propio sobre `products` compilaría igual y pasaría las pruebas igual. La regla equivalente de `PM` existe desde D-25 y solo cubría a `PM` |
| `T-20` | **Prueba unitaria de `RN-MV-006`** con el catálogo simulado | `EX-005` **no es alcanzable por HTTP hoy**, porque la oferta ya excluye lo que no sube. Sin esta prueba, borrar la comprobación de nivel del caso de uso dejaría la suite entera en verde. Ver §3 |

## 2. Lo que se apartó del plan, y por qué

**Tres apartados, los tres declarados como enmienda (Art. I.7).**

### 2.1 La migración es `V54` y no `V53`

Es **la tercera vez** que este número se mueve, y siempre por lo mismo: el número lo toma quien se aplica primero ([`modelo-datos.md` §1](../../../modelo-datos.md)). El 03-09-2026 se fusionó `V53__products_source_membership.sql` —el origen del upgrade, `RF-PM-001`—, de modo que el `53` dejó de estar libre antes de que estas tablas existieran. Reservar por adelantado y aplicar después es exactamente lo que Flyway no perdona.

### 2.2 `ClientCatalog` publica DOS lecturas y no tres

`plan.md` §3.2 le asignaba también el nivel de membresía vigente del cliente. **No lo lleva**: ese puerto ya existe desde `RF-PM-007` · `T-01` —`CurrentMembershipLookup`—, con su borde fijado por prueba y con la definición de «vigente» en un solo sitio desde el 24-08-2026.

Declararlo otra vez habría creado la segunda, que es el defecto que aquel puerto existe para evitar: **no falla**, devuelve resultados plausibles durante meses y solo se separa en el borde. La consecuencia es que `architecture.md` §15.2 registra **dos** lecturas cruzadas nuevas y no tres.

### 2.3 `Movement` no es una entidad JPA

Es el primer agregado del sistema que no lo es —`Product`, `Role` y `Membership` sí—, y son dos motivos que se suman:

1. **El reintento acotado lo exige.** Con `persist`, la violación de `uq_movements_code` marca la transacción para deshacerse y el segundo intento ya no cabe dentro de ella: «tres intentos y falla» pasaría a necesitar una transacción por intento, con la cabecera y sus líneas repartidas entre varias — lo que `plan.md` §7 prohíbe. Con `INSERT … ON CONFLICT (code) DO NOTHING` el rechazo es una cuenta de filas afectadas, y el reintento es un bucle. Es el mismo recurso, y por el mismo motivo, que `UserRepository.addRoles`.
2. **Esta tabla no se actualiza nunca** (`RN-MV-001`), de modo que el seguimiento de cambios de JPA —que es lo que se paga por mapearla— no tiene aquí nada que seguir. Es además la única tabla del sistema sin `updated_at` ni `deleted_at`, que es lo que el riesgo 4 del plan advertía.

Queda declarado lo que esto obliga: **las lecturas de `RF-MV-006` y `RF-MV-007` usarán un repositorio de consulta con registros planos**, como `ProductQueryRepository` y su `ProductRow` — que es el patrón dominante del proyecto para leer, y no una excepción que este requerimiento invente.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas | Estado |
|---|---|---|
| `CA-MV-001` | `T-02`, `T-10`, `T-13`, `T-15` | Cubierto |
| `CA-MV-002` | `T-07`, `T-11`, `T-12`, `T-15` | Cubierto |
| `CA-MV-003` | `T-05`, `T-08`, `T-11`, `T-15` | Cubierto — **se prueba corrigiendo el producto DESPUÉS** |
| `CA-MV-004` | `T-02`, `T-08`, `T-15` | Cubierto |
| `CA-MV-005` | `T-03`, `T-11`, `T-15` | Cubierto |
| `CA-MV-006` | `T-09`, `T-15` | Cubierto — incluido el día de la fecha del hecho en `America/Bogota` |
| `CA-MV-007` | `T-11`, `T-15` | Cubierto |
| **`CA-MV-008`** | `T-07`, `T-11`, `T-13`, `T-15` | **SIN PRUEBA.** La rama de código existe; el dato que la alcanza, no. Ver §4 |
| `CA-MV-009` | `T-07`, `T-11`, `T-13`, `T-15` | Cubierto |
| `CA-MV-010` | `T-05`, `T-06`, `T-11`, `T-15` | Cubierto — el inexistente da `422` y el fuera de la oferta, `409`, con el producto nombrado |
| `CA-MV-011` | `T-05`, `T-06`, `T-11`, `T-15`, `T-20` | Cubierto **por dos caminos**. Ver abajo |
| `CA-MV-012`, `CA-MV-013` | `T-03`, `T-11`, `T-15` | Cubierto |
| `CA-MV-014` | `T-05`, `T-11`, `T-15` | Cubierto |
| `CA-MV-015` | `T-01`, `T-11`, `T-15` | Cubierto — inexistente `422`, desactivado `409` |
| `CA-MV-016` | `T-12`, `T-13`, `T-15` | Cubierto |
| `CA-MV-017` | `T-07`, `T-11`, `T-15` | Cubierto — **y no estaba previsto que lo estuviera**. Ver el aviso de cabecera |
| `CA-MV-018` | `T-08`, `T-14`, `T-15` | Cubierto |

**`CA-MV-011` necesita dos pruebas, y merece leerse dos veces.** Por HTTP, un upgrade que no sube **nunca llega** a `RN-MV-006`: la oferta de `RF-PM-007` ya lo excluyó, y el rechazo que se ve es `EX-004`. La prueba de integración lo comprueba así porque es lo que hoy ocurre de verdad, y el criterio queda satisfecho — se rechaza **al registrar**, que es lo que exige.

Pero la oferta es una decisión de **`PM`** y puede ampliarse; que una venta no baje a nadie de nivel es una regla de **`MV`**. `RegisterSaleServiceTest` amplía la oferta a mano y alcanza `EX-005`, que es la única forma de que borrar esa comprobación haga fallar algo. Es exactamente el argumento del aviso de `plan.md` §3.2, y sin la segunda prueba ese aviso sería una intención.

## 4. Bloqueos

**`RF-SP-045` — el registro de clientes por enlace. Sin código, y bloquea MUCHO MENOS de lo que la versión 0.1.0 suponía.**

| Lo que trae | ¿Bloqueaba? |
|---|---|
| **Clientes colgados de un vendedor** (`RN-SP-020`, rama de consumidor) | **No.** `user_supervisors` existe desde `V21` y admite cualquier subordinado. `ClientCatalog` (`T-07`) se escribió entero, `EX-003` es alcanzable y `CA-MV-017` está probado. Lo que `RF-SP-045` añade es el camino **público** que los cuelga solo |
| **El estado `FTD_PENDIENTE`** (`RN-SP-026`) | **Sí, y es lo único.** No existe en `ck_users_status` —`RF-SP-045` lo estrena sustituyendo a `PENDIENTE`—, de modo que ninguna fila puede llevarlo y ninguna prueba lo puede sembrar |

**Consecuencia exacta, y solo esa: `CA-MV-008` no tiene prueba.** La comprobación **sí está escrita** en `RegisterSaleService` y es la única rama del servicio que hoy no se alcanza. Se escribió igualmente en lugar de dejarla para después, porque la alternativa es que el día que `RF-SP-045` aterrice **se le empiece a vender a cuentas que no pueden operar** sin que nada falle — un requerimiento que se da por terminado no vuelve a revisarse buscando lo que le faltaba.

**Cuando `RF-SP-045` exista, lo que hay que hacer aquí es una sola cosa**: sembrar un cliente en `FTD_PENDIENTE` en `RegisterSaleIT` y afirmar el `409` con `EX-002`. No hay código que escribir.

**D-26 sigue sin bloquear este requerimiento**, y conviene repetirlo porque bloquea al siguiente: registrar una venta **solo lee** de `SP`. La escritura —conceder el nivel comprado— aparece al confirmar, y es `RF-MV-003` quien no puede terminarse hasta que esa decisión se cierre.

## 5. Lo que se descubrió al construir, y no es de este requerimiento

**Los importes del movimiento son `numeric(14,2)` y `currencies.decimal_places` admite de cero a cuatro.**

`requirements/mv.md` §7 fija los tres importes de `movements` y los dos de `movement_details` en **dos decimales**, mientras que `V14` permite monedas de hasta cuatro y `products.price` es `numeric(14,4)` justamente por eso. Con una moneda de tres o cuatro decimales, **el libro redondearía en silencio lo que alguien pagó**.

No se cambió el esquema —lo fija un documento aprobado, y la decisión es del responsable del proyecto— y **no se dejó pasar**: `RegisterSaleService` rechaza al registrar el precio que no quepa, que es el único momento en que alguien está mirando. Hoy la única moneda sembrada es `USD` con dos decimales, de modo que esa rama no se alcanza.

**Lo que hay que decidir** es si los importes del libro pasan a `numeric(14,4)` —como los del catálogo— o si el sistema declara que no admitirá monedas de más de dos decimales. Mientras no se decida, el rechazo es la postura segura.

## 6. Definición de terminado

- [x] Las dieciocho tareas `Hecha` con su verificación pasando, y `./mvnw clean verify` en verde — **963 pruebas, 0 fallos**.
- [x] **Las suites de `PM` y de `SP` en verde sin cambios.** Lo que se les añadió son métodos nuevos sobre interfaces existentes y una interfaz nueva; **ninguna de sus pruebas se tocó**.
- [x] `CA-MV-007` pasando, que es la que afirma que **registrar una venta no cambia nada fuera del módulo**.
- [ ] Todos los criterios con prueba automatizada. **`CA-MV-008` no la tiene**, y su ausencia está declarada en §4 en lugar de darse por cubierta.
- [x] La matriz de trazabilidad y el contrato publicado al día.
