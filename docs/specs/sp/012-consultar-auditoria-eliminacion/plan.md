# PLAN — `RF-SP-012` Consultar auditoría de eliminación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-012` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento —flujos, filtros y criterios de aceptación— es el de [`spec.md`](spec.md) y no se repite aquí. La mecánica común a los cuatro registros de auditoría —conteo acotado, orden fijo, rango semiabierto, instantes con zona— la fijó el plan de [`RF-SP-011`](../011-consultar-auditoria-cambios/plan.md) y **este documento la hereda sin repetirla**. Aquí se decide solo lo que distingue a este registro: la búsqueda por texto sobre el motivo y la verificación de que ninguna eliminación puede ocurrir sin dejar evento.

---

## 1. Enfoque

Estructuralmente es `RF-SP-011` con otros filtros y otras dos columnas: `reason` y `snapshot`. Se hereda todo su §4 y §7: una sentencia de proyección, orden `occurred_at DESC, id DESC`, conteo acotado con `BoundedCount`, rango semiabierto sobre instantes con zona, `readOnly = true` y sin `domain`.

Tres cosas son propias de este requerimiento:

1. **La búsqueda por texto sobre el motivo** (`CA-SP-166`), que `spec.md` §14 añadió al aprobarse. Es la pregunta con la que empieza cualquier auditoría real —«enséñame todo lo que se borró alegando tal cosa»— y sobre una tabla que crece sin límite no puede resolverse con un recorrido secuencial. Exige un índice de trigramas, y ese índice puede ser **parcial** por una razón que este registro tiene y ningún otro: una parte grande de sus filas no tiene motivo por diseño.
2. **La verificación de que el esquema no admite ninguna cascada.** `spec.md` §14, pregunta 2, delegó en este documento comprobar que no existe `ON DELETE CASCADE` en ninguna relación. No es una formalidad: una cascada declarada en la base borra filas sin pasar por la aplicación y por tanto **sin emitir evento**, y eso no deja un hueco en el registro, deja un hueco invisible. Se verifica, y se convierte en prueba automática para que ninguna migración futura lo reintroduzca.
3. **El motivo vacío es un valor legítimo**, no un dato faltante (`FA-001`, `CA-SP-091`). Lo garantiza el esquema, no el código.

## 2. Cambios de esquema

**Migración:** `V10__create_audit_deletion_log_indexes.sql`

La tabla `audit_deletion_log`, su `ck_deletion_reason` y sus cuatro índices mínimos los crea `V4__create_audit_logs.sql` (`RF-SP-001`). Esta migración **no cambia columnas ni restricciones**: añade los dos accesos que faltan.

| Tabla | Cambio | Detalle |
|---|---|---|
| `audit_deletion_log` | Altera (índice) | `ix_audit_deletion_log_occurred_at` sobre `(occurred_at DESC, id DESC)` |
| `audit_deletion_log` | Altera (índice) | `ix_audit_deletion_log_reason_busqueda`, GIN de trigramas sobre `f_unaccent(lower(reason))`, **parcial** |

```sql
CREATE INDEX ix_audit_deletion_log_occurred_at
    ON audit_deletion_log (occurred_at DESC, id DESC);

CREATE INDEX ix_audit_deletion_log_reason_busqueda
    ON audit_deletion_log USING gin (f_unaccent(lower(reason)) gin_trgm_ops)
 WHERE deletion_type <> 'ASSOCIATION';
```

**El índice de línea de tiempo** es el mismo caso que en `RF-SP-011` §2 y por las mismas razones: ninguno de los cuatro índices de `architecture.md` §6.6.6 sirve al listado sin filtros ordenado por fecha, y el desempate por `id` es gratis y cronológico por ser UUID v7. No se repite el argumento.

**Por qué el índice de búsqueda es parcial, y por qué eso importa aquí más que en otras tablas.** `ck_deletion_reason` (`architecture.md` §6.6.3) exige motivo con contenido salvo cuando `deletion_type = 'ASSOCIATION'`, de modo que las filas de asociación son exactamente las que **nunca pueden coincidir con una búsqueda por motivo**. Excluirlas del índice no pierde ni un resultado.

Y no son pocas: `RF-SP-006` emite **una fila de asociación por cada permiso retirado**, mientras que una eliminación de negocio emite una sola por rol. En un sistema donde se ajustan permisos con regularidad, las asociaciones serán con holgura el tipo de evento más numeroso de esta tabla. El índice parcial deja fuera la mayor parte del volumen y se queda solo con las filas sobre las que la pregunta tiene sentido.

**Por qué aquí sí se crea un índice de trigramas y en `audit_change_log` no se creó ninguno adicional.** El argumento de `RF-SP-011` §2 sigue vigente: cada índice de una tabla de auditoría se paga en la transacción de negocio que emite el evento. La diferencia es la frecuencia. `audit_change_log` recibe una fila por **cada** alta y edición del sistema; `audit_deletion_log` recibe una por cada borrado, que es una operación mucho más rara y además deliberada. Pagar un índice GIN en cada eliminación es asumible; pagarlo en cada escritura no lo sería. Y sin él, `CA-SP-166` se resuelve con un recorrido completo de la tabla en cada búsqueda, que es justo lo que este plan existe para evitar.

**La búsqueda usa `f_unaccent`, creada en `V1__create_shared_functions.sql`** (`RF-SP-010`). Es su tercer consumidor, después del catálogo de permisos y de `ix_roles_busqueda`. Quien modifique el diccionario `unaccent` debe reindexar también este índice.

### Verificación exigida por `spec.md` §14

**Ninguna relación del esquema declara `ON DELETE CASCADE`.** Comprobado sobre las migraciones vigentes:

| Clave foránea | Declarada en | Acción |
|---|---|---|
| `fk_roles_parent` | `V5__create_roles.sql` | `ON DELETE RESTRICT` |
| `fk_role_permissions_roles` | `V6__create_role_permissions.sql` | `ON DELETE RESTRICT` |
| `fk_role_permissions_permissions` | `V6__create_role_permissions.sql` | `ON DELETE RESTRICT` |

Las tablas de auditoría no tienen clave foránea alguna: `actor_id` no referencia a `users` de forma deliberada (`RF-SP-001` §2).

La comprobación manual no basta, porque la garantía tiene que sobrevivir a las migraciones que aún no existen. Se convierte en **prueba automática** (§11): una consulta sobre `pg_constraint` que falla si aparece cualquier clave foránea con acción de borrado en cascada o de puesta a nulo. Es el equivalente, para el esquema, de lo que ArchUnit hace con las capas: una regla que se verifica sola en lugar de depender de que alguien la recuerde en cada revisión.

## 3. Componentes afectados

Paquete raíz del módulo: `com.factech.nexus.modules.system`. El reparto es el de `RF-SP-011` §3, con los nombres de este registro.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: la única regla aplicable, `RN-SP-005`, gobierna cómo se **escribe** el evento en `RF-SP-006`, no cómo se lee |
| `application` | `ListDeletionAuditService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)` |
| `application` | `DeletionAuditQuery` | Nuevo | Criterios ya validados y normalizados. Sin tipos de HTTP |
| `application` | `DeletionAuditItem` | Nuevo | Modelo de lectura |
| `application` | `DeletionAuditQueryRepository` | Nuevo | Puerto de consulta |
| `application` | `DeletionType` | Nuevo | Enum cerrado `LOGICAL`, `PHYSICAL`, `ASSOCIATION`. Lista blanca del filtro |
| `infrastructure` | `JpaDeletionAuditQueryRepository` | Nuevo | Adaptador. Predicado, proyección y conteo acotado |
| `infrastructure` | `AuditDeletionLogEntity` | Nuevo | Mapeo JPA. Solo como metamodelo; la consulta no lo instancia |
| `api` | `AuditController` | Modificado | Añade `GET /api/v1/audit/deletions` con **su** permiso |
| `api` | `ListDeletionAuditRequest` | Nuevo | Parámetros de consulta con Bean Validation (`VAL-001` a `VAL-003`) |
| `api` | `DeletionAuditItemResponse` | Nuevo | DTO de salida de cada fila |
| `shared/api` | `PageResponse<T>`, `BoundedCount` | Sin cambios | Definidos en `RF-SP-011`. Se reutilizan tal cual |

**`DeletionType` es un enum propio y no se comparte con nada.** Sus tres valores son los del `CHECK` del esquema y solo este registro los usa; ponerlo en un lugar compartido sugeriría que hay otro consumidor.

**No se reutiliza `ChangeAuditItem`.** Comparten el núcleo común de `architecture.md` §6.6.1 —momento, actor, origen, correlación— y difieren en todo lo demás: uno tiene `action` y `changes`, el otro `deletion_type`, `reason` y `snapshot`. Se estudió un tipo base con los campos comunes: se descarta porque la herencia entre modelos de lectura obliga a que los cuatro registros evolucionen juntos, y su único parentesco real es que comparten seis columnas por decisión de esquema, no por ser la misma cosa. Cuatro tipos planos de una decena de campos cada uno son más fáciles de leer y de cambiar por separado.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/audit/deletions` | Listado paginado de eliminaciones, con su motivo y el estado del registro eliminado |

**Petición**

```
GET /api/v1/audit/deletions?page=0&size=20
                           &module=SP
                           &entity=roles
                           &entityId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d10
                           &actorId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9d99
                           &deletionType=LOGICAL
                           &reason=duplicado
                           &from=2026-08-01T00:00:00Z
                           &to=2026-09-01T00:00:00Z
                           &correlationId=018f3a2b-7c41-7000-9a3d-1f2e5b8c9daa
```

| Parámetro | Tipo | Por defecto | Notas |
|---|---|---|---|
| `page`, `size` | entero | `0`, `20` | Igual que en `RF-SP-011` §4 |
| `module`, `entity` | texto | — | Coincidencia exacta |
| `entityId`, `actorId` | UUID | — | No se valida que existan |
| `deletionType` | enum | — | `LOGICAL`, `PHYSICAL` o `ASSOCIATION`. Otro → `VAL-003` |
| `reason` | texto | — | **Búsqueda por contención** sobre el motivo, insensible a mayúsculas y acentos |
| `from` / `to` | instante ISO-8601 | — | Rango semiabierto `[from, to)`. `from` posterior a `to` → `VAL-001` |
| `correlationId` | UUID | — | Todas las eliminaciones de una misma petición (`CA-SP-177`) |

- **`reason` es el único filtro por contención**; los demás son de igualdad. Es la asimetría que `spec.md` §6.1 pide: el motivo es texto libre y buscarlo por igualdad no serviría de nada.
- **`correlationId` se añadió a la especificación el 21-08-2026**, al aprobar este plan. `spec.md` §6.1 y §6.2 no lo declaraban, y sin él este registro quedaba fuera del cruce entre los cuatro —que es precisamente el mecanismo con el que `RF-SP-011` §14 descartó exponer `v_audit_timeline` como endpoint— (Art. I.7).
- **No hay `sort`**, por lo dicho en `RF-SP-011` §4: el orden cronológico es parte del significado del recurso.

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
      "deletionType": "LOGICAL",
      "reason": "Rol duplicado tras la fusión de las áreas de cobranza.",
      "snapshot": {
        "code": "COBRANZA_2",
        "name": "Cobranza secundaria",
        "roleType": "FUNCIONARIO",
        "parentRoleId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01",
        "status": "ACTIVO",
        "permissions": ["roles:read", "audit:read-changes"]
      },
      "correlationId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9daa",
      "ipAddress": "190.85.12.7",
      "userAgent": "Mozilla/5.0 …"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 37,
  "totalPages": 2,
  "totalIsExact": true
}
```

- **`snapshot` viaja como objeto JSON y este endpoint no lo interpreta.** Su forma la decide quien escribe el evento y varía por entidad: en un rol es el estado completo con sus permisos declarados (`RF-SP-009` §6), en una asociación son los dos extremos con sus **códigos legibles** (`RF-SP-006` §6). Devolverlo tal cual es lo que satisface `CA-SP-093`: reconstruir qué era el registro eliminado no es trabajo del lector, es una propiedad de lo que se escribió.
- **`reason` es nulo en las asociaciones y no se omite** (`FA-001`, `CA-SP-091`). Que sea un valor legítimo y no un dato faltante lo garantiza el esquema: `ck_deletion_reason` impide que una eliminación de negocio se registre sin motivo, de modo que un nulo aquí **solo puede** venir de una asociación. No hace falta código que lo compruebe ni un campo aparte que lo declare, y es lo que hace verificable a `CA-SP-090` sin recorrer la tabla.
- **`correlationId` e `ipAddress` son nulos a la vez o ninguno lo es**, por `ck_audit_deletion_log_origen`. Igual que en `RF-SP-011` §4.
- ~~**No se devuelve el nombre del actor**~~ · **Revertido el 28-08-2026**, con el mismo criterio que `RF-SP-011`: llega resuelto y el identificador sigue mandando.

**Cómo se aplica la búsqueda por motivo.** El término se recorta; si queda vacío, no se añade predicado. Si no, se escapan `\`, `%` y `_`, se envía como parámetro enlazado y se normaliza en la base de datos con la misma función que alimenta el índice:

```sql
WHERE deletion_type <> 'ASSOCIATION'
  AND f_unaccent(lower(reason)) LIKE f_unaccent(lower(:termino)) ESCAPE '\'
```

**Esa primera condición no es un adorno y merece explicarse.** Cumple dos funciones a la vez. Es **semánticamente exacta**: una asociación no declara motivo, así que jamás puede coincidir con una búsqueda por motivo, y excluirla no descarta ningún resultado posible. Y es lo que **hace utilizable el índice parcial**: PostgreSQL solo emplea un índice con `WHERE` si el predicado de la consulta implica el del índice; sin esa condición explícita, el planificador no puede demostrarlo y recorre la tabla entera, con lo que el índice existiría sin servir para nada. Se añade siempre que el filtro de motivo esté presente, y solo entonces.

Consecuencia que hay que aceptar: combinar `deletionType=ASSOCIATION` con `reason=algo` devuelve la colección vacía. Es correcto —se está pidiendo asociaciones cuyo motivo diga algo, y las asociaciones no tienen motivo— y conviene que quede escrito para que no se lea como un defecto.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | `from` posterior a `to` (`EX-001`) | `VAL-001` | `from` |
| `400` | `page` negativa o `size` fuera de `[1, 100]` (`EX-002`) | `VAL-002` | `page` o `size` |
| `400` | `deletionType` fuera de su dominio | `VAL-003` | `deletionType` |
| `400` | Un UUID o un instante malformado | `VAL-003` | El parámetro afectado |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `audit:read-deletions` | `AUTH-002` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

Sin `404` ni `422`, por lo dicho en `RF-SP-011` §4: un filtro sin coincidencias devuelve `200` con la colección vacía (`FA-002`), y una referencia inexistente tampoco es error, porque la auditoría conserva eventos de registros que ya no existen. Aquí ese argumento es todavía más claro que allí: **el registro al que apunta `entityId` está borrado por definición**.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/audit/deletions` | `audit:read-deletions` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`).
- Se declara sobre el método del controlador (`security.md` §6). Compartir `AuditController` con los otros tres registros no relaja nada: el permiso se declara por método, y `CA-SP-095` verifica que un actor sin este permiso recibe `403` aunque tenga los otros tres.
- **`audit:read-changes` y `audit:read-deletions` se conceden juntos al perfil de auditor de negocio** (`security.md` §4.4), pero siguen siendo permisos distintos y este endpoint exige exactamente uno. Que la práctica los agrupe no es razón para fusionarlos: soporte técnico tiene `audit:read-errors` y no debe ver qué se borró ni por qué.
- **No hay filtrado por alcance de datos**, y aquí conviene decirlo con más énfasis que en `RF-SP-011`: este registro es la única fuente que responde por qué desapareció algo. Ocultar parte a quien está autorizado a auditar lo inutilizaría como evidencia.
- La resolución del permiso puede usar la caché de `security.md` §4.5: solo se decide acceso.

## 6. Auditoría

Idéntica a `RF-SP-011` §6, que no se repite: la consulta exitosa **no se audita** (`spec.md` §14 de `RF-SP-011`, pregunta 3), los `400` tampoco, el `403` lo registra la capa de seguridad en `audit_security_log` y un `5xx` va a `audit_error_log` con `resource = 'audit_deletion_log'` y `operation = 'GET /api/v1/audit/deletions'`.

Dos precisiones propias:

- **Este endpoint no escribe en la tabla que lee**, que es lo que hace de la inmutabilidad una propiedad del código y no una promesa (`spec.md` §4.2). Se verifica en §11.
- **`RF-SP-014` no debe heredar esta sección.** Allí el acto de consultar es en sí mismo información de seguridad y su consulta sí emite evento propio.

## 7. Transaccionalidad

La de `RF-SP-011` §7, sin variaciones: una sola transacción `readOnly = true` para datos y conteo; `audit_error_log` y `audit_security_log` en transacción independiente con `REQUIRES_NEW`; `request_log` fuera de toda transacción.

`readOnly = true` cumple aquí la misma función de garantía que allí, y con más motivo: es la barrera que impide que un defecto escriba en el registro que documenta lo que desapareció.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `shared/api` | Ninguno. `PageResponse<T>` y `BoundedCount` los creó `RF-SP-011` y aquí se reutilizan |
| `shared/persistence` | `f_unaccent` gana un tercer consumidor. Quien modifique el diccionario `unaccent` debe reindexar también `ix_audit_deletion_log_reason_busqueda` |
| `SP` (`RF-SP-013`, `RF-SP-014`) | Heredan lo mismo que este de `RF-SP-011`, y **cada uno debe añadir su propio índice de línea de tiempo**: `V10` solo cubre `audit_deletion_log` |
| `SP` (todo el esquema) | Queda establecido que **ninguna migración puede declarar `ON DELETE CASCADE` ni `ON DELETE SET NULL`**, y hay una prueba que lo verifica (§2, §11). No es una preferencia de estilo: una cascada borra sin pasar por la aplicación y por tanto sin emitir evento |
| Usuarios (`RF-SP-024` a `RF-SP-033`) | Sus claves foráneas quedan sujetas a la misma restricción, y la prueba la aplica también a sus tablas por recorrer el esquema completo. `user_roles` es el caso a vigilar: una cascada desde `users` o desde `roles` borraría asignaciones sin dejar evento |
| `RF-SP-006` y `RF-SP-009` | Son los dos únicos que escriben hoy en esta tabla. Lo que devuelva `snapshot` depende de lo que ellos guarden: si `RF-SP-006` omitiera los códigos legibles, `CA-SP-093` fallaría aquí sin que este requerimiento tuviera nada que corregir |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Índice de trigramas **total** sobre `reason` | Indexaría las filas de asociación, que son las más numerosas de esta tabla y las únicas que jamás pueden coincidir con una búsqueda por motivo. Coste de escritura y de almacenamiento a cambio de nada |
| Sin índice de búsqueda, resolviendo `CA-SP-166` con un recorrido secuencial | Funciona con la tabla vacía y deja de funcionar exactamente cuando el registro empieza a tener valor. La búsqueda por motivo es la pregunta con la que empieza una auditoría real |
| Índice sobre `deletion_type` o `module` | Misma objeción que en `RF-SP-011` §2: cardinalidad demasiado baja para acotar nada, y coste en cada eliminación |
| Un tipo base compartido por los cuatro modelos de lectura de auditoría | Su único parentesco es compartir seis columnas por decisión de esquema, no ser la misma cosa. La herencia obligaría a que los cuatro registros evolucionaran juntos |
| Interpretar `snapshot` en el servidor y devolver una forma unificada | Su forma varía por entidad y por tipo de eliminación, y el valor del registro está en reproducir exactamente lo que se guardó. Interpretarlo añade código que puede divergir de lo escrito |
| Un campo aparte que declare «este evento no lleva motivo porque es una asociación» | Redundante: `deletionType` ya lo dice, y `ck_deletion_reason` lo garantiza en el esquema. Un campo derivado que puede desincronizarse del que lo determina |
| Exigir que la búsqueda por motivo devuelva también asociaciones | No hay nada que buscar en ellas. Incluirlas obligaría a renunciar al índice parcial para no encontrar ni un resultado más |
| Tipificar el motivo con un catálogo de códigos (D-20) | `spec.md` §14, pregunta 3: obligaría a prever hoy las razones por las que algo se borrará dentro de dos años, y lo previsible es que casi todo acabe bajo «Otro» |
| Ofrecer restauración de lo eliminado desde este registro | `spec.md` §4.2: la auditoría es un registro, no una papelera. `RF-SP-009` §9 ya descartó la restauración con sus propios argumentos |
| Confiar en la revisión manual para impedir un `ON DELETE CASCADE` futuro | Es exactamente el tipo de garantía que se pierde en una prisa. La consulta sobre `pg_constraint` cuesta una prueba y no se olvida |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Una migración futura declara `ON DELETE CASCADE` y abre un camino de borrado sin evento | **Alto** | Prueba automática sobre `pg_constraint` (§2, §11). El fallo aparece en CI al introducir la migración, no meses después al buscar un evento que nunca se escribió |
| El índice parcial no se usa porque la consulta omite `deletion_type <> 'ASSOCIATION'` | Medio | La condición se añade siempre que el filtro de motivo esté presente, y §11 lo verifica con `EXPLAIN`. Sin esa comprobación, el índice existe, la búsqueda funciona y nadie nota que recorre la tabla entera |
| `snapshot` voluminoso: un registro con muchos campos genera un evento grande (`spec.md` §13) | Medio | El tamaño de la respuesta lo acota la paginación, no el del `snapshot` individual. Lo que sí crece sin cota es el almacenamiento del registro, y eso corresponde a la política de retención de `architecture.md` §9, fuera de este alcance. Se anota porque la medición hay que hacerla |
| Un dato sensible llega a `snapshot` y esta consulta lo publica | **Alto** | El enmascaramiento es responsabilidad de `shared/audit` **al escribir** (Art. XV.5); `architecture.md` §6.6.3 lo declara de forma explícita para `snapshot`. No se re-enmascara al leer, por el mismo argumento de `RF-SP-011` §10: dos listas divergen y la de lectura no protege a quien consulte la base directamente. `CA-SP-094` se verifica sobre el camino de escritura |
| La búsqueda por motivo con un término de menos de tres caracteres no puede usar el índice de trigramas | Bajo | Degrada a recorrido sobre el subconjunto no-asociación. Si se volviera un uso frecuente, la corrección es exigir una longitud mínima al término, y es un cambio en la validación, no en el esquema |
| La paginación profunda degrada | Medio | Heredado de `RF-SP-011` §10, con la misma respuesta: filtrar. Aquí el volumen es menor, porque las eliminaciones son mucho más raras que los cambios |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V10` aplicada) y **API**. Sin nivel de dominio: este requerimiento no toca `domain`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-089` | Integración + API | Las eliminaciones se devuelven paginadas, ordenadas de más reciente a más antigua, con `reason` y `snapshot` |
| `CA-SP-090` | Integración | Tras una eliminación real de `RF-SP-009`, el evento trae motivo con contenido. Se verifica además que `ck_deletion_reason` rechaza en base de datos una fila `LOGICAL` con motivo vacío o en blanco |
| `CA-SP-091` | Integración + API | Tras una revocación real de `RF-SP-006`, el evento trae `deletionType = ASSOCIATION` y `reason` nulo, con el campo presente y no omitido; la respuesta es `200` y no hay error alguno |
| `CA-SP-092` | Integración + API | Cada filtro por separado y todos combinados devuelven solo las filas que cumplen |
| `CA-SP-166` | Integración + API | Buscar `duplicado`, `DUPLICADO` y `fusion` encuentra los eventos cuyo motivo dice «duplicado» y «fusión». Exige PostgreSQL real: `unaccent` no es simulable |
| `CA-SP-093` | Integración | El `snapshot` de un rol eliminado contiene código, nombre, clasificación, padre, estado y permisos declarados, suficientes para reconstruir qué era. El de una asociación contiene los códigos legibles de rol y permiso, no solo los identificadores |
| `CA-SP-094` | Integración | Se ejecuta una eliminación real de una entidad con un campo enmascarado y se comprueba que ni la fila ni la respuesta lo contienen. Se verifica **sobre el camino de escritura** |
| `CA-SP-095` | API | Un actor con los otros tres permisos de auditoría, pero sin `audit:read-deletions`, recibe `403` y queda la denegación en `audit_security_log` |
| `CA-SP-177` | Integración + API | Una operación que elimina varias asociaciones en una sola petición se recupera entera filtrando por su `correlationId` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| **Ausencia de cascadas en todo el esquema** | Integración | Una consulta sobre `pg_constraint` no devuelve ninguna clave foránea con `confdeltype` en `c` (cascade) ni `n` (set null), en **ninguna** tabla. Es la prueba que exige `spec.md` §14, pregunta 2, y la que impide que una migración futura abra un camino de borrado sin evento |
| Uso efectivo del índice parcial | Integración | El `EXPLAIN` de una búsqueda por motivo muestra el recorrido de `ix_audit_deletion_log_reason_busqueda`. Sin esta comprobación, omitir la condición de tipo pasa inadvertido: la búsqueda devuelve lo correcto recorriendo toda la tabla |
| Uso efectivo del índice de línea de tiempo | Integración | El `EXPLAIN` del listado sin filtros muestra `ix_audit_deletion_log_occurred_at`, no un ordenamiento de la tabla |
| Asociación combinada con búsqueda por motivo | API | `deletionType=ASSOCIATION&reason=algo` devuelve `200` con colección vacía, no un error |
| Código reutilizado tras eliminar | Integración | Se elimina un rol, se crea otro con el mismo código (`RF-SP-001`, `CA-SP-006`) y el evento de eliminación sigue conservando el estado del **original** |
| Búsqueda con `%`, `_` y `\` | Integración | Se tratan como texto literal: un motivo con `100%` no convierte la búsqueda en un comodín |
| Búsqueda vacía o solo espacios | API | Equivale a no filtrar, y **no** añade la condición de tipo: las asociaciones siguen apareciendo |
| Rango semiabierto y fecha sin zona | API | Igual que en `RF-SP-011` §11: un evento en el instante `to` no aparece, y `from=2026-08-01` devuelve `400` |
| Conteo acotado | Integración | Con el techo configurado en 10 y 25 eventos, `totalElements` vale 10, `totalIsExact` es `false` y la página 2 sigue devolviendo contenido |
| Número de sentencias por petición | Integración | **Dos** como máximo —datos y conteo—, sin consultas adicionales por fila |
| Ausencia de escritura | API | `POST`, `PUT`, `PATCH` y `DELETE` sobre `/api/v1/audit/deletions` devuelven `405` |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento. La prueba de ausencia de cascadas es de esquema, no de arquitectura, y se ejecuta con las de integración porque necesita la base de datos real.
