# TASKS — `RF-CM-001` Registrar una tasa de comisión por rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-001` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 1.0.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/flujos-de-pm-y-cm` (`T-01`–`T-15`) · `feature/comision-en-valor-fijo` (`T-16`–`T-27`) |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

    **Las veintisiete están hechas y verificadas el 02-09-2026.** La tercera compuerta del Art. I.6 sigue pendiente, y por eso el documento está `En revisión`.

---

## 1. El módulo sobre tres tablas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V49`: soltar las restricciones y **dejar caer** `product_id`, `user_id`, `valid_from` y `valid_to` | — | La tabla queda con rol, porcentaje y marcas de tiempo | **Hecha el 02-09-2026** |
| `T-02` | `V49`: **vaciar** `commission_rates`, con el motivo escrito en la migración | `T-01` | Ninguna fila del modelo anterior sobrevive | **Hecha el 02-09-2026** |
| `T-03` | `V49`: `uq_commission_rates_id_role`, **redundante con la PK a propósito** | `T-01` | La clave foránea compuesta de `RF-CM-007` puede declararse | **Hecha el 02-09-2026** |
| `T-04` | `CommissionRate`: rol, porcentaje, y nada más | `T-01` | El agregado no compila con referencias a producto o vigencia | **Hecha el 02-09-2026** |
| `T-05` | **Retirar `RateScope`** y crear `RateSource` | `T-04` | No queda ninguna referencia a los cuatro grados | **Hecha el 02-09-2026** |
| `T-06` | Puerto de escritura **sin bloqueo ni consulta de solapamiento** | `T-04` | El módulo compila sin ellos | **Hecha el 02-09-2026** |
| `T-07` | `CommissionRows`: las conversiones del driver, en un solo sitio | — | Los tres adaptadores de consulta la usan | **Hecha el 02-09-2026** |
| `T-08` | Puerto de consulta con la **cuenta de asociaciones como subconsulta correlacionada** | `T-07` | Una tasa con dos asociaciones aparece **una vez**, no dos | **Hecha el 02-09-2026** |
| `T-09` | `RegisterCommissionRateService`, con la única verificación que queda | `T-04` | Rol inexistente `422`, rol no vendedor `400` | **Hecha el 02-09-2026** |
| `T-10` | DTOs de entrada y salida, con `associatedProducts` | `T-09` | La respuesta del alta lo devuelve en cero | **Hecha el 02-09-2026** |
| `T-11` | `POST /api/v1/commission-rates` | `T-09`, `T-10` | `201` con `Location`, `400`, `403`, `422` | **Hecha el 02-09-2026** |
| `T-12` | Registro de auditoría de creación, armado por el agregado | `T-09` | La instantánea lleva rol y porcentaje | **Hecha el 02-09-2026** |
| `T-13` | Pruebas de los criterios de `spec.md` §12 | `T-11` | `CA-CM-001` a `CA-CM-008` | **Hecha el 02-09-2026** |
| `T-14` | Documentación OpenAPI, con el aviso de que **lo registrado no rige** | `T-11` | El contrato publicado lo dice en la descripción | **Hecha el 02-09-2026** |
| `T-15` | Aplicar las cuatro enmiendas del rediseño (`plan.md` §8) | `T-13` | Los cuatro documentos con su fila de control de cambios | **Hecha el 02-09-2026** |

### 1.1 El valor fijo

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-16` | `V50`: las tres columnas **en las dos tablas, en una sola migración** | `T-01` | No existe ninguna versión del esquema en la que una pieza admita el valor fijo y la otra no | **Hecha el 02-09-2026** |
| `T-17` | `V50`: rellenar `rate_type` con `DEFAULT 'PORCENTAJE'` **y quitarle el valor por defecto acto seguido** | `T-16` | Cubierta por `T-25`, que es una prueba y no una lectura de la migración | **Hecha el 02-09-2026** |
| `T-18` | `V50`: `ck_*_type`, `ck_*_forma` y `ck_*_fixed` en las dos tablas | `T-16` | Una fila `FIJO` con porcentaje es rechazada por el motor, no por la aplicación | **Hecha el 02-09-2026** |
| `T-19` | `V50`: **rehacer** `ck_*_percentage` con la rama nula **delante y explícita** | `T-16` | Sigue rechazando 101 en una fila `PORCENTAJE`, y acepta el nulo de una `FIJO` | **Hecha el 02-09-2026** |
| `T-20` | `CommissionRateType` y `CommissionValue`, con `RN-CM-016` **en el constructor** | — | No hay forma de construir un valor inconsistente, ni desde una corrección | **Hecha el 02-09-2026** |
| `T-21` | `CommissionValue` lanza **`ValidationException`**, no `BusinessRuleException` | `T-20` | El rechazo llega como `400` con `FieldError`, igual que `VAL-005` | **Hecha el 02-09-2026** |
| `T-22` | `CommissionRate` **incrusta** `CommissionValue` en lugar de llevar el porcentaje suelto | `T-20` | El agregado no compila con un `percentage` de primer nivel | **Hecha el 02-09-2026** |
| `T-23` | DTOs: `rateType` y `fixedAmount` entran, `percentage` **deja de ser obligatorio** | `T-22` | Una petición sin `rateType` da `400` con `VAL-002` | **Hecha el 02-09-2026** |
| `T-24` | La respuesta devuelve **la forma junto al valor**, y el campo de la otra forma **vacío, no omitido** | `T-23` | Ningún consumidor tiene que deducir la forma por qué campo venga | **Hecha el 02-09-2026** |
| `T-25` | **Prueba de esquema**: un `INSERT` directo **sin** `rate_type` falla | `T-17` | Es la única tarea que puede delatar que `DROP DEFAULT` se cayó de `V50` | **Hecha el 02-09-2026** |
| `T-26` | Pruebas de los criterios nuevos de `spec.md` §12 | `T-24` | `CA-CM-079` a `CA-CM-084` | **Hecha el 02-09-2026** |
| `T-27` | OpenAPI: **el cambio incompatible** y que el importe **no lleva moneda** | `T-24` | La descripción dice las dos cosas, y por qué | **Hecha el 02-09-2026** |

**La instantánea de auditoría no lleva tarea propia**, y es correcto: la arma el agregado a partir de `CommissionValue` (`plan.md` §6), de modo que `T-22` la actualiza sola. Su verificación va en `T-26`.

## 2. Orden de ejecución

**`T-02` es la única tarea irreversible del módulo y por eso va con `T-01`, no después.** Dejar el borrado para más adelante habría dado una ventana en la que la tabla tiene la forma nueva y filas con el significado viejo — que es exactamente la situación que la migración existe para evitar.

**`T-03` parece prescindible y no lo es.** Es la restricción única redundante con la clave primaria, y sin ella `RF-CM-007` **no puede declarar** su clave foránea compuesta. Se hace aquí porque es un cambio sobre esta tabla, aunque quien la necesite sea otro requerimiento.

**`T-17` es una sola tarea para dos sentencias que un lector separaría**, y están juntas a propósito. Poner el valor por defecto y quitarlo son **una operación**: el relleno lo necesita, el esquema definitivo no puede conservarlo, y dejarlas en tareas distintas permitiría dar la primera por hecha y aplazar la segunda. Aplazarla es el fallo silencioso de `V50` (`plan.md` §10, riesgo 5).

**`T-20` va antes que `T-16` en la práctica, aunque no dependa de ella.** El objeto de dominio es donde `RN-CM-016` se decide; los `CHECK` de `T-18` son la segunda línea. Escribir primero la migración invita a dar la regla por cerrada porque el motor la vigila — y el motor **no puede dar un mensaje** que distinga las tres formas de equivocarse.

**`T-19` es la tarea que nadie pediría y sin la cual `V50` miente.** No añade ninguna comprobación: la restricción de `V44` seguiría ahí y seguiría pasando. Lo que hace es **volver visible** que ya no comprueba las filas de tipo `FIJO`, porque un `CHECK` que evalúa a nulo acepta la fila. Sin ella, quien lea el esquema dentro de un año no podrá saber si eso se decidió o se pasó por alto.

**`T-25` prueba una línea de `SQL` y no una regla de negocio**, y es deliberado. Las pruebas de `T-26` pasan todas por la API, que **siempre envía la forma**; ninguna se entera de si la columna conserva su valor por defecto. Es el mismo argumento de `CA-CM-075` en `RF-CM-008`: se prueba lo que el esquema **habría dejado pasar**.

**`T-08` tiene una verificación que no es la obvia.** No comprueba que la cuenta sea correcta —eso lo hace `CA-CM-002`— sino que la tasa **aparezca una sola vez**. Con un `LEFT JOIN` agrupado mal, cada tasa saldría una vez por producto y el `LIMIT` de la paginación contaría filas del producto cartesiano en lugar de tasas, **devolviendo menos tasas de las pedidas sin que nada fallara**.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-001` | `T-09`, `T-11`, `T-13` |
| `CA-CM-002` | `T-08`, `T-10`, `T-13` |
| `CA-CM-003` | `T-04`, `T-05`, `T-10`, `T-13` |
| `CA-CM-004` | `T-01`, `T-13` |
| `CA-CM-005` | `T-04`, `T-13` |
| `CA-CM-006`, `CA-CM-007` | `T-09`, `T-13` |
| `CA-CM-008` | `T-10`, `T-13` |
| `CA-CM-079` | `T-16`, `T-22`, `T-24`, `T-26` |
| `CA-CM-080`, `CA-CM-081` | `T-18`, `T-20`, `T-21`, `T-23`, `T-26` |
| `CA-CM-082`, `CA-CM-083` | `T-18`, `T-26` |
| `CA-CM-084` | `T-26` |

**`T-17`, `T-19`, `T-25` y `T-27` no cubren ningún criterio de aceptación**, y quedan enumeradas para que su ausencia de la tabla no se lea como que sobran. Las tres primeras defienden **cosas que el negocio no puede ver** —que la forma sea obligatoria en el motor, que una restricción vieja siga diciendo la verdad—; la cuarta es el contrato publicado. `T-25` es, además, la única verificación de `T-17`.

## 4. Bloqueos

Ninguno.

**Queda una deuda que no bloquea nada, y que conviene no perder de vista**: `T-01` a `T-15` se escribieron **después** del código, invirtiendo el Art. I.6 — esa mitad de la lista no planificó, registró. `T-16` a `T-27` se escribieron antes de existir, que es como debía ser. La deuda está declarada en `spec.md` §15 y en `requirements.md` v0.87.0, y **se salda del único modo en que puede saldarse**: dejándola escrita y no repitiéndola.

## 5. Definición de terminado

- Las veintisiete tareas `Hecha` con su verificación pasando. **Comprobado el 02-09-2026**: `./mvnw clean verify` en verde, **287 unitarias y 902 de integración**, incluida `T-25` — la que no se ve fallar de otro modo.
- La matriz y el contrato publicado al día, **con el cambio incompatible declarado**.
