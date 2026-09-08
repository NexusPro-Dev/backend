# TASKS — `RF-SP-047` Registrar una tasa de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-047` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 07-09-2026 |
| Estado | **Completada el 08-09-2026** — `T-01` a `T-15` **Hecha**. La última fue `T-12`, la prueba concurrente: es la única que distingue la garantía real —el `EXCLUDE` del esquema— de la comprobación previa del caso de uso, que con dos peticiones a la vez no vale nada |
| Issue | Pendiente de crear |
| Rama | `feature/tasas-de-cambio` |
| Autor | Responsable técnico |
| Enmendadas | 08-09-2026 — `T-04` y `plan.md` §3 y §10: la traducción del `EXCLUDE` necesita el `SQLState` `23P01`, porque Hibernate no da el nombre de la restricción en una violación de exclusión. Lo destapó `T-12` |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V65__create_exchange_rates.sql`: la tabla con sus dos claves foráneas, los tres `CHECK` y el `EXCLUDE USING gist` (`plan.md` §2.1 y §2.2) | — | `mvn flyway:info` la lista aplicada. Una prueba de esquema comprueba las **tres** propiedades del `EXCLUDE`: rechaza el solapamiento, **admite** el par distinto y **admite** el rango contiguo | **Hecha el 07-09-2026** |
| `T-02` | Migración `V66__seed_exchange_rates_permissions.sql`: los cuatro permisos con UUID v7 literales, **su asociación a `SUPERADMIN` y `ADMIN`** y la guarda que aborta si falta alguna de las ocho filas | `T-01` | Integración: los cuatro existen y **los dos roles los tienen**. Sin la segunda mitad, `ADMIN` no podría conceder `exchange-rates:create` | **Hecha el 07-09-2026** |
| `T-03` | `domain/models/ExchangeRate`: agregado y modelo persistente. Valida origen distinto de destino, precio positivo y vigencia coherente | `T-01` | Unitaria sin Spring: los tres rechazos con su código, y la vigencia de un solo día **se acepta** | **Hecha el 08-09-2026** |
| `T-04` | `domain/repository/ExchangeRateRepository` y su adaptador, con **traducción del `EXCLUDE`** —nombre de restricción **y `SQLState` `23P01`**— y `flush` explícito | `T-03` | Integración: el solapamiento produce `409` sobre el campo de la vigencia. **Nunca por el texto del driver**. Con solo el nombre respondía `500`: Hibernate no lo da en las violaciones de exclusión (08-09-2026) | **Hecha el 07-09-2026** |
| `T-05` | `application`: `RegisterExchangeRateRequest` y `ExchangeRateResponse`, con las dos monedas resueltas | — | La fecha de fin llega **vacía y presente** en una tasa vitalicia, no ausente (`CA-SP-531`) | **Hecha el 07-09-2026** |
| `T-06` | `domain/service/RegisterExchangeRateService` con el orden de verificación de `plan.md` §5, y la **comprobación previa del solapamiento solo para el mensaje** | `T-04`, `T-05` | Cada rechazo llega con su código y **no se registra nada** | **Hecha el 07-09-2026** |
| `T-07` | Auditoría: evento `CREATE` con el estado inicial completo, en la misma transacción. La instantánea la arma el agregado | `T-06` | `audit_change_log` contiene el evento con todas las claves (`CA-SP-541`) | **Hecha el 07-09-2026** |
| `T-08` | `interfaces/ExchangeRateController`: `POST /api/v1/exchange-rates` con el permiso declarado sobre el método y `Location` en la respuesta | `T-06` | `201` con cabecera, y `403` sin el permiso | **Hecha el 07-09-2026** |
| `T-09` | Pruebas de API de los trece criterios de `spec.md` §12 | `T-08` | La suite cubre `CA-SP-530` a `CA-SP-542`, con sus estados y sus `error_code` | **Hecha el 08-09-2026** |
| `T-10` | **Prueba del borde exacto de `'[]'`**: una tasa que termina el día **antes** de que empiece la otra se admite; una que termina **el mismo día** se rechaza | `T-01` | `CA-SP-539`. Es la única que distingue `'[]'` de `'[)'`, y sin ella el error pasaría | **Hecha el 08-09-2026** |
| `T-11` | **Prueba de la escala de punta a punta**: `0,00024096` se envía, se guarda y se lee sin redondear | `T-08` | `CA-SP-535`, leyendo de la base y no solo de la respuesta | **Hecha el 08-09-2026** |
| `T-12` | Prueba concurrente: **dos altas simultáneas del mismo par y periodo** | `T-08` | Una queda y la otra recibe `409`. **No basta la verificación previa**: la garantía es el `EXCLUDE` | **Hecha el 08-09-2026** |
| `T-13` | Documentación OpenAPI del endpoint, con los seis campos y los estados `400`, `403`, `409` y `422` | `T-09` | `OpenApiContractIT` regenera el contrato y declara el endpoint. **La prosa dice que `validTo` nulo es vitalicia y que la inversa no se deduce** | **Hecha el 07-09-2026** |
| `T-14` | Ampliar las cuatro listas cerradas del catálogo de permisos: `PermissionsSeedIT`, `PermissionIT`, `JpaPermissionQueryRepositoryIT` y `ListPermissionsServiceIT` | `T-02` | Las cuatro cuentan **cuarenta y dos**. Esa fricción es deliberada: un permiso que aparezca sin que nadie toque esas listas es uno que nadie revisó | **Hecha el 07-09-2026** |
| `T-15` | Actualizar la matriz de `docs/requirements.md` | `T-09` | La fila de `RF-SP-047` refleja el estado | **Hecha el 08-09-2026** |

## 2. Orden de ejecución

`T-01` y `T-02` primero: sin tabla ni permisos no hay nada que probar de extremo a extremo.

`T-03`, `T-04` y `T-05` no dependen entre sí. `T-06` es la que las junta.

**`T-10`, `T-11` y `T-12` se escriben al final pero no son opcionales**: son las tres que fallan cuando alguien cambia `'[]'` por `'[)'`, recorta la escala o borra el `EXCLUDE` creyendo que la comprobación previa basta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-530` | `T-05`, `T-06`, `T-09` |
| `CA-SP-531` | `T-05`, `T-09` |
| `CA-SP-532`, `CA-SP-533` | `T-03`, `T-09` |
| `CA-SP-534` | `T-03`, `T-09` |
| `CA-SP-535` | `T-11` |
| `CA-SP-536` | `T-03`, `T-09` |
| `CA-SP-537` | `T-06`, `T-09` |
| `CA-SP-538` | `T-01`, `T-04`, `T-09` |
| `CA-SP-539` | `T-10` |
| `CA-SP-540` | `T-01`, `T-09` |
| `CA-SP-541` | `T-07` |
| `CA-SP-542` | `T-08` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-14` toca **cuatro pruebas de `SP` que enumeran el catálogo de permisos como lista cerrada**. Cualquier despiste ahí deja la suite roja lejos de este requerimiento | 07-09-2026 | Responsable técnico | **Cerrado el 08-09-2026** — las cuatro cuadran, y `ExchangeRatesPermissionsSeedIT` comprueba además las asociaciones, que ninguna de ellas mira |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **incluida la suite de `SP` sin cambios salvo las cuatro listas de permisos**.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
