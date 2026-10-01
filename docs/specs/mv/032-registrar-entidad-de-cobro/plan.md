# PLAN — `RF-MV-032` Registrar una entidad de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-032` |
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

**Un `INSERT` en `payout_institutions`**, después de validar y de leer el país en `SP`. **La unicidad del código la decide el esquema** (`uq_payout_institutions_code`): no hay `SELECT` previo, que dejaría pasar a dos peticiones simultáneas (`CA-MV-361`). La violación se traduce a `EX-004` por el nombre de la restricción.

**Este requerimiento carga la migración de todas las cuentas de cobro**, como `RF-MV-025` cargó `V58` para la etapa 3 (`RF-MV-025` · `plan.md` §1): las tres tablas y los ocho permisos se prueban juntos, y una migración por requerimiento solo añadiría números.

---

## 2. Cambios de esquema

**La siguiente migración libre al construir** —`V61` o superior—, `V6x__mv_cuentas_de_cobro.sql`:

| Elemento | Definición | Por qué |
|---|---|---|
| `payout_institutions` | Las columnas de [`requirements/mv.md` §7.11](../../../requirements/mv.md) | `RN-MV-054` |
| `uq_payout_institutions_code`, `ck_payout_institutions_kind` | Los de `requirements/mv.md` §7.6 | |
| `ck_payout_institutions_code` | `code ~ '^[A-Z][A-Z0-9_]{1,29}$'` | La forma de los demás códigos del sistema, con el mínimo de dos |
| `ck_payout_institutions_name` | `length(btrim(name)) > 0` | Como `ck_countries_name_not_blank` |
| `fk_payout_institutions_country` | `country_id` → `countries(id)` `RESTRICT` | Los países no se borran |
| `ix_payout_institutions_country` | `(country_id, name)` | El catálogo de un país, ordenado (`RF-MV-033`) |
| `payout_accounts` | Las columnas de `requirements/mv.md` §7.12 | `RN-MV-055`, para `RF-MV-035` |
| `ck_payout_accounts_forma`, `uq_payout_accounts_numero`, `uq_payout_accounts_principal`, `ck_payout_accounts_baja` | Los de `requirements/mv.md` §7.6 | Los dos únicos son **índices parciales** |
| `fk_payout_accounts_user` | `user_id` → `users(id)` **`ON DELETE CASCADE`** | La lección de `product_links`: en producción nadie borra personas, y las suites sí |
| `fk_payout_accounts_institution` | `institution_id` → `payout_institutions(id)` `RESTRICT` | Una entidad no se borra (`RN-MV-054`) |
| `ix_payout_accounts_user` | `(user_id) WHERE deleted_at IS NULL` | Las cuentas vivas de una persona (`RF-MV-036`) |
| `withdrawal_destinations` | Las columnas de `requirements/mv.md` §7.13; **`movement_id` es la clave primaria** | `RN-MV-056`, para `RF-MV-019` |
| `fk_withdrawal_destinations_movement` | → `movements(id)` **`ON DELETE CASCADE`** | Como `payments` y `movement_entries` |
| `fk_withdrawal_destinations_account` | → `payout_accounts(id)` **`ON DELETE CASCADE`** | La cuenta solo se borra de verdad en las suites, detrás de su persona; en producción se da de baja |
| `ck_withdrawal_destinations_kind` | `institution_kind IN ('BANCO','BILLETERA_MOVIL')` | La copia tiene el mismo dominio que el original |
| Ocho permisos | `create-payout-institution`, `update-payout-institution` y `read-user-payout-accounts` a `SUPERADMIN` y `ADMIN` explícito; `read-payout-institutions`, `create-own-payout-account`, `list-own-payout-accounts`, `update-own-payout-account` y `delete-own-payout-account` por tipo de rol (`FUNCIONARIO`, `VENDEDOR`, `CONSUMIDOR`), además de `SUPERADMIN` | `requirements/mv.md` §6. Como `V58` |

**No se siembra ninguna entidad** (`spec.md` §2). **Guardas al final**, como `V58`: los ocho permisos existen y cada rol porta los que le tocan. Catálogo 172 → **180**, `ADMIN` 170 → **178**.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `SP` · `countries/application` | `CountryCatalog` | **Nueva interfaz publicada** | `find(id)` → `CountryView(id, code, name, active)`. La implementa `countries` con una lectura por clave. **Es la primera de países**: hasta hoy nadie fuera de `SP` los leía |
| `domain/models` | `PayoutInstitutionKind` | Nuevo | `BANCO`, `BILLETERA_MOVIL`; sabe si exige tipo de cuenta (`RF-MV-035`) |
| `domain/models` | `PayoutInstitution` | Nuevo | `PayoutInstitution.registrar(codigo, nombre, tipo, pais)` valida `VAL-001` a `VAL-003` y normaliza código y nombre |
| `domain/repository` | `PayoutInstitutionRepository`, `JpaPayoutInstitutionRepository` | Nuevos | `insert`, `find(id)`, `list(filtros)`, `update` (los dos últimos para `RF-MV-033` y `RF-MV-034`) |
| `domain/service` | `PayoutInstitutionService` | Nuevo | `register`: validación, país por `CountryCatalog`, `INSERT`, traducción de `uq_payout_institutions_code`, auditoría. `RF-MV-033` y `RF-MV-034` añaden `list` y `update` |
| `application` | `PayoutInstitutionRequests`, `PayoutInstitutionResponse` | Nuevos | `@Schema(name = …)` explícito: `springdoc` funde los records con el mismo nombre simple |
| `interfaces` | `PayoutInstitutionController` | Nuevo | `POST /movements/payout-institutions` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/payout-institutions` | `movements:create-payout-institution` |

**Cuerpo**: `{ "code", "name", "kind", "countryId" }`. **Respuesta**: `201` con `PayoutInstitutionResponse` —`id`, `code`, `name`, `kind`, `country` (`id`, `code`, `name`), `active`, `createdAt`— y `Location` a la entidad.

| Código | Cuándo |
|---|---|
| `201` | Registrada |
| `400` | Datos ausentes o malformados, **todos juntos** (`EX-001`) |
| `401` / `403` | Sin token / sin `movements:create-payout-institution` |
| `409` | El país está inactivo (`EX-003`) o el código ya existe (`EX-004`) |
| `422` | El país no existe (`EX-002`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:create-payout-institution')")`. **Un permiso por operación** (`RN-SEG-014`): consultar y editar llevan el suyo.

---

## 6. Auditoría

Un `ChangeEvent` sobre `payout_institutions`, `INSERT`, con los cuatro datos.

---

## 7. Transaccionalidad

`@Transactional`: la lectura del país y el `INSERT`. La carrera del código la resuelve el índice único (§1).

---

## 8. Impacto sobre otros módulos

**`SP`**: **gana `CountryCatalog`**, una interfaz de solo lectura, y la implementa. `architecture.md` §15.2 recoge la fila. Ninguna escritura.

**El frontend**: una pantalla de administración. **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Que `MV` lea `countries` con su propio SQL | Una tabla de `SP` leída desde otro módulo; la interfaz publicada es la regla (`architecture.md` §15.2) |
| Fiarse de la clave foránea para el país inexistente | Daría un `500` o un mensaje del motor en vez de `EX-002`, y no distingue el país inactivo |
| Código único por país | La copia del retiro guarda el código, y no diría a cuál de los dos se pagó (`spec.md` §2.1) |
| Sembrar los bancos de Colombia | El responsable decidió que lo llene administración; una lista sembrada sería una lista que nadie revisó |
| Una migración por requerimiento | Tres números para un solo submódulo (§1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Recuentos del catálogo de permisos | 172 → **180**: tocar todos los sitios que lo cuentan (`PermissionIT`, las suites de siembra, `EndpointPermissionsIT`) |
| Que una suite deje entidades y otra cuente | Limpiar `payout_accounts` y `payout_institutions` **al empezar y al terminar**, con códigos únicos por suite |

---

## 11. Estrategia de prueba

Integración, `PayoutInstitutionsIT`: `CA-MV-358` a `CA-MV-365`, con dos hilos en `CA-MV-361`. **La migración**: `PermissionIT` y las suites de siembra con el catálogo en 180; una prueba de esquema para los dos índices parciales de `payout_accounts` y para `ck_payout_accounts_forma` y `ck_payout_accounts_baja`, que no tienen otra prueba hasta `RF-MV-035`.
