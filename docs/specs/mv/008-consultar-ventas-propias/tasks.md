# TASKS — `RF-MV-008` Consultar los movimientos propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-008` |
| Especificación | [`spec.md`](spec.md) v0.3.0 |
| Plan | [`plan.md`](plan.md) v0.3.0 |
| `plan.md` aprobado el | 05-09-2026 |
| Estado | **En revisión** — `T-01` a `T-12` `Hecha`; `T-13` a `T-16` `Hecha` el 16-09-2026 (§1.1); `T-17` a `T-20` (§1.2) `Hecha` el 21-09-2026; `T-21` a `T-23` (§1.3) `Hecha` el 21-09-2026 |
| Issue | La enmienda del 21-09-2026, [#76](https://github.com/NexusPro-Dev/backend/issues/76) |
| Rama | `feature/venta-de-productos`; la enmienda del 21-09-2026, en `feature/filtro-por-tipo-de-movimiento` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V58`**: `ix_movements_client` e `ix_movements_seller`, este último **parcial** sobre `seller_id IS NOT NULL` | — | Los dos existen y el segundo lleva su `WHERE`. Una venta sin vendedor no ocupa sitio en él | **Hecha** — 05-09-2026 |
| `T-02` | `MovementRole` con `BUYER`, `SELLER` y `BOTH` | — | El contrato publicado **enumera** los tres, no dice «texto» | **Hecha** — 05-09-2026 |
| `T-03` | `MyMovementsRequest` — página, tamaño y estado. **Sin identificador de persona** | — | El registro no tiene ningún campo por el que decir sobre quién se pregunta | **Hecha** — 05-09-2026 |
| `T-04` | `MyMovementResponse` — la fila, con `role` y las dos partes; `seller` nulable declarado con `types` | `T-02` | El contrato dice que `seller` puede ser nulo. **Con `nullable` no lo diría**, y no fallaría nada | **Hecha** — 05-09-2026 |
| `T-05` | `MovementRepository`: `findMine`, `countMine` y `findMineById` | `T-03` | Las tres llevan el alcance **dentro de la sentencia**. Ninguna admite un identificador de persona distinto del actor | **Hecha** — 05-09-2026 |
| `T-06` | `JpaMovementRepository`: las tres sentencias, con el `CASE` que resuelve el papel | `T-05` | El `CASE` cubre **los tres** casos. El de `BOTH` es el que se olvida | **Hecha** — 05-09-2026 |
| `T-07` | `ListMyMovementsService` y `GetMyMovementService` | `T-06` | Resuelven el actor con `AuthenticatedActor`. El detalle ajeno sale como no encontrado, no como prohibido | **Hecha** — 05-09-2026 |
| `T-08` | `MovementController`: los dos `GET`, con `/mine` **antes** de cualquier variable de ruta | `T-07` | Documentados, sin `@PreAuthorize` | **Hecha** — 05-09-2026 |
| `T-09` | Las dos rutas entran en la lista blanca de `EndpointPermissionsIT` | `T-08` | La ausencia de permiso queda **declarada**, no parece un olvido | **Hecha** — 05-09-2026 |
| `T-10` | `MyMovementsIT`: los trece criterios de aceptación | `T-08` | Incluida la que importa — `CA-MV-038`, **con `movements:read` puesto** | **Hecha** — 05-09-2026 |
| `T-11` | Prueba de que `/mine` no lo captura una variable de ruta | `T-08` | Hoy no hay `/{id}` en este controlador; la prueba existe para el día que `RF-MV-007` lo traiga | **Hecha** — 05-09-2026 |
| `T-12` | Regenerar el contrato OpenAPI y actualizar `requirements/mv.md` y la matriz de `requirements.md` | `T-10` | `RF-MV-008` deja de estar en `Pendiente` | **Hecha** — 05-09-2026 |

---

### 1.1 «Lo que vendí» se responde por las líneas — 16-09-2026

Enmienda del Art. I.7 sobre este requerimiento ya construido, por decisión del responsable del proyecto (`requirements/mv.md` v0.16.0, `plan.md` §2.1). La migración es la de `RF-MV-001` · `T-25` (`V12`) y no se repite aquí.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-13` | `JpaMovementRepository`: `findMine`, `countMine` y `findMineById` pasan de `client_id = ? OR seller_id = ?` a `user_id = ? OR EXISTS (… movement_details … seller_id = ?)`, y el `CASE` del papel con el mismo `EXISTS` | `RF-MV-001` · `T-25` | Una venta con dos líneas del mismo vendedor **cuenta una vez** (`CA-MV-040`); el `BOTH` sale para quien se vende a sí mismo (`CA-MV-037`) | **Hecha** — 16-09-2026 |
| `T-14` | `MyMovementResponse`: `client` → `user`; `seller` → `sellers`, lista **nunca nula** y sin repetir, leída con una segunda consulta por los movimientos de la página | `T-13` | El contrato declara `sellers` como lista obligatoria; `user` sustituye a `client` | **Hecha** — 16-09-2026 |
| `T-15` | `MyMovementsIT`: rehacer los criterios que miraban `client` y `seller`, y añadir la venta de quien no cuelga de nadie con papel `BOTH` | `T-14` | `CA-MV-035` a `CA-MV-037` y `CA-MV-043` en verde con la forma nueva; `sellers` vacía **y presente** comprobada sobre el JSON en crudo | **Hecha** — 16-09-2026 |
| `T-16` | Contrato OpenAPI: esquema regenerado y **prosa reescrita** — `user` es el sujeto, `sellers` son los de sus líneas | `T-14` | Las `@Operation` de los dos `GET` no nombran `client` ni un `seller` de cabecera | **Hecha** — 16-09-2026 |

### 1.2 El filtro por tipo, y el tipo en la fila — 21-09-2026

Enmienda de hecho (Art. I.7), `spec.md` 0.3.0 y `plan.md` 0.3.0 **antes** del código, el mismo día que `RF-MV-006` · §1.1. Sin migración.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-17` | `MyMovementsRequest` gana `type`, normalizado a mayúsculas; `findMine` y `countMine` lo reciben y `SELECCION_PROPIA` une `movement_types` y lo compara con `mt.code` con la misma forma que el estado | — | Con `type` puesto, el total cuenta lo que la página devuelve | **Hecha** — 21-09-2026 |
| `T-18` | `MyMovementRow` y `MyMovementResponse` ganan `type`; `ListMyMovementsService` valida `type` contra el catálogo con `findTypeByCode` (`VAL-004`) | `T-17` | El campo sale en el JSON de cada fila | **Hecha** — 21-09-2026 |
| `T-19` | `MovementController`: el parámetro documentado en la `@Operation` de `GET /mine`; `MyMovementsIT`: `CA-MV-120` con un **segundo tipo sembrado en la prueba** y combinado con el estado, el inexistente, y `CA-MV-121` | `T-18` | La prueba deja `movement_types` como lo encontró | **Hecha** — 21-09-2026 |
| `T-20` | Contrato OpenAPI regenerado y prosa releída; `docs/api/index.md`; matriz de `requirements.md` | `T-19` | `openapi.json` declara `type` en el parámetro y en `MyMovementResponse` | **Hecha** — 21-09-2026 |

### 1.3 Método de pago, comprobante y periodo — 21-09-2026, segunda enmienda del día

Enmienda de hecho (Art. I.7), `spec.md` 0.4.0 y `plan.md` 0.4.0 **antes** del código, por decisión del responsable del proyecto: los tres filtros de `RF-MV-006` en todos los listados. Sin migración.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-21` | `MyMovementsRequest` gana `paymentMethodId`, `code` (a mayúsculas), `from` y `to`; `findMine` y `countMine` los reciben y `SELECCION_PROPIA` los aplica con la forma `CAST(:x) IS NULL OR …`; `ListMyMovementsService` valida el rango (`VAL-005`) | — | Página y conteo sobre la misma sentencia; un código ajeno no devuelve nada | **Hecha** — 21-09-2026 (con la desviación de §3) |
| `T-22` | `MovementController`: los cuatro parámetros documentados en `GET /mine`; `MyMovementsIT`: `CA-MV-133` a `CA-MV-135`, con el comprobante ajeno | `T-21` | El `400` del rango invertido y el `VAL-006` del identificador malformado | **Hecha** — 21-09-2026 (`MyMovementsIT`, 22) |
| `T-23` | Contrato regenerado y prosa releída; `docs/api/index.md`; matriz de `requirements.md` | `T-22` | `openapi.json` declara los cuatro en `GET /api/v1/movements/mine` | **Hecha** — 21-09-2026 |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-035`, `CA-MV-036`, `CA-MV-037` | `T-06`, `T-10`, `T-13`, `T-15` — rehechos el 16-09-2026 con el vendedor en la línea; `MyMovementsIT` gana la venta con dos líneas del mismo vendedor, que cuenta una vez |
| `CA-MV-038` | `T-05`, `T-10` |
| `CA-MV-039`, `CA-MV-040`, `CA-MV-041` | `T-07`, `T-10` |
| `CA-MV-042` | `T-05`, `T-10` |
| `CA-MV-043` | `T-04`, `T-10`, `T-14`, `T-15` — rehecho el 16-09-2026: `user` y `sellers`, con la lista vacía comprobada sobre el JSON en crudo |
| `CA-MV-044`, `CA-MV-045` | `T-07`, `T-10` |
| `CA-MV-046`, `CA-MV-047` | `T-09`, `T-10` |
| `CA-MV-120`, `CA-MV-121` | `T-17`, `T-18`, `T-19` — 21-09-2026 |
| `CA-MV-133`, `CA-MV-134`, `CA-MV-135` | `T-21`, `T-22` — 21-09-2026 |

---

## 3. Desviaciones respecto del plan

**Los tres filtros del 21-09-2026 (§1.3) no entran en `SELECCION_PROPIA` con la forma `CAST(:x) IS NULL OR …` que `plan.md` §4.3 había escrito, sino con la clase `Filtro` del listado global**, añadida a esa sentencia: un identificador o un instante nulos enlazados sin tipo son lo que PostgreSQL no sabe convertir —el motivo por el que `RF-MV-006` armó su predicado por partes—, y la forma del `CAST` solo se había probado con cadenas. El estado y el tipo se quedan como estaban. `findMine` y `countMine` reciben un `MyMovementsFilter` en lugar de seis parámetros sueltos. Y el identificador malformado lo emite el conversor global como `VAL-001`, no como el `VAL-006` de la spec: es la discrepancia que `requirements.md` v0.43.0 dejó declarada para todos los listados.

**El `CASE` del papel se calcula en SQL y no en Java**, que es lo que `plan.md` §3 sugiere sin decirlo. El motivo apareció al escribirlo: hacerlo en Java obliga a que la fila cargue los dos identificadores solo para compararlos y descartarlos, y a que el mapeo conozca quién pregunta. En SQL, el parámetro ya está atado a la consulta.

**El detalle no reutiliza la consulta del listado.** Son dos sentencias distintas porque devuelven cosas distintas —una trae líneas y la otra no—, y fundirlas obligaría a traer las líneas siempre.

---

## 4. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los trece criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado.
- [x] `requirements/mv.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
