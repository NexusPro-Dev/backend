# PLAN — `RF-SP-079` Consultar mis cuentas de broker

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-079` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 08-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |

---

## 1. Enfoque

**`GET /api/v1/users/me/broker-accounts`**, en `UserController` junto a sus dos hermanas —`/me/team/broker-accounts` y `/{id}/broker-accounts`—, y **un método más en `GetBrokerAccountsService`**: `mine()`, que toma al actor de `CurrentActor` y llama a la misma `BrokerAccountQueryRepository.findByUser` que `of(id)`. **No comprueba existencia ni estructura**: la persona es la del token, y la estructura es justo lo que esta ruta no mira (`spec.md` §2). Misma respuesta, `BrokerAccountsResponse`, mismo orden.

**`/me/broker-accounts` y `/{id}/broker-accounts` no chocan**: Spring prefiere el patrón literal, y `me` no es un `uuid`. Es la misma convivencia que ya tienen `/me/clients` y `/{id}/clients`.

---

## 2. Cambios de esquema

**`V89`**, sin tablas: el permiso y su reparto.

| Cambio | Detalle |
|---|---|
| `broker-accounts:read-own` | Identificador literal `01a10e82-9000-7014-9c4f-5e7ada000006` (Art. V.11): la marca v7 de `V79`, secuencia 7014, y la serie de `broker-accounts` donde `V31` la dejó (`000005` → `000006`) |
| Reparto | **A todo rol por su tipo** —`FUNCIONARIO`, `VENDEDOR` y `CONSUMIDOR`—, como los de `V75`: también a los roles creados a mano. `ON CONFLICT DO NOTHING` |
| Guardas | Catálogo **210**; `SUPERADMIN` 210 y `ADMIN` 208; `CLIENTE` lo porta; contención de `RN-SEG-003` |

Sin auditoría, como `V31` y `V75`: nadie concede estas filas.

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `interfaces` | `UserController` | `GET /me/broker-accounts`, `@PreAuthorize("hasAuthority('broker-accounts:read-own')")`, con su `@Operation` |
| `domain/service` | `GetBrokerAccountsService` | `mine()` |
| `db/migration` | `V89__sp_mis_cuentas_de_broker.sql` | §2 |

---

## 4. Contrato de API

| Código | Cuándo |
|---|---|
| `200` | Las cuentas del actor, ordenadas; vacía si no tiene |
| `401` | Sin token (`AUTH-001`) |
| `403` | Sin `broker-accounts:read-own` (`AUTH-002`) |
| `500` | Fallo no controlado |

Sin `404`: la persona es la del token.

---

## 5. Autorización

`broker-accounts:read-own`, alcance sobre uno mismo (`RN-SEG-015`). `RN-SP-046` se enmienda para nombrar esta vía.

## 6. Auditoría

Ninguna: es una lectura.

## 7. Transaccionalidad

Una transacción de solo lectura, como `of(id)`.

## 8. Impacto sobre otros módulos y documentos

`requirements/sp.md` (`RF-SP-079`, `RN-SP-046`, `RF-SP-055`, tabla de rutas y de permisos), `security.md` §4.4, `requirements.md`, `api/index.md`. La tripleta de `RF-SP-055` no cambia: su `FA-005` sigue siendo cierto.

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Las cuentas dentro de `GET /users/me` | Sin permiso propio que diga a quién se le ofrece la vista (`RN-SEG-015`), y engorda una respuesta que leen todas las pantallas |
| Relajar `RF-SP-055` para que el titular pase | Mezcla estructura y titularidad en una comprobación que `RN-SP-046` acota a propósito, y rompe `CA-SP-914` |
| Paginar | Una persona tiene unas pocas cuentas (`RF-SP-055` §14) |

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Recuentos del catálogo | 209 → **210**, `ADMIN` 207 → **208**: las siete suites que lo cuentan, más `ALCANCE_PROPIO`, `reponerAlcancePropio`, `SystemRolesSeedIT` y `PermissionsSeedIT` |

## 11. Estrategia de prueba

Integración, en `BrokerAccountsIT` (la suite de `RF-SP-055` y `RF-SP-056`, que ya monta la cadena y las cuentas): `CA-SP-909` a `CA-SP-912` y `CA-SP-914`. `CA-SP-913` en las suites de siembra. `EndpointPermissionsIT` y `OwnScopePermissionsIT` con la ruta nueva.
