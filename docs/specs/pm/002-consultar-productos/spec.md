# SPEC — `RF-PM-002` Consultar productos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-002` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendada el | 28-08-2026 — ver §15 |
| Enmendada el | 07-09-2026 — **las membresías resueltas traen su color** (`RN-SP-024`). Ver §15 |
| Enmendada el | 07-09-2026 — **dos filtros nuevos: alcance e implementación** (`RN-PM-019`, `RN-PM-020`). Ver §15 |

---

## 1. Objetivo

Ver y encontrar lo que hay en el catálogo, incluido lo que ya no se ofrece.

## 2. Contexto

Con `RF-PM-001` se puede **crear** un producto y no **verlo**: quien administra no tiene de dónde sacar la lista para corregir un precio, retirar una oferta o comprobar qué upgrade está publicado hacia cada nivel. Es el mismo hueco que `SP` tuvo entre `RF-SP-001` y `RF-SP-002`.

**Este listado no es la oferta.** Devuelve el catálogo completo —lo activo, lo inactivo y, si se pide, lo retirado— y lo lee quien administra o vende. Lo que un cliente puede comprar es `RF-PM-007`, que responde otra pregunta y a otro actor.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Consulta el catálogo para gobernarlo |
| Fuerza comercial | Consulta el catálogo para vender o para atender a un cliente |

## 4. Alcance

### 4.1 Incluye

- Devolver los productos **paginados**.
- Filtrar por tipo, por estado y por membresía **de origen o de destino**.
- Buscar por nombre, sin distinguir mayúsculas ni acentos.
- Incluir o excluir los productos retirados.
- Ordenar por **fecha de alta** salvo que el actor pida otro orden de la lista admitida.

### 4.2 No incluye

- **El detalle de un producto**, que es `RF-PM-003`.
- **La oferta de un cliente**, que es `RF-PM-007` y depende de su nivel.
- **Cuántas veces se ha vendido cada producto.** No existen las ventas todavía, y cuando existan será una pregunta de su módulo.
- **El filtro por rango de precio.** No es lo que necesita quien administra, y añadirlo después es un parámetro opcional más: no rompe a ningún cliente. Resuelto el 26-08-2026.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-010` | El producto no desaparece: el retiro es lógico | `requirements/pm.md` §5.1 |
| `RN-PM-019` | El alcance dice hasta dónde se muestra, y es **acumulativo** | `requirements/pm.md` §5.1 |
| `RN-PM-020` | La implementación dice si lo comprado se aplica solo o espera autorización | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página y tamaño | No | Qué porción del catálogo se pide | Con un tamaño máximo; pedir más se **rechaza**, no se recorta |
| Tipo | No | Filtra por upgrade o por bot | Uno de los dos valores admitidos |
| Estado | No | Filtra por activo o inactivo | Uno de los valores admitidos |
| Membresía destino | No | Filtra los upgrades que **llevan** a ese nivel | Un destino que no existe devuelve una colección vacía, no un error |
| Membresía de origen | No | Filtra los upgrades que **salen** de ese nivel | Igual. Es la pregunta «qué puede comprar quien está en `FREE`» hecha desde administración, y **no sustituye a `RF-PM-007`**: aquella responde sobre quien llama y esta sobre cualquiera |
| Alcance | No | Filtra por hasta dónde se muestra el producto | Uno de los valores admitidos. **Es el único sitio donde este dato se consulta hoy**: `RF-PM-007` no lo filtra y el canal de hotlinks no existe todavía |
| Implementación | No | Filtra por si lo comprado se aplica solo o espera autorización | Uno de los valores admitidos |
| Búsqueda | No | Coincidencia parcial sobre el nombre | En blanco equivale a ausente |
| Incluir retirados | No | Si se devuelven también los productos eliminados | Por omisión **no** se devuelven |
| Orden | No | Por qué campo se ordena y en qué sentido | **Lista cerrada**: nombre, precio o fecha de alta. Cualquier otro valor se rechaza |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Productos | Identificador, tipo, nombre, descripción, **icono**, precio con su moneda, **vigencia en días**, estado y —en los upgrades— la membresía destino con su nombre y su nivel |
| Alcance e implementación | En **todas** las filas y en los dos tipos: hasta dónde se muestra el producto y quién aplica lo que otorga |
| Marca de retiro | En los retirados, que lo están y desde cuándo |
| Total | Cuántos productos cumplen el filtro |
| Orden | El aplicado, para que quien recibe la página sepa sobre qué está paginando |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de productos.

**Postcondiciones**

- Ninguna: la consulta no modifica nada.

## 8. Flujo principal

1. El actor pide el catálogo, con los filtros que quiera.
2. El sistema valida los parámetros de paginación y los valores de los filtros.
3. El sistema aplica el orden pedido, o el de alta si no se pidió ninguno.
4. El sistema devuelve la página pedida y el total que cumple el filtro.

## 9. Flujos alternativos

### FA-001 — Catálogo vacío

**Cuándo ocurre:** no hay productos, o ninguno cumple el filtro.

1. El sistema devuelve una colección vacía con total cero. **No es un error**: un catálogo sin productos es un estado legítimo del sistema recién puesto en marcha.

### FA-002 — Página más allá de la última

**Cuándo ocurre:** el actor pide una página que no existe.

1. El sistema devuelve una colección vacía **con el total real**, no con un total deducido de la página pedida.

## 10. Excepciones

### EX-001 — Parámetros inválidos

**Condición:** la página o el tamaño están fuera de rango, o un filtro trae un valor que no pertenece a su dominio.
**Respuesta del sistema:** rechaza la consulta **enumerando todos los parámetros mal escritos a la vez**, para que quien se equivocó en cuatro no tenga que corregir la dirección cuatro veces.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Página y tamaño dentro de rango | Los parámetros de paginación están fuera del rango admitido. |
| `VAL-002` | Tipo dentro del dominio | El tipo indicado no es válido. |
| `VAL-003` | Estado dentro del dominio | El estado indicado no es válido. |
| `VAL-004` | Identificador de membresía con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-005` | Campo de ordenamiento dentro de la lista admitida | El campo de ordenamiento indicado no es válido. |
| `VAL-006` | Alcance dentro del dominio | El alcance indicado no es válido. |
| `VAL-007` | Implementación dentro del dominio | La implementación indicada no es válida. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-013` | El sistema devuelve el catálogo paginado con el total de productos que cumplen el filtro |
| `CA-PM-014` | El sistema filtra por tipo y devuelve solo los upgrades o solo los bots |
| `CA-PM-015` | El sistema filtra por estado y devuelve también los inactivos cuando se piden |
| `CA-PM-016` | El sistema filtra los upgrades por su membresía destino |
| `CA-PM-109` | El sistema filtra los upgrades por su membresía **de origen**, y los dos filtros **se combinan**: origen y destino a la vez devuelven la pareja exacta |
| `CA-PM-017` | El sistema busca por nombre sin distinguir mayúsculas ni acentos, y la búsqueda en blanco equivale a no filtrar |
| `CA-PM-018` | El sistema **excluye los productos retirados** salvo que se pidan expresamente, y al pedirlos indica desde cuándo lo están |
| `CA-PM-019` | El sistema devuelve **el origen y el destino** de cada upgrade con su nombre y su nivel, sin exigir una segunda consulta |
| `CA-PM-020` | El sistema rechaza los parámetros inválidos **enumerándolos todos juntos** |
| `CA-PM-021` | El sistema devuelve una colección vacía y total cero cuando ningún producto cumple el filtro |
| `CA-PM-022` | El sistema rechaza la consulta a un actor sin el permiso de lectura de productos |
| `CA-PM-074` | El sistema devuelve el catálogo **en orden de alta** cuando nadie pide otro |
| `CA-PM-075` | El sistema ordena por nombre, por precio y por fecha de alta cuando se le pide, y **rechaza cualquier otro campo** en lugar de ignorarlo |
| `CA-PM-076` | El sistema devuelve las mismas filas sin repetir ni saltarse ninguna al recorrer todas las páginas, aunque varios productos compartan el valor por el que se ordena |
| `CA-PM-077` | El sistema devuelve los retirados a cualquier actor con el permiso de lectura, **sin exigir uno propio**, y **sin incluir el motivo del retiro** — que sí devuelve el detalle de `RF-PM-003`, uno a uno |
| `CA-PM-115` | El sistema filtra por **alcance** y devuelve solo los de `TIENDA` o solo los de `HOTLINKS` |
| `CA-PM-116` | El sistema filtra por **implementación**, admite el valor en minúsculas y **rechaza el que está fuera del dominio junto al resto de parámetros inválidos**, no por separado |
| `CA-PM-117` | Cada fila del listado devuelve **el alcance y la implementación**, en los dos tipos de producto |
| `CA-PM-142` | El sistema devuelve el **color** de las dos membresías de cada upgrade, resueltas en la misma sentencia |

## 13. Casos límite

- **Filtro por un destino que no existe:** colección vacía, no error. El actor filtró por algo que no está, y eso no es una entrada inválida.
- **Filtro por destino combinado con tipo bot:** ningún bot tiene destino, de modo que el resultado es siempre vacío. Debe devolverse vacío y no rechazarse: la combinación es coherente aunque sea inútil.
- **Búsqueda con comodines del motor de búsqueda:** un nombre que contenga los caracteres de comodín no debe ampliar la búsqueda a todo el catálogo.
- **Un producto retirado y uno vivo con el mismo nombre:** es posible, porque la unicidad es entre los vivos. El listado debe mostrarlos como dos filas distintas y no colapsarlos.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 26-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Cuál es el orden por omisión? | **Por fecha de alta.** Y con ello quedó dicho algo que esta spec no preguntaba: **hay dos formas de consulta y cada una tiene su orden**. Esta —la del administrador— va en orden de alta, porque quien gobierna el catálogo trabaja sobre lo último que entró. La del cliente (`RF-PM-007`) va **agrupada por tipo**, con los upgrades por **nivel** —que es el orden de la cadena, y el único en que «subir» significa algo— y los bots por fecha. Se enmienda `RF-PM-007` en consecuencia. **El desempate es el identificador**, y no es cosmético: sin un orden total, dos productos que compartan el valor ordenado pueden repetirse o saltarse entre páginas, y eso se descubre como «faltan productos» sin ningún error de por medio. Sale gratis, porque el identificador es un UUID v7 y su orden **es** el cronológico |
| 2 | ¿El orden es configurable por el actor? | **Sí, sobre una lista cerrada**: nombre, precio y fecha de alta. Cerrada y no abierta por el mismo motivo que en `RF-SP-025`: admitir un campo cualquiera deja ordenar por lo que a nadie se le ocurrió revisar. Un campo fuera de la lista se **rechaza**, no se ignora — ignorarlo devolvería un orden distinto del pedido sin decirlo |
| 3 | ¿Ver los retirados exige un permiso aparte? | **No: basta el de lectura.** La decisión se mantiene; **su motivo se enmendó el mismo día** (Art. I.7). Se aprobó diciendo que el motivo del retiro no viajaba en el catálogo, y horas después `RF-PM-003` resolvió que **el detalle sí lo devuelve** a quien tenga `products:read`. Lo que sigue siendo cierto —y sostiene la decisión— es que **el listado no lo lleva**: uno a uno es una consulta, en bloque sería una exportación de decisiones comerciales. Lo que se asume es que `products:read` alcanza al motivo de un producto, que en la auditoría acota `audit:read-deletions` |
| 4 | ¿Se puede filtrar por rango de precio? | **No por ahora**, y pasa a §4.2. No es lo que necesita quien administra, y añadirlo después es un parámetro opcional más que no rompe a ningún cliente. Escribirlo hoy traería sus casos límite —mínimo mayor que máximo, monedas distintas en el mismo filtro— sin que nadie los esté esperando |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.3.0 | 26-08-2026 | **Enmienda del motivo de la resolución 3**, el mismo día y por el Art. I.7: `RF-PM-003` resolvió que el detalle **sí devuelve el motivo del retiro**, de modo que el argumento con el que se aprobó —«el motivo no viaja en el catálogo»— dejó de ser cierto. La decisión no cambia: los retirados siguen sin exigir permiso propio, y lo que la sostiene ahora es que **el listado no lleva el motivo** aunque el detalle sí. `CA-PM-077` lo dice explícitamente. | Responsable del proyecto |
| 0.2.0 | 26-08-2026 | **Aprobada.** El orden por omisión es el de **alta**, configurable sobre una **lista cerrada** —nombre, precio, fecha—, con el identificador como desempate para que la paginación no repita ni salte filas. Los retirados **no exigen permiso propio**, y el filtro por rango de precio pasa a lo que no se incluye. Una de las resoluciones alcanza a otra spec: la consulta del cliente tiene **su propio orden**, agrupado por tipo, y `RF-PM-007` se enmienda con él. Cuatro criterios nuevos, `CA-PM-074` a `CA-PM-077`, y `VAL-005`. | Responsable del proyecto |
| 0.1.0 | 26-08-2026 | Redacción inicial, con cuatro preguntas abiertas. | Responsable técnico |
| 0.4.0 | 27-08-2026 | El listado devuelve la **vigencia en días** de cada producto (`RN-PM-015`). Sin ella, quien administra no distingue en la lista un upgrade permanente de uno de treinta días, que es la diferencia comercial más importante entre dos filas por lo demás idénticas. | Responsable del proyecto |
| 0.3.0 | 28-08-2026 | **La respuesta gana el icono** (`RN-PM-016`) y el tipo `SERVICIO` pasa a llamarse `BOT`. Ninguna de las dos cosas cambia el comportamiento de esta consulta: el icono viaja como un campo más —nulo y presente cuando no lo hay, por el mismo criterio que el destino y la vigencia— y el renombrado solo cambia el valor que se lee y por el que se filtra. **El contrato publicado cambia**, de modo que la copia del frontend queda vieja. | Responsable técnico |
| 0.4.0 | 07-09-2026 | **El catálogo gana dos filtros y dos columnas de salida: alcance e implementación** (`RN-PM-019`, `RN-PM-020`). **El del alcance no es un filtro más**: es el **único sitio del sistema donde ese dato se puede consultar hoy**, porque `RF-PM-007` no lo filtra —bajo la escala acumulativa los dos valores llegan a la tienda— y el canal de hotlinks todavía no existe. Sin él, el alcance sería un dato que se declara, se corrige y **no se puede ver**. Entran `VAL-006`, `VAL-007` y `CA-PM-115` a `CA-PM-117`. Los dos se validan **como el tipo y el estado**: se admiten en cualquier caja, se normalizan a su forma canónica y su rechazo **se acumula** con el resto en un solo `400` (`CA-PM-020`) — validar sin normalizar es el defecto sutil que devolvería `200` con la colección vacía en lugar de los productos que se piden. | Responsable del proyecto |
| 0.5.0 | 07-09-2026 | **Las membresías resueltas del listado traen su COLOR** (`RN-SP-024`). Sale del mismo `LEFT JOIN` que ya trae el código, el nombre y el nivel, de modo que **no cuesta ninguna consulta más** — que es exactamente el motivo por el que el destino entra por un `JOIN` y no fila a fila por el puerto. Entra `CA-PM-142`. | Responsable del proyecto |
