# SPEC — `RF-PM-020` Editar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-020` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |
| Enmendada el | 16-09-2026 — **las dos fechas de vigencia se corrigen, y el fin se vacía** (`RN-PM-047`). Ver §15 |

---

## 1. Objetivo

Corregir **lo que describe** al paquete —nombre, descripción, alcance y, desde el 16-09-2026, **cuándo se ofrece**— sin tocar **lo que contiene** ni **lo que lo identifica**.

## 2. Contexto

Es `RF-PM-004` para paquetes, con una frontera más corta. Allí lo inmutable eran el tipo, el código y las dos membresías —lo que define qué derecho otorga—; aquí lo inmutable es **el código** (`RN-PM-041`) y **la moneda** (`RN-PM-035`): los productos ya asociados están en ella, y cambiarla dejaría un paquete que suma monedas distintas. Los productos y sus descuentos no se corrigen por aquí: tienen sus tres operaciones.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Corrige el paquete |

## 4. Alcance

### 4.1 Incluye

- Corregir **nombre**, **descripción**, **alcance** y —desde el 16-09-2026— **inicio y fin de vigencia** de un paquete vivo, con semántica `PATCH`: lo que no viene no cambia.
- Vaciar la descripción y el fin de vigencia con nulo explícito.
- Devolver el paquete en la forma del detalle.

### 4.2 No incluye

- **Código y moneda.** Se rechazan si vienen, no se ignoran.
- **Estado**: `RF-PM-021`. **Productos y descuentos**: `RF-PM-023` a `RF-PM-025`.
- **Motivo de la corrección**: la auditoría registra qué cambió (`RF-PM-004`, resolución del 26-08-2026).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-035` | La moneda no se cambia | `requirements/pm.md` §5.1 |
| `RN-PM-041` | Código inmutable; nombre único entre vivos; alcance obligatorio y acumulativo | `requirements/pm.md` §5.1 |
| `RN-PM-040` | Vaciar la descripción de un paquete **activo** lo deja **sin poder ofrecerse** — no lo desactiva | `requirements/pm.md` §5.1 |
| `RN-PM-047` | **(Desde el 16-09-2026)** Las dos fechas se corrigen y el fin se vacía; la pareja resultante se comprueba entera; **cerrar el paquete es poner el fin en ayer**, y no lo desactiva: lo deja sin poder ofrecerse, como la descripción | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta. Paquete **vivo** |
| `name` | No | Nombre nuevo | Hasta 150 tras recortar; **no admite vaciarse**; único entre vivos |
| `description` | No | Descripción nueva | **Admite vaciarse** con nulo explícito |
| `scope` | No | Alcance nuevo | `TIENDA`, `HOTLINK`, `AMBOS` o `NINGUNO`; **no admite vaciarse** |
| `validFrom` | No | Inicio de vigencia nuevo (16-09-2026) | Fecha; **no admite vaciarse**: un paquete siempre sabe desde cuándo |
| `validTo` | No | Fin de vigencia nuevo (16-09-2026) | Fecha; **admite vaciarse** con nulo explícito, que es volver a indefinido. La pareja resultante —lo que venga más lo que ya había— no puede tener el fin antes del inicio |

Los tres estados de un campo —ausente, nulo, con valor— importan como en `RF-PM-004`: el nombre, el alcance y el inicio de vigencia rechazan el nulo; la descripción y el fin de vigencia lo toman como orden. **Un cuerpo sin ningún campo corregible responde `400`**, como en toda corrección del módulo.

### 6.2 Salida

`200` con el detalle (`RF-PM-019`), `updatedAt` avanzado si algo cambió.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `packages:update`; paquete vivo; el nombre nuevo, si viene, libre entre los vivos.

**Postcondiciones:** los campos presentes tienen su valor nuevo y `updated_at` avanzó solo si algo cambió; `audit_change_log` tiene la fila `UPDATE` con el antes y el después de lo tocado.

## 8. Flujo principal

1. Llega la petición.
2. El sistema rechaza los campos inmutables si vienen (`EX-003`) y comprueba que hay algo que corregir (`VAL-005`).
3. El sistema resuelve el paquete **vivo**, bloqueándolo (`EX-001`).
4. El sistema valida la forma de lo presente (§11) —la vigencia sobre la **pareja resultante**, con lo que venga y lo que ya había— y, si viene nombre, que no lo use otro vivo (`EX-002`).
5. El sistema aplica lo presente; si nada cambió de valor, devuelve el detalle sin escribir.
6. Escribe, audita y devuelve `200`.

## 9. Flujos alternativos

### FA-001 — Vaciar la descripción de un paquete activo

**Comportamiento:** se vacía. El paquete **no cambia de estado**, pero **deja de ofrecerse** (`RN-PM-040`, `RN-PM-039`) hasta que vuelva a tener descripción, y el detalle lo dice. Es lo mismo que hace `RF-PM-004` con un producto activo.

### FA-002 — Cambiar el alcance de `AMBOS` o `HOTLINK` a `TIENDA`

**Comportamiento:** el hotlink del paquete deja de resolver (`RF-PM-026`); la oferta no cambia. Es lo que `RN-PM-019` existe para permitir.

### FA-003 — Cerrar la vigencia de un paquete activo (16-09-2026)

**Comportamiento:** `validTo` en ayer, o en hoy para que sea el último día. El paquete **no cambia de estado** y **deja de ofrecerse** al día siguiente del fin (`RN-PM-047`, `RN-PM-039`), y el detalle dice «la vigencia terminó el …». Es `FA-001` con otra fecha: la forma de retirar una promoción sin desactivarla ni retirarla. Vaciar el fin la vuelve a abrir.

## 10. Excepciones

### EX-001 — El paquete no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un paquete vivo con ese identificador.»*

### EX-002 — El nombre ya lo usa otro paquete vivo

**Respuesta del sistema:** `409` — *«Ya existe un paquete con ese nombre.»*

### EX-003 — Se intenta cambiar el código o la moneda

**Respuesta del sistema:** `400` — *«El código y la moneda del paquete no se pueden modificar.»* **Se rechazan, no se ignoran**: ignorarlos haría creer que el cambio se aplicó.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | El nombre no admite vaciarse y cabe en 150 | El nombre del paquete no puede quedar vacío ni superar los 150 caracteres. |
| `VAL-003` | El alcance no admite vaciarse y está en el dominio | El alcance del paquete es obligatorio y debe ser TIENDA, HOTLINK, AMBOS o NINGUNO. |
| `VAL-004` | Ni `code` ni `currencyId` | El código y la moneda del paquete no se pueden modificar. |
| `VAL-005` | Al menos un campo corregible | Debe informar al menos uno de los campos corregibles. |
| `VAL-006` | El inicio de vigencia no admite vaciarse (16-09-2026) | El inicio de vigencia es obligatorio. |
| `VAL-007` | La pareja resultante tiene el fin no anterior al inicio (16-09-2026) | El fin de vigencia no puede ser anterior a su inicio. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-285` | El sistema corrige nombre, descripción y alcance por separado y juntos, y devuelve el detalle con `updatedAt` avanzado |
| `CA-PM-286` | El nulo explícito **vacía** la descripción y **se rechaza** en nombre y alcance |
| `CA-PM-287` | El sistema rechaza con `400` `code` y `currencyId`, y un cuerpo vacío |
| `CA-PM-288` | El sistema rechaza con `409` un nombre que ya usa otro vivo y admite el de uno retirado; y con `404` un paquete retirado |
| `CA-PM-289` | Vaciar la descripción de un paquete **activo** lo deja `offerable: false` con su motivo, sin cambiar su estado |
| `CA-PM-290` | Un cuerpo sin cambios de valor responde `200` sin avanzar `updatedAt` ni auditar; uno con cambios deja la fila `UPDATE` con antes y después |
| `CA-PM-374` | El sistema corrige `validFrom` y `validTo` por separado y juntos, con su antes y su después en la auditoría; **`validTo: null` vacía** y **`validFrom: null` se rechaza**; y rechaza con `400` la pareja resultante con el fin antes del inicio —también cuando solo viene **uno** de los dos y choca con el que ya había— (16-09-2026) |
| `CA-PM-375` | Poner `validTo` en ayer a un paquete **activo** y ofrecible lo deja `offerable: false` con «la vigencia terminó el …», sin cambiar su estado; vaciarlo lo devuelve a `offerable: true` (16-09-2026) |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Corregir el nombre al mismo con otra caja (`Combo oro` → `Combo Oro`) | **Es un cambio**: el nombre se guarda como se escribió, y las dos formas difieren en la fila aunque choquen en la unicidad. Cambia y audita; la unicidad no lo rechaza porque el que choca es él mismo (`existsAliveNameForOther`, como en el producto) |
| Dos correcciones simultáneas | El bloqueo las ordena; gana la última |
| Mover `validFrom` a mañana en un paquete activo | **Se admite** y lo oculta hasta mañana: «todavía no empieza». Es la otra mitad de `FA-003` |
| Corregir solo `validFrom` a una fecha posterior al `validTo` que ya había | **`400`** con `VAL-007`: la pareja se comprueba entera aunque venga un solo campo. Quien quiera mover las dos las manda juntas |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se podría cambiar la moneda de un paquete **vacío**? | **No, y es deliberado**: sería una excepción que solo vale mientras el paquete no tiene nada, y una regla con «mientras» es la que se olvida. Quien se equivocó de moneda en un paquete vacío lo retira y crea otro: cuesta dos peticiones |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. Hereda `RF-PM-004` con una frontera más corta: inmutables el **código** y la **moneda** —también en un paquete vacío, para no tener una regla con «mientras»—. Vaciar la descripción de un activo no lo desactiva: lo deja sin poder ofrecerse y el detalle lo dice. | Responsable técnico |
| 0.2.0 | 15-09-2026 | **Construida** (`PackageUpdateIT`). Enmienda de Art. I.7 al construir: **el alcance adopta los cuatro valores** de [`requirements/pm.md`](../../../requirements/pm.md) v0.35.0 §5.2.11 (`VAL-003`, `FA-002`); a `NINGUNO` el paquete deja de ofrecerse en las dos vistas sin cambiar de estado. | Responsable técnico |
| 0.3.0 | 16-09-2026 | **Las dos fechas de vigencia se corrigen, y el fin se vacía** (`RN-PM-047`, [`requirements/pm.md`](../../../requirements/pm.md) v0.39.0 §5.2.13). El inicio **no** es inmutable, al revés que en la tasa personalizada de `CM`: allí decide qué se pagó; aquí no hay nada pagado ni ninguna fila de otra tabla que dependa de cuándo empezó. La pareja resultante se comprueba entera aunque venga un solo campo. Cerrar la vigencia es `FA-001` con otra fecha (`FA-003`). `VAL-006`, `VAL-007`, `CA-PM-374`, `CA-PM-375`. | Responsable del proyecto |
