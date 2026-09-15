# PLAN — `RF-PM-027` Consultar el catálogo de hotlinks

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-027` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |

---

## 1. Enfoque

**La oferta sin la membresía y con el alcance como único predicado.**

`GetOwnOfferService` ya hace todo lo que esta lectura necesita —una sentencia con membresías, moneda y `rating`, la conversión en bloque, la separación en dos listas sin reordenar— salvo dos cosas: mira la membresía del actor y no mira el alcance. El servicio nuevo (`GetHotlinkCatalogService`) es esa misma secuencia sin el primer paso y con `scope = 'HOTLINKS'` en el `WHERE`. **Se reutiliza la forma (`OfferItem`) y no la sentencia**: la de la oferta lleva el origen como parámetro y su predicado de tipo, y meterle un modo «sin membresía y por alcance» la convertiría en dos consultas en una, que es lo que este módulo ha evitado siempre (`RF-PM-002` §9). Una sentencia más en `JpaProductQueryRepository`, la sexta, con el mismo `SELECT` y otro `WHERE`.

## 2. Cambios de esquema

**Ninguno.** Ni columna, ni índice —el catálogo es de decenas—, ni permiso: `products:hotlink` existe desde `V60`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `ProductQueryRepository.findHotlinkCatalog()` y su sentencia en `JpaProductQueryRepository`: el `SELECT` de la oferta con `WHERE deleted_at IS NULL AND status = 'ACTIVO' AND scope = 'HOTLINKS'`, mismo `ORDER BY` | `PM` |
| `domain/service` | **`GetHotlinkCatalogService.catalog()`** — `@Transactional(readOnly = true)`, sin `CurrentActor` ni `CurrentMembershipLookup` | `PM` |
| `application` | **`HotlinkCatalogResponse(upgrades, services)`**, con `OfferResponse.Offered` reutilizado; **sin `currentMembership`** | `PM` |
| `interfaces` | `ProductController` — `GET /api/v1/products/hotlinks`, `@PreAuthorize("hasAuthority('products:hotlink')")` | `PM` |

## 4. Contrato de API

`GET /api/v1/products/hotlinks` — `products:hotlink`. Sin parámetros. `200` con `HotlinkCatalogResponse`.

- **`HotlinkCatalogResponse` y no `OfferResponse`**: la oferta lleva `currentMembership` y aquí no hay ninguna; publicar la forma de la oferta con ese campo siempre nulo diría que a veces no lo es.
- **`OfferItem` tal cual**: es la forma de venta —`price`, `exchange`, `videoUrl`, `coverImageUrl`, `rating`, sin `purchasePrice`—, y un vendedor y un comprador miran el mismo producto.
- **La ruta es un segmento literal bajo `/products`**, como `/available`: Spring resuelve el literal antes que `/{id}`, es correcto, y por eso mismo tiene prueba (`CA-PM-346`).
- **`security`**: el esquema global; la ruta exige token. **La prosa de la `@Operation` dice tres cosas**: que no mira la membresía de quien llama, que no trae el enlace armado y cómo se compone, y que los paquetes llegarán con `RF-PM-026`.

## 5. Autorización

`@PreAuthorize("hasAuthority('products:hotlink')")`, y nada más. **Ningún cambio en el catálogo de permisos ni en `SecurityConfig`**; `EndpointPermissionsIT` reconoce la ruta por su anotación, sin excepción.

## 6. Auditoría

Ninguna: lectura.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Una sentencia** por el catálogo y **dos como máximo** por la conversión —la moneda de casa y las tasas de las monedas presentes—, exactamente como la oferta (`CA-PM-168`). El número **no sube** con los productos (`CA-PM-347`).

## 8. Impacto sobre otros módulos

**Ninguno.** No consume nada de `SP` que la oferta no consuma ya.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`RF-PM-002` con `scope=HOTLINKS&status=ACTIVO`** | Exige `products:read`, que abre el catálogo administrativo con el precio de compra dentro. `products:hotlink` existe para no dar aquel (`spec.md` §14.2) |
| **Un parámetro en `/products/available`** («modo vendedor») | La oferta responde sobre quien llama y su membresía; un modo que la ignore es otra lectura con otro permiso metida en la misma ruta |
| **Reutilizar `findOffer` con un modo** | Dos consultas en una: el predicado de origen y el de alcance no se combinan, se sustituyen. Una sentencia propia cuesta veinte líneas y se lee sola |
| **Componer `hotlinkPath` en la respuesta** | Una lectura de la persona por página para una concatenación que el frontend hace con el `username` que ya tiene (`spec.md` §14.1). Si se pide, es un campo más |
| **Paginar** | La oferta no pagina y este conjunto es el mismo orden de magnitud: el catálogo publicable |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien añade `p.purchase_price` a la sentencia nueva** «porque el vendedor debería saber el margen» | `OfferItem` no tiene el campo, y `CA-PM-342` comprueba que el cuerpo no lo trae bajo ningún nombre con un costo declarado |
| 2 | **Se filtra por la membresía del actor** «por coherencia con la oferta» | `CA-PM-341`: un vendedor en `ORO` ve el `BECA → ORO` |
| 3 | **La ruta cae en `/{id}`** tras un reordenado | `CA-PM-346` |

## 11. Estrategia de prueba

- **Integración de API** (`HotlinkCatalogIT`): los ocho criterios de `spec.md` §12, con un catálogo que mezcla `TIENDA` y `HOTLINKS`, activos e inactivos, un retirado, y un costo declarado. **Las que definen el requerimiento**: `CA-PM-340` —el conjunto— y `CA-PM-341` —sin la membresía de quien llama—.
- **De sentencias**: `CA-PM-347` cuenta consultas con tres y con ocho productos, y son las mismas.
- **De seguridad**: `EndpointPermissionsIT` lista la ruta con `products:hotlink`; `CA-PM-345` prueba `403` con los otros dos permisos de vista.
- **De contrato**: `OpenApiContractIT` regenera; `HotlinkCatalogResponse` entra en el contrato.
