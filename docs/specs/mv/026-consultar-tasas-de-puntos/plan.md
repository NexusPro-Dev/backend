# PLAN — `RF-MV-026` Consultar las tasas de puntos vigentes

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-026` |
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

**Una sentencia**: la vigente de cada moneda con `DISTINCT ON (currency_id) … ORDER BY currency_id, valid_from DESC`, filtrando `valid_from <= now()`, unida a `currencies` para el código y para exigir `is_active`. Es `PointsRates.vigentes(instante)` de `RF-MV-025` · `plan.md` §3, y usa el mismo índice `ix_points_rates_vigente`. **Una consulta y no una por moneda**: se comprueba contando sentencias.

---

## 2. Cambios de esquema

Ninguno. La tabla y el permiso los trae `RF-MV-025` · `T-01`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `JpaPointsRateRepository` | `vigentes(instante)` | La une a `currencies`: el código de la moneda es de `SP`, y se lee como lo leen los saldos (`JpaLedgerRepository`) |
| `domain/service` | `ListPointsRatesService` | Nuevo | `@Transactional(readOnly = true)` |
| `application` | `PointsRateResponse` | Se reutiliza | El de `RF-MV-025` |
| `interfaces` | `PointsRateController` | `GET /movements/points-rates` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/points-rates` | `movements:read-points-rates` |

**Respuesta**: un arreglo de `PointsRateResponse`, **sin envoltorio de página** (`spec.md` §2.1).

| Código | Cuándo |
|---|---|
| `200` | Siempre, también vacío |
| `401` / `403` | Sin token / sin `movements:read-points-rates` |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:read-points-rates')")`. El `POST` de la misma ruta exige otro permiso (`RN-SEG-014`).

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

Solo lectura.

---

## 8. Impacto sobre otros módulos

**`SP`**: se lee `currencies` en la sentencia, como ya hace `MV` con los saldos. **El frontend**: el selector de la compra de puntos y el precio en puntos de la tienda.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Pública, como `RF-MV-009` | La tasa no la necesita nadie sin cuenta (`spec.md` §2) |
| Paginada | Una fila por moneda |
| Con el histórico bajo un parámetro | Dos operaciones en una ruta, con dos públicos distintos |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que una tasa futura aparezca como vigente | El filtro `valid_from <= now()`; hoy ninguna se fija hacia el futuro, pero la sentencia no lo supone |

---

## 11. Estrategia de prueba

Integración, `ListPointsRatesIT`: `CA-MV-301` a `CA-MV-305`, contando una sola sentencia con las estadísticas de Hibernate.
