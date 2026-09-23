# PLAN — `RF-SP-068` Eliminar equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-068` |
| Especificación | [`spec.md`](spec.md), aprobada el 22-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Enfoque

**La baja lógica de siempre, con una comprobación que tiene que ser a prueba de carreras.** El orden es el de `RF-AC-005`: validar el motivo sin tocar la base, resolver el equipo **con bloqueo** en cualquier estado, distinguir inexistente de ya eliminado, y solo entonces mirar si tiene miembros vigentes. La comprobación de `RN-SP-054` va **dentro del bloqueo**, porque una asignación simultánea entre el `SELECT` y el `UPDATE` dejaría un equipo eliminado con alguien dentro — el estado que la regla existe para impedir.

**La instantánea lleva más que la fila.** Además del equipo, los identificadores de **todas** las personas que pasaron por él —las pertenencias cerradas—, con el mismo criterio que `RF-AC-005` aplicó a los cursos de una categoría: un registro de baja que dice «tuve gente» sin decir quién no sirve para reconstruir nada.

**Sin migración y sin nada nuevo en el esquema.** `teams.deleted_at` existe desde `V33`, `ix_team_members_team_vigente` es el índice por el que entra la comprobación, y `teams:delete` viene de `V34`.

## 2. Cambios de esquema

**Ninguno.**

Una nota que conviene dejar escrita: **`fk_team_members_team` no lleva `ON DELETE`** (`V33`), y es coherente con esta eliminación — no hay borrado físico del que defenderse, y si algún día lo hubiera, la clave foránea lo impediría en lugar de arrastrar el historial.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `Team` | Modificado | `delete(ahora)`: marca `deleted_at` sin tocar `status` ni nada más |
| `domain/repository` | `TeamRepository` | Modificado | `findForUpdate(id)` —en cualquier estado, con bloqueo— y `countActiveMembers(teamId)` |
| `domain/repository` | `TeamMemberRepository` | Modificado | `findAllMemberIdsEver(teamId)` para la instantánea |
| `domain/service` | `DeleteTeamService` | Nuevo | Motivo, `404`/`409`/`409`, instantánea, `deleted_at` y `AuditWriter`, en una transacción |
| `application` | `DeleteTeamRequest` | Nuevo | `reason` con `DeletionReason` de `shared/audit` — los mismos dos códigos que el resto del sistema |
| `interfaces` | `TeamController` | Modificado | `POST /api/v1/teams/{id}/deletion` con `teams:delete` |
| Pruebas | `TeamDeletionIT` | Nuevo | §11 |
| Pruebas | `TeamConcurrencyIT` | Modificado | Dos eliminaciones a la vez, y eliminación contra asignación |
| Pruebas | `EndpointPermissionsIT`, `OpenApiContractIT` | Modificado | La ruta y su extensión |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/teams/{id}/deletion` | Elimina lógicamente el equipo, con motivo |

**Petición**

```json
{ "reason": "Se fusionó con el Equipo Centro tras la reorganización de septiembre" }
```

**Respuesta `204`**, sin cuerpo.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Motivo ausente o vacío; motivo de más de 500; `id` mal formado | `VAL-001` a `VAL-003` |
| `403` | Sin `teams:delete` | — |
| `404` | El equipo no existe | `EX-001` |
| `409` | El equipo ya está eliminado | `EX-002` |
| `409` | El equipo tiene miembros vigentes | `EX-003` |

**Dos `409` con `error_code` distinto**, y es lo que permite al frontend decir una cosa u otra: «ya estaba eliminado» no lleva a ninguna acción, y «tiene miembros» lleva a `RF-SP-070` o a `RF-SP-069`.

## 5. Autorización

| Endpoint | Permiso |
|---|---|
| `POST /api/v1/teams/{id}/deletion` | `teams:delete` |

`CA-SP-777` prueba el `403` con `teams:change-status` y `teams:update` puestos: suspender no es eliminar, y corregir tampoco. Son tres decisiones distintas y tres permisos (`RN-SEG-014`).

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Eliminación | `audit_deletion_log` | Tipo `LOGICAL`, el motivo, el actor y la **instantánea**: el equipo con su estado y `deleted_at` nulo dentro, más los identificadores de todas las personas que pasaron por él |
| `409` por miembros o por ya eliminado | — | **Ninguno**: son `409` de negocio, como en `RF-AC-005` |

**`deleted_at` va nulo dentro de la instantánea** —es la foto de **antes**—, que es el detalle que `CA-AC-031` dejó fijado y que aquí se repite (`CA-SP-775`).

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Comprobación de miembros, `UPDATE` de `deleted_at` y fila de `audit_deletion_log` | **La misma**, con el equipo bloqueado desde el paso 3 (Art. V.14) |

**El bloqueo es la mitad del requerimiento.** Sin él, entre comprobar «no tiene miembros» y escribir `deleted_at` cabe una asignación de `RF-SP-069`, y el resultado sería justo lo que `RN-SP-054` prohíbe. Por el otro lado, `RF-SP-069` resuelve el equipo destino con el mismo bloqueo y comprueba que no esté eliminado, de modo que las dos operaciones se ordenan sea cual sea la que llegue primero (spec §13).

## 8. Impacto sobre otros módulos

Ninguno fuera de `SP`. Dentro: **`RF-SP-069` y `RF-SP-070` son las dos salidas** que el mensaje de `EX-003` nombra, y `RF-SP-065` es quien devuelve después el equipo eliminado con su motivo.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Cerrar las pertenencias vigentes al eliminar** | Movería la atribución de todas esas redes sin decisión explícita (`RN-SP-054`, spec §14.1). Es la postura de `RN-SEG-008` y `RN-SP-022` |
| **Permitir eliminar y dejar las pertenencias abiertas apuntando a un equipo eliminado** | Deja datos que se contradicen: `RF-SP-064` contaría miembros de algo que no existe, y `uq_team_members_vigente` seguiría ocupando el hueco de esas personas, impidiendo asignarlas a otro equipo sin entender por qué |
| **Borrar el historial cerrado** | Es un hecho y lo leerán las comisiones (spec §14.2) |
| **`DELETE /teams/{id}` con el motivo en el cuerpo** | Un `DELETE` con cuerpo no está garantizado por todos los intermediarios; el sistema usa `POST …/deletion` desde `RF-SP-029` |
| **Responder `404` al ya eliminado**, como hacen `RF-SP-066` y `RF-SP-067` | Aquí la distinción decide algo —saber que el primer retiro funcionó— y no hay nada que ocultar, porque el detalle devuelve los eliminados (spec `EX-002`) |
| **Comprobar los miembros antes de bloquear**, por ahorrar | Es la carrera que la regla existe para impedir (§7) |
| **Exigir que el equipo esté `INACTIVO` antes de eliminar** | Dos pasos para una decisión que ya lleva motivo; y un equipo vacío y activo creado por error se elimina igual de bien |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Que una asignación simultánea deje un equipo eliminado con miembros | Alto — rompe `RN-SP-054` en los datos | Bloqueo en la resolución y comprobación dentro de la transacción; `TeamConcurrencyIT` prueba las dos carreras |
| Que la instantánea no incluya a quienes pasaron por el equipo | Medio — baja irreconstruible | `CA-SP-775` la comprueba con dos pertenencias cerradas |
| Que el motivo inválido cueste consultas | Bajo | Se valida antes de tocar la base, y `CA-SP-773` lo afirma contando sentencias |
| Que alguien reutilice el nombre y crea haber restaurado el equipo | Bajo | `CA-SP-776` fija que el equipo nuevo nace sin miembros ni historial; la prosa del contrato lo dice |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-770`, `CA-SP-772` | Integración (`TeamDeletionIT`) | El `204` sobre un equipo vacío y sobre uno con solo pertenencias cerradas; que la fila conserva `status` y que las cerradas siguen ahí |
| `CA-SP-771` | API (`TeamDeletionIT`) | El `409` con miembros vigentes, su `error_code` propio y que no se escribe nada |
| `CA-SP-773` | API (`TeamDeletionIT`) | Los tres motivos inválidos, **sin sentencias** |
| `CA-SP-774` | API (`TeamDeletionIT`) | `404` del inexistente y `409` del ya eliminado, distinguidos por `error_code` |
| `CA-SP-775` | Integración (`TeamDeletionIT`) | La fila `LOGICAL` con motivo, actor, instantánea con `deleted_at` nulo y los identificadores de dos personas que pasaron |
| `CA-SP-776` | API (`TeamDeletionIT`) | El eliminado fuera del listado salvo `includeDeleted`; el detalle con su motivo; el alta con el mismo nombre, que nace vacía |
| `CA-SP-777` | Integración (`TeamConcurrencyIT`) + `EndpointPermissionsIT` | Dos eliminaciones simultáneas —un `204`, un `409`, **una** fila de auditoría—, la carrera contra una asignación, y el `403` con `teams:change-status` y `teams:update` |

**Lo que no se prueba aquí:** cómo se vacía un equipo, que es `RF-SP-070`. El fixture cierra las pertenencias directamente, y la carrera contra la asignación se escribe cuando `RF-SP-069` exista — queda declarada en `tasks.md` §4.
