# TASKS — `RF-SP-078` Recibir los avisos de los brokers

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-078` |
| Especificación | [`spec.md`](spec.md) v0.5.0 |
| Plan | [`plan.md`](plan.md) v0.5.0 |
| `plan.md` aprobado el | 08-10-2026 |
| Estado | **En revisión** — `T-01` a `T-08` `Hecha` el 08-10-2026; `T-09` pendiente; `T-11` a `T-14` `Hecha` el 09-10-2026 (enmienda 0.3.0); `T-15` y `T-16` `Hecha` el 09-10-2026 (enmienda 0.4.0); `T-17` a `T-19` `Hecha` el 10-10-2026 (enmienda 0.5.0) |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V85__sp_avisos_de_brokers.sql`: `broker_notifications`, su `CHECK`, su clave foránea, su índice y sus comentarios | — | Flyway aplica sobre una base limpia | **Hecha** — 08-10-2026 |
| `T-02` | `BrokerNotificationSettings` y el bloque `nexus.brokers.notification-tokens` de `application.yml` | — | | **Hecha** — 08-10-2026 |
| `T-03` | `BrokerNotificationRepository` y `JpaBrokerNotificationRepository`: `isActive` e `insert` | `T-01` | | **Hecha** — 08-10-2026 |
| `T-04` | `BrokerNotice` y `ReceiveBrokerNotificationService`: el orden de las comprobaciones, el `token` fuera de la consulta, las cabeceras filtradas | `T-02`, `T-03` | | **Hecha** — 08-10-2026 |
| `T-05` | `BrokerNotificationController` (`GET` y `POST`, el cuerpo del flujo con su tope) y la ruta en `RUTAS_PUBLICAS` | `T-04` | `200` vacío; `401`, `404`, `503` y `400` sin guardar | **Hecha** — 08-10-2026 |
| `T-06` | `RequestLogFilter` oculta el valor de `token` | — | | **Hecha** — 08-10-2026 |
| `T-07` | `BrokerNotificationsIT`: `CA-SP-897` a `CA-SP-907` | `T-05`, `T-06` | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 08-10-2026 |
| `T-08` | `EndpointPermissionsIT` (`PUBLICAS`); contrato con la prosa; `deployment.md` y `api/index.md`; `requirements.md` | `T-07` | | **Hecha** — 08-10-2026 |
| `T-10` | La ruta por nombre (spec 0.2.0): `{name}` en el controlador, `findActiveByName` en el repositorio, `CA-SP-908`, `EndpointPermissionsIT` y el contrato | `T-08` | | **Hecha** — 08-10-2026 |
| `T-11` | `V93`: `brokers.advertiser` y `brokers.url`, con `iq_option` cargado (`plan.md` §13) | — | La guarda pasa al migrar | **Hecha** — 09-10-2026 |
| `T-12` | El secreto común; `findActiveByAdvertiser`; `receiveCommon` con su orden; las dos rutas comunes y las viejas obsoletas; `RUTAS_PUBLICAS`; `url` en `BrokerItem` (`RF-SP-052`, `CA-SP-953`) | `T-11` | | **Hecha** — 09-10-2026 |
| `T-13` | `BrokerNotificationsIT`: `CA-SP-946` a `CA-SP-952`; `EndpointPermissionsIT` | `T-12` | Cada criterio afirmado en el cuerpo | **Hecha** — 09-10-2026 |
| `T-14` | Contrato, `api/index.md`; `BROKER_NOTIFICATION_TOKEN` en `.env.example` | `T-13` | `mvn verify` en verde | **Hecha** — 09-10-2026 |
| `T-15` | La configuración del evento y los campos; `BrokerRegistrationNotices.apply`, llamado tras guardar (`plan.md` §14) | `T-13` de `RF-SP-053` | | **Hecha** — 09-10-2026 |
| `T-16` | `BrokerNotificationsIT`: `CA-SP-964` a `CA-SP-968`; `deployment.md` | `T-15` | `mvn verify` en verde | **Hecha** — 09-10-2026 |
| `T-17` | `V98`; los dos eventos y `event-id` en la configuración; `lockByNumber`, `markFirstDeposit`, `countOperation`, `otherWithEventId`, `hasFirstDeposit`; `ConfirmFirstDepositService`; el despacho por evento (`plan.md` §15) | `T-16` | Flyway aplica sobre una base limpia | **Hecha** — 10-10-2026 |
| `T-18` | La activación al asociar: `claim`, `assignHolder` y el registro por enlace; `activity` en las dos respuestas | `T-17` | | **Hecha** — 10-10-2026 |
| `T-19` | `BrokerNotificationsIT` y `SelfRegistrationIT`: `CA-SP-995` a `CA-SP-1002`; `deployment.md`, `api/index.md`, `.env.example` | `T-18` | `mvn verify` en verde | **Hecha** — 10-10-2026 |
| `T-09` | Configurar los tres secretos en Railway y la dirección en el panel de afiliados de cada broker; disparar un aviso de prueba desde cada panel y verlo en la base | `T-08` | Una fila por broker | Pendiente |

---

## 2. Orden de ejecución

`T-01`, `T-02` y `T-06` en paralelo → `T-03` → `T-04` → `T-05` → `T-07` → `T-08` → `T-09`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-897` a `CA-SP-899`, `CA-SP-905` | `T-03`, `T-04`, `T-05`, `T-07` |
| `CA-SP-900` a `CA-SP-904` | `T-04`, `T-05`, `T-07` |
| `CA-SP-906` | `T-04`, `T-06`, `T-07` |
| `CA-SP-907` | `T-04`, `T-07` |
| `CA-SP-908` | `T-10` |
| `CA-SP-946` a `CA-SP-952` | `T-11` a `T-13` |
| `CA-SP-964` a `CA-SP-968` | `T-15`, `T-16` |
| `CA-SP-995` a `CA-SP-1002` | `T-17` a `T-19` |

---

## 3.1 Desviaciones respecto del plan

**Los secretos de la suite van en `IntegrationTestBase`** y no en un `@TestPropertySource` de la clase: este abriría un contexto de Spring propio, con su propio grupo de conexiones. `IQOPTION` y `EXNOVA` llevan secreto para toda la suite y `EXOPTION` no, que es lo que `CA-SP-902` necesita. **Los métodos del controlador se llaman `brokerNotificationByQuery` y `brokerNotificationByBody`**, para que el `operationId` del contrato no sea `get` y `post`.

## 4. Bloqueos

**`T-09` necesita acceso a los paneles de afiliados** de los tres brokers y una dirección pública del backend: en local los avisos no llegan sin un túnel, como los de PayRetailers ([`deployment.md`](../../../deployment.md) §6.5.2).

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde: 572 unitarias y 2639 de integración, 08-10-2026.
- [x] Los doce criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [ ] Un aviso real de cada broker guardado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
