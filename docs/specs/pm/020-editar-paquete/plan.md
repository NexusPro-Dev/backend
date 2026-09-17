# PLAN — `RF-PM-020` Editar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-020` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |
| Reabierto el | 16-09-2026 — **la vigencia se corrige** (`RN-PM-047`): dos `Patchable` más, ver §3, §4 y §11 (Art. I.7) |
| Reaprobado el | 16-09-2026 — Responsable del proyecto |

---

## 1. Enfoque

**`RF-PM-004` con tres campos y dos inmutables declarados.** Se hereda entera la mecánica —`Patchable` con sus tres estados, los inmutables como `Patchable<Object>` para rechazarlos con mensaje propio, la unicidad del nombre con `existsAliveNameForOther`, el bloqueo de fila, la auditoría solo de lo que cambió, la relectura del detalle—. No hay nada nuevo que decidir; lo que hay es no olvidar nada de aquello.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `UpdatePackageRequest` — `Patchable<String> name`, `Patchable<String> description`, `Patchable<PackageScope> scope`, **`Patchable<LocalDate> validFrom` y `validTo` (16-09-2026)**, y `Patchable<Object> code` / `currencyId` para `VAL-004`; `informaAlgo()`, `traeInmutables()` | `PM` |
| `domain/models` | `ProductPackage.update(...)` que devuelve el mapa de cambios `{campo: {before, after}}` y avanza `updatedAt` solo si no está vacío. **(16-09-2026)** Recibe además `Patchable<LocalDate> validFrom` y `validTo`, resuelve la pareja resultante y la pasa por `verificarVigencia` **antes** de aplicar nada, para que un `400` no deje medio cambio en la entidad | `PM` |
| `domain/repository` | `ProductPackageRepository.existsAliveNameForOther(name, id)` | `PM` |
| `domain/service` | `UpdatePackageService` | `PM` |
| `interfaces` | `PackageController` — `PATCH /api/v1/packages/{id}` | `PM` |

## 4. Contrato de API

`PATCH /api/v1/packages/{id}` — `packages:update`. Cuerpo con cualquiera de `name`, `description`, `scope`, `validFrom`, `validTo`; `200` con `PackageDetailResponse`.

- `description: null` y `validTo: null` **vacían**; `name: null`, `scope: null` y `validFrom: null` son `400`.
- La pareja de vigencia resultante con el fin antes del inicio: `400` con `VAL-007`, también cuando solo viene uno de los dos.
- `code` y `currencyId` en el cuerpo: `400` con `VAL-004`, **antes** de mirar nada más.
- Cuerpo vacío: `400` con `VAL-005`.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:update')")`.

## 6. Auditoría

`UPDATE` con solo los campos que cambiaron. Sin fila si no cambió nada.

## 7. Transaccionalidad

`@Transactional`. El paquete con `FOR UPDATE`; el nombre contra otros vivos solo si viene; escritura y auditoría solo si algo cambió; relectura del detalle.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Permitir cambiar la moneda con el paquete vacío** | Regla con «mientras» (`spec.md` §14.1) |
| **Ignorar `code` y `currencyId`** | Haría creer que el cambio se aplicó (`RF-PM-004`) |
| **Desactivar el paquete al vaciar la descripción** | Cambiaría el estado que alguien decidió por un hecho que el detalle ya enseña (`RN-PM-040`) |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El nulo del nombre se trata como ausente** | `Patchable` con su deserializador; `CA-PM-286` |
| 2 | **La unicidad del nombre choca consigo misma** al cambiar la caja | `existsAliveNameForOther` excluye el propio identificador; caso límite de §13 |

## 11. Estrategia de prueba

- **Unitaria**: `ProductPackage.update` — cada campo, el nulo de la descripción, sin cambios; **(16-09-2026)** las dos fechas, el nulo del fin, el nulo del inicio rechazado, y el fin que choca con el inicio que ya había.
- **Integración de API** (`PackageUpdateIT`): los seis criterios; la que define el requerimiento es `CA-PM-289`, porque une la corrección con la ofrecibilidad — y desde el 16-09-2026 `CA-PM-375` hace lo mismo con la vigencia.
