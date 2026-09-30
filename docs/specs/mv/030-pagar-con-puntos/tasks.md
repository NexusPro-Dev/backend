# TASKS — `RF-MV-030` Pagar una compra con puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-030` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/compra-de-puntos` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | En la migración de la etapa: rechazar los pagos `POINTS` pendientes, con su guarda | `RF-MV-025` `T-01` | Sobre la base de desarrollo, ninguno queda pendiente | Pendiente |
| `T-02` | Extraer `SaleConfirmation` de `ConfirmSaleService` | — | `ConfirmSaleIT` y `ConfirmSaleConcurrencyIT` en verde **sin tocarlas** | Pendiente |
| `T-03` | `PointsAmount.costo` (hacia arriba) y `PointsPayment` | `T-02`, `RF-MV-025` `T-04` | Unitarias del redondeo | Pendiente |
| `T-04` | `BuyByHotlinkService`, `BuyPackageService`, `RetryPaymentService` llaman a `PointsPayment` | `T-03` | | Pendiente |
| `T-05` | `RegisterSaleService` y `PublishedRegistrationSaleRegistrar` rechazan `POINTS` | — | | Pendiente |
| `T-06` | `PaymentResponse.points` | `T-03` | Nulo salvo en pagos con puntos | Pendiente |
| `T-07` | `PayWithPointsIT`: `CA-MV-332` a `CA-MV-343` | `T-01` a `T-06` | `CA-MV-340` con dos hilos | Pendiente |
| `T-08` | Contrato: prosa de cada `@Operation` afectada releída; `requirements.md` | `T-07` | | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-MV-025`**: `T-02` y `T-05` pueden ir primero, no dependen de nada; luego `T-01` → `T-03` → `T-04` → `T-06` → `T-07` → `T-08`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-332` a `CA-MV-336` | `T-02`, `T-03`, `T-04`, `T-07` |
| `CA-MV-337` a `CA-MV-341` | `T-03`, `T-04`, `T-07` |
| `CA-MV-342` | `T-05`, `T-07` |
| `CA-MV-343` | `T-01`, `T-07` |

---

## 4. Bloqueos

**`RF-MV-025`**, que trae la cuenta `PUNTOS_EMITIDOS`, el evento `PAGO` y la tasa. Para probarlo hace falta llenar una cuenta de puntos: las suites lo hacen por `RF-MV-027` y `RF-MV-028`, o sembrando el asiento.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los doce criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
