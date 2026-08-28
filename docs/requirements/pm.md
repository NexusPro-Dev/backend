# Requerimientos del Módulo — `PM` Productos y Mercadeo

| Campo | Valor |
|---|---|
| Módulo | `PM` — Productos y Mercadeo |
| Paquete | `modules/products` |
| Prefijos de permiso | `products:` |
| Versión | 0.12.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 26-08-2026 |
| Última actualización | 27-08-2026 |

!!! info "Qué va en este documento"

    El catálogo de requerimientos del módulo: qué debe hacer, bajo qué reglas y con qué permisos.

    El comportamiento detallado de cada requerimiento —flujos, validaciones, criterios de aceptación y casos límite— vive en su tripleta, en `docs/specs/pm/`. Aquí no se repite.

!!! warning "Documento en Borrador: tres decisiones lo condicionan"

    Este documento se redacta antes de su primera compuerta y contiene **tres propuestas que necesitan aprobación explícita**, porque una vez usadas no se deshacen o cuestan caro:

    1. **El código `PM`.** Un código, en cuanto aparece en un identificador, no se cambia jamás ([`modules.md` §2.1](../modules.md#21-regla-de-decision)). En cuanto exista `RF-PM-001`, esta letra queda fijada para siempre.
    2. **La frontera del alcance** (§1.3): este módulo **define y publica** el catálogo; **no cobra ni entrega**. El motivo, en §1.4.
    3. ~~**Cómo lee este módulo las membresías y monedas de `SP`**~~ — **resuelta el 26-08-2026** con el cierre de **D-25**: `SP` publica tres interfaces de solo lectura y `PM` las importa (§3).

    `modules.md` §6 advierte además que los códigos de los módulos candidatos no deberían fijarse hasta conocer el alcance completo del producto. Se procede igualmente por decisión del responsable del proyecto, y queda escrito que se procedió sabiéndolo.

---

## 1. Información del módulo

### 1.1 Descripción

`PM` es dueño de **lo que la plataforma vende**. Un producto es una unidad de venta con nombre, precio y moneda, y existe en **dos tipos que no se mezclan**: el **upgrade de membresía**, que da derecho a pasar al nivel de acceso que declara, y el **servicio del sistema**, que da derecho a una prestación de la plataforma.

El módulo gobierna ese catálogo —lo crea, lo consulta, lo corrige, lo activa y lo retira— y **publica a cada persona lo que puede comprar**, que no es lo mismo que el catálogo completo.

### 1.2 Objetivo

Hoy la membresía de una persona solo cambia porque un administrador se la asigna (`RF-SP-032`). No existe **nada que comprar**: ni un precio, ni una oferta, ni un lugar donde diga qué cuesta subir de nivel. Este módulo pone ese objeto en el sistema, que es el paso sin el cual la venta —y con ella las comisiones y la facturación— no tiene sobre qué operar.

### 1.3 Alcance

**Incluye**

- Registrar un producto de cualquiera de los dos tipos, con su precio y su moneda.
- Consultar el catálogo completo, en lista y en detalle, con filtros por tipo, estado y membresía destino.
- Corregir un producto: nombre, descripción, precio y moneda.
- Activar y desactivar un producto, que es lo que decide si se ofrece.
- Retirar un producto por eliminación lógica y con motivo.
- **Publicar a cada persona la oferta que le aplica**, que en los upgrades depende de su nivel actual.

**No incluye**

- **La compra y el cobro.** Ni orden, ni estado de pago, ni pasarela. Corresponde al área de **Finanzas** del inventario ([`modules.md` §6](../modules.md#6-alcance-por-inventariar)), todavía por definir.
- **La aplicación del upgrade sobre la persona.** Cambiar el nivel de alguien es escribir en `user_memberships`, que es tabla de `SP` y tiene su propio requerimiento (`RF-SP-032`). Ver §1.4.
- **El contenido de lo que se vende.** Qué cursos o qué sesiones incluye un nivel pertenece a **Academia**; qué señales, a **Señales**. Este módulo vende el derecho, no lo entrega.
- **Comisiones y atribución de la venta.** A quién se le paga por vender un producto es del área de **Comisiones**.
- **Promociones, descuentos y campañas.** El nombre del módulo las anticipa y su alcance las admite, pero no se registran todavía: un precio promocional con vigencia es un requerimiento con su propia tabla, y escribirlo hoy sería adelantarlo sin necesidad.

### 1.4 La frontera, y por qué está donde está

**Un producto de upgrade no cambia la membresía de nadie.** Declara un derecho y su precio; quien lo ejerce es la operación de compra, que no existe todavía. La tentación es cerrar el círculo aquí mismo —comprar y aplicar en un solo paso— y hay dos razones para no hacerlo:

1. **`user_memberships` es de `SP`.** Un módulo no accede a las tablas ni a los repositorios de otro ([`modules.md` §7](../modules.md#7-reglas-de-dependencia)). Aplicar el upgrade desde aquí obliga a que `SP` **publique** esa escritura como interfaz de aplicación, con sus reglas intactas —`RN-SP-018` incluida—, y eso es una ampliación de `SP`, no de este módulo.
2. **Comprar sin cobrar es una venta que no ocurrió.** Registrar la compra antes de que exista el cobro produce un objeto que dice que alguien pagó cuando nadie verificó que pagara. Es peor que no tenerlo, porque parece que se tiene.

Lo que este documento sí deja resuelto es que **el catálogo esté diseñado para esa continuación**, y en dos sentidos. El producto **no desaparece nunca** (`RN-PM-010`), de modo que una compra futura siempre podrá resolver qué se compró. Y **cada compra guardará el importe que se pagó y la vigencia que compró**, en lugar de leerlos del producto: resuelto el 26-08-2026 al aprobar `RF-PM-004` y ampliado el 27-08-2026 con `RN-PM-015`, es una condición que este módulo **impone a uno que todavía no existe**, porque sin ella corregir un precio o una vigencia pasaría a reescribir lo ya vendido.

**Y qué ocurre cuando la vigencia vence**, decidido el 27-08-2026: la persona **se queda sin nivel vigente**. No vuelve al que tenía antes ni baja al más bajo de la cadena; es lo que `SP` ya hace, porque `user_memberships` admite fecha de fin y una membresía vencida deja de conceder —vencer no es lo mismo que no tener, pero para el acceso da igual—. Volver al nivel anterior habría exigido que la compra **guardase cuál era**, porque después de asignar el nuevo esa información no está en ningún sitio.

---

## 2. Submódulos

Según [`modules.md` §5](../modules.md#5-fichas-de-modulo).

| Submódulo | Responsabilidad | Requerimientos |
|---|---|---|
| Productos | Alta, consulta, edición, estado y retiro del catálogo | `RF-PM-001` a `RF-PM-006` |
| Oferta | Qué puede comprar quien mira, que no es el catálogo completo | `RF-PM-007` |

**Por qué la oferta es un submódulo y no una consulta más.** Responde una pregunta distinta y a otro actor: el catálogo lo lee quien administra y contiene todo —lo inactivo, lo retirado, el motivo del retiro—; la oferta la lee el cliente y contiene **solo lo que le aplica a él**. Separarlas evita el error que consiste en filtrar la respuesta en el navegador.

---

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| `SP` | Consume | **Membresías** (`RN-PM-003`): validar que el destino de un upgrade existe, y conocer su nivel para decidir la oferta |
| `SP` | Consume | **Monedas** (`RN-PM-008`): validar que la moneda existe, está activa, y con cuántos decimales se expresa su importe |
| `SP` | Consume | **Membresía vigente del actor** (`RN-PM-011`): sin ella no puede decidirse qué upgrades ofrecerle |
| `SP` | Consume | Autorización, auditoría, paginación y jerarquía de errores, que son infraestructura compartida y no una dependencia de negocio |

La dependencia es **acíclica**: `PM` consume `SP` y `SP` no consume nada ([`modules.md` §7](../modules.md#7-reglas-de-dependencia)).

!!! success "D-25 — cerrada el 26-08-2026"

    Las tres primeras filas de esta tabla se resuelven con **interfaces de aplicación de solo lectura que publica `SP`**, una por lectura: si una membresía existe y qué nivel tiene, si una moneda está activa y cuántos decimales declara, y cuál es la membresía vigente de una persona. `PM` las importa; `SP` no se entera de que `PM` existe.

    **Lo que cruza la frontera son modelos de lectura, nunca entidades**, y la definición de «vigente» **se queda en `SP`**: reimplementarla aquí es el defecto que devuelve resultados plausibles durante meses. La ausencia del dato llega como valor vacío, y qué `4xx` produce lo decide este módulo, que es quien tiene el contrato. Una regla de **ArchUnit** impide que `modules/products` importe repositorios o entidades de `modules/system`.

    Las tareas que escriben esos puertos pertenecen a **`RF-PM-001` y `RF-PM-007`**, aunque el código viva en paquetes de `SP`. El detalle completo, en [`architecture.md` §15.2](../architecture.md#152-como-consume-un-modulo-los-datos-de-otro-cierre-de-d-25).

---

## 4. Actores

| Actor | Rol en el módulo | Permisos típicos |
|---|---|---|
| Administrador | Define y gobierna el catálogo entero | `products:create`, `products:read`, `products:update`, `products:delete` |
| Funcionario · fuerza comercial | Consulta el catálogo para vender o para atender a un cliente | `products:read` |
| Consumidor | Ve lo que puede comprar. **Sin permiso**: le basta estar autenticado | — |

**El consumidor no lleva `products:read`, y es a propósito.** Ese permiso abre el catálogo completo, con lo inactivo y lo retirado dentro. `RF-PM-007` responde con lo suyo y solo con lo suyo, de modo que exigirlo obligaría a conceder a cada cliente la lectura de todo el catálogo para que pudiera ver tres líneas. Es la misma decisión que `RF-SP-039` tomó con el perfil propio.

---

## 5. Reglas de negocio

### 5.1 Reglas propias del módulo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-PM-001` | Dos tipos, y el tipo es inmutable | Al registrar y en toda edición | El producto es `UPGRADE_MEMBRESIA` o `SERVICIO`. El tipo se fija al crear y **ninguna operación lo cambia** | Crítica |
| `RN-PM-002` | Destino obligatorio en el upgrade, prohibido en el servicio | Al registrar | Un `UPGRADE_MEMBRESIA` declara **una** membresía destino; un `SERVICIO` **no puede** declararla. La condición se exige en los dos sentidos | Crítica |
| `RN-PM-003` | El destino es una membresía real de la cadena | Al registrar un upgrade | La membresía destino debe existir en `SP`. Se declara además como clave foránea | Crítica |
| `RN-PM-004` | Un solo upgrade activo por destino | **Al activar**, y no al registrar | No pueden coexistir **dos productos de upgrade activos hacia la misma membresía**. Se comprueba en un solo sitio porque el producto **nace inactivo** (`RN-PM-012`): dos copias de esta regla —una en el alta y otra en la activación— acabarían divergiendo, y la que se quedara atrás no fallaría, admitiría | Crítica |
| `RN-PM-005` | Nombre único entre los vivos | Al registrar y al editar | El nombre no se repite entre los productos no eliminados, **sin distinguir mayúsculas ni acentos** | Alta |
| `RN-PM-006` | El precio es mayor que cero | Al registrar y al editar | Un precio de cero o negativo se rechaza. Lo gratuito no se vende: se concede | Alta |
| `RN-PM-007` | El precio respeta los decimales de su moneda | Al registrar y al editar | El importe no puede tener más decimales que los que declara su moneda (`currencies.decimal_places`) | Media |
| `RN-PM-008` | La moneda debe estar activa al declararla | Al registrar y al editar el precio | Se rechaza una moneda inexistente o inactiva. Que **después** se desactive no invalida lo ya registrado | Media |
| `RN-PM-009` | Solo se ofrece lo activo | Siempre que se publique la oferta | Un producto inactivo o eliminado no aparece en `RF-PM-007`, aunque siga siendo visible en el catálogo administrativo | Alta |
| `RN-PM-010` | El producto no desaparece | Al eliminar | La eliminación es **lógica y con motivo** (Art. V.13). La fila permanece para que lo que se venda siga resolviendo qué era y cuánto costaba | Alta |
| `RN-PM-011` | Un upgrade se ofrece solo hacia arriba | Al publicar la oferta | A una persona se le ofrece un upgrade **solo si su membresía vigente es de nivel inferior al destino**. Quien no tiene membresía no ve upgrades | Alta |
| `RN-PM-012` | El producto nace inactivo | Al registrar | Todo producto se registra **`INACTIVO`**: existe, no se ofrece, y se publica con `RF-PM-005`. Es lo que permite revisar precio y texto antes de ponerlo a la venta, y lo que deja `RN-PM-004` viviendo en un solo sitio | Alta |
| `RN-PM-013` | El código no se libera nunca | Siempre | Todo producto lleva un **código corto, estable e inmutable**, único **incluso frente a los eliminados** — al revés que el nombre. Es la referencia desde la que una factura o una comisión dirán qué se vendió, y el nombre no sirve porque `RF-PM-004` lo deja corregir | **Crítica** |
| `RN-PM-014` | No se publica lo que no se explica | Al activar | Un producto **sin descripción no puede activarse**. Registrarlo sin ella es legítimo —está preparándose—; ofrecérselo a un cliente sin decirle qué se lleva, no | Media |
| `RN-PM-015` | La vigencia se mide en días y es opcional | Al registrar y al editar | Un producto puede declarar **cuántos días dura lo que otorga**, contados desde la compra. Es **opcional en los dos tipos**: sin ella, lo adquirido **no caduca**. Si se declara, es un entero **mayor que cero** | Alta |

### 5.2 Por qué las cuatro críticas son críticas

**`RN-PM-001` — el tipo no cambia.** Convertir un `SERVICIO` en `UPGRADE_MEMBRESIA` después de venderlo reescribe qué compró quien lo compró. El campo no es una etiqueta: decide qué otras columnas son obligatorias y qué derecho se adquiere.

**`RN-PM-002` — la condición va en los dos sentidos.** Un upgrade sin destino no dice a qué nivel lleva y es inservible; un servicio **con** destino promete un cambio de membresía que nadie va a aplicar. La segunda mitad es la que se olvida, y es la peligrosa: no falla, promete.

**`RN-PM-003` — el destino existe.** Sin esta regla un upgrade puede apuntar a un identificador que no es nada, y el defecto solo se ve al intentar aplicarlo: con el cobro ya hecho.

**`RN-PM-004` — un solo upgrade activo por destino.** Dos productos activos hacia el mismo nivel son **dos precios simultáneos para exactamente lo mismo**, y quien compre pagará el que la interfaz liste primero. Esto no se descubre como un error: se descubre como una discrepancia de facturación meses después.

### 5.3 Reglas de otros documentos que este módulo aplica

No se copian: se referencian, porque dos copias de una regla acaban divergiendo.

| Regla | Dónde vive | Cómo alcanza a este módulo |
|---|---|---|
| `RN-SP-006`, `RN-SP-007` | [`requirements/sp.md` §5.1](sp.md#51-reglas-propias-del-modulo) | La cadena de membresías es **lineal y ordenada por `level`**. `RN-PM-011` se apoya en ese orden: sin él, «nivel superior» no significa nada |
| `RN-SP-018` | [`requirements/sp.md` §5.1](sp.md#51-reglas-propias-del-modulo) | Consumidor ⟺ membresía. Es lo que garantiza que todo cliente tenga un nivel del que partir, y por tanto que `RF-PM-007` pueda decidir su oferta |
| `RN-SP-010` | [`requirements/sp.md` §5.1](sp.md#51-reglas-propias-del-modulo) | El catálogo de monedas no se edita por API. Este módulo lo **lee**, nunca lo toca |
| `RN-SEG-003` | [`security.md` §4](../security.md) | Los cuatro permisos `products:` se conceden por rol como cualquier otro, y ningún rol puede conceder lo que su padre no tiene |
| Art. V.13 | [`constitution.md`](../constitution.md) | Toda eliminación exige motivo, que viaja al registro de eliminación con la instantánea de lo borrado |

---

## 6. Requerimientos funcionales

### 6.1 Resumen

| ID | Requerimiento | Prioridad | Permiso | Estado |
|---|---|---|---|---|
| `RF-PM-001` | Registrar producto | **Crítica** | `products:create` | **Tasks aprobadas** |
| `RF-PM-002` | Consultar productos | **Crítica** | `products:read` | **Tasks aprobadas** |
| `RF-PM-003` | Consultar el detalle de un producto | Alta | `products:read` | **Tasks aprobadas** |
| `RF-PM-004` | Editar producto | Alta | `products:update` | **Tasks aprobadas** |
| `RF-PM-005` | Cambiar el estado de un producto | Alta | `products:update` | **Tasks aprobadas** |
| `RF-PM-006` | Eliminar producto | Media | `products:delete` | **Tasks aprobadas** |
| `RF-PM-007` | Consultar la oferta disponible para uno mismo | Alta | Autenticado | **Tasks aprobadas** |

**Prioridades:** Crítica · Alta · Media · Baja.
**Estados:** los de [`requirements.md` §4](../requirements.md#4-matriz-de-trazabilidad), que es su autoridad.

!!! note "Un solo requerimiento de alta para los dos tipos"

    Podría haber dos —«registrar upgrade» y «registrar servicio»—, y se decidió que no: es **un endpoint, un caso de uso y una tabla**, con una validación condicional según el tipo. Partirlo obligaría a dos tripletas que describen la misma operación y a dos Pull Requests sobre el mismo controlador, lo que choca con el Art. XIV.2 en lugar de servirlo.

    El precedente es `RF-SP-024`, que aplica tres reglas condicionales en los dos sentidos —consumidor ⟺ membresía, vendedor ⟺ superior— dentro de un solo requerimiento de alta.

**Orden sugerido de implementación:** `RF-PM-001` → `RF-PM-002` → `RF-PM-003` → `RF-PM-005` → `RF-PM-004` → `RF-PM-006` → `RF-PM-007`.

El alta crea la tabla y el catálogo, y sin catálogo no hay nada que consultar. `RF-PM-007` va **al final** porque es el único que necesita la membresía vigente del actor: de las tres interfaces que `SP` publica (D-25), las otras dos —membresía y moneda— las necesita ya `RF-PM-001`.

### 6.2 Fichas

#### `RF-PM-001` — Registrar producto

| Campo | Valor |
|---|---|
| Objetivo | Poner en el sistema algo que se puede vender, con su precio |
| Actor | Administrador |
| Permiso requerido | `products:create` |
| Prioridad | **Crítica** |
| Reglas aplicables | `RN-PM-001` a `RN-PM-008`, `RN-PM-012`, `RN-PM-013` |
| Depende de | — |
| Tripleta | `docs/specs/pm/001-registrar-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Registra un producto declarando su **tipo**, su nombre, su precio y su moneda; si el tipo es `UPGRADE_MEMBRESIA`, además su membresía destino, que es obligatoria ahí y está prohibida en el otro tipo. Es el requerimiento que crea la tabla del módulo y **siembra sus cuatro permisos**, con la obligación de asociarlos a `SUPERADMIN` y `ADMIN` en la misma migración ([`security.md` §4.4](../security.md#44-catalogo-de-permisos)): olvidarlo no falla al aplicar la migración, deja a `ADMIN` incapaz de conceder lo que no tiene.

#### `RF-PM-002` — Consultar productos

| Campo | Valor |
|---|---|
| Objetivo | Ver y encontrar lo que hay en el catálogo, incluido lo que no se ofrece |
| Actor | Administrador · fuerza comercial |
| Permiso requerido | `products:read` |
| Prioridad | **Crítica** |
| Reglas aplicables | — |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/002-consultar-productos/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Devuelve el catálogo **paginado**, con filtros por tipo, estado y membresía destino, y búsqueda por nombre. Incluye lo inactivo y **excluye lo eliminado salvo que se pida expresamente**, porque un catálogo que oculta lo retirado impide entender por qué un producto dejó de venderse.

#### `RF-PM-003` — Consultar el detalle de un producto

| Campo | Valor |
|---|---|
| Objetivo | Ver todo lo que se sabe de un producto, incluido su retiro |
| Actor | Administrador · fuerza comercial |
| Permiso requerido | `products:read` |
| Prioridad | Alta |
| Reglas aplicables | — |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/003-consultar-detalle-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Devuelve un producto por su identificador con sus datos completos y, cuando es un upgrade, **la membresía destino resuelta** —su código, su nombre y su nivel— y no solo su identificador: un detalle que obliga a una segunda llamada para ser legible no es un detalle.

#### `RF-PM-004` — Editar producto

| Campo | Valor |
|---|---|
| Objetivo | Corregir lo que se puede corregir sin reescribir lo vendido |
| Actor | Administrador |
| Permiso requerido | `products:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-001`, `RN-PM-005` a `RN-PM-008` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/004-editar-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Permite corregir **nombre, descripción, precio y moneda**. **No permite cambiar el tipo** (`RN-PM-001`) **ni la membresía destino**: las dos definen qué derecho otorga el producto, y cambiarlas convierte lo comprado en otra cosa. Quien necesite otro destino registra otro producto y retira el anterior.

#### `RF-PM-005` — Cambiar el estado de un producto

| Campo | Valor |
|---|---|
| Objetivo | Decidir si el producto se ofrece, sin borrarlo |
| Actor | Administrador |
| Permiso requerido | `products:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-004`, `RN-PM-009` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/005-cambiar-estado-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Activa o desactiva un producto. Es la operación que gobierna la oferta en el día a día: desactivar lo retira de la venta **sin tocar nada de lo ya vendido**. Reactivar un upgrade vuelve a exigir `RN-PM-004`, porque en el intervalo puede haberse activado otro hacia el mismo destino.

#### `RF-PM-006` — Eliminar producto

| Campo | Valor |
|---|---|
| Objetivo | Retirar del catálogo lo que fue un error o ya no existe |
| Actor | Administrador |
| Permiso requerido | `products:delete` |
| Prioridad | Media |
| Reglas aplicables | `RN-PM-009`, `RN-PM-010` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/006-eliminar-producto/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Elimina lógicamente un producto **exigiendo motivo** (Art. V.13), que viaja al registro de eliminación con la instantánea de lo retirado. El producto deja de ofrecerse y deja de contar para `RN-PM-004`, pero **su fila permanece**: el día que existan compras, cada una tendrá que poder decir qué compró.

#### `RF-PM-007` — Consultar la oferta disponible para uno mismo

| Campo | Valor |
|---|---|
| Objetivo | Que un cliente vea qué puede comprar, sin que el navegador decida la regla |
| Actor | Cualquier persona autenticada |
| Permiso requerido | — (autenticado) |
| Prioridad | Alta |
| Reglas aplicables | `RN-PM-009`, `RN-PM-011` |
| Depende de | `RF-PM-001` |
| Tripleta | `docs/specs/pm/007-consultar-oferta-propia/` |
| Estado | **Tasks aprobadas** (26-08-2026) |

Devuelve **solo productos activos**, y de los de tipo upgrade **solo los que llevan a un nivel superior al que el actor tiene hoy**. No admite parámetro de persona: responde sobre quien llama y sobre nadie más, como `RF-SP-039`. Nunca devuelve el motivo de retiro, ni lo inactivo, ni la membresía de terceros.

---

## 7. Requerimientos no funcionales

Definidos en [`security.md` §11](../security.md) y en la constitución. Los que este módulo debe satisfacer:

| ID | Requerimiento |
|---|---|
| `RNF-SEG-001` | Autenticación y autorización basada en roles y permisos |
| `RNF-SEG-002` | Todo endpoint no declarado como público exige autenticación. **Este módulo no publica ninguno público** |
| `RNF-PERF-001` | Lectura p95 < 500 ms, escritura p95 < 1 s (Art. XV.9) |
| `RNF-MAN-001` | Ninguna regla de negocio del módulo vive en el controlador (`architecture.md` §5) |

---

## 8. Integraciones

| Sistema o módulo | Tipo | Dirección | Descripción |
|---|---|---|---|
| `SP` | Interfaz de aplicación | Entrada | Membresías, monedas y la membresía vigente del actor, por **tres interfaces de solo lectura** que `SP` publica (D-25, cerrada el 26-08-2026) |

Ninguna con sistemas externos. La pasarela de pago, que sería la primera, pertenece al alcance que §1.3 deja fuera.

---

## 9. API

| Método | Ruta | Requerimiento | Permiso |
|---|---|---|---|
| `POST` | `/api/v1/products` | `RF-PM-001` | `products:create` |
| `GET` | `/api/v1/products` | `RF-PM-002` | `products:read` |
| `GET` | `/api/v1/products/available` | `RF-PM-007` | Autenticado |
| `GET` | `/api/v1/products/{id}` | `RF-PM-003` | `products:read` |
| `PATCH` | `/api/v1/products/{id}` | `RF-PM-004` | `products:update` |
| `PATCH` | `/api/v1/products/{id}/status` | `RF-PM-005` | `products:update` |
| `DELETE` | `/api/v1/products/{id}` | `RF-PM-006` | `products:delete` |

El contrato detallado de cada endpoint se define en el `plan.md` de su tripleta.

!!! warning "`/products/available` compite con `/products/{id}`, y el orden importa"

    Las dos rutas coinciden en forma. Spring resuelve primero el patrón **más específico** —el segmento literal gana a la variable de ruta—, de modo que `/products/available` no se interpreta como un identificador. Es correcto, y **por eso mismo debe tener prueba**: si alguien reordena o renombra, el síntoma sería un `400` por identificador inválido en la única ruta que un cliente usa a diario.

    La alternativa era colgarla de otro recurso (`/me/products`). Se descartó para que los endpoints del módulo vivan bajo su propio recurso, y la decisión se anota aquí para que el `plan.md` de `RF-PM-007` no la vuelva a abrir sin motivo.

---

## 10. Persistencia

| Entidad | Descripción | Dueño |
|---|---|---|
| `products` | El catálogo: qué se vende, de qué tipo, a qué precio | Este módulo |

Ninguna otra. `memberships` y `currencies` se **referencian** por clave foránea y pertenecen a `SP`.

### 10.1 Campos principales — `products`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `code` | `varchar(50)` | No | No | No | — | — |
| `validity_days` | `integer` | No | No | Sí | — | — |
| `type` | `varchar(30)` | No | No | No | — | — |
| `name` | `varchar(150)` | No | No | No | — | — |
| `description` | `text` | No | No | Sí | — | — |
| `target_membership_id` | `uuid` | No | Sí | Sí | — | `memberships` |
| `price` | `numeric(14,4)` | No | No | No | — | — |
| `currency_id` | `uuid` | No | Sí | No | — | `currencies` |
| `status` | `varchar(20)` | No | No | No | `ACTIVO` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

Sin columnas de actor, y **sin columna de motivo**: quién retiró el producto y por qué residen en `audit_deletion_log`, con la instantánea de la fila (Art. V.7 y V.13). Es lo que hacen `roles` y `users`.

`type` tiene dominio cerrado:

| Valor | Qué derecho otorga |
|---|---|
| `UPGRADE_MEMBRESIA` | Pasar a la membresía que declara `target_membership_id` |
| `SERVICIO` | Una prestación del sistema, sin efecto sobre el nivel de acceso |

`status` tiene dominio cerrado —`ACTIVO`, `INACTIVO`— y decide si el producto se ofrece (`RN-PM-009`). **No se usa `boolean`**, al revés que los catálogos de `SP`: el dominio es candidato a crecer —un `BORRADOR` que permita preparar un producto sin publicarlo es previsible— y añadir un valor a un `varchar` con `CHECK` es una migración, mientras que convertir un `boolean` en tres estados es una reescritura de todo lo que lo consulta.

**El valor por omisión de `status` es `INACTIVO`** (`RN-PM-012`), y con él se descartó por ahora el tercer valor `BORRADOR`: la distinción entre «nunca publicado» y «retirado de la venta» es fina y no urge, y añadirla después es exactamente la migración barata que este párrafo describe. Resuelto el 26-08-2026 al aprobar `RF-PM-001`.

**`price` se declara `numeric(14,4)` y no `numeric(12,2)`.** La escala no puede fijarse en dos porque `currencies.decimal_places` no siempre vale dos, y el sistema declara ese campo precisamente para no asumirlo. Cuatro decimales cubren toda moneda ISO 4217 en circulación. La escala **efectiva** de cada producto la decide su moneda, y esa es `RN-PM-007`.

### 10.2 Restricciones exigidas en el esquema

| Restricción | Sobre | Regla que implementa |
|---|---|---|
| `ck_products_type` | `type IN ('UPGRADE_MEMBRESIA','SERVICIO')` | `RN-PM-001` |
| `ck_products_status` | `status IN ('ACTIVO','INACTIVO')`, con `DEFAULT 'INACTIVO'` | `RN-PM-009`, `RN-PM-012` |
| `ck_products_type_target` | `(type = 'UPGRADE_MEMBRESIA' AND target_membership_id IS NOT NULL) OR (type = 'SERVICIO' AND target_membership_id IS NULL)` | `RN-PM-002` |
| `ck_products_price_positive` | `price > 0` | `RN-PM-006` |
| `ck_products_validity_positive` | `validity_days IS NULL OR validity_days > 0` | `RN-PM-015`. La rama `IS NULL` se escribe **explícita** aunque `validity_days > 0` sola también admitiría el nulo —un `CHECK` que evalúa a `NULL` acepta la fila—: así el permiso es deliberado y no accidental, y el día que la vigencia se vuelva obligatoria basta con quitar esa rama |
| `fk_products_target_membership` | `target_membership_id` → `memberships(id)` | `RN-PM-003` |
| `uq_products_code` | `products(code)` — restricción **total**, no parcial | `RN-PM-013`: al revés que el nombre, el código **no se libera** al retirar un producto. El día que una factura diga `UPGRADE_ORO` tiene que resolver a un solo producto para siempre |
| `ck_products_code_format` | `code ~ '^[A-Z][A-Z0-9_]*$'` | `RN-PM-013`. Mismo formato que `roles` y `memberships` |
| `fk_products_currency` | `currency_id` → `currencies(id)` | `RN-PM-008` |
| `uq_products_name` | Índice único sobre `f_unaccent(lower(name))`, **parcial**: `WHERE deleted_at IS NULL` | `RN-PM-005` |
| `uq_products_upgrade_target` | Índice único sobre `target_membership_id`, **parcial**: `WHERE type = 'UPGRADE_MEMBRESIA' AND status = 'ACTIVO' AND deleted_at IS NULL` | `RN-PM-004` |

Se declaran en la base de datos, no solo en Java (Art. V.6).

!!! important "Dos advertencias que este proyecto ya pagó una vez"

    **`ck_products_type_target` no puede evaluar a `NULL`.** Sus dos ramas son predicados `IS NULL` / `IS NOT NULL`, que devuelven siempre verdadero o falso. La precaución no es teórica: `ck_deletion_reason` se escribió con un `OR` cuyo lado nulo evaluaba a `NULL`, y un `CHECK` que devuelve `NULL` **acepta la fila** — la restricción existía y no restringía nada (`requirements.md` v0.31.0).

    **Los dos índices únicos son parciales, y un índice parcial no admite `DEFERRABLE`**, que es propiedad de una *restricción* y no de un índice. Ninguna de estas dos unicidades podrá comprobarse al confirmar la transacción: morderán en el `INSERT` o el `UPDATE` que las viole, y el plan debe traducirlas ahí en lugar de proponer diferirlas (hallazgo de `RF-SP-019`, `requirements.md` v0.31.0).

### 10.3 Lo que no se declara en el esquema

| Regla | Por qué no | Cómo se verifica |
|---|---|---|
| `RN-PM-007` — decimales según la moneda | Un `CHECK` no puede consultar otra tabla, y la escala admisible depende de `currencies.decimal_places` | En el dominio, con prueba unitaria propia sobre una moneda de dos decimales y otra de cero |
| `RN-PM-008` — la moneda debe estar **activa** | La clave foránea garantiza que existe, no que esté vigente | En el caso de uso, contra la interfaz que `SP` publique (**D-25**) |
| `RN-PM-011` — la oferta va hacia arriba | Es una consulta, no una restricción de integridad | En el caso de uso de `RF-PM-007`, con prueba sobre los tres casos: nivel inferior, igual y superior |

---

## 11. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 26-08-2026 | Creación del módulo `PM` con sus **siete requerimientos** y **once reglas propias**. Registra los **dos tipos de producto** —upgrade de membresía y servicio del sistema— con la condición cruzada que los separa (`RN-PM-002`), la unicidad de un solo upgrade activo por destino (`RN-PM-004`) y la oferta que solo mira hacia arriba (`RN-PM-011`). Deja **fuera del alcance la compra y el cobro**, con el motivo escrito en §1.4, y registra **D-25**: `SP` no publica hoy ninguna interfaz de aplicación que este módulo pueda consumir para leer membresías, monedas y la membresía vigente del actor, de modo que la decisión bloquea los `plan.md` de `RF-PM-001` y `RF-PM-007` pero no sus especificaciones. | Responsable técnico |
| 0.2.0 | 26-08-2026 | **Las siete `spec.md` quedan redactadas** y los siete requerimientos pasan a `Spec en revisión`. Traen **veintisiete preguntas abiertas** que hay que resolver antes de aprobarlas, y ninguna es de trámite: qué estado tiene un producto recién creado, si lleva código estable además del nombre, qué orden trae el catálogo, si se puede cambiar el precio de algo ya vendido, si retirar exige desactivar primero, y qué ve en su oferta quien no es consumidor. Las cinco de `RF-PM-007` son las que más lejos llegan: **qué se le ofrece a quien no tiene membresía**, si los servicios dependen del nivel, si el precio se ajusta por nivel —que es la puerta de entrada de las promociones—, si se ofrecen todos los upgrades superiores o solo el siguiente, y si la oferta se pagina. Las especificaciones **no** dependen de **D-25**: qué debe pasar está decidido; por dónde entra el dato de `SP` sigue abierto y bloquea los planes de `RF-PM-001` y `RF-PM-007`. | Responsable técnico |
| 0.3.0 | 26-08-2026 | **`RF-PM-001` cruza su primera compuerta**, y sus cinco resoluciones alcanzan al módulo entero. Tres reglas nuevas. **`RN-PM-012` — el producto nace `INACTIVO`**, y el motivo no es la prudencia sino dónde vive `RN-PM-004`: naciendo activo habría que comprobar «un solo upgrade activo por destino» en el alta **y** en la activación, y la copia que se quedara atrás no fallaría, **admitiría**; ahora la comprobación vive solo en `RF-PM-005`, y con ella se muda allí la excepción y su criterio. **`RN-PM-013` — el código no se libera nunca**: el producto gana código corto, inmutable y único **incluso frente a los eliminados**, al revés que el nombre, porque el nombre es una etiqueta que `RF-PM-004` deja corregir y una factura necesita que `UPGRADE_ORO` resuelva a un solo producto para siempre. **`RN-PM-014` — no se publica lo que no se explica**: la descripción es opcional al registrar y obligatoria al activar, que es la forma de no llenarla de ruido sin dejar publicar algo que el cliente no entiende. §10 incorpora la columna `code` con `uq_products_code` **total** y su formato, y `status` gana `DEFAULT 'INACTIVO'`. Se descarta por ahora el tercer valor `BORRADOR`, con su motivo escrito. | Responsable del proyecto |
| 0.4.0 | 26-08-2026 | **`RF-PM-002` cruza su primera compuerta.** El catálogo del administrador se ordena **por fecha de alta** salvo que se pida otra cosa de una **lista cerrada** —nombre, precio, fecha—, con el identificador como desempate: sin un orden total, dos productos que compartan el valor ordenado pueden repetirse o saltarse entre páginas, y eso se descubre como «faltan productos» sin ningún error de por medio. Sale gratis porque el identificador es un UUID v7 y su orden **es** el cronológico. Los retirados **no exigen permiso propio** —el motivo del retiro no viaja en el catálogo, vive en la auditoría de eliminación con el suyo— y el filtro por rango de precio pasa a lo que no se incluye. **La resolución alcanzó a otra spec**: quedó dicho que las dos consultas tienen órdenes distintos porque responden a actores distintos, de modo que `RF-PM-007` devuelve la oferta **agrupada por tipo**, con los upgrades por **nivel destino** —el único orden en el que «subir» significa algo— y los servicios por fecha. | Responsable del proyecto |
| 0.5.0 | 26-08-2026 | **`RF-PM-003` cruza su primera compuerta**, con dos resoluciones que se apartan de lo recomendado y una que lo confirma. **El detalle devuelve el motivo del retiro** a quien tenga `products:read`: delante de un producto retirado «por qué» es la pregunta de todo el mundo, y obligar a cambiar de pantalla convierte la auditoría en un trámite. La consecuencia se asume por escrito —`products:read` alcanza a un dato que en la auditoría acota `audit:read-deletions`— y se acota a la consulta individual: **el listado no lo lleva**, porque uno a uno es una consulta y en bloque sería una exportación de decisiones comerciales. Esto **enmendó el motivo** con el que se había aprobado la resolución 3 de `RF-PM-002` horas antes (Art. I.7): la decisión sigue en pie, su justificación se reescribió. **No devuelve autoría**: el Art. V.7 mantiene las columnas de actor fuera de las tablas, y traerlas aquí obligaría a duplicar el dato o a leer el almacén de evidencia de otro módulo. **El precio viaja como número**, con los decimales de su moneda y no con la escala de la columna, y queda declarado lo que eso cuesta: un número JSON pasa por coma flotante de doble precisión en cualquier cliente JavaScript, de modo que ningún total calculado en el navegador puede ser el que se cobre. | Responsable del proyecto |
| 0.6.0 | 26-08-2026 | **`RF-PM-004` cruza su primera compuerta**, y de sus cuatro preguntas **solo dos hubo que decidirlas**: las otras las había cerrado ya la aprobación de `RF-PM-001`, que es lo que ocurre cuando una decisión anterior alcanza a una spec posterior. **El precio se puede corregir siempre**, y eso deja escrita en §1.4 una condición que este módulo **impone a uno que todavía no existe**: cada compra guardará el importe que se pagó en lugar de leerlo del producto, porque si lo leyera, corregir un precio pasaría a reescribir facturas ya emitidas. Se descartaron congelar el precio de lo vendido —cada cambio costaría un alta y un retiro, y el catálogo se llenaría de productos casi idénticos— y versionar el precio con vigencia, que es la puerta de entrada de las promociones que §1.3 deja fuera. **No se exige motivo** al corregir: la auditoría ya registra qué cambió, de cuánto a cuánto, quién y cuándo, y exigirlo en cada coma llena ese campo de «ajuste». Las dos cerradas por consecuencia: corregir un producto **inactivo** no solo se admite, es imprescindible —es el estado en el que nace—, y el **código es inmutable**, de modo que se suma al tipo y al destino entre lo que la petición no puede traer. | Responsable del proyecto |
| 0.7.0 | 26-08-2026 | **`RF-PM-005` y `RF-PM-006` cruzan su primera compuerta**, y con ellas quedan aprobadas seis de las siete. **Ninguna de las dos exige motivo para cambiar el estado**, por coherencia con `RF-PM-004` y con los catálogos de `SP`; el retiro sí lo exige, porque lo obliga el Art. V.13. **Un producto se retira en cualquier estado**, sin desactivarlo antes: el motivo obligatorio ya es la barrera, y exigir el paso previo haría que **todos** los registros de eliminación dijeran «inactivo», destruyendo el dato que `CA-PM-052` conserva para saber si el producto estaba a la venta. **Un producto vendido se podrá retirar** —la fila permanece y la compra guardará su propio importe—, con la consecuencia declarada de que quien consulte su compra verá el producto retirado. **Ningún evento de seguridad** en el retiro, igual que en el alta: un producto no concede privilegios. La pregunta del carrito se traslada a quien escriba la compra, con lo único que hoy puede afirmarse: aquí no se reserva nada. | Responsable del proyecto |
| 0.8.0 | 26-08-2026 | **`RF-PM-007` cruza su primera compuerta, y con ella las siete del módulo.** Quien no tiene nivel **ve los servicios y ningún upgrade**: ofrecerle el primero sería venderle una membresía, y un nivel no se obtiene comprando un salto sino recibiendo un rol de consumidor (`RN-SP-018`). Los **servicios no dependen del nivel**, y queda escrito lo que costaría que dependieran — una **relación nueva entre producto y membresía**, con su tabla y una enmienda de `RN-PM-002`, no un filtro más—. El **precio no se ajusta** por quién mira, porque un precio distinto según el actor es un descuento y los descuentos son promociones, que §1.3 deja fuera: admitirlo aquí las colaría sin tabla donde vivir ni vigencia que las acote. Se ofrecen **todos los upgrades superiores** y no solo el siguiente. La paginación se resolvió **sin decidirla**: hoy no se pagina, y las dos colecciones viajan **envueltas en un objeto** —como `RF-SP-017` hizo con la cadena de membresías— para que el día que los servicios crezcan, añadirla no rompa a ningún cliente. | Responsable del proyecto |
| 0.9.0 | 26-08-2026 | **D-25 cerrada**, y con ella la tercera de las tres decisiones que este documento declaraba pendientes al nacer. `SP` publica **tres interfaces de aplicación de solo lectura** —membresía y su nivel, moneda y sus decimales, membresía vigente de una persona— y `PM` las importa; el desarrollo está en `architecture.md` §15.2 y vale para cualquier par de módulos. Las fichas de `RF-PM-001` y `RF-PM-007` **dejan de depender de una decisión pendiente**: los siete requerimientos tienen ya spec aprobada y ninguno tiene bloqueada su segunda compuerta. Las tareas que escriben esos puertos pertenecen a esos dos requerimientos aunque el código viva en paquetes de `SP`. | Responsable del proyecto |
| 0.10.0 | 26-08-2026 | **Los siete `plan.md` aprobados**: el módulo cruza entero la segunda compuerta el mismo día que la primera. Dos decisiones de los planes quedan firmes y alcanzan más allá de su requerimiento: la **lectura estrecha del motivo de eliminación en `shared/audit`**, que es de donde `RF-PM-003` toma el motivo que devuelve, y el **`JOIN` a `memberships`** con el que `RF-PM-002` resuelve el destino de cada upgrade en lugar de llamar al puerto fila a fila. Lo que sigue es la tercera compuerta: las `tasks.md`. | Responsable del proyecto |
| 0.11.0 | 26-08-2026 | **Las siete `tasks.md` aprobadas**: la tripleta del módulo está completa y el código puede escribirse (Art. I.1). **95 tareas**, con el orden de implementación fijado por una dependencia que no es la de los identificadores: `RF-PM-003` necesita una eliminación registrada, que escribe `RF-PM-006`, de modo que la secuencia es `001 → 002 → 005 → 006 → 003 → 004 → 007`. Tres tareas escriben **fuera de `PM`** —dos interfaces en `SP`, una tercera para la membresía vigente, y la lectura estrecha del motivo en `shared/audit`— y son las de mayor riesgo: una regresión ahí alcanza a `SP` entero, y por eso su definición de terminado exige que su suite siga en verde sin cambios. | Responsable del proyecto |
| 0.12.0 | 27-08-2026 | **Los productos ganan vigencia de adquisición, medida en días** (`RN-PM-015`), por decisión del responsable del proyecto. Es **opcional y en los dos tipos**: sin ella, lo adquirido **no caduca** —comprar Oro y quedarse en Oro—; con ella, el derecho dura los días que declare, contados desde la compra. Se descartó hacerla obligatoria porque vender algo permanente habría exigido un valor de relleno —mil años— que ningún `CHECK` distingue de un error de tecleo. §10 incorpora `validity_days` y `ck_products_validity_positive`, cuya rama `IS NULL` se escribe **explícita** aunque la comparación sola también admitiría el nulo: así el permiso es deliberado y no accidental. **Dos condiciones más sobre la compra futura**, en §1.4: cada compra guardará **la vigencia que compró** además del importe —o corregir una vigencia reescribiría lo ya vendido—, y **al vencer, la persona se queda sin nivel vigente**: no vuelve al que tenía antes, porque eso habría exigido que la compra guardase cuál era, ni baja al más bajo, que castigaría a quien ya estaba arriba. Enmienda las siete tripletas, aprobadas el día anterior (Art. I.7). | Responsable del proyecto |
