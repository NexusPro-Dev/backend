# PLAN — `RF-PM-025` Desasociar un producto de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-025` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |

---

## 1. Enfoque

**La desasociación de `RF-CM-008`, con el paquete bloqueado y el paquete devuelto.** Borrado físico, `DeletionEvent` `ASSOCIATION` sin motivo, instantánea con el descuento. Se bloquea el paquete para que la desasociación se ordene con las asociaciones y las activaciones simultáneas (`RF-PM-021` cuenta filas bajo el mismo bloqueo).

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `PackageItemRepository.delete(item)` | `PM` |
| `domain/service` | `DissociatePackageProductService` | `PM` |
| `interfaces` | `PackageController` — `DELETE /api/v1/packages/{id}/products/{productId}`, **sin `@RequestBody`** | `PM` |

## 4. Contrato de API

`DELETE /api/v1/packages/{id}/products/{productId}` — `packages:update`. Sin cuerpo. `200` con `PackageDetailResponse`.

**`DELETE` sin cuerpo** por lo mismo que el retiro de la reseña: no hay motivo que proteger. **`200` y no `204`** por lo que dice `spec.md` §14.1.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:update')")`.

## 6. Auditoría

`DeletionEvent` `ASSOCIATION` de `product_package_items`, `reason` nulo —`ck_deletion_reason` lo admite en `ASSOCIATION`—, instantánea con paquete, producto, forma, valor y precio del producto en ese instante.

## 7. Transaccionalidad

`@Transactional`. El paquete con `FOR UPDATE`, la fila, el `DELETE`, la auditoría, la relectura del detalle.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Retiro lógico de la fila** | Es una asociación (Art. V.13, `RN-PM-042`); un `deleted_at` en la clave primaria compuesta obligaría a un índice parcial para volver a asociar |
| **`204`** | Lo que cambió es el precio del paquete (`spec.md` §14.1) |
| **Desactivar el paquete al bajar de dos** | `RN-PM-040`: lo saca de la oferta sin tocar un estado que alguien decidió |
| **Exigir motivo** | La fila se agota en el par; el motivo sería «lo quito» |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La instantánea se toma después de borrar** | Orden escrito; `CA-PM-322` |
| 2 | **Una activación simultánea cuenta dos filas y una desasociación deja una** | Las dos bloquean el paquete: se ordenan |

## 11. Estrategia de prueba

- **Integración de API** (`PackageProductsIT`): los seis criterios; la que define el requerimiento es `CA-PM-322`.
- **Concurrencia** (`PackageConcurrencyIT`): activar y desasociar a la vez sobre un paquete de dos → o queda activo con dos, o queda inactivo con uno, nunca activo con uno **sin** `offerable: false`.
