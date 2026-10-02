# TASKS — `RF-MV-043` Consultar los pagos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-043` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/listado-de-pagos` — sale de `feature/stripe-tarjeta`, o de `develop` cuando la tarjeta esté mezclada (`plan.md` §10) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V64`**: `ix_payments_occurred_at` sobre `payments (occurred_at DESC, id DESC)`; `movements:list-payments` (`01a0ef9c-6800-701e-9c4f-5e7ad7000057`) a `SUPERADMIN` y `ADMIN`, con el bloque de comprobación de `V61` | `RF-MV-040` · `V62` | El índice existe con las dos columnas en ese orden; el permiso está en los dos roles y en ningún otro | Pendiente |
| `T-02` | `ListPaymentsRequest` — página, tamaño y los seis filtros; estado y tipo a mayúsculas | — | `type=venta` llega al repositorio como `VENTA` | Pendiente |
| `T-03` | `PaymentListItemResponse` con `@Schema(name = "PaymentListItem")`, el movimiento anidado y `PaymentResponse.Method`; nulables con `types`, `@JsonInclude(ALWAYS)`; sin `idempotencyKey` ni `points` | — | El contrato declara nulables los siete campos que lo son, y el esquema no se funde con `MovementPayment` | Pendiente |
| `T-04` | `PaymentRepository`: `PaymentFilter`, la fila de lectura, `findAll` y `countAll` (`BoundedCount`) | `T-02` | El puerto no conoce la petición HTTP | Pendiente |
| `T-05` | `JpaPaymentRepository`: parte de `payments` y une el movimiento; **un** predicado —la clase `Filtro`— para la página y el conteo; comprobante por fragmento con escape; rango semiabierto sobre `p.occurred_at`; conteo con `LIMIT techo + 1` | `T-04` | Página y total filtran igual; un movimiento con dos pagos da dos filas | Pendiente |
| `T-06` | `ListPaymentsService`: estado contra `PaymentStatus`, tipo con `findTypeByCode`, rango; **los tres juntos**; pagina, cuenta y mapea | `T-05` | Tres parámetros mal escritos, tres errores en una respuesta | Pendiente |
| `T-07` | `PaymentController`: `GET /api/v1/movements/payments` con `@PreAuthorize("hasAuthority('movements:list-payments')")`, documentado | `T-06`, `T-01` | **No** entra en la lista blanca de `EndpointPermissionsIT`; `GET /movements/{id}` sigue respondiendo igual | Pendiente |
| `T-08` | `PaymentsListIT`: `CA-MV-480` a `CA-MV-493` | `T-07` | `CA-MV-481` con un actor que **tiene** pagos propios; la incidencia sembrada con `UPDATE`; la forma de la fila sobre el JSON en crudo | Pendiente |
| `T-09` | `PaymentsBoundedCountIT`: `CA-MV-494` con `nexus.pagination.count-limit=3` | `T-07` | Cuatro pagos: total 3 e inexacto; dos: total 2 y exacto | Pendiente |
| `T-10` | Los recuentos del catálogo de 179 a **180** (`ADMIN` 178) —eran 181 y 182 antes de `V63`, la conciliación por el pago—: `PermissionIT`, `PermissionsSeedIT` —dos sitios y la lista aprobada—, `MovementsPermissionsSeedIT`, `TeamsPermissionsSeedIT`, `SaleLinesPermissionSeedIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`, `SystemRolesSeedIT` | `T-01` | `grep -rnE "\b181\b" src/test` no deja ninguno del catálogo | Pendiente |
| `T-11` | Regenerar el contrato OpenAPI y releer la prosa del `GET` nuevo; `docs/api/index.md` | `T-08` | `openapi.json` publica `PaymentListItem` y no toca `MovementPayment` | Pendiente |
| `T-12` | Estado de construcción: `requirements/mv.md` (§4.1, §4.7 y control de cambios), `security.md` (sembrado), `requirements.md` (fila e indicadores) | `T-11` | `RF-MV-043` dice **En desarrollo** en los dos catálogos | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-480`, `CA-MV-481` | `T-01`, `T-07`, `T-08` |
| `CA-MV-482` | `T-01`, `T-05`, `T-08` |
| `CA-MV-483` | `T-05`, `T-08` |
| `CA-MV-484` a `CA-MV-490` | `T-05`, `T-06`, `T-08` |
| `CA-MV-491`, `CA-MV-492`, `CA-MV-493` | `T-03`, `T-08` |
| `CA-MV-494` | `T-05`, `T-09` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los quince criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements/mv.md`, `security.md`, `modelo-datos.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
