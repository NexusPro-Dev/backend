# PLAN — `RF-SP-011` Consultar auditoría de cambios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-011` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, filtros y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. Este documento decide lo único que distingue a esta consulta de las anteriores: **cómo se sostiene sobre una tabla que crece sin límite**. Es también el primero de los cuatro de auditoría, así que fija la mecánica que heredan `RF-SP-012` a `RF-SP-014`.

---

## 1. Enfoque

Una sola sentencia de lectura sobre una proyección, sin `JOIN`, sin entidades y sin `domain`: `spec.md` §5 declara que ninguna regla de negocio gobierna esta consulta. Estructuralmente es `RF-SP-002` con otros filtros.

Lo que la hace distinta es el volumen. `roles` tiene decenas de filas y `permissions` dieciséis; `audit_change_log` recibe **una fila por cada escritura de negocio de todo el sistema** y no se purga dentro de este alcance. Eso invalida dos cosas que en los listados anteriores eran gratis:

1. **El conteo exacto por página.** El plan de `RF-SP-002` §4 dejó anotado que su estrategia de conteo no debía heredarse aquí sin revisarla. Se revisa y se cambia: el conteo pasa a ser **exacto hasta un techo** y aproximado por encima, de modo que su coste queda acotado con independencia del tamaño de la tabla.
2. **El orden por defecto.** `spec.md` §8 exige del más reciente al más antiguo, y ninguno de los índices que `V4` declara sirve a una consulta sin filtros ordenada por fecha. Sin un índice sobre `occurred_at`, la primera página del listado obliga a ordenar la tabla entera. Se añade en `V9`.

El resto —filtros opcionales, colección vacía sin error, `readOnly = true`— es la mecánica ya establecida y se reutiliza sin cambios.

## 2. Cambios de esquema

**Migración:** `V9__create_audit_change_log_timeline_index.sql`

La tabla `audit_change_log` y sus cuatro índices mínimos los crea `V4__create_audit_logs.sql` (`RF-SP-001`), conforme a `architecture.md` §6.6.6. Esta migración **no cambia columnas ni restricciones**: añade el único acceso que falta.

| Tabla | Cambio | Detalle |
|---|---|---|
| `audit_change_log` | Altera (índice) | `ix_audit_change_log_occurred_at` sobre `(occurred_at DESC, id DESC)` |

**Por qué hace falta y por qué no estaba.** Los cuatro índices de `architecture.md` §6.6.6 responden preguntas que empiezan por un filtro: la línea de tiempo de un registro, todo lo que hizo una persona, el enlace con una petición, la investigación por origen. Ninguno responde la pregunta que hace el listado **sin filtros**, que es «los últimos veinte eventos de todo el sistema»: un B-tree sobre `(entity, entity_id, occurred_at DESC)` no sirve para ordenar por `occurred_at` a secas, porque sus dos primeras columnas mandan en el orden. Sin este índice, la primera página del listado por defecto ordena la tabla entera para devolver veinte filas.

**Por qué el índice incluye `id`.** Es la misma razón por la que `RF-SP-002` añade `id` como desempate a todo ordenamiento: dos eventos pueden compartir `occurred_at` —dos escrituras dentro del mismo milisegundo son perfectamente posibles— y sin desempate el orden de las filas empatadas queda a criterio del plan de ejecución, que puede cambiar entre la página 1 y la 2. Aquí el desempate tiene además una propiedad que en `roles` no se daba: `id` es un **UUID v7**, cuyos bits más significativos son la marca temporal (Art. V.11), de modo que ordenar por `id DESC` dentro del mismo instante sigue siendo orden cronológico y no un desempate arbitrario. Al llevarlo en el índice, el desempate es gratis: no añade una operación de ordenamiento.

**Qué índices NO se crean, y por qué importa.** Sería fácil justificar uno por cada filtro de `spec.md` §6.1 —módulo, acción, entidad—. No se hace, por dos razones:

- **Cardinalidad.** `module` tiene tantos valores como módulos tenga el sistema, y `action` exactamente dos (`CREATE` y `UPDATE`). Un índice sobre una columna que divide la tabla en dos mitades no acota nada: el planificador lo descarta y recorre igual. Filtrar por `action` es útil para el usuario y barato de aplicar sobre el conjunto que ya acotaron el orden y el resto de filtros; no merece estructura propia.
- **Coste de escritura.** Cada índice de esta tabla se paga en **cada operación de negocio del sistema**, porque cada una emite su evento dentro de la misma transacción (Art. V.14). Un índice que no se usa no es neutro: es latencia añadida a toda alta, edición y borrado de NEXUS. En una tabla append-only de alto volumen, el criterio correcto es el mínimo que sostiene las consultas reales, no el máximo que podría servir.

Si alguna consulta concreta se demostrara lenta con datos reales, el índice que la resuelva es una migración de una línea y no cambia el contrato. Anotado en §10.

**Recordatorios de la plantilla que no aplican:** no se crea ninguna tabla, así que no hay clave primaria UUID v7 que declarar, ni `created_at`/`updated_at` —estas tablas no los tienen a propósito (`RF-SP-001` §2)—, ni columnas de actor que omitir, ni integridad declarativa que añadir.

## 3. Componentes afectados

Paquete raíz del módulo: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `spec.md` §5 no declara ninguna regla `RN-…` |
| `application` | `ListChangeAuditService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)`. Traduce la consulta al puerto y arma el resultado paginado |
| `application` | `ChangeAuditQuery` | Nuevo | Criterios ya validados y normalizados. Sin tipos de HTTP |
| `application` | `ChangeAuditItem` | Nuevo | Modelo de lectura: exactamente los campos que el listado devuelve |
| `application` | `ChangeAuditQueryRepository` | Nuevo | Puerto de consulta: recibe `ChangeAuditQuery`, devuelve la página y el total acotado |
| `application` | `AuditAction` | Nuevo | Enum cerrado `CREATE`, `UPDATE`. Es la lista blanca del filtro de acción |
| `infrastructure` | `JpaChangeAuditQueryRepository` | Nuevo | Adaptador. Construye predicado y proyección con la API de criterios, y ejecuta el conteo acotado |
| `infrastructure` | `AuditChangeLogEntity` | Nuevo | Mapeo JPA de `audit_change_log`. Se usa como metamodelo para nombrar columnas; la consulta no lo instancia |
| `api` | `AuditController` | Nuevo | `GET /api/v1/audit/changes`. Declara el permiso y delega. Los otros tres registros añadirán aquí su método |
| `api` | `ListChangeAuditRequest` | Nuevo | Parámetros de consulta con Bean Validation (`VAL-001` a `VAL-003`) |
| `api` | `ChangeAuditItemResponse` | Nuevo | DTO de salida de cada fila |
| `shared/api` | `PageResponse<T>` | **Modificado** | Gana `totalIsExact` (§4). Afecta a **todos** los listados del sistema |
| `shared/api` | `BoundedCount` | Nuevo | Encapsula el conteo con techo: ejecuta la sentencia acotada y devuelve el total con su marca de exactitud |
| `shared/config` | — | Modificado | Declara `nexus.pagination.count-limit: 10000`, junto a los dos valores de paginación que ya existen |

Tres decisiones de reparto:

**`AuditController` es un controlador nuevo, y uno solo para los cuatro registros.** Las rutas son `/api/v1/audit/changes`, `/deletions`, `/errors` y `/security`: un mismo recurso raíz con cuatro colecciones hermanas, cada una con su permiso. Un controlador por registro multiplicaría por cuatro la configuración de rutas sin separar nada que esté acoplado; uno solo mantiene junto lo que se lee junto. Cada método declara **su** permiso, de modo que compartir clase no comparte autorización.

**`BoundedCount` vive en `shared/api`, no en el adaptador.** Los cuatro registros de auditoría lo necesitan igual, y `RF-SP-002` podría adoptarlo el día que `roles` crezca. Dejarlo en `JpaChangeAuditQueryRepository` garantizaría cuatro copias con cuatro techos distintos.

**No hay puerto hacia `USR` y el actor se devuelve como identificador.** Se estudió resolver el nombre de quien hizo el cambio, como `RF-SP-003` resuelve el número de usuarios de un rol. Se descarta aquí: `SP` no puede leer las tablas de `USR` (`architecture.md` §5.3), de modo que haría falta un puerto con resolución **por lotes** —una llamada por página, nunca una por fila— y una decisión sobre qué mostrar cuando ese módulo no responda. Nada de eso lo pide `spec.md` §6.2, que dice «actor» sin exigir su nombre, y el valor probatorio del registro está en el identificador, que no cambia nunca; un nombre es una foto del momento en que se consultó, no del momento en que ocurrió el evento. La consecuencia está declarada en §10: hasta que exista `USR`, la pantalla muestra un UUID.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/audit/changes` | Listado paginado de eventos de creación y edición |

**Petición**

```
GET /api/v1/audit/changes?page=0&size=20
                         &module=SP
                         &entity=roles
                         &entityId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10
                         &actorId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d99
                         &action=UPDATE
                         &from=2026-08-01T00:00:00Z
                         &to=2026-09-01T00:00:00Z
                         &correlationId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9daa
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `page` | entero | `0` | Base cero. Negativa → `VAL-002` |
| `size` | entero | `20` | Entre 1 y 100. Fuera de rango → `VAL-002`; **no se recorta** |
| `module` | texto | — | Coincidencia exacta. Un módulo inexistente devuelve colección vacía |
| `entity` | texto | — | Coincidencia exacta |
| `entityId` | UUID | — | Registro concreto. No se valida que exista |
| `actorId` | UUID | — | No se valida que exista |
| `action` | enum | — | `CREATE` o `UPDATE`. Otro → `VAL-003` |
| `from` / `to` | instante ISO-8601 | — | Rango del evento. `from` posterior a `to` → `VAL-001` |
| `correlationId` | UUID | — | Todos los eventos de una misma petición (`CA-SP-085`) |

- **No hay `sort`.** El orden es parte del significado de este recurso: `spec.md` §8 exige del más reciente al más antiguo, y un registro cronológico que pudiera ordenarse por módulo o por entidad respondería otra pregunta. Ahorra además la lista blanca de ordenamiento que `RF-SP-002` necesitó, y con ella su superficie de validación. La sentencia ordena siempre por `occurred_at DESC, id DESC`.
- **`from` y `to` son instantes, no fechas.** Se aceptan como `OffsetDateTime` (`2026-08-01T00:00:00-05:00`) y se rechaza una fecha suelta. Si se admitiera `2026-08-01`, el servidor tendría que elegir una zona horaria para interpretarla y elegiría la del servidor, que casi nunca es la de quien consulta: un auditor en Bogotá pediría «el día 1» y recibiría desde las 19:00 del día anterior. `spec.md` §13 lo deja claro —los eventos se almacenan en tiempo universal y la conversión es del cliente—, y esta es la forma de que el cliente pueda hacerla.
- **El rango es semiabierto: `occurred_at >= from AND occurred_at < to`.** Con ambos extremos inclusivos, dos rangos consecutivos —agosto y septiembre— devolverían dos veces el evento que cayera exactamente en la medianoche del 1 de septiembre, y quien recorra la línea de tiempo mes a mes contaría de más. El semiabierto hace que los rangos se encadenen sin solaparse.
- **Ningún filtro es obligatorio**, incluido el rango de fechas. `spec.md` §14, pregunta 2, lo resolvió: limitar el rango obligaría a trocear justo la consulta que más valor tiene, la línea de tiempo completa de un registro. Que se sostenga es cuestión de índices y del conteo acotado, no de negocio.

**Respuesta `200`**

```json
{
  "content": [
    {
      "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01",
      "occurredAt": "2026-08-21T14:32:11.482Z",
      "actorId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d99",
      "module": "SP",
      "entity": "roles",
      "entityId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10",
      "action": "UPDATE",
      "changes": {
        "name": { "before": "Supervisor", "after": "Supervisor de zona" }
      },
      "correlationId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9daa",
      "ipAddress": "190.85.12.7",
      "userAgent": "Mozilla/5.0 …"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1483,
  "totalPages": 75,
  "totalIsExact": true
}
```

Decisiones del contrato:

- **`changes` viaja como objeto JSON, no como cadena.** La columna es `jsonb`; serializarla como texto obligaría al cliente a un segundo `parse` y a tratar como opaco algo que no lo es. Su forma la fija `architecture.md` §6.6.2 y **este endpoint no la interpreta**: en un `CREATE` es el estado inicial y en un `UPDATE` el diff con `before` y `after`, y la respuesta lo devuelve tal como se escribió. Es lo que satisface `CA-SP-082` y `CA-SP-083` sin código que distinga ambos casos.
- **`correlationId` e `ipAddress` son nulos a la vez o no lo son ninguno** (`FA-002`, `CA-SP-086`). No hace falta código que lo garantice: lo impone `ck_audit_change_log_origen` en el esquema (`architecture.md` §6.6.1). El endpoint se limita a no inventar valores por defecto y a **no omitir los campos** cuando son nulos, porque un campo ausente es indistinguible de uno que el cliente no conoce.
- **`actorId` nulo significa «lo hizo el sistema»**, no «se perdió el dato»: es lo que ocurre con las filas que emite `V7__seed_system_roles.sql` (Art. V.15).
- **No se devuelve el nombre del actor** ni el de la entidad afectada, por lo dicho en §3.
- **`userAgent` se devuelve tal cual.** Es texto provisto por el cliente y por tanto no confiable, pero eso es cierto también de la fila almacenada: aquí no se sanea nada que no se haya saneado al escribir. Lo único que se garantiza es que se serializa como dato, nunca interpolado en HTML por este servicio, que es de todos modos un problema del consumidor.
- **`totalIsExact` es nuevo en `PageResponse<T>`** y se desarrolla abajo.

### El conteo acotado

Es la decisión central de este plan y la que heredan los otros tres registros.

El conteo exacto de `RF-SP-002` —`COUNT(*)` sobre el conjunto filtrado— es correcto sobre decenas de filas e insostenible sobre millones: obliga a recorrer todas las filas que cumplen el predicado, aunque solo se devuelvan veinte. Con la tabla vacía no se nota; con dos años de operación, una consulta sin filtros tarda segundos y los tarda **en cada página**.

La sentencia de conteo pasa a ser:

```sql
SELECT count(*) FROM (
    SELECT 1 FROM audit_change_log
     WHERE <mismo predicado que los datos>
     LIMIT 10001
) t;
```

| Resultado | `totalElements` | `totalIsExact` | Qué sabe el cliente |
|---|---|---|---|
| ≤ 10 000 | El número real | `true` | El total exacto |
| 10 001 | `10000` | `false` | «Hay más de diez mil»; siga filtrando o paginando |

Cuatro consecuencias que conviene dejar escritas:

- **El coste queda acotado por construcción.** La sentencia nunca examina más de 10 001 filas, tenga la tabla mil o cien millones. Es la propiedad que se buscaba: el tiempo de respuesta deja de depender del tamaño del registro.
- **La inmensa mayoría de las consultas siguen dando el número real.** Quien audita llega con un filtro —un registro, una persona, una petición, un mes—, y ese conjunto rara vez pasa de diez mil eventos. El techo lo toca sobre todo el listado sin filtros, que es precisamente donde el total exacto menos informa.
- **`totalPages` es una cota inferior cuando `totalIsExact` es `false`.** Se calcula igual, sobre el total devuelto. Y **pedir una página más allá de esa cota sigue funcionando**: devuelve contenido si lo hay, y colección vacía si no. Es lo que impide que el techo se convierta en un muro.
- **El campo se añade al `PageResponse<T>` compartido, no a un DTO propio de auditoría.** Con un tipo aparte, cada consumidor tendría que distinguir dos formas de página, y el día que `roles` crezca habría que migrar su contrato. Añadido al compartido, los listados que cuentan de forma exacta devuelven `totalIsExact: true` siempre y ningún cliente existente se rompe: añadir un campo a una respuesta nunca lo hace. El impacto sobre `RF-SP-002` está en §8.

**El techo es configuración, no constante.** `nexus.pagination.count-limit`, junto a `default-size` y `max-size` (`RF-SP-002` §8). Un despliegue con poco volumen puede subirlo y recuperar el total exacto siempre; uno grande puede bajarlo. Lo que no puede es desaparecer, porque entonces vuelve el recorrido completo.

**El conteo y los datos usan la misma función de predicado**, igual que en `RF-SP-002` §4: un conteo que aplique un filtro distinto al de los datos produce un total que no corresponde a lo devuelto, y es un fallo que ninguna prueba de la página detecta.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | `from` posterior a `to` (`EX-001`) | `VAL-001` | `from` |
| `400` | `page` negativa o `size` fuera de `[1, 100]` (`EX-002`) | `VAL-002` | `page` o `size` |
| `400` | `action` fuera de su dominio | `VAL-003` | `action` |
| `400` | Un UUID o un instante malformado | `VAL-003` | El parámetro afectado |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `audit:read-changes` | `AUTH-002` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **Los cuatro `400` se evalúan juntos y se devuelven juntos** en `errors`, como en `RF-SP-002` §4: son independientes entre sí.
- **No hay `404` ni `422`.** Un filtro sin coincidencias devuelve `200` con la colección vacía (`FA-001`), y una página más allá de la última hace lo mismo. Un `entityId` o un `actorId` que no existan tampoco son error: la auditoría conserva eventos de registros que ya no existen (`spec.md` §13), de modo que exigir que la referencia exista rompería justo la consulta que da sentido al registro.
- Los `type` que este endpoint usa ya los estrenó `RF-SP-001`. El formato es el de `architecture.md` §7.3, con `correlationId` siempre presente. **Ese `correlationId` es el de la petición de consulta**, no el del filtro; comparten nombre y no significado, y conviene tenerlo presente al leer una respuesta de error de este endpoint.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/audit/changes` | `audit:read-changes` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`).
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **Los cuatro registros se leen con cuatro permisos distintos**, y este endpoint exige exactamente uno. `security.md` §4.4 lo justifica: quién editó un rol es información de operación y quién intentó entrar y falló es información de seguridad; un único `audit:read` obligaría a conceder la segunda para dar la primera. `CA-SP-088` verifica precisamente eso: un actor con `audit:read-errors` y `audit:read-security` recibe `403` aquí. Compartir `AuditController` con los otros tres registros no lo relaja, porque el permiso se declara por método.
- **No hay filtrado por alcance de datos.** Quien tiene el permiso ve todos los eventos, de todos los módulos. Es coherente con el propósito: una auditoría que oculta parte de lo ocurrido a quien está autorizado a auditar no sirve como evidencia.
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso, no un techo de privilegios.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6).

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita.** Ver abajo |
| Rechazo `400` | — | **No se audita**: son validaciones de formato (`architecture.md` §6.6.4) |
| Denegación `403` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'audit_change_log'`, `operation = 'GET /api/v1/audit/changes'`, `error_code = 'ERR-500'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera el estado (`spec.md` §7) |
| — | `audit_deletion_log` | No aplica |

- **Leer la auditoría de cambios no genera evento propio** (`spec.md` §14, pregunta 3). Toda consulta deja rastro en `request_log` con su actor, su `correlation_id` y su IP, y eso basta para saber quién miró qué. `RF-SP-014` será la excepción: ahí el acto de mirar es en sí mismo información de seguridad, y aquella consulta sí emitirá su evento. **Conviene no copiar esta sección hacia allá.**
- **Este endpoint no escribe en las tablas que lee**, y es la única garantía que hace falta para que la inmutabilidad del registro (`spec.md` §4.2) no dependa de la disciplina: no existe camino de escritura porque no existe método que lo ofrezca. `CA-SP-076` de `RF-SP-010` verificó lo mismo para el catálogo de permisos; aquí se verifica en §11.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Consulta de datos y conteo acotado | **Una sola**, `@Transactional(readOnly = true)` sobre `ListChangeAuditService` (`development-guide.md` §10) |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` tiene aquí un valor que excede lo habitual: marca la transacción como de solo lectura en PostgreSQL, de modo que **ningún defecto de programación puede escribir en una tabla de auditoría desde este camino**. En una tabla cuya credibilidad depende de ser inmutable, esa garantía vale más que el ahorro de no registrar entidades para comprobación de cambios.

El desfase entre el conteo y los datos bajo escrituras concurrentes que `RF-SP-002` §7 aceptó existe también aquí, y es **más probable**: esta tabla recibe escrituras constantemente, de modo que entre la sentencia de datos y la de conteo pueden entrar eventos nuevos. Se acepta por la misma razón y con un argumento adicional: sobre un registro cronológico ordenado del más reciente al más antiguo, lo que entra durante la consulta entra por el principio y no altera las páginas ya recorridas.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `shared/api` | `PageResponse<T>` gana `totalIsExact`. **Lo devuelven todos los listados del sistema**, presentes y futuros: `RF-SP-002` con valor `true` constante, porque su conteo es exacto. Añadir un campo no rompe a ningún cliente, pero es un cambio en el contrato compartido y por eso se declara aquí en lugar de en un DTO propio |
| `shared/api` | `BoundedCount` nace aquí y lo usan `RF-SP-012` a `RF-SP-014`. `RF-SP-002` puede adoptarlo el día que `roles` deje de medirse en decenas; hoy no lo necesita |
| `shared/config` | Declara `nexus.pagination.count-limit`, junto a `default-size` y `max-size` |
| `SP` (`RF-SP-012` a `RF-SP-014`) | Heredan el conteo acotado, el orden fijo por `occurred_at DESC, id DESC`, el rango semiabierto, los instantes con zona y el reparto de componentes. **Cada uno debe añadir su propio índice de línea de tiempo** sobre su tabla: `V9` solo cubre `audit_change_log`. Y `RF-SP-014` **no** debe heredar §6: su consulta sí emite evento de seguridad |
| `shared/audit` | Sin cambios. Este requerimiento solo lee lo que aquel escribe |
| `USR` | Ninguna dependencia. El actor se devuelve como identificador y no se resuelve su nombre (§3). Si algún requerimiento posterior lo pide, será un puerto con resolución por lotes —una llamada por página— y con una decisión propia sobre qué mostrar cuando `USR` no responda, como la que tomó `RF-SP-003` §4 |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Conteo exacto siempre, como en `RF-SP-002` | Correcto sobre decenas de filas e insostenible sobre millones: cada página paga un recorrido de todo el conjunto filtrado. El propio plan de `RF-SP-002` §4 dejó anotado que no debía heredarse aquí |
| Paginación por cursor, sin totales | Es la forma natural de recorrer un registro cronológico y no degrada en páginas altas, pero elimina `totalElements`, `totalPages` y el salto a una página arbitraria, que `spec.md` §6.2 exige. Habría obligado a devolver las cuatro especificaciones de auditoría a su compuerta |
| Estimación del planificador (`reltuples`, `EXPLAIN`) para el listado sin filtros | Barata siempre, pero el total puede desviarse mucho del real y el cliente no tiene forma de saber cuánto. El conteo acotado miente **de una sola manera y declarada**: por debajo del techo es exacto, por encima dice que lo es |
| Techo constante en el código en vez de configuración | Un despliegue pequeño no podría recuperar el total exacto y uno grande no podría bajarlo, para ahorrar un valor en un fichero |
| Un `totalIsExact` solo en un DTO de auditoría, sin tocar `PageResponse<T>` | Dos formas de página que cada consumidor debe distinguir, y una migración de contrato el día que `roles` crezca |
| Exigir al menos un filtro, o un rango de fechas obligatorio | `spec.md` §14, pregunta 2, lo resolvió: obligaría a trocear la consulta que más valor tiene, la línea de tiempo completa de un registro. El conteo acotado resuelve el coste sin recortar lo que se puede preguntar |
| Ofrecer `sort` con lista blanca, como `RF-SP-002` | El orden cronológico es parte del significado de este recurso. Poder ordenarlo por módulo o por entidad respondería otra pregunta, y añadiría la superficie de validación de una lista blanca que aquí no hace falta |
| Aceptar `from` y `to` como fechas sin zona | El servidor tendría que elegir una zona para interpretarlas y elegiría la suya. Un auditor en otra zona pediría «el día 1» y recibiría desde las 19:00 del día anterior, sin ningún aviso |
| Rango con ambos extremos inclusivos | Dos rangos consecutivos devolverían dos veces el evento que caiga exactamente en el límite, y quien recorra la línea de tiempo mes a mes contaría de más |
| Un índice por cada filtro de `spec.md` §6.1 | `module` y `action` tienen cardinalidad demasiado baja para que un índice acote nada, y cada índice de esta tabla se paga en **cada escritura de negocio del sistema**, porque el evento se emite en la misma transacción |
| Serializar `changes` como cadena | Obliga al cliente a un segundo `parse` y trata como opaco un `jsonb` que no lo es |
| Interpretar `changes` en el servidor y devolver una forma unificada para `CREATE` y `UPDATE` | Añade código que puede divergir de lo que se escribió, sobre el único registro cuyo valor es reproducir exactamente lo ocurrido. `architecture.md` §6.6.2 ya fija las dos formas |
| Resolver el nombre del actor consultando `USR` | `SP` no puede leer sus tablas (`architecture.md` §5.3) y haría falta un puerto con resolución por lotes. `spec.md` §6.2 no lo pide, y el valor probatorio está en el identificador, que no cambia; un nombre es una foto del momento de la consulta, no del evento |
| Exponer `v_audit_timeline` como endpoint de este requerimiento | `spec.md` §14, pregunta 1: la vista existe para el diagnóstico directo sobre la base y exige los cuatro permisos. Como consulta de API es un requerimiento propio, no una opción de este |
| Ofrecer exportación del resultado | `spec.md` §14, pregunta 4: exportar tiene reglas propias —formato, tamaño, retención del fichero y quién puede llevarse un volcado de la auditoría fuera del sistema— y no cabe como opción de esta consulta |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La paginación profunda degrada: `OFFSET 100000` obliga a PostgreSQL a leer y descartar cien mil filas | Medio | El índice de `V9` la sostiene mucho mejor que un recorrido secuencial, pero el coste sigue creciendo con la página. Se acepta: quien llega a la página cinco mil está usando la herramienta mal, y la respuesta es filtrar. Si se volviera un uso real, la corrección es paginación por cursor **como modo adicional**, sin retirar el actual, y es un requerimiento propio |
| `totalIsExact: false` se ignora en el cliente y la pantalla muestra «10 000 resultados» como si fuera el total | Medio | El nombre del campo dice lo que significa y no hay valor por defecto que lo esconda. **Debe acordarse con el frontend** antes de implementar: la etiqueta correcta es «más de 10 000», no «10 000» |
| Un índice más sobre `audit_change_log` encarece **toda** escritura de negocio del sistema | Medio | Es el motivo por el que solo se añade uno y por el que §2 rechaza los demás. Cualquier índice futuro sobre esta tabla debe justificar su coste con una consulta lenta medida, no con una suposición |
| Hasta que exista `USR`, la pantalla muestra el UUID del actor en lugar de su nombre | Bajo | Consecuencia aceptada de no crear un puerto que `spec.md` no pide (§3). El identificador es el dato probatorio; el nombre es presentación y llegará con el requerimiento que lo pida |
| El registro crece sin purga y no hay política de retención en este alcance | Medio | `architecture.md` §9 la contempla; este requerimiento no la implementa ni debe hacerlo. Se anota porque el conteo acotado mitiga el síntoma de consulta, **no** el crecimiento del almacenamiento |
| Un dato sensible llega a `changes` y esta consulta lo publica | **Alto** | El enmascaramiento es responsabilidad de `shared/audit` **al escribir** (Art. XV.5), y esta consulta devuelve lo almacenado sin reinterpretarlo. Añadir aquí una segunda lista de campos a ocultar daría falsa confianza: dos listas divergen, y la de lectura no protegería a quien consulte la base directamente. `CA-SP-087` se verifica sobre el camino de escritura (§11) |
| El techo de conteo se configura en un valor muy alto y desaparece la protección | Bajo | Documentado en §4. Un techo de un millón devuelve el problema que el mecanismo evita |

## 11. Estrategia de prueba

Niveles: **Unitaria** (sin Spring ni base de datos), **Integración** (Testcontainers sobre PostgreSQL real, con `V9` aplicada) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel de dominio: este requerimiento no toca `domain`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-081` | Integración + API | Con más eventos que el tamaño de página, la respuesta trae `size` elementos ordenados de más reciente a más antiguo; la segunda página no repite ni omite ninguno |
| `CA-SP-082` | Integración + API | Tras una edición real de `RF-SP-004`, `changes` contiene **solo** los campos modificados, cada uno con `before` y `after` |
| `CA-SP-083` | Integración + API | Tras un alta real de `RF-SP-001`, `changes` contiene el estado inicial completo, sin `before` ni `after` |
| `CA-SP-084` | Integración + API | Cada filtro por separado y todos combinados devuelven solo las filas que cumplen |
| `CA-SP-085` | Integración + API | Una operación que emite varios eventos se recupera entera filtrando por su `correlationId` |
| `CA-SP-086` | Integración + API | Un evento emitido por migración devuelve `correlationId` e `ipAddress` **ambos nulos**, con los campos presentes y no omitidos; no existe fila con uno solo de los dos |
| `CA-SP-087` | Integración | Se ejecuta una escritura real con un campo enmascarado y se comprueba que ni la fila de `audit_change_log` ni la respuesta de este endpoint lo contienen. Se verifica **sobre el camino de escritura**, que es donde vive la garantía |
| `CA-SP-088` | API | Un actor con `audit:read-errors` y `audit:read-security`, pero sin `audit:read-changes`, recibe `403`, no obtiene evento alguno y queda la denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Conteo por debajo del techo | Integración | Con 50 eventos y el techo configurado en 100, `totalElements` vale 50 y `totalIsExact` es `true` |
| Conteo por encima del techo | Integración | Con el techo configurado en 10 y 25 eventos, `totalElements` vale 10 y `totalIsExact` es `false`. Es la prueba que hace verificable el mecanismo sin insertar diez mil filas |
| Navegar más allá de la cota inferior | Integración | Con el techo en 10 y 25 eventos, la página 2 devuelve contenido real: el techo no es un muro |
| El conteo no examina más filas que el techo | Integración | El `EXPLAIN (ANALYZE)` de la sentencia de conteo muestra que el nodo de límite corta en `count-limit + 1` |
| Predicado compartido | Integración | Datos y conteo aplican el mismo filtro: con un filtro que deja 3 eventos, `totalElements` vale 3 y no el total de la tabla |
| Registro eliminado | Integración | Los eventos de un rol borrado con `RF-SP-009` siguen consultándose por su `entityId`. Es la razón de ser del registro |
| Rango de fechas inverso | API | `from` posterior a `to` devuelve `400` con `VAL-001`, y **no** una colección vacía |
| Rango semiabierto | Integración | Un evento exactamente en el instante `to` **no** aparece; el mismo evento con ese instante como `from` **sí**. Dos rangos consecutivos no lo devuelven dos veces |
| Fecha sin zona horaria | API | `from=2026-08-01` devuelve `400`, no se interpreta con la zona del servidor |
| Empate en `occurred_at` | Integración | Dos eventos con el mismo instante aparecen en orden estable entre dos llamadas, y recorrer todas las páginas devuelve cada uno exactamente una vez |
| Uso efectivo del índice | Integración | El `EXPLAIN` del listado sin filtros muestra el recorrido de `ix_audit_change_log_occurred_at`, no un ordenamiento de la tabla. Sin esta comprobación, el índice puede dejar de usarse por un cambio en el predicado sin que ninguna prueba funcional lo note |
| Número de sentencias por petición | Integración | **Dos** como máximo —datos y conteo—, con independencia del número de eventos, y ninguna consulta adicional por fila |
| Ausencia de escritura | API | `POST`, `PUT`, `PATCH` y `DELETE` sobre `/api/v1/audit/changes` devuelven `405`. Es lo que hace verificable que el registro es inmutable por API (`spec.md` §4.2) |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento. No se añade ninguna nueva: no toca `domain` y no introduce dependencias entre módulos.
