# PLAN — `RF-PM-022` Eliminar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-022` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |

---

## 1. Enfoque

**`DeleteProductService` con otra entidad y una instantánea más ancha.** Los cinco pasos son los mismos —motivo antes que nada, fila bloqueada en cualquier estado, instantánea antes de marcar, marca sin tocar el estado, registro en la misma transacción—; lo que cambia es que la instantánea incluye las filas de asociación con su descuento, para lo que dice `spec.md` §14.1.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `DeletePackageRequest` — `reason` | `PM` |
| `domain/models` | `ProductPackage.delete(ahora)`, `estaRetirado()`, `instantanea(items)` que anida los productos con su descuento | `PM` |
| `domain/service` | `DeletePackageService`, reutilizando `DeletionReason` del módulo | `PM` |
| `interfaces` | `PackageController` — `POST /api/v1/packages/{id}/deletion` | `PM` |

## 4. Contrato de API

`POST /api/v1/packages/{id}/deletion` — `packages:delete`. `{ "reason": "…" }` → `204`. **`POST` y no `DELETE`**, por lo mismo que el producto: el cuerpo lleva el motivo (`requirements/pm.md` §9).

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:delete')")`.

## 6. Auditoría

`DeletionEvent` `LOGICAL` de `product_packages`, con el motivo y la instantánea —paquete y `items: [{product_id, code, discount_type, discount_value, price_en_ese_instante}]`—.

## 7. Transaccionalidad

`@Transactional`. El paquete con `FOR UPDATE` en cualquier estado, sus filas para la instantánea, el `UPDATE`, la auditoría.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Borrar las filas de asociación al retirar** | El paquete retirado dejaría de decir qué contenía (`spec.md` §14.1) |
| **Exigir desasociar o desactivar antes** | Como en `RF-PM-006`: el motivo ya es la barrera, y exigir el paso previo destruiría el dato de si estaba a la venta |
| **`DELETE` con cuerpo** | La RFC 9110 no garantiza el cuerpo |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La instantánea se toma después de marcar** | El orden está escrito y `CA-PM-301` comprueba `deleted_at` nulo dentro de la instantánea |
| 2 | **Alguien «limpia» las filas de asociación de los retirados** | `CA-PM-298` cuenta las filas después del retiro |

## 11. Estrategia de prueba

- **Integración de API** (`PackageDeletionIT`): los seis criterios; la que define el requerimiento es `CA-PM-301`, la instantánea con los productos.
- **Concurrencia**: dos retiros → un `204` y un `409`, una fila de auditoría.
