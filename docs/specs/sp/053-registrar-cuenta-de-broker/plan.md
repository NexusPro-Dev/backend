# PLAN — `RF-SP-053` Registrar una cuenta de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-053` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 08-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |

---

## 1. Enfoque

**Este plan carga las piezas comunes de `RF-SP-053`, `RF-SP-080` y `RF-SP-081`**: un servicio, un repositorio de escritura, la migración y las seis rutas. Los planes de los otros dos remiten aquí y dicen solo lo suyo.

**`ManageBrokerAccountsService`**, en `brokers/domain/service`, con dos puertas por operación —la propia toma a la persona de `CurrentActor`, la de administración la toma de la ruta y comprueba que existe— y **un solo cuerpo** detrás, que recibe si quien actúa es el titular. Es lo único que cambia entre las dos: `RN-SP-067` se aplica solo al titular.

**`BrokerAccountWriter`** (`JpaBrokerAccountWriter`), SQL nativo como el resto del submódulo: `insert`, `lock` (`SELECT … FOR UPDATE` de la cuenta), `updateAccountId` y `delete`. **La unicidad la decide `uq_user_brokers_cuenta`**, traducida a `EX-009` por el nombre de la restricción con `flush` explícito, como `JpaBrokerAccountRegistrar`. El broker se lee con `BrokerAccountRegistrar.find`, que ya existe y ya responde «inexistente o apagado» igual.

**La respuesta** es `BrokerAccountItem`, la fila de `RF-SP-055`, leída después de escribir con `BrokerAccountQueryRepository`.

---

## 2. Cambios de esquema

**`V90`**, sin tablas: seis permisos.

| Permiso | Identificador (Art. V.11) | Reparto |
|---|---|---|
| `broker-accounts:create-own` | `01a10e82-9000-7015-9c4f-5e7ada000007` | Todo rol por su tipo, los tres |
| `broker-accounts:update-own` | `01a10e82-9000-7016-9c4f-5e7ada000008` | Todo rol por su tipo, los tres |
| `broker-accounts:delete-own` | `01a10e82-9000-7017-9c4f-5e7ada000009` | Todo rol por su tipo, los tres |
| `broker-accounts:create` | `01a10e82-9000-7018-9c4f-5e7ada00000a` | `SUPERADMIN` y `ADMIN` explícitos |
| `broker-accounts:update` | `01a10e82-9000-7019-9c4f-5e7ada00000b` | `SUPERADMIN` y `ADMIN` explícitos |
| `broker-accounts:delete` | `01a10e82-9000-701a-9c4f-5e7ada00000c` | `SUPERADMIN` y `ADMIN` explícitos |

La marca v7 de `V79`, secuencias 7015 a 701a, y la serie de `broker-accounts` donde `V89` la dejó. **Guardas**: catálogo **216**, `SUPERADMIN` 216, `ADMIN` 214, `CLIENTE` porta los tres propios y ninguno de los tres amplios, contención de `RN-SEG-003`. Sin auditoría, como `V89`.

`user_brokers` no cambia: el borrado es físico y nada la referencia.

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `interfaces` | `UserController` | Las seis rutas de §4, con sus `@Operation` |
| `application` | `CreateBrokerAccountRequest`, `UpdateBrokerAccountRequest` | Nuevos |
| `domain/service` | `ManageBrokerAccountsService` | Nuevo |
| `domain/repository` | `BrokerAccountWriter`, `JpaBrokerAccountWriter` | Nuevos |
| `db/migration` | `V90__sp_gestionar_cuentas_de_broker.sql` | §2 |

---

## 4. Contrato de API

| Ruta | Permiso | Requerimiento |
|---|---|---|
| `POST /api/v1/users/me/broker-accounts` | `broker-accounts:create-own` | `RF-SP-053` |
| `POST /api/v1/users/{id}/broker-accounts` | `broker-accounts:create` | `RF-SP-053` |
| `PATCH /api/v1/users/me/broker-accounts/{brokerAccountId}` | `broker-accounts:update-own` | `RF-SP-080` |
| `PATCH /api/v1/users/{id}/broker-accounts/{brokerAccountId}` | `broker-accounts:update` | `RF-SP-080` |
| `DELETE /api/v1/users/me/broker-accounts/{brokerAccountId}` | `broker-accounts:delete-own` | `RF-SP-081` |
| `DELETE /api/v1/users/{id}/broker-accounts/{brokerAccountId}` | `broker-accounts:delete` | `RF-SP-081` |

**`brokerAccountId` y no `accountId` en la ruta**: `accountId` ya es, en el cuerpo y en la respuesta, el identificador **en el broker**; llamar igual al interno los confundiría.

Alta: `201` con la cuenta. Códigos en `spec.md` §10.

---

## 5. Autorización

Un permiso por ruta (`RN-SEG-015`). Los propios no comprueban nada más que la titularidad, que sale de la consulta (`WHERE user_id = actor`); los amplios alcanzan a cualquiera. **El superior comercial no gestiona** (`spec.md` §2.1).

## 6. Auditoría

Un `ChangeEvent` sobre `user_brokers`, módulo `SP`: `CREATE` con broker, identificador, persona y estado; `UPDATE` con el identificador antes y después (`RF-SP-080`); `DELETE` con la fila entera (`RF-SP-081`).

## 7. Transaccionalidad

Una transacción por petición. Editar y borrar bloquean la cuenta antes de comprobar `RN-SP-067`: sin el bloqueo, el estado podría cambiar entre la comprobación y la escritura.

## 8. Impacto sobre otros módulos y documentos

`requirements/sp.md` (`RF-SP-053`, `RF-SP-080`, `RF-SP-081`, `RN-SP-067`, §5.2 de `user_brokers`, rutas), `security.md` §4.4, `requirements.md`, `api/index.md`.

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Reutilizar `BrokerAccountRegistrar.declare` | Su traducción de `EX-009` nombra el campo del registro (`brokerAccountId`), y aquí es `accountId`; y es el puerto de `users` hacia `brokers`, no el de este submódulo |
| Rutas bajo `/broker-accounts/{id}` para administración | La cuenta se gestiona desde la persona, como se consulta (`RF-SP-055`), y la ruta dice de quién es |

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Recuentos del catálogo | 210 → **216**, `ADMIN` 208 → **214**, `CLIENTE` 37 → **40**: las siete suites, `ALCANCE_PROPIO`, `reponerAlcancePropio`, `SystemRolesSeedIT` y `PermissionsSeedIT` |
| `fk_user_brokers_user` es `RESTRICT` | La suite borra las cuentas al terminar, como `BrokerAccountsIT` |

## 11. Estrategia de prueba

Integración, `ManageBrokerAccountsIT`, con los criterios de los tres requerimientos. `CA-SP-937` en las suites de siembra. `EndpointPermissionsIT` y `OwnScopePermissionsIT` con las seis rutas.
