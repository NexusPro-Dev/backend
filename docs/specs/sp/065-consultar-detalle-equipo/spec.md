# SPEC — `RF-SP-065` Consultar el detalle de un equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-065` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Objetivo

Abrir un equipo y ver **quiénes lo forman hoy**, además de su ficha, para decidir sobre él: a quién reubicar, si se puede eliminar y qué parte de la red comercial queda dentro.

## 2. Contexto

Es `RF-SP-003` para equipos: la vista que responde de un vistazo qué es este equipo y quién está dentro, que es la ventaja de tener una lista explícita en lugar de deducirla. Hereda de `RF-AC-003` y `RF-PM-003` la decisión que más se consulta y menos se recuerda: **un equipo eliminado también se devuelve**, con su `deletedAt` y el motivo leído de la auditoría, porque quien tiene `teams:read` ve el catálogo entero y porque el listado ya lo enseña con `includeDeleted=true` — abrir desde ahí y recibir `404` sería una incoherencia entre dos lecturas del mismo permiso.

**Los miembros son la razón de ser de esta lectura.** `RF-SP-064` dice cuántos hay; aquí están los nombres, que es lo que hace falta para reubicar a alguien (`RF-SP-069`), para vaciar un equipo antes de eliminarlo (`RN-SP-054`) y para entender qué red cuelga de él: cada manager de la lista arrastra, por `user_supervisors`, a sus directores y a los agentes de estos.

**Solo los vigentes.** Quién perteneció y cuándo salió es historial, se conserva en `team_members` y lo necesitará quien liquide comisiones, no quien administra hoy. Publicarlo aquí mezclaría dos preguntas —«¿quiénes están?» y «¿quiénes estuvieron?»— en una respuesta donde la segunda crece sin cota.

**No se pagina, y es una decisión con fecha.** Un equipo reúne a la cúspide: unas pocas personas. Paginar una lista que cabe en una pantalla añade contrato —`page`, `size`, totales— que nadie va a usar; si algún día un equipo reuniera cientos de managers, el problema sería de organización antes que de API, y la paginación entraría entonces como enmienda. Es el mismo razonamiento que `RF-SP-059` aplicó a los vendedores de un cliente, y el contrario del que `RF-SP-061` aplicó a la cartera, donde la cardinalidad sí crece.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador con `teams:read` | Consulta el detalle de un equipo |

## 4. Alcance

### 4.1 Incluye

- Devolver el equipo por identificador, **vivo o eliminado**, con nombre, descripción, estado y fechas.
- La lista de sus **miembros vigentes**, con quién es cada uno y desde cuándo pertenece, ordenada por antigüedad en el equipo.
- `memberCount`, el mismo número que publica `RF-SP-064`.
- Si está eliminado: `deletedAt` y el **motivo** de la eliminación, leído de la auditoría.

### 4.2 No incluye

- **El historial de pertenencias.** Quién estuvo y cuándo salió se conserva y no se publica aquí (§14.2).
- **La red que cuelga de cada miembro.** Los directores y agentes de un manager se consultan con `RF-SP-042` sobre esa persona; publicarlos aquí sería devolver media empresa por una lectura de equipo.
- **Los roles, el correo ni la membresía de cada miembro.** La ficha de la persona es `RF-SP-026`.
- **Paginar.** §14.3.
- **Conceder alcance.** Ver el detalle de un equipo no abre ningún dato de sus miembros (D-22).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-051` | A un equipo solo pertenecen managers — por eso la lista no necesita decir el rol de cada uno | `requirements/sp.md` §5.1 |
| `RN-SP-052` | Un equipo vigente por manager, con historial; aquí se publican **solo los vigentes** | `requirements/sp.md` §5.1 |
| `RN-SP-053` | Un equipo `INACTIVO` conserva sus miembros | `requirements/sp.md` §5.1 |
| `RN-SP-054` | No se elimina un equipo con miembros vigentes — por eso un eliminado sale con la lista vacía | `requirements/sp.md` §5.1 |
| `RN-SEG-014` | Listar y ver el detalle son dos operaciones y dos permisos | `security.md` §4.3 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `id` | Sí | El equipo que se consulta | `uuid` bien formado en la ruta; si no, `400` (`VAL-001`) |

### 6.2 Salida

`200` con:

| Dato | Descripción |
|---|---|
| `id`, `name` | Cuál es |
| `description` | **Presente y nula** si no tiene |
| `status` | `ACTIVO` o `INACTIVO` |
| `memberCount` | Cuántos miembros vigentes, el mismo número de `RF-SP-064` |
| `members[]` | Cada miembro vigente: `id`, `username`, `firstName`, `lastName`, `status` —el de la **persona**— y `joinedAt`, desde cuándo pertenece a **este** equipo |
| `createdAt`, `updatedAt` | Las dos fechas de auditoría |
| `deletedAt` | Presente **solo si está eliminado** (`NON_NULL`) |
| `deletionReason` | El motivo, leído de `audit_deletion_log`; **presente solo si está eliminado**, y nulo si el registro no se encuentra |

**`members` va ordenado por antigüedad en el equipo** —quien lleva más tiempo primero—, con el nombre de usuario como desempate para que el orden sea determinista. Es el orden con el que se lee una lista de personas que se han ido incorporando, y el mismo criterio con el que `RF-SP-059` pone primero al vendedor más antiguo.

**El `status` de cada miembro es el de la persona**, no el de su pertenencia: una pertenencia vigente no tiene estados. Se publica porque un manager desactivado sigue en su equipo —cambiar el estado de una persona **no** la saca (`RN-SP-055`)— y quien administra necesita verlo sin abrir cada ficha.

**Un miembro eliminado no aparece**, y no hace falta una regla nueva: eliminar a una persona cierra su pertenencia en la misma transacción (`RN-SP-055`), de modo que deja de ser vigente.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `teams:read`.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. Llega la petición con el identificador.
2. El sistema valida que sea un `uuid` bien formado.
3. El sistema lee el equipo, vivo o eliminado (`EX-001` si no existe).
4. El sistema lee sus miembros vigentes con los datos de cada persona.
5. Si está eliminado, lee el motivo de la auditoría de eliminación.
6. Devuelve `200` con la ficha y la lista.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | El equipo **no tiene miembros** | `200` con `members` vacío y `memberCount` en cero. Es el estado normal de un equipo recién creado |
| `FA-002` | El equipo está **`INACTIVO`** | Se devuelve igual, **con sus miembros**: desactivar no vacía (`RN-SP-053`) |
| `FA-003` | El equipo está **eliminado** | Se devuelve con `deletedAt` y `deletionReason`, y con `members` vacío — no por ocultarlos, sino porque `RN-SP-054` no deja eliminar un equipo que tenga vigentes |
| `FA-004` | El equipo está eliminado y **no hay registro de eliminación** | `deletionReason` presente y nulo, sin `500`: el detalle no depende de la auditoría para responder, como en `RF-AC-003` |
| `FA-005` | Un miembro está **desactivado o bloqueado** | Sale igual, con su estado. Cambiar el estado de una persona no la saca del equipo (`RN-SP-055`) |

## 10. Excepciones

### EX-001 — El equipo no existe

**Condición:** ningún equipo tiene ese identificador.
**Respuesta del sistema:** `404` — *«No existe un equipo con ese identificador.»* **Eliminado no es inexistente**: aquel se devuelve.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | `id` es un `uuid` bien formado | El identificador indicado no es válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-748` | El sistema devuelve el equipo con `id`, `name`, `description` **presente y nula** si no tiene, `status`, `memberCount`, `members[]`, `createdAt` y `updatedAt`, y **sin** `deletedAt` ni `deletionReason` si está vivo |
| `CA-SP-749` | Cada miembro trae `id`, `username`, `firstName`, `lastName`, `status` de la persona y `joinedAt` de **este** equipo, y la lista va **por antigüedad**, con el nombre de usuario de desempate |
| `CA-SP-750` | `members` contiene **solo los vigentes**: quien tuvo una pertenencia cerrada en este equipo no aparece, y quien está hoy en otro equipo tampoco |
| `CA-SP-751` | `memberCount` coincide con el tamaño de `members` y con el número que devuelve `RF-SP-064` para el mismo equipo |
| `CA-SP-752` | Un equipo `INACTIVO` se devuelve **con sus miembros**; un equipo **eliminado** se devuelve con `deletedAt`, `deletionReason` y `members` vacío; uno inexistente responde `404` y un identificador mal formado, `400` (`VAL-001`) |
| `CA-SP-753` | Un equipo eliminado **sin registro de eliminación** devuelve `deletionReason` presente y nulo, sin `500` |
| `CA-SP-754` | El número de sentencias es fijo: **dos** para un equipo vivo —ficha y miembros— y **tres** para uno eliminado, con miembros o sin ellos; no crece con el número de miembros |
| `CA-SP-755` | Sin `teams:read` el detalle responde `403` **aunque el actor porte `teams:list`**, y `EndpointPermissionsIT` recibe `GET /teams/{id}` con su código |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un miembro **desactivado o bloqueado** | Sale, con su estado: sigue siendo manager y sigue en el equipo |
| Un miembro **eliminado** | No sale: su pertenencia se cerró al eliminarlo (`RN-SP-055`) |
| Un miembro que **dejó de ser manager** | No sale: retirar el rol cierra la pertenencia (`RN-SP-055`). Si apareciera, sería un defecto de `RF-SP-031`, no de esta lectura |
| Dos miembros con el **mismo `joinedAt`** —asignados en la misma petición, que es el caso normal de `RF-SP-069`— | Se desempatan por nombre de usuario, y por eso el orden no depende del azar |
| Equipo eliminado con pertenencias **cerradas** | Sale con `members` vacío y `memberCount` en cero; el historial sigue en la tabla para quien liquide |
| Un `uuid` con la forma correcta que no es de un equipo sino de un usuario | `404`: no hay equipo con ese identificador, y la respuesta no distingue de qué era |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se devuelve un equipo eliminado, o `404`? | **Se devuelve**, con `deletedAt` y el motivo. Lo heredamos de `RF-PM-003` y `RF-AC-003`, y aquí hay una razón más: `RF-SP-064` con `includeDeleted=true` ya lo enseña, y abrirlo desde ahí para recibir `404` sería incoherente dentro del mismo permiso |
| 2 | ¿Se publica el historial de pertenencias? | **No.** «Quiénes están» y «quiénes estuvieron» son dos preguntas, y la segunda crece sin cota y la hará quien liquide comisiones, sobre el historial completo. El dato se conserva en `team_members`; publicarlo es otro requerimiento el día que alguien lo pida |
| 3 | ¿Se pagina la lista de miembros? | **No.** Un equipo reúne a la cúspide, que son unas pocas personas; paginar añade contrato que nadie usará. `RF-SP-061` sí pagina porque una cartera puede tener cientos de clientes — la diferencia está en la cardinalidad, no en el gusto |
| 4 | ¿Cada miembro trae sus roles? | **No.** A un equipo solo pertenecen managers (`RN-SP-051`): el rol es el mismo para todos y publicarlo sería repetir la regla en cada fila. `RF-SP-042` sí los trae porque allí conviven directores, agentes y managers |
| 5 | ¿Se devuelve la red que cuelga de cada miembro? | **No.** Es `RF-SP-042` sobre cada persona. Un equipo de cuatro managers con sus ramas completas es media empresa en una respuesta |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 22-09-2026 | Redacción inicial. Hereda de `RF-AC-003` y `RF-PM-003` que el eliminado **se devuelve con su motivo** —y aquí con un argumento propio: el listado ya lo enseña—, y de `RF-SP-059` el no paginar una lista corta, frente a `RF-SP-061` que sí pagina porque la cardinalidad crece. Decide: solo vigentes, orden por antigüedad con desempate por nombre de usuario, el estado de la persona en cada fila, sin roles y sin la red de cada manager. Ocho criterios, `CA-SP-748` a `CA-SP-755`. | Responsable del proyecto |
