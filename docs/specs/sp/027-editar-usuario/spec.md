# SPEC — `RF-SP-027` Editar usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-027` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Corregir o actualizar los datos de identificación de una persona cuando dejan de ser correctos.

## 2. Contexto

Los datos de una persona cambian: se casa y cambia de apellido, cambia de correo, o simplemente el alta los escribió mal. Ninguno de esos cambios altera lo que la persona puede hacer, y por eso se separan de las operaciones que sí lo alteran.

**Esta funcionalidad no modifica roles, membresía, estado ni credencial.** Cada una de esas operaciones tiene sus propias reglas de rechazo y su propio requerimiento: `RF-SP-028` a `RF-SP-033`, `RF-SP-037` y `RF-SP-038`. Agruparlas en una sola edición haría imposible especificar por separado cuándo se rechaza cada una, que es el mismo argumento con el que se acotó `RF-SP-004`.

El **nombre de usuario queda fuera**, y por un motivo más fuerte que en el caso del código de un rol: es la identidad con la que la persona aparece en cualquier registro y con la que inicia sesión. Cambiarlo haría que la misma persona pareciera dos a lo largo del tiempo, que es exactamente lo que `RN-SP-016` existe para impedir.

**El correo, en cambio, sí se edita, y eso tiene un peso que conviene no minimizar.** Desde `RF-SP-024` el correo es una de las dos formas de iniciar sesión, de modo que modificarlo **cambia cómo esa persona entra en el sistema**. Nadie queda fuera —el nombre de usuario sigue funcionando, y es justamente para eso que es inmutable—, pero un actor que edita el correo de una cuenta ajena está tocando una vía de acceso, y ese es el patrón clásico de apropiación de cuentas. Por eso el cambio de correo emite además evento de seguridad, y el de nombre o apellidos no.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Edita cualquier usuario |
| Administrador | Edita cualquier usuario |

## 4. Alcance

### 4.1 Incluye

- Modificación del nombre, los apellidos y el correo.

### 4.2 No incluye

- El nombre de usuario, que es inmutable: es la identidad de la persona en la auditoría y en el inicio de sesión.
- Los roles → `RF-SP-030` y `RF-SP-031`.
- La membresía → `RF-SP-032` y `RF-SP-033`.
- El estado → `RF-SP-028`.
- La contraseña → `RF-SP-037` y `RF-SP-038`.
- Eliminar al usuario → `RF-SP-029`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-016` | El nombre de usuario y el correo son únicos entre los usuarios; el nombre de usuario no cambia | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador | Sí | Usuario que se edita | Debe existir y no estar eliminado |
| Nombre y apellidos | No | Nuevos datos de la persona | No pueden quedar vacíos si se envían |
| Correo | No | Nuevo correo | Único entre los usuarios. Formato de correo válido |

Al menos uno de los campos modificables debe venir informado.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Usuario | Usuario con sus datos actualizados |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de usuarios.
- El usuario existe y no está eliminado.

**Postcondiciones**

- El usuario conserva su nombre de usuario, sus roles, su membresía, su estado y su credencial.
- Queda constancia del cambio en la auditoría de cambios, con el antes y el después de cada campo modificado. **Si cambió el correo, queda además constancia en la auditoría de seguridad**, con severidad alta y con el usuario afectado como objeto del evento.

## 8. Flujo principal

1. El actor solicita editar un usuario y proporciona los campos a modificar.
2. El sistema verifica que el usuario exista y no esté eliminado.
3. Si se envía correo, el sistema verifica que no esté en uso por otro usuario.
4. El sistema aplica los cambios.
5. El sistema registra el evento en la auditoría de cambios, con el antes y el después de cada campo modificado, y —si cambió el correo— también en la de seguridad.
6. El sistema informa el usuario actualizado.

## 9. Flujos alternativos

### FA-001 — Edición sin cambio efectivo

**Cuándo ocurre:** los valores enviados coinciden con los actuales.

1. El sistema no registra evento de auditoría, porque nada cambió.
2. Devuelve el usuario sin modificar, sin tratarlo como error.

## 10. Excepciones

### EX-001 — Correo ya en uso

**Condición:** otro usuario ya tiene ese correo.
**Respuesta del sistema:** rechaza la edición e informa el conflicto, sin revelar de qué usuario se trata.

### EX-002 — Usuario inexistente

**Condición:** el identificador no corresponde a ningún usuario vigente, o el usuario está eliminado.
**Respuesta del sistema:** informa que el usuario no existe, sin distinguir ambos casos.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un campo modificable informado | Debe indicar al menos un campo a modificar. |
| `VAL-002` | Nombre no vacío si se envía | El nombre de la persona no puede estar vacío. |
| `VAL-003` | Correo con formato válido si se envía | El correo indicado no es válido. |
| `VAL-004` | Correo único entre los usuarios | Ese correo ya está en uso. |
| `VAL-005` | Longitud máxima de los campos de texto | El campo excede la longitud permitida. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-221` | El sistema modifica el nombre, los apellidos y el correo con datos válidos |
| `CA-SP-222` | El sistema conserva sin cambios el nombre de usuario, los roles, la membresía, el estado y la credencial |
| `CA-SP-223` | El sistema no expone operación alguna para modificar el nombre de usuario |
| `CA-SP-224` | El sistema rechaza un correo ya usado por otro usuario, sin revelar cuál |
| `CA-SP-225` | El sistema registra en la auditoría de cambios solo los campos que cambiaron, con su antes y después |
| `CA-SP-226` | El sistema no registra evento cuando los valores enviados coinciden con los actuales |
| `CA-SP-227` | El cambio de correo no invalida las sesiones abiertas de la persona |
| `CA-SP-355` | El correo anterior queda **liberado**: otro usuario puede tomarlo en un alta o en una edición posterior |
| `CA-SP-356` | El cambio de correo se registra **también** en la auditoría de seguridad, con severidad alta y el usuario afectado como objeto; el cambio de nombre o apellidos, no |
| `CA-SP-357` | Tras cambiar el correo, la persona puede iniciar sesión con el nuevo y **no** con el anterior, y en ambos casos su nombre de usuario sigue funcionando |
| `CA-SP-228` | El sistema informa que el usuario no existe cuando está eliminado lógicamente |
| `CA-SP-229` | El sistema rechaza la edición a un actor sin el permiso de modificación de usuarios |

## 13. Casos límite

- **Correo igual al actual:** no es conflicto consigo mismo; la unicidad se verifica contra los demás usuarios.
- **Correo que otra persona tuvo y liberó:** se admite. Es la consecuencia de la resolución 2, y la diferencia con el correo de un usuario **eliminado**, que sigue reservado para siempre (`RN-SP-016`).
- **Correo mal escrito:** la persona deja de recibir notificaciones y pierde esa vía de acceso, sin que nada falle. Conserva su nombre de usuario para entrar, y otro actor puede corregirlo. Es el riesgo que la verificación cerraría, y que la resolución 3 deja abierto de forma consciente.
- **Correo con mayúsculas o espacios sobrantes:** se normaliza antes de verificar la unicidad, igual que en el alta.
- **Usuario eliminado lógicamente:** se trata como inexistente.
- **Edición concurrente:** gana el último en escribir, mismo criterio que en `RF-SP-004`. La auditoría de cambios conserva ambas ediciones, de modo que el cambio perdido es reconstruible.
- **El actor se edita a sí mismo:** se admite. `RN-SEG-011` protege a los roles, no a los usuarios, y editar el propio nombre no concede ningún privilegio.
- **Usuario inactivo o bloqueado:** puede editarse. Corregir el nombre de alguien no depende de que pueda entrar.
- **Nombre solo con espacios:** se rechaza por validación tras recortar los extremos.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación. Dos de ellas cambiaron de signo respecto del borrador, porque `RF-SP-024` había resuelto entretanto que **el correo también sirve para iniciar sesión**.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El correo es editable? | **Sí.** Es el dato que más cambia de una persona —un cambio de apellido, un correo corporativo que se sustituye, un error de escritura en el alta—, y dejarlo inmutable vaciaría este requerimiento de contenido. Lo que hace segura la decisión es que el **nombre de usuario** sí es inmutable (`RF-SP-024`): cambiar el correo altera una de las dos vías de acceso, pero nunca deja a nadie fuera, porque la otra sigue funcionando. `CA-SP-357` lo verifica de extremo a extremo |
| 2 | Si el correo cambia, ¿queda liberado el anterior para otro usuario? | **Sí, queda liberado.** La auditoría referencia a las personas por su identificador y por su nombre de usuario, nunca por su correo, de modo que la trazabilidad no depende de él y liberarlo no confunde a nadie con nadie. Es la **diferencia deliberada con la eliminación**, donde `RN-SP-016` sí lo reserva para siempre: allí la persona desaparece y su correo es parte de cómo se la identificaba; aquí sigue existiendo y con identidad propia. `RN-SP-016` se enmienda para distinguir los dos casos, que hoy no separaba. `CA-SP-355` lo verifica |
| 3 | ¿El cambio de correo exige verificarlo antes de aplicarlo? | **No por ahora**, y queda anotado como riesgo con su condición de disparo. Verificarlo exige un canal de correo y un flujo de confirmación que ningún requerimiento cubre todavía; exigirlo dejaría este requerimiento bloqueado hasta que existan. El riesgo asumido es acotado gracias a la resolución 1: un correo mal escrito deja a la persona sin notificaciones y sin **esa** vía de acceso, pero conserva su nombre de usuario para entrar y otro actor puede corregirlo. **El día que exista recuperación de contraseña por correo, la verificación deja de ser opcional**, porque entonces un correo equivocado entregaría el control de la cuenta a un tercero |
| 4 | ¿Se registra el cambio también en la auditoría de seguridad? | **Sí, pero solo el del correo.** La pregunta cambió de peso con la resolución 1: el correo es ahora una credencial de acceso, y modificar el de una cuenta ajena altera cómo esa persona entra en el sistema —el patrón clásico de apropiación de cuentas—. Eso lo separa de `RF-SP-022` y `RF-SP-023`, donde no había privilegio ni acceso en juego. El cambio de nombre o apellidos **no** emite evento de seguridad, porque no toca ninguna vía de acceso. `security.md` §8.1, cuyo catálogo es cerrado, se enmienda para incorporar el evento. `CA-SP-356` verifica ambas mitades |
