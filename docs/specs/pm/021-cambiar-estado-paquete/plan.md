# PLAN — `RF-PM-021` Cambiar el estado de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-021` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |

---

## 1. Enfoque

**`RF-PM-005` sin la unicidad del upgrade y con una cuenta de filas.** El producto comprueba `RN-PM-004` al activar; el paquete comprueba `RN-PM-040`: descripción y **cuántas** filas de asociación tiene. Es una sentencia de conteo más, sobre la clave primaria de `product_package_items`, y nada de lo que la oferta decide después.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `ChangePackageStatusRequest` — `status` | `PM` |
| `domain/models` | `ProductPackage.activate(ahora)` / `deactivate(ahora)` que devuelven si cambió; `puedePublicarse(cuantosProductos)` con la lista de motivos | `PM` |
| `domain/repository` | `PackageItemRepository.countByPackage(packageId)` | `PM` |
| `domain/service` | `ChangePackageStatusService` | `PM` |
| `interfaces` | `PackageController` — `PATCH /api/v1/packages/{id}/status` | `PM` |

## 4. Contrato de API

`PATCH /api/v1/packages/{id}/status` — `packages:update`. `{ "status": "ACTIVO" }` → `200` con `PackageDetailResponse`. Los `409` de activar llevan **todos** los motivos en `errors`, uno por `FieldError`.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:update')")`.

## 6. Auditoría

`UPDATE` de `status` con antes y después; nada si no cambió.

## 7. Transaccionalidad

`@Transactional`. El paquete con `FOR UPDATE`, el conteo solo al activar, la escritura y la auditoría solo si cambia, la relectura del detalle.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Exigir productos activos al activar** | Es un hecho de otras filas que cambia con el tiempo; la oferta lo mira siempre y el detalle lo dice (`spec.md` §14.1) |
| **Desactivar solo el paquete cuando baja de dos** | Cambiaría un estado que alguien decidió; `RN-PM-040` lo saca de la oferta sin tocarlo |
| **Un motivo por respuesta** | Quien se equivocó en dos corrige una vez |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El conteo se hace sobre una lectura sin bloqueo** y una desasociación simultánea lo deja en uno | El paquete está bloqueado y `RF-PM-025` bloquea el paquete también: se ordenan |

## 11. Estrategia de prueba

- **Unitaria**: `puedePublicarse` con cero, uno y dos productos, con y sin descripción.
- **Integración de API** (`PackageStatusIT`): los siete criterios; la que define el requerimiento es `CA-PM-294`: se activa con un producto inactivo dentro y el detalle lo dice.
