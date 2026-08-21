# PLAN — `RF-SP-008` Cambiar el rol padre de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-008` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Enfoque

Es la única operación del módulo capaz de dejar la estructura en un estado del que no se puede salir. Todas las demás pueden equivocarse y corregirse; un ciclo en la jerarquía no tiene raíz, y sin raíz la resolución de permisos no termina.

Tres verificaciones sostienen la operación, y las tres son distintas entre sí:

1. **Ausencia de ciclo** (`RN-SEG-006`): el nuevo padre no puede ser el propio rol ni ninguno de sus descendientes.
2. **Contención frente al nuevo padre** (`RN-SEG-013`): los permisos que el rol ya declara deben caber en el nuevo padre. Si no caben, se rechaza; **nunca** se retiran.
3. **Serialización**: dos reubicaciones simultáneas pueden ser correctas por separado y cerrar un ciclo al aplicarse. Ninguna verificación aislada lo detecta, porque cada una ve una jerarquía que aún es acíclica.

La tercera es la que obliga a un mecanismo que ningún otro requerimiento del módulo necesita.

## 2. Cambios de esquema

**Ninguno.** La columna `parent_role_id` y su clave foránea se crean en `V5__create_roles.sql` (`RF-SP-001`).

La detección de ciclos recorre la descendencia del rol movido y se apoya en `ix_roles_parent_role_id`, que `V5__create_roles.sql` sí crea (plan de `RF-SP-001` §2, verificado el 21-08-2026). Sin ese índice, cada nivel del recorrido sería una lectura completa de la tabla. Lo asumen igual los planes de `RF-SP-003` y `RF-SP-006`.

**No se añade restricción de ciclos en la base de datos.** PostgreSQL no puede expresarla de forma declarativa: haría falta un disparador con una consulta recursiva, que llevaría `RN-SEG-006` fuera del dominio y la dejaría sin poder probarse sin levantar la base de datos (Art. VI.3).

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `RoleHierarchy` | Nuevo | Objeto de dominio que recibe el rol, su descendencia y el nuevo padre, y decide si el movimiento es válido. Contiene `RN-SEG-006` y `RN-SEG-013` |
| `domain` | `HierarchyViolation` | Nuevo | Resultado del rechazo: si fue por ciclo o por contención, y con qué permisos o qué rol |
| `domain` | `Role` | Modificado | Método `reparent(Role nuevoPadre)`, que aplica el cambio ya validado |
| `application` | `ChangeRoleParentService` | Nuevo | Caso de uso. `@Transactional`, adquiere el bloqueo, carga la descendencia y emite la auditoría |
| `application` | `RoleHierarchyLock` | Nuevo | Puerto que serializa toda mutación de la jerarquía (§5) |
| `application` | `RoleRepository` | Modificado | Añade la carga de la **descendencia completa** de un rol, con profundidad acotada |
| `infrastructure` | `AdvisoryRoleHierarchyLock` | Nuevo | Adaptador del bloqueo sobre PostgreSQL con `pg_try_advisory_xact_lock` —intento sin espera—, liberado al terminar la transacción |
| `infrastructure` | `JpaRoleRepository` | Modificado | Recupera la descendencia con una consulta recursiva |
| `api` | `RoleController` | Modificado | Añade `PATCH /api/v1/roles/{id}/parent` |
| `api` | `ChangeRoleParentRequest` | Nuevo | DTO con el identificador del nuevo padre |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/roles/{id}/parent` | Reubica el rol bajo otro rol padre |

Subrecurso propio, como el estado en `RF-SP-007`, y por la misma razón: sus reglas de rechazo no se parecen a las de ninguna otra edición.

**Petición**

```json
{ "parentRoleId": "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01" }
```

**Respuesta `200`** — `RoleResponse`, definido en `RF-SP-001`, con el nuevo rol padre. No se devuelve `RoleDetailResponse`, que arrastraría sus dos subconsultas de conteo a un camino de escritura que no las necesita (`RF-SP-004` §4).

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Identificador ausente o malformado | `VAL-001` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `roles:update` | `AUTH-002` |
| `403` | El rol está entre los del actor (`EX-005`) | `RN-SEG-011` |
| `404` | El rol que se mueve no existe o está eliminado (`EX-006`) | `EX-006` |
| `409` | El rol es de sistema (`EX-005`) | `RN-SEG-012` |
| `409` | Se intenta reubicar el rol raíz (`EX-003`) | `RN-SEG-007` |
| `409` | El movimiento formaría un ciclo (`EX-002`) | `RN-SEG-006` |
| `409` | El rol excede al nuevo padre (`EX-001`) | `RN-SEG-013` |
| `409` | Hay otra reubicación en curso: no se pudo tomar el bloqueo (§5) | `RN-SEG-006` |
| `422` | El nuevo padre no existe o está inactivo (`EX-004`) | `EX-004` |
| `500` | Fallo no controlado | `ERR-500` |

`EX-006` se añadió a `spec.md` el 21-08-2026, al aprobar este plan: la especificación no declaraba la excepción del rol que se mueve inexistente, y este documento la referenciaba con `EX-004`, que es el **nuevo padre** inexistente o inactivo. Dos filas con significados distintos compartían código (Art. I.7).

Los dos `403` son distintos y no deben fusionarse: el primero lo produce la capa de seguridad compartida antes de entrar al caso de uso; el segundo, el caso de uso con el rol ya cargado.

El `409` por contención **debe enumerar los permisos que sobran**. Como no hay retirada automática, esa lista es lo único que permite al actor avanzar: los retira con `RF-SP-006` y reintenta.

**Orden de verificación**

1. Formato del identificador.
2. Rol existente, vigente, no de sistema y no raíz.
3. El actor no tiene el rol asignado.
4. Nuevo padre existente y activo.
5. **Adquisición del bloqueo de jerarquía.**
6. Ausencia de ciclo.
7. Contención frente al nuevo padre.

El bloqueo se adquiere **antes** de las dos verificaciones estructurales y no antes de todo: los rechazos por formato, permiso o existencia no tocan la jerarquía y no deben serializarse con nada.

## 5. Autorización y serialización

| Endpoint | Permiso requerido |
|---|---|
| `PATCH /api/v1/roles/{id}/parent` | `roles:update` |

**El bloqueo de jerarquía** es lo característico de este plan. Se implementa como un bloqueo consultivo de PostgreSQL, tomado sobre una clave fija que representa «la jerarquía de roles», y liberado al terminar la transacción.

Es un bloqueo **único para toda la jerarquía**, no por rol. La alternativa —bloquear el rol movido y la cadena del nuevo padre— parece más fina pero no cierra el problema: dos movimientos en ramas distintas pueden no compartir ninguna fila y aun así cerrar un ciclo entre ambos. Ejemplo con cuatro roles, `A → B` y `C → D`:

```
Mover B bajo D    (válido: D no desciende de B)
Mover D bajo B    (válido: B no desciende de D)
```

Cada operación es correcta contra la jerarquía que ve. Aplicadas a la vez, `B` y `D` quedan colgando el uno del otro. Solo un bloqueo que abarque ambas ramas lo impide, y como no se sabe de antemano qué ramas se tocarán, el único que sirve es el global.

El coste es que las reubicaciones no se solapan. Es aceptable porque reubicar un rol es una operación excepcional; no lo sería si el bloqueo alcanzara también a las lecturas, que no es el caso.

**El bloqueo se intenta sin esperar.** Se usa `pg_try_advisory_xact_lock`, no `pg_advisory_xact_lock`: si otra petición lo tiene tomado, esta se rechaza de inmediato con `409` —«hay otra reubicación en curso, reintente»— en lugar de quedarse esperando. Decidido el 21-08-2026, por dos razones. La primera es operativa: una espera indefinida encadena peticiones colgadas, cada una ocupando una conexión del pool, y basta una transacción lenta para agotarlo. La segunda es de verificación: con espera, `CA-SP-161` depende de la temporización de dos transacciones y se vuelve intermitente; sin espera, el resultado es determinista —una tiene éxito, la otra recibe `409`— y la prueba deja de ser frágil. El precio es que el actor puede recibir un rechazo espurio, algo tolerable en una operación que ocurre muy de vez en cuando y cuyo reintento es inmediato.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Reubicación efectiva | `audit_change_log` | `action = UPDATE`, con `changes` conteniendo solo `parent_role_id`, con su antes y su después |
| Reubicación efectiva | `audit_security_log` | Modificación de rol, severidad **Alta** |
| Nuevo padre igual al actual | — | **Ningún evento** |
| Rechazo por `EX-001` a `EX-006` | `audit_error_log` | `resource = 'roles'`, `operation` con método y ruta, `error_code` de la tabla de §4, `error_type = 'BUSINESS_RULE'`, `http_status`, `severity` y `message` saneado. Severidad **Alta** para `RN-SEG-006` y `RN-SEG-013` —un ciclo corrompe la estructura y un exceso de contención es escalada— y para `RN-SEG-011`; **Media** para el resto |
| Rechazo `409` por bloqueo no obtenido | `audit_error_log` | `error_type = 'BUSINESS_RULE'`, `severity = 'MEDIA'`. No es un fallo: es la serialización funcionando, y conviene poder contar con qué frecuencia ocurre |
| Rechazo `400` de formato | — | **No se audita** (`architecture.md` §6.6.4) |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

El diff registra los identificadores, pero conviene incluir también los **códigos** del padre anterior y del nuevo, por la misma razón que en `RF-SP-006`: dentro de un año, resolver dos referencias puede ser imposible.

Solo se audita el rol movido. Los roles hijos lo acompañan sin que ninguno cambie: su `parent_role_id` sigue apuntando al mismo sitio.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Bloqueo de jerarquía | Adquirido dentro de la transacción, liberado al terminarla |
| Actualización de `parent_role_id` y su evento en `audit_change_log` | **La misma** (Art. V.14) |
| Evento en `audit_security_log` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |

El bloqueo consultivo ligado a la transacción se libera solo, también si la transacción falla. Un bloqueo de sesión exigiría liberarlo a mano y dejaría la jerarquía inmovilizada ante cualquier excepción no prevista.

## 8. Impacto sobre otros módulos

- **`shared/security`: no se invalida nada, y conviene entender por qué.** La caché de `security.md` §4.5 guarda `rol → permisos declarados`, y reubicar un rol no cambia ni uno solo de ellos: cambia de quién cuelga, no qué concede. Tampoco cambia nada para sus portadores, porque `RN-SEG-004` obliga a que cada rol declare sus permisos de forma explícita y los efectivos son la unión de los roles activos, sin herencia. Lo que sí cambia es la **cota** —el nuevo padre es otro techo—, pero esa cota solo se consulta al conceder permisos (`RN-SEG-003`, en `RF-SP-005`), y allí se lee de la base de datos y nunca de la caché, por la decisión de `RF-SP-001` §5. Invalidar aquí no refrescaría nada y dejaría escrito que la caché guarda algo relacionado con la jerarquía, que es lo que llevaría a diseñar mal el día que alguien lea este documento. El borrador sí invalidaba; se retiró el 21-08-2026.
- **Los roles hijos no se tocan.** Su contención sigue siendo válida por transitividad: si el rol cabe en el nuevo padre, sus hijos —que ya cabían en él— también.
- **`RF-SP-005` y `RF-SP-006`** deben tomar el mismo bloqueo si alguna vez modifican la estructura. Hoy no lo hacen: cambian permisos, no relaciones.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Retirar automáticamente los permisos sobrantes | Sería una revocación implícita, y revocar tiene reglas propias: `RN-SEG-005` rechaza retirar lo que un rol hijo declara. Habría que reimplementar esa verificación aquí o saltarla en silencio |
| Bloquear solo el rol movido y la cadena del nuevo padre | No cierra el problema. Dos movimientos en ramas disjuntas no comparten ninguna fila y pueden cerrar un ciclo entre ambos, como muestra el ejemplo de §5 |
| Detectar el ciclo con un disparador y una consulta recursiva | Garantía más fuerte, porque ninguna ruta de escritura la esquiva, pero lleva `RN-SEG-006` a la base de datos, donde no puede probarse sin levantarla (Art. VI.3) |
| Exigir que el nuevo padre sea del mismo tipo | El catálogo aprobado ya lo contradice: `MANAGER` es comercial y cuelga de `ADMIN`, que es funcionario. La cabeza de la cadena comercial tiene que colgar de algo |
| Verificar la contención de toda la descendencia contra el nuevo padre | Redundante por transitividad. Si el rol cabe en el nuevo padre, sus hijos también, porque ya cabían en el rol |
| Recorrer la descendencia sin límite de profundidad | Una jerarquía corrupta por un fallo previo convertiría el recorrido en infinito. El límite es una salvaguarda, no una restricción de negocio |
| Aplicar el cambio y revertir si el ciclo se detecta después | Deja la jerarquía inconsistente durante la ventana, y la resolución de permisos concurrente podría no terminar en ese intervalo |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Un ciclo llega a formarse | **Crítico** | Serialización con bloqueo global. `CA-SP-161` debe probarse con dos transacciones concurrentes reales, no con dos llamadas secuenciales |
| El bloqueo no se libera y la jerarquía queda inmovilizada | Alto | Bloqueo consultivo ligado a la transacción, que se libera también ante excepción. Además se toma **sin espera**: una petición que no lo obtiene se rechaza en el acto en vez de encolarse ocupando una conexión del pool (§5) |
| ~~Falta índice en `roles(parent_role_id)`~~ | — | **Verificado el 21-08-2026:** `ix_roles_parent_role_id` sí se crea en `V5__create_roles.sql` |
| Recorrido infinito por jerarquía ya corrupta | Medio | Límite de profundidad explícito, con error controlado al superarlo |
| Se implementa la retirada automática de permisos por parecer útil | Medio | Declarado en el «no incluye» de la spec y en §9 |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-056` | Integración | El rol queda colgando del nuevo padre |
| `CA-SP-057` | Unitaria + API | El dominio identifica los permisos sobrantes; la API devuelve `409` enumerándolos |
| `CA-SP-058` | Unitaria | Mover un rol bajo su propio nieto se rechaza |
| `CA-SP-059` | Unitaria | Un rol como padre de sí mismo se rechaza |
| `CA-SP-060` | API | El rol raíz devuelve `409` con `RN-SEG-007` |
| `CA-SP-061` | Integración | Tras mover un rol con hijos, los hijos siguen colgando de él y su contención se mantiene |
| `CA-SP-062` | Integración | Enviar el padre actual no genera fila en ninguno de los dos registros |
| `CA-SP-063` | Integración | Una fila en cada registro, con `parent_role_id` en el diff |
| `CA-SP-160` | Integración | Un rol comercial queda colgando de uno funcionario sin rechazo |
| `CA-SP-161` | **Integración concurrente** | Dos transacciones que intentan `B → D` y `D → B` a la vez: una tiene éxito, la otra se rechaza, y no queda ciclo |
| `CA-SP-162` | Integración | Tras un rechazo por contención, el rol conserva todos sus permisos |
| `CA-SP-175` | API | Mover un rol inexistente o eliminado devuelve `404` con `EX-006`, distinto del `422` con `EX-004` que devuelve un nuevo padre inexistente |

`CA-SP-161` es la prueba difícil y la que no puede omitirse. Ejecutarla como dos llamadas secuenciales pasaría siempre y no probaría nada: hace falta abrir dos transacciones, dejarlas competir por el bloqueo y comprobar el estado final de la jerarquía.
