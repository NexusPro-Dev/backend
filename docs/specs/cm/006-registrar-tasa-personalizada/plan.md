# PLAN — `RF-CM-006` Registrar la tasa personalizada de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-006` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. La mecánica común la fijó el plan de [`RF-CM-001`](../001-registrar-tasa-comision-rol/plan.md) y **este documento la hereda sin repetirla**.

---

## 1. Enfoque

Tabla propia con vigencia, y **la única restricción del módulo que dos peticiones simultáneas pueden burlar**.

**Es aquí donde vive todo lo que el catálogo por rol perdió**: la exclusión en el motor, el bloqueo que la acompaña, el volcado explícito y la traducción de la violación. No es duplicación — es que la vigencia se mudó, y con ella su maquinaria.

## 2. Cambios de esquema

`V48` crea `user_commission_rates`: identificador, persona, porcentaje, vigencia en `date`, marcas de tiempo y retiro lógico.

**`V49` le añade la forma y el valor fijo**, con exactamente los mismos tres cambios y las mismas tres restricciones que a `commission_rates`. **La migración es una sola y está argumentada en `RF-CM-001` §2.3**, incluido el detalle que más fácil se pierde —el `DROP DEFAULT` sobre `rate_type`—, y no se repite aquí.

Lo único que hay que decir en este documento es **por qué las dos tablas se tratan igual sin excepción**: la elección de forma es una propiedad del **valor de una comisión**, no de la pieza que lo declara. En cuanto una de las dos tablas admitiera algo que la otra no —un tope, un decimal más, una moneda—, `RF-CM-005` tendría que devolver dos cosas distintas según de dónde saliera la respuesta, y **la resolución dejaría de poder hablar de «la comisión efectiva» en singular**.

### 2.1 La vigencia se mide en `date` y no en `timestamptz`

Es la **excepción justificada** al criterio del proyecto, heredada de la tabla anterior: una comisión cambia «a partir del día 1», no a partir de las 00:00:00.000 de una zona horaria concreta. Declararla con instante obligaría a decidir **en qué zona se corta el día**, y esa no es una decisión que tenga que tomar quien declara una tasa.

### 2.2 `RN-CM-006` — ningún día cubierto dos veces

```
EXCLUDE USING gist (user_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
  WHERE (deleted_at IS NULL)
```

**Tiene que estar en el motor.** Comprobarlo con una consulta previa seguida de una inserción es una carrera: dos peticiones simultáneas leen que no hay solape y las dos insertan. Es exactamente el defecto que `RN-SP-018` tuvo y que se corrigió el 26-08-2026.

**No es una unicidad**: lo que no debe repetirse no es un valor, es un **intervalo**. Un `UNIQUE` no puede expresarlo.

Tres detalles que parecen cosméticos y no lo son:

| Detalle | Qué pasaría sin él |
|---|---|
| El rango lleva **los dos extremos incluidos** | Con el semiabierto que PostgreSQL usa por omisión, dos tasas consecutivas que comparten el día de corte **no chocarían** y ese día quedaría cubierto dos veces |
| La restricción es **parcial sobre las vivas** | Una tasa retirada seguiría bloqueando sus días, y retirar dejaría el periodo inutilizable **para siempre** — y nada más fallaría |
| Un `valid_to` nulo produce un rango **sin límite superior** | Es exactamente «rige indefinidamente», sin ningún valor centinela |

**El `COALESCE` de la versión anterior desaparece.** Allí normalizaba el producto y la persona nulos; aquí `user_id` es obligatorio. **La regla se simplificó con el modelo**, y conviene notarlo: la restricción más difícil del módulo se volvió legible.

### 2.3 Sin `role_id`

Por decisión del responsable del proyecto. **Lo que cuesta está en `spec.md` §13** y no se repite aquí, salvo la consecuencia técnica: **no hay ninguna restricción del esquema que pueda atar esta tasa al rol de su titular**, porque el rol vive en `user_roles` y una clave foránea no expresa «y que además siga teniéndolo».

## 3. Componentes afectados

| Capa | Componente | Nuevo | Responsabilidad |
|---|---|---|---|
| `domain/models` | `UserCommissionRate` | Sí | El agregado, con vigencia y los dos nulos opuestos. **Desde v0.2.0 incrusta `CommissionValue`** |
| `domain/repository` | `UserCommissionRateRepository` y su adaptador | Sí | Escritura, **con bloqueo y traducción** |
| `domain/repository` | `UserCommissionRateQueryRepository` y su adaptador | Sí | El listado (`RF-CM-002`) |
| `domain/service` | `RegisterUserCommissionRateService` | Sí | Alta |
| `domain/service` | `UpdateUserCommissionRateService` | Sí | Corrección |
| `domain/service` | `DeleteUserCommissionRateService` | Sí | Retiro |
| `application` | Los DTO de alta, corrección, ítem, página y respuesta | Sí | — |
| `interfaces` | `UserCommissionRateController` | Sí | Las cuatro operaciones |

**Recurso propio y no un filtro del de tasas de rol**, porque las dos piezas no se parecen: esta tiene vigencia y persona, aquella tiene rol y asociaciones. Fundirlas obligaba a un endpoint cuyas validaciones dependen de qué campo llegó — que es exactamente lo que había hasta el 01-09-2026.

**`DeleteCommissionRateRequest` se comparte con las tasas de rol.** Es un motivo y nada más; duplicarlo habría dado dos esquemas idénticos en el contrato publicado.

**`CommissionValue` también se comparte, y por un motivo más fuerte que el ahorro.** Lo funda `RF-CM-001` §3.1; aquí basta decir qué se gana con que sea **el mismo objeto y no dos iguales**: `RN-CM-016` se decide una vez, y **`RF-CM-005` puede devolver la comisión resuelta sin saber de cuál de las dos tablas salió**. Dos objetos gemelos obligarían a la resolución a elegir uno de los dos o a inventar un tercero al que convertir los dos.

!!! warning "La corrección de esta tasa **sustituye el valor entero**, y eso cambia lo que `Patchable` significa aquí"

    Hasta ahora la corrección trataba el porcentaje y el fin de vigencia como dos campos parcheables independientes, con los **dos nulos opuestos** de §7: vaciar el fin se obedece, vaciar el porcentaje se rechaza.

    Con la forma dentro, **el valor deja de ser un campo y pasa a ser una pareja**: quien corrige envía forma y valor juntos, y lo que se sustituye es el `CommissionValue` completo (`spec.md` §8). El fin de vigencia sigue siendo un `Patchable` con su nulo obediente; **el valor ya no lo es**, y esa asimetría hay que escribirla en el DTO de corrección para que nadie la deshaga por parecer inconsistente.

    Es lo que hace que `spec.md` `FA-006` funcione sin ninguna regla nueva: cambiar de forma es sustituir el valor, y el sistema no tiene que distinguir esa corrección de cualquier otra.

## 4. Las tres piezas que sostienen `RN-CM-006`

Ninguna basta sola, y por eso están las tres.

### 4.1 La restricción del motor

Es **la autoridad**. Lo demás la acompaña.

### 4.2 El bloqueo consultivo por persona

**Existe por algo medido y no previsto** (28-08-2026, sobre la tabla anterior): sin él, dos altas simultáneas con rangos que se solapan **se interbloquean** —cada una espera a que la otra confirme su entrada en el índice— y PostgreSQL aborta una con `40P01`. El cliente recibía un `500` en lugar del `409` que le toca.

Es **bloqueante y no un intento**, al revés que el de la jerarquía de roles: aquí no se quiere rechazar a la segunda peticionaria, se quiere que **espere y compruebe**. Una colisión de hash serializa a dos personas que no compartían nada — pérdida de paralelismo, no fallo de corrección.

### 4.3 La traducción de la violación

**Hibernate no da el nombre de la restricción cuando la violación es de EXCLUSIÓN.** Su extractor reconoce los mensajes de `UNIQUE` y de clave foránea, y ante «conflicting key value violates exclusion constraint» devuelve nulo — comprobado el 28-08-2026, y el síntoma era un `500` donde tocaba un `409`.

De ahí que se mire el **`SQLState`**, que es igual de estructural y además estándar: **`23P01` es, y solo es, violación de restricción de exclusión**. No se lee el texto del mensaje, de modo que la regla del proyecto —traducir por algo que no cambie entre versiones del driver— se sigue cumpliendo.

**Lo que esto asume, y hay que saberlo:** esta tabla tiene **una sola** restricción de exclusión. El día que se añada una segunda, el estado deja de identificarla sin ambigüedad.

**Y se atrapa `RuntimeException` y no `PersistenceException`**: según por dónde salga el volcado, el fallo llega envuelto de una forma o de otra, y atrapando solo una el mismo defecto se escapa como `500` en un camino y no en el otro.

## 5. Contrato de API

| Verbo y ruta | Devuelve |
|---|---|
| `POST /api/v1/user-commission-rates` | `201` con `Location` |
| `GET /api/v1/user-commission-rates` | `200`, página |
| `PATCH /api/v1/user-commission-rates/{id}` | `200` |
| `POST /api/v1/user-commission-rates/{id}/deletion` | `204` |

| Estado | Cuándo |
|---|---|
| `400` | Las validaciones, y los campos no corregibles |
| `403` | Sin el permiso correspondiente |
| `404` | La tasa no existe o está retirada |
| `409` | **Solapamiento**, o ya estaba retirada |
| `422` | La persona no existe |

## 6. Autorización

`commissions:create`, `commissions:read`, `commissions:update` y `commissions:delete`, los mismos cuatro que las tasas de rol.

**No se estrena ninguno**, y es deliberado: el grado de una tasa —de rol o de persona— es un **dato**, no una operación distinta. Distinguirlo en el permiso obligaría a mantener sincronizados el modelo de permisos y la forma de las tablas.

## 7. Auditoría

Cambios en el alta y en la corrección; **eliminación** en el retiro, con motivo e instantánea.

**La instantánea del retiro conserva la vigencia**, y esa es la decisión con nombre de este plan: **la vigencia no se toca al retirar**. Cerrarla «de paso» dejaría la tabla más ordenada y **destruiría la evidencia** — el registro de eliminación debe poder decir **qué periodo cubría** lo retirado, y si el retiro la modificara, todas las instantáneas dirían lo mismo y ese dato dejaría de significar nada.

Es el criterio con el que `RF-PM-006` no toca el estado de un producto al retirarlo: **la salvaguarda no puede destruir lo que protege**. Y es lo único de aquel razonamiento que sobrevivió al rediseño, porque es la única tabla que conserva vigencia.

## 8. Transaccionalidad

`@Transactional` en las tres escrituras.

**En la corrección, el orden de dos llamadas no es cosmético:**

1. **El bloqueo se toma antes de tocar la entidad.** Es una consulta nativa, y el proveedor de persistencia vuelca lo pendiente antes de ejecutar una. Tomándolo después de aplicar el cambio, ese volcado ocurriría **dentro del bloqueo y fuera de todo `try`**, y la violación volvería a escaparse como `500`.
2. **El volcado explícito va antes de auditar.** La entidad está gestionada y la actualización saldría en la confirmación, **fuera de todo `try`**. Es exactamente lo que le ocurrió a `RF-SP-027` con el correo duplicado.

## 9. Impacto sobre otros módulos

**Ninguno en el código.** Se consume `UserCatalog`, que `SP` ya publica.

## 10. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Mantenerla en el mismo endpoint que la tasa de rol** | Obliga a validaciones que dependen de qué campo llegó. Es lo que había y lo que el rediseño deshizo |
| **Conservarle el `role_id`** | Decisión del responsable del proyecto en contra. Su coste está declarado en `spec.md` §13 |
| Exigir que la persona porte rol vendedor | Sin `role_id` no hay **qué** rol exigir. Exigir «alguno» sería una regla distinta que nadie pidió |
| Comprobar el solapamiento con una consulta previa | Es una carrera. Es el defecto de `RN-SP-018`, corregido el 26-08-2026 |
| Un `UNIQUE` en lugar de un `EXCLUDE` | Lo que no debe repetirse es un intervalo, no un valor |
| Rango semiabierto | Dos tasas consecutivas compartirían el día de corte sin chocar |
| Restricción sobre todas las filas y no solo las vivas | Retirar dejaría el periodo inutilizable para siempre, **y nada más fallaría** |
| Vigencia en `timestamptz` | Obliga a decidir en qué zona se corta el día. Ver §2.1 |
| Cerrar la vigencia al retirar | Destruye el dato de qué periodo cubría lo retirado. Ver §7 |
| Bloqueo no bloqueante, como en la jerarquía de roles | Allí se quiere rechazar a la segunda; aquí se quiere que espere y compruebe |
| Traducir por el texto del mensaje del driver | Cambia entre versiones y convertiría la traducción en un `500` silencioso |
| **Un `CommissionValue` propio de esta tabla**, gemelo del de las tasas de rol | `RN-CM-016` se decidiría dos veces, y `RF-CM-005` tendría que elegir uno de los dos o inventar un tercero al que convertir ambos |
| Dejar que esta tabla admita **solo porcentaje**, y el valor fijo solo en el catálogo por rol | Simplifica el módulo y **rompe la resolución**: obligaría a `RF-CM-005` a devolver cosas de forma distinta según de dónde saliera la respuesta. Ver §2 |
| **Darle moneda propia a la tasa personalizada**, aunque el catálogo por rol no la tenga | Es donde más falta hace y donde menos se puede: no hay producto con el que comprobar que coincide, de modo que sería un dato que nadie valida y que la liquidación tendría que decidir si respetar o ignorar |
| Exigir que el fin de vigencia exista cuando la forma es **valor fijo** | Sería inventar una regla para acotar `RN-CM-018` por un lado que no es el suyo. Un importe desmesurado con fecha de caducidad sigue siendo desmesurado |
| **Prohibir corregir la forma**, obligando a cerrar y abrir | Descartado en `spec.md` §14, no aquí. Convierte arreglar una equivocación en una pérdida de datos |

## 11. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Una tasa sobreviva a que su titular deje de vender, y siga pagando** | **No se mitiga.** Es consecuencia declarada de no llevar rol (`spec.md` §13). Cerrarla exige un acto deliberado — retirarla o ponerle fin |
| 2 | Un solapamiento salga como `500` | Las tres piezas de §4, y la prueba concurrente |
| 3 | Se añada una segunda restricción de exclusión a la tabla | La traducción por `SQLState` dejaría de identificarla. Declarado en §4.3 y en el código |
| 4 | Alguien cierre la vigencia al retirar «para dejarlo ordenado» | §7, y `CA-CM-059` lo comprueba |
| 5 | **Un importe fijo personalizado se cobre en monedas que nadie previó** | **No se mitiga, y aquí es peor que en el catálogo por rol**: esta tasa no se acota a ningún producto. `spec.md` §2 lo declara entero y `CA-CM-089` lo fija como comportamiento esperado |
| 6 | **Se corrija la forma de una tasa cuya vigencia ya pasó**, reescribiendo un periodo cerrado | Aceptado: es el mismo riesgo que corregir su porcentaje. `FA-006` dice cuál es la operación correcta para acordar un cambio —cerrar y abrir— y cuál para arreglar una equivocación |
| 7 | El valor deje de ser una pareja y vuelva a parchearse por campos sueltos | La asimetría con el fin de vigencia está escrita en §3, y `CA-CM-088` la comprueba corrigiendo la forma |

## 12. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Alta y persona resuelta | Integración | `CA-CM-051`, `CA-CM-052` |
| **Se admite a quien no vende** | Integración | `CA-CM-053`. Es la prueba que fija la consecuencia de §11.1 |
| Solapamiento | Integración | `CA-CM-054`, y **`CA-CM-055` con el día de corte**, que es la que verifica el rango cerrado |
| Consecutivas y personas distintas | Integración | `CA-CM-056`, `CA-CM-057` |
| **Retirar libera los días** | Integración | `CA-CM-058`. Verifica que la restricción sea **parcial** |
| **Retirar no cierra la vigencia** | Integración | `CA-CM-059` |
| Los dos nulos opuestos | Integración y unitaria | `CA-CM-060`: vaciar el fin se cumple, vaciar el porcentaje se rechaza |
| Inmutables y validaciones | Integración | `CA-CM-061`, `CA-CM-062` |
| **Concurrencia** | Integración concurrente | Dos altas simultáneas del mismo periodo: una `201`, otra `409`, **ninguna `500`**, y **una sola fila** |
| El agregado | Unitaria | Vigencia de un día, orden invertido, instantánea con el nulo como nulo |

| **Alta en valor fijo** | Integración | `CA-CM-085` |
| Las dos formas, ninguna, y la equivocada | Integración | `CA-CM-086`: `400` con `VAL-011`, **el mismo mensaje que en `RF-CM-001`** |
| **Consecutivas de formas distintas** | Integración | `CA-CM-087`: y las dos quedan, que es el historial que solo esta pieza conserva |
| **Corregir cambia la forma** | Integración | `CA-CM-088`: y el evento lleva el antes y el después **de las dos cosas**, no solo del número |
| **El importe fijo no distingue monedas** | Integración | `CA-CM-089`: resolviendo contra dos productos de monedas distintas, **el mismo importe y ninguna señal** |

**La prueba concurrente es la que verifica dónde vive la regla.** Las demás pasarían igual si el solapamiento se comprobara con una consulta previa; esta no.

**`CA-CM-089` es la única prueba de este requerimiento que necesita productos de `PM`**, y no rompe la frontera de D-25: no consulta el catálogo, **resuelve** (`RF-CM-005`) contra dos productos que existen, que es lo que hace un vendedor. Comprobarlo sin productos sería comprobar que un número es igual a sí mismo.

**`CA-CM-088` comprueba el evento y no solo el resultado**, y ahí está su valor. Que la tasa quede en valor fijo lo vería cualquier consulta; que **el registro de auditoría conserve que antes era un porcentaje** es lo único que permitirá entender, meses después, por qué un periodo ya liquidado dice una cosa y la tasa dice otra.
