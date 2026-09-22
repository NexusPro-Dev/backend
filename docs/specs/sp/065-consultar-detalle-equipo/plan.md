# PLAN — `RF-SP-065` Consultar el detalle de un equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-065` |
| Especificación | [`spec.md`](spec.md), aprobada el 22-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Enfoque

**El lector del detalle ya existe: lo escribió `RF-SP-063` para poder devolver el alta.** `TeamDetailReader` resuelve la ficha y sus miembros vigentes, y hasta hoy solo se ha ejercitado sobre un equipo recién creado, es decir, vacío. Este requerimiento lo completa con lo que el alta no necesitaba —los miembros de verdad, el equipo eliminado y su motivo— y le pone una ruta delante.

**Dos sentencias fijas, tres si está eliminado.** La ficha con su `memberCount` en una —la misma subconsulta correlacionada de `RF-SP-064`, por `ix_team_members_team_vigente`—, los miembros con sus personas en otra, y el motivo de la auditoría solo cuando hay `deletedAt`. **Ninguna crece con el número de miembros**, que es lo que `CA-SP-754` afirma y lo que impide que alguien resuelva la lista con una consulta por persona.

**El motivo se lee de `audit_deletion_log` y la respuesta no depende de él.** Si el registro no está, `deletionReason` sale nulo y presente, y el `200` se devuelve igual: es la decisión de `RF-PM-003`, repetida en `RF-AC-003`, y existe porque una lectura no debe caerse por un hueco en la auditoría.

## 2. Cambios de esquema

**Ninguno.** `teams` y `team_members` existen desde `V33`, con `ix_team_members_team_vigente`; `teams:read` desde `V34`.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/service` | `TeamDetailReader` | **Modificado** | Gana la lectura de los miembros con los datos de la persona y la del motivo de eliminación. Lo estrenó `RF-SP-063` devolviendo la forma vacía |
| `domain/repository` | `TeamQueryRepository`, `JpaTeamQueryRepository` | Modificado | `findDetail(id)` —la ficha con `memberCount`, viva o eliminada— y `findMembers(teamId)` —`JOIN users u ON u.id = tm.user_id`, `WHERE tm.team_id = :equipo AND tm.ended_at IS NULL`, `ORDER BY tm.started_at, u.username` |
| `application` | `TeamDetailResponse`, `TeamMemberItem` | Modificado | `deletedAt` y `deletionReason` en `NON_NULL`; el miembro con el `status` de la persona |
| `domain/service` | `GetTeamService` | Nuevo | El `404` del inexistente y la orquestación de las dos o tres lecturas, `@Transactional(readOnly = true)` |
| `interfaces` | `TeamController` | Modificado | `GET /api/v1/teams/{id}` con `teams:read` |
| Pruebas | `TeamDetailIT` | Nuevo | §11 |
| Pruebas | `EndpointPermissionsIT`, `OpenApiContractIT` | Modificado | La ruta y su extensión |

**El lector de la auditoría es el que ya usan `RF-PM-003` y `RF-AC-003`**: se reutiliza el puerto existente sobre `audit_deletion_log` y no se escribe uno propio, porque la tercera consulta es la misma pregunta —«¿por qué se eliminó esta fila?»— con otra entidad.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/teams/{id}` | Ficha del equipo y sus miembros vigentes |

**Respuesta `200`**

```json
{
  "id": "01a0c1f0-7a00-7001-9c4f-5e7ad6000001",
  "name": "Equipo Norte",
  "description": "Managers de la región norte",
  "status": "ACTIVO",
  "memberCount": 2,
  "members": [
    {
      "id": "01a0b6f6-7400-7001-9c4f-5e7ad4000011",
      "username": "manager1",
      "firstName": "Ana",
      "lastName": "Ruiz",
      "status": "ACTIVO",
      "joinedAt": "2026-09-22T15:10:00Z"
    },
    {
      "id": "01a0b6f6-7400-7002-9c4f-5e7ad4000012",
      "username": "manager2",
      "firstName": "Luis",
      "lastName": "Peña",
      "status": "INACTIVO",
      "joinedAt": "2026-09-23T09:41:22Z"
    }
  ],
  "createdAt": "2026-09-22T14:05:11Z",
  "updatedAt": "2026-09-22T14:05:11Z"
}
```

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | `id` mal formado | `VAL-001` |
| `403` | Sin `teams:read` | — |
| `404` | No existe un equipo con ese identificador | `EX-001` |

**Es la misma forma que devuelve el alta** (`RF-SP-063` §6.2), y a propósito: el frontend trata «acabo de crearlo» y «lo abrí» con el mismo modelo.

## 5. Autorización

| Endpoint | Permiso |
|---|---|
| `GET /api/v1/teams/{id}` | `teams:read` |

`CA-SP-755` lo prueba en negativo con `teams:list` puesto: listar y abrir son dos operaciones (`RN-SEG-014`). **El `403` sale antes de tocar la base**, de modo que no distingue si el identificador existe; el `404` solo llega con el permiso puesto, y no es una fuga porque quien porta `teams:read` normalmente porta `teams:list` y ve la lista entera.

**Nada por pertenencia.** El servicio no pregunta si el actor está en el equipo: un administrador no pertenece a ninguno, y un manager no abre el suyo por esta vía (D-22).

## 6. Auditoría

Ninguna emitida. **Una leída**: el motivo de la eliminación, de `audit_deletion_log`, y solo cuando el equipo está eliminado.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`, con las dos o tres sentencias sobre la misma instantánea — importa aquí más que en un listado: `memberCount` y `members` se leen por separado y tienen que cuadrar (`CA-SP-751`).

## 8. Impacto sobre otros módulos

Ninguno. La única dependencia es interna a `SP`: el detalle lee `users` para nombrar a cada miembro, dentro del mismo módulo.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Una sola consulta con `JOIN`** para la ficha y los miembros | Repite las columnas del equipo en cada fila y obliga a rearmar el agregado en memoria; con cero miembros hay que distinguir «no existe» de «existe y está vacío» por un `LEFT JOIN` con nulos. Dos consultas dicen lo mismo sin ese trabajo |
| **Devolver `404` al eliminado** | `RF-SP-064` lo enseña con `includeDeleted=true`: abrirlo desde la lista y recibir `404` sería incoherente dentro del mismo permiso (spec §14.1) |
| **Publicar el historial de pertenencias con una bandera** `includeHistory` | Es otra pregunta, crece sin cota y la hará quien liquide comisiones. El dato se conserva; la lectura se especifica el día que alguien la pida (spec §14.2) |
| **Paginar `members`** | Un equipo reúne a la cúspide. La paginación entra como enmienda si un equipo llega a tener cientos, y hasta entonces es contrato que nadie usa |
| **Traer los roles de cada miembro**, como `RF-SP-042` | Todos portan el mismo (`RN-SP-051`): sería repetir la regla en cada fila. Donde hay mezcla —el equipo a cargo— sí se publican |
| **Reutilizar `TeamItem` para los miembros** | `TeamItem` es la fila de un equipo; el miembro es una persona. Compartir un tipo por parecerse en tres campos es cómo se acaba publicando `memberCount` de una persona |
| **Resolver el motivo con una consulta por equipo en el listado también** | En el detalle es una consulta; en un listado sería una por fila. Por eso el motivo vive aquí y no en `RF-SP-064` |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Que `memberCount` y `members` no cuadren por leerse en consultas distintas | Medio — dos números que se contradicen en la misma respuesta | Misma transacción de solo lectura y `CA-SP-751`, que compara los dos y además contra `RF-SP-064` |
| Que la lista crezca en sentencias al añadir el estado de la persona | Medio — un `N+1` silencioso | El `JOIN` a `users` va en la misma consulta, y `CA-SP-754` cuenta las sentencias con uno y con varios miembros |
| Que un equipo eliminado con miembros vigentes rompa el supuesto de `FA-003` | Bajo | `RN-SP-054` lo impide en `RF-SP-068`, y `CA-SP-752` comprueba el caso normal; si apareciera, la lectura los devolvería sin fallar |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-748`, `CA-SP-749` | API (`TeamDetailIT`) | La ficha completa y las seis columnas de cada miembro, con el orden por antigüedad y el desempate por nombre de usuario —dos miembros con el mismo `joinedAt`, que es el caso normal de una asignación múltiple |
| `CA-SP-750` | API (`TeamDetailIT`) | Una pertenencia cerrada en este equipo y un manager que hoy está en otro: ninguno aparece |
| `CA-SP-751` | API (`TeamDetailIT`) | `memberCount` contra el tamaño de `members` y contra la fila de `RF-SP-064` |
| `CA-SP-752`, `CA-SP-753` | API (`TeamDetailIT`) | El `INACTIVO` con miembros, el eliminado con motivo y sin miembros, el eliminado **sin** registro de auditoría, el inexistente y el `uuid` mal formado |
| `CA-SP-754` | Integración (`TeamDetailIT`) | **Número de sentencias**: dos con uno y con cinco miembros, tres en el eliminado |
| `CA-SP-755` | API + `EndpointPermissionsIT` | El `403` con `teams:list` puesto, y la ruta con su código |

**El fixture:** un equipo con dos miembros —uno activo y uno desactivado, asignados con la misma marca de tiempo—, una pertenencia cerrada de un tercero, un manager que pertenece a otro equipo, un equipo `INACTIVO` con miembros, un equipo eliminado con su registro de auditoría y otro eliminado a mano **sin** registro.

**Lo que no se prueba aquí:** cómo entran y salen los miembros (`RF-SP-069`, `RF-SP-070`); las pertenencias las inserta el fixture directamente.
