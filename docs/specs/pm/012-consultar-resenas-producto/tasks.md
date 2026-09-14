# TASKS — `RF-PM-012` Consultar las reseñas de un producto, sin autenticación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-012` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 14-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 14-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `domain/repository/ProductCommentQueryRepository` + `JpaProductCommentQueryRepository`: `findPageByProduct(productId, slice)` con el **`JOIN users`** de `plan.md` §4.1 seleccionando **solo `first_name` y `last_name`**, y `countLiveByProduct(productId)` | `RF-PM-009 · T-01` | Integración: orden `created_at DESC, id DESC`; no trae retiradas; trae las de autores inactivos; dos con el mismo instante no se repiten entre páginas (`CA-PM-211`) | **Hecha el 14-09-2026** |
| `T-02` | `application/ProductCommentPublicItem` con `author { firstName, lastName }` como registro **propio** | — | El contrato declara exactamente seis campos en el elemento y dos en `author` | **Hecha el 14-09-2026** |
| `T-03` | `domain/service/GetProductCommentsService`: identificador como **texto** convertido dentro (`VAL-001` → `EX-001`), producto comprable por `isPurchasable` (`EX-001`), página y total. **Un solo punto de salida para el `404`** | `T-01`, `T-02`, `RF-PM-009 · T-06` | `CA-PM-205`: los cuatro casos lanzan la misma excepción con el mismo mensaje | **Hecha el 14-09-2026** |
| `T-04` | `ProductCommentController`: `GET /api/v1/products/{id}/comments`, `@PathVariable String id`, **sin `@PreAuthorize`**, `page`/`size` por `Pagination.resolver` | `T-03` | `CA-PM-202`, `CA-PM-206`, `CA-PM-210` | **Hecha el 14-09-2026** |
| `T-05` | **Seguridad en dos sitios**: la ruta en la lista **por método** de `SecurityConfig` —`GET`, patrón de un segmento, junto a los catálogos, **con su motivo escrito al lado**— y en `EndpointPermissionsIT` como pública a propósito, **más** las tres que no se abren: `POST` de la misma ruta, `/comments/mine`, `PATCH`/`DELETE` de `/comments/{id}` responden `401` sin token | `T-04` | Sin lo primero, `401`; sin lo segundo, `EndpointPermissionsIT` falla. Si el patrón alcanza a `/mine`, la prueba de `401` lo destapa | **Hecha el 14-09-2026** |
| `T-06` | **Límite de tasa por familia**: en `RateLimitFilter.reglaDe`, `GET` sobre `/api/v1/products/*/comments` → `Regla(familia, ajustes.publicCatalog())`. Sin política nueva | `T-05` | `CA-PM-208` en `RateLimitIT`: el exceso recibe `429`, y **tres productos distintos** topan con la misma cota | **Hecha el 14-09-2026** |
| `T-07` | **LA PRUEBA DEL CUERPO ENTERO** (`ProductCommentListIT`): cada elemento comparado contra la forma esperada, sin ningún campo más | `T-04` | `CA-PM-203`. Es la que sostiene `RN-PM-030`: un `userId` o `username` añadido «para el avatar» la rompe | **Hecha el 14-09-2026** |
| `T-08` | Prueba del `404` uniforme: inactivo, retirado, inexistente y **malformado**, comparando el cuerpo de los cuatro | `T-04` | `CA-PM-205` | **Hecha el 14-09-2026** |
| `T-09` | Pruebas de los criterios restantes: retiradas fuera y autores inactivos dentro, misma respuesta con token —incluido el del autor de una de ellas— | `T-04` | `CA-PM-204`, `CA-PM-207` | **Hecha el 14-09-2026** |
| `T-10` | Prueba de **número de sentencias**: tres; una cuando el producto no procede; **igual con una página de uno que de veinte** | `T-04` | `CA-PM-209` | **Hecha el 14-09-2026** |
| `T-11` | Documentación OpenAPI. **La prosa dice** que es público, que del autor solo viaja el nombre, que el `404` no distingue —malformado incluido—, que la respuesta no cambia con el token, y que **el texto no está saneado y el front debe escaparlo** | `T-04` | El contrato declara `200`, `400` (paginación), `404`, `429`, y **ningún** esquema de seguridad para esta ruta | **Hecha el 14-09-2026** |
| `T-12` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-09` | La fila de `RF-PM-012` refleja el estado | **Hecha el 14-09-2026** |

## 2. Orden de ejecución

`T-01` y `T-02` son independientes. `T-03` las junta.

**`T-05` va con `T-04`, en el mismo commit**: una ruta pública que se declara en el controlador y no en la configuración responde `401` y parece rota; una que se declara en la configuración y no en `EndpointPermissionsIT` es una ruta pública que nadie revisó. Y **la mitad negativa de `T-05`** —las tres rutas hermanas que siguen exigiendo token— es la que impide que el patrón de un segmento se convierta en `/**` por descuido.

**`T-07` y `T-08` no son opcionales.** Son las dos disciplinas del hotlink aplicadas a otra persona.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-202` | `T-01`, `T-04` |
| `CA-PM-203` | `T-02`, `T-07` |
| `CA-PM-204` | `T-01`, `T-09` |
| `CA-PM-205` | `T-03`, `T-08` |
| `CA-PM-206` | `T-04` |
| `CA-PM-207` | `T-09` |
| `CA-PM-208` | `T-06` |
| `CA-PM-209` | `T-10` |
| `CA-PM-210` | `T-04` |
| `CA-PM-211` | `T-01` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-009 · T-01` (tabla) y `T-06` (`isPurchasable`) | 14-09-2026 | Responsable técnico | **Cerrado el 14-09-2026** |
| 2 | `T-05` y `T-06` escriben en `shared/security`. Cualquier regresión en las rutas públicas existentes es responsabilidad de este requerimiento | 14-09-2026 | Responsable técnico | **Cerrado el 14-09-2026** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local, **incluidas `EndpointPermissionsIT` y `RateLimitIT` con las rutas públicas anteriores intactas**.
- [ ] La ruta está declarada pública **en los dos sitios**, solo en `GET`, y sus tres hermanas siguen exigiendo token.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida, sin esquema de seguridad en esta ruta.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
