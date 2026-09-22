# SPEC — `RF-SP-067` Cambiar el estado de un equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-067` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Objetivo

**Dejar de organizar con un equipo sin deshacer cómo estaba organizada la cúspide hasta hoy**: suspenderlo para que no reciba a nadie más, conservando a quienes ya están dentro.

## 2. Contexto

Es `RF-SP-007` para equipos, y comparte con él la forma —idempotente, sin motivo, con auditoría de cambio— y **no** el fondo: desactivar un rol retira permisos a sus portadores de inmediato (`RN-SEG-002`) y por eso emite además un evento de seguridad; desactivar un equipo **no le quita nada a nadie**, porque un equipo no concede. Lo único que cambia es hacia adelante: deja de admitir miembros nuevos.

**Qué significa `INACTIVO`, y por qué no vacía.** `RN-SP-053` lo decide: el equipo suspendido conserva sus miembros. Cerrarlos en silencio movería la atribución de toda una red —cada manager arrastra a sus directores y a los agentes de estos— sin que nadie lo hubiera decidido, y las comisiones leerán ese historial para repartir. Es la misma postura que `RN-SP-022` toma con el superior de un vendedor: lo que cambia la estructura se decide explícitamente, operación a operación.

**Por eso el estado y la eliminación son dos cosas distintas.** «Ya no organizo con este equipo, pero quiero ver quién estaba» es `INACTIVO`; «esto fue un error y no debería existir» es `RF-SP-068`, que exige vaciarlo antes (`RN-SP-054`). Un equipo tiene las dos cosas —estado y baja lógica— por lo mismo que las tiene un rol.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador con `teams:change-status` | Suspende o reactiva un equipo |

## 4. Alcance

### 4.1 Incluye

- Cambiar el estado de un equipo entre `ACTIVO` e `INACTIVO`, en los dos sentidos.
- Conservar las pertenencias vigentes en ambos sentidos.
- Devolver el equipo actualizado en la forma del detalle (`RF-SP-065`).

### 4.2 No incluye

- **Vaciar el equipo.** Los miembros se retiran con `RF-SP-070`, con motivo y uno a uno.
- **Eliminar.** Es `RF-SP-068`.
- **Cambiar el estado de las personas.** Un manager de un equipo suspendido sigue activo, sigue siendo manager y sigue teniendo su red.
- **Un motivo.** §14.2.
- **Cambiar el estado de un equipo eliminado.** `404`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-053` | Un equipo `INACTIVO` no recibe miembros; los que tiene los conserva | `requirements/sp.md` §5.1 |
| `RN-SP-052` | Las pertenencias vigentes solo se cierran al reasignar o retirar | `requirements/sp.md` §5.1 |
| `RN-SEG-014` | Cambiar el estado es una operación propia, con su permiso | `security.md` §4.3 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| `id` | Sí | Qué equipo | `uuid` en la ruta; equipo **no eliminado** |
| `status` | Sí | El estado al que pasa | `ACTIVO` o `INACTIVO`, sin distinguir mayúsculas; otro valor es `400` (`VAL-001`) |

**Se declara el estado destino, no se alterna.** Un «cambiar» sin destino haría que dos peticiones idénticas dejaran resultados distintos según el orden en que llegaran, que es exactamente lo que la idempotencia evita.

### 6.2 Salida

`200` con el equipo en la **forma del detalle** (`RF-SP-065`), con el estado nuevo, sus miembros vigentes intactos y `updatedAt` avanzado si hubo cambio.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `teams:change-status`; el equipo existe y no está eliminado.

**Postcondiciones:** `status` es el declarado; **ninguna fila de `team_members` cambia**; si el estado cambió de verdad, `audit_change_log` tiene una fila `UPDATE` con el valor anterior y el nuevo, en la misma transacción. **Ningún evento de seguridad**: nadie gana ni pierde permisos.

## 8. Flujo principal

1. Llega la petición con el estado destino.
2. El sistema valida que el estado pertenece al dominio (§11).
3. El sistema resuelve el equipo no eliminado; si no lo encuentra, `EX-001`.
4. Si el estado ya es el declarado, no toca nada y devuelve el detalle (`FA-001`).
5. Si cambia, aplica el estado, avanza `updated_at` y registra el cambio en la auditoría, en la misma transacción.
6. Devuelve `200` con el detalle.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | El equipo **ya está en ese estado** | `200` con el detalle, **sin escribir nada y sin auditar**: la operación es idempotente, como en `RF-SP-007` (`CA-SP-052`). Repetir una petición que ya surtió efecto no es un error |
| `FA-002` | El equipo tiene **miembros vigentes** y se desactiva | Se desactiva y **los conserva** (`RN-SP-053`). El detalle los sigue devolviendo y `RF-SP-064` los sigue contando |
| `FA-003` | Se reactiva un equipo `INACTIVO` con miembros | Vuelve a `ACTIVO` con los mismos miembros: la suspensión no dejó nada pendiente de rehacer |
| `FA-004` | El equipo está **vacío** | Se suspende igual. Un equipo vacío e inactivo es el estado natural de uno que se dejó de usar y todavía no se elimina |

## 10. Excepciones

### EX-001 — El equipo no existe o está eliminado

**Respuesta del sistema:** `404` — *«No existe un equipo con ese identificador.»* Mismo criterio que `RF-SP-066`: sobre un eliminado no hay estado que cambiar, y distinguirlo no aportaría ninguna decisión.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | `status` presente y dentro del dominio (`ACTIVO`, `INACTIVO`) | El estado indicado no es válido. |
| `VAL-002` | `id` es un `uuid` bien formado | El identificador indicado no es válido. |
| `VAL-003` | Ningún campo desconocido — en particular, ni `name`, ni `members`, ni `reason` | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-763` | El sistema desactiva un equipo `ACTIVO` y lo reactiva después, devolviendo en ambos casos `200` con la forma del detalle y el estado nuevo |
| `CA-SP-764` | Desactivar **conserva** las pertenencias vigentes: el detalle sigue devolviendo a los mismos miembros y `RF-SP-064` sigue contándolos (`RN-SP-053`) |
| `CA-SP-765` | El sistema **no escribe ni audita** cuando el equipo ya estaba en el estado solicitado, y responde `200` igual |
| `CA-SP-766` | Un equipo `INACTIVO` **no admite miembros nuevos**: la asignación de `RF-SP-069` responde `409` mientras siga suspendido, y vuelve a admitirlos al reactivarlo |
| `CA-SP-767` | El sistema rechaza con `400` un estado ausente o desconocido, un identificador mal formado y un cuerpo con campos no admitidos; y con `404` el equipo inexistente o eliminado |
| `CA-SP-768` | `audit_change_log` tiene una fila `UPDATE` con el actor, el estado anterior y el nuevo, en la misma transacción; **`audit_security_log` no recibe nada** |
| `CA-SP-769` | Sin `teams:change-status` la operación responde `403` **aunque el actor porte `teams:update`**, y `EndpointPermissionsIT` recibe `PATCH /teams/{id}/status` con su código |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Desactivar un equipo con miembros y luego intentar eliminarlo | `RF-SP-068` responde `409`: desactivar no vacía, y `RN-SP-054` exige vacío. Las dos operaciones no se sustituyen |
| Retirar a un miembro de un equipo `INACTIVO` | **Se puede** (`RF-SP-070`): es la única forma de vaciarlo para poder eliminarlo |
| Reasignar a un manager **desde** un equipo inactivo **hacia** uno activo | Se puede: la restricción es del equipo que **recibe** (`RN-SP-053`), no del que suelta |
| Dos cambios de estado simultáneos al mismo valor | Uno escribe y audita, el otro es idempotente; ninguno falla |
| Un equipo inactivo en el listado | Sale, y se puede acotar con `status=INACTIVO` (`RF-SP-064`) |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Desactivar cierra las pertenencias? | **No** (`RN-SP-053`). Movería la atribución de toda una red sin decisión explícita, y las comisiones leen ese historial. Quien quiera vaciar lo hace miembro a miembro, con motivo (`RF-SP-070`) |
| 2 | ¿El cambio de estado exige motivo? | **No**, como en `RF-SP-007` (`CA-SP-159`). El motivo es la barrera de lo irreversible —la eliminación (Art. V.13)— y suspender se deshace con una petición. Exigirlo produce motivos escritos por obligación, que no se leen |
| 3 | ¿Hace falta el estado, teniendo eliminación lógica? | **Sí**, y son cosas distintas: `INACTIVO` es «ya no organizo con él, y quiero verlo»; eliminado es «no debería existir», exige vaciarlo antes y lo saca del listado. Un rol tiene los dos por la misma razón |
| 4 | ¿Un equipo `INACTIVO` se puede editar? | **Sí** (`RF-SP-066` `FA-006`). Corregir una errata antes de reactivarlo es lo normal |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 22-09-2026 | Redacción inicial. Hereda de `RF-SP-007` la forma —estado destino declarado, idempotente, sin motivo, con auditoría de cambio— y **no** su evento de seguridad: un equipo no concede permisos y desactivarlo no retira nada a nadie. Fija que desactivar **conserva** los miembros (`RN-SP-053`) y que la restricción de `INACTIVO` es sobre el equipo que **recibe**, no sobre el que suelta. Siete criterios, `CA-SP-763` a `CA-SP-769`. | Responsable del proyecto |
