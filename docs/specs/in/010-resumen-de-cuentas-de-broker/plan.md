# PLAN — `RF-IN-010` Consultar el resumen de cuentas de broker y de mi personal

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-010` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 15-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |

---

## 1. Enfoque

**Sin consultas nuevas**: todo sale del puerto de `RF-IN-009`, `BrokerAccountFigures`.

- **`commercialForce()`** da las personas con su `roleCode` y su superior.
- **`bySellerAndBroker(null, null, true)`** da, por vendedor de origen, las cuentas, los FTD (todas en `FIRST_DEPOSIT`) y las sin titular de toda la historia; la fila de vendedor nulo —o de uno fuera de la fuerza comercial— es lo no atribuido, que es `withoutSeller`.
- **`bySellerAndBroker(inicioDeHoy, inicioDeMañana, false)`** da las creadas hoy y los FTD llegados hoy, cada uno por su fecha (`RN-IN-015`).

El servicio suma los brokers por vendedor, se queda con las personas del alcance y agrupa por `roleCode`. **El orden de los grupos es el de un recorrido en pre-orden del árbol** —cúspide, luego sus hijos—, que da la jerarquía sin leer el catálogo de roles; dentro del grupo, por usuario.

**El alcance** (`RN-IN-002`, `CommercialReach.reachOf`): `EVERYTHING`, toda la fuerza comercial y los totales con lo no atribuido; `NETWORK`, `reach.sellers()` —él incluido—; `OWN`, solo él. Fuera de `EVERYTHING`, los totales son la suma de las personas del alcance y `withoutSeller` va nulo.

**Hoy** es `BusinessCalendar.hoy()`, de su medianoche a la siguiente en la zona del negocio.

## 2. Cambios de esquema

**`V115__in_resumen_de_cuentas_de_broker.sql`**: el permiso `indicators:read-broker-accounts-summary` (`01a10e82-9000-701e-9c4f-5e7ad800000a`) a los roles `FUNCIONARIO` y `VENDEDOR` por su tipo, con su guarda, como `V101`. El catálogo crece en uno.

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `IN` `application` | `BrokerAccountsSummaryResponse` | Nuevo |
| `IN` `domain/service` | `GetBrokerAccountsSummaryService` | Nuevo |
| `IN` `interfaces` | `BrokerAccountIndicatorsController` | Nueva ruta `GET /api/v1/indicators/broker-accounts/summary` |

## 4. Contrato de API

`GET /api/v1/indicators/broker-accounts/summary`, `200`:

```json
{ "today": "2026-10-15",
  "totals": { "accounts": 120, "ftd": 30, "withoutHolder": 40, "withoutSeller": 5,
              "accountsToday": 3, "ftdToday": 1 },
  "staff": [ { "roleCode": "DIRECTOR", "accounts": 50, "ftd": 10, "accountsToday": 1, "ftdToday": 0,
               "people": [ { "user": { "id": "…", "username": "…", "firstName": "…", "lastName": "…" },
                             "accounts": 50, "ftd": 10, "accountsToday": 1, "ftdToday": 0 } ] } ] }
```

## 5. Autorización

`indicators:read-broker-accounts-summary` (`RN-IN-001`); el alcance, `RN-IN-002`.

## 6. Auditoría y transaccionalidad

Lectura sin auditoría, en una transacción de solo lectura.

## 7. Impacto sobre otros documentos

`requirements/in.md` v0.22.0, `security.md` v0.142.0, `requirements.md`, `api/index.md`.

## 8. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Ampliar `RF-IN-009` con `?view=summary` | Una ruta con dos formas de respuesta; y el permiso es por indicador (`RN-IN-001`) |
| Consulta SQL propia con «hoy» | Repetiría lo que ya cuenta `bySellerAndBroker` con su fecha |

## 9. Riesgos

| Riesgo | Mitigación |
|---|---|
| Recuentos del catálogo de permisos | Se actualizan todas las suites que lo cuentan o listan por rol |

## 10. Estrategia de prueba

`BrokerAccountsSummaryIT` con `CA-IN-111` a `CA-IN-115`; las suites de siembra; `EndpointPermissionsIT`; contrato.
