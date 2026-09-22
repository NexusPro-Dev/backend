# SPEC — `RF-SP-066` Editar equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-066` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Objetivo

Corregir **cómo se llama un equipo y qué dice de sí mismo**, sin tocar quién está dentro ni si está en uso.

## 2. Contexto

Es `RF-SP-004` para equipos, y hereda de él lo que esa edición decidió el primer día: **se editan los campos descriptivos y nada más**. El estado tiene su requerimiento (`RF-SP-067`), los miembros tienen los suyos (`RF-SP-069`, `RF-SP-070`) y la eliminación el suyo (`RF-SP-068`), porque cada una de esas operaciones tiene reglas propias y mezclarlas en un `PATCH` obligaría a que un solo cuerpo pudiera fallar por cuatro motivos distintos.

**Renombrar es la operación de verdad, y no es cosmética.** El nombre es lo único que identifica a un equipo ante una persona (`RN-SP-050`), y renombrarlo revalida la unicidad contra los demás equipos no eliminados — por eso la edición puede devolver `409`, que es lo que la distingue de una corrección de texto.

**Un equipo eliminado no se edita.** Corregir el nombre de algo que ya no existe no tiene efecto útil y sí uno indeseado: liberaría o reservaría un nombre según el capricho de la edición, cuando `RN-SP-050` ya decidió que un eliminado **no compite** por la unicidad.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador con `teams:update` | Corrige el nombre o la descripción |

## 4. Alcance

### 4.1 Incluye

- Modificar el **nombre**, con revalidación de la unicidad entre los no eliminados.
- Modificar la **descripción**, incluida la posibilidad de dejarla vacía.
- Devolver el equipo actualizado en la forma del detalle (`RF-SP-065`).

### 4.2 No incluye

- **El estado.** Es `RF-SP-067`, con su regla propia (`RN-SP-053`).
- **Los miembros.** Son `RF-SP-069` y `RF-SP-070`, con motivo y con historial.
- **Eliminar.** Es `RF-SP-068`.
- **Editar un equipo eliminado.** §14.2.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-050` | El nombre es único entre los no eliminados, sin distinguir mayúsculas ni acentos | `requirements/sp.md` §5.1 |
| `RN-SEG-014` | Un permiso gobierna una operación: editar no es cambiar el estado ni eliminar | `security.md` §4.3 |
| Art. V.7 | El cambio se audita; la fila no lleva columnas de actor | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| `id` | Sí | Qué equipo | `uuid` en la ruta; equipo **no eliminado** |
| `name` | No | El nombre nuevo | Si viene: hasta 100 tras recortar, con contenido, y único entre los no eliminados |
| `description` | No | La descripción nueva | Si viene: hasta 500; **`null` explícito la borra**, y una de solo espacios se guarda nula |

**Al menos uno de los dos debe venir** (`VAL-003`): un `PATCH` vacío no es una corrección, es una petición sin intención, y responderle `200` haría creer que algo cambió.

**Omitir un campo no es borrarlo.** `description` ausente deja la que había; `description: null` la borra. Es la distinción que todo `PATCH` del sistema hace, y la única forma de vaciar un campo opcional sin inventar un `DELETE` para él.

### 6.2 Salida

`200` con el equipo en la **forma del detalle** (`RF-SP-065`), ya con los valores nuevos, sus miembros vigentes y `updatedAt` **distinto** de `createdAt`.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `teams:update`; el equipo existe y **no está eliminado**; si viene nombre, está libre entre los no eliminados.

**Postcondiciones:** la fila refleja los valores nuevos y `updated_at` avanza; **`status`, `deleted_at` y las pertenencias no cambian**; `audit_change_log` tiene una fila `UPDATE` con el diff de los campos modificados, en la misma transacción.

## 8. Flujo principal

1. Llega la petición con uno de los dos campos, o con los dos.
2. El sistema valida la forma de lo que venga y que venga **algo** (§11).
3. El sistema resuelve el equipo **no eliminado**; si no lo encuentra, `EX-001`.
4. Si el nombre cambia, comprueba que no lo use otro equipo no eliminado (`EX-002`).
5. El sistema aplica los cambios, avanza `updated_at` y registra el diff en la auditoría, en la misma transacción.
6. Devuelve `200` con el detalle.

El paso 4 tiene su red en `uq_teams_name`, igual que el alta: la carrera entre dos renombrados al mismo nombre la muerde el índice y sale por el mismo `409`.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | El nombre nuevo es **el mismo** que ya tenía | Se admite y no es un error: la unicidad no se viola contra uno mismo. `updated_at` avanza y el diff sale vacío o con el resto de campos |
| `FA-002` | Solo cambia la descripción | Se admite; no se toca la unicidad del nombre ni se consulta |
| `FA-003` | `description: null` | La descripción se borra: la respuesta la trae **presente y nula** |
| `FA-004` | El nombre nuevo difiere de otro solo en mayúsculas o acentos | `409` (`EX-002`), por lo mismo que en el alta |
| `FA-005` | El nombre nuevo lo tiene un equipo **eliminado** | Se admite: un eliminado no compite por la unicidad (`RN-SP-050`) |
| `FA-006` | El equipo está `INACTIVO` | **Se edita igual.** Inactivo significa que no recibe miembros (`RN-SP-053`), no que sea inmutable; corregir una errata en un equipo suspendido es exactamente lo que se hace antes de reactivarlo |

## 10. Excepciones

### EX-001 — El equipo no existe o está eliminado

**Condición:** no hay equipo con ese identificador, o lo hay y tiene `deleted_at`.
**Respuesta del sistema:** `404` — *«No existe un equipo con ese identificador.»* **Los dos casos responden igual**, al contrario que en `RF-SP-068`: allí el `409` de «ya estaba eliminado» informa de que el retiro anterior funcionó; aquí un equipo eliminado **no es editable en ningún caso**, y distinguirlo solo añadiría un código sin decisión detrás.

### EX-002 — El nombre nuevo ya lo usa otro equipo no eliminado

**Respuesta del sistema:** `409` — *«Ya existe un equipo con ese nombre.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | `name`, si viene, con contenido y de hasta 100 caracteres tras recortar | El nombre no puede estar vacío ni superar los 100 caracteres. |
| `VAL-002` | `description`, si viene con valor, de hasta 500 caracteres | La descripción no puede exceder 500 caracteres. |
| `VAL-003` | Al menos uno de los dos campos presente | Debe indicar al menos un campo a modificar. |
| `VAL-004` | Ningún campo desconocido — en particular, ni `status`, ni `members` | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-756` | El sistema cambia el nombre con `200` y devuelve la forma del detalle con el valor nuevo, sus miembros vigentes y `updatedAt` **distinto** de `createdAt` |
| `CA-SP-757` | El sistema cambia solo la descripción sin tocar el nombre; **`description: null` la borra** y la respuesta la trae presente y nula; omitirla la conserva |
| `CA-SP-758` | El sistema rechaza con `409` un nombre que ya usa otro equipo no eliminado, **sin distinguir mayúsculas ni acentos**, y **admite** el de uno eliminado y **el suyo propio** |
| `CA-SP-759` | El sistema rechaza con `400` el nombre vacío o largo, la descripción larga, el cuerpo **sin ningún campo** (`VAL-003`) y el cuerpo con `status` o `members` (`VAL-004`) |
| `CA-SP-760` | El sistema responde `404` al equipo inexistente **y al eliminado**, con el mismo código |
| `CA-SP-761` | La edición **no toca** el estado, `deleted_at` ni las pertenencias: un equipo `INACTIVO` con miembros se edita y sigue `INACTIVO` con los mismos miembros |
| `CA-SP-762` | `audit_change_log` tiene una fila `UPDATE` con el actor y **el diff de los campos modificados**, en la misma transacción; sin `teams:update` la operación responde `403` aunque el actor porte `teams:read` y `teams:create` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Renombrar al mismo nombre que ya tenía | Se admite (`FA-001`): la unicidad no se viola contra uno mismo, y rechazarlo obligaría al frontend a comparar antes de enviar |
| Dos renombrados simultáneos al mismo nombre | Uno queda y el otro recibe `409` por el índice, no `500` |
| Renombrar un equipo y crear otro con el nombre viejo, a la vez | Se ordenan por el índice: el alta pasa si el renombrado ya se confirmó, y recibe `409` si no. Ninguna de las dos deja dos equipos con el mismo nombre |
| `description` con 500 caracteres exactos | Se admite: el tope es inclusivo, como en `roles` |
| Editar un equipo con miembros dentro | Se admite: los miembros no dependen del nombre; siguen ahí y la respuesta los trae |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La edición cambia también el estado, como haría un `PUT`? | **No.** `RF-SP-067` tiene `RN-SP-053` detrás —un equipo `INACTIVO` no recibe miembros— y merece su permiso y su auditoría. Es la misma separación que `roles` hace desde `RF-SP-004` |
| 2 | ¿Se puede editar un equipo eliminado? | **No**, `404`. Corregir el nombre de un eliminado no tiene efecto útil y sí uno raro: mover una unicidad de la que ya no forma parte |
| 3 | ¿El nombre es inmutable, como el código de un rol? | **No.** Precisamente porque **no hay código**: si el nombre tampoco se pudiera corregir, una errata en el alta sería permanente y la única salida sería eliminar y volver a crear, perdiendo el historial de pertenencias |
| 4 | ¿`PATCH` o `PUT`? | **`PATCH`**, como toda edición parcial del sistema. Un `PUT` obligaría a enviar el estado y abriría la puerta a cambiarlo por esta vía |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 22-09-2026 | Redacción inicial. Hereda `RF-SP-004` —solo los campos descriptivos, cada otra operación con su requerimiento— y decide lo propio del equipo: **el nombre sí se corrige**, porque no hay código que lo respalde, y renombrar revalida `RN-SP-050`; `description: null` borra; el eliminado responde `404` y no se edita; el `INACTIVO` **sí** se edita. Siete criterios, `CA-SP-756` a `CA-SP-762`. | Responsable del proyecto |
