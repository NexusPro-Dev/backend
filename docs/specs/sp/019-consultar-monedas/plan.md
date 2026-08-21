# PLAN — `RF-SP-019` Consultar monedas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-019` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento es el de [`spec.md`](spec.md) y no se repite aquí. Igual que en `RF-SP-010`, **el peso de este requerimiento no está en el endpoint, está en las migraciones**: la consulta es una sentencia trivial, y lo que se decide aquí es un esquema del que dependerá todo cálculo financiero del sistema.

---

## 1. Enfoque

`spec.md` §2 lo dice sin rodeos: el catálogo existe hoy para que la segunda moneda no cueste una migración de cada tabla financiera. Este plan traduce ese propósito en tres decisiones de esquema y en ninguna de código:

1. **Las dos invariantes del catálogo se declaran en la base de datos, no en el dominio.** «Exactamente una moneda por defecto» y «la moneda por defecto no puede desactivarse» son restricciones expresables, y `RF-SP-023` —que todavía no tiene especificación— las heredará ya garantizadas en lugar de tener que implementarlas.
2. **El catálogo se siembra por migración y sí se audita**, a diferencia del de permisos. La razón está en §2 y es la que distingue una tabla que nunca cambia de una que cambia poco.
3. **El sistema comprueba al arrancar que el catálogo es utilizable**, porque `spec.md` §13 lo pide y porque un backend financiero sin moneda de referencia no debería atender peticiones.

`domain` no participa. `spec.md` §5 declara una sola regla, `RN-SP-010`, y es casi negativa: lo único que puede cambiarse es el estado, y eso lo hará `RF-SP-023`. Aquí se cumple porque no existe endpoint de escritura, exactamente como en `RF-SP-010`, y eso condiciona cómo se prueba (§11): por lo que la API **no** expone.

## 2. Cambios de esquema

Dos migraciones: el esquema y la siembra, separados por el mismo criterio con el que `RF-SP-010` separó `V2__create_permissions.sql` de `V3__seed_permissions.sql`. Una migración que se lee «crea la tabla» y otra que se lee «puebla el catálogo» dejan un historial que dice qué pasó; una sola migración mixta obliga a leerla entera para saberlo, y el catálogo crecerá con migraciones posteriores mientras el esquema no.

### `V14__create_currencies.sql`

Campos tomados de `requirements/sp.md` §10.5.

| Tabla | Cambio | Detalle |
|---|---|---|
| `currencies` | Crea | `id uuid PRIMARY KEY`, `code char(3) NOT NULL`, `name varchar(100) NOT NULL`, `symbol varchar(10) NULL`, `decimal_places smallint NOT NULL DEFAULT 2`, `is_default boolean NOT NULL DEFAULT false`, `is_active boolean NOT NULL DEFAULT true`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()` |

Restricciones:

| Nombre | Definición | Por qué |
|---|---|---|
| `uq_currencies_code` | `UNIQUE (code)` | `requirements/sp.md` §10.7. Total, no parcial: no hay borrado lógico |
| `uq_currencies_name` | `UNIQUE (name)` | No está en §10.7 y se añade (§8). Dos filas con el mismo nombre y distinto código serían indistinguibles en cualquier selector |
| `ck_currencies_code_format` | `CHECK (code ~ '^[A-Z]{3}$')` | ISO 4217 en mayúsculas (`requirements/sp.md` §10.5). En el esquema y no solo en el DTO, porque **el único punto de entrada de esta tabla es una migración**: una validación en Java no la cubriría en absoluto |
| `ck_currencies_decimal_places` | `CHECK (decimal_places BETWEEN 0 AND 4)` | Cero es legítimo —hay monedas sin fracción (`spec.md` §13)— y cuatro es el máximo que usa ISO 4217. Sin cota, una errata de siembra produce redondeos silenciosamente erróneos en todo cálculo posterior |
| `uq_currencies_single_default` | `CREATE UNIQUE INDEX … ON currencies ((is_default)) WHERE is_default` | `CA-SP-169`. Garantiza **como máximo** una moneda por defecto; el «exactamente una» lo aporta la siembra. Es la misma construcción que `uq_roles_single_root` en `RF-SP-001` §2 |
| `ck_currencies_default_active` | `CHECK (NOT is_default OR is_active)` | El último caso límite de `spec.md` §13: dar de baja la moneda con la que opera el sistema dejaría los importes sin referencia válida. Ver abajo |

**`updated_at` no está en `requirements/sp.md` §10.5 y se declara igual.** Ese documento lista solo `created_at` para esta tabla, y el Art. V.7 exige ambas marcas en toda tabla de negocio. La omisión probablemente venía de suponer que el catálogo no cambia; pero sí cambia: `RF-SP-023` modifica `is_active`, y sin `updated_at` no habría forma de saber cuándo se dio de baja una moneda sin consultar la auditoría. `requirements/sp.md` §10.5 se enmienda (§8).

**La restricción que hace verificable el último caso límite.** `spec.md` §13 dice que la moneda por defecto inactiva «no debe poder ocurrir» y remite a `RF-SP-023`. Este plan no espera a ese requerimiento:

```sql
CONSTRAINT ck_currencies_default_active CHECK (NOT is_default OR is_active)
```

Tres consecuencias, y son las mismas que `RF-SP-013` §2 obtuvo con `ck_audit_error_log_status`:

- **`RF-SP-023` nace con la mitad de su trabajo hecho.** No tendrá que implementar «no puedes desactivar la moneda por defecto» como regla de aplicación: la operación fallará en la base de datos, y su plan decidirá solo cómo traducir ese fallo a un mensaje. Lo que sí deberá decidir es qué ocurre al **cambiar** cuál es la moneda por defecto, que esta restricción no cubre.
- **Protege también contra la migración.** El camino de escritura de esta tabla es hoy exclusivamente una migración Flyway, donde ninguna validación de Java interviene. Una migración que desactivara la moneda por defecto por descuido fallaría al aplicarse, que es el momento correcto.
- **Tenía que declararse ahora**, al crear la tabla: añadir un `CHECK` sobre una tabla en uso obliga a validar las filas existentes y a decidir qué hacer con las que no cumplen.

**No se crea índice de búsqueda ni de ordenamiento.** El catálogo tiene hoy una fila y tendrá pocas; se devuelve entero y ordenado por `code`, y un recorrido secuencial sobre una tabla de ese tamaño es más rápido que consultar cualquier índice. Mismo criterio que `RF-SP-010` §2 aplicó a `permissions`.

### `V15__seed_currencies.sql`

Siembra la moneda con la que opera el sistema (`CA-SP-132`):

| `code` | `name` | `symbol` | `decimal_places` | `is_default` | `is_active` |
|---|---|---|---|---|---|
| `USD` | Dólar estadounidense | `$` | `2` | `true` | `true` |

**La moneda sembrada es `USD`, y esto se corrigió el 21-08-2026 al aprobar el plan.** El borrador sembraba `COP`, que ningún documento aprobado declara: `requirements/sp.md` §10.5 dice que el catálogo «hoy contiene únicamente `USD`» y §10.7 lo repite al describir el formato del código, y la guía de origen del módulo lo fija como «de momento solo USD pero es para poder escalar a futuro». El plan contradecía el requerimiento, y es el requerimiento el que manda.

Cuatro decisiones sobre esta migración:

- **Identificador UUID v7 literal**, escrito en el propio SQL y generado una sola vez al redactar la migración. Ni `gen_random_uuid()` ni ninguna generación en base de datos: el Art. V.11 lo prohíbe, y además el identificador debe ser el mismo en todos los entornos, para que las pruebas y cualquier dato financiero futuro puedan referenciarlo por constante. Mismo criterio de `V3` y `V7`.
- **`decimal_places = 2`, que es lo que ISO 4217 asigna al dólar estadounidense.** El número no se elige por conveniencia de presentación: mostrar importes con más o menos dígitos es decisión del frontend y es reversible, mientras que redondear al **guardar** destruye información que ya no se recupera. Cambiarlo mientras no exista ningún importe es una migración de una fila; después obliga a revisar cada cálculo hecho hasta entonces (`spec.md` §14, pregunta 3).
- **Se siembra una sola moneda**, que es el estado esperado hoy (`spec.md` §13) y lo que `requirements/sp.md` §10.5 declara. No se añade ninguna otra «por si acaso»: una moneda que existe en el catálogo puede seleccionarse, y ofrecer una en la que no se opera es peor que no tenerla. Cuando se opere en otra, será una migración de una fila, que es exactamente para lo que el catálogo existe.
- **El símbolo es `$`.** Es el de uso corriente para el dólar y hoy no hay ambigüedad posible, porque es la única moneda del catálogo. El día que se incorpore otra que también use `$`, distinguirlas es asunto de la presentación —el `code` siempre desambigua— y no exige tocar el esquema, porque `symbol` es un texto libre de hasta diez caracteres.
- **La siembra sí emite su fila de `audit_change_log`**, con `action = 'CREATE'` y `actor_id`, `correlation_id` e `ip_address` en `NULL` (Art. V.15). Es la diferencia con `V3__seed_permissions.sql`, que no audita, y el motivo es preciso: `RF-SP-010` §2 argumentó que un permiso «no tiene línea de tiempo, porque `RN-SP-004` lo hace inmutable por API». Una moneda **sí** la tiene: `RF-SP-023` puede desactivarla, y ese evento aparecería en `RF-SP-011` como el segundo capítulo de una historia cuyo primero faltaría. Es el mismo criterio con el que `V7__seed_system_roles.sql` audita el poblado de los roles de sistema.

### La comprobación de arranque

`spec.md` §13 dice que un catálogo vacío «solo ocurriría si faltara la migración de siembra» y que **conviene que el sistema lo detecte al arrancar**. Se implementa así: un componente de `shared/config` verifica al arrancar que existe **exactamente una** moneda con `is_default = true` y `is_active = true`, y **falla el arranque** si no la hay.

Falla, y no advierte. Un backend financiero que atiende peticiones sin moneda de referencia produce datos que habrá que corregir después uno por uno, mientras que un arranque fallido es visible de inmediato y no corrompe nada. El coste —que un error de siembra deje el servicio caído— es precisamente el aviso que se quiere.

La comprobación es de arranque y no de cada petición: el catálogo solo cambia por migración o por `RF-SP-023`, que tiene sus propias restricciones.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `RN-SP-010` se cumple por ausencia de endpoint de escritura, no por código (§11) |
| `application` | `ListCurrenciesService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)` |
| `application` | `CurrencyItem` | Nuevo | Modelo de lectura |
| `application` | `CurrencyQueryRepository` | Nuevo | Puerto de consulta. `RF-SP-023` lo complementará con su puerto de escritura, no con métodos aquí |
| `infrastructure` | `JpaCurrencyQueryRepository` | Nuevo | Adaptador. Predicado y proyección con la API de criterios |
| `infrastructure` | `CurrencyEntity` | Nuevo | Mapeo JPA de `currencies`. Se usa como metamodelo; la consulta no lo instancia |
| `api` | `CurrencyController` | Nuevo | `GET /api/v1/currencies`. `RF-SP-023` añadirá aquí su método |
| `api` | `ListCurrenciesRequest` | Nuevo | Un parámetro de consulta. Sin Bean Validation: `spec.md` §11 no declara ninguna validación |
| `api` | `CurrencyResponse` | Nuevo | DTO de salida |
| `shared/config` | `CurrencyCatalogHealthCheck` | Nuevo | Comprobación de arranque (§2) |
| `shared/api` | `PageResponse<T>` | Sin cambios | **No se usa**: este catálogo no se pagina (§4) |

**`CurrencyController` es un controlador nuevo.** El recurso es `/api/v1/currencies` y `RF-SP-023` cuelga de él. Mismo criterio de `RF-SP-010` §3.

**La comprobación de arranque vive en `shared/config` y no en `application`.** No es un caso de uso: no lo invoca nadie, no tiene actor y no responde a una petición. Su lugar es la configuración del contexto, junto a lo que decide si la aplicación puede levantarse.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/currencies` | Catálogo completo de monedas |

**Petición**

```
GET /api/v1/currencies?includeInactive=true
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `includeInactive` | booleano | `false` | Incorpora las monedas dadas de baja (`CA-SP-170`) |

- **No hay `page`, `size` ni `sort`.** `spec.md` §6.1 lo decide de forma explícita, y no aceptarlos siquiera es lo que lo hace verificable: los parámetros desconocidos se ignoran en silencio por defecto en Spring. El DTO de entrada declara **un** campo y la respuesta **no** se envuelve en `PageResponse`. Mismo mecanismo de `RF-SP-010` §4.
- **No hay búsqueda.** `spec.md` §6.1 no la pide, y sobre un catálogo que hoy tiene una fila sería ceremonia. Es la diferencia con `RF-SP-021`, donde el catálogo de países crece por API y la búsqueda es lo que de verdad acota.
- **`includeInactive` **añade**, no sustituye.** Con `true` se devuelven activas e inactivas, no solo las inactivas: `spec.md` §4.1 dice «las inactivas se piden explícitamente», y un filtro que ocultara las activas respondería una pregunta que nadie hace.
- **Un valor no booleano produce `400`** por conversión, aunque `spec.md` §11 no declare validaciones: es un fallo de forma que el conversor de Spring resuelve antes de llegar al caso de uso, no una regla de este requerimiento.

**Respuesta `200`**

```json
{
  "content": [
    {
      "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
      "code": "USD",
      "name": "Dólar estadounidense",
      "symbol": "$",
      "decimalPlaces": 2,
      "isDefault": true,
      "isActive": true
    }
  ]
}
```

- **La colección va envuelta en `content`, no como arreglo desnudo**, y **no se reutiliza `PageResponse<T>` con valores de adorno**, por lo dicho en `RF-SP-010` §4.
- **Sigue siendo una colección aunque tenga un solo elemento** (`spec.md` §13). No se devuelve un objeto suelto ni «la moneda por defecto» como recurso propio: el día que haya dos, el contrato no cambia.
- **`decimalPlaces` se devuelve siempre y es el campo más importante de la respuesta** (`CA-SP-168`, `spec.md` §14, pregunta 3). Cero es un valor legítimo y distinto de «no se sabe», y por eso el campo es obligatorio en el esquema con `DEFAULT 2`: nunca es nulo.
- **`isDefault` lo devuelve cada elemento** en lugar de existir un campo aparte con el identificador de la moneda por defecto. Con una sola moneda ambas formas son equivalentes; con varias, un campo aparte obligaría a cruzarlo con la lista, y `uq_currencies_single_default` ya garantiza que exactamente un elemento lo lleva a `true` (`CA-SP-169`).
- **`symbol` puede venir vacío** y se devuelve como `null`, nunca omitido (`spec.md` §13).
- **No se devuelven `createdAt` ni `updatedAt`.** No son información de negocio para quien compone una operación financiera; `createdAt` diría cuándo se aplicó la migración de siembra, distinto en cada entorno. Misma decisión que en `RF-SP-010` §4 y `RF-SP-015` §4.
- **No se devuelven tasas de cambio ni conversión** (`spec.md` §4.2). No hay campo, no hay tabla y no hay integración: es un requerimiento que no existe.
- **El orden es `ORDER BY code`, siempre y sin que el cliente pueda cambiarlo.** Sin `ORDER BY` explícito PostgreSQL no garantiza orden alguno, y un catálogo que cambia de orden entre dos llamadas hace inútil compararlo. No se ordena por `is_default` primero: con una moneda es indistinguible, y con varias produciría un orden que cambia cuando cambia la moneda por defecto.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | `includeInactive` no es un booleano | `VAL-003` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | Autenticado sin `currencies:read` | `AUTH-002` |
| `500` | Fallo no controlado | `ERR-500` |

**No hay `404` ni `422`.** `spec.md` §10 no declara ninguna excepción propia. Un catálogo vacío devolvería `200` con `content` vacío —aunque no debería poder ocurrir, porque el arranque habría fallado antes (§2)—.

**Cuántas consultas cuesta.** Una:

```sql
SELECT c.id, c.code, c.name, c.symbol, c.decimal_places, c.is_default, c.is_active
  FROM currencies c
 WHERE :incluirInactivas OR c.is_active
 ORDER BY c.code;
```

Sin `JOIN`, sin conteo —no se pagina— y sin colecciones perezosas: no se carga `CurrencyEntity`, se materializa `CurrencyItem` con `cb.construct`.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/currencies` | `currencies:read` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`), que sembró el bloque de `currencies:` aunque ningún endpoint lo declarara todavía.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **El actor es «cualquier rol autenticado con el permiso»** (`spec.md` §3), no un administrador: este catálogo alimenta operaciones financieras corrientes, y por eso su permiso es de lectura y se concederá con holgura. Es distinto de `currencies:update`, que `RF-SP-023` exigirá para cambiar el estado.
- **No hay filtrado por alcance de datos.** Una moneda no pertenece a nadie.
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6). `CA-SP-133` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita** |
| **Siembra de `V15`** | `audit_change_log` | `module = 'SP'`, `entity = 'currencies'`, `action = 'CREATE'`, `changes` con el estado inicial completo, y `actor_id`, `correlation_id` e `ip_address` **nulos** (Art. V.15) |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'currencies'`, `operation = 'GET /api/v1/currencies'`, `error_code = 'ERR-500'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_deletion_log` | No aplica: una moneda no se elimina |

- **Una consulta exitosa no produce evento de seguridad**: el catálogo de `security.md` §8.1 es cerrado y no incluye la lectura de monedas.
- **La siembra sí se audita**, por lo dicho en §2, y es la diferencia con `V3__seed_permissions.sql`. Sin esa fila, el día en que `RF-SP-023` desactivara una moneda, `RF-SP-011` mostraría una modificación sobre un registro cuya creación no consta.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| La consulta | **Una sola**, `@Transactional(readOnly = true)` sobre `ListCurrenciesService` (`development-guide.md` §10) |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |
| Comprobación de arranque | Fuera de toda transacción de negocio: ocurre antes de que el servicio acepte peticiones |

`readOnly = true` tiene aquí el valor de diseño de `RF-SP-010` §7: es la garantía de que `RN-SP-010` no se incumple por accidente desde el único camino de lectura de esta tabla. Una sola sentencia, una sola instantánea.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `requirements/sp.md` | **§10.5 gana `updated_at`**, que no declaraba pese al Art. V.7 y pese a que `is_active` cambia por API. **§10.7 gana `uq_currencies_name`, `uq_currencies_single_default` y `ck_currencies_default_active`** |
| **`RF-SP-023`** | Nace con dos garantías ya en el esquema: no podrá desactivar la moneda por defecto (`ck_currencies_default_active`) ni dejar dos por defecto (`uq_currencies_single_default`). Su plan decidirá cómo traducir esos fallos a mensajes, y **qué ocurre al cambiar cuál es la moneda por defecto**, que ninguna de las dos restricciones cubre. El intercambio no exige tocar el esquema: dos sentencias en la misma transacción —`false` en la vigente, después `true` en la nueva— no dejan en ningún instante dos filas en `true`. Y **no puede resolverse difiriendo el índice**, porque un índice único parcial no es una restricción y `DEFERRABLE` no se le aplica (§10) |
| **Todo módulo financiero futuro** | Obligación declarada: **un importe se almacena junto con el identificador de su moneda**, y el redondeo usa el `decimal_places` de esa moneda, nunca una constante. Es para lo que este catálogo existe hoy con una sola fila (`spec.md` §2) |
| `shared/config` | Gana la comprobación de arranque, que es la primera del sistema que puede impedir que el servicio levante. Si más adelante hubiera otras, conviene agruparlas para que el mensaje de fallo diga todas las que fallan y no la primera |
| `RF-SP-011` | Su consulta responde ahora también por la entidad `currencies`, incluida la fila de la siembra. Ninguna adaptación |
| `architecture.md` | §7.4 exige paginar «las colecciones», y este endpoint se aparta de forma consciente, como ya hicieron `RF-SP-003` §4, `RF-SP-010` §4 y `RF-SP-017` §4. No se propone enmendar el documento: la regla general sigue siendo correcta |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Una sola migración que cree y siembre | El historial dejaría de decir qué pasó, y el catálogo crecerá con migraciones posteriores mientras el esquema no. Mismo criterio con el que `RF-SP-010` separó `V2` de `V3` |
| No auditar la siembra, como hace `V3__seed_permissions.sql` | Un permiso no tiene línea de tiempo; una moneda **sí**, porque `RF-SP-023` puede desactivarla. Sin la fila del poblado, esa modificación aparecería en `RF-SP-011` como el segundo capítulo de una historia sin primero |
| `decimal_places = 0`, siguiendo la práctica de presentar importes sin fracción | El redondeo al guardar destruye información que no se recupera; el redondeo al mostrar es reversible y es decisión del frontend. ISO 4217 asigna 2 al dólar, y cambiarlo después de que exista el primer importe obliga a revisar cada cálculo (`spec.md` §14, pregunta 3) |
| Sembrar `COP` en lugar de `USD` | Era lo que decía el borrador de este plan y contradice a `requirements/sp.md` §10.5, que declara `USD` como única moneda del catálogo, y a la guía de origen del módulo. Corregido antes de aprobar |
| Sembrar además `USD` y otras monedas de uso frecuente | Una moneda que existe en el catálogo puede seleccionarse, y ofrecer una en la que no se opera es peor que no tenerla. Añadirla es una migración de una fila, que es exactamente para lo que el catálogo existe |
| Dejar «la moneda por defecto no se desactiva» para `RF-SP-023` | El único camino de escritura de esta tabla es hoy una migración, donde ninguna validación de Java interviene. Y añadir el `CHECK` con la tabla en uso obliga a validar las filas existentes |
| Un `CHECK` que exija que exista al menos una moneda por defecto | No es expresable: un `CHECK` evalúa una fila, no la ausencia de filas en la tabla. Por eso el «exactamente una» se reparte entre el índice único parcial —como máximo una— y la comprobación de arranque —al menos una— |
| Que la comprobación de arranque advierta en el log en lugar de fallar | Un backend financiero atendiendo peticiones sin moneda de referencia produce datos que habrá que corregir uno por uno. Un arranque fallido es visible de inmediato y no corrompe nada |
| Comprobar el catálogo en cada petición en lugar de al arrancar | El catálogo solo cambia por migración o por `RF-SP-023`, que tiene sus propias restricciones. Sería pagar una consulta por petición para detectar algo que no puede pasar entre dos peticiones |
| Un endpoint propio para «la moneda por defecto» | Con una sola moneda es indistinguible del listado, y con varias obliga a mantener dos contratos que dicen lo mismo. `isDefault` en cada elemento lo resuelve |
| Paginar el catálogo | `spec.md` §6.1 lo resolvió, y con una fila la discusión es teórica. Paginar un catálogo que alimenta selectores obliga a recorrer páginas para llenar un desplegable |
| Devolver la colección como arreglo desnudo | Impide añadir después cualquier metadato sin romper a todos los clientes. Mismo criterio de `RF-SP-010` §4 |
| Ofrecer búsqueda sobre código y nombre | `spec.md` §6.1 no la pide, y sobre un catálogo de una fila sería ceremonia. Es la diferencia con `RF-SP-021` |
| Devolver también las tasas de cambio | `spec.md` §4.2 las excluye. Una tasa tiene vigencia temporal, fuente y reglas de actualización propias: es un requerimiento, no un campo |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| ~~`decimal_places = 2` resulta no ser lo que el negocio quería~~ | — | **Resuelto el 21-08-2026:** confirmados dos decimales, que es lo que ISO 4217 asigna al dólar. La misma revisión corrigió la moneda sembrada, que el borrador declaraba como `COP` contra lo que dice `requirements/sp.md` §10.5 (§2) |
| Un módulo financiero guarda importes sin referencia a la moneda | **Alto** | Es exactamente lo que este catálogo existe para evitar (`spec.md` §2), y este requerimiento no puede impedirlo por su cuenta. La obligación queda declarada en §8, donde la verá quien construya el módulo de cobros |
| Un módulo financiero redondea con una constante en lugar de con `decimal_places` | **Alto** | Ídem. El síntoma no es un fallo sino un importe ligeramente distinto, que nadie nota hasta que alguien concilia |
| La migración de siembra no se aplica en un entorno y el servicio no arranca | Medio | Es el comportamiento pretendido (§2). El mensaje de fallo debe decir qué falta y cuál es la migración que lo siembra, no solo que la comprobación falló |
| `RF-SP-023` no puede cambiar cuál es la moneda por defecto sin violar el índice único | Bajo | **Se resuelve sin tocar el esquema, y esto se corrigió el 21-08-2026.** El borrador proponía declarar `uq_currencies_single_default` diferida: **no es posible** —`DEFERRABLE` solo se aplica a restricciones, y un índice único **parcial** no lo es— y además no hace falta. El intercambio se hace con dos sentencias en la misma transacción, primero `false` en la vigente y después `true` en la nueva, de modo que en ningún instante hay dos filas en `true`. Lo que `RF-SP-023` sí deberá decidir es qué ocurre si la nueva estaba inactiva, porque `ck_currencies_default_active` lo impide |
| El catálogo crece y devolverlo entero deja de ser razonable | Bajo | Las monedas de operación de una empresa se cuentan con los dedos. Si creciera en un orden de magnitud, la decisión de no paginar habría que revisarla en la especificación, no aquí |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V14` y `V15` aplicadas) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel unitario: este requerimiento no tiene `domain`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-130` | Integración + API | La respuesta trae cada moneda con `code`, `name`, `symbol`, `decimalPlaces`, `isDefault` e `isActive`, y el cuerpo **no** contiene campos de paginación |
| `CA-SP-131` | API | Sobre la colección `/api/v1/currencies`, que está mapeada por el `GET` de este requerimiento, `POST`, `PUT`, `PATCH` y `DELETE` devuelven **`405`**. Sobre `/api/v1/currencies/{id}` y sobre `/api/v1/currencies/{id}/status` devuelven **`404`**, porque **ninguna de las dos rutas está mapeada**: no existe endpoint de detalle de moneda, y el subrecurso de estado llegará con `RF-SP-023`. Corregido el 21-08-2026: el borrador exigía `405` en todas, y un `405` presupone una ruta mapeada para algún método. Es la única forma de verificar `RN-SP-010`, que no tiene código que la implemente |
| `CA-SP-168` | Integración + API | Cada moneda devuelve `decimalPlaces`, y el campo nunca es nulo ni se omite |
| `CA-SP-169` | Integración | Tras `V15` existe **exactamente una** fila con `is_default = true`. Un `INSERT` directo de una segunda es rechazado por `uq_currencies_single_default` |
| `CA-SP-170` | API | Con una moneda inactiva sembrada en la prueba: sin el parámetro no aparece; con `includeInactive=true` aparecen ambas |
| `CA-SP-132` | Integración | Tras `V15`, el catálogo contiene la moneda con la que opera el sistema, con su código, sus decimales y su símbolo |
| `CA-SP-133` | API | Un actor autenticado sin `currencies:read` recibe `403`, no obtiene dato alguno y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Catálogo vacío al arrancar | Integración | Con la tabla vacía, el contexto de Spring **no levanta** y el mensaje de fallo nombra la migración de siembra |
| Moneda por defecto inactiva | Integración | Un `UPDATE` directo que ponga `is_active = false` en la moneda por defecto es rechazado por `ck_currencies_default_active`. Es la prueba que verifica el último caso límite de `spec.md` §13 **antes** de que exista `RF-SP-023` |
| Moneda sin símbolo | Integración | Se devuelve con `symbol: null`, sin omitir el campo |
| Moneda sin decimales | Integración | `decimal_places = 0` se acepta y se devuelve como `0`, distinguible de un nulo. `-1` y `5` son rechazados por `ck_currencies_decimal_places` |
| Catálogo con una sola moneda | API | La respuesta sigue siendo una colección con un elemento, no un objeto suelto |
| Formato del código | Integración | `ck_currencies_code_format` rechaza `usd`, `US` y `USD1`; acepta `USD` |
| Auditoría de la siembra | Integración | Tras `V15` existe una fila en `audit_change_log` con `entity = 'currencies'`, `action = 'CREATE'` y `actor_id`, `correlation_id` e `ip_address` **nulos** |
| Parámetros de paginación ignorados | API | `?page=2&size=5` devuelve el catálogo completo, no un error |
| Parámetro no booleano | API | `includeInactive=quizas` devuelve `400`, no se interpreta como `false` |
| Orden estable | Integración | Con tres monedas sembradas en la prueba, dos llamadas consecutivas devuelven el mismo orden por `code` |
| Número de sentencias por petición | Integración | **Una**, con y sin el parámetro |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo.
