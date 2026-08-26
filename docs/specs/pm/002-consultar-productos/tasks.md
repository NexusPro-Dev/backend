# TASKS — `RF-PM-002` Consultar productos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-002` |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V41__create_products_search_index.sql`: índice de trigramas sobre `f_unaccent(lower(name))` e índice de listado sobre `(created_at DESC, id DESC)` | `RF-PM-001 · T-01` | `mvn flyway:info` los lista. **No se indexan `type` ni `status`**: dos y tres valores no dan selectividad | Pendiente |
| `T-02` | `application/ProductSortField`: dominio **cerrado** con `name`, `price` y `createdAt` | — | Unitaria: un campo fuera de la lista no es representable, y el analizador devuelve el error de `VAL-005` en lugar de un valor por omisión | Pendiente |
| `T-03` | `application/ListProductsRequest`: los cinco filtros, la paginación y el orden, con `includeDeleted` en `false` por omisión | `T-02` | Los cuatro `400` se devuelven **juntos**, no de uno en uno | Pendiente |
| `T-04` | `application`: `ProductItem` y la envoltura `PageResponse<ProductItem>` con `totalIsExact` en `true` | — | Compila y serializa | Pendiente |
| `T-05` | `domain/repository/ProductQueryRepository` y su adaptador: **una sentencia**, predicado dinámico, `LEFT JOIN` a `memberships` y a `currencies`, y **orden total** con `id` de desempate | `T-01`, `T-04` | Integración: la consulta cuesta **una** sentencia con y sin filtros, y el plan usa el índice | Pendiente |
| `T-06` | Búsqueda: recorte del término, **escape de `\`, `%` y `_`**, parámetro enlazado y `f_unaccent` aplicado **a los dos lados** | `T-05` | Buscar «membresia» encuentra «Membresía»; un término con `%` no devuelve el catálogo entero | Pendiente |
| `T-07` | Conteo **exacto**, sin el atajo de omitirlo cuando la página no se llena | `T-05` | La página vacía más allá de la última devuelve el **total real**, no uno deducido del desplazamiento (`FA-002`) | Pendiente |
| `T-08` | `domain/service/ListProductsService` con `@Transactional(readOnly = true)` | `T-05`, `T-07` | La transacción se declara de solo lectura | Pendiente |
| `T-09` | `interfaces`: `GET /api/v1/products` con `products:read` sobre el método | `T-08` | `403` sin el permiso | Pendiente |
| `T-10` | Pruebas de API de los criterios de `spec.md` §12 | `T-09` | La suite cubre `CA-PM-013` a `CA-PM-022` y `CA-PM-074`, `CA-PM-075`, `CA-PM-077` | Pendiente |
| `T-11` | **Prueba de paginación estable**: se recorren todas las páginas con varios productos del mismo instante de alta y no falta ni se repite ninguno | `T-05` | `CA-PM-076`. Sin el desempate por `id` esta prueba falla, y es la única que lo detecta | Pendiente |
| `T-12` | Prueba de `EXPLAIN`: con doscientos productos sembrados, la búsqueda usa `ix_products_busqueda` | `T-06` | La prueba **siembra volumen a propósito**: con pocas filas el planificador elige recorrido secuencial y la prueba no probaría nada | Pendiente |
| `T-13` | Documentación OpenAPI del endpoint con sus siete parámetros | `T-10` | El contrato declara los filtros y los estados | Pendiente |
| `T-14` | Actualizar la matriz de trazabilidad | `T-10` | La fila refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-02`, `T-03` y `T-04` son independientes y baratas. `T-05` es la pieza central y la que más se beneficia de escribirse con `T-06` y `T-07` a la vista: las tres deciden la misma sentencia.

`T-11` y `T-12` se escriben al final pero **no son opcionales**: son las dos que fallan cuando alguien simplifica el orden o borra el índice.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-013`, `CA-PM-021` | `T-05`, `T-07`, `T-10` |
| `CA-PM-014` a `CA-PM-016` | `T-03`, `T-05`, `T-10` |
| `CA-PM-017` | `T-06`, `T-10` |
| `CA-PM-018` | `T-03`, `T-05` |
| `CA-PM-019` | `T-05` |
| `CA-PM-020` | `T-03` |
| `CA-PM-022` | `T-09` |
| `CA-PM-074`, `CA-PM-075` | `T-02`, `T-05` |
| `CA-PM-076` | `T-11` |
| `CA-PM-077` | `T-10` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende entera de `RF-PM-001`: sin tabla no hay catálogo que listar | 26-08-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
