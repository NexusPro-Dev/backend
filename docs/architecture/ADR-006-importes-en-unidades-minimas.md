# ADR-006 — Importes en unidades mínimas

| Campo | Valor |
|---|---|
| Estado | **Decidido el 05-10-2026** |
| Fecha | 05-10-2026 |
| Decide | Responsable del proyecto |
| Redacta | Bonilla Diaz William Steven |
| Documentos afectados | `architecture.md` §6.2 y §15 · `modelo-datos.md` · `requirements/sp.md` §10 · `requirements/pm.md` · `requirements/cm.md` · `requirements/mv.md` · tripletas `RF-PM-001`, `RF-PM-003`, `RF-PM-004`, `RF-PM-017`, `RF-CM-001`, `RF-CM-002`, `RF-CM-013`, `RF-CM-020`, `RF-MV-001`, `RF-MV-025`, `RF-MV-027`, `RF-SP-019`, `RF-SP-023` |
| Migración | `V65` (la `V64` es de la comisión directa por rol, que la construye otra línea de trabajo el mismo día) |

---

## Contexto

El responsable pidió el 05-10-2026 **guardar los precios como enteros y no como `double`: multiplicar por cien al guardar y dividir por cien al consultar.**

**Hoy no hay ningún `double`.** Todo importe se guarda en `numeric(14,2)` o `numeric(14,4)`, y en Java es `BigDecimal`. Las dos cosas son exactas: el error de representación binaria que hace que `0,1 + 0,2` no dé `0,3`, que es lo que motiva la regla en la mayoría de los proyectos, **no existe en este esquema**. Se le dijo así al responsable y mantuvo la decisión. Lo que aporta el cambio es esto:

1. **Una sola escala para todo el dinero.** Hoy conviven `numeric(14,2)` en las ventas y `numeric(14,4)` en los precios, las tasas de comisión y el afftrack. El precio de un producto podía tener cuatro decimales y la línea que lo vende solo dos, de modo que **la venta redondeaba lo que el catálogo había aceptado**.
2. **Un entero no admite decimales de más por construcción.** Con `numeric(14,4)` un tercer y un cuarto decimal entran sin que nadie lo note; en un `bigint` no hay dónde ponerlos.
3. **El mismo convenio que usan las pasarelas.** Stripe (§15.4) ya habla en centavos: `amount` es un entero en la unidad mínima de la moneda.

## Decisión

### 1. Qué se guarda como entero

**Todo importe en dinero**, y solo eso. Cada valor se guarda en **centésimas**: `12,50` se guarda `1250`.

| Tabla | Columnas | Antes |
|---|---|---|
| `products` | `price`, `purchase_price` | `numeric(14,4)` |
| `product_package_items` | `discount_value` | `numeric(14,4)` |
| `commission_rates` | `fixed_amount`, `direct_fixed_amount` | `numeric(14,4)` · `numeric(14,2)` |
| `user_commission_rates` | `fixed_amount` | `numeric(14,4)` |
| `commissions` | `fixed_amount`, `unit_price`, `commission_amount` | `numeric(14,4)` · `numeric(14,2)` · `numeric(14,4)` |
| `commission_batches` | `total_amount` | `numeric(14,4)` |
| `afftrack_rates` · `user_afftrack_rates` | `amount_per_ftd` | `numeric(14,4)` |
| `movements` | `total_amount`, `discount_amount`, `payable_amount`, `points_amount` | `numeric(14,2)` |
| `movement_details` | `unit_price`, `line_discount`, `line_amount` | `numeric(14,2)` |
| `movement_detail_discounts` | `value`, `discount_value` | `numeric(14,4)` · `numeric(14,2)` |
| `payments` | `amount`, `refunded_amount` | `numeric(14,2)` |
| `accounts` | `balance` | `numeric(14,2)` |
| `movement_entries` | `amount`, `balance_after` | `numeric(14,2)` |

`commission_rates.direct_fixed_amount` nace en `V64` (la comisión directa por rol) y `V65` la convierte con las demás. Las tres columnas `products.direct_commission_*` **no aparecen** porque `V64` las retira.

**Quedan fuera, porque no son dinero:**

| Columna | Por qué |
|---|---|
| `percentage` de `commission_rates`, `user_commission_rates` y `commissions`; `direct_percentage` de `commission_rates` | Un porcentaje, `numeric(5,2)`, con su propio techo de cien |
| `exchange_rates.price` | Una tasa no es un importe: `sp.md` §10.14 ya explica por qué necesita **ocho** decimales (`COP → USD` es del orden de `0,00024`). Con dos se guardaría como cero |
| `points_rates.points_per_unit` | Una tasa de conversión con cuatro decimales, por el mismo motivo |

**Dos columnas guardan dinero o porcentaje según el tipo**, y se convierten **enteras**: `product_package_items.discount_value` y `movement_detail_discounts.value`, que son `PORCENTAJE` o `FIJO`. Partir la columna por tipo obligaría a que el mismo campo cambiara de unidad según la fila, que es justo la confusión que este ADR quiere evitar. **Un descuento de `12,50 %` se guarda `1250`**, igual que uno fijo de `12,50`, y el `CHECK` del techo del porcentaje pasa de `100` a `10000`.

**Los puntos van con el libro.** `accounts` y `movement_entries` llevan dinero y puntos (la cuenta `PUNTOS`, `RN-MV-050`) en las mismas columnas. Se convierten enteras, y con ellas `movements.points_amount`, que es lo que se abona en esa cuenta: un asiento de puntos y la compra que lo origina tienen que estar en la misma unidad o la conciliación compara peras con manzanas.

### 2. Dos decimales, siempre

**Ningún importe tiene más de dos decimales.** Las columnas que admitían cuatro se redondean al migrar con **`HALF_UP`**, la misma regla que el código ya aplica al redondear un importe a la escala de su moneda. La API **rechaza con un `VAL`** toda entrada con más de dos decimales en lugar de redondearla: si alguien manda `9,999`, que se entere de que no se guardó eso.

**De ahí sale una consecuencia sobre `SP`:** `currencies.decimal_places` admitía de `0` a `4`. Una moneda de tres o cuatro decimales ya no cabe en centésimas, y el `CHECK` pasa a **`0` a `2`**. No hay ninguna sembrada con más de dos (hoy solo existe `USD`, con dos) y el catálogo no se edita por API (`RN-SP-010`), de modo que **no hay dato que perder**: es una cota nueva sobre la semilla. Una moneda sin fracción sigue siendo legítima, y se guarda igual en centésimas, que siempre serán cero.

**El cálculo intermedio no cambia.** La comisión se sigue calculando con cuatro decimales en memoria (`ChainCommissionCalculator`) y se redondea **al guardarse**. Lo que cambia es dónde se pierde la precisión, no cómo se calcula.

### 3. `bigint`, no `integer`

Un `integer` en centésimas tiene su techo en `21.474.836,47`. **En pesos colombianos un saldo o un total lo supera sin esfuerzo.** `bigint` llega a noventa mil billones, y los `numeric(14,…)` actuales caben con holgura.

### 4. Un convertidor, y el dominio no se entera

**La conversión vive en un `AttributeConverter<BigDecimal, Long>`** compartido (`shared`): multiplica por cien al escribir y divide al leer (`BigDecimal.valueOf(centésimas, 2)`). **El dominio, los casos de uso, los DTO y la API siguen hablando en decimales.** El contrato OpenAPI no cambia de forma: un precio sigue siendo `12.50` en el JSON.

**Si llega al convertidor un valor con más de dos decimales**, lo redondea con `HALF_UP`. Es una red, no la regla: la regla es el `VAL` de la API y el redondeo explícito del dominio. Un importe que llegue sin redondear al convertidor es un defecto del caso de uso, aunque el convertidor lo tape.

### 5. El SQL nativo ve centésimas

**Esta es la parte que el convertidor no cubre.** Las consultas nativas y las proyecciones de JPQL que leen una columna sin pasar por la entidad (un `SUM`, un filtro por rango de precio, un `CHECK` reescrito) ven el entero. La regla:

- **Lo que se lee** de SQL nativo se convierte al mapearlo, con el mismo ayudante que usa el convertidor. **Nunca se divide en SQL**: `price / 100` en PostgreSQL devuelve un `numeric` de escala arbitraria, y repartir la conversión entre SQL y Java deja dos sitios donde equivocarse.
- **Lo que se compara** (un parámetro como «precio desde») se convierte a centésimas **antes** de vincularlo.
- **La aritmética en SQL** (`SUM`, diferencias) opera en centésimas y se convierte al final, y eso es exacto: sumar enteros no redondea.

Cada consulta se revisa una a una. **El síntoma de olvidar una no es un error**: es un importe cien veces mayor o cien veces menor, con un `200`. Por eso cada tripleta que lee importes por SQL nativo lleva una prueba que compara el valor devuelto con el guardado.

## Alternativas descartadas

**Quedarse en `numeric`.** Es exacto y no exige ningún convertidor. Se descarta porque el responsable quiere el entero, y porque la mezcla de escalas (§Contexto, punto 1) era un defecto real que este cambio arregla de paso.

**`Long` en el dominio**, con las entidades en centésimas y la conversión en los DTO. Es más explícito, pero toca cada cálculo, cada validación y cada prueba de cuatro módulos. Además deja los centavos a la vista de la lógica de negocio, que es justo donde un `× 100` olvidado no salta en ninguna prueba de forma.

**`× 10000` en las columnas de cuatro decimales.** Conservaría la precisión de hoy, pero dejaría dos factores conviviendo en el esquema, y el SQL nativo tendría que saber cuál aplica a cada columna. Un solo factor es la mitad de este ADR.

**`integer`.** Es más pequeño, pero se queda corto para COP (§3).

## Consecuencias

- **`V65`** convierte las columnas en su sitio: `ALTER COLUMN … TYPE bigint USING round(col * 100)`, con `round` de PostgreSQL, que sobre `numeric` deshace el empate alejándose de cero: exactamente lo que hace `RoundingMode.HALF_UP` en Java, también con importes negativos (un asiento de débito). Reescribe los `CHECK` que comparan con constantes (`<= 100` pasa a `<= 10000`) y la cota de `currencies.decimal_places`.
- **`VAL-005`** («el precio admite como mucho cuatro decimales») pasa a **dos**, y lo mismo ocurre con los `@Digits(fraction = 4)` del importe fijo de comisión y del afftrack.
- **Los porcentajes no cambian**, ni en la base ni en la API.
- **Las pruebas que insertan importes por SQL** (siembras de suite, `JdbcTemplate`) pasan a escribir centésimas. Es el punto donde más fácil es romper una suite lejos de aquí.
- **Toda columna de dinero nueva nace `bigint` con el convertidor.** Una `numeric` de dinero nueva es un defecto de revisión.

## Control de cambios

| Versión | Fecha | Cambio | Aprobado por |
|---|---|---|---|
| 1.0.0 | 05-10-2026 | Decisión inicial: importes en centésimas, `bigint`, dos decimales con `HALF_UP`, convertidor JPA y la regla del SQL nativo. | Responsable del proyecto |
