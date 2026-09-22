# SPEC — `RF-SP-069` Asignar miembros a un equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-069` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Objetivo

Decir **a qué equipo pertenece cada manager desde hoy**, en una sola operación y con motivo, cerrando la pertenencia anterior de quien venía de otro equipo.

## 2. Contexto

Es la operación que da sentido a las cinco anteriores: hasta ahora los equipos existen, se listan, se abren, se corrigen y se eliminan, y **están todos vacíos**. Aquí es donde `memberCount` deja de decir cero.

**Se opera desde el equipo, no desde la persona.** La pregunta que se hace quien administra es «a quién pongo en este equipo», y ponerlo en la ruta del equipo hace que el permiso viva con el recurso que cambia. La consecuencia es que **una petición mueve a varias personas al mismo equipo**, y no a una persona entre equipos — lo segundo se consigue igual, asignándola al destino, porque `RN-SP-052` cierra sola la pertenencia anterior.

**Es la gemela de `RF-SP-041`**, la asignación de superior comercial, y hereda de ella tres cosas: el **motivo obligatorio** —el historial sustentará el reparto de comisiones y un tramo sin explicación es un agujero cuando alguien discuta una liquidación—, que la asignación **rige desde el momento de ejecutarse** y no admite fecha declarada, y que comprobar quién puede estar a cargo de quién se resuelve **sobre el rol de mayor rango** y no sobre «el primero» (`RN-SP-011`).

**Y se diferencia de ella en lo esencial: aquí no se manda.** `user_supervisors` dice quién está a cargo de quién; esta tabla dice en qué cajón de la cúspide está cada manager. Por eso no hay validación de parentesco entre el equipo y la persona —un equipo no tiene rol— y sí una condición sobre quién puede entrar: **solo la cúspide** (`RN-SP-051`).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador con `teams:assign-members` | Asigna uno o varios managers a un equipo |

## 4. Alcance

### 4.1 Incluye

- Asignar **uno o varios** managers a un equipo `ACTIVO`, en una sola transacción y con motivo.
- Cerrar la pertenencia vigente anterior de quien venía de otro equipo, abriendo la nueva (`RN-SP-052`).
- Rechazar la operación **entera** si alguna de las personas no puede entrar.
- Auditar cada pertenencia abierta y cada una cerrada **bajo el mismo identificador de correlación**.

### 4.2 No incluye

- **Asignar directores ni agentes.** Pertenecen al equipo de su manager por la cadena de `user_supervisors` (`RN-SP-051`), no por una fila propia.
- **Retirar.** Es `RF-SP-070`; asignar a otro equipo sí cierra la anterior, pero «sacarlo sin ponerlo en ningún sitio» es otra operación.
- **Cambiar quién manda sobre quién.** Eso es `RF-SP-041` y `user_supervisors`; un equipo no altera ninguna cadena de mando.
- **Conceder alcance de datos.** Entrar en un equipo no abre ningún dato a nadie (D-22).
- **Declarar desde cuándo.** La pertenencia empieza al ejecutarse.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-051` | A un equipo solo pertenecen managers: quien porta el rol vendedor **de mayor rango** | `requirements/sp.md` §5.1 |
| `RN-SP-052` | Un equipo vigente por manager; asignar a otro **cierra** el anterior y el historial se conserva | `requirements/sp.md` §5.1 |
| `RN-SP-053` | Un equipo `INACTIVO` **no recibe** miembros | `requirements/sp.md` §5.1 |
| `RN-SP-011` | El rango comercial lo expresa `parent_role_id`; la cúspide es el rol vendedor cuyo padre no es vendedor | `requirements/sp.md` §5.1 |
| `RN-SP-025` | Una persona porta un solo rol de tipo vendedor | `requirements/sp.md` §5.1 |
| Art. V.13 | El cambio de estructura se audita con su motivo | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| `id` | Sí | El equipo que recibe | `uuid` en la ruta; existente, **no eliminado** y **`ACTIVO`** |
| `memberIds` | Sí | Quiénes entran | Lista de `uuid`, **al menos uno** y **como máximo 100**; sin repetidos —un identificador dos veces se trata una sola vez, no es un error |
| `reason` | Sí | Por qué | Con contenido tras recortar; hasta 500 caracteres |

### 6.2 Salida

`200` con el equipo en la **forma del detalle** (`RF-SP-065`), ya con sus miembros: los que había y los que entran, por antigüedad. Devolver el detalle y no `204` es lo que permite al frontend pintar el resultado sin una segunda petición, y hace observable de inmediato que quien venía de otro equipo ya no está allí.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `teams:assign-members`; el equipo existe, no está eliminado y está `ACTIVO`; cada persona existe, no está eliminada y porta el rol vendedor de mayor rango; el motivo tiene contenido.

**Postcondiciones:** cada persona indicada tiene una pertenencia **vigente** a este equipo; las que venían de otro tienen la anterior **cerrada** con su fecha de fin; `audit_change_log` tiene una fila por cada pertenencia abierta y cada una cerrada, **todas con el mismo identificador de correlación** y con el motivo; nada cambia en `user_supervisors` ni en `user_roles`.

## 8. Flujo principal

1. Llega la petición con la lista y el motivo.
2. El sistema valida la forma de ambos **antes de consultar nada** (§11).
3. El sistema resuelve el equipo con bloqueo: si no existe o está eliminado, `EX-001`; si está `INACTIVO`, `EX-002`.
4. El sistema resuelve a las personas: si alguna no existe o está eliminada, `EX-003`.
5. El sistema comprueba que **todas** portan el rol vendedor de mayor rango: si alguna no, `EX-004`.
6. Para cada persona que ya pertenece a **este** equipo, no hace nada (`FA-001`).
7. Para cada una que pertenece a **otro**, cierra esa pertenencia y abre la nueva.
8. El sistema registra los cambios en la auditoría, todos bajo el mismo identificador de correlación, en la misma transacción.
9. Devuelve `200` con el detalle del equipo.

Los pasos 4 y 5 se resuelven **para toda la lista antes de escribir nada**: es lo que hace posible informar de **todas** las personas que no pueden entrar en una sola respuesta, en lugar de rechazar por la primera y obligar a descubrirlas de una en una.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | Una persona **ya pertenece a este equipo** | No se hace nada con ella y **no es un error**: la operación declara un estado, y decir lo que ya es cierto no falla. No se cierra ni se reabre su pertenencia —perdería su antigüedad— ni se audita |
| `FA-002` | Una persona **pertenece a otro equipo** | Se cierra allí y se abre aquí, en la misma transacción (`RN-SP-052`). El otro equipo lo refleja de inmediato en su `memberCount` |
| `FA-003` | La misma persona **repetida** en la lista | Se trata una sola vez. Repetir un identificador no cambia la intención |
| `FA-004` | La persona está **desactivada o bloqueada** | **Entra igual.** Un manager suspendido sigue siendo manager (`RN-SP-055`), y no poder organizarlo dejaría al administrador sin forma de ordenar la cúspide antes de reactivarlo |
| `FA-005` | El equipo ya tenía miembros | Los conserva; los nuevos se suman |

## 10. Excepciones

### EX-001 — El equipo no existe o está eliminado

**Respuesta del sistema:** `404` — *«No existe un equipo con ese identificador.»*

### EX-002 — El equipo está `INACTIVO`

**Condición:** el equipo existe y su estado es `INACTIVO` (`RN-SP-053`).
**Respuesta del sistema:** `409` — *«El equipo está inactivo y no admite miembros nuevos. Actívelo antes de asignar.»* Es un conflicto **con el estado del recurso de la ruta**, y por eso `409` y no `422`, que aquí se reserva para lo que viene en el cuerpo.

### EX-003 — Alguna persona no existe o está eliminada

**Respuesta del sistema:** `422`, rechazando la operación **entera** e informando **cuáles**, sin distinguir entre no haber existido nunca y estar eliminada — el mismo criterio de `RF-SP-030` `EX-002`.

### EX-004 — Alguna persona no es de la cúspide

**Condición:** alguna no porta ningún rol de tipo `VENDEDOR`, o porta uno que **no es el de mayor rango** —un director, un agente— (`RN-SP-051`).
**Respuesta del sistema:** `422`, rechazando la operación entera, citando `RN-SP-051` e informando **cuáles** y por qué: *«Solo pueden pertenecer a un equipo quienes portan el rol comercial de mayor rango.»* Un director pertenece al equipo de su manager por la cadena de mando, y ponerlo aparte permitiría que figurara en un equipo distinto del de quien lo manda.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un identificador informado | Debe indicar al menos una persona. |
| `VAL-002` | Identificadores con formato válido | El identificador indicado no es válido. |
| `VAL-003` | Como máximo 100 por petición | No es posible asignar más de 100 personas en una sola solicitud. |
| `VAL-004` | Motivo presente y con contenido | El motivo del cambio es obligatorio. |
| `VAL-005` | Motivo de hasta 500 caracteres | El motivo no puede exceder 500 caracteres. |
| `VAL-006` | Ningún campo desconocido — en particular, ni `startedAt`, ni `status` | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-778` | El sistema asigna **varios** managers a la vez con `200` y devuelve el detalle del equipo con todos sus miembros, por antigüedad, y `memberCount` acorde |
| `CA-SP-779` | Quien venía de **otro equipo** queda con la pertenencia anterior **cerrada** y una nueva abierta: el equipo de origen deja de contarlo y su historial conserva la fila cerrada (`RN-SP-052`) |
| `CA-SP-780` | Quien **ya pertenece a este equipo** no cambia: conserva su `joinedAt` original, no se audita y la respuesta es `200` (`FA-001`) |
| `CA-SP-781` | El sistema rechaza con `422` la operación **entera** si alguna persona no existe o está eliminada, e informa **cuáles**; ninguna de las demás queda asignada |
| `CA-SP-782` | El sistema rechaza con `422` la operación entera si alguna persona **no es de la cúspide** —un director, un agente, alguien sin rol comercial—, e informa cuáles y por qué (`RN-SP-051`) |
| `CA-SP-783` | El sistema rechaza con `409` la asignación a un equipo **`INACTIVO`** y con `404` la de un equipo inexistente o eliminado |
| `CA-SP-784` | Una persona **desactivada o bloqueada** que es manager **entra igual** |
| `CA-SP-785` | El sistema rechaza con `400` la lista vacía, un identificador mal formado, más de 100, el motivo ausente o largo y un cuerpo con campos no admitidos, **sin escribir nada** |
| `CA-SP-786` | `audit_change_log` tiene una fila por cada pertenencia **abierta** y cada una **cerrada**, con el actor, el motivo y **el mismo identificador de correlación** para toda la petición |
| `CA-SP-787` | La operación **no toca** `user_supervisors` ni `user_roles`: la cadena de mando y los roles de cada persona quedan exactamente igual |
| `CA-SP-788` | Sin `teams:assign-members` responde `403` **aunque el actor porte `teams:update` y `teams:remove-members`**, y `EndpointPermissionsIT` recibe `POST /teams/{id}/members` con su código |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Asignar a alguien al equipo en el que ya está, junto a otras personas | Su fila no se toca y las demás entran (`FA-001`, `FA-003`) |
| Mover a un manager **desde** un equipo `INACTIVO` **hacia** uno `ACTIVO` | Se puede: la restricción es del que recibe (`RN-SP-053`) |
| Dos asignaciones simultáneas de la misma persona a dos equipos | `uq_team_members_vigente` deja una sola pertenencia vigente: una gana y la otra recibe el mismo `409` que la comprobación previa, no un `500` |
| Asignar mientras alguien elimina el equipo | El bloqueo los ordena: si la eliminación gana, la asignación recibe `404`; si gana la asignación, la eliminación recibe su `409` por tener miembros (`RF-SP-068` §13) |
| Un manager que **nunca** ha estado en un equipo | Entra con una fila nueva; no hay nada que cerrar |
| Una persona que porta **dos** roles vendedores | `RN-SP-025` lo prohíbe y el sistema falla de forma visible al resolver su rango, en lugar de elegir uno: es la postura que `SellerRoleCatalog` ya toma |
| 100 identificadores exactos | Se admite: el tope es inclusivo, como en `RF-SP-030` |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se asigna desde el equipo o desde la persona? | **Desde el equipo.** Es la pregunta que se hace quien administra, y así el permiso vive con el recurso que cambia. Mover a una persona entre equipos se consigue asignándola al destino, porque `RN-SP-052` cierra la anterior |
| 2 | ¿Puede entrar un director o un agente? | **No** (`RN-SP-051`). Pertenecen al equipo de su manager por la cadena de mando; con fila propia podrían figurar en un equipo distinto del de quien los manda, y no habría regla que lo impidiera sin volver a recorrer `user_supervisors` |
| 3 | ¿Asignar a alguien que ya está es un error? | **No**, y tampoco reinicia su antigüedad: la operación declara un estado. Cerrar y reabrir le quitaría el `joinedAt` que el detalle ordena |
| 4 | ¿Toda la lista o nada? | **Toda o nada.** A medias, el administrador no sabría quién entró sin volver a consultar, y el motivo declarado valdría para un conjunto distinto del que pidió |
| 5 | ¿Exige motivo, como `RF-SP-041`? | **Sí**, y por la misma razón: el historial de a qué equipo pertenecía cada manager decide a quién se atribuye lo que su red produjo, y las comisiones lo leerán |
| 6 | ¿Puede entrar alguien desactivado? | **Sí** (`FA-004`). Un manager suspendido sigue siendo manager; prohibirlo impediría ordenar la cúspide antes de reactivarlo, y el estado de la persona ya se publica en el detalle |
| 7 | ¿Se admite declarar `startedAt`? | **No**, como en `RF-SP-041`: la asignación rige al ejecutarse. Una fecha declarada permitiría reescribir a quién se atribuía una venta de hace tres meses |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 22-09-2026 | Redacción inicial. Gemela de `RF-SP-041`: motivo obligatorio, vigencia al ejecutarse, rango resuelto sobre el rol de mayor rango. Decide lo propio: se opera **desde el equipo**, con varias personas por petición y **toda la lista o nada**; solo entra la cúspide (`RN-SP-051`, `422` con la lista de quienes no pueden); asignar a otro equipo **cierra** el anterior (`RN-SP-052`); el equipo `INACTIVO` responde `409` (`RN-SP-053`) y el eliminado `404`; quien ya está no se toca ni pierde antigüedad; un manager desactivado entra igual. Once criterios, `CA-SP-778` a `CA-SP-788`. | Responsable del proyecto |
