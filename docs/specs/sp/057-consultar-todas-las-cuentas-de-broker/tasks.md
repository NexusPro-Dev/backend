# TASKS — `RF-SP-057` Consultar y filtrar todas las cuentas de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-057` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 10-09-2026 |
| Estado | **En revisión** — `T-01` a `T-14` **Hecha** el 10-09-2026, con `mvn verify` en **1193 pruebas verdes**. Enmendado el mismo día: la respuesta gana su resumen |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V82__user_brokers_search_index.sql`: gin de trigramas sobre `f_unaccent(lower(external_id))`, **con la expresión del predicado** | — | `CA-SP-653`, y el índice se usa de verdad | **Hecha el 10-09-2026** |
| `T-02` | `ListBrokerAccountsRequest`: los siete filtros, con el recorte de `search` y el `Integer` de la paginación | — | Los nombres son `search`, `from` y `to` — los del sistema | **Hecha el 10-09-2026** |
| `T-03` | **La recursiva** en `BrokerAccountQueryRepository`: `UNION` sin `ALL`, `ended_at IS NULL` en **los dos brazos**, sin sembrar la raíz | `T-02` | `CA-SP-648`, `CA-SP-649`, `CA-SP-650` | **Hecha el 10-09-2026** |
| `T-04` | El predicado de los seis filtros restantes, **escrito una vez** y compartido por la página y el conteo | `T-03` | `CA-SP-652` a `CA-SP-656`. El `deleted_at` va **fuera** de la recursiva | **Hecha el 10-09-2026** |
| `T-05` | `ListBrokerAccountsService`: valida `status` y el rango, y pagina | `T-04` | `CA-SP-655` | **Hecha el 10-09-2026** |
| `T-06` | `BrokerAccountController`: `GET /api/v1/broker-accounts` con `hasAuthority('broker-accounts:read')` | `T-05` | `CA-SP-646`, `CA-SP-647` | **Hecha el 10-09-2026** |
| `T-07` | Pruebas de API de `spec.md` §12 | `T-06` | `CA-SP-646` a `CA-SP-657` | **Hecha el 10-09-2026** |
| `T-08` | **La prueba de la rama que no se corta**: una persona eliminada **con subordinados vivos** | `T-07` | `CA-SP-651`. Es la que distingue filtrar de podar | **Hecha el 10-09-2026** |
| `T-09` | Documentación OpenAPI: **prosa**, y el `200` **sin `@Schema(implementation)`** | `T-07` | Se comprueba **leyendo `docs/api/openapi.json`**: la fila tiene que salir tipada | **Hecha el 10-09-2026** |
| `T-10` | Actualizar la matriz de `docs/requirements.md` | `T-07` | La fila de `RF-SP-057` refleja el estado | **Hecha el 10-09-2026** |
| `T-11` | **El resumen**: una consulta agrupada por `(broker, estado)` sobre el mismo predicado, que **sustituye a `countAll`** | `T-04` | `CA-SP-669`, `CA-SP-670`. `totalElements` sale de ella | **Hecha el 10-09-2026** |
| `T-12` | `BrokerAccountsPage` y `BrokerAccountsSummary`, con el desglose ordenado por nombre de broker | `T-11` | `CA-SP-671`, `CA-SP-674` | **Hecha el 10-09-2026** |
| `T-13` | Pruebas del resumen, **incluida la del cero que engaña**: `?status=REGISTER` deja `firstDeposit` en cero | `T-12` | `CA-SP-669` a `CA-SP-674` | **Hecha el 10-09-2026** |
| `T-14` | Prosa OpenAPI del resumen, **con la frase del cero** | `T-13` | Dice que ese cero significa «no pediste ninguno» | **Hecha el 10-09-2026** |

## 2. Orden de ejecución

`T-01` y `T-02` primero, que no dependen de nada.

**`T-03` es la tarea del requerimiento**, y conviene escribir sus tres pruebas —nieto, raíz, ex-miembro— **antes de dar por buena la consulta**: las tres formas de equivocarla devuelven resultados plausibles.

**`T-08` va separada de `T-07` a propósito.** Es la única que distingue **filtrar** de **podar**, y metida entre las demás se lee como una repetición de `CA-SP-650`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-646`, `CA-SP-647` | `T-06`, `T-07` |
| `CA-SP-648`, `CA-SP-649`, `CA-SP-650` | `T-03`, `T-07` |
| `CA-SP-651` | `T-04`, `T-08` |
| `CA-SP-652` a `CA-SP-654` | `T-01`, `T-04`, `T-07` |
| `CA-SP-655` | `T-05`, `T-07` |
| `CA-SP-656`, `CA-SP-657` | `T-04`, `T-07`, `T-09` |
| `CA-SP-669`, `CA-SP-670` | `T-11`, `T-13` |
| `CA-SP-671`, `CA-SP-674` | `T-12`, `T-13` |
| `CA-SP-672`, `CA-SP-673` | `T-11`, `T-13`, `T-14` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **Nadie mueve la cuenta a `FIRST_DEPOSIT`** (`RF-SP-054`), de modo que el filtro por estado se prueba **sembrando el valor en la base**. No bloquea | 10-09-2026 | Responsable del proyecto | **Abierto** |
| 2 | **D-22 sigue abierta**, y este requerimiento **no la toca**: filtra por estructura, no se autoriza por ella | 10-09-2026 | Responsable del proyecto | **Abierto** |
| 3 | **Otras seis rutas publican la envoltura de página sin tipo** —los cuatro listados de auditoría, `GET /api/v1/roles` y `GET /api/v1/users`—. Es anterior a esta tanda y **no se arregla aquí** | 10-09-2026 | Responsable del proyecto | **Abierto** |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**, y **la fila sale tipada**.
- [x] Documentación afectada actualizada en el mismo Pull Request.
- [x] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
