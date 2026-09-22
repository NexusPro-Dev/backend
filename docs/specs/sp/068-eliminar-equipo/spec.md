# SPEC — `RF-SP-068` Eliminar equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-068` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Objetivo

Retirar del sistema un equipo que fue un error o que ya no tiene sentido, **con motivo y solo si está vacío**, sin perder el rastro de quiénes estuvieron en él.

## 2. Contexto

Es la eliminación lógica con motivo que el sistema hace en todas partes (Art. V.13): `deleted_at`, el motivo y la instantánea a `audit_deletion_log`, y `404` frente a `409` para distinguir «no existe» de «ya estaba eliminado». Lo propio del equipo son dos cosas, y van en sentidos contrarios.

**La primera: se rechaza si tiene miembros vigentes** (`RN-SP-054`). Es lo contrario de lo que decidió `RF-AC-005` para una categoría, y la diferencia no es de gusto: una categoría es **un filtro**, y retirarla no rompe nada porque sus cursos se ofrecen igual sin cajón; un equipo es **la única forma de decir en qué parte de la red está un manager**, y eliminarlo con gente dentro dejaría a esas personas sin pertenencia sin que nadie lo hubiera decidido — moviendo, de paso, la atribución de todo lo que cuelga de ellas. Es la misma postura que `RN-SEG-008` toma con un rol que tiene usuarios y `RN-SP-022` con un superior que tiene equipo: **lo que otros sostienen no se retira solo**.

**La segunda: el historial sobrevive.** Las pertenencias cerradas **no se borran**, aunque el equipo se elimine. Son un hecho —esta persona estuvo aquí entre estas dos fechas— y son exactamente lo que las comisiones leerán para repartir lo que esa red produjo. Un equipo eliminado sigue pudiendo responder «quién pasó por aquí», aunque su detalle público ya no lo publique (`RF-SP-065` §14.2).

**Y el nombre queda libre** (`RN-SP-050`): la unicidad es parcial y un eliminado no compite. No hay código que conservar, de modo que volver a crear «Equipo Norte» al día siguiente es legítimo y **no revive nada**: es un equipo nuevo, con identificador nuevo y sin historial.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador con `teams:delete` | Elimina el equipo, declarando el motivo |

## 4. Alcance

### 4.1 Incluye

- Eliminar lógicamente un equipo **sin miembros vigentes**, con motivo obligatorio.
- Conservar sus pertenencias cerradas.
- Registrar la baja en `audit_deletion_log` con el motivo, el actor y la instantánea del equipo **con los identificadores de quienes estuvieron en él**.

### 4.2 No incluye

- **Vaciar el equipo.** Quien lo elimina lo vacía antes, con `RF-SP-070` y con motivo por cada retiro.
- **Borrar el historial.** §14.2.
- **Tocar a las personas.** Un manager cuyo equipo se elimina sigue siendo manager, sigue teniendo su red en `user_supervisors` y simplemente no pertenece a ningún equipo — que es un estado legítimo (`RN-SP-052`).
- **Revivir un equipo eliminado.** No hay operación de restauración; se crea otro y el nombre está libre.
- **Eliminación física.** Ninguna baja del sistema lo es.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-054` | No se elimina un equipo con miembros vigentes; la baja es lógica y exige motivo | `requirements/sp.md` §5.1 |
| `RN-SP-052` | El historial de pertenencias no se borra | `requirements/sp.md` §5.1 |
| `RN-SP-050` | El nombre queda libre al eliminar: la unicidad es parcial | `requirements/sp.md` §5.1 |
| Art. V.13 | El motivo es obligatorio y viaja con la instantánea | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| `id` | Sí | Qué equipo | `uuid` en la ruta; equipo existente y **no eliminado** |
| `reason` | Sí | Por qué se elimina | Con contenido tras recortar; hasta 500 caracteres |

### 6.2 Salida

`204`. Nada que devolver: el equipo eliminado se consulta por `RF-SP-065`, que lo devuelve con su `deletedAt` y este motivo.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `teams:delete`; el equipo existe, no está eliminado y **no tiene ninguna pertenencia vigente**; el motivo tiene contenido.

**Postcondiciones:** `deleted_at` puesto y **nada más de la fila cambia** —`status` incluido: un equipo eliminado conserva el estado que tenía—; las filas cerradas de `team_members` **intactas**; `audit_deletion_log` con una fila `LOGICAL`, el motivo, el actor y la instantánea, en la misma transacción. El nombre queda disponible para un alta nueva.

## 8. Flujo principal

1. Llega la petición con el motivo.
2. El sistema valida el motivo **antes de consultar nada** (`VAL-002`, `VAL-003`).
3. El sistema resuelve el equipo **en cualquier estado**, con bloqueo: si no existe, `EX-001`; si ya está eliminado, `EX-002`.
4. El sistema comprueba que no tenga pertenencias vigentes; si las tiene, `EX-003`.
5. El sistema toma la instantánea —el equipo y los identificadores de quienes pasaron por él—, marca `deleted_at` y registra la baja, en la misma transacción.
6. Devuelve `204`.

El paso 2 va **antes** de cualquier consulta a propósito: un motivo ausente no debe costar ni una sentencia, que es lo que `RF-AC-005` dejó escrito en `CA-AC-029`.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | El equipo está **vacío y activo** | Se elimina. No hace falta desactivarlo antes: son dos operaciones independientes |
| `FA-002` | El equipo está **`INACTIVO` y vacío** | Se elimina igual, y `status` se conserva en la fila y en la instantánea |
| `FA-003` | El equipo tiene **solo pertenencias cerradas** | Se elimina: lo que impide eliminar son las **vigentes** (`RN-SP-054`). Las cerradas se conservan |
| `FA-004` | El nombre se reutiliza después en un alta | Se admite (`RN-SP-050`), y el equipo nuevo **no hereda nada**: ni miembros, ni historial, ni identificador |

## 10. Excepciones

### EX-001 — El equipo no existe

**Respuesta del sistema:** `404` — *«No existe un equipo con ese identificador.»*

### EX-002 — El equipo ya está eliminado

**Respuesta del sistema:** `409` — *«El equipo ya está eliminado.»* **Se distingue** del inexistente, al contrario que en `RF-SP-066` y `RF-SP-067`: aquí la distinción sí decide algo —quien elimina dos veces merece saber que la primera funcionó— y no hay nada que ocultar, porque `RF-SP-065` devuelve los eliminados a quien tiene `teams:read`. Es el mismo criterio de `RF-AC-005` `EX-002`.

### EX-003 — El equipo tiene miembros vigentes

**Condición:** al menos una fila de `team_members` con este equipo y sin fecha de fin.
**Respuesta del sistema:** `409` — *«El equipo tiene miembros y no puede eliminarse. Retírelos o reasígnelos antes.»* El mensaje **dice qué hacer**, porque la salida no es obvia: hay dos, retirar (`RF-SP-070`) o reubicar en otro equipo (`RF-SP-069`).

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | `id` es un `uuid` bien formado | El identificador indicado no es válido. |
| `VAL-002` | Motivo presente y con contenido | El motivo de la eliminación es obligatorio. |
| `VAL-003` | Motivo de hasta 500 caracteres | El motivo no puede exceder 500 caracteres. |

Los dos códigos del motivo son los que `DeletionReason` ya publica en `shared/audit` desde `RF-PM-006`, y se usan tal cual: un motivo ausente y uno largo no son el mismo error.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-770` | El sistema elimina con `204` un equipo vacío, y la fila conserva `status` y todo lo demás salvo `deleted_at` |
| `CA-SP-771` | El sistema rechaza con `409` la eliminación de un equipo **con miembros vigentes**, con el mensaje que indica retirarlos o reasignarlos, y **no escribe nada** |
| `CA-SP-772` | Un equipo con **solo pertenencias cerradas** se elimina, y esas filas **siguen ahí** después |
| `CA-SP-773` | El sistema rechaza con `400` el motivo ausente, vacío, de solo espacios (`VAL-002`) o de más de 500 (`VAL-003`), **sin consultar nada** |
| `CA-SP-774` | El sistema responde `404` al equipo inexistente y `409` al **ya eliminado**, distinguiéndolos |
| `CA-SP-775` | `audit_deletion_log` tiene la fila `LOGICAL` con el motivo, el actor y la instantánea del equipo **con los identificadores de quienes pasaron por él**, y `deleted_at` nulo dentro de la instantánea |
| `CA-SP-776` | El equipo eliminado **desaparece** del listado salvo `includeDeleted=true`, su detalle lo devuelve con `deletedAt` y `deletionReason`, y **su nombre puede reutilizarse** en un alta nueva, que nace sin miembros ni historial |
| `CA-SP-777` | Dos eliminaciones simultáneas dejan un `204`, un `409` y **una** fila de auditoría; sin `teams:delete` la operación responde `403` aunque el actor porte `teams:change-status` y `teams:update` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Eliminar y, a la vez, asignar un miembro al mismo equipo | El bloqueo los ordena: si la asignación gana, la eliminación recibe `409` por `EX-003`; si gana la eliminación, la asignación recibe `404` — nunca queda un equipo eliminado con alguien dentro |
| Dos eliminaciones simultáneas | Una `204` y otra `409`, una sola fila de auditoría (`CA-SP-777`) |
| Eliminar el **único** equipo del sistema | Se admite: ningún manager deja de ser manager por no estar en un equipo (`RN-SP-052`) |
| Motivo de 500 caracteres exactos | Se admite: el tope es inclusivo |
| El equipo tiene un miembro cuya **persona** está eliminada | Esa pertenencia ya está cerrada (`RN-SP-055`), de modo que no cuenta como vigente y el equipo se puede eliminar |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se rechaza la eliminación por tener miembros, o se cierran solos? | **Se rechaza** (`RN-SP-054`). Cerrarlos movería la atribución de todas sus redes sin decisión explícita — la misma postura de `RN-SEG-008` y `RN-SP-022`. `RF-AC-005` decidió lo contrario porque una categoría es un filtro y no sostiene nada |
| 2 | ¿Se borra el historial de pertenencias al eliminar? | **No.** Es un hecho y lo leerán las comisiones. Borrarlo dejaría un equipo eliminado que dice «tuve gente» sin poder decir quién, que es lo que `RF-AC-005` §14.1 razonó para la clasificación |
| 3 | ¿Hace falta desactivar antes de eliminar? | **No.** Son independientes: se puede eliminar un equipo activo y vacío, y se puede tener uno inactivo indefinidamente |
| 4 | ¿El nombre queda libre? | **Sí** (`RN-SP-050`), y el equipo nuevo no hereda nada. Reservarlo para siempre exigiría un código que no existe, y condenaría a la empresa a inventar nombres por culpa de un error de hace un año |
| 5 | ¿`DELETE` o `POST …/deletion`? | **`POST /teams/{id}/deletion`**, como `RF-SP-029`, `RF-SP-050` y `RF-AC-005`: el motivo viaja en el cuerpo, y un `DELETE` con cuerpo no está garantizado por todos los intermediarios |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 22-09-2026 | Redacción inicial. Hereda la forma de `RF-AC-005` —lógica, con motivo validado antes de consultar, `404` frente a `409`, instantánea con lo que contenía— y **decide al revés en lo esencial**: aquí sí se rechaza por tener miembros (`RN-SP-054`), porque un equipo sostiene la pertenencia de una red y una categoría solo filtra. El historial cerrado sobrevive; el nombre queda libre y el equipo nuevo no hereda nada. Ocho criterios, `CA-SP-770` a `CA-SP-777`. | Responsable del proyecto |
