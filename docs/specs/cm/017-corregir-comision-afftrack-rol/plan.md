# PLAN — `RF-CM-017` Corregir el límite o el valor de una comisión afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-017` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 29-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 29-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

La mecánica es la de [`RF-CM-003`](../003-corregir-valor-tasa/plan.md): `PATCH` con presencia explícita de campos, comparación por valor y no por escala, y auditoría solo si cambia algo.

---

## 1. Enfoque

El cuerpo se deserializa **distinguiendo ausente de nulo**, con el mismo mecanismo que `UpdateCommissionRateRequest`, porque `EX-002` y `VAL-002` necesitan saber qué llegó. Los campos inmutables se aceptan en el cuerpo **solo para rechazarlos**. La comparación del valor usa `compareTo` y no `equals` (`FA-001`).

---

## 2. Cambios de esquema

**Ninguno**: `V54`. El choque de límites lo cierra `uq_afftrack_rates_product_role_threshold` en el `UPDATE`, y el adaptador lo traduce a `409` como en el alta.

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio |
|---|---|---|---|
| `CM` | `domain/models` | `AfftrackRate` | Gana `corregir(threshold, amount)`, que dice si cambió algo |
| `CM` | `domain/service` | `UpdateAfftrackRateService` | Nuevo |
| `CM` | `application` | `UpdateAfftrackRateRequest` | Nuevo, `@Schema(name)` |
| `CM` | `interfaces` | `AfftrackRateController` | Gana el `PATCH` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `PATCH` | `/api/v1/afftrack-rates/{id}` | `afftrack-rates:update` |

Cuerpo: `threshold`, `amountPerFtd`, ambos opcionales; `roleId` y `productId` se rechazan si llegan. Respuesta `200` con `AfftrackRateResponse`. Códigos: `200`, `400`, `401`, `403`, `404`, `409`.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('afftrack-rates:update')")`; en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

`ChangeAction.UPDATE` con el antes y el después, solo si `corregir` devuelve que cambió algo.

---

## 7. Transaccionalidad

`@Transactional`, con la fila leída `FOR UPDATE` para que dos correcciones del mismo escalón no escriban la una sobre la otra con un antes que ya no es el de la base.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Corregir el límite como retirar y registrar por dentro | La liquidación pasada apunta al escalón por su identificador (`threshold_rate_id`); cambiárselo por debajo no aporta nada, y la liquidación ya copió el límite que pagó |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que una corrección toque lo pagado | `CA-CM-222` corre un cierre, corrige y compara |

---

## 11. Estrategia de prueba

`UpdateAfftrackRateIT`: `CA-CM-221` a `CA-CM-226`. `CA-CM-222` depende del cierre de `RF-CM-020`.
