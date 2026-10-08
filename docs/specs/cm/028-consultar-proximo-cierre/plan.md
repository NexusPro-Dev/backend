# PLAN — `RF-CM-028` Consultar el próximo cierre y cómo se pagará

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-028` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 08-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Un componente que sabe cuándo es el próximo turno, y una lectura de la elección de ese turno.**

```
si el cierre programado está apagado → 409 (EX-001)
turno    = ClosingSchedule.next(ahora)          — el siguiente disparo del cron, en la zona del negocio
apertura = turno − 48 h
elección = commission_payment_choices por scheduled_for = turno   — puede no haber
responder { turno, apertura, ahora ∈ [apertura, turno), modo (o AUTOMATICO), elegido, quién, cuándo }
```

**`ClosingSchedule` es lo único nuevo que importa**, y lo comparten esta tripleta, [`RF-CM-029`](../029-elegir-pago-del-cierre/plan.md) y el cierre ([`RF-CM-009`](../009-cerrar-periodo-comisiones/plan.md) §16). Lee las dos propiedades que ya existen —`nexus.commissions.closing.enabled` y `.cron`— y la zona de `BusinessCalendar`. **`CommissionClosingJob` sigue siendo el que dispara**: `ClosingSchedule` solo calcula, y el job no cambia.

**«El próximo» es `cron.next(ahora)`**, que es estrictamente posterior a `ahora`. A las 00:00:00 del día 1 el turno de ese instante ya no es el próximo: es el que corre (`FA-002`).

---

## 2. Cambios de esquema

**`V87__cm_pago_del_cierre.sql`** crea `commission_payment_choices` (`requirements/cm.md` §7.14) —`scheduled_for` único, `payment_mode` con su `CHECK`, `chosen_by` a `users`— y añade a `commission_closings` `payment_mode`, `batches_paid` y `batches_not_paid` (§7.8), con su `CHECK`: `payment_mode` presente **si y solo si** `PROGRAMADO`. **Los cierres programados que ya existen** quedan `MANUAL` —así se pagaron— con cero pagados. Siembra los dos permisos de esta tripleta y de `RF-CM-029`: **catálogo 207 → 209**, `ADMIN` 205 → 207. Es la migración de las tres tripletas; esta la nombra porque es la primera que se construye.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `PaymentMode` | Nuevo: `AUTOMATICO`, `MANUAL` | |
| `domain/service` | `ClosingSchedule` | Nuevo: `enabled()`, `next(OffsetDateTime)` y `windowOpensAt(turno)` —`turno − 48 h`— | §1 |
| `domain/repository` | `PaymentChoiceRepository` + `Jpa…` | Nuevo: `find(scheduledFor)` | SQL nativo, como `CommissionClosingRepository` |
| `domain/service` | `GetNextClosingService` | Nuevo, de solo lectura | |
| `application` | `NextClosingResponse` | Nuevo, con `@Schema(name)` | §4 |
| `interfaces` | `CommissionClosingController` | Gana `GET /next` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/commission-closings/next` | `commission-closings:read-next` |

```json
{
  "scheduledFor": "2026-11-01T05:00:00Z",
  "windowOpensAt": "2026-10-30T05:00:00Z",
  "windowOpen": false,
  "paymentMode": "AUTOMATICO",
  "chosen": false,
  "chosenBy": null,
  "chosenAt": null
}
```

| Código | Cuándo |
|---|---|
| `200` | Siempre que el cierre programado esté encendido |
| `409` | `EX-001`: está apagado |
| `401` / `403` | Sin token / sin el permiso |

**`chosenBy` es el identificador**, como `triggeredBy` en `CommissionClosingResponse`. **`/next` no choca con nada**: `/commission-closings` no tiene `/{id}`.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('commission-closings:read-next')")`; en `PERMISO_DE_CADA_OPERACION`. **No reutiliza `commission-closings:read`** (`RN-SEG-014`): consultar lo que pasó y consultar lo que va a pasar son operaciones distintas, y la segunda solo la necesita quien elige.

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Calcular el próximo turno en el frontend | Tendría que conocer el `cron` y la zona del negocio, que son configuración del servidor |
| Que `CommissionClosingJob` exponga el próximo turno | El job no existe cuando el cierre está apagado —es `@ConditionalOnProperty`—, y la suite lo apaga |
| Guardar el próximo turno en una tabla | Cambiaría con cada cambio de `cron` sin que nadie lo actualizara |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **La suite apaga el cierre**, y con él esta ruta responde `409` | La ventana se prueba con el servicio construido a mano, con un `ClosingSchedule` encendido y un `BusinessCalendar` de reloj fijo, sin contexto de Spring nuevo; por HTTP solo `CA-CM-384` y `CA-CM-385` |
| Los recuentos del catálogo | Las siete suites que lo cuentan: `PermissionIT`, `PermissionsSeedIT`, `SaleLinesPermissionSeedIT`, `TeamsPermissionsSeedIT`, `CommissionSettlementPermissionsSeedIT`, `JpaPermissionQueryRepositoryIT` y `ListPermissionsServiceIT` |

---

## 11. Estrategia de prueba

**`ClosingScheduleTest`**, unitaria: el próximo turno y la apertura con el `cron` mensual, en Bogotá, a final de un mes de 31 y de uno de 30 días, y a las 00:00:00 del día 1 (`CA-CM-383`, `FA-002`). **`NextClosingIT`**: `CA-CM-381` y `CA-CM-382` con el servicio y un reloj fijo, y una elección escrita por SQL; `CA-CM-384` y `CA-CM-385` por HTTP.
