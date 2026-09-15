# SPEC — `RF-PM-020` Editar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-020` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Corregir **lo que describe** al paquete —nombre, descripción, alcance— sin tocar **lo que contiene** ni **lo que lo identifica**.

## 2. Contexto

Es `RF-PM-004` para paquetes, con una frontera más corta. Allí lo inmutable eran el tipo, el código y las dos membresías —lo que define qué derecho otorga—; aquí lo inmutable es **el código** (`RN-PM-041`) y **la moneda** (`RN-PM-035`): los productos ya asociados están en ella, y cambiarla dejaría un paquete que suma monedas distintas. Los productos y sus descuentos no se corrigen por aquí: tienen sus tres operaciones.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Corrige el paquete |

## 4. Alcance

### 4.1 Incluye

- Corregir **nombre**, **descripción** y **alcance** de un paquete vivo, con semántica `PATCH`: lo que no viene no cambia.
- Vaciar la descripción con nulo explícito.
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

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta. Paquete **vivo** |
| `name` | No | Nombre nuevo | Hasta 150 tras recortar; **no admite vaciarse**; único entre vivos |
| `description` | No | Descripción nueva | **Admite vaciarse** con nulo explícito |
| `scope` | No | Alcance nuevo | `TIENDA` o `HOTLINKS`; **no admite vaciarse** |

Los tres estados de un campo —ausente, nulo, con valor— importan como en `RF-PM-004`: el nombre y el alcance rechazan el nulo; la descripción lo toma como orden. **Un cuerpo sin ningún campo corregible responde `400`**, como en toda corrección del módulo.

### 6.2 Salida

`200` con el detalle (`RF-PM-019`), `updatedAt` avanzado si algo cambió.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `packages:update`; paquete vivo; el nombre nuevo, si viene, libre entre los vivos.

**Postcondiciones:** los campos presentes tienen su valor nuevo y `updated_at` avanzó solo si algo cambió; `audit_change_log` tiene la fila `UPDATE` con el antes y el después de lo tocado.

## 8. Flujo principal

1. Llega la petición.
2. El sistema rechaza los campos inmutables si vienen (`EX-003`) y comprueba que hay algo que corregir (`VAL-005`).
3. El sistema resuelve el paquete **vivo**, bloqueándolo (`EX-001`).
4. El sistema valida la forma de lo presente (§11) y, si viene nombre, que no lo use otro vivo (`EX-002`).
5. El sistema aplica lo presente; si nada cambió de valor, devuelve el detalle sin escribir.
6. Escribe, audita y devuelve `200`.

## 9. Flujos alternativos

### FA-001 — Vaciar la descripción de un paquete activo

**Comportamiento:** se vacía. El paquete **no cambia de estado**, pero **deja de ofrecerse** (`RN-PM-040`, `RN-PM-039`) hasta que vuelva a tener descripción, y el detalle lo dice. Es lo mismo que hace `RF-PM-004` con un producto activo.

### FA-002 — Cambiar el alcance de `HOTLINKS` a `TIENDA`

**Comportamiento:** el hotlink del paquete deja de resolver (`RF-PM-026`); la oferta no cambia. Es lo que `RN-PM-019` existe para permitir.

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
| `VAL-003` | El alcance no admite vaciarse y está en el dominio | El alcance del paquete es obligatorio y debe ser TIENDA u HOTLINKS. |
| `VAL-004` | Ni `code` ni `currencyId` | El código y la moneda del paquete no se pueden modificar. |
| `VAL-005` | Al menos un campo corregible | Debe informar al menos uno de los campos corregibles. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-285` | El sistema corrige nombre, descripción y alcance por separado y juntos, y devuelve el detalle con `updatedAt` avanzado |
| `CA-PM-286` | El nulo explícito **vacía** la descripción y **se rechaza** en nombre y alcance |
| `CA-PM-287` | El sistema rechaza con `400` `code` y `currencyId`, y un cuerpo vacío |
| `CA-PM-288` | El sistema rechaza con `409` un nombre que ya usa otro vivo y admite el de uno retirado; y con `404` un paquete retirado |
| `CA-PM-289` | Vaciar la descripción de un paquete **activo** lo deja `offerable: false` con su motivo, sin cambiar su estado |
| `CA-PM-290` | Un cuerpo sin cambios de valor responde `200` sin avanzar `updatedAt` ni auditar; uno con cambios deja la fila `UPDATE` con antes y después |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Corregir el nombre al mismo con otra caja (`Combo oro` → `Combo Oro`) | **Es un cambio**: el nombre se guarda como se escribió, y las dos formas difieren en la fila aunque choquen en la unicidad. Cambia y audita; la unicidad no lo rechaza porque el que choca es él mismo (`existsAliveNameForOther`, como en el producto) |
| Dos correcciones simultáneas | El bloqueo las ordena; gana la última |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se podría cambiar la moneda de un paquete **vacío**? | **No, y es deliberado**: sería una excepción que solo vale mientras el paquete no tiene nada, y una regla con «mientras» es la que se olvida. Quien se equivocó de moneda en un paquete vacío lo retira y crea otro: cuesta dos peticiones |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. Hereda `RF-PM-004` con una frontera más corta: inmutables el **código** y la **moneda** —también en un paquete vacío, para no tener una regla con «mientras»—. Vaciar la descripción de un activo no lo desactiva: lo deja sin poder ofrecerse y el detalle lo dice. | Responsable técnico |
