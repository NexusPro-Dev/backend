# Flujos por caso de uso — `PM` Productos y Mercadeo

| Campo | Valor |
|---|---|
| Módulo | `PM` — Productos y Mercadeo |
| Versión | 0.1.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 01-09-2026 |
| Última actualización | 01-09-2026 |

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
    D1 -->|"UPGRADE_MEMBRESIA"| V3{"¿declara<br/>membresía destino?"}
    V3 -->|no| E0["RN-PM-002 · el upgrade<br/>la exige"]
    V3 -->|sí| V4{"¿existe?"}
    V4 -->|no| E2["EX-002 · destino inexistente"]
    V4 -->|sí| V6
    D1 -.->|"BOT · FA-001"| V5{"¿declara destino<br/>o icono?"}
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
    class E0,E00,E01,E1,E2,E3,E5 ex
    class FIN ok
```

**Las dos primeras verificaciones se comportan al revés a propósito.** El **código** se comprueba contra **todos** los productos, incluidos los retirados; el **nombre**, solo contra los vivos. El nombre es una etiqueta que `RF-PM-004` deja corregir; el código es lo que una factura usará para siempre.

**`RN-PM-002` va en los dos sentidos, y la mitad que se olvida es la segunda.** Un upgrade sin destino es inservible; un **bot con destino promete un cambio de nivel que nadie va a aplicar**. No falla: promete.

**Falta `EX-004` y no es un error.** Está **tachada** en la spec: era «ya hay un upgrade activo hacia ese destino» y **migró a `RF-PM-005`** cuando se decidió que el producto naciera inactivo. El número se deja vacío para que la migración de la regla quede a la vista.

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
    V2 -->|sí| V3{"¿es un upgrade con OTRO<br/>upgrade ya activo<br/>hacia el mismo destino?"}
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

**`RN-PM-004` se comprueba aquí y en ningún otro sitio**, y esa es la razón de que el producto nazca inactivo. Con dos copias —una en el alta y otra aquí— la que se quedara atrás **no fallaría: admitiría**. Dos upgrades activos al mismo nivel son **dos precios simultáneos para lo mismo**, y eso no se descubre como un error: se descubre como una discrepancia de facturación meses después.

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
    A(["Actor con products:read<br/>filtra por tipo, estado, destino<br/>o busca por nombre"])
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
    V1 -.->|"no · FA-001"| Z["Nivel = ninguno"]
    V1 -.->|"vencida · FA-003"| Z
    V1 -->|sí| N["Nivel = el suyo"]

    Z --> Q["Una sentencia:<br/>solo ACTIVO y no retirado"]
    N --> Q
    Q --> R1["Upgrades: level ESTRICTAMENTE<br/>MENOR que el suyo<br/>la cadena crece hacia abajo"]
    Q --> R2["Bots: TODOS los activos,<br/>para cualquiera"]
    R1 --> D1{"¿alguno?"}
    D1 -.->|"no · FA-002<br/>está en la cima"| FIN
    D1 -->|sí| FIN(["Dos colecciones ENVUELTAS<br/>+ su nivel actual"])
    R2 --> FIN

    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class FIN ok
```

**`FA-001` y `FA-003` van al mismo sitio, y decirlo importa.** Vencer no es lo mismo que no tener, pero para decidir «a dónde puede subir» produce el mismo resultado. Y la vigencia **la calcula `SP`**: reimplementarla aquí es el defecto que devuelve resultados plausibles durante meses.

**«Nivel superior» es número MENOR.** La cadena crece hacia abajo —`1` es la cima—, de modo que la comparación es `<` estricto. **Escrita al revés ofrecería bajadas de nivel, cobrándolas, y ninguna prueba de camino feliz lo vería.**

**El estricto es lo que impide ofrecer el upgrade al nivel que ya se tiene**: sería cobrar por quedarse donde se está.

**Las dos colecciones van envueltas** para que el día que los bots crezcan, añadir paginación no rompa a ningún cliente.

---

## 3. Lo que el dibujo dejó a la vista

| # | Observación | Dónde se resuelve |
|---|---|---|
| 1 | **`RF-PM-001` salta de `EX-003` a `EX-005`.** No falta nada: `EX-004` está **tachada** en la spec porque la regla —«ya hay un upgrade activo hacia ese destino»— **migró a `RF-PM-005`** al decidirse que el producto nace inactivo. El hueco es deliberado y deja la migración a la vista | `spec.md` §10 de `RF-PM-001` |
| 2 | **`RF-PM-001` verifica dos unicidades con criterios opuestos en pasos consecutivos** —el código contra todos, el nombre solo contra los vivos—, y el diagrama las pone una encima de otra. Es de las pocas asimetrías del sistema que se entienden mejor viéndolas juntas | `RN-PM-005`, `RN-PM-013` |
| 3 | **`RN-PM-002` y `RN-PM-016` se comprueban en el mismo rombo y no son la misma regla.** La primera obliga y prohíbe —destino en el upgrade, prohibido en el bot—; la segunda **solo prohíbe**, porque un upgrade sin icono es un producto normal | `requirements/pm.md` §5.2 |
| 4 | **Las dos consultas del módulo tienen actores, permisos y órdenes distintos**, y la única forma de ver que no son variantes de la misma es ponerlas al lado. Fundirlas con un filtro habría dado a cada cliente el catálogo entero | `flujos-del-modulo.md` §2 |
| 5 | **`RF-PM-007` es el único caso del sistema cuyo diagrama no tiene ni un solo recuadro rojo.** No admite entrada: sus tres caminos son alternativos y ninguno es un error | `spec.md` §10 de `RF-PM-007` |
| 6 | **`RF-PM-004` guarda un defecto ya corregido que conviene no repetir**: comparar el precio en crudo contra los decimales de la moneda falla, porque lo leído de la base viene con la escala de la columna. El síntoma era cambiar **solo** la moneda y ver rechazado un precio que sí cabía | `ProductPrice.cabeEn` |

---

## 4. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 01-09-2026 | Creación. Un diagrama por cada uno de los **siete** casos de uso, transcritos de las §8, §9 y §10 de sus tripletas. Los dos que más aportan son `RF-PM-005` —donde se ve que las condiciones **dependen del estado al que se va y no del actual**, cosa que un diagrama de estados simétrico esconde— y `RF-PM-001`, que pone en pasos consecutivos **dos unicidades con criterios opuestos**. §3 recoge seis observaciones, entre ellas el **hueco deliberado de `EX-004`**, tachada porque su regla migró a otro requerimiento. | Responsable técnico |
