# SPEC — `RF-PM-017` Registrar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-017` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |
| Enmendada el | 16-09-2026 — **la respuesta trae `coverImageUrl`, presente y nula**: la portada llega después del alta (`RN-PM-045`, `RF-PM-028`). Ver §15 |

---

## 1. Objetivo

Poner en el sistema un **paquete vacío** —código, nombre, moneda y alcance— para empezar a armarlo: los productos entran después, con su descuento, por `RF-PM-023`.

## 2. Contexto

Las cuatro decisiones que dan forma al submódulo están en [`requirements/pm.md` §5.2.10](../../../requirements/pm.md) y no se repiten aquí. Esta spec es el **alta**: crea las dos tablas —`product_packages` y `product_package_items`— y **siembra los cuatro permisos `packages:`**, con la obligación de asociarlos a `SUPERADMIN` y `ADMIN` en la misma migración.

**El paquete nace vacío y sin precio.** Vacío porque asociar productos es otra operación con sus propias reglas —moneda, descuento, origen—, y meterlas en el alta produciría una petición que falla por siete motivos distintos a la vez. Sin precio porque **no lo tiene nunca**: es la suma de lo que contiene, calculada en cada lectura (`RN-PM-036`).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Registra el paquete |

## 4. Alcance

### 4.1 Incluye

- Registrar un paquete con **código, nombre, moneda y alcance**, obligatorios, y **descripción** opcional.
- Crear `product_packages` y `product_package_items`, y sembrar `packages:read`, `packages:create`, `packages:update` y `packages:delete`.
- Devolver el paquete recién creado **con su cuenta hecha**: cero productos, precio cero, `offerable: false` con su motivo.

### 4.2 No incluye

- **Asociar productos en el alta.** `RF-PM-023`.
- **Activarlo.** Nace `INACTIVO` (`RN-PM-041`) y se publica con `RF-PM-021`, cuando tenga descripción y al menos dos productos.
- **Elegir la implementación.** Un paquete no la tiene: se entrega producto a producto, cada uno con la suya.
- **Un precio declarado.** No existe el campo, y no existirá (`RN-PM-036`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-035` | **El paquete declara su moneda al nacer, no la cambia, y solo reúne productos en esa moneda** | `requirements/pm.md` §5.1 |
| `RN-PM-041` | **El paquete es catálogo, y hereda las reglas de forma del producto**: código inmutable y no liberado, nombre único entre vivos, nace inactivo, alcance obligatorio | `requirements/pm.md` §5.1 |
| `RN-PM-036` | El precio se calcula siempre — y aquí, sobre nada, es cero | `requirements/pm.md` §5.1 |
| `RN-PM-008` | La moneda debe estar activa al declararla | `requirements/pm.md` §5.1 |
| `RN-SEG-003` | Los permisos se conceden por rol; ningún rol concede lo que su padre no tiene | `security.md` §4 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Código (`code`) | Sí | Referencia estable e inmutable | `^[A-Z][A-Z0-9_]*$`, hasta 50; se normaliza a mayúsculas; **único frente a todos**, retirados incluidos |
| Nombre (`name`) | Sí | Cómo se llama | Hasta 150 tras recortar; **único entre los vivos** sin distinguir mayúsculas ni acentos |
| Descripción (`description`) | No | Qué se lleva quien lo compra | Texto libre; **obligatoria para activar**, no para registrar |
| Moneda (`currencyId`) | Sí | En qué se expresa el paquete entero | Debe existir y estar activa (`RN-PM-008`); **inmutable** |
| Alcance (`scope`) | Sí | En qué vistas se publica | `TIENDA`, `HOTLINK`, `AMBOS` o `NINGUNO` —el mismo dominio que el producto desde el 15-09-2026—, **sin valor por omisión** (`RN-PM-019`) |

**Ni precio, ni implementación, ni productos.** Un `price` en el cuerpo es un campo desconocido y se rechaza como tal.

### 6.2 Salida

`201` con el paquete en la **misma forma del detalle** (`RF-PM-019`): identificador, código, nombre, descripción, moneda resuelta, alcance, estado `INACTIVO`, `items` vacío, `price`, `listPrice` y `savings` en **cero**, `exchange` nulo y presente, `offerable: false` con `offerableReason` diciendo que faltan productos, y las dos fechas iguales.

**Se devuelve la forma completa aunque esté vacía**, para que el front trate «acabo de crearlo» y «lo abrí» igual, como hace el alta del producto.

**Y desde el 16-09-2026 trae `coverImageUrl`, presente y nula** (`RN-PM-045`): el alta sigue siendo JSON y la portada se sube después con `RF-PM-028`, de modo que nula es lo único que puede traer un paquete recién registrado — como el producto desde `RF-PM-014`. **El cuerpo no admite la imagen ni un icono ni un color**: el paquete no declara ninguno de los dos, y sin portada el frontend pinta los suyos por omisión.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `packages:create`; código libre frente a todos; nombre libre entre los vivos; moneda existente y activa.

**Postcondiciones:** existe la fila en `product_packages` con `status = INACTIVO`, sin filas en `product_package_items`; `audit_change_log` tiene una fila `CREATE` de `product_packages`. Ningún evento de seguridad: un paquete no concede nada.

## 8. Flujo principal

1. Llega la petición con código, nombre, moneda, alcance y —si viene— descripción.
2. El sistema valida la forma de los cinco (§11).
3. El sistema comprueba que el **código** no existe —ni vivo ni retirado— (`EX-001`).
4. El sistema comprueba que el **nombre** no lo usa otro paquete vivo (`EX-002`).
5. El sistema comprueba que la **moneda** existe y está activa, por la interfaz que `SP` publica (`EX-003`).
6. El sistema inserta el paquete en `INACTIVO` y registra la creación en la auditoría, en la misma transacción.
7. Devuelve `201` con el paquete vacío.

Los pasos 3 y 4 tienen su red en el esquema —`uq_product_packages_code` total y `uq_product_packages_name` parcial—: la carrera entre dos altas simultáneas la muerde el índice y el repositorio la traduce al mismo `409` que la comprobación previa, como en `RF-PM-001`.

## 9. Flujos alternativos

### FA-001 — Sin descripción

**Comportamiento:** se registra igual. La descripción es lo que `RF-PM-021` exigirá para activar, no para existir.

### FA-002 — El código llega en minúsculas

**Comportamiento:** se normaliza a mayúsculas antes de validar y de comparar, como el del producto.

## 10. Excepciones

### EX-001 — El código ya existe

**Condición:** hay un paquete —vivo o retirado— con ese código.
**Respuesta del sistema:** `409` — *«Ya existe un paquete con ese código.»* No distingue si el que lo ocupa está retirado: el código **no se libera** (`RN-PM-041`).

### EX-002 — El nombre ya lo usa un paquete vivo

**Respuesta del sistema:** `409` — *«Ya existe un paquete con ese nombre.»* Un retirado **sí** libera el nombre.

### EX-003 — La moneda no existe o está inactiva

**Respuesta del sistema:** `422` — *«La moneda indicada no existe o no está activa.»* Es el mismo código y el mismo trato que da `RF-PM-001` a la moneda del producto.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Código presente y con la forma admitida | El código es obligatorio y debe empezar por una letra mayúscula y contener solo letras mayúsculas, dígitos y guion bajo. |
| `VAL-002` | Nombre presente y de hasta 150 caracteres tras recortar | El nombre es obligatorio y no puede superar los 150 caracteres. |
| `VAL-003` | Moneda presente | La moneda es obligatoria. |
| `VAL-004` | Alcance presente y dentro del dominio | El alcance es obligatorio y debe ser TIENDA, HOTLINK, AMBOS o NINGUNO. |
| `VAL-005` | Ningún campo desconocido — en particular, ni `price` ni `products` ni `status` | El cuerpo de la petición contiene campos no admitidos. |

Las cuatro primeras se devuelven **juntas**: quien se equivocó en dos corrige una vez.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-261` | El sistema registra el paquete con `201`, en `INACTIVO`, sin productos, con `price`, `listPrice` y `savings` en **cero** y `offerable: false` |
| `CA-PM-262` | El sistema rechaza con `409` un código que ya usa otro paquete, **también si ese paquete está retirado** |
| `CA-PM-263` | El sistema rechaza con `409` un nombre que ya usa un paquete vivo, sin distinguir mayúsculas ni acentos, y **admite** el de uno retirado |
| `CA-PM-264` | El sistema rechaza con `422` una moneda inexistente y una inactiva |
| `CA-PM-265` | El sistema rechaza con `400` el código mal formado, el nombre ausente, la moneda ausente y el alcance ausente o fuera de dominio, **juntos** |
| `CA-PM-266` | El sistema rechaza con `400` un cuerpo que traiga `price`, `products` o `status` |
| `CA-PM-267` | El sistema registra una fila `CREATE` en `audit_change_log` con el actor, en la misma transacción |
| `CA-PM-268` | Los cuatro permisos `packages:` están sembrados con identificador estable y asociados a `SUPERADMIN` y `ADMIN`, y **no** a `CLIENTE`; sin `packages:create` el alta responde `403` aunque el actor porte los cuatro `products:` |
| `CA-PM-371` | La respuesta del alta trae **`coverImageUrl` presente y nula** (16-09-2026) |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos altas simultáneas con el mismo código | Una queda y la otra recibe `409` por el índice total, no `500` |
| El nombre coincide con el de un **producto** | Se admite: son catálogos distintos y la unicidad es por tabla |
| La moneda se desactiva **después** | El paquete no se invalida, como el producto (`RN-PM-008`) |
| Se registra con una descripción de solo espacios | Se guarda como **nula**: recortada queda vacía, y una descripción vacía no permite activar |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se admite crear el paquete **con** sus productos en la misma petición? | **No.** La asociación tiene reglas propias —moneda, descuento, origen de los upgrades— y un alta que las juntara fallaría por siete motivos a la vez y necesitaría un rollback parcial que nadie sabría leer. Dos peticiones, dos responsabilidades |
| 2 | ¿Por qué la moneda es un dato del paquete si se deduce de sus productos? | Porque un paquete **vacío** también la tiene, y es contra la que se comprueba cada asociación (`RN-PM-035`). Deducirla del primer producto haría que el segundo producto fallara por una razón que el usuario no declaró en ningún sitio |
| 3 | ¿Se reutilizan los `products:`? | **No**, por decisión del responsable del proyecto (§5.2.10): recurso propio |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. **El paquete nace vacío y sin precio**: asociar es otra operación con reglas propias, y el precio no existe como campo (`RN-PM-036`). Hereda la forma del producto —código inmutable y no liberado, nombre único entre vivos, nace inactivo, alcance sin omisión— y **la moneda es propia e inmutable** aunque se deduzca de los productos, porque un paquete vacío también la tiene. Crea las dos tablas y siembra los cuatro `packages:` con la guarda de siempre. | Responsable técnico |
| 0.2.0 | 15-09-2026 | **Construida** (`V91`, `V93`, `PackagesIT`). Dos enmiendas de Art. I.7 al construir: **el alcance adopta los cuatro valores** de [`requirements/pm.md`](../../../requirements/pm.md) v0.35.0 §5.2.11 —`VAL-004` cambia de mensaje y `ck_product_packages_scope` nace ya con los cuatro—; y **la siembra de permisos es `V93` y no `V92`**, porque `V92` la tomó el alcance de los productos el mismo día («una migración reservada no está reservada», como el plan advertía). Los identificadores de los permisos son los previstos: `…000008` a `…000011`. | Responsable técnico |
| 0.3.0 | 16-09-2026 | **La respuesta trae `coverImageUrl`, presente y nula** (`RN-PM-045`, [`requirements/pm.md`](../../../requirements/pm.md) v0.37.0 §5.2.12): la portada del paquete llega después del alta, con `RF-PM-028`, y el alta sigue siendo JSON. Sin icono ni color en el cuerpo: el paquete no los declara. `CA-PM-371`. Enmienda que construye `RF-PM-028` (Art. I.7). | Responsable del proyecto |
