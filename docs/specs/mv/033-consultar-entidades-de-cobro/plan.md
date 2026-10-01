# PLAN — `RF-MV-033` Consultar las entidades de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-033` |
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

---

## 1. Enfoque

**Una sentencia**: `payout_institutions` unida a `countries` para el código y el nombre del país, con los tres filtros como condiciones opcionales y `ORDER BY countries.name, payout_institutions.name, payout_institutions.id`. El último criterio desempata, para que el orden sea estable.

**Leer `countries` en el `JOIN` y no por `CountryCatalog`** es la excepción que ya hacen los listados de `MV` con los nombres de usuario y de producto: es una proyección de solo lectura en la misma sentencia, y resolver país por país sería un N+1. **El dato que decide algo —si el país existe y está activo— sigue pasando por la interfaz**, en `RF-MV-032`.

---

## 2. Cambios de esquema

Ninguno: `ix_payout_institutions_country` lo trae `RF-MV-032`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `PayoutInstitutionRepository` | Gana `list(pais, tipo, estado)` | |
| `domain/service` | `PayoutInstitutionService` | Gana `list` | Valida los filtros |
| `application` | `PayoutInstitutionResponse` | Reutilizada | `createdAt` incluido |
| `interfaces` | `PayoutInstitutionController` | Gana `GET` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/payout-institutions` | `movements:read-payout-institutions` |

**Parámetros**: `countryId`, `kind` (`BANCO` · `BILLETERA_MOVIL`), `status` (`ACTIVA` · `INACTIVA` · `TODAS`, por omisión `ACTIVA`). **Respuesta**: `200` con un arreglo de `PayoutInstitutionResponse`, sin envoltorio de página, como `RF-MV-009`.

| Código | Cuándo |
|---|---|
| `200` | La lista, quizá vacía |
| `400` | Un filtro malformado (`EX-001`) |
| `401` / `403` | Sin token / sin `movements:read-payout-institutions` |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:read-payout-institutions')")`. Lo siembra la migración de `RF-MV-032` por tipo de rol.

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

**El frontend**: el selector del formulario de cuenta y la pantalla de administración del catálogo. **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Filtrar por el país de quien pregunta | Administración no podría ver otros países sin un segundo permiso y una segunda ruta (`spec.md` §2.1) |
| Dos rutas, una de administración y otra para las personas | Dos permisos para la misma lectura; `RN-SEG-014` pide uno por operación, no uno por público |
| Paginar | Un catálogo de decenas de filas (`spec.md` §2.1) |
| Pública, como `RF-MV-009` | No la necesita nadie sin sesión |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que el catálogo crezca hasta necesitar página | Añadir `page` y `size` sería compatible: la respuesta cambiaría de forma, y quedaría declarado como cambio rompedor |

---

## 11. Estrategia de prueba

Integración, `ListPayoutInstitutionsIT`: `CA-MV-366` a `CA-MV-370`, con entidades de dos países, de los dos tipos y en los dos estados. `CA-MV-370` con un usuario de tipo `CONSUMIDOR`.
