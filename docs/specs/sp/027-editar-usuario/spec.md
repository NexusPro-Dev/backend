# SPEC — `RF-SP-027` Editar usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-027` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada | 08-09-2026 — `RN-SP-035` y `RN-SP-037`: el documento y el contacto pasan a ser corregibles; nace `EX-004`, `VAL-007` a `VAL-009` y `CA-SP-594` a `CA-SP-596` (Art. I.7) |
| Enmendada | 07-09-2026 — `RN-SP-034`: el país pasa a ser corregible desde aquí; nace `EX-003`, `VAL-006` y `CA-SP-578` a `CA-SP-580` (Art. I.7) |

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
- **Corrección del documento** —tipo y número— y de los **cuatro campos de contacto** (`RN-SP-035`, `RN-SP-037`, 08-09-2026). Es la **única** operación que cambia el documento; el contacto lo cambia además el titular por `RF-SP-044`.
- **Corrección del país** (`RN-SP-034`, 07-09-2026). Es la **única** operación que lo cambia: el alta lo fija y ningún otro requerimiento lo toca.

### 4.2 No incluye

- El nombre de usuario, que es inmutable: es la identidad de la persona en la auditoría y en el inicio de sesión.
- Los roles → `RF-SP-030` y `RF-SP-031`.
- La membresía → `RF-SP-032` y `RF-SP-033`.
- El estado → `RF-SP-028`.
- La contraseña → `RF-SP-037` y `RF-SP-038`.
- Eliminar al usuario → `RF-SP-029`.
- **Que el titular cambie su propio país** → no existe. `RF-SP-044` edita el perfil propio y **no admite el campo**: el país decide qué medios de pago se ofrecen (`RN-MV-019`), y cambiárselo uno mismo sería cambiarse de mercado.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-016` | El nombre de usuario y el correo son únicos entre los usuarios; el nombre de usuario no cambia | `requirements/sp.md` §5.1 |
| `RN-SP-034` | Todo usuario pertenece a un país, y solo se asigna uno **activo** | `requirements/sp.md` §5.1 |
| `RN-SP-035` | El documento identifica a la persona; el par tipo+número es único y no se libera | `requirements/sp.md` §5.1 |
| `RN-SP-037` | El teléfono es obligatorio y la dirección opcional | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador | Sí | Usuario que se edita | Debe existir y no estar eliminado |
| Nombre y apellidos | No | Nuevos datos de la persona | No pueden quedar vacíos si se envían |
| Correo | No | Nuevo correo | Único entre los usuarios. Formato de correo válido |
| Tipo y número de documento | No | Nueva identidad documental | **Van juntos o no van**: enviar uno solo se rechaza. El tipo debe existir y estar **activo**; el par no puede tenerlo ya nadie. **No se admite vaciarlos** en una persona que ya los tiene |
| Teléfono | No | Nuevo teléfono | **No se admite vaciarlo**: `RN-SP-037` lo hace obligatorio. En una persona que no lo tenía, informarlo lo añade |
| Dirección, complemento y ciudad | No | Nuevos datos de contacto | **Sí se admite vaciarlos** con nulo explícito, y son los únicos campos del cuerpo de los que se puede decir eso: son opcionales, de modo que el nulo **sí es una orden** — «ya no vive ahí» es un hecho que hay que poder registrar |
| País | No | Nuevo país de la persona | Debe existir y estar **activo** (`RN-SP-034`). **No se admite vaciarlo**: la columna es obligatoria y el estado «sin país» no existe |

Al menos uno de los campos modificables debe venir informado.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Usuario | Usuario con sus datos actualizados, **con el país resuelto** |

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
3.bis Si se envía país, el sistema verifica que exista en el catálogo y esté activo.
3.ter Si se envía documento, el sistema verifica que el tipo exista y esté activo, y que el par no lo tenga ya otra persona.
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

### EX-004 — Documento inexistente, inactivo o ya en uso

**Condición:** el tipo de documento no existe, existe y está **inactivo**, o el par tipo+número ya lo tiene otra persona —vigente o eliminada—.

**Respuesta del sistema:** rechaza la edición completa, con el mismo reparto que el alta hace en `EX-010`. El conflicto por documento repetido **no dice de quién es**, igual que el del correo.

**Y aquí aparece un caso que el alta no tiene: corregir el documento de alguien que ya lo tenía.** Se admite —es una errata de digitación, que es justo lo que esta operación existe para arreglar— y **el documento anterior NO queda libre**: `RN-SP-035` no lo libera al cambiarlo, con la diferencia deliberada respecto del correo, que sí se libera (§14, resolución 2). El motivo es el mismo que lo hace único: un documento identifica a una persona en el mundo real, y liberarlo permitiría que otra ficha lo tomara y que las dos fueran indistinguibles en la auditoría. **Corregir una errata no debe abrir esa puerta.**

### EX-003 — País inexistente o inactivo

**Condición:** el país indicado no existe en el catálogo, o existe y está **inactivo**.
**Respuesta del sistema:** rechaza la edición completa y cita `RN-SP-034`, **distinguiendo los dos casos** con el mismo criterio que `RF-SP-024` `EX-009`: inexistente es una referencia rota, inactivo es una referencia que resuelve y que una regla rechaza.

**Y aquí la distinción tiene una consecuencia que en el alta no tenía.** Esta es la operación con la que se saca a alguien de un país recién desactivado, de modo que quien la usa **está mirando personas cuyo país actual está inactivo**. Que el rechazo sea del país **de destino** y nunca del actual es lo que hace que la corrección sea posible: si la edición exigiera que el país vigente estuviera activo, nadie podría mover a quien más falta le hace.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un campo modificable informado | Debe indicar al menos un campo a modificar. |
| `VAL-002` | Nombre no vacío si se envía | El nombre de la persona no puede estar vacío. |
| `VAL-003` | Correo con formato válido si se envía | El correo indicado no es válido. |
| `VAL-004` | Correo único entre los usuarios | Ese correo ya está en uso. |
| `VAL-005` | Longitud máxima de los campos de texto | El campo excede la longitud permitida. |
| `VAL-006` | País existente y activo si se envía; **nulo explícito rechazado** (`RN-SP-034`) | El país indicado no es válido. |
| `VAL-007` | Tipo y número de documento **van juntos**, y ninguno admite nulo explícito (`RN-SP-035`) | El tipo y el número de documento se informan juntos. |
| `VAL-008` | Teléfono con formato admitido; **nulo explícito rechazado** (`RN-SP-037`) | El teléfono indicado no es válido. |
| `VAL-009` | Dirección, complemento y ciudad: en blanco se rechaza, **nulo explícito se admite** y vacía el campo | El campo no puede quedar en blanco. |

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
| `CA-SP-578` | El sistema cambia el país de una persona, y el detalle (`RF-SP-026`) devuelve el nuevo |
| `CA-SP-579` | El sistema **rechaza** vaciar el país con un nulo explícito, y lo rechaza como petición inválida y no como fallo interno |
| `CA-SP-580` | El cambio de país queda en la auditoría de cambios con su antes y su después, y **reenviar el mismo país no registra evento** (`FA-001`) |
| `CA-SP-594` | El sistema cambia el documento y los cuatro campos de contacto de una persona, y el detalle devuelve los nuevos |
| `CA-SP-595` | El sistema rechaza el documento que **ya tiene otra persona**, incluida una eliminada, sin revelar de quién es; y el documento anterior de quien lo corrige **no queda libre** |
| `CA-SP-596` | El nulo explícito **vacía** la dirección, el complemento y la ciudad, y **se rechaza** en el tipo, el número y el teléfono |
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
- **Persona sin documento —de las anteriores al 08-09-2026— a la que se le informa uno:** se admite, y es el caso para el que esta operación hace más falta. No es un cambio: es completar lo que faltaba.
- **Enviar solo el número de documento, sin el tipo:** se rechaza con `VAL-007`. Un número sin decir de qué documento es no significa nada, y `ck_users_document_pair` lo impide además en el motor.
- **Corregir una errata del número dejando el mismo tipo:** se admite. El documento anterior **no queda libre**, y esa es la asimetría deliberada con el correo (`EX-004`).
- **Vaciar la ciudad de alguien que se mudó y aún no se sabe adónde:** se admite. Es el único grupo de campos del cuerpo donde el nulo es una orden y no un error.
- **Persona cuyo país actual fue desactivado:** se edita con normalidad, y es el caso para el que este campo existe. Lo que se verifica es el país **de destino**, nunca el vigente.
- **País de destino igual al actual:** no es conflicto ni error; entra en `FA-001` y no registra evento, igual que reenviar el mismo correo.
- **País que se desactiva durante la edición:** mismo trato que en el alta (`RF-SP-024` §13). O se ve activo o se rechaza, pero nadie acaba movido a un país que acaba de retirarse.
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
