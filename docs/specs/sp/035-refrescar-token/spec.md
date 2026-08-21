# SPEC — `RF-SP-035` Refrescar el token de acceso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-035` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Prolongar la sesión de quien ya se autenticó, sin volver a pedirle la contraseña y sin que el sistema pierda la capacidad de expulsarlo.

## 2. Contexto

El token de acceso dura quince minutos. Sin este requerimiento, la gente tendría que volver a escribir su contraseña cada cuarto de hora, y esa vida corta —que es lo que permite validar peticiones sin consultar la base de datos— sería insostenible. Refrescar es lo que hace viable la decisión D-08.

La pieza que lo permite es el refresh token: opaco, de siete días, y del que el servidor guarda solo su hash. Cada uso lo **revoca y emite uno nuevo**, conservando el vínculo con el que lo reemplazó. Esa rotación no es un adorno: es lo que convierte el robo de un refresh token en algo detectable.

El mecanismo es este. Si alguien roba un refresh token y lo usa, el legítimo dueño usará después el suyo —que ya fue revocado por el ladrón— y el sistema verá llegar un token revocado. Y al revés: si el dueño lo usa primero, el ladrón presentará el revocado. En ambos casos aparece la misma señal, y la única lectura razonable es que **hay dos copias del mismo token en circulación**. La respuesta es contundente y tiene que serlo: se revoca **toda la familia de tokens de esa sesión**, ambos quedan fuera y la persona debe autenticarse de nuevo. Sin esta regla, la rotación no protege de nada: el ladrón simplemente seguiría rotando.

El otro trabajo de esta operación es menos visible y no menos importante. Refrescar es el momento en que la sesión vuelve a pasar por la base de datos, y por tanto **el único punto en que se puede revalidar el estado de la persona** sin romper el diseño sin estado. Si alguien fue desactivado, bloqueado o eliminado mientras tenía sesión abierta, aquí es donde debe detenerse.

### Dos límites que la rotación por sí sola no pone

**La sesión tiene un techo absoluto.** Si cada refresco extendiera la vigencia siete días más, una sesión que se refresque sola no caducaría nunca y la contraseña dejaría de tener efecto sobre ella: bastaría con robar un refresh token y rotarlo con disciplina para quedarse dentro indefinidamente. Por eso la **familia** tiene una duración total declarada en configuración, contada desde el inicio de sesión, tras la cual hay que volver a autenticarse aunque la cadena siga viva.

**No todo token revocado es una señal de robo.** Un token puede estar revocado por tres motivos muy distintos: porque se rotó, porque la persona cerró sesión, o porque se le retiró el acceso. Solo el primero significa que hay dos copias en circulación. Por eso cada revocación **registra su motivo**, y `EX-001` se dispara únicamente cuando ese motivo fue la rotación. Sin ese dato, cerrar sesión y reintentar produciría un evento de severidad alta falso en cada cliente mal comportado, y el registro de seguridad acabaría llenándose de incidentes que no lo son.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con una sesión abierta | Presenta su refresh token para obtener credenciales nuevas |

No hay permiso asociado: el endpoint es público y su autorización es el propio refresh token (`requirements/sp.md` §6.1).

## 4. Alcance

### 4.1 Incluye

- Validación del refresh token presentado: que exista, que no haya expirado y que no esté revocado.
- Revalidación del estado de la persona.
- Emisión de un token de acceso nuevo y rotación del refresh token.
- Detección de reutilización de un refresh token revocado, con revocación de toda la familia de la sesión.

### 4.2 No incluye

- Autenticar con contraseña → `RF-SP-034`.
- Cerrar la sesión → `RF-SP-036`.
- Ampliar la sesión indefinidamente: la familia caduca al agotar la **duración máxima de sesión**, contada desde el inicio de sesión y no desde el último refresco.
- Cambiar los datos de la persona o sus roles: el token nuevo refleja los que tenga en ese momento, pero esta operación no los modifica.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-009` | Los permisos efectivos son la unión de los permisos de los roles activos | `security.md` §4.3 |
| `RNF-SEG-006` | Los eventos de seguridad quedan registrados en la auditoría de seguridad | `security.md` §11 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Refresh token | Sí | Credencial de vida larga entregada al iniciar sesión o en el refresco anterior | Se envía únicamente a este endpoint. Nunca se registra en ningún log, ni en claro ni transformado |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Token de acceso | Token nuevo, con los códigos de rol **vigentes** de la persona |
| Refresh token | Token nuevo que sustituye al presentado, que queda revocado |
| Vigencia | Cuánto dura el token de acceso nuevo |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El refresh token presentado existe, no ha expirado y no está revocado.
- La persona a la que pertenece existe, está activa y no está bloqueada ni eliminada.

**Postcondiciones**

- El refresh token presentado queda **revocado**, con el vínculo al que lo reemplaza.
- La persona dispone de un token de acceso nuevo y de un refresh token nuevo.
- El token de acceso nuevo refleja los roles **vigentes** de la persona, no los que tenía al iniciar sesión.
- La vigencia total de la sesión **no se amplía**: el techo se cuenta desde el inicio de sesión, no desde el último refresco.
- La revocación del token presentado queda registrada con su **motivo**: rotación.
- **No se registra evento en la auditoría de seguridad**: el refresco exitoso no es un evento del catálogo de `security.md` §8.1.

## 8. Flujo principal

1. La persona presenta su refresh token.
2. El sistema comprueba que no se haya superado el límite de refrescos por origen.
3. El sistema localiza el token por su hash y verifica que exista y no haya expirado.
4. El sistema verifica que el token no esté revocado, y —si lo está— con qué motivo.
5. El sistema verifica que la familia no haya agotado la duración máxima de sesión.
6. El sistema verifica que la persona exista, esté activa y no esté bloqueada ni eliminada.
7. El sistema revoca el token presentado con motivo «rotación» y emite uno nuevo, dejando registrado el vínculo entre ambos.
8. El sistema emite un token de acceso nuevo con los códigos de rol vigentes de la persona.
9. El sistema entrega ambas credenciales y la vigencia del token de acceso.

## 9. Flujos alternativos

### FA-001 — Los roles de la persona cambiaron desde el inicio de sesión

**Cuándo ocurre:** se le asignaron o retiraron roles mientras tenía la sesión abierta.

1. El token de acceso nuevo se emite con los roles **vigentes**, no con los del token anterior.
2. Es el punto en el que se cierra la latencia de hasta quince minutos que `security.md` §4.5 declara para `RF-SP-030`.

## 10. Excepciones

### EX-001 — Reutilización de un refresh token revocado

**Condición:** el token presentado existe y fue revocado **por rotación**, es decir, por un uso anterior. No aplica cuando la revocación se debió a un cierre de sesión o al retiro del acceso, que caen en `EX-004`.
**Respuesta del sistema:** asume robo de credenciales. **Revoca toda la familia de tokens de esa sesión**, de modo que ni el legítimo dueño ni quien lo robó pueden continuar, rechaza la petición y obliga a autenticarse de nuevo. Registra el evento en la auditoría de seguridad con severidad **alta**, identificando el token **por su registro, nunca por su valor** (`security.md` §8.3).

El coste está asumido: si la reutilización fue un accidente del cliente —dos pestañas refrescando a la vez, un reintento tras un error de red—, la persona pierde la sesión sin que nadie le haya robado nada. Se acepta porque no hay forma de distinguir el accidente del robo, y equivocarse en el otro sentido deja al ladrón dentro.

### EX-002 — Refresh token inexistente o expirado

**Condición:** el token no corresponde a ninguna sesión, o su vigencia terminó.
**Respuesta del sistema:** rechaza la petición con una respuesta que no distingue ambos casos, e indica que debe iniciarse sesión de nuevo. **No** revoca ninguna familia: un token que no existe no identifica ninguna sesión que revocar.

### EX-003 — La persona ya no puede autenticarse

**Condición:** el usuario está inactivo, bloqueado o eliminado.
**Respuesta del sistema:** rechaza la petición, revoca el token presentado y no emite ninguno nuevo. Es el punto de control que impide que una sesión sobreviva a la desactivación de su titular.

### EX-004 — Token revocado por cierre de sesión o por retiro del acceso

**Condición:** el token existe y está revocado, pero **no por rotación**: lo revocó `RF-SP-036`, `RF-SP-028`, `RF-SP-031` o un cambio de contraseña.
**Respuesta del sistema:** rechaza la petición con la misma respuesta que `EX-002` e indica que debe iniciarse sesión de nuevo. **No revoca ninguna familia y no registra evento de severidad alta:** no hay dos copias en circulación, solo un cliente que reintenta con una credencial que el sistema retiró a propósito.

Distinguir este caso de `EX-001` es la razón por la que cada revocación guarda su motivo. Sin ese dato, cerrar sesión y reintentar sería indistinguible de un robo.

### EX-005 — Sesión que agotó su vigencia total

**Condición:** la cadena sigue viva y sin revocar, pero la familia superó la duración máxima de sesión contada desde el inicio.
**Respuesta del sistema:** rechaza la petición, revoca la familia e indica que debe iniciarse sesión de nuevo. No es un incidente: es el techo funcionando, y se registra con la severidad de un cierre, no de un robo.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Refresh token obligatorio | La sesión no es válida. Inicie sesión de nuevo. |
| `VAL-002` | Refresh token existente, vigente y no revocado | La sesión no es válida. Inicie sesión de nuevo. |
| `VAL-003` | La persona está habilitada para autenticarse | La sesión no es válida. Inicie sesión de nuevo. |

Las tres comparten mensaje de forma deliberada: el cliente no debe poder deducir de la respuesta si el token fue robado, si expiró o si la cuenta fue desactivada.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-301` | El sistema entrega un token de acceso nuevo y un refresh token nuevo ante un refresh token válido |
| `CA-SP-302` | El refresh token presentado queda revocado tras usarse, y no sirve una segunda vez |
| `CA-SP-303` | El sistema conserva el vínculo entre el token revocado y el que lo reemplazó |
| `CA-SP-304` | Presentar un refresh token ya revocado revoca **toda la familia** de esa sesión y obliga a autenticarse de nuevo |
| `CA-SP-305` | La reutilización de un token revocado se registra en la auditoría de seguridad con severidad alta |
| `CA-SP-306` | Ningún registro contiene el valor de un refresh token, ni siquiera en el evento de reutilización |
| `CA-SP-307` | El sistema rechaza el refresco cuando la persona está inactiva, bloqueada o eliminada, y revoca el token presentado |
| `CA-SP-308` | El token de acceso nuevo refleja los roles vigentes de la persona, no los que tenía al iniciar sesión |
| `CA-SP-309` | Un refresh token inexistente o expirado se rechaza con la misma respuesta, sin revocar ninguna familia |
| `CA-SP-381` | La familia caduca al agotar la **duración máxima de sesión**, aunque la cadena se haya refrescado sin interrupción |
| `CA-SP-382` | Cada revocación registra su **motivo**, y solo el motivo «rotación» dispara la revocación de familia |
| `CA-SP-383` | Cerrar sesión y reintentar el refresco **no** produce evento de severidad alta ni revoca la familia |
| `CA-SP-384` | El sistema rechaza por exceso de peticiones los refrescos que superan el límite por origen |
| `CA-SP-385` | El refresco **exitoso** no deja fila en `audit_security_log` |
| `CA-SP-310` | El servidor localiza el token por su hash y nunca almacena su valor |

## 13. Casos límite

- **Dos refrescos concurrentes con el mismo token:** uno gana y el otro cae en `EX-001`, con la familia revocada. Es el falso positivo más probable y el motivo por el que el cliente debe serializar sus refrescos.
- **Refresco justo cuando el token expira:** el resultado depende del instante. Se rechaza como expirado y la persona vuelve a autenticarse; no debe producirse un estado intermedio.
- **Persona desactivada mientras refresca:** `RF-SP-028` ya revocó sus tokens, de modo que el refresco encuentra el token revocado. Debe resolverse como `EX-003` y no como reutilización: revocar por desactivación no es señal de robo, y tratarlo como tal registraría un evento de severidad alta falso.
- **Cambio de contraseña durante la sesión:** `security.md` §5.5 obliga a revocar todos los refresh tokens, de modo que el refresco siguiente falla y la persona vuelve a autenticarse. Es el comportamiento buscado.
- **Refresco desde una IP distinta de la del inicio de sesión:** se admite. Es lo normal en redes móviles, y rechazarlo produciría más falsos positivos que detecciones.
- **Familia de tokens de siete días encadenando refrescos cada quince minutos:** produce cientos de filas revocadas por sesión. La política de purga de `security.md` §5.5 no es opcional.
- **Refresh token de un usuario eliminado:** se rechaza, y su registro se conserva como el resto de su historia.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación. Las dos primeras obligaron a añadir a §2 el apartado sobre los límites que la rotación por sí sola no pone, y a `security.md` §5.4 y §5.5 sus enmiendas correspondientes.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La familia de tokens tiene una vigencia total, o se renueva indefinidamente? | **Tiene un techo absoluto**, declarado en configuración y contado **desde el inicio de sesión**, no desde el último refresco. Sin él, una sesión que se refresque sola no caduca nunca y la contraseña deja de tener efecto sobre ella: bastaría con robar un refresh token y rotarlo con disciplina para permanecer dentro indefinidamente, que es justo lo que la rotación pretende impedir. Agotar el techo **no es un incidente** —`EX-005` lo trata como un cierre, no como un robo—, y la comodidad que se pierde es real: quien usa el sistema a diario tendrá que volver a escribir su contraseña cada cierto tiempo |
| 2 | ¿Cómo se distingue el token revocado por robo del revocado por desactivación o por cierre de sesión? | **Cada revocación registra su motivo**, y `EX-001` se dispara únicamente cuando ese motivo fue la **rotación**. Un token revocado por `RF-SP-036`, `RF-SP-028`, `RF-SP-031` o por un cambio de contraseña cae en `EX-004`: se rechaza con la misma respuesta genérica, pero **sin revocar familia y sin evento de severidad alta**. Sin ese dato, cerrar sesión y reintentar sería indistinguible de un robo, y el registro de seguridad se llenaría de incidentes falsos hasta volverse inútil — que es la forma más silenciosa de perder una defensa |
| 3 | ¿El refresco debe tener límite de tasa? | **Sí, por origen, y más holgado que el del inicio de sesión.** `security.md` §5.5 solo lo exigía para el inicio de sesión, pero este endpoint es igualmente **público** y consulta la base de datos en cada llamada, de modo que sin cota es un vector de carga. Se declara más holgado porque un cliente legítimo refresca cada quince minutos, no cada segundo. Queda escrito en `security.md` §5.5 para que no dependa de que alguien lo recuerde al implementar |
| 4 | ¿Se registra en la auditoría de seguridad el refresco exitoso? | **No.** El catálogo de `security.md` §8.1 es cerrado y no lo incluye. Registrarlo multiplicaría el volumen de `audit_security_log` por el número de refrescos —decenas por jornada y por persona— sin responder ninguna pregunta que el inicio de sesión no responda ya, y ese registro es de **retención prolongada** y no se purga sin decisión documentada (Art. XV.8). En el registro quedan los cuatro eventos que sí dicen algo: el inicio, el cierre, el intento fallido y la reutilización. `CA-SP-385` verifica la ausencia |
