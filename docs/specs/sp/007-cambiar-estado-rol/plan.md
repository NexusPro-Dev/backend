# PLAN — `RF-SP-007` Cambiar el estado de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-007` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 21-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Enfoque

Una sola columna cambia de valor. La dificultad no está ahí, está en la palabra **inmediato** de `RN-SEG-002`: un rol inactivo deja de conceder permisos al instante, aunque siga asignado y aunque sus portadores tengan un token vigente.

Eso convierte una actualización trivial en una operación con dos efectos que no comparten mecanismo:

1. **Persistir el estado**, que es la parte fácil.
2. **Dejar sin efecto la resolución de permisos en curso**, que es lo que hace que el cambio se note antes de que expire ningún token.

El segundo efecto es el requerimiento. Sin él, desactivar un rol sería una anotación sin consecuencias durante quince minutos.

La operación **no se propaga**: los roles hijos no se tocan, ni se rechaza por tenerlos.

## 2. Cambios de esquema

**Ninguno.** La columna `status` y su restricción `ck_roles_status` se crean en `V5__create_roles.sql` (`RF-SP-001`).

**No se añade columna de motivo.** La spec resolvió que no se exige, y el Art. V.13 solo lo obliga en las eliminaciones. Añadir una columna nulable «por si acaso» produce un campo que casi siempre está vacío y que nadie sabe si puede confiar en interpretar.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain` | `Role` | Modificado | Métodos `activate()` y `deactivate()`. Devuelven si hubo cambio efectivo, para `CA-SP-052` |
| `domain` | `RoleStatus` | Sin cambios | Enumerado definido en `RF-SP-001` |
| `application` | `ChangeRoleStatusService` | Nuevo | Caso de uso. `@Transactional`, aplica el cambio y coordina la invalidación |
| `application` | `RolePermissionCacheInvalidator` | Sin cambios | Puerto definido en `RF-SP-005` |
| `application` | `RoleChangeAuditor` | Sin cambios | Puerto definido en `RF-SP-001` |
| `infrastructure` | `JpaRoleRepository` | Sin cambios | Actualiza el agregado ya cargado |
| `api` | `RoleController` | Modificado | Añade `PATCH /api/v1/roles/{id}/status` |
| `api` | `ChangeRoleStatusRequest` | Nuevo | DTO con el estado destino. **No lleva motivo** |
| `api` | `RoleResponse` | Sin cambios | Definido en `RF-SP-001` |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/roles/{id}/status` | Activa o desactiva el rol |

Subrecurso propio, y no un campo dentro de `PATCH /roles/{id}`, por dos razones: el estado tiene reglas de rechazo distintas de las del nombre, y `RF-SP-004` declara explícitamente que no lo modifica.

**Petición**

```json
{ "status": "INACTIVO" }
```

Se envía el **estado destino** y no una acción (`activate` / `deactivate`) porque hace la operación idempotente por construcción: repetir la misma petición deja el mismo resultado, que es lo que `FA-001` describe.

**Respuesta `200`** — `RoleResponse`, definido en `RF-SP-001`, con el estado ya actualizado. No se devuelve `RoleDetailResponse`, que arrastraría sus dos subconsultas de conteo a un camino de escritura que no las necesita (`RF-SP-004` §4).

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | Estado ausente o fuera del dominio | `VAL-001` |
| `400` | Cuerpo con un campo desconocido, incluido un motivo | `VAL-001` |
| `401` | Token ausente o inválido | `AUTH-001` |
| `403` | El actor no posee `roles:update` | `AUTH-002` |
| `403` | El rol está entre los del actor (`EX-002`) | `RN-SEG-011` |
| `404` | El rol no existe o está eliminado (`EX-003`) | `EX-003` |
| `409` | El rol es de sistema o es el rol raíz (`EX-001`) | `RN-SEG-012`, `RN-SEG-007` |
| `500` | Fallo no controlado | `ERR-500` |

Los dos `403` son distintos y no deben fusionarse: el primero lo produce la capa de seguridad compartida antes de entrar al caso de uso; el segundo, el caso de uso con el rol ya cargado. El `409` cita `RN-SEG-007` cuando lo que se intenta desactivar es el rol raíz, y `RN-SEG-012` cuando es un rol de sistema cualquiera.

El rechazo de un campo desconocido es lo que hace verificable `CA-SP-159`: sin él, enviar un motivo se ignoraría en silencio y el criterio no comprobaría nada.

## 5. Autorización

| Endpoint | Permiso requerido |
|---|---|
| `PATCH /api/v1/roles/{id}/status` | `roles:update` |

`RN-SEG-011` es aquí más que una formalidad: sin ella, un administrador podría desactivar su propio rol y quedarse sin acceso en la misma petición. Se verifica igual que en el resto del módulo, contra los **roles vigentes del actor leídos de la base de datos** —no contra los códigos del token— y solo sobre los asignados directamente (`RF-SP-004` §5).

**El rol raíz se rechaza por su propia condición**, no solo por estar marcado como de sistema. La verificación es `esRaiz || esDeSistema`, de modo que la protección no depende de que alguien recuerde marcarlo.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Cambio efectivo | `audit_change_log` | `action = UPDATE`, con `changes` conteniendo solo `status`, con su antes y su después |
| Cambio efectivo | `audit_security_log` | Cambio de estado de un rol, severidad **Alta** |
| Sin cambio efectivo | — | **Ningún evento** |
| Rechazo por `EX-001` a `EX-003` | `audit_error_log` | `resource = 'roles'`, `operation` con método y ruta, `error_code` de la tabla de §4, `error_type = 'BUSINESS_RULE'`, `http_status`, `severity` y `message` saneado. Severidad **Alta** para `RN-SEG-011` y para el intento sobre el rol raíz; **Media** para el resto |
| Rechazo `400` de formato | — | **No se audita** (`architecture.md` §6.6.4) |
| Denegación `403` por `AUTH-002` | `audit_security_log` | `event_type` de denegación de autorización, `severity = 'MEDIA'`, `outcome = 'FAILURE'`. Lo emite la capa de seguridad compartida |
| Fallo no controlado `5xx` | `audit_error_log` | `error_type = 'UNHANDLED'`, `severity = 'ALTA'` |

La severidad es Alta y no Media porque desactivar un rol retira acceso a todos sus portadores a la vez. Es, en efecto, una revocación masiva, aunque el mecanismo sea distinto.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| Actualización de `status` y su evento en `audit_change_log` | **La misma** (Art. V.14) |
| Invalidación de la caché de permisos | Tras el commit, nunca antes |
| Evento en `audit_security_log` | **Independiente**, `REQUIRES_NEW`, enganchada al commit |

## 8. Impacto sobre otros módulos

**`shared/security`** es donde este requerimiento tiene su efecto real. La resolución de permisos consulta el estado del rol, de modo que la invalidación debe alcanzar **la entrada de ese rol**, que es lo único que la caché guarda: `security.md` §4.5 describe una caché de `rol → permisos`, y los permisos efectivos de una persona se calculan como unión de sus roles activos en cada petición. No existe, por tanto, un resultado derivado por usuario que haya que invalidar aparte, y el puerto `RolePermissionCacheInvalidator` tiene un solo método: invalidar por rol. Si algún día se cachease `usuario → permisos efectivos`, invalidar por rol exigiría saber quién lo porta —es decir, resolver quién lo porta en cada escritura— y habría que revisar esta decisión junto con `security.md` §4.5.

**Alcance del despliegue.** La caché es en memoria del proceso. Con más de una instancia del backend, invalidar tras el commit la vacía solo en la que atendió la petición, y las demás seguirían concediendo el permiso de un rol ya desactivado hasta que su entrada caducara. Queda registrado como riesgo (§10); no altera este plan, porque la corrección sería un adaptador distinto del mismo puerto, no un cambio en el caso de uso.

Es la diferencia con `RF-SP-005` y `RF-SP-006`, donde cambia **qué** concede el rol. Aquí cambia **si** concede algo, y eso afecta a todos sus portadores de golpe.

**Ningún rol hijo se ve afectado**, y el plan lo hace explícito porque es la suposición contraria la que resulta intuitiva.

**`RF-SP-005` no debe verificar el estado del rol padre.** Su contención se mide sobre permisos declarados, que no cambian al desactivar. Añadir una comprobación de estado allí sería una regla que nadie pidió y que impediría reorganizar un área suspendida.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Enviar una acción (`activate` / `deactivate`) en lugar del estado destino | Obliga a que el servidor conozca el estado actual para saber si la acción aplica, y hace que repetir la petición no sea neutro. El estado destino es idempotente por construcción |
| Incluir el estado en `PATCH /roles/{id}` | `RF-SP-004` declara que no lo modifica, y sus reglas de rechazo son otras. Juntarlos obligaría a que un solo endpoint aplicara dos conjuntos de reglas según qué campos llegaran |
| Desactivar los roles hijos en cascada | Dejaría sin acceso a personas que nadie pretendía suspender, y reactivar después obligaría a recordar qué estaba activo antes. La contención no lo exige: se mide sobre permisos declarados |
| Rechazar si el rol tiene hijos activos | Convertiría cerrar un área de varios niveles en una secuencia que hay que ordenar a mano, sin ganar ninguna garantía |
| No invalidar la caché y esperar a que expiren los tokens | Convierte «inmediato» en «hasta quince minutos». `RN-SEG-002` dice lo contrario, y es la única parte no trivial de este requerimiento |
| Filtrar por estado en cada resolución en lugar de invalidar | Añade una comprobación al camino más caliente del sistema para evitar una invalidación que ocurre muy de vez en cuando |
| Añadir columna de motivo nulable | Produce un campo casi siempre vacío del que nadie sabe si puede fiarse. El Art. V.13 lo exige donde el registro desaparece; aquí el rol sigue estando |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La invalidación no llega a producirse y el rol sigue concediendo | **Alto** | `CA-SP-050` debe probarse resolviendo permisos de un portador real, no comprobando la columna |
| Con más de una instancia del backend, la invalidación en memoria solo afecta a la que atendió la petición: las demás siguen concediendo el permiso de un rol desactivado | Medio | Registrado el 21-08-2026 y **aceptado**. Con una sola instancia el problema no existe, porque la que escribe es la misma que decide. La corrección, el día que se despliegue una segunda, es un adaptador compartido del mismo puerto —o propagar la invalidación por un canal común— sin tocar `ChangeRoleStatusService`. Afecta igual a `RF-SP-005`, `RF-SP-006` y `RF-SP-009` |
| Se implementa la cascada por parecer lo natural | Medio | Declarado en §8 y en el «no incluye» de la spec |
| `RF-SP-005` añade una comprobación de estado del padre | Medio | Anotado en §8 y en los casos límite de la spec |
| Un administrador se desactiva su propio rol | **Alto** | `RN-SEG-011`, verificado por `CA-SP-054` |
| El rol raíz queda desactivable si deja de ser de sistema | Medio | La verificación es `esRaiz || esDeSistema`, no solo lo segundo |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-049` | Integración | Desactivar y reactivar deja la columna en el valor esperado |
| `CA-SP-050` | Integración | **Resolviendo permisos de un portador**, no leyendo la columna: tras desactivar, ya no concede |
| `CA-SP-051` | Integración | Las asignaciones del rol siguen existiendo tras desactivarlo |
| `CA-SP-052` | Integración | Repetir el mismo estado no genera fila en ninguno de los dos registros |
| `CA-SP-053` | API | Rol de sistema devuelve `409` con `RN-SEG-012` |
| `CA-SP-054` | API | Rol propio del actor devuelve `403` con `RN-SEG-011` |
| `CA-SP-055` | Integración | Una fila en cada registro, con `status` en el diff |
| `CA-SP-157` | Integración | Tras desactivar el padre, un portador del rol hijo conserva sus permisos |
| `CA-SP-158` | API | El rol raíz devuelve `409`, incluso simulando que no fuera de sistema |
| `CA-SP-159` | API | Un cuerpo con motivo devuelve `400`, no se ignora |

`CA-SP-050` es la prueba que decide si este requerimiento está bien implementado. Verificarla leyendo la columna daría verde con una implementación que no invalida nada, que es exactamente el defecto que hay que evitar.
