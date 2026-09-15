# PLAN — `RF-PM-017` Registrar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-017` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 15-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 15-09-2026 |

---

## 1. Enfoque

**El alta del producto, sin tipo ni membresías ni precio, y con dos tablas y cuatro permisos por delante.**

Se hereda la forma de `RF-PM-001` —normalización del código, unicidad total y parcial con traducción de la restricción, moneda validada por el puerto de `SP`— y se quita todo lo que el paquete no tiene. Lo que este plan añade es **el componente que calcula el precio** (`PackagePricing`), porque la respuesta del alta ya es la forma del detalle y el detalle lleva la cuenta hecha: sobre cero productos, cero.

## 2. Cambios de esquema

**Dos migraciones**, por el reparto de `V39`/`V40` y `V87`/`V88`: las tablas en una, los permisos en otra. **Los números se asignan al construir**: `V90` es de la portada, de modo que serán `V91` y `V92` salvo que otra tanda se adelante — «una migración reservada no está reservada».

### `V91__create_product_packages.sql`

```sql
CREATE TABLE product_packages (
    id           uuid         PRIMARY KEY,
    code         varchar(50)  NOT NULL,
    name         varchar(150) NOT NULL,
    description  text         NULL,
    currency_id  uuid         NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'INACTIVO',
    scope        varchar(20)  NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted_at   timestamptz  NULL,
    CONSTRAINT uq_product_packages_code        UNIQUE (code),
    CONSTRAINT ck_product_packages_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_product_packages_status      CHECK (status IN ('ACTIVO','INACTIVO')),
    CONSTRAINT ck_product_packages_scope       CHECK (scope IN ('TIENDA','HOTLINKS')),
    CONSTRAINT fk_product_packages_currency    FOREIGN KEY (currency_id) REFERENCES currencies (id)
);
CREATE UNIQUE INDEX uq_product_packages_name
    ON product_packages (f_unaccent(lower(name))) WHERE deleted_at IS NULL;

CREATE TABLE product_package_items (
    package_id      uuid          NOT NULL,
    product_id      uuid          NOT NULL,
    discount_type   varchar(20)   NOT NULL,
    discount_value  numeric(14,4) NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_product_package_items PRIMARY KEY (package_id, product_id),
    CONSTRAINT fk_product_package_items_package FOREIGN KEY (package_id) REFERENCES product_packages (id),
    CONSTRAINT fk_product_package_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_product_package_items_type       CHECK (discount_type IN ('PORCENTAJE','FIJO')),
    CONSTRAINT ck_product_package_items_value      CHECK (discount_value >= 0),
    CONSTRAINT ck_product_package_items_percentage CHECK (discount_type <> 'PORCENTAJE' OR discount_value <= 100)
);
CREATE INDEX ix_product_package_items_product ON product_package_items (product_id);
```

- **`uq_product_packages_code` es una restricción y `uq_product_packages_name` un índice parcial**, como en `products`: el código no se libera nunca y el nombre sí. El índice parcial **no admite `DEFERRABLE`** y muerde en el `INSERT`; el repositorio traduce las dos al `409` de la spec.
- **Sin `price`** — es `RN-PM-036` —, **sin `implementation`** y **sin `ON DELETE`** en ninguna clave foránea: ni el paquete, ni el producto, ni la moneda se borran físicamente.
- **`ck_product_package_items_percentage` es el único techo que cabe en el esquema.** El del fijo —el precio del producto— está en otra tabla y vive en el caso de uso de `RF-PM-023`.
- La segunda tabla se crea aquí aunque la use `RF-PM-023`: las dos definen el paquete, y una migración por tabla dejaría en el historial un estado en el que existe el paquete y no puede contener nada.

### `V92__seed_packages_permissions.sql`

Cuatro filas con identificador literal —la serie de `PM` continúa: `…000008` a `…000011`—, asociadas a `SUPERADMIN` y `ADMIN` en la misma migración y **con la guarda** que cuenta ocho asociaciones. **A `CLIENTE` no**, por lo mismo de siempre.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `ProductPackage` (entidad), `PackageStatus`, `DiscountType`, `PackageItem` (entidad con clave compuesta) | `PM` |
| `domain/models` | **`PackagePricing`** — la cuenta de `RN-PM-036` en un solo sitio: por producto y total, con redondeo a la moneda | `PM` |
| `domain/repository` | `ProductPackageRepository` + `JpaProductPackageRepository`: `save` con traducción de las dos unicidades, `existsCode`, `existsAliveName`, `findAliveByIdForUpdate`, `findByIdForUpdate` | `PM` |
| `domain/repository` | `ProductPackageQueryRepository` + `Jpa…`: `findDetail(id)` — el paquete con sus filas de asociación y el producto de cada una (**una sentencia**) | `PM` |
| `domain/service` | `RegisterPackageService` | `PM` |
| `application` | `RegisterPackageRequest`, `PackageDetailResponse` con `PackageItemResponse`, `PackageTotals` | `PM` |
| `interfaces` | `PackageController` — `POST /api/v1/packages` | `PM` |
| `shared/security` | La ruta en `EndpointPermissionsIT` con su permiso; `PackagesPermissionsSeedIT` nuevo | `shared` |

**`PackagePricing` nace aquí y lo consumen seis lecturas.** Recibe la moneda del paquete y una lista de `(precio del producto, forma, valor)` y devuelve el precio de cada uno y los tres totales. Es un objeto de dominio sin dependencias, con prueba unitaria propia, y **es el único sitio del sistema que sabe restar un descuento**: si la cuenta cambia, cambia ahí.

## 4. Contrato de API

`POST /api/v1/packages` — `packages:create`.

```json
{ "code": "COMBO_ORO", "name": "Combo Oro", "description": "…", "currencyId": "…", "scope": "HOTLINKS" }
```

`201` con `PackageDetailResponse`:

```json
{
  "id": "…", "code": "COMBO_ORO", "name": "Combo Oro", "description": "…",
  "currency": { "id": "…", "code": "USD", "decimalPlaces": 2 },
  "scope": "HOTLINKS", "status": "INACTIVO",
  "items": [],
  "price": 0.00, "listPrice": 0.00, "savings": 0.00, "exchange": null,
  "offerable": false, "offerableReason": "El paquete tiene menos de dos productos.",
  "createdAt": "…", "updatedAt": "…"
}
```

- **Los tres importes viajan como número** con los decimales de la moneda, como `price` del producto (`RF-PM-003`), y con la misma advertencia: ningún total calculado en el navegador es el que se cobre.
- **`offerable` y `offerableReason` siempre presentes**: `true` con motivo nulo; `false` con **el primer motivo** encontrado, en un orden fijo — menos de dos productos, sin descripción, inactivo, un producto no ofrecible (nombrándolo).
- **`exchange` presente y nulo** sobre cero: no hay nada que convertir.

## 5. Autorización

`@PreAuthorize("hasAuthority('packages:create')")`. Los `products:` **no** habilitan: `CA-PM-268` lo prueba con un actor que porta los cuatro.

## 6. Auditoría

`ChangeEvent` `CREATE` de `product_packages` con la instantánea del paquete. Sin evento de seguridad.

## 7. Transaccionalidad

`@Transactional`. **Cinco sentencias**: código, nombre, moneda (puerto de `SP`), `INSERT`, auditoría; y **una más** para releer el detalle con su forma completa —que sobre un paquete vacío no tiene filas de asociación que sumar—.

## 8. Impacto sobre otros módulos

**Ninguno en código.** `currencies` se referencia por clave foránea y se valida por el puerto `CurrencyLookup` que `SP` ya publica para `RF-PM-001`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Alta con productos dentro** | Siete motivos de fallo en una petición y un rollback parcial ilegible (`spec.md` §14.1) |
| **Deducir la moneda del primer producto** | El segundo producto fallaría por una razón que nadie declaró; y el paquete vacío no tendría moneda |
| **Columna `price` mantenida por triggers o servicios** | La copia que se quedara atrás no fallaría, mentiría (`RN-PM-036`). Es la misma decisión que `rating` |
| **Una migración por tabla** | Dejaría un estado intermedio en el que el paquete existe y no puede contener nada |
| **Reutilizar `products:create`** | Decisión del responsable del proyecto: recurso propio |
| **La cuenta en SQL** | Dos cuentas —motor y Java— para el mismo precio; `PackagePricing` es una sola |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien añade `price` a la tabla** «para no sumar cada vez» | `RN-PM-036` es crítica y §10.6 declara la ausencia; la prueba de `RF-PM-019` que corrige un producto y espera que el paquete cambie solo la haría fallar |
| 2 | **Dos altas simultáneas** con el mismo código o nombre | Restricción total e índice parcial; el repositorio traduce las dos |
| 3 | **Se olvida asociar los permisos a `ADMIN`** | La guarda de `V92` y `PackagesPermissionsSeedIT` |
| 4 | **La cuenta se duplica** en el detalle, la lista, la oferta y el hotlink | Un solo `PackagePricing`, sin dependencias, con prueba unitaria; las seis lecturas lo usan |

## 11. Estrategia de prueba

- **Unitarias**: `ProductPackage` (código normalizado, nombre recortado, descripción vacía → nula) y **`PackagePricing`** (redondeo por moneda, fijo mayor que el precio → cero, porcentaje, totales, lista vacía → ceros).
- **Integración de API** (`PackagesIT`): los ocho criterios de `spec.md` §12, la traducción de las dos unicidades y el `403` con los `products:`.
- **Siembra** (`PackagesPermissionsSeedIT`): cuatro permisos, identificadores estables, dos asociaciones cada uno.
- **Contrato**: el esquema declara `PackageDetailResponse` con `offerable` y `offerableReason`; la prosa dice que nace vacío e inactivo y que el precio se calcula.
