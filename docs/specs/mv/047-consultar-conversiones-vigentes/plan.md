# PLAN — `RF-MV-047` Consultar la conversión vigente de cada país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-047` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Una sentencia**, como `RF-MV-026`: la vigente de cada país con `DISTINCT ON (country_id) … ORDER BY country_id, valid_from DESC`, filtrando `valid_from <= now()` y, si llega, el país; unida a `countries` para el código y el nombre y para exigir `is_active`, y a `currencies` dos veces —la local y la base— para su código y sus decimales. Es `CountryConversionRateRepository.currentAll(instante, país)` de `RF-MV-046` · `plan.md` §3, sobre el mismo índice. **Una consulta y no una por país**: se comprueba contando sentencias.

---

## 2. Cambios de esquema

Ninguno. La tabla y el permiso los trae `RF-MV-046` · `T-01`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `JpaCountryConversionRateRepository` | `currentAll(instante, país)` | Lee `countries` y `currencies` en la sentencia, como los saldos leen `currencies` |
| `domain/service` | `CountryConversionRateService` | `current` | `@Transactional(readOnly = true)` |
| `application` | `CountryConversionRateResponse` | Se reutiliza | El de `RF-MV-046` |
| `interfaces` | `CountryConversionRateController` | `GET /movements/conversion-rates` | Parámetro opcional `countryId` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/conversion-rates` | `movements:read-conversion-rates` |

**Parámetro**: `countryId`, opcional. **Respuesta**: un arreglo de `CountryConversionRateResponse`, **sin envoltorio de página** (`spec.md` §2.1).

| Código | Cuándo |
|---|---|
| `200` | Siempre, también vacío |
| `400` | `countryId` sin forma de identificador (`EX-001`) |
| `401` / `403` | Sin token / sin `movements:read-conversion-rates` |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:read-conversion-rates')")`. El `POST` de la misma ruta exige otro permiso (`RN-SEG-014`).

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

Solo lectura.

---

## 8. Impacto sobre otros módulos

**`SP`**: se leen `countries` y `currencies` en la sentencia. **El frontend**: el importe en moneda local al pagar y al retirar.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Pública | La conversión no la necesita nadie sin cuenta, como la tasa de puntos |
| Paginada | Una fila por país |
| Un `GET /{countryId}` aparte | Dos operaciones para lo mismo; el filtro opcional basta |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que una conversión futura aparezca como vigente | El filtro `valid_from <= now()` |

---

## 11. Estrategia de prueba

Integración, en la misma `CountryConversionRatesIT` que `RF-MV-046`: `CA-MV-557` a `CA-MV-561`, contando una sola sentencia con las estadísticas de Hibernate.
