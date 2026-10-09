# TASKS — `RF-MV-007` Consultar el detalle de un movimiento, con su comprobante

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-007` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** — `T-01` a `T-07` `Hecha` el 30-09-2026 |
| Enmendadas | 01-10-2026 — `T-08` y `T-09` por **el destino del retiro** (`RN-MV-056`, §7) |
| Enmendadas | 09-10-2026 — `T-10` y `T-11` por **la oficina de cada línea** (`RN-MV-078`, §1.1), `Pendiente` |
| Issue | Pendiente de crear |
| Rama | `feature/detalle-de-movimiento` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `GetMovementService`: `findById` + `SaleDetailMapper.de`, `EX-001` si no existe; sin actor | — | Lo ajeno se abre | Hecha |
| `T-02` | `MovementController`: `GET /{id}` con `movements:read-detail` y la variable con forma de UUID, al final de la clase, con los códigos de `plan.md` §4 | `T-01`, `T-05` | Las rutas literales siguen respondiendo; `CA-MV-140` sigue en `404` | Hecha |
| `T-03` | `MovementDetailIT`: `CA-MV-287` a `CA-MV-292` | `T-02`, `T-06` | `CA-MV-290` compara el cuerpo entero | Hecha |
| `T-04` | `EndpointPermissionsIT`; contrato regenerado con la prosa releída; `requirements.md`, `requirements/mv.md`, `security.md`, `api/index.md` | `T-03` | | Hecha |
| `T-05` | **`V56`**: `movements:read-detail` a todo rol con `movements:read`, con sus guardas; los recuentos del catálogo a 163 y `ADMIN` a 161 | — | La migración aborta si un rol con el listado se queda sin el detalle | Hecha |
| `T-06` | **`SaleResponse.type`**: el mapper lo copia de la cabecera; registrar escribe `VENTA` | — | El detalle de un retiro dice `RETIRO` | Hecha |
| `T-07` | La enmienda de `spec.md` y `plan.md` a la 0.2.0 | `T-05` | | Hecha |

### 1.1 La oficina de cada línea — 09-10-2026

Enmienda de hecho (Art. I.7), `spec.md` 0.4.0 y `plan.md` 0.4.0 **antes** del código. Sin migración propia: la columna la trae `V95`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-10` | `SaleLineResponse.team` (`LineTeam`, de `RF-MV-006` `T-18`) con `types = {"object", "null"}`; `MovementLineRow` gana `teamId` y `teamName`; `findLinesOf` con `LEFT JOIN teams` en su misma sentencia; `SaleDetailMapper.lineas` arma el `team` | `V95`, `RF-MV-006` `T-18` | El detalle no gana sentencias; `CA-MV-525` de `RF-MV-008` sigue en verde | **Pendiente** |
| `T-11` | `MovementDetailIT`: `CA-MV-720`, con equipo propio limpiado al terminar, una línea con oficina, otra sin ella y el director del vendedor hoy en otro equipo; `CA-MV-290` sin cambios; contrato regenerado —`team` nulable en `SaleLineResponse`, **un** esquema `LineTeam`— y `docs/api/index.md` | `T-10` | Solo altas en el contrato | **Pendiente** |

## 2. Orden de ejecución

`T-05` y `T-06` → `T-01` → `T-02` → `T-03` → `T-04`; `T-07` con `T-05`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-287`, `CA-MV-288`, `CA-MV-289` | `T-01`, `T-03`, `T-06` |
| `CA-MV-290` | `T-01`, `T-03` |
| `CA-MV-291` | `T-02`, `T-03` |
| `CA-MV-292` | `T-02`, `T-03`, `T-04`, `T-05` |
| `CA-MV-720` | `T-10`, `T-11` — 09-10-2026 |

## 4. Bloqueos

Ninguno.

## 5. Desviaciones respecto del plan

**Tres cosas que la construcción cambió, las tres el 30-09-2026.**

- **El permiso** (`T-05`): el plan 0.1.0 reutilizaba `movements:read`, y `EndpointPermissionsIT` —`ningunPermisoGobiernaDosOperaciones`— lo rechazó en la primera ejecución. Nace `movements:read-detail` por `V56`; `spec.md` y `plan.md` pasan a la 0.2.0.
- **La ruta con forma de UUID** (`T-02`): `MyMovementsIT` —`CA-MV-140`— esperaba `404` en `GET /movements/mine` y recibió `400`, porque la variable nueva la capturaba.
- **El tipo en la respuesta** (`T-06`): el detalle no decía si un movimiento es una venta o un retiro. El plan no lo previó porque dio por hecho que `SaleResponse` lo traía.

**Y una de la prueba**: `MovementDetailIT` siembra la venta por SQL, como `ConfirmSaleIT`, y no por `POST /movements` como decía `plan.md` §11: la suite solo lee, y registrar por HTTP exige montar cliente, vendedores y catálogo que no afirman nada. El retiro sí sale por su ruta.

---

## 6. Definición de terminado

- [x] `./mvnw clean verify` en verde.
- [x] Los seis criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md`, `requirements/mv.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 7. El destino de un retiro — enmienda del 01-10-2026

Rama: `feature/cuentas-de-cobro`. Después de `RF-MV-019` `T-11` y `T-12`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-08` | `SaleResponse.withdrawalDestination` y su lectura en `SaleDetailMapper`, solo para `RETIRO` | `RF-MV-019` `T-12` | La venta no gana sentencias | **Hecha** — 01-10-2026 |
| `T-09` | `CA-MV-424` y `CA-MV-425` en `MovementDetailIT`; contrato regenerado | `T-08` | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 01-10-2026 |

**01-10-2026 — construido** (issue [#157](https://github.com/NexusPro-Dev/backend/issues/157)). El destino lo lee `WithdrawalDestinations.deRetiro`, que solo consulta si el tipo es `RETIRO`; `GetMovementService` y `GetMyMovementService` lo pasan al mapper. **La suite es `WithdrawalDestinationIT`** y no `MovementDetailIT`.
