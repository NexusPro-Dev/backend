# SPEC — `RF-SP-044` Editar el propio perfil

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-044` |
| Módulo | `SP` — Sistema Principal |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | Pendiente |
| Fecha de aprobación | — |

---

## 1. Objetivo

Permitir que cualquier persona autenticada corrija sus propios datos de identificación sin depender de un administrador.

## 2. Contexto

`RF-SP-027` ya corrige el nombre, los apellidos y el correo de una persona, pero exige `users:update`, que es un permiso de **administración de usuarios**. Un agente, un director o un cliente no lo tienen y no deben tenerlo: concedérselo para que pueda arreglar su propio apellido le daría de paso la capacidad de editar el de cualquiera.

Hoy, por tanto, **quien no administra usuarios no puede corregir un dato suyo mal escrito**: tiene que pedírselo a alguien con permiso. Es la misma asimetría que ya resolvió `RF-SP-039` en la lectura —consultar el propio perfil sin exigir `users:read`—, y este requerimiento es su contraparte de escritura. La ruta es la misma y el criterio también: `me` es un literal y no un identificador, y el alcance es **siempre el actor y solo el actor**.

**El correo se admite, y es la decisión que carga esta especificación.** Desde `RF-SP-024` el correo es una de las dos formas de iniciar sesión, y desde `RF-SP-040` es además **la vía por la que se recupera una contraseña olvidada**. Eso convierte «cambiar el correo» en «cambiar quién puede recuperar esta cuenta»: quien se apodere de una sesión ajena y pueda cambiarlo se queda con la cuenta **de forma permanente**, aunque la persona legítima cambie después su contraseña.

Por eso el cambio de correo **exige la contraseña actual en la misma petición**. No es un trámite: una sesión robada no lleva la contraseña, de modo que exigirla convierte el robo de sesión en algo que caduca —el token vence— en lugar de en una apropiación definitiva. El nombre y los apellidos no la piden, porque equivocarlos no abre ninguna puerta.

**Lo que esto no sustituye es la verificación del correo**, que `RF-SP-027` ya dejó anotada como pendiente y que este requerimiento hereda: el correo nuevo se acepta sin comprobar que exista ni que sea de quien lo declara, de modo que un error de tecleo deja a la persona sin vía de recuperación. Queda en §14 y en los riesgos del plan.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona autenticada | Edita sus propios datos, y solo los suyos |

## 4. Alcance

### 4.1 Incluye

- Modificación del **propio** nombre, apellidos y correo.
- Exigencia de la contraseña actual **cuando y solo cuando** se cambia el correo.

### 4.2 No incluye

- Editar los datos de otra persona → `RF-SP-027`, que exige `users:update`.
- El nombre de usuario, que es inmutable por `RN-SP-016`.
- La propia contraseña → `RF-SP-037`.
- Los roles, la membresía, el estado y el superior comercial: ninguno es un dato que la persona decida sobre sí misma. Son `RF-SP-030` a `RF-SP-033`, `RF-SP-028` y `RF-SP-041`.
- **Verificar el correo nuevo.** No existe hoy infraestructura para ello y no se abre aquí: ver §14.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-016` | El nombre de usuario y el correo son únicos entre los usuarios; el nombre de usuario no cambia | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Nombre y apellidos | No | Nuevos datos de la persona | No pueden quedar vacíos si se envían |
| Correo | No | Nuevo correo | Único entre los usuarios. Formato válido |
| Contraseña actual | Condicional | Prueba de que quien pide el cambio es la persona | **Obligatoria si y solo si se envía correo.** Debe coincidir con la vigente |

No hay identificador de entrada: el usuario a editar es **el que porta el token**. Al menos uno de los campos modificables debe venir informado.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Perfil | Los datos del actor ya actualizados |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado. No se exige ningún permiso.
- El actor no tiene pendiente un cambio obligatorio de contraseña (§13, `CL-003`).

**Postcondiciones**

- El actor conserva su nombre de usuario, sus roles, su membresía, su estado, su superior comercial y su credencial.
- Queda constancia en la auditoría de cambios con el antes y el después de cada campo modificado.
- **Si cambió el correo**, queda además constancia en la auditoría de seguridad con severidad alta, igual que en `RF-SP-027`.
- **Si la contraseña actual no era correcta**, queda constancia en la auditoría de seguridad y **nada se modifica**.

## 8. Flujo principal

1. La persona solicita editar su perfil y proporciona los campos a modificar.
2. El sistema resuelve al actor a partir del token; no acepta ningún identificador.
3. Si se envía correo, el sistema exige la contraseña actual y comprueba que coincida con la vigente.
4. Si se envía correo, el sistema verifica que no esté en uso por otro usuario.
5. El sistema aplica los cambios.
6. El sistema registra el evento en la auditoría de cambios y —si cambió el correo— también en la de seguridad.
7. El sistema informa el perfil actualizado.

## 9. Flujos alternativos

### FA-001 — Edición sin cambio efectivo

**Cuándo ocurre:** los valores enviados coinciden con los actuales.

1. El sistema no registra evento de auditoría, porque nada cambió.
2. Devuelve el perfil sin modificar, sin tratarlo como error.

**El correo repetido sigue exigiendo la contraseña.** Que el valor no cambie se sabe *después* de mirar el actual, y hacer depender de ello la exigencia daría una forma de averiguar el correo vigente probando valores: el que no pidiera contraseña sería el bueno.

## 10. Excepciones

### EX-001 — Correo ya en uso

**Condición:** otro usuario ya tiene ese correo.

**Respuesta del sistema:** rechaza la edición e informa el conflicto, sin revelar de qué usuario se trata.

### EX-002 — Contraseña actual incorrecta

**Condición:** se envía correo y la contraseña no coincide con la vigente.

**Respuesta del sistema:** rechaza la edición sin modificar nada y registra el intento en la auditoría de seguridad.

**No bloquea la cuenta ni cuenta como intento fallido de inicio de sesión.** Quien está aquí ya se autenticó; tratarlo como un ataque de credenciales permitiría que alguien con una sesión ajena dejara a la persona legítima bloqueada, que es un daño mayor que el que se evita.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un campo modificable informado | Debe indicar al menos un campo a modificar. |
| `VAL-002` | Nombre no vacío si se envía | El nombre de la persona no puede estar vacío. |
| `VAL-003` | Correo con formato válido si se envía | El correo indicado no es válido. |
| `VAL-004` | Correo único entre los usuarios | Ese correo ya está en uso. |
| `VAL-005` | Longitud máxima de los campos de texto | El campo excede la longitud permitida. |
| `VAL-006` | Contraseña actual presente si se envía correo | Para cambiar su correo debe indicar su contraseña actual. |
| `VAL-007` | Contraseña actual correcta | La contraseña actual no es correcta. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-494` | Una persona autenticada **sin ningún permiso** modifica su nombre y sus apellidos |
| `CA-SP-495` | La operación afecta **solo** al actor: el cuerpo no admite identificador y ningún otro usuario cambia |
| `CA-SP-496` | Con correo y contraseña actual correcta, el correo queda cambiado |
| `CA-SP-497` | Con correo y **sin** contraseña actual, se rechaza con `VAL-006` y nada cambia |
| `CA-SP-498` | Con correo y contraseña actual **incorrecta**, se rechaza con `VAL-007` y nada cambia |
| `CA-SP-499` | Cambiar solo nombre y apellidos **no** exige contraseña actual |
| `CA-SP-500` | Un correo ya usado por otra persona se rechaza sin revelar de quién es |
| `CA-SP-501` | El nombre de usuario no cambia aunque se envíe |
| `CA-SP-502` | Los roles, la membresía, el estado y el superior comercial quedan intactos |
| `CA-SP-503` | El cambio de correo emite evento de seguridad de severidad alta; el de nombre, no |
| `CA-SP-504` | La contraseña actual incorrecta emite evento de seguridad y **no** incrementa los intentos fallidos ni bloquea la cuenta |
| `CA-SP-505` | Enviar los mismos valores no registra auditoría y responde sin error |
| `CA-SP-506` | Sin token, la petición se rechaza como no autenticada |

## 13. Casos límite

| ID | Caso | Resolución |
|---|---|---|
| `CL-001` | Se envía el mismo correo que ya se tiene | `FA-001`: no cambia nada, pero **sigue exigiendo la contraseña** |
| `CL-002` | El correo nuevo es el de un usuario **eliminado** | Se rechaza: `RN-SP-016` reserva la identidad de los eliminados para siempre |
| `CL-003` | El actor tiene pendiente el cambio obligatorio de contraseña | Se rechaza. `MustChangePasswordFilter` no incluye esta ruta entre las alcanzables con la marca: quien debe cambiar su contraseña lo hace primero |
| `CL-004` | La cuenta del actor se desactiva mientras edita | La autorización ya lo resuelve: el token deja de admitirse |

## 14. Preguntas abiertas

| Pregunta | Estado |
|---|---|
| **Verificar el correo nuevo antes de darlo por bueno.** Heredada de `RF-SP-027`, resolución 3, y ya sin coartada: desde `RF-SP-040` el correo es la vía de recuperación, de modo que un correo mal tecleado deja a la persona sin forma de recuperar su cuenta. Exige un flujo de confirmación por enlace o código, que no existe | **Abierta.** No bloquea este requerimiento: sin él, el dato mal escrito hoy no se puede ni corregir |
