# Requerimientos del Módulo — `SP` Sistema Principal

| Campo | Valor |
|---|---|
| Módulo | `SP` — Sistema Principal |
| Paquete | `modules/system` |
| Versión | 1.2.0 |
| Estado | **Aprobado** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 20-08-2026 |
| Última actualización | 20-08-2026 |
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

**No incluye**

- Los usuarios, sus credenciales y su autenticación → módulo `USR`.
- La asignación de roles a personas → módulo `USR`, porque su sujeto es el usuario y no el rol.
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
| `Cliente` | Cliente | `ADMIN` | No |

!!! important "Qué acota `parent_role_id`, y qué no"

    La columna «Rol padre» es **contención de privilegios**: acota qué permisos puede declarar cada rol (`RN-SEG-003`). Es una relación **rol → rol** y no dice nada sobre los datos.

    La estructura comercial —manager, director y agente— es otra cosa: una relación **persona → persona** que determina **de quién** puede ver los datos cada uno. Los tres roles necesitan el mismo permiso sobre comisiones; lo que cambia es el conjunto de registros.

    Modelar esa estructura en `roles.parent_role_id` sería un error: esa columna no acota datos y relaciona roles, no usuarios. Cuando el alcance comercial se retome, necesitará su propia estructura y su propio modelo de alcance (D-22).

    Que ambas jerarquías coincidan en forma es una casualidad de este dominio, no una equivalencia.

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

    `RN-SEG-…` es el espacio de las **reglas transversales de seguridad**: gobiernan la autorización en todo el sistema y varias de ellas —`RN-SEG-009`, `010` y `011`— alcanzan también a `USR`. Renombrarlas al código de un módulo obligaría a partirlas.

    Las reglas propias de este módulo sí llevan su prefijo y están en §5.1 como `RN-SP-…`. La convención completa está en [`requirements.md` §3.1](../requirements.md).

### 5.1 Reglas propias del módulo

Reglas que no son transversales de seguridad y por tanto sí llevan el prefijo del módulo.

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-SP-001` | Superadministrador siempre presente | Al eliminar o desactivar un usuario, o al retirarle el rol | Debe existir siempre al menos un usuario con rol `SUPERADMIN`; la operación que dejaría al sistema sin ninguno se rechaza | **Crítica** |
| `RN-SP-002` | Rol padre obligatorio | Al crear o editar un rol | Todo rol declara un rol padre, salvo `SUPERADMIN`, que es el único sin él | Alta |
| `RN-SP-003` | Clasificación del rol | Al crear un rol | Todo rol se clasifica como `FUNCIONARIO` (personal interno), `VENDEDOR` (personal de la fuerza comercial) o `CONSUMIDOR` (cliente del sistema) | Alta |
| `RN-SP-011` | Orden de mando comercial | Al crear o reubicar un rol `VENDEDOR` | El orden de mando de la fuerza comercial se expresa con `parent_role_id`: el rol superior es el rol padre. No existe un campo de rango aparte | Alta |
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

**Orden sugerido de implementación:** `RF-SP-010` → `RF-SP-001` → `RF-SP-002` → `RF-SP-005` → el resto. El catálogo de permisos es prerrequisito de todo lo demás, y sin roles no hay nada que auditar.

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
| Tripleta | Pendiente de redactar |
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
| Tripleta | Pendiente de redactar |
| Estado | Pendiente |

Activa o desactiva una moneda. **La moneda por defecto no puede desactivarse**: dejaría los importes del sistema sin referencia válida. Nace de la aprobación de `RF-SP-019` el 21-08-2026.

## 7. Requerimientos no funcionales

Definidos en [`security.md` §11](../security.md) y en la constitución. Los que este módulo debe satisfacer:

| ID | Requerimiento |
|---|---|
| `RNF-SEG-001` | Autenticación y autorización basada en roles y permisos |
| `RNF-SEG-002` | Todo endpoint no declarado como público exige autenticación |
| `RNF-SEG-006` | Los eventos de seguridad quedan registrados en `audit_security_log` |
| `RNF-PERF-001` | Lectura p95 < 500 ms, escritura p95 < 1 s (Art. XV.9) |

## 8. Integraciones

Ninguna con sistemas externos. Internamente, `USR` consume de este módulo el catálogo de roles y la resolución de permisos efectivos, a través de la interfaz publicada por su capa `application` (`architecture.md` §5.3).

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

`code` sigue ISO 3166-1 alfa-2 (`CO`, `US`). No se edita ni elimina (`RN-SP-009`); lo único modificable es `is_active`, a través de `RF-SP-022`. El catálogo **no se siembra** con la lista internacional completa: los países se dan de alta por la API a medida que la plataforma llega a ellos.

### 10.7 Restricciones exigidas en el esquema

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

!!! important "La unicidad de rol es parcial, no total"

    `RN-SEG-001` exige que el nombre y el código de un rol sean únicos **entre los no eliminados lógicamente**. Una restricción única corriente lo impediría: el nombre de un rol borrado quedaría bloqueado para siempre.

    PostgreSQL lo resuelve con un índice único parcial:

    ```sql
    CREATE UNIQUE INDEX uq_roles_name ON roles (name) WHERE deleted_at IS NULL;
    ```

    Descubrirlo después de tener datos obliga a migrar la restricción con la tabla en uso.

`RN-SEG-006` (ausencia de ciclos), `RN-SEG-003` (contención) y `RN-SP-001` (superadministrador siempre presente) **no** son expresables como restricción declarativa: se verifican en el dominio, y por eso exigen prueba unitaria propia.

### 10.5 Campos de los registros de auditoría

Definidos en [`architecture.md` §6.6](../architecture.md), que detalla el núcleo común de las cuatro tablas y las columnas propias de cada una. No se repiten aquí.

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
| 1.3.0 | 21-08-2026 | Consecuencias de aprobar las specs de `RF-SP-010` a `RF-SP-021`. `RN-SP-007` admite crear una membresía sin indicar hija; `RN-SP-009` y `RN-SP-010` admiten cambiar el estado de países y monedas, y `RN-SP-008` deja constancia de que las membresías no lo llevan. Dos requerimientos nuevos, `RF-SP-022` y `RF-SP-023`. `currencies` incorpora `decimal_places`, `is_default` e `is_active`, y `countries` incorpora `is_active`. | Responsable técnico |
