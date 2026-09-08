# TASKS — `RF-SP-052` Consultar el catálogo de brokers

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-052` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 08-09-2026 |
| Estado | **Completada el 08-09-2026** — `T-01` a `T-13` **Hecha**. Se amplió el mismo día que se escribió: el catálogo se sembró y la ruta se abrió sin sesión. Lo que queda no es trabajo sino **decisiones** (§4) |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V73__create_brokers.sql`: la tabla y el **índice único funcional** sobre `f_unaccent(lower(name))` | — | `CA-SP-607`: dos nombres que solo se distinguen por caja o acento chocan | **Hecha el 08-09-2026** |
| `T-02` | Migración `V74__create_user_brokers.sql`: las dos claves foráneas y **`uq_user_brokers_cuenta` sobre `(broker_id, external_id)`** | `T-01` | `RN-SP-038`, y se prueba **por los dos lados** en `T-06` | **Hecha el 08-09-2026** |
| `T-03` | Migración `V75__seed_brokers_permission.sql`: `brokers:read` con UUID literal, asociado a `SUPERADMIN` y `ADMIN`, con la guarda que aborta si falta una fila | — | El catálogo de permisos pasa de 43 a **44** | **Hecha el 08-09-2026** |
| `T-04` | `domain/models/Broker`, `domain/repository/BrokerRepository`, `application/BrokerResponse` y `domain/service/ListBrokersService` | `T-01` | El orden por nombre lo fija la consulta (`CA-SP-605`) | **Hecha el 08-09-2026** |
| `T-05` | `interfaces/BrokerController`: `GET /api/v1/brokers` con `includeInactive` y **sin `@PreAuthorize`**, desde que la ruta es pública | `T-04` | `CA-SP-602`, `CA-SP-603`, `CA-SP-608` | **Hecha el 08-09-2026** |
| `T-06` | **La prueba de esquema de `RN-SP-038`, por los dos lados** | `T-02` | **Dos cuentas distintas de la misma persona en el mismo broker se ADMITEN**; la misma cuenta declarada por dos personas se **rechaza**. Una sola de las dos mitades no distingue este único del contrario | **Hecha el 08-09-2026** |
| `T-07` | Ampliar las cuatro listas cerradas del catálogo de permisos | `T-03` | Las cuatro cuentan **cuarenta y cuatro** | **Hecha el 08-09-2026** |
| `T-08` | Pruebas de API de los criterios de `spec.md` §12 | `T-05` | `CA-SP-602` a `CA-SP-605`, `CA-SP-607` y `CA-SP-608` | **Hecha el 08-09-2026** |
| `T-09` | Documentación OpenAPI del endpoint | `T-08` | La prosa dice que **el catálogo no se administra por API** y por qué | **Hecha el 08-09-2026** |
| `T-10` | Actualizar la matriz de `docs/requirements.md` | `T-08` | La fila de `RF-SP-052` refleja el estado | **Hecha el 08-09-2026** |
| `T-11` | **La siembra del catálogo** (`V76`): `IQOPTION`, `EXNOVA` y `EXOPTION`, con guarda que aborta si no quedan tres | `T-01` | El catálogo deja de estar vacío. Cierra el bloqueo 1 | **Hecha el 08-09-2026** |
| `T-12` | **Abrir la ruta**: entra en `CATALOGOS_PUBLICOS` de `SecurityConfig` —solo en `GET`— y en la lista de rutas sin permiso a propósito de `EndpointPermissionsIT` | `T-05` | `CA-SP-608`: responde sin token, y lo mismo con uno | **Hecha el 08-09-2026** |
| `T-13` | **La cota del límite de tasa** de los tres catálogos públicos: 120/min por origen y por catálogo | `T-12` | Son rutas públicas que consultan la base en cada llamada, como el refresco | **Hecha el 08-09-2026** |

## 2. Orden de ejecución

`T-01` a `T-03` primero: sin tablas ni permiso no hay nada que probar.

**`T-06` conviene escribirla justo después de `T-02`**, cuando todavía se recuerda por qué el único no es el que parece.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-602`, `CA-SP-603` | `T-05`, `T-08` |
| `CA-SP-604` | `T-05` — no hay más métodos que el `GET` |
| `CA-SP-605` | `T-04`, `T-08` |
| ~~`CA-SP-606`~~ | ~~`T-05`, `T-08`~~ — retirado el 08-09-2026 |
| `CA-SP-608` | `T-12` |
| `CA-SP-607` | `T-01`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | ~~**No hay lista de brokers que sembrar.**~~ | 08-09-2026 | Responsable del proyecto | **Cerrado el 08-09-2026** — `IQOPTION`, `EXNOVA` y `EXOPTION` (`V76`) |
| 2 | **Quién declara una cuenta de broker no está decidido** (`RF-SP-053`), de modo que `user_brokers` se crea **sin código que la escriba**. No bloquea este requerimiento | 08-09-2026 | Responsable del proyecto | **Abierto** |
| 3 | **El webhook del broker no tiene nada decidido** (`RF-SP-054`): ni autenticación, ni qué hacer con una cuenta que nadie declaró, ni idempotencia. Sería la segunda ruta pública del sistema y la primera que **escribe** | 08-09-2026 | Responsable del proyecto | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [x] El endpoint es **público a propósito**, y consta en `EndpointPermissionsIT` con su motivo escrito al lado.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
