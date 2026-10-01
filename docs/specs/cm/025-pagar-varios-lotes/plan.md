# PLAN — `RF-CM-025` Pagar varios lotes de una vez

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-025` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 01-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

Pagar un lote es [`RF-CM-011`](../011-marcar-lote-pagado/plan.md), **y se reutiliza entero**: su bloqueo, su abono, su marca y su auditoría.

---

## 1. Enfoque

**Un bucle sin transacción propia que llama a `PayCommissionBatchService.pay` una vez por lote.**

```
validar la lista (VAL-001 a VAL-003, juntos)
para cada id, en orden:
    try   pay(id)                         — su propia transacción: @Transactional del bean
          → pagado(id, code, paidAmount, movementId)
    catch BusinessRuleException | ResourceNotFoundException e
          → no pagado(id, e.código, e.mensaje)
    catch RuntimeException e
          → log.error; no pagado(id, "EX-001", genérico)
```

**Que el servicio de esta tripleta NO sea `@Transactional` es lo que hace verdad `RN-CM-049`**: cada llamada a `pay` cruza el proxy de Spring y abre y confirma su propia transacción. Si este servicio la abriera, la excepción del tercer lote marcaría la transacción entera para revertir y se llevaría los dos primeros pagos.

**El orden es el de la petición** (`CA-CM-309`). Cada lote se bloquea en su propia transacción y la suelta antes del siguiente, de modo que no hay dos lotes bloqueados a la vez y el orden no importa para el interbloqueo.

**`CA-CM-311` no necesita nada nuevo**: es `CA-CM-192` de `RF-CM-011`. El segundo que llega al mismo lote espera al `FOR UPDATE`, lee `PAGADO` y su `pay` lanza `EX-003`, que aquí se convierte en una fila.

---

## 2. Cambios de esquema

**`V60__cm_pagar_varios_lotes.sql`**: el permiso `commission-batches:pay-batches`, serie de `CM` a continuación de `V59` (`000031`), a `SUPERADMIN` y `ADMIN` **explícitos**. **Catálogo 171 → 172**, `ADMIN` 169 → 170.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `PayCommissionBatchesRequest` | Nuevo | `batchIds: [UUID]` |
| `application` | `CommissionBatchesPaymentResponse` | Nuevo | `results` —por lote: `batchId`, `paid`, `code`, `paidAmount`, `movementId`, `reasonCode`, `reason`— y `paidCount`, `notPaidCount`. `@Schema(name)` en los dos `record`s |
| `domain/service` | `PayCommissionBatchesService` | Nuevo, **sin `@Transactional`** | §1 |
| `interfaces` | `CommissionBatchController` | Gana `POST /payments` | |

**La ruta es `/commission-batches/payments` y no `/{id}/payment`**: un literal de un segmento bajo el recurso, como `/closing` y `/mine`. No choca con `/{id}`, que no tiene `POST` de un segmento.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/commission-batches/payments` | `commission-batches:pay-batches` |

```json
{ "batchIds": ["…", "…"] }
```

| Código | Cuándo |
|---|---|
| `200` | Siempre que la lista sea válida, **aunque no se pague ninguno** (`CA-CM-312`): el resultado está en cada fila |
| `400` | `VAL-001` a `VAL-003`, juntos |
| `401` / `403` | Sin token / sin el permiso |

**`200` y no `207`**: el contrato del proyecto no usa `Multi-Status`, y la respuesta ya dice lote a lote qué pasó.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('commission-batches:pay-batches')")`; en `PERMISO_DE_CADA_OPERACION`. **No basta `commission-batches:pay`**: `PayCommissionBatchService.pay` no comprueba permisos —los comprueba su ruta—, de modo que llamarlo desde aquí no exige el de la ruta suelta, y es correcto: el permiso de esta operación es el suyo.

---

## 6. Auditoría

**La de cada pago**, que escribe `pay` (`CA-CM-313`). No se audita la petición en conjunto: no cambia nada que no esté ya en las constancias de cada lote.

---

## 7. Transaccionalidad

**Una transacción por lote**, la de `pay` (§1). La petición no tiene transacción propia.

---

## 8. Impacto sobre otros módulos

**Ninguno**: `MV` recibe un abono por lote, como hoy.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| `TransactionTemplate` con `REQUIRES_NEW` dentro de un servicio transaccional | Lo mismo con más piezas: el proxy de `pay` ya da la transacción por lote |
| Pagar en paralelo | Más rápido con cien lotes, y con un orden de respuesta que dependería de los hilos. Cien pagos en serie son segundos |
| Validar todos los lotes antes de pagar ninguno | Daría una foto que podría cambiar antes de pagar; el pago ya comprueba con el lote bloqueado |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que alguien anote `@Transactional` en el servicio «por coherencia» | Su Javadoc lo dice, y `CA-CM-308` falla si ocurre: el pago bueno se revertiría con el malo |
| Los recuentos del catálogo | Las mismas suites que `V59`, más `CommissionSettlementPermissionsSeedIT` |

---

## 11. Estrategia de prueba

`PayCommissionBatchesIT`: `CA-CM-306` a `CA-CM-314`, con lotes de devengo y cierre reales, como `PayCommissionBatchIT`. `CA-CM-308` provoca el fallo del abono como `CA-CM-193` —un total negativo con el `CHECK` retirado—. `CA-CM-311` con dos hilos.
