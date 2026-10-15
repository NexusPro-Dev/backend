# PLAN — `RF-SP-084` Editar el enlace y el modelo de un broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-084` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 15-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |

---

## 1. Enfoque

**Como la edición de una entidad de cobro** (`RF-MV-034`, `PayoutInstitutionService.update`): un registro con solo los campos editables —lo demás lo rechaza `FAIL_ON_UNKNOWN_PROPERTIES`—, bloqueo de la fila, escritura de lo que cambia y `ChangeEvent` con el antes y el después **solo de las columnas cambiadas**. La diferencia: aquí `""` vacía el campo, porque enlace y modelo admiten nulo y el nombre de una entidad no.

## 2. Cambios de esquema

`V112`: el permiso `brokers:update` y su reparto a `SUPERADMIN` y `ADMIN`, con la guarda del recuento (251; `ADMIN` 249). Sin columnas nuevas.

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `interfaces` | `BrokerController` | `PATCH /api/v1/brokers/{id}` |
| `application` | `UpdateBrokerRequest` | Nuevo: `url`, `model` |
| `domain/service` | `UpdateBrokerService` | Nuevo: valida, bloquea, escribe, audita |
| `domain/repository` | `JpaBrokerQueryRepository` | Leer y bloquear uno, escribir `url`/`model` |

## 4. Contrato de API

`PATCH /api/v1/brokers/{id}`, cuerpo `{ "url": …, "model": … }`, `200` con `BrokerItem`.

## 5. Autorización

`brokers:update`, a `SUPERADMIN` y `ADMIN` explícitos (`RN-SEG-015`). El `GET` sigue público.

## 6. Auditoría

`ChangeEvent` `UPDATE` sobre `brokers`, módulo `SP`, con `before`/`after` de `url` y `model` cuando cambian.

## 7. Transaccionalidad

Una transacción; el broker se bloquea (`FOR UPDATE`) antes de comparar.

## 8. Impacto sobre otros documentos

`requirements/sp.md` (`RN-SP-039`, ficha, rutas), `security.md`, `modelo-datos.md`, `requirements.md`, `api/index.md`.

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| `null` explícito para vaciar (`JsonNullable`) | Distinguir ausente de nulo pide otra mecánica que el sistema no usa; `""` es explícito y basta |
| Editar también nombre y `advertiser` | Lo descartó el responsable; el `advertiser` reasignaría avisos ya clasificados |

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un enlace inválido llega al motor y sale `500` | Se valida antes con la misma expresión que `ck_brokers_url` |
| Recuentos del catálogo | +1 en las suites que lo cuentan; `ADMIN` +1 |

## 11. Estrategia de prueba

`BrokersIT` con `CA-SP-1020` a `CA-SP-1022`; `CA-SP-1023` en las suites de siembra; `EndpointPermissionsIT`.
