# SPEC — `RF-SP-038` Restablecer la contraseña de un usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-038` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Devolver el acceso a quien perdió su contraseña, poniéndole una nueva sin conocer la anterior.

## 2. Contexto

Es la salida para el caso más común de todos: alguien olvidó su contraseña y no puede entrar. `RF-SP-037` no sirve, porque exige conocer la vigente, y el sistema no puede recuperarla: se guarda con Argon2id y solo puede sustituirse (`security.md` §3.2).

Es también la operación **más delicada del módulo**, y conviene no disimularlo. Quien la ejecuta obtiene, durante un tiempo, la capacidad de entrar como otra persona: fija una credencial que conoce sobre una cuenta que no es suya. Ninguna otra operación de `SP` concede eso. Ni siquiera asignar el rol raíz, porque eso deja rastro atribuible a quien lo hizo, mientras que entrar con la credencial de otro produce actividad atribuida a esa otra persona.

De ahí salen las tres defensas que esta especificación impone. La primera, un **permiso propio**, `users:reset-password`, separado de `users:update`: quien administra datos de usuarios no restablece credenciales por el mero hecho de administrarlos. La segunda, que la cuenta afectada quede marcada para **cambio obligatorio de contraseña**, de modo que la ventana en que el administrador conoce la credencial dure hasta el primer inicio de sesión y no más. La tercera, un evento de auditoría de seguridad de severidad alta, porque esta operación es la primera que hay que revisar cuando alguien pregunta cómo entró alguien donde no debía.

Mientras no exista el flujo de autoservicio de `RF-SP-040`, este es el único camino de vuelta para quien olvidó su contraseña, y por eso existe pese a su coste.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Restablece la contraseña de cualquier usuario |
| Administrador | Restablece la contraseña de los usuarios, si posee el permiso |

El permiso `users:reset-password` no acompaña automáticamente a `users:update`: se concede por separado, igual que los cuatro permisos de auditoría (`security.md` §4.4).

## 4. Alcance

### 4.1 Incluye

- Sustitución de la contraseña de otra persona, sin conocer la anterior.
- Marcado de la cuenta para cambio obligatorio, con **caducidad** de la credencial provisional.
- Revocación de todas las sesiones abiertas de esa persona.
- Registro del restablecimiento en la auditoría de seguridad.

### 4.2 No incluye

- Cambiar la propia contraseña → `RF-SP-037`, que exige conocer la vigente. Esta operación **rechaza** aplicarse sobre la cuenta del actor.
- Recuperar o mostrar la contraseña anterior: es imposible por diseño.
- Liberar el bloqueo por intentos fallidos → `RF-SP-028`. Son cosas distintas y a menudo se confunden: quien está bloqueado no ha olvidado su contraseña.
- Autoservicio de recuperación por correo → `RF-SP-040`, registrado el 21-08-2026 y todavía sin especificar. Mientras no exista, esta operación es el único camino de vuelta.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-017` | El actor no aplica la operación sobre su propia cuenta | `requirements/sp.md` §5.1 |
| `RNF-SEG-006` | Los eventos de seguridad quedan registrados en la auditoría de seguridad | `security.md` §11 |

La política de contraseña está definida en `security.md` §3.2 y no se redefine aquí.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del usuario | Sí | Persona cuya contraseña se restablece | Debe existir, no estar eliminada y no ser el propio actor |
| Contraseña nueva | Sí | Credencial provisional que se le asigna | Debe cumplir la política mínima. Nunca se registra en ningún log |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Confirmación | Resultado de la operación, sin cuerpo de datos ni credencial alguna |

La contraseña asignada **no se devuelve** en la respuesta: la conoce quien la escribió, y repetirla en la respuesta la expondría a cualquier registro de la operación.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de restablecimiento de contraseñas.
- El usuario existe y no está eliminado.
- El usuario no es el propio actor.
- La contraseña nueva cumple la política mínima.

**Postcondiciones**

- La contraseña del usuario queda sustituida y almacenada con Argon2id.
- La cuenta queda **marcada para cambio obligatorio** de contraseña, y la credencial provisional **caduca** pasado el plazo configurado: superado, deja de servir y hay que restablecerla de nuevo.
- **Todos** los refresh tokens de esa persona quedan revocados con motivo `ACCESO_RETIRADO`, y sus tokens de acceso vigentes dejan de admitirse.
- Su estado, sus roles y su membresía no cambian. Si estaba bloqueada, **sigue bloqueada**.
- Queda constancia en la auditoría de seguridad con severidad alta, con el usuario afectado como objeto del evento y sin ningún dato de la credencial.

## 8. Flujo principal

1. El actor solicita restablecer la contraseña de un usuario y proporciona la nueva.
2. El sistema verifica que el usuario exista y no esté eliminado.
3. El sistema verifica que el usuario no sea el propio actor.
4. El sistema verifica que la contraseña nueva cumpla la política mínima.
5. El sistema sustituye la credencial, marca la cuenta para cambio obligatorio y fija el momento en que la credencial provisional caduca.
6. El sistema revoca todos los refresh tokens de esa persona, con motivo `ACCESO_RETIRADO`.
7. El sistema registra el restablecimiento en la auditoría de seguridad, con severidad alta.
8. El sistema confirma la operación.

## 9. Flujos alternativos

Ninguno. La operación no admite variantes: o se cumplen todas las condiciones, o se rechaza.

## 10. Excepciones

### EX-001 — El actor es el propio usuario

**Condición:** el identificador corresponde a la cuenta del actor.
**Respuesta del sistema:** rechaza la operación, cita `RN-SP-017` e indica que debe usarse `RF-SP-037`. Permitirlo daría a quien posee el permiso una forma de cambiar su propia contraseña sin conocer la vigente, que es exactamente la defensa que `RF-SP-037` levanta.

### EX-002 — Contraseña que no cumple la política

**Condición:** la contraseña nueva incumple alguna regla de la política mínima.
**Respuesta del sistema:** rechaza la operación e informa qué regla incumple, sin reproducir la contraseña en el mensaje ni en ningún registro.

### EX-003 — Usuario inexistente o eliminado

**Condición:** el identificador no corresponde a ningún usuario vigente.
**Respuesta del sistema:** rechaza la operación e informa que el usuario no existe, sin distinguir ambos casos.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Contraseña nueva obligatoria | Debe indicar la contraseña nueva. |
| `VAL-002` | Contraseña nueva conforme a la política mínima | La contraseña no cumple la política de seguridad. |
| `VAL-003` | El actor no es el usuario afectado | No es posible restablecer su propia contraseña por esta vía. |
| `VAL-004` | Usuario existente y no eliminado | El usuario solicitado no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-329` | El sistema sustituye la contraseña de otra persona y esta puede autenticarse con la nueva |
| `CA-SP-330` | La cuenta queda marcada para cambio obligatorio, y la marca se limpia al ejecutar `RF-SP-037` |
| `CA-SP-331` | Todos los refresh tokens de la persona afectada quedan revocados, y sus tokens de acceso dejan de admitirse |
| `CA-SP-332` | El sistema rechaza que el actor restablezca su propia contraseña, e indica que debe usar `RF-SP-037` |
| `CA-SP-333` | El sistema rechaza una contraseña que no cumple la política, indicando qué regla incumple |
| `CA-SP-334` | La respuesta no contiene la contraseña asignada |
| `CA-SP-335` | El estado, los roles y la membresía de la persona no cambian; una cuenta bloqueada sigue bloqueada |
| `CA-SP-336` | El sistema registra el restablecimiento en la auditoría de seguridad con severidad alta y con el usuario afectado como objeto |
| `CA-SP-392` | La credencial provisional **caduca** pasado el plazo configurado: superado, la persona ya no puede autenticarse con ella y hay que restablecerla de nuevo |
| `CA-SP-393` | El sistema no devuelve la contraseña asignada en la respuesta, y ningún registro la contiene |
| `CA-SP-394` | Restablecer la contraseña **no** levanta un bloqueo vigente, ni automático ni manual |
| `CA-SP-337` | El sistema rechaza la operación a un actor que posee `users:update` pero no `users:reset-password` |

## 13. Casos límite

- **Usuario bloqueado por intentos fallidos:** el restablecimiento **no** lo desbloquea. Son dos problemas distintos y a menudo aparecen juntos; quien atienda el caso deberá ejecutar además `RF-SP-028`. Mezclarlos haría que restablecer una contraseña levantara en silencio una defensa que el sistema puso.
- **Usuario inactivo:** puede restablecerse su contraseña. Seguirá sin poder entrar hasta que se le reactive, y ese es el orden correcto: preparar la credencial no concede acceso.
- **Restablecer la contraseña del último superadministrador:** se admite. No hay riesgo de dejar al sistema sin administración: la cuenta sigue activa y conserva su rol. Es distinto de desactivarla o eliminarla.
- **Restablecimiento sobre alguien con sesión abierta:** cae de inmediato, igual que en `RF-SP-028`. Si la cuenta estaba comprometida, esperar quince minutos sería justo lo contrario de lo que se busca.
- **La persona nunca inicia sesión tras el restablecimiento:** la credencial provisional caduca por sí sola pasado el plazo, de modo que la ventana en que el administrador conoce una credencial válida queda acotada sin depender de que nadie lo revise. Superado el plazo hay que restablecerla de nuevo.
- **Dos restablecimientos concurrentes sobre la misma cuenta:** ambos se serializan sobre la fila; prevalece el último, y ambos quedan registrados en la auditoría de seguridad.
- **Restablecimiento seguido de un cambio propio:** es el recorrido esperado. La marca se limpia y las sesiones se revocan por segunda vez, sin efecto adverso.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación. Con ella queda cerrada la primera compuerta de los treinta y ocho requerimientos especificados del módulo.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La contraseña la escribe el administrador, o la genera el sistema? | **La escribe el administrador, y no se devuelve nunca.** Que la genere el sistema parecía más seguro —cumpliría la política y nadie la elegiría débil—, pero obliga a **devolverla en la respuesta**, con lo que la credencial viaja por HTTP y acaba en los registros del lado del cliente y a la vista de quien mire la pantalla. Es exactamente lo que el resto del diseño evita. Que la escriba el administrador no elimina el problema de fondo —alguien conoce la credencial de otro—, pero ese problema se acota con la resolución 2, no con el origen de la contraseña |
| 2 | ¿La marca de cambio obligatorio debe caducar? | **Sí: la credencial provisional caduca** pasado el plazo configurado. Sin caducidad, una cuenta restablecida y nunca usada queda indefinidamente con una credencial conocida por otra persona, y nadie se entera porque no falla nada. Con ella, la ventana se cierra sola: superado el plazo, la credencial deja de servir y hay que restablecerla de nuevo. Añade a la cuenta un dato de vigencia que hoy no existe, y ese es el coste. `CA-SP-392` lo verifica |
| 3 | ¿Debe notificarse a la persona afectada que su contraseña fue restablecida? | **Anotado como riesgo, sin resolverlo aquí.** Es la única forma de que se entere si el restablecimiento **no lo pidió ella**, que es precisamente el caso de abuso contra el que valen poco las demás defensas. Exige el mismo canal de correo que `RF-SP-040` necesita, de modo que resolverlo aquí dejaría este requerimiento bloqueado — y con él bloqueado, quien olvida su contraseña se queda sin ninguna vía de vuelta. Se anota con su condición de disparo: el día que exista canal de notificación, deja de ser opcional |
| 4 | ¿Restablecer y desbloquear deben ser la misma operación? | **Separadas.** Restablecer una credencial **no debe levantar en silencio un bloqueo** que el sistema puso por sospecha, ni uno que un actor puso deliberadamente (`RF-SP-028`). Son dos problemas distintos que a menudo aparecen juntos, y la objeción es real: quien atiende a la persona tendrá que hacer dos llamadas con dos permisos distintos. Se acepta, porque la alternativa es que una operación sobre la credencial deshaga una decisión de seguridad sin que nadie lo haya pedido. La interfaz de soporte puede ofrecer ambas contiguas sin que el sistema las funda. `CA-SP-394` lo verifica |
