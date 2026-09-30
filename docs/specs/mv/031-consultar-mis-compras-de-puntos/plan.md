# PLAN — `RF-MV-031` Consultar mis compras de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-031` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 30-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Un listado propio más**, con la forma de `RF-MV-008`: el alcance en la sentencia (`user_id = :actor`), **el tipo fijado en ella** (`COMPRA_PUNTOS`), los filtros de operación de `RN-MV-037` y el total acotado de siempre. **Dos sentencias por página**: la de las filas —con la tasa y el último pago por `LATERAL`, como los demás listados— y la de los pagos de las compras de la página, en una sola `IN`. Se comprueba contando sentencias (`CA-MV-350`).

---

## 2. Cambios de esquema

Ninguno.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `JpaMovementRepository` | `findOwnPointsPurchases(actor, filtro, página)` y su recuento | |
| `domain/service` | `ListMyPointsPurchasesService` | Nuevo | Validación de filtros —los errores juntos— y paginación |
| `application` | `PointsPurchaseResponse` | Se reutiliza | El de `RF-MV-027` |
| `interfaces` | `PointsPurchaseController` | `GET /movements/mine/points-purchases` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/mine/points-purchases` | `movements:list-own-points-purchases` |

**Parámetros**: `page`, `size`, `status`, `currencyId`, `code`, `from`, `to`. **Respuesta**: `PageResponse` de `PointsPurchaseResponse`.

| Código | Cuándo |
|---|---|
| `200` | Siempre, también vacío |
| `400` | Estado desconocido o periodo invertido, **juntos** |
| `401` / `403` | Sin token / sin `movements:list-own-points-purchases` |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:list-own-points-purchases')")`. El `POST` de la misma ruta exige `movements:buy-points` (`RN-SEG-014`).

---

## 6. Auditoría

Ninguna.

---

## 7. Transaccionalidad

Solo lectura.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Traerlas en «mis compras» | `RN-MV-047` la dejó en ventas el 26-09-2026 |
| Un detalle propio | Repetiría la fila (`spec.md` §2.1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un N+1 sobre los pagos, que devuelve el mismo JSON | Estadísticas de Hibernate; `CA-MV-350` |

---

## 11. Estrategia de prueba

Integración, `ListMyPointsPurchasesIT`: `CA-MV-344` a `CA-MV-350`.
