# PLAN — `RF-CM-001` Registrar una tarifa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-001` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 28-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. **Este es el plan ancla del módulo**: fija la tabla, la restricción de no solapamiento, los puertos que `CM` necesita de `SP` y de `PM`, y la mecánica que heredan `RF-CM-002` a `RF-CM-005`.

---

## 1. Enfoque

Un alta corriente con una tabla nueva, y **dos cosas que no son corrientes**:

1. **`RN-CM-006` no es una unicidad, es una exclusión.** Lo que no puede repetirse no es un valor sino un **intervalo de días**, y eso no lo declara un `UNIQUE`. Se resuelve con una restricción `EXCLUDE` (§2), y **tiene que estar en el motor**: comprobarlo solo en el caso de uso lo dejaría a merced de dos peticiones simultáneas — el defecto exacto que `RN-SP-018` tuvo y que se corrigió el 26-08-2026.
2. **Este requerimiento amplía otros dos módulos.** `CM` necesita leer roles, personas y productos, y hoy **`SP` publica dos interfaces y `PM` ninguna**. Las cuatro que faltan se construyen aquí (§8), porque el reparto que fijó D-25 dice que las escribe quien las necesita.

El resto —`@Transactional`, traducción de violaciones de integridad, evento de auditoría, respuesta con los datos resueltos— es la mecánica ya establecida en `PM` y se reutiliza sin cambios.

## 2. Cambios de esquema

**Migración:** `V44__create_commission_rates.sql`

!!! danger "El número de versión depende de un PR sin mergear"

    `V43` la ocupa el renombrado a `BOT` de `PM`, que **está abierto en otro PR**. Esta migración toma `V44` **dando por hecho que aquel entra antes**. Si no entrara, hay que renumerar **antes** de aplicar nada: dos migraciones con el mismo número no fallan al escribirlas, fallan al desplegar, y para entonces una de las dos ya tiene su suma de comprobación registrada.

| Tabla | Cambio | Detalle |
|---|---|---|
| `commission_rates` | Crea | La tabla del módulo, con sus tres claves foráneas y sus dos `CHECK` |
| `commission_rates` | Crea | `ex_commission_rates_sin_solape`, la restricción de exclusión de `RN-CM-006` |
| — | Extensión | `btree_gist`, que hace falta para combinar `=` sobre `uuid` con `&&` sobre un rango en el mismo índice |

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE commission_rates (
    id          uuid          PRIMARY KEY,
    role_id     uuid          NOT NULL,
    product_id  uuid          NULL,
    user_id     uuid          NULL,
    percentage  numeric(5,2)  NOT NULL,
    valid_from  date          NOT NULL,
    valid_to    date          NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    deleted_at  timestamptz   NULL,

    CONSTRAINT ck_commission_rates_percentage
        CHECK (percentage >= 0 AND percentage <= 100),

    CONSTRAINT ck_commission_rates_vigencia
        CHECK (valid_to IS NULL OR valid_to >= valid_from),

    CONSTRAINT fk_commission_rates_role
        FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_commission_rates_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_commission_rates_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

ALTER TABLE commission_rates
    ADD CONSTRAINT ex_commission_rates_sin_solape
    EXCLUDE USING gist (
        role_id WITH =,
        COALESCE(product_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        COALESCE(user_id,    '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    ) WHERE (deleted_at IS NULL);
```

**Cuatro decisiones dentro de esa restricción, y ninguna es cosmética:**

**1. El `COALESCE` no es un truco, es la única salida.** En PostgreSQL **dos `NULL` no son iguales**, ni en un `UNIQUE` ni en un `EXCLUDE`. Sin él, dos tarifas por omisión idénticas del mismo rol —las dos con producto y persona nulos— **no chocarían**, y `RN-CM-006` sería una regla escrita que el motor no sostiene. El centinela es el **UUID nulo**, y es seguro porque `id` se genera como **UUID v7** (Art. V.11), que nunca produce ceros: no puede colisionar con un identificador real.

**2. `daterange(valid_from, valid_to, '[]')` con los dos extremos incluidos.** `spec.md` §13 lo exige: si una tarifa termina el 31, la siguiente empieza el 1. Con el rango semiabierto por arriba —`'[)'`, que es el que PostgreSQL usa por omisión— **dos tarifas consecutivas que comparten el día de corte no chocarían**, y ese día quedaría cubierto dos veces. Un `valid_to` nulo produce un rango **sin límite superior**, que es exactamente «rige indefinidamente».

**3. La restricción es PARCIAL sobre las vivas.** Sin el `WHERE (deleted_at IS NULL)`, una tarifa retirada seguiría bloqueando sus días y `CA-CM-037` sería imposible de cumplir: retirar dejaría el periodo inutilizable para siempre.

**4. `btree_gist` hace falta y no es un capricho.** Un índice GiST no sabe comparar `uuid` con `=` por sí solo; la extensión es la que aporta esas clases de operador. El proyecto ya declara extensiones en `V1` —`unaccent` y `pg_trgm`—, así que no es un precedente nuevo.

**Migración de permisos:** `V45__seed_commissions_permissions.sql`, con los cuatro códigos de `requirements/cm.md` §6, siguiendo el patrón de `V40` para `PM`.

**Índices.** Ninguno más allá del que la propia restricción crea. El índice GiST cubre el acceso por rol, y es el que usa la resolución de `RF-CM-005`. Añadir uno por `product_id` o por `user_id` antes de tener volumen sería pagar escritura por una lectura que nadie ha demostrado lenta — mismo criterio que `RF-SP-011` §2.

## 3. Componentes afectados

Paquete raíz del módulo: `com.factech.nexus.modules.commissions`.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `CommissionRate` | Nuevo | Agregado y modelo persistente. Normaliza y valida `RN-CM-007` y `RN-CM-009` |
| `domain/models` | `RateScope` | Nuevo | El **grado** en que la tarifa fue declarada, derivado de qué campos vienen. No es una columna: se calcula |
| `domain/repository` | `CommissionRateRepository` | Nuevo | Puerto de escritura |
| `domain/repository` | `JpaCommissionRateRepository` | Nuevo | Adaptador. **Traduce la violación de `ex_commission_rates_sin_solape`** en el rechazo de `EX-007` |
| `domain/service` | `RegisterCommissionRateService` | Nuevo | Caso de uso, con el orden de verificación de §4 |
| `application` | `RegisterCommissionRateRequest` | Nuevo | Cuerpo con Bean Validation (`VAL-001` a `VAL-006`) |
| `application` | `CommissionRateResponse` | Nuevo | Respuesta con rol, producto y persona resueltos |
| `interfaces` | `CommissionRateController` | Nuevo | `POST /api/v1/commission-rates`. Los otros tres de tarifas añadirán aquí su método |

**`RateScope` se calcula y no se guarda**, y es deliberado: una columna que dijera el grado podría contradecir a las tres que lo determinan —«del rol» con una persona declarada—, y esa contradicción no la detecta nada. Es el mismo criterio con el que `spec.md` §1.1 rechazó la casilla «para todos».

## 4. Contrato de API

`POST /api/v1/commission-rates` · `201 Created` con `Location`.

**El orden de verificación es el contrato** (`spec.md` §8), y el motivo de cada paso:

1. **Formato y rangos** (Bean Validation): `VAL-001` a `VAL-006`.
2. **El rol existe y es vendedor** (`EX-001`, `EX-002`). Va antes que todo lo demás porque es el que decide si la tarifa tiene sentido.
3. **El producto existe y no está retirado** (`EX-003`, `EX-004`).
4. **La persona existe** (`EX-005`) **y porta el rol** (`EX-006`). En este orden: sin él, quien envía una persona inexistente leería «no porta ese rol», que es un dato distinto.
5. **El solapamiento** (`EX-007`), que lo resuelve la base y no una consulta previa. Ver §7.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-006`, y `EX-001` —el rol no es vendedor— |
| `403` | Sin el permiso `commissions:create` |
| `409` | `EX-007`, solapamiento |
| `422` | `EX-002` a `EX-006`: el dato enviado no resuelve a nada válido |

**`EX-001` es `400` y no `422`**, al revés que las otras: que un rol no sea de tipo vendedor no es un dato que no exista, es un dato que **no vale para esto**. Mismo corte que `PM` usó entre `VAL-008` y `EX-002`.

## 5. Autorización

Permiso `commissions:create`, declarativo en el controlador. **Alcance global explícito**: quien tiene el permiso registra tarifas de cualquier rol y de cualquier persona. Es lo que `spec.md` §14 declara y lo que **D-22 puede obligar a revisar**.

## 6. Auditoría

Evento de **creación** en la auditoría de cambios, con el estado inicial completo de la tarifa, módulo `CM` y entidad `commission_rates`. **Sin evento de seguridad**: declarar una comisión no cambia privilegios de nadie — mismo criterio que el alta de un producto.

## 7. Transaccionalidad

`@Transactional` en el caso de uso. **El solapamiento no se comprueba con un `SELECT` previo**, y esa es la decisión con más consecuencia de este plan: una consulta previa seguida de un `INSERT` es una carrera —dos peticiones simultáneas leen que no hay solape y las dos insertan—, exactamente lo que le ocurrió a `RN-SP-018`. Se deja fallar la restricción y **se traduce la violación** en el `409` de `EX-007`.

**Lo que cuesta:** el mensaje de `EX-007` debe decir con **cuál** se solapa, y esa información no viene en el error del motor. Se obtiene con una consulta **después** de capturar la violación, ya fuera de la carrera: en ese punto la otra tarifa existe con certeza.

## 8. Impacto sobre otros módulos

**Este requerimiento amplía `SP` y `PM`.** Es el coste declarado de ser el primer módulo que depende de dos, y las cuatro interfaces se escriben aquí porque **las escribe quien las necesita** (D-25, `architecture.md` §15.2).

| Módulo | Interfaz nueva | Lectura que publica |
|---|---|---|
| `SP` | `RoleCatalog` | El rol: código, nombre y **tipo** |
| `SP` | `UserCatalog` | La persona: nombre de usuario y nombre |
| `SP` | `SellerRoleCatalog` | **Qué rol vendedor porta** una persona |
| `PM` | `ProductCatalog` | El producto: código, nombre y **si está retirado** |

**Una interfaz por lectura y no una fachada**, como fija §15.2: con una sola, añadir un método cambiaría el contrato de todos sus consumidores y de sus dobles de prueba.

**`SellerRoleCatalog` cubre por sí solo `RN-CM-003`, y eso lo hace posible `RN-SP-025`.** Si una persona solo puede portar **un** rol vendedor, entonces «¿porta esta persona este rol?» y «¿cuál es su rol vendedor?» son la misma pregunta, y basta una interfaz para las dos. **Mientras `RN-SP-025` no esté implementada, esa equivalencia no se sostiene** — es el bloqueo declarado en `tasks.md`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Comprobar el solapamiento con un `SELECT` previo | Es una carrera. Dos peticiones simultáneas leen que no hay solape y las dos insertan. Es el defecto de `RN-SP-018`, ya vivido |
| Columnas `product_id` y `user_id` **no** nulables con un centinela | Haría el `EXCLUDE` más directo, y a cambio metería un identificador falso en el modelo de dominio y en la respuesta. El centinela pertenece al índice, no al dato |
| Una columna `scope` que declare el grado | Puede contradecir a las tres columnas que lo determinan, y esa contradicción no la detecta nada. Se calcula |
| Vigencia con `timestamptz` en lugar de `date` | Obligaría a decidir en qué zona horaria se corta el día, y esa decisión no la tiene que tomar quien declara una tarifa |
| Un `UNIQUE` parcial por cada combinación de nulos | Cuatro índices en lugar de uno, y **ninguno resuelve el solapamiento**: la unicidad no es lo que hay que impedir |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El número de migración choca** con el de `PM` si su PR no entra antes | Declarado en §2 con marca de peligro. Se comprueba antes de mergear |
| 2 | **`RN-SP-025` no está implementada**, y de ella depende que `SellerRoleCatalog` sea determinista | Bloqueo declarado en `tasks.md`. `RF-CM-005` no puede darse por terminado antes |
| 3 | El mensaje de `EX-007` exige una consulta extra tras la violación | Acotada al camino de error, que no es el común |
| 4 | `btree_gist` es una extensión más que el despliegue debe poder crear | `V1` ya crea dos; si el rol de la base no pudiera, fallaría al migrar y no en producción |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| `RN-CM-007` y `RN-CM-009` en el agregado | Unitaria | Sin Spring: porcentaje fuera de rango, fin anterior al inicio, y el **cero como valor válido** |
| Los cuatro grados | Integración | `CA-CM-001` a `CA-CM-003`, con el grado devuelto en cada uno |
| Las cinco excepciones de datos | Integración | `CA-CM-006` a `CA-CM-008`, con el retirado distinguido del inexistente |
| **El no solapamiento** | Integración | `CA-CM-009`, `CA-CM-010` y `CA-CM-013`: se rechaza el solape, se admite lo consecutivo, y conviven productos distintos |
| **El solapamiento bajo concurrencia** | Integración concurrente | Dos altas simultáneas del mismo caso y periodo: **una `201` y una `409`**, y una sola fila. Es la prueba que verifica que la restricción está en el motor y no en el caso de uso |
| El día de corte | Integración | Una tarifa que termina el 31 y otra que empieza el 31 **chocan**; empezando el 1, no |
