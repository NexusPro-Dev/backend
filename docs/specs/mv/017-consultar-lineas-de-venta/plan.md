# PLAN — `RF-MV-017` Consultar las líneas de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-017` |
| Especificación | [`spec.md`](spec.md), aprobada el 23-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 23-09-2026 |

---

## 1. Enfoque

**Una consulta y un conteo, y todo lo que la fila publica dentro de la primera.** La fila lleva datos de cinco tablas —la línea, la venta, el sujeto, el vendedor y la moneda—, y la tentación es resolver el producto o el vendedor aparte, como hace `findSellersOf` en el listado de movimientos. Allí tiene sentido porque **una venta tiene varios vendedores** y el listado devuelve una fila por venta; **aquí la fila ES la línea y tiene exactamente un vendedor**, de modo que todo cabe en las uniones de la misma sentencia. `CA-MV-178` lo fija: dos sentencias con una fila y dos con veinte.

**El vendedor se une con `LEFT JOIN`.** `movement_details.seller_id` es nulable —`V12` lo declaró así para los tipos de movimiento que no venden— y un `JOIN` corriente **haría desaparecer la fila** en lugar de publicarla con `seller` nulo. Es el defecto más fácil de escribir aquí y el que `CA-MV-165` existe para impedir.

**El orden es el de la venta, no el de la línea.** `occurred_at DESC` y, de desempate, `movement_id, product_name`: dos líneas de la misma venta comparten instante, y sin un desempate determinista la paginación repetiría o se saltaría filas entre páginas. Es la lección de `ix_users_busqueda` aplicada al orden en lugar de al filtro.

**El conteo va acotado** (`BoundedCount`, `RF-SP-011`) y aquí más que en ningún otro listado: `movement_details` es la tabla que crece más rápido del sistema, porque una venta de cinco productos son cinco filas.

## 2. Cambios de esquema

**Ninguno en tablas.** Todo lo que la fila publica existe: `movement_details` desde `V7`, el vendedor por línea desde `V12`, el nombre congelado y el descuento desde `V14`, la entrega desde `V16`.

**Una migración de catálogo: `V37`**, que siembra `movements:list-sale-lines` y lo asocia a `SUPERADMIN` y `ADMIN`, con las guardas de siempre —el catálogo en 135, `SUPERADMIN` 135, `ADMIN` 129 y la contención de `RN-SEG-003`—.

**Índices: ninguno nuevo, y conviene justificarlo.** `ix_movement_details_seller` (`V12`, parcial sobre `seller_id, movement_id`) sirve el filtro por vendedor; `idx_movement_details_movement` sirve el filtro por venta y la unión; y el orden por `occurred_at` entra por el índice de fecha que `V15` creó para eso. El filtro por producto recorre `movement_details` sin índice propio, y **se deja así a propósito**: es el filtro menos frecuente de los siete y añadir un índice a la tabla que más crece se paga en cada venta que se registre. Si el uso lo desmiente, es una migración de una línea.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `application` | `SaleLinesRequest` | Nuevo | Los **diez** parámetros (0.2.0: `typeStatus`); los estados y las fechas llegan como **texto** para que su `400` viaje junto a los demás |
| `application` | `SaleLineResponse` | Nuevo | La fila publicada: la línea y su venta (`spec.md` §6.2) |
| `domain/repository` | `MovementRepository` | Modificado | `findSaleLines(filtro, offset, limit)` y `countSaleLines(filtro, techo)`, con los registros `SaleLinesFilter` y `SaleLineRow` |
| `domain/repository` | `JpaMovementRepository` | Modificado | Las dos sentencias, con el `LEFT JOIN` del vendedor y el predicado de tipo `VENTA`. El `JOIN` de `movement_type_statuses` entra en 0.2.0 **solo para filtrar**: la columna no se publica, y va en el bloque de tablas compartido para que la página y el conteo no divergan |
| `domain/service` | `ListSaleLinesService` | Nuevo | Valida los **seis** `400` **juntos**, resuelve el orden y la página, y arma la respuesta. `@Transactional(readOnly = true)` |
| `interfaces` | `MovementController` | Modificado | `GET /api/v1/movements/sales/lines` con `movements:list-sale-lines` |
| Pruebas | `SaleLinesIT` | Nuevo | §11 |
| Pruebas | `EndpointPermissionsIT`, `OpenApiContractIT` | Modificado | La ruta y su extensión |
| Pruebas | Las cuatro del catálogo | Modificado | El recuento a **135** |

**No se toca `ListMovementsService` ni `MovementResponse`.** Son otro contrato —una fila por venta— y cambian por otros motivos; compartir el servicio ataría los dos listados a la vez.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/movements/sales/lines` | Todas las líneas de venta, paginadas |

**Parámetros**: `page`, `size`, `movementId`, `userId`, `sellerId`, `productId`, `status`, `deliveryStatus`, `code`, `from`, `to`. Todos opcionales y combinables.

**Respuesta `200`**: `PageResponse<SaleLineResponse>` con `totalIsExact`.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Paginación, identificador, estado, estado de entrega, **estado del tipo** o rango inválidos — **juntos** | `VAL-001` a `VAL-005` (el del estado del tipo viaja con `VAL-005`, como en `RF-MV-015`) |
| `401` | Sin token | `AUTH-001` |
| `403` | Sin `movements:list-sale-lines` | `AUTH-002` |

**Ruta bajo `/sales` y no `/lines` sueltas**, porque se acota a las ventas (`spec.md` §4.2): el día que un depósito tenga líneas, su consulta será otra ruta y no un parámetro de esta.

## 5. Autorización

| Endpoint | Permiso |
|---|---|
| `GET /api/v1/movements/sales/lines` | `movements:list-sale-lines` |

**El permiso abre y no hay alcance que decida nada** (`spec.md` §2): con él se ve todo el libro. Es lo contrario de `movements:list-sales`, y `CA-MV-176` lo prueba exigiendo `403` con `movements:read`, `movements:list-sales` y `movements:read-own-products` puestos — los tres permisos que un integrador confundiría con este.

Se siembra **solo para `SUPERADMIN` y `ADMIN`**, al contrario que `movements:list-sales`, que `V32` dio a todo rol por su tipo porque era la vista de cualquiera. Esta no lo es.

## 6. Auditoría

**Nada.** El sistema no audita lecturas (`security.md` §7), y este requerimiento no estrena ninguna excepción a eso.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| La página y el conteo | La misma, **de solo lectura** |

`@Transactional(readOnly = true)` no es decorativo: deja las dos sentencias en la misma instantánea, de modo que el total no puede contradecir a la página cuando alguien registra una venta entre las dos.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| Fuera de `MV` | Ninguno. No consume ningún puerto de `SP` ni de `PM`: el nombre del producto y el del vendedor están **en la línea y en `users`**, y el catálogo de productos no se lee |
| `MV` | Aditivo. Ninguna consulta existente cambia de forma |

**Dos dependencias declaradas con `RF-MV-016`** (estados de comisión, en otra rama, acordadas con su sesión el 23-09-2026):

1. **`V36` va delante.** Su migración deja el catálogo en 134 y la guarda de `V37` lo espera ahí. Si el orden se invierte, esta tripleta renumera a `V36` y permiso 134 — no se escriben guardas tolerantes.
2. **`movements` gana `type_status_id` (`NOT NULL`, sin `DEFAULT`)** con su migración. Al integrar, **los fixtures de `SaleLinesIT` que insertan ventas a mano necesitan esa columna**; su sesión pasó la subconsulta que la resuelve. No afecta al código de producción de este requerimiento.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Anidar las líneas dentro de cada venta** | Hace la respuesta impaginable: el tamaño de página dejaría de significar filas. Y eso ya existe, es el detalle (`RF-MV-007`) |
| **Reutilizar `movements:read`** | Rompería `RN-SEG-014` en silencio: un permiso, dos operaciones. Es el defecto que `RF-SP-060` recorrió el sistema entero para quitar |
| **Darle el alcance de `RN-MV-031`** | Un vendedor con el permiso vería la empresa entera, o el permiso significaría dos cosas según quién lo porte (`spec.md` §14.1) |
| **Resolver el vendedor en una segunda consulta**, como `findSellersOf` | Allí hace falta porque una venta tiene varios; aquí la fila tiene uno. Sería un `N+1` sin ganar nada |
| **`JOIN` en lugar de `LEFT JOIN` para el vendedor** | Haría desaparecer las líneas sin vendedor en lugar de publicarlas con nulo (`CA-MV-165`) |
| **Derivar el estado de entrega** como `RF-MV-014` | Repetiría aquí su máquina de estados, y dos copias divergen (`spec.md` §14.3) |
| **Publicar `typeStatus` en cada fila**, como `RF-MV-006` y `RF-MV-015` | Decisión del responsable del proyecto del 23-09-2026: se **filtra** por él y no se publica (`spec.md` §14.7). El `JOIN` se paga igual cuando el filtro viene, y la fila no crece para responder una pregunta que este listado no hace |
| **Resolver el estado del tipo con una subconsulta en el `WHERE`** en lugar del `JOIN` | Ahorraría el `JOIN` cuando el filtro no viene, y a cambio el predicado quedaría escrito distinto que en los otros dos listados. La consistencia de la casa gana: `filtro.igual("mts.code", …)` es lo que ya hacen `RF-MV-006` y `RF-MV-015` |
| **Conteo exacto** | `movement_details` es la tabla que más crece; un `count(*)` sin techo se paga en cada página |
| **Un índice para el filtro por producto** | El filtro menos frecuente de siete, y el índice se paga en cada venta registrada (§2). Se deja declarado por si el uso lo desmiente |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Que el `LEFT JOIN` se escriba como `JOIN` en una refactorización | Alto: filas que desaparecen sin error | `CA-MV-165`, con una línea sin vendedor en el fixture |
| Que la paginación repita o salte filas | Medio | Desempate determinista en el orden (§1), verificado por `CA-MV-163` |
| Que alguien resuelva el producto o el vendedor por fila | Medio — `N+1` en la tabla que más crece | `CA-MV-178` cuenta sentencias con las estadísticas de Hibernate |
| Que el total sea exacto y cueste en el libro grande | Bajo | `BoundedCount` con el techo del sistema, y `CA-MV-175` comprueba las dos caras |
| Que la guarda de `V37` falle por el orden de integración | Bajo, y **declarado** | §8, acordado con la sesión de `RF-MV-016` |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-MV-163`, `CA-MV-164` | API (`SaleLinesIT`) | La fila por línea, su contenido y el orden con desempate |
| `CA-MV-165` | Integración (`SaleLinesIT`) | La línea sin vendedor **sale**, con `seller` nulo |
| `CA-MV-166` a `CA-MV-172` | API (`SaleLinesIT`) | Los siete filtros, uno a uno, con el semiabierto y la caja |
| `CA-MV-173` | API (`SaleLinesIT`) | La combinación, y la vacía con `200` |
| `CA-MV-174` | API (`SaleLinesIT`) | Los cinco `400`, **juntos** en una sola respuesta |
| `CA-MV-175` | Integración (`SaleLinesIT`) | El techo: por encima, el total es el techo y `totalIsExact` es `false` |
| `CA-MV-176` | API + `EndpointPermissionsIT` | El `403` con los tres permisos vecinos puestos |
| `CA-MV-177` | Integración (`SaleLinesPermissionSeedIT`) | La siembra de `V37` y el catálogo en 135 |
| `CA-MV-178` | Integración (`SaleLinesIT`) | Dos sentencias con una fila y dos con veinte |
| `CA-MV-179` | Integración (`SaleLinesIT`) | Una línea de un movimiento que no es venta **no sale** — se siembra a mano, porque hoy no hay otro tipo |
| `CA-MV-180` | API (`SaleLinesIT`) | El filtro por estado del tipo, y combinado con otro |
| `CA-MV-181` | API (`SaleLinesIT`) | El `400` del estado del tipo desconocido, **con los demás en la misma respuesta** |

**El fixture**: dos ventas confirmadas con dos y tres líneas, una anulada con una, una línea **sin vendedor**, dos vendedores distintos en la misma venta, dos productos, dos sujetos, y una línea de un movimiento de otro tipo sembrado a mano para `CA-MV-179`.

**Desde 0.2.0 el fixture declara el estado del tipo de cada venta**, que `V36` hizo `NOT NULL`: se resuelve **por código** y no por identificador literal, y el movimiento de otro tipo de `CA-MV-179` necesita **el suyo**, porque la clave ajena de `movements` es compuesta —`(type_status_id, movement_type_id)`— y un estado de otro tipo no vale. Para `CA-MV-180` una de las ventas queda en `VALIDAR_COMISIONES` y la otra en `VALIDADO`.
