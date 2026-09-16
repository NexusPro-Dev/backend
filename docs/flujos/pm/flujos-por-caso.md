# Flujos por caso de uso — `PM` Productos y Mercadeo

| Campo | Valor |
|---|---|
| Módulo | `PM` — Productos y Mercadeo |
| Versión | 0.5.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 01-09-2026 |
| Última actualización | 16-09-2026 |

!!! info "Qué va en este documento"

    Un diagrama por caso de uso: qué puede hacer el actor, qué verifica el sistema en cada paso y por dónde sale la operación cuando una verificación falla.

    Cada diagrama es la transcripción literal de las **§8 Flujo principal**, **§9 Flujos alternativos** y **§10 Excepciones** de su spec. No añade comportamiento. Ante cualquier discrepancia, **manda la spec**.

!!! note "Convención de los diagramas"

    | Forma | Significado |
    |---|---|
    | Cápsula | Acción del actor, o respuesta final del sistema |
    | Rombo | Verificación del sistema |
    | Rectángulo | Paso del sistema que produce efecto |
    | Recuadro rojo | Rechazo tipificado, con su identificador `EX-00n` |
    | Línea punteada | Flujo alternativo `FA-00n`: no es error |

---

## 1. El catálogo

### `RF-PM-001` · Registrar un producto

Un alta para los dos tipos, con la condición cruzada que los separa.

```mermaid
flowchart TD
    A(["Actor · código, tipo, nombre,<br/>precio, moneda y vigencia"])
    A --> V1{"¿el código está<br/>libre? INCLUIDOS<br/>los retirados"}
    V1 -->|no| E5["EX-005 · RN-PM-013<br/>el código no se libera jamás"]
    V1 -->|sí| V2{"¿el nombre está libre<br/>entre los NO retirados?"}
    V2 -->|no| E1["EX-001 · RN-PM-005<br/>sin distinguir acentos ni mayúsculas"]
    V2 -->|sí| D1{"¿qué tipo?"}
    D1 -->|"UPGRADE_MEMBRESIA"| V3{"¿declara ORIGEN<br/>Y destino?"}
    V3 -->|no| E0["RN-PM-002 · el upgrade<br/>exige LAS DOS · VAL-007<br/>dice CUÁL falta"]
    V3 -->|sí| V4{"¿existen<br/>las dos?"}
    V4 -->|no| E2["EX-002 · dice CUÁL<br/>de las dos no existe"]
    V4 -->|sí| V8{"¿el origen está POR DEBAJO<br/>del destino? level MAYOR"}
    V8 -->|no| E4["VAL-014 · RN-PM-017<br/>igual → 400 · por encima → 422<br/>un descenso vendido como upgrade"]
    V8 -->|sí| V6
    D1 -.->|"BOT · FA-001"| V5{"¿declara alguna<br/>membresía o icono?"}
    V5 -->|sí| E00["RN-PM-002 y RN-PM-016<br/>el bot los tiene PROHIBIDOS"]
    V5 -->|no| V6{"¿la moneda existe<br/>y está ACTIVA?"}
    V6 -->|no| E3["EX-003 · moneda no válida"]
    V6 -->|sí| V7{"¿el precio es mayor que cero<br/>y cabe en los decimales<br/>de esa moneda?"}
    V7 -->|no| E01["RN-PM-006, RN-PM-007"]
    V7 -->|sí| P1["Registra el producto<br/>SIEMPRE INACTIVO · RN-PM-012"]
    P1 --> P2["Auditoría de cambios<br/>NINGÚN evento de seguridad"]
    P2 --> FIN(["Informa el producto creado"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E0,E00,E01,E1,E2,E3,E4,E5 ex
    class FIN ok
```

**Las dos primeras verificaciones se comportan al revés a propósito.** El **código** se comprueba contra **todos** los productos, incluidos los retirados; el **nombre**, solo contra los vivos. El nombre es una etiqueta que `RF-PM-004` deja corregir; el código es lo que una factura usará para siempre.

**`RN-PM-002` va en los dos sentidos, y desde el 02-09-2026 sobre las DOS membresías.** Un upgrade sin origen o sin destino es inservible; un **bot con cualquiera de las dos promete un cambio de nivel que nadie va a aplicar**. No falla: promete. Con dos campos, el rechazo **dice cuál** de los cuatro casos se dio — un mensaje que no distinga obliga a probar los dos.

**El origen se declara y no se deduce, y esa es la razón de la enmienda.** Mientras se deducía —«cualquiera por debajo del destino»— el **salto era imposible**: «subir a `ORO`» era el mismo producto y el mismo precio para quien sube un escalón y para quien sube tres. Ahora **cada salto es un producto** (`RN-PM-018`).

**El rombo de `RN-PM-017` produce dos códigos distintos a propósito.** Origen **igual** al destino lo ve el agregado —le basta comparar dos identificadores— y es `400`; origen **por encima** exige leer el `level` de dos filas de `memberships` y es `422`, porque el dato existe y lo que no vale es la relación entre los dos.

**Falta `EX-004` y no es un error.** Está **tachada** en la spec: era «ya hay un upgrade activo hacia ese destino» y **migró a `RF-PM-005`** cuando se decidió que el producto naciera inactivo. El número se deja vacío para que la migración de la regla quede a la vista. Al migrar cambió además de forma: hoy la regla se cuenta **por pareja origen→destino**.

---

### `RF-PM-004` · Corregir un producto

```mermaid
flowchart TD
    A(["Actor · nombre, descripción,<br/>icono, precio o moneda"])
    A --> V1{"¿trae tipo, código<br/>o membresía destino?"}
    V1 -->|sí| E4["EX-004 · son INMUTABLES<br/>RN-PM-001, RN-PM-013"]
    V1 -->|no| V2{"¿el producto existe<br/>y no está retirado?"}
    V2 -->|no| E1["EX-001 · los dos casos<br/>comparten respuesta"]
    V2 -->|sí| V3{"¿el nombre nuevo<br/>está libre?"}
    V3 -->|no| E2["EX-002"]
    V3 -->|sí| V4{"¿la moneda nueva<br/>existe y está activa?"}
    V4 -->|no| E3["EX-003"]
    V4 -->|sí| V5{"¿el precio cabe en los<br/>decimales de SU moneda?"}
    V5 -->|no| E01["RN-PM-007"]
    V5 -->|sí| D1{"¿cambia algo<br/>de verdad?"}
    D1 -.->|"no · FA-001"| FIN
    D1 -->|sí| P1["Aplica el cambio"]
    P1 --> P2["Auditoría de cambios<br/>SIN motivo: la auditoría ya dice<br/>qué cambió y de cuánto a cuánto"]
    P2 --> FIN(["Informa el producto"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E01,E1,E2,E3,E4 ex
    class FIN ok
```

**`V5` tuvo un defecto que la prueba de camino feliz no veía**, y conviene que quede dibujado: el precio **leído de la base** viene con la escala de la columna —`numeric(14,4)`, de modo que `49.99` llega como `49.9900`—, y compararlo en crudo daba cuatro decimales contra los dos de la moneda. El síntoma era exacto: **cambiar solo la moneda, sin tocar el precio, se rechazaba por decimales que ese precio no tiene**. Se compara la escala **significativa**.

**El precio se puede corregir siempre**, y de ahí sale la condición que este módulo le impone a quien registre las compras: cada compra guarda su propio importe, o corregir un precio reescribiría facturas ya emitidas.

**No se exige motivo**, al revés que al retirar: la auditoría ya registra qué cambió, de cuánto a cuánto, quién y cuándo, y exigirlo en cada coma llena ese campo de «ajuste».

---

### `RF-PM-005` · Cambiar el estado

El único caso del módulo cuyas condiciones **dependen del estado al que se va**, no del actual.

```mermaid
flowchart TD
    A(["Actor · activar o desactivar"])
    A --> V1{"¿el producto existe<br/>y no está retirado?"}
    V1 -->|no| E1["EX-001"]
    V1 -->|sí| D0{"¿ya está<br/>en ese estado?"}
    D0 -.->|"sí · FA-001"| FIN
    D0 -->|no| D1{"¿hacia dónde?"}

    D1 -.->|"DESACTIVAR · FA-002"| P2["Sin condiciones:<br/>deja de ofrecerse y ya"]
    D1 -->|ACTIVAR| V2{"¿tiene descripción?"}
    V2 -->|no| E0["RN-PM-014 · no se publica<br/>lo que no se explica"]
    V2 -->|sí| V3{"¿es un upgrade con OTRO<br/>upgrade ya activo con la<br/>MISMA PAREJA origen→destino?"}
    V3 -->|sí| E2["EX-002 · RN-PM-004<br/>informa CUÁL desactivar"]
    V3 -->|no| P1["Activa"]
    P1 --> P3["Auditoría de cambios"]
    P2 --> P3
    P3 --> FIN(["Informa el producto"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E0,E1,E2 ex
    class FIN ok
```

**La asimetría es el requerimiento.** Activar exige dos cosas; desactivar, ninguna. Un diagrama de estados con transiciones simétricas lo escondería, y por eso aquí se bifurca.

**`RN-PM-004` se comprueba aquí y en ningún otro sitio**, y esa es la razón de que el producto nazca inactivo. Con dos copias —una en el alta y otra aquí— la que se quedara atrás **no fallaría: admitiría**. Dos upgrades activos **con la misma pareja origen→destino** son **dos precios simultáneos para lo mismo**, y eso no se descubre como un error: se descubre como una discrepancia de facturación meses después.

**Lo que cambió el 02-09-2026 es qué cuenta como «lo mismo».** Dos upgrades hacia `ORO`, uno desde `BECA` y otro desde `PLATINO`, **no compiten**: venden saltos distintos, y que cuesten distinto es lo normal. La regla anterior —una por destino— prohibía exactamente lo que el origen existe para permitir.

**`EX-002` informa cuál desactivar.** Saber que hay un conflicto sin saber con qué deja al actor buscando a ciegas.

---

### `RF-PM-006` · Retirar un producto

```mermaid
flowchart TD
    A(["Actor · con MOTIVO obligatorio"])
    A --> V1{"¿el motivo viene<br/>y no está vacío?"}
    V1 -->|no| E3["EX-003 · Art. V.13"]
    V1 -->|sí| V2{"¿el producto existe?"}
    V2 -->|no| E1["EX-001"]
    V2 -->|sí| V3{"¿ya estaba retirado?"}
    V3 -->|sí| E2["EX-002"]
    V3 -->|no| D1{"¿estaba activo?"}
    D1 -.->|"sí · FA-001"| P1
    D1 -->|no| P1["Eliminación LÓGICA<br/>en CUALQUIER estado:<br/>no hay que desactivar antes"]
    P1 --> P2["Registro de eliminación<br/>con motivo e instantánea"]
    P2 --> FIN(["Confirma el retiro"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1,E2,E3 ex
    class FIN ok
```

**No hay que desactivar antes, y el motivo es sutil.** Exigir el paso previo haría que **todos** los registros de eliminación dijeran «inactivo», destruyendo el dato que se conserva para saber si el producto estaba a la venta cuando se retiró. El motivo obligatorio ya es la barrera.

**La ruta es `POST /{id}/deletion` y no `DELETE`**: la RFC 9110 no define semántica para el cuerpo de un `DELETE` y un intermediario puede descartarlo, con lo que la petición llegaría **sin el motivo** que el Art. V.13 exige.

---

## 2. Las dos consultas

### `RF-PM-002` · Consultar el catálogo

```mermaid
flowchart TD
    A(["Actor con products:read<br/>filtra por tipo, estado, origen,<br/>destino o busca por nombre"])
    A --> V1{"¿paginación, dominios<br/>e identificadores válidos?"}
    V1 -->|no| E1["EX-001 · los cuatro primeros<br/>se devuelven JUNTOS"]
    V1 -->|sí| V2{"¿campo de<br/>ordenamiento en<br/>la lista blanca?"}
    V2 -->|no| E01["VAL-005 · se RECHAZA,<br/>no se ignora"]
    V2 -->|sí| D1{"¿se piden los<br/>retirados?"}
    D1 -.->|no| P1
    D1 -->|sí| P1["Consulta · orden por defecto:<br/>fecha de alta descendente,<br/>con el id como desempate"]
    P1 --> D2{"¿hay resultados?"}
    D2 -.->|"no · FA-001"| FIN
    D2 -.->|"página más allá<br/>de la última · FA-002"| FIN
    D2 -->|sí| FIN(["Página, con el orden aplicado.<br/>SIN el motivo del retiro"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E01,E1 ex
    class FIN ok
```

**El campo de ordenamiento fuera de la lista blanca se rechaza y no se ignora**: ignorarlo devolvería un orden distinto del pedido **sin decirlo**.

**El identificador es el desempate, y sale gratis.** Sin un orden total, dos productos que compartan el valor ordenado pueden repetirse o saltarse entre páginas — y eso se descubre como «faltan productos», sin ningún error de por medio. El `id` es un UUID v7, de modo que su orden **es** el cronológico.

**No lleva el motivo del retiro**, y la sentencia ni siquiera lo selecciona — que es lo único que hace verificable el criterio. Uno a uno es una consulta; en bloque sería una exportación de decisiones comerciales.

---

### `RF-PM-003` · Consultar el detalle

```mermaid
flowchart TD
    A(["Actor con products:read"])
    A --> V1{"¿existe alguna fila<br/>con ese identificador?"}
    V1 -->|no| E1["EX-001 · inexistente"]
    V1 -->|sí| D1{"¿está retirado?"}
    D1 -.->|"no · normal"| P1
    D1 -.->|"sí · FA-001"| P2["Se devuelve MARCADO como retirado,<br/>NO como inexistente<br/>+ el MOTIVO del retiro"]
    P2 --> P1["Destino y moneda resueltos<br/>en la MISMA sentencia"]
    P1 --> FIN(["Detalle del producto"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1 ex
    class FIN ok
```

**Un producto retirado se devuelve, no se oculta**, al revés que un rol eliminado. El catálogo **conserva** lo retirado a propósito: entender por qué algo dejó de venderse es media razón de existir del módulo.

**Devuelve el motivo del retiro a quien tenga `products:read`**, y esa decisión se tomó a conciencia: delante de un producto retirado «por qué» es la pregunta de todo el mundo, y obligar a cambiar de pantalla convierte la auditoría en un trámite. La consecuencia está asumida por escrito — `products:read` alcanza a un dato que en la auditoría acota `audit:read-deletions`.

**No devuelve autoría**: el Art. V.7 mantiene las columnas de actor fuera de las tablas.

---

### `RF-PM-007` · Consultar la oferta propia

El único requerimiento del módulo **sin ninguna excepción tipificada**: no admite entrada, así que no hay nada que rechazar.

```mermaid
flowchart TD
    A(["Cualquier persona autenticada<br/>SIN parámetros"])
    A --> P1["El actor sale del token"]
    P1 --> V1{"¿tiene membresía<br/>VIGENTE?"}
    V1 -.->|"no · FA-001"| Z["Membresía = ninguna"]
    V1 -.->|"vencida · FA-003"| Z
    V1 -->|sí| N["Membresía = la suya"]

    Z --> Q["Una sentencia:<br/>solo ACTIVO y no retirado"]
    N --> Q
    Q --> R1["Upgrades: los que declaran<br/>SU membresía como ORIGEN<br/>coincidencia exacta"]
    Q --> R2["Bots: TODOS los activos,<br/>para cualquiera"]
    R1 --> D1{"¿alguno?"}
    D1 -.->|"no · FA-002<br/>está en la cima"| FIN
    D1 -->|sí| FIN(["Dos colecciones ENVUELTAS<br/>+ su nivel actual"])
    R2 --> FIN

    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class FIN ok
```

**`FA-001` y `FA-003` van al mismo sitio, y decirlo importa.** Vencer no es lo mismo que no tener, pero para decidir «a dónde puede subir» produce el mismo resultado. Y la vigencia **la calcula `SP`**: reimplementarla aquí es el defecto que devuelve resultados plausibles durante meses.

**Aquí ya no se comparan niveles**, desde el 02-09-2026. La oferta es una **coincidencia exacta**: los upgrades cuyo origen es la membresía del actor. **La comparación no desapareció, se mudó** — se hace una vez, al registrar el producto (`RN-PM-017`), en lugar de en cada consulta.

**Y `FA-001` sale del propio filtro**: quien no tiene membresía no coincide con ningún origen. Antes había que escribirlo aparte. Lo mismo con el upgrade **hacia** el nivel que ya se tiene: no se ofrece porque su origen es otro, sin que nadie lo prohíba expresamente.

**Lo que se paga por ello conviene tenerlo delante**: si nadie declara un upgrade desde `VIP`, quien esté en `VIP` **no ve ninguna subida** — sin error y sin aviso, y el catálogo se ve perfectamente bien desde administración. La cobertura de la cadena deja de ser automática.

**Las dos colecciones van envueltas** para que el día que los bots crezcan, añadir paginación no rompa a ningún cliente.

---

## 3. Los paquetes

Diez casos desde el 15-09-2026, dibujados **en el orden de construcción** de `requirements/pm.md` §6.1 y no en el de los identificadores. Todos comparten un rombo —«¿el paquete existe y está vivo?»— que en las escrituras va con **`FOR UPDATE`**: el paquete es lo que se bloquea, y el producto nunca.

### `RF-PM-017` · Registrar un paquete

El alta del producto, sin precio y sin productos.

```mermaid
flowchart TD
    A(["Actor · código, nombre, moneda,<br/>alcance y —si viene— descripción"])
    A --> V0{"¿los cinco tienen forma?<br/>VAL-001 a VAL-004 JUNTAS"}
    V0 -->|no| E0["400 · ni price, ni products,<br/>ni status en el cuerpo · VAL-005"]
    V0 -->|sí| V1{"¿el código está libre?<br/>INCLUIDOS los retirados"}
    V1 -->|no| E1["EX-001 · 409 · RN-PM-041<br/>el código no se libera"]
    V1 -->|sí| V2{"¿el nombre está libre<br/>entre los VIVOS?"}
    V2 -->|no| E2["EX-002 · 409"]
    V2 -->|sí| V3{"¿la moneda existe<br/>y está ACTIVA? · SP"}
    V3 -->|no| E3["EX-003 · 422"]
    V3 -->|sí| P1["Inserta el paquete<br/>INACTIVO · SIN precio · SIN productos<br/>la moneda queda FIJADA · RN-PM-035<br/>alcance: TIENDA, HOTLINK, AMBOS o NINGUNO"]
    P1 --> P2["Auditoría de cambios"]
    P2 --> FIN(["201 · el paquete vacío<br/>items vacío · totales en cero · offerable false"])
    A -.->|"FA-001 · sin descripción<br/>se registra igual"| V0
    A -.->|"FA-002 · código en minúsculas<br/>se normaliza"| V0

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E0,E1,E2,E3 ex
    class FIN ok
```

**El cuerpo no admite `price`**, y el rechazo es deliberado: aceptarlo y descartarlo haría creer que el paquete tiene un precio propio. **Tampoco `products`**: los productos entran uno a uno por `RF-PM-023`, donde cada uno tiene sus siete verificaciones. Las dos unicidades se comportan como las del producto —código contra todos, nombre contra los vivos— y tienen su red en `uq_product_packages_code` y `uq_product_packages_name`.

---

### `RF-PM-023` · Asociar un producto

**La operación que define al paquete**: siete verificaciones en fila, y cada una con su mensaje.

```mermaid
flowchart TD
    A(["Actor · paquete en la ruta;<br/>producto, forma y valor en el cuerpo"])
    A --> V0{"¿forma válida?<br/>porcentaje 0–100 · valor ≥ 0<br/>con los decimales de la moneda"}
    V0 -->|no| E0["400 · VAL-001 a VAL-005"]
    V0 -->|sí| V1{"¿el paquete existe<br/>y está vivo? · FOR UPDATE"}
    V1 -->|no| E1["EX-001 · 404"]
    V1 -->|sí| V2{"¿el producto existe?"}
    V2 -->|no| E2["EX-002 · 422<br/>como RF-CM-007"]
    V2 -->|sí| V3{"¿activo y<br/>no retirado?"}
    V3 -->|no| E3["EX-003 · 409 · RN-PM-039<br/>SE DISTINGUE del inexistente"]
    V3 -->|sí| V4{"¿en la moneda<br/>del paquete?"}
    V4 -->|no| E4["EX-004 · 409 · RN-PM-035<br/>nombra las DOS monedas"]
    V4 -->|sí| V5{"¿ya está<br/>en el paquete?"}
    V5 -->|sí| E5["EX-005 · 409 · RN-PM-038<br/>corrija su descuento"]
    V5 -->|no| V6{"¿el descuento deja el precio<br/>DE HOY en cero o más?<br/>gratuito → solo admite cero"}
    V6 -->|no| E6["EX-006 · 409 · RN-PM-037<br/>nombra el precio"]
    V6 -->|sí| D{"¿upgrade?"}
    D -->|"BOT · FA-004"| P1
    D -->|"sí"| V7{"¿el paquete YA tiene<br/>un upgrade? · RN-PM-046"}
    V7 -->|sí| E7["EX-007 · 409 · RN-PM-046<br/>nombra el que ya está"]
    V7 -->|"no · ocupa el único sitio · FA-003"| P1["Inserta la asociación<br/>la clave primaria es la red de EX-005"]
    P1 --> P2["Auditoría de cambios"]
    P2 --> FIN(["201 · el paquete ENTERO<br/>con su precio recalculado"])
    V0 -.->|"FA-001 · descuento cero<br/>entra a su precio"| V1
    V1 -.->|"FA-002 · el paquete está INACTIVO<br/>se asocia igual: es como se arma"| V2

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E0,E1,E2,E3,E4,E5,E6,E7 ex
    class FIN ok
```

**`EX-002` es `422` y `EX-003` es `409`, y el salto es deliberado.** El producto inexistente es un dato del cuerpo que no resuelve —el trato de `RF-CM-007`—; el producto inactivo **existe**, y quien llama tiene `packages:update`, ve el catálogo entero y merece saber por qué no entra. Es lo contrario de la lista pública de reseñas, que no distingue.

**El último rombo es el que evita el paquete que nadie puede comprar.** Un paquete con upgrades desde `BECA` y desde `PLATINO` no se le puede ofrecer a nadie entero; dejarlo asociar produciría un paquete bien configurado que la oferta oculta para siempre. Se rechaza aquí porque es el único sitio donde el error tiene a alguien delante. **Los bots no fijan origen ni lo miran.**

---

### `RF-PM-021` · Cambiar el estado

Como en el producto, las condiciones dependen del estado **al que se va**.

```mermaid
flowchart TD
    A(["Actor · estado"])
    A --> V1{"¿el paquete existe<br/>y está vivo? · FOR UPDATE"}
    V1 -->|no| E1["EX-001 · 404"]
    V1 -->|sí| V2{"¿es el estado<br/>actual?"}
    V2 -.->|"sí"| SIN(["200 · sin escribir"])
    V2 -->|no| D{"¿a cuál va?"}
    D -->|"INACTIVO · FA-002"| P1["Escribe · SIN condiciones<br/>sale de la oferta y del hotlink"]
    D -->|"ACTIVO"| V3{"¿tiene descripción<br/>Y al menos DOS productos?<br/>las dos JUNTAS"}
    V3 -->|no| E2["EX-002 y/o EX-003 · 409<br/>RN-PM-040 · en UNA respuesta"]
    V3 -->|sí| P1
    V3 -.->|"FA-001 · un producto inactivo dentro<br/>SE ACTIVA igual · offerable false lo nombra"| P1
    P1 --> P2["Auditoría de cambios"]
    P2 --> FIN(["200 · el detalle"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1,E2 ex
    class FIN,SIN ok
```

**Activar mira lo que es del paquete y no lo que es de sus productos.** Descripción y cuántos son suyos; que los productos estén activos hoy es de cada producto, cambia sin que el paquete se entere, y por eso lo mira la oferta **cada vez** (`RN-PM-039`). Los dos motivos del `409` **van juntos**: quien activó un paquete vacío y sin descripción corrige una vez.

---

### `RF-PM-024` · Corregir un descuento

Forma **y** valor, juntos y obligatorios: son un solo dato.

```mermaid
flowchart TD
    A(["Actor · paquete y producto en la ruta;<br/>forma Y valor en el cuerpo"])
    A --> V0{"¿forma válida?"}
    V0 -->|no| E0["400 · los dos son obligatorios"]
    V0 -->|sí| V1{"¿el paquete existe<br/>y está vivo? · FOR UPDATE"}
    V1 -->|no| E1["EX-001 · 404"]
    V1 -->|sí| V2{"¿el producto está<br/>en el paquete?"}
    V2 -->|no| E2["EX-002 · 404<br/>la PAREJA no existe"]
    V2 -->|sí| V3{"¿el descuento deja el precio<br/>DE HOY en cero o más?"}
    V3 -->|no| E3["EX-003 · 409 · RN-PM-037<br/>el mismo mensaje que RF-PM-023 EX-006"]
    V3 -->|sí| V4{"¿cambió algo<br/>de valor?"}
    V4 -.->|no| SIN(["200 · sin escribir"])
    V4 -->|sí| P1["UPDATE de forma y valor<br/>NO mira el estado del producto"]
    P1 --> P2["Auditoría de cambios<br/>type y value · antes y después · RN-PM-042"]
    P2 --> FIN(["200 · el detalle"])
    V3 -.->|"FA-002 · el precio del producto BAJÓ<br/>la cota es la de hoy: el hueco se cierra solo"| V4

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E0,E1,E2,E3 ex
    class FIN,SIN ok
```

**`EX-002` es `404` y no distingue «el producto no existe» de «no está aquí»**: lo que se corrige es la pareja, y la pareja no existe. **Y no mira el estado del producto**: la fila existe, el descuento es del paquete, y un producto inactivo dentro puede corregirse igual — lo que decide si se ofrece es la oferta.

---

### `RF-PM-025` · Desasociar un producto

Sin cuerpo, sin motivo, y con el paquete de vuelta.

```mermaid
flowchart TD
    A(["Actor · DELETE · SIN cuerpo · SIN motivo"])
    A --> V1{"¿el paquete existe<br/>y está vivo? · FOR UPDATE"}
    V1 -->|no| E1["EX-001 · 404"]
    V1 -->|sí| V2{"¿el producto está<br/>en el paquete?"}
    V2 -->|no| E2["EX-002 · 404 · no 409<br/>el borrado físico no deja<br/>con qué distinguir"]
    V2 -->|sí| P0["Instantánea · paquete, producto,<br/>forma, valor y precio de hoy"]
    P0 --> P1["DELETE físico de la fila"]
    P1 --> P2["Eliminación ASSOCIATION<br/>SIN motivo · RN-PM-042 · Art. V.13"]
    P2 --> FIN(["200 · el paquete<br/>con su precio recalculado"])
    P1 -.->|"FA-001 · queda con uno o con cero<br/>NO se desactiva · offerable false"| P2
    P1 -.->|"FA-002 · era el único upgrade<br/>el ORIGEN queda libre"| P2

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1,E2 ex
    class FIN ok
```

**Responde `200` con el paquete y no `204`**, porque lo que cambió es su precio. **Y es el único borrado físico del módulo**: la fila es una asociación (Art. V.13), y el registro de eliminación guarda el descuento en la instantánea para que la auditoría diga qué rebaja tenía el producto cuando salió.

---

### `RF-PM-020` · Editar un paquete

Nombre, descripción y alcance. **Código y moneda se rechazan, no se ignoran.**

```mermaid
flowchart TD
    A(["Actor · nombre, descripción, alcance · lo que venga"])
    A --> V0{"¿viene código<br/>o moneda?"}
    V0 -->|"sí"| E3["EX-003 · 400<br/>se RECHAZAN, no se ignoran"]
    V0 -->|no| V00{"¿hay algo<br/>que corregir?"}
    V00 -->|no| E00["VAL-005 · 400"]
    V00 -->|sí| V1{"¿el paquete existe<br/>y está vivo? · FOR UPDATE"}
    V1 -->|no| E1["EX-001 · 404"]
    V1 -->|sí| V2{"¿lo presente tiene forma,<br/>y el nombre está libre<br/>entre los VIVOS?"}
    V2 -->|no| E2["EX-002 · 409<br/>o 400 de forma"]
    V2 -->|sí| V3{"¿cambió algo<br/>de valor?"}
    V3 -.->|no| SIN(["200 · sin escribir"])
    V3 -->|sí| P1["UPDATE de lo presente"]
    P1 --> P2["Auditoría de cambios<br/>antes y después"]
    P2 --> FIN(["200 · el detalle"])
    P1 -.->|"FA-001 · vaciar la descripción de un ACTIVO<br/>no cambia de estado · DEJA DE OFRECERSE"| P2
    P1 -.->|"FA-002 · AMBOS → TIENDA<br/>el hotlink deja de resolver · la oferta no cambia"| P2

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E00,E1,E2,E3 ex
    class FIN,SIN ok
```

**La moneda es inmutable porque es la unidad en la que se suma**: cambiarla dejaría los descuentos fijos expresados en otra cosa y el paquete con un precio que no es de nadie. Y **vaciar la descripción de un paquete activo se permite**, como en el producto: no lo desactiva, lo saca de la oferta hasta que vuelva a tenerla (`RN-PM-040`), y el detalle lo dice.

---

### `RF-PM-022` · Retirar un paquete

```mermaid
flowchart TD
    A(["Actor · motivo"])
    A --> V0{"¿el motivo tiene forma?<br/>ANTES de cualquier consulta"}
    V0 -->|no| E0["VAL-002 · 400"]
    V0 -->|sí| V1{"¿el paquete existe?<br/>en CUALQUIER estado · FOR UPDATE"}
    V1 -->|no| E1["EX-001 · 404"]
    V1 -->|sí| V2{"¿ya está<br/>retirado?"}
    V2 -->|"sí"| E2["EX-002 · 409 · SE DISTINGUE<br/>quien retira dos veces merece<br/>saber que la primera funcionó"]
    V2 -->|no| P0["Instantánea · el paquete y sus filas"]
    P0 --> P1["deleted_at<br/>las filas de asociación PERMANECEN"]
    P1 --> P2["Auditoría de cambios<br/>+ eliminación con motivo e instantánea"]
    P2 --> FIN(["204"])
    V2 -.->|"FA-001 · activo y con productos<br/>se retira igual · sale de oferta y hotlink"| P0

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E0,E1,E2 ex
    class FIN ok
```

**Las filas de asociación no se borran al retirar el paquete.** Quedan bajo el retirado para que el detalle administrativo siga diciendo qué tenía dentro y con qué descuento — la instantánea de la eliminación las lleva también, pero la instantánea es auditoría y las filas son catálogo. **Los productos no cambian**: eran suyos antes del paquete y lo siguen siendo después.

---

### `RF-PM-019` · Consultar el detalle

La lectura de administración: **todo**, con la cuenta hecha y el motivo de lo que la detiene.

```mermaid
flowchart TD
    A(["Actor · identificador · packages:read"])
    A --> Q1["1 · el paquete CON sus productos<br/>retirado incluido · LEFT JOIN · una sentencia"]
    Q1 --> V1{"¿existe?"}
    V1 -->|no| E1["EX-001 · 404<br/>un retirado NO es 404"]
    V1 -->|sí| C["PackagePricing · priceInPackage por producto<br/>listPrice · price · savings · RN-PM-036"]
    C --> X["Conversión de price<br/>una o dos sentencias"]
    X --> O["PackageOfferability<br/>offerable y su motivo · orden fijo"]
    O --> R{"¿retirado?"}
    R -->|"sí"| M["Motivo del retiro<br/>una sentencia más · FA-003"]
    R -->|no| FIN
    M --> FIN(["200 · con purchasePrice,<br/>y con status y deleted de cada producto"])
    C -.->|"FA-001 · vacío<br/>totales en cero · exchange nulo"| X
    C -.->|"FA-002 · producto inactivo o retirado<br/>SE DEVUELVE y SIGUE SUMANDO<br/>offerable false lo nombra"| X
    C -.->|"FA-004 · el fijo supera el precio de hoy<br/>priceInPackage 0 · nada avisa · RN-PM-037"| X

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1 ex
    class FIN ok
```

**El producto inactivo se devuelve y sigue sumando**, porque esta es la única pantalla desde la que se arregla: ocultarlo dejaría el paquete en `offerable: false` sin nada visible que lo explique. **Tres, cuatro o cinco sentencias**: paquete con productos, moneda de casa, tasa si hay algo que convertir, y el motivo si está retirado — la misma escalera que `RF-PM-003`.

---

### `RF-PM-018` · Consultar los paquetes

```mermaid
flowchart TD
    A(["Actor · filtros y paginación · packages:read"])
    A --> V0{"¿filtros y paginación<br/>válidos? JUNTOS"}
    V0 -->|no| E0["400 · la única salida<br/>que no es 200"]
    V0 -->|sí| D{"¿orden por precio?<br/>FA-002"}
    D -->|no| Q1["1 · la página de paquetes y su total"]
    Q1 --> Q2["2 · TODAS las filas de asociación de la página<br/>con su producto · UNA sentencia"]
    D -->|"sí"| Q2b["1 · las filas, ordenadas por la SUMA sin redondear<br/>desempate por identificador<br/>el precio no está en ninguna columna"]
    Q2b --> Q1b["2 · los paquetes de esa página"]
    Q2 --> C
    Q1b --> C["PackagePricing y PackageOfferability por paquete<br/>offerable es COLUMNA, no filtro"]
    C --> X["Conversión para todas las monedas de la página<br/>una sentencia de tasas"]
    X --> FIN(["200 · content y total<br/>FA-001 · vacío y cero"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E0 ex
    class FIN ok
```

**Ordenar por precio invierte el orden de las sentencias**, y es la consecuencia más visible de que el precio se calcule: no hay columna por la que ordenar la página, así que se ordena la sentencia de filas por la suma sin redondear y los paquetes se traen después. **`offerable` es columna y no filtro**: quien administra quiere ver precisamente los que no se ofrecen.

---

### `RF-PM-026` · El hotlink del paquete

Público, y con **un solo `404`** para todo lo que no procede.

```mermaid
flowchart TD
    A(["Cualquiera · username y código · SIN token"])
    A --> S1["1 · PublicSellerLookup"]
    S1 --> V1{"¿existe y es<br/>fuerza comercial?"}
    V1 -->|no| E["EX-001 · 404<br/>EL MISMO CUERPO en todos los casos,<br/>y el mismo que el hotlink del producto"]
    V1 -->|sí| S2["2 · el paquete por código · sin distinguir mayúsculas<br/>ACTIVO, vivo y de alcance HOTLINK o AMBOS<br/>con sus productos como ProductRow · rating incluido"]
    S2 --> V2{"¿vino?"}
    V2 -->|no| E
    V2 -->|sí| O{"PackageOfferability<br/>¿ofrecible HOY?<br/>decidido en Java, no en el WHERE"}
    O -->|"no · producto inactivo o retirado,<br/>menos de dos, sin descripción"| E
    O -->|"sí"| C["PackagePricing · RN-PM-043<br/>SIN purchasePrice"]
    C --> X["3 y 4 · moneda de casa y tasa<br/>solo si hay que convertir"]
    X --> FIN(["200 · seller · package · items con el<br/>ProductRef del hotlink · listPrice · price<br/>savings · exchange"])
    X -.->|"FA-001 · sin tasa, o ya en la moneda de casa<br/>exchange vacía y PRESENTE"| FIN
    A -.->|"FA-002 · con token válido<br/>la MISMA respuesta"| S1
    C -.->|"FA-003 · el fijo supera el precio<br/>priceInPackage 0 · se publica la cuenta de hoy"| X

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E ex
    class FIN ok
```

**El paquete no ofrecible responde lo mismo que el inexistente**, y es la diferencia con el detalle: distinguirlos publicaría, sin token, que el paquete existe y qué le pasa. Quien tiene que saberlo es administración, y lo sabe por `RF-PM-019` con permiso. **La ofrecibilidad se decide en Java y no en el `WHERE`** para que `RN-PM-039` viva en un solo sitio. **Y el alcance de los productos no filtra dentro del paquete**: el canal lo decide el paquete.

---

### `RF-PM-007` · La oferta gana `packages` — enmienda del 15-09-2026

El diagrama de §2 no cambia; **se le añade una tercera colección** con su propio filtro.

```mermaid
flowchart TD
    A(["Actor con token · la oferta"])
    A --> Q["Los paquetes ACTIVOS, vivos y de alcance TIENDA o AMBOS<br/>con sus líneas · UNA sentencia · las monedas van a la MISMA sentencia de tasas"]
    Q --> O{"PackageOfferability<br/>¿ofrecible HOY?"}
    O -->|no| OUT["No aparece · nada lo dice"]
    O -->|"sí"| M{"¿SU upgrade —uno como máximo, RN-PM-046—<br/>sale de LA membresía del actor?<br/>RN-PM-044"}
    M -->|"no"| OUT
    M -->|"sí · o no tiene upgrade:<br/>solo bots → a todo el mundo"| C["PackagePricing · cada producto en la<br/>forma de la oferta · SIN purchasePrice"]
    C --> FIN(["packages · envuelta · presente aunque vacía<br/>ordenada por fecha de alta"])

    classDef mal fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class OUT mal
    class FIN ok
```

**Es `RN-PM-011` aplicada al paquete entero.** Un paquete se ofrece a quien puede comprarlo **todo**: como lleva un upgrade como máximo (`RN-PM-046`, comprobado al asociar desde el 16-09-2026; antes, «comparten origen»), basta comparar el origen de ese upgrade con la membresía vigente del actor (`RN-PM-044`). Sin membresía solo se ven los paquetes de bots — igual que sin membresía solo se ven bots. **La construye `RF-PM-019`** (`T-11`, `T-12`), porque es donde nacen las dos piezas que necesita.

---

## 4. Lo que el dibujo dejó a la vista

| # | Observación | Dónde se resuelve |
|---|---|---|
| 1 | **`RF-PM-001` salta de `EX-003` a `EX-005`.** No falta nada: `EX-004` está **tachada** en la spec porque la regla —«ya hay un upgrade activo hacia ese destino»— **migró a `RF-PM-005`** al decidirse que el producto nace inactivo. El hueco es deliberado y deja la migración a la vista | `spec.md` §10 de `RF-PM-001` |
| 2 | **`RF-PM-001` verifica dos unicidades con criterios opuestos en pasos consecutivos** —el código contra todos, el nombre solo contra los vivos—, y el diagrama las pone una encima de otra. Es de las pocas asimetrías del sistema que se entienden mejor viéndolas juntas | `RN-PM-005`, `RN-PM-013` |
| 3 | **`RN-PM-002` y `RN-PM-016` se comprueban en el mismo rombo y no son la misma regla.** La primera obliga y prohíbe —destino en el upgrade, prohibido en el bot—; la segunda **solo prohíbe**, porque un upgrade sin icono es un producto normal | `requirements/pm.md` §5.2 |
| 4 | **Las dos consultas del módulo tienen actores, permisos y órdenes distintos**, y la única forma de ver que no son variantes de la misma es ponerlas al lado. Fundirlas con un filtro habría dado a cada cliente el catálogo entero | `flujos-del-modulo.md` §2 |
| 5 | **`RF-PM-007` es el único caso del sistema cuyo diagrama no tiene ni un solo recuadro rojo.** No admite entrada: sus tres caminos son alternativos y ninguno es un error | `spec.md` §10 de `RF-PM-007` |
| 6 | **`RF-PM-004` guarda un defecto ya corregido que conviene no repetir**: comparar el precio en crudo contra los decimales de la moneda falla, porque lo leído de la base viene con la escala de la columna. El síntoma era cambiar **solo** la moneda y ver rechazado un precio que sí cabía | `ProductPrice.cabeEn` |
| 7 | **`RF-PM-023` pasa de `422` a `409` entre dos rombos consecutivos sobre el mismo producto.** Inexistente es un dato del cuerpo que no resuelve; inactivo **existe**, y quien llama tiene permiso para saber por qué no entra. Es la asimetría opuesta a la de las reseñas, donde ambos son el mismo `404` | `RF-PM-023` `EX-002` y `EX-003` |
| 8 | **`RF-PM-024` y `RF-PM-025` tienen la misma `EX-002` —`404`, «ese producto no está en el paquete»— por razones distintas.** En la corrección, porque lo que se edita es la pareja y la pareja no existe; en la desasociación, porque el borrado físico no deja con qué distinguir «nunca estuvo» de «ya se quitó» | `RF-PM-024` §10, `RF-PM-025` §10 |
| 9 | **La misma pregunta —«¿el descuento deja el precio de hoy en cero o más?»— aparece en tres diagramas con tres efectos.** En `RF-PM-023` y `RF-PM-024` es un rombo que rechaza; en `RF-PM-019` es un flujo alternativo que **cuenta cero y no avisa**. No es incoherencia: la cota se comprueba cuando alguien escribe, y el precio del producto puede bajar después sin que nadie escriba en el paquete | `RN-PM-037` |
| 10 | **`RF-PM-026` es el diagrama con un solo recuadro rojo al que llegan cuatro flechas.** Vendedor, paquete, ofrecibilidad y —dentro de la sentencia— estado, retiro y alcance, todos al mismo `404` con el mismo cuerpo. Un diagrama con siete recuadros habría sido más fiel al código y **menos fiel a la decisión** | `RF-PM-026` §10 |
| 11 | **`RF-PM-017` es el único alta del sistema cuya validación de campos desconocidos nombra tres campos concretos**: `price`, `products` y `status`. Los tres son los que alguien que viene del alta de producto intentaría mandar, y los tres tienen respuesta en otro sitio — la cuenta, `RF-PM-023` y `RF-PM-021` | `RF-PM-017` `VAL-005` |

---

## 5. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.5.0 | 16-09-2026 | **Un paquete lleva UN upgrade como máximo** (`requirements/pm.md` v0.38.0, `RN-PM-046`): en el diagrama de `RF-PM-023` el rombo del origen pasa a «¿ya tiene un upgrade?» y `EX-007` nombra el que está; en el de la oferta (`RF-PM-007`) el filtro mira **el** upgrade. | Responsable técnico |
| 0.4.0 | 15-09-2026 | **Los diez casos quedan construidos** y tres diagramas se retocan con lo que cambió entre el dibujo y el código: el **alta** nombra los cuatro valores del alcance, el **hotlink del paquete** publica `HOTLINK` o `AMBOS`, y la **oferta** trae los paquetes de alcance `TIENDA` o `AMBOS` en **una** sentencia y no dos. El resto se construyó como estaba dibujado; las cuentas de sentencias reales quedaron en `CA-PM-284` y `CA-PM-338`. | Responsable técnico |
| 0.3.0 | 15-09-2026 | **Diez casos nuevos: los paquetes** (`RF-PM-017` a `RF-PM-026`), transcritos de las §8, §9 y §10 de sus tripletas y dibujados **en el orden de construcción**, más el diagrama de la **enmienda de `RF-PM-007`** —la oferta gana `packages`—. El que más aporta es `RF-PM-023`, la asociación: **siete rombos en fila** que cambian de `422` a `409` sobre el mismo producto y terminan en el que evita el paquete que nadie podría comprar. Le sigue `RF-PM-026`, con **un solo recuadro rojo al que llegan cuatro flechas**. §4 gana cinco observaciones, entre ellas que la misma pregunta sobre la cota del descuento aparece en tres diagramas con tres efectos. | Responsable técnico |
| 0.2.0 | 02-09-2026 | **El upgrade declara de dónde sale**, y tres diagramas cambian. El del **alta** gana un rombo —`RN-PM-017`, el origen por debajo del destino— que produce **dos códigos distintos a propósito**: origen igual al destino es `400` porque lo ve el agregado, y origen por encima es `422` porque exige el `level` de dos filas de `memberships`. El del **cambio de estado** deja de contar «un upgrade activo por destino» y pasa a contarlo **por pareja origen→destino**: dos upgrades hacia `ORO`, uno desde `BECA` y otro desde `PLATINO`, ya no compiten. Y el de la **oferta propia** pierde su comparación de niveles entera: pasa a ser una **coincidencia exacta** por origen, con lo que `FA-001` y el upgrade hacia el nivel que ya se tiene salen **del propio filtro** en lugar de estar escritos aparte. Queda anotado lo que se paga: si nadie declara un upgrade desde `VIP`, quien esté en `VIP` no ve ninguna subida — sin error y sin aviso. | Responsable técnico |
| 0.1.0 | 01-09-2026 | Creación. Un diagrama por cada uno de los **siete** casos de uso, transcritos de las §8, §9 y §10 de sus tripletas. Los dos que más aportan son `RF-PM-005` —donde se ve que las condiciones **dependen del estado al que se va y no del actual**, cosa que un diagrama de estados simétrico esconde— y `RF-PM-001`, que pone en pasos consecutivos **dos unicidades con criterios opuestos**. §3 recoge seis observaciones, entre ellas el **hueco deliberado de `EX-004`**, tachada porque su regla migró a otro requerimiento. | Responsable técnico |
