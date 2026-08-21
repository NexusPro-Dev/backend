# PLAN — `RF-SP-023` Cambiar el estado de una moneda

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-023` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí.

---

## 1. Enfoque

Es el gemelo de [`RF-SP-022`](../022-cambiar-estado-pais/plan.md) y **hereda de él la forma entera**: subrecurso `/status`, estado destino booleano, idempotencia resuelta en el dominio, bloqueo de fila para que la concurrencia no duplique eventos, auditoría solo en `audit_change_log` y sin motivo. Lo que allí está argumentado no se repite aquí; este documento se limita a lo que difiere.

Y lo que difiere es una sola cosa, aunque gobierna el plan entero: **hay una moneda que no puede desactivarse**, y esa prohibición ya está en la base de datos.

`RF-SP-019` §2 declaró `ck_currencies_default_active CHECK (NOT is_default OR is_active)` al crear la tabla, y lo hizo con este requerimiento en mente: «`RF-SP-023` nace con la mitad de su trabajo hecho». De ahí salen las tres decisiones del plan:

1. **La garantía la da la restricción; la verificación en el caso de uso existe para dar un mensaje.** Es el criterio que `RF-SP-001` §9 fijó para la unicidad y que se repite aquí: la restricción decide, el `SELECT` redacta. Sin la verificación previa, la operación fallaría igual pero con un error de integridad convertido en `500`.
2. **Este requerimiento es inerte hasta que exista la segunda moneda.** `spec.md` §13 lo dice y conviene no disimularlo: hoy el catálogo tiene una sola moneda (`USD`) y es la de defecto, de modo que ninguna operación de este requerimiento puede aplicarse sobre ella. Se construye ahora porque el esquema y el listado ya existen, y porque construirlo cuando urja sería construirlo con prisa.
3. **La operación no toca la definición de la moneda.** `decimal_places` es el campo del que depende todo redondeo financiero (`RF-SP-019` §4), y `CA-SP-188` exige que no cambie. El contrato lo garantiza rechazando cualquier campo que no sea el estado.

`domain` participa en lo mismo que en `RF-SP-022` —la idempotencia— más la única regla de negocio de este requerimiento.

## 2. Cambios de esquema

**Ninguno.** La tabla `currencies`, sus seis restricciones y la siembra de `USD` las crean `V14__create_currencies.sql` y `V15__seed_currencies.sql` (`RF-SP-019`), y el permiso `currencies:update` sale de `V3__seed_permissions.sql` (`RF-SP-010`).

Las dos restricciones que este requerimiento hereda ya garantizadas:

| Restricción | Qué garantiza aquí |
|---|---|
| `ck_currencies_default_active` | `EX-001`: la moneda por defecto no puede quedar inactiva. La operación falla en la base de datos aunque el caso de uso no la verificara |
| `uq_currencies_single_default` | Que sigue habiendo exactamente una moneda por defecto. Este requerimiento no la toca, y por eso no puede romperla |

**No se añade columna de motivo**, por lo dicho en `RF-SP-022` §2 y confirmado en `spec.md` §14 de aquel requerimiento, resolución 2: el Art. V.13 lo obliga donde el registro desaparece, y aquí la moneda sigue existiendo.

**Este requerimiento no cambia cuál es la moneda por defecto**, y `spec.md` §4.2 lo excluye de forma explícita. `RF-SP-019` §10 dejó anotado cómo se haría el día que exista un requerimiento para ello —dos sentencias en la misma transacción, primero `false` en la vigente y después `true` en la nueva, que en ningún instante dejan dos filas en `true`— y también que **no** puede resolverse difiriendo `uq_currencies_single_default`, porque un índice único parcial no es una restricción y no admite `DEFERRABLE`. Nada de eso pertenece a este plan; se referencia para que no se reabra.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Currency` | **Nuevo** | Agregado. Es el primero de esta tabla: `RF-SP-019` no tiene `domain` porque solo lee. Contiene `activate()`, `deactivate()` y `RN-SP-010` |
| `domain` | `CurrencyRepository` | **Nuevo** | Puerto: `findByIdForUpdate(UUID): Optional<Currency>` y `save`. Distinto de `CurrencyQueryRepository`, que es de lectura (§3, abajo) |
| `domain` | `DefaultCurrencyDeactivation` | Nuevo | Excepción de dominio de `EX-001`. Lleva el código de la moneda, para que el mensaje pueda nombrarla |
| `application` | `ChangeCurrencyStatusService` | Nuevo | Caso de uso. `@Transactional`, aplica el cambio y emite la auditoría solo si lo hubo |
| `application` | `ChangeCurrencyStatusCommand` | Nuevo | Entrada del caso de uso: identificador y estado destino. Sin tipos de HTTP |
| `application` | `CurrencyChangeAuditor` | Nuevo | Puerto hacia `shared/audit` para los eventos de cambio de este recurso |
| `infrastructure` | `JpaCurrencyRepository` | Nuevo | Adaptador. Carga bloqueada, persistencia y traducción de la violación de `ck_currencies_default_active` |
| `infrastructure` | `CurrencyEntity` | Sin cambios | Mapeo JPA de `RF-SP-019`. Aquí sí se instancia, porque hay escritura |
| `infrastructure` | `CurrencyJpaMapper` | Nuevo | Conversión entidad ↔ agregado; el agregado no se anota con JPA |
| `api` | `CurrencyController` | **Modificado** | Añade `PATCH /api/v1/currencies/{id}/status`. Es el segundo y último método de este controlador |
| `api` | `ChangeCurrencyStatusRequest` | Nuevo | DTO con el estado destino. **No lleva motivo** |
| `api` | `CurrencyResponse` | Sin cambios | DTO de `RF-SP-019`. Se reutiliza tal cual |
| `shared/api` | `CanonicalUuidConverter` | Sin cambios | Creado en `RF-SP-003` |

Tres decisiones de reparto:

**`CurrencyRepository` es un puerto nuevo y distinto de `CurrencyQueryRepository`.** El de `RF-SP-019` devuelve `CurrencyItem`, un modelo de lectura, y su servicio es `readOnly`; este devuelve el agregado y lo protege. Es el mismo criterio con el que `RF-SP-002` separó `RoleQueryRepository` de `RoleRepository` y `RF-SP-017` §3 hizo lo propio con las membresías. Ampliar el de consulta con un `save` metería una escritura en un puerto cuyo servicio declara que no escribe.

**`Currency` como agregado nace aquí y no en `RF-SP-019`.** Aquel requerimiento no tiene `domain` porque su regla es negativa y se cumple por ausencia de endpoint (`RF-SP-019` §3). Este introduce la primera escritura sobre la tabla y con ella la primera regla que hay que verificar, de modo que el agregado aparece cuando hay algo que proteger y no antes.

**`EX-001` se lanza desde `domain`, no desde el servicio.** `RN-SP-010` dice que la moneda por defecto no se desactiva, y esa es la única regla de negocio del requerimiento: tiene que ser verificable con una prueba unitaria sin Spring ni base de datos (Art. VI.3). El servicio orquesta; el agregado decide.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/currencies/{id}/status` | Activa o desactiva una moneda del catálogo |

Subrecurso propio, por lo dicho en `RF-SP-022` §4: `RN-SP-010` prohíbe editar la moneda, de modo que `PATCH /currencies/{id}` **no existe y debe seguir sin existir** —devuelve `404`, porque esa ruta no está mapeada para ningún método (`RF-SP-019` §11)—.

**Petición**

```json
{ "isActive": false }
```

Booleano y no enumerado, estado destino y no acción, y `FAIL_ON_UNKNOWN_PROPERTIES` activo: todo ello por los motivos de `RF-SP-022` §4, que no se repiten. Aquí el rechazo de campos desconocidos sostiene además `CA-SP-188`: un cuerpo con `decimalPlaces`, `symbol`, `name` o `isDefault` devuelve `400` y **no se ignora en silencio**. Sin ese rechazo, el criterio que protege el campo del que depende todo redondeo financiero no comprobaría nada.

**Respuesta `200`**

`CurrencyResponse`, el mismo cuerpo que devuelve `RF-SP-019`, con el estado ya actualizado:

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
  "code": "USD",
  "name": "Dólar estadounidense",
  "symbol": "$",
  "decimalPlaces": 2,
  "isDefault": true,
  "isActive": true
}
```

- **La respuesta incluye `isDefault` y `decimalPlaces`**, que este requerimiento no puede cambiar. No es redundancia: son los dos campos cuya inmutabilidad exige `CA-SP-188`, y devolverlos permite comprobarla en la misma respuesta.
- **`FA-001` devuelve `200` igual**, con la moneda sin cambios y **sin dejar evento de auditoría**. Es donde `CA-SP-190` lo verifica.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | El identificador no es un UUID en forma canónica | `VAL-001` | `id` |
| `400` | `isActive` ausente, nulo o no booleano (`VAL-001`) | `VAL-001` | `isActive` |
| `400` | Cuerpo con un campo desconocido, incluido un motivo o cualquier campo de la definición | `VAL-001` | El campo sobrante |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `currencies:update` | `AUTH-002` | — |
| `404` | No existe moneda con ese identificador (`EX-002`) | `EX-002` | — |
| `409` | Se intenta desactivar la moneda por defecto (`EX-001`) | `RN-SP-010` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **El `409` lleva `RN-SP-010` como `error_code`, y no `EX-001`.** Es la convención de `development-guide.md` §7.2: el código es el identificador de la regla incumplida cuando existe una, y el de la excepción cuando no la hay. Aquí sí la hay, y `spec.md` §10 pide de forma explícita que la respuesta la cite. Es la diferencia con `RF-SP-016`, cuyo duplicado no viola ninguna regla `RN-…` y por eso usa el código de la excepción.
- **El mensaje del `409` debe nombrar la moneda y explicar la consecuencia**, no solo negar. `spec.md` §10 lo redacta: los importes del sistema quedarían sin referencia válida, y cambiar cuál es la moneda por defecto es una operación de migración, no de API. Sin esa segunda frase, quien reciba el error intentará buscar el endpoint que no existe.
- **`409` y no `422`**: es un conflicto con el estado actual del recurso sobre datos que existen, que es la definición de `409` en `architecture.md` §7.2.
- **`VAL-002` y `VAL-003` no producen códigos propios.** Enuncian como validación lo mismo que `EX-002` y `EX-001`; un solo hecho, un solo código.
- **Reactivar nunca falla por regla.** `EX-001` solo alcanza a la desactivación: activar una moneda inactiva no puede violar nada, y activar la de defecto —que ya está activa por `ck_currencies_default_active`— cae en `FA-001`.

**Orden de verificación**

1. Formato del identificador y del cuerpo (`VAL-001`), todas juntas.
2. Moneda existente (`EX-002`), cargada **con bloqueo de fila** (§7).
3. Si se pide desactivar, que no sea la moneda por defecto (`EX-001`), en `domain`.
4. Aplicación del estado, que decide si hubo cambio.

El paso 3 va **después** de cargar y antes de aplicar: no puede evaluarse sin la fila, y no debe evaluarse después de haberla modificado.

**Qué ocurre si la verificación previa faltara.** La operación no pasaría igualmente: `ck_currencies_default_active` la rechazaría en el `flush`, y el adaptador traduciría esa violación —distinguiéndola **por el nombre de la restricción**, nunca por el texto del mensaje del driver— al mismo `409`. La verificación en `domain` existe para que el mensaje sea comprensible y para que la regla sea probable sin base de datos; la restricción existe para que la garantía no dependa de que alguien la escriba. Ambas se prueban por separado (§11).

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `PATCH /api/v1/currencies/{id}/status` | `currencies:update` |

- El permiso **ya existe** en el catálogo: lo crea `V3__seed_permissions.sql` (`RF-SP-010`), que sembró el bloque de `currencies:` aunque ningún endpoint lo declarara todavía.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).

### `currencies:update` se reserva a `SUPERADMIN`

Es la decisión de este plan con mayor alcance fuera de él, y resuelve una contradicción real que se descubrió al redactarlo.

`spec.md` §3 declara **un solo actor**, el Super Administrador, y la ficha de `requirements/sp.md` §6.2 dice lo mismo. Pero `RF-SP-001` §2 decidió que `V7__seed_system_roles.sql` diera a `ADMIN` **el catálogo completo salvo `audit:read-security`**, de modo que `ADMIN` habría tenido `currencies:update` y habría podido desactivar monedas contra lo que la especificación aprobada dice.

Se resuelve del lado de la especificación, confirmado el 21-08-2026: **`V7` pasa a excluir dos permisos de `ADMIN`, no uno**. La reserva de `SUPERADMIN` queda así:

| Permiso reservado | Por qué |
|---|---|
| `audit:read-security` | Es el registro donde quedan los intentos de escalada de privilegios. Un `ADMIN` que pudiera leerlo comprobaría si su propio intento quedó registrado (`RF-SP-014` §5) |
| `currencies:update` | El estado de una moneda condiciona todo cálculo financiero, y el catálogo se puebla por migración: la operación pertenece a quien mantiene la plataforma, no a la administración del negocio (`spec.md` §3) |

**El precio, y es el mismo que ya se aceptó para el primero:** `security.md` §4.1 exige que `ADMIN` posea todo permiso que cualquier rol de negocio declare, porque `RN-SEG-003` le impediría crear un rol que declarase uno que él no tiene. Con esta reserva, **`ADMIN` no podrá crear ningún rol que declare `currencies:update`**; solo `SUPERADMIN` podrá. Es coherente con que la operación le esté vedada: quien no puede hacer algo tampoco debería poder delegarlo.

`security.md` §4.1 y §4.4 se enmiendan para recoger la segunda reserva y para que la obligación de asociar todo permiso sembrado a `SUPERADMIN` **y** a `ADMIN` lleve su excepción escrita (§8). Sin esa enmienda, la próxima migración que siembre permisos seguirá la regla al pie de la letra y devolverá a `ADMIN` lo que este plan le retira.

Lo demás:

- **No hay techo de privilegios que verificar.** Una moneda no concede permisos, de modo que los permisos efectivos del actor no intervienen y la resolución del permiso **sí** puede usar la caché de `security.md` §4.5. Misma conclusión que `RF-SP-019` §5 y `RF-SP-022` §5.
- **No hay filtrado por alcance de datos.** Una moneda no pertenece a nadie.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de `audit_security_log` (§6). `CA-SP-191` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Cambio efectivo | `audit_change_log` | `module = 'SP'`, `entity = 'currencies'`, `entity_id` de la moneda, `action = 'UPDATE'`, `changes` con **solo** `is_active`, con su valor anterior y el nuevo |
| Cambio sin efecto (`FA-001`) | — | **Ningún evento**, en ningún registro |
| Rechazo `409` por `EX-001` | `audit_error_log` | `resource = 'currencies'`, `operation = 'PATCH /api/v1/currencies/{id}/status'`, `error_code = 'RN-SP-010'`, `error_type = 'BUSINESS_RULE'`, `http_status = 409`, `severity = 'MEDIA'` y `message` saneado |
| Rechazo `404` por `EX-002` | — | **No se audita**: `architecture.md` §6.6.4 lo deja fuera y `ck_audit_error_log_status` lo impide en el esquema |
| Rechazo `400` de formato | — | **No se audita** (`architecture.md` §6.6.4) |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_security_log` **por el cambio** | **No aplica** |
| — | `audit_deletion_log` | No aplica: una moneda no se elimina |

Tres decisiones:

- **El cambio de estado no emite evento de seguridad, y `CA-SP-339` lo verifica en los dos sentidos.** `spec.md` §14 de `RF-SP-022`, resolución 2, lo fijó **como criterio del módulo**: el cambio de estado de un catálogo se audita en `audit_change_log` y solo ahí. No hay privilegio en juego —una moneda inactiva deja de ofrecerse, no retira acceso a nadie— y `security.md` §8.1 es un catálogo cerrado de eventos de control de acceso. Es la asimetría con `RF-SP-007`, que sí registra en ambos porque un rol inactivo deja de conceder permisos.
- **El rechazo por `EX-001` sí se audita, con severidad `MEDIA`.** Es un rechazo de regla de negocio y sigue la convención que fijó `RF-SP-001` §6. No es `ALTA`: intentar desactivar la moneda por defecto es un error de operación, no un intento de escalada de privilegios, que es lo que la severidad alta señala en el resto del módulo.
- **`changes` lleva solo `is_active`**, no la moneda entera, y **`updated_at` queda fuera** por ser consecuencia de la escritura y no un dato que alguien decidiera cambiar. Igual que en `RF-SP-022` §6.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Bloqueo de la fila, `UPDATE` de `is_active` y su evento en `audit_change_log` | **La misma** (Art. V.14) |
| `audit_error_log` de un rechazo o un fallo | **Independiente**, `REQUIRES_NEW` |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`@Transactional` vive sobre `ChangeCurrencyStatusService`, en `application`.

**La moneda se carga con bloqueo de fila**, por el mismo motivo que en `RF-SP-022` §7 y sin repetir el argumento: sin él, dos desactivaciones simultáneas emiten dos eventos para un solo cambio, y el segundo describe una transición que no ocurrió. Aquí el bloqueo tiene además un efecto sobre `EX-001`: garantiza que entre comprobar que la moneda no es la de defecto y escribir el estado no pueda colarse una operación que la convirtiera en la de defecto. Hoy ninguna operación de API puede hacerlo —cambiar la moneda por defecto es una migración—, de modo que el bloqueo cubre un caso que solo podría venir de un despliegue concurrente con una migración; se toma igual, porque no cuesta nada.

**No hay evento posterior al commit** y **no hay caché que invalidar**: una moneda no interviene en la resolución de permisos de `security.md` §4.5. Igual que en `RF-SP-022` §7.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| **`RF-SP-001`** | Su `V7__seed_system_roles.sql` pasa a excluir **dos** permisos del conjunto de `ADMIN`: `audit:read-security` y `currencies:update` (§5). Es una edición de la migración, no una migración de datos, mientras nada esté desplegado |
| **`security.md`** | §4.1 y §4.4 recogen la segunda reserva de `SUPERADMIN`, y la obligación de asociar todo permiso sembrado a `SUPERADMIN` y a `ADMIN` pasa a llevar su lista de excepciones. Sin esa enmienda, la próxima migración que siembre permisos devolverá a `ADMIN` lo que este plan le retira |
| **`RF-SP-019`** | Comparte `CurrencyController` y `CurrencyResponse`. Sus dos restricciones son lo que hace innecesario implementar `RN-SP-010` como lógica de aplicación (§2). Su `CA-SP-131` verifica que `/{id}/status` devuelve `404` mientras este requerimiento no exista, y **debe actualizarse cuando se integre**: pasará a devolver `405` para los métodos distintos de `PATCH`, y `404` seguirá siendo la respuesta de `/{id}` a secas |
| `RF-SP-022` | Es el gemelo del que este plan hereda la forma. La única diferencia de contrato es el `409`, que allí no existe |
| `RF-SP-011` | Su consulta responde también por la entidad `currencies` con `action = 'UPDATE'`. Ninguna adaptación |
| `RF-SP-014` | **No** recibe nada de este requerimiento, y es la decisión de §6 |
| **Módulos financieros futuros** | Una moneda inactiva **sigue resolviéndose** para los importes ya expresados en ella, con sus decimales intactos (`CA-SP-189`). La obligación que `RF-SP-019` §8 declaró se confirma: el redondeo usa el `decimal_places` de la moneda del importe, nunca una constante, y **nunca se filtra por `is_active` al resolver un importe guardado** —solo al ofrecer monedas en una operación nueva— |
| `requirements/sp.md` | Ninguna enmienda. `RN-SP-010` ya recoge que el estado es lo único modificable y que la moneda por defecto no puede desactivarse |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Confiar solo en `ck_currencies_default_active` y no verificar en el dominio | La operación fallaría igual, pero con un error de integridad que llegaría al cliente como `500` en lugar del `409` que `spec.md` §10 exige, y `RN-SP-010` no sería probable sin levantar PostgreSQL (Art. VI.3) |
| Verificar solo en el dominio y no declarar la restricción | Ya está declarada desde `RF-SP-019` §2, y retirarla dejaría la garantía a merced de que ningún camino futuro —una migración, un proceso interno— la esquive. La restricción decide; la verificación redacta |
| Permitir desactivar la moneda por defecto si no hay importes registrados | Añade una condición que hay que evaluar consultando tablas de módulos que no existen, para habilitar un caso que se resuelve mejor cambiando antes cuál es la moneda por defecto. Y el día que hubiera importes, la operación pasaría a fallar sin que nada hubiera cambiado en el catálogo |
| Permitir cambiar cuál es la moneda por defecto en la misma operación | `spec.md` §4.2 lo excluye. Arrastra una decisión que excede a un catálogo —cómo se reinterpretan los importes ya guardados— y `RF-SP-019` §14, pregunta 3, la dejó fuera de forma explícita hasta que haya más de una moneda |
| Un permiso propio, `currencies:deactivate` | Separaría activar de desactivar sin que ninguna de las dos sea más peligrosa que la otra: la operación es un interruptor y reactivar es la corrección de haber desactivado |
| Dejar `currencies:update` en el conjunto de `ADMIN` | Contradice `spec.md` §3 y la ficha de `requirements/sp.md` §6.2, que declaran un único actor. Mantenerlo habría exigido devolver ambos documentos a su compuerta para añadir al Administrador como actor (§5) |
| Enviar una acción, un enumerado, o exigir motivo | Descartadas en `RF-SP-022` §9 con los mismos argumentos, que aquí no se repiten |
| Devolver `204` sin cuerpo | `spec.md` §6.2 declara la moneda actualizada como salida, y devolverla permite comprobar en la misma respuesta que `decimalPlaces`, `symbol` y `isDefault` no cambiaron |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La enmienda de `security.md` §4.1 no se aplica y una migración futura devuelve `currencies:update` a `ADMIN` | **Alto** | El síntoma no sería visible: `ADMIN` podría desactivar monedas y nada fallaría. Declarado en §8 como enmienda obligatoria, y verificado por la prueba de §11 que comprueba el conjunto de permisos de `ADMIN` tras `V7` |
| Se implementa sin bloqueo de fila y la auditoría registra dos eventos para un solo cambio | Medio | Heredado de `RF-SP-022` §10, con la misma prueba de concurrencia |
| La violación de `ck_currencies_default_active` no se traduce y sale como `500` | Medio | El adaptador la distingue por **nombre de restricción**, nunca por el texto del mensaje del driver, que cambia entre versiones. Prueba propia en §11, forzando el camino sin verificación previa |
| Se copia de `RF-SP-007` y se emite además el evento de seguridad | Medio | `CA-SP-339` verifica la ausencia. El criterio del módulo está fijado en `spec.md` §14 de `RF-SP-022` |
| El requerimiento se implementa y nadie puede ejercitarlo | Bajo | Es el estado real: con una sola moneda, que además es la de defecto, ninguna operación aplica (`spec.md` §13). Las pruebas siembran una segunda moneda para poder ejercitarlo, y eso **no** cambia el catálogo de producción |
| Un módulo financiero filtra por `is_active` al resolver un importe ya guardado | **Alto** | Obligación declarada en §8 y ya anotada en `RF-SP-019` §8. El síntoma no es un fallo sino un importe que deja de poder interpretarse |
| `CA-SP-131` de `RF-SP-019` empieza a fallar al integrarse este requerimiento | Bajo | Previsto en §8: esa prueba afirma hoy que `/{id}/status` devuelve `404`, y pasará a `405` para los métodos distintos de `PATCH`. Debe actualizarse en el mismo Pull Request |

## 11. Estrategia de prueba

Niveles: **Unitaria** (dominio, sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V14` y `V15` aplicadas **más una segunda moneda sembrada en la prueba**) y **API**.

**Toda prueba de este requerimiento necesita una segunda moneda.** Con una sola, que además es la de defecto, ninguna operación aplica (`spec.md` §13). Las pruebas siembran una moneda adicional en su propio montaje; el catálogo de producción no cambia.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-185` | Unitaria + Integración + API | Sobre una segunda moneda no marcada por defecto, `deactivate()` y `activate()` aplican el estado y devuelven que hubo cambio; el endpoint devuelve `200` y la fila queda con ese valor |
| `CA-SP-186` | Unitaria + Integración + API | El dominio rechaza desactivar la moneda por defecto **sin base de datos**; el endpoint devuelve `409` con `error_code = 'RN-SP-010'` y un mensaje que nombra la moneda y explica la consecuencia; y `ck_currencies_default_active` rechaza el `UPDATE` también por sentencia directa |
| `CA-SP-187` | Integración + API | Tras desactivar, `GET /api/v1/currencies` **no** devuelve la moneda, y con `includeInactive=true` sí, con `isActive: false`. Se verifica sobre el endpoint de `RF-SP-019` |
| `CA-SP-188` | Integración + API | Tras el cambio, `code`, `name`, `symbol`, `decimal_places` e `is_default` son idénticos a los anteriores, y solo `is_active` y `updated_at` cambiaron. Un cuerpo con cualquiera de esos campos devuelve `400` por campo desconocido |
| `CA-SP-189` | Integración | Una moneda desactivada **sigue existiendo** y se recupera por su identificador con sus decimales intactos. Hoy no hay tabla de importes que la referencie, de modo que la prueba lo comprueba sobre la fila; cuando exista el primer módulo financiero, esta prueba es la que hay que ampliar |
| `CA-SP-190` | Unitaria + Integración | El dominio devuelve «sin cambio» al aplicar el estado que ya tenía; tras esa petición **no existe ninguna fila nueva** en `audit_change_log`, y `updated_at` tampoco cambió |
| `CA-SP-339` | Integración | Tras un cambio efectivo existe **una** fila en `audit_change_log` con `action = 'UPDATE'` y `changes` conteniendo solo `is_active`, y **ninguna** fila nueva en `audit_security_log` |
| `CA-SP-340` | API | Un cuerpo con `reason` devuelve `400` por campo desconocido, **no se ignora** |
| `CA-SP-191` | API | Un actor autenticado sin `currencies:update` recibe `403`, la moneda no cambia y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| **Reserva de `currencies:update`** | Integración | Tras `V7`, el conjunto de permisos de `ADMIN` **no** contiene `currencies:update` ni `audit:read-security`, y el de `SUPERADMIN` contiene el catálogo completo. Es la prueba que impide que una migración futura devuelva el permiso sin que nadie lo note (§10) |
| Un `ADMIN` intenta la operación | API | Recibe `403` con `AUTH-002`, y queda el evento de denegación. Es la mitad observable de la reserva |
| Traducción de la restricción sin verificación previa | Integración | Forzando el camino que salta la verificación de `domain`, la violación de `ck_currencies_default_active` se traduce a `409` con `RN-SP-010`, **nunca a `500`**, y se distingue por nombre de restricción |
| **Cambio concurrente de la misma moneda** | Integración | Dos transacciones reales que desactivan la misma moneda a la vez: ambas devuelven `200`, la fila queda inactiva y existe **exactamente un** evento en `audit_change_log` |
| Reactivar una moneda inactiva | API | Se admite sin condiciones y sin verificar `EX-001`: activar no puede violar la regla |
| Activar la moneda por defecto | API | Cae en `FA-001` y devuelve `200` sin evento: ya está activa por `ck_currencies_default_active` |
| Catálogo con una sola moneda | API | Toda operación sobre ella devuelve `409` con `RN-SP-010` si se pide desactivarla, y `200` sin evento si se pide activarla. Es el estado real hoy y documenta que el requerimiento está inerte hasta la segunda moneda |
| Moneda inexistente | API | `404` con `EX-002`, y **no** se trata como un cambio sin efecto |
| Identificador con formato incorrecto | API | `abc`, `1-1-1-1-1` y un UUID de 35 caracteres devuelven los tres `400` con `VAL-001` y campo `id`, nunca `404` |
| El `404` no llega a la auditoría de error | Integración | Tras un `404`, no existe fila nueva en `audit_error_log`; un `INSERT` directo con ese estado es rechazado por `ck_audit_error_log_status` |
| Ausencia de edición sobre el recurso | API | `PATCH` y `PUT` sobre `/api/v1/currencies/{id}` siguen devolviendo `404` tras existir este endpoint; sobre `/{id}/status`, los métodos distintos de `PATCH` devuelven `405` |
| Número de sentencias por petición | Integración | El `SELECT … FOR UPDATE`, el `UPDATE` y la inserción del evento; ninguna adicional, y **ninguna escritura** cuando la petición cae en `FA-001` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo.
