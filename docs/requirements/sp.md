# Requerimientos del Módulo — `SP` Sistema Principal

| Campo | Valor |
|---|---|
| Módulo | `SP` — Sistema Principal |
| Paquete | `modules/system` |
| Prefijos de permiso | `roles:`, `permissions:`, `audit:`, `memberships:`, `currencies:`, `countries:`, `users:` |
| Versión | 1.17.0 |
| Estado | **Aprobado** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 20-08-2026 |
| Última actualización | 22-08-2026 |
| Fecha de aprobación | 20-08-2026 |

!!! info "Qué va en este documento"

    El catálogo de requerimientos del módulo: qué debe hacer, bajo qué reglas y con qué permisos.

    El comportamiento detallado de cada requerimiento —flujos, validaciones, criterios de aceptación y casos límite— vive en su tripleta, en `docs/specs/sp/`. Aquí no se repite.

---

## 1. Información del módulo

### 1.1 Descripción

`SP` gobierna **quién puede hacer qué** en NEXUS y deja constancia de lo que ocurre. Administra el catálogo de permisos, la definición de roles con su contención de privilegios, y la consulta de los cuatro registros de auditoría.

Es la raíz del grafo de dependencias: no depende de ningún módulo, y todos los demás dependen de él.

### 1.2 Objetivo

Permitir que la organización defina su estructura de autorización sin intervención de desarrollo. Crear un rol nuevo, o cambiar el alcance de uno existente, debe ser una operación administrativa y no un despliegue.

### 1.3 Alcance

**Incluye**

- Consulta del catálogo de permisos.
- Alta, consulta, edición, cambio de estado y eliminación de roles.
- Asignación y revocación de permisos sobre un rol, con la contención respecto de su rol padre.
- Consulta de los cuatro registros de auditoría, por separado y con permisos diferenciados.
- Alta, consulta, edición, estado y baja de usuarios.
- Asignación y retiro de roles sobre una persona.
- Asignación y retiro de la membresía de un consumidor.
- Registro del superior comercial de un vendedor y consulta del equipo que tiene a cargo.
- Inicio y cierre de sesión, refresco de token y gestión de la contraseña.

**No incluye**

- La definición de qué contenidos exige cada nivel de membresía: corresponde a los módulos de academia y productos.
- La **escritura** de los registros de auditoría: la emite cada módulo al ejecutar su operación. `SP` solo los consulta.

## 2. Submódulos

Según [`modules.md` §5.1](../modules.md).

| Submódulo | Responsabilidad | Requerimientos |
|---|---|---|
| Roles | Alta, consulta, edición, estado, jerarquía y eliminación | `RF-SP-001` a `RF-SP-004`, `RF-SP-007` a `RF-SP-009` |
| Permisos | Catálogo de permisos. Solo lectura por API | `RF-SP-010`, `RF-SP-015` |
| Roles y permisos | Asociación y revocación de permisos sobre un rol | `RF-SP-005`, `RF-SP-006` |
| Membresías | Nivel de acceso del consumidor a servicios y contenidos | `RF-SP-016` a `RF-SP-018` |
| Monedas | Catálogo de monedas. Solo lectura por API | `RF-SP-019` |
| Países | Catálogo de países | `RF-SP-020`, `RF-SP-021` |
| **Usuarios** | Alta, consulta, edición, estado y baja de las personas que acceden al sistema, y consulta del propio perfil | `RF-SP-024` a `RF-SP-029`, `RF-SP-039` |
| **Roles de usuario** | Asignación y retiro de roles sobre una persona | `RF-SP-030`, `RF-SP-031` |
| **Membresía del usuario** | Asignación y retiro del nivel de acceso de un consumidor | `RF-SP-032`, `RF-SP-033` |
| **Estructura comercial** | Quién está a cargo de quién dentro de la fuerza comercial: el superior de cada vendedor y el equipo que tiene a cargo | `RF-SP-041`, `RF-SP-042` |
| **Credenciales y acceso** | Inicio y cierre de sesión, refresco, y gestión de la contraseña | `RF-SP-034` a `RF-SP-038`, `RF-SP-040` |
| Auditoría | Consulta de los cuatro registros | `RF-SP-011` a `RF-SP-014` |

!!! note "Sobre el submódulo Parámetros"

    `modules.md` §5.1 registraba un submódulo «Parámetros» con alcance por definir. Los catálogos de **Monedas** y **Países** cubren lo que se esperaba de él, de modo que se retira como submódulo propio. Si aparece configuración transversal que no sea un catálogo, deberá registrarse de nuevo con su propio alcance.

## 3. Dependencias

Ninguna. `SP` es la raíz del grafo y debe seguir siéndolo: si llegara a depender de otro módulo aparecería un ciclo ([`modules.md` §7](../modules.md)).

## 4. Actores

Los actores de este módulo son los **roles del sistema**.

| Actor | Rol en este módulo | Permisos de `SP` |
|---|---|---|
| Super Administrador | Tiene acceso completo al sistema | Posee todos los permisos |
| Administrador | Define roles, su alcance de permisos y la configuración | `roles:*`, `permissions:read`, los cuatro de auditoría |
| Contabilidad | Consume roles; no los administra | `audit:read-changes`, `audit:read-deletions` |
| Manager | Consume roles; no los administra | — |
| Director | Consume roles; no los administra | — |
| Agente o vendedor | Consume roles; no los administra | — |
| Estudiante | Consume roles; no los administra | — |
| Líder académico | Consume roles; no los administra | — |

Solo el **Super Administrador** y **Administrador** operan sobre `SP`. Los demás roles aparecen aquí porque son los sujetos que `SP` define, no porque ejecuten sus requerimientos.

Los permisos de auditoría se conceden por separado: los cuatro registros no tienen la misma sensibilidad y no se leen en bloque ([`security.md` §4.4](../security.md)).

### 4.1 Catálogo inicial de roles del sistema

Propuesta de códigos y de jerarquía de **contención de privilegios** (`RN-SEG-003`). Cierra parcialmente la decisión D-17.

| Código | Nombre | Rol padre | `is_system` |
|---|---|---|---|
| `SUPERADMIN` | Superadministrador | — | Sí |
| `ADMIN` | Administrador | `SUPERADMIN` | Sí |
| `CONTABILIDAD` | Contabilidad | `ADMIN` | Sí |
| `LIDER_ACADEMICO` | Líder académico | `ADMIN` | Sí |
| `MANAGER` | Manager | `ADMIN` | Sí |
| `DIRECTOR` | Director | `MANAGER` | Sí |
| `AGENTE` | Agente o vendedor | `DIRECTOR` | Sí |
| `ESTUDIANTE` | Estudiante | `ADMIN` | No |
| `CLIENTE` | Cliente | `ADMIN` | No |

!!! important "Qué acota `parent_role_id`, y qué no"

    La columna «Rol padre» es **contención de privilegios**: acota qué permisos puede declarar cada rol (`RN-SEG-003`). Es una relación **rol → rol** y no dice nada sobre los datos.

    La estructura comercial —manager, director y agente— es otra cosa: una relación **persona → persona** que dice **quién está a cargo de quién**. Los tres roles necesitan el mismo permiso sobre comisiones; lo que cambia es el conjunto de registros.

    Modelar esa estructura en `roles.parent_role_id` sería un error: esa columna no acota datos y relaciona roles, no usuarios. Vive por eso en su propia tabla, `user_supervisors` (§10.7), con sus requerimientos (`RF-SP-041`, `RF-SP-042`) y sus reglas (`RN-SP-019` a `RN-SP-022`).

    **Registrar la estructura no es acotar datos.** Que el sistema sepa que Ana tiene a Luis a cargo no le concede a Ana ninguna visibilidad sobre los datos de Luis: eso lo decidirá el modelo de alcance, que sigue pendiente como **D-22**. La estructura es el insumo que D-22 necesitará, no su sustituto, y por eso puede registrarse antes sin infringir la reserva de [`security.md` §6](../security.md).

    Que ambas jerarquías coincidan en forma **ya no es una casualidad: es una exigencia.** `RN-SP-020` obliga a que el superior de una persona porte el rol padre inmediato del rol de esa persona. Siguen siendo dos ejes distintos —uno acota permisos, el otro acotará datos—, pero desde el 22-08-2026 el segundo se apoya en el primero para validarse.

!!! note "Por qué `SUPERADMIN` está por encima de `ADMIN`"

    `SUPERADMIN` es el **rol técnico del responsable del software**, no un rol de negocio. Existe para las funcionalidades reservadas a quien desarrolla y mantiene la plataforma, que se irán definiendo, y para satisfacer `RN-SEG-007`: alguien tiene que poder crear y acotar al administrador.

    `ADMIN` es el **máximo rol de negocio**: tiene acceso completo a la operación, y por eso todos los roles funcionales cuelgan de él. La contención (`RN-SEG-003`) obliga entonces a que `ADMIN` posea todo permiso que cualquier rol de negocio declare, incluidos los financieros de `CONTABILIDAD`.

    **Consecuencia a tener presente:** con este diseño no hay separación entre quien configura el sistema y quien controla el dinero. Si en algún momento se quiere que Contabilidad pueda aprobar algo que Administración no, habrá que colgarla de `SUPERADMIN`.

## 5. Reglas de negocio

Las reglas de autorización están definidas en [`security.md` §4.3](../security.md), donde cada una declara cuándo aplica, qué debe ocurrir y su prioridad. **No se redefinen aquí**: esta tabla solo indica a qué requerimiento afecta cada una.

| ID | Regla | Aplica a |
|---|---|---|
| `RN-SEG-001` | Código y nombre de rol únicos | `RF-SP-001`, `RF-SP-004` |
| `RN-SEG-002` | Un rol `INACTIVO` no concede permisos aunque siga asignado | `RF-SP-007` |
| `RN-SEG-003` | Los permisos de un rol son subconjunto de los de su padre | `RF-SP-005`, `RF-SP-008` |
| `RN-SEG-004` | La validación se hace contra el padre inmediato, sin recorrer ancestros | `RF-SP-005` |
| `RN-SEG-005` | Revocar un permiso se rechaza si un rol descendiente lo declara | `RF-SP-006` |
| `RN-SEG-006` | La cadena de roles padre no admite ciclos | `RF-SP-008` |
| `RN-SEG-007` | Existe exactamente un rol raíz sin padre | `RF-SP-001`, `RF-SP-008` |
| `RN-SEG-008` | No se elimina un rol con hijos o con usuarios asignados | `RF-SP-009` |
| `RN-SEG-010` | Nadie asigna permisos que no posee | `RF-SP-005` |
| `RN-SEG-011` | Nadie modifica los permisos de un rol que tiene asignado | `RF-SP-004` a `RF-SP-009` |
| `RN-SEG-012` | Los roles de sistema no se modifican ni eliminan por la API | `RF-SP-004` a `RF-SP-009` |
| `RN-SEG-013` | Cambiar el rol padre revalida `RN-SEG-003` contra el nuevo padre | `RF-SP-008` |

!!! note "Por qué estas reglas llevan `SEG` y no `SP`"

    `RN-SEG-…` es el espacio de las **reglas transversales de seguridad**: gobiernan la autorización en todo el sistema y varias de ellas —`RN-SEG-009`, `010` y `011`— alcanzan a cualquier módulo que asigne roles o resuelva permisos, no solo a este. Renombrarlas al código de un módulo obligaría a partirlas.

    Las reglas propias de este módulo sí llevan su prefijo y están en §5.1 como `RN-SP-…`. La convención completa está en [`requirements.md` §3.1](../requirements.md).

### 5.1 Reglas propias del módulo

Reglas que no son transversales de seguridad y por tanto sí llevan el prefijo del módulo.

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-SP-001` | Superadministrador siempre presente | Al eliminar un usuario, al retirarle el acceso —desactivándolo o bloqueándolo— o al retirarle el rol | Debe existir siempre al menos un usuario **`ACTIVO`** con rol `SUPERADMIN`; la operación que dejaría al sistema sin ninguno se rechaza. **La condición se mide sobre usuarios activos**, no sobre usuarios existentes: un superadministrador inactivo, bloqueado o eliminado no puede administrar nada, y contarlo dejaría la garantía vacía. La comprobación debe serializarse sobre el conjunto de portadores activos del rol raíz, no sobre la fila del usuario afectado | **Crítica** |
| `RN-SP-002` | Rol padre obligatorio | Al crear o editar un rol | Todo rol declara un rol padre, salvo `SUPERADMIN`, que es el único sin él | Alta |
| `RN-SP-003` | Clasificación del rol | Al crear un rol | Todo rol se clasifica como `FUNCIONARIO` (personal interno), `VENDEDOR` (personal de la fuerza comercial) o `CONSUMIDOR` (cliente del sistema) | Alta |
| `RN-SP-013` | Membresía solo para consumidores | Al asignar una membresía a un usuario | El usuario debe tener al menos un rol de clasificación `CONSUMIDOR`. Si no lo tiene, la asignación se rechaza. Junto con `RN-SP-018` —su recíproca— hace que el rol de consumidor y la membresía sean **inseparables**: ninguno de los dos puede existir sin el otro, y por eso el primero se concede indicando ambos y el último se retira arrastrando el otro | **Crítica** |
| `RN-SP-014` | Una membresía por usuario | Al asignar una membresía | Un usuario tiene como mucho **una membresía asignada**. Asignar otra sustituye la anterior, y el cambio queda auditado. La asignación admite una **fecha de fin opcional**: sin ella es indefinida, y con ella deja de estar **vigente** al pasar. **La vigencia se evalúa al consultarla, no la retira ningún proceso** (`RF-SP-032`), de modo que una membresía vencida conserva su fila —y su plaza— hasta que se renueve o se retire, sin conceder nivel alguno | Alta |
| `RN-SP-015` | Retiro del último rol consumidor | Al retirar roles de un usuario | Si el retiro deja al usuario sin ningún rol `CONSUMIDOR`, **su membresía se retira en la misma operación y transacción**, y ambos hechos quedan auditados bajo el mismo identificador de correlación. No se rechaza: es la única salida del estado de consumidor, y rechazarla produciría un bloqueo mutuo con `RN-SP-018`. Enmendada el 21-08-2026 al aprobar `RF-SP-033`; antes exigía retirar la membresía primero | Alta |
| `RN-SP-018` | Todo consumidor tiene membresía | Siempre | Un usuario que porta al menos un rol de clasificación `CONSUMIDOR` **debe tener una membresía asignada**. El estado «consumidor sin nivel» no existe. En consecuencia: asignar el primer rol `CONSUMIDOR` a quien no tiene membresía **exige indicarla en la misma operación** (`RF-SP-024`, `RF-SP-030`), y retirar el último rol `CONSUMIDOR` **retira la membresía** (`RN-SP-015`). Se adquieren juntos y se sueltan juntos | **Crítica** |
| `RN-SP-016` | Identidad no reutilizable | Al registrar o editar un usuario | El nombre de usuario y el correo son únicos entre **todos** los usuarios, incluidos los eliminados. A diferencia del rol, **no se liberan al eliminar**: reutilizarlos permitiría que la actividad de dos personas distintas se confundiera en la auditoría. El **nombre de usuario** es además inmutable y no admite el carácter `@`; el correo sí puede corregirse (`RF-SP-027`), y ambos sirven para iniciar sesión. **La reserva permanente alcanza solo a la eliminación:** al corregir el correo de una persona, el anterior **queda liberado** y otro usuario puede tomarlo, porque la auditoría no referencia a nadie por su correo | **Crítica** |
| `RN-SP-017` | Un usuario no se opera a sí mismo | Al eliminar un usuario, al cambiar su estado y **al cambiar su superior comercial** | El actor no puede aplicar la operación sobre su propia cuenta. Alcanza a las tres operaciones en que quien ejecuta tendría interés directo en el resultado: dejar de existir, recuperar su propio acceso y **reubicarse en la estructura comercial**, de la que cuelga la atribución de su producción. Ampliada el 22-08-2026 al aprobar `RF-SP-041`; antes solo alcanzaba a eliminar y desactivar | Alta |
| `RN-SP-011` | Orden de mando comercial | Al crear o reubicar un rol `VENDEDOR` | El orden de mando de la fuerza comercial se expresa con `parent_role_id`: el rol superior es el rol padre. No existe un campo de rango aparte | Alta |
| `RN-SP-019` | Superior comercial obligatorio | Al registrar un usuario, al asignarle roles y al retirárselos | Un usuario que porta al menos un rol de clasificación `VENDEDOR` **debe tener un superior comercial**, salvo quien porta el rol vendedor de mayor rango —aquel cuyo rol padre no es `VENDEDOR`—, que es la cúspide de la fuerza comercial y no declara ninguno. El estado «vendedor sin superior» no existe: toda operación que conceda el primer rol `VENDEDOR` de una persona **o que cambie cuál es su rol vendedor de mayor rango** —un ascenso— **exige indicar el superior en la misma operación** (`RF-SP-024`, `RF-SP-030`), y retirar el último rol `VENDEDOR` **retira su superior** en la misma transacción (`RF-SP-031`), auditando ambos hechos bajo el mismo identificador de correlación. Es la forma que `RN-SP-018` ya da al par consumidor-membresía, con una exigencia más: allí el nivel no depende de qué rol se conceda después, y aquí sí | **Crítica** |
| `RN-SP-020` | El superior porta el rol padre | Al asignar o cambiar el superior comercial, y al conceder un rol `VENDEDOR` | El superior debe portar el rol **padre inmediato** del **rol vendedor de mayor rango** que porta el subordinado (`RN-SP-011`): quien es `AGENTE` reporta a quien porta `DIRECTOR`, nunca a otro `AGENTE` ni directamente a un `MANAGER`. La validación es contra el padre inmediato y **no recorre ancestros**, igual que `RN-SEG-004`. Se evalúa sobre el rol de mayor rango, y no sobre «el primero», porque **un ascenso cambia con quién debe cumplirse**: quien pasa de `AGENTE` a `DIRECTOR` deja de poder estar a cargo de un director. Su consecuencia útil es que la cadena de personas hereda la aciclicidad de la cadena de roles (`RN-SEG-006`) y no necesita una regla anti-ciclos propia | **Crítica** |
| `RN-SP-021` | Un superior por vendedor | Al asignar el superior comercial | Un usuario tiene como mucho **un superior vigente**. Asignar otro cierra la asignación anterior —que conserva su fila con fecha de fin— y abre una nueva. El historial no se borra: determina a quién se atribuía cada resultado en cada momento, y las comisiones lo necesitarán | Alta |
| `RN-SP-022` | Ningún equipo se queda sin superior | Al desactivar, bloquear o eliminar un usuario, y al retirarle el rol `VENDEDOR` | Si el usuario tiene personas a cargo, la operación **se rechaza** hasta que se reasignen. **No se reasignan solas** al superior del superior: la estructura comercial determinará atribución de negocio, y desplazarla sin decisión explícita cambiaría en silencio a quién pertenece un resultado. Es la misma postura que `RN-SEG-008` toma con un rol que tiene hijos | Alta |
| `RN-SP-004` | Permisos inmutables por API | Siempre | Los permisos no se crean, editan ni eliminan por la API: se pueblan y modifican únicamente por migración | Alta |
| `RN-SP-005` | Revocación sin motivo | Al retirar un permiso de un rol | La fila de asociación se elimina físicamente y se audita en `audit_deletion_log` sin motivo declarado (Art. V.13, excepción de asociaciones) | Alta |
| `RN-SP-006` | Membresía acotada por nivel | Al crear una membresía | Toda membresía está sujeta a una de mayor nivel; solo la membresía superior queda libre de ella | Alta |
| `RN-SP-007` | Inserción en la cadena de membresías | Al crear una membresía | Se indica cuál es su membresía hija, si la hay, y el sistema reordena la jerarquía en consecuencia. Si no se indica ninguna, la nueva membresía queda en el extremo inferior de la cadena | Alta |
| `RN-SP-008` | Membresías inmutables | Al editar o eliminar una membresía | La operación se rechaza. Solo se admite el reordenamiento derivado de `RN-SP-007`. **No llevan indicador de activo**: desactivar un eslabón dejaría un hueco en un orden lineal | Media |
| `RN-SP-009` | Países inmutables salvo su estado | Al editar o eliminar un país | La operación se rechaza. Lo único modificable es el indicador de país activo (`RF-SP-022`), que permite retirar de la circulación un alta equivocada sin borrar el registro | Media |
| `RN-SP-010` | Monedas inmutables por API salvo su estado | Siempre | Las monedas no se crean, editan ni eliminan por la API. Lo único modificable es el indicador de moneda activa (`RF-SP-023`), y la moneda por defecto no puede desactivarse | Media |

!!! info "Sobre la inmutabilidad de los catálogos"

    Países y monedas **sí** llevan indicador de activo; las membresías **no**. La diferencia no es de criterio sino de estructura: un catálogo plano admite que un elemento deje de ofrecerse sin que los demás se enteren, mientras que la cadena de membresías es un orden lineal en el que retirar un eslabón obliga a decidir qué pasa con el hueco y con quien lo tenía asignado.

    Desactivar **no es corregir**: el código y el nombre erróneos permanecen, y los datos que ya los referencian siguen resolviéndolos. Es lo que evita que el error se propague a partir de ese momento, no lo que lo repara.

!!! info "Sobre `RN-SP-005`"

    Retirar un permiso de un rol **elimina físicamente** la fila de `role_permissions`, y por tanto se audita en `audit_deletion_log`. No se exige motivo: una asociación rol-permiso no es una entidad de negocio y su «por qué» ya está en el propio evento —qué permiso, de qué rol, quién y cuándo—. Un motivo de texto libre aquí se rellenaría con ruido.

    Esto exigió enmendar el Art. V.13, que prohibía las eliminaciones sin motivo. La excepción quedó acotada a las asociaciones.

## 6. Requerimientos funcionales

### 6.1 Resumen

| ID | Requerimiento | Prioridad | Permiso | Estado |
|---|---|---|---|---|
| `RF-SP-001` | Registrar rol | Crítica | `roles:create` | Pendiente |
| `RF-SP-002` | Consultar roles | Crítica | `roles:read` | Pendiente |
| `RF-SP-003` | Consultar detalle de un rol | Alta | `roles:read` | Pendiente |
| `RF-SP-004` | Editar rol | Alta | `roles:update` | Pendiente |
| `RF-SP-005` | Asignar permisos a un rol | Crítica | `roles:update` | Pendiente |
| `RF-SP-006` | Revocar permisos de un rol | Alta | `roles:update` | Pendiente |
| `RF-SP-007` | Cambiar el estado de un rol | Alta | `roles:update` | Pendiente |
| `RF-SP-008` | Cambiar el rol padre de un rol | Media | `roles:update` | Pendiente |
| `RF-SP-009` | Eliminar rol | Media | `roles:delete` | Pendiente |
| `RF-SP-010` | Consultar catálogo de permisos | Crítica | `permissions:read` | Pendiente |
| `RF-SP-011` | Consultar auditoría de cambios | Media | `audit:read-changes` | Pendiente |
| `RF-SP-012` | Consultar auditoría de eliminación | Media | `audit:read-deletions` | Pendiente |
| `RF-SP-013` | Consultar auditoría de error | Media | `audit:read-errors` | Pendiente |
| `RF-SP-014` | Consultar auditoría de seguridad | Alta | `audit:read-security` | Pendiente |
| `RF-SP-015` | Consultar detalle de un permiso | Media | `permissions:read` | Pendiente |
| `RF-SP-016` | Registrar membresía | Alta | `memberships:create` | Pendiente |
| `RF-SP-017` | Consultar membresías | Alta | `memberships:read` | Pendiente |
| `RF-SP-018` | Consultar detalle de una membresía | Media | `memberships:read` | Pendiente |
| `RF-SP-019` | Consultar monedas | Media | `currencies:read` | Pendiente |
| `RF-SP-020` | Registrar país | Media | `countries:create` | Pendiente |
| `RF-SP-021` | Consultar países | Media | `countries:read` | Pendiente |
| `RF-SP-022` | Cambiar el estado de un país | Media | `countries:update` | Pendiente |
| `RF-SP-023` | Cambiar el estado de una moneda | Baja | `currencies:update` | Pendiente |
| `RF-SP-024` | Registrar usuario | **Crítica** | `users:create` | Pendiente |
| `RF-SP-025` | Consultar usuarios | **Crítica** | `users:read` | Pendiente |
| `RF-SP-026` | Consultar detalle de un usuario | Alta | `users:read` | Pendiente |
| `RF-SP-027` | Editar usuario | Alta | `users:update` | Pendiente |
| `RF-SP-028` | Cambiar el estado de un usuario | Alta | `users:update` | Pendiente |
| `RF-SP-029` | Eliminar usuario | Media | `users:delete` | Pendiente |
| `RF-SP-030` | Asignar roles a un usuario | **Crítica** | `users:assign-roles` | Pendiente |
| `RF-SP-031` | Retirar roles de un usuario | Alta | `users:assign-roles` | Pendiente |
| `RF-SP-032` | Asignar membresía a un usuario | Alta | `users:assign-membership` | Pendiente |
| `RF-SP-033` | Retirar la membresía de un usuario | Media | `users:assign-membership` | Pendiente |
| `RF-SP-034` | Iniciar sesión | **Crítica** | — (público) | Pendiente |
| `RF-SP-035` | Refrescar el token de acceso | **Crítica** | — (público) | Pendiente |
| `RF-SP-036` | Cerrar sesión | Alta | — (público) | Pendiente |
| `RF-SP-037` | Cambiar la propia contraseña | Alta | Autenticado | Pendiente |
| `RF-SP-038` | Restablecer la contraseña de un usuario | Media | `users:reset-password` | Pendiente |
| `RF-SP-039` | Consultar el propio perfil | Alta | Autenticado | Pendiente |
| `RF-SP-040` | Restablecer la propia contraseña olvidada | Alta | — (público) | Pendiente |
| `RF-SP-041` | Asignar o cambiar el superior comercial de un usuario | **Crítica** | `users:assign-supervisor` | Pendiente |
| `RF-SP-042` | Consultar el equipo a cargo de un usuario | Media | `users:read` | Pendiente |

**Orden sugerido de implementación:** `RF-SP-010` → `RF-SP-001` → `RF-SP-002` → `RF-SP-005` → `RF-SP-024` → `RF-SP-030` → `RF-SP-041` → `RF-SP-003` → `RF-SP-009` → el resto.

El catálogo de permisos es prerrequisito de todo lo demás, y sin roles no hay nada que auditar. `RF-SP-024` y `RF-SP-030` se adelantan porque crean `users` y `user_roles`, y **dos requerimientos de roles dependen de esas tablas**: `RF-SP-003` devuelve cuántos usuarios tiene un rol, y `RF-SP-009` no puede eliminar un rol sin comprobar que nadie lo tiene asignado (`RN-SEG-008`). Mientras `USR` era un módulo aparte, esa dependencia se resolvía invirtiéndola con un puerto; al absorberse los usuarios en `SP` (v1.3.0), la inversión dejó de tener sentido y lo que queda es una dependencia de esquema, que se resuelve ordenando. Fijado el 21-08-2026 al revisar los planes de `RF-SP-003` y `RF-SP-009`.

`RF-SP-041` entra justo detrás de `RF-SP-030` y **no puede quedar para el final**: `RN-SP-019` lo vuelve parte del alta de cualquier vendedor, de modo que `RF-SP-024` y `RF-SP-030` no están terminados sin él. Añadido el 22-08-2026 al desaparcar la estructura comercial.

#### Precedencias dentro del bloque de usuarios

No fijan un orden total: declaran qué **no** puede ir antes de qué, y por qué. Añadidas el 22-08-2026 al aprobarse los planes de `RF-SP-025` a `RF-SP-029`.

| Precedencia | Motivo |
|---|---|
| **`RF-SP-034` antes de `RF-SP-026`, `RF-SP-027`, `RF-SP-028`, `RF-SP-029`, `RF-SP-031`, `RF-SP-037`, `RF-SP-038` y `RF-SP-040`** | Crea `refresh_tokens` y las tres columnas de control de acceso —`failed_attempts`, `locked_until`, `last_login_at`—, e implementa el puerto de revocación de sesiones. Sin él, «retirar el acceso» no tiene qué revocar y varios criterios de aceptación no son verificables |
| **`RF-SP-028` antes de `RF-SP-029` y `RF-SP-031`** | Declara `ix_user_supervisors_supervisor_vigente` (§10.8) y cinco componentes que los tres comparten, entre ellos la serialización de `RN-SP-001`. Reescribirlos por separado es de donde salen las divergencias que `RF-SP-028` §14, resolución 4, prohíbe |
| **`RF-SP-030` antes de `RF-SP-025`** | Declara `ix_user_roles_role_id`, del que depende el filtro por rol del listado. Sin él la consulta funciona y recorre la tabla de asignaciones entera: el síntoma es lentitud, no error |
| `RF-SP-041` antes de `RF-SP-042` | Sin estructura registrada no hay estructura que consultar |
| `RF-SP-040` **al final, y solo tras cerrar D-23** | Su `plan.md` no puede escribirse antes de decidir el mecanismo de envío (`architecture.md` §16) |

`RF-SP-025` es el único requerimiento del bloque que **no** depende de `RF-SP-034`: no devuelve ningún dato de control de acceso, y `CA-SP-345` verifica precisamente esa ausencia.

### 6.2 Fichas

#### `RF-SP-001` — Registrar rol

| Campo | Valor |
|---|---|
| Objetivo | Permitir que la organización defina un rol nuevo sin desplegar código |
| Actor | Administrador |
| Permiso requerido | `roles:create` |
| Prioridad | Crítica |
| Reglas aplicables | `RN-SEG-001`, `RN-SEG-003`, `RN-SEG-007`, `RN-SEG-010`, `RN-SP-002`, `RN-SP-003`, `RN-SP-011` |
| Depende de | `RF-SP-010` |
| Tripleta | `docs/specs/sp/001-registrar-rol/` |
| Estado | Pendiente |

El sistema debe permitir a un usuario autorizado registrar un rol con su código, nombre, descripción, clasificación, rol padre y conjunto inicial de permisos. Los permisos declarados quedan acotados por los del rol padre y por los del propio actor que lo crea.

#### `RF-SP-002` — Consultar roles

| Campo | Valor |
|---|---|
| Objetivo | Ver qué roles existen y en qué estado |
| Actor | Administrador |
| Permiso requerido | `roles:read` |
| Prioridad | Crítica |
| Reglas aplicables | — |
| Depende de | `RF-SP-001` |
| Tripleta | `docs/specs/sp/002-consultar-roles/` |
| Estado | Pendiente |

Listado paginado de roles, con filtro por estado y por rol padre, y búsqueda por código o nombre. Toda colección se pagina (`architecture.md` §7.4).

#### `RF-SP-003` — Consultar detalle de un rol

| Campo | Valor |
|---|---|
| Objetivo | Conocer el alcance exacto de un rol antes de asignarlo o modificarlo |
| Actor | Administrador |
| Permiso requerido | `roles:read` |
| Prioridad | Alta |
| Reglas aplicables | — |
| Depende de | `RF-SP-001` |
| Tripleta | `docs/specs/sp/003-consultar-detalle-rol/` |
| Estado | Pendiente |

Devuelve el rol con su lista explícita de permisos, su rol padre y sus roles hijos. Responde de un vistazo qué puede hacer alguien con ese rol, que es la ventaja de no usar herencia.

#### `RF-SP-004` — Editar rol

| Campo | Valor |
|---|---|
| Objetivo | Corregir el nombre o la descripción de un rol |
| Actor | Administrador |
| Permiso requerido | `roles:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-SEG-001`, `RN-SEG-011`, `RN-SEG-012` |
| Depende de | `RF-SP-001` |
| Tripleta | `docs/specs/sp/004-editar-rol/` |
| Estado | Pendiente |

Modifica nombre y descripción. **No** modifica permisos, estado ni rol padre: cada una de esas operaciones tiene sus propias reglas y su propio requerimiento.

#### `RF-SP-005` — Asignar permisos a un rol

| Campo | Valor |
|---|---|
| Objetivo | Ampliar lo que un rol puede hacer, sin exceder su cota |
| Actor | Administrador |
| Permiso requerido | `roles:update` |
| Prioridad | Crítica |
| Reglas aplicables | `RN-SEG-003`, `RN-SEG-004`, `RN-SEG-010`, `RN-SEG-011`, `RN-SEG-012` |
| Depende de | `RF-SP-001`, `RF-SP-010` |
| Tripleta | `docs/specs/sp/005-asignar-permisos/` |
| Estado | Pendiente |

Agrega permisos a un rol. La operación se rechaza si algún permiso no está contenido en el rol padre (`RN-SEG-003`) o en los permisos efectivos del actor (`RN-SEG-010`). Es el requerimiento donde se materializa el modelo de contención.

#### `RF-SP-006` — Revocar permisos de un rol

| Campo | Valor |
|---|---|
| Objetivo | Reducir el alcance de un rol sin romper el invariante de contención |
| Actor | Administrador |
| Permiso requerido | `roles:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-SEG-005`, `RN-SEG-011`, `RN-SEG-012` |
| Depende de | `RF-SP-005` |
| Tripleta | `docs/specs/sp/006-revocar-permisos/` |
| Estado | Pendiente |

Retira permisos de un rol. Si algún rol descendiente declara el permiso que se retira, la operación **se rechaza** e informa qué roles lo impiden; el sistema no revoca en cascada de forma implícita (`RN-SEG-005`).

#### `RF-SP-007` — Cambiar el estado de un rol

| Campo | Valor |
|---|---|
| Objetivo | Suspender un rol sin perder su definición ni sus asignaciones |
| Actor | Administrador |
| Permiso requerido | `roles:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-SEG-002`, `RN-SEG-011`, `RN-SEG-012` |
| Depende de | `RF-SP-001` |
| Tripleta | `docs/specs/sp/007-cambiar-estado-rol/` |
| Estado | Pendiente |

Activa o desactiva un rol. Un rol `INACTIVO` deja de conceder permisos de inmediato aunque siga asignado a usuarios (`RN-SEG-002`), lo que exige invalidar la caché de resolución (`security.md` §4.5).

#### `RF-SP-008` — Cambiar el rol padre de un rol

| Campo | Valor |
|---|---|
| Objetivo | Reubicar un rol en la jerarquía de contención |
| Actor | Administrador |
| Permiso requerido | `roles:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-SEG-003`, `RN-SEG-006`, `RN-SEG-007`, `RN-SEG-013` |
| Depende de | `RF-SP-001` |
| Tripleta | `docs/specs/sp/008-cambiar-rol-padre/` |
| Estado | Pendiente |

Reasigna el rol padre. Exige revalidar la contención contra el nuevo padre (`RN-SEG-013`) y verificar que no se forme un ciclo (`RN-SEG-006`).

#### `RF-SP-009` — Eliminar rol

| Campo | Valor |
|---|---|
| Objetivo | Retirar un rol que dejó de tener sentido |
| Actor | Administrador |
| Permiso requerido | `roles:delete` |
| Prioridad | Media |
| Reglas aplicables | `RN-SEG-008`, `RN-SEG-011`, `RN-SEG-012` |
| Depende de | `RF-SP-001` |
| Tripleta | `docs/specs/sp/009-eliminar-rol/` |
| Estado | Pendiente |

Elimina un rol que no tenga roles hijos ni usuarios asignados (`RN-SEG-008`). La eliminación **exige motivo** y conserva el estado del registro (Art. V.13), por lo que el endpoint recibe el motivo en el cuerpo de la petición.

#### `RF-SP-010` — Consultar catálogo de permisos

| Campo | Valor |
|---|---|
| Objetivo | Saber qué permisos existen para poder componer roles |
| Actor | Administrador |
| Permiso requerido | `permissions:read` |
| Prioridad | Crítica |
| Reglas aplicables | — |
| Depende de | — |
| Tripleta | `docs/specs/sp/010-consultar-permisos/` |
| Estado | Pendiente |

Lista los permisos disponibles con su código, recurso, acción y descripción legible. El catálogo es **solo lectura por API**: se puebla y modifica por migración Flyway (`security.md` §4.4).

#### `RF-SP-011` — Consultar auditoría de cambios

| Campo | Valor |
|---|---|
| Objetivo | Responder quién creó o editó un registro, y qué cambió |
| Actor | Auditor de negocio, Responsable de seguridad |
| Permiso requerido | `audit:read-changes` |
| Prioridad | Media |
| Reglas aplicables | — |
| Depende de | — |
| Tripleta | `docs/specs/sp/011-consultar-auditoria-cambios/` |
| Estado | Pendiente |

Consulta paginada de `audit_change_log`, con filtro por módulo, entidad, identificador, actor y rango de fechas. Es la **única** fuente del actor de un cambio, porque las tablas de negocio no lo almacenan (Art. V.7).

#### `RF-SP-012` — Consultar auditoría de eliminación

| Campo | Valor |
|---|---|
| Objetivo | Responder quién eliminó qué, por qué, y qué era lo eliminado |
| Actor | Auditor de negocio, Responsable de seguridad |
| Permiso requerido | `audit:read-deletions` |
| Prioridad | Media |
| Reglas aplicables | — |
| Depende de | — |
| Tripleta | `docs/specs/sp/012-consultar-auditoria-eliminacion/` |
| Estado | Pendiente |

Consulta paginada de `audit_deletion_log`, incluyendo el motivo declarado y el estado del registro al momento de eliminarse.

#### `RF-SP-013` — Consultar auditoría de error

| Campo | Valor |
|---|---|
| Objetivo | Diagnosticar a quién le falló qué, y sobre qué recurso |
| Actor | Soporte técnico, Responsable de seguridad |
| Permiso requerido | `audit:read-errors` |
| Prioridad | Media |
| Reglas aplicables | — |
| Depende de | — |
| Tripleta | `docs/specs/sp/013-consultar-auditoria-error/` |
| Estado | Pendiente |

Consulta paginada de `audit_error_log`, con filtro por tipo de error, severidad, código y rango de fechas. El detalle técnico completo no está aquí: se alcanza por el identificador de correlación (`architecture.md` §6.6.4).

#### `RF-SP-014` — Consultar auditoría de seguridad

| Campo | Valor |
|---|---|
| Objetivo | Revisar la actividad sobre el control de acceso |
| Actor | Responsable de seguridad |
| Permiso requerido | `audit:read-security` |
| Prioridad | Alta |
| Reglas aplicables | — |
| Depende de | — |
| Tripleta | `docs/specs/sp/014-consultar-auditoria-seguridad/` |
| Estado | Pendiente |

Consulta paginada de `audit_security_log`, con filtro por tipo de evento, severidad, resultado y actor. Su permiso se concede aparte de los demás de auditoría (`security.md` §8).

#### `RF-SP-015` — Consultar detalle de un permiso

| Campo | Valor |
|---|---|
| Objetivo | Conocer el alcance exacto de un permiso antes de asignarlo a un rol |
| Actor | Super Administrador, Administrador |
| Permiso requerido | `permissions:read` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-004` |
| Depende de | `RF-SP-010` |
| Tripleta | `docs/specs/sp/015-consultar-detalle-permiso/` |
| Estado | Pendiente |

Devuelve un permiso con su recurso, acción, nombre y descripción legible.

#### `RF-SP-016` — Registrar membresía

| Campo | Valor |
|---|---|
| Objetivo | Definir un nivel de acceso para los consumidores del sistema |
| Actor | Super Administrador, Administrador |
| Permiso requerido | `memberships:create` |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-006`, `RN-SP-007` |
| Depende de | — |
| Tripleta | `docs/specs/sp/016-registrar-membresia/` |
| Estado | Pendiente |

Crea una membresía indicando cuál será su membresía hija; el sistema la inserta en la cadena y reordena la jerarquía. La membresía determina a qué servicios y contenidos accede un consumidor: un curso puede estar disponible solo desde cierto nivel.

#### `RF-SP-017` — Consultar membresías

| Campo | Valor |
|---|---|
| Objetivo | Ver los niveles definidos y su orden en la jerarquía |
| Actor | Super Administrador, Administrador |
| Permiso requerido | `memberships:read` |
| Prioridad | Alta |
| Reglas aplicables | — |
| Depende de | `RF-SP-016` |
| Tripleta | `docs/specs/sp/017-consultar-membresias/` |
| Estado | Pendiente |

Listado paginado, presentado según el orden de la jerarquía y no por fecha de creación: el orden es la información relevante.

#### `RF-SP-018` — Consultar detalle de una membresía

| Campo | Valor |
|---|---|
| Objetivo | Conocer el nivel de una membresía y su posición en la cadena |
| Actor | Super Administrador, Administrador |
| Permiso requerido | `memberships:read` |
| Prioridad | Media |
| Reglas aplicables | — |
| Depende de | `RF-SP-016` |
| Tripleta | `docs/specs/sp/018-consultar-detalle-membresia/` |
| Estado | Pendiente |

Devuelve la membresía con su membresía superior y su membresía hija.

#### `RF-SP-019` — Consultar monedas

| Campo | Valor |
|---|---|
| Objetivo | Disponer del catálogo de monedas para las operaciones financieras |
| Actor | Cualquier rol autenticado con el permiso |
| Permiso requerido | `currencies:read` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-010` |
| Depende de | — |
| Tripleta | `docs/specs/sp/019-consultar-monedas/` |
| Estado | Pendiente |

Listado de monedas. Hoy contiene únicamente `USD`; el catálogo existe para que incorporar otra moneda no exija cambiar el modelo de datos más adelante.

#### `RF-SP-020` — Registrar país

| Campo | Valor |
|---|---|
| Objetivo | Incorporar un país al catálogo |
| Actor | Super Administrador, Administrador |
| Permiso requerido | `countries:create` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-009` |
| Depende de | — |
| Tripleta | `docs/specs/sp/020-registrar-pais/` |
| Estado | Pendiente |

Crea un país. Una vez creado no puede editarse ni eliminarse, de modo que la validación en el alta es la única oportunidad de evitar un dato incorrecto.

#### `RF-SP-021` — Consultar países

| Campo | Valor |
|---|---|
| Objetivo | Disponer del catálogo de países |
| Actor | Cualquier rol autenticado con el permiso |
| Permiso requerido | `countries:read` |
| Prioridad | Media |
| Reglas aplicables | — |
| Depende de | `RF-SP-020` |
| Tripleta | `docs/specs/sp/021-consultar-paises/` |
| Estado | Pendiente |

Listado de países.

#### `RF-SP-022` — Cambiar el estado de un país

| Campo | Valor |
|---|---|
| Objetivo | Retirar de la circulación un país registrado por error, o reincorporarlo |
| Actor | Super Administrador, Administrador |
| Permiso requerido | `countries:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-009` |
| Depende de | `RF-SP-020` |
| Tripleta | `docs/specs/sp/022-cambiar-estado-pais/` |
| Estado | Pendiente |

Activa o desactiva un país. Un país inactivo deja de ofrecerse en `RF-SP-021`, pero su registro permanece y los datos que ya lo referencian siguen resolviéndolo. Es la única modificación admitida sobre el catálogo, y nace de la aprobación de `RF-SP-020` el 21-08-2026.

#### `RF-SP-023` — Cambiar el estado de una moneda

| Campo | Valor |
|---|---|
| Objetivo | Incorporar una moneda sin habilitarla todavía, o retirar una que deja de usarse |
| Actor | Super Administrador |
| Permiso requerido | `currencies:update` |
| Prioridad | Baja |
| Reglas aplicables | `RN-SP-010` |
| Depende de | `RF-SP-019` |
| Tripleta | `docs/specs/sp/023-cambiar-estado-moneda/` |
| Estado | Pendiente |

Activa o desactiva una moneda. **La moneda por defecto no puede desactivarse**: dejaría los importes del sistema sin referencia válida. Nace de la aprobación de `RF-SP-019` el 21-08-2026.

#### `RF-SP-039` — Consultar el propio perfil

| Campo | Valor |
|---|---|
| Objetivo | Permitir que cualquier persona autenticada vea sus propios datos, sus roles y sus permisos efectivos |
| Actor | Cualquier persona autenticada |
| Permiso requerido | — (Autenticado) |
| Prioridad | Alta |
| Reglas aplicables | `RN-SEG-009`, `RN-SEG-002` |
| Depende de | `RF-SP-024` |
| Tripleta | `docs/specs/sp/039-consultar-perfil-propio/` |
| Estado | Pendiente |

Devuelve el perfil del **actor y solo del actor**, sin exigir `users:read`. Toda interfaz autenticada lo necesita para saber qué mostrar: sin él, quien no administra usuarios no puede ver ni sus propios permisos. No se resolvió dentro de `RF-SP-026` porque su alcance de datos y su autorización son distintos —siempre el actor, nunca otro—, y mezclarlos obligaría a aquella consulta a comportarse de dos maneras según a quién apuntara el identificador. Nace de la aprobación de `RF-SP-026` el 21-08-2026.

#### `RF-SP-040` — Restablecer la propia contraseña olvidada

| Campo | Valor |
|---|---|
| Objetivo | Permitir que quien olvidó su contraseña la restablezca por sí mismo, sin conocer la vigente y sin intervención de un administrador |
| Actor | Cualquier persona con una cuenta en el sistema |
| Permiso requerido | — (público) |
| Prioridad | Alta |
| Reglas aplicables | — |
| Depende de | `RF-SP-024`, `RF-SP-037` |
| Tripleta | `docs/specs/sp/040-restablecer-contrasena-olvidada/` |
| Estado | Pendiente |

Hoy quien olvida su contraseña depende de que un administrador ejecute `RF-SP-038`, lo que significa que **ese administrador conoce temporalmente la credencial de otra persona**. El indicador de cambio obligatorio acota esa ventana pero no la elimina, y el camino administrativo no escala a los consumidores.

**Exige dos piezas que hoy no existen en ningún requerimiento:** un canal de correo y un token de un solo uso, con vigencia corta y validez única. Por eso no se resolvió dentro de `RF-SP-037`, que exige conocer la contraseña vigente. Nace de la aprobación de `RF-SP-037` el 21-08-2026.

Cuando se especifique arrastrará además dos decisiones ya anotadas como riesgo: la **verificación del correo** al cambiarlo (`RF-SP-027`, resolución 3), que deja de ser opcional en cuanto el correo sea la vía de recuperación, y la caducidad de la credencial provisional de `RF-SP-038`.

#### `RF-SP-041` — Asignar o cambiar el superior comercial de un usuario

| Campo | Valor |
|---|---|
| Objetivo | Registrar quién está a cargo de quién dentro de la fuerza comercial |
| Actor | Administrador |
| Permiso requerido | `users:assign-supervisor` |
| Prioridad | **Crítica** |
| Reglas aplicables | `RN-SP-019`, `RN-SP-020`, `RN-SP-021`, `RN-SP-003`, `RN-SP-011`, `RN-SP-017` |
| Depende de | `RF-SP-024`, `RF-SP-030` |
| Tripleta | `docs/specs/sp/041-asignar-superior-comercial/` |
| Estado | Pendiente |

Establece o sustituye el superior comercial de un usuario que porta un rol `VENDEDOR`. El superior ha de portar el rol padre inmediato del rol del subordinado (`RN-SP-020`), de modo que la estructura de personas no puede contradecir el orden de mando que ya declaran los roles.

Es **crítica** aunque sea una operación pequeña: `RN-SP-019` la vuelve inseparable del alta de un vendedor, y sin ella `RF-SP-024` y `RF-SP-030` no pueden conceder el primer rol `VENDEDOR` a nadie.

La sustitución **no borra la asignación anterior**: la cierra con su fecha de fin (`RN-SP-021`). Quién estaba a cargo de quién en una fecha dada es un dato de negocio, no una versión vieja de un dato.

Exige **motivo declarado**, que el Art. V.13 solo impone a las eliminaciones: aquí es una exigencia adicional, porque el historial sustentará el reparto de comisiones y un tramo sin explicación se convierte en un agujero cuando alguien discuta una liquidación. La asignación rige **siempre desde el momento de ejecutarse**; no admite fecha declarada.

#### `RF-SP-042` — Consultar el equipo a cargo de un usuario

| Campo | Valor |
|---|---|
| Objetivo | Ver el superior de una persona y las personas que tiene a cargo |
| Actor | Administrador |
| Permiso requerido | `users:read` |
| Prioridad | Media |
| Reglas aplicables | — |
| Depende de | `RF-SP-041` |
| Tripleta | `docs/specs/sp/042-consultar-equipo-a-cargo/` |
| Estado | Pendiente |

Devuelve, para el usuario indicado, su **superior inmediato** y su **equipo directo**: las personas de las que es superior hoy.

Dos cosas quedan deliberadamente fuera, y ambas por el mismo motivo:

- **No recorre el árbol completo.** Un manager no obtiene aquí a los agentes de sus directores.
- **No existe una variante «mi equipo»** que se resuelva contra el actor en lugar de contra un identificador.

La segunda es **alcance por persona**, que [`security.md` §6](../security.md) reserva hasta resolver **D-22**. La primera no lo es, pero carece de sentido sin ella: quien necesita ver su red descendente completa es el propio manager, no un administrador. Ambas se especificarán juntas cuando D-22 esté cerrada. Lo que este requerimiento sí garantiza mientras tanto es que **el dato ya está registrado** y que la consulta futura no tendrá que reconstruirlo.

## 7. Requerimientos no funcionales

Definidos en [`security.md` §11](../security.md) y en la constitución. Los que este módulo debe satisfacer:

| ID | Requerimiento |
|---|---|
| `RNF-SEG-001` | Autenticación y autorización basada en roles y permisos |
| `RNF-SEG-002` | Todo endpoint no declarado como público exige autenticación |
| `RNF-SEG-006` | Los eventos de seguridad quedan registrados en `audit_security_log` |
| `RNF-PERF-001` | Lectura p95 < 500 ms, escritura p95 < 1 s (Art. XV.9) |
| `RNF-FIA-001` | El **envío de notificaciones salientes es desacoplado de la respuesta** que lo origina, y su fallo no la altera ([`architecture.md` §15.1](../architecture.md)). No es una exigencia de rendimiento: `RF-SP-040` responde de forma indistinguible exista o no la identidad, y esperar al envío delataría el caso por el tiempo de respuesta |

## 8. Integraciones

Ninguna con sistemas externos ni con otros módulos. Al absorber los usuarios, sus roles y su acceso, `SP` deja de tener dependencias: es autocontenido y no necesita que ningún otro módulo exista para funcionar.

## 9. API

| Método | Ruta | Requerimiento | Permiso |
|---|---|---|---|
| `POST` | `/api/v1/roles` | `RF-SP-001` | `roles:create` |
| `GET` | `/api/v1/roles` | `RF-SP-002` | `roles:read` |
| `GET` | `/api/v1/roles/{id}` | `RF-SP-003` | `roles:read` |
| `PATCH` | `/api/v1/roles/{id}` | `RF-SP-004` | `roles:update` |
| `POST` | `/api/v1/roles/{id}/permissions` | `RF-SP-005` | `roles:update` |
| `DELETE` | `/api/v1/roles/{id}/permissions` | `RF-SP-006` | `roles:update` |
| `PATCH` | `/api/v1/roles/{id}/status` | `RF-SP-007` | `roles:update` |
| `PATCH` | `/api/v1/roles/{id}/parent` | `RF-SP-008` | `roles:update` |
| `DELETE` | `/api/v1/roles/{id}` | `RF-SP-009` | `roles:delete` |
| `GET` | `/api/v1/permissions` | `RF-SP-010` | `permissions:read` |
| `GET` | `/api/v1/audit/changes` | `RF-SP-011` | `audit:read-changes` |
| `GET` | `/api/v1/audit/deletions` | `RF-SP-012` | `audit:read-deletions` |
| `GET` | `/api/v1/audit/errors` | `RF-SP-013` | `audit:read-errors` |
| `GET` | `/api/v1/audit/security` | `RF-SP-014` | `audit:read-security` |
| `GET` | `/api/v1/permissions/{id}` | `RF-SP-015` | `permissions:read` |
| `POST` | `/api/v1/memberships` | `RF-SP-016` | `memberships:create` |
| `GET` | `/api/v1/memberships` | `RF-SP-017` | `memberships:read` |
| `GET` | `/api/v1/memberships/{id}` | `RF-SP-018` | `memberships:read` |
| `GET` | `/api/v1/currencies` | `RF-SP-019` | `currencies:read` |
| `POST` | `/api/v1/countries` | `RF-SP-020` | `countries:create` |
| `GET` | `/api/v1/countries` | `RF-SP-021` | `countries:read` |
| `PATCH` | `/api/v1/countries/{id}/status` | `RF-SP-022` | `countries:update` |
| `PATCH` | `/api/v1/currencies/{id}/status` | `RF-SP-023` | `currencies:update` |
| `POST` | `/api/v1/users` | `RF-SP-024` | `users:create` |
| `GET` | `/api/v1/users` | `RF-SP-025` | `users:read` |
| `GET` | `/api/v1/users/{id}` | `RF-SP-026` | `users:read` |
| `PATCH` | `/api/v1/users/{id}` | `RF-SP-027` | `users:update` |
| `PATCH` | `/api/v1/users/{id}/status` | `RF-SP-028` | `users:update` |
| `POST` | `/api/v1/users/{id}/deletion` | `RF-SP-029` | `users:delete` |
| `POST` | `/api/v1/users/{id}/roles` | `RF-SP-030` | `users:assign-roles` |
| `DELETE` | `/api/v1/users/{id}/roles` | `RF-SP-031` | `users:assign-roles` |
| `PUT` | `/api/v1/users/{id}/membership` | `RF-SP-032` | `users:assign-membership` |
| `DELETE` | `/api/v1/users/{id}/membership` | `RF-SP-033` | `users:assign-membership` |
| `POST` | `/api/v1/auth/login` | `RF-SP-034` | — |
| `POST` | `/api/v1/auth/refresh` | `RF-SP-035` | — |
| `POST` | `/api/v1/auth/logout` | `RF-SP-036` | — (público, autorizado por el refresh token) |
| `POST` | `/api/v1/auth/password` | `RF-SP-037` | Autenticado |
| `POST` | `/api/v1/users/{id}/password-reset` | `RF-SP-038` | `users:reset-password` |
| `POST` | `/api/v1/auth/password-recovery` | `RF-SP-040` | — (público) |
| `GET` | `/api/v1/users/me` | `RF-SP-039` | Autenticado |
| `PATCH` | `/api/v1/users/{id}/supervisor` | `RF-SP-041` | `users:assign-supervisor` |
| `GET` | `/api/v1/users/{id}/team` | `RF-SP-042` | `users:read` |

Rutas propuestas. El contrato exacto de cada una se fija en el `plan.md` de su tripleta.

## 10. Persistencia

| Entidad | Descripción | Dueño |
|---|---|---|
| `permissions` | Catálogo de permisos `recurso:acción` | `SP` |
| `roles` | Roles, su estado y su rol padre | `SP` |
| `role_permissions` | Permisos declarados por cada rol | `SP` |
| `memberships` | Niveles de acceso del consumidor | `SP` |
| `currencies` | Catálogo de monedas | `SP` |
| `countries` | Catálogo de países | `SP` |
| `users` | Personas que acceden al sistema, con su credencial y su estado | `SP` |
| `user_roles` | Roles asignados a cada usuario | `SP` |
| `user_memberships` | Membresía vigente de cada usuario consumidor | `SP` |
| `user_supervisors` | Superior comercial de cada vendedor, con su historial | `SP` |
| `refresh_tokens` | Sesiones revocables | `SP` |
| `audit_change_log` | Auditoría de creación y edición | `SP` |
| `audit_deletion_log` | Auditoría de eliminación | `SP` |
| `audit_error_log` | Auditoría de fallos | `SP` |
| `audit_security_log` | Auditoría de control de acceso | `SP` |

Estructura lógica en [`security.md` §9](../security.md) y [`architecture.md` §6.6](../architecture.md). El esquema exacto vive en las migraciones Flyway, que son su fuente de verdad (Art. V.3).

`SP` es dueño de las cuatro tablas de auditoría, pero **no es quien las escribe**: cada módulo emite sus propios eventos al ejecutar sus operaciones. `SP` publica la interfaz de escritura y es el único que las consulta por API.

### 10.1 Campos principales — `permissions`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `code` | `varchar(100)` | No | No | No | — | — |
| `resource` | `varchar(50)` | No | No | No | — | — |
| `action` | `varchar(50)` | No | No | No | — | — |
| `name` | `varchar(100)` | No | No | No | — | — |
| `description` | `text` | No | No | Sí | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

`code` es la concatenación `resource:action` y es único (`uq_permissions_code`). Se mantiene como columna propia para poder consultarlo y referenciarlo directamente.

### 10.2 Campos principales — `roles`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `code` | `varchar(50)` | No | No | No | — | — |
| `name` | `varchar(100)` | No | No | No | — | — |
| `description` | `text` | No | No | Sí | — | — |
| `role_type` | `varchar(20)` | No | No | No | — | — |
| `parent_role_id` | `uuid` | No | Sí | Sí | — | `roles` |
| `status` | `varchar(20)` | No | No | No | `ACTIVO` | — |
| `is_system` | `boolean` | No | No | No | `false` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

`description` lleva un `CHECK` de **500 caracteres** como longitud máxima (`ck_roles_description_length`). El límite se declara en el esquema, no solo en el DTO de entrada: sin un número, `VAL-007` de `RF-SP-001` no es implementable, y el listado de `RF-SP-002` devolvería respuestas de tamaño impredecible con hasta cien filas por página. Resuelto el 21-08-2026, al aprobar el plan de `RF-SP-001`.

`parent_role_id` es nulo **únicamente** en el rol raíz (`RN-SEG-007`, `RN-SP-002`). No implica herencia: acota los privilegios del rol hijo (`security.md` §4.2).

`role_type` clasifica el rol con dominio cerrado (`RN-SP-003`):

| Valor | Quién es |
|---|---|
| `FUNCIONARIO` | Personal interno de la empresa |
| `VENDEDOR` | Personal de la fuerza comercial |
| `CONSUMIDOR` | Cliente del sistema |

De él depende, entre otras cosas, qué roles pueden asociarse a una membresía —solo los `CONSUMIDOR`— y cuáles declaran rango comercial.

El orden de mando de la fuerza comercial se expresa con **`parent_role_id`**, el mismo campo que acota los permisos: el rol superior es el rol padre (`RN-SP-011`). No hay un campo de rango aparte.

!!! important "Consecuencia de usar `parent_role_id` para las dos cosas"

    Para los roles `VENDEDOR`, la cadena de mando y la contención de privilegios son **la misma relación**. Eso impone una condición permanente: **un rol comercial nunca puede tener un permiso que su superior no tenga**, porque `RN-SEG-003` lo rechazaría.

    Es coherente con la estructura actual —el agente hace menos que el director, y el director menos que el manager— y mantiene un solo lugar donde mirar. Si alguna vez se necesitara que un rol comercial pudiera algo que su superior no puede, habría que separar ambos ejes.

!!! note "El orden es entre roles, no entre personas"

    `parent_role_id` establece que un director está por encima de un agente. **No** dice qué agentes tiene un director concreto: esa es una relación entre personas y forma parte de la estructura comercial, hoy aparcada.

    Tampoco determina el alcance de datos, que sigue pendiente como **D-22**.

### 10.3 Campos principales — `role_permissions`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `role_id` | `uuid` | Sí | Sí | No | — | `roles` |
| `permission_id` | `uuid` | Sí | Sí | No | — | `permissions` |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |

Su clave primaria es **compuesta** (`role_id`, `permission_id`), y es la excepción declarada al Art. V.11: la unicidad del par es la restricción que importa, y una clave sustituta añadiría una columna sin significado.

### 10.4 Campos principales — `memberships`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `code` | `varchar(50)` | No | No | No | — | — |
| `name` | `varchar(100)` | No | No | No | — | — |
| `description` | `text` | No | No | Sí | — | — |
| `parent_membership_id` | `uuid` | No | Sí | Sí | — | `memberships` |
| `level` | `smallint` | No | No | No | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

`parent_membership_id` apunta a la membresía **de mayor nivel** y es nulo solo en la superior (`RN-SP-006`). `level` materializa el orden para poder consultarlo y ordenarlo sin recorrer la cadena; se recalcula al insertar una membresía nueva (`RN-SP-007`).

!!! important "La cadena de membresías no es un árbol"

    Cada membresía tiene **una sola** hija: es una lista ordenada, no una jerarquía ramificada. Insertar una membresía en medio reencadena a su hija y desplaza los niveles siguientes.

    Que dos membresías no puedan declarar la misma superior se garantiza **en el esquema**, con una restricción única sobre `parent_membership_id` (`uq_memberships_parent`), no solo en el dominio: el Art. V.6 exige declarar la integridad en la base de datos. Sin ella, la cadena podría bifurcarse y el orden dejaría de estar definido.

### 10.5 Campos principales — `currencies`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `code` | `char(3)` | No | No | No | — | — |
| `name` | `varchar(100)` | No | No | No | — | — |
| `symbol` | `varchar(10)` | No | No | Sí | — | — |
| `decimal_places` | `smallint` | No | No | No | `2` | — |
| `is_default` | `boolean` | No | No | No | `false` | — |
| `is_active` | `boolean` | No | No | No | `true` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |

`code` sigue ISO 4217 (`USD`). Se puebla por migración y no se modifica por API (`RN-SP-010`), salvo `is_active` a través de `RF-SP-023`.

`decimal_places` condiciona el redondeo de todo cálculo financiero y no siempre vale dos: hay monedas sin fracción, en las que cero es un valor legítimo. `is_default` marca la moneda con la que opera el sistema, y **exactamente una fila la lleva a `true`**: la restricción se declara en el esquema con un índice único parcial, no solo en el dominio (Art. V.6). La moneda por defecto no puede desactivarse.

### 10.6 Campos principales — `countries`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `code` | `char(2)` | No | No | No | — | — |
| `name` | `varchar(100)` | No | No | No | — | — |
| `is_active` | `boolean` | No | No | No | `true` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

`updated_at` se incorporó el 21-08-2026 al aprobar el `plan.md` de `RF-SP-020`: el Art. V.7 lo obliga en toda tabla de negocio, y aquí además hay algo que modificar —`RF-SP-022` cambia `is_active`—, de modo que sin la columna no habría forma de saber cuándo se retiró un país de la circulación salvo recorriendo la auditoría.

`code` sigue ISO 3166-1 alfa-2 (`CO`, `US`). No se edita ni elimina (`RN-SP-009`); lo único modificable es `is_active`, a través de `RF-SP-022`. El catálogo **no se siembra** con la lista internacional completa: los países se dan de alta por la API a medida que la plataforma llega a ellos.

### 10.7 Campos principales — `user_supervisors`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `user_id` | `uuid` | No | Sí | No | — | `users` |
| `supervisor_id` | `uuid` | No | Sí | No | — | `users` |
| `started_at` | `timestamptz` | No | No | No | `now()` | — |
| `ended_at` | `timestamptz` | No | No | Sí | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

`user_id` es el **subordinado** y `supervisor_id` el **superior**: la fila se lee «`user_id` está a cargo de `supervisor_id`». Una fila con `ended_at` nulo es la asignación **vigente**; las demás son historial cerrado y no se borran (`RN-SP-021`).

Es la única tabla del módulo que **relaciona dos usuarios entre sí**, y la primera pieza de la red comercial ([`modules.md` §6](../modules.md)).

!!! important "Por qué lleva clave sustituta y las otras asociaciones no"

    `role_permissions` y `user_roles` no llevan `id` porque la unicidad del par es toda la información que contienen (§1). Aquí no: el mismo par `(user_id, supervisor_id)` puede repetirse legítimamente si alguien vuelve a estar a cargo de quien ya lo estuvo, y lo que distingue una fila de otra es **el periodo**.

    Por el mismo motivo la unicidad es **parcial** y no total: `uq_user_supervisors_vigente` cubre `user_id` solo `WHERE ended_at IS NULL`. Una restricción única corriente sobre `user_id` haría imposible el historial, que es justo lo que `RN-SP-021` exige conservar.

!!! warning "Esta tabla no concede acceso a nada"

    Ninguna consulta de otro requerimiento debe filtrar por `user_supervisors` para decidir **qué datos ve** un usuario. Registrar la estructura y usarla como alcance son cosas distintas, y lo segundo espera a **D-22** ([`security.md` §6](../security.md)).

    Se anota aquí porque es el error probable: la tabla existe, la tentación de resolver con ella un alcance pendiente de diseñar es inmediata, y hacerlo dejaría el modelo de alcance repartido entre requerimientos en lugar de definido en un sitio.

### 10.8 Restricciones exigidas en el esquema

Declaradas en la base de datos, no solo en Java (Art. V.6):

| Restricción | Sobre |
|---|---|
| `uq_permissions_code` | `permissions(code)` |
| `uq_roles_code` | **Índice único parcial**: `roles(code) WHERE deleted_at IS NULL` — `RN-SEG-001` |
| `uq_roles_name` | **Índice único parcial**: `roles(name) WHERE deleted_at IS NULL` — `RN-SEG-001` |
| `fk_roles_parent` | `roles(parent_role_id)` → `roles(id)`, con restricción de eliminación — `RN-SEG-008` |
| `ck_roles_status` | `roles(status)` en (`ACTIVO`, `INACTIVO`) — `RN-SEG-002` |
| `ck_roles_type` | `roles(role_type)` en (`FUNCIONARIO`, `VENDEDOR`, `CONSUMIDOR`) — `RN-SP-003` |
| `fk_role_permissions_roles` | `role_permissions(role_id)` → `roles(id)` |
| `fk_role_permissions_permissions` | `role_permissions(permission_id)` → `permissions(id)` |
| `fk_memberships_parent` | `memberships(parent_membership_id)` → `memberships(id)` — `RN-SP-006` |
| `uq_memberships_parent` | `memberships(parent_membership_id)` — garantiza una sola hija por membresía |
| `ix_roles_busqueda` | Índice de trigramas sobre `roles` para la búsqueda insensible a mayúsculas y acentos. Requiere las extensiones `unaccent` y `pg_trgm`: la coincidencia es por contención, y un índice B-tree corriente no la sostiene |
| `ix_countries_busqueda` | Ídem sobre `countries` |
| `uq_memberships_code` | `memberships(code)` |
| `uq_currencies_code` | `currencies(code)` |
| `uq_countries_code` | `countries(code)` |
| `uq_countries_name` | **Índice único funcional**: `countries (f_unaccent(lower(name)))` — no sobre `name` literal. `RN-SP-009` no admite edición, de modo que `Panamá` y `Panama` conviviendo serían dos opciones indistinguibles **para siempre**. Es la asimetría deliberada con `uq_roles_name`, que sí es literal porque allí `RF-SP-004` permite renombrar |
| `ck_countries_code_format` | `countries(code ~ '^[A-Z]{2}$')` — `char(2)` acota la longitud pero admitiría `1`, `-` o un espacio de relleno |
| `ck_countries_name_not_blank` | `countries(length(btrim(name)) > 0)` |
| `uq_users_username` | **Índice único funcional total**: `users (lower(username))` — `RN-SP-016`. Va sobre la forma en minúsculas para que `JPerez` y `jperez` no puedan coexistir, y es **total** y no parcial porque eliminar a alguien **no libera** su nombre de usuario. Es la asimetría deliberada con `uq_roles_code`, que sí es parcial. **Obliga a `RF-SP-034` a comparar el nombre de usuario sin distinguir mayúsculas** |
| `uq_users_email` | `UNIQUE (email)` — `RN-SP-016`. Restricción corriente y no índice funcional, porque el correo se persiste ya normalizado. Total por el mismo motivo que la anterior |
| `ck_users_email_normalized` | `users(email = lower(btrim(email)))` — sin él, un `INSERT` directo mete `Juan@X.com` y `uq_users_email` deja de significar lo que dice |
| `ck_users_email_format` | `users(email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')` — comprobación de forma mínima; la validación buena está en el DTO |
| `ck_users_username_no_at` | `users(position('@' in username) = 0)` — `VAL-010`. **Es lo que sostiene el inicio de sesión con ambas identidades**: ningún nombre de usuario puede parecerse a un correo |
| `ck_users_username_format` | `users(username ~ '^[A-Za-z0-9._-]{3,50}$')` — sin espacios ni acentos. Un nombre con espacio al final es indistinguible del mismo sin él, y es permanente |
| `ck_users_names_not_blank` | `users(length(btrim(first_name)) > 0 AND length(btrim(last_name)) > 0)` |
| `ck_users_status` | `users(status)` en (`ACTIVO`, `INACTIVO`, `BLOQUEADO`, `PENDIENTE`) |
| `pk_user_roles` | **Clave primaria compuesta**: `user_roles(user_id, role_id)` |
| `fk_user_roles_user` | `user_roles(user_id)` → `users(id)`, `ON DELETE RESTRICT` |
| `fk_user_roles_role` | `user_roles(role_id)` → `roles(id)`, `ON DELETE RESTRICT` — red debajo de `RN-SEG-008` |
| `pk_user_memberships` | **Clave primaria**: `user_memberships(user_id)` — declara `RN-SP-014` en el esquema: una membresía por usuario |
| `fk_user_memberships_user` | `user_memberships(user_id)` → `users(id)`, `ON DELETE RESTRICT` |
| `fk_user_memberships_membership` | `user_memberships(membership_id)` → `memberships(id)`, `ON DELETE RESTRICT` |
| `ck_user_memberships_periodo` | `user_memberships(ends_at IS NULL OR ends_at > started_at)` |
| `fk_user_supervisors_user` | `user_supervisors(user_id)` → `users(id)` |
| `fk_user_supervisors_supervisor` | `user_supervisors(supervisor_id)` → `users(id)`, con restricción de eliminación — `RN-SP-022` |
| `uq_user_supervisors_vigente` | **Índice único parcial**: `user_supervisors(user_id) WHERE ended_at IS NULL` — `RN-SP-021`. Un solo superior vigente por persona; el historial cerrado no compite por esa unicidad |
| `ck_user_supervisors_no_self` | `user_supervisors(user_id <> supervisor_id)` — nadie está a cargo de sí mismo |
| `ck_user_supervisors_periodo` | `user_supervisors(ended_at IS NULL OR ended_at > started_at)` — un periodo cerrado no puede terminar antes de empezar |
| `ix_users_busqueda` | Índice de trigramas sobre `users`, en **tres expresiones**: `f_unaccent(lower(username))`, `f_unaccent(lower(email))` y `f_unaccent(lower(first_name \|\| ' ' \|\| last_name))`. La tercera es el **nombre completo concatenado**, y sin ella teclear `juan perez` no encuentra a nadie: ese texto no está contenido en ninguna de las dos columnas por separado. Lo declara `RF-SP-025` |
| `ix_user_memberships_membership_id` | `user_memberships(membership_id)` — filtro por membresía de `RF-SP-025`. La clave primaria va sobre `user_id` y no sirve a la consulta contraria |
| `ix_user_supervisors_supervisor_vigente` | **Índice parcial**: `user_supervisors(supervisor_id) WHERE ended_at IS NULL` — responde «¿quién está a cargo de esta persona **hoy**?», que es lo que preguntan `RN-SP-022` y `RF-SP-042`. Parcial y no total porque el historial cerrado nunca forma parte de esa respuesta y crecería indefinidamente dentro del índice. Lo declara `RF-SP-028`, y **sustituye al nombre `ix_user_supervisors_supervisor_id`** que el plan de `RF-SP-024` había anticipado: aquel describía un índice sobre una columna, y este lleva además una condición |

!!! important "La unicidad de rol es parcial, no total"

    `RN-SEG-001` exige que el nombre y el código de un rol sean únicos **entre los no eliminados lógicamente**. Una restricción única corriente lo impediría: el nombre de un rol borrado quedaría bloqueado para siempre.

    PostgreSQL lo resuelve con un índice único parcial:

    ```sql
    CREATE UNIQUE INDEX uq_roles_name ON roles (name) WHERE deleted_at IS NULL;
    ```

    Descubrirlo después de tener datos obliga a migrar la restricción con la tabla en uso.

`RN-SEG-006` (ausencia de ciclos), `RN-SEG-003` (contención), `RN-SP-001` (superadministrador siempre presente), `RN-SP-019` (superior comercial obligatorio), `RN-SP-020` (el superior porta el rol padre) y `RN-SP-022` (ningún equipo sin superior) **no** son expresables como restricción declarativa: se verifican en el dominio, y por eso exigen prueba unitaria propia.

Las tres últimas se apoyan además en datos de otras tablas —`user_roles` y `roles`—, de modo que ni siquiera un `CHECK` con subconsulta las sostendría: PostgreSQL no admite subconsultas en `CHECK`.

### 10.9 Campos de los registros de auditoría

Definidos en [`architecture.md` §6.6](../architecture.md), que detalla el núcleo común de las cuatro tablas y las columnas propias de cada una. No se repiten aquí.

### 10.10 Campos principales — `users`

Añadida el 22-08-2026 al aprobar el `plan.md` de `RF-SP-024`, que es quien crea la tabla (`V18__create_users.sql`).

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `username` | `varchar(50)` | No | No | No | — | — |
| `email` | `varchar(255)` | No | No | No | — | — |
| `first_name` | `varchar(100)` | No | No | No | — | — |
| `last_name` | `varchar(100)` | No | No | No | — | — |
| `password_hash` | `varchar(255)` | No | No | No | — | — |
| `must_change_password` | `boolean` | No | No | No | `false` | — |
| `status` | `varchar(20)` | No | No | No | `'ACTIVO'` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

**`username` se persiste tal como se escribió y su unicidad ignora la caja** (§10.8). El correo, en cambio, se persiste ya normalizado —recortado y en minúsculas— y su unicidad es una restricción corriente. La asimetría es deliberada: el nombre de usuario es como la persona aparece en la auditoría durante años, y el correo es una dirección de buzón cuya forma canónica es la minúscula.

!!! important "El esquema inicial no lleva todas las columnas del modelo lógico"

    [`security.md` §9](../security.md) enumera además `failed_attempts`, `locked_until` y `last_login_at`. **Las tres las crea `RF-SP-034`**, que es quien las escribe todas y quien se implementa primero; `RF-SP-028` únicamente las lee y las limpia al reactivar una cuenta. El reparto quedó fijado el 22-08-2026, al aprobarse los planes de `RF-SP-026` y `RF-SP-028`; hasta entonces este documento las atribuía a los dos sin repartirlas.

    No es una omisión: una columna disponible antes de que exista la regla que la gobierna se acaba usando por un camino que nadie diseñó. Añadirla es una migración corriente.

    **`deleted_at` es la excepción, y nace con la tabla en `V18`.** El plan de `RF-SP-024` la había dejado a `RF-SP-029`; se corrigió el 22-08-2026 (Art. I.7) por dos motivos. [`architecture.md` §6.4](../architecture.md) la declara **columna obligatoria de toda tabla de negocio**, junto a `id`, `created_at` y `updated_at`, de modo que su ausencia era una excepción que nadie había declarado. Y **diez requerimientos la leen antes de que `RF-SP-029` la escriba**: `RF-SP-003` y `RF-SP-009` se implementan antes y sus planes ya la daban por existente, y `RF-SP-025` a `RF-SP-027` no serían implementables sin ella. El criterio del párrafo anterior vale para las columnas que **nadie lee** hasta que llega su requerimiento; no vale para esta. Lo que sigue siendo de `RF-SP-029` es **escribirla**: es el único que la pone a un valor distinto de nulo.

### 10.11 Campos principales — `user_roles`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `user_id` | `uuid` | **Sí (compuesta)** | Sí | No | — | `users` |
| `role_id` | `uuid` | **Sí (compuesta)** | Sí | No | — | `roles` |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |

**No lleva clave sustituta ni `updated_at`**, y ninguna de las dos ausencias contradice el Art. V.7: la unicidad del par es toda la información que la fila contiene, y una asignación no se modifica —se crea y se borra—, de modo que una marca de última modificación sería siempre igual a la de creación. Mismo criterio que `role_permissions` (§10.3).

La crea `RF-SP-024` (`V19__create_user_roles.sql`), porque el alta ya escribe asignaciones. `RF-SP-030` añade `ix_user_roles_role_id`, que es el índice del que dependen `RF-SP-003` y `RF-SP-009` para contar cuántos usuarios porta un rol.

### 10.12 Campos principales — `user_memberships`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `user_id` | `uuid` | Sí | Sí | No | — | `users` |
| `membership_id` | `uuid` | No | Sí | No | — | `memberships` |
| `started_at` | `timestamptz` | No | No | No | `now()` | — |
| `ends_at` | `timestamptz` | No | No | Sí | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

**`user_id` es la clave primaria, y eso declara `RN-SP-014` en el esquema**: una membresía por usuario deja de ser una regla que el dominio debe recordar y pasa a ser imposible por construcción. `RF-SP-032` sustituye con un `UPDATE`, no insertando una fila nueva.

**`ends_at` nulo es una membresía indefinida.** Una fecha pasada no retira nada: `RN-SP-014` fija que la vigencia se evalúa al consultarla y que ningún proceso limpia la fila. Por eso no hay columna de estado ni marca de caducada — ese vacío es deliberado.

`RF-SP-024` la crea (`V20__create_user_memberships.sql`) y concede la membresía **indefinida**: el alta no admite fecha de fin, que se pone después con `RF-SP-032`.

!!! note "Por qué estas tres secciones van al final y no en su sitio"

    Las subsecciones de §10 se numeran **por orden de incorporación**, no por dependencia. Insertarlas entre las existentes obligaría a renumerar `user_supervisors`, las restricciones y la auditoría, y ocho `plan.md` ya aprobados referencian esos números. La legibilidad del orden vale menos que la estabilidad de las referencias.

## 11. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 20-08-2026 | Creación inicial. 14 requerimientos funcionales derivados de `security.md` y `modules.md`. | Responsable técnico |
| 0.2.0 | 20-08-2026 | §10 incorpora los campos principales y las restricciones del esquema, conforme a la plantilla de requerimientos por módulo. | Responsable técnico |
| 0.3.0 | 20-08-2026 | §4 registra los siete roles reales de la Épica 2 (HU08–HU14) y propone su jerarquía de contención. Se advierte la distinción entre contención de privilegios y jerarquía comercial. | Responsable técnico |
| 0.4.0 | 20-08-2026 | Se complementa con la guía `guides/001-sp.md`: tres submódulos nuevos (membresías, monedas, países), siete requerimientos, diez reglas propias `RN-SP-` y las consecuencias de esquema derivadas (unicidad parcial por borrado lógico y clasificación del rol). | Responsable técnico |
| 0.5.0 | 20-08-2026 | Se cierran los puntos abiertos: `SUPERADMIN` queda documentado como rol técnico y `ADMIN` como máximo rol de negocio, la convención `RN-SEG` frente a `RN-SP` queda resuelta, y la unicidad de hija de las membresías se garantiza en el esquema. | Responsable técnico |
| 0.6.0 | 20-08-2026 | `role_type` pasa a tres valores con `VENDEDOR`, y los roles vendedores declaran `sales_rank` para ordenar el mando dentro de la fuerza comercial. Nuevas reglas `RN-SP-011` y `RN-SP-012`. | Responsable técnico |
| 0.7.0 | 20-08-2026 | Se retira `sales_rank`: el orden de mando comercial se expresa con `parent_role_id`, el mismo campo que acota los permisos. `RN-SP-011` se reescribe y `RN-SP-012` queda retirada, con su número consumido. | Responsable técnico |
| 1.0.0 | 20-08-2026 | Primera versión aprobada. Los 21 requerimientos quedan registrados en la matriz de trazabilidad. | Responsable técnico |
| 1.1.0 | 20-08-2026 | §10.7 incorpora los índices funcionales de búsqueda insensible a mayúsculas y acentos, que exigen la extensión `unaccent`. | Responsable técnico |
| 1.2.0 | 20-08-2026 | Los índices de búsqueda pasan a ser de trigramas y exigen también la extensión `pg_trgm`: la coincidencia es por contención y un B-tree no la sostiene. | Responsable técnico |
| 1.3.0 | 21-08-2026 | El módulo absorbe los usuarios: cuatro submódulos nuevos, quince requerimientos (`RF-SP-024` a `RF-SP-038`), cinco reglas (`RN-SP-013` a `RN-SP-017`) y cuatro entidades. `SP` deja de depender de `USR`, que desaparece. | Responsable técnico |
| 1.3.0 | 21-08-2026 | Consecuencias de aprobar las specs de `RF-SP-010` a `RF-SP-021`. `RN-SP-007` admite crear una membresía sin indicar hija; `RN-SP-009` y `RN-SP-010` admiten cambiar el estado de países y monedas, y `RN-SP-008` deja constancia de que las membresías no lo llevan. Dos requerimientos nuevos, `RF-SP-022` y `RF-SP-023`. `currencies` incorpora `decimal_places`, `is_default` e `is_active`, y `countries` incorpora `is_active`. | Responsable técnico |
| 1.4.0 | 21-08-2026 | Consecuencias de aprobar las specs de `RF-SP-022` a `RF-SP-024`. **`RN-SP-016` se enmienda:** se contradecía a sí misma —declaraba la unicidad «entre los usuarios no eliminados» y a la vez que el nombre y el correo no se liberan al eliminar—; el «nombre de acceso» pasa a llamarse **nombre de usuario**, queda declarado inmutable y sin el carácter `@`, y se fija que tanto él como el correo sirven para iniciar sesión. El alta de usuario fija la contraseña inicial, la cuenta nace `ACTIVO` y marcada para cambio obligatorio de contraseña, y `PENDIENTE` queda declarado y sin usar hasta que exista un flujo de activación. `users` incorpora nombre, apellidos y el indicador de cambio de contraseña pendiente. | Responsable técnico |
| 1.5.0 | 21-08-2026 | Consecuencias de aprobar las specs de `RF-SP-025` y `RF-SP-026`. Se registra `RF-SP-039`, **consultar el propio perfil**, con su ficha, su ruta y su submódulo: ninguna persona sin `users:read` podía ver sus propios datos ni sus propios permisos, y toda interfaz autenticada los necesita. | Responsable técnico |
| 1.6.0 | 21-08-2026 | Consecuencias de aprobar `RF-SP-027`. `RN-SP-016` precisa que la reserva permanente del correo alcanza **solo a la eliminación**: al corregirlo, el anterior queda liberado, porque la auditoría no referencia a nadie por su correo. | Responsable técnico |
| 1.7.0 | 21-08-2026 | Consecuencias de aprobar `RF-SP-028`. `RN-SP-001` se enmienda: la condición se mide sobre usuarios **`ACTIVO`**, no sobre usuarios existentes —un superadministrador inactivo no administra nada—, alcanza también al bloqueo, y su comprobación debe serializarse sobre el conjunto de portadores activos del rol raíz. Misma lectura obligatoria en `RF-SP-029` y `RF-SP-031`. | Responsable técnico |
| 1.8.0 | 21-08-2026 | Consecuencias de aprobar el `plan.md` de `RF-SP-020`. §10.6 incorpora `updated_at` a `countries` \(Art. V.7, y `RF-SP-022` mueve la fila\). §10.7 incorpora `uq_countries_name` —**índice único funcional** sobre `f_unaccent(lower(name))`, no literal—, `ck_countries_code_format` y `ck_countries_name_not_blank`. | Responsable técnico |
| 1.9.0 | 21-08-2026 | Consecuencias de aprobar `RF-SP-032`. `RN-SP-014` admite una **fecha de fin opcional** en la asignación de membresía, cuya vigencia se evalúa al consultarla y no la retira ningún proceso; una membresía vencida conserva su fila sin conceder nivel. `RN-SP-015` se precisa: solo la membresía **vigente** impide retirar el último rol consumidor. | Responsable técnico |
| 1.10.0 | 21-08-2026 | Consecuencias de aprobar `RF-SP-033`. **Regla nueva `RN-SP-018`:** todo usuario con rol `CONSUMIDOR` debe tener membresía; el rol y el nivel son inseparables. `RN-SP-013` recoge que son recíprocas y `RN-SP-015` pasa de rechazar a **retirar la membresía en cascada**, porque el rechazo producía un bloqueo mutuo del que nadie podía salir. Se enmiendan `RF-SP-024`, `RF-SP-030` y `RF-SP-031`, ya aprobadas \(Art. I.7\): el primer rol de consumidor se concede indicando la membresía, y el último se retira arrastrándola. | Responsable técnico |
| 1.11.0 | 21-08-2026 | Consecuencias de aprobar `RF-SP-036`, `RF-SP-037` y `RF-SP-038`. `RF-SP-036` pasa a **público**, autorizado por el propio refresh token, y así se recoge en §6.1 y §9. Se registra `RF-SP-040` —restablecer la propia contraseña olvidada— con su ficha, su ruta y su submódulo: hasta que exista, el único camino de vuelta pasa por un administrador que conoce temporalmente la credencial ajena. | Responsable técnico |
| 1.12.0 | 22-08-2026 | **Se desaparca la estructura comercial persona → persona.** Submódulo nuevo «Estructura comercial», entidad nueva `user_supervisors` \(§10.7\) con su historial y sus cinco restricciones \(§10.8\), y dos requerimientos: `RF-SP-041` —asignar o cambiar el superior comercial— y `RF-SP-042` —consultar el equipo a cargo—. Cuatro reglas nuevas, `RN-SP-019` a `RN-SP-022`: el superior es obligatorio para todo vendedor salvo la cúspide, debe portar el rol padre inmediato del subordinado, es único vigente y no puede retirarse dejando un equipo huérfano. **Se enmiendan cinco especificaciones ya aprobadas** \(Art. I.7\): `RF-SP-024` y `RF-SP-030` conceden el rol `VENDEDOR` indicando el superior; `RF-SP-031` lo cierra al retirar el último; y `RF-SP-028` y `RF-SP-029` rechazan retirar el acceso o eliminar a quien tiene equipo a cargo. La nota de §4.1 se reescribe: la coincidencia entre la jerarquía de roles y la de personas deja de ser casual y pasa a ser exigida por `RN-SP-020`. **D-22 sigue abierta**: registrar la estructura no concede alcance sobre los datos, y §10.7 lo advierte donde puede cometerse el error. | Responsable técnico |
| 1.13.0 | 22-08-2026 | Consecuencias de aprobar `RF-SP-041`. **`RN-SP-017` se amplía**: además de eliminar y cambiar el estado, alcanza al cambio de superior comercial, la tercera operación en que el actor tendría interés directo sobre su propia cuenta. El cambio de superior **exige motivo** —adicional al Art. V.13, que solo lo impone a las eliminaciones— y **no admite fecha declarada**: rige desde que se ejecuta. No emite evento en la auditoría de seguridad, con condición de disparo anotada para cuando D-22 haga depender el alcance de datos de esta relación. | Responsable técnico |
| 1.14.0 | 22-08-2026 | Se redactan las especificaciones de `RF-SP-039`, `RF-SP-040` y `RF-SP-042`, las tres últimas que faltaban: sus fichas dejan de decir «pendiente de redactar» y apuntan a su tripleta. Quedan **en revisión**. Al aprobarse `RF-SP-040`, §9 necesitará una **segunda ruta**: el requerimiento comprende dos operaciones públicas encadenadas —solicitar el permiso temporal y consumirlo—, y hoy la tabla solo declara una. | Responsable técnico |
| 1.15.0 | 22-08-2026 | Consecuencias de aprobar `RF-SP-040`. §7 incorpora **`RNF-FIA-001`**: el envío de notificaciones salientes es **desacoplado de la respuesta** que lo origina y su fallo no la altera. No es rendimiento: `RF-SP-040` responde de forma indistinguible exista o no la identidad, y esperar al envío delataría el caso por el tiempo. El canal queda decidido como infraestructura transversal en `architecture.md` §15.1, y la vigencia del permiso temporal en `security.md` §3.2. Con esta aprobación, **las cuarenta y dos specs del módulo quedan cerradas**. | Responsable técnico |
| 1.16.0 | 22-08-2026 | Consecuencias de aprobar el `plan.md` de `RF-SP-024`. §10 gana tres secciones nuevas —**§10.10 `users`, §10.11 `user_roles` y §10.12 `user_memberships`**—, que la tabla de entidades listaba sin campos declarados, y §10.8 gana sus quince restricciones. Tres decisiones que alcanzan a todo el módulo: `uq_users_username` va sobre `lower(username)` y es **total**, no parcial —`RN-SP-016` no libera nada al eliminar—, lo que **obliga a `RF-SP-034`** a comparar el nombre de usuario sin distinguir mayúsculas; `pk_user_memberships` sobre `user_id` declara `RN-SP-014` en el esquema; y `user_roles` la crea `RF-SP-024` y no `RF-SP-030`, porque el alta ya escribe asignaciones. Las subsecciones se numeran por orden de incorporación para no renumerar las referencias de ocho planes aprobados. | Responsable técnico |
| 1.17.0 | 22-08-2026 | Consecuencias de aprobar los `plan.md` de **`RF-SP-025` a `RF-SP-029`**, con lo que el submódulo de Usuarios queda con su tripleta completa. §6.1 gana las **precedencias del bloque**: `RF-SP-034` antes de los seis requerimientos que revocan sesiones, `RF-SP-028` antes de `RF-SP-029` y `RF-SP-031`, y `RF-SP-030` antes de `RF-SP-025`. §10.8 incorpora tres índices: `ix_users_busqueda` —de trigramas, con el **nombre completo concatenado** como tercera expresión, porque sin ella `juan perez` no encuentra a nadie—, `ix_user_memberships_membership_id` e `ix_user_supervisors_supervisor_vigente`, **parcial** y con el nombre corregido respecto del que anticipó `RF-SP-024`. §10.10 incorpora **`deleted_at` a `users`**: nace con la tabla y no con `RF-SP-029`, porque `architecture.md` §6.4 la declara obligatoria en toda tabla de negocio y **diez requerimientos la leen antes de que alguien la escriba** \(Art. I.7\); y precisa que las tres columnas de control de acceso son de `RF-SP-034`, que `RF-SP-028` solo lee y limpia. | Responsable técnico |
