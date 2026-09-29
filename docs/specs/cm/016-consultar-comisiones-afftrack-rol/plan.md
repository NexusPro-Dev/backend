# PLAN — `RF-CM-016` Consultar las comisiones afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-016` |
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

La mecánica es la de `GET /commission-rates` ([`RF-CM-002`](../002-consultar-tasas-comision/plan.md)): **una sentencia con `JOIN` de lectura** a `roles`, `products` y `currencies`, como `JpaCommissionRateQueryRepository`.

---

## 1. Enfoque

Una sentencia paginada sobre `afftrack_rates`, con el filtro de retiradas en el predicado y `amountAtThreshold` calculado en la propia sentencia (`threshold * amount_per_ftd`), para que el valor que se muestra y el que se paga salgan de la misma cuenta.

---

## 2. Cambios de esquema

**Ninguno**: `V54` (`RF-CM-015`), con `ix_afftrack_rates_product_role`.

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio |
|---|---|---|---|
| `CM` | `domain/repository` | `AfftrackRateQueryRepository` y adaptador | Nuevos |
| `CM` | `domain/service` | `ListAfftrackRatesService` | Nuevo |
| `CM` | `application` | `ListAfftrackRatesRequest`, `AfftrackRateItem`, `AfftrackRatePageResponse` | Nuevos, `@Schema(name)` explícito |
| `CM` | `interfaces` | `AfftrackRateController` | Gana el `GET` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/afftrack-rates` | `afftrack-rates:read` |

Filtros `productId`, `roleId`, `includeDeleted`; `page`, `size`. Orden fijo: producto (código), rol (código), `threshold` ascendente. Cada elemento lleva `deletedAt`, nulo en los vivos. Códigos: `200`, `400` todos juntos, `401`, `403`.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('afftrack-rates:read')")`; en `PERMISO_DE_CADA_OPERACION`.

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
| Agrupar los escalones por escala en la respuesta (`{producto, rol, escalones: [...]}`) | Rompe la paginación —una escala no cabe en «veinte por página»— y el orden ya los deja juntos |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un `N+1` al resolver rol y producto | Estadísticas de Hibernate (`CA-CM-220`) |

---

## 11. Estrategia de prueba

`ListAfftrackRatesIT`: `CA-CM-217` a `CA-CM-220`.
