# PLAN — `RF-SP-064` Consultar equipos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-064` |
| Especificación | [`spec.md`](spec.md), aprobada el 22-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Enfoque

**Un listado de administración con la forma que ya tiene el sistema, y una sola decisión propia: cómo se cuenta.** La paginación, el desempate por identificador, la búsqueda sin acentos y el `includeDeleted` son los de `RF-AC-002` y no se rediscuten. Lo que este plan fija es que **`memberCount` se resuelve en la misma sentencia que la página**, con una subconsulta correlacionada que entra por `ix_team_members_team_vigente`, y no con una consulta por fila ni con un `JOIN` agregado que multiplicaría filas antes de agrupar.

**Repositorio de consulta aparte del de escritura.** `TeamQueryRepository` es nativo y devuelve filas planas —`TeamRow`—, como `CourseCategoryQueryRepository`: el listado no necesita el agregado y traerlo obligaría a cargar la descripción de cada equipo para tirarla.

**Sin migración.** Las dos tablas y los dos índices los creó `V33` (`RF-SP-063`), incluido `ix_teams_busqueda`, que nació sin que nadie lo usara **para esto** y que este requerimiento estrena.

## 2. Cambios de esquema

**Ninguno.** `teams`, `team_members`, `uq_teams_name`, `ix_teams_busqueda` e `ix_team_members_team_vigente` existen desde `V33`; el permiso `teams:list`, desde `V34`.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/repository` | `TeamQueryRepository`, `JpaTeamQueryRepository` | Nuevo | `count(filtros)` y `page(filtros, offset, limit)`. Nativas, con `TeamRow` (`id`, `name`, `status`, `memberCount`, `createdAt`, `deletedAt`) |
| `application` | `ListTeamsRequest` | Nuevo | `q`, `status`, `includeDeleted`, `sort`, `page`, `size`, con sus validaciones |
| `application` | `TeamSortField` | Nuevo | La lista cerrada `name` (omisión) y `createdAt`; `resolver` rechaza lo demás con `VAL-003`, como `CourseCategorySortField` |
| `application` | `TeamItem`, `TeamPageResponse` | Nuevo | La fila publicada y la envoltura, para que springdoc emita un esquema con nombre propio |
| `domain/service` | `ListTeamsService` | Nuevo | `Pagination.resolver`, conteo y página, `@Transactional(readOnly = true)` |
| `interfaces` | `TeamController` | Modificado | `GET /api/v1/teams` con `teams:list` |
| Pruebas | `TeamListIT` | Nuevo | §11 |
| Pruebas | `EndpointPermissionsIT`, `OpenApiContractIT` | Modificado | La ruta y su extensión |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/teams?q=&status=&includeDeleted=&sort=&page=&size=` | Catálogo de administración de equipos |

**Respuesta `200`**

```json
{
  "content": [
    {
      "id": "01a0c1f0-7a00-7001-9c4f-5e7ad6000001",
      "name": "Equipo Norte",
      "status": "ACTIVO",
      "memberCount": 3,
      "createdAt": "2026-09-22T14:05:11Z"
    },
    {
      "id": "01a0c1f0-7a00-7002-9c4f-5e7ad6000002",
      "name": "Equipo Sur",
      "status": "INACTIVO",
      "memberCount": 0,
      "createdAt": "2026-09-22T14:07:40Z"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "page": 0,
  "size": 20,
  "totalIsExact": true
}
```

- **`deletedAt` es `NON_NULL`**: no aparece en los vivos y aparece en los eliminados cuando se piden, como en `RF-AC-002`.
- **`PageResponse<TeamItem>` sin `@Schema(implementation = PageResponse.class)`**, por lo que `RF-SP-056` dejó escrito: el anotado publica la envoltura cruda, y springdoc dejado solo emite `PageResponseTeamItem` con la fila dentro.
- **`status` se acepta en cualquier caja** y se publica en mayúsculas.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Paginación, `sort`, `status` o `includeDeleted` inválidos, **juntos** | `VAL-001` a `VAL-004` |
| `403` | Sin `teams:list` | — |

## 5. Autorización

| Endpoint | Permiso |
|---|---|
| `GET /api/v1/teams` | `teams:list` |

Listado y detalle son **dos permisos** (`RN-SEG-014`), y `CA-SP-747` lo afirma con la prueba negativa: con `teams:read` puesto y `teams:list` no, la lista responde `403`. Es la comprobación que impide el `hasAnyAuthority` puesto por comodidad.

**Nada por estructura ni por pertenencia.** `ListTeamsService` no pregunta quién es el actor: quien porta el permiso ve **todos** los equipos. Un manager no ve «el suyo» por esta vía, y si algún día debe verlo será un requerimiento propio con permiso de alcance propio (`RN-SEG-015`), no un filtro implícito aquí.

## 6. Auditoría

Ninguna. Es una lectura.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`, y dentro **dos sentencias** sobre la misma instantánea: el total y la página. El conteo es exacto (`BoundedCount.exacto`): los equipos se cuentan en decenas y la tabla no crece sin cota.

## 8. Impacto sobre otros módulos

Ninguno. Nadie fuera de `SP` lee `teams`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Contar los miembros con una consulta por fila** | Es el `N+1` que `CA-SP-746` existe para impedir. La subconsulta correlacionada entra por el índice parcial y cuesta lo mismo con uno que con veinte |
| **`LEFT JOIN team_members … GROUP BY`** | Funciona y obliga a agrupar por todas las columnas publicadas; con el `LIMIT` de la página, además, hay que agrupar **antes** de paginar o la página sale mal. La subconsulta dice lo mismo sin esa trampa |
| **Publicar los miembros en cada fila** | El tamaño de la respuesta pasa a ser el producto de dos cardinalidades, y la lista se usa para recorrer, no para leer. El detalle los trae (`RF-SP-065`) |
| **Ordenar por `memberCount`** | Ordenar por un valor calculado obliga a materializarlo para toda la tabla antes de paginar. Si alguien lo pide, entra como enmienda con su coste medido |
| **Un filtro `empty=true`** | El recuento ya está publicado y el frontend separa. Un filtro que se deriva de un campo devuelto es contrato que hay que sostener sin ganar nada |
| **Ordenar por fecha de alta por omisión**, como `RF-PM-002` | El producto no tiene con qué identificarse salvo su fecha; el equipo se busca por nombre y la lista es corta (spec §14.1) |
| **Incluir los eliminados por omisión** | Ningún listado del sistema lo hace, y el catálogo de administración se lee para operar, no para auditar |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Que el orden alfabético no sea determinista entre dos nombres iguales | Bajo | No pueden ser iguales entre vivos (`uq_teams_name`); con un eliminado de por medio, el desempate es el identificador, que es cronológico |
| Que la búsqueda no use `ix_teams_busqueda` por divergir la expresión | Medio — sale como lentitud, no como error | El predicado usa **la expresión del índice**, `f_unaccent(lower(name))`, y la prueba de sentencias de `CA-SP-746` deja el camino abierto a un `EXPLAIN` el día que la tabla lo justifique |
| Que `memberCount` y el detalle no cuadren | Medio — dos números que dicen cosas distintas | `CA-SP-740` los compara en la misma prueba, que es lo que `RF-AC-002` dejó escrito para `courseCount` |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-740`, `CA-SP-745` | API (`TeamListIT`) | Las columnas de la fila y el recuento de vigentes, contrastado con el detalle; un equipo con dos cerradas y una abierta; uno `INACTIVO` con miembros |
| `CA-SP-741` | API (`TeamListIT`) | Orden alfabético sin acentos con desempate por identificador; `createdAt` admitido; otro campo `400` |
| `CA-SP-742`, `CA-SP-743` | API (`TeamListIT`) | `includeDeleted` y `status`, combinados y por separado |
| `CA-SP-744` | API (`TeamListIT`) | La búsqueda por contenido, con acentos y sin ellos |
| `CA-SP-746` | Integración (`TeamListIT`) | **Número de sentencias** con página de uno y de veinte: dos en ambos casos. La forma de `CA-AC-014` |
| `CA-SP-747` | API + `EndpointPermissionsIT` | El `403` con `teams:read` puesto, y la ruta con su código |

**El fixture es el que hace verificables cuatro criterios a la vez**: cuatro equipos —dos activos, uno inactivo con miembros, uno eliminado—, nombres con y sin acentos que se solapan en una subcadena, y un manager con dos pertenencias cerradas y una abierta.

**Lo que no se prueba aquí:** quiénes son los miembros (`RF-SP-065`) y cómo entran (`RF-SP-069`); las filas de `team_members` las inserta el fixture directamente, como `RF-SP-061` insertó las `HOTLINK` que nadie escribía todavía.
