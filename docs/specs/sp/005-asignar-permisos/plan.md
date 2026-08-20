# PLAN — `RF-SP-005` Asignar permisos a un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-005` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 20-08-2026 |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobado por | — |
| Fecha de aprobación | — |

---

## 1. Enfoque

Es el requerimiento donde el modelo de contención deja de ser un documento y pasa a ser código. Todo lo demás del módulo lo rodea.

La operación es **aditiva e idempotente**: agrega los permisos que faltan e ignora los que ya estaban. Esa forma no es una comodidad, es lo que la mantiene fuera del alcance de `RN-SEG-005`: como nunca retira nada, ningún rol descendiente puede quedar excediendo a su padre, y no hace falta verificar la descendencia.

Dos verificaciones actúan a la vez y ninguna sustituye a la otra:

- **`RN-SEG-003`** acota el rol respecto de **su rol padre**.
- **`RN-SEG-010`** acota al **actor**: nadie concede lo que no posee.

Sin la segunda, un administrador podría ampliar un rol que cuelga de un padre poderoso hasta darle permisos que él no tiene, y después asignárselo a otra persona.

La operación se aplica **entera o no se aplica**. Un rechazo parcial dejaría el rol en un estado que nadie pidió.

## 2. Cambios de esquema

**Ninguno.** `role_permissions` y su clave primaria compuesta se crean en `V5__create_role_permissions.sql` (`RF-SP-001`).

La clave primaria compuesta `(role_id, permission_id)` es además el mecanismo que absorbe la asignación concurrente del mismo permiso: dos peticiones simultáneas no producen fila duplicada ni error interno, porque la segunda encuentra la fila ya presente y el caso queda cubierto por el flujo alternativo de la spec.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Role` | Modificado | Método `grantPermissions(Set<PermissionCode>, Role parent, Set<PermissionCode> actorPermissions)`. Contiene `RN-SEG-003` y `RN-SEG-010`, y devuelve qué permisos se agregaron realmente |
| `domain` | `PermissionContainmentViolation` | Nuevo | Resultado del rechazo: qué permisos incumplen y contra qué cota. Es lo que permite a la API decir **cuáles**, no solo que falló |
| `domain` | `RoleRepository` | Sin cambios | Puerto de `RF-SP-001` |
| `application` | `GrantRolePermissionsService` | Nuevo | Caso de uso. `@Transactional`, resuelve las cotas y emite la auditoría |
| `application` | `PermissionCatalog` | Sin cambios | Puerto de `RF-SP-001`. Verifica que los permisos existan |
| `application` | `AuthenticatedActor` | Modificado | Añade la obtención de los permisos efectivos del actor para `RN-SEG-010` |
| `application` | `RolePermissionCacheInvalidator` | Nuevo | Puerto hacia `shared/security` para dejar sin efecto la resolución de permisos del rol |
| `infrastructure` | `JpaRoleRepository` | Modificado | Persiste las nuevas filas de `role_permissions` |
| `infrastructure` | `SecurityContextActorAdapter` | Modificado | Resuelve los permisos efectivos del actor **desde la base de datos** (§5) |
| `api` | `RoleController` | Modificado | Añade `POST /api/v1/roles/{id}/permissions` |
| `api` | `GrantPermissionsRequest` | Nuevo | DTO de entrada con Bean Validation (`VAL-001`, `VAL-002`, `VAL-006`) |
| `api` | `RoleResponse` | Sin cambios | Definido en `RF-SP-001` |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/roles/{id}/permissions` | Agrega permisos al rol |

Se usa `POST` sobre una subruta y no `PUT` sobre la lista, precisamente porque la operación **no** representa el estado final del conjunto: `PUT` invitaría a interpretarla como un reemplazo, que es lo que la spec descartó.

**Petición**

```json
{
  "permissionIds": [
    "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d01",
    "018f3a2b-7c41-7000-9a3d-1f2e5b8c9d02"
  ]
}
```

Los permisos van por identificador y no por código, igual que en `RF-SP-001`, para no mezclar dos espacios de identificación en el mismo cuerpo.

**Respuesta `200`** — el rol con su lista de permisos actualizada.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Lista vacía, identificador malformado o más de 100 elementos | `VAL-001`, `VAL-002`, `VAL-006` |
| `403` | Sin `roles:update`, o el rol está entre los del actor (`EX-005`) | `RN-SEG-011` |
| `404` | El rol no existe o está eliminado | `EX-004` |
| `409` | Algún permiso excede al rol padre (`EX-001`) | `RN-SEG-003` |
| `409` | Algún permiso excede al actor (`EX-002`) | `RN-SEG-010` |
| `409` | El rol es de sistema (`EX-004`) | `RN-SEG-012` |
| `422` | Algún permiso no existe en el catálogo (`EX-003`) | `EX-003` |

Los cuerpos de `409` por contención **deben enumerar los permisos que incumplen**. Sin ese detalle el actor no puede corregir su petición, y la spec lo exige de forma explícita en `EX-001` y `EX-002`.

**Orden de verificación**

1. Formato, obligatoriedad y límite de 100, todas juntas.
2. Rol existente y vigente.
3. Rol no de sistema.
4. El actor no tiene el rol asignado.
5. Todos los permisos existen en el catálogo.
6. Contención en el rol padre.
7. Contención en el actor.

Los pasos 6 y 7 no son evaluables sin haber resuelto antes el catálogo: el orden es dependencia, no preferencia.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `POST /api/v1/roles/{id}/permissions` | `roles:update` |

**`RN-SEG-010` se evalúa leyendo la base de datos, no la caché de resolución.**

Es la misma decisión que tomó el plan de `RF-SP-001` y conviene mantenerla por la misma razón: esta es una operación de escritura poco frecuente, y una entrada de caché obsoleta aquí no se traduce en una lectura desactualizada sino en **una concesión indebida y permanente**. El ahorro no compensa.

**`RN-SEG-011`** se verifica con el código del rol ya cargado contra los códigos que transporta el token, sin consulta adicional, y solo sobre los roles asignados directamente.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Permisos agregados | `audit_change_log` | `action = UPDATE` sobre la entidad `roles`, con `changes` conteniendo **solo los permisos realmente agregados** |
| Permisos agregados | `audit_security_log` | Cambio de permisos de un rol, severidad **Alta** |
| Ninguno agregado | — | **Ningún evento**: si todos los permisos ya estaban, nada cambió |

Un solo evento de cambio por operación, no uno por fila de `role_permissions`. El evento documenta una decisión de negocio —«se amplió este rol»—, no tres inserciones.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Inserción en `role_permissions` y su evento en `audit_change_log` | **La misma** (Art. V.14) |
| Invalidación de la caché de permisos | Tras el commit, nunca antes |
| Evento en `audit_security_log` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |

La caché se invalida **después** de confirmar. Invalidarla antes abriría una ventana en la que una petición concurrente la repuebla con el estado antiguo y el cambio no tendría efecto, que es justo lo que `CA-SP-038` verifica.

## 8. Impacto sobre otros módulos

- **`shared/security`** expone la invalidación de la resolución de permisos. Es la primera vez que se necesita; `RF-SP-006` y `RF-SP-007` usarán el mismo puerto.
- **Ningún rol hijo se modifica.** Su contención sigue siendo válida porque el conjunto del padre solo creció. Esta es la asimetría con `RF-SP-006`, donde el conjunto encoge y sí hay que mirar hacia abajo.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Reemplazar la lista completa con `PUT` | Haría revocaciones implícitas, y revocar tiene reglas propias. O se reimplementa aquí `RN-SEG-005`, con dos copias de la misma regla, o se salta en silencio y un rol hijo queda excediendo a su padre sin que nada falle |
| Aplicar los permisos válidos e ignorar los que fallan | Dejaría el rol en un estado que nadie pidió y que el actor no podría prever. `EX-001` y `EX-002` exigen rechazo completo |
| Resolver la contención en SQL, insertando solo los permisos que el padre tiene | El rol se crearía con un subconjunto en lugar de rechazarse, que es lo contrario de lo especificado. Y llevaría `RN-SEG-003` a la base de datos, donde no puede probarse sin levantar PostgreSQL (Art. VI.3) |
| Evaluar `RN-SEG-010` con la caché de permisos | Una entrada obsoleta se traduce en una concesión indebida que persiste. En una lectura la caché obsoleta muestra un dato viejo; aquí escribe un privilegio que no correspondía |
| Propagar el permiso a los roles hijos | Contradice el modelo: cada rol declara sus permisos de forma explícita, y es lo que permite responder qué puede hacer alguien leyendo una sola lista (`RN-SEG-004`) |
| Verificar la descendencia como hace `RF-SP-006` | Innecesario. Al no retirar nada, ningún descendiente puede quedar fuera de su cota. Añadirlo sería coste sin garantía adicional |
| Emitir un evento de auditoría por permiso agregado | Multiplica las filas sin añadir información: la operación es una decisión de negocio, no tres |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La caché se invalida antes del commit y el cambio no surte efecto | **Alto** | Enganchada al commit, verificado por `CA-SP-038` |
| El rechazo no enumera los permisos que incumplen | Medio | El dominio devuelve el detalle; la API lo traslada. Cubierto por `CA-SP-032` y `CA-SP-033` |
| `RN-SEG-010` se implementa contra la caché por eficiencia | **Alto** | Anotado aquí y en `RF-SP-001`. Es una concesión indebida, no una lectura desactualizada |
| El rol raíz se trata como error por no tener padre | Medio | `FA-002` lo cubre: se omite `RN-SEG-003` y se mantiene `RN-SEG-010`. Verificado por `CA-SP-035` |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-031` | Integración | Las filas quedan en `role_permissions` |
| `CA-SP-032` | Unitaria + API | El dominio rechaza y enumera; la API devuelve `409` con los permisos citados |
| `CA-SP-033` | Unitaria + API | Ídem contra los permisos efectivos del actor |
| `CA-SP-034` | Integración | Repetir la misma petición no produce error ni filas duplicadas |
| `CA-SP-035` | Unitaria | Un rol sin padre omite `RN-SEG-003` y conserva `RN-SEG-010` |
| `CA-SP-036` | API | Rol de sistema devuelve `409` con `RN-SEG-012` |
| `CA-SP-037` | API | Rol propio del actor devuelve `403` con `RN-SEG-011` |
| `CA-SP-038` | Integración | Tras la operación, una resolución de permisos refleja el cambio de inmediato |
| `CA-SP-039` | Integración | Una fila en cada registro de auditoría, con los permisos agregados en el diff |
| `CA-SP-040` | Unitaria | La verificación consulta solo el padre inmediato, sin recorrer ancestros |
| `CA-SP-153` | Integración | Los permisos previos siguen presentes tras la operación |
| `CA-SP-154` | API | Una petición con 101 permisos devuelve `400` |

`CA-SP-040` merece una prueba propia y no derivada: con una cadena de tres roles, se verifica que la operación consulta al padre y **no** al abuelo. Es la garantía de que la contención sigue siendo de un solo nivel, que es lo que hace viable el modelo.
