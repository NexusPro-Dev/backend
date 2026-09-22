# PLAN — `RF-SP-070` Retirar miembros de un equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-070` |
| Especificación | [`spec.md`](spec.md), aprobada el 22-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Enfoque

**Dos trabajos en una tripleta, y conviene verlos separados.** El primero es la operación manual: una ruta, un servicio que cierra pertenencias en bloque y su auditoría — la mitad del trabajo de `RF-SP-069` y sin ninguna regla de admisión que evaluar. El segundo es **`RN-SP-055`**: que retirar el rol de manager o eliminar a la persona cierre su pertenencia **dentro de la transacción de aquellas operaciones**, que ya están construidas.

**El segundo se resuelve con un puerto, no con una llamada entre servicios.** `teams` publica `TeamMembershipRetirement` —«cierra la pertenencia vigente de esta persona, si la tiene, con este motivo y esta correlación»— y `RF-SP-029` y `RF-SP-031` lo invocan dentro de su `@Transactional`. Es la forma que el módulo ya usa para `CourseTreeRetirement` y la que mantiene la dirección de la dependencia: **`users` conoce un puerto de `teams`, y `teams` no llama a `users` para esto**. La alternativa —que `teams` escuchara un evento— se descarta en §9.

**Idempotente por definición.** El puerto no falla si la persona no tiene pertenencia: eliminar a alguien que nunca estuvo en un equipo no es un caso de error, y hacer fallar la eliminación de una persona por eso sería exactamente el tipo de acoplamiento que una enmienda no debe introducir. El `422` de «no pertenece» vive **solo** en la operación manual, donde hay alguien que lo pidió explícitamente.

## 2. Cambios de esquema

**Ninguno.** `team_members` y sus índices existen desde `V33`; `teams:remove-members`, desde `V34`.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/repository` | `TeamMemberRepository` | Modificado | `findActiveIn(teamId, userIds)` — las pertenencias vigentes **de este equipo** para un conjunto de personas, en una consulta |
| `domain/service` | `RemoveTeamMembersService` | Nuevo | Bloqueo del equipo, `404`, resolución en bloque, `422` con la lista, cierres y auditoría correlacionada, relectura del detalle |
| `application` | `RemoveTeamMembersRequest` | Nuevo | `memberIds` 1..100 y `reason` con `ChangeReason`; rechaza campos desconocidos |
| `application` | `TeamMembershipRetirement` | **Nuevo — el puerto de `RN-SP-055`** | `retireFrom(userId, motivo, correlacion)`: cierra la pertenencia vigente si la hay, y no falla si no la hay. Publicado por `teams`, consumido por `users` |
| `interfaces` | `TeamController` | Modificado | `POST /api/v1/teams/{id}/members/removals` con `teams:remove-members` |
| `users/domain/service` | `RevokeUserRolesService` (`RF-SP-031`) | **Modificado** | Si el retiro deja a la persona sin el rol de mayor rango, invoca el puerto **en la misma transacción** y con la misma correlación |
| `users/domain/service` | `DeleteUserService` (`RF-SP-029`) | **Modificado** | Invoca el puerto en la misma transacción |
| Tripletas | `docs/specs/sp/029-eliminar-usuario/`, `031-retirar-roles-usuario/` | **Enmendadas (Art. I.7)** | La enmienda se escribe **antes** de tocar su código: `T-01` y `T-02` |
| Pruebas | `TeamMemberRemovalIT`, `TeamMembershipRetirementIT` | Nuevo | §11 |
| Pruebas | `UserDeletionIT`, `RevokeUserRolesIT` | Modificado | `CA-SP-795`, `CA-SP-796` |
| Pruebas | `EndpointPermissionsIT`, `OpenApiContractIT` | Modificado | La ruta y su extensión |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/teams/{id}/members/removals` | Cierra la pertenencia de una o varias personas en este equipo |

**Petición**

```json
{
  "memberIds": ["01a0b6f6-7400-7002-9c4f-5e7ad4000012"],
  "reason": "Sale de la estructura comercial tras la reorganización"
}
```

**Respuesta `200`**: la forma del detalle de `RF-SP-065`, ya sin los retirados.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Lista vacía, identificador mal formado, más de 100, motivo ausente o largo, campo no admitido | `VAL-001` a `VAL-006` |
| `403` | Sin `teams:remove-members` | — |
| `404` | El equipo no existe o está eliminado | `EX-001` |
| `422` | Alguna persona no pertenece hoy a este equipo | `EX-002` |

**`…/members/removals` y no `DELETE /teams/{id}/members`**: el motivo viaja en el cuerpo, y un `DELETE` con cuerpo no está garantizado por todos los intermediarios. Es la forma de `RF-SP-031` (`/roles/revocations`) y de las eliminaciones del sistema.

## 5. Autorización

| Endpoint | Permiso |
|---|---|
| `POST /api/v1/teams/{id}/members/removals` | `teams:remove-members` |

`CA-SP-797` prueba el `403` con `teams:assign-members` puesto: mover gente y dejarla fuera de todo equipo son dos decisiones distintas (`RN-SEG-014`).

**El puerto de `RN-SP-055` no exige permiso, y es correcto**: no es una operación de la API. Quien elimina a una persona ya trae `users:delete`, y quien le retira el rol, `users:revoke-roles`; la pertenencia se cierra como **consecuencia** de esa operación autorizada, no como una segunda operación. Por eso el puerto no entra en `EndpointPermissionsIT` —no es una ruta— y sí en las suites de `RF-SP-029` y `RF-SP-031`.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Retiro manual | `audit_change_log` | Una fila `UPDATE` de `team_members` por cierre, con la fecha de fin, el motivo y **un identificador de correlación por petición** |
| Cierre por `RN-SP-055` | `audit_change_log` | Fila `UPDATE` con el motivo de la operación que lo causó y **la correlación de esa operación** — la del retiro del rol o la de la eliminación |
| Persona sin pertenencia en el puerto | — | Nada: no hubo cambio |

**Que la correlación sea la de la operación causante es la mitad del valor de la enmienda**: permite responder «cuando esta persona dejó de ser manager, ¿qué más pasó?» leyendo una sola correlación, que es lo que `RN-SP-019` ya exige para el par rol-superior.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Cierres del retiro manual y su auditoría | **La misma**, con el equipo bloqueado |
| Cierre por `RN-SP-055` y la operación que lo causa | **La misma que la del retiro de rol o la eliminación** (Art. V.14) |

**Lo segundo es la exigencia de fondo de `RN-SP-055`**, y `CA-SP-795` la prueba por el lado que importa: si el retiro del rol falla, la pertenencia **no** se cierra. Un cierre que sobreviviera a una operación fallida dejaría a alguien fuera de su equipo sin haber dejado de ser manager.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| Fuera de `SP` | Ninguno |
| `SP` · usuarios | **`RF-SP-029` y `RF-SP-031` cambian de comportamiento** (Art. I.7): cierran la pertenencia en su transacción. Sus tripletas se enmiendan antes de tocar su código, y sus suites ganan el criterio |
| `SP` · usuarios | **`RF-SP-028` no cambia**, y se declara para que no se asuma lo contrario: cambiar el estado de una persona **no** la saca del equipo |

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Que `RF-SP-029` y `RF-SP-031` llamen al endpoint** | Una operación interna no pasa por HTTP; perdería la transacción y exigiría un actor con permiso que no tiene por qué existir |
| **Un evento de dominio que `teams` escuche** | Rompe la transacción única que `RN-SP-055` exige: un evento consumido después dejaría una ventana con alguien que ya no es manager dentro de un equipo, y `CA-SP-795` no podría afirmar que el fallo del retiro deshace el cierre |
| **Que `teams` no se entere y la lista se filtre al leer** | El detalle tendría que comprobar el rol de cada miembro en cada lectura, y `RN-SP-051` pasaría de ser una regla de escritura a un filtro permanente que cuesta un `JOIN` por consulta |
| **Que el puerto falle si la persona no tiene pertenencia** | Haría fallar la eliminación de cualquier persona que no esté en un equipo, que son casi todas |
| **`DELETE /teams/{id}/members` con cuerpo** | Un `DELETE` con cuerpo no está garantizado por los intermediarios (§4) |
| **Tratar «no pertenece» como no-op**, por simetría con la asignación | El administrador creería haber sacado a alguien de donde no estaba (spec §14.1) |
| **Prohibir retirar de un equipo `INACTIVO`** | La regla se cerraría sobre sí misma: el equipo no podría vaciarse y por tanto no podría eliminarse |
| **Distinguir en el `422` «no tiene equipo» de «está en otro»** | Convertiría el error en una consulta sobre dónde está cada cual, que ya responden `RF-SP-064` y `RF-SP-065` |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Que el cierre por `RN-SP-055` quede fuera de la transacción causante | Alto — alguien fuera de su equipo sin haber dejado de ser manager | `CA-SP-795` prueba el caso de fallo, no solo el feliz |
| Que la enmienda se implemente antes de escribirse en las tripletas de `RF-SP-029` y `RF-SP-031` | Medio — el orden que el Art. I.7 exige | `T-01` y `T-02` son las **primeras** tareas y no tocan código |
| Que `users` acabe dependiendo de más de este puerto | Medio — acoplamiento creciente entre submódulos | El puerto tiene **un** método y la regla de ArchUnit de `architecture.md` §5.3 sigue vigilando la frontera |
| Que una reorganización cierre pertenencias a medias | Medio | Toda la lista o nada, en una transacción, con el equipo bloqueado |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-789` | API (`TeamMemberRemovalIT`) | El retiro de uno y de varios; el detalle sin ellos; **la fila cerrada sigue existiendo** con su fecha de fin |
| `CA-SP-790` | API (`TeamMemberRemovalIT`) | El `422` con quien no tiene equipo y con quien está en otro, la lista de causantes y que **nadie** se retira |
| `CA-SP-791` | Integración (`TeamMemberRemovalIT`) | Retiro sobre `INACTIVO`; vaciado del último y **eliminación que ya no responde `409`**, recorriendo `RN-SP-054` entera |
| `CA-SP-792` | API (`TeamMemberRemovalIT`) | Las seis validaciones, sin escribir |
| `CA-SP-793` | Integración (`TeamMemberRemovalIT`) | Una fila `UPDATE` por cierre, con motivo y una sola correlación |
| `CA-SP-794` | Integración (`TeamMemberRemovalIT`) | `user_roles`, `user_supervisors` y el estado de las personas, intactos |
| `CA-SP-795` | Integración (`RevokeUserRolesIT`) | Retirar el rol de manager cierra la pertenencia con la **misma correlación**; y **si el retiro falla, la pertenencia sigue abierta** |
| `CA-SP-796` | Integración (`UserDeletionIT`) | Eliminar cierra la pertenencia; **cambiar el estado no** |
| `CA-SP-797` | API + `EndpointPermissionsIT` | El `403` con `teams:assign-members`, y la ruta con su código |

**Unitaria del puerto** (`TeamMembershipRetirementIT`): cierra si hay pertenencia, no falla si no la hay, y no toca nada más.

**El fixture:** un equipo activo con tres miembros, uno inactivo con dos, un manager sin equipo, un manager en otro equipo, y una persona con rol de manager a la que se le retira el rol dentro de la prueba.
