# TASKS — `RF-MV-008` Consultar los movimientos propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-008` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 05-09-2026 |
| Estado | **En revisión** |
| Rama | `feature/venta-de-productos` |

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

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-035`, `CA-MV-036`, `CA-MV-037` | `T-06`, `T-10` |
| `CA-MV-038` | `T-05`, `T-10` |
| `CA-MV-039`, `CA-MV-040`, `CA-MV-041` | `T-07`, `T-10` |
| `CA-MV-042` | `T-05`, `T-10` |
| `CA-MV-043` | `T-04`, `T-10` |
| `CA-MV-044`, `CA-MV-045` | `T-07`, `T-10` |
| `CA-MV-046`, `CA-MV-047` | `T-09`, `T-10` |

---

## 3. Desviaciones respecto del plan

**El `CASE` del papel se calcula en SQL y no en Java**, que es lo que `plan.md` §3 sugiere sin decirlo. El motivo apareció al escribirlo: hacerlo en Java obliga a que la fila cargue los dos identificadores solo para compararlos y descartarlos, y a que el mapeo conozca quién pregunta. En SQL, el parámetro ya está atado a la consulta.

**El detalle no reutiliza la consulta del listado.** Son dos sentencias distintas porque devuelven cosas distintas —una trae líneas y la otra no—, y fundirlas obligaría a traer las líneas siempre.

---

## 4. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los trece criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado.
- [x] `requirements/mv.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
