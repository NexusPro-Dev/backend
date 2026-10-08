# TASKS — `RF-SP-078` Recibir los avisos de los brokers

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-078` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 08-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V85__sp_avisos_de_brokers.sql`: `broker_notifications`, su `CHECK`, su clave foránea, su índice y sus comentarios | — | Flyway aplica sobre una base limpia | Pendiente |
| `T-02` | `BrokerNotificationSettings` y el bloque `nexus.brokers.notification-tokens` de `application.yml` | — | | Pendiente |
| `T-03` | `BrokerNotificationRepository` y `JpaBrokerNotificationRepository`: `isActive` e `insert` | `T-01` | | Pendiente |
| `T-04` | `BrokerNotice` y `ReceiveBrokerNotificationService`: el orden de las comprobaciones, el `token` fuera de la consulta, las cabeceras filtradas | `T-02`, `T-03` | | Pendiente |
| `T-05` | `BrokerNotificationController` (`GET` y `POST`, el cuerpo del flujo con su tope) y la ruta en `RUTAS_PUBLICAS` | `T-04` | `200` vacío; `401`, `404`, `503` y `400` sin guardar | Pendiente |
| `T-06` | `RequestLogFilter` oculta el valor de `token` | — | | Pendiente |
| `T-07` | `BrokerNotificationsIT`: `CA-SP-897` a `CA-SP-907` | `T-05`, `T-06` | Cada criterio afirmado en el cuerpo de la prueba | Pendiente |
| `T-08` | `EndpointPermissionsIT` (`PUBLICAS`); contrato con la prosa; `deployment.md` y `api/index.md`; `requirements.md` | `T-07` | | Pendiente |
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

---

## 4. Bloqueos

**`T-09` necesita acceso a los paneles de afiliados** de los tres brokers y una dirección pública del backend: en local los avisos no llegan sin un túnel, como los de PayRetailers ([`deployment.md`](../../../deployment.md) §6.5.2).

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los once criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] Un aviso real de cada broker guardado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
