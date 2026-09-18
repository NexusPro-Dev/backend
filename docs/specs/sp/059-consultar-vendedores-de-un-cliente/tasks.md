# TASKS — `RF-SP-059` Consultar los vendedores de un cliente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-059` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/vendedores-de-un-cliente` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V20__sp_vendedores_del_cliente.sql`: la tabla con sus dos `CHECK`, sus tres claves foráneas, `uq_client_sellers_principal` e `ix_client_sellers_vendedor`; después la mudanza —copiar la vigente de cada cliente como `REGISTRO` con `started_at`, borrar todas las de clientes— con el predicado «`CONSUMIDOR` y no `VENDEDOR`» | — | `CA-SP-688`, `CA-SP-692`. Un cliente ascendido a vendedor **conserva** su fila de mando | Pendiente |
| `T-02` | Prueba de esquema `ClientSellersSchemaIT`: los dos `CHECK`, el índice único parcial (dos `REGISTRO` se rechazan, `REGISTRO` + `HOTLINK` conviven) y la mudanza sobre datos sembrados en la prueba | `T-01` | `CA-SP-688`, `CA-SP-692`, riesgo 2 del plan | Pendiente |
| `T-03` | `ClientSellerRepository` (puerto) y `JpaClientSellerRepository` nativo en `users/domain/repository`: `registerPrincipal`, `attachFirstMovement`, `principalOf`, `findSellersOf` ordenado principal primero y después por `created_at` | `T-01` | El orden de `spec.md` §6.2 sale de la consulta, no de Java | Pendiente |
| `T-04` | `ClientSellersResponse` y `SellerItem` en `users/application`: `username`, `firstName`, `lastName`, `origin`, `principal`, `linkedAt` — **sin `id`** | `T-03` | `CA-SP-687` | Pendiente |
| `T-05` | `GetClientSellersService`: la variante por actor y la variante por identificador con su `404` | `T-04` | `CA-SP-689`, `CA-SP-690`, `CA-SP-691` | Pendiente |
| `T-06` | `UserController`: `GET /api/v1/users/me/sellers` **sin `@PreAuthorize`** y con el motivo escrito; `GET /api/v1/users/{id}/sellers` con `users:read` | `T-05` | `CA-SP-686`, `CA-SP-691` | Pendiente |
| `T-07` | Declarar `/me/sellers` en `EndpointPermissionsIT` como autenticada sin permiso, **con su motivo** | `T-06` | La lista cerrada sigue siendo exhaustiva | Pendiente |
| `T-08` | `RegisterClientByLinkService`: `registerPrincipal` **antes** de la venta y `attachFirstMovement` después; `RegistrationSaleRegistrar.registerSale` devuelve `RegisteredSale(id, code)` y `PublishedRegistrationSaleRegistrar` lo adapta. **Deja de llamar a `assignSupervisor`** | `T-03` | `CA-SP-697`: la fila `REGISTRO` lleva la venta del enlace y `user_supervisors` no recibe nada | Pendiente |
| `T-09` | `PublishedUserCatalog.sellerOf`: primero el `REGISTRO` de `client_sellers`, después el superior vigente. Javadoc de `ClientCatalog` y de `RN-MV-003` al día | `T-03` | `CA-SP-695` | Pendiente |
| `T-10` | `GetBrokerAccountsService`: la autorización admite **superior vigente o principal** (`RN-SP-046`), y el `404` común se conserva | `T-03` | `CA-SP-693`, y `CA-SP-634`/`CA-SP-635` siguen en verde | Pendiente |
| `T-11` | `JpaBrokerAccountQueryRepository` — el equipo de `RF-SP-056`: `findByTeamOf` y `countByTeamOf` sobre la **unión** de subordinados vigentes y clientes `REGISTRO` | `T-01` | Un agente ve las cuentas de sus clientes en `/me/team/broker-accounts`; el total cuenta la unión | Pendiente |
| `T-12` | `JpaBrokerAccountQueryRepository` — `RF-SP-057`: la recursiva sigue sobre `user_supervisors` y **el alcance de cuentas** pasa a ser red ∪ clientes `REGISTRO` de la raíz y de cada nodo; `SIN_VENDEDOR_ENCIMA` pasa a mirar `client_sellers` | `T-01` | `CA-SP-648` precisado, `CA-SP-649` intacto; el resumen de `CA-SP-673` cuadra | Pendiente |
| `T-13` | `JpaBrokerAccountQueryRepository` — `RF-SP-058`: `countDirectAccountsBySupervisor`, `countDirectConsumersBySupervisor` y `countUnassignedAccounts` sobre `client_sellers` `REGISTRO`; Javadoc de `GetNetworkIndicatorsService` | `T-01` | `CA-SP-694`; `losNumerosCuadran` sigue cuadrando con `RF-SP-057` | Pendiente |
| `T-14` | `semilla-desarrollo.sql`: los tres clientes a `client_sellers` con la misma profundidad elegida —agente, director, manager—; cabecera y comentarios al día. `DevelopmentSeedIT` invierte la comprobación | `T-01` | Ningún cliente en `user_supervisors`; `cliente1` → `agente1`, `cliente2` → `director1`, `cliente3` → `manager1` como `REGISTRO` | Pendiente |
| `T-15` | Invertir las pruebas de las enmiendas: `SelfRegistrationIT` (`CA-SP-513`, `CA-SP-525`, `CA-SP-526` → `CA-SP-697` a `CA-SP-699`) y `CommercialTeamIT` (`CA-SP-625` → `CA-SP-696`) | `T-08`, `T-14` | Las cuatro nuevas en verde; las cuatro viejas ya no existen | Pendiente |
| `T-16` | `ClientSellersIT`: `CA-SP-686` a `CA-SP-691`, con una fila `HOTLINK` insertada a mano para el orden y `principal = false` | `T-07` | Los seis criterios | Pendiente |
| `T-17` | Documentación OpenAPI de las dos rutas: **prosa**, no solo esquema — que hoy la lista tiene un elemento y **por qué**, que el principal no se cambia, y qué significa `linkedAt` en una fila migrada. `api/index.md` con las dos rutas y el contrato regenerado | `T-16` | El diff de `openapi.json` solo añade | Pendiente |
| `T-18` | Actualizar la matriz de `docs/requirements.md` y los estados de esta tripleta | `T-17` | La fila de `RF-SP-059` refleja el estado; las de `RF-SP-042`, `045`, `055` a `058` y `RF-MV-001` citan sus criterios nuevos | Pendiente |

## 2. Orden de ejecución

**`T-01` primero y sola**: es la que cambia el mundo para todas las demás. `T-02` justo después, cuando todavía se recuerda por qué el predicado tiene dos mitades.

**`T-08` a `T-13` van en el mismo Pull Request que `T-01`, sin excepción.** Después de `V20`, una lectura que siga mirando `user_supervisors` no falla: **devuelve de menos**. `RF-SP-055` negaría las cuentas a un agente, `RF-SP-058` pondría a todos los clientes en «no atribuido» y `RF-MV-001` atribuiría cada venta al propio cliente. Son las tres cosas que `CA-SP-693` a `CA-SP-695` existen para impedir.

**`T-03` a `T-07` y `T-16` son la lectura**, y son lo único que puede esperar sin que nada se rompa.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-686`, `CA-SP-687` | `T-03`, `T-04`, `T-06`, `T-16` |
| `CA-SP-688` | `T-01`, `T-02` |
| `CA-SP-689`, `CA-SP-690`, `CA-SP-691` | `T-05`, `T-06`, `T-16` |
| `CA-SP-692` | `T-01`, `T-02`, `T-14` |
| `CA-SP-693` | `T-10` |
| `CA-SP-694` | `T-13` |
| `CA-SP-695` | `T-09` |
| `CA-SP-696` a `CA-SP-699` (en `RF-SP-042` y `RF-SP-045`) | `T-08`, `T-15` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **Las filas `HOTLINK` no las escribe nadie** hasta `RF-MV-011` y `RF-MV-013`. **No bloquea este requerimiento**: la lista devuelve el principal desde el primer día y el contrato ya es el definitivo | 18-09-2026 | Responsable del proyecto | **Abierto** |
| 2 | **Corregir un principal mal registrado no tiene vía.** Declarado el 18-09-2026 con la decisión de que el principal no se cambia; si algún día hace falta, es un requerimiento nuevo con motivo y `SUPERADMIN`, no un `UPDATE` | 18-09-2026 | Responsable del proyecto | **Abierto** |
| 3 | **Qué hace `CM` con una venta cuyo vendedor `REGISTRO` fue retirado.** `RN-SP-022` dejó de proteger la cartera y lo remite a la liquidación, que no existe | 18-09-2026 | Responsable del proyecto | **Abierto** |
| 4 | **El frontend puede estar enseñando «mi agente» desde `GET /users/me`**, que deja de traer `supervisor` a un cliente. Avisar a las sesiones de frontend con la ruta nueva antes de integrar | 18-09-2026 | Responsable técnico | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde, **incluidos los cuatro invertidos en otras tripletas**.
- [ ] `mvn verify` en verde en local.
- [ ] `/me/sellers` consta en `EndpointPermissionsIT` con su motivo escrito al lado.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [ ] Ningún cliente queda en `user_supervisors` en la semilla de desarrollo.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
