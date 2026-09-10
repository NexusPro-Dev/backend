# TASKS — `RF-SP-055` Consultar las cuentas de broker de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-055` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 10-09-2026 |
| Estado | **En revisión** — `T-01` a `T-13` **Hecha** el 10-09-2026, con `mvn verify` en **1162 pruebas verdes** |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V80__user_brokers_status.sql`: la columna con `NOT NULL DEFAULT 'REGISTER'` y `ck_user_brokers_status` | — | Las filas ya declaradas quedan en `REGISTER`, que es su valor **cierto** | **Hecha el 10-09-2026** |
| `T-02` | Migración `V81__seed_broker_accounts_permission.sql`: `broker-accounts:read` con UUID literal, asociado a `SUPERADMIN` y `ADMIN`, con la guarda que aborta si falta una fila | — | El catálogo de permisos pasa de 44 a **cuarenta y cinco** | **Hecha el 10-09-2026** |
| `T-03` | `UserBrokerStatus` en `brokers/domain/models`, con el porqué del inglés escrito en el Javadoc | `T-01` | Los dos valores coinciden **exactamente** con los del `CHECK` | **Hecha el 10-09-2026** |
| `T-04` | `BrokerAccountQueryRepository` (puerto) y su implementación nativa: las cuentas de una persona, con su broker, ordenadas por nombre de broker e identificador de cuenta | `T-03` | `CA-SP-630`, y el orden de §6.2 de la spec | **Hecha el 10-09-2026** |
| `T-05` | `BrokerAccountItem` y `BrokerAccountsResponse` en `brokers/application` | `T-04` | `accountId`, no `externalId`; `brokerUsername` **presente aunque nulo** | **Hecha el 10-09-2026** |
| `T-06` | `GetBrokerAccountsService`: la autorización de dos pasos —permiso, o superior **vigente**— y el `404` común | `T-05` | `CA-SP-629`, `CA-SP-633`, `CA-SP-634`, `CA-SP-635` | **Hecha el 10-09-2026** |
| `T-07` | `UserController`: `GET /api/v1/users/{id}/broker-accounts`, **sin `@PreAuthorize`** y con el motivo escrito al lado | `T-06` | La ausencia de anotación es deliberada (`plan.md` §5) | **Hecha el 10-09-2026** |
| `T-08` | Declarar la ruta en `EndpointPermissionsIT` como autenticada sin permiso, **con su motivo** | `T-07` | La lista cerrada de rutas sigue siendo exhaustiva | **Hecha el 10-09-2026** |
| `T-09` | Ampliar las cuatro listas cerradas del catálogo de permisos | `T-02` | Las cuatro cuentan **cuarenta y cinco** | **Hecha el 10-09-2026** |
| `T-10` | Prueba de esquema del `CHECK` y del `DEFAULT` | `T-01` | `CA-SP-631`, y un valor fuera del par se rechaza | **Hecha el 10-09-2026** |
| `T-11` | Pruebas de API de los criterios de `spec.md` §12, **incluida la comparación de los dos cuerpos del `404`** | `T-08` | `CA-SP-629` a `CA-SP-636` | **Hecha el 10-09-2026** |
| `T-12` | Documentación OpenAPI del endpoint: **prosa**, no solo esquema | `T-11` | Dice que el estado hoy es siempre `REGISTER` y **por qué**, y que el `404` cubre dos casos a propósito | **Hecha el 10-09-2026** |
| `T-13` | Actualizar la matriz de `docs/requirements.md` | `T-11` | La fila de `RF-SP-055` refleja el estado | **Hecha el 10-09-2026** |

## 2. Orden de ejecución

`T-01` y `T-02` primero: sin columna ni permiso no hay nada que leer ni con qué autorizar.

**`T-10` conviene escribirla justo después de `T-01`**, cuando todavía se recuerda que el `DEFAULT` es lo que hace ciertas las filas viejas.

**`T-11` es la que sostiene el requerimiento**, y de sus casos el que no puede faltar es la comparación de los dos cuerpos del `404`: es lo único que impide que mañana alguien lo convierta en `403` creyendo que mejora el mensaje.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-629`, `CA-SP-633` | `T-06`, `T-11` |
| `CA-SP-630`, `CA-SP-632` | `T-04`, `T-05`, `T-11` |
| `CA-SP-631` | `T-01`, `T-10`, `T-11` |
| `CA-SP-634`, `CA-SP-635` | `T-06`, `T-11` |
| `CA-SP-636` | `T-04`, `T-11` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **Nadie mueve la cuenta a `FIRST_DEPOSIT`.** Quien lo hará es el webhook de `RF-SP-054`, que sigue sin decidir autenticación, idempotencia ni qué hacer con una cuenta que nadie declaró. **No bloquea este requerimiento**: la columna se lee desde el primer día y su valor es cierto | 10-09-2026 | Responsable del proyecto | **Abierto** |
| 2 | **D-22 sigue abierta.** `RN-SP-046` no la resuelve: la excepción queda acotada a esta lectura y ninguna otra puede añadirse citándola | 10-09-2026 | Responsable del proyecto | **Abierto** |
| 3 | **Desvincular una cuenta sigue sin decidirse** (`RF-SP-053`), y por eso `user_brokers` no lleva `deleted_at`. No bloquea esta lectura | 08-09-2026 | Responsable del proyecto | **Abierto** |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] El endpoint consta en `EndpointPermissionsIT` con su motivo escrito al lado.
- [x] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [x] Documentación afectada actualizada en el mismo Pull Request.
- [x] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
