# PLAN — `RF-SP-022` Cambiar el estado de un país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-022` |
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

Un `UPDATE` de una columna booleana. Es, literalmente, todo lo que este requerimiento escribe, y aun así merece plan propio por tres motivos que no están en la sentencia:

1. **Es la única mutación que `countries` admitirá jamás.** El plan de [`RF-SP-020`](../020-registrar-pais/plan.md) §4 cerró la tabla de métodos del recurso y declaró que `PATCH /api/v1/countries/{id}/status` sería **la excepción, y la única**. Este documento la abre, y al abrirla fija su forma para siempre: lo que no entre aquí no entra después, porque `RN-SP-009` no deja otra puerta.
2. **La idempotencia no es una comodidad, es un criterio.** `CA-SP-182` exige que aplicar el estado que la fila ya tiene **no registre evento**, y eso obliga a que el dominio distinga «se aplicó el cambio» de «no había nada que cambiar». Es el mismo mecanismo que `RF-SP-007` §3 introdujo para los roles y se reutiliza tal cual.
3. **Este requerimiento se audita en un solo registro, y decirlo es la mitad del trabajo.** `CA-SP-183` no pide solo que el evento esté en `audit_change_log`: pide que **no** esté en `audit_security_log`. Es una asimetría deliberada con `RF-SP-007`, y §6 la argumenta.

`domain` participa poco pero participa: `Country.activate()` y `Country.deactivate()` son donde vive la idempotencia, y son verificables sin Spring ni base de datos (Art. VI.3).

## 2. Cambios de esquema

**Ninguno.** La tabla `countries`, sus dos índices únicos, sus dos `CHECK` y la intercalación de `name` los crea `V16__create_countries.sql` (`RF-SP-020`), y el permiso `countries:update` sale de `V3__seed_permissions.sql` (`RF-SP-010`), que sembró el bloque completo de `countries:` aunque ningún endpoint lo declarara todavía.

**No se añade columna de motivo**, y no es una omisión: `spec.md` §14, pregunta 2, lo resolvió. El Art. V.13 obliga al motivo solo donde el registro desaparece, y aquí el país sigue existiendo y la operación es reversible en una petición. Una columna nulable «por si acaso» produce un campo casi siempre vacío del que nadie sabe si puede fiarse. Es la misma resolución que en `RF-SP-007` §2.

**No se añade columna de versión ni marca de bloqueo optimista.** La serialización que `spec.md` §13 exige se resuelve con un bloqueo de fila en la transacción (§7), que no necesita esquema.

**`updated_at` cobra sentido con este requerimiento.** `RF-SP-020` §2 la declaró contra lo que decían `requirements/sp.md` §10.6 y `modelo-datos.md` §2, precisamente porque esta operación existía: sin ella no habría forma de saber cuándo se retiró un país de la circulación salvo recorriendo la auditoría. Aquí es la única columna que cambia junto a `is_active`.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Country` | **Modificado** | Añade `activate()` y `deactivate()`, que aplican el estado y **devuelven si hubo cambio efectivo**. Es donde vive `CA-SP-182` |
| `domain` | `CountryRepository` | **Modificado** | Puerto de `RF-SP-020`. Añade `findByIdForUpdate(UUID): Optional<Country>`, que carga la fila bloqueada (§7) |
| `application` | `ChangeCountryStatusService` | Nuevo | Caso de uso. `@Transactional`, aplica el cambio y emite la auditoría solo si lo hubo |
| `application` | `ChangeCountryStatusCommand` | Nuevo | Entrada del caso de uso: identificador y estado destino. Sin tipos de HTTP |
| `application` | `CountryChangeAuditor` | Sin cambios | Puerto definido en `RF-SP-020`. Se reutiliza tal cual |
| `infrastructure` | `JpaCountryRepository` | **Modificado** | Implementa la carga bloqueada y persiste el cambio |
| `infrastructure` | `CountryEntity`, `CountryJpaMapper` | Sin cambios | Definidos en `RF-SP-020` |
| `api` | `CountryController` | **Modificado** | Añade `PATCH /api/v1/countries/{id}/status`. Es el tercer y último método de este controlador |
| `api` | `ChangeCountryStatusRequest` | Nuevo | DTO con el estado destino. **No lleva motivo** |
| `api` | `CountryResponse` | Sin cambios | DTO de `RF-SP-020`. Se reutiliza tal cual |
| `shared/api` | `CanonicalUuidConverter` | Sin cambios | Creado en `RF-SP-003`. Convierte el identificador de la ruta o falla con `400` |

Dos decisiones de reparto:

**La idempotencia vive en `domain`, no en el servicio.** `deactivate()` devuelve si hubo cambio, y el servicio decide qué hacer con esa respuesta. Ponerla en el servicio —comparando el estado leído con el pedido antes de llamar— la volvería inseparable de la transacción y del puerto: probar `CA-SP-182` exigiría PostgreSQL. Como método del agregado, se prueba con dos líneas y sin Spring. Es exactamente lo que `RF-SP-007` §3 hizo con `Role`.

**No se crea un `CountryStatusChangeAuditor`.** `CountryChangeAuditor` ya existe desde `RF-SP-020` y emite eventos de `audit_change_log` sobre la entidad `countries`; un puerto por operación multiplicaría tipos sin separar nada que esté acoplado.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/countries/{id}/status` | Activa o desactiva un país del catálogo |

**Subrecurso propio, y no un campo dentro de un `PATCH /countries/{id}`.** No es una preferencia de estilo: `RN-SP-009` prohíbe editar el país, de modo que `PATCH /countries/{id}` **no existe y debe seguir sin existir** —devuelve `404`, porque esa ruta no está mapeada para ningún método (`RF-SP-020` §4)—. Un endpoint de edición que solo aceptara un campo sería una puerta abierta a que alguien añadiera el segundo.

**Petición**

```json
{ "isActive": false }
```

- **Se envía el estado destino y no una acción** (`activate` / `deactivate`), porque hace la operación idempotente por construcción: repetir la misma petición deja el mismo resultado, que es lo que `FA-001` describe. Es el criterio de `RF-SP-007` §4.
- **El campo es un booleano `isActive` y no un enumerado `status`.** La columna es `boolean` y `CountryResponse` ya devuelve `isActive` desde `RF-SP-020`: pedir el cambio con un nombre y devolverlo con otro obligaría al cliente a traducir. Es la diferencia con `RF-SP-007`, donde `roles.status` es un dominio cerrado de dos valores con nombre propio —`ACTIVO`, `INACTIVO`— y el enum es lo que corresponde. Aquí no hay tercer estado posible ni previsible: un país se ofrece o no se ofrece.
- **`null` no es un valor admisible.** Ausente o nulo produce `400` con `VAL-001`: el estado es obligatorio (`spec.md` §11).
- **El DTO se deserializa con `FAIL_ON_UNKNOWN_PROPERTIES` activo**, de modo que un cuerpo con `reason`, con `code` o con `name` devuelve `400` y **no se ignora en silencio**. Eso es lo que hace verificable a `CA-SP-338` —la operación no admite motivo— y a `CA-SP-180` —el código y el nombre no cambian—: sin el rechazo, enviarlos se ignoraría y ambos criterios pasarían sin comprobar nada.

**Respuesta `200`**

`CountryResponse`, el mismo cuerpo que devuelve el alta en `RF-SP-020`, con el estado ya actualizado:

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d50",
  "code": "PA",
  "name": "Panamá",
  "isActive": false
}
```

- **`200` y no `204`.** El cuerpo devuelve el país tal como quedó, que es lo que `spec.md` §6.2 pide, y ahorra al cliente una segunda llamada para refrescar el selector.
- **`FA-001` devuelve `200` igual**, con el país sin cambios. No es un error y no se distingue en el estado HTTP: el resultado observable —el país está en el estado pedido— es idéntico. Lo que sí se distingue es que **no queda evento de auditoría**, y ahí es donde `CA-SP-182` lo verifica.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | El identificador no es un UUID en forma canónica | `VAL-001` | `id` |
| `400` | `isActive` ausente, nulo o no booleano (`VAL-001`) | `VAL-001` | `isActive` |
| `400` | Cuerpo con un campo desconocido, incluido un motivo | `VAL-001` | El campo sobrante |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `countries:update` | `AUTH-002` | — |
| `404` | No existe país con ese identificador (`EX-001`) | `EX-001` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **`404` y no `422`**, por el criterio de `development-guide.md` §7.1: el recurso **de la ruta** es el país, y su ausencia es exactamente lo que `404` significa. El `422` se reserva para una referencia inexistente en el cuerpo.
- **`VAL-002` no produce un código propio.** Enuncia como validación lo mismo que `EX-001`; un solo hecho, un solo código. Es el criterio de `RF-SP-003` §4 y `RF-SP-015` §4.
- **No hay `409`.** Este requerimiento no declara ninguna regla de negocio con rechazo: no hay país que no pueda desactivarse. Es la diferencia con `RF-SP-023`, donde la moneda por defecto sí lo tiene, y con `RF-SP-007`, donde el rol de sistema y el rol raíz lo tienen.
- **Un identificador malformado es `400`, no `404`**, y el mecanismo ya está resuelto: `CanonicalUuidConverter` (`RF-SP-003` §4) exige los 36 caracteres canónicos antes de delegar en `UUID.fromString`, porque el JDK convierte sin error formas abreviadas como `1-1-1-1-1`. La ruta **no** se declara con restricción de patrón, que produciría `404` por falta de manejador.
- Todos los `type` que este endpoint usa ya los estrenaron `RF-SP-001` y `RF-SP-003`.

**Orden de verificación**

1. Formato del identificador y del cuerpo (`VAL-001`), todas juntas.
2. País existente (`EX-001`), cargado **con bloqueo de fila** (§7).
3. Aplicación del estado en el dominio, que decide si hubo cambio.

No hay más: el paso 3 no puede fallar por regla de negocio, porque no hay ninguna que lo gobierne.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `PATCH /api/v1/countries/{id}/status` | `countries:update` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`). No hace falta migración de permisos.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **`countries:update` es distinto de `countries:create` y de `countries:read`**, y los tres se conceden por separado. Consultar el catálogo lo necesita cualquiera que rellene un formulario; registrar es irreversible; retirar de la circulación afecta a lo que todos los formularios ofrecen a partir de ese momento.
- **El actor es Administrador o Super Administrador** (`spec.md` §3). `V7__seed_system_roles.sql` da a `ADMIN` el catálogo completo salvo `audit:read-security` (`RF-SP-001` §2), de modo que ambos lo tienen sin ninguna migración adicional.
- **No hay techo de privilegios que verificar.** No existe aquí nada análogo a `RN-SEG-010`: un país no concede permisos, de modo que los permisos efectivos del actor no intervienen y la resolución del permiso **sí** puede usar la caché de `security.md` §4.5. Es la conclusión contraria a la de `RF-SP-001` §5 y la misma que la de `RF-SP-020` §5.
- **No hay filtrado por alcance de datos.** Un país no pertenece a nadie.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de `audit_security_log` (§6). `CA-SP-184` se satisface ahí, no en `ChangeCountryStatusService`.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Cambio efectivo | `audit_change_log` | `module = 'SP'`, `entity = 'countries'`, `entity_id` del país, `action = 'UPDATE'`, `changes` con **solo** `is_active`, con su valor anterior y el nuevo |
| Cambio sin efecto (`FA-001`) | — | **Ningún evento**, en ningún registro |
| Rechazo `404` por `EX-001` | — | **No se audita.** Ver abajo |
| Rechazo `400` de formato | — | **No se audita** (`architecture.md` §6.6.4): es ruido de formulario |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'countries'`, `operation = 'PATCH /api/v1/countries/{id}/status'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_security_log` **por el cambio** | **No aplica.** Ver abajo |
| — | `audit_deletion_log` | No aplica: `RN-SP-009` hace que un país no se elimine nunca |

Cuatro decisiones:

- **El cambio de estado no emite evento de seguridad, y `CA-SP-183` lo verifica en los dos sentidos.** Es la asimetría deliberada con `RF-SP-007`, que sí registra en ambos: allí un rol inactivo **deja de conceder permisos**, y eso es un cambio de privilegio que un responsable de seguridad tiene que poder encontrar. Aquí no hay privilegio en juego —un país inactivo solo deja de ofrecerse en un selector—, y el catálogo de `security.md` §8.1 es cerrado y no lo contempla. Llenarlo de eventos de catálogo degradaría el registro que se consulta buscando un incidente. La decisión la fijó `spec.md` §14, pregunta 1, y `RF-SP-023` §6 la hereda: **el cambio de estado de un catálogo se audita en `audit_change_log` y solo ahí**.
- **`changes` lleva solo `is_active`.** No el país entero: el registro de cambios guarda el diff de lo que mutó (`architecture.md` §6.6.2), y `updated_at` no se incluye porque es consecuencia de la escritura, no un dato de negocio que alguien decidiera cambiar.
- **El `404` no se audita**, y conviene decir por qué no es una omisión. No hay regla de negocio incumplida ni cambio de estado: es un identificador que no encuentra fila. Además está **prohibido**: `ck_audit_error_log_status` (`RF-SP-013` §2) rechaza en el esquema cualquier fila con `http_status` en `(400, 401, 403, 404)`, de modo que intentar registrarlo no produciría un dato incorrecto sino un `INSERT` que falla. Es la misma conclusión de `RF-SP-003` §6.
- **Sin evento no hay autor.** Es la razón por la que `FA-001` no registra nada y a la vez la razón por la que el cambio efectivo sí: `countries` no tiene columna de actor (Art. V.7), de modo que `audit_change_log` es la **única** fuente que responde quién retiró un país de la circulación y cuándo.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Bloqueo de la fila, `UPDATE` de `is_active` y su evento en `audit_change_log` | **La misma** (Art. V.14). Si el cambio se revierte, su evento también |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`@Transactional` vive sobre `ChangeCountryStatusService`, en `application`; nunca en el controlador ni en el repositorio.

**Por qué el país se carga con bloqueo de fila.** `spec.md` §13 lo exige: «ambas peticiones se serializan sobre la fila; la segunda encuentra el estado ya aplicado y cae en `FA-001`». Sin `SELECT … FOR UPDATE`, dos peticiones simultáneas de desactivación leen ambas `is_active = true`, ambas concluyen que hay cambio efectivo y ambas emiten su evento: el resultado en la tabla es correcto y la auditoría queda con **dos eventos para un solo cambio**, el segundo diciendo que pasó de activo a inactivo un país que ya estaba inactivo. Es un defecto que ninguna prueba secuencial detecta y que corrompe la única fuente de autoría que hay.

El bloqueo es de una fila, la operación es excepcional y no estorba a los lectores: el listado de `RF-SP-021` no se ve afectado, porque `FOR UPDATE` no bloquea a quien solo lee.

**No hay evento posterior al commit**, a diferencia de `RF-SP-001` §7 y `RF-SP-007` §7: allí se enganchaba al commit el evento de seguridad, y aquí no hay ninguno que emitir.

**No hay caché que invalidar.** `countries` no interviene en la resolución de permisos de `security.md` §4.5. Es la diferencia con `RF-SP-007`, donde desactivar un rol obliga a invalidar su entrada para que el cambio sea inmediato, y conviene no copiar aquel plan hacia aquí: invalidar de más solo cuesta rendimiento, pero escribir código que busca una caché inexistente cuesta la revisión de quien lo lea después.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| **`RF-SP-020`** | Su tabla de métodos cerrados de §4 gana **su única excepción**, que este plan abre. `PATCH /countries/{id}` sigue devolviendo `404` y `PATCH /countries/{id}/status` pasa a existir: la prueba de `CA-SP-137` debe distinguir ambas rutas, y ya lo hace |
| **`RF-SP-021`** | Es donde el cambio se observa. `includeInactive` deja de ser un parámetro sin efecto práctico: hasta ahora solo podía probarse sembrando un inactivo a mano. `CA-SP-179` se verifica sobre ese endpoint, no sobre este |
| `RF-SP-011` | Su consulta responde también por la entidad `countries` con `action = 'UPDATE'`. Ninguna adaptación: el registro es genérico por diseño |
| `RF-SP-014` | **No** recibe nada de este requerimiento, y es la decisión de §6. Su catálogo de dieciséis códigos no gana ninguno |
| **`RF-SP-023`** | Hereda de aquí la forma entera: subrecurso `/status`, estado destino booleano, idempotencia en el dominio, auditoría solo en cambios y sin motivo. Lo que **no** hereda es la ausencia de `409`: la moneda por defecto no puede desactivarse |
| Módulos futuros que referencien países | Un país inactivo **sigue siendo referenciable y sigue resolviéndose** (`CA-SP-181`). La obligación que `RF-SP-020` §8 y `RF-SP-021` §8 ya declararon se confirma aquí: se filtra por `is_active` al **ofrecer** el catálogo, nunca al **resolver** un dato ya guardado. Filtrar al resolver dejaría registros sin país |
| `requirements/sp.md` | Ninguna enmienda. `RN-SP-009` ya recoge desde el 21-08-2026 que el estado es la única modificación admitida, resultado de la pregunta 1 de `RF-SP-020` §14 |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Un `PATCH /api/v1/countries/{id}` que acepte el campo `isActive` | `RN-SP-009` prohíbe editar el país, y un endpoint de edición que hoy solo acepta un campo es una puerta abierta a que alguien añada el segundo. El subrecurso hace que la prohibición sea estructural y no una convención |
| `DELETE /api/v1/countries/{id}` con semántica de baja lógica | Diría que el país se elimina, que es exactamente lo que `RN-SP-009` prohíbe y lo que `spec.md` §2 se molesta en aclarar que **no** ocurre. Además obligaría a un método distinto para reactivar, rompiendo la simetría de una operación que es un interruptor |
| Enviar una acción (`activate` / `deactivate`) en lugar del estado destino | Obliga al servidor a conocer el estado actual para saber si la acción aplica, y hace que repetir la petición no sea neutro. El estado destino es idempotente por construcción. Mismo criterio de `RF-SP-007` §9 |
| Un enumerado `status` con `ACTIVO` / `INACTIVO`, como en `RF-SP-007` | La columna es `boolean` y la respuesta ya devuelve `isActive`: pedir el cambio con un nombre y devolverlo con otro obligaría al cliente a traducir. En `roles` el enum corresponde porque `status` es un dominio con nombre propio; aquí no hay tercer estado posible |
| Exigir motivo al desactivar | `spec.md` §14, pregunta 2: el Art. V.13 lo obliga donde el registro desaparece, y aquí el país sigue existiendo y la operación es reversible en una petición. Crearía además un patrón —motivo fuera de una eliminación— que habría que sostener después en `RF-SP-023` y `RF-SP-028` |
| Registrar también el evento en `audit_security_log` | `spec.md` §14, pregunta 1: no hay privilegio en juego, el catálogo de `security.md` §8.1 es cerrado, y llenarlo de eventos de catálogo degradaría el registro que se consulta buscando un incidente |
| Devolver `204` sin cuerpo | El cliente necesita el país tal como quedó para refrescar el selector, y `spec.md` §6.2 lo declara como salida. Un `204` obligaría a una segunda llamada |
| Rechazar la desactivación de un país con datos que lo referencian | `spec.md` §13 lo resuelve al revés y con razón: es el caso para el que existe la operación. El alta equivocada que **ya se usó** es justamente la que más urge retirar de la circulación |
| Advertir cuántos datos referencian al país antes de desactivarlo | `spec.md` §14, pregunta 3: hoy ninguna tabla referencia a `countries` (`modelo-datos.md` §2), de modo que el conteo valdría siempre cero y exigiría invertir una dependencia hacia módulos que no existen. Se replantea con la primera clave foránea entrante (§10) |
| Cargar el país sin bloqueo de fila y confiar en el último en escribir | El resultado en la tabla sería correcto y la auditoría quedaría con dos eventos para un solo cambio, el segundo describiendo una transición que no ocurrió. Corrompe la única fuente de autoría que hay (§7) |
| Bloqueo optimista con columna de versión | Resuelve la carrera devolviendo un conflicto al segundo actor, cuando lo correcto según `FA-001` es que la segunda petición **tenga éxito** sin registrar nada. Añadiría además una columna y un código de error que nadie pidió |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Se implementa sin bloqueo de fila y la auditoría registra dos eventos para un solo cambio | Medio | Es el defecto más fácil de introducir aquí, porque ninguna prueba secuencial lo detecta y el estado final de la tabla es correcto. Prueba de concurrencia propia en §11, con dos transacciones reales |
| Se copia de `RF-SP-007` y se emite además el evento de seguridad, o se invalida una caché inexistente | Medio | Declarado en §6 y en §7. `CA-SP-183` verifica la ausencia del evento de seguridad, que es lo que convierte la decisión en comprobable |
| Alguien añade `PATCH /api/v1/countries/{id}` por parecer lo natural en un CRUD | Medio | La existencia de `/{id}/status` lo hace más probable, no menos. Cubierto por `CA-SP-137` de `RF-SP-020`, que exige `404` sobre `/{id}` y que este requerimiento **no** debe relajar |
| Un módulo futuro filtra por `is_active` al resolver un país ya guardado y lo muestra vacío | Medio | Obligación declarada en §8 y ya anotada en `RF-SP-020` §8 y `RF-SP-021` §8. Este requerimiento la vuelve real: hasta ahora ningún país podía estar inactivo |
| Todos los países quedan inactivos y ningún formulario puede seleccionar país | Bajo | `spec.md` §13 lo admite de forma explícita: es posible, no se impide y es reversible en una petición. El catálogo se puebla manualmente y el caso es visible de inmediato |
| El conteo de referencias sigue sin poder ofrecerse cuando el negocio lo pida | Bajo | Anotado con su **condición de disparo**: se replantea cuando aparezca la primera clave foránea entrante a `countries`. Hasta entonces el dato valdría siempre cero (`spec.md` §14, pregunta 3) |
| Desactivar se confunde con corregir | Bajo | `spec.md` §2 lo aclara y este plan no lo repite en el contrato: el `409` que rechazaría un código o un nombre en el cuerpo es el `400` por campo desconocido de §4, que es más claro |

## 11. Estrategia de prueba

Niveles: **Unitaria** (dominio, sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V16` y `V17` aplicadas) y **API** (extremo a extremo por HTTP, con autenticación).

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-178` | Unitaria + Integración + API | `deactivate()` y `activate()` aplican el estado y devuelven que hubo cambio; el endpoint devuelve `200` con `isActive` actualizado y la fila queda con ese valor |
| `CA-SP-179` | Integración + API | Tras desactivar, `GET /api/v1/countries` **no** devuelve el país, y con `includeInactive=true` sí, con `isActive: false`. Se verifica sobre el endpoint de `RF-SP-021` |
| `CA-SP-180` | Integración | Tras el cambio, `code` y `name` son idénticos byte a byte a los anteriores, y solo `is_active` y `updated_at` cambiaron |
| `CA-SP-181` | Integración | Un país desactivado **sigue existiendo** y se recupera por su identificador con todos sus datos. Hoy no hay tabla que lo referencie, de modo que la prueba lo comprueba directamente sobre la fila; cuando exista la primera clave foránea, esta prueba es la que hay que ampliar |
| `CA-SP-182` | Unitaria + Integración | El dominio devuelve «sin cambio» al aplicar el estado que ya tenía; tras esa petición **no existe ninguna fila nueva** en `audit_change_log`, y `updated_at` tampoco cambió |
| `CA-SP-183` | Integración | Tras un cambio efectivo existe **una** fila en `audit_change_log` con `action = 'UPDATE'` y `changes` conteniendo solo `is_active` con su antes y su después, y **ninguna** fila nueva en `audit_security_log` |
| `CA-SP-338` | API | Un cuerpo con `reason` devuelve `400` por campo desconocido, **no se ignora**. Sin el rechazo, el criterio no comprobaría nada |
| `CA-SP-184` | API | Un actor autenticado sin `countries:update` recibe `403`, el país no cambia y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| **Cambio concurrente del mismo país** | Integración | Dos transacciones reales que desactivan el mismo país a la vez: ambas devuelven `200`, la fila queda inactiva y existe **exactamente un** evento en `audit_change_log`. Sin bloqueo de fila habría dos, y el segundo describiría una transición que no ocurrió |
| Reactivar un país desactivado hace tiempo | API | Se admite sin condiciones y sin ninguna verificación adicional |
| País inexistente | API | `404` con `EX-001`, y **no** se trata como un cambio sin efecto |
| Identificador con formato incorrecto | API | `abc`, `1-1-1-1-1` y un UUID de 35 caracteres devuelven los tres `400` con `VAL-001` y campo `id`, nunca `404` |
| Cuerpo con código o nombre | API | Devuelve `400` por campo desconocido. Es la otra mitad de `CA-SP-180`: el catálogo no se edita ni por descuido |
| Estado ausente, nulo o no booleano | API | Los tres devuelven `400` con `VAL-001` y campo `isActive` |
| Todos los países inactivos | API | El listado por defecto devuelve `200` con `content` vacío, y reactivar uno lo devuelve a la circulación en una petición |
| El `404` no llega a la auditoría de error | Integración | Tras un `404`, no existe fila nueva en `audit_error_log`. Un intento de escribirla sería rechazado por `ck_audit_error_log_status`, y la prueba lo comprueba también por `INSERT` directo |
| Ausencia de edición sobre el recurso | API | `PATCH` y `PUT` sobre `/api/v1/countries/{id}` siguen devolviendo `404` tras existir este endpoint. Es lo que impide que el subrecurso se convierta en la excusa para abrir la edición |
| Número de sentencias por petición | Integración | El `SELECT … FOR UPDATE`, el `UPDATE` y la inserción del evento. Ninguna consulta adicional, y **ninguna** cuando la petición cae en `FA-001` salvo la de carga |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo. No se añade ninguna regla nueva: no introduce dependencias entre módulos.
