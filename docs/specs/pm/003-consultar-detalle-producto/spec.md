# SPEC — `RF-PM-003` Consultar el detalle de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-003` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendada el | 28-08-2026 — ver §15 |
| Enmendada el | 07-09-2026 — **las membresías traen su color** (`RN-SP-024`). Ver §15 |
| Enmendada el | 07-09-2026 — **el detalle devuelve el alcance y la implementación** (`RN-PM-019`, `RN-PM-020`). Ver §15 |
| Enmendada el | 08-09-2026 — **el detalle devuelve los DOS precios** (`RN-PM-023`, `RN-PM-024`). Ver §15 |
| Enmendada el | 08-09-2026 — **el detalle devuelve también la CONVERSIÓN** (`RN-PM-024` reescrita). Ver §15 |
| Enmendada el | 12-09-2026 — **el segundo precio es el de COMPRA** (`RN-PM-023`, `RN-PM-024`): `purchasePrice` sustituye a `publicPrice`, y la conversión se calcula siempre sobre `price`. Ver §15 |
| Enmendada el | 14-09-2026 — **el detalle devuelve `rating` —promedio y cantidad de reseñas vivas— en la misma sentencia** (`RN-PM-031`, `RF-PM-009`). Ver §15 |
| Enmendada el | 14-09-2026 — **el detalle devuelve `videoUrl`, el enlace del video** (`RN-PM-032`), presente y nulo cuando no hay. Ver §15 |
| Enmendada el | 14-09-2026 — **el detalle devuelve `coverImageUrl`, la dirección de la portada** (`RN-PM-033`, `RF-PM-014`), presente y nula cuando no hay, también en un retirado. Ver §15 |

---

## 1. Objetivo

Ver todo lo que el sistema sabe de un producto, incluido su retiro.

## 2. Contexto

El listado de `RF-PM-002` responde «qué hay»; esta consulta responde «qué es exactamente esto». Es la pantalla desde la que se decide corregir un precio, desactivar una oferta o entender por qué un producto dejó de venderse, y por eso trae cosas que el listado no lleva.

**Las dos membresías llegan resueltas.** Cuando el producto es un upgrade, el detalle devuelve el código, el nombre y el **nivel** de la membresía de la que sale y de la membresía a la que lleva: un detalle que obliga a una segunda llamada para ser legible no es un detalle.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Consulta un producto antes de corregirlo o retirarlo |
| Fuerza comercial | Consulta un producto para explicárselo a un cliente |

## 4. Alcance

### 4.1 Incluye

- Devolver un producto por su identificador, con todos sus datos.
- Resolver **las dos membresías** de los upgrades, con su nivel: sin el origen, el detalle no dice a quién va dirigido el producto.
- Indicar si el producto está retirado y desde cuándo.
- Devolver **el motivo por el que se retiró**, cuando el producto está retirado.

### 4.2 No incluye

- **El historial de cambios del producto, y quién hizo cada uno.** Qué cambió, cuándo y **a manos de quién** vive en la auditoría, que tiene sus propias consultas (`RF-SP-011`). El Art. V.7 mantiene las columnas de actor fuera de la tabla a propósito, y traer ese dato aquí obligaría a duplicarlo o a leer el almacén de evidencia de otro módulo.
- **Cuántas veces se ha vendido.** No existen las ventas.
- **Los otros productos que llevan al mismo destino.** El detalle habla de un producto, no del catálogo; esa pregunta la responde el filtro de `RF-PM-002`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-010` | El producto no desaparece: el retiro es lógico | `requirements/pm.md` §5.1 |
| `RN-PM-019` | El alcance dice hasta dónde se muestra, y es **acumulativo** | `requirements/pm.md` §5.1 |
| `RN-PM-020` | La implementación dice si lo comprado se aplica solo o espera autorización | `requirements/pm.md` §5.1 |
| `RN-PM-023` | **El precio de compra es opcional y no se cobra** — es lo que NEXUS paga por el producto | `requirements/pm.md` §5.1 |
| `RN-PM-024` | **El precio de compra no sale de administración; el precio y la conversión salen en toda lectura** — el detalle es una de las dos lecturas que lo devuelven | `requirements/pm.md` §5.1 |
| `RN-PM-024` | **El precio del sistema no sale de administración** — y este detalle **es** administración | `requirements/pm.md` §5.1 |
| `RN-PM-032` | **Un producto puede enlazar un video, y el enlace sale en toda lectura** — el detalle lo devuelve, presente y nulo cuando no hay | `requirements/pm.md` §5.1 |
| `RN-PM-033` | **La portada es un archivo y se publica por su identificador** — el detalle devuelve su dirección, `coverImageUrl`, presente y nula cuando no hay; los bytes los sirve `RF-PM-016` | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del producto | Sí | Cuál se consulta | Debe tener forma de identificador válido |

### 6.2 Salida

**Desde el 14-09-2026 la respuesta lleva `rating`** (`RN-PM-031`): el detalle devuelve `rating` —promedio y cantidad de reseñas vivas— en la misma sentencia. Es un objeto **presente siempre**, con `average` —dos decimales, **nulo** cuando no hay reseñas— y `count` —**cero** cuando no hay—. Cuentan solo las reseñas **vivas**: una retirada sale de la cuenta en el acto. La enmienda la construye `RF-PM-009` (`T-10`, `T-11`), y lo que este requerimiento tiene que conservar es su número de sentencias: el agregado viaja **en la misma consulta** que el producto.

| Dato | Descripción |
|---|---|
| Producto | Identificador, código, tipo, nombre, descripción, **icono**, **el enlace del video**, **la dirección de la portada**, **los dos precios** con su moneda, **vigencia en días** y estado |
| Los dos precios | El **del sistema** —el que se cobra— y el **de compra** —lo que NEXUS paga por el producto—, este **presente y nulo** cuando no se conoce (`RN-PM-023`). Es, con el listado, **el único sitio donde se ven juntos**: la oferta y el hotlink devuelven uno solo, porque el otro es el margen (`RN-PM-024`, 12-09-2026) |
| La conversión | La moneda por omisión, **la tasa aplicada** y el **importe convertido** de `price`. **Presente y nula** cuando no hay nada que convertir (`RN-PM-024`, 08-09-2026). El precio de compra **no se convierte** |
| Alcance e implementación | Hasta dónde se muestra el producto y quién aplica lo que otorga. En los **dos** tipos (`RN-PM-019`, `RN-PM-020`) |
| Membresía destino | En los upgrades: código, nombre y **nivel**. Vacía en los bots |
| Membresía de origen | Igual. **Aquí sí viaja**, al revés que en `RF-PM-007`: allí el origen es siempre el del actor y repetirlo sobraría, y aquí es una propiedad del producto que nadie más dice |
| Marca de retiro | Si está retirado, desde cuándo y **con qué motivo** |
| Marcas temporales | Cuándo se creó y cuándo se modificó por última vez. **Sin actor**: quién lo hizo vive en la auditoría |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de productos.

**Postcondiciones**

- Ninguna: la consulta no modifica nada.

## 8. Flujo principal

1. El actor pide un producto por su identificador.
2. El sistema comprueba que el identificador tiene forma válida.
3. El sistema devuelve el producto con **sus dos membresías resueltas** si es un upgrade.

## 9. Flujos alternativos

### FA-001 — Producto retirado

**Cuándo ocurre:** el producto está eliminado lógicamente.

1. El sistema **lo devuelve igual**, marcado como retirado y con la fecha.
2. No responde «no existe»: existió, se vendió quizá, y ocultarlo impediría entender qué era.

### FA-002 — Producto de bot

**Cuándo ocurre:** el producto no es un upgrade.

1. La membresía destino llega **vacía y presente**, no ausente. Un campo que falta es indistinguible de uno que el cliente no conoce.

## 10. Excepciones

### EX-001 — Producto inexistente

**Condición:** no hay ningún producto con ese identificador.
**Respuesta del sistema:** responde que el recurso no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-023` | El sistema devuelve el producto con todos sus datos y su precio con la moneda en que está expresado |
| `CA-PM-024` | El sistema resuelve la membresía destino de un upgrade con su código, su nombre y su nivel |
| `CA-PM-025` | El sistema devuelve la membresía destino **vacía y presente** en un producto de bot |
| `CA-PM-026` | El sistema devuelve un producto retirado marcándolo como tal y con su fecha de retiro, en lugar de responder que no existe |
| `CA-PM-027` | El sistema responde que el recurso no existe ante un identificador que no corresponde a ningún producto |
| `CA-PM-028` | El sistema rechaza un identificador con formato inválido como dato inválido, y no como recurso no encontrado |
| `CA-PM-029` | El sistema rechaza la consulta a un actor sin el permiso de lectura de productos |
| `CA-PM-080` | El sistema devuelve el **motivo del retiro** de un producto retirado a cualquier actor con `products:read`, sin exigir permiso de auditoría |
| `CA-PM-081` | El sistema **no devuelve quién** creó, corrigió ni retiró el producto, ni siquiera resuelto desde la auditoría |
| `CA-PM-082` | El sistema devuelve el precio como **número**, con los decimales que declara su moneda y no con la escala de la columna: `49.99` en una moneda de dos decimales, no `49.9900` |
| `CA-PM-118` | El sistema devuelve **el alcance y la implementación** del producto, en los dos tipos y también en uno retirado |
| `CA-PM-143` | El sistema devuelve el **color** de las dos membresías del detalle |
| `CA-PM-152` | El sistema devuelve **los dos precios**, cada uno con los decimales de la moneda, y el **de compra —`purchasePrice`— en nulo presente** cuando no se conoce |
| `CA-PM-166` | El sistema devuelve la **conversión** a la moneda por omisión —moneda, tasa e importe—, calculada **sobre `price`** —el de compra nunca se convierte—, y **presente y nula** cuando el producto ya está en esa moneda o cuando no hay tasa vigente |
| `CA-PM-224` | El sistema devuelve **`videoUrl`** tal cual se guardó, y **presente y nulo** cuando el producto no lo declara — también en un producto retirado |
| `CA-PM-233` | El sistema devuelve **`coverImageUrl`** con la forma `/api/v1/product-images/{uuid}` cuando hay portada, **presente y nulo** cuando no — también en un producto **retirado**, cuya portada sigue existiendo y sirviéndose (`RN-PM-010`) — y sin ninguna consulta más |

## 13. Casos límite

- **Identificador con forma válida pero inexistente:** «no existe». Es distinto del anterior y los dos deben probarse: uno es un dato mal escrito, el otro un recurso ausente.
- **Identificador no canónico:** una forma laxa del identificador debe rechazarse como dato inválido y no resolverse en silencio. Es el mismo hueco que `RF-SP-018` tuvo abierto durante dos días.
- **Upgrade cuyo destino cambió de nivel:** la cadena de membresías se reordena al insertar un eslabón (`RN-SP-007`), de modo que el nivel del destino **no es estable en el tiempo**. El detalle devuelve el nivel **actual**, no el que tenía cuando se creó el producto.
- **Producto retirado cuyo destino sigue vivo:** se devuelve con normalidad; retirar el producto no toca la membresía.
- **Precio con cuatro decimales en una moneda de dos:** no puede ocurrir, porque `RN-PM-007` lo impide al escribir. Si ocurriera —una carga directa en la base—, el detalle devuelve **lo almacenado** y no lo redondea: redondear aquí escondería el dato inválido en lugar de mostrarlo.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 26-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El detalle devuelve el motivo del retiro? | **Sí, a quien tenga `products:read`.** Delante de un producto retirado, «por qué» es la pregunta que se hace todo el mundo, y obligar a cambiar de pantalla para responderla convierte la auditoría en un trámite. **La consecuencia se asume y queda escrita**: `products:read` alcanza así a un dato que en la auditoría acota `audit:read-deletions`, de modo que ese permiso deja de acotar el motivo **de un producto** —sigue acotando el de todo lo demás y la línea de tiempo completa—. **El listado no lo lleva** (`CA-PM-077`): uno a uno es una consulta, en bloque es una exportación de decisiones comerciales. Esta resolución **enmienda el motivo** con el que se aprobó la resolución 3 de `RF-PM-002`, que decía que el motivo no viajaba en el catálogo; la decisión de aquella —los retirados no exigen permiso propio— no cambia, su justificación sí |
| 2 | ¿Devuelve quién lo creó y quién lo retiró? | **No.** El Art. V.7 mantiene las columnas de actor fuera de las tablas a propósito: quién hizo qué es evidencia y vive en los registros de auditoría. Traerlo aquí obligaría a duplicar el dato en `products` —y entonces habría dos verdades— o a que este módulo consulte el almacén de evidencia de otro, que es justo lo que `modules.md` §7 impide. El motivo sí viaja porque **es un dato del retiro**, no del actor |
| 3 | ¿El precio viaja como número o como texto formateado? | **Como número.** El formato lo decide quien lo muestra, y un importe formateado no se puede sumar: facturación y comisiones tendrían que deshacerlo para operar. **Con los decimales de su moneda, no con la escala de la columna**: `49.99` en una moneda de dos decimales y no `49.9900`, porque la escala de `numeric(14,4)` es una decisión de almacenamiento y no algo que el contrato deba exponer. **Consecuencia asumida y declarada**: un número JSON pasa por coma flotante de doble precisión en cualquier cliente JavaScript, de modo que sumar importes en el navegador puede desviarse un céntimo. El importe que cuenta es siempre el del servidor; el día que exista facturación, ningún total calculado en el cliente puede ser el que se cobre |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **Aprobada.** El detalle devuelve el **motivo del retiro** a quien tenga `products:read` —con la consecuencia declarada de que ese permiso alcanza a un dato que la auditoría acota, y con el listado quedando fuera—, **no devuelve autoría** —el Art. V.7 la mantiene en la auditoría a propósito— y el **precio viaja como número**, con los decimales de su moneda y no con la escala de la columna. Tres criterios nuevos, `CA-PM-080` a `CA-PM-082`, y un caso límite sobre el precio que no se redondea al leer. | Responsable del proyecto |
| 0.1.0 | 26-08-2026 | Redacción inicial, con tres preguntas abiertas. | Responsable técnico |
| 0.3.0 | 27-08-2026 | El detalle devuelve la **vigencia en días** (`RN-PM-015`), y **vacía y presente** cuando el producto no caduca — un campo ausente sería indistinguible de uno que el cliente no conoce. | Responsable del proyecto |
| 0.3.0 | 28-08-2026 | **La respuesta gana el icono** (`RN-PM-016`) y el tipo `SERVICIO` pasa a llamarse `BOT`. Ninguna de las dos cosas cambia el comportamiento de esta consulta: el icono viaja como un campo más —nulo y presente cuando no lo hay, por el mismo criterio que el destino y la vigencia— y el renombrado solo cambia el valor que se lee y por el que se filtra. **El contrato publicado cambia**, de modo que la copia del frontend queda vieja. | Responsable técnico |
| 0.4.0 | 07-09-2026 | **El detalle devuelve el alcance y la implementación** (`RN-PM-019`, `RN-PM-020`). Es una ampliación de la salida y nada más — no hay validación nueva, ni excepción, ni permiso —, y aun así se escribe: un detalle **sin** las dos obligaría a abrir la edición para saber dónde se publica un producto y cómo se entrega, que es exactamente el «detalle que obliga a una segunda llamada» que esta spec existe para evitar. Entra `CA-PM-118`. | Responsable del proyecto |
| 0.5.0 | 07-09-2026 | **Las membresías del detalle traen su COLOR** (`RN-SP-024`). Es la misma referencia compartida que devuelven el alta, el listado y la oferta, y por eso el cambio es de una línea: **dos formas del mismo dato obligarían a la interfaz a escribir dos lectores**, que es lo que esta spec ya evitaba reutilizando `MembershipRef`. Entra `CA-PM-143`. | Responsable del proyecto |
| 0.6.0 | 08-09-2026 | **El detalle devuelve los DOS precios** (`RN-PM-023`, `RN-PM-024`): el del sistema —el que se cobra— y el público —el que se anuncia—. El público llega **presente y nulo** cuando el producto no lo declara, y no ausente: su nulo **significa** «se anuncia con el precio del sistema», que es exactamente el trato que esta spec ya da al destino de un bot y a la vigencia de lo que no caduca. **`CA-PM-082` alcanza a los dos**: los dos importes salen con los decimales de su moneda y no con la escala de la columna, y lo hacen con **la misma función** — escrita dos veces, el mismo producto acabaría enseñando sus dos precios con escalas distintas. **Este detalle y el listado son los dos únicos sitios donde los dos importes se ven juntos**, y lo que los separa de `RF-PM-007` y `RF-PM-008` es `products:read`. Entra `CA-PM-152`. | Responsable del proyecto |
| 0.7.0 | 08-09-2026 | **El detalle devuelve además la CONVERSIÓN** (`RN-PM-024`, reescrita el mismo día): moneda por omisión, tasa e importe convertido, **presente y nula** cuando no hay nada que convertir. Nace `CA-PM-166`. **Aquí cuesta UNA consulta más —la moneda de casa— y una segunda solo si el producto está en otra moneda**; el diseño por lotes que necesita el listado está en [`002-consultar-productos/plan.md` §4.1](../002-consultar-productos/plan.md) y esta lectura usa el mismo componente con una sola moneda. La consecuencia que hay que anotar es que **`GetProductServiceIT` pasa de contar una sentencia a contar dos**: esa prueba existe para que el motivo de retiro no se consulte en los productos vivos, y ese criterio no cambia — lo que cambia es el número, y se actualiza en vez de relajar la prueba. | Responsable del proyecto |
| 0.8.0 | 12-09-2026 | **El segundo precio pasa a ser el de COMPRA**, por decisión del responsable del proyecto (`requirements/pm.md` v0.23.0 §5.2.6): lo que NEXUS paga por el producto. `purchasePrice` **sustituye** a `publicPrice`, con la misma forma —presente y nulo cuando no se conoce—. El detalle sigue siendo, con el listado, el único sitio donde se ven los dos importes, y el motivo se endurece: el segundo es el margen. **La conversión se calcula siempre sobre `price`** (`CA-PM-166` reescrito). | Responsable del proyecto |
| 0.9.0 | 14-09-2026 | **Entra `rating` en la respuesta** —el promedio y la cantidad de reseñas vivas del producto— por `RN-PM-031` ([`requirements/pm.md`](../../../requirements/pm.md) v0.24.0 §5.2.7): el detalle devuelve `rating` —promedio y cantidad de reseñas vivas— en la misma sentencia. Enmienda de Art. I.7 declarada por el plan de [`RF-PM-009`](../009-resenar-producto/plan.md) §4.1 y construida por sus tareas `T-10` y `T-11`; los criterios que la prueban son `CA-PM-180` a `CA-PM-182` de aquella tripleta. **El promedio no se guarda en `products`**: se cuenta, por un `LEFT JOIN LATERAL` sobre el índice parcial de `product_comments`, para que ninguna copia pueda quedarse atrás. | Responsable del proyecto |
| 0.10.0 | 14-09-2026 | **El detalle devuelve `videoUrl`, el enlace del video** (`RN-PM-032`, [`requirements/pm.md`](../../../requirements/pm.md) v0.27.0 §5.2.8), presente y nulo cuando no hay, y sin ninguna consulta más. Nace `CA-PM-224`. Enmienda de Art. I.7. | Responsable del proyecto |
| 0.11.0 | 14-09-2026 | **El detalle devuelve `coverImageUrl`, la dirección de la portada** (`RN-PM-033`, [`requirements/pm.md`](../../../requirements/pm.md) v0.29.0 §5.2.9), presente y nula cuando no hay, sin consulta más y **también en un retirado**: la portada es parte de lo que el producto era, y `RF-PM-015` no la quita al retirar. `CA-PM-233`. Enmienda que construye `RF-PM-014` (Art. I.7). | Responsable del proyecto |
