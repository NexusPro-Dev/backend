# PLAN — `RF-PM-018` Consultar paquetes

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-018` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |

---

## 1. Enfoque

**El listado del producto, con una segunda sentencia que trae las filas de toda la página.**

`RF-PM-002` resuelve la conversión de la página en dos sentencias fijas; este listado añade una tercera fija —las asociaciones de todos los paquetes de la página, con su producto— y se la da a `PackagePricing` y `PackageOfferability` **agrupada por paquete en Java**. El número de sentencias no depende de cuántos paquetes ni de cuántos productos tengan.

## 2. Cambios de esquema

**Ninguno.** El orden por precio no tiene columna a propósito (`RN-PM-036`) y se resuelve como dice §4.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `ListPackagesRequest` (filtros, `sort`, paginación, con validación conjunta como `ListProductsRequest`), `PackageSortField`, `PackageItemSummary` (fila), `PackagePageResponse` | `PM` |
| `domain/repository` | `ProductPackageQueryRepository.search(filtros, orden, offset, limit)`, `count(filtros)`, y **`findItemsOf(List<UUID> packageIds)`** — todas las filas de asociación de esos paquetes con su producto, en una sentencia | `PM` |
| `domain/service` | `ListPackagesService` | `PM` |
| `interfaces` | `PackageController` — `GET /api/v1/packages` | `PM` |

## 4. Contrato de API

`GET /api/v1/packages?status=&scope=&currencyId=&q=&includeDeleted=&sort=&page=&size=` — `packages:read`.

Envoltura del sistema; cada fila: `id`, `code`, `name`, `currency`, `scope`, `status`, `itemCount`, `listPrice`, `price`, `savings`, `exchange`, `offerable`, `createdAt`, `deletedAt` (`NON_NULL`).

**El orden por `price`.** El precio no está en la tabla. La sentencia de paquetes ordena por una subconsulta `SUM(p.price − descuento)` **sin redondear** —solo para ordenar— y desempata por identificador; el importe que viaja lo calcula `PackagePricing` después, redondeado por producto. La diferencia entre las dos cuentas cabe en un céntimo por producto y puede alterar el orden entre paquetes casi iguales; `spec.md` §14.2 lo acepta. **La subconsulta se escribe una vez** en una constante del repositorio, y su expresión de descuento es la traducción literal de la de `PackagePricing`, con la prueba `CA-PM-273` como el hilo que las mantiene iguales.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:read')")`; `products:read` no habilita (`CA-PM-276`).

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Cinco sentencias fijas**: página, total, filas de la página, moneda de casa, tasas de las monedas presentes — la última solo si hay algo que convertir. `CA-PM-274` lo cuenta con una página de uno y una de veinte.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una sentencia por paquete para sus filas** | `N+1`; la página de veinte costaría veinticinco |
| **Calcular `price` en SQL y publicarlo desde ahí** | Dos cuentas; la de Java es la única que redondea por producto y a la moneda |
| **Filtro por `offerable`** | Rompe el total o repite la regla (`spec.md` §14.1) |
| **Orden por precio solo en Java** | Ordenaría la página y no el conjunto: la primera página no tendría los más caros |
| **Traer `offerableReason` por fila** | Exportación de fallos; el detalle lo dice |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La subconsulta de orden y `PackagePricing` divergen** | Una constante, con la expresión documentada como traducción de la de Java; `CA-PM-273` |
| 2 | **`findItemsOf` con lista vacía** rompe el `IN` | Se cortocircuita: página vacía, cero sentencias de filas |
| 3 | **El total se calcula con `JOIN` a las filas** y multiplica | `count` no une nada: cuenta paquetes |

## 11. Estrategia de prueba

- **Integración de API** (`PackageListIT`): los ocho criterios; **la que define el requerimiento es `CA-PM-274`**, la de sentencias.
- **De orden por precio**: dos paquetes, cambiar el precio de un producto, ver el cambio de posición (`CA-PM-273`).
- **De coherencia con el detalle**: los totales de la fila son los del detalle (`CA-PM-269`).
