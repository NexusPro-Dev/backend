# TASKS — `RF-MV-007` Consultar el detalle de un movimiento, con su comprobante

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-007` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** — `T-01` a `T-07` `Hecha` el 30-09-2026 |
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

## 2. Orden de ejecución

`T-05` y `T-06` → `T-01` → `T-02` → `T-03` → `T-04`; `T-07` con `T-05`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-287`, `CA-MV-288`, `CA-MV-289` | `T-01`, `T-03`, `T-06` |
| `CA-MV-290` | `T-01`, `T-03` |
| `CA-MV-291` | `T-02`, `T-03` |
| `CA-MV-292` | `T-02`, `T-03`, `T-04`, `T-05` |

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
