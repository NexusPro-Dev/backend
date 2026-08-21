# PLAN — `RF-SP-016` Registrar membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-016` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, excepciones, validaciones y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide tres cosas que la especificación deliberadamente no toca y de las que depende que la cadena no se rompa: **qué significa exactamente el número que va en `level`**, **qué restricciones del esquema garantizan que la cadena siga siendo lineal** y **cómo se resuelve una inserción concurrente sobre el mismo punto**.

---

## 1. Enfoque

Es el primer requerimiento de escritura del módulo desde `RF-SP-001`, y el primero cuya operación **modifica filas que el actor no mencionó**: insertar una membresía en medio reencadena a su hija y desplaza los niveles de todas las que quedan por debajo.

Eso tiene tres consecuencias que gobiernan el plan entero:

1. **La cadena es un invariante, no una convención.** Que cada membresía tenga como mucho una hija (`CA-SP-114`) y que exista una sola membresía superior son propiedades declarables en el esquema, y se declaran ahí (§2). El dominio las verifica también, pero no es el dominio quien las garantiza bajo concurrencia.
2. **`level` es un dato derivado.** Duplica la información que ya está en `parent_membership_id`, y existe solo para poder ordenar y comparar sin recorrer la cadena (`requirements/sp.md` §10.4). Toda la operación se diseña para que ambos no puedan divergir: se escriben en la misma transacción y su coherencia se prueba (§11).
3. **El reordenamiento es una escritura masiva sobre una restricción única.** Desplazar los niveles un lugar viola transitoriamente la unicidad de `level`, y reencadenar viola transitoriamente la unicidad de la hija. Ambas restricciones se declaran **diferidas** (§2), que es lo que permite tenerlas y a la vez poder reordenar.

El caso de uso vive en `application` y orquesta: verificación de unicidad, resolución de la hija indicada, construcción del agregado en `domain` —donde viven `RN-SP-006` y `RN-SP-007`, verificables sin Spring ni base de datos—, persistencia, recálculo de niveles y emisión de **un evento de auditoría por cada membresía tocada**, todos en la misma transacción y bajo el mismo identificador de correlación.

## 2. Cambios de esquema

**Migración:** `V13__create_memberships.sql`

Campos tomados de `requirements/sp.md` §10.4, restricciones de §10.7 más las cuatro que este plan añade.

| Tabla | Cambio | Detalle |
|---|---|---|
| `memberships` | Crea | `id uuid PRIMARY KEY`, `code varchar(50) NOT NULL`, `name varchar(100) NOT NULL`, `description text NULL`, `parent_membership_id uuid NULL`, `level smallint NOT NULL`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()` |

Restricciones e índices:

| Nombre | Definición | Por qué |
|---|---|---|
| `uq_memberships_code` | `UNIQUE (code)` | `requirements/sp.md` §10.7 y `VAL-003`. Restricción **total**, no parcial: no hay borrado lógico, de modo que no existe estado en el que un código deba poder repetirse |
| `uq_memberships_name` | `CREATE UNIQUE INDEX … ON memberships (f_unaccent(lower(name)))` | `VAL-004`. **No está en `requirements/sp.md` §10.7**, que solo declara la de código; se añade y ese documento se enmienda (§8). Sin ella, `EX-001` sería una verificación de aplicación que dos altas simultáneas burlan. Va sobre la forma normalizada, no sobre el texto: `RN-SP-008` hace la membresía inmutable, de modo que `Plata` y `plata` convivirían para siempre. Es un índice único funcional y no una restricción de tabla, porque una restricción no admite expresión; `f_unaccent` existe desde `V1` (`RF-SP-010`) |
| `ck_memberships_code_format` | `CHECK (code ~ '^[A-Z][A-Z0-9_]*$')` | `VAL-006`, añadida a `spec.md` §11 el 21-08-2026 (Art. I.7). Es el mismo formato ya aprobado para `roles`, y va en el esquema por la misma razón que allí: la garantía vale también para las migraciones de poblado y para cualquier punto de entrada futuro |
| `fk_memberships_parent` | `FOREIGN KEY (parent_membership_id) REFERENCES memberships(id) ON DELETE RESTRICT` | `RN-SP-006`. `RESTRICT` y no `CASCADE`: `RN-SP-008` prohíbe eliminar, y una eliminación física accidental debe fallar en lugar de vaciar la cadena entera. `RF-SP-012` §2 verifica por prueba que ninguna clave foránea del esquema declara cascada |
| `uq_memberships_parent` | `UNIQUE NULLS NOT DISTINCT (parent_membership_id) DEFERRABLE INITIALLY DEFERRED` | La restricción central de la tabla. Ver abajo |
| `uq_memberships_level` | `UNIQUE (level) DEFERRABLE INITIALLY DEFERRED` | En un orden lineal cada posición es única. Sin ella, un recálculo defectuoso deja dos membresías en el mismo nivel y el listado de `RF-SP-017` devuelve un orden arbitrario entre ambas |
| `ck_memberships_level_positive` | `CHECK (level >= 1)` | El nivel `1` es la cima (§ *La numeración de `level`*). Un cero o un negativo solo pueden venir de un recálculo defectuoso |
| `ck_memberships_parent_not_self` | `CHECK (parent_membership_id IS NULL OR parent_membership_id <> id)` | Cadena de longitud uno. Cuesta una línea y no depende de que el dominio esté bien |
| `ck_memberships_description_length` | `CHECK (description IS NULL OR length(description) <= 500)` | Mismo límite y mismo motivo que en `roles` y en `permissions`: la columna es `text` y `RF-SP-017` devuelve la cadena entera sin paginar, de modo que sin cota el tamaño de la respuesta es impredecible |

**No se declara `deleted_at`** (`requirements/sp.md` §10.4 no lo declara): `RN-SP-008` hace que una membresía no se elimine nunca. **Sí se declaran `created_at` y `updated_at`** por Art. V.7, y `updated_at` no es decorativo aquí: cambia cada vez que el reordenamiento toca la fila, que es lo único que puede cambiar en ella.

**El formato del código y la normalización del nombre se decidieron al aprobar este plan**, el 21-08-2026. El borrador no declaraba ninguno de los dos, con el argumento de que `spec.md` §11 no los exigía y de que el esquema no debe inventar reglas. El argumento era correcto en la forma y equivocado en el fondo: `RN-SP-008` hace que una membresía **no se edite ni se elimine nunca**, de modo que un código en minúsculas o un `Plata` junto a un `plata` no tienen corrección posible por la API. La ventana para decidirlo es antes de la primera membresía; después exige migrar datos. La especificación volvió a su compuerta y ganó `VAL-006` y la precisión de `VAL-004` (Art. I.7), y el esquema las declara arriba.

Lo que sigue **sin** declararse es un `CHECK` sobre la longitud del código y del nombre más allá del `varchar`: esos límites ya los impone el tipo de la columna.

### La restricción que impide que la cadena se bifurque

```sql
CONSTRAINT uq_memberships_parent UNIQUE NULLS NOT DISTINCT (parent_membership_id)
    DEFERRABLE INITIALLY DEFERRED
```

`requirements/sp.md` §10.7 declara `uq_memberships_parent` sobre `memberships(parent_membership_id)` y explica bien por qué: «que dos membresías no puedan declarar la misma superior se garantiza en el esquema, no solo en el dominio». **Escrita como una restricción única corriente, no garantiza lo que ese documento dice.** PostgreSQL trata los nulos como distintos entre sí, de modo que una `UNIQUE (parent_membership_id)` admite tantas filas con `parent_membership_id IS NULL` como se quiera: es decir, admite **varias membresías superiores**, que es justo la bifurcación que se quería impedir, y en el peor sitio.

`NULLS NOT DISTINCT` —disponible desde PostgreSQL 15, y la línea vigente es la 17 (`architecture.md` §3)— cierra el hueco con una sola cláusula: a lo sumo una fila puede tener `parent_membership_id` nulo, es decir, **existe como mucho una membresía superior**, y a lo sumo una fila puede apuntar a cada membresía, es decir, **cada una tiene como mucho una hija**. Las dos mitades de `CA-SP-114` en una restricción.

Es la diferencia con `roles`, donde `RF-SP-001` §2 necesitó dos objetos —`fk_roles_parent` más el índice único parcial `uq_roles_single_root`— porque allí la unicidad es condicional al borrado lógico y una restricción de tabla no admite un `WHERE`. Aquí no hay borrado lógico, así que la forma simple es también la correcta.

**Por qué es diferida.** Insertar en medio deja transitoriamente dos filas apuntando a la misma superior: la nueva membresía toma como hija a `X`, y hasta que la sentencia que actualiza a `X` se ejecute, `X` sigue apuntando a su superior anterior mientras la nueva ya apunta también a ella. Con la restricción inmediata, el orden de las sentencias tendría que ser exacto y el caso de insertar por encima de la superior no tendría orden válido alguno. `DEFERRABLE INITIALLY DEFERRED` la evalúa al confirmar la transacción, que es cuando la cadena tiene que ser correcta, y no en cada paso intermedio.

**Lo que la diferencia cuesta**, y hay que asumirlo: la violación se detecta **en el `COMMIT`**, fuera del bloque donde el adaptador podría capturarla. La consecuencia para el código está en §3 y §8; la de negocio, en §4.

### La numeración de `level`

`requirements/sp.md` §10.4 dice que `level` «materializa el orden para poder consultarlo y ordenarlo sin recorrer la cadena», pero no dice hacia dónde crece. Este plan lo fija, porque de ello depende cuántas filas toca cada alta:

> **`level` es la distancia hasta la cima. La membresía superior tiene `level = 1` y el número crece hacia abajo.**

Y con ello, una precisión de vocabulario que conviene dejar escrita porque las reglas usan la palabra en el otro sentido: en `RN-SP-006` —«toda membresía está sujeta a una de **mayor nivel**»— *mayor* es jerárquico, no numérico. La superior de una membresía tiene siempre un `level` **menor**. `requirements/sp.md` §10.4 se enmienda para recogerlo (§8); sin esa frase, cualquier lectura posterior del documento invierte la comparación.

Se elige así por lo que ocurre en el alta más común:

| Caso de `spec.md` | Con `1` = cima | Con `1` = extremo inferior |
|---|---|---|
| `FA-001`, primera membresía | `level = 1`. Ninguna otra fila | `level = 1`. Ninguna otra fila |
| `FA-002`, extremo inferior | `level = max + 1`. **Ninguna otra fila** | Toda la cadena se renumera |
| Inserción en medio | Se desplazan las de nivel ≥ el suyo | Se desplazan las de nivel ≤ el suyo |
| Por encima de la superior | `level = 1` y se desplaza todo | `level = max + 1`. Ninguna otra fila |

`FA-002` dice literalmente «no hay reordenamiento: nada queda por debajo de ella», y solo la primera columna lo cumple también para los niveles. La numeración inversa obligaría a reescribir la cadena entera en el alta que la especificación describe como la que no toca nada.

**Ninguna de las dos numeraciones hace estable el número frente a una inserción intermedia**, y esa es la consecuencia importante para los demás módulos: si academia o productos guardaran «este contenido exige nivel 3», ese 3 cambiaría de significado la primera vez que se intercalara una membresía. Por eso §8 declara la obligación: **un módulo que exija un nivel mínimo referencia la membresía por su `id`, y compara por `level` en el momento de evaluar**. Es la misma razón por la que la cadena es única y global (`spec.md` §14, pregunta 4).

### Cómo queda cada alta

Con `H` la hija indicada y `n = H.level`:

```sql
-- FA-002, sin hija: no toca ninguna otra fila
INSERT INTO memberships (id, code, name, description, parent_membership_id, level)
SELECT :id, :code, :name, :description, m.id, m.level + 1
  FROM memberships m
 WHERE m.parent_membership_id IS NULL OR TRUE  -- la de mayor level, ver §4
 ORDER BY m.level DESC LIMIT 1;

-- Inserción por encima de H
UPDATE memberships SET level = level + 1, updated_at = now() WHERE level >= :n;
INSERT INTO memberships (…, parent_membership_id, level) VALUES (…, :padreDeH, :n);
UPDATE memberships SET parent_membership_id = :id, updated_at = now() WHERE id = :hija;
```

El `UPDATE` masivo de niveles es exactamente el que exige la restricción diferida: PostgreSQL verifica la unicidad de `level` fila a fila dentro de una misma sentencia, de modo que `SET level = level + 1` colisiona consigo mismo aunque el estado final sea correcto. No hay orden de ejecución que lo evite, y por eso la solución no es ordenar las sentencias sino diferir la comprobación.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2; `domain` no importa Spring ni JPA, y la prueba de ArchUnit de `RF-SP-001` lo verifica.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Membership` | Nuevo | Agregado. Código, nombre, descripción, superior y nivel |
| `domain` | `MembershipChain` | Nuevo | **El invariante lineal.** Recibe la cadena vigente y la hija indicada, y devuelve la posición de la nueva y los niveles recalculados. Aquí viven `RN-SP-006` y `RN-SP-007`, verificables sin Spring ni base de datos (Art. VI.3) |
| `domain` | `MembershipRepository` | Nuevo | Puerto: `save`, `findById`, `existsCode`, `existsName`, `loadChainForUpdate` |
| `application` | `RegisterMembershipService` | Nuevo | Caso de uso. `@Transactional`, orquesta el orden de verificación de §4 y emite la auditoría |
| `application` | `RegisterMembershipCommand` | Nuevo | Entrada del caso de uso, sin tipos de HTTP |
| `application` | `MembershipChangeAuditor` | Nuevo | Puerto hacia `shared/audit` para los eventos de cambio de esta operación |
| `infrastructure` | `JpaMembershipRepository` | Nuevo | Adaptador. Bloqueo de la cadena, `UPDATE` masivo de niveles y traducción de la violación de índice único |
| `infrastructure` | `MembershipEntity` | Nuevo | Mapeo JPA. La relación al padre, `LAZY` |
| `infrastructure` | `MembershipJpaMapper` | Nuevo | Conversión entidad ↔ agregado; el agregado no se anota con JPA |
| `api` | `MembershipController` | Nuevo | `POST /api/v1/memberships`. Declara el permiso, valida el DTO y devuelve `201` con `Location`. `RF-SP-017` y `RF-SP-018` añadirán aquí sus métodos |
| `api` | `RegisterMembershipRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-001`, `VAL-002`) |
| `api` | `MembershipResponse` | Nuevo | DTO de salida. Lo reutilizan `RF-SP-017` y `RF-SP-018` |
| `shared/error` | `GlobalExceptionHandler` | **Modificado** | Debe traducir la violación de restricción **diferida**, que salta al confirmar y no dentro del caso de uso (§8) |

Tres decisiones de reparto:

**`MembershipChain` es un objeto de dominio, no lógica dentro del servicio.** `RN-SP-007` —dónde queda la nueva y qué niveles cambian— es la única regla de negocio real de este requerimiento, y meterla en el servicio la volvería inseparable de la transacción y de los puertos: probar «insertar por encima de la superior» exigiría PostgreSQL. Como objeto de dominio, los seis casos de `spec.md` §9 y §13 se prueban con listas en memoria (§11).

**El `UPDATE` masivo de niveles lo ejecuta el adaptador, no el agregado fila a fila.** El dominio decide *qué* nivel corresponde a cada membresía; el adaptador lo aplica con una sola sentencia. La alternativa —cargar cada entidad afectada y modificarla— produce tantas sentencias como membresías haya y no aporta nada, porque no hay regla que evaluar por fila. Lo que el adaptador **no** hace es decidir: recibe el resultado del dominio y lo escribe.

**`MembershipController` es un controlador nuevo, no un método de otro.** El recurso es `/api/v1/memberships` y `RF-SP-017` y `RF-SP-018` cuelgan de él. Es el mismo criterio con el que `RF-SP-010` §3 creó `PermissionController`.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/memberships` | Registra una membresía y la inserta en la cadena |

**Petición**

```json
{
  "code": "PLATA",
  "name": "Plata",
  "description": "Acceso a los cursos de nivel intermedio.",
  "childMembershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d20"
}
```

- **`childMembershipId` admite ausencia y `null`, con el mismo significado**: la nueva membresía va al extremo inferior de la cadena (`FA-002`). Es el caso más común y el que no toca ninguna otra fila.
- **No existe campo `level` ni `parentMembershipId`.** El nivel lo calcula el sistema (`CA-SP-115`) y la superior se deduce de la hija indicada; el DTO se deserializa con `FAIL_ON_UNKNOWN_PROPERTIES` activo, de modo que enviar cualquiera de los dos devuelve `400` y no se ignora en silencio. Es lo mismo que `RF-SP-001` §4 hizo con `status` e `isSystem`, y es lo que hace verificable que la posición no se pueda forzar desde fuera.
- **La membresía se indica por su hija y no por su superior**, porque así lo fija `RN-SP-007`. La razón se lee mejor desde el negocio: al crear un nivel intermedio se sabe a quién quiere uno dejar por debajo.
- `name` y `description` se recortan de espacios al inicio y al final antes de validar y persistir; sin ese recorte, `"Plata "` y `"Plata"` serían dos nombres distintos para `uq_memberships_name` y la unicidad se burlaría con un espacio. **`code` no se toca**: se persiste tal como llegó, igual que en `RF-SP-001` §4, para que el actor vea exactamente qué código quedó registrado.

**Respuesta `201`**

Con cabecera `Location: /api/v1/memberships/{id}`.

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d30",
  "code": "PLATA",
  "name": "Plata",
  "description": "Acceso a los cursos de nivel intermedio.",
  "level": 2,
  "parentMembershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10",
  "childMembershipId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d20",
  "createdAt": "2026-08-21T14:32:11Z",
  "updatedAt": "2026-08-21T14:32:11Z"
}
```

- **La respuesta devuelve la posición resultante, no solo lo que se envió** (`spec.md` §6.2: «con su nivel y su posición en la cadena»). `level`, `parentMembershipId` y `childMembershipId` son las tres cosas que el actor no podía saber antes de la operación.
- **Los vecinos van como identificadores, no como objetos anidados.** Devolverlos con su código y su nombre obligaría a dos consultas más para una información que el cliente casi siempre ya tiene: llegó aquí desde `RF-SP-017`, que devuelve la cadena entera. `RF-SP-018` es el endpoint que sí los expande, porque es su razón de ser.
- **`childMembershipId` es nulo cuando la nueva quedó en el extremo inferior**, y `parentMembershipId` es nulo cuando quedó como superior (`FA-001` y el caso límite de insertar por encima de la cima). Ambos se devuelven como `null` sin omitirse.
- **No se devuelve la cadena completa reordenada.** Un alta que devolviera todas las membresías afectadas mezclaría la respuesta de la operación con la del listado; quien necesite ver el resultado global llama a `RF-SP-017`, que devuelve la cadena entera en una sola llamada y sin paginar.
- **No existe `createdBy`** ni equivalente: el actor no vive en la tabla de negocio (Art. V.7). Quién creó la membresía se responde con `RF-SP-011`.

**Orden de verificación.** Determina qué error recibe una petición que incumple varias cosas a la vez:

1. Formato y obligatoriedad (`VAL-001`, `VAL-002`). Se evalúan **todas** y se devuelven juntas en `errors`.
2. **Bloqueo de la cadena** (abajo).
3. Unicidad de código y nombre (`EX-001`).
4. Existencia de la hija indicada (`EX-002`).
5. Cálculo de la posición y de los niveles en `domain`.

El bloqueo va antes de las verificaciones y no después: verificar sobre una cadena que otra transacción está reordenando produce decisiones tomadas sobre un estado que ya no existe.

### Cómo se resuelve la inserción concurrente

Es el tercer caso límite de `spec.md` §13 —«ambas pretenderían ser su superior; la restricción única debe resolver el empate sin dejar la cadena bifurcada»— y tiene dos capas:

**Primera capa: bloqueo explícito.** El caso de uso abre con

```sql
SELECT id, parent_membership_id, level FROM memberships ORDER BY level FOR UPDATE;
```

La cadena se lee entera y bloqueada. Es asumible porque son unos pocos elementos (`spec.md` §6.1) y porque las altas de membresía son raras: se serializan las inserciones concurrentes, y la segunda encuentra la cadena ya reordenada y calcula su posición sobre el estado real. El listado de `RF-SP-017` **no se bloquea**: `FOR UPDATE` no estorba a los lectores.

**Segunda capa: la restricción diferida.** Si el bloqueo fallara por cualquier motivo —un camino nuevo que no lo tome, una réplica, un defecto—, `uq_memberships_parent` rechaza el empate al confirmar. Esa es la garantía de que la cadena no se bifurca; el bloqueo es lo que hace que el usuario reciba un error comprensible en lugar de un fallo de integridad.

**Y ahí está el detalle que hay que asumir:** una violación de restricción diferida salta en el `COMMIT`, es decir, **fuera del caso de uso**, cuando el interceptor transaccional confirma. El adaptador no puede capturarla como sí captura la de código o nombre duplicado. Se traduce en `GlobalExceptionHandler`, que la distingue por el **nombre de la restricción** —nunca por el texto del mensaje del driver, que cambia entre versiones— y devuelve `409` con `error_code = 'EX-003'`. Es un `409` sin verificación previa que lo anticipe, y por eso su mensaje dice que la cadena cambió durante la operación y que se reintente, no que el dato sea inválido.

**`EX-003` es una excepción propia, y eso se corrigió al aprobar este plan.** El borrador reutilizaba `EX-002`, que en `spec.md` §10 es la membresía hija inexistente y devuelve `422`: dos hechos distintos con un solo código y con estados distintos, que es el mismo defecto que el Art. I.7 ya obligó a corregir en `RF-SP-008` y `RF-SP-009`. La especificación volvió a su compuerta y ganó `EX-003` con su criterio `CA-SP-349`.

**Errores**

| Caso | Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|---|
| `VAL-001` | `400` | Falta el código o viene en blanco | `VAL-001` | `code` |
| `VAL-002` | `400` | Falta el nombre o viene en blanco | `VAL-002` | `name` |
| — | `400` | El código, el nombre o la descripción exceden su longitud | `VAL-001` / `VAL-002` | El campo que excede |
| `EX-001` / `VAL-003` / `VAL-004` | `409` | Existe otra membresía con ese código o ese nombre; el detalle dice cuál de los dos | `EX-001` | `code` o `name` |
| — | `400` | El código no cumple `^[A-Z][A-Z0-9_]*$` | `VAL-006` | `code` |
| `EX-002` / `VAL-005` | `422` | La membresía hija indicada no existe | `EX-002` | `childMembershipId` |
| `EX-003` | `409` | La cadena cambió durante la operación (empate concurrente) | `EX-003` | — |
| — | `401` | Token ausente o inválido | `AUTH-001` | — |
| — | `403` | Autenticado sin `memberships:create` | `AUTH-002` | — |
| — | `500` | Fallo no controlado | `ERR-500` | — |

- **`422` y no `404` para la hija inexistente.** El recurso de la petición es la colección `/api/v1/memberships`, que existe; lo que no resuelve es una referencia **del cuerpo**. Es exactamente el criterio de `development-guide.md` §7.1, estrenado por `RF-SP-001` con su rol padre.
- **`409` y no `422` para el duplicado**, porque es un conflicto con el estado actual sobre datos que existen (`architecture.md` §7.2).
- **`EX-001` no produce un `error_code` de regla**, a diferencia de `RF-SP-001`, donde el duplicado violaba `RN-SEG-001`. Aquí no hay regla `RN-…` de unicidad de membresía, de modo que el código es el de la excepción de la especificación (`architecture.md` §7.3). `VAL-003` y `VAL-004` enuncian como validación lo mismo que `EX-001` y no producen un tercer código.
- **La verificación previa de unicidad existe para poder dar un mensaje preciso**, no para garantizarla: la garantía la dan `uq_memberships_code` y `uq_memberships_name`, y su violación se captura y se traduce distinguiendo por nombre de restricción cuál de las dos saltó. Es el criterio de `RF-SP-001` §9: la restricción decide, el `SELECT` solo redacta.
- Todos los `type` que este endpoint usa ya los estrenaron `RF-SP-001` y `RF-SP-003`. El formato es el de `architecture.md` §7.3, con `correlationId` siempre presente.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/memberships` | `memberships:create` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`), que sembró el bloque completo de `memberships:` aunque ningún endpoint lo declarara todavía.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **`memberships:create` no se comparte con `memberships:read`.** Consultar la cadena es una operación de apoyo que cualquier administrador necesita; insertar un nivel intermedio cambia el alcance de todos los consumidores que ya tenían membresía (`spec.md` §14, pregunta 3) y es irreversible por `RN-SP-008`.
- **No hay techo de privilegios que verificar.** A diferencia de `RF-SP-001`, aquí no existe nada análogo a `RN-SEG-010`: una membresía no concede permisos del sistema, de modo que quien puede crearla puede crear cualquiera. Los permisos efectivos del actor no intervienen, y por tanto la resolución del permiso **sí** puede usar la caché de `security.md` §4.5.
- **No hay filtrado por alcance de datos.** La cadena es única y global (`spec.md` §14, pregunta 4).
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6). `CA-SP-119` se satisface ahí.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Alta de la membresía | `audit_change_log` | `module = 'SP'`, `entity = 'memberships'`, `entity_id` de la nueva, `action = 'CREATE'`, `changes` con el estado inicial completo: `code`, `name`, `description`, `parent_membership_id` y `level` |
| **Cada membresía que el reordenamiento modificó** | `audit_change_log` | Una fila **por cada una**, `action = 'UPDATE'`, `changes` con el diff de lo que cambió: `level` siempre, y `parent_membership_id` en la hija reencadenada |
| Rechazo por `EX-001` o `EX-002` | `audit_error_log` | `resource = 'memberships'`, `operation = 'POST /api/v1/memberships'`, `error_code` de la tabla de §4, `error_type = 'BUSINESS_RULE'`, `http_status`, `severity = 'MEDIA'` y `message` saneado |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_security_log` | **No aplica al alta.** Ver abajo |
| — | `audit_deletion_log` | No aplica: este requerimiento no elimina nada |

Cuatro decisiones:

- **Un evento por cada membresía tocada, todos bajo el mismo `correlation_id`** (`CA-SP-118`, `spec.md` §14, pregunta 2). Es la única forma de que `RF-SP-011` responda «quién cambió el nivel de esta membresía», que es como se pregunta en la práctica: un único evento sobre la creada dejaría los cambios de las demás sin autor, y `audit_change_log` es la única fuente de esa autoría (Art. V.7). El identificador de correlación compartido es lo que permite recuperar la operación entera con `CA-SP-085` de `RF-SP-011`.
- **La escritura masiva de niveles no puede auditarse con un solo evento agregado.** Sería tentador, porque una sola sentencia cambió *n* filas; pero la línea de tiempo de una membresía se consulta por su `entity_id`, y un evento agregado no aparecería en ninguna de ellas. El coste es acotado: la cadena tiene unos pocos elementos.
- **El alta no emite evento de seguridad**, y conviene decir por qué no es una omisión. El catálogo de `security.md` §8.1 es cerrado y no incluye las membresías: son un nivel de acceso a **contenido**, no un privilegio sobre el sistema, y no intervienen en la resolución de permisos de §4.5. Es la asimetría deliberada con `RF-SP-001`, donde crear un rol sí amplía la superficie de privilegios y por eso emite dos eventos.
- **Las validaciones de formato (`400`) no se auditan** (`architecture.md` §6.6.4): son ruido de formulario, y `ck_audit_error_log_status` (`RF-SP-013`) rechazaría la fila en el esquema.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Bloqueo de la cadena, `INSERT`, `UPDATE` de niveles, reencadenado y **todos** sus eventos de `audit_change_log` | **La misma** (Art. V.14). Si el alta se revierte, sus eventos también; si un evento falla, el alta falla |
| `audit_error_log` de un rechazo o un fallo | **Independiente**, `REQUIRES_NEW` |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`@Transactional` vive sobre `RegisterMembershipService`, en `application`; nunca en el controlador ni en el repositorio.

Dos matices propios de esta operación:

**La atomicidad no es un detalle, es el requerimiento.** Una transacción parcialmente aplicada dejaría la cadena bifurcada o con niveles duplicados, es decir, un orden lineal que ya no es lineal, y `RN-SP-008` impide corregirlo por la API. Las restricciones diferidas garantizan que una transacción incoherente **no pueda confirmarse**: si el recálculo produjo dos membresías en el mismo nivel, el `COMMIT` falla y no queda rastro.

**No hay evento posterior al commit**, a diferencia de `RF-SP-001` §7. Allí el evento de seguridad se enganchaba al commit para no producir eventos fantasma; aquí no hay evento de seguridad, y los de cambio van dentro por mandato del Art. V.14.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `requirements/sp.md` | **§10.7 gana `uq_memberships_name`** —sobre `f_unaccent(lower(name))`— y **`ck_memberships_code_format`**, que no declaraba, y **§10.4 recoge que `uq_memberships_parent` es `NULLS NOT DISTINCT`** —sin lo cual admite varias membresías superiores— y que `level` cuenta desde la cima, con `1` como la superior. Sin esa última frase, «mayor nivel» se lee al revés |
| `shared/error` | `GlobalExceptionHandler` debe traducir la violación de una restricción **diferida**, que salta al confirmar y no dentro del caso de uso. Es el primer requerimiento que lo necesita, y quien implemente el manejador debe saberlo antes de encontrárselo: capturarla en el adaptador no funciona |
| `RF-SP-017` y `RF-SP-018` | Cuelgan de `MembershipController` y reutilizan `MembershipResponse`. Ambos leen `level` con el significado que fija este plan. `RF-SP-017` ordena por `level ASC` y `RF-SP-018` deduce la hija por `parent_membership_id`, que `uq_memberships_parent` garantiza única |
| `RF-SP-032` y `RF-SP-033` | Asignan y retiran la membresía de un usuario. `user_memberships` referenciará `memberships(id)`, y `RN-SP-013` exige además que el usuario tenga un rol `CONSUMIDOR`. Nada de eso condiciona este requerimiento; lo que sí impone es que la clave foránea sea `ON DELETE RESTRICT`, coherente con que una membresía no se elimine |
| **Academia y productos** | Obligación declarada: **un contenido que exija un nivel mínimo referencia la membresía por su `id`, nunca por el número de `level`**. El número cambia de significado cada vez que se intercala una membresía, y eso es para lo que sirve insertar (`spec.md` §14, pregunta 3). La comparación por nivel se hace en el momento de evaluar el acceso, resolviendo el `id` a su `level` vigente |
| `shared/audit` | Sin cambios estructurales. Gana un cliente que emite **varios eventos de cambio en una sola operación**, que es un uso ya previsto por el identificador de correlación |
| `RF-SP-011` | Su consulta responde ahora también por la entidad `memberships`. Ninguna adaptación: el registro es genérico por diseño |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| `UNIQUE (parent_membership_id)` corriente, como se lee `requirements/sp.md` §10.7 | PostgreSQL trata los nulos como distintos, de modo que admitiría varias membresías superiores: exactamente la bifurcación que la restricción existe para impedir, y en el peor punto de la cadena |
| Un índice único parcial adicional para el nulo, como `uq_roles_single_root` en `roles` | Es la forma correcta **allí**, donde la unicidad es condicional al borrado lógico y una restricción de tabla no admite `WHERE`. Aquí no hay borrado lógico, y `NULLS NOT DISTINCT` resuelve las dos mitades de `CA-SP-114` con una sola cláusula |
| Restricciones inmediatas en lugar de diferidas | El `UPDATE` masivo de niveles colisiona consigo mismo aunque el estado final sea correcto, y el caso de insertar por encima de la superior no tiene orden de sentencias válido. Diferir comprueba cuando la cadena tiene que ser correcta: al confirmar |
| No declarar `uq_memberships_level` | Un recálculo defectuoso dejaría dos membresías en el mismo nivel y el listado de `RF-SP-017` devolvería un orden arbitrario entre ambas, sin que nada fallara. Es el defecto más difícil de notar de esta tabla |
| Prescindir de `level` y recorrer `parent_membership_id` al consultar | Elimina la posibilidad de que ambos diverjan, a cambio de que todo listado y toda comparación de nivel exijan una consulta recursiva. `requirements/sp.md` §10.4 ya decidió materializarlo; y sin `level`, `RF-SP-017` no podría ordenar sin `WITH RECURSIVE` |
| Numerar `level` desde el extremo inferior | Haría que el alta más común —`FA-002`, sin hija— renumerara toda la cadena, cuando la especificación dice que ese caso no reordena nada. La ventaja aparente, que «mayor nivel» coincidiera con mayor número, se resuelve con una frase en `requirements/sp.md` §10.4 |
| Numerar con huecos (10, 20, 30) para insertar sin renumerar | Evita el `UPDATE` masivo mientras haya hueco y obliga a una renumeración completa cuando se agota, que es el peor momento posible: en producción y sin previo aviso. Con unos pocos elementos, el `UPDATE` masivo no cuesta nada |
| Indicar la membresía **superior** en lugar de la hija | `RN-SP-007` fija que se indica la hija. Y desde el negocio se lee mejor: al crear un nivel intermedio se sabe a quién se quiere dejar por debajo |
| Aceptar `level` en la petición | Permitiría forzar una posición incoherente con la cadena y convertiría `CA-SP-115` en algo que el cliente puede incumplir. El sistema lo calcula, y `FAIL_ON_UNKNOWN_PROPERTIES` hace verificable que no se acepta |
| Resolver el empate concurrente solo con la restricción, sin bloqueo previo | El usuario recibiría un fallo de integridad traducido a `409` en un caso que no es un conflicto de datos sino una carrera. El bloqueo serializa lo que son unas pocas operaciones al año y da un resultado correcto en lugar de un error |
| Bloquear solo la fila de la hija indicada, no la cadena entera | No cubre `FA-002`, donde no hay hija que bloquear, ni la inserción por encima de la superior. Y el `UPDATE` masivo toca de todos modos filas que no estarían bloqueadas |
| Un solo evento de auditoría agregado para todo el reordenamiento | `spec.md` §14, pregunta 2, lo resolvió: la línea de tiempo de una membresía se consulta por su `entity_id`, y un evento agregado no aparecería en ninguna de ellas. Los cambios de nivel quedarían sin autor |
| Emitir además un evento de `audit_security_log` | El catálogo de `security.md` §8.1 es cerrado y no incluye las membresías, que son un nivel de acceso a contenido y no un privilegio sobre el sistema. Añadirlo obligaría a ampliar un catálogo cerrado para un evento que nadie consulta desde la seguridad |
| Devolver la cadena completa reordenada en la respuesta del alta | Mezcla la respuesta de la operación con la del listado. `RF-SP-017` devuelve la cadena entera en una llamada y sin paginar |
| Un `CHECK` de formato sobre `code`, como en `roles` | `spec.md` §11 no declara ninguna validación de formato para el código de una membresía, y el esquema no debe inventar reglas que ningún documento aprobado establece. La consecuencia se anota en §10 |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Un nombre mal escrito queda para siempre (`RN-SP-008`) | Medio | Es la consecuencia asumida que `spec.md` §13 y §14 ya declararon: la corrección es una migración, operación excepcional y trazable. Toda la defensa está en el alta, y por eso las validaciones se evalúan todas juntas y el recorte de espacios ocurre **antes** de comprobar la unicidad |
| ~~`uq_memberships_name` distingue mayúsculas y acentos: `Plata` y `plata` podrían coexistir~~ | — | **Resuelto el 21-08-2026:** la unicidad se declara sobre `f_unaccent(lower(name))` y el código gana `ck_memberships_code_format` (§2). Se decidió ahora precisamente porque `RN-SP-008` no deja corrección posible después, y porque hacerlo con la tabla ya poblada exigiría migrar datos. `spec.md` §11 recoge `VAL-006` y la precisión de `VAL-004` |
| `level` y `parent_membership_id` divergen por un defecto en el recálculo | **Alto** | `uq_memberships_level` y `uq_memberships_parent` impiden los dos síntomas más graves —nivel repetido y cadena bifurcada— en el `COMMIT`. Lo que ninguna restricción declarativa puede impedir es que el orden de `level` contradiga el de la cadena estando ambos bien formados; eso se cubre con la prueba de coherencia de §11, que recorre la cadena y compara |
| La violación de la restricción diferida no se traduce y llega al cliente como `500` | Medio | Salta en el `COMMIT`, fuera del caso de uso: es el defecto más fácil de introducir aquí, porque el `try/catch` del adaptador —que sí funciona para código y nombre— no la ve. Traducción en `GlobalExceptionHandler` por nombre de restricción (§4) y prueba de concurrencia propia en §11 |
| El bloqueo de la cadena serializa las altas y una operación lenta bloquea a las demás | Bajo | Son unos pocos elementos y las altas de membresía son excepcionales. El listado de `RF-SP-017` no se ve afectado: `FOR UPDATE` no bloquea a los lectores |
| Un módulo de contenidos guarda «exige nivel 3» y una inserción intermedia cambia el significado de ese 3 | **Alto** | Obligación declarada en §8: se referencia la membresía por su `id` y se compara por `level` en el momento de evaluar. Es un riesgo que este módulo no puede cerrar por su cuenta, y por eso se escribe donde lo verá quien construya academia y productos |
| Insertar un nivel intermedio cambia el alcance de quienes ya tenían membresía | Medio | Es deliberado (`spec.md` §14, pregunta 3): el acceso se evalúa siempre por nivel y congelarlo haría que la cadena dejara de significar nada. Lo que este plan aporta es que el cambio queda **auditado membresía por membresía**, de modo que puede reconstruirse quién lo provocó y cuándo |
| La primera membresía se crea con una hija indicada que no existe, y el sistema queda sin cadena | Bajo | `EX-002` lo rechaza con `422` antes de escribir nada. `FA-001` es el único camino de la primera membresía y no admite hija, porque no hay ninguna |

## 11. Estrategia de prueba

Niveles: **Unitaria** (dominio, sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V13` aplicada) y **API** (extremo a extremo por HTTP, con autenticación).

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-111` | Unitaria + Integración + API | Sobre una tabla vacía, el alta sin hija deja una membresía con `parent_membership_id` nulo y `level = 1`; el endpoint devuelve `201` con ambos vecinos nulos |
| `CA-SP-112` | Unitaria + Integración + API | Con una cadena de tres, insertar indicando la del medio como hija deja la nueva entre esa y su superior anterior, y la hija reencadenada apunta a la nueva |
| `CA-SP-113` | Unitaria + API | Sin `childMembershipId` —ausente y `null`, las dos formas— la nueva queda en el extremo inferior, con `level = max + 1` y **sin que ninguna otra fila cambie** |
| `CA-SP-114` | Integración | Tras cada uno de los cinco casos de inserción, ninguna membresía tiene dos hijas y existe exactamente una superior. Además, un `INSERT` directo que declare una superior ya tomada es rechazado por `uq_memberships_parent`, y un segundo `INSERT` con `parent_membership_id` nulo también |
| `CA-SP-115` | Unitaria + Integración | Los niveles resultantes son consecutivos desde `1` y sin huecos, y `updated_at` cambió **solo** en las membresías desplazadas |
| `CA-SP-116` | Integración + API | Los índices únicos rechazan el duplicado en base de datos; el endpoint devuelve `409` con `EX-001` e indica si el duplicado es de código o de nombre |
| `CA-SP-117` | API | Una hija inexistente devuelve `422` con `EX-002` y campo `childMembershipId`, y **no** se escribe ninguna fila |
| `CA-SP-118` | Integración + API | Tras una inserción intermedia sobre una cadena de cuatro existen un evento `CREATE` y tantos `UPDATE` como membresías desplazadas, **todos con el mismo `correlation_id`**, y la operación completa se recupera filtrando por él en `RF-SP-011` |
| `CA-SP-119` | API | Un actor autenticado sin `memberships:create` recibe `403`, no se crea nada y queda el evento de denegación en `audit_security_log` |
| `CA-SP-347` | Integración + API | `ck_memberships_code_format` rechaza minúsculas, guion medio, espacios y códigos que empiezan por dígito; el endpoint devuelve `400` con `VAL-006` |
| `CA-SP-348` | Integración + API | Con `Plata` registrada, un alta con `plata` y otra con `Platá` devuelven `409` con `EX-001`; el índice único funcional las rechaza también por `INSERT` directo |
| `CA-SP-349` | Integración | El empate concurrente devuelve `409` con `error_code = 'EX-003'`, distinto del `422` con `EX-002` de la hija inexistente |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Insertar por encima de la membresía superior | Unitaria + Integración | La nueva queda con `parent_membership_id` nulo y `level = 1`; la anterior superior pasa a `2` y apunta a la nueva. Es el caso que ningún orden de sentencias resolvería con restricciones inmediatas |
| Cadena con una sola membresía | Unitaria + API | Insertar por encima y por debajo son las dos únicas posibilidades, y ambas producen una cadena de dos coherente |
| Inserción concurrente sobre la misma hija | Integración | Dos altas simultáneas indicando la misma hija: una devuelve `201` y la otra `409`; **nunca `500`**, y la cadena resultante no está bifurcada ni tiene niveles repetidos. Es la prueba que verifica la traducción de la restricción diferida |
| Alta concurrente del mismo código | Integración | Dos altas simultáneas con el mismo código: una `201`, la otra `409` con `EX-001`. Nunca `500` |
| Coherencia entre `level` y la cadena | Integración | Tras una secuencia aleatoria de veinte inserciones, recorrer la cadena desde la superior por `parent_membership_id` produce exactamente el mismo orden que ordenar por `level`, y los niveles son `1..n` sin huecos. Es la prueba que detecta una divergencia que ninguna restricción declarativa puede impedir |
| Rechazo del nivel enviado por el cliente | API | Una petición con `level` o `parentMembershipId` devuelve `400` por campo desconocido. Es lo que hace verificable que la posición no se puede forzar |
| Nombre con espacios sobrantes | API | `"Plata "` y `"Plata"` son el mismo nombre para la unicidad: el segundo alta devuelve `409` |
| Descripción en el límite | Integración | 500 caracteres se aceptan; 501 devuelven `400`, y `ck_memberships_description_length` los rechaza también por `INSERT` directo |
| Atomicidad del reordenamiento | Integración | Forzando un fallo después del `UPDATE` de niveles y antes del reencadenado, **no queda ninguna fila escrita** y la cadena conserva su estado anterior |
| Número de sentencias por alta | Integración | El bloqueo, la verificación de unicidad, el `INSERT`, un único `UPDATE` masivo de niveles y el reencadenado: el número no crece con el tamaño de la cadena salvo en los eventos de auditoría, que son uno por membresía tocada por mandato de `CA-SP-118` |
| Ausencia de edición y eliminación | API | `PUT`, `PATCH` y `DELETE` sobre `/api/v1/memberships/{id}` devuelven `405`. Es la única forma de verificar `RN-SP-008`, que no tiene código que la implemente |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento, y la prueba de ausencia de cascadas de `RF-SP-012` §11 se ejecuta sobre el esquema completo, incluida `fk_memberships_parent`.
