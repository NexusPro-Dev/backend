# PLAN — `RF-MV-050` Conciliar los cobros pendientes con la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-050` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

---

## 1. Enfoque

**Un `@Scheduled(cron = "${payretailers.reconcile.cron}")` en `LocalChargeSweep`**, que toma el bloqueo consultivo de la transacción (`pg_try_advisory_xact_lock`) como el cierre de comisiones, lee un lote de pagos con `PaymentRepository.localChargesToReconcile(antesDe, lote)` —sobre `ix_payments_cobro_local_pendiente`— y llama a **`LocalChargeReconciler.conciliar(pago)`** de `RF-MV-049` por cada uno. **Ninguna lógica de desenlace vive aquí**: el barrido solo elige por quién preguntar.

---

## 2. Cambios de esquema

Ninguno: el índice lo trae `RF-MV-048` · `T-01`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `domain/service` | `LocalChargeSweep` | Nuevo |
| `domain/repository` | `PaymentRepository` | `localChargesToReconcile(antesDe, lote)` |
| `infrastructure` | `PayRetailersSettings` | `reconcileCron` (`0 */5 * * * *`) y `reconcileAfter` (`PT10M`) |

**«Sin noticias»** se mide por el último aviso guardado de ese cobro o, si no hay, por cuándo se abrió el pago.

---

## 4. Contrato de API

Ninguno.

---

## 5. Autorización

Ninguna.

---

## 6. Auditoría

La del desenlace de cada pago, como en `RF-MV-049`.

---

## 7. Transaccionalidad

La selección, en una transacción corta con el bloqueo consultivo. Cada pago, por `conciliar`, en la suya.

---

## 8. Impacto sobre otros módulos

Ninguno nuevo.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Solo el aviso | PayRetailers no reentrega (`RN-MV-064`) |
| ShedLock u otra librería | El bloqueo consultivo ya resuelve las instancias, como en `CM` |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Muchas preguntas a la pasarela | Lote acotado y diez minutos de espera |

---

## 11. Estrategia de prueba

Integración, `LocalChargeSweepIT`, llamando al barrido directamente con un reloj fijo y un doble de la pasarela: `CA-MV-620` a `CA-MV-624`.
