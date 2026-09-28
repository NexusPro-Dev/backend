# PLAN — `RF-CM-014` Consultar el desenlace de las líneas de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-014` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 28-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

La mecánica es la de [`RF-CM-010`](../010-consultar-lotes-comision/plan.md): una sentencia propia y una lectura en bloque por módulo ajeno.

---

## 1. Enfoque

**Una sentencia sobre `commission_accruals`, y los datos de la línea pedidos a `MV` de una vez por página.** El problema es el filtro por **venta** y por **producto**, que no están en `commission_accruals`: se resuelven **antes** de la sentencia, preguntando a `MV` qué líneas son de esa venta o de ese producto (`CommissionableLines.detailIdsOf(movementId, productId)`) y filtrando por ese conjunto. **No se copian en la tabla**: harían crecer `commission_accruals` con datos que no deciden nada, solo para filtrar.

---

## 2. Cambios de esquema

**Ninguno**: `V51`. Un índice de apoyo al orden y al filtro, `ix_commission_accruals_outcome_updated` sobre `(outcome, updated_at DESC)`, **se añade a `V51`**.

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio | Nota |
|---|---|---|---|---|
| `MV` | `application` | `CommissionableLines` | Gana `detailIdsOf(UUID movementId, UUID productId)` | Cualquiera de los dos nulo, no filtra por él |
| `CM` | `domain/repository` | `CommissionAccrualQueryRepository` y adaptador | Nuevo | |
| `CM` | `domain/service` | `ListCommissionAccrualsService` | Nuevo | Valida, resuelve el filtro por `MV`, lee y compone |
| `CM` | `application` | `CommissionAccrualItem`, `CommissionAccrualPageResponse`, `ListCommissionAccrualsRequest` | Nuevos | `@Schema(name)` explícito |
| `CM` | `interfaces` | `CommissionAccrualController` | Nuevo | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/commission-accruals` | `commission-accruals:read` |

Filtros `outcome`, `movementId`, `productId`, `from`, `to`; orden `updated_at` descendente. Códigos: `200`, `400` todos juntos, `401`, `403`.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('commission-accruals:read')")`; en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

**`MV`** gana `detailIdsOf` en `CommissionableLines`. Sin enmienda documental (`requirements/mv.md` v0.49.0).

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Copiar venta y producto en `commission_accruals` | Datos que no deciden nada, solo para filtrar |
| Incluir las líneas sin desenlace, calculándolas | Sería el barrido en cada lectura; `spec.md` §2.1 las deja fuera |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un `N+1` al componer | Estadísticas de Hibernate (`CA-CM-208`) |

---

## 11. Estrategia de prueba

`ListCommissionAccrualsIT`: `CA-CM-203` a `CA-CM-208`, con rechazos provocados por tasas que suman más del 100 % y corregidos antes de un cierre real.
