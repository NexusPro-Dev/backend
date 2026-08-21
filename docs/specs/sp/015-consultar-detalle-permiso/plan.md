# PLAN — `RF-SP-015` Consultar detalle de un permiso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-015` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento es el de [`spec.md`](spec.md) y no se repite aquí. Es el requerimiento más pequeño del módulo: no crea esquema, no toca `domain` y reutiliza componentes que ya existen. Este documento decide lo único que queda abierto —qué campos devuelve el detalle y por qué no son más que los del listado— y cierra la pregunta que el plan de `RF-SP-010` §8 le dejó explícitamente.

---

## 1. Enfoque

Una sentencia de lectura por clave primaria sobre `permissions`, sin `JOIN`, sin conteos y sin reglas de negocio. Estructuralmente es `RF-SP-003` sin nada de lo que allí lo hacía costoso: no hay padre que resolver, no hay colección que traer aparte, no hay subconsultas de conteo y no hay borrado lógico que filtrar, porque `permissions` no lo tiene (`RF-SP-010` §2).

Todo el trabajo ya está hecho por otros dos requerimientos, y este se limita a componerlo:

- **El controlador y el permiso** los crea `RF-SP-010`: `PermissionController` y `permissions:read`. Este requerimiento añade un método.
- **La conversión estricta del identificador** la creó `RF-SP-003`: `CanonicalUuidConverter` en `shared/api`, sin el cual un identificador malformado devolvería `404` en lugar del `400` que `spec.md` §13 exige. No se vuelve a decidir aquí; se reutiliza y se prueba.

`domain` no participa. `spec.md` §5 declara una sola regla, `RN-SP-004`, y es negativa: se cumple porque no existe endpoint de escritura, no porque haya código que la verifique. Igual que en `RF-SP-010`, eso condiciona cómo se prueba (§11): por lo que la API **no** expone.

## 2. Cambios de esquema

**Ninguno.** La tabla `permissions` y sus restricciones las crea `V2__create_permissions.sql` y el catálogo lo puebla `V3__seed_permissions.sql`, ambas de `RF-SP-010`; el permiso `permissions:read` sale de esa misma siembra.

Tampoco se añade índice: el acceso es por clave primaria, que ya está indexada por definición. Y no se crea el índice de búsqueda que `RF-SP-010` §2 descartó, porque este requerimiento no busca nada.

## 3. Componentes afectados

Paquete raíz: `com.factech.nexus.modules.system`. Reglas de dependencia de `architecture.md` §5.2.

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | — | — | Sin participación: `RN-SP-004` se cumple por ausencia de endpoint de escritura |
| `application` | `GetPermissionService` | Nuevo | Caso de uso. `@Transactional(readOnly = true)`. Recupera el permiso o lanza la excepción de no encontrado |
| `application` | `PermissionItem` | Sin cambios | Modelo de lectura, definido en `RF-SP-003` y ampliado en `RF-SP-010` con `resource`, `action` y `description`. **Ya tiene exactamente los campos que este detalle devuelve** |
| `application` | `PermissionQueryRepository` | **Modificado** | Puerto de consulta de `RF-SP-010`. Gana `findById(UUID): Optional<PermissionItem>` |
| `infrastructure` | `JpaPermissionQueryRepository` | Modificado | Implementa el método nuevo con la misma proyección que el listado |
| `api` | `PermissionController` | Modificado | Añade `GET /api/v1/permissions/{id}` con el mismo permiso |
| `api` | `PermissionResponse` | Sin cambios | DTO de `RF-SP-001`, ampliado en `RF-SP-010`. Se reutiliza tal cual (§4) |
| `shared/api` | `CanonicalUuidConverter` | Sin cambios | Creado en `RF-SP-003`. Convierte el identificador de la ruta o falla con `400` |

Dos decisiones de reparto:

**El método se añade a `PermissionQueryRepository`, no a `PermissionCatalog`.** Es la misma frontera que `RF-SP-010` §3 estableció: `PermissionCatalog` resuelve un conjunto de identificadores para que el **dominio decida** —lo usan `RF-SP-001` y `RF-SP-005` al verificar contención—, mientras que `PermissionQueryRepository` devuelve modelos de lectura para responder consultas. Ambos acabarían teniendo un `findById` con firmas parecidas y significados distintos; mezclarlos haría que un cambio en la proyección del catálogo afectara a una regla de negocio.

**No se crea `GetPermissionQuery`.** El caso de uso recibe un `UUID` y nada más: un tipo envoltorio para un único argumento sin invariante propia sería ceremonia. `RF-SP-010` sí lo necesita porque tiene tres criterios que normalizar.

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/permissions/{id}` | Detalle de un permiso del catálogo |

**Petición**

```
GET /api/v1/permissions/018f3a2b-7c41-7000-9a3d-1f2e5b8c9d02
```

Sin cuerpo y sin parámetros de consulta.

**Respuesta `200`**

```json
{
  "id": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d02",
  "code": "audit:read-changes",
  "resource": "audit",
  "action": "read-changes",
  "name": "Consultar auditoría de cambios",
  "description": "Permite consultar el registro de modificaciones sobre entidades del sistema."
}
```

Cuatro decisiones del contrato:

- **El detalle devuelve exactamente los mismos campos que un elemento del listado, y usa el mismo tipo.** Es la respuesta a la pregunta que `RF-SP-010` §8 dejó abierta. `spec.md` §6.2 pide «código, recurso, acción, nombre y descripción», que es literalmente el contenido de `PermissionResponse` tras la ampliación de `RF-SP-010`. Un tipo propio para el detalle sería una segunda representación del mismo concepto sin un solo campo de diferencia, y obligaría a decidir en cada endpoint futuro cuál usar —el criterio con el que `RF-SP-010` §3 rechazó duplicar el par de tipos—.
- **No se devuelven `createdAt` ni `updatedAt`**, y esta es la decisión que `RF-SP-010` §4 delegó aquí. En una tabla que solo cambia por migración, esas marcas cuentan **cuándo se desplegó una migración**, no cuándo ocurrió algo de negocio: un permiso sembrado en `V3` tendría la fecha del primer arranque del entorno, distinta en cada uno, y un permiso cuya descripción se corrigiera en una migración futura tendría un `updatedAt` que no corresponde a ninguna acción de nadie. `spec.md` §6.2 no las pide, y devolverlas invitaría a interpretarlas como historial cuando el historial de esta tabla lo lleva `flyway_schema_history`.
- **No se devuelven los roles que declaran el permiso** (`spec.md` §14, pregunta 1). No hay `JOIN` a `role_permissions` ni subconsulta correlacionada en la sentencia, que es lo único que lo hace verificable (§11). Es la misma decisión y el mismo motivo que en `RF-SP-010` §4.
- **`description` puede venir vacía** y se devuelve como `null`, nunca omitida (`spec.md` §13). Un campo ausente es indistinguible de uno que el cliente no conoce.

**Errores**

| Código | Cuándo | `error_code` | Campo en `errors` |
|---|---|---|---|
| `400` | El identificador no es un UUID en forma canónica (`VAL-001`) | `VAL-001` | `id` |
| `401` | Token ausente o inválido | `AUTH-001` | — |
| `403` | Autenticado sin `permissions:read` | `AUTH-002` | — |
| `404` | No existe permiso con ese identificador (`EX-001`) | `EX-001` | — |
| `500` | Fallo no controlado | `ERR-500` | — |

- **`404` y no `422`**, por el criterio de `development-guide.md` §7.1: el recurso **de la ruta** es el permiso, y su ausencia es exactamente lo que `404` significa. El `422` se reserva para una referencia inexistente **en el cuerpo**, que es el caso de `RF-SP-001` con su rol padre.
- **`VAL-002` no produce un código propio.** Enuncia como validación lo mismo que `EX-001`; un solo hecho, un solo código. Es el criterio de `RF-SP-003` §4.
- **Un identificador malformado es `400`, no `404`.** El mecanismo no es gratuito y ya está resuelto: `CanonicalUuidConverter` (`RF-SP-003` §4) exige los 36 caracteres canónicos antes de delegar en `UUID.fromString`, porque el JDK acepta formas abreviadas como `1-1-1-1-1` y las convierte en un identificador válido que simplemente no existe, produciendo el `404` que `spec.md` §13 prohíbe. Aquí solo hay que **no** declarar la ruta con restricción de patrón, que devolvería `404` por falta de manejador.
- **No hay `409` ni `422`**: este requerimiento no declara reglas de negocio con rechazo ni referencias que resolver.
- Todos los `type` que usa ya los estrenaron `RF-SP-001` y `RF-SP-003`. El formato es el de `architecture.md` §7.3, con `correlationId` siempre presente.

**Cuántas consultas cuesta.** Una:

```sql
SELECT p.id, p.code, p.resource, p.action, p.name, p.description
  FROM permissions p
 WHERE p.id = :id;
```

Sin `JOIN`, sin subconsultas y sin colecciones perezosas: no se carga `PermissionEntity`, se materializa `PermissionItem` con `cb.construct`, de modo que no hay asociación que un mapeador, un `toString` o la serialización pudieran recorrer. No hay `N+1` posible porque no hay ninguna asociación que atravesar. Tampoco hay filtro por `deleted_at`: esta tabla no tiene borrado lógico (`RF-SP-010` §2), y añadirlo sugeriría que sí.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `GET /api/v1/permissions/{id}` | `permissions:read` |

- El permiso **ya existe**: lo crea `V3__seed_permissions.sql` (`RF-SP-010`), que lo sembró justamente para este requerimiento y para el listado.
- **Es el mismo permiso que `RF-SP-010`**, por el criterio de `RF-SP-003` §5: detalle y listado responden la misma pregunta con distinto grano, y exigir un permiso propio obligaría a concederlos siempre juntos.
- Se declara sobre el método del controlador (`security.md` §6). Un endpoint sin declaración queda inaccesible, no público (Art. IV.1).
- **No hay filtrado por alcance de datos.** Un permiso no pertenece a nadie: quien puede leer el catálogo lee cualquiera de sus entradas.
- La resolución del permiso puede usar la caché de `security.md` §4.5: aquí solo se decide acceso, no un techo de privilegios.
- El `403` lo produce la capa de seguridad antes de entrar al caso de uso, y es ella quien emite el evento de seguridad (§6). `CA-SP-080` se satisface ahí, no en `GetPermissionService`.

## 6. Auditoría

| Operación | Registro | Contenido relevante |
|---|---|---|
| Consulta exitosa | — | **No se audita** |
| Rechazo `400` o `404` | — | **No se audita**: `architecture.md` §6.6.4 deja fuera la validación de formato y el `404`, y `ck_audit_error_log_status` (`RF-SP-013`) lo impide en el esquema |
| Denegación `403` | `audit_security_log` | `event_type = 'AUTHORIZATION_DENIED'`, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad |
| Fallo no controlado `5xx` | `audit_error_log` | `resource = 'permissions'`, `operation = 'GET /api/v1/permissions/{id}'`, `error_code = 'ERR-500'`, `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |
| — | `audit_change_log` | No aplica: la consulta no altera el estado, y el catálogo no se altera nunca por API |
| — | `audit_deletion_log` | No aplica |

- **Una consulta exitosa no produce evento de seguridad.** El catálogo de `security.md` §8.1 es cerrado y no incluye la lectura del catálogo de permisos; el rastro de quién consultó qué lo aporta `request_log`. Misma conclusión de `RF-SP-010` §6, y la contraria a la de `RF-SP-014`, donde mirar es en sí mismo un evento de seguridad.
- **El `404` no se audita**, y conviene decir por qué no es una omisión: un identificador que no existe puede ser un error de escritura tanto como un sondeo, pero el registro de peticiones ya lo recoge (Art. XV.4) y llevarlo a `audit_error_log` está prohibido desde `RF-SP-013`.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| La consulta | **Una sola**, `@Transactional(readOnly = true)` sobre `GetPermissionService` (`development-guide.md` §10) |
| `audit_error_log` de un fallo no controlado | **Independiente**, `REQUIRES_NEW` (Art. V.14) |
| `audit_security_log` de la denegación `403` | **Independiente**, `REQUIRES_NEW`. La emite la capa de seguridad |
| `request_log` | Ninguna: posterior a la respuesta, *best effort* |

`readOnly = true` tiene aquí el mismo valor de diseño que en `RF-SP-010` §7: marca la transacción como de solo lectura en PostgreSQL y es la garantía de que `RN-SP-004` no se incumple por accidente desde uno de los dos únicos caminos que tocan esta tabla. Con una sola sentencia no hay matiz de consistencia entre lecturas: una instantánea.

## 8. Impacto sobre otros módulos

| Módulo | Impacto |
|---|---|
| `RF-SP-010` | Comparte `PermissionController`, `PermissionQueryRepository`, `PermissionItem` y `PermissionResponse`. **Su §8 dejó abierta la decisión sobre `createdAt` y `updatedAt`, y este plan la cierra: no se exponen** (§4). Ningún cambio en su contrato |
| `RF-SP-003` | Reutiliza `CanonicalUuidConverter` sin modificarlo. Es su segundo consumidor, y confirma que el conversor pertenece a `shared/api` y no al requerimiento que lo estrenó |
| `RF-SP-005` | Es el requerimiento al que esta consulta da apoyo (`spec.md` §2): se llega aquí desde el catálogo, antes de conceder el permiso. No hay dependencia de código en ningún sentido |
| `shared/api`, `shared/error` | Ninguno. No se añade ningún tipo de excepción: `ResourceNotFoundException` ya existe en la jerarquía de `development-guide.md` §7.1 desde `RF-SP-003` |
| Frontend | La pantalla de composición de roles puede enlazar cada permiso del catálogo con su detalle sin una segunda llamada al listado. El identificador ya viene en la respuesta de `RF-SP-010` |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Un DTO propio para el detalle, distinto de `PermissionResponse` | Sería una segunda representación del mismo concepto sin un solo campo de diferencia, y obligaría a decidir en cada endpoint futuro cuál usar. Mismo criterio con el que `RF-SP-010` §3 rechazó duplicar el par de tipos |
| Devolver `createdAt` y `updatedAt` porque «un detalle suele traer más que un listado» | En una tabla que solo cambia por migración, esas marcas cuentan cuándo se desplegó una migración y difieren entre entornos. Invitarían a leerlas como historial cuando el historial lo lleva `flyway_schema_history` |
| Devolver los roles que declaran el permiso | `spec.md` §14, pregunta 1: es el recorrido inverso del catálogo, encarece una consulta trivial y la relación se consulta desde el lado que ya la tiene. Misma resolución que la pregunta 2 de `RF-SP-010` |
| Admitir también el acceso por código (`/api/v1/permissions/roles:read`) | `spec.md` §14, pregunta 2, lo resolvió: dos formas de direccionar el mismo recurso obligan a distinguir en cada petición cuál es cuál. Y el código lleva dos puntos, que en una ruta obliga además a codificarlo. El código sigue siendo la vía para *encontrar* el permiso, como filtro y búsqueda de `RF-SP-010` |
| Añadir el método a `PermissionCatalog` en lugar de a `PermissionQueryRepository` | Mezcla resolución para decidir con consulta para mostrar en un puerto que el dominio usa. Un cambio en la proyección del catálogo afectaría a una regla de negocio |
| Declarar la ruta con restricción de patrón sobre el identificador | Un identificador malformado no encontraría manejador y Spring respondería `404`, que es exactamente el error que `spec.md` §13 prohíbe. Está documentado en `RF-SP-003` §4 |
| Un caso de uso genérico de lectura por identificador, reutilizable entre entidades | Un servicio parametrizado por tipo obliga a que la capa de aplicación conozca un metamodelo, y ahorra veinte líneas a cambio de un punto donde cualquier entidad puede leerse sin su permiso |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Alguien declara la ruta con restricción de patrón y `CA-SP-079` empieza a fallar por un `404` en lugar de un `400` | Bajo | Prueba propia en §11 sobre las tres formas de identificador inválido. Es el defecto más fácil de reintroducir, porque la restricción de patrón parece una validación y es lo contrario |
| El detalle y el listado divergen al ampliarse uno de los dos | Bajo | Comparten `PermissionItem` y `PermissionResponse`: ampliar uno amplía los dos. La prueba de §11 compara ambas respuestas campo por campo, de modo que una divergencia rompe la construcción |
| Un permiso se elimina por migración y este endpoint empieza a devolver `404` para un identificador que estaba en uso | Bajo | `RF-SP-010` §2 lo previó: `permissions` no tiene borrado lógico y la clave foránea `ON DELETE RESTRICT` de `role_permissions` obliga a quien lo intente a encontrarse con las asociaciones vigentes. El `404` sería entonces correcto |
| El identificador de un permiso cambia entre entornos y un enlace guardado deja de resolver | Bajo | No puede ocurrir: `V3` siembra identificadores UUID v7 **literales**, iguales en todos los entornos (`RF-SP-010` §2). Es una de las razones por las que se sembraron así |

## 11. Estrategia de prueba

Niveles: **Integración** (Testcontainers sobre PostgreSQL real, con `V1` a `V3` aplicadas) y **API** (extremo a extremo por HTTP, con autenticación). No hay nivel unitario: este requerimiento no tiene `domain`.

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-078` | Integración + API | Consultado por el identificador de un permiso sembrado, devuelve `200` con `code`, `resource`, `action`, `name` y `description`, y sus valores coinciden con los de `V3` |
| `CA-SP-079` | API | Un UUID canónico que no corresponde a ningún permiso devuelve `404` con `EX-001` y sin ninguna pista de qué podría haber sido |
| `CA-SP-080` | API | Un actor autenticado sin `permissions:read` recibe `403`, no obtiene dato alguno y queda el evento de denegación en `audit_security_log` |

Casos límite de `spec.md` §13 y decisiones de este plan que exigen prueba propia (Art. VII.3):

| Caso | Nivel | Qué verifica |
|---|---|---|
| Permiso sin descripción | Integración | Se devuelve con `description: null`, sin omitir el campo |
| Identificador con formato incorrecto | API | `abc`, `1-1-1-1-1` y un UUID de 35 caracteres devuelven los tres `400` con `VAL-001` y campo `id`, **nunca `404`**. La segunda forma es la que el JDK convertiría sin error |
| Coherencia con el listado | Integración | El objeto que devuelve el detalle es **campo por campo idéntico** al elemento correspondiente de `GET /api/v1/permissions`. Es lo que impide que ambos endpoints diverjan |
| Ausencia de marcas temporales | API | El cuerpo no contiene `createdAt` ni `updatedAt`, ni ningún campo que `spec.md` §6.2 no declare |
| Número de sentencias por petición | Integración | **Una**, y ninguna sobre `role_permissions` ni sobre `roles`. Es lo que hace verificable que el detalle no cuenta ni lista roles |
| Ausencia de escritura | API | `POST`, `PUT`, `PATCH` y `DELETE` sobre `/api/v1/permissions/{id}` devuelven `405`. Junto con la prueba equivalente de `RF-SP-010` (`CA-SP-076`), es la única forma de verificar `RN-SP-004`, que no tiene código que la implemente |

Las reglas de ArchUnit introducidas en `RF-SP-001` y `RF-SP-003` cubren también este requerimiento. No se añade ninguna nueva: no toca `domain` y no introduce dependencias entre módulos.
