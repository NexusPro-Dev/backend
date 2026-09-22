# PLAN — `RF-SP-062` Autenticarse no autoriza nada

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-062` |
| Especificación | [`spec.md`](spec.md), aprobada el 21-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 21-09-2026 |

---

## 1. Enfoque

**Es `RF-SP-060` con otro padre.** Aquel repartió cincuenta y un códigos entre operaciones que ya tenían permiso; este da permiso a once que no lo tenían, y el «padre» que todas portaban era estar autenticado. La forma es la misma y se hereda entera: documentos antes que código, módulo a módulo, una migración de datos con guardas que abortan, `EndpointPermissionsIT` como la prueba que sostiene la regla, y las suites que hoy llaman a esas rutas con `user(id)` a secas ganan la autoridad que `V31` da a los roles.

**Lo que este plan añade es la cobertura**: hasta hoy `EndpointPermissionsIT` admitía dos clases de excepción —«pública» y «autenticada a propósito»— y la segunda desaparece. La lista pasa a llamarse `PUBLICAS`, cada entrada debe coincidir con una ruta que `SecurityConfig` sirva con `permitAll`, y una autenticada sin anotación falla diciendo cuál. Es lo que convierte la regla en algo que no se puede olvidar.

**Y el reparto por tipo de rol**, que `V28` no necesitaba porque cada hijo iba a quien tuviera el padre. Aquí el padre lo tenía todo el mundo, y dar los once a `CLIENTE` habría hecho que el frontend le ofreciera «mi cartera» a un cliente: los tres de vendedor van a `FUNCIONARIO` y `VENDEDOR`, los ocho restantes a todos. Por **tipo** y no por código, para que un rol creado a mano reciba lo suyo sin lista que mantener.

## 2. Cambios de esquema

**Ninguno en tablas.** Una migración de datos, **`V31__sp_autenticarse_no_autoriza_nada.sql`**:

1. **Siembra los once** con identificadores literales: marca v7 del 21-09-2026 (`01a0c143-2c00`), secuencias `7001` a `700b` de una serie propia (`01a0c143-2c00-7NNN-9c4f-…`), y el sufijo de cada módulo continuando donde quedó — `SP` `5e7ad0` desde `000027` (cinco de `users`), `broker-accounts` `5e7ada` desde `000004` (dos), `MV` `5e7ad7` desde `000005` (tres), `PM` `5e7ad5` en `000026` (`packages:buy`).
2. **Reparte por tipo**: `INSERT INTO role_permissions … SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE p.id IN (…) AND (r.role_type IN ('FUNCIONARIO','VENDEDOR') OR (r.role_type = 'CONSUMIDOR' AND p.code NOT IN (los tres de vendedor)))`, con `ON CONFLICT DO NOTHING` por si alguien concedió uno a mano entre dos arranques.
3. **Guardas** que abortan: catálogo en **124**; `SUPERADMIN` 124; `ADMIN` 118; `CLIENTE` con exactamente **ocho** de los once; y **cero** parejas (rol, permiso) donde el padre no porte el permiso (`RN-SEG-003`), la misma consulta de `V28`.

Sin auditoría, como `V8`, `V22`, `V28`, `V29` y `V30`: las filas de `role_permissions` que nacen aquí no las concedió nadie — las tenía todo el mundo bajo el token.

## 3. Componentes afectados

| Componente | Cambio |
|---|---|
| `V31` | Los once permisos y su reparto |
| `UserController` | `@PreAuthorize` en `GET /me` (`users:read-own-profile`), `PATCH /me` (`users:update-own-profile`), `GET /me/sellers` (`users:read-own-sellers`), `GET /me/clients` (`users:read-own-clients`), `GET /me/team/broker-accounts` (`broker-accounts:read-own-team`), `GET /{id}/broker-accounts` (`broker-accounts:read-team-member`); la prosa de cada una deja de decir «sin permiso» y dice por qué lo lleva |
| `AuthController` | `POST /auth/password` con `users:change-own-password` |
| `MovementController` | `GET /mine` (`movements:list-own`), `GET /mine/{id}` (`movements:read-own`), `GET /mine/products` (`movements:read-own-products`) |
| `PackagePurchaseController` | `POST /{code}/purchases` con `packages:buy` |
| `GetBrokerAccountsService` | **No cambia**: el permiso abre la ruta en el controlador y el servicio sigue decidiendo el alcance (`RN-SP-046`) |
| `EndpointPermissionsIT` | `SIN_PERMISO_A_PROPOSITO` pasa a `PUBLICAS` con solo las catorce; una prueba nueva afirma que cada pública está en `SecurityConfig` como `permitAll` y que ninguna anotada lo está; `PERMISO_DE_CADA_OPERACION` gana las once |
| `PermissionsSeedIT` | Ciento veinticuatro; veinte de `users`; `ADMIN` 118; los literales de `V31`; el reparto a `CLIENTE` y a la fuerza comercial |
| `PermissionSplitMigrationIT` | Gana un caso: dos roles creados a mano antes de `V31` —uno `VENDEDOR` bajo `AGENTE`, uno `CONSUMIDOR` bajo `SUPERADMIN`— reciben once y ocho (`CA-SP-726`) |
| `PermissionIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT` | Ciento veinticuatro |
| `OpenApiContractIT` | Las once `x-required-permission` |
| Suites que llaman a las once rutas con `user(id)` a secas | Ganan la autoridad: `OwnProfileIT`, `UpdateOwnProfileIT`, `OwnCredentialsIT`, `MustChangePasswordIT`, `AccessRevocationIT`, `ClientSellersIT`, `SellerClientsIT`, `BrokerAccountsIT`, `NetworkIndicatorsIT`, `AllBrokerAccountsIT`, `MyMovementsIT`, `MyProductsIT`, `BuyPackageIT`, `ConfirmSaleIT`, `VoidSaleIT`, `SelfRegistrationIT` |
| `docs/security.md`, `requirements/sp.md`, `mv.md`, `pm.md` | Ya enmendados el 21-09-2026 (v0.67.0, v1.68.0, v0.30.0, v0.42.0) |
| Diez tripletas | La nota de Art. I.7 y la línea normativa con el código (§4) |
| `docs/api/index.md`, `requirements.md` | Al cerrar |

## 4. Enmiendas a otras tripletas

Antes de tocar controladores, como `RF-SP-060` hizo (`T-03`/`T-04`): cada spec y plan gana la nota fechada `!!! note "Enmienda de Art. I.7 — 21-09-2026, RF-SP-062"` tras la cabecera, y las líneas normativas —actor, precondiciones, `403`, criterios— nombran el código. Las líneas que dicen «sin permiso, alcance sobre uno mismo» se conservan como historia y la nota las explica.

| Tripleta | Código |
|---|---|
| `RF-SP-037` | `users:change-own-password` |
| `RF-SP-039` | `users:read-own-profile` |
| `RF-SP-044` | `users:update-own-profile` |
| `RF-SP-055` | `broker-accounts:read-team-member` (abre la ruta; `RN-SP-046` decide el alcance) |
| `RF-SP-056` | `broker-accounts:read-own-team` |
| `RF-SP-059` | `users:read-own-sellers` |
| `RF-SP-061` | `users:read-own-clients` |
| `RF-MV-008` | `movements:list-own` y `movements:read-own` |
| `RF-MV-012` | `packages:buy` |
| `RF-MV-014` | `movements:read-own-products` |

## 5. Autorización

Once `@PreAuthorize("hasAuthority('…')")`, cada uno con su código y ninguno con `hasAnyAuthority`. El `403` sale de la anotación antes de tocar la base, como en toda operación con permiso; en las de alcance propio no hay identificador que proteger de un oráculo, de modo que `403` y no `404`, como hasta hoy en las que ya tenían permiso.

**`GET /users/{id}/broker-accounts` es la única con dos capas** y las dos se conservan: la anotación exige `broker-accounts:read-team-member`; debajo, `GetBrokerAccountsService` sigue preguntando permiso amplio (`broker-accounts:read`), superior vigente o principal, y responde `404` a quien no es ninguna de las tres cosas. `CA-SP-728` lo afirma con el permiso puesto y la estructura ausente.

## 6. Auditoría

Ninguna. Es catálogo.

## 7. Transaccionalidad

`V31` corre en la transacción de Flyway: o siembra y reparte entero, o no deja nada.

## 8. Enmiendas a otros documentos

Aplicadas el 21-09-2026, antes que esta tripleta: `security.md` 0.67.0 (`RN-SEG-015`, §4.4 con los once, la nota de reparto y las catorce públicas), `requirements/sp.md` 1.68.0 (§6.1, §9, las siete fichas y la de este requerimiento), `requirements/mv.md` 0.30.0 (§4.1, §6, las fichas de `012` y `014`, y los códigos con los que nacerán `002`, `011` y `013`), `requirements/pm.md` 0.42.0 (`packages:buy` anotado). Después, las diez tripletas (§4). Al cerrar: `api/index.md`, el contrato y la matriz.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Un solo permiso `self:*` o `me:read`** para todo lo propio | Es un permiso, once operaciones: exactamente lo que `RN-SEG-014` prohíbe, y el frontend no podría ofrecer «mi cartera» sin ofrecer «mi perfil» |
| **Dar los once a todos los roles**, también a `CONSUMIDOR` | El frontend le ofrecería «mis clientes» y «las cuentas de mi equipo» a un cliente, y la regla existe precisamente para que decida por el permiso |
| **Sembrar solo a `SUPERADMIN` y `ADMIN`**, como `V22`, `V29` y `V30`, y que `RF-SP-005` conceda el resto | Dejaría a todo cliente y a toda la fuerza comercial sin ver su perfil en el primer arranque. `V28` ya sentó que una migración que reparte no deja a nadie sin lo que tenía |
| **Reparto por código de rol** (`CLIENTE`, `AGENTE`, …) en lugar de por tipo | No alcanzaría a los roles creados a mano, que quedarían sin nada; el tipo es lo que `V8` fijó como significado del rol |
| **Convertir `broker-accounts:read-team-member` en la autorización completa** y retirar la estructura del servicio | Ampliaría D-22 —quien porte el permiso vería a cualquiera— cuando la decisión fue no ampliarla; el permiso abre, la estructura acota |
| **Exigir permiso también a las públicas** | No hay actor: `login` no puede pedir un permiso a quien aún no tiene token. Son el límite de la regla |
| **Tratarlo como enmienda directa**, sin RF | Toca una regla transversal, once tripletas y una migración; `RF-SP-060` sentó que eso merece tripleta propia |

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un despliegue con **sesiones vivas**: el token que ya tenían no lleva los permisos nuevos hasta que se renueve | Los permisos se resuelven por petición desde `role_permissions` (`CurrentActor`), no van dentro del token; el primer `GET /users/me` tras `V31` ya los ve |
| Un rol creado a mano después de `V31` deja a sus personas sin perfil | Escrito en `security.md` §4.4 y en la spec `FA-001`; el frontend muestra el `403` con el código que falta |
| Que una suite de otro módulo llame a una de las once rutas con `user(id)` a secas y no esté en la lista de §3 | La suite completa lo dice: un `403` inesperado en la corrida es la señal, y se corrige concediendo la autoridad, nunca quitando la anotación |
| `V25`–`V27` reservadas para el bloque 4 de `AC` | `V31` va por encima; no se cruzan. El siguiente libre es `V32` |

## 11. Estrategia de prueba

- **`EndpointPermissionsIT`**: cobertura total (`CA-SP-723`) e inyectividad (`CA-SP-727`); las catorce públicas contrastadas con `SecurityConfig`.
- **`OwnScopePermissionsIT`** (nueva): las once rutas con el actor autenticado **sin** el permiso responden `403`, y con él, `200` o lo que corresponda (`CA-SP-724`); `GET /users/{id}/broker-accounts` con el permiso y sin estructura, `404` (`CA-SP-728`).
- **`PermissionsSeedIT`**: ciento veinticuatro, los literales, `SUPERADMIN` 124, `ADMIN` 118, `CLIENTE` ocho sin los tres de vendedor, `MANAGER`/`DIRECTOR`/`AGENTE` once (`CA-SP-725`).
- **`PermissionSplitMigrationIT`**: el caso de roles creados antes de `V31` (`CA-SP-726`).
- **`OpenApiContractIT`**: las once extensiones (`CA-SP-729`).
- **`DevelopmentSeedIT`**: las veinte personas de la semilla portan `users:read-own-profile` por su rol (`CA-SP-730`).
