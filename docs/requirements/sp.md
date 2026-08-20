# Requerimientos del Módulo — `SP` Sistema Principal

| Campo | Valor |
|---|---|
| Módulo | `SP` — Sistema Principal |
| Paquete | `modules/system` |
| Versión | 0.3.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 20-08-2026 |
| Última actualización | 20-08-2026 |

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
| Permisos | Catálogo de permisos. Solo lectura por API; se pueblan por migración | `RF-SP-010` |
| Roles | Alta, edición, estado, contención de privilegios y jerarquía | `RF-SP-001` a `RF-SP-009` |
| Auditoría | Consulta de los cuatro registros | `RF-SP-011` a `RF-SP-014` |
| Parámetros | Configuración transversal del sistema | *(sin requerimientos: alcance por definir)* |

!!! warning "Submódulo Parámetros sin definir"

    `modules.md` §5.1 registra este submódulo con sus entidades «por definir». Mientras no se le asignen requerimientos, el módulo `SP` no puede darse por completo.

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
| `ESTUDIANTE` | Estudiante | `ADMIN` | Sí |

!!! danger "Dos jerarquías distintas que NO deben confundirse"

    La columna «Rol padre» de esta tabla es **contención de privilegios**: acota qué permisos puede declarar cada rol (`RN-SEG-003`). Es una relación **rol → rol**.

    La cadena comercial **manager → director → agente** de HU10, HU11 y HU12 es otra cosa: una relación **persona → persona** que determina **de quién** puede ver los datos cada uno.

    Los tres roles piden el **mismo permiso** (`commissions:read`); lo que cambia es el conjunto de datos. Modelar la red comercial en `roles.parent_role_id` sería un error: esa columna no acota datos, y además relaciona roles, no usuarios. La red comercial necesita su propia estructura, en `USR` o en un módulo de red comercial.

    Que ambas jerarquías coincidan en forma es una casualidad de este dominio, no una equivalencia.

!!! warning "Requiere confirmación"

    - **¿`ADMIN` contiene realmente a `CONTABILIDAD`?** La contención exige que `ADMIN` posea **todos** los permisos financieros. HU08 menciona pagos y comisiones, pero no retiros, balances ni egresos. Si el negocio quiere que Contabilidad pueda algo que Administración no, `CONTABILIDAD` debe colgar de `SUPERADMIN`, no de `ADMIN`.
    - **¿`ADMIN` es el rol raíz, o existe `SUPERADMIN` por encima?** HU08 dice «acceso completo», y `RN-SEG-007` exige exactamente un rol raíz. Si `ADMIN` fuera la raíz, no habría quién lo cree ni quién lo acote.
    - **HU14 tiene el actor cambiado:** está redactada desde el administrador que asigna el rol, no desde el líder académico. Esa funcionalidad pertenece a `USR`; falta la historia del líder académico desde su propia perspectiva.

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

!!! danger "Punto abierto: prefijo de las reglas"

    La nomenclatura de [`requirements.md` §3.1](../requirements.md) define las reglas de negocio como `RN-[MÓDULO]-NNN`, pero estas usan `RN-SEG-…`, y `SEG` **no es un código de módulo**: es una categoría de requerimiento no funcional.

    Hay que decidir antes de que las reglas se referencien desde el código:

    - **Renombrar** a `RN-SP-…` las que pertenecen a este módulo. Cumple la nomenclatura, pero `RN-SEG-009`, `010` y `011` abarcan también a `USR`, así que ninguna asignación es limpia.
    - **Declarar `SEG` como espacio de reglas transversales de seguridad**, documentando la excepción en `requirements.md` §3.1.

    Recomiendo la segunda: son reglas que cruzan módulos por naturaleza, y renombrarlas obligaría a partir tres de ellas.

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

**Orden sugerido de implementación:** `RF-SP-010` → `RF-SP-001` → `RF-SP-002` → `RF-SP-005` → el resto. El catálogo de permisos es prerrequisito de todo lo demás, y sin roles no hay nada que auditar.

### 6.2 Fichas

#### `RF-SP-001` — Registrar rol

| Campo | Valor |
|---|---|
| Objetivo | Permitir que la organización defina un rol nuevo sin desplegar código |
| Actor | Administrador |
| Permiso requerido | `roles:create` |
| Prioridad | Crítica |
| Reglas aplicables | `RN-SEG-001`, `RN-SEG-003`, `RN-SEG-007`, `RN-SEG-010` |
| Depende de | `RF-SP-010` |
| Tripleta | `docs/specs/sp/001-registrar-rol/` |
| Estado | Pendiente |

El sistema debe permitir a un usuario autorizado registrar un rol con su código, nombre, descripción, rol padre y conjunto inicial de permisos. Los permisos declarados quedan acotados por los del rol padre y por los del propio actor que lo crea.

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

Rutas propuestas. El contrato exacto de cada una se fija en el `plan.md` de su tripleta.

## 10. Persistencia

| Entidad | Descripción | Dueño |
|---|---|---|
| `permissions` | Catálogo de permisos `recurso:acción` | `SP` |
| `roles` | Roles, su estado y su rol padre | `SP` |
| `role_permissions` | Permisos declarados por cada rol | `SP` |
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
| `parent_role_id` | `uuid` | No | Sí | Sí | — | `roles` |
| `status` | `varchar(20)` | No | No | No | `ACTIVO` | — |
| `is_system` | `boolean` | No | No | No | `false` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

`parent_role_id` es nulo **únicamente** en el rol raíz (`RN-SEG-007`). No implica herencia: acota los privilegios del rol hijo (`security.md` §4.2).

### 10.3 Campos principales — `role_permissions`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `role_id` | `uuid` | Sí | Sí | No | — | `roles` |
| `permission_id` | `uuid` | Sí | Sí | No | — | `permissions` |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |

Su clave primaria es **compuesta** (`role_id`, `permission_id`), y es la excepción declarada al Art. V.11: la unicidad del par es la restricción que importa, y una clave sustituta añadiría una columna sin significado.

### 10.4 Restricciones exigidas en el esquema

Declaradas en la base de datos, no solo en Java (Art. V.6):

| Restricción | Sobre |
|---|---|
| `uq_permissions_code` | `permissions(code)` |
| `uq_roles_code` | `roles(code)` — `RN-SEG-001` |
| `uq_roles_name` | `roles(name)` — `RN-SEG-001` |
| `fk_roles_parent` | `roles(parent_role_id)` → `roles(id)`, con restricción de eliminación — `RN-SEG-008` |
| `ck_roles_status` | `roles(status)` en (`ACTIVO`, `INACTIVO`) — `RN-SEG-002` |
| `fk_role_permissions_roles` | `role_permissions(role_id)` → `roles(id)` |
| `fk_role_permissions_permissions` | `role_permissions(permission_id)` → `permissions(id)` |

`RN-SEG-006` (ausencia de ciclos) y `RN-SEG-003` (contención) **no** son expresables como restricción declarativa: se verifican en el dominio, y por eso exigen prueba unitaria propia.

### 10.5 Campos de los registros de auditoría

Definidos en [`architecture.md` §6.6](../architecture.md), que detalla el núcleo común de las cuatro tablas y las columnas propias de cada una. No se repiten aquí.

## 11. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 20-08-2026 | Creación inicial. 14 requerimientos funcionales derivados de `security.md` y `modules.md`. | Responsable técnico |
| 0.2.0 | 20-08-2026 | §10 incorpora los campos principales y las restricciones del esquema, conforme a la plantilla de requerimientos por módulo. | Responsable técnico |
| 0.3.0 | 20-08-2026 | §4 registra los siete roles reales de la Épica 2 (HU08–HU14) y propone su jerarquía de contención. Se advierte la distinción entre contención de privilegios y jerarquía comercial. | Responsable técnico |
