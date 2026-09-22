# SPEC — `RF-SP-070` Retirar miembros de un equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-070` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Objetivo

Sacar a uno o varios managers de su equipo **sin ponerlos en otro**, con motivo, y dejar constancia de que estuvieron.

## 2. Contexto

Es la operación inversa de `RF-SP-069`, y existe porque **mover no es lo mismo que sacar**. Asignar a otro equipo cierra la pertenencia anterior por `RN-SP-052`, pero eso solo sirve cuando hay un destino; aquí no lo hay, y hacen falta dos cosas que la asignación no puede dar: **vaciar un equipo** —lo que `RN-SP-054` exige antes de eliminarlo— y dejar a alguien **sin equipo**, que es un estado legítimo (`RN-SP-052`: un manager sin equipo no rompe nada).

**Se puede retirar de un equipo `INACTIVO`, y es deliberado.** `RN-SP-053` prohíbe que un equipo suspendido **reciba**, no que suelte; si también prohibiera soltar, un equipo suspendido con gente dentro no podría vaciarse nunca y por tanto no podría eliminarse — la regla se habría cerrado sobre sí misma.

**Aquí «no pertenece» sí es un error, al contrario que en la asignación.** Asignar a quien ya está declara un estado que ya era cierto; retirar a quien no está es **creer que se está sacando a alguien de un sitio donde no está**, y devolver `200` dejaría al administrador con una idea falsa de cómo quedó la cúspide.

**Y este requerimiento trae consigo la enmienda de `RN-SP-055`** (Art. I.7): quien deja de ser manager —porque se le retira el rol (`RF-SP-031`) o porque se le elimina (`RF-SP-029`)— **sale de su equipo en la misma transacción**, sin pasar por esta operación. Sin eso, un equipo podría contener a alguien que ya no es manager y `RN-SP-051` se cumpliría al asignar y dejaría de cumplirse después, sin que nadie lo notara. Los dos requerimientos están construidos, de modo que sus tripletas se enmiendan **antes** de tocar su código.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador con `teams:remove-members` | Retira uno o varios miembros de un equipo |

## 4. Alcance

### 4.1 Incluye

- Cerrar la pertenencia vigente de **una o varias** personas **en este equipo**, con motivo y en una sola transacción.
- Permitirlo también sobre un equipo `INACTIVO`.
- Rechazar la operación **entera** si alguna de las personas no pertenece hoy a este equipo.
- Auditar cada cierre bajo un mismo identificador de correlación.
- **La enmienda de `RN-SP-055`** sobre `RF-SP-029` y `RF-SP-031`: el retiro automático cuando se deja de ser manager.

### 4.2 No incluye

- **Mover a otro equipo.** Es `RF-SP-069`, que además cierra sola la anterior.
- **Borrar el historial.** La fila se cierra con su fecha de fin y se conserva (`RN-SP-052`).
- **Tocar el rol ni la cadena de mando.** Quien sale de un equipo sigue siendo manager y conserva su red en `user_supervisors`.
- **Eliminar el equipo.** Es `RF-SP-068`, y esta operación es el paso previo cuando hay gente dentro.
- **Sacar a alguien por cambiarle el estado.** Un manager desactivado **sigue** en su equipo (`RN-SP-055`).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-052` | La pertenencia se cierra, no se borra; un manager sin equipo es un estado legítimo | `requirements/sp.md` §5.1 |
| `RN-SP-053` | El `INACTIVO` no recibe; **sí suelta** | `requirements/sp.md` §5.1 |
| `RN-SP-054` | No se elimina un equipo con miembros vigentes — esta es una de las dos salidas | `requirements/sp.md` §5.1 |
| `RN-SP-055` | La pertenencia sigue al rol: quien deja de ser manager o es eliminado sale de su equipo en la misma transacción | `requirements/sp.md` §5.1 |
| Art. V.13 | El cambio de estructura se audita con su motivo | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| `id` | Sí | El equipo del que se retira | `uuid` en la ruta; existente y **no eliminado**. Puede estar `INACTIVO` |
| `memberIds` | Sí | Quiénes salen | Lista de `uuid`, **al menos uno** y **como máximo 100**; repetidos se tratan una sola vez |
| `reason` | Sí | Por qué | Con contenido tras recortar; hasta 500 caracteres |

### 6.2 Salida

`200` con el equipo en la **forma del detalle** (`RF-SP-065`), ya sin los retirados y con `memberCount` actualizado — igual que la asignación, y por lo mismo: el administrador ve cómo quedó sin una segunda petición.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `teams:remove-members`; el equipo existe y no está eliminado; **cada persona indicada tiene hoy una pertenencia vigente a este equipo**; el motivo tiene contenido.

**Postcondiciones:** cada pertenencia indicada queda **cerrada** con su fecha de fin y **sigue existiendo**; ninguna persona queda con pertenencia vigente a este equipo; `audit_change_log` tiene una fila por cierre, con el motivo y un mismo identificador de correlación; **nada cambia** en `user_roles`, `user_supervisors` ni en el estado de las personas.

## 8. Flujo principal

1. Llega la petición con la lista y el motivo.
2. El sistema valida la forma de ambos **antes de consultar nada** (§11).
3. El sistema resuelve el equipo con bloqueo: si no existe o está eliminado, `EX-001`.
4. El sistema lee las pertenencias vigentes de las personas indicadas **en este equipo**: si alguna no la tiene, `EX-002`.
5. El sistema cierra todas y registra los cierres en la auditoría, bajo el mismo identificador de correlación y en la misma transacción.
6. Devuelve `200` con el detalle.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | El equipo está **`INACTIVO`** | Se retira igual: la restricción es sobre recibir (`RN-SP-053`). Es la vía para vaciarlo y poder eliminarlo |
| `FA-002` | Se retira al **último** miembro | El equipo queda vacío, con `memberCount` en cero, y ya puede eliminarse (`RN-SP-054`) |
| `FA-003` | La persona está **desactivada o bloqueada** | Se retira igual: su estado no tiene que ver con su pertenencia |
| `FA-004` | La misma persona **repetida** en la lista | Se trata una sola vez |
| `FA-005` | La persona **dejó de ser manager** antes de esta petición | Su pertenencia ya está cerrada por `RN-SP-055`, de modo que no pertenece y la petición cae en `EX-002`. Es coherente: el sistema ya la sacó |

## 10. Excepciones

### EX-001 — El equipo no existe o está eliminado

**Respuesta del sistema:** `404` — *«No existe un equipo con ese identificador.»*

### EX-002 — Alguna persona no pertenece hoy a este equipo

**Condición:** alguna de las indicadas no tiene pertenencia vigente **a este equipo** — no pertenece a ninguno, o pertenece a otro.
**Respuesta del sistema:** `422`, rechazando la operación **entera** e informando **cuáles**: *«Estas personas no pertenecen hoy a este equipo.»* **No se distingue** entre «no tiene equipo» y «está en otro», y es deliberado: las dos significan lo mismo para esta operación —aquí no está— y distinguirlas convertiría el mensaje de error en una consulta sobre dónde está cada cual, que es `RF-SP-064` y `RF-SP-065`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un identificador informado | Debe indicar al menos una persona. |
| `VAL-002` | Identificadores con formato válido | El identificador indicado no es válido. |
| `VAL-003` | Como máximo 100 por petición | No es posible retirar más de 100 personas en una sola solicitud. |
| `VAL-004` | Motivo presente y con contenido | El motivo del cambio es obligatorio. |
| `VAL-005` | Motivo de hasta 500 caracteres | El motivo no puede exceder 500 caracteres. |
| `VAL-006` | Ningún campo desconocido — en particular, ni `endedAt`, ni `teamId` | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-789` | El sistema retira **uno o varios** con `200`, devuelve el detalle sin ellos y con `memberCount` actualizado, y **la fila queda cerrada, no borrada**: sigue en el historial con su fecha de fin |
| `CA-SP-790` | El sistema rechaza con `422` la operación **entera** si alguna persona no pertenece hoy a este equipo —porque no tiene equipo o porque está en otro—, informa cuáles y **no retira a ninguna** |
| `CA-SP-791` | Se puede retirar de un equipo **`INACTIVO`**, y tras retirar al último el equipo **puede eliminarse** (`RF-SP-068` deja de responder `409`); un equipo eliminado responde `404` |
| `CA-SP-792` | El sistema rechaza con `400` la lista vacía, un identificador mal formado, más de 100, el motivo ausente o largo y un cuerpo con campos no admitidos, **sin escribir nada** |
| `CA-SP-793` | `audit_change_log` tiene una fila `UPDATE` por cada cierre, con el actor, el motivo y **un mismo identificador de correlación** para toda la petición |
| `CA-SP-794` | La operación **no toca** `user_roles`, `user_supervisors` ni el estado de las personas: quien sale sigue siendo manager y conserva su red |
| `CA-SP-795` | **`RN-SP-055`**: retirar a una persona su rol de manager (`RF-SP-031`) **cierra su pertenencia en la misma transacción**, con el mismo identificador de correlación que el retiro del rol; si el retiro del rol falla, la pertenencia **no** se cierra |
| `CA-SP-796` | **`RN-SP-055`**: eliminar a una persona (`RF-SP-029`) cierra su pertenencia en la misma transacción; **cambiar su estado (`RF-SP-028`) no la saca** del equipo |
| `CA-SP-797` | Sin `teams:remove-members` responde `403` **aunque el actor porte `teams:assign-members`**, y `EndpointPermissionsIT` recibe `POST /teams/{id}/members/removals` con su código |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Retirar a alguien que está en **otro** equipo | `422` (`EX-002`): esta operación es «sácalo de aquí», y ahí no está |
| Retirar y asignar a la misma persona a la vez, en equipos distintos | Se ordenan por `uq_team_members_vigente` y por el bloqueo de cada equipo; el resultado nunca deja dos pertenencias vigentes ni una cerrada dos veces |
| Retirar al último miembro y eliminar el equipo en la misma sesión | Es el camino previsto por `RN-SP-054`, y `CA-SP-791` lo recorre entero |
| Un **descenso**: un manager pasa a director | Pierde el rol de mayor rango y `RN-SP-055` cierra su pertenencia; su nuevo superior lo coloca, por `user_supervisors`, en el equipo de ese superior |
| Retirar a alguien cuya persona fue eliminada hace un minuto | Su pertenencia ya está cerrada (`RN-SP-055`) y la petición cae en `EX-002` |
| 100 identificadores exactos | Se admite: el tope es inclusivo |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Retirar a quien no pertenece es error o no-op? | **Error, `422`.** Al contrario que asignar a quien ya está: allí se declara un estado que ya era cierto; aquí el administrador cree estar sacando a alguien de donde no está |
| 2 | ¿Se puede retirar de un equipo `INACTIVO`? | **Sí.** `RN-SP-053` prohíbe recibir, no soltar; si prohibiera soltar, un equipo suspendido con gente no podría vaciarse ni eliminarse nunca |
| 3 | ¿Dónde vive el retiro automático de `RN-SP-055`? | **En `RF-SP-029` y `RF-SP-031`**, dentro de su transacción, no aquí. Esta operación es la manual; aquella es una consecuencia del cambio de rol, y ponerla aquí obligaría a que eliminar a una persona llamara a un endpoint |
| 4 | ¿Cambiar el estado de una persona la saca del equipo? | **No.** Un manager suspendido sigue siendo manager. Solo el rol y la eliminación disparan `RN-SP-055` |
| 5 | ¿Se borra la fila al retirar? | **No**, se cierra. Es el historial que las comisiones leerán, y el mismo criterio de `user_supervisors` (`RN-SP-021`) |
| 6 | ¿Hace falta motivo si el retiro es una corrección? | **Sí**, como en la asignación y en `RF-SP-041`: el historial sustenta el reparto y un tramo sin explicación es un agujero cuando alguien discuta una liquidación |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 22-09-2026 | Redacción inicial. Inversa de `RF-SP-069` y **distinta en un punto**: «no pertenece» sí es error (`422`), porque sacar a quien no está es una idea falsa del estado. Se admite sobre un equipo `INACTIVO` —`RN-SP-053` prohíbe recibir, no soltar—, lo que evita que la regla se cierre sobre sí misma e impida vaciar y eliminar. Trae la **enmienda de Art. I.7 de `RN-SP-055`** a `RF-SP-029` y `RF-SP-031`, construidos: quien deja de ser manager o se elimina sale de su equipo en la misma transacción, mientras que cambiar su estado no. Nueve criterios, `CA-SP-789` a `CA-SP-797`. | Responsable del proyecto |
