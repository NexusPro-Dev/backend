# TASKS — `RF-MV-001` Registrar una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-001` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.1.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

!!! warning "Estas tareas se pueden escribir hoy y no se pueden terminar hoy"

    Cinco de ellas necesitan **clientes que cuelguen de un vendedor** y el estado **`FTD_PENDIENTE`**, y las dos cosas las crea `RF-SP-045`, que no tiene una línea de código. Ver §4.

    Lo que sí se puede hacer desde el primer día es todo lo demás: el esquema, el agregado, el código de comprobante y las lecturas cruzadas.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V53`: `movement_types` y `payment_methods`, **creadas y sembradas en la misma migración** — `VENTA`/`VTA`, `EFECTIVO` y `TRANSFERENCIA` | — | Ninguna versión del esquema tiene la tabla de tipos vacía | **Pendiente** |
| `T-02` | `V53`: `movements`, **sin `updated_at` ni `deleted_at`**, con `ck_movements_status`, `ck_movements_payable`, `ck_movements_amounts`, `ck_movements_confirmed` y `uq_movements_code` | `T-01` | Un `UPDATE` que cambie un importe no lo impide el esquema, pero **ninguna operación lo hace**; los cuatro `CHECK` rechazan sus casos | **Pendiente** |
| `T-03` | `V53`: `movement_details`, con `uq_movement_details_producto`, `ck_movement_details_quantity` y `ck_movement_details_validity` **con la rama nula delante** | `T-02` | Una segunda línea del mismo producto la rechaza el motor, no la aplicación | **Pendiente** |
| `T-04` | `V51`: sembrar los cuatro permisos `movements:` **y asociarlos SOLO a `SUPERADMIN`** — la reserva de [`mv.md` §6.1](../../../requirements/mv.md), que se aparta de `security.md` §4.4 | — | Cubierta por `T-16`, que es una prueba y no una lectura del `SQL` | **Hecha** — 02-09-2026 |
| `T-05` | `PM`: `ProductCatalog` gana la **vista de venta por lote** —precio, moneda, tipo, vigencia y membresía destino— sin tocar la vista existente | — | La suite de `PM` sigue en verde **sin cambios**, y `CM` no recompila por esto | **Pendiente** |
| `T-06` | `PM`: `ProductCatalog` gana la **consulta de oferta por lote**, que reutiliza lo que `RF-PM-007` ya resuelve | `T-05` | Una sola llamada resuelve las cinco líneas de una venta de cinco productos | **Pendiente** |
| `T-07` | `SP`: `ClientCatalog` nuevo — **estado, vendedor vigente y nivel de membresía** del cliente | — | La suite de `SP` sigue en verde sin cambios | **Pendiente** |
| `T-08` | `Movement`, `MovementLine` y `MovementStatus`, con **el total calculado por el agregado** y la instantánea de auditoría armada por él | — | No existe forma de construir una venta cuyo total no sea la suma de sus líneas | **Pendiente** |
| `T-09` | `MovementCode`: prefijo, día de **la fecha del hecho en `America/Bogota`** y seis caracteres del alfabeto de Crockford **sin `I`, `L`, `O` ni `U`** | — | Una venta de las 23:30 en Bogotá lleva **su** día, no el siguiente | **Pendiente** |
| `T-10` | `MovementRepository` y su adaptador: cabecera y líneas en una sola transacción, con **reintento acotado** ante colisión de código | `T-02`, `T-03`, `T-09` | Con el generador forzado a colisionar, el tercer intento falla en lugar de reintentar sin fin | **Pendiente** |
| `T-11` | `RegisterSaleService`: el orden de `spec.md` §8 — cliente, vendedor, composición, oferta, moneda, copia, totales, código | `T-05`–`T-10` | Una petición con producto repetido **no llega a consultar la oferta** | **Pendiente** |
| `T-12` | DTOs de entrada y salida, con **el vendedor resuelto** y el descuento en cero | `T-11` | La respuesta lleva el vendedor que el actor no envió | **Pendiente** |
| `T-13` | `POST /api/v1/movements`, con el reparto de códigos de `plan.md` §4 | `T-11`, `T-12` | `201` con `Location`; `400`, `403`, `409` y `422` cada uno en su caso | **Pendiente** |
| `T-14` | Registro de auditoría de creación, **con el vendedor congelado dentro** | `T-11` | La instantánea permite responder a quién se atribuyó la venta sin reconstruir la estructura comercial | **Pendiente** |
| `T-15` | Pruebas de los criterios de `spec.md` §12 | `T-13` | `CA-MV-001` a `CA-MV-018` | **Pendiente** |
| `T-16` | **Prueba de la siembra**: los cuatro permisos existen, **están asociados a `SUPERADMIN`** y **ninguno lo está a `ADMIN`** | `T-04` | Es la única tarea que delata que la asociación se cayó de `V51`, y la única que fija que la reserva sea entera y no a medias | **Hecha** — 02-09-2026 |
| `T-17` | Documentación OpenAPI: que **el precio no se envía** y que la venta **nace pendiente y no concede nada** | `T-13` | El contrato publicado dice las dos cosas | **Pendiente** |
| `T-18` | **Comprobar que las tres enmiendas de `plan.md` §8 siguen valiendo** tras el código, y llevar la matriz al estado final | `T-15` | Las cuatro tablas dejan de estar «diseñadas y sin escribir» en `modelo-datos.md`, y las tres lecturas de `architecture.md` §15.2 existen con esa forma | **Pendiente** |

## 2. Orden de ejecución

**`T-01` y `T-04` no dependen de nada y son las que menos se parecen al resto.** La primera siembra catálogos y la segunda permisos: las dos se pueden hacer el primer día y las dos son las que más fácil se dan por hechas sin comprobarse, porque una migración que se aplica **parece** correcta.

**`T-04` y `T-16` se hicieron el 02-09-2026, antes que ninguna otra**, y con dos apartados de lo que esta tabla decía. El primero es la reserva: los cuatro permisos **no se asocian a `ADMIN`**, por decisión del responsable del proyecto, con la consecuencia que [`mv.md` §6.1](../../../requirements/mv.md) deja escrita — mientras siga en pie, `RN-SEG-003` impide que la fuerza comercial declare `movements:create`, de modo que lo que este requerimiento construya lo podrá ejecutar el superadministrador y nadie más. El segundo es el número: la siembra vive en `V51__seed_movements_permissions.sql` **sola**, sin las cuatro tablas, y las tablas pasan a `V53`. `V51` estaba reservado por `RN-SP-025` y `V52` por este requerimiento, y **ninguna de las dos estaba escrita**: el número lo toma quien se aplica primero, porque Flyway deja fuera una migración con número por debajo del último aplicado.

**`T-05`, `T-06` y `T-07` van antes que el caso de uso y no después.** Son código en módulos ajenos, y son **el trabajo de mayor riesgo de este requerimiento**: si `PM` o `SP` no pueden publicar lo que hace falta con la forma que hace falta, es mejor descubrirlo antes de haber escrito el agregado que lo consume.

**`T-08` y `T-09` no dependen del esquema**, y conviene hacerlas mientras las migraciones se revisan: el agregado y el código de comprobante se prueban enteros sin base de datos.

**`T-11` tiene el orden dentro escrito en su verificación, y no es un detalle de eficiencia.** Comprobar la oferta es lo caro; hacerlo antes de saber si la petición está bien formada gasta ese trabajo para rechazarla por un producto repetido. La prueba que lo verifica cuenta consultas, no tiempos.

**`T-16` prueba una migración y no una regla de negocio**, y es deliberado. Todas las demás pruebas usan un actor al que la suite le concede permisos directamente; **ninguna se entera** de si el permiso está sembrado ni de a qué roles se asoció. Es el mismo argumento con el que `CM` escribió una prueba de esquema para su `DROP DEFAULT`. Con la reserva de §6.1 vale doble: la aserción de que **`ADMIN` no tiene ninguno de los cuatro** es lo único que distingue una reserva decidida de media reserva que nadie eligió.

**`T-18` va la última, y no es «aplicar las enmiendas»: es comprobarlas.** Las tres se aplicaron el 02-09-2026, junto con este plan y antes de una sola línea de código — que es el orden que el responsable del proyecto fijó: primero el documento del módulo, después el modelo de datos, después las tripletas, y el código al final. Lo que queda para el final es lo único que no se puede saber por adelantado: si lo construido **es** lo que esos tres documentos dicen.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-MV-001` | `T-02`, `T-10`, `T-13`, `T-15` |
| `CA-MV-002` | `T-07`, `T-11`, `T-12`, `T-15` |
| `CA-MV-003` | `T-05`, `T-08`, `T-11`, `T-15` |
| `CA-MV-004` | `T-02`, `T-08`, `T-15` |
| `CA-MV-005` | `T-03`, `T-11`, `T-15` |
| `CA-MV-006` | `T-09`, `T-15` |
| `CA-MV-007` | `T-11`, `T-15` |
| `CA-MV-008`, `CA-MV-009` | `T-07`, `T-11`, `T-13`, `T-15` |
| `CA-MV-010`, `CA-MV-011` | `T-05`, `T-06`, `T-11`, `T-15` |
| `CA-MV-012`, `CA-MV-013` | `T-03`, `T-11`, `T-15` |
| `CA-MV-014` | `T-05`, `T-11`, `T-15` |
| `CA-MV-015` | `T-01`, `T-11`, `T-15` |
| `CA-MV-016` | `T-12`, `T-13`, `T-15` |
| `CA-MV-017` | `T-07`, `T-11`, `T-15` |
| `CA-MV-018` | `T-08`, `T-14`, `T-15` |

**`T-16`, `T-17` y `T-18` no cubren ningún criterio**, y quedan enumeradas para que su ausencia de esta tabla no se lea como que sobran. La primera defiende algo que el negocio no puede ver —que `ADMIN` pueda conceder lo que se acaba de sembrar—; la segunda es el contrato publicado; la tercera, los documentos que gobiernan.

## 4. Bloqueos

**`RF-SP-045` — el registro de clientes por enlace. Sin código.**

Es lo que crea **clientes que cuelgan de un vendedor** (`RN-SP-020`, rama de consumidor) y el estado **`FTD_PENDIENTE`** (`RN-SP-026`). Cinco tareas no se pueden terminar sin él:

| Tarea | Qué le falta |
|---|---|
| `T-07` | El vendedor de un cliente **no existe como concepto** hasta que alguien cuelgue clientes de la estructura comercial |
| `T-11` | `EX-002` y `EX-003` no tienen nada que comprobar |
| `T-15` | `CA-MV-008` y `CA-MV-017` no se pueden escribir |

**No bloquea empezar.** `T-01` a `T-06`, `T-08`, `T-09` y `T-10` son independientes, y son la mayor parte del trabajo.

**D-26 no bloquea este requerimiento**, y conviene decirlo porque bloquea al siguiente: registrar una venta **solo lee** de `SP`. La escritura —conceder el nivel comprado— aparece al confirmar, y es `RF-MV-003` quien no puede terminarse hasta que esa decisión se cierre.

## 5. Definición de terminado

- Las dieciocho tareas `Hecha` con su verificación pasando, y `./mvnw clean verify` en verde.
- **Las suites de `PM` y de `SP` en verde sin cambios**: lo que se les añadió son métodos nuevos, y si alguna de sus pruebas cambia es que se modificó algo suyo.
- `CA-MV-007` pasando, que es la que afirma que **registrar una venta no cambia nada fuera del módulo**.
- La matriz de trazabilidad y el contrato publicado al día.
