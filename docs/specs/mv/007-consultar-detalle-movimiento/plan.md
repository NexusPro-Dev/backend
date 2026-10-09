# PLAN — `RF-MV-007` Consultar el detalle de un movimiento, con su comprobante

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-007` |
| Especificación | [`spec.md`](spec.md) v0.4.0 |
| Versión | 0.4.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |
| Enmendado el | 01-10-2026 — `SaleResponse` gana el destino del retiro (§12) |
| Enmendado el | 09-10-2026 — `SaleLineResponse` gana `team`, leído en la misma sentencia de las líneas (`RN-MV-078`) |

!!! warning "Enmendado el 09-10-2026 — `team` en cada línea"

    `spec.md` v0.4.0 (`RN-MV-078`, [`requirements/mv.md`](../../../requirements/mv.md) v0.97.0 §4.13). **La columna la trae `V97`** —`movement_details.team_id`, clave foránea a `teams` con `ON DELETE SET NULL`— y la escriben el registro, la asignación del vendedor (`RF-MV-016`) y el relleno (`RF-MV-058`); **el detalle solo la lee**. Ninguna migración ni permiso.

    **`SaleLineResponse` gana `team`, de tipo `LineTeam(UUID id, String name)`** —el registro de `application` con `@Schema(name = "LineTeam")` que nace con `RF-MV-006` · `plan.md` v0.4.0 y que `RF-MV-017` también publica: una oficina, una forma—, declarado nulable con **`types = {"object", "null"}`** y no con `nullable`, que springdoc descarta en silencio en OpenAPI 3.1 (la trampa que `seller` ya pagó). La clase ya lleva `@JsonInclude(ALWAYS)`, de modo que `team` viaja **presente y nulo**. **`MovementLineRow` gana `teamId` y `teamName`**, y **`findLinesOf` los lee en su misma sentencia** con `LEFT JOIN teams tm ON tm.id = d.team_id` —`LEFT` por lo mismo que el vendedor: la columna admite nulo y un `JOIN` corriente haría desaparecer la línea—. **Sin predicado sobre `deleted_at`**: un equipo eliminado lógicamente (`RN-SP-054`) se sigue nombrando; y el nombre es el de hoy, porque lo congelado es **cuál** oficina. **Ninguna sentencia nueva**: el detalle sigue siendo cabecera, líneas, rebajas y pagos, y «mis compras» (`CA-MV-525`) sigue sin una consulta por fila.

    **`SaleDetailMapper.lineas` arma el `team`** —nulo si `teamId` lo es— y, por ser el único sitio donde se arma la línea leída, **lo ganan a la vez** todas las respuestas que pasan por él: este detalle (`GetMovementService`), el propio (`GetMyMovementService`), «mis compras» (`ListMyMovementsService`), confirmar, anular, rechazar un pago, volver a pagar y asignar vendedores. **La respuesta de registrar NO pasa por el mapper**: `RegisterSaleService` la arma desde el dominio con `SaleResponse.de` → `SaleLineResponse.de(MovementLine, …)`, y su `team` lo resuelve la enmienda de `RF-MV-001`, que es la que escribe la oficina; este plan fija el campo y su forma, y el registro construido de `SaleLineResponse` gana el componente para los dos caminos.

    **`MV` lee `teams` en SQL nativo y no desde Java**: D-25 y `ArchUnit` lo impiden desde código, y un `JOIN` de lectura para un nombre es lo que `findLinesOf` ya hace con `users` para el vendedor. **Descartado**: resolver el nombre aparte por un puerto de `SP` —una ida más por detalle—, y calcular la oficina al leer por la estructura de hoy —lo que `RN-MV-078` prohíbe—.

    **Pruebas** en `MovementDetailIT`, que ya siembra la venta por SQL: el fixture crea un equipo propio con nombre único, escribe `team_id` en una línea y deja otra sin él, y lo borra al terminar. `CA-MV-720` comprueba `team` con identificador y nombre en la primera, `team` **presente y nulo** sobre el JSON en crudo en la segunda, y que la oficina sale aunque el director del vendedor pertenezca hoy a **otro** equipo —o a ninguno—; `CA-MV-290` sigue comparando el cuerpo entero de las dos rutas y lo cubre en el detalle propio.

!!! info "Qué va en este documento"

    **Cómo se construye, y por qué así.** Las decisiones de negocio están en `spec.md`.

---

## 1. Enfoque

**No hay nada que construir en la capa de datos.** `MovementRepository.findById` es desde el 17-09-2026 el detalle **sin alcance** que usan confirmar (`RF-MV-003`), rechazar un pago (`RF-MV-004`), volver a pagar (`RF-MV-018`) y asignar vendedores (`RF-MV-016`): la misma proyección que `findMineById` —la del detalle propio de `RF-MV-008`— sin el predicado del actor, y su Javadoc dice por qué: **no tener dos proyecciones de la misma cabecera**. `SaleDetailMapper` la convierte en `SaleResponse`, que es la respuesta del detalle propio y la de registrar una venta.

Lo que falta es **la ruta y el servicio que la une a esas dos piezas**. Por eso el requerimiento es pequeño, y por eso CA-MV-290 —«lo mismo que el detalle propio»— se cumple por construcción: las dos rutas leen con la misma cabecera y arman con el mismo traductor.

## 2. Cambios de esquema

**`V56`**: el permiso **`movements:read-detail`**, con literal v7 en la serie de `movements:` (000041), **repartido a todo rol que porte `movements:read`** —hoy `SUPERADMIN`, `ADMIN`, `MANAGER` y `DIRECTOR`, por `V40`— como `V47` repartió los del aula a quien portaba `courses:learn`. Guardas por conjunto y de contención (`RN-SEG-003`). Catálogo 162 → **163**, `ADMIN` 160 → **161**.

*Enmienda del 30-09-2026: la 0.1.0 decía «ningún permiso nuevo»; ver `spec.md` §14, pregunta 2.*

## 3. Componentes afectados

| Componente | Cambio |
|---|---|
| `domain/service/GetMovementService` | **Nuevo.** `findById` + `SaleDetailMapper.de`; `EX-001` si no existe |
| `interfaces/MovementController` | `GET /api/v1/movements/{id}` con `movements:read-detail`, la variable con forma de UUID |
| `application/SaleResponse` | **Gana `type`** —`VENTA`, `RETIRO`, `BONO`—: el detalle no decía el tipo, y este abre movimientos que no son ventas. Campo nuevo, compatible |
| `db/migration/V56` | El permiso |
| `EndpointPermissionsIT` | La ruta nueva en el mapa de cada operación con su permiso |
| Recuentos del catálogo | `PermissionIT`, `PermissionsSeedIT`, `MovementsPermissionsSeedIT`, `TeamsPermissionsSeedIT`, `SaleLinesPermissionSeedIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`; y `SystemRolesSeedIT`, que fija lo que portan `MANAGER` y `DIRECTOR` |
| `MovementDetailIT` | **Nueva**: `CA-MV-287` a `CA-MV-292` |

**Un servicio aparte y no un segundo método en `GetMyMovementService`**: aquel lleva el actor inyectado y su Javadoc dice que el alcance va dentro de la consulta para que no haya una comprobación de pertenencia «que alguien pueda mover de sitio». Poner a su lado un método sin alcance es justo eso.

## 4. Contrato de API

```
GET /api/v1/movements/{id}
```

**Es el `GET` del recurso que `POST /api/v1/movements` crea y `GET /api/v1/movements` lista**, sin segmento, como dejó dicho `RF-MV-006` · `plan.md` §4.

**No choca con ninguna ruta literal.** `/sales`, `/sales/lines`, `/mine/shopping`, `/mine/products` y `/mine/balances` son literales y Spring las prefiere a la variable; `/mine/{id}` tiene dos segmentos. La ruta se declara **al final** del controlador, junto a su hermana propia.

**La variable solo admite la forma de un UUID** (enmienda del 30-09-2026). Sin la expresión, `GET /movements/mine` —la ruta que `RF-MV-008` retiró el 22-09-2026— caería aquí y respondería `400` por identificador malformado, y su `CA-MV-140` promete `404`: la prueba lo detectó al construir. Con ella, un segmento que no es un identificador no es esta ruta. El precio es que el identificador malformado deja de ser `400` y pasa a `404`, en esta ruta y solo en esta.

| Respuesta | Cuándo |
|---|---|
| `200` | El comprobante (`SaleResponse`) |
| `401` | Sin token (`AUTH-001`) |
| `403` | Sin `movements:read-detail` (`AUTH-002`) |
| `404` | No existe (`EX-001`), o no tiene forma de UUID |

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:read-detail')")`: un permiso por operación (`RN-SEG-014`, `spec.md` §14, pregunta 2). **No hay alcance**: el servicio no recibe al actor.

## 6. Auditoría

Ninguna: es una lectura.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`, como el detalle propio: cabecera, líneas y pagos se leen en la misma instantánea.

## 8. Impacto sobre otros módulos y documentos

| Documento | Cambio |
|---|---|
| `requirements/mv.md` | §6: el permiso nuevo, y `movements:read` deja de decir «y el detalle de cualquiera»; control de cambios |
| `requirements.md` | La fila con su tripleta y **En desarrollo**; spec redactadas, aprobadas y planes +1; endpoints funcionando +1 |
| `security.md` | §4.4: el permiso nuevo en el bloque y en la prosa; catálogo 163 |
| `api/index.md` | La ruta nueva y el campo `type` |
| `RF-MV-006` · `spec.md` §14 y `RF-MV-008` · `spec.md` | Ninguno: dejaron escrito que el detalle «no existe»; es historia y no se reescribe |

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| **Abrir `/mine/{id}` a quien tenga `movements:read`** | Mezcla dos alcances en una ruta, y el 404 del ajeno —la defensa de `RF-MV-008`— pasaría a depender de un permiso |
| **Una respuesta propia de administración** | Dos formas del mismo comprobante envejecen por separado; `spec.md` §4.1 pide la misma |
| **Reutilizar `movements:read`** (la 0.1.0 de este plan) | Viola `RN-SEG-014`, y `EndpointPermissionsIT` lo rechaza (`spec.md` §14, pregunta 2) |
| **Validar el identificador con `400`, como el detalle propio** | Captura `GET /movements/mine`, que `CA-MV-140` promete en `404` |

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que alguien acote `findById` pensando en el detalle propio | Su Javadoc ya dice que es el detalle sin alcance y quién lo usa; `CA-MV-287` fija que lo ajeno se abre |

## 11. Estrategia de prueba

`MovementDetailIT`, de integración y por HTTP. **La venta se registra por el camino de verdad** —`POST /api/v1/movements`— y no con `INSERT`, para que el detalle lea lo que el sistema escribe; el retiro, con `LedgerFixtures` y la ruta de pedirlo. `CA-MV-290` compara **el cuerpo entero** de las dos rutas sobre el mismo movimiento.

---

## 12. El destino de un retiro — enmienda del 01-10-2026

`RN-MV-056` (`spec.md` v0.3.0 §6.2). **`SaleResponse` gana `withdrawalDestination`**, de tipo `WithdrawalDestinationResponse` (`RF-MV-019` · `plan.md` §12), nulo salvo en un retiro con copia. **`SaleDetailMapper` lo rellena con una sentencia más, y solo si el movimiento es un `RETIRO`**: el detalle de una venta sigue con las mismas sentencias que hoy. Como el detalle propio (`RF-MV-008`) usa el mismo mapper, lo gana sin código propio.

**Es un campo nuevo y nulo**: no rompe a ningún cliente. **No se publica en los listados** (`RF-MV-006`, `RF-MV-015`): quien aprueba abre el detalle, y llevar la copia a cada fila sería un `JOIN` en todas las páginas para un dato que solo se lee de a uno.

**Pruebas**: `MovementDetailIT` gana `CA-MV-424` y `CA-MV-425`.
