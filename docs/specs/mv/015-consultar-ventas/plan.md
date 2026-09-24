# PLAN — `RF-MV-015` Consultar las ventas de mi alcance

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-015` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 21-09-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Enmendado el | 21-09-2026 — `paymentMethodId` y `code` (§4.1, §11) |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 21-09-2026 |

!!! warning "Enmendado el 24-09-2026 — el filtro `code` busca por FRAGMENTO"

    `RN-MV-037` ([`requirements/mv.md`](../../../requirements/mv.md) v0.41.0), a petición del responsable del proyecto: «por si solo me sé una parte». El filtro `code` de las ventas del alcance **deja de exigir el comprobante entero** y pasa a devolver todo el que lo **contenga**, sin distinguir mayúsculas.

    **Es una ampliación y no un cambio de contrato**: el código completo sigue encontrando lo que encontraba, porque un comprobante se contiene a sí mismo. Lo que cambia para quien lo pinta es que la respuesta puede traer **más de una fila** donde antes traía como mucho una.

    **Tres cosas que NO cambian, y conviene que no se den por hechas.** `type` y `typeStatus` **siguen siendo exactos**: se eligen de un conjunto cerrado, no se teclean, y un `LIKE` ahí haría que pedir `VENTA` arrastrara cualquier tipo que la contenga. Los comodines `%` y `_` que escriba el usuario se **escapan** —son texto y no patrón—, que es la misma defensa que `RF-SP-025` ya tenía escrita. Y **el alcance no se ensancha**: va en la misma sentencia y **antes** que este predicado, de modo que quien solo ve lo suyo sigue viendo lo suyo.

    **Se indexa con trigramas** (`ix_movements_codigo_busqueda`, `V39`), como `ix_users_busqueda`: `uq_movements_code` no puede responder por un fragmento del medio —un B-tree solo responde por el principio— y sin el índice nuevo la consulta recorrería la tabla entera.

!!! info "Qué va en este documento"

    **Cómo se construye, y por qué así.** Tablas, clases, endpoints, transacciones, pruebas.

    Lo que decide el negocio está en `spec.md` y no se repite aquí.

---

## 1. Enfoque

**Es `RF-MV-006` con el alcance puesto por quien pregunta**, y las diferencias son dos: el predicado lleva **el alcance del actor** además de los filtros, y el tipo va fijo a `VENTA`. La fila, el orden, el conteo acotado, la segunda consulta de vendedores y la forma de armar el predicado **se reutilizan tal cual**; lo nuevo es de dónde sale el alcance y cómo entra en la sentencia.

**Y el alcance NO lo calcula `MV`: lo publica `SP`.** Quién manda a quién (`user_supervisors`) y de qué tipo es cada rol (`user_roles.role_type`) son de `SP`, y `modules.md` §7 prohíbe leer sus tablas desde otro módulo. Lo que decide **dónde** vive la definición de «mi red» es la regla 2 de `architecture.md` §15.2 —la regla se queda con su dueño—: `RF-SP-057` ya recorre el subárbol para las cuentas de broker, y una segunda copia de ese recorrido en `MV` divergiría sin fallar. **`SP` publica `CommercialReach`**, «hasta dónde llega esta persona», y `MV` lo aplica. Es el resolvedor de `ADR-005` opción B con **un** tipo de alcance; la comprobación de arquitectura que aquel ADR ponía antes queda pendiente y declarada (§10).

**Lo que se prueba por HTTP es la profundidad y la frontera entre ramas**, y eso decide la forma de la semilla de la prueba: un árbol con dos directores bajo un manager y agentes bajo cada uno, con **una venta distinta por vendedor**, para que cada nivel vea exactamente el conjunto que le toca y ningún error de recorrido dé un número plausible.

---

## 2. Cambios de esquema

**Ninguna tabla, columna ni índice.** El recorrido lo responde `ix_user_supervisors_supervisor_vigente` (`V4`), que es el que `RF-SP-057` ya usa; el vendedor de la línea, `ix_movement_details_seller` (`V12`); el orden, `ix_movements_occurred_at` (`V15`).

**Una migración de datos, `V32__mv_ventas_de_mi_alcance.sql`**: siembra `movements:list-sales` con literal de la serie de `MV` —`01a0c143-2c00-700c-9c4f-5e7ad7000008`, la marca del 21-09-2026 continuando la secuencia de `V31`— y lo da **a todo rol por su tipo**, los tres tipos, con `ON CONFLICT`: es la vista de ventas de cualquiera, y el alcance lo pone `RN-MV-031`, no el reparto. Guardas: **125** en el catálogo, `SUPERADMIN` 125, `ADMIN` 119, cero parejas que rompan `RN-SEG-003`. Sin auditoría, como `V31`.

---

## 3. Componentes afectados

### 3.1 En `SP` — el alcance

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `users/application` | `CommercialReach` | **Nuevo** | La interfaz publicada: `Reach reachOf(UUID actorId)`. `Reach` es un registro `(Kind kind, Set<UUID> sellers)` con `Kind` = `EVERYTHING` \| `NETWORK` \| `OWN`; `sellers` solo tiene contenido en `NETWORK` y **contiene al actor** |
| `users/domain/repository` | `JpaCommercialReach` | **Nuevo** | Dos sentencias nativas: los **tipos de rol vivos** del actor —`user_roles` × `roles` con `deleted_at` nulo y `status = 'ACTIVO'`, el mismo predicado de `JpaEffectivePermissions`— y, si es vendedor, **el subárbol** con la `WITH RECURSIVE` de `RF-SP-057` (relación vigente, `UNION` y no `UNION ALL`), más el propio actor |

**Precedencia en Java, no en SQL**: `FUNCIONARIO` → `EVERYTHING`; si no, `VENDEDOR` → `NETWORK`; si no, `OWN`. Es la lista de `RN-MV-031` escrita una vez.

**Devuelve identificadores y no una sentencia**, y es una decisión con precio: una red de miles de vendedores viaja como conjunto y entra en un `IN (…)`. Se acepta porque hoy la fuerza comercial se cuenta en decenas, porque la alternativa —que `SP` publique un fragmento SQL o que `MV` lea `user_supervisors`— rompe la frontera que §15.2 sostiene, y porque el día que el conjunto pese, la salida es un puerto que **aplique** el predicado, no una copia del recorrido en `MV` (§10).

### 3.2 En `MV` — la consulta

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `ListSalesRequest` | Nuevo | Página, tamaño, `userId`, `status`, `from`, `to`. Estado normalizado a mayúsculas |
| `domain/repository` | `MovementRepository` | Modificado | Gana `findSales` y `countSales` sobre `SalesFilter(Reach, UUID sellerId, status, from, to)`; reutiliza `MovementRow`, `findSellersOf` y `BoundedCount` |
| `domain/repository` | `JpaMovementRepository` | Modificado | **Un** predicado para página y conteo, con la misma clase `Filtro`: `mt.code = 'VENTA'`, el alcance (§4.4) y los filtros |
| `domain/service` | `ListSalesService` | Nuevo | Valida junto, pide el alcance a `CommercialReach`, **corta antes de consultar** cuando el `userId` está fuera del alcance (§4.4), pagina, cuenta acotado y mapea a `MovementResponse` |
| `interfaces` | `MovementController` | Modificado | `GET /api/v1/movements/sales`, con `@PreAuthorize("hasAuthority('movements:list-sales')")` |
| `db/migration` | `V32` | Nueva | §2 |

**`MovementResponse` se reutiliza, no se copia.** La spec §6.2 decidió que la fila es la de `RF-MV-006`; un registro nuevo con los mismos campos sería la segunda forma de la misma venta.

**`ListSalesService` y no un parámetro de alcance en `ListMovementsService`.** Es el argumento de `RF-MV-006` §9 al revés: allí se descartó un `/mine` con «todos» porque mezclaba dos modelos de seguridad; aquí se descarta un `/movements` con «solo mi alcance» por lo mismo. Comparten repositorio, fila y mapeo —que es lo que no es contrato— y no el caso de uso.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/sales` | `movements:list-sales` |

**`/sales` es un literal bajo `/movements`, como `/mine`**: la raíz es la lectura de administración, `/mine` la propia, `/sales` la de mi alcance para las ventas. Los tipos futuros serán `/deposits`, `/commissions`… cada uno con su permiso (`spec.md` §2.1). El día que `RF-MV-007` traiga `/{id}`, Spring resuelve el literal antes que la variable, como ya ocurre con `/mine`.

### 4.1 Parámetros

| Parámetro | Tipo | Nota |
|---|---|---|
| `page`, `size` | | `Pagination`, como todo listado |
| `userId` | UUID | Vendedor de **alguna línea**, **dentro del alcance**. Fuera de él, o inexistente: página vacía, **sin consultar** |
| `status` | `PENDIENTE` \| `CONFIRMADA` \| `RECHAZADA` \| `ANULADA` | Uno no admitido es `400` `VAL-002`, contra `MovementStatus` |
| `from`, `to` | instante ISO-8601 con zona | Semiabierto sobre `occurred_at`; `from` posterior a `to` es `400` `VAL-004` |
| `paymentMethodId` (21-09-2026) | UUID | Igualdad; uno inexistente da página vacía. Entra en `filtroDeVentas` con `Filtro.igual`, como en `filtroGlobal` |
| `code` (21-09-2026) | texto | En mayúsculas, igualdad sobre `uq_movements_code`. **Después** del alcance en el mismo predicado: un comprobante ajeno no devuelve nada |

**No hay `type`**: el tipo es el de la ruta. **No hay `sellerId` ni `userId` con dos sentidos**: `userId` es el nombre que el responsable usó y significa **la persona de mi red como vendedora**; el sujeto no se filtra aquí (`spec.md` §2.2).

### 4.2 La respuesta

`PageResponse<MovementResponse>`, exactamente la de `GET /api/v1/movements`: `id`, `code`, `type` (siempre `VENTA`), `status`, `user`, `sellers`, `currency`, `paymentMethod`, los tres importes, `occurredAt`, `confirmedAt` nulo y presente, y `totalIsExact`. Ninguna forma nueva en el contrato.

### 4.3 Códigos de respuesta

| Código | Cuándo |
|---|---|
| `200` | La página, aunque esté vacía — también cuando `userId` está fuera del alcance |
| `400` | Paginación, estado, identificador o rango inválidos |
| `401` | Sin token |
| `403` | Sin `movements:list-sales`. **Ni `movements:read` ni `movements:list-own` lo sustituyen** |

### 4.4 Cómo entra el alcance en la sentencia

| `Reach.kind` | Predicado de alcance | Con `userId` |
|---|---|---|
| `EVERYTHING` | ninguno | `EXISTS (movement_details d … d.seller_id = :vendedor)` |
| `NETWORK` | `EXISTS (movement_details d WHERE d.movement_id = m.id AND d.seller_id IN (:red))` | si `userId ∈ red`, el `EXISTS` con `= :vendedor` en lugar del `IN`; si no, **vacío sin consultar** |
| `OWN` | `m.user_id = :actor` | si `userId = actor`, lo mismo; si no, **vacío sin consultar** |

**El corte «sin consultar» no es una optimización: es la regla.** Fuera del alcance la respuesta es vacía **por definición**, y no porque la sentencia no encuentre filas; escribirlo así deja el oráculo cerrado aunque alguien cambie el predicado. Y el `EXISTS` deja **una fila por venta** tenga las líneas que tenga (`CA-MV-131`), como en `RF-MV-006` y `RF-MV-008`.

---

## 5. Autorización

**El permiso abre; el alcance decide qué se ve.** `@PreAuthorize` con `movements:list-sales` es la puerta —`RN-SEG-015`: toda operación con token exige permiso— y `CommercialReach` es lo que hace que un director y un manager, **con el mismo permiso**, vean conjuntos distintos. Es exactamente la situación que `security.md` §6 describía como la que D-22 tenía que resolver, resuelta para un dato.

**No se usa `hasAnyAuthority` con `movements:read`.** Quien administra tiene los dos permisos y entra por cualquiera de las dos rutas; darle esta por `movements:read` rompería `RN-SEG-014` en silencio (`CA-MV-130`).

**La ruta entra en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`** con su código; sin eso la suite falla, y es lo que se quiere.

---

## 6. Auditoría

Ninguna (`spec.md` §7).

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)` en el caso de uso, que envuelve la resolución del alcance y las tres sentencias —página, conteo y vendedores— para que describan el mismo instante.

---

## 8. Impacto sobre otros módulos

| Documento | Cambio |
|---|---|
| `requirements/mv.md` | v0.32.0: `RF-MV-015` en §4.1 con su ficha, `RN-MV-031` en §5.1, §5.3 deja de aplazar la pregunta, `movements:list-sales` en §6 — **hecho** |
| `security.md` | v0.68.0: §4.4 con el permiso (125) y la actualización de D-22 en §6 — **hecho** |
| `architecture.md` | v0.34.0: `CommercialReach` en la tabla de §15.2 — **hecho** |
| `architecture/ADR-005` | Actualización del 21-09-2026: las preguntas de negocio respondidas; la comprobación pendiente — **hecho** |
| `requirements/sp.md` | v1.70.0: §8 anota la interfaz publicada — **hecho** |
| `requirements.md` | v0.187.0: fila, indicadores y control — **hecho** |
| `docs/api/index.md` | Al construir: la ruta nueva y el permiso |

**`CM` hereda una pregunta**: cuando las comisiones se consulten, «las de mi red» tendrán que declarar su alcance, y `CommercialReach` ya responde la mitad. No se decide aquí.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un parámetro «solo mi alcance» sobre `GET /movements` | Dos modelos de seguridad en una ruta (`RF-MV-006` §9); el permiso de administración abriría además el alcance |
| Una ruta con el tipo en el camino, `/movements/by-type/{type}` | Decisión del responsable: cada tipo tendrá su alcance y su permiso, y una ruta única los haría depender del parámetro |
| Que `MV` recorra `user_supervisors` con su propia `WITH RECURSIVE` | Segunda definición de «mi red», divergente de `RF-SP-057` sin fallar; y `modules.md` §7 lo prohíbe |
| Que `SP` publique un fragmento SQL | Ataría `MV` al esquema de `SP` por la puerta de atrás, que es lo que §15.2 existe para evitar |
| `403` o `404` cuando `userId` no es de mi red | Oráculo de la estructura: probando identificadores se reconstruye quién cuelga de quién (`spec.md` §10) |
| Un solo nivel, como `RN-SP-046` | No responde la pregunta: el manager no vería nada. Decisión del responsable, escrita en `RN-MV-031` |
| Conteo exacto | Para `EVERYTHING` es la tabla entera; para una red grande, casi. Es la forma de `RF-MV-006` |
| Una fila propia (`SaleResponse`-like) | La misma venta con dos formas; `spec.md` §6.2 lo descarta |
| Resolver el tipo de rol desde los permisos del token | Los permisos no dicen el tipo de rol, y deducirlo de ellos sería reconstruir en `MV` una autorización que es de `SP` |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que una red grande produzca un `IN (…)` con miles de identificadores | Hoy son decenas. **Disparador de revisión**: si el conjunto pasa de unos pocos miles, `SP` publica un puerto que **aplique** el alcance (una `EXISTS` sobre una tabla temporal o un tipo compuesto), no una copia del recorrido |
| Que el siguiente listado con alcance lo resuelva por su cuenta | Es el riesgo que `ADR-005` señala y que su comprobación de arquitectura cerraría; **sigue pendiente**, declarado en `security.md` §6 y en el ADR |
| Un ciclo en `user_supervisors`, aunque `RN-SP-020` lo prohíba | `UNION` y no `UNION ALL`: el recorrido termina (`spec.md` §13) |
| Que la venta de quien dejó la red «desaparezca» y alguien lo lea como pérdida de datos | Está decidido y escrito (`FA-006`, `CA-MV-128`); la comisión devengada es de `CM` y no se toca |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Los tres tipos de rol: consumidor solo lo suyo; agente solo lo suyo; director él + agentes; manager toda la profundidad; funcionario todo | Integración, sobre un árbol con dos ramas y **una venta por vendedor** | La profundidad y la frontera entre ramas solo se ven con datos reales y por HTTP |
| `userId` dentro y fuera del alcance, inexistente, y para el consumidor | Integración | `CA-MV-127`: vacío, no error, y el corte sin consultar |
| Quien dejó la red (`ended_at` puesto) desaparece | Integración | `CA-MV-128` |
| Estado y periodo combinados con `userId`; errores juntos | Integración | `CA-MV-129` |
| Método de pago y código dentro del alcance; el comprobante ajeno vacío (21-09-2026) | Integración | `CA-MV-136` |
| `403` sin el permiso, con `movements:read` y con `movements:list-own`; `401` | Integración | `CA-MV-130`; y la ruta en `PERMISO_DE_CADA_OPERACION` |
| La fila es la de `RF-MV-006`, sin `role`; varias líneas del mismo vendedor cuentan una vez | Integración, sobre el JSON en crudo | `CA-MV-131` |
| Paginación, orden, y total acotado con el techo bajado | Integración | `CA-MV-132`, como `MovementsBoundedCountIT` |
| `CommercialReach`: los tres `Kind` y la precedencia; el subárbol con la raíz; un rol retirado o inactivo no cuenta | Integración en `SP` | Es la definición que se publica; se prueba donde vive |
| `V32`: 125, reparto a los tres tipos, guardas | `PermissionsSeedIT`, `PermissionIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT` | Los recuentos del catálogo |
| Contrato regenerado | `OpenApiContractIT` | Solo altas: la ruta y su permiso |
