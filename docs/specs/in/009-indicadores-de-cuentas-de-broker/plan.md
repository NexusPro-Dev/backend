# PLAN — `RF-IN-009` Consultar los indicadores de cuentas de broker de la red

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-009` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 10-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |

---

## 1. Enfoque

**El recorrido del árbol se muda tal cual** de `GetNetworkIndicatorsService` (`SP`) a `GetBrokerNetworkIndicatorsService` (`IN`): la fuerza comercial con su superior vigente, la raíz de cada rama —sin superior o con superior que no es fuerza comercial—, el post-orden que arma cada padre con lo que traen sus hijos, y la conversión recalculada sobre la suma. **Lo que cambia es de dónde salen los números.**

**`SP` publica `BrokerAccountFigures`** (`system.brokers.application`), que implementa `JpaBrokerAccountFigures` con **tres consultas planas**, todas sobre las cuentas `CONSUMIDOR` cuyo titular no está eliminado:

1. **`commercialForce()`** —la de `RF-SP-058`, movida—.
2. **`bySellerAndBroker(desde, hasta)`**: una fila por **vendedor de origen** y broker —`r.user_id` si es fuerza comercial, **nulo** si no tiene origen o no lo es: eso es lo no atribuido— con las seis cifras, cada una con su fecha (`RN-IN-015`), en un solo recorrido con `count(*) FILTER (WHERE …)`.
3. **`consumersBySeller(desde, hasta)`**: los consumidores **distintos** por vendedor de origen, aparte porque una persona con cuentas en dos brokers es una.

Y **`byBucket(vendedores, desde, hasta, unidad, zona)`** para los tramos de los totales: las cuentas creadas por tramo de `created_at` y los FTD por tramo de `first_deposit_at`, con `date_trunc(:unidad, … AT TIME ZONE :zona)` como `JpaPointsFigures`.

**Los tipos del puerto son de `SP`**: `OffsetDateTime` para el intervalo y `String` para la unidad. `SP` no consume de nadie y no puede usar los de `MV` (`SalesFigures.Interval`).

**El alcance** es el de `RN-IN-002` con `CommercialReach.reachOf`: `EVERYTHING` sin `sellerId` arma el bosque entero y lo no atribuido; con `sellerId`, la rama de ese vendedor; `NETWORK`, la del actor —o la de un `sellerId` de su red—; fuera del alcance, la respuesta vacía.

## 2. Cambios de esquema

**`V101__in_indicadores_de_cuentas_de_broker.sql`**: el permiso `indicators:read-broker-accounts-network` (`01a10e82-9000-701d-9c4f-5e7ad8000009`) a los roles `FUNCIONARIO` y `VENDEDOR` por su tipo, con su guarda; **y borra `broker-accounts:read-indicators`** con su reparto. Sin tablas ni índices: `ix_user_brokers_origen` ya sirve para unir el origen.

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `SP` `application` | `BrokerAccountFigures` | Nuevo: el puerto |
| `SP` `domain/repository` | `JpaBrokerAccountFigures` | Nuevo; `findCommercialForce` y los conteos de `RF-SP-058` salen de `JpaBrokerAccountQueryRepository` |
| `SP` | `GetNetworkIndicatorsService`, `NetworkIndicatorsResponse`, la ruta de `BrokerAccountController`, `NetworkIndicatorsIT` | **Se retiran** |
| `IN` `application` | `BrokerNetworkIndicatorsResponse` | Nuevo |
| `IN` `domain/service` | `GetBrokerNetworkIndicatorsService` | Nuevo |
| `IN` `interfaces` | `BrokerAccountIndicatorsController` | Nuevo: `GET /api/v1/indicators/broker-accounts/network` |

## 4. Contrato de API

`GET /api/v1/indicators/broker-accounts/network?from=&to=&sellerId=&granularity=`, `200`:

```json
{ "period": {…}, "nodes": [ { "user": {…}, "roleCode": "DIRECTOR",
    "own": BLOQUE, "network": BLOQUE, "children": [ … ] } ],
  "totals": BLOQUE, "unassigned": BLOQUE | null,
  "granularity": "MONTH" | null, "buckets": [ { "start": "2026-10-01", "accounts": 3, "ftd": 1 } ] | null }
```

`BLOQUE` = `accounts`, `ftd`, `pending`, `conversion` (nula sin cuentas), `withoutHolder`, `consumers`, `activeAccounts`, `operations` y `byBroker` —`broker` y las mismas cifras salvo `consumers`—. Los esquemas se nombran `BrokerNetwork…` para no chocar con los de `SP` que se retiran.

## 5. Autorización

`indicators:read-broker-accounts-network` (`RN-IN-001`); el alcance, `RN-IN-002`.

## 6. Auditoría y transaccionalidad

Lectura sin auditoría; las consultas en una transacción de solo lectura.

## 7. Impacto sobre otros documentos

`requirements/in.md` v0.21.0, `requirements/sp.md` v1.122.0, `security.md` v0.134.0, `requirements.md` v0.349.0, `api/index.md`; la tripleta de `RF-SP-058` queda como historia.

## 8. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Recursiva por nodo | Un `N + 1` de consultas caras; el recorrido en memoria sobre tres consultas planas es el de `RF-SP-058` |
| Operaciones por periodo exacto contando avisos | Habría que cruzar `broker_notifications` con el nombre configurado del evento y el número de cada aviso; la cuenta ya guarda el acumulado |

## 9. Riesgos

| Riesgo | Mitigación |
|---|---|
| El frontend nombra `broker-accounts:read-indicators` en su diccionario | Se avisa a la sesión del frontend; no hay pantalla que use la ruta |
| Recuentos | El catálogo no cambia (uno sale, otro entra); las listas por rol de `SystemRolesSeedIT` y `RoleDetailIT` ganan uno en `VENDEDOR` |

## 10. Estrategia de prueba

`BrokerNetworkIndicatorsIT` con `CA-IN-104` a `CA-IN-110`; las suites de siembra; `EndpointPermissionsIT` cambia la ruta.
