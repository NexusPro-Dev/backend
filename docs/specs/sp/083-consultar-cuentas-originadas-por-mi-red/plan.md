# PLAN — `RF-SP-083` Consultar las cuentas de broker que originó mi red

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-083` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 10-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |

---

## 1. Enfoque

**Sobre la consulta de `RF-SP-057`**, y no una propia: la misma fila (`TeamBrokerAccountItem`), el mismo predicado y el mismo resumen (`BrokerAccountsPage`). `BrokerAccountFilters` gana dos filtros que la API de administración no publica: **`referrerNetworkOf`** —la raíz de la red de origen— y **`referrerUserId`** —un vendedor concreto, el `sellerId`—. `ListBrokerAccountsService.listReferred` los fija con el actor y deja el resto como en `list`.

**La red de origen es otra recursiva**, con su propio parámetro (`:origen`), que **incluye a la raíz**:

```sql
red_origen AS (
    SELECT CAST(:origen AS uuid) AS user_id
    UNION
    SELECT us.user_id FROM user_supervisors us
      JOIN red_origen ro ON us.supervisor_id = ro.user_id
     WHERE us.ended_at IS NULL)
```

Va en el mismo `WITH RECURSIVE` que la red de `supervisorId` cuando los dos están, y el predicado añade `ub.kind = 'CONSUMIDOR' AND r.user_id IN (SELECT user_id FROM red_origen)` —`r` es la cuenta de origen, ya unida para la fila—.

## 2. Cambios de esquema

**`V100__sp_cuentas_originadas_por_mi_red.sql`**: el permiso `broker-accounts:read-own-referred` (`01a10e82-9000-701c-9c4f-5e7ada00000e`) a los roles de tipo `VENDEDOR` y `FUNCIONARIO`, con su guarda; y `ix_user_brokers_busqueda_usuario`, gin de trigramas sobre `f_unaccent(lower(broker_username))`, para el `search` ampliado de `RF-SP-057`.

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `interfaces` | `UserController` | `GET /api/v1/users/me/referred-broker-accounts` |
| `application` | `ListReferredBrokerAccountsRequest` | Nuevo |
| `domain/service` | `ListBrokerAccountsService` | `listReferred`; `list` y `listReferred` comparten la página y el resumen |
| `domain/repository` | `BrokerAccountQueryRepository`, `JpaBrokerAccountQueryRepository` | Los dos filtros y la recursiva de origen |

## 4. Contrato de API

`GET /api/v1/users/me/referred-broker-accounts?status=&brokerId=&sellerId=&hasHolder=&search=&from=&to=&page=&size=`, `200` con `BrokerAccountsPage`.

## 5. Autorización

`broker-accounts:read-own-referred`, sembrado por tipo de rol (`RN-SEG-015`). El alcance, la red del actor.

## 6. Auditoría y transaccionalidad

Lectura: sin auditoría. Resumen y página en la misma transacción de solo lectura, como `RF-SP-057`.

## 7. Impacto sobre otros documentos

`requirements/sp.md`, `security.md`, `requirements.md`, `modelo-datos.md`, `api/index.md` y la tripleta de `RF-SP-057` (v0.5.0).

## 8. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un `?referrerNetworkOf=` en `GET /broker-accounts` | Esa ruta es de administración; el vendedor no tiene `broker-accounts:read` |
| Por el titular (`client_sellers`) y no por el origen | Dejaría fuera las cuentas sin titular, que son justo las que nacen del enlace |

## 9. Riesgos

| Riesgo | Mitigación |
|---|---|
| La recursiva por cada consulta | Una sola por petición, la misma forma que la de `RN-SP-047` |
| Recuentos del catálogo | +1 en las suites que lo cuentan; `ADMIN` +1 |

## 10. Estrategia de prueba

`ReferredBrokerAccountsIT` con `CA-SP-1003` a `CA-SP-1006`; las suites de siembra; `EndpointPermissionsIT`.
