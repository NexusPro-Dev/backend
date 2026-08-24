# SPEC — `RF-SP-034` Iniciar sesión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-034` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Probar que quien se presenta es quien dice ser, y entregarle las credenciales con las que operará durante su sesión.

## 2. Contexto

Es la única puerta del sistema y el único requerimiento **público**: se ejecuta sin identidad previa, porque establecerla es precisamente su trabajo. Todo lo demás en `SP` presupone un actor autenticado; aquí no hay ninguno.

Esa condición cambia las reglas de cómo se responde. En cualquier otra funcionalidad, un mensaje preciso ayuda a quien se equivocó. Aquí, decir «ese usuario no existe» le confirma a un atacante qué cuentas probar, y decir «contraseña incorrecta» le confirma cuáles existen. Por eso `security.md` §3.2 y §5.5 exigen que la respuesta ante credenciales inválidas **no revele si el usuario existe**, ni en el mensaje ni en el tiempo de respuesta: una comprobación que termina antes cuando el usuario no existe filtra la misma información que el mensaje, solo que más despacio.

Lo que se entrega son dos piezas con propósitos distintos (`security.md` §5.2). Un **token de acceso** firmado, de quince minutos, que viaja en cada petición y se valida sin consultar la base de datos; transporta los códigos de rol de la persona, no sus permisos, y ningún dato personal, porque va firmado pero no cifrado. Y un **refresh token** opaco, de siete días, del que el servidor guarda solo su hash y que puede revocarse de inmediato: es lo que permite cerrar una sesión de verdad y expulsar a alguien comprometido.

La otra defensa es el **bloqueo por intentos fallidos**. Sin él, la contraseña más larga acaba cayendo por fuerza bruta; con él, cada intento fallido acerca la cuenta a un bloqueo de duración creciente. Y cada uno de esos intentos se audita, porque un ataque de fuerza bruta se reconoce por su patrón —muchos usuarios desde una IP, o un usuario desde muchas— y ese patrón solo existe si cada intento dejó rastro (`security.md` §8.2).

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con una cuenta en el sistema | Presenta sus credenciales para obtener una sesión |

No hay permiso asociado: el endpoint es público (`requirements/sp.md` §6.1).

## 4. Alcance

### 4.1 Incluye

- Verificación de las credenciales presentadas.
- Emisión del token de acceso y del refresh token.
- Registro del intento, exitoso o fallido, en la auditoría de seguridad.
- Conteo de intentos fallidos y bloqueo automático de la cuenta al superar el umbral.

### 4.2 No incluye

- Renovar la sesión → `RF-SP-035`.
- Cerrarla → `RF-SP-036`.
- Cambiar o recuperar la contraseña → `RF-SP-037` y `RF-SP-038`. El sistema **no puede recuperar** una contraseña, solo restablecerla (`security.md` §3.2).
- Registrar a la persona: la cuenta debe existir → `RF-SP-024`.
- Liberar un bloqueo antes de que expire → `RF-SP-028`.
- Autenticación de procesos automáticos, que usarán un tipo de identidad distinto todavía sin especificar (`security.md` §3.1).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-002` | Un rol inactivo no concede permisos aunque siga asignado | `security.md` §4.3 |
| `RN-SEG-009` | Los permisos efectivos son la unión de los permisos de los roles activos | `security.md` §4.3 |
| `RNF-SEG-006` | Los eventos de seguridad quedan registrados en la auditoría de seguridad | `security.md` §11 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador de acceso | Sí | Con qué se presenta la persona | **Su nombre de usuario o su correo**, indistintamente. La prohibición del `@` en el nombre de usuario (`RF-SP-024`) hace que ningún valor sea ambiguo |
| Contraseña | Sí | Credencial de la persona | Nunca se registra, ni en claro ni transformada, en ningún log |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Token de acceso | Credencial de vida corta con la que se opera. Transporta los códigos de rol de la persona y ningún dato personal |
| Refresh token | Credencial de vida larga, opaca y revocable, con la que se renueva la sesión |
| Vigencia | Cuánto dura el token de acceso, para que el cliente sepa cuándo renovarlo |

La salida **no incluye** los permisos efectivos de la persona ni sus datos personales. Quien los necesite los consulta después, ya autenticado.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- La cuenta existe, está activa y no está bloqueada.
- La contraseña presentada coincide con la registrada.

Estas precondiciones **no se comunican por separado**, con una excepción: su incumplimiento produce siempre la misma respuesta (`EX-001`) **salvo la cuenta bloqueada**, que sí se identifica como tal (`EX-002`).

**Postcondiciones**

- La persona dispone de un token de acceso y de un refresh token vigentes.
- Si la cuenta tiene pendiente el **cambio obligatorio de contraseña**, la respuesta lo advierte y el resto de endpoints se le niegan hasta que ejecute `RF-SP-037`.
- El refresh token queda persistido **solo como hash**, junto con la IP y el agente de usuario desde los que se abrió la sesión.
- El contador de intentos fallidos de la cuenta vuelve a cero.
- Queda registrado el momento del último inicio de sesión.
- Queda constancia del intento en la auditoría de seguridad, en **transacción independiente** (Art. V.14).

## 8. Flujo principal

1. La persona presenta su identificador de acceso y su contraseña.
2. El sistema comprueba que no se haya superado el límite de intentos por credencial y por origen.
3. El sistema localiza la cuenta por su nombre de usuario o por su correo, y verifica su estado.
4. El sistema compara la contraseña presentada con la registrada, de forma resistente a ataques de temporización.
5. El sistema pone a cero el contador de intentos fallidos y registra el momento del inicio de sesión.
6. El sistema emite el token de acceso con los códigos de rol de la persona, y un refresh token, del que persiste solo el hash.
7. El sistema registra el inicio de sesión exitoso en la auditoría de seguridad, con severidad informativa.
8. El sistema entrega ambas credenciales y la vigencia del token de acceso.

## 9. Flujos alternativos

### FA-001 — Persona sin ningún rol activo

**Cuándo ocurre:** la cuenta es válida y todos sus roles están inactivos. Desde `RN-SP-023` (24-08-2026) esta es la **única** forma de llegar aquí: nadie puede quedarse sin roles.

1. La autenticación **tiene éxito**: la identidad quedó probada, que es lo que este requerimiento decide.
2. El token de acceso se emite sin códigos de rol, o solo con los de roles inactivos, que no conceden nada (`RN-SEG-002`).
3. Toda petición posterior será denegada por autorización. No es un fallo de autenticación y no debe confundirse con uno.

### FA-002 — Cuenta con cambio obligatorio de contraseña pendiente

**Cuándo ocurre:** la credencial la fijó otra persona, al registrar la cuenta (`RF-SP-024`) o al restablecerla (`RF-SP-038`).

1. La autenticación **tiene éxito** y se entregan ambas credenciales. Rechazar aquí dejaría a la persona sin poder cambiar la contraseña, porque necesita una sesión para hacerlo.
2. La respuesta advierte que debe cambiarla.
3. El resto de endpoints le responden que debe cambiarla antes de operar. La única operación admitida con esa marca puesta es `RF-SP-037`, que además es quien la limpia.

## 10. Excepciones

### EX-001 — Credenciales inválidas, cuenta inexistente, inactiva o eliminada

**Condición:** el identificador no corresponde a ninguna cuenta, la contraseña no coincide, o la cuenta está inactiva o eliminada.
**Respuesta del sistema:** **una única respuesta indistinguible para los cuatro casos**, tanto en el mensaje como en el tiempo de respuesta. Si la cuenta existía, incrementa su contador de intentos fallidos. Registra el intento fallido en la auditoría de seguridad con severidad media, identificando la cuenta que se intentó usar como objeto del evento y conservando la IP de origen.

Unificar los cuatro casos es deliberado y tiene un coste: alguien cuya cuenta fue desactivada verá el mismo mensaje que si se hubiera equivocado de contraseña, y no sabrá a qué atenerse. Se acepta porque distinguirlos convierte el endpoint en un verificador de qué cuentas existen y cuáles están activas.

### EX-002 — Cuenta bloqueada

**Condición:** la cuenta está bloqueada —por intentos fallidos o por decisión de un actor (`RF-SP-028`)— y el bloqueo sigue vigente.
**Respuesta del sistema:** rechaza el intento **sin comprobar la contraseña** e informa que la cuenta está bloqueada. Si el bloqueo es automático, indica cuándo expira; si es manual, indica que debe contactarse con un administrador, porque no expira solo.

**Es una excepción consciente a `EX-001`**, y conviene sostener el argumento: quien provocó un bloqueo por fuerza bruta ya sabe que la cuenta existe —fue él quien la bloqueó—, de modo que callarlo no le oculta nada y solo deja al titular legítimo sin entender por qué su contraseña correcta no funciona. Y en el bloqueo **manual** el silencio sería peor todavía: la cuenta no se desbloquea sola, y sin mensaje la persona reintentaría indefinidamente.

La contraseña **no se comprueba** antes de rechazar. Hacerlo permitiría usar el tiempo de respuesta para distinguir una contraseña correcta de una incorrecta sobre una cuenta bloqueada.

### EX-003 — Se alcanza el umbral de intentos fallidos

**Condición:** el intento fallido actual hace que la cuenta alcance el número configurado de fallos consecutivos.
**Respuesta del sistema:** bloquea la cuenta por un tiempo creciente y registra el bloqueo en la auditoría de seguridad con severidad **alta**. La respuesta a quien lo intentó sigue siendo la de `EX-001`.

### EX-004 — Límite de intentos por origen superado

**Condición:** desde la misma credencial o el mismo origen llegan más intentos de los admitidos en la ventana configurada.
**Respuesta del sistema:** rechaza la petición por exceso de peticiones, sin llegar a comprobar credenciales, e informa cuándo puede reintentarse.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador de acceso obligatorio | Debe indicar su usuario y su contraseña. |
| `VAL-002` | Contraseña obligatoria | Debe indicar su usuario y su contraseña. |
| `VAL-003` | Credenciales válidas y cuenta habilitada | Usuario o contraseña incorrectos. |
| `VAL-004` | Cuenta no bloqueada | Su cuenta está bloqueada. |

El mensaje de `VAL-003` es el mismo para todos los casos de `EX-001`, y las validaciones de formato no deben permitir deducir nada sobre la existencia de la cuenta.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-289` | El sistema autentica a una persona con credenciales válidas y le entrega un token de acceso y un refresh token |
| `CA-SP-290` | El token de acceso transporta los códigos de rol de la persona y **ningún** dato personal ni correo |
| `CA-SP-291` | El servidor persiste únicamente el hash del refresh token, nunca su valor |
| `CA-SP-292` | El sistema devuelve la misma respuesta ante usuario inexistente, contraseña incorrecta y cuenta inactiva |
| `CA-SP-293` | El tiempo de respuesta ante un usuario inexistente no es distinguible del de una contraseña incorrecta |
| `CA-SP-294` | El sistema bloquea la cuenta al alcanzar el número configurado de intentos fallidos consecutivos |
| `CA-SP-295` | El contador de intentos fallidos vuelve a cero tras un inicio de sesión exitoso |
| `CA-SP-296` | El sistema registra el inicio de sesión exitoso, el fallido y el bloqueo en la auditoría de seguridad, con las severidades de `security.md` §8.1 |
| `CA-SP-297` | El evento de intento fallido se conserva aunque la transacción de negocio se revierta (Art. V.14) |
| `CA-SP-298` | Ningún registro del sistema contiene la contraseña presentada, ni en claro ni transformada |
| `CA-SP-299` | Una persona sin roles activos se autentica con éxito y recibe un token que no concede nada |
| `CA-SP-375` | La persona se autentica **indistintamente con su nombre de usuario o con su correo**, y con ninguno de los dos si la contraseña no coincide |
| `CA-SP-376` | El sistema bloquea la cuenta al **quinto** intento fallido consecutivo, y la duración del bloqueo crece con cada bloqueo sucesivo hasta un techo declarado |
| `CA-SP-377` | La cuenta bloqueada recibe una respuesta **distinta** de la de credenciales inválidas, y el sistema no comprueba su contraseña antes de rechazar |
| `CA-SP-378` | El bloqueo manual se informa como tal y sin momento de expiración; el automático, con el suyo |
| `CA-SP-379` | Una cuenta con cambio obligatorio pendiente **se autentica** y la respuesta lo advierte |
| `CA-SP-380` | El sistema no limita el número de sesiones simultáneas: dos inicios de sesión producen dos refresh tokens independientes y ambos funcionan |
| `CA-SP-300` | El sistema rechaza por exceso de peticiones los intentos que superan el límite por credencial o por origen |

## 13. Casos límite

- **Cuenta desactivada durante una sesión abierta:** no afecta a este requerimiento, sino a la validación de cada petición. `RF-SP-028` ya revoca sus refresh tokens y exige que su token de acceso deje de admitirse.
- **Contraseña correcta sobre una cuenta eliminada:** se rechaza como `EX-001`. El registro existe para la auditoría, no para entrar.
- **Intentos fallidos alternados con uno exitoso:** el contador se pone a cero en el exitoso. El umbral cuenta fallos **consecutivos**, no acumulados.
- **Bloqueo que expira entre dos intentos:** el siguiente intento se procesa con normalidad, sin intervención de nadie.
- **Cuenta bloqueada manualmente:** no expira sola, de modo que el mensaje debe remitir a un administrador. Es la diferencia con el bloqueo automático, que sí indica cuándo termina.
- **Identificador con arroba:** solo puede ser un correo, porque el nombre de usuario no la admite. Es lo que hace innecesario preguntar al cliente con cuál de los dos se está presentando.
- **Correo que otra persona tuvo y liberó:** identifica a su titular actual. `RF-SP-027` libera el correo anterior al corregirlo, de modo que el identificador siempre resuelve a lo sumo a una cuenta.
- **Muchos intentos sobre una cuenta inexistente:** no hay contador que incrementar, de modo que el bloqueo por cuenta no protege. El límite por origen de `EX-004` es la única defensa en ese caso, y por eso no es opcional.
- **Dos inicios de sesión simultáneos de la misma persona:** se admiten y producen dos refresh tokens independientes. No hay tope de sesiones: el crecimiento se controla con la política de purga de `security.md` §5.5, no rechazando inicios de sesión legítimos.
- **IP de origen detrás de una cadena de proxies:** debe resolverse según el Art. V.15, o el registro de seguridad guardará la IP del proxy y las consultas por origen dejarán de servir.
- **Reloj del servidor desajustado:** afecta a la vigencia del token, que se valida por tiempo. Es un riesgo de operación, no de esta especificación.

## 14. Preguntas abiertas

Ninguna. Las cinco se resolvieron el 21-08-2026, antes de aprobar la especificación. La primera y la cuarta se arrastraron de `RF-SP-024`, aprobada el mismo día.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se inicia sesión con el nombre de usuario, con el correo, o con cualquiera de los dos? | **Con cualquiera de los dos**, arrastrado de la resolución 4 de `RF-SP-024`. Lo que hace viable admitir ambos sin ambigüedad es que el **nombre de usuario no admite el carácter `@`**: ningún valor presentado puede confundirse con el otro tipo, las dos columnas no necesitan compartir espacio de unicidad, y el cliente no tiene que declarar con cuál se está presentando. El sistema busca por ambas y a lo sumo una resuelve |
| 2 | ¿Cuántos intentos fallidos disparan el bloqueo, y cuánto dura? | **Cinco intentos consecutivos**, con duración **creciente y con techo**, todo declarado en configuración. Los cinco dejan margen a quien no recuerda cuál de sus contraseñas usó, sin regalar intentos a un ataque. La progresión encarece el ataque sostenido; **el techo es la parte que no puede faltar**, porque sin él alguien puede dejar la cuenta de otra persona bloqueada durante días provocando fallos a propósito, que es una denegación de servicio contra su titular. `CA-SP-376` fija el umbral como comprobable y deja los valores de la progresión en configuración |
| 3 | ¿La cuenta bloqueada recibe una respuesta distinta? | **Sí**, y es una excepción consciente a `EX-001`, que unifica todo lo demás. El argumento es que **no revela nada nuevo**: quien provocó el bloqueo por fuerza bruta ya sabe que la cuenta existe, porque fue él quien la bloqueó. Callarlo solo perjudica al titular legítimo, que no entiende por qué su contraseña correcta no funciona. Con el **bloqueo manual** de `RF-SP-028` el argumento se refuerza: esa cuenta no se desbloquea sola, y sin mensaje la persona reintentaría indefinidamente. La respuesta distingue ambos orígenes —el automático dice cuándo expira, el manual remite a un administrador— y en ningún caso se comprueba la contraseña antes de rechazar, para no filtrar por tiempo de respuesta lo que el mensaje no dice |
| 4 | ¿La contraseña marcada para cambio obligatorio impide iniciar sesión? | **No: autentica y advierte**, arrastrado de la resolución 2 de `RF-SP-024`. Rechazar dejaría a la persona sin poder cambiarla, porque necesita una sesión para hacerlo. Se entregan ambas credenciales, la respuesta lo advierte, y el resto de endpoints le niegan el paso hasta que ejecute `RF-SP-037`, que es quien limpia la marca (`FA-002`) |
| 5 | ¿Hay límite de sesiones simultáneas por persona? | **No se limita por negocio.** Cada inicio de sesión crea un refresh token más, y el crecimiento se controla con la **política de purga de tokens expirados** que `security.md` §5.5 ya exige, no rechazando inicios de sesión legítimos. Un tope obligaría además a decidir qué ocurre al superarlo, y ambas respuestas posibles —rechazar el inicio, o cerrar la sesión más antigua— sorprenden a alguien que simplemente usa varios dispositivos. `CA-SP-380` deja verificado que dos sesiones simultáneas conviven |
