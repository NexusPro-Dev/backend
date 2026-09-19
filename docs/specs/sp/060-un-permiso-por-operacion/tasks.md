# TASKS — `RF-SP-060` Un permiso por operación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-060` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 19-09-2026 |
| Estado | **En revisión** — `T-01` a `T-12`, `T-14` y `T-15` **Hecha** el 19-09-2026; `T-13` bloqueada por el bloque 4 de `AC` |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **Tramo 1**: `security.md` (`RN-SEG-014`, §4.4, 0.63.0), `requirements/sp.md` (§6.1, §9, fichas de `060` y `059`, 1.62.0), `requirements.md` (fila, §5, 0.178.0) | — | Cada documento con su fila de control y su versión | **Hecha el 19-09-2026** |
| `T-02` | Verificar `spec.md` §6.2 contra los controladores: cada operación existe con ese método y esa ruta, y cada número de requerimiento es el de la tabla §9 de su módulo | — | Noventa y siete operaciones con permiso hoy, más las seis del bloque 4 de `AC` | **Hecha el 19-09-2026** |
| `T-03` | Enmiendas a `requirements/pm.md` (§4, §6.1, §9 y catorce fichas) y `requirements/cm.md` (§4, §6), con fila de control | `T-02` | Los veinticinco permisos de `PM` y los diez de `CM` en su documento | **Hecha el 19-09-2026** |
| `T-04` | Enmiendas de Art. I.7 a las **treinta** tripletas de `SP`, `PM` y `CM` cuya operación cambia de permiso (las diecisiete que conservan su código no cambian): nota fechada en `spec.md` y `plan.md`, fila de control donde la spec la tiene, y el código nuevo en las líneas normativas —actor, precondiciones, `403`, criterios—; las líneas que hablan de la siembra original se conservan como historia | `T-03` | Ninguna línea normativa de esas tripletas nombra un permiso que su operación ya no exige | **Hecha el 19-09-2026** |
| `T-05` | Avisar a las sesiones de frontend con la tabla de `spec.md` §6.2 | `T-02` | Acuse en el hilo | **Hecha el 19-09-2026** — acuse de `frontend-4b`: registrado como R-44 y a la espera de que `V28` esté en rama compartida |
| `T-06` | `V28__sp_un_permiso_por_operacion.sql`: los cincuenta y un permisos con literal (`plan.md` §2.1), el reparto (§2.2), las veintiuna descripciones (§2.3) y las cuatro guardas (§2.4) | `T-04` | `mvn verify` migra; las guardas cuentan 111 / 111 / 105 / 0 | **Hecha el 19-09-2026** |
| `T-07` | `PermissionsSeedIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT` y `PermissionIT` cuentan ciento once; `PermissionsSeedIT` afirma los literales de `V8`, `V19` y `V22` intactos y las descripciones de los veintiuno | `T-06` | `CA-SP-688`, `CA-SP-691`, `CA-SP-695` | **Hecha el 19-09-2026** — y las dos suites de siembra de `PM` y las dos de `AC` cuentan lo suyo |
| `T-08` | `PermissionSplitMigrationIT`: esquema propio, Flyway hasta `V27`, dos roles bajo `ADMIN`, `V28`, afirmaciones (`plan.md` §5) | `T-06` | `CA-SP-692`, `CA-SP-693` | **Hecha el 19-09-2026** — cuatro pruebas, con Flyway hasta `V24` porque `V25` a `V27` son del bloque 4 de `AC` |
| `T-09` | `EndpointPermissionsIT`: la inyectividad con el mensaje que lista permiso y operaciones, y la tabla operación → permiso de `spec.md` §6.2; `AC` declarado como pendiente con fecha hasta `T-13` | `T-06` | `CA-SP-689`, `CA-SP-690` (positiva) | **Hecha el 19-09-2026** |
| `T-10` | **`SP`**: `RoleController`, `PermissionController`, `MembershipController`, `UserController`, `BrokerAccountController`; sus pruebas conceden el código nuevo y ganan el caso negativo del padre; el rol con `users:list` y sin `users:read` | `T-09` | `CA-SP-690` (negativa) en trece operaciones; `CA-SP-694`; suite de `SP` en verde | **Hecha el 19-09-2026** — la mitad negativa vive en `PermissionSplitIT`, una clase para los tres módulos |
| `T-11` | **`PM`**: `ProductController`, `ProductCommentController`, `PackageController` y sus pruebas, con el caso negativo del padre y `RN-PM-027` sobre los códigos nuevos de reseña | `T-10` | `CA-SP-690` en catorce operaciones; `CA-SP-697`; suite de `PM` en verde | **Hecha el 19-09-2026** |
| `T-12` | **`CM`**: `UserCommissionRateController`, `ProductCommissionRateController`, `CommissionResolutionController` y sus pruebas, con el caso negativo del padre | `T-11` | `CA-SP-690` en seis operaciones; suite de `CM` en verde | **Hecha el 19-09-2026** |
| `T-13` | **`AC`, tramo 3**: `requirements/ac.md` §6.1 y §7 con fila propia; las veintiuna tripletas de `AC`; los cuatro controladores y los del bloque 4; sus pruebas; `T-09` vacía su lista de pendientes | `T-12`, bloque 4 de `AC` construido | `CA-SP-690` en dieciocho operaciones; suite de `AC` en verde; `EndpointPermissionsIT` sin excepciones | **Bloqueada** (§4, 1) |
| `T-14` | Contrato regenerado y comparado; `docs/api/index.md` con su fila; `docs/testing` donde describa `EndpointPermissionsIT` | `T-12` | `CA-SP-696`: cincuenta y una operaciones con `x-required-permission` nuevo, ninguna con forma nueva | **Hecha el 19-09-2026** — `docs/testing` no describe `EndpointPermissionsIT`: nada que tocar ahí |
| `T-15` | Suite completa en verde y matriz de `docs/requirements.md` al día | `T-14` | `mvn verify` | **Hecha el 19-09-2026** para el tramo 2; vuelve a correr con `T-13` |

## 2. Orden de ejecución

**Primero los documentos y después el código**, y no al revés: `T-03` y `T-04` antes de `T-06`. Es la lección del 02-09-2026, cuando `CM` se rehízo entero y las tripletas se escribieron detrás describiendo lo hecho en lugar de decidirlo.

**`T-06` a `T-09` juntas**: la migración sin sus pruebas no se sabe si reparte bien, y `T-09` es la que vigila desde ese momento.

**`T-10`, `T-11` y `T-12` en ese orden y una a la vez**, con la suite del módulo en verde antes de la siguiente (`plan.md` §6): un `403` inesperado en toda la suite no dice cuál de los cincuenta y un permisos está mal.

**`T-13` espera al bloque 4 de `AC`** y la hace quien cierre `AC`, con este plan delante. `V28` ya habrá sembrado sus dieciocho.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-688` | `T-06`, `T-07` |
| `CA-SP-689` | `T-09` |
| `CA-SP-690` | `T-09`, `T-10`, `T-11`, `T-12`, `T-13` |
| `CA-SP-691` | `T-06`, `T-07` |
| `CA-SP-692`, `CA-SP-693` | `T-08` |
| `CA-SP-694` | `T-10` |
| `CA-SP-695` | `T-06`, `T-07` |
| `CA-SP-696` | `T-14` |
| `CA-SP-697` | `T-11` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **El bloque 4 de `AC` (`RF-AC-016` a `021`) se construye con `courses:update`** por sus tripletas aprobadas, y `AC` se reparte entero después (`spec.md` §14, 4). `T-13` espera a ese bloque | 19-09-2026 | Responsable técnico de `AC` | **Abierto** |
| 2 | **El frontend decide por código de permiso.** Cincuenta y una operaciones cambian el suyo; en el backend nadie pierde acceso, pero el frontend puede esconder lo que sí se puede hasta que adopte la tabla de `spec.md` §6.2 (`T-05`) | 19-09-2026 | Responsable del proyecto | **Abierto** |
| 3 | **Los bloques 5 y 6 de `AC`** (portadas y aula) nacerán con un permiso por operación. Los nombres los fija `AC` con las convenciones de `spec.md` §6.3 —para el aula, lo que hoy es un solo `courses:learn` sobre tres lecturas— | 19-09-2026 | Responsable técnico de `AC` | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`. — Falta `T-13` (tramo 3, `AC`).
- [x] Todos los criterios de aceptación con prueba automatizada en verde — `CA-SP-690` en `AC` cuando llegue `T-13`.
- [x] `mvn verify` en verde en local.
- [ ] **`EndpointPermissionsIT` sin excepciones pendientes**: ningún permiso gobierna dos operaciones.
- [x] El contrato OpenAPI coincide con el comportamiento real, `x-required-permission` incluido.
- [x] Documentación afectada actualizada en el mismo Pull Request.
- [x] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
