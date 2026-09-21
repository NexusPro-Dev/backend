# SPEC — `RF-MV-015` Consultar las ventas de mi alcance

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-015` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 21-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que **cada persona vea las ventas que le tocan según quién es**, por una sola consulta: el cliente, las suyas; el vendedor, las suyas **y las de toda su red hacia abajo** —el director lo que vendieron sus agentes, el manager lo que vendieron sus directores y, por ellos, sus agentes—; quien administra, todas. Y que pueda acotarlas a **una persona de su red**, a un estado y a un periodo.

---

## 2. Contexto

**La pregunta la hizo el responsable del proyecto el 21-09-2026 con sus palabras**: «crearemos un endpoint por tipo de movimiento y con un filtro por `user_id`, y según el usuario hará lo siguiente: rol de tipo consumidor, solo sus movimientos; rol de tipo vendedor, desde el rango más bajo y va subiendo a nivel jerárquico según la línea en `seller_id` — yo como director puedo ver las ventas que han hecho mis agentes asignados, y los managers de los directores y por ende también los agentes».

**Hasta hoy el libro se leía de dos formas, y ninguna responde eso.** `RF-MV-008` responde «¿en qué movimientos participé yo?» —y un director no participa en lo que vende su agente—; `RF-MV-006` responde «todo», a quien tenga el permiso de administración, y dárselo a un director sería enseñarle las ventas de la empresa entera. Entre las dos faltaba **la lectura de la fuerza comercial**: «qué vendió mi gente».

**Y esa lectura era exactamente la que `requirements/mv.md` §5.3 aplazaba con D-22**, «quién ve las ventas de quién». Hoy se decide, **para las ventas y solo para ellas**: el alcance lo fija el tipo de rol de quien pregunta, y para el vendedor lo fija la estructura comercial **en profundidad** (`RN-MV-031`). Es la segunda lectura del sistema autorizada por estructura y la primera que recorre el árbol entero, y `security.md` §6 lo registra con lo que se rompe y lo que se conserva.

### 2.1 Una consulta por tipo de movimiento, y no una con el tipo dentro

El libro es de todos los hechos económicos y hoy solo hay ventas. La decisión del responsable es que **cada tipo tenga su consulta**: esta es la de las ventas, y los depósitos, los puntos y las comisiones tendrán la suya. No es duplicar por gusto: **cada tipo tendrá reglas de alcance distintas** —quién ve un depósito no es quién ve una venta, y una comisión la ve quien la cobra— y una sola consulta con el tipo como parámetro obligaría a que el alcance dependiera del parámetro, que es la forma de que nadie sepa qué ve por dónde. Con una consulta por tipo, **cada una declara su alcance y su permiso**, y el frontend sabe qué vista abre con cuál.

### 2.2 Lo que acota la consulta

| Filtro | Pregunta que responde |
|---|---|
| Persona | «¿Qué vendió **este** de los míos?» — una persona de mi red **como vendedora** de alguna línea. Para el cliente no aplica: solo hay uno posible, él |
| Estado | «¿Qué está pendiente de cobrar entre lo que vendió mi gente?» |
| Periodo | «¿Qué vendió mi red en septiembre?» — sobre **cuándo ocurrió** el hecho |

**Se combinan.** Lo que no se ofrece es lo mismo que `RF-MV-006` §2.2 deja fuera, y por lo mismo: texto libre, otro orden que el cronológico, y sumas — un total de lo vendido por la red es un **informe**, con sus reglas sobre qué cuenta, y este listado no las decide. Tampoco el método de pago ni el comprobante: son preguntas de quien concilia, y quien concilia tiene `RF-MV-006`.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien porta un rol de tipo **consumidor** | Ve las ventas **a su nombre** |
| Quien porta un rol de tipo **vendedor** | Ve las ventas que vendió **él o alguien de su red**, en toda la profundidad |
| Quien porta un rol de tipo **funcionario** | Ve **todas** las ventas, como en `RF-MV-006` |

**Todos entran por el mismo permiso** (`movements:list-sales`), que **abre la consulta**; lo que cada uno ve lo decide `RN-MV-031`. Es lo que `security.md` §6 exige desde `RN-SEG-015` —toda operación con token exige permiso— y lo que separa «poder preguntar» de «hasta dónde alcanza la respuesta».

---

## 4. Alcance

### 4.1 Incluye

- El **listado paginado** de las ventas del alcance de quien pregunta, del más reciente al más antiguo.
- Los **tres filtros** de §2.2, combinables.
- En cada fila, **lo mismo que `RF-MV-006`**: el tipo, el estado, el sujeto y los vendedores de sus líneas, el medio de pago, la moneda, los importes, cuándo ocurrió y cuándo se confirmó.
- La **decisión de alcance** de `RN-MV-031`, por tipo de rol y por estructura.

### 4.2 No incluye

- **Lo que un vendedor compró.** Aquí solo lo que vendió; sus compras son `RF-MV-008`.
- **El detalle de una venta** con sus líneas → `RF-MV-007` (pendiente) y `RF-MV-008` para lo propio.
- **Los otros tipos de movimiento**, que tendrán su consulta (§2.1).
- **Publicar la estructura.** Quién cuelga de quién es `RF-SP-042`; esta consulta no dice de quién es cada venta más allá de los vendedores de sus líneas, que ya viajan en `RF-MV-006` y `RF-MV-008`.
- Búsqueda por texto, ordenamiento a elección, totales, exportar.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| **`RN-MV-031`** | **Es el requerimiento.** Consumidor: lo suyo; vendedor: él y su red en profundidad, por el vendedor de alguna línea; funcionario: todo. Precedencia funcionario > vendedor > consumidor; sin rol, lo suyo. La raíz se incluye. Fuera del alcance, vacío |
| `RN-MV-003` | «Vendido por» es cosa de **cada línea**: una venta entra en el alcance de un vendedor si **alguna** de sus líneas es suya o de su red, y aparece **una sola vez** aunque tenga varias |
| `RN-MV-026` | Para el consumidor, «lo suyo» es la venta **a su nombre** |
| `RN-MV-001` | Solo lee |
| `RN-SP-019`, `RN-SP-020` | «Mi red» es la estructura de mando **vigente**: quien cuelga de mí hoy, y quien cuelga de él. Quien dejó la red no cuenta, aunque lo que vendió mientras estuvo se le siga atribuyendo — y **sí aparece**, porque la venta lleva a **su** vendedor y ese vendedor ya no es mío. Es la misma lectura de «vigente» que `RN-SP-047` |
| `RN-SP-049` | **Los clientes no se recorren.** La venta llega al vendedor por sus líneas; la cartera (`client_sellers`) no interviene |

**Una regla nueva, `RN-MV-031`**, y es la que carga el requerimiento entero.

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Página, tamaño | No | Como todo listado del sistema |
| Persona | No | Solo las ventas en las que esa persona es vendedora de alguna línea, **si está en mi alcance**. Fuera de él —o inexistente— da una **página vacía**, no un error: el filtro no confirma quién cuelga de quién. Para el consumidor, cualquier persona que no sea él da vacío |
| Estado | No | Solo las ventas en ese estado. Uno que no exista es un **error** (`RF-MV-006` §6.1) |
| Desde, hasta | No | Como en `RF-MV-006`: instantes sobre **cuándo ocurrió**, rango **semiabierto**, «desde» posterior a «hasta» es un error |

**Sobre quién se pregunta NO se indica, y esa es la mitad del requerimiento**: el alcance sale de **quién es** quien pregunta —su tipo de rol y su lugar en la estructura—, y no hay forma de pedir el alcance de otra persona. El filtro por persona **acota dentro** del alcance; no lo cambia.

### 6.2 Salida

Cada venta devuelve **lo mismo que una fila de `RF-MV-006` §6.2**: identificador y código, tipo, estado, sujeto, vendedores sin repetir, moneda y método de pago, importes, cuándo ocurrió y cuándo se confirmó. **Sin el papel** de quien pregunta: un manager no participa en lo que vendió su agente, y un campo que valiera «ninguno» casi siempre mentiría por omisión — es el mismo argumento de `RF-MV-006` §6.2.

**Es la misma fila y no una nueva, a propósito.** Quien administra y quien dirige un equipo miran **la misma venta**; que la vieran con formas distintas obligaría al frontend a dos pantallas para un mismo hecho, que es lo contrario de lo que el responsable pidió.

**El total puede ser aproximado**, como en `RF-MV-006`: para quien administra es la tabla entera, y una red grande crece con el libro.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene `movements:list-sales` |
| Postcondición | **Ninguna.** No se escribe nada, no se audita nada — consultar con permiso no es un evento (`RF-MV-006` §7) |

---

## 8. Flujo principal

1. El actor pide las ventas, con los filtros que quiera.
2. El sistema comprueba que tiene el permiso.
3. **Resuelve hasta dónde llega el actor** (`RN-MV-031`): todo, su red con él dentro, o él mismo.
4. Selecciona las ventas de ese alcance que cumplen **todos** los filtros indicados.
5. Ordena del más reciente al más antiguo y devuelve la página pedida, con el total hasta el techo.

---

## 9. Flujos alternativos

### FA-001 — Ninguna venta cumple los filtros

Página **vacía**, no un error. Vale también para la persona que no está en el alcance o no existe (§6.1).

### FA-002 — El vendedor no tiene a nadie a cargo

Ve **lo que vendió él**. Un agente recién llegado con su primera venta la ve aquí; la raíz se incluye (`RN-MV-031`).

### FA-003 — El actor porta roles de más de un tipo

Se rige por el de **mayor precedencia**: funcionario antes que vendedor, vendedor antes que consumidor. Un vendedor que además es cliente ve aquí **lo que vendió**, no lo que compró — sus compras están en `RF-MV-008`.

### FA-004 — El actor no porta ningún rol vivo

Ve **lo suyo**, como un consumidor: es el alcance más pequeño y el único que no puede enseñar de más. Que una persona sin roles tenga el permiso no debería ocurrir (`RN-SEG-015`); si ocurre, el alcance no lo amplía.

### FA-005 — Una venta con líneas de vendedores distintos, uno dentro y otro fuera de mi red

Aparece, **una vez**, con su lista de vendedores completa: alguna línea es de los míos. Hoy ninguna entrada la produce (`RF-MV-006` §13).

### FA-006 — Alguien de mi red dejó de estarlo

Sus ventas **dejan de aparecer** desde ese instante, aunque las hiciera cuando era mío: la venta lleva a su vendedor y ese vendedor ya no cuelga de mí (`RN-SP-047` decidió lo mismo para las cuentas de broker). Lo que sí sigue es la comisión que devengó, que es de `CM`.

### FA-007 — Hay más ventas que el techo del conteo

Como `RF-MV-006` · `FA-003`: la página se devuelve, el total es el techo y la respuesta lo declara inexacto.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene `movements:list-sales` | Prohibido |

**No hay `EX` para «la persona indicada no es de tu red».** Sería un oráculo de la estructura: probando identificadores, un vendedor sabría quién cuelga de quién. Es página vacía (§6.1), por lo mismo que `RN-SP-046` unifica su `404`.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La página no es negativa y el tamaño está dentro del límite del sistema |
| `VAL-002` | El estado indicado, si viene, es uno de los que existen |
| `VAL-003` | El identificador de persona, si viene, está bien formado |
| `VAL-004` | «Desde» y «hasta», si vienen, son instantes bien formados, y «desde» no es posterior a «hasta» |

**Los problemas de validación se devuelven juntos**, como en `RF-MV-006`.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-122` | Un **consumidor** ve solo las ventas **a su nombre**: no la que vendió su vendedor a otro, ni ninguna en la que no sea el sujeto |
| `CA-MV-123` | Un **agente** sin nadie a cargo ve **lo que vendió él** y nada más — ni lo que compró, ni lo que vendió otro agente del mismo director |
| `CA-MV-124` | Un **director** ve lo que vendió él **y lo que vendieron sus agentes**; no lo de los agentes de otro director |
| `CA-MV-125` | Un **manager** ve lo suyo, lo de sus directores **y lo de los agentes de estos**: la profundidad entera |
| `CA-MV-126` | Quien porta un rol **funcionario** ve **todas** las ventas, incluidas las de vendedores que no cuelgan de nadie |
| `CA-MV-127` | El filtro por **persona** devuelve lo que vendió esa persona **si está en mi alcance**; una persona fuera de mi red, o inexistente, da una **página vacía** y no un error; y para el consumidor, cualquier persona que no sea él da vacío |
| `CA-MV-128` | Quien **dejó de colgar de mí** deja de aparecer, con lo que vendió mientras colgaba |
| `CA-MV-129` | Los filtros por **estado** y **periodo** acotan dentro del alcance y se **combinan** con el de persona; un estado inexistente o un rango invertido son un error, **juntos** |
| `CA-MV-130` | Quien **no** tiene `movements:list-sales` recibe **prohibido**; **sin autenticar**, `401`; y **ni `movements:read` ni `movements:list-own` abren esta consulta** |
| `CA-MV-131` | La fila es **la de `RF-MV-006`** —tipo, sujeto, vendedores sin repetir, importes, confirmación nula y presente— y **sin papel**; una venta con varias líneas del mismo vendedor aparece **una vez** |
| `CA-MV-132` | El listado va **paginado y envuelto**, del más reciente al más antiguo, estable entre páginas, y **el total es el techo** por encima del techo del conteo |

**`CA-MV-123` a `CA-MV-125` son los que sostienen el requerimiento**, y **`CA-MV-127` es el que lo protege**: los primeros prueban que la red se recorre entera y que no se cruza a la rama de al lado; el último, que el filtro por persona no se convierte en la forma de descubrir la estructura.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una **autoventa** —quien no cuelga de nadie compra y es su propio vendedor (`RN-MV-003`)— | La ve **él** como vendedor de la línea, y la ve su superior si lo tuviera. Para quien la mira como consumidor por el otro camino, es la venta a su nombre |
| Un vendedor que **compró** algo | Su compra **no** sale aquí; sale en `RF-MV-008`. Sí sale aquí para el vendedor que se la vendió, y para la cadena de arriba |
| Una venta **anulada** o **rechazada** de mi red | Aparece, con su estado: es la forma de ver «cuánto intenta cobrar mi gente y no entra» |
| Un vendedor cuyo **superior cambió** | Aparece para el superior **de hoy**; el de ayer deja de verlo (`FA-006`) |
| La estructura con un **ciclo**, aunque el sistema lo prohíba | La consulta **termina**: el recorrido acumula sin repetir, como `RN-SP-047` |
| El **libro vacío**, o una red sin ventas | Página vacía y total cero, exacto |
| Dos ventas **en el mismo instante** | Orden estable entre ellas, y no depende de la página |

---

## 14. Preguntas abiertas

**El modelo general de alcance.** Esta consulta decide el alcance **de las ventas** y lo resuelve con un componente que `SP` publica para eso; **no** decide cómo declarará el suyo el próximo listado que lo necesite ni cómo se comprobará que lo declara. Es lo que `ADR-005` deja pendiente, y la comprobación de arquitectura que allí se recomienda sigue sin existir.

**El detalle.** Un director ve la fila de una venta de su agente y hoy **no puede abrirla**: `RF-MV-007` sigue sin escribirse y el detalle propio (`RF-MV-008`) es solo para quien participa. Es lo primero que va a faltar, igual que en `RF-MV-006`.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 21-09-2026 | Primera versión, a petición del responsable del proyecto —«un endpoint por tipo de movimiento y con un filtro por `user_id`; consumidor solo lo suyo; vendedor desde el rango más bajo y subiendo por la jerarquía según el `seller_id` de la línea»— y con cuatro decisiones suyas del mismo día: **una ruta por tipo** y no una con el tipo dentro (§2.1); **la persona del filtro es un vendedor de mi red** (§2.2); **el vendedor ve también lo suyo** (`FA-002`); **quien administra lo ve todo** por la misma consulta (§3). Nace `RN-MV-031`, que **decide para las ventas lo que D-22 aplazaba**: la segunda lectura del sistema autorizada por estructura y la primera en profundidad (`security.md` v0.68.0). Fuera del alcance se responde **vacío** y no un error, para que el filtro no sea un oráculo de la estructura (`EX`, `CA-MV-127`). La fila es la de `RF-MV-006`, a propósito. Once criterios, `CA-MV-122` a `CA-MV-132`. | Responsable del proyecto |
