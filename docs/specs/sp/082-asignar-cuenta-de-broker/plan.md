# PLAN — `RF-SP-082` Asignar una cuenta de broker sin titular

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-082` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 09-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |

---

## 1. Enfoque

**Sobre las piezas de `RF-SP-053`** (su `plan.md` §1 y §13): `ManageBrokerAccountsService.assignHolder` y `BrokerAccountWriter.lockAny(brokerAccountId)` —el `lock` sin persona— y `assignUser`. El tipo de la persona con `kindFor`. La respuesta, la fila de `RF-SP-057` leída por identificador.

## 2. Cambios de esquema

Los de `V96` (`RF-SP-053` `plan.md` §13), incluido el permiso.

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `interfaces` | `BrokerAccountController` | `PATCH /api/v1/broker-accounts/{brokerAccountId}/holder` |
| `application` | `AssignBrokerAccountHolderRequest`, `ListBrokerAccountsRequest` | Nuevo; `hasHolder` |
| `domain/service` | `ManageBrokerAccountsService`, `ListBrokerAccountsService` | `assignHolder`; el filtro |
| `domain/repository` | `BrokerAccountWriter`, `JpaBrokerAccountQueryRepository` | `lockAny`, `assignUser`; `LEFT JOIN users` en el listado global y `findOne` |

## 4. Contrato de API

`PATCH /api/v1/broker-accounts/{brokerAccountId}/holder`, cuerpo `{ "userId": … }`, `200` con `TeamBrokerAccountItem`, cuyo `user` pasa a admitir nulo.

## 5. Autorización

`broker-accounts:assign-user`, a `SUPERADMIN` y `ADMIN` explícitos (`RN-SEG-015`).

## 6. Auditoría

`ChangeEvent` `UPDATE` sobre `user_brokers` con `user_id` antes —nulo— y después.

## 7. Transaccionalidad

Una transacción; la cuenta se bloquea antes de mirar si tiene titular.

## 8. Impacto sobre otros documentos

`requirements/sp.md`, `security.md`, `requirements.md`, `api/index.md`.

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Bajo `/users/{id}/broker-accounts` | La cuenta no es de nadie todavía: la ruta nombraría a la persona antes de que la cuenta sea suya |
| Permitir reasignar | Quitaría la cuenta a su titular sin borrado auditado |

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| El listado global dejaba fuera las cuentas sin titular (`JOIN users`) | `LEFT JOIN`, y `u.deleted_at IS NULL` pasa a `(u.id IS NULL OR u.deleted_at IS NULL)` |
| Recuentos del catálogo | +1 en las siete suites; `ADMIN` +1 |

## 11. Estrategia de prueba

`AssignBrokerAccountHolderIT` con `CA-SP-969` a `CA-SP-971`; `CA-SP-972` en las suites de siembra; `EndpointPermissionsIT`.
