# SPEC — `RF-SP-039` Consultar el propio perfil

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-039` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 22-08-2026 |

---

## 1. Objetivo

Permitir que cualquier persona autenticada vea sus propios datos, sus roles y lo que puede hacer, sin necesidad del permiso de lectura de usuarios.

## 2. Contexto

Hoy hay un agujero: quien no administra usuarios **no puede ver ni sus propios datos**. `RF-SP-026` los devuelve, pero exige `users:read`, un permiso que la inmensa mayoría de las personas del sistema no tendrá nunca —agentes, estudiantes, clientes—. El resultado es que la interfaz no puede ni saludar a nadie por su nombre.

El problema real es mayor que el saludo. **Toda interfaz autenticada necesita saber qué mostrar**, y eso se decide con los permisos efectivos de quien mira. Sin este requerimiento, el cliente tendría que deducirlos, guardarlos al iniciar sesión o adivinarlos, y las tres salidas son peores: la primera duplica en el navegador una regla que vive en el servidor, la segunda queda obsoleta en cuanto alguien cambia un rol, y la tercera no es una salida.

No se resolvió dentro de `RF-SP-026` porque **su alcance de datos y su autorización son distintos**: aquella consulta apunta a cualquiera y exige permiso; esta apunta siempre al actor y no exige ninguno. Mezclarlas obligaría a un solo endpoint a comportarse de dos maneras según a quién señalara el identificador, que es la clase de ambigüedad que acaba en un fallo de autorización.

Nace de la aprobación de `RF-SP-026` el 21-08-2026.

**No es alcance de datos por persona.** La reserva de `security.md` §6 —ningún requerimiento con alcance por persona antes de resolver D-22— no lo alcanza, porque aquí no hay conjunto que acotar: el actor y solo el actor, siempre, sin excepción ni parámetro que lo cambie.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona autenticada | Consulta su propio perfil. No existe forma de consultar el de otro |

## 4. Alcance

### 4.1 Incluye

- Datos de identificación del actor: nombre de usuario, correo y nombre.
- Sus roles asignados y sus **permisos efectivos**, resueltos como la unión de los permisos de sus roles activos.
- Su membresía vigente, cuando la tiene.
- Su **último inicio de sesión**, tal como lo registra `RF-SP-034` al entrar. Es un dato informativo —«conectado desde las 09:14»—, **no una señal de acceso ajeno**: el valor se sobrescribe en cada entrada, de modo que quien consulta su perfil ve siempre el de su propia sesión en curso. Se aceptó así el 22-08-2026 para no reabrir `RF-SP-034`, ya aprobada.
- Su **superior comercial vigente**, cuando lo tiene (`RF-SP-041`).
- Si debe cambiar su contraseña antes de poder operar.

### 4.2 No incluye

- El perfil de otra persona → `RF-SP-026`, que exige `users:read`. Esta consulta **no admite identificador**: no hay a quién apuntar.
- Cualquier dato de la credencial. Ni el hash, ni su antigüedad, ni nada derivado de ella.
- **Modificar los propios datos.** No es una omisión, es una decisión: la corrección del propio correo o del propio nombre se pide a quien administra usuarios → `RF-SP-027`, que exige `users:update`. La autoedición del perfil **no existe como requerimiento y no se registra ninguno**; convertir esta consulta en escritura le costaría la propiedad que hoy la hace segura —sin entrada, es imposible transformarla en una lectura ajena—. Resuelto el 22-08-2026.
- Cambiar la propia contraseña → `RF-SP-037`.
- El historial de sesiones propias, que es auditoría de seguridad → `RF-SP-014`. El único momento de acceso que se devuelve es el de la sesión en curso, no un listado. **Queda un hueco conocido**: quien sospeche que alguien ha entrado con su cuenta no puede comprobarlo, porque `RF-SP-014` exige permiso de auditoría y ninguna persona corriente lo tendrá. Se anota como riesgo, no como alcance de esta consulta.
- **El equipo propio**, y cualquier otra lectura hacia abajo en la estructura comercial → `RF-SP-042`. Devolver a quién reporta uno mismo no abre esa puerta: es un dato del actor, no un conjunto de terceros.
- Las fechas de creación y modificación de la cuenta, y el momento en que expira un bloqueo. Las primeras son contexto administrativo que solo sirve a quien administra (`RF-SP-026`); el segundo es imposible por construcción, porque una cuenta bloqueada no puede autenticarse y por tanto no puede llegar hasta aquí.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-009` | Los permisos efectivos son la unión de los permisos de los roles **activos** | `security.md` §4.3 |
| `RN-SEG-002` | Un rol inactivo no concede permisos aunque siga asignado | `security.md` §4.3 |
| `RN-SP-021` | Un superior vigente por persona; la asignación anterior se cierra y se conserva | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

Ninguna. La identidad del actor se resuelve a partir de su autenticación, y **no se admite ningún parámetro** que permita señalar a otra persona. Es lo que hace imposible convertir esta consulta en una lectura ajena por descuido.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Identidad | Nombre de usuario, correo y nombre de la persona |
| Estado | Estado de la cuenta. Siempre `ACTIVO`: ningún otro estado permite llegar hasta aquí |
| Roles | Roles asignados, cada uno con su estado |
| Permisos efectivos | Unión de los permisos de sus roles **activos**, ya resuelta |
| Membresía | Membresía vigente y su nivel, cuando la persona tiene una |
| Último inicio de sesión | Momento registrado por `RF-SP-034` en el acceso en curso. Se sobrescribe en cada entrada: no revela accesos anteriores |
| Superior comercial | Persona que la tiene a cargo y el rol que porta, cuando existe. Solo el **vigente**: el historial que conserva `RN-SP-021` no se devuelve aquí |
| Cambio de contraseña pendiente | Indicador de que debe ejecutar `RF-SP-037` antes de poder operar |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado. No se exige ningún permiso.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita su perfil.
2. El sistema identifica al actor a partir de su autenticación.
3. El sistema recupera sus datos, sus roles, su membresía vigente, su último inicio de sesión y su superior comercial vigente, cuando lo tiene.
4. El sistema resuelve sus permisos efectivos como la unión de los permisos de sus roles activos.
5. El sistema devuelve el perfil.

## 9. Flujos alternativos

### FA-001 — Persona sin permisos efectivos

**Cuándo ocurre:** todos los roles del actor están inactivos.

1. El sistema devuelve la lista de permisos efectivos **vacía**.
2. No es un error, y esta consulta es lo que permite a la interfaz explicarlo en lugar de mostrar una pantalla rota.

*(Hasta el 24-08-2026 este flujo cubría también «el actor no tiene ningún rol». `RN-SP-023` eliminó ese estado.)*

### FA-002 — Persona con cambio de contraseña pendiente

**Cuándo ocurre:** la cuenta está marcada para cambio obligatorio (`RF-SP-024` o `RF-SP-038`).

1. El sistema devuelve el perfil con el indicador activo.
2. Es la respuesta que permite a la interfaz llevar a la persona a `RF-SP-037` en lugar de dejarla chocar contra el rechazo de todos los demás endpoints.

### FA-003 — Persona sin superior comercial

**Cuándo ocurre:** el actor no porta ningún rol de clasificación `VENDEDOR`, o es la cúspide de la fuerza comercial (`RN-SP-019`).

1. El sistema devuelve el perfil **sin superior comercial**.
2. No es un error ni un dato faltante: es el estado normal de la mayoría de las personas del sistema. La interfaz simplemente no pinta esa parte.

## 10. Excepciones

### EX-001 — Actor sin autenticar

**Condición:** la petición no trae credencial válida.
**Respuesta del sistema:** rechaza la petición por falta de autenticación, no por falta de permiso (`architecture.md` §7.2).

### EX-002 — El actor dejó de existir durante su sesión

**Condición:** el token es válido pero la cuenta fue eliminada después de emitirse.
**Respuesta del sistema:** rechaza la petición como no autenticado. La sesión ya debería haber caído —`RF-SP-029` revoca los refresh tokens—, pero el token de acceso vigente puede sobrevivir hasta quince minutos (`security.md` §4.5), y esta consulta no debe devolver el perfil de una cuenta que ya no existe.

## 11. Validaciones

Ninguna. La consulta no recibe datos de entrada.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-430` | Una persona **sin** `users:read` obtiene su propio perfil completo |
| `CA-SP-431` | El perfil devuelve los permisos efectivos ya resueltos, como unión de los roles activos y sin duplicados |
| `CA-SP-432` | Los permisos de un rol inactivo no aparecen entre los permisos efectivos, aunque el rol siga asignado |
| `CA-SP-433` | La lista de permisos efectivos llega vacía cuando la persona no tiene roles activos, sin error |
| `CA-SP-434` | La consulta **no admite** ningún parámetro que permita obtener el perfil de otra persona |
| `CA-SP-435` | La respuesta no contiene ningún dato de la credencial, ni siquiera transformado |
| `CA-SP-436` | El perfil indica que hay un cambio de contraseña pendiente cuando la cuenta está marcada, y deja de indicarlo tras ejecutar `RF-SP-037` |
| `CA-SP-437` | El perfil devuelve la membresía vigente cuando la persona la tiene, y nada cuando no |
| `CA-SP-438` | El sistema rechaza la petición sin credencial válida como no autenticado, no como falta de permiso |
| `CA-SP-439` | El sistema rechaza la petición cuando la cuenta del actor fue eliminada después de emitirse su token |
| `CA-SP-440` | El perfil devuelve el **momento de inicio de la sesión en curso**, tal como lo registró `RF-SP-034`, y nada cuando la cuenta nunca ha entrado |
| `CA-SP-441` | El perfil devuelve el **superior comercial vigente** cuando la persona lo tiene, y nada cuando no pertenece a la fuerza comercial o es su cúspide |
| `CA-SP-470` | La respuesta **no** contiene el equipo a cargo de la persona, ni ningún dato de terceros que dependan de ella |
| `CA-SP-471` | La respuesta **no** contiene fechas de creación ni de modificación de la cuenta, ni momento de expiración de bloqueo |
| `CA-SP-472` | La consulta **no** admite ninguna operación de escritura sobre los datos del actor |

## 13. Casos límite

- **Persona con `users:read`:** obtiene aquí lo mismo que cualquiera. Esta consulta no cambia según quién pregunte, y esa es su propiedad más valiosa: no tiene dos comportamientos que puedan divergir.
- **Cuenta desactivada o bloqueada durante la sesión:** sus refresh tokens ya fueron revocados por `RF-SP-028`, pero su token de acceso deja de admitirse de inmediato, de modo que no llega hasta aquí.
- **Rol retirado mientras la sesión sigue abierta:** el perfil refleja el estado real desde el momento en que se ejecuta la consulta, aunque el token de acceso siga transportando el código del rol retirado durante unos minutos. La consulta lee del sistema, no del token, y esa asimetría hay que conocerla: **el perfil puede mostrar menos permisos de los que el token todavía admite**.
- **Persona con muchos roles:** la respuesta crece con la unión de permisos. No se pagina: es el perfil de una sola persona y partirlo obligaría a la interfaz a pedirlo en trozos para poder pintar un menú.
- **Primer inicio de sesión de una cuenta:** el dato llega vacío únicamente si se consulta antes de que `RF-SP-034` lo haya registrado. La interfaz no debe interpretar ese vacío como un cero ni como una fecha nula significativa.
- **Lo que este dato NO responde:** «¿ha entrado alguien más con mi cuenta?». `RF-SP-034` sobrescribe el valor en cada entrada, de modo que el titular ve siempre el suyo. Responderlo exigiría conservar el acceso **anterior** además del actual, lo que reabriría `RF-SP-034`, ya aprobada. Queda como riesgo declarado en §4.2.
- **Persona que no pertenece a la fuerza comercial:** el superior llega vacío, igual que la membresía de quien no es consumidor. No es un error ni un dato faltante.
- **Superior de alguien que acaba de ser reasignado:** se devuelve el vigente en el momento de la consulta. El anterior no aparece: el historial existe (`RN-SP-021`) pero no se lee por aquí ni por `RF-SP-042`.
- **Persona que quiere corregir su correo:** no puede hacerlo, ni aquí ni en ningún otro requerimiento. Debe pedírselo a quien administra usuarios (`RF-SP-027`). Es una consecuencia aceptada el 22-08-2026, no un olvido, y su síntoma será tráfico de soporte por correcciones triviales.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 22-08-2026, antes de aprobar la especificación. Ninguna alcanza a otro documento —ninguna spec aprobada hubo que reabrir—, pero dos dejan huecos declarados que conviene no olvidar: nadie puede comprobar si otro entró con su cuenta (§13) y nadie puede corregir sus propios datos (§4.2).

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Devuelve lo mismo que `RF-SP-026` sobre uno mismo, o un subconjunto? | **Subconjunto, más el último inicio de sesión.** Fuera quedan las fechas de creación y modificación —contexto administrativo que solo sirve a quien administra— y el momento en que expira un bloqueo, que es **imposible por construcción**: una cuenta bloqueada no puede autenticarse y por tanto no llega hasta aquí. El último acceso sí entra, pero **con su alcance corregido**: `RF-SP-034` sobrescribe ese valor en cada entrada, de modo que lo que el titular ve es el inicio de **su sesión en curso**, no un acceso anterior. Es un dato informativo, no la señal de intrusión que parecía al plantear la pregunta. Detectar un acceso ajeno exigiría conservar además el penúltimo, y eso reabriría `RF-SP-034`, ya aprobada: se deja como **hueco declarado** en §4.2 y §13, no como alcance de esta consulta. `CA-SP-440` y `CA-SP-471` lo verifican por ambos lados |
| 2 | ¿Incluye su posición en la estructura comercial? | **Solo el superior vigente**, nunca el equipo. La distinción es la que sostiene la reserva de D-22: a quién reporta uno **es un dato del actor**; quiénes dependen de uno **es un conjunto de terceros acotado por quien pregunta**, que es la definición misma de alcance por persona (`security.md` §6). Se asume que la pregunta siguiente será por el equipo propio y que habrá que sostener la negativa hasta que D-22 se cierre. `CA-SP-441` y `CA-SP-470` fijan ambos lados |
| 3 | ¿Puede una persona corregir aquí su propio correo o su nombre? | **No, y no se registra ningún requerimiento para ello.** La autoedición del perfil no existe en el sistema: quien necesite corregir su ficha se lo pide a quien administra usuarios (`RF-SP-027`). Se descartó tanto resolverlo aquí —convertiría una consulta sin entrada, imposible de desviar hacia una lectura ajena, en una escritura con reglas propias— como abrir un requerimiento nuevo. **Es un hueco aceptado a conciencia**, y §13 declara su síntoma: tráfico de soporte por correcciones triviales. Si aparece, el momento de registrarlo será ese |
