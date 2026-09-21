# PLAN — `RF-SP-061` Consultar los clientes de un vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-061` |
| Especificación | [`spec.md`](spec.md), aprobada el 21-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 21-09-2026 |
| Enmendado | 21-09-2026 — exige **`users:read-own-clients`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` (la ruta `/me`); a `CONSUMIDOR` no |

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`users:read-own-clients`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo, a `CONSUMIDOR` no.



## 1. Enfoque

**Una lectura sobre una tabla que ya existe, con un permiso que no.** `RF-SP-059` pagó la mudanza —`V20`, cinco lecturas cambiadas de sitio— y dejó `client_sellers` con un índice por `seller_id` que nadie usaba; este plan lo usa. Lo único que se siembra es `users:read-clients`, y el resto es la forma que `RF-SP-059` ya fijó para el mismo par de rutas: una sobre el actor, otra por identificador, en `UserController`, con el repositorio nativo de `client_sellers` ganando dos métodos.

**Se hereda de `RF-SP-059` sin repetirlo**: el repositorio propio y nativo sin entidad JPA (su plan §1), el controlador único para `/users` (§1), los dos modelos de autorización y el `403` (§5), la ausencia de auditoría (§6). Lo que este plan decide es lo que allí se decidió al revés y la spec §14 justifica: paginación, orden, `id` y estado.

**Se pagina como `RF-SP-056`**, no como `RF-SP-042`: la respuesta es una `PageResponse<SellerClientItem>` a secas, sin envoltura con la persona consultada, porque quien pide `/users/{id}/clients` ya sabe de quién pregunta y la cartera no tiene «superior» que mostrar al lado.

## 2. Cambios de esquema

**Ninguno en tablas.** Una migración de datos, **`V30__sp_semilla_permiso_users_read_clients.sql`**, con la forma de `V29`:

- Un `INSERT` en `permissions` con identificador literal **`01a0c143-2c00-7002-9c4f-5e7ad0000026`**: la marca v7 del 21-09-2026 que `V29` estrenó, secuencia `7002`, y la serie de `SP` continuando en hex donde `users:read-sellers` la dejó (`…000025`).
- `role_permissions` para `SUPERADMIN` y `ADMIN`, explícitas. A `CLIENTE` no.
- Dos guardas: el catálogo en **113** y el permiso asociado a **2** roles. Sin auditoría, como `V8`, `V22`, `V28` y `V29`.

**No hay reparto de padre** (spec §10): la operación no existía y ningún rol creado a mano la tenía bajo otro nombre. Es la diferencia con `V28`, y por eso la migración es la de `V29` y no la de `V28`.

## 3. Componentes afectados

| Componente | Cambio |
|---|---|
| `ClientSellerRepository` + `JpaClientSellerRepository` | `countClientsOf(sellerId, origin)` y `findClientsOf(sellerId, origin, offset, limit)`: `JOIN users c ON c.id = cs.client_id AND c.deleted_at IS NULL`, `WHERE cs.seller_id = :vendedor [AND cs.origin = :origen]`, `ORDER BY cs.created_at DESC, c.username`. Una fila nueva, `SellerClientRow`, con el estado del cliente |
| `SellerClientItem` (`users/application`) | La fila publicada: `id`, `username`, `firstName`, `lastName`, `status`, `origin`, `principal`, `linkedAt`. `principal` derivado de `origin` como en `SellerItem` |
| `ClientOrigin` (`users/application`) | El filtro: resuelve `REGISTRO`/`HOTLINK` sin distinguir mayúsculas y rechaza lo demás con `VAL-001`, como `CourseCategorySortField.resolver` rechaza un campo desconocido |
| `GetSellerClientsService` | `mine(origin, page, size)` y `of(sellerId, origin, page, size)`: `Pagination.resolver`, el `404` del identificador, conteo y página |
| `UserController` | `GET /me/clients` sin `@PreAuthorize` con el motivo escrito; `GET /{id}/clients` con `users:read-clients` |
| `V30` | El permiso |
| `EndpointPermissionsIT` | `/me/clients` en `SIN_PERMISO_A_PROPOSITO`; `/{id}/clients` en `PERMISO_DE_CADA_OPERACION` |
| `PermissionsSeedIT`, `PermissionIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT` | Ciento trece; quince de `users`; `ADMIN` 107; el literal de `V30` |
| `OpenApiContractIT` | La `x-required-permission` de la ruta |
| `docs/api/index.md`, `security.md` §4.4, `requirements/sp.md` §6.1 y §9, `requirements.md` | Ya enmendados los tres primeros el 21-09-2026 (v1.66.0, v0.66.0); la matriz al cerrar |

## 4. Contrato de API

`GET /api/v1/users/me/clients?origin=&page=&size=` · `GET /api/v1/users/{id}/clients?origin=&page=&size=`

```json
{
  "content": [
    {
      "id": "01a0c1a2-3b40-7001-9c4f-5e7ad4000031",
      "username": "cliente7",
      "firstName": "Marta",
      "lastName": "Lozano",
      "status": "FTD_PENDIENTE",
      "origin": "REGISTRO",
      "principal": true,
      "linkedAt": "2026-09-20T15:02:11Z"
    },
    {
      "id": "01a0b9c4-1d00-7001-9c4f-5e7ad4000022",
      "username": "cliente2",
      "firstName": "Jorge",
      "lastName": "Pinto",
      "status": "ACTIVO",
      "origin": "HOTLINK",
      "principal": false,
      "linkedAt": "2026-09-17T18:40:55Z"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "page": 0,
  "size": 20,
  "totalIsExact": true
}
```

- **`PageResponse<SellerClientItem>` sin `@Schema(implementation = PageResponse.class)`**, por lo que `RF-SP-056` dejó escrito en el controlador: el anotado publica la envoltura cruda y springdoc, dejado solo, emite `PageResponseSellerClientItem` con la fila dentro.
- **Mismo cuerpo en las dos rutas.**
- **`status` es el del cliente**, el mismo valor que `RF-SP-025` publica; no el del vínculo, que no tiene estado.
- **`linkedAt` es `created_at`** de `client_sellers`, con el mismo significado que en `RF-SP-059`.
- **`origin` como parámetro se acepta en cualquier caja** y se publica en mayúsculas.

## 5. Autorización

- **`/me/clients`: `@PreAuthorize("hasAuthority('users:read-own-clients')")` desde el 21-09-2026** (`RF-SP-062`); hasta entonces sin `@PreAuthorize`, con el motivo al lado y declarada en `EndpointPermissionsIT`. El actor sale del token; no hay identificador que validar.
- **`/{id}/clients`: `@PreAuthorize("hasAuthority('users:read-clients')")`**. El `403` sale antes de tocar la base; el `404` solo con el permiso puesto y una persona inexistente o eliminada. Ni `users:read`, ni `users:read-team`, ni `users:read-sellers` lo abren, y la prueba lo afirma con los tres (`CA-SP-718`), porque un `hasAnyAuthority` puesto por comodidad sería exactamente lo que `RN-SEG-014` prohíbe.
- **Nada por estructura.** `GetSellerClientsService` no pregunta por `user_supervisors` ni por `RN-SP-046`; `CA-SP-720` lo afirma con un director sobre su agente.

## 6. Auditoría

Ninguna. Es una lectura.

## 7. Transaccionalidad

`@Transactional(readOnly = true)` en el servicio, y dentro dos sentencias —conteo y página— sobre la misma instantánea. El conteo es exacto (`BoundedCount.exacto`): va por `ix_client_sellers_vendedor` y una cartera se cuenta en cientos; el techo de `RF-MV-006` existe para tablas que crecen sin cota, y esta crece con los registros.

## 8. Enmiendas a otros documentos

Aplicadas el 21-09-2026, antes que esta tripleta: `requirements/sp.md` 1.66.0 (la ficha con lo que se publica y por qué, §6.1 y §9) y `security.md` 0.66.0 (`users:read-clients` en §4.4, ciento trece). Ninguna otra tripleta cambia: esta lectura no invalida ningún criterio ajeno.

Al terminar: `api/index.md` con las dos rutas, el contrato regenerado y la matriz. Y `docs/para-el-backend.md` del frontend cierra `R-45` cuando lo consuma; no es de este repositorio.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Reutilizar `users:read-sellers`** para las dos lecturas de `client_sellers` | Es un permiso, dos operaciones: lo que `RN-SEG-014` prohíbe. Y la razón de fondo la dio el responsable del proyecto: el frontend decide qué vista mostrar por el código, y «vendedores de un cliente» y «clientes de un vendedor» son dos vistas |
| **Autorizar al superior comercial por estructura**, como `RN-SP-046` | D-22 tiene una excepción y `security.md` §5 la declara única. Abrir otra trae `404` en vez de `403`, recorrido de `user_supervisors` y una prueba de tres niveles; quien deba ver carteras ajenas porta el permiso (spec §10) |
| **Devolver la cartera dentro de `CommercialStructureResponse`**, junto al equipo | Mezclaría otra vez equipo y cartera, que es lo que el 18-09-2026 separó; y la cartera no tiene «superior» que mostrar |
| **Sin paginar, como `RF-SP-059`** | Un cliente tiene un puñado de vendedores; un vendedor puede tener cientos de clientes. `RF-SP-059` envolvió en `content` precisamente «para dejar sitio a paginar sin romper a nadie» |
| **Ordenar principal primero, como `RF-SP-059`** | Allí el orden contesta «¿quién me trajo?»; aquí, «¿a quién tengo?», y lo nuevo es lo que se atiende. El filtro `origin` separa los propios cuando eso es lo que se quiere |
| **Incluir a los clientes eliminados con su estado** | Su `id` no abriría nada (`RF-SP-026` responde `404`) y `RF-SP-025` tampoco los lista: una fila con enlace roto. La fila de `client_sellers` queda y volvería a verse al restaurar |
| **Un `hasAnyAuthority('users:read-clients','users:read')`** «por si acaso» | Es la forma silenciosa de romper `RN-SEG-014`: la inyectividad de `EndpointPermissionsIT` la detectaría, pero la prueba negativa con `users:read` (`CA-SP-718`) es la que dice por qué |

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que `V30` choque con una migración de `backend-ff` (`V25`–`V27` reservadas para el bloque 4 de `AC`) | Los números no se cruzan: `V30` va por encima de `V29`, y `V25`–`V27` siguen libres para `AC`. Queda avisado en la memoria del proyecto y en el PR |
| Que el orden `created_at DESC` no sea estable entre dos filas con la misma marca | El desempate es `username`, único en `users` |
| Que la cartera de un vendedor con miles de clientes haga lento el conteo | Va por índice y es una consulta; si algún día la cartera se mide en decenas de miles, el techo de `BoundedCount` está a una línea |

## 11. Estrategia de prueba

- **Integración del endpoint** (`SellerClientsIT`): `CA-SP-714` a `CA-SP-721`, con un vendedor que registró dos clientes y una fila `HOTLINK` insertada a mano, un cliente desactivado, uno eliminado, un director encima del vendedor y un agente subordinado que no debe aparecer.
- **Catálogo**: `PermissionsSeedIT` (ciento trece, quince de `users`, el literal de `V30`, `ADMIN` 107), `PermissionIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`.
- **Rutas**: `EndpointPermissionsIT` con `/me/clients` como autenticada sin permiso y `/{id}/clients` con `users:read-clients` (`CA-SP-722`); `OpenApiContractIT` con la extensión.
- **Lo que no se prueba aquí**: la escritura de vínculos, que es de `RF-SP-045` y de las compras.
