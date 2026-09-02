# PLAN — `RF-CM-007` Asociar una tasa de rol a un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-007` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. La mecánica común la fijó el plan de [`RF-CM-001`](../001-registrar-tasa-comision-rol/plan.md) y **este documento la hereda sin repetirla**.

---

## 1. Enfoque

Una tabla de asociación con **clave primaria compuesta**, y dos decisiones de esquema que convierten dos reglas de negocio en imposibilidades en lugar de en comprobaciones.

**Es el plan más corto del módulo y el que más regla mete en el esquema.** `RN-CM-013` no la comprueba ningún caso de uso, y que el rol copiado no pueda divergir tampoco.

## 2. Cambios de esquema

`V48` crea `product_commission_rates`: producto, rol, tasa y marca de creación.

### 2.1 La clave primaria ES la regla

```
PRIMARY KEY (product_id, role_id)
```

**`RN-CM-013` —un solo porcentaje por rol y producto— no es una regla que alguien comprueba: es la forma de la tabla.** Dos tasas del mismo rol sobre el mismo producto harían **indeterminada** la resolución, y la elección quedaría a criterio del plan de ejecución de la consulta.

Nótese que la clave **no incluye la tasa**. Es deliberado: lo que no puede repetirse es la pareja rol–producto, y si la tasa formara parte de la clave, dos tasas distintas del mismo rol sobre el mismo producto **cabrían las dos**.

### 2.2 El rol va copiado, y no puede divergir

`role_id` está en esta tabla aunque la tasa ya lo declare, y **no es la desnormalización que parece**: existe para que §2.1 pueda formarse. Sin él, la unicidad tendría que unir dos tablas, **y ningún índice lo hace**.

Lo que impide que mienta es que la clave foránea es **compuesta**:

```
FOREIGN KEY (commission_rate_id, role_id) REFERENCES commission_rates (id, role_id)
```

**Copiar un rol distinto del que la tasa declara es imposible, no improbable.** Y ese es el motivo de que `RF-CM-001` declare un `UNIQUE (id, role_id)` redundante con su clave primaria: PostgreSQL exige que el destino de una foránea compuesta sea una restricción única sobre exactamente esas columnas.

**Sin esa foránea compuesta el daño no sería una fila fea**: la resolución buscaría por un rol y **pagaría el porcentaje de otro**.

### 2.3 Sin retiro lógico, a propósito

Una asociación **no es un hecho del pasado que haya que conservar, es una configuración vigente**. Lo que hay que conservar —con qué porcentaje se pagó— es obligación de la liquidación (`RN-CM-008`).

**Esta decisión tiene una consecuencia que se pagó en otro requerimiento**: al no haber `deleted_at`, una asociación sobreviviría al retiro lógico de su tasa apuntando a una fila muerta. Es lo que obligó a `RN-CM-015` en `RF-CM-004`. **Las dos decisiones son correctas por separado y juntas abrían un agujero**, y se cierra donde se puede cerrar.

## 3. Componentes afectados

| Capa | Componente | Nuevo | Responsabilidad |
|---|---|---|---|
| `domain/models` | `ProductCommissionRate` | Sí | La asociación, con clave compuesta |
| `domain/repository` | `ProductCommissionRateRepository` y su adaptador | Sí | Escritura, con la traducción del conflicto |
| `domain/service` | `AssociateProductService` | Sí | Caso de uso |
| `application` | `AssociateProductRequest`, `ProductAssociationResponse` | Sí | Un campo de entrada; la colección envuelta de salida |
| `interfaces` | `CommissionRateController` | **Modificado** | `POST /api/v1/commission-rates/{id}/products` |

**La asociación cuelga de la tasa y no es un recurso raíz**, porque no existe sin ella: una asociación es «esta tasa, sobre este producto».

**El agregado se construye con la tasa entera y no con su identificador**, y esa firma es la que hace que el rol se copie de donde debe. Aceptar el rol por parámetro habría dejado la puerta abierta a pasarle otro.

## 4. Por qué no se comprueba antes de escribir

`RN-CM-013` **no se comprueba con una consulta previa**, y es la misma razón que en `RF-CM-006`: dos peticiones simultáneas leerían las dos que no hay conflicto y las dos insertarían. Se escribe y **se traduce la violación** de la clave primaria.

La traducción mira el nombre de la restricción y, como respaldo, el `SQLState` **`23505`** —violación de unicidad—. Al revés que con la exclusión de `RF-CM-006`, aquí el proveedor de persistencia **sí** da el nombre; el respaldo está por si alguna vez no lo diera.

**El mensaje habla del rol y no de la tasa**, por lo que dice `spec.md` §10: el conflicto puede ser con una tasa distinta, y decir «esta tasa ya está asociada» mandaría a buscar el problema en el sitio equivocado.

## 5. Contrato de API

`POST /api/v1/commission-rates/{id}/products` · `201 Created`.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-001`, y **enviar un campo que la petición no declara** |
| `403` | Sin el permiso `commissions:update` |
| `404` | `EX-001`: la tasa no existe o está retirada |
| `409` | `EX-002`: el producto está retirado · `EX-004`: ese rol ya paga por ese producto |
| `422` | `EX-003`: el producto no existe |

**El producto retirado es `409` y no `400`**: el dato es correcto y existe — lo que falla es una regla de negocio sobre el estado en que está. Es la misma traducción que `PM` usa.

**Enviar `roleId` da `400` y no se ignora**, porque la petición no lo declara y el deserializador rechaza lo desconocido. **Es el comportamiento que se quiere**: descartarlo en silencio haría creer a quien lo envió que el rol se había aplicado.

**Devuelve `201` con todas las asociaciones de la tasa**, no solo la nueva. La colección va **envuelta** para que añadir paginación algún día no rompa a nadie.

## 6. Autorización

Permiso `commissions:update`. Alcance global explícito.

**Es lo único que pone una tasa en vigor y no estrena permiso propio.** La decisión está declarada como discutible en `cm.md` §6 y en `spec.md` §14.

## 7. Auditoría

Registro de **cambios**, acción de creación, con el producto, el rol, la tasa y **el porcentaje**.

**El porcentaje viaja aunque sea de la tasa y no de la asociación**, y aquí tiene un motivo que en la lectura no tiene: es lo que permite reconstruir **qué se puso en vigor ese día** sin depender de que la tasa siga diciendo lo mismo — que, siendo `RF-CM-003` lo que es, puede no ser el caso.

## 8. Transaccionalidad

`@Transactional`. La comprobación de que la tasa está viva y la escritura ocurren en la misma transacción, y eso es lo que cierra la ventana que `RF-CM-004` §7 menciona.

## 9. Impacto sobre otros módulos

**Ninguno en el código.** Se consume `ProductCatalog`, que `PM` ya publica.

**Y es la lectura que `CM` estrenó como primer módulo que depende de dos.** La frontera de **D-25** se sostiene: esta clase no importa entidades ni repositorios de `PM`. La clave foránea hacia `products` **sí** cruza, y no la contradice — lo que `modules.md` §7 defiende es la frontera del código, y la integridad se declara en el motor (Art. V.6).

## 10. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **El producto como columna de la tasa** | Obliga a duplicar la tasa una vez por producto, y a corregir cincuenta filas al cambiar un porcentaje — con una que se queda atrás |
| **Clave primaria que incluya la tasa** | Dos tasas distintas del mismo rol sobre el mismo producto **cabrían las dos**, y la resolución sería indeterminada |
| No copiar el rol | La unicidad de `RN-CM-013` tendría que unir dos tablas. Ningún índice lo hace |
| Copiar el rol con foránea **simple** hacia `commission_rates(id)` | El rol copiado podría divergir, y la resolución **pagaría el porcentaje de otro rol** |
| Comprobar `RN-CM-013` con una consulta previa | Es una carrera |
| Recibir el rol en la petición | Permite enviar uno distinto, y el error llegaría como problema de integridad en lugar de como un dato que nadie tenía que dar |
| Dar retiro lógico a la asociación | Convierte una configuración vigente en un historial que nadie consultará, y obliga a filtrar por `deleted_at` en la consulta más caliente del módulo |
| Sustituir la asociación existente en silencio | Cambiar lo que paga un producto es una decisión, no un efecto secundario de otra |
| Que la operación sea idempotente | El conflicto de `EX-004` es **informativo**: dice que ese rol ya cobra, posiblemente con otra tasa |
| Devolver solo la asociación creada | La pregunta que viene después es «¿sobre qué rige ahora?», y exigiría una segunda llamada |

## 11. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | Dos asociaciones simultáneas del mismo rol al mismo producto | La clave primaria. `CA-CM-071` lo prueba concurrente |
| 2 | El rol copiado divergir del de la tasa | La foránea **compuesta**. Es imposible, no improbable |
| 3 | **La asociación sobreviva al retiro lógico de su tasa** | `RN-CM-015`, en `RF-CM-004`. No se puede cerrar desde aquí: una clave foránea no distingue una fila viva de una retirada lógicamente |
| 4 | Alguien asocie y crea que ya está todo, sin saber que la tasa puede corregirse después | `RF-CM-003` §13 y el porcentaje en la auditoría de esta operación |

## 12. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Asociar y devolver todas | Integración | `CA-CM-063` |
| **La asociación pone en vigor** | Integración | `CA-CM-064`, verificado **resolviendo**: es la otra mitad de `CA-CM-039` |
| **El rol se copia de la tasa** | Integración | `CA-CM-065`: enviar otro da `400` **y no escribe nada**; por la vía buena, el rol en base es el de la tasa |
| `RN-CM-013` | Integración | `CA-CM-066`, con **otra tasa del mismo rol**, que es el caso que un `UNIQUE` sobre la tasa dejaría pasar |
| Varios roles, varios productos | Integración | `CA-CM-067`, `CA-CM-068`. El segundo comprueba que **la tasa no se duplica** |
| Producto retirado e inexistente | Integración | `CA-CM-069`: `409` frente a `422` |
| Tasa retirada | Integración | `CA-CM-070` |
| **Concurrencia** | Integración concurrente | `CA-CM-071`: dos asociaciones simultáneas, una `201` y otra `409`, **ninguna `500`**, **una sola fila** |
| Permiso | Integración | `CA-CM-072` |
| El agregado | Unitaria | Que `create(...)` copie el rol **de la tasa** que recibe |

**`CA-CM-066` usa otra tasa del mismo rol y no la misma dos veces**, y esa elección es la prueba. Repetir la misma tasa fallaría también con una clave primaria mal puesta sobre `(product_id, commission_rate_id)`; **solo el caso de dos tasas distintas distingue la clave correcta de la equivocada**.
