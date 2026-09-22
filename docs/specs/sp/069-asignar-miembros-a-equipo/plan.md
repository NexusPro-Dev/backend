# PLAN — `RF-SP-069` Asignar miembros a un equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-069` |
| Especificación | [`spec.md`](spec.md), aprobada el 22-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Enfoque

**Resolver todo antes de escribir nada, y escribir todo de una vez.** El servicio carga el equipo con bloqueo, resuelve a las personas de la lista en **una** consulta, resuelve sus roles en **otra**, decide quién puede entrar, y solo entonces cierra y abre pertenencias. Es lo que hace posible el `422` con la lista completa de quienes no pueden, en lugar de rechazar por la primera.

**La cúspide se decide con lo que ya existe.** `RoleCatalog.roleIdsOf(userId)` y `findAllById`, y `CommercialStructure.rolDeMayorRango` / `esCuspide` — las mismas piezas con las que `AssignSupervisorService` comprueba `RN-SP-020`. **No se escribe una segunda definición de «quién es la cúspide»**: si mañana nace un rango por encima de `MANAGER`, cambia `parent_role_id` y las dos operaciones lo siguen sin tocar código, que es exactamente el motivo por el que `RN-SP-051` se enunció por la forma de la jerarquía y no por el código del rol.

**El cruce de paquetes es hacia `users`, y es de lectura.** `teams` consume `RoleCatalog`, `UserCatalog` y `CommercialStructure`, que ya son puertos publicados dentro de `SP`; no toca `user_roles` ni `user_supervisors` por su cuenta. La dirección contraria —`users` leyendo `teams`— no existe todavía y llegará el día que la ficha de una persona diga en qué equipo está.

**La transacción escribe dos cosas por persona movida**: el cierre de la pertenencia anterior y la apertura de la nueva. Las dos, con las de todas las personas de la petición, **bajo un mismo identificador de correlación** en la auditoría, como `RN-SP-019` exige para el par rol-superior.

## 2. Cambios de esquema

**Ninguno.** `team_members`, `uq_team_members_vigente` e `ix_team_members_team_vigente` existen desde `V33`; `teams:assign-members`, desde `V34`.

**`uq_team_members_vigente` es la red de `RN-SP-052`**: si dos peticiones simultáneas intentan abrir dos pertenencias vigentes para la misma persona, el índice muerde y el repositorio lo traduce al mismo `409` que la comprobación previa, nunca a un `500`. Es el patrón de `uq_teams_name` en el alta.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `TeamMember` | Modificado | `open` y `close` ya existen desde `V33`; aquí se ejercitan por primera vez |
| `domain/repository` | `TeamMemberRepository` | Modificado | `findActiveOf(userIds)` —las pertenencias vigentes de un conjunto de personas, en una consulta—, `save`, `saveAll` |
| `domain/service` | `TeamMembershipRules` | Nuevo | **La regla, aislada y sin Spring** (Art. VI.3): dada la lista de personas con sus roles, devuelve quiénes pueden entrar y quiénes no y por qué. Es `RN-SP-051` en una clase, probable con dobles |
| `domain/service` | `AssignTeamMembersService` | Nuevo | Orquesta: bloqueo del equipo, `404`/`409`, resolución en bloque, `TeamMembershipRules`, cierres y aperturas, auditoría correlacionada, relectura del detalle. `@Transactional` |
| `application` | `AssignTeamMembersRequest` | Nuevo | `memberIds` (1..100, sin repetidos) y `reason` con `ChangeReason` de `shared/audit` — el mismo tipo que usa `RF-SP-041` |
| `interfaces` | `TeamController` | Modificado | `POST /api/v1/teams/{id}/members` con `teams:assign-members` |
| `users` (consumidos) | `RoleCatalog`, `UserCatalog`, `CommercialStructure` | Sin cambios | Se usan tal como están |
| Pruebas | `TeamMembersIT`, `TeamMembershipRulesTest` | Nuevo | §11 |
| Pruebas | `TeamConcurrencyIT` | Modificado | Doble asignación de la misma persona; asignación contra eliminación |
| Pruebas | `TeamStatusIT` | Modificado | `CA-SP-766`, que esperaba a este requerimiento |
| Pruebas | `EndpointPermissionsIT`, `OpenApiContractIT` | Modificado | La ruta y su extensión |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/teams/{id}/members` | Asigna managers al equipo, cerrando su pertenencia anterior |

**Petición**

```json
{
  "memberIds": [
    "01a0b6f6-7400-7001-9c4f-5e7ad4000011",
    "01a0b6f6-7400-7002-9c4f-5e7ad4000012"
  ],
  "reason": "Reorganización de la región norte, septiembre de 2026"
}
```

**Respuesta `200`**: la forma del detalle de `RF-SP-065`, con los miembros resultantes.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Lista vacía, identificador mal formado, más de 100, motivo ausente o largo, campo no admitido | `VAL-001` a `VAL-006` |
| `403` | Sin `teams:assign-members` | — |
| `404` | El equipo no existe o está eliminado (`EX-001`) | `EX-001` |
| `409` | El equipo está `INACTIVO` (`EX-002`) | `RN-SP-053` |
| `422` | Alguna persona no existe o está eliminada (`EX-003`) | `EX-003` |
| `422` | Alguna persona no es de la cúspide (`EX-004`) | `RN-SP-051` |

**`409` para el estado del equipo y `422` para lo que viene en el cuerpo**, que es el reparto que `RF-SP-030` fijó: el conflicto con el recurso de la ruta es `409`; lo que el cliente envió y no se puede procesar, `422`. Los dos `422` llevan en `fieldErrors` **la lista de identificadores** que causan el rechazo.

## 5. Autorización

| Endpoint | Permiso |
|---|---|
| `POST /api/v1/teams/{id}/members` | `teams:assign-members` |

`CA-SP-788` prueba el `403` con `teams:update` y `teams:remove-members` puestos. **Asignar y retirar son dos permisos**, y no por simetría formal: quien reorganiza la cúspide puede necesitar mover gente sin poder dejar a nadie fuera de todo equipo, y el frontend enseña dos acciones distintas.

**Nada por estructura ni por pertenencia**: el actor no necesita relación alguna con el equipo ni con las personas. Un manager no se asigna a sí mismo (D-22).

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Pertenencia abierta | `audit_change_log` | `CREATE` de `team_members` con el equipo, la persona y el motivo |
| Pertenencia cerrada por el movimiento | `audit_change_log` | `UPDATE` de `team_members` con la fecha de fin y el motivo |
| Persona que ya pertenecía (`FA-001`) | — | **Nada**: no hubo cambio |
| `409` y `422` | — | Nada: son rechazos de negocio |

**Un identificador de correlación para toda la petición**, de modo que «esta reorganización movió a estas cinco personas» se pueda reconstruir de una pieza. Es lo que `RN-SP-019` exige del par rol-superior y aquí se aplica a un lote.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Cierres, aperturas y todas sus filas de auditoría | **La misma**, con el equipo destino bloqueado (Art. V.14) |

**El bloqueo es del equipo destino**, no de las personas: es lo que ordena esta operación contra `RF-SP-068` (eliminar) sobre el mismo equipo. Contra otra asignación de **la misma persona a otro equipo** el ordenamiento no puede venir del bloqueo —son equipos distintos— y lo pone `uq_team_members_vigente`, que es exactamente para lo que se declaró parcial (§2).

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| Fuera de `SP` | Ninguno |
| `SP` · usuarios | **De lectura**: `RoleCatalog`, `UserCatalog` y `CommercialStructure`. Ninguna escritura, y `CA-SP-787` lo afirma comprobando que `user_supervisors` y `user_roles` quedan intactos |
| `CM` (futuro) | Es quien leerá este historial al liquidar. El identificador de correlación y el motivo son para él |

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Asignar desde la persona** (`PUT /users/{id}/team`) | La pregunta del administrador es «a quién pongo aquí», y el permiso debe vivir con el recurso que cambia. Además obligaría a una petición por persona en una reorganización |
| **Una persona por petición** | Una reorganización son varias personas y un solo motivo; con una petición por cabeza el motivo se repite y el lote deja de ser reconstruible |
| **Aplicar las que se puedan y devolver los errores del resto** | El administrador no sabría quién entró sin volver a consultar, y el motivo declarado valdría para un conjunto distinto del pedido |
| **Rechazar por la primera persona inválida** | Obligaría a descubrirlas de una en una. Resolver en bloque cuesta dos consultas y las informa todas |
| **Permitir directores y agentes** «y que el frontend filtre» | `RN-SP-051`: podrían figurar en un equipo distinto del de su manager, y no habría regla que lo impidiera sin recorrer la cadena |
| **Deducir la cúspide por el código `MANAGER`** | Ataría la regla al catálogo de roles de hoy. `CommercialStructure.esCuspide` la decide por la forma de la jerarquía, y un rango nuevo por encima no exigiría tocar código |
| **Reabrir la pertenencia de quien ya está**, por uniformidad | Perdería su antigüedad, que es lo que ordena la lista del detalle, y llenaría el historial de tramos falsos |
| **Cerrar la anterior en una operación aparte** | Dejaría un instante sin equipo, y con él un hueco en el historial que las comisiones leerían como «no pertenecía a nadie» |
| **Admitir `startedAt` declarado** | Permitiría reescribir a quién se atribuía una venta pasada (spec §14.7) |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Que dos asignaciones simultáneas dejen dos pertenencias vigentes de la misma persona | Alto — rompe `RN-SP-052` | `uq_team_members_vigente` y la traducción del choque al `409` de negocio; `TeamConcurrencyIT` lo prueba |
| Que la resolución de roles degenere en una consulta por persona | Medio — `N+1` en un lote de cien | `roleIdsOf` y `findAllById` en bloque; la prueba del lote de cien comprueba que el número de sentencias no crece con él |
| Que `RN-SP-025` no se cumpla y alguien porte dos roles vendedores | Bajo, pero silencioso | `SellerRoleCatalog` y `CommercialStructure` ya fallan de forma visible; esta operación no elige uno |
| Que la regla de la cúspide se duplique aquí y en `AssignSupervisorService` | Medio — dos definiciones que divergen | `TeamMembershipRules` **usa** `CommercialStructure`, no la reimplementa |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-778`, `CA-SP-780` | API (`TeamMembersIT`) | El lote con varios; quien ya estaba conserva `joinedAt` y no se audita |
| `CA-SP-779` | Integración (`TeamMembersIT`) | El movimiento entre equipos: fila cerrada en el origen, abierta en el destino, recuentos de ambos |
| `CA-SP-781`, `CA-SP-782` | API (`TeamMembersIT`) | Los dos `422` con la lista de causantes, y que **nadie** queda asignado |
| `CA-SP-782` | Unitaria (`TeamMembershipRulesTest`) | `RN-SP-051` aislada: manager sí; director, agente, cliente y persona sin rol, no. Sin Spring ni base de datos |
| `CA-SP-783` | API (`TeamMembersIT`) | `409` del `INACTIVO` y `404` del eliminado |
| `CA-SP-784` | API (`TeamMembersIT`) | El manager desactivado entra |
| `CA-SP-785` | API (`TeamMembersIT`) | Las seis validaciones, sin escribir |
| `CA-SP-786` | Integración (`TeamMembersIT`) | Filas `CREATE` y `UPDATE` con motivo y **un solo identificador de correlación** para toda la petición |
| `CA-SP-787` | Integración (`TeamMembersIT`) | `user_supervisors` y `user_roles` intactos antes y después |
| `CA-SP-788` | API + `EndpointPermissionsIT` | El `403` con los dos permisos vecinos, y la ruta con su código |
| `CA-SP-766` (de `RF-SP-067`) | API (`TeamStatusIT`) | **Se cierra aquí**: asignar a un equipo suspendido responde `409` y pasa tras reactivarlo |

**Y dos carreras** en `TeamConcurrencyIT`: la misma persona asignada a dos equipos a la vez —una gana, la otra `409`, nunca `500`— y asignación contra eliminación del mismo equipo, que cierra la media carrera declarada en `RF-SP-068` `T-09`.

**El fixture:** dos equipos activos, uno inactivo; tres managers —uno sin equipo, uno en el otro equipo, uno desactivado—, un director, un agente, un cliente y una persona eliminada.
