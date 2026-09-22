# SPEC — `RF-SP-040` Restablecer la propia contraseña olvidada

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-040` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |
| Enmendada el | 26-08-2026 — ver §15 |

---

!!! warning "Aprobada, pero no implementable hasta cerrar D-23"

    El 22-08-2026 quedó decidida la **forma** del canal de envío: infraestructura transversal con puerto publicado, no módulo ni submódulo ([`architecture.md` §15.1](../../../architecture.md)). Eso es lo que permitía aprobar esta especificación.

    Falta el **mecanismo concreto** —proveedor, desacople, reintentos y rebotes—, registrado como **D-23**. Su `plan.md` no puede escribirse antes de esa decisión, porque la mitad de este requerimiento consiste en hacer llegar algo a alguien.

---

## 1. Objetivo

Permitir que quien olvidó su contraseña la restablezca por sí mismo, sin conocer la vigente y sin que ninguna otra persona llegue a conocer la nueva.

## 2. Contexto

Hoy el único camino de vuelta es `RF-SP-038`: un administrador fija una credencial sobre una cuenta ajena. Esa operación existe pese a su coste, y su propia especificación lo dice sin disimulo —**quien la ejecuta obtiene, durante un tiempo, la capacidad de entrar como otra persona**—. El indicador de cambio obligatorio y la caducidad de la credencial provisional acotan la ventana, pero no la eliminan.

Y hay un problema que ninguna defensa acota: **no escala a los consumidores**. Un sistema con clientes y estudiantes no puede sostener que cada olvido de contraseña pase por un administrador que además queda conociendo la credencial. Es la clase de proceso que acaba en credenciales compartidas por teléfono.

La diferencia esencial con `RF-SP-038` es quién termina conociendo el secreto: aquí, **solo el titular**. Ninguna otra persona interviene, y por eso esta operación no necesita marcar la cuenta para cambio obligatorio: no hay ventana que cerrar.

A cambio, es la única operación de escritura **pública** del módulo junto al inicio de sesión, y eso la convierte en superficie de ataque: cualquiera puede invocarla contra la cuenta de otro. De ahí salen las defensas de §4.1.

Nace de la aprobación de `RF-SP-037` el 21-08-2026.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con una cuenta en el sistema | Solicita y ejecuta el restablecimiento de su propia contraseña |
| Cualquiera, sin cuenta ni sesión | Puede **invocar** la solicitud. Es público, y esa es la razón de que la respuesta no revele nada |

## 4. Alcance

El requerimiento comprende **dos operaciones encadenadas**, ambas públicas, que se especifican juntas porque por separado no significan nada: una emite el permiso temporal de restablecer, la otra lo consume.

### 4.1 Incluye

- **Solicitud**: la persona indica su identidad y el sistema le hace llegar, por el canal que se decida, un permiso temporal de un solo uso.
- **Confirmación**: presentando ese permiso y una contraseña nueva, la credencial queda sustituida.
- **Respuesta indistinguible** ante identidades existentes e inexistentes, para que la solicitud no sirva para averiguar quién tiene cuenta.
- **Limitación de tasa** sobre la solicitud, por identidad y por origen (`security.md` §5.5).
- Invalidación del permiso temporal al usarse, al caducar, o al emitirse uno nuevo.
- Revocación de todas las sesiones abiertas de esa persona al completarse.
- **Aviso al titular** de que su contraseña fue restablecida, por el mismo canal. Es lo que le permite enterarse si **no fue él quien lo pidió**, que es el caso de abuso contra el que las demás defensas valen poco. Cierra también la condición de disparo que `RF-SP-038` dejó anotada en su resolución 3.

### 4.2 No incluye

- Cambiar la contraseña conociendo la vigente → `RF-SP-037`.
- Restablecer la contraseña de otra persona → `RF-SP-038`, que es administrativa y exige permiso.
- **Levantar un bloqueo**, ni automático ni manual → `RF-SP-028`. Mismo criterio que `RF-SP-038` (`CA-SP-394`): una operación sobre la credencial no deshace una decisión de seguridad.
- Desbloquear una cuenta `INACTIVO`: quien no tiene acceso no lo recupera por cambiar su contraseña.
- Recuperar la contraseña anterior: es imposible por diseño (`security.md` §3.2).
- Verificar que el correo de la cuenta pertenece a quien dice: es un problema anterior, anotado como riesgo en `RF-SP-027`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RNF-SEG-006` | Los eventos de seguridad quedan registrados en la auditoría de seguridad | `security.md` §11 |
| `RNF-FIA-001` | El envío es desacoplado de la respuesta y su fallo no la altera | `requirements/sp.md` §7 |

La política de contraseña está definida en `security.md` §3.2 y no se redefine aquí.

## 6. Datos

### 6.1 Entrada — solicitud

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identidad | Sí | Nombre de usuario o correo de la persona | Se admiten ambos, igual que en `RF-SP-034`. No se comprueba que exista: la respuesta es la misma en todo caso |

### 6.2 Entrada — confirmación

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Permiso temporal | Sí | El que se hizo llegar en la solicitud | De un solo uso y con vigencia corta. Inválido si ya se usó, si caducó o si se emitió otro después |
| Contraseña nueva | Sí | Credencial que la persona elige | Debe cumplir la política mínima. Nunca se registra en ningún log |

### 6.3 Salida

| Dato | Descripción |
|---|---|
| Confirmación de la solicitud | Mensaje **idéntico** exista o no la identidad indicada. No informa de si se envió nada ni a dónde |
| Confirmación del restablecimiento | Resultado de la operación, sin cuerpo de datos ni credencial alguna |

Ni la contraseña ni el permiso temporal aparecen en ninguna respuesta.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- Ninguna para la solicitud: es pública y no exige autenticación.
- Para la confirmación, el permiso temporal presentado está vigente y no se ha usado.

**Postcondiciones**

- La contraseña queda sustituida y almacenada con Argon2id.
- El permiso temporal queda **consumido**: no sirve una segunda vez.
- **Todos** los refresh tokens de la persona quedan revocados con motivo `ACCESO_RETIRADO`, y sus tokens de acceso vigentes dejan de admitirse.
- La cuenta **no** queda marcada para cambio obligatorio: la contraseña la eligió su titular y nadie más la conoce. Es la diferencia deliberada con `RF-SP-038`.
- Su estado, sus roles y su membresía no cambian. Si estaba bloqueada, **sigue bloqueada**.
- Queda constancia en la auditoría de seguridad con severidad alta, tanto de la solicitud como del restablecimiento, y sin ningún dato de la credencial.

## 8. Flujo principal

1. La persona solicita restablecer su contraseña e indica su nombre de usuario o su correo.
2. El sistema responde de forma **idéntica** exista o no esa identidad, sin revelar nada.
3. Si la identidad existe, el sistema emite un permiso temporal de un solo uso, invalida cualquier otro anterior de esa persona y lo hace llegar por el canal establecido.
4. El sistema registra la solicitud en la auditoría de seguridad.
5. La persona presenta el permiso temporal junto con la contraseña nueva.
6. El sistema verifica que el permiso esté vigente, no se haya usado y no haya sido sustituido.
7. El sistema verifica que la contraseña nueva cumpla la política mínima.
8. El sistema sustituye la credencial, consume el permiso temporal y revoca todas las sesiones de esa persona.
9. El sistema registra el restablecimiento en la auditoría de seguridad, con severidad alta, y hace llegar al titular el aviso de que su contraseña cambió.
10. El sistema confirma la operación.

Los pasos 3 y 9 **no bloquean la respuesta**: el envío ocurre desacoplado (`RNF-FIA-001`). En el paso 3 es una exigencia de seguridad, no de rendimiento —esperar al envío haría que el tiempo de respuesta delatara si la identidad existe—, y en el paso 9 es su consecuencia natural.

## 9. Flujos alternativos

### FA-001 — La identidad indicada no existe

**Cuándo ocurre:** nadie tiene ese nombre de usuario ni ese correo.

1. El sistema **no emite** ningún permiso temporal y no envía nada.
2. La respuesta es **idéntica** a la del caso exitoso, incluido el tiempo de respuesta hasta donde sea razonable.
3. Se registra el intento en la auditoría de seguridad: una ráfaga de solicitudes sobre identidades inexistentes es un reconocimiento en curso.

### FA-002 — Segunda solicitud antes de usar la primera

**Cuándo ocurre:** la persona pide el restablecimiento dos veces seguidas.

1. El permiso temporal anterior queda **invalidado** y solo sirve el último.
2. Evita que queden varios permisos vivos a la vez, cada uno una vía de entrada abierta.

### FA-003 — La cuenta está bloqueada o inactiva

**Cuándo ocurre:** la persona no puede autenticarse por su estado.

1. La solicitud se admite y el restablecimiento se completa con normalidad.
2. **La persona sigue sin poder entrar**: el estado no cambia. Preparar la credencial no concede acceso, mismo criterio que `RF-SP-038` §13.

## 10. Excepciones

### EX-001 — Permiso temporal inválido, caducado o ya usado

**Condición:** el permiso presentado no existe, caducó, ya se consumió o fue sustituido por otro posterior.
**Respuesta del sistema:** rechaza la operación con **una sola respuesta para los cuatro casos**. Distinguirlos diría a quien prueba permisos al azar cuál de ellos estuvo a punto de acertar.

### EX-002 — Contraseña que no cumple la política

**Condición:** la contraseña nueva incumple alguna regla de la política mínima.
**Respuesta del sistema:** rechaza la operación e informa qué regla incumple, sin reproducir la contraseña en el mensaje ni en ningún registro. **No consume el permiso temporal**: el error es de la persona legítima, y obligarla a pedir otro por escribir una contraseña corta sería castigar el intento correcto.

### EX-003 — Demasiadas solicitudes

**Condición:** se supera el límite de tasa configurado, por identidad o por origen.
**Respuesta del sistema:** rechaza la solicitud sin revelar si la identidad existe, y **dice cuántos segundos hay que esperar**. Sin este límite, la operación permite inundar de correos a una persona real, que es acoso, y sondear identidades en masa.

**Superar la cota cuesta una espera fija** y no solo aguardar a que la ventana deslice: es lo único que impide que quien la supera siga pidiendo al ritmo que la ventana le vaya dejando. La espera **no se alarga al insistir** —se fija una vez, en la petición que cruza la cota—, porque renovarla convertiría unas cuantas peticiones de más en un bloqueo indefinido para quien tenga un cliente que reintenta solo. Los números viven en configuración y no en esta spec ([`security.md` §5.5.1](../../../security.md)): desde el 26-08-2026, **cinco por minuto y cinco minutos de espera**.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identidad obligatoria en la solicitud | Debe indicar su nombre de usuario o su correo. |
| `VAL-002` | Permiso temporal obligatorio en la confirmación | El enlace de restablecimiento no es válido. |
| `VAL-003` | Contraseña nueva obligatoria | Debe indicar la contraseña nueva. |
| `VAL-004` | Contraseña nueva conforme a la política mínima | La contraseña no cumple la política de seguridad. |
| `VAL-005` | Permiso temporal vigente, no usado y no sustituido | El enlace de restablecimiento no es válido o ha caducado. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-456` | Una persona completa el restablecimiento y se autentica con la contraseña nueva, sin intervención de nadie |
| `CA-SP-457` | La respuesta a la solicitud es **idéntica** para una identidad existente y para una inexistente |
| `CA-SP-458` | El permiso temporal **no sirve dos veces**: el segundo intento se rechaza |
| `CA-SP-459` | El permiso temporal caducado se rechaza, con la misma respuesta que uno inexistente |
| `CA-SP-460` | Emitir un permiso nuevo **invalida** el anterior de esa misma persona |
| `CA-SP-461` | Una contraseña que no cumple la política se rechaza **sin consumir** el permiso temporal |
| `CA-SP-462` | Al completarse, todos los refresh tokens de la persona quedan revocados y sus tokens de acceso dejan de admitirse |
| `CA-SP-463` | La cuenta **no** queda marcada para cambio obligatorio de contraseña |
| `CA-SP-464` | El restablecimiento **no** levanta un bloqueo vigente, ni automático ni manual |
| `CA-SP-465` | El restablecimiento **no** cambia el estado de la cuenta: una cuenta inactiva sigue inactiva |
| `CA-SP-466` | Ni la contraseña ni el permiso temporal aparecen en ninguna respuesta ni en ningún registro |
| `CA-SP-467` | El sistema rechaza la solicitud al superar el límite de tasa, sin revelar si la identidad existe |
| `CA-SP-492` | El sistema, al superar la cota, responde con la **espera fija** configurada y no con lo que le quede a la ventana |
| `CA-SP-493` | El sistema **no alarga la espera** cuando se insiste durante ella: el número que devuelve decrece |
| `CA-SP-468` | El sistema registra en la auditoría de seguridad, con severidad alta, tanto la solicitud como el restablecimiento |
| `CA-SP-469` | La auditoría de seguridad registra también las solicitudes sobre identidades inexistentes |
| `CA-SP-473` | El **tiempo de respuesta** a la solicitud no permite distinguir una identidad existente de una inexistente: la respuesta no espera al envío |
| `CA-SP-474` | Al completarse el restablecimiento, el titular recibe el **aviso** de que su contraseña cambió |
| `CA-SP-475` | Un fallo en el envío **no altera** la respuesta de la solicitud ni la del restablecimiento |

## 13. Casos límite

- **Cuenta con cambio obligatorio pendiente que usa esta vía:** la marca queda limpia, igual que con `RF-SP-037`. Es un camino legítimo: quien recibió una credencial provisional de un administrador y la olvidó antes de usarla.
- **Permiso temporal solicitado y usado desde dispositivos distintos:** se admite. Vincularlo al origen rompería el caso normal —se pide en el ordenador y se abre el correo en el teléfono—.
- **Dos confirmaciones concurrentes con el mismo permiso:** ambas se serializan; la primera lo consume y la segunda se rechaza como inválido.
- **Persona sin correo utilizable:** el correo es obligatorio y único desde `RF-SP-024`, de modo que siempre hay uno. Que **sea suyo** es otra cuestión, y es el riesgo anotado en `RF-SP-027`: sin verificación del correo, quien lo cambió por error queda sin vía de vuelta, y quien lo cambió con mala intención se lleva la cuenta. Esta funcionalidad **hereda** ese riesgo y lo agrava, porque convierte el correo en la llave de la cuenta.
- **Permiso temporal emitido y la persona nunca lo usa:** caduca solo. No queda nada abierto.
- **Solicitud sobre una cuenta eliminada:** se trata como identidad inexistente. No se emite nada, y la respuesta es la misma que en cualquier otro caso.
- **Reloj del sistema y caducidad:** la vigencia se mide en el servidor. Ningún dato que venga de fuera decide si el permiso sigue vivo.
- **El envío falla y nadie se entera por la respuesta:** es la consecuencia asumida del desacople (`RNF-FIA-001`). La persona pide el restablecimiento, recibe la confirmación de siempre y no le llega nada. El tratamiento de ese fallo —reintentos y registro— forma parte de **D-23**, y sin él el síntoma es indistinguible de un correo en la carpeta de no deseado.
- **Aviso de cambio usado para molestar:** quien conozca el correo de alguien puede provocarle avisos repetidos. Lo acota el límite de tasa de `EX-003`, que es el mismo que protege la solicitud.

## 14. Preguntas abiertas

Ninguna. Las cinco se resolvieron el 22-08-2026, antes de aprobar la especificación. La primera alcanza a todo el sistema y se resolvió **fuera** de este documento, en `architecture.md`; la segunda, en `security.md`.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Cómo se incorpora el canal de envío? | **Infraestructura transversal con puerto publicado**, registrada en [`architecture.md` §15.1](../../../architecture.md): ni submódulo de `SP` ni módulo propio. Cada módulo que envíe algo declara en su propio requerimiento **qué** envía y **cuándo**. Se descartó el submódulo porque haría a `SP` dueño de los envíos de academia y comisiones, que no son suyos; y el módulo propio porque fijaría un código inmutable sobre un alcance que `modules.md` §6 dice expresamente que no puede fijarse todavía —el mismo error que se evitó con la red comercial—. **Lo que queda pendiente es el mecanismo concreto, D-23**, y sin él no puede escribirse el `plan.md` |
| 2 | ¿Cuánto dura el permiso temporal? | **Treinta minutos**, fijados el 22-08-2026 y declarados en configuración desde [`security.md` §3.2](../../../security.md) junto al resto de parámetros de credenciales, no en esta spec: así se ajustan sin enmendar un requerimiento. Se descartaron quince minutos —acotan más, pero multiplican las solicitudes repetidas de quien llegó tarde, y cada repetición es otro correo— y sesenta, lo habitual en la industria, que deja una hora de exposición por solicitud. La spec fija lo que no es negociable: un solo uso, y emitir uno nuevo invalida el anterior |
| 3 | ¿Se notifica al titular de que su contraseña cambió? | **Sí.** Es la única forma de que se entere si no fue él quien lo pidió, que es exactamente el caso de abuso contra el que las demás defensas valen poco: quien completa el flujo con un correo ajeno ya tiene la cuenta. Cierra la condición de disparo que `RF-SP-038` dejó anotada en su resolución 3. El coste —que el aviso puede usarse para molestar— lo acota el mismo límite de tasa de `EX-003`, y queda declarado en §13 |
| 4 | ¿Se admite para cuentas bloqueadas? | **Sí, sin levantar el bloqueo** (`FA-003`), por coherencia con `CA-SP-394` de `RF-SP-038`. Rechazarlo revelaría que la cuenta está bloqueada, y esa fuga rompería la defensa central de esta operación. El riesgo residual es acotado: fijar una contraseña sobre una cuenta que no puede autenticarse **no concede acceso**, y el bloqueo solo lo levanta `RF-SP-028` |
| 5 | ¿La respuesta indistinguible se sostiene en el tiempo de respuesta? | **Sí, y por eso el envío es desacoplado** (`RNF-FIA-001`). Igualar solo el mensaje dejaría la defensa declarada pero no real: emitir el permiso y enviar cuesta cientos de milisegundos más que no hacer nada, y eso se mide desde fuera con un cronómetro. Se descartó el retardo uniforme porque penaliza a todo el mundo y vuelve a filtrar en cuanto el envío se ralentiza por encima del retardo elegido. La consecuencia asumida —un fallo de envío no se refleja en la respuesta— está en §13 y su tratamiento forma parte de D-23 |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **`EX-003` gana la espera fija.** Por decisión del responsable del proyecto, superar la cota de solicitudes deja de significar «aguarde a que la ventana deslice» y pasa a costar una espera declarada, que el rechazo comunica. La spec fija **lo que no es negociable** —que la espera exista, que se comunique y que **no se alargue al insistir**— y deja los números en configuración (`security.md` §5.5.1), donde el 26-08-2026 quedan en **cinco por minuto y cinco minutos de espera**, frente a las tres por hora anteriores. Dos criterios nuevos, `CA-SP-492` y `CA-SP-493`. **Consecuencia declarada y no resuelta**: la cota sube unas veinte veces, y lo que se multiplica por veinte es cuántos correos puede provocar un desconocido en la bandeja de una persona real — §13 ya anotaba ese abuso como acotado por este mismo límite. | Responsable técnico |
| 0.1.0 | 22-08-2026 | Redacción inicial, aprobada en su compuerta. Las preguntas abiertas se resolvieron antes de aprobar; ver §14. | Responsable técnico |
