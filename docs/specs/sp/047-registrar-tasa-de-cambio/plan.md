# PLAN — `RF-SP-047` Registrar una tasa de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-047` |
| Especificación | [`spec.md`](spec.md), aprobada el 07-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |

---

## 1. Enfoque

**Una tabla, un caso de uso y una restricción que decide.** El alta valida lo que puede validar y **deja que el motor decida el solapamiento**, porque es lo único que puede hacerlo sin condiciones de carrera.

**El paquete es `modules/system/exchangerates`**, hermano de `currencies` y `memberships`. No cuelga de `currencies`: aquel es un catálogo de solo lectura por API (`RN-SP-010`) y este se administra entero, de modo que meterlo dentro obligaría a que un paquete «de solo lectura» expusiera cuatro escrituras.

## 2. Cambios de esquema

### 2.1 `V62__create_exchange_rates.sql`

| Columna | Tipo | Por qué |
|---|---|---|
| `id` | `uuid` | UUID v7, como todo el sistema |
| `source_currency_id` | `uuid NOT NULL` | Clave foránea a `currencies` |
| `target_currency_id` | `uuid NOT NULL` | Clave foránea a `currencies`, **a la misma tabla** |
| `price` | `numeric(18,8) NOT NULL` | **No es un importe**: ver abajo |
| `valid_from` | `date NOT NULL` | Rige por días, no por instantes |
| `valid_to` | `date NULL` | Nulo: **vitalicia** |
| `is_active` | `boolean NOT NULL DEFAULT true` | El estado |
| `created_at`, `updated_at` | `timestamptz` | Art. V.7 |
| `deleted_at` | `timestamptz NULL` | Retiro lógico (`RN-SP-033`) |

!!! danger "`numeric(18,8)` rompe con `numeric(14,4)`, que es la forma de todo importe del sistema"

    Y hay que romperla: **una tasa no es un importe**. `products.price` y los cinco de `movements` llevan la escala que su **moneda** admite —`currencies.decimal_places` va de cero a cuatro—, pero una tasa **no está expresada en ninguna moneda**: es un cociente entre dos.

    Con cuatro decimales, `COP → USD` —del orden de `0,00024`— se guardaría como `0,0002`, y una moneda más devaluada se guardaría como **cero**. Ocho decimales es lo que usan las tesorerías y las pasarelas; los diez dígitos enteros cubren el extremo contrario.

**Restricciones declaradas con la tabla:**

| Restricción | Definición | Regla |
|---|---|---|
| `fk_exchange_rates_source` | → `currencies(id)` | `RN-SP-029` |
| `fk_exchange_rates_target` | → `currencies(id)` | `RN-SP-029` |
| `ck_exchange_rates_monedas_distintas` | `source_currency_id <> target_currency_id` | `RN-SP-029` |
| `ck_exchange_rates_price_positive` | `price > 0` | `RN-SP-030` |
| `ck_exchange_rates_vigencia` | `valid_to IS NULL OR valid_to >= valid_from` | `RN-SP-031` |
| `uq_exchange_rates_vigente` | `EXCLUDE USING gist (...)` | `RN-SP-032` |

!!! important "La rama `IS NULL` de la vigencia va delante y explícita"

    `valid_to >= valid_from` **sola** admitiría el nulo igualmente —un `CHECK` que evalúa a `NULL` **acepta** la fila—, y eso es exactamente lo que se quiere para una tasa vitalicia. Se escribe explícito para que ese permiso sea **deliberado y no accidental**, que es lo que este proyecto ya pagó una vez con `ck_deletion_reason`.

### 2.2 El `EXCLUDE`, pieza por pieza

```sql
ALTER TABLE exchange_rates
    ADD CONSTRAINT uq_exchange_rates_vigente
    EXCLUDE USING gist (
        source_currency_id WITH =,
        target_currency_id WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    ) WHERE (is_active AND deleted_at IS NULL);
```

| Pieza | Qué pasa si falta |
|---|---|
| Las dos monedas `WITH =` | Prohibiría que `USD → COP` y `USD → EUR` convivan, que es justo lo que la regla **sí** admite |
| `'[]'` | Con `'[)'` —el intervalo por omisión— una tasa que termina el 30 de junio y otra que empieza el 30 de junio **no se solaparían**, y ese día habría dos |
| `WHERE (is_active AND deleted_at IS NULL)` | Una tasa retirada o suspendida **bloquearía sus días para siempre**, y nada más fallaría |

**Necesita `btree_gist`, y ya está instalada**: la puso `V44` para las tasas de comisión. Esta migración **no la vuelve a declarar** — un `CREATE EXTENSION IF NOT EXISTS` de más no rompe nada, y deja creer que la dependencia es de aquí.

### 2.3 `V63__seed_exchange_rates_permissions.sql`

Los cuatro permisos con identificador literal (Art. V.11) **y su asociación a `SUPERADMIN` y a `ADMIN` en la misma migración**, con la guarda que `V51` estrenó: si alguna de las ocho filas de `role_permissions` no se insertó, la migración aborta. Olvidarlo no falla al aplicar — deja a `ADMIN` incapaz de conceder lo que no tiene.

## 3. Componentes afectados

| Capa | Elemento | Nota |
|---|---|---|
| `domain/models` | `ExchangeRate` | Agregado y modelo persistente, como `Product` y `Role` |
| `domain/repository` | `ExchangeRateRepository` + adaptador | **Traduce la violación del `EXCLUDE` por nombre de restricción** |
| `domain/service` | `RegisterExchangeRateService` | El orden de verificación de §5 |
| `application` | `RegisterExchangeRateRequest`, `ExchangeRateResponse` | |
| `interfaces` | `ExchangeRateController` | `POST /api/v1/exchange-rates` |

**Reutiliza `CurrencyCatalog`**, el puerto que `SP` publica para `PM` (**D-25**), en lugar de leer `currencies` directamente. Está dentro del mismo módulo y podría leer la tabla, y aun así se usa el puerto: devuelve `active` y `decimalPlaces` ya resueltos, y es el sitio donde «una moneda existe y está activa» ya está escrito una vez.

## 4. Contrato de API

`POST /api/v1/exchange-rates`

**Petición**

```json
{
  "sourceCurrencyId": "01a03336-6d00-7001-9c4f-5e7ad3000001",
  "targetCurrencyId": "01a03336-6d00-7002-9c4f-5e7ad3000002",
  "price": 4150.00000000,
  "validFrom": "2026-09-01",
  "validTo": null,
  "isActive": true
}
```

- **`price` llega como número**, y su escala **no la fija ninguna moneda**: la fija esta tabla. Hasta ocho decimales, y se valida con anotación porque la regla no depende de ningún otro campo.
- **`validTo` ausente y `null` significan lo mismo**: la tasa es vitalicia. No hay un tercer estado que distinguir, de modo que aquí **no hace falta `Patchable`** — eso es cosa del `PATCH` de `RF-SP-049`.
- **`isActive` es opcional y por omisión `true`.** Se recibe, al revés que el `status` de `RF-PM-001`: allí una regla obliga a nacer inactivo y aquí no hay ninguna (`spec.md` §14, resolución 1).
- **Las fechas son `date` y no `date-time`**: una tasa rige por días. Enviar una hora se rechaza por formato.

**Respuesta `201`** con la tasa y sus dos monedas resueltas, y cabecera `Location`.

## 5. Orden de verificación

Es el contrato, y el orden importa:

1. **Forma** de lo recibido (Bean Validation).
2. **Las dos monedas existen y están activas**, por `CurrencyCatalog`. Se resuelven **las dos** aunque la primera falle, para poder decir cuál de ellas es.
3. **Origen distinto de destino** — en el agregado, que es donde vive la regla.
4. **Precio y vigencia** — en el agregado.
5. **`INSERT`**, y el `EXCLUDE` decide el solapamiento.

!!! danger "La comprobación previa del solapamiento NO sustituye a la restricción"

    Se hace una consulta previa **para poder dar un mensaje que nombre el periodo que choca**, y esa consulta **no garantiza nada**: dos altas simultáneas la pasan las dos. Quien decide es el `EXCLUDE`, y su violación se traduce **por nombre de restricción** —nunca por el texto del driver— a un `409`.

    Es el mismo reparto que `RF-PM-001` tiene con `uq_products_code`: la verificación previa redacta, la restricción decide.

## 6. Autorización

`@PreAuthorize("hasAuthority('exchange-rates:create')")` sobre el método. Los cuatro permisos se siembran en `V63` y se asocian a `SUPERADMIN` y `ADMIN`.

## 7. Auditoría

Un evento `CREATE` con el estado inicial completo, **en la misma transacción**. Sin evento de seguridad: una tasa no concede privilegios sobre el sistema, y el catálogo de `security.md` §8.1 es cerrado. Es la misma postura que `RF-PM-001` tomó con los productos.

La instantánea la arma el agregado y **la comparte con el retiro** (`RF-SP-050`): si cada caso de uso armara su mapa, el registro de creación y el de eliminación describirían la misma tasa con claves distintas, y compararlos —que es para lo que existen— dejaría de ser posible.

## 8. Transaccionalidad

Una transacción: inserción y evento. **Sin bloqueo pesimista**, y no es un olvido: aquí no se lee ningún agregado para modificarlo. La concurrencia la resuelve la restricción.

## 9. Impacto sobre otros módulos

**Ninguno.** `MV` sigue exigiendo una sola moneda por venta (`RN-MV-012`), y `PM` sigue expresando cada precio en su moneda. Lo que cambia es el **motivo** de aquella regla, y eso ya está anotado en `requirements/mv.md` v0.11.0.

## 10. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una columna en `currencies`** | Una tasa tiene vigencia y una columna no. La pregunta «¿a cuánto estaba en marzo?» sería irrespondible, y la de hoy se sobrescribiría cada vez |
| **Comprobar el solapamiento solo en el caso de uso** | Dos altas simultáneas lo atraviesan las dos. Es el defecto que **no falla**: deja dos tasas vigentes y nadie se entera hasta que alguien convierta |
| **`UNIQUE (origen, destino, valid_from, valid_to)`** | No expresa la regla. Dos rangos distintos que se solapan pasan la unicidad — `spec.md` §12, `CA-SP-538` es justo ese caso |
| **`timestamptz` en vez de `date`** | Obligaría a decidir en qué zona se corta el día, que es la decisión que `architecture.md` §15.1.1 resolvió para el código de una venta y que aquí no hace falta abrir |
| **Deducir la tasa inversa** | La inversa aritmética casi nunca es la tasa real. Produciría un número plausible y falso (`spec.md` §14, resolución 3) |

## 11. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La violación del `EXCLUDE` llega como `500`** si nadie la traduce | El adaptador traduce **por nombre de restricción**, y `CA-SP-538` lo comprueba de extremo a extremo |
| 2 | **`'[)'` en vez de `'[]'`** dejaría pasar dos tasas que comparten un día | `CA-SP-539` prueba el borde exacto: una termina el día antes de que la otra empiece —se admite— y una termina **el mismo día** que la otra empieza — se rechaza |
| 3 | **La escala se recorta en algún punto del camino** —DTO, JSON, `BigDecimal`— y la tasa se guarda redondeada | `CA-SP-535` comprueba `0,00024096` de punta a punta, leyéndolo de la base |
| 4 | Alguien añade `CREATE EXTENSION btree_gist` «por si acaso» | Queda escrito en §2.2 que la instaló `V44`: repetirla deja creer que la dependencia nace aquí |

## 12. Estrategia de prueba

- **Unitarias del agregado**: origen igual a destino, precio no positivo, vigencia invertida, vigencia de un solo día.
- **Integración del esquema**: el `EXCLUDE` rechaza el solapamiento y **admite** el par distinto y el rango contiguo.
- **Integración de API**: los trece criterios de `spec.md` §12.
- **Concurrente**: dos altas simultáneas del mismo par y periodo — una queda, la otra recibe `409`. Es la única que demuestra que la comprobación previa no es la garantía.
