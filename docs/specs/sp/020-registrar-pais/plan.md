# PLAN — `RF-SP-020` Registrar país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-020` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendado | 28-08-2026 — `code` pasa a `char(3)` y `ck_countries_code_format` a `^[A-Z]{3}$`, por el cambio a ISO 3166-1 alfa-3. La migración es `V42`, no `V16`: esa ya está aplicada (Art. I.7) |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide tres cosas de las que depende que el catálogo no se llene de duplicados irreparables: **cuándo se normaliza el código respecto de cuándo se verifica su unicidad**, **qué restricciones del esquema sostienen esa unicidad bajo concurrencia** y **qué operaciones de escritura quedan cerradas para siempre sobre este recurso**.

---

## 1. Enfoque

Es un alta de catálogo con dos filas de datos, y sin embargo no es trivial, por una razón que la especificación deja dicha en §2: **`RN-SP-009` no admite edición ni borrado**. Un nombre mal escrito queda para siempre y aparecerá en cada formulario. La validación del alta es casi toda la defensa que hay, y todo este plan se ordena alrededor de eso.

De ahí salen las tres decisiones que lo gobiernan:

1. **La normalización ocurre antes de verificar la unicidad, no después.** `co` y `CO` deben ser el mismo país, y `"Panamá "` y `"Panamá"` el mismo nombre. Si se normaliza después de comprobar, la comprobación mira un valor distinto del que se persiste y la unicidad se burla con un espacio.
2. **La unicidad la garantiza el esquema, no el servicio.** Dos altas simultáneas del mismo código pasan las dos comprobaciones de aplicación; solo un índice único decide el empate. El servicio comprueba para dar un mensaje útil, no para garantizar nada.
3. **Este plan crea el recurso entero.** Es el primero que toca `countries`, de modo que aquí nacen la tabla, la entidad, el repositorio y el controlador del que colgarán `RF-SP-021` y `RF-SP-022`. Lo que este documento deja fuera del controlador, esos dos lo añaden; lo que declara cerrado, lo está para siempre.

El punto 3 tiene una consecuencia que conviene declarar por escrito: **`countries` es la primera tabla del sistema cuya única mutación posible es un `UPDATE` de una columna booleana**, la de `RF-SP-022`. No hay edición, no hay borrado lógico y no hay borrado físico. El esquema debe reflejar eso, y no dejar columnas preparadas «por si acaso» para operaciones que ningún requerimiento contempla.

## 2. Cambios de esquema

**Migración:** `V16__create_countries.sql`

Es la siguiente libre de la serie: `V15__seed_currencies.sql` (`RF-SP-019`) es la última comprometida. Campos tomados de `requirements/sp.md` §10.6, restricciones de §10.7 más las tres que este plan añade.

| Tabla | Cambio | Detalle |
|---|---|---|
| `countries` | Crea | `id uuid PRIMARY KEY`, `code char(3) NOT NULL`, `name varchar(100) COLLATE "es-x-icu" NOT NULL`, `is_active boolean NOT NULL DEFAULT true`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()` |

**`name` declara la intercalación del español en la columna**, añadido el 21-08-2026 al aprobar `RF-SP-021`. Ese requerimiento exige que el catálogo se ordene alfabéticamente según el idioma y no por bytes —con la intercalación `C`, «Panamá» va detrás de «Perú»—, y la API de criterios con la que se construye su consulta **no puede expresar `COLLATE`**. Declararla aquí hace que un `ORDER BY name` corriente ya ordene bien, y que el orden correcto sea el comportamiento por omisión de cualquier consulta futura sobre esta tabla en lugar de algo que cada una deba recordar. Se elige ICU y no una intercalación del sistema operativo porque las de la biblioteca C dependen de qué configuraciones regionales estén instaladas en la imagen del contenedor, y `postgres:17-alpine` es una imagen mínima. No afecta a `uq_countries_name`, que va sobre una expresión con su propia intercalación, ni a la igualdad con la que se comprueba la unicidad.

Restricciones e índices:

| Nombre | Definición | Por qué |
|---|---|---|
| `uq_countries_code` | `UNIQUE (code)` | `requirements/sp.md` §10.7 y `VAL-004`. Restricción **total**, no parcial: no hay borrado lógico, de modo que no existe estado en el que un código deba poder repetirse. Es la diferencia con `uq_roles_code`, que sí es parcial porque un rol eliminado libera el suyo |
| `uq_countries_name` | `CREATE UNIQUE INDEX uq_countries_name ON countries (f_unaccent(lower(name)))` | `VAL-005`. **No está en `requirements/sp.md` §10.7**, que solo declara la de código; se añade y ese documento se enmienda (§8). Sin ella, `EX-001` sería una verificación de aplicación que dos altas simultáneas burlan, y el duplicado resultante sería **permanente**. Se declara sobre la **forma normalizada** y no sobre `name` literal: ver abajo |
| `ck_countries_code_format` | `CHECK (code ~ '^[A-Z]{3}$')` | `VAL-002` y `EX-002`. `char(3)` acota la longitud pero no impide `1`, `-` ni un espacio de relleno; sin el `CHECK`, un `INSERT` directo mete basura en un catálogo que después nadie puede corregir |
| `ck_countries_name_not_blank` | `CHECK (length(btrim(name)) > 0)` | `VAL-003`. Un nombre de un solo espacio pasaría el `NOT NULL` y quedaría para siempre |

### Por qué la unicidad del nombre es sobre la forma normalizada

Es la única restricción de este plan que se aparta de cómo se resolvió lo mismo en `roles`, y conviene dejar escrito por qué.

`uq_roles_name` es `UNIQUE (name)` literal, y `RF-SP-001` §10 aceptó de forma consciente que `Contabilidad` y `contabilidad` pudieran coexistir. Allí el coste es acotado: si el duplicado molesta, `RF-SP-004` permite renombrar uno de los dos.

Aquí no existe esa salida. `RN-SP-009` no admite edición, de modo que `Panamá` y `Panama` conviviendo en el catálogo serían **dos opciones indistinguibles en cada selector, para siempre**, sin forma de fusionarlas ni de corregir ninguna. Desactivar una con `RF-SP-022` retira la opción pero deja los datos que ya la referenciaban apuntando a un país distinto del que apunta el resto.

```sql
CREATE UNIQUE INDEX uq_countries_name ON countries (f_unaccent(lower(name)));
```

`f_unaccent` existe desde `V1__create_shared_functions.sql` (`RF-SP-010`) y está declarada `IMMUTABLE` precisamente para poder indexarse. **Decidirlo ahora no cuesta nada; decidirlo después del primer país registrado obliga a migrar datos**, porque crear el índice sobre una tabla que ya contiene variantes falla.

El coste asumido es que dos países cuyos nombres solo difieran en acentos o mayúsculas no podrán coexistir. En ISO 3166-1 no hay ninguno, y si apareciera, el código de tres letras los distingue.

**Consecuencia sobre el mensaje de `EX-001`:** el `409` por nombre puede dispararse contra una fila cuyo nombre **no es idéntico** al enviado. El mensaje debe decir que ya existe un país con ese nombre e incluir el nombre registrado, o el actor verá rechazado un `"Panama"` que no encuentra en ninguna parte.

**Se declara `updated_at`, que `requirements/sp.md` §10.6 y `modelo-datos.md` §2 omiten.** No es una omisión inofensiva que este plan pueda dejar pasar: el Art. V.7 obliga a que toda tabla de negocio lleve marca de última modificación, y aquí además hay algo que modificar —`RF-SP-022` cambia `is_active`—, de modo que sin la columna no habría forma de saber cuándo se retiró un país de la circulación salvo recorriendo la auditoría. Ambos documentos se enmiendan (§8). La frase de `modelo-datos.md` §2 —«ninguna de las tres lleva `updated_at` completo ni borrado lógico»— era cierta cuando se escribió, antes de que existieran `RF-SP-022` y `RF-SP-023`.

**No se declara `deleted_at`.** `RN-SP-009` hace que un país no se elimine nunca, ni lógica ni físicamente. Añadir la columna «por simetría con `roles`» crearía un camino que ningún requerimiento contempla y que el día que alguien lo use dejará huérfanos los datos que referencien al país.

**No se crea `ix_countries_busqueda`.** `requirements/sp.md` §10.7 lo declara y **pertenece a `RF-SP-021`**, que es quien introduce la búsqueda; así lo anticipa ya el plan de `RF-SP-017` §2. Aquí la tabla nace sin él y el alta no lo necesita: la unicidad la resuelven los índices únicos. Crear el índice de trigramas ahora sería mantener una estructura que ninguna consulta de este requerimiento usa.

**`code` es `char(3)` y no `varchar(3)`**, como declara §10.6. Con `ck_countries_code_format` exigiendo exactamente tres mayúsculas, el relleno con espacios que caracteriza a `char` no puede llegar a producirse, de modo que la diferencia de semántica entre ambos tipos queda sin efecto observable.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2; `domain` no importa Spring ni JPA, y la prueba de ArchUnit de `RF-SP-001` lo verifica.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Country` | Nuevo | Agregado. Código, nombre y estado. **Nace siempre activo**: el constructor no recibe el estado (`CA-SP-171`) |
| `domain` | `CountryCode` | Nuevo | Objeto de valor. **Normaliza a mayúsculas y valida el formato de tres letras en un solo sitio**, sin Spring ni base de datos (Art. VI.3) |
| `domain` | `CountryRepository` | Nuevo | Puerto: `save`, `existsCode`, `existsName`. `RF-SP-021` y `RF-SP-022` le añadirán los suyos |
| `application` | `RegisterCountryService` | Nuevo | Caso de uso. `@Transactional`, orquesta el orden de verificación de §4 y emite la auditoría |
| `application` | `RegisterCountryCommand` | Nuevo | Entrada del caso de uso, sin tipos de HTTP |
| `application` | `CountryChangeAuditor` | Nuevo | Puerto hacia `shared/audit` para los eventos de cambio de este recurso. Lo reutiliza `RF-SP-022` |
| `infrastructure` | `JpaCountryRepository` | Nuevo | Adaptador. Traduce la violación de índice único distinguiendo **cuál** de los dos se violó |
| `infrastructure` | `CountryEntity` | Nuevo | Mapeo JPA |
| `infrastructure` | `CountryJpaMapper` | Nuevo | Conversión entidad ↔ agregado; el agregado no se anota con JPA |
| `api` | `CountryController` | Nuevo | `POST /api/v1/countries`. Declara el permiso, valida el DTO y devuelve `201` **sin `Location`** (§4). `RF-SP-021` y `RF-SP-022` añadirán aquí sus métodos |
| `api` | `RegisterCountryRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-001` a `VAL-003`) |
| `api` | `CountryResponse` | Nuevo | DTO de salida. Lo reutilizan `RF-SP-021` y `RF-SP-022` |

Dos decisiones de reparto:

**`CountryCode` es un objeto de valor y no una anotación de validación en el DTO.** La normalización a mayúsculas y la comprobación de formato tienen que ocurrir en el mismo sitio y en ese orden, y tienen que ocurrir también cuando el código llegue por un camino que no sea el DTO —`RF-SP-021` lo recibe como filtro de búsqueda—. Repartirlo entre una anotación y una llamada a `toUpperCase()` en el servicio es exactamente cómo se acaba comprobando un valor y persistiendo otro.

**`CountryController` es un controlador nuevo.** El recurso es `/api/v1/countries` y de él cuelgan `RF-SP-021` y `RF-SP-022`. Mismo criterio con el que `RF-SP-010` §3 creó `PermissionController` y `RF-SP-016` §3 creó `MembershipController`.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/countries` | Registra un país en el catálogo |

**Petición**

```json
{
  "code": "PA",
  "name": "Panamá"
}
```

- **No existe campo `isActive`.** El país nace activo y el alta no recibe el estado (`CA-SP-171`). El DTO se deserializa con `FAIL_ON_UNKNOWN_PROPERTIES` activo, de modo que enviarlo devuelve `400` y no se ignora en silencio. Es lo mismo que `RF-SP-001` §4 hizo con `status`, y es lo que deja un único camino hacia el estado inactivo —`RF-SP-022`— y un solo lugar donde auditarlo.
- **`code` se normaliza a mayúsculas y se recorta antes de validar el formato y la unicidad.** `"co"`, `" CO"` y `"CO"` son el mismo país. Es la diferencia deliberada con `RF-SP-001` §4, donde el código de un rol en minúsculas se **rechaza** en lugar de normalizarse: allí el actor inventa el código y conviene que vea exactamente cuál quedó; aquí el código no lo inventa nadie, lo fija ISO 3166-1, y rechazar `"co"` sería pedantería sobre un valor que solo puede escribirse de una forma.
- **`name` se recorta de espacios al inicio y al final antes de validar y persistir.** Sin ese recorte, `"Panamá "` y `"Panamá"` serían dos nombres distintos para `uq_countries_name` y la unicidad se burlaría con un espacio. **El interior no se toca**: los nombres compuestos llevan espacios legítimos.
- **`name` admite acentos y caracteres no latinos** sin transformación alguna. El catálogo es internacional y `spec.md` §13 lo exige; la insensibilidad a acentos pertenece a la **búsqueda** de `RF-SP-021`, no al dato almacenado.

**Respuesta `201`**

**Sin cabecera `Location`**, y es una decisión y no un olvido. Se corrigió el 21-08-2026 al aprobar este plan: el borrador devolvía `Location: /api/v1/countries/{id}`, una URL que **no resuelve** —no existe endpoint de detalle de país en ningún requerimiento del módulo, de modo que seguirla devuelve `404` (§4, tabla de métodos cerrados)—. Una cabecera que existe para que el cliente vaya al recurso creado y lo lleva a la nada es peor que no ponerla.

El `id` lo devuelve el cuerpo, que es de donde el cliente lo toma de todos modos, y el catálogo se consulta entero con `RF-SP-021`. Es la asimetría con `RF-SP-001` y `RF-SP-016`, que sí devuelven `Location` porque `RF-SP-003` y `RF-SP-018` publican el detalle correspondiente. Si algún día existiera `GET /api/v1/countries/{id}`, añadir la cabecera es aditivo y no rompe a ningún cliente.

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d40",
  "code": "PA",
  "name": "Panamá",
  "isActive": true,
  "createdAt": "2026-08-21T14:32:11Z",
  "updatedAt": "2026-08-21T14:32:11Z"
}
```

- **Se devuelve el código ya normalizado**, que es la única forma de que el actor vea qué quedó registrado cuando escribió otra cosa.
- **`isActive` va en la respuesta aunque no vaya en la petición.** `spec.md` §6.2 lo pide —«con su código, su nombre y su estado»— y `CA-SP-171` se verifica leyéndolo.
- **No existe `createdBy`** ni equivalente: el actor no vive en la tabla de negocio (Art. V.7). Quién registró el país se responde con `RF-SP-011`.

**Métodos que este recurso no expone, y no los expondrá nunca**

| Método y ruta | Respuesta | Por qué |
|---|---|---|
| `PUT` / `PATCH` / `DELETE` `/api/v1/countries/{id}` | **`404`** | `RN-SP-009`: el catálogo no se edita ni se elimina. Ver abajo por qué `404` y no `405` |
| `PUT` / `PATCH` / `DELETE` `/api/v1/countries` | `405` | La colección **sí** está mapeada, por el `POST` de este requerimiento y el `GET` de `RF-SP-021` |
| `PATCH` `/api/v1/countries/{id}/status` | — | **Es la excepción**, y la única: la introduce `RF-SP-022` |

**Por qué `404` y no `405` sobre `/{id}`, corregido el 21-08-2026 al aprobar este plan.** El borrador exigía `405` en las tres, y eso no puede ocurrir: `405 Method Not Allowed` presupone que la ruta está mapeada para **algún** método, y `/api/v1/countries/{id}` no lo está en ningún requerimiento del módulo —no existe endpoint de detalle de país—. Spring no encuentra manejador para la ruta y responde `404`. La prueba de `CA-SP-137` habría fallado no por un defecto del código sino por afirmar un estado que el contrato nunca produce.

La distinción no es una sutileza: es la diferencia entre «este recurso no admite ese método» y «esa ruta no existe», y un criterio de aceptación que verifica lo que la API **no** expone tiene que afirmar exactamente lo que un cliente recibe.

`CA-SP-137` no tiene código que lo implemente: se cumple porque esos métodos no existen. El subrecurso `/status` es lo que hace que la verificación tenga que ser precisa: no basta con comprobar que `PATCH` sobre el recurso falla, hay que comprobar que falla sobre `{id}` —con `404`— y funciona sobre `{id}/status` cuando llegue `RF-SP-022`.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Código o nombre ausentes, o nombre en blanco (`VAL-001`, `VAL-003`) | `VAL-001`, `VAL-003` |
| `400` | El código no tiene el formato de tres letras (`VAL-002`, `EX-002`) | `VAL-002` |
| `400` | Nombre por encima de 100 caracteres | `VAL-003` |
| `400` | Cuerpo con un campo desconocido, incluido `isActive` | `VAL-001` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `countries:create` | `AUTH-002` |
| `409` | Código o nombre ya en uso (`EX-001`) | `EX-001` |
| `500` | Fallo no controlado | `ERR-500` |

El formato de error es el de `architecture.md` §7.3.

**El `409` debe decir cuál de los dos está duplicado.** `EX-001` lo exige de forma explícita, y no es un adorno: al no existir corrección posible, quien recibe el error necesita saber si el problema es que el país ya está registrado —caso en el que no hay nada que hacer— o que otro país distinto tomó ese nombre. El adaptador distingue por el nombre de la restricción violada, `uq_countries_code` o `uq_countries_name`, y no por una comprobación previa: bajo concurrencia, la comprobación previa puede decir que ambos están libres y el índice rechazar de todos modos.

**En el caso del nombre, el mensaje incluye además el nombre ya registrado.** Al ser `uq_countries_name` un índice sobre la forma normalizada (§2), el rechazo puede venir de una fila cuyo nombre no es idéntico al enviado: quien escribe `"Panama"` choca con `"Panamá"`. Sin el nombre registrado en el mensaje, el error resulta incomprensible.

**Orden de verificación.** Determina qué error recibe una petición que incumple varias cosas a la vez:

1. Formato y obligatoriedad (`VAL-001` a `VAL-003`). Se evalúan **todas** y se devuelven juntas en `errors`.
2. Normalización del código y recorte del nombre.
3. Unicidad de código y nombre (`EX-001`), primero por consulta —para el mensaje— y en última instancia por el índice.

La normalización va **entre** las dos, y ese es el punto entero del orden: validar el formato antes de normalizar rechazaría `"co"`, y comprobar la unicidad antes de normalizar la comprobaría sobre un valor que no es el que se va a guardar.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/countries` | `countries:create` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`), que sembró el bloque completo de `countries:` aunque ningún endpoint lo declarara todavía.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **`countries:create` no se comparte con `countries:read`.** Consultar el catálogo lo necesita cualquiera que rellene un formulario; registrar un país es irreversible.
- **No hay techo de privilegios que verificar.** No existe aquí nada análogo a `RN-SEG-010`: un país no concede permisos, de modo que los permisos efectivos del actor no intervienen y la resolución del permiso **sí** puede usar la caché de `security.md` §4.5.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6). `CA-SP-139` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Alta del país | `audit_change_log` | `module = 'SP'`, `entity = 'countries'`, `entity_id` del nuevo, `action = 'CREATE'`, `changes` con el estado inicial completo: `code` **ya normalizado**, `name` recortado e `is_active` |
| Rechazo por `EX-001` | `audit_error_log` | `resource = 'countries'`, `operation = 'POST /api/v1/countries'`, `error_code = 'EX-001'`, `error_type = 'BUSINESS_RULE'`, `http_status = 409`, `severity = 'MEDIA'` y `message` saneado |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_security_log` | **No aplica al alta.** Ver abajo |
| — | `audit_deletion_log` | No aplica: este requerimiento no elimina nada, y `RN-SP-009` hace que nada lo elimine nunca |

Tres decisiones:

- **El alta no emite evento de seguridad.** El catálogo de `security.md` §8.1 es cerrado y no incluye los países: registrar uno no cambia ningún privilegio ni interviene en la resolución de permisos de §4.5. Es la misma asimetría que `RF-SP-016` §6 declaró para las membresías, y el criterio que `RF-SP-022` confirmó al aprobarse (`spec.md` §14 de ese requerimiento, resolución 1).
- **`changes` guarda el valor normalizado, no el enviado.** La auditoría debe reflejar lo que quedó en la tabla. Si alguien escribió `"co"` y se registró `"CO"`, el evento dice `"CO"`; la forma exacta en que llegó la petición es asunto del `request_log` (Art. XV.3).
- **Las validaciones de formato (`400`) no se auditan** (`architecture.md` §6.6.4): son ruido de formulario, y `ck_audit_error_log_status` (`RF-SP-013`) rechazaría la fila en el esquema.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| `INSERT` en `countries` y su evento en `audit_change_log` | **La misma** (Art. V.14). Si el alta se revierte, su evento también |
| `audit_error_log` de un rechazo o un fallo | **Independiente**, `REQUIRES_NEW` |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`@Transactional` vive sobre `RegisterCountryService`, en `application`; nunca en el controlador ni en el repositorio.

**No hay evento posterior al commit**, a diferencia de `RF-SP-001` §7: allí el evento de seguridad se enganchaba al commit para no producir eventos fantasma, y aquí no hay evento de seguridad.

**La violación de índice único salta dentro del caso de uso**, en el `flush` del `INSERT`, no en el `COMMIT`. Las restricciones son inmediatas y no diferidas —no hay ninguna operación multifila que lo exija, a diferencia de `RF-SP-016`—, de modo que el adaptador puede capturarla y traducirla, y `GlobalExceptionHandler` no necesita cambios.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `requirements/sp.md` | **§10.6 gana `updated_at`**, que no declaraba, por el Art. V.7 y porque `RF-SP-022` modifica la fila. **§10.7 gana `uq_countries_name` —declarada sobre `f_unaccent(lower(name))`, no sobre `name` literal—**, `ck_countries_code_format` y `ck_countries_name_not_blank`, que tampoco declaraba. Enmiendas de este plan (Art. I.7) |
| `RF-SP-010` | `V16` **depende de `V1__create_shared_functions.sql`**, que es donde vive `f_unaccent`. Es la segunda vez que esa función sostiene algo fuera de una búsqueda, y la primera en que sostiene una **restricción de integridad**: el argumento de `RF-SP-010` §2 para adelantarla a `V1` se refuerza |
| `modelo-datos.md` | §2 dice que ninguno de los tres catálogos lleva `updated_at`. Deja de ser cierto para `countries`, y el diagrama y la frase se corrigen. La afirmación sigue valiendo para `memberships`, que no tiene ninguna mutación posible |
| `RF-SP-021` | Cuelga de `CountryController` y reutiliza `CountryResponse`. Es quien crea `ix_countries_busqueda`, que este plan deliberadamente no crea, y quien decide el filtro por defecto sobre `is_active` |
| `RF-SP-022` | Cuelga de `CountryController`, reutiliza `CountryResponse` y `CountryChangeAuditor`, y es **la única operación de escritura que este recurso admitirá además del alta**. Su `PATCH /{id}/status` es la excepción a la tabla de métodos cerrados de §4 |
| `RF-SP-011` | Su consulta responde ahora también por la entidad `countries`. Ninguna adaptación: el registro es genérico por diseño |
| **Módulos futuros que ubiquen personas u operaciones** | Hoy **ninguna tabla referencia a `countries`** (`modelo-datos.md` §2). El primero que lo haga debe declarar su clave foránea `ON DELETE RESTRICT`, coherente con que un país no se elimine, y debe saber que **un país puede estar inactivo y seguir siendo referenciado**: desactivar no invalida los datos existentes (`CA-SP-181` de `RF-SP-022`). Filtrar por `is_active` al **ofrecer** el catálogo es correcto; filtrarlo al **resolver** un dato ya guardado dejaría registros sin país |
| `shared/audit` | Sin cambios estructurales. Gana un cliente más |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Rechazar el código en minúsculas, como `RF-SP-001` hace con los roles | Allí el actor inventa el código y conviene que vea exactamente cuál quedó. Aquí lo fija ISO 3166-1 y solo puede escribirse de una forma: rechazar `"co"` sería pedantería sobre un valor sin ambigüedad. `spec.md` §13 ya resolvió que se normaliza |
| Normalizar después de comprobar la unicidad | Comprobaría un valor y persistiría otro. Es el defecto que llena de duplicados un catálogo que no se puede corregir |
| Confiar la unicidad solo a la comprobación del servicio | Dos altas simultáneas la pasan las dos. Solo un índice único decide el empate, y sin él el duplicado sería permanente |
| Confiar la unicidad solo al índice, sin comprobación previa | Funcionaría, pero el mensaje saldría de traducir una violación de integridad en todos los casos, incluido el más común, que es teclear un país ya registrado. La comprobación previa existe para el mensaje, no para la garantía |
| No declarar `uq_countries_name`, como se lee `requirements/sp.md` §10.7 | Dejaría `EX-001` sin respaldo en el esquema para la mitad del nombre. Y el duplicado de nombre es peor que el de código: dos filas indistinguibles en cada selector, sin forma de saber cuál usar |
| `uq_countries_name` como `UNIQUE (name)` literal, igual que `uq_roles_name` | Es la forma correcta **allí**, donde `RF-SP-004` permite renombrar y el duplicado tiene salida. Aquí `RN-SP-009` no admite edición, de modo que `Panamá` y `Panama` conviviendo serían dos opciones indistinguibles para siempre. Resuelto el 21-08-2026 al aprobar este plan: la unicidad va sobre `f_unaccent(lower(name))` |
| Declarar la unicidad normalizada más adelante, si el problema aparece | Crear el índice sobre una tabla que ya contiene variantes falla, y resolverlo obliga a migrar datos y a decidir cuál de las dos filas sobrevive sin poder editar ninguna. Es la decisión que hay que tomar antes del primer país registrado o no tomarla nunca |
| No declarar `ck_countries_code_format` y confiar en `char(3)` | `char(3)` admite `1`, `-` y un espacio de relleno. Un `INSERT` directo —una migración, una corrección manual— metería basura permanente |
| Añadir `deleted_at` por simetría con `roles` | Crea un camino que `RN-SP-009` prohíbe y que el día que alguien use dejará huérfanos los datos que referencien al país. `RF-SP-022` es la salida prevista, y no borra |
| No declarar `updated_at` porque `requirements/sp.md` §10.6 no lo hace | Incumple el Art. V.7 y deja `RF-SP-022` sin marca de cuándo se retiró un país. La omisión era coherente cuando se escribió, antes de que existiera el cambio de estado |
| Sembrar el catálogo internacional completo por migración | `spec.md` §14, pregunta 2, lo resolvió: llenaría cada selector de opciones a las que no se opera. Los países se dan de alta de uno en uno |
| Crear `ix_countries_busqueda` en esta migración | Ninguna consulta de este requerimiento lo usa. Pertenece a `RF-SP-021`, y así lo anticipa el plan de `RF-SP-017` §2 |
| Aceptar `isActive` en la petición | Permitiría registrar un país ya desactivado, un segundo camino hacia el estado inactivo y un segundo lugar donde auditarlo. `spec.md` §14, pregunta 1, dejó `RF-SP-022` como el único |
| Devolver `200` en lugar de `201` sin `Location` | El recurso se crea y tiene identificador propio. `RF-SP-016` §4 fijó el mismo contrato para el alta de membresía |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Un nombre mal escrito queda para siempre (`RN-SP-009`) | **Alto** | Es la consecuencia asumida que `spec.md` §2 declara. Toda la defensa está en el alta: las validaciones se evalúan todas juntas, el recorte y la normalización ocurren **antes** de comprobar la unicidad, y `RF-SP-022` permite al menos retirarlo de la circulación. No lo repara |
| Dos países cuyos nombres solo difieran en acentos o mayúsculas no podrán coexistir | Bajo | **Coste asumido** de declarar `uq_countries_name` sobre `f_unaccent(lower(name))`, resuelto el 21-08-2026. En ISO 3166-1 no hay ningún par así, y si apareciera, el código de tres letras los distingue. Se prefiere a la alternativa, que era un duplicado permanente e incorregible |
| `f_unaccent` se declara `IMMUTABLE` y la base de datos no lo verifica: redefinir el diccionario `unaccent` deja `uq_countries_name` con valores calculados con el anterior | Medio | Es el mismo riesgo que `RF-SP-010` §10 ya registró para `ix_roles_busqueda`, y la consecuencia operativa es la misma: tocar el diccionario obliga a `REINDEX` de todo lo que dependa de la función. Aquí es más grave que en un índice de búsqueda, porque el índice **garantiza una unicidad**: un `REINDEX` omitido podría dejar entrar un duplicado que después no se puede corregir |
| Dos altas simultáneas del mismo código producen un `500` en vez de un `409` | Medio | El adaptador traduce la violación por nombre de restricción, y §11 lo prueba con dos altas concurrentes. `CA-SP-136` solo comprueba el caso secuencial |
| El `409` no distingue si el duplicado es de código o de nombre | Medio | `EX-001` lo exige. La distinción sale del nombre de la restricción violada, no de la comprobación previa, que bajo concurrencia puede decir que ambos están libres |
| Se implementa un `PATCH /countries/{id}` por parecer lo natural en un CRUD | Medio | Declarado en la tabla de métodos cerrados de §4 y probado en §11 por lo que la API **no** expone. La existencia de `/{id}/status` lo hace más probable, no menos |
| Un módulo futuro filtra por `is_active` al resolver un país ya guardado | Medio | Declarado en §8 como obligación para quien introduzca la primera clave foránea. Filtrar al ofrecer es correcto; filtrar al resolver deja registros sin país |
| El catálogo arranca vacío y ningún formulario puede seleccionar país | Bajo | Es el estado real de diseño (`spec.md` §14, pregunta 2, y `CA-SP-142` de `RF-SP-021`). Se resuelve dando de alta los países con los que se opera, que son pocos |

## 11. Estrategia de prueba

Niveles: **Unitaria** (dominio, sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V16` aplicada) y **API** (extremo a extremo por HTTP, con autenticación).

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-134` | Integración + API | El alta con código y nombre válidos devuelve `201` con el país en el cuerpo y **sin cabecera `Location`**, y la fila queda con el código normalizado |
| `CA-SP-135` | Unitaria + API | `CountryCode` rechaza `"C"`, `"COL"`, `"C1"`, `"--"` y la cadena vacía; el endpoint devuelve `400` con `VAL-002` |
| `CA-SP-136` | Integración + API | Los índices únicos rechazan el duplicado en base de datos —el de nombre, también en sus variantes de acento y caja—; el endpoint devuelve `409` con `EX-001` **e indica si el duplicado es de código o de nombre** |
| `CA-SP-137` | API | `PUT`, `PATCH` y `DELETE` sobre `/api/v1/countries/{id}` devuelven **`404`**, porque esa ruta no está mapeada para ningún método; sobre la colección `/api/v1/countries`, que sí lo está, devuelven `405`. En ningún caso se escribe fila alguna. Es la única forma de verificar `RN-SP-009`, que no tiene código que la implemente |
| `CA-SP-171` | Integración + API | La fila queda con `is_active = true` sin que la petición lo indique, y la respuesta lo devuelve |
| `CA-SP-138` | Integración + API | Una fila en `audit_change_log` con `action = 'CREATE'` y `changes` conteniendo el código **normalizado** |
| `CA-SP-139` | API | Un actor autenticado sin `countries:create` recibe `403`, no se crea nada y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Código en minúsculas | Unitaria + Integración + API | `"co"` se registra como `"CO"`, la respuesta devuelve `"CO"`, y un alta posterior con `"CO"` devuelve `409`. Es la prueba que verifica que se normaliza **antes** de comprobar |
| Nombre con espacios sobrantes | API | `"Panamá "` y `"Panamá"` son el mismo nombre para la unicidad: el segundo alta devuelve `409` |
| Nombre solo con espacios | API + Integración | Devuelve `400` con `VAL-003`, y `ck_countries_name_not_blank` lo rechaza también por `INSERT` directo |
| Nombre con acentos y caracteres no latinos | Integración | Se persiste y se devuelve **sin transformación**. La insensibilidad a acentos es de la búsqueda de `RF-SP-021`, no del dato |
| Alta concurrente del mismo código | Integración | Dos altas simultáneas con el mismo código: una `201`, la otra `409` con `EX-001`. **Nunca `500`** |
| Alta concurrente del mismo nombre | Integración | Ídem sobre `uq_countries_name`, y el mensaje debe señalar el nombre y no el código |
| Nombre que solo difiere en acentos o mayúsculas | Integración + API | Registrado `"Panamá"`, un alta de `"Panama"`, `"panamá"` y `"PANAMA"` devuelve `409` en los tres casos, **y el mensaje incluye el nombre registrado**. Es la prueba que verifica que la unicidad va sobre la forma normalizada y que el error es comprensible |
| El índice normalizado sobre datos preexistentes | Integración | Con `"Panamá"` y `"Panama"` insertados directamente antes de crear el índice, `CREATE UNIQUE INDEX` falla. Documenta por qué la decisión no era aplazable |
| Rechazo de `isActive` en la petición | API | Un cuerpo con `isActive` devuelve `400` por campo desconocido, no se ignora. Es lo que hace verificable que solo `RF-SP-022` cambia el estado |
| Nombre en el límite | Integración | 100 caracteres se aceptan; 101 devuelven `400` |
| `INSERT` directo con código inválido | Integración | `ck_countries_code_format` rechaza `'1A'`, `'c'` y `'CO '`. La defensa no depende de que la petición pase por el DTO |
| Ausencia de edición y eliminación en el esquema | Integración | La tabla no tiene `deleted_at`, y `updated_at` existe y arranca igual a `created_at` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo.
