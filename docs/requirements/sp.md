# Requerimientos del Módulo — `SP` Sistema Principal

| Campo | Valor |
|---|---|
| Módulo | `SP` — Sistema Principal |
| Paquete | `modules/system` |
| Prefijos de permiso | `roles:`, `permissions:`, `audit:`, `memberships:`, `currencies:`, `countries:`, `users:`, `exchange-rates:`, `document-types:`, `brokers:`, `broker-accounts:`, `teams:` |
| Versión | 1.79.0 |
| Estado | **Aprobado** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 20-08-2026 |
| Última actualización | 22-09-2026 |
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
- Equipos: alta, consulta, edición, estado y eliminación de los equipos en que se organiza la cúspide de la fuerza comercial, y asignación y retiro de sus managers.
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
| **Tasas de cambio** | A cuánto se cambia una moneda por otra, y desde cuándo. **Se administra por API**, al revés que el catálogo de monedas | `RF-SP-047` a `RF-SP-050` |
| Países | Catálogo de países | `RF-SP-020`, `RF-SP-021` |
| **Tipos de documento** | Catálogo de los documentos de identidad admitidos. **Solo lectura por API**, como el de monedas — y con una diferencia que lo define: **solo contiene documentos de persona mayor de edad**, de modo que el catálogo *es* la validación | `RF-SP-051` |
| **Brokers** | Catálogo de los brokers con los que opera la plataforma, y **la cuenta que cada persona tiene en cada uno**. El catálogo es **solo lectura por API** como el de monedas; el vínculo lo declara quien abre la cuenta, **lo consulta el superior comercial** y **lo completa un webhook del broker** | `RF-SP-052` a `RF-SP-056` |
| **Usuarios** | Alta, consulta, edición, estado y baja de las personas que acceden al sistema, y consulta del propio perfil | `RF-SP-024` a `RF-SP-029`, `RF-SP-039` |
| **Roles de usuario** | Asignación y retiro de roles sobre una persona | `RF-SP-030`, `RF-SP-031` |
| **Membresía del usuario** | Asignación y retiro del nivel de acceso de un consumidor | `RF-SP-032`, `RF-SP-033` |
| **Estructura comercial** | Quién está a cargo de quién dentro de la fuerza comercial: el superior de cada vendedor y el equipo que tiene a cargo | `RF-SP-041`, `RF-SP-042` |
| **Equipos** | Cómo se organiza **la cúspide** de la fuerza comercial: cada equipo reúne managers —quienes portan el rol vendedor de mayor rango y por eso no tienen superior (`RN-SP-019`)—, y con cada manager entra, por su cadena de mando en `user_supervisors`, toda la red que cuelga de él. **Un equipo no manda**: agrupa | `RF-SP-063` a `RF-SP-070` |
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
| Manager | Consume roles; no los administra | — |
| Director | Consume roles; no los administra | — |
| Agente o vendedor | Consume roles; no los administra | — |
| Estudiante | Consume roles; no los administra | — |

Solo el **Super Administrador** y **Administrador** operan sobre `SP`. Los demás roles aparecen aquí porque son los sujetos que `SP` define, no porque ejecuten sus requerimientos.

Los permisos de auditoría se conceden por separado: los cuatro registros no tienen la misma sensibilidad y no se leen en bloque ([`security.md` §4.4](../security.md)).

### 4.1 Catálogo inicial de roles del sistema

Propuesta de códigos y de jerarquía de **contención de privilegios** (`RN-SEG-003`). Cierra parcialmente la decisión D-17.

!!! warning "Dos roles se retiraron del catálogo el 29-08-2026"

    `CONTABILIDAD` y `LIDER_ACADEMICO` se sembraban desde `V7` y **se retiraron por decisión del responsable del proyecto**. La migración `V7__seed_system_roles.sql` se editó en el sitio, y esa edición tiene una consecuencia que hay que conocer: **Flyway valida las migraciones aplicadas por suma de comprobación**, de modo que cualquier base donde `V7` ya estuviera aplicada —un entorno local, `develop`— **fallará al arrancar** hasta que se recree o se repare su historial. Sobre una base limpia no hay nada que hacer.

    `CONTABILIDAD` no era decorado en las pruebas: era el **único rol sembrado con permisos acotados**, y esa forma —un hijo de `ADMIN` con dos permisos y ninguno más— es la que hacía verificables la contención de privilegios y que un token autentique sin conceder de más. `LIDER_ACADEMICO` era el **único nombre sembrado con un acento**, del que dependía la búsqueda sin acentos y el índice de trigramas de `V32`. Las pruebas afectadas **no se repuntaron a otro rol**: se les dio un fixture propio, de modo que no vuelvan a depender de qué siembre el sistema.

| Código | Nombre | Rol padre | `is_system` |
|---|---|---|---|
| `SUPERADMIN` | Superadministrador | — | Sí |
| `ADMIN` | Administrador | `SUPERADMIN` | Sí |
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

    `ADMIN` es el **máximo rol de negocio**: tiene acceso completo a la operación, y por eso todos los roles funcionales cuelgan de él. La contención (`RN-SEG-003`) obliga entonces a que `ADMIN` posea todo permiso que cualquier rol de negocio declare, incluidos los financieros que llegue a declarar cualquier rol contable que se cree por la API.

    **Consecuencia a tener presente:** con este diseño no hay separación entre quien configura el sistema y quien controla el dinero. Si en algún momento se quiere que un rol contable pueda aprobar algo que Administración no, habrá que colgarlo de `SUPERADMIN` y no de `ADMIN`.

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
| `RN-SEG-012` | Los roles de sistema no se editan, reubican, desactivan ni eliminan por la API; **sus permisos sí se administran** | `RF-SP-004`, `RF-SP-007` a `RF-SP-009` |
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
| `RN-SP-013` | ~~Membresía solo para consumidores~~ | — | **RETIRADA el 05-09-2026**, por decisión del responsable del proyecto. Decía que asignar una membresía exigía portar al menos un rol `CONSUMIDOR`, y que el rol y el nivel eran **inseparables**. Desde que **toda persona tiene membresía** (`RN-SP-018`, reescrita), esa exigencia se contradice con la regla que la sustituye: el superadministrador y los funcionarios tienen nivel y no son consumidores de nada. **La fila se conserva y no se borra**: el código estaba citado en dos respuestas de error, en cinco `plan.md` aprobados y en `flujos-del-modulo.md`, y borrarlo dejaría referencias colgando | — |
| `RN-SP-014` | Una membresía vigente por usuario | Al asignar una membresía | Un usuario tiene como mucho **una membresía vigente**, y **conserva todas las que tuvo**: conceder otra **cierra la anterior e inserta una fila nueva**, no reescribe la que había. La asignación admite una **fecha de fin opcional**: sin ella es indefinida, y con ella deja de estar **vigente** al pasar. **La vigencia se evalúa al consultarla, no la retira ningún proceso** (`RF-SP-032`), de modo que una membresía vencida conserva su fila —y su plaza— hasta que se renueve o se retire, sin conceder nivel alguno. **Terminar por vencimiento y terminar por sustitución son dos hechos distintos y se guardan por separado**: `ends_at` dice hasta cuándo se pagó y `closed_at` cuándo se cerró de verdad, de modo que «pagó hasta el 30 y se la reemplazaron el 12» es una pregunta que el sistema sabe responder. Enmendada el 05-09-2026: hasta entonces la tabla llevaba **una sola fila por persona** y asignar sustituía con un `UPDATE`, sin dejar rastro del nivel anterior | Alta |
| `RN-SP-015` | ~~Retiro del último rol consumidor~~ | — | **RETIRADA el 05-09-2026**, junto con `RN-SP-013` y por lo mismo. Decía que quedarse sin rol `CONSUMIDOR` **retiraba la membresía** en la misma transacción. Con `RN-SP-018` reescrita esa cascada no puede existir: dejaría sin nivel a alguien que debe tener uno. **Y lo que se hace en su lugar es no hacer nada** — quien deja de ser consumidor **conserva la membresía que tenía**, incluida una comprada. Bajarla al suelo sería quitarle algo que pagó, y ninguna regla pide eso | — |
| `RN-SP-018` | Todo usuario tiene membresía | Siempre | **Toda persona tiene una membresía vigente**, y no solo los consumidores. Quien no recibe una al registrarse **arranca en la más baja**, que es la de código `BECA` — sembrada por `V46`, imposible de borrar (`RN-SP-008`) y única por `uq_memberships_code`. El estado «usuario sin nivel» **no existe para nadie**. En consecuencia: el alta **ya no exige indicar membresía** (`RF-SP-024`), asignar roles **ya no la admite** —cambiarla es `RF-SP-032` y tiene su propio permiso (`RF-SP-030`)—, y retirarla **devuelve al suelo en lugar de dejar sin nada** (`RF-SP-033`). **Reescrita el 05-09-2026**; hasta entonces decía «todo consumidor tiene membresía» y ataba el nivel al rol, con `RN-SP-013` y `RN-SP-015` sosteniendo las dos mitades de esa atadura | **Crítica** |
| `RN-SP-016` | Identidad no reutilizable | Al registrar o editar un usuario | El nombre de usuario y el correo son únicos entre **todos** los usuarios, incluidos los eliminados. A diferencia del rol, **no se liberan al eliminar**: reutilizarlos permitiría que la actividad de dos personas distintas se confundiera en la auditoría. El **nombre de usuario** es además inmutable y no admite el carácter `@`; el correo sí puede corregirse (`RF-SP-027`), y ambos sirven para iniciar sesión. **La reserva permanente alcanza solo a la eliminación:** al corregir el correo de una persona, el anterior **queda liberado** y otro usuario puede tomarlo, porque la auditoría no referencia a nadie por su correo | **Crítica** |
| `RN-SP-017` | Un usuario no se opera a sí mismo | Al eliminar un usuario, al cambiar su estado y **al cambiar su superior comercial** | El actor no puede aplicar la operación sobre su propia cuenta. Alcanza a las tres operaciones en que quien ejecuta tendría interés directo en el resultado: dejar de existir, recuperar su propio acceso y **reubicarse en la estructura comercial**, de la que cuelga la atribución de su producción. Ampliada el 22-08-2026 al aprobar `RF-SP-041`; antes solo alcanzaba a eliminar y desactivar | Alta |
| `RN-SP-011` | Orden de mando comercial | Al crear o reubicar un rol `VENDEDOR` | El orden de mando de la fuerza comercial se expresa con `parent_role_id`: el rol superior es el rol padre. No existe un campo de rango aparte | Alta |
| `RN-SP-019` | Superior comercial obligatorio | Al registrar un usuario, al asignarle roles y al retirárselos | Un usuario que porta al menos un rol de clasificación `VENDEDOR` **debe tener un superior comercial**, salvo quien porta el rol vendedor de mayor rango —aquel cuyo rol padre no es `VENDEDOR`—, que es la cúspide de la fuerza comercial y no declara ninguno. El estado «vendedor sin superior» no existe: toda operación que conceda el primer rol `VENDEDOR` de una persona **o que cambie cuál es su rol vendedor de mayor rango** —un ascenso— **exige indicar el superior en la misma operación** (`RF-SP-024`, `RF-SP-030`), y retirar el último rol `VENDEDOR` **retira su superior** en la misma transacción (`RF-SP-031`), auditando ambos hechos bajo el mismo identificador de correlación. Es la forma que `RN-SP-018` ya da al par consumidor-membresía, con una exigencia más: allí el nivel no depende de qué rol se conceda después, y aquí sí | **Crítica** |
| `RN-SP-020` | El superior porta el rol padre | Al asignar o cambiar el superior comercial y al conceder un rol `VENDEDOR` | El superior debe portar el rol **padre inmediato** del **rol vendedor de mayor rango** que porta el subordinado (`RN-SP-011`): quien es `AGENTE` reporta a quien porta `DIRECTOR`, nunca a otro `AGENTE` ni directamente a un `MANAGER`. La validación es contra el padre inmediato y **no recorre ancestros**, igual que `RN-SEG-004`. Se evalúa sobre el rol de mayor rango, y no sobre «el primero», porque **un ascenso cambia con quién debe cumplirse**. Su consecuencia útil: la cadena de personas hereda la aciclicidad de la de roles (`RN-SEG-006`) y no necesita una regla anti-ciclos propia. **Desde el 18-09-2026 la regla vuelve a tener una sola rama.** La rama de consumidor que ganó el 01-09-2026 —«si el subordinado es cliente, basta con que el superior porte algún rol `VENDEDOR`»— desaparece con el cliente de esta estructura (`RN-SP-028`); lo que exigía vive ahora en `RN-SP-027` y `RN-SP-049`, sobre `client_sellers`, y nunca llegó a construirse (`T-02` de `RF-SP-045`) | **Crítica** |
| `RN-SP-021` | Un superior vigente por vendedor | Al asignar el superior comercial | Un usuario tiene como mucho **un superior vigente** en `user_supervisors`. Asignar otro cierra la asignación anterior —que conserva su fila con fecha de fin— y abre una nueva. El historial no se borra: determina a quién se atribuía cada resultado en cada momento, y las comisiones lo necesitarán. **Desde el 18-09-2026 la regla habla solo de vendedores**: entre el 01-09-2026 y esa fecha decía además que «para el consumidor esa fila es su agente principal», y ya no hay tal fila — el principal de un cliente es la fila `REGISTRO` de `client_sellers` (`RN-SP-049`), que no se cierra ni se sustituye | Alta |
| `RN-SP-022` | Ningún equipo se queda sin superior | Al desactivar, bloquear o eliminar un usuario, y al retirarle el rol `VENDEDOR` | Si el usuario tiene personas a cargo **en `user_supervisors`** —sus vendedores directos—, la operación **se rechaza** hasta que se reasignen. **No se reasignan solas** al superior del superior: la estructura comercial determinará atribución de negocio, y desplazarla sin decisión explícita cambiaría en silencio a quién pertenece un resultado. Es la misma postura que `RN-SEG-008` toma con un rol que tiene hijos. **Desde el 18-09-2026 la cartera de clientes NO cuenta**, y es una decisión, no un descuido: el vínculo cliente-vendedor es un hecho de `client_sellers` que no se reasigna (`RN-SP-049`), de modo que exigir reasignarla haría imposible retirar a cualquier vendedor que haya registrado a alguien. Entre el 01-09-2026 y esa fecha la regla sí alcanzaba a la cartera —«retirar a un agente exige reasignar también su cartera»— porque el cliente colgaba de esta tabla; lo que un vendedor retirado deja tras de sí —clientes cuyo principal ya no está— lo decidirá `CM` al liquidar, no esta regla | Alta |
| `RN-SP-023` | Todo usuario tiene al menos un rol | Al registrar un usuario y al retirarle roles | Un usuario **debe tener siempre al menos un rol asignado**. El alta lo exige —`roles` deja de ser opcional— y el retiro rechaza quitar el último. El estado «usuario sin ningún rol» deja de existir. **La regla mira la asignación, no el estado del rol**: un usuario cuyos roles estén todos inactivos la cumple, y que no conceda nada lo resuelve `RN-SEG-002`. Esa acotación es deliberada — exigir un rol *activo* haría que desactivar o eliminar un rol (`RF-SP-007`, `RF-SP-009`) pudiera violar la regla **a distancia**, sobre personas que nadie estaba tocando, y dejaría operaciones del catálogo bloqueadas por el estado de terceros. **No es expresable como restricción del esquema**: «al menos una fila en `user_roles`» exige un disparador o una restricción diferida, de modo que vive en el dominio y se verifica dentro de la transacción, igual que `RN-SP-001`. Añadida el 24-08-2026; enmienda `RF-SP-024` y `RF-SP-031`, ya aprobadas (Art. I.7) | **Crítica** |
| `RN-SP-004` | Permisos inmutables por API | Siempre | Los permisos no se crean, editan ni eliminan por la API: se pueblan y modifican únicamente por migración | Alta |
| `RN-SP-005` | Revocación sin motivo | Al retirar un permiso de un rol | La fila de asociación se elimina físicamente y se audita en `audit_deletion_log` sin motivo declarado (Art. V.13, excepción de asociaciones) | Alta |
| `RN-SP-006` | Membresía acotada por nivel | Al crear una membresía | Toda membresía está sujeta a una de mayor nivel; solo la membresía superior queda libre de ella | Alta |
| `RN-SP-007` | Inserción en la cadena de membresías | Al crear una membresía | Se indica cuál es su membresía hija, si la hay, y el sistema reordena la jerarquía en consecuencia. Si no se indica ninguna, la nueva membresía queda en el extremo inferior de la cadena | Alta |
| `RN-SP-008` | Membresías inmutables | Al editar o eliminar una membresía | La operación se rechaza. Solo se admite el reordenamiento derivado de `RN-SP-007`. **No llevan indicador de activo**: desactivar un eslabón dejaría un hueco en un orden lineal | Media |
| `RN-SP-024` | La membresía declara su color | Al registrar una membresía | Toda membresía declara el **color con el que el frontend la pinta**: seis dígitos **hexadecimales sin `#`**, normalizados a mayúsculas. Es **obligatorio** y **único entre las membresías**. Obligatorio porque un color opcional obliga al navegador a inventarse uno de reserva, que es exactamente la decisión que este campo saca del frontend —y con dos pantallas eligiendo por su cuenta, el mismo nivel acaba pintado de dos maneras sin que nadie lo note hasta verlas juntas—. Único porque dos niveles del mismo color son indistinguibles justo en lo que el campo existe para distinguir; la unicidad atrapa el valor repetido y **no** dos tonos que un ojo humano no separa, y eso no lo arregla ninguna regla. **Consecuencia declarada:** `RN-SP-008` mantiene la membresía inmutable, de modo que **un color mal elegido no se puede corregir** — ver la nota que sigue a esta tabla | Media |
| `RN-SP-025` | Un solo rol vendedor por persona | Al asignar roles a un usuario | Una persona **no puede portar dos roles de tipo `VENDEDOR`** a la vez. **Asignar uno SUSTITUYE al que porte**, en la misma transacción — no se rechaza, y no hay que retirarlo antes: el ascenso seguiría siendo una operación y la persona no queda sin rol vendedor en ningún instante. **Vive en el motor** (§10.11): `user_roles` copia el `role_type` y un índice único parcial cierra la regla, de modo que **dos asignaciones simultáneas no pueden colarla**. Ver la nota que sigue a esta tabla | **Crítica** |
| `RN-SP-026` | El registro por enlace nace sin poder operar | Al registrarse un cliente desde un enlace | La cuenta se crea en estado **`FTD_PENDIENTE`**: **autentica y no opera**. Es el primer estado del sistema que separa esas dos cosas, porque hasta ahora todo lo que autenticaba estaba `ACTIVO`. La cuenta sale de ahí cuando se confirma el depósito por el valor del producto —el **FTD**—, que hoy hace un actor a mano por `RF-SP-028` y mañana hará el webhook del bróker | **Crítica** |
| `RN-SP-027` | Ningún cliente se registra sin vendedor | Al registrarse un cliente desde un enlace | El enlace declara **quién lo generó**, y sin un vendedor válido el registro **se rechaza**. No se admite la atribución vacía: produciría clientes huérfanos que nadie descubre hasta el día de pagar una comisión. El vendedor debe existir, no estar eliminado y **portar un rol `VENDEDOR`** — un funcionario no comisiona | **Crítica** |
| `RN-SP-028` | El cliente **no** cuelga de la estructura comercial: sus vendedores viven en `client_sellers` | Siempre | **Revertida el 18-09-2026** por decisión del responsable del proyecto; entre el 01-09-2026 y esa fecha decía lo contrario —«el cliente cuelga de su vendedor en la **misma** estructura, como una fila más de `user_supervisors`»— y el código construido hasta hoy lo hace así. La atribución del cliente **tiene tabla propia**: `client_sellers` (§10.19), con el cliente en `client_id` y el vendedor en `seller_id`, y `user_supervisors` vuelve a significar **una sola cosa**: quién manda sobre quién dentro de la fuerza comercial. Lo que se gana es que ninguna consulta del árbol tenga que distinguir «fila de mando» de «fila de cartera», que `RF-SP-042` deje de mezclar equipo y clientes, y que retirar a un vendedor no exija mover una cartera que nunca fue un mando (`RN-SP-022`). Lo que se paga queda declarado: **subir de un cliente a su manager ya no es un recorrido de una tabla** sino un salto de `client_sellers` a su vendedor `REGISTRO` y desde ahí el recorrido de `user_supervisors` — la liquidación multinivel de `CM` tendrá ese join en la hoja, y es un join fijo, no un caso especial. Lo que la regla sigue sin traer es con qué producto entró el cliente: ese dato pertenece al hecho comisionable —la venta— y no a la relación | **Crítica** |
| `RN-SP-029` | Una tasa cambia **de una moneda a OTRA**, y las dos existen y están **activas** al declararla | Al registrar y al corregir | Origen y destino son monedas del catálogo, y **no pueden ser la misma**: una tasa de `USD` a `USD` no expresa ningún cambio. Que **después** se desactive una moneda no invalida la tasa ya registrada, por el mismo criterio que `RN-PM-008` | **Crítica** |
| `RN-SP-030` | El precio es **mayor que cero** | Al registrar y al corregir | Una tasa de cero o negativa no es un cambio, es una destrucción de valor. Se declara con **ocho decimales** (§10.14) | Alta |
| `RN-SP-031` | La vigencia **empieza siempre y puede no terminar** | Al registrar y al corregir | `valid_from` es **obligatoria**; `valid_to` es **opcional** y nula significa **vitalicia**. Si se declara, no puede ser anterior al inicio | Alta |
| `RN-SP-032` | **Dos tasas vigentes del mismo par no se solapan** | Al registrar, al corregir y al activar | No pueden coexistir dos tasas **activas y vivas** con el mismo **origen y destino** cuyas vigencias se toquen. **El mismo origen sí puede cambiarse a varias monedas a la vez** —`USD → COP` y `USD → EUR` conviven—: lo que la regla acota es el **par**, no el origen. Se declara en el motor con un `EXCLUDE` (§5.2) | **Crítica** |
| `RN-SP-033` | La tasa no desaparece | Al retirar | La eliminación es **lógica y con motivo** (Art. V.13). La fila permanece porque una conversión hecha ayer tiene que poder decir con qué tasa se hizo | Alta |
| `RN-SP-035` | Toda persona se identifica con un documento | Al registrar un usuario y al editarlo | Toda persona declara **tipo y número de documento**, y los dos juntos: no existe el número sin el tipo ni el tipo sin el número. El tipo sale del catálogo de `RF-SP-051`, que **solo contiene documentos de mayor de edad** — de modo que registrar a un menor **no se rechaza con una comprobación: es imposible de expresar**, porque el tipo que lo acreditaría no está en el catálogo y la clave foránea no admite otra cosa. **El par tipo+número es único entre todas las personas y no se libera al eliminar**, con el mismo criterio que `RN-SP-016` aplica al nombre de usuario y al correo: dos personas compartiendo documento harían indistinguible su actividad en la auditoría. **Se corrige solo por `RF-SP-027`**, con `users:update`; el titular **no** lo toca | **Crítica** |
| `RN-SP-036` | El catálogo de tipos de documento no se administra por API | Siempre | El catálogo se **puebla por migración** y no se crea, ni se edita, ni se elimina desde ningún endpoint: `RF-SP-051` solo lo consulta. Es la misma decisión que `RN-SP-010` toma sobre las monedas, y aquí es **más fuerte todavía**, porque el contenido del catálogo **es una regla de negocio**: dar de alta por API un tipo de documento de menor de edad dejaría entrar menores sin que ninguna regla cambiara ni nadie lo notara. Lo que deja de admitirse se retira con `is_active`, también por migración | **Crítica** |
| `RN-SP-037` | Toda persona tiene teléfono personal; el de la empresa y la dirección son opcionales | Al registrar un usuario y al editarlo | El **teléfono personal es obligatorio** —es la vía de contacto con la que se opera— y **el teléfono de la empresa, la dirección, el complemento y la ciudad son opcionales**: exigir una dirección postal a un funcionario interno bloquearía su alta sin que nadie la necesite, y exigir un teléfono de empresa **bloquearía el alta de todo el que no tenga una**. Los cinco son **datos de contacto y no de identidad**, y esa es la razón de que el titular sí los corrija desde `RF-SP-044`: una mudanza o un número nuevo no deberían costar un ticket administrativo. **El de la empresa se añadió el 10-09-2026** y **no se pide en `RF-SP-045`**: quien se registra por un enlace es un cliente, y el formulario público no debe preguntar por algo que no le aplica | Alta |
| `RN-SP-038` | **Una cuenta de broker pertenece a UNA sola persona** | Al vincular una cuenta de broker | Dos personas **no pueden declarar la misma cuenta**: el par **broker + identificador** es único en todo el sistema, y el segundo intento se rechaza con `409`. **Una misma persona SÍ puede tener varias cuentas en el mismo broker** —es lo normal en el ramo—, de modo que lo que se acota no es cuántas cuentas tiene alguien sino **de quién es cada cuenta**. Se declara en el esquema y no en un caso de uso: quien la sostiene es un índice único, porque dos altas simultáneas de la misma cuenta pasan cualquier comprobación previa | **Crítica** |
| `RN-SP-039` | El catálogo de brokers no se administra por API | Siempre | El catálogo se **puebla por migración** y no se crea, ni se edita, ni se elimina desde la API: solo se consulta. Es la misma decisión que `RN-SP-010` toma con las monedas y `RN-SP-036` con los tipos de documento, y por el mismo motivo — son pocos, cambian poco, y cada alta es una decisión de negocio que merece quedar en el historial del repositorio y no en una fila que alguien insertó un martes | Alta |
| `RN-SP-040` | **El nombre de usuario en el broker llega DESPUÉS** | Al vincular una cuenta y al recibir la confirmación del broker | La cuenta se declara con **el broker y el identificador**, que es lo que la persona conoce; el **nombre de usuario en el broker lo rellena más tarde el webhook** del propio broker. Por eso la columna admite nulo, y **ese nulo significa «el broker todavía no lo ha confirmado»**, no «esta cuenta no tiene nombre». La distinción importa: una columna obligatoria obligaría a inventarse un valor en el alta, y el dato inventado sobreviviría a la confirmación | Alta |
| `RN-SP-041` | **Los tres catálogos del registro se consultan sin iniciar sesión** | Al consultar países, tipos de documento o brokers | El `GET` de los tres es **público** (08-09-2026): quien rellena el formulario de registro (`RF-SP-045`) todavía no tiene cuenta, y sin esto el desplegable no tiene de dónde sacar las opciones. **Solo el `GET`** — el alta y el cambio de estado de países siguen exigiendo su permiso. **Lo que publican no identifica a nadie**: son listas de opciones, y por eso no hace falta aquí nada del diseño que el hotlink necesita. Consecuencia declarada: `countries:read`, `document-types:read` y `brokers:read` **dejan de gobernar esas lecturas** y quedan sembrados sin endpoint que los exija | Alta |
| `RN-SP-042` | **Quien se registra por un enlace `BECA → BECA` declara su cuenta de broker** | Al registrarse por enlace (`RF-SP-045`) | El formulario exige **al menos una cuenta de broker** —broker e identificador— cuando el producto del enlace es una membresía **de gratuita a gratuita**, y **solo entonces**. **Se declaran UNA O MÁS**: una persona puede operar con varios brokers, y este formulario es hoy la única vía para declararlos. El motivo es el estado en el que nace la cuenta: `FTD_PENDIENTE` autentica y **no opera**, y quien la saca de ahí es el **depósito confirmado por el webhook del broker** (`RF-SP-054`) — sin la cuenta declarada, el sistema no puede saber de quién es un depósito cuando llegue, y la persona se queda encerrada. La cuenta se declara con lo que la persona conoce; el **nombre de usuario en el broker lo rellena después el webhook** (`RN-SP-040`). Se aplican `RN-SP-038` y su `409`: **una cuenta es de una sola persona** | **Crítica** |
| `RN-SP-043` | **Todo registro por enlace anota su venta** | Al registrarse por enlace (`RF-SP-045`) | El formulario declara **el movimiento** que el alta produce —producto, método de pago, vendedor y tipo— y el registro **lo anota siempre**, también en el enlace gratuito (09-09-2026, por decisión del responsable del proyecto): todo alta deja rastro de qué se vendió. La venta **no se reimplementa**: la registra `RF-MV-001` con sus reglas enteras, de modo que hay **una sola definición de vender**. **Y el bloque del movimiento ES el enlace**: desde el 09-09-2026 el producto (`productId`) y quien lo compartió (`sellerUsername`) viajan **solo ahí**. Llevaban además un duplicado en el primer nivel —`product` y `referrer`— y se retiró: dos campos para un dato no son una redundancia inofensiva, son **dos valores que pueden discrepar**, y hacían falta dos comprobaciones cuya única razón de ser era vigilar esa discrepancia. Al quitar el duplicado **la divergencia dejó de poder expresarse**, y con ella murieron las dos comprobaciones. Dos consecuencias que sí quedan: **(1)** el vendedor del movimiento es el que el registro cuelga como superior, de donde `RN-MV-003` lo vuelve a sacar — una sola declaración gobierna las dos cosas; **(2)** el método de pago es **condicional al importe** (`RN-MV-022`) y no al producto: se omite si la venta vale cero —ahí lo pone `MV`, y el catálogo público ni siquiera lo devuelve— y es obligatorio si tiene importe. **Todo va en la misma transacción**: si la venta se rechaza, no queda ni la persona | **Crítica** |
| `RN-SP-044` | **El camino de pago se admite, y lo que cambia es el estado y el nivel** | Al registrarse por enlace (`RF-SP-045`) | Un enlace hacia una membresía **de pago** ya **no se rechaza** (09-09-2026): lo que el producto decide es **cómo nace la cuenta**, no si se admite. **Enlace `BECA → BECA`**: la cuenta nace `FTD_PENDIENTE` —autentica y no opera— y recibe **la membresía del producto**, porque nadie pagó nada y no hay nada que confirmar; quien la saca de ahí es el depósito del broker (`RN-SP-042`). **Enlace de pago**: la cuenta nace **`ACTIVO`** —quien paga no tiene ningún depósito que esperar— y recibe **la membresía del suelo**, no la comprada. La comprada la concede **confirmar la venta** (`RN-MV-020`), y concederla en el alta duplicaría lo que hace la confirmación: dos concesiones para una sola compra, y la segunda **sin pago verificado**. Es la contraparte exacta de `RN-MV-004` —registrar una venta no concede nada— y de ahí sale que estar activo y tener el nivel comprado sean **dos cosas distintas** | **Crítica** |
| `RN-SP-034` | Todo usuario pertenece a un país | Al registrar un usuario y al editarlo | Toda persona declara **exactamente un país** del catálogo, y el estado «usuario sin país» **no existe**: el alta lo exige —tanto la administrativa (`RF-SP-024`) como el registro por enlace (`RF-SP-045`)— y la columna es `NOT NULL`. **Solo se puede asignar un país activo**, pero **desactivarlo después no invalida a quien ya lo tenía**: `RF-SP-022` retira un país de los selectores y deja resolviendo a los datos que ya lo referencian, que es exactamente lo que aquí ocurre. **Se corrige solo por `RF-SP-027`**, con `users:update`; el titular **no** lo cambia desde `RF-SP-044` | **Crítica** |
| `RN-SP-009` | Países inmutables salvo su estado | Al editar o eliminar un país | La operación se rechaza. Lo único modificable es el indicador de país activo (`RF-SP-022`), que permite retirar de la circulación un alta equivocada sin borrar el registro | Media |
| `RN-SP-010` | Monedas inmutables por API salvo su estado | Siempre | Las monedas no se crean, editan ni eliminan por la API. Lo único modificable es el indicador de moneda activa (`RF-SP-023`), y la moneda por defecto no puede desactivarse | Media |
| `RN-SP-045` | **Toda cuenta de broker declara en qué punto está** | Al declarar una cuenta y al confirmarse el primer depósito | Una cuenta vive en uno de **dos estados** (10-09-2026, por decisión del responsable del proyecto): **`REGISTER`** —la cuenta está declarada y el broker no ha confirmado ningún depósito— y **`FIRST_DEPOSIT`** —el primer depósito está confirmado—. **Nace siempre en `REGISTER`**, y hoy **nadie la mueve de ahí**: quien la mueve es el webhook del broker (`RF-SP-054`), que no está construido. Eso se declara por adelantado en lugar de disimularlo, porque es el defecto que `RF-SP-035` dejó escrito con la purga —un campo que nadie escribe parece funcionalidad que sí está—; la diferencia es que aquí la columna **sí se lee** desde el primer día (`RF-SP-055` y `RF-SP-056`), y quien la consulte verá `REGISTER` en todas las cuentas **porque es la verdad**: ninguna tiene depósito confirmado mientras el webhook no exista. **Los dos valores van en inglés**, contra la costumbre del resto de enumerados del sistema —`ACTIVO`, `FTD_PENDIENTE`, `CONFIRMADO`—, y es deliberado: son **el vocabulario del broker** y así se llaman en la integración que los va a escribir. **No es el estado de la persona**: `users.status` dice si la cuenta del sistema opera (`RN-SP-044`) y este dice qué ha pasado en el broker; una persona con varias cuentas puede tener unas en un estado y otras en otro, de modo que uno **no** se deriva del otro | Alta |
| `RN-SP-048` | **Cómo suman los indicadores de la red comercial** | Al consultar los indicadores (`RF-SP-058`) | La regla es **una sola, aplicada en todos los niveles**: el indicador de una persona son **los suyos directos más la suma de los de quienes cuelgan de ella**. Un agente recibe los de sus clientes directos; un director, los suyos directos más los de sus agentes; un manager, los suyos más los de sus directores. Cuatro precisiones sin las cuales el número no significa nada: **(1) La unidad es LA CUENTA y no la persona** (10-09-2026): un cliente con dos cuentas depositadas suma **dos**, porque lo que se mide es el dinero que entró y ese es el número que cuadra con el broker. **(2) Solo cuentan las cuentas de CONSUMIDORES**, por decisión del responsable del proyecto — «solo los roles de tipo consumidores tienen ftds»—: la cuenta personal de un vendedor no es una captación, y contarla premiaría a quien deposita en la suya. Se resuelve por `user_roles.role_type` y no por la posición en el árbol. **(3) El total de una persona YA CONTIENE el de sus subordinados**, de modo que **sumar la columna de totales de una lista cuenta dos veces**: por eso la respuesta publica **los dos números por separado** —propios y red— y nunca uno solo. **(4) Los NODOS del árbol son la fuerza comercial y los consumidores no aparecen**: son las hojas que aportan el número, no filas del árbol — publicarlos convertiría el indicador en el listado de clientes de la empresa. Consecuencia declarada: lo que cuelga de quien **no** es fuerza comercial no entra en ningún nodo, y por eso la respuesta lleva además **lo no atribuido**, para que el árbol cuadre con `RF-SP-057`. **(5) El cliente llega a su agente por `client_sellers`, no por `user_supervisors`** (18-09-2026, `RN-SP-028` revertida): las cuentas de un consumidor suman en su vendedor **principal** —la fila `REGISTRO`— y desde él suben por `user_supervisors`. Un vínculo por `HOTLINK` **no suma**: lo que el indicador mide es la captación, y captó quien registró | **Crítica** |
| `RN-SP-049` | **Un cliente tiene UN vendedor principal —el que lo registró, para siempre— y tantos vendedores VINCULADOS como le hayan vendido por hotlink** | Al registrarse por enlace (`RF-SP-045`) y al comprar por el hotlink de un vendedor (`RF-MV-011`, `RF-MV-013`) | Decisión del responsable del proyecto, 16-09-2026, **enmendada el 18-09-2026**: la relación cliente-vendedor vive **entera** en `client_sellers` (§10.19) y el cliente **no tiene fila en `user_supervisors`** (`RN-SP-028`). Una fila por pareja cliente-vendedor, con el origen y la fecha en que nació, **sin fin**: es un hecho —ese vendedor lo registró, o le vendió— y los hechos no se cierran. **El principal es la fila con origen `REGISTRO`**, la del vendedor cuyo enlace usó para registrarse, y **no se cambia**: no hay operación que lo reasigne ni historial que cerrar, porque quién trajo a una persona no deja de ser cierto. Cada cliente tiene **exactamente una** fila `REGISTRO` (`RN-SP-027`) y cero o más `HOTLINK`. **Comprar por otro hotlink NO cambia el principal**: lo que el vínculo decide es **la atribución de la venta** —la compra por hotlink se atribuye al dueño del enlace, sea o no el principal, y la compra desde la tienda al principal (`RN-MV-003`)—. La estructura comercial —equipo (`RF-SP-042`), protección al retirar (`RN-SP-022`), superior de un vendedor (`RN-SP-021`)— mira solo a `user_supervisors` y por tanto **no ve clientes**; lo que sí llega al principal por esta tabla son las cuentas de broker de su cartera (`RN-SP-046`) y los indicadores (`RN-SP-048`). Un vendedor solo vinculado ve a ese cliente **únicamente en sus propios movimientos** (`RF-MV-008`). El cliente ve a todos sus vendedores en `RF-SP-059`; el vendedor, a toda su cartera en `RF-SP-061` | Alta |
| `RN-SP-047` | **La red de un vendedor es TODO lo que cuelga de él** | Al consultar el listado de administración de cuentas de broker (`RF-SP-057`) | Filtrar por un vendedor devuelve las cuentas de **toda su red en profundidad** —sus subordinados, los subordinados de estos, y así hasta abajo—, y **no** solo las de su equipo directo. Es **la primera consulta recursiva del sistema** sobre `user_supervisors`, y **rompe a propósito la cota de un solo nivel** que `RF-SP-042`, `RF-SP-055` y `RF-SP-056` se imponen: aquellas la tienen porque las autoriza la **estructura** —y devolver la rama entera publicaría la empresa a quien solo lleva un equipo—; esta la autoriza **`broker-accounts:read`**, que ya ve las cuentas de cualquiera, de modo que **la profundidad no le concede nada que no tuviera**: le ahorra recorrer el árbol a mano. **La raíz NO se incluye**: preguntar por la red de una persona devuelve la de los suyos, no las cuentas de ella —que se piden por `userId`, y los dos filtros se pueden combinar—. **Solo la estructura vigente** (`ended_at` nulo): quien dejó la red ayer no cuenta hoy, aunque su resultado de ayer se le siga atribuyendo. **El ciclo no puede colgar la consulta** aunque los datos lo tuvieran: la recursión acumula con `UNION` y no con `UNION ALL`, de modo que una persona ya vista no vuelve a expandirse | Alta |
| `RN-SP-046` | **Las cuentas de broker las ve el superior vigente, o quien traiga el permiso** | Al consultar cuentas de broker (`RF-SP-055`, `RF-SP-056`) | Las cuentas de una persona las consulta **su superior comercial vigente** —para un vendedor, la fila de `user_supervisors` con `ended_at` nulo; para un cliente, su vendedor **principal**, que desde el 18-09-2026 es la fila `REGISTRO` de `client_sellers` (`RN-SP-049`); un vendedor solo **vinculado** por hotlink no las ve— y, sobre cualquiera, quien traiga **`broker-accounts:read`**. **La estructura comercial es la llave**, y es la primera lectura del sistema que la usa como tal: hasta hoy registrar la estructura **no concedía alcance de datos** —lo dice `V21` y lo deja abierto la **D-22**—, y esta regla **no la resuelve**: la acota a esta lectura y a ninguna otra. **Un solo nivel, como `RF-SP-042`**: el equipo directo, nunca el árbol descendente. **Quien no es el superior vigente ni trae el permiso recibe `404`**, el mismo que si la persona no existiera: quien puede probar identificadores ajenos no debe poder distinguir «no existe» de «no es tuyo», que es el mismo criterio con que el hotlink de `RF-PM-008` unifica su `404`. **Y el titular NO se ve a sí mismo** por esta vía (10-09-2026): la lectura se definió sobre el equipo, no sobre el perfil propio; el día que el cliente deba ver sus cuentas, la vía es `RF-SP-039` y su `GET /users/me` — no relajar esta | Alta |
| `RN-SP-050` | **El nombre de un equipo es único entre los no eliminados, sin distinguir mayúsculas ni acentos** | Al registrar un equipo y al editarlo | No hay código: el nombre es **la única columna de negocio que identifica** a un equipo, como ocurre con los brokers (`uq_brokers_name`), y por eso la unicidad es **funcional** —sobre `f_unaccent(lower(name))`— y no literal: «Equipo Norte» y «equipo norte» serían dos opciones indistinguibles en cualquier selector. Es la asimetría deliberada con `uq_roles_name`, que es literal porque el rol tiene además un código. Y es **parcial**, `WHERE deleted_at IS NULL`, como la de los roles: eliminar un equipo libera su nombre, porque la eliminación es lógica y el nombre no identifica a una persona (`RN-SP-016` razona lo contrario para `users`, y el motivo —la auditoría de dos personas bajo la misma etiqueta— no alcanza a un equipo) | Alta |
| `RN-SP-051` | **A un equipo solo pertenecen managers** | Al asignar un manager a un equipo | Quien se asigna debe portar **el rol vendedor de mayor rango** —aquel de clasificación `VENDEDOR` cuyo rol padre no es `VENDEDOR`—, que es exactamente la persona que `RN-SP-019` exime de superior: la cúspide de la fuerza comercial, hoy `MANAGER`. La regla se define por la **forma de la jerarquía de roles** y no por el código, con el mismo criterio que `RN-SP-019` y `RN-SP-020`: si algún día se crea un rango por encima, la regla lo sigue sin enmienda. Directores y agentes **no se asignan**: pertenecen al equipo de su manager por la cadena de `user_supervisors`, y asignarlos aparte permitiría que un director figurara en un equipo distinto del de quien lo manda. Se comprueba en el caso de uso, como `RN-SP-020`: depende de `user_roles` y `roles`, y un `CHECK` no consulta otra tabla | **Crítica** |
| `RN-SP-052` | **Un manager tiene como mucho UN equipo vigente, y el historial se conserva** | Al asignar un manager a un equipo y al retirarlo | Asignar a un manager que ya pertenece a otro equipo **cierra la pertenencia anterior** —que conserva su fila con fecha de fin— y abre la nueva, en la misma transacción; retirarlo la cierra sin abrir otra. Nada se borra: a qué equipo pertenecía cada manager **en cada momento** decide a quién se atribuye lo que su red produjo, y las comisiones lo necesitarán, con la misma razón que `RN-SP-021` da para el superior. **Un manager sin equipo es un estado legítimo**, al contrario que el vendedor sin superior de `RN-SP-019`: el equipo agrupa, no manda, y nada del sistema queda indefinido mientras falta. Por eso conceder el rol de manager (`RF-SP-030`) no exige indicar equipo, y la pertenencia se declara aparte (`RF-SP-069`) | Alta |
| `RN-SP-053` | **Un equipo `INACTIVO` no recibe managers; los que tiene los conserva** | Al asignar un manager a un equipo y al cambiar el estado del equipo | Asignar a un equipo `INACTIVO` se rechaza. Desactivarlo **no cierra** las pertenencias vigentes: suspender un equipo es dejar de organizar con él hacia adelante, no deshacer cómo estaba organizada la cúspide hasta hoy, y cerrarlas en silencio movería la atribución de toda una red sin que nadie lo decidiera —la misma postura que `RN-SP-022` toma con el superior de un vendedor retirado—. Quien quiera vaciar un equipo lo hace manager a manager (`RF-SP-070`) o reubicándolos (`RF-SP-069`), con motivo | Media |
| `RN-SP-054` | **No se elimina un equipo con managers vigentes** | Al eliminar un equipo | Si el equipo tiene al menos una pertenencia con `ended_at` nulo, la eliminación **se rechaza** hasta que sus managers se retiren o se reubiquen. **No se reubican solos** ni se cierran solos, por el motivo de `RN-SP-053`. Es la misma postura que `RN-SEG-008` toma con un rol que tiene usuarios y `RN-SP-022` con un superior que tiene equipo. La eliminación es **lógica** (`deleted_at`) y exige motivo (Art. V.13); las pertenencias cerradas **sobreviven** a la eliminación del equipo, porque son historial y el historial no se borra (`RN-SP-052`) | Alta |
| `RN-SP-055` | **La pertenencia sigue al rol: quien deja de ser manager sale de su equipo** | Al retirar a una persona el rol vendedor de mayor rango (`RF-SP-031`) y al eliminarla (`RF-SP-029`) | Si la persona tiene una pertenencia vigente, la operación **la cierra en la misma transacción**, auditando ambos hechos bajo el mismo identificador de correlación — la forma que `RN-SP-019` ya da al superior cuando se retira el último rol `VENDEDOR`. Sin esta regla, un equipo podría contener a alguien que ya no es manager, y `RN-SP-051` se cumpliría al asignar y dejaría de cumplirse después sin que nadie lo notara. **Un descenso es un retiro**: quien pasa de manager a director pierde el rol de mayor rango y por tanto su equipo; su nuevo superior lo coloca, por `user_supervisors`, en el equipo de ese superior. Cambiar el estado de la persona (`RF-SP-028`) **no** la saca del equipo: un manager suspendido sigue siendo manager. Es una enmienda de hecho (Art. I.7) a `RF-SP-029` y `RF-SP-031`, construidos, que se aplica en sus tripletas antes de tocar su código, con la tarea declarada en `RF-SP-070` | Alta |

!!! danger "`RN-SP-025` vive en el motor, y hasta el 02-09-2026 no vivía en ninguna parte"

    La regla nació el 28-08-2026 porque la pidió `CM`: sin ella, «el rol vendedor de una persona» no es una pregunta con una sola respuesta, y la resolución de comisiones elegiría en silencio. **Se declaró y no se construyó**, de modo que durante cinco días una persona pudo portar dos.

    Lo que la sostenía mientras tanto era **una excepción, no una regla**: `SellerRoleCatalog` revienta con `AmbiguousSellerRoleException` en lugar de elegir. Deliberadamente **no se traduce a un `4xx`** — no es culpa de quien pregunta, es que el sistema está en un estado que no debería existir.

    **Se decidió declararla en el esquema y no en el caso de uso** (02-09-2026), revirtiendo lo que `modules.md` §5.3 afirmaba. `user_roles` copia el `role_type`, una clave foránea compuesta impide que la copia diverja, y un **índice único parcial** sobre `(user_id) WHERE role_type = 'VENDEDOR'` cierra la regla. El detalle, en §10.11.

    **El motivo que decidió es un precedente, no una preferencia:** `RN-SP-018` se comprobaba en el caso de uso, **no se sostuvo bajo concurrencia** y hubo que corregirla el 26-08-2026. Es la misma tabla y la misma clase de comprobación.

    **Y la columna abría más de lo que se le pidió.** `RN-SP-013` y `RN-SP-018` —solo los consumidores tienen membresía— estaban declaradas como no expresables en el esquema por depender de `user_roles` y `roles.role_type`; con el `role_type` ya copiado, **pasan a serlo**. No se hace hoy: es otro requerimiento y otra tripleta. **Y ya no se hará nunca (05-09-2026)**: `RN-SP-013` está retirada y `RN-SP-018` dice ahora que **toda** persona tiene membresía, de modo que la restricción que se podía declarar —«solo los consumidores»— describe una regla que dejó de existir. Lo que queda por declarar es la contraria, y esa **no** es expresable: «toda fila de `users` tiene una fila abierta en `user_memberships`» es una comprobación entre tablas que ningún `CHECK` alcanza.

!!! danger "`RN-SP-025` contradecía el ASCENSO, que estaba construido y probado desde el 24-08-2026"

    `RF-SP-030` permite ascender asignando el rol nuevo: quien portaba `AGENTE` recibe `DIRECTOR` y **queda portando los dos**. Está en su §13 —«un `DIRECTOR` que recibe además `AGENTE` no cambia su rol de mayor rango»— y toda la mecánica de `CommercialStructure` —comparar el rango **antes y después**— existe porque se daba por supuesto que conviven.

    **Las dos cosas estaban aprobadas y nadie las cruzó.** `RF-SP-030` se construyó el 24-08-2026; `RN-SP-025` nació el 28-08-2026 pidiéndola `CM`. Se descubrió el 02-09-2026 al ir a construirla, y **la regla habría hecho fallar `CA-SP-399`**.

    **Se resolvió haciendo que asignar SUSTITUYA** (responsable del proyecto, 02-09-2026), y no que rechace:

    | Alternativa | Por qué se descartó |
    |---|---|
    | Rechazar el segundo, y retirar el primero con `RF-SP-031` | Son dos llamadas, y **entre ellas la persona no es vendedora**. Peor: si el rol vendedor era el único que portaba, `RN-SP-023` **rechaza el retiro** y el ascenso queda imposible sin darle antes un rol de relleno |
    | Retirar `RN-SP-025` y que `CM` resuelva «el de mayor rango» | Devuelve al sistema a **elegir en silencio** cuál es el rol vendedor de alguien. Hoy `SellerRoleCatalog` revienta a propósito para no hacerlo |

    **Lo que cuesta queda escrito: «asignar» pasa a poder retirar**, y el nombre de la operación ya no lo dice entero. La auditoría tiene que registrar las dos cosas — el rol que entra y el que sale—, porque si solo registra el que entra, **el rol retirado desaparece sin que ningún evento lo explique**.

!!! warning "El color se elige una vez y no se corrige — hueco aceptado el 26-08-2026"

    `RN-SP-024` añade a la membresía el primer campo **puramente estético** del módulo, y `RN-SP-008` la mantiene inmutable. La combinación tiene una consecuencia que conviene ver escrita antes de que la descubra alguien mirando la interfaz: **un color mal elegido —o simplemente feo— es permanente**, y la única salida es crear otra membresía, que reordena la cadena entera.

    Se acepta a conciencia y **no** se abre hoy un requerimiento de corrección, por la misma razón que lo demás de la membresía es inmutable. Lo que sí queda fijada es **la condición para reabrirlo**: en cuanto haya que cambiar un color en un entorno con datos —un cambio de identidad visual, un color que no cumpla contraste, o una carga inicial hecha con valores de relleno—, se registra `RF-SP-043` «cambiar el color de una membresía» y con él la **primera excepción** a `RN-SP-008`, acotada a este campo por ser el único que no participa en ninguna regla del backend.

!!! info "Sobre la inmutabilidad de los catálogos"

    Países y monedas **sí** llevan indicador de activo; las membresías **no**. La diferencia no es de criterio sino de estructura: un catálogo plano admite que un elemento deje de ofrecerse sin que los demás se enteren, mientras que la cadena de membresías es un orden lineal en el que retirar un eslabón obliga a decidir qué pasa con el hueco y con quien lo tenía asignado.

    Desactivar **no es corregir**: el código y el nombre erróneos permanecen, y los datos que ya los referencian siguen resolviéndolos. Es lo que evita que el error se propague a partir de ese momento, no lo que lo repara.

!!! important "`RN-SP-034` — por qué el país es una columna de `users` y no una tabla puente"

    Las otras dos cosas que una persona «tiene» en este módulo viven en tablas propias, y conviene decir por qué esta no. `user_memberships` es un **historial** porque una membresía se concede por un periodo, vence y se sustituye (`RN-SP-014`); `user_supervisors` lo es porque el mando cambia y hay que poder responder quién estaba a cargo **entonces** (`RN-SP-021`).

    **El país no tiene vigencia.** No se concede hasta una fecha, no vence y nadie pregunta en qué país estaba alguien el mes pasado: lo que el sistema necesita saber es dónde está **hoy**, que es lo que decide qué medios de pago se le ofrecen (`RN-MV-019`) y en qué moneda se le habla. Una tabla puente para un dato sin periodo añadiría un `join` a cada consulta de usuario a cambio de nada, y el rastro del cambio —quién lo movió y cuándo— ya lo guarda `audit_change_log` sin necesidad de una tabla más.

!!! warning "«Solo países activos» **no** es declarable en el esquema, y la clave foránea compuesta que lo parecería está prohibida aquí"

    La mitad de `RN-SP-034` que exige que el país asignado esté **activo** se comprueba en el caso de uso, no en el motor, y no por comodidad.

    El patrón que `RN-SP-025` estrenó el 02-09-2026 —copiar el dato del que depende la regla y atarlo con una **clave foránea compuesta** (§10.8)— parece aplicable: bastaría copiar `is_active` a `users` y declarar `(country_id, is_active) → countries(id, is_active)`. **No vale, y falla en la condición que aquel mismo caso dejó escrita: el dato copiado tiene que ser inmutable en su origen.** `role_type` no se corrige nunca; `countries.is_active` es **lo único que `RN-SP-009` deja cambiar**, y es `RF-SP-022` quien lo cambia.

    Lo que ocurriría es concreto: la clave foránea compuesta **haría fallar `RF-SP-022`** en cuanto un solo usuario tuviera ese país. Desactivar dejaría de ser «retirarlo de los selectores» —lo que `RF-SP-022` promete— y pasaría a ser una operación bloqueada por terceros, sobre gente a la que nadie estaba tocando. Es el mismo daño a distancia que `RN-SP-023` evita al mirar la asignación y no el estado del rol.

    De modo que la comprobación es **de entrada, no permanente**: se exige país activo al asignarlo, y quien ya lo tenía lo conserva aunque se desactive después.

!!! info "Sobre `RN-SP-005`"

    Retirar un permiso de un rol **elimina físicamente** la fila de `role_permissions`, y por tanto se audita en `audit_deletion_log`. No se exige motivo: una asociación rol-permiso no es una entidad de negocio y su «por qué» ya está en el propio evento —qué permiso, de qué rol, quién y cuándo—. Un motivo de texto libre aquí se rellenaría con ruido.

    Esto exigió enmendar el Art. V.13, que prohibía las eliminaciones sin motivo. La excepción quedó acotada a las asociaciones.

### 5.2 `RN-SP-032` — por qué el no solapamiento vive en el motor

**La regla en una frase:** en cualquier instante, un par origen→destino tiene **como mucho una** tasa activa.

**Un `UNIQUE` no puede expresarlo**, y conviene entender por qué antes de intentarlo: la unicidad compara **valores iguales**, y aquí lo que no puede repetirse es un **solapamiento de rangos**. `USD → COP` del 1 de enero al 30 de junio y `USD → COP` del 1 de junio al 31 de diciembre tienen fechas **distintas** —pasarían cualquier `UNIQUE`— y en junio hay **dos tasas para el mismo cambio**.

Se declara con la misma forma que `user_commission_rates` estrenó en `V44` y `V49` mantuvo:

```sql
EXCLUDE USING gist (
    source_currency_id WITH =,
    target_currency_id WITH =,
    daterange(valid_from, valid_to, '[]') WITH &&
) WHERE (is_active AND deleted_at IS NULL)
```

**Las tres piezas hacen falta y ninguna sobra:**

| Pieza | Qué pasa si falta |
|---|---|
| Las dos monedas `WITH =` | La restricción compararía solo rangos y prohibiría que `USD → COP` y `USD → EUR` convivan, que es justo lo que esta regla **sí** admite |
| `daterange(..., '[]')` | Con `'[)'` —el intervalo por omisión— una tasa que termina el 30 de junio y otra que empieza el 30 de junio **no se solaparían**, y ese día habría dos |
| `WHERE (is_active AND deleted_at IS NULL)` | Una tasa retirada o desactivada seguiría **bloqueando sus días para siempre**, y nada más fallaría — el periodo quedaría inutilizable sin que nadie supiera por qué |

!!! danger "La consecuencia del `WHERE`: activar puede violar la regla"

    Si las inactivas no bloquean, **activar una tasa que se solapa con la vigente es la operación peligrosa**, no el alta. Es exactamente el reparto que `RN-PM-004` tiene en `PM`, donde la comprobación vive en `RF-PM-005` y no en el alta.

    Aquí no hay un requerimiento de «cambiar el estado»: el estado se declara al registrar (`RF-SP-047`) y se corrige en `RF-SP-049`, de modo que **los dos** tienen que traducir la violación del `EXCLUDE` a un `409` legible. Si alguno la dejara subir, el actor recibiría un `500` sobre una regla de negocio.

!!! important "Requiere `btree_gist`, y esa extensión ya está instalada"

    Un `EXCLUDE` que mezcla igualdad sobre `uuid` con solapamiento sobre `daterange` necesita `btree_gist`. La instaló `V44` para la primera tabla de tasas de comisión; esta la reutiliza y **no la vuelve a declarar**.

## 6. Requerimientos funcionales

### 6.1 Resumen

| ID | Requerimiento | Prioridad | Permiso | Estado |
|---|---|---|---|---|
| `RF-SP-001` | Registrar rol | Crítica | `roles:create` | En desarrollo |
| `RF-SP-002` | Consultar roles | Crítica | `roles:list` | En desarrollo |
| `RF-SP-003` | Consultar detalle de un rol | Alta | `roles:read` | En desarrollo |
| `RF-SP-004` | Editar rol | Alta | `roles:update` | En desarrollo |
| `RF-SP-005` | Asignar permisos a un rol | Crítica | `roles:assign-permissions` | En desarrollo |
| `RF-SP-006` | Revocar permisos de un rol | Alta | `roles:revoke-permissions` | En desarrollo |
| `RF-SP-007` | Cambiar el estado de un rol | Alta | `roles:change-status` | En desarrollo |
| `RF-SP-008` | Cambiar el rol padre de un rol | Media | `roles:assign-parent` | En desarrollo |
| `RF-SP-009` | Eliminar rol | Media | `roles:delete` | En desarrollo |
| `RF-SP-010` | Consultar catálogo de permisos | Crítica | `permissions:list` | En desarrollo |
| `RF-SP-011` | Consultar auditoría de cambios | Media | `audit:read-changes` | En desarrollo |
| `RF-SP-012` | Consultar auditoría de eliminación | Media | `audit:read-deletions` | En desarrollo |
| `RF-SP-013` | Consultar auditoría de error | Media | `audit:read-errors` | En desarrollo |
| `RF-SP-014` | Consultar auditoría de seguridad | Alta | `audit:read-security` | En desarrollo |
| `RF-SP-015` | Consultar detalle de un permiso | Media | `permissions:read` | En desarrollo |
| `RF-SP-016` | Registrar membresía | Alta | `memberships:create` | En desarrollo |
| `RF-SP-017` | Consultar membresías | Alta | `memberships:list` | En desarrollo |
| `RF-SP-018` | Consultar detalle de una membresía | Media | `memberships:read` | En desarrollo |
| `RF-SP-019` | Consultar monedas | Media | `currencies:read` | En desarrollo |
| `RF-SP-020` | Registrar país | Media | `countries:create` | En desarrollo |
| `RF-SP-021` | Consultar países | Media | **Público** | En desarrollo |
| `RF-SP-022` | Cambiar el estado de un país | Media | `countries:update` | En desarrollo |
| `RF-SP-023` | Cambiar el estado de una moneda | Baja | `currencies:update` | En desarrollo |
| `RF-SP-024` | Registrar usuario | **Crítica** | `users:create` | En desarrollo |
| `RF-SP-025` | Consultar usuarios | **Crítica** | `users:list` | En desarrollo |
| `RF-SP-026` | Consultar detalle de un usuario | Alta | `users:read` | En desarrollo |
| `RF-SP-027` | Editar usuario | Alta | `users:update` | En desarrollo |
| `RF-SP-028` | Cambiar el estado de un usuario | Alta | `users:change-status` | En desarrollo |
| `RF-SP-029` | Eliminar usuario | Media | `users:delete` | En desarrollo |
| `RF-SP-030` | Asignar roles a un usuario | **Crítica** | `users:assign-roles` | En desarrollo |
| `RF-SP-031` | Retirar roles de un usuario | Alta | `users:revoke-roles` | En desarrollo |
| `RF-SP-032` | Asignar membresía a un usuario | Alta | `users:assign-membership` | En desarrollo |
| `RF-SP-033` | Devolver la membresía de un usuario al suelo | Media | `users:revoke-membership` | En desarrollo |
| `RF-SP-034` | Iniciar sesión | **Crítica** | — (público) | En desarrollo |
| `RF-SP-035` | Refrescar el token de acceso | **Crítica** | — (público) | En desarrollo |
| `RF-SP-036` | Cerrar sesión | Alta | — (público) | En desarrollo |
| `RF-SP-037` | Cambiar la propia contraseña | Alta | `users:change-own-password` | En desarrollo |
| `RF-SP-038` | Restablecer la contraseña de un usuario | Media | `users:reset-password` | En desarrollo |
| `RF-SP-039` | Consultar el propio perfil | Alta | `users:read-own-profile` | En desarrollo |
| `RF-SP-040` | Restablecer la propia contraseña olvidada | Alta | — (público) | En desarrollo |
| `RF-SP-041` | Asignar o cambiar el superior comercial de un usuario | **Crítica** | `users:assign-supervisor` | En desarrollo |
| `RF-SP-042` | Consultar el equipo a cargo de un usuario | Media | `users:read-team` | En desarrollo |
| `RF-SP-044` | Editar el propio perfil | Alta | `users:update-own-profile` | En desarrollo |
| `RF-SP-045` | Registro de clientes por enlace | **Crítica** | **Público** | **En desarrollo** |
| `RF-SP-047` | Registrar una tasa de cambio | Alta | `exchange-rates:create` | En desarrollo |
| `RF-SP-048` | Consultar las tasas de cambio | Alta | `exchange-rates:read` | Tasks en revisión |
| `RF-SP-049` | Corregir una tasa de cambio | Media | `exchange-rates:update` | Tasks en revisión |
| `RF-SP-050` | Retirar una tasa de cambio | Media | `exchange-rates:delete` | Tasks en revisión |
| `RF-SP-051` | Consultar tipos de documento | Alta | **Público** | Pendiente |
| `RF-SP-052` | Consultar el catálogo de brokers | Alta | **Público** | **Tasks en revisión** |
| `RF-SP-053` | Vincular una cuenta de broker a una persona | Alta | Por decidir | **Pendiente** |
| `RF-SP-054` | Completar la cuenta de broker desde el webhook del broker | Media | **Ninguno: lo llama el broker** | **Pendiente** |
| `RF-SP-055` | Consultar las cuentas de broker de una persona | Alta | `broker-accounts:read-team-member`, y **superior vigente** o `broker-accounts:read` para el alcance | **En desarrollo** |
| `RF-SP-056` | Consultar las cuentas de broker del equipo | Alta | `broker-accounts:read-own-team` (el equipo propio) | **En desarrollo** |
| `RF-SP-057` | Consultar y filtrar todas las cuentas de broker | Alta | `broker-accounts:read` | **En desarrollo** |
| `RF-SP-058` | Consultar los indicadores de la red comercial | **Crítica** | `broker-accounts:read-indicators` | **En desarrollo** |
| `RF-SP-059` | Consultar los vendedores de un cliente | Media | `users:read-own-sellers` (los propios), o `users:read-sellers` | **En desarrollo** |
| `RF-SP-060` | Un permiso por operación | **Crítica** | — (es el catálogo) | Tasks en revisión |
| `RF-SP-061` | Consultar los clientes de un vendedor | Media | `users:read-own-clients` (la propia), o `users:read-clients` | **En desarrollo** |
| `RF-SP-062` | Autenticarse no autoriza nada | **Crítica** | — (es el catálogo y la regla) | **En desarrollo** |
| `RF-SP-063` | Registrar equipo | Alta | `teams:create` | **En desarrollo** |
| `RF-SP-064` | Consultar equipos | Alta | `teams:list` | **En desarrollo** |
| `RF-SP-065` | Consultar detalle de un equipo | Media | `teams:read` | **En desarrollo** |
| `RF-SP-066` | Editar equipo | Media | `teams:update` | **En desarrollo** |
| `RF-SP-067` | Cambiar el estado de un equipo | Media | `teams:change-status` | **Tasks en revisión** |
| `RF-SP-068` | Eliminar equipo | Baja | `teams:delete` | **Tasks en revisión** |
| `RF-SP-069` | Asignar miembros a un equipo | Alta | `teams:assign-members` | **Tasks en revisión** |
| `RF-SP-070` | Retirar miembros de un equipo | Media | `teams:remove-members` | **Tasks en revisión** |

!!! info "Dónde vive el estado de un requerimiento"

    Esta columna es un **resumen**. La autoridad es la matriz de trazabilidad de [`requirements.md` §4](../requirements.md#4-matriz-de-trazabilidad), que registra además la tripleta, el Issue, el Pull Request y las pruebas de cada fila; aquí se copia el estado para que el catálogo del módulo se lea de un vistazo, y se actualiza **con el mismo cambio** (Art. III.6).

    Al 26-08-2026 los cuarenta y dos están **`En desarrollo`**: los cuarenta y dos tienen tripleta aprobada y endpoint funcionando, y ninguno pasa a `Implementado` porque eso exige Pull Request aprobado e integrado (Art. XVI).

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
| Reglas aplicables | `RN-SEG-003`, `RN-SEG-004`, `RN-SEG-010`, `RN-SEG-011` |
| Depende de | `RF-SP-001`, `RF-SP-010` |
| Tripleta | `docs/specs/sp/005-asignar-permisos/` |
| Estado | Pendiente |

Agrega permisos a un rol. La operación se rechaza si algún permiso no está contenido en el rol padre (`RN-SEG-003`) o en los permisos efectivos del actor (`RN-SEG-010`). Es el requerimiento donde se materializa el modelo de contención. **Alcanza también a los roles de sistema** (desde el 16-09-2026): `V8` los siembra sin permisos a la espera de esta operación, y `RN-SEG-012` protege su identidad y su posición, no lo que conceden.

#### `RF-SP-006` — Revocar permisos de un rol

| Campo | Valor |
|---|---|
| Objetivo | Reducir el alcance de un rol sin romper el invariante de contención |
| Actor | Administrador |
| Permiso requerido | `roles:update` |
| Prioridad | Alta |
| Reglas aplicables | `RN-SEG-005`, `RN-SEG-011` |
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
| Reglas aplicables | `RN-SP-006`, `RN-SP-007`, `RN-SP-024` |
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
| Actor | **Cualquiera, sin autenticar** |
| Permiso requerido | **Ninguno: es público** (`RN-SP-041`, 08-09-2026) |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-041` |
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
| Permiso requerido | `users:read-own-profile` — **hasta el 21-09-2026 «Autenticado»**, cuando `RF-SP-062` (`RN-SEG-015`) decidió que autenticarse no autoriza nada |
| Prioridad | Alta |
| Reglas aplicables | `RN-SEG-009`, `RN-SEG-002` |
| Depende de | `RF-SP-024` |
| Tripleta | `docs/specs/sp/039-consultar-perfil-propio/` |
| Estado | Pendiente |

Devuelve el perfil del **actor y solo del actor**, sin exigir `users:read`. Toda interfaz autenticada lo necesita para saber qué mostrar: sin él, quien no administra usuarios no puede ver ni sus propios permisos. No se resolvió dentro de `RF-SP-026` porque su alcance de datos y su autorización son distintos —siempre el actor, nunca otro—, y mezclarlos obligaría a aquella consulta a comportarse de dos maneras según a quién apuntara el identificador. Nace de la aprobación de `RF-SP-026` el 21-08-2026.

!!! important "Enmendado el 04-09-2026 (Art. I.7): **publica el identificador**"

    Esta consulta no devolvía el `uuid` de la persona, y el motivo escrito era «quien pregunta ya sabe quién es». **Es falso, y lo demostró el frontend al consumirlo**: quien pregunta sabe su nombre de usuario, no su identificador — el `uuid` viaja dentro del token y **no se puede leer desde el navegador sin descomponer un JWT**, que es justo lo que un cliente no debe hacer.

    La consecuencia era concreta: `POST /api/v1/movements` exige `clientId`, de modo que **quien compraba para sí mismo no podía decir quién era**. El único rodeo disponible —buscarse en `GET /api/v1/users?search=…`— exige `users:read`, que un cliente no tiene, y derivaría en el navegador un dato que el contrato no publica.

    **Se añade `id` y no se toca nada más.** No abre alcance: es el identificador **del propio actor**, que ya conoce el servidor y que esta consulta resuelve del token; no permite señalar a nadie más, porque la operación sigue sin admitir parámetros. Lo pidió el frontend como `R-28`.

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

**No asigna el vendedor de un cliente** (18-09-2026): un consumidor no tiene superior comercial; su vendedor principal es la fila `REGISTRO` de `client_sellers`, nace con el registro y no se reasigna (`RN-SP-049`). Entre el 01-09-2026 y esa fecha la rama de consumidor de `RN-SP-020` —la `T-02` de `RF-SP-045`, nunca construida— lo habría permitido; deja de existir sin haberse escrito.

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

!!! important "Enmendado el 10-09-2026: filtra por roles, y publica los roles de cada persona"

    Por decisión del responsable del proyecto. Son **dos cambios**, y el segundo es el que hace útil al primero.

    **Cada persona de la respuesta —la consultada, su superior y cada miembro del equipo— lleva ahora `roles`: la lista completa de los que porta**, con identificador, código y nombre, ordenada por código y **presente aunque vaya vacía**. Sustituye a `roleCode`, que devolvía **uno solo** y además solo si era de clasificación `VENDEDOR`. Desde que `RF-SP-045` cuelga a los clientes de esta misma estructura (§10.7), la cartera llegaba con **el rol en nulo**: no había forma de distinguir a un cliente de un vendedor sin rol. Lo que §10.7 daba por hecho —«cada fila lleva ya los roles de la persona, que es lo que permite distinguirlos»— **no fue cierto hasta hoy**.

    **El equipo directo admite un filtro por `roles`**, por códigos y varios a la vez, con semántica **O**: entra quien porte **alguno** de los pedidos. El total del equipo cuenta **lo filtrado**, no el equipo entero. Un código inexistente devuelve la página vacía y **no es un error**, mismo criterio que `RF-SP-025` con su filtro por rol.

    **El filtro no toca al superior ni a la persona consultada.** No es una omisión: el superior es uno solo y **su ausencia ya significa otra cosa** —«no depende de nadie», la cúspide—. Filtrarlo haría indistinguibles «es la cúspide» y «lo tiene, y no casa con el filtro».

    **Revierte la resolución 3 de la especificación**, que el 22-08-2026 descartó los filtros porque `RF-SP-025` ya filtra. El argumento dejó de valer el día que la cartera de clientes entró en esta estructura: `RF-SP-025` filtra **el listado general de usuarios**, y no sabe responder «de la gente que cuelga de este agente, enséñame solo los clientes».

!!! important "Enmendado el 18-09-2026: la cartera sale del equipo"

    Por decisión del responsable del proyecto (`RN-SP-028` revertida). El equipo directo vuelve a ser **solo fuerza comercial**: los clientes ya no cuelgan de `user_supervisors` y por tanto **no aparecen aquí**, ni filtrando por `CLIENTE`. La lectura no cambia de forma —sigue leyendo la tabla sin preguntar por el tipo de rol— y por eso **no necesita código**: en cuanto la migración de `RF-SP-059` mueva las filas, deja de devolverlos.

    **Los `roles` de cada persona y el filtro por `roles` se conservan.** El motivo que los trajo —distinguir clientes de vendedores— desaparece, pero un manager con directores y agentes mezclados sigue queriendo filtrar, y quitar un filtro publicado por haber perdido su primer motivo rompería a quien ya lo consume. La cartera de un vendedor —«¿a quiénes registró, a quiénes les vendió?»— es la lectura inversa de `RF-SP-059` y queda registrada como `RF-SP-061`, pendiente (nació como `RF-SP-060` en `feature/vendedores-de-un-cliente` y se renumeró el 21-09-2026 al integrar, porque `RF-SP-060` —un permiso por operación— nació en `feature/academia` el 19-09-2026).

#### `RF-SP-044` — Editar el propio perfil

| Campo | Valor |
|---|---|
| Objetivo | Permitir que cualquier persona autenticada corrija sus propios datos de identificación sin depender de un administrador |
| Actor | Cualquier persona autenticada |
| Permiso requerido | `users:update-own-profile` — **hasta el 21-09-2026 «Autenticado»**, cuando `RF-SP-062` (`RN-SEG-015`) decidió que autenticarse no autoriza nada |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-016` |
| Depende de | `RF-SP-039` |
| Tripleta | `docs/specs/sp/044-editar-perfil-propio/` |
| Estado | Pendiente |

Modifica el **propio** nombre, apellidos y correo. Es la contraparte de escritura de `RF-SP-039`: `RF-SP-027` ya hace este cambio, pero exige `users:update`, que es un permiso de administración, de modo que hoy **quien no administra usuarios no puede corregir un dato suyo mal escrito**. Concederle ese permiso para que arregle su propio apellido le daría de paso la capacidad de editar el de cualquiera.

**El cambio de correo exige la contraseña actual, y el de nombre no.** Desde `RF-SP-040` el correo es la vía por la que se recupera una contraseña olvidada, de modo que cambiarlo es cambiar quién puede recuperar la cuenta: una sesión robada no lleva la contraseña, y exigirla convierte el robo de sesión en algo que caduca en lugar de en una apropiación permanente. Equivocar un apellido, en cambio, no abre ninguna puerta.


#### `RF-SP-045` — Registro de clientes por enlace

| Campo | Valor |
|---|---|
| Objetivo | Que un cliente entre por sí mismo desde un enlace, con su membresía puesta y atribuido al vendedor que lo trajo |
| Actor | Persona sin cuenta |
| Permiso requerido | — (**Público**) |
| Prioridad | **Crítica** |
| Reglas aplicables | `RN-SP-016`, `RN-SP-018`, `RN-SP-026`, `RN-SP-027`, `RN-SP-028`, `RN-SP-034` a `RN-SP-038`, `RN-SP-040` a `RN-SP-044`, `RN-SP-049` |
| Depende de | `RF-SP-024`, `RF-SP-032`, `RF-SP-052`, `RF-PM-001`, `RF-MV-001` |
| Tripleta | `docs/specs/sp/045-registro-de-clientes-por-enlace/` |
| Estado | **En desarrollo** (09-09-2026) |

**Es el primer endpoint público del sistema que escribe.** Los seis que ya existen o leen, o consumen una credencial que el propio sistema emitió; este crea una persona, le concede un rol, le asigna una membresía y escribe una atribución a petición de alguien que todavía no es nadie.

El enlace lleva dos datos: el **producto** y el **vendedor** que lo generó, y desde el 09-09-2026 **los dos viajan dentro del bloque del movimiento** (`RN-SP-043`) — no en el primer nivel del cuerpo, donde estaban además duplicados. El producto declara la membresía destino (`RN-PM-002`) y su vigencia en días (`RN-PM-015`), de modo que registrarse concede el rol de consumidor y el nivel en la misma operación, que es justo lo que `RN-SP-018` exige.

**Ninguno de los dos datos es un secreto, y no hace falta que lo sea.** El código de producto es legible por diseño, así que cualquiera puede componer un enlace que no le dieron — y no gana nada. El enlace **gratuito** produce una cuenta en `FTD_PENDIENTE`, que autentica y no opera. Y el **de pago**, admitido desde el 09-09-2026, tampoco concede lo que se compra: la cuenta nace activa con la membresía **del suelo**, y la comprada llega cuando alguien confirma el pago (`RN-SP-044`). Por eso el enlace **se compone y no se persiste**: una tabla de enlaces emitidos es la defensa que haría falta si el enlace concediera algo.

**Y desde el 09-09-2026 el alta anota su venta** (`RN-SP-043`), en la misma transacción y con las reglas de `RF-MV-001` — no con una venta «simplificada» escrita aquí, que sería una segunda definición de vender y se quedaría atrás sin que nada fallara. La venta nace **pendiente**, que es lo que sostiene que registrarse y pagar sigan siendo dos hechos distintos.

Lo que sí queda abierto y declarado es que **la atribución es forjable**: quien componga el enlace elige a qué vendedor se apunta. No concede acceso, pero ensucia la base sobre la que `CM` comisionará, y la condición para cerrarlo está escrita — en cuanto se liquide una comisión sobre una atribución, el enlace tiene que dejar de ser componible. Nace el 01-09-2026, por decisión del responsable del proyecto.

**Dónde se escribe la atribución cambió el 18-09-2026** (`RN-SP-028` revertida, `RN-SP-049`): el cliente ya no cuelga de su vendedor en `user_supervisors`; el registro inserta la fila `REGISTRO` de `client_sellers`, con la venta del enlace como `first_movement_id`, y esa fila es su principal para siempre. Es una enmienda de hecho al requerimiento construido (Art. I.7): la migración de `RF-SP-059` mueve las filas que este endpoint escribió hasta hoy, y el caso de uso cambia de tabla **sin cambiar de contrato**. `T-02` —la rama de consumidor de `RN-SP-020`— deja de estar pendiente: ya no existe.
Hereda de `RF-SP-027` la pregunta abierta de la **verificación del correo**, que ya no tiene coartada: un correo mal tecleado deja a la persona sin vía de recuperación. No bloquea este requerimiento, porque hoy ese dato no se puede ni corregir. Nace el 31-08-2026, por decisión del responsable del proyecto.

#### `RF-SP-051` — Consultar tipos de documento

| Campo | Valor |
|---|---|
| Objetivo | Disponer del catálogo de documentos de identidad admitidos, para poder registrar a una persona |
| Actor | **Cualquiera, sin autenticar** |
| Permiso requerido | **Ninguno: es público** (`RN-SP-041`, 08-09-2026) |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-035`, `RN-SP-036`, `RN-SP-041` |
| Depende de | — |
| Tripleta | `docs/specs/sp/051-consultar-tipos-de-documento/` |
| Estado | Pendiente |

Listado de tipos de documento, con su **nombre** y su **abreviación**. Se puebla por migración y no se administra por API (`RN-SP-036`), igual que el catálogo de monedas.

!!! important "Este catálogo no es una lista de opciones: **es la validación de mayoría de edad**"

    Se pidió «validación de mayores de edad» y la forma que toma es la más barata y la más difícil de
    saltarse: **el catálogo solo contiene documentos que acredita una persona mayor de edad**. No
    lleva una columna que marque cuáles sí y cuáles no, y el alta no ejecuta ninguna comprobación de
    edad.

    **La diferencia importa.** Con una columna del tipo `acredita_mayoría`, registrar a un menor
    sería *posible y rechazado* — y bastaría con que un caso de uso futuro olvidara mirarla para que
    dejara de rechazarse. Sin ella, registrar a un menor es **inexpresable**: no hay identificador
    que poner en `users.document_type_id` que signifique «Tarjeta de Identidad», y quien lo intente
    choca contra `fk_users_document_type`. La regla vive en el **contenido** del catálogo y en el
    motor, no en un `if`.

    **Y de ahí sale `RN-SP-036`, que aquí no es simetría con las monedas sino una necesidad.** Si el
    catálogo se pudiera administrar por API, cualquiera con el permiso de alta añadiría «Tarjeta de
    Identidad» y **la validación desaparecería sin que ninguna regla cambiara, sin migración y sin
    que nadie lo notara**. Por eso se puebla por migración: cambiar quién puede entrar exige un
    cambio de esquema revisado, no una llamada.

    **Lo que esto NO hace, y queda dicho:** el tipo de documento es un **indicio** de mayoría de
    edad, no una prueba. Un pasaporte lo tiene un niño igual. La prueba de verdad exige **fecha de
    nacimiento**, que no se pide hoy; la condición para abrirla queda escrita — en cuanto haya que
    acreditar una edad concreta y no solo «es adulto», se registra el campo con su propia regla y
    este catálogo pasa a ser lo que su nombre dice.

---

#### `RF-SP-052` — Consultar el catálogo de brokers

| Campo | Valor |
|---|---|
| Objetivo | Disponer de la lista de brokers con los que opera la plataforma, para poder declarar en cuál se tiene cuenta |
| Actor | **Cualquiera, sin autenticar** |
| Permiso requerido | **Ninguno: es público** (`RN-SP-041`, 08-09-2026) |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-039`, `RN-SP-041` |
| Depende de | — |
| Tripleta | `docs/specs/sp/052-consultar-brokers/` |
| Estado | **Tasks en revisión** (08-09-2026) |

Listado de brokers **activos**, con su nombre. Se puebla por migración y no se administra por API (`RN-SP-039`), igual que los catálogos de monedas y de tipos de documento.

**De momento guarda solo el nombre**, por decisión del responsable del proyecto. No lleva código ni abreviación, y eso tiene una consecuencia que conviene tener escrita: **la clave de negocio es el nombre**, de modo que es él quien va con índice único y quien no puede repetirse. El día que un broker haga falta identificarlo por algo estable frente a un cambio de nombre comercial, se añade una columna `code` — y hasta entonces renombrar un broker es una migración, no una corrección.

#### `RF-SP-053` — Vincular una cuenta de broker a una persona

| Campo | Valor |
|---|---|
| Objetivo | Que quede registrado qué cuenta tiene cada persona en cada broker |
| Actor | **Por decidir** |
| Permiso requerido | **Por decidir** |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-038`, `RN-SP-040` |
| Depende de | `RF-SP-052` |
| Tripleta | Pendiente de crear |
| Estado | **Pendiente** — registrado y sin `spec.md` |

**La tabla existe desde el 08-09-2026 y el endpoint no**, y eso es deliberado: el responsable del proyecto pidió el catálogo y la tabla, y **quién declara la cuenta no está decidido** — si la declara el titular sobre sí mismo, como el perfil propio, o un funcionario con permiso sobre cualquiera.

Lo que sí está decidido y ya vive en el esquema: **la cuenta se declara con el broker y el identificador** —lo que la persona conoce— y **el nombre de usuario en el broker llega después** (`RN-SP-040`). Y **una cuenta es de una sola persona** (`RN-SP-038`), garantizado por índice único y no por una comprobación previa.

#### `RF-SP-054` — Completar la cuenta de broker desde el webhook del broker

| Campo | Valor |
|---|---|
| Objetivo | Que el propio broker confirme la cuenta y complete los datos que la persona no aportó |
| Actor | **El broker**, por integración |
| Permiso requerido | **Ninguno de los del sistema**: no lo llama una persona |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-040` |
| Depende de | `RF-SP-053` |
| Tripleta | Pendiente de crear |
| Estado | **Pendiente** — registrado y sin `spec.md` |

**Es una ruta que llama alguien de fuera, y eso la convierte en la segunda superficie pública del sistema** —la primera es el hotlink de `RF-PM-008`—, con una diferencia que la hace más delicada: aquella **lee** y esta **escribe**. Todo lo que la gobierna está sin decidir y se registra aquí para que no se improvise el día que se construya:

- **Cómo se autentica el broker.** Firma del cuerpo con un secreto compartido, contraseña de aplicación o lista de orígenes: sin esto, cualquiera puede reescribir la cuenta de cualquiera.
- **Qué pasa si el webhook llega para una cuenta que nadie declaró.** Se ignora, se registra o se crea.
- **Si puede cambiar el identificador**, o solo rellenar el nombre de usuario.
- **La reentrega**: un webhook se repite, de modo que la operación tiene que ser **idempotente** o dejará dos rastros del mismo hecho.

#### `RF-SP-055` — Consultar las cuentas de broker de una persona

| Campo | Valor |
|---|---|
| Objetivo | Saber qué cuentas declaró una persona, en qué broker y **en qué punto está cada una** |
| Actor | **Su superior comercial vigente**, o un administrador |
| Permiso requerido | `broker-accounts:read-team-member` abre la ruta (desde el 21-09-2026, `RF-SP-062`); **quién es visible lo decide `RN-SP-046`**: el superior vigente ve a su subordinado directo o a su cliente propio, y quien además porte `broker-accounts:read` ve a cualquiera |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-040`, `RN-SP-045`, `RN-SP-046` |
| Depende de | `RF-SP-041`, `RF-SP-045`, `RF-SP-052` |
| Tripleta | `docs/specs/sp/055-consultar-cuentas-de-broker/` |
| Estado | **En desarrollo** (10-09-2026) |

Las cuentas de **una** persona: broker, identificador, nombre de usuario en el broker —**nulo mientras el broker no lo confirme** (`RN-SP-040`)— y **estado** (`RN-SP-045`).

**La estructura comercial es la llave, y esto es lo nuevo.** Hasta hoy ninguna lectura del sistema se autorizaba por `user_supervisors`: la tabla decía a quién se atribuye cada resultado y nada más, y `V21` lo dejó escrito —«registrar la estructura no concede alcance de datos»—. **Aquí sí lo concede, y solo aquí**: la **D-22** sigue abierta y este requerimiento no la resuelve.

**Quien no es el superior vigente ni trae el permiso recibe `404`**, no `403`: distinguir «esa persona no existe» de «esa persona no es tuya» convertiría el endpoint en un oráculo de identificadores para cualquier vendedor.

**No lo usa el titular sobre sí mismo.** Se decidió el 10-09-2026 dejarlo fuera: la lectura se definió sobre el equipo, y abrirla al titular es una decisión distinta que tiene su propia vía —`RF-SP-039`—.

#### `RF-SP-056` — Consultar las cuentas de broker del equipo

| Campo | Valor |
|---|---|
| Objetivo | Ver de una sola vez **qué gente de mi equipo ya depositó y quién sigue esperando** |
| Actor | **El superior comercial**, sobre su propio equipo |
| Permiso requerido | `broker-accounts:read-own-team` — **hasta el 21-09-2026 «Autenticado»**, cuando `RF-SP-062` (`RN-SEG-015`) decidió que autenticarse no autoriza nada. El alcance —el equipo propio— sigue poniéndolo la estructura (`RN-SP-046`) |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-040`, `RN-SP-045`, `RN-SP-046` |
| Depende de | `RF-SP-041`, `RF-SP-055` |
| Tripleta | `docs/specs/sp/056-consultar-cuentas-de-broker-del-equipo/` |
| Estado | **En desarrollo** (10-09-2026) |

El **listado plano y paginado** de las cuentas de todas las personas que dependen de mí, **cada fila con su titular**, y con filtro por **estado** y por **broker**.

**Es la misma pregunta que `RF-SP-055`, hecha del otro lado**, y por eso son dos y no uno: recorrer el equipo cliente a cliente obligaría al frontend a `N + 1` llamadas para pintar una pantalla que es una sola lista, y ordenar «los que faltan por depositar» sería imposible sin traérselos todos antes.

**Un solo nivel** (`RN-SP-046`), como `RF-SP-042`: quienes reportan **directamente**. El árbol descendente publicaría la estructura entera de la empresa por una lectura de cuentas de broker.

**Quien no tiene equipo recibe `200` con la página vacía**, no `404` ni `403`. «No tengo a nadie a cargo» es una respuesta legítima y distinta de un error, con el mismo criterio que `RF-SP-042` aplica a quien no pertenece a la fuerza comercial.

#### `RF-SP-057` — Consultar y filtrar todas las cuentas de broker

| Campo | Valor |
|---|---|
| Objetivo | Ver **todas** las cuentas de broker del sistema y acotarlas: por **red de un vendedor**, por persona, por estado, por broker, por texto y por fecha de declaración |
| Actor | Administrador |
| Permiso requerido | `broker-accounts:read` |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-040`, `RN-SP-045`, `RN-SP-047` |
| Depende de | `RF-SP-055`, `RF-SP-056` |
| Tripleta | `docs/specs/sp/057-consultar-todas-las-cuentas-de-broker/` |
| Estado | **En desarrollo** (10-09-2026) |

**Nace de un hueco que `RF-SP-056` cerró a propósito y duró un día.** Aquel resolvió «¿cómo va **mi** equipo?» y dejó escrito que un administrador no podía pedir el de otro —«un `?supervisorId=` es otro requerimiento y nadie lo ha pedido»—. Se pidió el 10-09-2026: el superadministrador quiere **ver todas las cuentas y filtrar por el vendedor que le interese**, y sin esto tenía que recorrer el árbol persona a persona, una llamada por cada una.

**Es un recurso propio y no un parámetro más en `/users/me/team/...`.** Colgarlo de `me` obligaría a que la ruta dejara de significar «lo mío» en cuanto llegara un `supervisorId`, y el mismo camino serviría a dos preguntas con dos autorizaciones distintas.

**`supervisorId` devuelve la red ENTERA, en profundidad** (`RN-SP-047`), y no un nivel. Es la primera consulta recursiva del sistema, y rompe a propósito la cota que se imponen `RF-SP-042`, `RF-SP-055` y `RF-SP-056`: aquellas la tienen porque las autoriza la **estructura**; esta la autoriza el **permiso**, que ya alcanza a todo el mundo — la profundidad no concede nada nuevo, ahorra el recorrido.

**Sin `supervisorId` devuelve el sistema entero**, paginado. Es lo que se pidió y es lo que el permiso significa.

**No retira `RF-SP-055`.** Aquella existe para **el superior sin permiso**, y su `404` uniforme sigue siendo su razón de ser. Esta y aquella se parecen en la forma y no en quién puede llamarlas.

**La respuesta lleva además un RESUMEN de lo filtrado** (10-09-2026, por decisión del responsable del proyecto): **cuántos registros hay** y **cuántos están en cada uno de los dos estados**, los tres con su **desglose por broker**. Va dentro del listado y no en un endpoint aparte — una sola llamada pinta la tabla y sus contadores, y así no pueden desincronizarse.

**Van los DOS estados y no solo el depósito**: el embudo tiene dos lados, y obligar al cliente a restar —`accounts − firstDeposit`— es pedirle que rehaga una cuenta ya hecha. Con dos valores posibles la resta es trivial **hoy**; el día que `RN-SP-045` admita un tercero dejaría de serlo, y quien la escribió no se enteraría. **`accounts` es siempre `register` + `firstDeposit`.**

**El resumen respeta TODOS los filtros, incluido `status`**, por decisión expresa del responsable del proyecto. Es la opción **coherente** —el resumen describe exactamente lo que la consulta devuelve, sin excepciones— y tiene una consecuencia que hay que conocer antes de pintarla: **con `?status=REGISTER` el total de `FIRST_DEPOSIT` vale siempre cero**, y con `?status=FIRST_DEPOSIT` vale siempre el total. Ese cero **no significa «nadie ha depositado»**: significa «no pediste ninguno». Se descartó la alternativa —que el conteo de FTD ignorase el filtro de estado— porque habría dado dos números calculados sobre conjuntos distintos dentro de la misma respuesta.

**El desglose por broker trae TODOS los brokers del catálogo, con cero donde no hay.** Nació al revés el mismo día y se invirtió: un arreglo cuya longitud depende del filtro obliga a **rearmar las columnas en cada consulta**, y una columna que desaparece se lee como un dato que falta, no como un cero. Consecuencia declarada: **un broker desactivado sigue apareciendo** —apagarlo no borra lo declarado en él—, al precio de una columna muerta el día que se retire alguno.

#### `RF-SP-058` — Consultar los indicadores de la red comercial

| Campo | Valor |
|---|---|
| Objetivo | Saber **cuánto FTD lleva cada vendedor**, lo suyo y lo de toda su red, con el embudo y la conversión |
| Actor | Administrador |
| Permiso requerido | `broker-accounts:read` |
| Prioridad | **Crítica** |
| Reglas aplicables | `RN-SP-045`, `RN-SP-047`, `RN-SP-048` |
| Depende de | `RF-SP-057` |
| Tripleta | `docs/specs/sp/058-indicadores-de-la-red-comercial/` |
| Estado | **En desarrollo** (10-09-2026) |

**El árbol de la fuerza comercial, cada nodo con sus dos bloques de números**: `own` —lo que cuelga directamente de él— y `network` —él más todo lo de abajo—. Cada bloque lleva cuentas declaradas, con primer depósito, pendientes, conversión y cuántos consumidores.

**Los dos bloques van SIEMPRE, y no es redundancia**: el total de un director ya contiene el de sus agentes, de modo que **sumar una columna de totales cuenta dos veces**. Publicar solo el total invita a ese error; publicar los dos hace que el que suma tenga que elegir cuál, y elegir es acordarse.

**Los consumidores no son nodos.** Aportan el número y no aparecen: el árbol es de la fuerza comercial. Publicarlos convertiría un indicador de gestión en el listado de clientes de la empresa.

**La conversión es nula cuando no hay cuentas, y no cero.** Cero se lee como «nadie convirtió» y la verdad es «no hay nada que convertir» — la misma distinción que `RN-SP-040` hace con el nulo del nombre de usuario.

**Y la respuesta lleva lo NO ATRIBUIDO**, que es lo que hace que los números cuadren: las cuentas de consumidores que no cuelgan de ningún vendedor no entran en ningún nodo, y sin ese bloque el árbol sumaría menos que `RF-SP-057` sin que nadie supiera por qué.

**Hoy todos los FTD serán cero.** Nadie mueve una cuenta a `FIRST_DEPOSIT` hasta que exista el webhook de `RF-SP-054`, de modo que el embudo se verá entero en `pending`. No es un fallo del indicador: es el estado real del sistema.

#### `RF-SP-047` — Registrar una tasa de cambio

| Campo | Valor |
|---|---|
| Objetivo | Declarar a cuánto se cambia una moneda por otra, y desde cuándo |
| Actor | Administrador |
| Permiso requerido | `exchange-rates:create` |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-029` a `RN-SP-032` |
| Depende de | — |
| Tripleta | `docs/specs/sp/047-registrar-tasa-de-cambio/` |
| Estado | **Tasks en revisión** (07-09-2026) |

Registra una tasa declarando **origen, destino, precio y desde cuándo rige**, con la fecha de fin opcional —sin ella la tasa es **vitalicia**— y su estado. Es el requerimiento que crea la tabla del módulo y **siembra sus cuatro permisos**, con la obligación de asociarlos a `SUPERADMIN` y `ADMIN` en la misma migración ([`security.md` §4.4](../security.md#44-catalogo-de-permisos)).

**El rechazo que más importa es el solapamiento** (`RN-SP-032`): lo decide un `EXCLUDE` del motor y llega como `409`, no como `500`. Traducirlo es tarea de este requerimiento y de `RF-SP-049`.

#### `RF-SP-048` — Consultar las tasas de cambio

| Campo | Valor |
|---|---|
| Objetivo | Ver qué tasas hay, cuáles rigen hoy y cuáles rigieron |
| Actor | Administrador · fuerza comercial |
| Permiso requerido | `exchange-rates:read` |
| Prioridad | Alta |
| Reglas aplicables | — |
| Depende de | `RF-SP-047` |
| Tripleta | `docs/specs/sp/048-consultar-tasas-de-cambio/` |
| Estado | **Tasks en revisión** (07-09-2026) |

Devuelve las tasas **paginadas**, con las dos monedas resueltas —código y decimales— y filtros por origen, destino, estado y **vigencia a una fecha**. Ese último es el que responde la pregunta que se hace a diario: *¿a cuánto está el cambio hoy?*

**Incluye las vencidas y excluye las retiradas salvo que se pidan**, por el mismo criterio que `RF-PM-002`: una tasa que dejó de regir explica por qué una conversión de hace un mes dio lo que dio.

#### `RF-SP-049` — Corregir una tasa de cambio

| Campo | Valor |
|---|---|
| Objetivo | Enmendar lo que se declaró mal, sin reescribir lo que ya se convirtió |
| Actor | Administrador |
| Permiso requerido | `exchange-rates:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-030` a `RN-SP-032` |
| Depende de | `RF-SP-047` |
| Tripleta | `docs/specs/sp/049-corregir-tasa-de-cambio/` |
| Estado | **Tasks en revisión** (07-09-2026) |

Permite corregir **el precio, la vigencia y el estado**. **No permite cambiar ninguna de las dos monedas**: son las que definen qué cambio expresa la tasa, y tocarlas la convertiría en otra — quien necesite otro par registra otra y retira esta. Es el mismo criterio que `RF-PM-004` aplica al tipo y a las membresías de un producto.

**Es la operación que puede violar `RN-SP-032` sin que el alta lo haya hecho**: mover una vigencia o activar una tasa inactiva puede pisar a la que ya rige (§5.2).

#### `RF-SP-050` — Retirar una tasa de cambio

| Campo | Valor |
|---|---|
| Objetivo | Sacar de circulación una tasa que no debió existir |
| Actor | Administrador |
| Permiso requerido | `exchange-rates:delete` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-032`, `RN-SP-033` |
| Depende de | `RF-SP-047` |
| Tripleta | `docs/specs/sp/050-retirar-tasa-de-cambio/` |
| Estado | **Tasks en revisión** (07-09-2026) |

Retira lógicamente una tasa **exigiendo motivo** (Art. V.13), que viaja al registro de eliminación con la instantánea de lo retirado. **La fila permanece**: el día que algo se convierta con una tasa, esa conversión tendrá que poder decir cuál usó.

**Retirar libera su periodo** (§5.2): el `EXCLUDE` es parcial sobre las vivas, de modo que después de retirar se puede declarar otra tasa que cubra esos mismos días.
#### `RF-SP-059` — Consultar los vendedores de un cliente

| Campo | Valor |
|---|---|
| Objetivo | Que un cliente sepa **quiénes le venden** —su agente principal y los vendedores por cuyo hotlink compró— y que administración pueda verlo |
| Actor | El propio cliente; Administrador |
| Permiso requerido | `users:read-own-sellers` (`GET /users/me/sellers`, **autenticado a secas hasta el 21-09-2026**, `RF-SP-062`), o `users:read-sellers` (`GET /users/{id}/sellers`) — **`users:read` hasta el 19-09-2026**, cuando `RF-SP-060` decidió un permiso por operación; lo siembra **`V29`** el 21-09-2026, al integrar la rama que construyó la ruta |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-027`, `RN-SP-028`, `RN-SP-049` |
| Depende de | `RF-SP-045` |
| Tripleta | `docs/specs/sp/059-consultar-vendedores-de-un-cliente/` |
| Estado | **En desarrollo** (21-09-2026) — registrado el 16-09-2026, **rediseñado el 18-09-2026** (trae la migración) y construido ese día en su rama; integrado el 21-09-2026 con `users:read-sellers` y `V29` |

**Trae la migración que crea `client_sellers` y saca a los clientes de `user_supervisors`** (`RN-SP-028` revertida el 18-09-2026). Copia cada fila vigente cuyo subordinado porte un rol `CONSUMIDOR` como `REGISTRO` con `first_movement_id` nulo —el dato existía antes que la tabla, y la venta de registro no se puede reconstruir con certeza— y **borra** de `user_supervisors` todas las filas de clientes, vigentes y cerradas: no se cierran, porque no eran mando y una fila cerrada seguiría diciendo que lo fueron. Ya no depende de `RF-MV-011`: hasta que la compra por hotlink exista la lista tiene un solo elemento —el principal— y **el contrato ya es el definitivo**.

**La lista sale de `client_sellers`** y trae, de cada vendedor, nombre y apellido, el origen del vínculo, la fecha en que nació y si es el **principal** —el de origen `REGISTRO`, quien lo registró—. Ordenada con el principal primero y después por fecha de vínculo.

**No publica nada que el cliente no sepa ya**: cada vendedor de la lista es alguien a quien le compró o quien lo registró. Y no publica más que nombre y apellido, como el hotlink (`RN-PM-022`).

**Lo que no hace**: no permite cambiar el principal —porque no se cambia (`RN-SP-049`); hasta el 18-09-2026 esa vía era `RF-SP-041`, y ya no alcanza a clientes— ni quitar un vínculo, porque un vínculo es un hecho.

#### `RF-SP-060` — Un permiso por operación

| Campo | Valor |
|---|---|
| Objetivo | Que **cada operación de la API exija un permiso que ninguna otra exige**, de modo que conceder un permiso a un rol conceda exactamente una cosa |
| Actor | Administrador de roles; el sistema, al migrar |
| Permiso requerido | — Es un cambio del catálogo (`security.md` §4.4: datos, no código), aplicado por migración |
| Prioridad | **Crítica** |
| Reglas aplicables | `RN-SEG-003`, `RN-SEG-007`, `RN-SEG-009`, **`RN-SEG-014`** (nace con él) |
| Depende de | El bloque 4 de `AC` (`RF-AC-016` a `RF-AC-021`) para su tramo 3 |
| Tripleta | `docs/specs/sp/060-un-permiso-por-operacion/` |
| Estado | **Tasks en revisión** — registrado el 19-09-2026 |

**Lo pidió el responsable del proyecto el 19-09-2026**: «hay algunos permisos que se le podrán asignar a diferentes roles, pero al compartir permiso con otras rutas puede que se llegue a asignar rutas que no debería». Hoy sesenta permisos gobiernan noventa y siete operaciones, y **veintiún códigos gobiernan más de una** —`roles:update` cinco, entre ellas asignar y revocar permisos; `courses:update` diez; `packages:update` siete—. Un permiso que agrupa es un permiso que **no se puede conceder a medias**, y el catálogo existe para los roles que se crearán con una parte.

**Lo que decide**: cada uno de los veintiún códigos **se queda con una operación** y estrecha su descripción; las demás reciben código nuevo. **Ningún código se renombra ni se retira.** Son cincuenta y un permisos nuevos y el catálogo pasa de sesenta a **ciento once** —trece de `SP`, catorce de `PM`, seis de `CM`, dieciocho de `AC`, ninguno de `MV`—, con el reparto completo y las convenciones de nombre en la spec (§6.2 y §6.3): `list`/`read`, `change-status`, `set-cover`/`remove-cover`, `assign-`/`revoke-`, `add-`/`update-`/`remove-` para elementos con datos propios, y recurso propio para lo que tiene identidad y rutas propias (`course-modules:`, `lessons:`, `user-commission-rates:`).

**Nadie pierde nada al migrar**: la migración da cada hijo a **todo rol que porte el padre** —los dos de sistema y cualquiera creado a mano—, con lo que `RN-SEG-003` se conserva por construcción. Estrechar después es trabajo de quien administre roles (`RF-SP-006`). La reserva del superadministrador no cambia: ninguno de sus seis se divide.

**Lo que impide que vuelva a pasar** es `RN-SEG-014` y la prueba que la vigila: `EndpointPermissionsIT` afirma desde entonces que ningún permiso aparece en dos operaciones.

**En `SP` cambian trece operaciones**: `roles:list`, `roles:assign-permissions`, `roles:revoke-permissions`, `roles:change-status`, `roles:assign-parent`, `permissions:list`, `memberships:list`, `users:list`, `users:change-status`, `users:revoke-roles`, `users:revoke-membership`, `users:read-team` y `broker-accounts:read-indicators`; §6.1 y §9 ya los nombran. `RF-SP-059`, pendiente, nacerá con `users:read-sellers` — **y nació con él el 21-09-2026**: su rama se redactó con `users:read` el día antes de este requerimiento, y al integrarla `V29` siembra `users:read-sellers` (serie de `SP`, `…000025`) a `SUPERADMIN` y `ADMIN`; el catálogo pasa a **ciento doce**.

#### `RF-SP-061` — Consultar los clientes de un vendedor

| Campo | Valor |
|---|---|
| Objetivo | Que un vendedor vea **su cartera** —a quiénes registró y a quiénes les vendió por hotlink— y que administración pueda verla |
| Actor | El propio vendedor; Administrador |
| Permiso requerido | `users:read-own-clients` (`GET /users/me/clients`, **autenticado a secas hasta el 21-09-2026**, `RF-SP-062`), o `users:read-clients` (`GET /users/{id}/clients`) — permiso propio por `RN-SEG-014` (`RF-SP-060`), sembrado por `V30` a `SUPERADMIN` y `ADMIN` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-049`, `RN-SEG-014` |
| Depende de | `RF-SP-059` |
| Tripleta | `docs/specs/sp/061-consultar-clientes-de-un-vendedor/` |
| Estado | **En desarrollo** (21-09-2026) — registrado el 18-09-2026 como `RF-SP-060` en `feature/vendedores-de-un-cliente`; renumerado a `061` el 21-09-2026 al integrar sobre `feature/academia`, donde `RF-SP-060` ya era «un permiso por operación»; tripleta redactada el mismo día |

Es la lectura inversa de `RF-SP-059`, y nace el día que la cartera sale de `RF-SP-042`: hasta el 18-09-2026 «los clientes de un agente» se respondía filtrando el equipo por `CLIENTE`, y con `RN-SP-028` revertida el equipo no los contiene. Lee `client_sellers` por `seller_id` —el índice que §10.19 exige existe para esto— y distingue, por cada cliente, si es **suyo** (`REGISTRO`) o solo **vinculado** (`HOTLINK`). Paginada, como `RF-SP-042`. Es la misma pregunta que hoy contesta `RF-SP-057` para las cuentas de broker, hecha sobre las personas.

**Lo que devuelve de cada cliente, decidido el 21-09-2026**: identificador, nombre de usuario, nombre y apellido, **estado**, origen del vínculo (`REGISTRO` o `HOTLINK`), si el vendedor es su **principal** y desde cuándo. **Con `id`, al contrario que `RF-SP-059`**, y por la razón inversa: desde la cartera se abre la ficha del cliente (`RF-SP-026`), y un vendedor sin identificador no tendría cómo llegar; el cliente de `RF-SP-059`, en cambio, no tiene ninguna ruta donde usar el del vendedor. **Con estado**, porque un vendedor necesita distinguir un cliente activo de uno desactivado para trabajar su cartera, y porque el vínculo es un hecho que no se oculta cuando el cliente cambia de estado (`RN-SP-049`). **Los más recientes primero**, porque la cartera crece por el final y lo nuevo es lo que se atiende; un filtro opcional `origin` separa los propios de los vinculados. **Solo `client_sellers`**: un vendedor subordinado en `user_supervisors` no es un cliente de su director, y la lista no lo incluye —el equipo es `RF-SP-042`—. Tampoco se autoriza por estructura: el director de un vendedor no ve la cartera de este por serlo (D-22 no se amplía); la ve quien porte `users:read-clients`.

**Lo pidió el frontend el 21-09-2026 (R-45 de `docs/para-el-backend.md`)**: `/mis-clientes` y la pestaña «Clientes» de la ficha de un usuario leían `GET /users/{id}/team?roles=CONSUMIDOR` y se quedan sin fuente al salir la cartera del equipo, de modo que este requerimiento las desbloquea. Y una petición concreta para su tripleta: **la respuesta trae el `id` del cliente** —desde la cartera se abre su ficha, y sin `id` no hay enlace—, al contrario que `RF-SP-059`, que omite el del vendedor porque nada se abre desde ahí.

#### `RF-SP-062` — Autenticarse no autoriza nada

| Campo | Valor |
|---|---|
| Objetivo | Que **toda operación que exija token exija además un permiso**, también las que actúan sobre el propio actor, de modo que el frontend decida qué vista o consulta mostrar mirando solo los permisos |
| Actor | Administrador de roles; el sistema, al migrar |
| Permiso requerido | — Es un cambio del catálogo (`security.md` §4.4) y de once anotaciones, aplicado por migración |
| Prioridad | **Crítica** |
| Reglas aplicables | `RN-SEG-015` (nace con él), `RN-SEG-014`, `RN-SEG-003` |
| Depende de | `RF-SP-060` |
| Tripleta | `docs/specs/sp/062-autenticarse-no-autoriza-nada/` |
| Estado | **En desarrollo** (21-09-2026) — construido el mismo día que su tripleta |

**Lo pidió el responsable del proyecto el 21-09-2026, dos veces y con la razón**: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; hazla como regla general, es decir, no basta con solo tener el token». `RF-SP-060` había dejado un permiso por operación **entre las operaciones que tenían permiso**; once se atendían con solo el token —el propio perfil y su corrección, la propia contraseña, mis vendedores, mis clientes, las cuentas de broker de mi equipo y las de una persona a cargo, mis movimientos y su detalle, mis productos comprados, la compra propia de un paquete—, cada una con el argumento «alcance sobre uno mismo: no hay nada que autorizar más allá de estar autenticado». El argumento era correcto para la seguridad y no sirve al frontend, que con «tiene token» no puede decidir nada.

**Lo que decide**: once permisos nuevos con `own` para el alcance sobre uno mismo (`users:read-own-profile`, `users:update-own-profile`, `users:change-own-password`, `users:read-own-sellers`, `users:read-own-clients`, `broker-accounts:read-own-team`, `broker-accounts:read-team-member`, `movements:list-own`, `movements:read-own`, `movements:read-own-products`, `packages:buy`), sembrados por `V31` y dados a **todo rol por su tipo** —los de `FUNCIONARIO` y `VENDEDOR` reciben los once; los de `CONSUMIDOR`, ocho: no los tres de vendedor— para que nadie pierda nada; el catálogo pasa a **ciento veinticuatro**. Las **catorce públicas** —las que se atienden sin token— siguen sin permiso: no es una excepción a la regla, es su límite. `EndpointPermissionsIT` deja de admitir «autenticada a propósito» y solo admite «pública».

**Lo que cuesta**: un rol creado a mano nace sin permisos, y desde hoy eso significa que sus personas no pueden ni ver su perfil ni cambiar su contraseña hasta que se les concedan los de alcance propio por `RF-SP-005`. Queda escrito en `security.md` §4.4.

#### `RF-SP-063` — Registrar equipo

| Campo | Valor |
|---|---|
| Objetivo | Crear un equipo en el que organizar a los managers |
| Actor | Administrador |
| Permiso requerido | `teams:create` |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-050` |
| Depende de | — |
| Tripleta | `docs/specs/sp/063-registrar-equipo/` |
| Estado | **En desarrollo** (22-09-2026) |

Registra un equipo con **nombre**, **descripción** opcional y estado inicial `ACTIVO`. **No tiene código**, al contrario que roles y membresías, y es una decisión y no un olvido: el código de un rol existe porque el código lo referencia (`hasAuthority`, `SellerRoleCatalog`), y el de una membresía porque `PM` la referencia desde un producto; **nada del sistema referencia a un equipo por nombre estable** —se llega a él por su identificador— y un código que nadie usa es una columna más que mantener única. Lo que identifica es el nombre, y por eso su unicidad es funcional (`RN-SP-050`). **Nace vacío**: los managers se asignan después (`RF-SP-069`), porque asignar exige validar contra `user_roles` y tiene sus propias reglas, y mezclar las dos operaciones en un alta haría que un nombre repetido y un manager inválido salieran por el mismo `409`.

**Lo pidió el responsable del proyecto el 21-09-2026** —«un CRUD de equipos, sirve para organizar el máximo rango de vendedores»— y ese mismo día se fijó la forma: solo managers, uno vigente por manager con historial, la ficha sin código ni país, y las ocho operaciones de este bloque. El equipo **no es una tercera jerarquía**: es una **partición de la cúspide**. Debajo de un manager la organización ya está dicha por `user_supervisors`; lo que faltaba era cómo se agrupan quienes no tienen a nadie encima, y eso es lo único que esta tabla añade.

#### `RF-SP-064` — Consultar equipos

| Campo | Valor |
|---|---|
| Objetivo | Ver qué equipos existen, en qué estado y con cuántos managers |
| Actor | Administrador |
| Permiso requerido | `teams:list` |
| Prioridad | Alta |
| Reglas aplicables | — |
| Depende de | `RF-SP-063` |
| Tripleta | `docs/specs/sp/064-consultar-equipos/` |
| Estado | **Tasks en revisión** (22-09-2026) |

Listado paginado de equipos no eliminados, con filtro por estado y búsqueda por nombre insensible a mayúsculas y acentos, como `RF-SP-002`. Cada fila lleva, además de la ficha, **cuántos managers vigentes** tiene: es lo que distingue de un vistazo un equipo en uso de uno vacío, y contarlos cuesta un índice parcial (`ix_team_members_team_vigente`) que `RF-SP-065` y `RN-SP-054` necesitan igual. **No devuelve a los managers**: eso es el detalle. Listado y detalle son dos permisos (`RN-SEG-014`).

#### `RF-SP-065` — Consultar detalle de un equipo

| Campo | Valor |
|---|---|
| Objetivo | Ver la ficha de un equipo y quiénes lo forman hoy |
| Actor | Administrador |
| Permiso requerido | `teams:read` |
| Prioridad | Media |
| Reglas aplicables | — |
| Depende de | `RF-SP-063` |
| Tripleta | `docs/specs/sp/065-consultar-detalle-equipo/` |
| Estado | **Tasks en revisión** (22-09-2026) |

Devuelve el equipo con la lista de sus **managers vigentes** —identificador, nombre de usuario, nombre y apellido, estado de la persona y desde cuándo pertenece—, ordenados por antigüedad en el equipo. **Sin paginar**: la cúspide de la fuerza comercial son unas pocas personas por equipo, y paginar una lista que cabe en una pantalla añadiría un contrato sin un caso que lo pida; si algún día un equipo reuniera cientos de managers, el problema sería de organización antes que de API. **Solo vigentes**: el historial —quién perteneció y cuándo salió— lo conserva la tabla y lo consultará quien lo necesite (las comisiones), no esta ficha. **La lectura inversa —a qué equipo pertenece un manager— no está en este bloque**: se anota como pendiente para la ficha de la persona (`RF-SP-026`) o para `GET /users/{id}/team`, y se decidirá cuando el frontend diga dónde la necesita.

#### `RF-SP-066` — Editar equipo

| Campo | Valor |
|---|---|
| Objetivo | Corregir el nombre o la descripción de un equipo |
| Actor | Administrador |
| Permiso requerido | `teams:update` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-050` |
| Depende de | `RF-SP-063` |
| Tripleta | `docs/specs/sp/066-editar-equipo/` |
| Estado | **Tasks en revisión** (22-09-2026) |

Modifica nombre y descripción, con la misma forma que `RF-SP-004`. **No** modifica el estado ni los managers: cada una de esas operaciones tiene sus reglas y su requerimiento. Renombrar revalida `RN-SP-050` contra los demás equipos no eliminados.

#### `RF-SP-067` — Cambiar el estado de un equipo

| Campo | Valor |
|---|---|
| Objetivo | Dejar de organizar con un equipo sin perder cómo estaba organizado |
| Actor | Administrador |
| Permiso requerido | `teams:change-status` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-053` |
| Depende de | `RF-SP-063` |
| Tripleta | `docs/specs/sp/067-cambiar-estado-equipo/` |
| Estado | **Tasks en revisión** (22-09-2026) |

Activa o desactiva un equipo. Un equipo `INACTIVO` deja de admitir managers nuevos y **conserva** los que tiene (`RN-SP-053`). A diferencia de `RF-SP-007`, el cambio **no toca la autorización de nadie**: un equipo no concede ni retira permisos, y no hay caché que invalidar.

#### `RF-SP-068` — Eliminar equipo

| Campo | Valor |
|---|---|
| Objetivo | Retirar un equipo que dejó de tener sentido |
| Actor | Administrador |
| Permiso requerido | `teams:delete` |
| Prioridad | Baja |
| Reglas aplicables | `RN-SP-054` |
| Depende de | `RF-SP-063` |
| Tripleta | `docs/specs/sp/068-eliminar-equipo/` |
| Estado | **Tasks en revisión** (22-09-2026) |

Elimina lógicamente un equipo **sin managers vigentes** (`RN-SP-054`), con motivo declarado (Art. V.13) y por la misma ruta que `RF-SP-029` y `RF-SP-050`: `POST …/deletion` con el motivo en el cuerpo. El nombre queda libre (`RN-SP-050`); las pertenencias cerradas sobreviven.

#### `RF-SP-069` — Asignar miembros a un equipo

| Campo | Valor |
|---|---|
| Objetivo | Decir a qué equipo pertenece cada manager desde hoy |
| Actor | Administrador |
| Permiso requerido | `teams:assign-members` |
| Prioridad | Alta |
| Reglas aplicables | `RN-SP-051`, `RN-SP-052`, `RN-SP-053`, `RN-SP-003`, `RN-SP-011` |
| Depende de | `RF-SP-063`, `RF-SP-030` |
| Tripleta | `docs/specs/sp/069-asignar-miembros-a-equipo/` |
| Estado | **Tasks en revisión** (22-09-2026) |

Asigna **uno o varios** managers a un equipo `ACTIVO`, en una sola operación y una sola transacción, como `RF-SP-030` asigna roles. Cada persona debe portar el rol vendedor de mayor rango (`RN-SP-051`); si ya pertenece a otro equipo, la pertenencia anterior **se cierra** y se abre la nueva (`RN-SP-052`); si ya pertenece a **este**, la operación **no hace nada con ella y no falla** —volver a decir lo que ya es cierto no es un error, mismo criterio que `RF-SP-030` con un rol que ya se porta—. **Toda la lista o nada**: una persona que no cumple hace fallar la operación entera, porque a medias dejaría al administrador sin saber cuáles entraron.

Exige **motivo declarado**, por la misma razón que `RF-SP-041`: el historial sustentará el reparto de comisiones, y un cambio de equipo sin explicación es un agujero cuando alguien discuta una liquidación. La asignación rige **desde el momento de ejecutarse**; no admite fecha declarada. Se opera **desde el equipo** (`POST /teams/{id}/members`) y no desde la persona, porque la pregunta que el administrador se hace es «a quién pongo en este equipo», y porque así el permiso vive con el recurso que cambia.

#### `RF-SP-070` — Retirar miembros de un equipo

| Campo | Valor |
|---|---|
| Objetivo | Sacar a un manager de su equipo sin ponerlo en otro |
| Actor | Administrador |
| Permiso requerido | `teams:remove-members` |
| Prioridad | Media |
| Reglas aplicables | `RN-SP-052`, `RN-SP-055` |
| Depende de | `RF-SP-069` |
| Tripleta | `docs/specs/sp/070-retirar-miembros-de-equipo/` |
| Estado | **Tasks en revisión** (22-09-2026) |

Cierra la pertenencia vigente de uno o varios managers **de este equipo**, con motivo, en una sola transacción. Una persona que no pertenece hoy a este equipo hace fallar la operación: al contrario que en la asignación, aquí decir lo que no es cierto sí es un error, porque el administrador cree estar retirando a alguien de un sitio donde no está. El equipo puede estar `INACTIVO`: retirar es la única forma de vaciar un equipo suspendido (`RN-SP-053`) y la que `RN-SP-054` exige antes de eliminarlo.

**Trae consigo la enmienda de `RN-SP-055` a `RF-SP-029` y `RF-SP-031`**: retirar el rol de manager o eliminar a la persona cierra su pertenencia en la misma transacción. Se declara aquí y no en `RF-SP-069` porque es un retiro que ocurre por otra causa, y su tripleta es la que toca los dos casos de uso construidos, en el orden del Art. I.7: primero sus tripletas, después su código.

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

**Publica dos lecturas hacia otros módulos desde el 27-08-2026** (**D-25**, `architecture.md` §15.2): el **catálogo de membresías** —si una existe y qué nivel tiene— y el **catálogo de monedas** —si existe, si está activa y cuántos decimales declara—. Las consume `PM`, y las escribió `RF-PM-001`: `SP` no gana ningún requerimiento por ello, porque ningún actor pide «publicar una interfaz» como comportamiento.

**Sigue sin depender de nadie.** Publicar no es depender: la dirección de la dependencia es `PM` → `SP`, y una regla de ArchUnit impide que `modules/products` importe repositorios o entidades de `modules/system`.

**Y desde el 17-09-2026 publica su primera ESCRITURA** (**D-26**, cerrada ese día por el responsable del proyecto; [`architecture.md` §15.2](../architecture.md)): **`MembershipGrant`**, «conceder el nivel comprado», que `MV` invoca al confirmar el pago de una venta con un upgrade (`RF-MV-003`, `RN-MV-020`). Lo que hace es exactamente lo que `RF-SP-032` hace a mano y con las mismas reglas: **cierra la vigente e inserta la nueva** (`RN-SP-014`), con la vigencia contada desde el instante que la orden indica y sin fecha de fin cuando lo comprado no caduca, deja el suelo intacto (`RN-SP-018`) y escribe su asiento en `audit_change_log` como `user_memberships`. **Lo que no hace es decidir si conceder**: eso lo decide `MV` antes de llamar (`RN-MV-029`), y por eso la operación no compara niveles. Corre **en la transacción de quien la llama**: si conceder falla, la venta no se confirma, y al revés. La escribe `RF-MV-003` en paquetes de `SP`, por lo mismo que las lecturas: ningún actor pide «publicar una escritura» como comportamiento.

**Y desde el 21-09-2026 publica el ALCANCE COMERCIAL de una persona** (**D-22**, [`architecture.md` §15.2](../architecture.md)): **`CommercialReach`**, «hasta dónde llega esta persona» —todo si porta un rol `FUNCIONARIO`; su red en profundidad, con ella dentro, si porta uno `VENDEDOR` (§10.7, la relación vigente, recursiva); ella misma si es `CONSUMIDOR` o no porta ninguno—. Lo pide `MV` para `RF-MV-015` (`RN-MV-031`: el vendedor ve las ventas de toda su red) y lo escribe esa tripleta en paquetes de `SP`, como las demás. **Lo que decide que viva aquí**: quién manda a quién y qué tipo es cada rol son de `SP`, y una segunda definición de «mi red» en `MV` divergiría de la de `RF-SP-057` sin fallar. `SP` no gana ningún requerimiento por ello.

**Y desde el 18-09-2026 publica la primera lectura que responde sobre un PERMISO y no sobre un dato** (**D-25**, [`architecture.md` §15.2](../architecture.md)): **`PermissionHolderLookup`**, «¿esta persona porta este permiso?», un booleano sobre un código. La pide `AC` —el instructor de un curso tiene que portar `courses:teach` (`RN-AC-006`), y `AC` no puede leer `user_roles` ni `role_permissions`— y la escribió `RF-AC-008` · `T-03` en paquetes de `SP`, como las demás. **Lo que decide es qué NO publica: la lista.** Dar «los permisos de esta persona» a otro módulo es darle con qué reconstruir fuera de `SP` la autorización que es suya, y con qué equivocarse al hacerlo; responder sí o no sobre un código no da nada más que lo que se preguntó. **«Portar» significa lo mismo que para `RN-SEG-010`**: por un rol vivo y `ACTIVO` de una persona no retirada, y el predicado es **una sola constante** (`JpaEffectivePermissions.ROLES_QUE_CONCEDEN`) que las dos consultas comparten, con una prueba de cada lado que las mantiene iguales — un rol inactivo no puede conceder en una y no en la otra sin que algo falle. Persona inexistente, retirada, o permiso inexistente responden falso sin distinguirse: quien pregunta ya comprobó la existencia por `UserCatalog`.

## 9. API

| Método | Ruta | Requerimiento | Permiso |
|---|---|---|---|
| `POST` | `/api/v1/roles` | `RF-SP-001` | `roles:create` |
| `GET` | `/api/v1/roles` | `RF-SP-002` | `roles:list` |
| `GET` | `/api/v1/roles/{id}` | `RF-SP-003` | `roles:read` |
| `PATCH` | `/api/v1/roles/{id}` | `RF-SP-004` | `roles:update` |
| `POST` | `/api/v1/roles/{id}/permissions` | `RF-SP-005` | `roles:assign-permissions` |
| `DELETE` | `/api/v1/roles/{id}/permissions` | `RF-SP-006` | `roles:revoke-permissions` |
| `PATCH` | `/api/v1/roles/{id}/status` | `RF-SP-007` | `roles:change-status` |
| `PATCH` | `/api/v1/roles/{id}/parent` | `RF-SP-008` | `roles:assign-parent` |
| `DELETE` | `/api/v1/roles/{id}` | `RF-SP-009` | `roles:delete` |
| `GET` | `/api/v1/permissions` | `RF-SP-010` | `permissions:list` |
| `GET` | `/api/v1/audit/changes` | `RF-SP-011` | `audit:read-changes` |
| `GET` | `/api/v1/audit/deletions` | `RF-SP-012` | `audit:read-deletions` |
| `GET` | `/api/v1/audit/errors` | `RF-SP-013` | `audit:read-errors` |
| `GET` | `/api/v1/audit/security` | `RF-SP-014` | `audit:read-security` |
| `GET` | `/api/v1/permissions/{id}` | `RF-SP-015` | `permissions:read` |
| `POST` | `/api/v1/memberships` | `RF-SP-016` | `memberships:create` |
| `GET` | `/api/v1/memberships` | `RF-SP-017` | `memberships:list` |
| `GET` | `/api/v1/memberships/{id}` | `RF-SP-018` | `memberships:read` |
| `GET` | `/api/v1/currencies` | `RF-SP-019` | `currencies:read` |
| `POST` | `/api/v1/countries` | `RF-SP-020` | `countries:create` |
| `GET` | `/api/v1/countries` | `RF-SP-021` | **Ninguno: público** |
| `PATCH` | `/api/v1/countries/{id}/status` | `RF-SP-022` | `countries:update` |
| `PATCH` | `/api/v1/currencies/{id}/status` | `RF-SP-023` | `currencies:update` |
| `POST` | `/api/v1/users` | `RF-SP-024` | `users:create` |
| `GET` | `/api/v1/users` | `RF-SP-025` | `users:list` |
| `GET` | `/api/v1/users/{id}` | `RF-SP-026` | `users:read` |
| `PATCH` | `/api/v1/users/{id}` | `RF-SP-027` | `users:update` |
| `PATCH` | `/api/v1/users/{id}/status` | `RF-SP-028` | `users:change-status` |
| `POST` | `/api/v1/users/{id}/deletion` | `RF-SP-029` | `users:delete` |
| `POST` | `/api/v1/users/{id}/roles` | `RF-SP-030` | `users:assign-roles` |
| `POST` | `/api/v1/users/{id}/roles/revocations` | `RF-SP-031` | `users:revoke-roles` |
| `PUT` | `/api/v1/users/{id}/membership` | `RF-SP-032` | `users:assign-membership` |
| `DELETE` | `/api/v1/users/{id}/membership` | `RF-SP-033` | `users:revoke-membership` |
| `POST` | `/api/v1/auth/login` | `RF-SP-034` | — |
| `POST` | `/api/v1/auth/refresh` | `RF-SP-035` | — |
| `POST` | `/api/v1/auth/logout` | `RF-SP-036` | — (público, autorizado por el refresh token) |
| `POST` | `/api/v1/auth/password` | `RF-SP-037` | `users:change-own-password` |
| `POST` | `/api/v1/users/{id}/password-reset` | `RF-SP-038` | `users:reset-password` |
| `POST` | `/api/v1/auth/password-recovery` | `RF-SP-040` | — (público) |
| `POST` | `/api/v1/auth/password-recovery/confirmation` | `RF-SP-040` | — (público, autorizado por el permiso temporal) |
| `GET` | `/api/v1/users/me` | `RF-SP-039` | `users:read-own-profile` |
| `PATCH` | `/api/v1/users/{id}/supervisor` | `RF-SP-041` | `users:assign-supervisor` |
| `GET` | `/api/v1/users/{id}/team` | `RF-SP-042` | `users:read-team` |
| `GET` | `/api/v1/users/me/sellers` | `RF-SP-059` | `users:read-own-sellers` |
| `GET` | `/api/v1/users/{id}/sellers` | `RF-SP-059` | `users:read-sellers` |
| `GET` | `/api/v1/users/me/clients` | `RF-SP-061` | `users:read-own-clients` |
| `GET` | `/api/v1/users/{id}/clients` | `RF-SP-061` | `users:read-clients` |
| `PATCH` | `/api/v1/users/me` | `RF-SP-044` | `users:update-own-profile` |
| `POST` | `/api/v1/auth/registration` | `RF-SP-045` | **Público** |
| `POST` | `/api/v1/exchange-rates` | `RF-SP-047` | `exchange-rates:create` |
| `GET` | `/api/v1/exchange-rates` | `RF-SP-048` | `exchange-rates:read` |
| `PATCH` | `/api/v1/exchange-rates/{id}` | `RF-SP-049` | `exchange-rates:update` |
| `POST` | `/api/v1/exchange-rates/{id}/deletion` | `RF-SP-050` | `exchange-rates:delete` |
| `GET` | `/api/v1/document-types` | `RF-SP-051` | **Ninguno: público** |
| `GET` | `/api/v1/brokers` | `RF-SP-052` | **Ninguno: público** |
| `GET` | `/api/v1/users/{id}/broker-accounts` | `RF-SP-055` | `broker-accounts:read-team-member` (alcance: superior vigente, o `broker-accounts:read`) |
| `GET` | `/api/v1/users/me/team/broker-accounts` | `RF-SP-056` | `broker-accounts:read-own-team` |
| `GET` | `/api/v1/broker-accounts` | `RF-SP-057` | `broker-accounts:read` |
| `GET` | `/api/v1/broker-accounts/indicators` | `RF-SP-058` | `broker-accounts:read-indicators` |
| `POST` | `/api/v1/teams` | `RF-SP-063` | `teams:create` |
| `GET` | `/api/v1/teams` | `RF-SP-064` | `teams:list` |
| `GET` | `/api/v1/teams/{id}` | `RF-SP-065` | `teams:read` |
| `PATCH` | `/api/v1/teams/{id}` | `RF-SP-066` | `teams:update` |
| `PATCH` | `/api/v1/teams/{id}/status` | `RF-SP-067` | `teams:change-status` |
| `POST` | `/api/v1/teams/{id}/deletion` | `RF-SP-068` | `teams:delete` |
| `POST` | `/api/v1/teams/{id}/members` | `RF-SP-069` | `teams:assign-members` |
| `POST` | `/api/v1/teams/{id}/members/removals` | `RF-SP-070` | `teams:remove-members` |

Rutas propuestas. El contrato exacto de cada una se fija en el `plan.md` de su tripleta.

## 10. Persistencia

| Entidad | Descripción | Dueño |
|---|---|---|
| `permissions` | Catálogo de permisos `recurso:acción` | `SP` |
| `roles` | Roles, su estado y su rol padre | `SP` |
| `role_permissions` | Permisos declarados por cada rol | `SP` |
| `memberships` | Niveles de acceso del consumidor | `SP` |
| `currencies` | Catálogo de monedas | `SP` |
| `exchange_rates` | A cuánto se cambia una moneda por otra, con su vigencia | `SP` |
| `countries` | Catálogo de países | `SP` |
| `document_types` | Catálogo de documentos de identidad admitidos — **solo los de mayor de edad** | `SP` |
| `users` | Personas que acceden al sistema, con su credencial y su estado | `SP` |
| `user_roles` | Roles asignados a cada usuario | `SP` |
| `user_memberships` | Historial de membresías de cada usuario — **de todos, no solo de los consumidores** (`RN-SP-018`); la abierta es la actual | `SP` |
| `user_supervisors` | Superior comercial de cada vendedor, con su historial — **solo fuerza comercial** desde el 18-09-2026 (`RN-SP-028`) | `SP` |
| `client_sellers` | Los vendedores de cada cliente: el principal —`REGISTRO`, quien lo registró, inmutable— y los vinculados por hotlink (`RN-SP-049`); **la única tabla donde un cliente se relaciona con un vendedor** desde el 18-09-2026 | `SP` |
| `refresh_tokens` | Sesiones revocables | `SP` |
| `password_reset_permits` | Permisos de un solo uso para recuperar la contraseña olvidada | `SP` |
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
| `color` | `varchar(6)` | No | No | No | — | — |
| `parent_membership_id` | `uuid` | No | Sí | Sí | — | `memberships` |
| `level` | `smallint` | No | No | No | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

`parent_membership_id` apunta a la membresía **de mayor nivel** y es nulo solo en la superior (`RN-SP-006`). `level` materializa el orden para poder consultarlo y ordenarlo sin recorrer la cadena; se recalcula al insertar una membresía nueva (`RN-SP-007`).

`color` es el color con el que el frontend pinta el nivel: **seis dígitos hexadecimales, sin `#` y en mayúsculas** (`RN-SP-024`). El `#` no se guarda porque es notación de CSS y no parte del valor; devolverlo obligaría a todo consumidor que no sea una hoja de estilos —una app móvil, un informe— a quitárselo. La normalización a mayúsculas ocurre **al escribir**, de modo que `1e88e5` y `1E88E5` no puedan convivir como dos filas distintas y la unicidad signifique algo.

Se declara `varchar(6)` y no `char(6)` porque `char(n)` **rellena con espacios**: un valor de cinco dígitos quedaría almacenado con seis caracteres, y toda la comprobación pasaría a depender de la expresión regular en lugar de apoyarse también en la longitud.

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
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

`code` sigue ISO 4217 (`USD`). Se puebla por migración y no se modifica por API (`RN-SP-010`), salvo `is_active` a través de `RF-SP-023`.

`decimal_places` condiciona el redondeo de todo cálculo financiero y no siempre vale dos: hay monedas sin fracción, en las que cero es un valor legítimo. `is_default` marca la moneda con la que opera el sistema, y **exactamente una fila la lleva a `true`**: la restricción se declara en el esquema con un índice único parcial, no solo en el dominio (Art. V.6). La moneda por defecto no puede desactivarse.

### 10.6 Campos principales — `countries`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `code` | `char(3)` | No | No | No | — | — |
| `name` | `varchar(100)` | No | No | No | — | — |
| `is_active` | `boolean` | No | No | No | `true` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

`updated_at` se incorporó el 21-08-2026 al aprobar el `plan.md` de `RF-SP-020`: el Art. V.7 lo obliga en toda tabla de negocio, y aquí además hay algo que modificar —`RF-SP-022` cambia `is_active`—, de modo que sin la columna no habría forma de saber cuándo se retiró un país de la circulación salvo recorriendo la auditoría.

`code` sigue ISO 3166-1 alfa-3 (`COL`, `USA`). No se edita ni elimina (`RN-SP-009`); lo único modificable es `is_active`, a través de `RF-SP-022`. El catálogo **no se siembra** con la lista internacional completa: los países se dan de alta por la API a medida que la plataforma llega a ellos.

!!! important "El catálogo deja de nacer vacío: `RN-SP-034` obliga a sembrar **una** fila, Colombia"

    Hasta el 07-09-2026 este catálogo arrancaba sin ninguna fila, y era coherente: nadie dependía de él para existir. `RN-SP-034` lo rompe — `users.country_id` es `NOT NULL`, y **`V22` siembra un superadministrador** que hay que rellenar con algo. Un catálogo vacío haría fallar la migración en toda base, incluidas las de las pruebas de integración.

    Se siembra **Colombia** (`COL`), por decisión del responsable del proyecto, con identificador **UUID v7 literal** para que sea el mismo en todos los entornos (Art. V.11). Mismo criterio que `V15` con `USD`: **una sola fila, la del mercado desde el que se opera**, y ninguna otra «por si acaso» — un país que existe en el catálogo puede seleccionarse, y ofrecer uno en el que no se opera es peor que no tenerlo.

    **Y la elección no tiene vuelta atrás**: `RN-SP-009` no admite editar ni borrar un país, de modo que un código o un nombre mal sembrados solo se pueden **desactivar**, nunca corregir. Es la misma irreversibilidad que obligó a `V42` a levantar excepción en lugar de adivinar equivalencias.

`countries` recibe con `RN-SP-034` su **segunda clave foránea entrante**, y la primera que viene de una persona: hasta el 04-09-2026 era una isla, `payment_method_exclusions` la sacó de esa condición (`V55`) y ahora lo hace `users.country_id`. Con las dos juntas el sistema sabe **dónde no vale un medio de pago** y **dónde está quien va a pagar**, que es la asimetría que [`modelo-datos.md` §6](../modelo-datos.md) tenía anotada como pendiente.

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

Es una de las dos tablas del módulo que **relacionan dos usuarios entre sí** —la otra es `client_sellers`, §10.19—, y la primera pieza de la red comercial ([`modules.md` §6](../modules.md)).

!!! important "Entre el 01-09-2026 y el 18-09-2026 esta tabla contuvo también a los clientes"

    **Ya no.** Desde el 18-09-2026, por decisión del responsable del proyecto (`RN-SP-028` revertida), el cliente vive en `client_sellers` (§10.19) y esta tabla vuelve a relacionar **vendedores entre sí** y nada más; la migración de `RF-SP-059` movió las filas y borró las de clientes, vigentes y cerradas. Lo que sigue es la historia de por qué estuvo aquí y de lo que costó, y se conserva porque explica la forma que tienen `RF-SP-042` y `RN-SP-022`.

    Hasta el 01-09-2026 relacionaba **vendedores entre sí** y nada más, y la semilla de desarrollo lo dejaba escrito: «`ADMIN` y `CLIENTE` quedan fuera porque no son vendedores». **La semilla se corrigió el 04-09-2026**, tres días después que el diseño: hasta entonces no había **ni una cartera** en desarrollo, de modo que la mitad comercial de una venta no se podía ver funcionando en local. Ahora cuelga a los tres clientes a **profundidad distinta** —de un agente, de un director y de un manager—, que es lo que hace observable la rama de consumidor de `RN-SP-020`. `ADMIN` siguió fuera hasta el 10-09-2026 — ver la nota siguiente.

    Durante esos diecisiete días, el cliente que se registraba por un enlace **colgaba de su vendedor en esta misma tabla**, con el cliente en `user_id` y el vendedor en `supervisor_id`. Lo que la fila significaba cambiaba según quién fuera el subordinado —**«reporta a»** entre vendedores, **«fue traído por»** cuando era un cliente— y `RN-SP-020` lo distinguía con una rama de consumidor que ya no existe.

    **Lo que se gana es que el árbol comercial esté completo en un solo sitio.** Subir de un cliente hasta el manager que cobra por él es un recorrido de esta tabla, y no un join con una segunda estructura y un caso especial en la hoja — que es la forma que una liquidación multinivel necesita.

    **Lo que cuesta está en `RN-SP-022`**, y no es menor: «tener personas a cargo» pasa a incluir la cartera de clientes, de modo que retirar a un agente exige reasignarla. Y `RF-SP-042` —consultar el equipo a cargo— empieza a devolver clientes junto al equipo. **Que se distinguieran «sin cambiar el contrato» fue una suposición, y era falsa**: aquella respuesta llevaba un solo rol y solo de clasificación `VENDEDOR`, de modo que la cartera llegaba con el rol **en nulo** y un cliente era indistinguible de un vendedor sin rol. Se corrigió el **10-09-2026**, cuando cada persona pasó a llevar **la lista completa de sus roles** y el equipo pasó a filtrarse por ellos.

!!! warning "La semilla de desarrollo cuelga a los funcionarios, y `RF-SP-041` no sabría hacerlo"

    Desde el **10-09-2026**, y por decisión del responsable del proyecto, la semilla de desarrollo cuelga también **`admin1` de `superadmin`** y **los tres managers de `admin1`**, de modo que el árbol de personas llega de punta a punta: `superadmin ← admin1 ← manager ← director ← agente`, con la cartera de clientes en las hojas. Se pidió para **poder ver la estructura completa** en local, que hasta entonces nacía partida en dos: la fuerza comercial colgaba de tres managers que no colgaban de nadie, y `admin1` quedaba suelto.

    **Son cuatro filas que la API rechaza, y quedan declaradas como deuda.** `RF-SP-041` devolvería `409` en los dos casos: **`VAL-004`** para el manager —`RN-SP-019` lo exceptúa por ser la cúspide de la fuerza comercial, ya que su rol padre `ADMIN` no es `VENDEDOR`— y **`VAL-003`** para el administrador, que no pertenece a la fuerza comercial y por tanto no tiene superior que asignar. `RN-SP-020` tampoco las cubre: exige un subordinado vendedor, y un `FUNCIONARIO` no lo es.

    **Lo que no son es incoherentes**, y por eso la deuda es de alcance y no de diseño: `SUPERADMIN → ADMIN → MANAGER` es exactamente el parentesco que declara §4.1, el mismo que `RN-SP-020` exige entre vendedores. Lo que falta es **decidir si la estructura de personas deja de ser comercial para ser la jerarquía completa** — y entonces `RN-SP-019` y `RN-SP-020` se enmiendan, `SUPERADMIN` pasa a ser la única cúspide y `CommercialStructure` deja de filtrar por clasificación `VENDEDOR`.

    **Mientras no se decida, esas filas viven solo en la semilla.** Ninguna regla, ningún requerimiento y ninguna prueba de otro caso de uso deben apoyarse en ellas. `RF-SP-042` las devolverá —lee la tabla sin preguntar por el tipo de rol—, y eso no las convierte en contrato. Lo que sí cambia en desarrollo es **quién es la cúspide**: la única persona sin superior pasa a ser `superadmin`, de modo que la omisión de `supervisor` que `CA-SP-445` define sigue siendo observable, pero en **una** persona y no en cuatro.

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
| `uq_memberships_parent` | `memberships(parent_membership_id)` **con `NULLS NOT DISTINCT` y diferida** — garantiza una sola hija por membresía **y una sola membresía superior**. La cláusula se añadió el 24-08-2026 al implementar `RF-SP-016`: escrita como restricción única corriente, PostgreSQL trata los nulos como distintos y admitiría varias filas sin superior, es decir, varias cimas — justo la bifurcación que esta restricción existe para impedir, y en el peor sitio |
| `ix_roles_busqueda` | Índice de trigramas sobre `roles` para la búsqueda insensible a mayúsculas y acentos. Requiere las extensiones `unaccent` y `pg_trgm`: la coincidencia es por contención, y un índice B-tree corriente no la sostiene |
| `ix_countries_busqueda` | Ídem sobre `countries` |
| `uq_memberships_code` | `memberships(code)` |
| `uq_memberships_name` | **Índice único funcional**: `memberships (f_unaccent(lower(name)))` — no sobre `name` literal. Añadido el 24-08-2026 al implementar `RF-SP-016`. `RN-SP-008` no admite edición, de modo que `Plata` y `plata` conviviendo serían dos niveles indistinguibles **para siempre**. Mismo criterio que `uq_countries_name`, y la misma asimetría deliberada con `uq_roles_name` |
| `ck_memberships_code_format` | `memberships(code ~ '^[A-Z][A-Z0-9_]*$')` — `VAL-006`. Mismo formato que `roles`, y en el esquema por la misma razón: un código en minúsculas quedaría sin corrección posible por la API |
| `uq_memberships_level` | `memberships(level)` **diferida** — en un orden lineal cada posición es única. Diferida porque `SET level = level + 1` colisiona consigo mismo dentro de la misma sentencia |
| `ck_memberships_level_positive` | `memberships(level >= 1)` — el nivel `1` es la cima |
| `ck_memberships_parent_not_self` | `memberships(parent_membership_id <> id)` |
| `ck_memberships_color_format` | `memberships(color ~ '^[0-9A-F]{6}$')` — seis dígitos hexadecimales en mayúsculas, sin `#` (`RN-SP-024`). La normalización a mayúsculas la hace el dominio **antes** de escribir; la restricción rechaza lo que llegue por cualquier otra vía, incluida una migración |
| `uq_memberships_color` | `memberships(color)` — dos niveles del mismo color son indistinguibles justo en lo que el campo existe para distinguir (`RN-SP-024`). Atrapa el valor repetido y **no** dos tonos que un ojo humano no separa |
| `uq_currencies_code` | `currencies(code)` |
| `uq_currencies_name` | `currencies(name)` — dos filas con el mismo nombre y distinto código serían indistinguibles en cualquier selector |
| `ck_currencies_code_format` | `currencies(code ~ '^[A-Z]{3}$')` — ISO 4217. En el esquema y no en el DTO, porque el único punto de entrada de esta tabla es una migración |
| `ck_currencies_decimal_places` | `currencies(decimal_places BETWEEN 0 AND 4)` — cero es legítimo y cuatro es el máximo de ISO 4217. Sin cota, una errata de siembra produce redondeos silenciosamente erróneos en todo cálculo posterior |
| `uq_currencies_single_default` | **Índice único parcial**: `currencies ((is_default)) WHERE is_default` — `CA-SP-169`. Misma construcción que `uq_roles_single_root` |
| `ck_currencies_default_active` | `currencies(NOT is_default OR is_active)` — dar de baja la moneda con la que opera el sistema dejaría los importes sin referencia válida. Hace que `RF-SP-023` nazca con la mitad de su trabajo hecho, y protege también contra una migración descuidada |
| `uq_countries_code` | `countries(code)` |
| `uq_countries_name` | **Índice único funcional**: `countries (f_unaccent(lower(name)))` — no sobre `name` literal. `RN-SP-009` no admite edición, de modo que `Panamá` y `Panama` conviviendo serían dos opciones indistinguibles **para siempre**. Es la asimetría deliberada con `uq_roles_name`, que sí es literal porque allí `RF-SP-004` permite renombrar |
| `ck_countries_code_format` | `countries(code ~ '^[A-Z]{3}$')` — `char(3)` acota la longitud pero admitiría `1`, `-` o un espacio de relleno. **Tres letras y no dos** desde la v1.27.0: el código es ISO 3166-1 alfa-3, como el de una moneda lo es de ISO 4217 |
| `ck_countries_name_not_blank` | `countries(length(btrim(name)) > 0)` |
| `uq_users_username` | **Índice único funcional total**: `users (lower(username))` — `RN-SP-016`. Va sobre la forma en minúsculas para que `JPerez` y `jperez` no puedan coexistir, y es **total** y no parcial porque eliminar a alguien **no libera** su nombre de usuario. Es la asimetría deliberada con `uq_roles_code`, que sí es parcial. **Obliga a `RF-SP-034` a comparar el nombre de usuario sin distinguir mayúsculas** |
| `uq_users_email` | `UNIQUE (email)` — `RN-SP-016`. Restricción corriente y no índice funcional, porque el correo se persiste ya normalizado. Total por el mismo motivo que la anterior |
| `ck_users_email_normalized` | `users(email = lower(btrim(email)))` — sin él, un `INSERT` directo mete `Juan@X.com` y `uq_users_email` deja de significar lo que dice |
| `ck_users_email_format` | `users(email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')` — comprobación de forma mínima; la validación buena está en el DTO |
| `ck_users_username_no_at` | `users(position('@' in username) = 0)` — `VAL-010`. **Es lo que sostiene el inicio de sesión con ambas identidades**: ningún nombre de usuario puede parecerse a un correo |
| `ck_users_username_format` | `users(username ~ '^[A-Za-z0-9._-]{3,50}$')` — sin espacios ni acentos. Un nombre con espacio al final es indistinguible del mismo sin él, y es permanente |
| `ck_users_names_not_blank` | `users(length(btrim(first_name)) > 0 AND length(btrim(last_name)) > 0)` |
| `fk_users_country` | `users(country_id)` → `countries(id)` — `RN-SP-034`. **Simple y no compuesta**, y **sin `ON DELETE`**: la compuesta `(country_id, is_active)` haría fallar `RF-SP-022` sobre un país con usuarios (§5.1), y no hay borrado del que defenderse porque `RN-SP-009` no lo admite |
| `ck_users_status` | `users(status)` en (`ACTIVO`, `INACTIVO`, `BLOQUEADO`, `FTD_PENDIENTE`) — `RN-SP-026`. **`FTD_PENDIENTE` sustituye a `PENDIENTE`**, que estaba declarado y sin usar desde `V18` justamente para que estrenarlo no costara alterar el `CHECK` de una tabla en uso. El cambio es de dominio y **no de datos**: ninguna fila llevaba el valor retirado |
| `pk_user_roles` | **Clave primaria compuesta**: `user_roles(user_id, role_id)` |
| `fk_user_roles_user` | `user_roles(user_id)` → `users(id)`, `ON DELETE RESTRICT` |
| `fk_user_roles_role` | `user_roles(role_id, role_type)` → `roles(id, role_type)`, `ON DELETE RESTRICT` — red debajo de `RN-SEG-008`, y **compuesta desde el 02-09-2026** para que el `role_type` copiado no pueda divergir |
| `uq_roles_id_role_type` | `roles(id, role_type)` — **redundante con la clave primaria**, y esa es toda su función: PostgreSQL exige que el destino de una clave foránea compuesta sea único sobre exactamente esas columnas |
| `uq_user_roles_vendedor` | Único **parcial**: `user_roles(user_id) WHERE role_type = 'VENDEDOR'` — `RN-SP-025` |
| `pk_user_memberships` | **Clave primaria**: `user_memberships(id)` — un identificador propio, porque la tabla guarda **todas** las membresías que alguien tuvo y `user_id` se repite. **Desde el 05-09-2026**; hasta entonces la clave iba sobre `user_id` y era ella quien declaraba `RN-SP-014` |
| `fk_user_memberships_user` | `user_memberships(user_id)` → `users(id)`, `ON DELETE RESTRICT` |
| `fk_user_memberships_membership` | `user_memberships(membership_id)` → `memberships(id)`, `ON DELETE RESTRICT` |
| `ck_user_memberships_periodo` | `user_memberships(ends_at IS NULL OR ends_at > started_at)` |
| `ck_user_memberships_cierre` | `user_memberships(closed_at IS NULL OR closed_at >= started_at)` — un cierre anterior al comienzo no es un caso de negocio. Va con `>=` y no con `>` a propósito: **conceder y cerrar en la misma transacción producen el mismo instante**, y prohibirlo haría fallar la operación normal de `RF-SP-032` |
| `uq_user_memberships_abierta` | **Índice único parcial**: `user_memberships(user_id) WHERE closed_at IS NULL` — **una sola fila abierta por persona**, y es esta la que carga `RN-SP-014` en el día a día. Su otro trabajo es menos visible y no menor: `RF-SP-025` y `RF-SP-026` cruzan esta tabla con un `LEFT JOIN`, y **sin ella ese cruce multiplicaría filas** en cuanto alguien tuviera dos membresías — el listado de usuarios repetiría personas. Obliga a que conceder **cierre siempre** la anterior, incluso si ya estaba vencida |
| `ex_user_memberships_sin_solape` | **`EXCLUDE USING gist`**: `user_id WITH =`, `tstzrange(started_at, COALESCE(LEAST(ends_at, closed_at), 'infinity')) WITH &&` — impide que **dos periodos se pisen**, que es lo que el índice de arriba **no** puede decir: aquel habla de filas abiertas, y dos membresías **cerradas** con fechas solapadas lo satisfarían sin problema. `LEAST` da el fin **real** —vence o la cierran, lo que ocurra antes— y devuelve la que no sea nula; el `COALESCE` cubre el caso en que las dos lo son, que es la membresía indefinida y viva. **Los dos hacen falta y ninguno sobra**: el único parcial no ve el historial y el `EXCLUDE` no ve dos filas abiertas que no se solapan —una vencida y otra nueva—. Mismo patrón y misma extensión que `ex_commission_rates_sin_solape` (`V44`), y por el mismo motivo que allí: comprobarlo con un `SELECT` previo es una carrera |
| `fk_user_supervisors_user` | `user_supervisors(user_id)` → `users(id)` |
| `fk_user_supervisors_supervisor` | `user_supervisors(supervisor_id)` → `users(id)`, con restricción de eliminación — `RN-SP-022` |
| `uq_user_supervisors_vigente` | **Índice único parcial**: `user_supervisors(user_id) WHERE ended_at IS NULL` — `RN-SP-021`. Un solo superior vigente por persona; el historial cerrado no compite por esa unicidad |
| `ck_user_supervisors_no_self` | `user_supervisors(user_id <> supervisor_id)` — nadie está a cargo de sí mismo |
| `ck_user_supervisors_periodo` | `user_supervisors(ended_at IS NULL OR ended_at > started_at)` — un periodo cerrado no puede terminar antes de empezar |
| `ix_users_busqueda` | Índice de trigramas sobre `users`, en **tres expresiones**: `f_unaccent(lower(username))`, `f_unaccent(lower(email))` y `f_unaccent(lower(first_name \|\| ' ' \|\| last_name))`. La tercera es el **nombre completo concatenado**, y sin ella teclear `juan perez` no encuentra a nadie: ese texto no está contenido en ninguna de las dos columnas por separado. Lo declara `RF-SP-025` |
| `uq_document_types_abbreviation` | `document_types(abbreviation)` — la abreviación **es** el código, y por eso no hay una columna `code` además (§10.15) |
| `uq_document_types_name` | **Índice único funcional**: `document_types (f_unaccent(lower(name)))` — mismo criterio que `uq_countries_name`. Dos entradas que solo difieran en acentos serían dos opciones indistinguibles en el selector del alta |
| `uq_brokers_name` | **Índice único funcional**: `brokers (f_unaccent(lower(name)))` — mismo criterio que los países y los tipos de documento. Con el nombre como única columna de negocio, es él quien identifica: sin este índice, «Exness» y «exness» serían dos brokers |
| `uq_user_brokers_cuenta` | `user_brokers(broker_id, external_id)` — **`RN-SP-038`**: una cuenta es de una sola persona. NO es `(user_id, broker_id)`, que prohibiría lo que sí se admite —varias cuentas de la misma persona en el mismo broker— y permitiría lo que no |
| `ix_user_brokers_busqueda` | `user_brokers` **gin de trigramas** sobre `f_unaccent(lower(external_id))` — **`RF-SP-057`**. Las expresiones son **las del predicado**, como en `ix_users_busqueda`: si divergieran, el índice existiría y el planificador no lo usaría nunca, y el defecto no saldría como error sino como una consulta lenta que nadie relaciona con esta migración |
| `ck_user_brokers_status` | `user_brokers(status)` en (`REGISTER`, `FIRST_DEPOSIT`) — **`RN-SP-045`**. Mismo recurso que `ck_users_status` y por el mismo motivo: el conjunto de valores es una regla de negocio, y una columna de texto libre deja entrar `register` en minúscula el día que alguien escriba la fila desde otro sitio |
| `ck_document_types_abbreviation_format` | `document_types(abbreviation ~ '^[A-Z][A-Z0-9]{0,9} `users(country_id)` — filtro por país de `RF-SP-025`. **Total y no parcial**, al revés que los dos índices de abajo: aquellos existen para responder «hoy» sobre tablas con historial, y aquí no hay historial que excluir — el país es una columna del propio agregado (§10.10). Y hace **doble trabajo**: sin él, el `NO ACTION` de `fk_users_country` recorrería `users` entera en cada intento de borrar un país |
| `ix_user_memberships_membership_id` | **Índice parcial**: `user_memberships(membership_id) WHERE closed_at IS NULL` — filtro por membresía de `RF-SP-025`. **Parcial desde el 05-09-2026**: esa consulta pregunta quiénes tienen **hoy** esa membresía, y el historial cerrado nunca forma parte de la respuesta y crecería indefinidamente dentro del índice. Es el mismo criterio con el que `ix_user_supervisors_supervisor_vigente` ya es parcial |
| `ix_user_supervisors_supervisor_vigente` | **Índice parcial**: `user_supervisors(supervisor_id) WHERE ended_at IS NULL` — responde «¿quién está a cargo de esta persona **hoy**?», que es lo que preguntan `RN-SP-022` y `RF-SP-042`. Parcial y no total porque el historial cerrado nunca forma parte de esa respuesta y crecería indefinidamente dentro del índice. Lo declara `RF-SP-028`, y **sustituye al nombre `ix_user_supervisors_supervisor_id`** que el plan de `RF-SP-024` había anticipado: aquel describía un índice sobre una columna, y este lleva además una condición |
| `fk_exchange_rates_source` | `exchange_rates.source_currency_id` → `currencies(id)` — `RN-SP-029` |
| `fk_exchange_rates_target` | `exchange_rates.target_currency_id` → `currencies(id)` — `RN-SP-029` |
| `ck_exchange_rates_monedas_distintas` | `source_currency_id <> target_currency_id` — `RN-SP-029`. Una tasa de una moneda a sí misma no expresa ningún cambio |
| `ck_exchange_rates_price_positive` | `price > 0` — `RN-SP-030` |
| `ck_exchange_rates_vigencia` | `valid_to IS NULL OR valid_to >= valid_from` — `RN-SP-031`. La rama `IS NULL` va **delante y explícita**: un `CHECK` que evalúa a `NULL` **acepta** la fila, y sin ella toda tasa vitalicia pasaría sin comprobarse |
| `uq_exchange_rates_vigente` | **`EXCLUDE USING gist`** sobre las dos monedas `WITH =` y `daterange(valid_from, valid_to, '[]') WITH &&`, **parcial**: `WHERE (is_active AND deleted_at IS NULL)` — `RN-SP-032`. **Un `UNIQUE` no puede expresarlo**: lo que no puede repetirse no es un valor, es un **solapamiento** (§5.2) |
| `uq_teams_name` | **Índice único funcional y parcial**: `teams (f_unaccent(lower(name))) WHERE deleted_at IS NULL` — `RN-SP-050`. Funcional como `uq_brokers_name` porque el nombre es lo único que identifica; parcial como `uq_roles_name` porque la eliminación es lógica y libera el nombre |
| `ck_teams_name_not_blank` | `teams(length(btrim(name)) > 0)` |
| `ck_teams_description_length` | `teams(description IS NULL OR length(description) <= 500)` — mismo límite y mismo motivo que `ck_roles_description_length` |
| `ck_teams_status` | `teams(status)` en (`ACTIVO`, `INACTIVO`) — `RN-SP-053` |
| `ix_teams_busqueda` | `teams` **gin de trigramas** sobre `f_unaccent(lower(name))` — búsqueda de `RF-SP-064`, con **la expresión del predicado** y no otra (la lección de `ix_users_busqueda`) |
| `fk_team_members_team` | `team_members(team_id)` → `teams(id)`, sin `ON DELETE`: la eliminación del equipo es lógica y `RN-SP-054` exige que no queden vigentes |
| `fk_team_members_user` | `team_members(user_id)` → `users(id)`, `ON DELETE RESTRICT` |
| `uq_team_members_vigente` | **Índice único parcial**: `team_members(user_id) WHERE ended_at IS NULL` — `RN-SP-052`. Un solo equipo vigente por manager; el historial cerrado no compite. Misma construcción que `uq_user_supervisors_vigente` |
| `ck_team_members_periodo` | `team_members(ended_at IS NULL OR ended_at > started_at)` |
| `ix_team_members_team_vigente` | **Índice parcial**: `team_members(team_id) WHERE ended_at IS NULL` — «¿quiénes forman este equipo **hoy**?», que es lo que preguntan `RF-SP-064` (el recuento), `RF-SP-065` y `RN-SP-054`. Parcial por el mismo motivo que `ix_user_supervisors_supervisor_vigente` |

!!! important "La unicidad de rol es parcial, no total"

    `RN-SEG-001` exige que el nombre y el código de un rol sean únicos **entre los no eliminados lógicamente**. Una restricción única corriente lo impediría: el nombre de un rol borrado quedaría bloqueado para siempre.

    PostgreSQL lo resuelve con un índice único parcial:

    ```sql
    CREATE UNIQUE INDEX uq_roles_name ON roles (name) WHERE deleted_at IS NULL;
    ```

    Descubrirlo después de tener datos obliga a migrar la restricción con la tabla en uso.

`RN-SEG-006` (ausencia de ciclos), `RN-SEG-003` (contención), `RN-SP-001` (superadministrador siempre presente), `RN-SP-019` (superior comercial obligatorio), `RN-SP-020` (el superior porta el rol padre), `RN-SP-022` (ningún equipo sin superior), `RN-SP-051` (a un equipo solo pertenecen managers), `RN-SP-054` (no se elimina un equipo con managers vigentes) y `RN-SP-055` (la pertenencia sigue al rol) **no** son expresables como restricción declarativa: se verifican en el dominio, y por eso exigen prueba unitaria propia.

Las tres últimas se apoyan además en datos de otras tablas —`user_roles` y `roles`—, de modo que ni siquiera un `CHECK` con subconsulta las sostendría: PostgreSQL no admite subconsultas en `CHECK`.

**`RN-SP-035` se declara ENTERA en el motor, y es la única regla de identidad de la que se puede decir eso.** El par tipo+número lo sostiene `uq_users_document`, la inseparabilidad de los dos campos `ck_users_document_pair`, la normalización `ck_users_document_number_normalized`, y **la validación de mayoría de edad la sostiene `fk_users_document_type` junto con el contenido del catálogo** — que es la parte que no se ve en ninguna restricción y que sin embargo es la que decide. Ningún caso de uso ejecuta una comprobación de edad, porque no hay nada que comprobar: lo que el catálogo no ofrece no se puede escribir.

**`RN-SP-034` está declarada a medias, y es el único caso así de la lista.** Su mitad estructural —todo usuario tiene un país del catálogo— sí vive en el motor, con `NOT NULL` y `fk_users_country`. Su mitad de estado —el país asignado tiene que estar **activo**— no, y no porque no se pueda escribir, sino porque **escribirla rompería `RF-SP-022`**: el detalle está en §5.1.

!!! warning "«Depende de otra tabla» no siempre significa «no se puede declarar», y el 02-09-2026 se comprobó"

    `RN-SP-025` estaba en esta lista por ese motivo, y **salió de ella**: la columna que necesitaba no era una subconsulta sino **una copia atada por una clave foránea compuesta** (§10.11). El dato se trae a la tabla donde la restricción tiene que vivir, y la FK impide que la copia mienta.

    **La condición es que el dato copiado sea inmutable en su origen**, y aquí lo es: `role_type` no se corrige. Donde el origen cambia, el patrón no vale —la FK bloquearía la corrección legítima— y la regla vuelve al dominio.

    Queda anotado que `RN-SP-013` y `RN-SP-018` cumplían esa condición y **podrían haber salido también**. **No se hará (05-09-2026)**: la primera está retirada y la segunda dice ahora que **toda** persona tiene membresía, con lo que la restricción declarable describía una regla que ya no existe.

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
| `country_id` | `uuid` | No | **Sí** | **No** | — | `countries` |
| `password_hash` | `varchar(255)` | No | No | No | — | — |
| `must_change_password` | `boolean` | No | No | No | `false` | — |
| `provisional_password_expires_at` | `timestamptz` | No | No | Sí | — | — |
| `status` | `varchar(20)` | No | No | No | `'ACTIVO'` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

**`username` se persiste tal como se escribió y su unicidad ignora la caja** (§10.8). El correo, en cambio, se persiste ya normalizado —recortado y en minúsculas— y su unicidad es una restricción corriente. La asimetría es deliberada: el nombre de usuario es como la persona aparece en la auditoría durante años, y el correo es una dirección de buzón cuya forma canónica es la minúscula.

**`country_id` es `NOT NULL`, y es la única columna de esta tabla que apunta a un catálogo** (`RN-SP-034`, 07-09-2026). Nace obligatoria y no nulable-hoy-obligatoria-mañana, y esa decisión tiene un precio que se paga una sola vez: la migración que la añade **tiene que rellenar las filas existentes**, y para poder hacerlo **siembra Colombia** en un catálogo que hasta ahora nacía vacío (§10.6). El precio de la alternativa era permanente — una columna nulable obliga a **todo** consumidor futuro a contemplar la ausencia, y `RN-SP-034` dice justamente que esa ausencia no significa nada.

**No lleva `ON DELETE` de ningún tipo**, y no hace falta declararlo: `RN-SP-009` no admite borrar un país, ni lógica ni físicamente, de modo que la fila apuntada **no puede desaparecer**. El comportamiento por omisión —`NO ACTION`— es aquí una red que nadie llegará a tocar, y declarar `RESTRICT` sugeriría que existe un borrado del que defenderse.

**El `deleted_at` de un usuario no libera nada aquí**, al contrario de lo que ocurre con las asignaciones de `RF-SP-029`: el país es una columna del propio agregado, viaja con la fila y sigue diciendo dónde estaba esa persona cuando se la eliminó. Es lo que hace que la instantánea de `audit_deletion_log` sea completa.

!!! important "El esquema inicial no lleva todas las columnas del modelo lógico"

    [`security.md` §9](../security.md) enumera además `failed_attempts`, `locked_until` y `last_login_at`. **Las tres las crea `RF-SP-034`**, que es quien las escribe todas y quien se implementa primero; `RF-SP-028` únicamente las lee y las limpia al reactivar una cuenta. El reparto quedó fijado el 22-08-2026, al aprobarse los planes de `RF-SP-026` y `RF-SP-028`; hasta entonces este documento las atribuía a los dos sin repartirlas.

    No es una omisión: una columna disponible antes de que exista la regla que la gobierna se acaba usando por un camino que nadie diseñó. Añadirla es una migración corriente.

    **`deleted_at` es la excepción, y nace con la tabla en `V18`.** El plan de `RF-SP-024` la había dejado a `RF-SP-029`; se corrigió el 22-08-2026 (Art. I.7) por dos motivos. [`architecture.md` §6.4](../architecture.md) la declara **columna obligatoria de toda tabla de negocio**, junto a `id`, `created_at` y `updated_at`, de modo que su ausencia era una excepción que nadie había declarado. Y **diez requerimientos la leen antes de que `RF-SP-029` la escriba**: `RF-SP-003` y `RF-SP-009` se implementan antes y sus planes ya la daban por existente, y `RF-SP-025` a `RF-SP-027` no serían implementables sin ella. El criterio del párrafo anterior vale para las columnas que **nadie lee** hasta que llega su requerimiento; no vale para esta. Lo que sigue siendo de `RF-SP-029` es **escribirla**: es el único que la pone a un valor distinto de nulo.

### 10.11 Campos principales — `user_roles`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `user_id` | `uuid` | **Sí (compuesta)** | Sí | No | — | `users` |
| `role_id` | `uuid` | **Sí (compuesta)** | Sí | No | — | `roles` |
| `role_type` | `varchar(20)` | No | **Sí (compuesta)** | No | — | `roles` |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |

**No lleva clave sustituta ni `updated_at`**, y ninguna de las dos ausencias contradice el Art. V.7: la unicidad del par es toda la información que la fila contiene, y una asignación no se modifica —se crea y se borra—, de modo que una marca de última modificación sería siempre igual a la de creación. Mismo criterio que `role_permissions` (§10.3).

La crea `RF-SP-024` (`V19__create_user_roles.sql`), porque el alta ya escribe asignaciones. `RF-SP-030` añade `ix_user_roles_role_id`, que es el índice del que dependen `RF-SP-003` y `RF-SP-009` para contar cuántos usuarios porta un rol.

!!! danger "`role_type` es una copia, y existe para que `RN-SP-025` viva en el motor"

    Es el mismo dato que `roles.role_type` y **está desnormalizado a propósito**. Sin él, «una persona no puede portar dos roles de tipo `VENDEDOR`» no se puede declarar: un `CHECK` no consulta otra tabla y un índice único no puede unir `user_roles` con `roles`.

    **Lo que impide que la copia mienta es la clave foránea compuesta** `(role_id, role_type) → roles(id, role_type)`, que exige a su vez un `UNIQUE (id, role_type)` sobre `roles` —**redundante con su clave primaria, y esa es toda su función**—. Es exactamente el patrón que `V49` usó en `product_commission_rates`, y funciona aquí por la misma razón: **`role_type` no es editable** (`RF-SP-004` solo corrige nombre y descripción), de modo que la copia no puede quedarse atrás.

    Sobre esa columna, `uq_user_roles_vendedor` —único parcial sobre `(user_id) WHERE role_type = 'VENDEDOR'`— cierra la regla. **Dos asignaciones simultáneas no pueden colarla**, que es lo que una comprobación en el caso de uso no garantiza: `RN-SP-018` lo intentó y hubo que corregirla el 26-08-2026.

    **Y no lleva `updated_at` por lo mismo que el resto de la tabla**: si `role_type` cambiara, la FK compuesta rechazaría el cambio en `roles` antes de que llegara aquí.

### 10.12 Campos principales — `user_memberships`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `user_id` | `uuid` | No | Sí | No | — | `users` |
| `membership_id` | `uuid` | No | Sí | No | — | `memberships` |
| `started_at` | `timestamptz` | No | No | No | `now()` | — |
| `ends_at` | `timestamptz` | No | No | Sí | — | — |
| `closed_at` | `timestamptz` | No | No | Sí | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

**Es un historial, no una foto.** Cada fila es **una membresía que alguien tuvo**, con su periodo. Conceder otra **cierra la que había e inserta una nueva**; nada se sobrescribe. Hasta el 05-09-2026 la tabla tenía `user_id` como clave primaria y `RF-SP-032` sustituía con un `UPDATE` — el nivel anterior no se podía reconstruir, y esa deuda estaba escrita y aceptada. Dejó de estarlo por decisión del responsable del proyecto.

**`ends_at` y `closed_at` responden preguntas distintas, y por eso son dos columnas.** `ends_at` es **hasta cuándo se pagó** —nula, indefinida—; `closed_at` es **cuándo dejó de ser la actual**. Una membresía de treinta días que se reemplaza el día doce termina con `ends_at` en el día treinta y `closed_at` en el doce, y las dos cosas son ciertas: se pagó un mes y se usó menos de medio. Con una sola columna esa diferencia se pierde, y con ella la respuesta a un reclamo.

**La fila abierta es la que tiene `closed_at` nulo, y hay como mucho una** (`uq_user_memberships_abierta`). «Abierta» no es «vigente»: una membresía vencida **sigue abierta** hasta que se conceda otra o se retire, y por eso conserva su plaza sin conceder nivel. Vigente es lo otro, y se evalúa al consultarla:

```sql
closed_at IS NULL AND (ends_at IS NULL OR ends_at > now())
```

**Conceder cierra SIEMPRE, aunque la anterior ya estuviera vencida.** No es celo: si no se cerrara, quedarían dos filas abiertas, y el `LEFT JOIN` de `RF-SP-025` y `RF-SP-026` empezaría a devolver a la misma persona dos veces.

**Retirar (`RF-SP-033`) cierra la que hay y abre una `BECA`.** Ese mismo día pasó por dos cambios y conviene leer los dos: primero dejó de **borrar** para **cerrar** —el `DELETE` llevaba escrito su motivo, que `RN-SP-015` decía que quien deja de ser consumidor **no tiene** membresía, y el historial lo invierte porque la fila cerrada dice justo que la tuvo y se la quitaron—; y después dejó de **dejar sin nada** para **devolver al suelo**, porque `RN-SP-018` reescrita no admite a nadie sin nivel. `RN-SP-015` quedó retirada por el camino. El cierre sigue sin tocar `ends_at`, y sigue siendo el criterio con el que `endSupervisor` nunca fue un `DELETE` (`V21`).

`RF-SP-024` la creó (`V20__create_user_memberships.sql`) y `V56` la convierte en historial. El alta sigue concediendo la membresía **indefinida**: no admite fecha de fin, que se pone después con `RF-SP-032`.
!!! note "Por qué estas tres secciones van al final y no en su sitio"

    Las subsecciones de §10 se numeran **por orden de incorporación**, no por dependencia. Insertarlas entre las existentes obligaría a renumerar `user_supervisors`, las restricciones y la auditoría, y ocho `plan.md` ya aprobados referencian esos números. La legibilidad del orden vale menos que la estabilidad de las referencias.


### 10.13 Campos principales — `password_reset_permits`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `user_id` | `uuid` | No | Sí | No | — | `users` |
| `permit_hash` | `varchar(255)` | No | No | No | — | — |
| `expires_at` | `timestamptz` | No | No | No | — | — |
| `consumed_at` | `timestamptz` | No | No | Sí | — | — |
| `superseded_at` | `timestamptz` | No | No | Sí | — | — |
| `requested_ip` | `inet` | No | No | Sí | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |

**Solo el hash del permiso, nunca su valor.** Mismo criterio que `refresh_tokens`: quien lea esta tabla no puede tomar la cuenta de nadie. El valor no existe en el servidor más allá del instante en que se entrega al canal de envío.

**Dos columnas de invalidez y no un estado**, porque las dos razones se investigan distinto: `consumed_at` dice que alguien **completó** el flujo y `superseded_at` que **pidió otro**. Una sola columna las haría indistinguibles.

**`uq_password_reset_permits_vigente` —único parcial sobre `user_id` donde ambas son nulas— declara en el esquema que solo vive un permiso a la vez.** Escrito únicamente en el caso de uso, dos solicitudes concurrentes dejarían dos permisos vivos y con ellos **dos vías de entrada abiertas** a la misma cuenta.

**No es una tabla de negocio**: sin `updated_at` ni `deleted_at`. La caducidad se evalúa al consultarla y **ningún proceso la limpia**, igual que `refresh_tokens` antes de su purga — y con el mismo hueco declarado: la purga de permisos consumidos y caducados no tiene requerimiento que la cubra.

`RF-SP-040` la crea (`V37__create_password_reset_permits.sql`). El plan la numeraba `V29`, número que quedó tomado al aplicarse `V13` a `V36` mientras la tripleta esperaba a **D-23**.

### 10.14 Campos principales — `exchange_rates`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `source_currency_id` | `uuid` | No | Sí | No | — | `currencies` |
| `target_currency_id` | `uuid` | No | Sí | No | — | `currencies` |
| `price` | `numeric(18,8)` | No | No | No | — | — |
| `valid_from` | `date` | No | No | No | — | — |
| `valid_to` | `date` | No | No | **Sí** | — | — |
| `is_active` | `boolean` | No | No | No | `true` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

**`numeric(18,8)` y no `numeric(14,4)` como `products.price`**, y la diferencia no es de gusto: una tasa **no es un importe**. Con cuatro decimales, `COP → USD` —del orden de `0,00024`— se guardaría como `0,0002`, y una moneda más devaluada se guardaría como **cero**. Ocho decimales es lo que usan las tesorerías y las pasarelas, y los diez dígitos enteros cubren el otro extremo.

**`valid_from` y `valid_to` son `date` y no `timestamptz`**, igual que en las dos tablas de tasas de comisión. Una tasa de cambio rige **por días**, no por instantes: declararla con hora obligaría a decidir en qué zona se corta el día, que es la decisión que [`architecture.md` §15.1.1](../architecture.md) resolvió para el código de una venta y que aquí no hace falta abrir.

**`is_active` es booleano y no un `varchar` con `CHECK`**, al revés que `products.status`. Aquel se declaró así porque su dominio **es candidato a crecer** —un `BORRADOR` era previsible—; aquí no lo es: la única distinción que un tercer estado expresaría —«programada, aún no rige»— **ya la expresan las fechas**. Es la forma que `currencies` y `countries` usan en este mismo módulo.

**`updated_at` sí, `deleted_at` sí**: es una tabla de negocio que se corrige (`RF-SP-049`) y se retira con motivo (`RN-SP-033`), al revés que `password_reset_permits`.

**Sin columna de motivo y sin columnas de actor**: quién retiró la tasa y por qué residen en `audit_deletion_log`, con la instantánea de la fila (Art. V.7 y V.13).
### 10.15 Campos principales — `document_types`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `abbreviation` | `varchar(10)` | No | No | No | — | — |
| `name` | `varchar(100)` | No | No | No | — | — |
| `is_active` | `boolean` | No | No | No | `true` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

**La abreviación es el identificador estable y el nombre es el que se muestra**, y no hay una tercera columna `code`: la abreviación **es** el código. Añadir las dos cosas daría tres identificadores para un mismo concepto y obligaría a decidir cuál manda. `uq_document_types_abbreviation` la hace única; el nombre lleva su propio índice funcional sobre `f_unaccent(lower(name))`, con el mismo criterio que `countries` (§10.6).

**`is_active` existe y ninguna operación de la API lo escribe**, y conviene justificarlo porque contradice en apariencia el criterio de `RF-SP-024` §2 —«una columna disponible antes de que exista la regla que la gobierna acaba usándose por un camino que nadie diseñó»—. La diferencia es que **esta columna sí se lee desde el primer día**: `RF-SP-051` publica solo los activos. Y sin ella **no hay forma de retirar un tipo de documento**: `fk_users_document_type` impide borrar la fila en cuanto una sola persona la referencie, de modo que la alternativa a `is_active` no es «no tener la columna», es «no poder retirar nunca».

**No lleva `deleted_at`.** Es la misma decisión que `countries` y `currencies` toman, y por el mismo motivo: un tipo de documento no se elimina, se retira de la circulación. Las personas que ya lo declararon lo siguen resolviendo.

**Qué contiene el catálogo es una regla de negocio y no un dato de configuración** (`RN-SP-036`). La siembra lleva **solo documentos de persona mayor de edad**; los que acreditan minoría —tarjeta de identidad, registro civil— **no están y no se añaden**. Esa ausencia es la validación entera.

### 10.16 Campos de contacto e identidad documental — `users`

Añadidos el 08-09-2026 (`RN-SP-035`, `RN-SP-037`), y `company_phone` el 10-09-2026. Se listan aparte de §10.10 para no mezclarlos con las columnas que nacieron con la tabla.

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `document_type_id` | `uuid` | No | **Sí** | Sí | — | `document_types` |
| `document_number` | `varchar(30)` | No | No | Sí | — | — |
| `address_line1` | `varchar(150)` | No | No | Sí | — | — |
| `address_line2` | `varchar(150)` | No | No | Sí | — | — |
| `city` | `varchar(100)` | No | No | Sí | — | — |
| `phone` | `varchar(20)` | No | No | Sí | — | — |
| `company_phone` | `varchar(20)` | No | No | Sí | — | — |

!!! warning "Las siete nacen **nulables en el esquema** y dos de ellas son **obligatorias en la API**, y la asimetría es deliberada"

    `RN-SP-035` y `RN-SP-037` hacen obligatorios el **documento** y el **teléfono personal** al registrar. Aun
    así la columna admite nulo, al revés que `country_id` (§10.10), y el motivo es la diferencia
    entre los dos rellenos.

    Un país de relleno es **una afirmación neutra**: poner «Colombia» al superadministrador no dice
    nada falso sobre nadie. Un **número de documento** de relleno es otra cosa — es una afirmación
    sobre la identidad de una persona, y **cualquier valor que se invente es falso**. Lo mismo el
    teléfono. `V22` siembra un superadministrador que no tiene ni uno ni otro, y la semilla de
    desarrollo crea decenas de personas que tampoco.

    De modo que el esquema **admite la ausencia** —que es la verdad sobre esas filas— y **la API la
    prohíbe** en toda alta nueva. La consecuencia hay que aceptarla entera: **existen y seguirán
    existiendo personas sin documento**, y todo consumidor tiene que contemplarlo. Lo que no puede
    ocurrir es que se creen más.

    **La condición para endurecerlo queda escrita**: el día que ninguna fila tenga el documento nulo
    —porque se completaron todas—, una migración puede poner `NOT NULL`. Antes no, y no por
    prudencia: hacerlo obligaría a inventar los datos que faltan.

**`document_number` se persiste normalizado** —recortado y en mayúsculas—, con el mismo criterio que el correo: los documentos con letra —pasaportes, cédulas de extranjería— se escriben en mayúscula por convención, y guardarlos tal cual haría que `abc123` y `ABC123` fueran dos personas distintas. La unicidad va sobre el **par** `(document_type_id, document_number)` y no sobre el número solo: dos catálogos distintos pueden numerar igual.

**`phone` se persiste normalizado a dígitos con un `+` opcional**, sin espacios, guiones ni paréntesis. No se valida contra el país: eso exigiría un catálogo de prefijos que nadie ha pedido, y una validación a medias rechazaría números legítimos.

**`company_phone` es el teléfono de la empresa**, añadido el **10-09-2026** por decisión del responsable del proyecto, y recibe **exactamente el mismo trato de forma** que el personal: misma normalización, misma restricción `~ '^\+?[0-9]{7,15}$'`, mismo largo. Lo que no comparte es la obligatoriedad — `RN-SP-037` lo deja **opcional**, de modo que aquí el nulo **no es una fila vieja pendiente de completar sino un hecho**: esa persona no tiene teléfono de empresa. Por eso es el único de los cinco campos de contacto que **se puede vaciar de vuelta** una vez informado, y `RF-SP-027` y `RF-SP-044` aceptan su nulo explícito como orden de borrado, igual que hacen con la dirección.

**`RF-SP-045` no lo pide.** El formulario público de registro por enlace da de alta a un cliente, y preguntarle por el teléfono de su empresa sería preguntar por algo que no tiene. Puede añadirlo después desde su propio perfil (`RF-SP-044`), que es donde el dato deja de ser un obstáculo para el alta.

**`address_line2` es el complemento** —apartamento, torre, referencia— y es opcional **por naturaleza y no por transición**, como `company_phone`: una dirección puede no tener complemento, y eso no es un dato que falte.


### 10.17 Campos principales — `brokers`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `name` | `varchar(120)` | No | No | No | — | — |
| `is_active` | `boolean` | No | No | No | `true` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

**Una sola columna de negocio, y por decisión explícita** (08-09-2026): «de momento el nombre». De ahí sale que **el nombre sea la clave de negocio** —único, con la misma intercalación `es-x-icu` que `countries.name` y `document_types.name`— y no un dato descriptivo. Es la diferencia con `currencies`, donde el nombre puede repetirse porque quien identifica es el `code`.

**Lo que eso cuesta, dicho por adelantado**: renombrar un broker cambia su clave de negocio. Mientras nadie referencie brokers por nombre desde fuera —hoy nadie lo hace: `user_brokers` apunta por `id`— el coste es cero. El día que un integrador los pida por nombre, hace falta una columna `code` estable.

**`is_active` existe y ninguna operación de la API lo escribe**, exactamente como en `document_types`: dejar de operar con un broker no puede borrar las cuentas que ya se declararon en él, de modo que la baja es un cambio de estado por migración y nunca un `DELETE`.

### 10.18 Campos principales — `user_brokers`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `user_id` | `uuid` | No | Sí | No | — | `users` |
| `broker_id` | `uuid` | No | Sí | No | — | `brokers` |
| `external_id` | `varchar(80)` | No | No | No | — | — |
| `broker_username` | `varchar(120)` | No | No | **Sí** | — | — |
| `status` | `varchar(20)` | No | No | No | `'REGISTER'` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

**`status` dice en qué punto está la cuenta** (`RN-SP-045`, 10-09-2026): `REGISTER` o `FIRST_DEPOSIT`, con `CHECK` en el motor como `users.status`. **Nace en `REGISTER` y hoy nadie la mueve** —la mueve el webhook de `RF-SP-054`—, y **eso no la convierte en un campo por si acaso**: se lee desde el primer día en `RF-SP-055` y `RF-SP-056`, y el valor que devuelve es cierto. **Los dos valores van en inglés** y el resto de enumerados del sistema no: son el vocabulario del broker que los va a escribir.

**No es el estado de la persona.** `users.status` dice si la cuenta del sistema opera y este dice qué ha pasado en el broker; una persona con dos cuentas puede tenerlas en estados distintos, de modo que **uno no se deriva del otro** — y de ahí que el `FTD_PENDIENTE` del titular no se pueda leer de aquí sin decidir antes qué significa tener una cuenta depositada y otra no.

**`external_id` es el identificador de la persona EN EL BROKER** —el número de cuenta— y es lo único que la persona conoce al declararla.

**`broker_username` admite nulo, y su nulo significa algo** (`RN-SP-040`): «el broker todavía no lo ha confirmado». Lo rellena el webhook de `RF-SP-054`, no el alta. Declararlo obligatorio obligaría a inventar un valor en el alta, y el valor inventado sobreviviría a la confirmación.

**El único es `(broker_id, external_id)` y NO `(user_id, broker_id)`** (`RN-SP-038`). Esa elección es el requerimiento entero:

- **Una persona SÍ puede tener varias cuentas en el mismo broker**, que es lo normal en el ramo.
- **Una cuenta NO puede ser de dos personas.** El segundo que la declare recibe `409`, y quien lo garantiza es el índice —no una comprobación previa—, porque dos altas simultáneas de la misma cuenta pasan cualquier comprobación previa y solo chocan en el motor.

**No lleva `deleted_at`.** Desvincular una cuenta no está decidido todavía (`RF-SP-053` no existe), y añadir la columna hoy sería declarar una operación que nadie implementa — el defecto que `RF-SP-035` dejó escrito con la purga: un campo puesto «por si acaso» que nadie escribe parece una funcionalidad que sí está.
### 10.19 Campos principales — `client_sellers`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `client_id` | `uuid` | Sí | Sí | No | — | `users` |
| `seller_id` | `uuid` | Sí | Sí | No | — | `users` |
| `origin` | `varchar(20)` | No | No | No | — | — |
| `first_movement_id` | `uuid` | No | Sí | Sí | — | `movements` |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |

Diseñada el 16-09-2026 (`RN-SP-049`) y **creada por la migración de `RF-SP-059`** (18-09-2026), que además trae desde `user_supervisors` a los clientes que ya existían; `RF-SP-045` la escribe desde entonces, y `RF-MV-011` y `RF-MV-013` le añadirán las filas por hotlink. **La pareja es la clave**: un vendedor se vincula a un cliente una vez, y la segunda compra por su hotlink no añade fila. **Sin `ended_at` ni `deleted_at`**: el vínculo es un hecho y no se cierra. **Y el principal tampoco cambia**: es la fila `REGISTRO`, y desde el 18-09-2026 no vive en `user_supervisors` ni en ningún otro sitio — el cliente no tiene fila allí (`RN-SP-028`).

`origin` es `REGISTRO` para la fila que nace con `RF-SP-045` —el principal, **una por cliente**— y `HOTLINK` para las que nacen con `RF-MV-011` y `RF-MV-013`. `first_movement_id` apunta a **la venta que creó el vínculo** —la del registro por enlace (`RN-SP-043`) o la primera compra por ese hotlink— y es nula solo para las filas que la migración de `RF-SP-059` trae desde `user_supervisors`, cuya venta de registro no se puede reconstruir con certeza.

**Por qué no va en `user_supervisors`, y por qué el cliente tampoco.** Aquella tabla significa **mando** —un superior vigente, historial con cierre, y la protección de `RN-SP-022`—. Un cliente no manda ni es mandado: ni su registro ni su vínculo de venta se cierran, y ninguno debe disparar esas consecuencias. Tenerlo allí obligaba a que cada consulta del árbol distinguiera «fila de mando» de «fila de cartera», y la que se olvidaba contaba clientes como equipo — que es lo que `RF-SP-042` hizo entre el 01-09-2026 y el 18-09-2026. Por eso ese día la relación entera vino aquí (`RN-SP-028`).

**Restricciones exigidas en el esquema**: clave primaria `(client_id, seller_id)`; `CHECK (client_id <> seller_id)`; `CHECK (origin IN ('REGISTRO', 'HOTLINK'))`; claves foráneas a `users` sin `ON DELETE` y a `movements` sin `ON DELETE`; índice por `seller_id` para «los clientes de un vendedor» (`RF-SP-061`); **índice único parcial sobre `client_id` con `origin = 'REGISTRO'`** (`uq_client_sellers_principal`), porque el principal es uno y la base tiene que saberlo. Que el vendedor **porte un rol `VENDEDOR`** se comprueba en el caso de uso, como en `RN-SP-020`: un CHECK no consulta otra tabla.

### 10.20 Campos principales — `teams`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `name` | `varchar(100)` | No | No | No | — | — |
| `description` | `text` | No | No | Sí | — | — |
| `status` | `varchar(20)` | No | No | No | `ACTIVO` | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |
| `deleted_at` | `timestamptz` | No | No | Sí | — | — |

Diseñada el 21-09-2026 (`RF-SP-063` a `RF-SP-068`) y **la creará `V33`**, la migración de `RF-SP-063`. Tiene la forma de `roles` sin `code`, `role_type`, `parent_role_id` ni `is_system`: un equipo no tiene código porque nada lo referencia por nombre estable (ficha de `RF-SP-063`), no tiene padre porque **los equipos no se anidan** —la jerarquía debajo de cada manager ya la dice `user_supervisors`, y encima de los equipos no hay nada que decir—, y no hay equipos de sistema porque ninguno nace con la plataforma: la semilla de desarrollo creará algunos para poder verlos, y eso no es lo mismo que una fila que el código necesite.

La baja es **lógica** (`deleted_at`), como la de `roles` y al contrario que la de los catálogos con `is_active`: un equipo tiene historial colgando (`team_members`) y borrar la fila lo dejaría apuntando a nada. Y tiene además **estado** (`RN-SP-053`), porque «no recibe a nadie más» y «no existe» son dos cosas distintas que el administrador quiere poder decir por separado —la misma pareja que `roles` tiene con `status` y `deleted_at`.

### 10.21 Campos principales — `team_members`

| Campo | Tipo | PK | FK | Nullable | Default | Entidad relacional |
|---|---|---|---|---|---|---|
| `id` | `uuid` | Sí | No | No | — | — |
| `team_id` | `uuid` | No | Sí | No | — | `teams` |
| `user_id` | `uuid` | No | Sí | No | — | `users` |
| `started_at` | `timestamptz` | No | No | No | `now()` | — |
| `ended_at` | `timestamptz` | No | No | Sí | — | — |
| `created_at` | `timestamptz` | No | No | No | `now()` | — |
| `updated_at` | `timestamptz` | No | No | No | `now()` | — |

La fila se lee «`user_id` pertenece al equipo `team_id` desde `started_at`». Una fila con `ended_at` nulo es la pertenencia **vigente**; las demás son historial cerrado y no se borran (`RN-SP-052`). La creará `V33` junto con `teams`.

**Tiene la forma exacta de `user_supervisors`** (§10.7), y por los mismos motivos: clave sustituta porque el mismo par puede repetirse en el tiempo —un manager vuelve a un equipo en el que ya estuvo— y lo que distingue una fila de otra es el periodo; unicidad **parcial** sobre `user_id` `WHERE ended_at IS NULL`, porque una restricción única corriente haría imposible el historial. Lo que cambia es **qué hay al otro lado**: allí una persona, aquí un equipo. **Se llama `team_members` y no `team_members`**, por decisión del responsable del proyecto (21-09-2026): sus filas son hoy managers y solo managers (`RN-SP-051`), pero «manager» es el **nombre actual** del rango más alto, y el rango más alto puede cambiar —crearse uno por encima, renombrarse—; la regla ya no ata al código (habla de «quien porta el rol vendedor de mayor rango») y el nombre de la tabla, de los permisos y de las rutas tampoco debe hacerlo. «Miembro» significa aquí **quien tiene fila**, que es exactamente la cúspide, y en la prosa de este documento se sigue diciendo «manager» porque hoy lo son.

**Solo la cúspide tiene fila.** Un director o un agente **pertenece** al equipo de su manager, pero no tiene fila aquí: su pertenencia se deriva subiendo por `user_supervisors` hasta quien no tiene superior y mirando la fila vigente de esa persona. Es un recorrido, no un dato, y se decidió así (21-09-2026) para que no pueda contradecirse: si el director tuviera fila propia, alguien podría ponerlo en un equipo distinto del de quien lo manda, y no habría regla que lo impidiera sin volver a recorrer la cadena. Lo que se paga es que «el equipo de este agente» cueste el recorrido —la misma recursiva de `RN-SP-047`, hacia arriba y acotada por `RN-SP-020` a tres niveles hoy—, y es un precio que se paga solo cuando alguien lo pregunte.

!!! warning "Esta tabla tampoco concede acceso a nada"

    Como `user_supervisors` y `client_sellers`: ninguna consulta debe filtrar por equipo para decidir **qué datos ve** alguien. Un equipo agrupa managers para organizarlos y, cuando `CM` lo necesite, para atribuir lo que su red produjo; el modelo de alcance sigue pendiente como **D-22** ([`security.md` §6](../security.md)), y el día que se resuelva será allí donde se decida si «mi equipo» significa algo para la autorización.

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
| 1.18.0 | 22-08-2026 | Consecuencias de aprobar los `plan.md` de **`RF-SP-030` a `RF-SP-033`**, con lo que el submódulo de asignación de roles y membresías queda con su tripleta completa. §9 se corrige: `RF-SP-031` pasa de `DELETE /api/v1/users/{id}/roles` a **`POST /api/v1/users/{id}/roles/revocations`**, porque su lista de roles viaja en el cuerpo y RFC 9110 no le define semántica a un cuerpo en `DELETE` — es la misma corrección que `RF-SP-006` aplicó el 21-08-2026, y el fallo que evita es **silencioso**: un intermediario que descarte el cuerpo produciría un retiro sin roles. `RF-SP-033` **conserva su `DELETE`**: no lleva cuerpo. Tres componentes de dominio quedan declarados **compartidos** y no pueden escribirse dos veces: `RoleGrantPolicy` \(`RN-SEG-010`, con `RF-SP-005` y `RF-SP-024`\), `RootRoleGuard` \(`RN-SP-001` bajo bloqueo sobre el conjunto de portadores activos, con `RF-SP-028` y `RF-SP-029`\) y `CommercialRank` \(el rol vendedor de **mayor rango**, que es lo que distingue un ascenso de una asignación lateral\). Se enmienda `spec.md` de `RF-SP-030` §11 \(Art. I.7\): los cuatro casos condicionales —membresía y superior, faltantes o no admitidos— pasan de `400` a **`422`**, porque ninguno se decide mirando solo el cuerpo y un `400` no debe poder salir del caso de uso. Y se fija que la **definición de «vigente»** de una membresía vive en un solo componente de dominio, que `RF-SP-026` y `RF-SP-031` reutilizan en lugar de reimplantarla como `WHERE`. | Responsable técnico |
| 1.19.0 | 24-08-2026 | Consecuencias de aprobar los `plan.md` de **`RF-SP-034` a `RF-SP-042`**, con lo que **las cuarenta y dos tripletas del módulo quedan completas**. §9 gana la **segunda ruta de `RF-SP-040`** —`POST /api/v1/auth/password-recovery/confirmation`—, que este documento anticipaba desde la v1.14.0: el requerimiento comprende dos operaciones públicas encadenadas con reglas opuestas, y fundirlas en una habría hecho imposible declarar sus excepciones. §10.10 gana **`provisional_password_expires_at`** en `users`: la credencial que fija `RF-SP-038` **caduca**, y sin esa columna una cuenta restablecida y nunca usada queda indefinidamente con una credencial conocida por otra persona sin que nada falle; va con un `CHECK` que la ata a `must_change_password`, porque una credencial provisional sin caducidad es justo la ventana que el requerimiento existe para cerrar. Se corrige además la atribución de tres componentes compartidos en los planes de `RF-SP-030` y `RF-SP-031`, que los declaraban nuevos: `PrivilegeContainment` y `CommercialStructure` los **crea `RF-SP-024`**, y `RootAdministratorPresence` junto con `RootRoleHolderRepository`, `SessionRevoker` y `SupervisedTeamCounter`, **`RF-SP-028`** — que es exactamente el error que esos mismos planes advertían. | Responsable técnico |
| 1.20.0 | 24-08-2026 | **Enmienda de §10.8 al implementar el submódulo de membresías** (`RF-SP-016` · `T-16`). `uq_memberships_parent` gana **`NULLS NOT DISTINCT`** y pasa a declararse **diferida**: escrita como restricción única corriente no garantizaba lo que §10.4 afirma, porque PostgreSQL trata los nulos como distintos y admitiría varias membresías sin superior —varias cimas—, que es la bifurcación que existe para impedir. Se incorporan además cuatro restricciones que el esquema declara y este documento omitía: `uq_memberships_name` sobre `f_unaccent(lower(name))` —`RN-SP-008` no admite edición y `Plata` junto a `plata` convivirían para siempre—, `ck_memberships_code_format`, `uq_memberships_level` diferida y `ck_memberships_level_positive`. | Responsable técnico |
| 1.21.0 | 24-08-2026 | **Enmienda de §10.5 y §10.8 al implementar el catálogo de monedas** (`RF-SP-019` · `T-10`). `currencies` gana **`updated_at`**, que este documento omitía pese al Art. V.7: la omisión venía de suponer que el catálogo no cambia, pero sí cambia —`RF-SP-023` modifica `is_active`— y sin esa marca no habría forma de saber cuándo se dio de baja una moneda sin recorrer la auditoría. §10.8 incorpora las cinco restricciones que el esquema declara y el documento no listaba, entre ellas **`ck_currencies_default_active`**, que impide dejar inactiva la moneda por defecto por cualquiera de sus dos caminos de escritura —la API y una migración—. | Responsable técnico |
| 1.22.0 | 24-08-2026 | **Regla nueva `RN-SP-023`: todo usuario tiene al menos un rol.** El estado «usuario sin ningún rol» deja de existir: una cuenta así puede autenticarse y no puede hacer absolutamente nada, de modo que solo servía para reservar un nombre de usuario y un correo — que `RN-SP-016` no libera nunca. Se exige en **las dos puertas**, porque cerrar solo una deja el invariante sin sostener: `RF-SP-024` pasa `roles` de opcional a **obligatorio** y `RF-SP-031` rechaza quitar el último. **La regla mira la asignación, no el estado del rol**, y esa acotación es deliberada: exigir un rol *activo* haría que desactivar o eliminar un rol \(`RF-SP-007`, `RF-SP-009`\) pudiera violarla **a distancia**, sobre personas que nadie estaba tocando, y dejaría operaciones del catálogo bloqueadas por el estado de terceros; que un rol inactivo no conceda nada ya lo resuelve `RN-SEG-002`. **No es expresable en el esquema** —«al menos una fila en `user_roles`» exige disparador o restricción diferida—, de modo que vive en el dominio y se verifica dentro de la transacción, igual que `RN-SP-001`. **Enmienda seis especificaciones aprobadas** \(Art. I.7\): `RF-SP-024` retira `FA-001`, estrena `EX-008` e invierte `CA-SP-197`; `RF-SP-031` retira `FA-002`, estrena `EX-006` e invierte `CA-SP-269`; y `RF-SP-025`, `RF-SP-026`, `RF-SP-034` y `RF-SP-039` reencuadran su flujo de «sin roles» como «sin **permisos efectivos**», que sigue siendo alcanzable con todos los roles inactivos y es lo único que queda de aquel caso. | Responsable técnico |
| 1.23.0 | 26-08-2026 | **`RF-SP-040` deja de ser el requerimiento sin implementar del módulo**, al cerrarse **D-23** con Resend. §10 gana `password_reset_permits` en la tabla de entidades y **§10.13** con sus campos. Tres cosas quedan escritas ahí porque el esquema las sostiene y el dominio no podría: **solo el hash del permiso**, nunca su valor, igual que `refresh_tokens`; **dos columnas de invalidez y no un estado** —`consumed_at` dice que alguien completó el flujo y `superseded_at` que pidió otro, y una sola columna las haría indistinguibles justo donde la auditoría necesita separarlas—; y el **único parcial `uq_password_reset_permits_vigente`**, que declara en el esquema que solo vive un permiso a la vez: escrito únicamente en el caso de uso, dos solicitudes concurrentes dejarían **dos vías de entrada abiertas** a la misma cuenta. La migración es **`V37` y no `V29`** como decía el plan: ese número quedó tomado al aplicarse `V13` a `V36` mientras la tripleta esperaba la decisión. Se corrige además la numeración duplicada de esta misma tabla: la fila de `RN-SP-023` figuraba como `1.20.0`, número que ya usaba la enmienda de membresías, y pasa a `1.22.0`. | Responsable técnico |
| 1.24.0 | 26-08-2026 | **§6.1 se sincroniza con la matriz de trazabilidad.** Las cuarenta y dos filas figuraban en `Pendiente` —el estado de «registrado, sin `spec.md`»— cuando los cuarenta y dos tienen tripleta aprobada y endpoint funcionando desde el 26-08-2026: pasan a **`En desarrollo`**. La columna llevaba desde el 20-08-2026 sin tocarse, de modo que el catálogo del módulo afirmaba que no existía nada de lo que ya estaba construido. Se añade además de dónde sale ese dato: la **autoridad es [`requirements.md` §4](../requirements.md#4-matriz-de-trazabilidad)** y aquí se copia, para que la próxima divergencia se resuelva sin tener que averiguar cuál de las dos tablas manda. Ninguno pasa a `Implementado`: el Art. XVI exige Pull Request aprobado e integrado y no hay ninguno. | Responsable técnico |
| 1.25.0 | 26-08-2026 | **La membresía gana su color** (`RN-SP-024`), por decisión del responsable del proyecto: seis dígitos **hexadecimales sin `#`**, normalizados a mayúsculas, con los que el frontend pinta el nivel. §10.4 incorpora la columna `color` —`varchar(6)` y no `char(6)`, porque `char(n)` rellena con espacios y dejaría toda la comprobación en manos de la expresión regular— y §10.8 sus dos restricciones: `ck_memberships_color_format` y `uq_memberships_color`. Es **obligatorio**, porque un color opcional obliga al navegador a inventarse uno de reserva, que es exactamente la decisión que este campo saca del frontend; y **único**, porque dos niveles del mismo color son indistinguibles justo en lo que el campo existe para distinguir — con la salvedad escrita de que la unicidad atrapa el valor repetido y no dos tonos que un ojo humano no separa. **Queda declarado un hueco que se acepta a conciencia**: `RN-SP-008` mantiene la membresía inmutable, de modo que **un color mal elegido no se corrige**, y §5.1 fija la condición para reabrirlo como `RF-SP-043`. Enmienda las tripletas de `RF-SP-016`, `RF-SP-017` y `RF-SP-018`, ya aprobadas e implementadas (Art. I.7), y obliga a la migración `V38`. | Responsable técnico |
| 1.26.0 | 27-08-2026 | **`SP` publica sus dos primeras interfaces hacia otro módulo** (§8): el catálogo de membresías y el de monedas, que consume `PM` al registrar un producto. Las escribió `RF-PM-001` y **no abren requerimiento nuevo aquí**: ningún actor pide «publicar una interfaz» como comportamiento observable. Publicar no es depender —la dirección sigue siendo `PM` → `SP`— y una regla de ArchUnit lo ancla. Queda anotado que el submódulo de usuarios ya tenía su propio `MembershipCatalog` interno para lo que él necesita al asignar una membresía: **son la misma lectura declarada dos veces**, y consolidarlas es deuda pendiente que toca código ya implementado. | Responsable técnico |
| 1.27.0 | 28-08-2026 | **El código de un país pasa de dos letras a tres** —ISO 3166-1 **alfa-3**: `COL`, `USA`—, por decisión del responsable del proyecto. §10.6 declara `code` como `char(3)` y §10.8 su `CHECK` sobre `'^[A-Z]{3}$'`. El catálogo queda además alineado con `currencies.code`, que ya era de tres por ISO 4217. **Lo que no es un `ALTER` inofensivo es la migración**: ensanchar `char(2)` a `char(3)` convierte `CO` en `'CO '` —`char` rellena con espacios—, no en `COL`, y esa fila pasa el `NOT NULL` y el `UNIQUE`; solo la caza el `CHECK` nuevo, cuyo mensaje habla de una restricción y no del país. `V42` ensancha, **traduce con una tabla de equivalencias explícita** y **levanta excepción nombrando los códigos que no sabe traducir** en lugar de adivinarlos: `RN-SP-009` prohíbe editar un país, de modo que una equivalencia equivocada no tendría corrección posible. `V16` no se toca, que está aplicada. Enmienda la tripleta de `RF-SP-020`, aprobada e implementada (Art. I.7): §6.1, `EX-002`, `VAL-002` y `CA-SP-135`. | Responsable técnico |
| 1.28.0 | 28-08-2026 | **Nace `RN-SP-025`: una persona no puede portar dos roles de tipo `VENDEDOR`.** La regla llega desde fuera —la pide el módulo `CM`, incorporado el mismo día— y **se registra aquí porque gobierna `RF-SP-030`**, que es la asignación de roles y es de este módulo. El motivo es una ambigüedad que `CM` no puede resolver solo: con dos roles vendedores de tarifas distintas y ninguna tarifa propia, **no hay forma no arbitraria de elegir el porcentaje**. Se descartaron las otras dos salidas —adivinar tomando el mayor, o exigir tarifa propia— porque las dos dejan la ambigüedad viva y la segunda la descubre el día de liquidar. **No se puede declarar en el esquema**: un `CHECK` no consulta otra tabla y un índice único no puede unir `user_roles` con `roles` para mirar `role_type`, de modo que la regla vive en el caso de uso y necesita el **mismo bloqueo pesimista que `RN-SP-018`** —cuya versión sin bloqueo no se sostuvo bajo concurrencia y se corrigió el 26-08-2026—, porque dos asignaciones simultáneas la burlarían igual. Enmienda pendiente en la tripleta de `RF-SP-030` (Art. I.7). | Responsable del proyecto |
| 1.29.0 | 29-08-2026 | **El catálogo sembrado pierde dos roles**: `CONTABILIDAD` y `LIDER_ACADEMICO` se retiran de `V7__seed_system_roles.sql` por decisión del responsable del proyecto, y §4 y §4.1 dejan de listarlos. La migración **se editó en el sitio** en lugar de retirarlos con una `V47` —también por decisión del responsable—, y eso tiene un coste que queda escrito: Flyway valida las migraciones aplicadas por suma de comprobación, de modo que **toda base donde `V7` ya estuviera aplicada falla al arrancar** hasta recrearla o reparar su historial. Retirarlos **no fue quitar dos filas de una tabla**: `CONTABILIDAD` era el único rol sembrado con permisos acotados —un hijo de `ADMIN` con dos permisos y ninguno más—, la forma con la que se verificaban la contención de privilegios (`CreateRoleIT`) y que un token autentique sin conceder de más (`AuthIT`); y `LIDER_ACADEMICO` era el único nombre sembrado con acento, del que dependían la búsqueda sin acentos (`RolesQueryIT`) y el índice de trigramas de `V32`. Las pruebas afectadas **no se repuntaron a otro rol** —repuntar `CONTABILIDAD` a `ADMIN` las habría dejado pasando sin verificar lo que fueron escritas para verificar—: se reescribió `SystemRolesSeedIT` entera y las demás recibieron **fixtures propios**, de modo que ya no dependen de qué siembre el sistema. | Responsable del proyecto |
| 1.30.0 | 31-08-2026 | **Nace `RF-SP-044`: editar el propio perfil**, por decisión del responsable del proyecto. Es la contraparte de escritura de `RF-SP-039`: `RF-SP-027` ya corrige nombre, apellidos y correo, pero exige `users:update` —un permiso de **administración**—, de modo que hoy **quien no administra usuarios no puede corregir un dato suyo mal escrito** y tiene que pedírselo a alguien; concederle ese permiso para que arregle su propio apellido le daría de paso la capacidad de editar el de cualquiera. La decisión que carga el requerimiento es el **correo**: desde `RF-SP-040` es la vía por la que se recupera una contraseña olvidada, de modo que cambiarlo es **cambiar quién puede recuperar la cuenta**, y por eso **exige la contraseña actual en la misma petición** mientras que el nombre y los apellidos no. Una sesión robada no lleva la contraseña, y exigirla convierte el robo de sesión en algo que **caduca** en lugar de en una apropiación permanente. Queda declarado lo que **no** resuelve —la **verificación del correo nuevo**, heredada de `RF-SP-027` y ya sin coartada, porque un correo mal tecleado deja a la persona sin vía de recuperación— y dos decisiones que no se ven en el camino feliz: el fallo de contraseña **no incrementa los intentos fallidos ni bloquea la cuenta**, porque eso permitiría a quien tenga una sesión ajena dejar fuera a la persona legítima; y el **correo repetido sigue exigiendo la contraseña**, porque hacer depender la exigencia de que el valor cambie daría una forma de averiguar el correo vigente probando valores. La ruta es `PATCH /api/v1/users/me` y **no** se añade a las alcanzables con la marca de cambio obligatorio: quien tiene una credencial provisional sin estrenar la estrena antes de tocar su correo, que es precisamente el dato que quien la emitió podría querer cambiarle. | Responsable del proyecto |
| 1.31.0 | 01-09-2026 | **Nace `RF-SP-045`: el registro de clientes por enlace**, por decisión del responsable del proyecto, y con él **el primer endpoint público del sistema que escribe**. Los seis que ya existen o leen o consumen una credencial que el propio sistema emitió; este crea una persona, le concede un rol, le asigna una membresía y la cuelga de un vendedor a petición de alguien que todavía no es nadie. El enlace lleva **el producto y el vendedor que lo generó**: el producto declara la membresía destino (`RN-PM-002`) y su vigencia (`RN-PM-015`), de modo que el rol de consumidor y el nivel se conceden juntos, que es lo que `RN-SP-018` ya exigía. **Ninguno de los dos datos es secreto y no hace falta que lo sea**: el camino de pago exige pasarela —de Finanzas, inexistente— y el gratuito produce una cuenta que autentica y no opera, así que forjar el enlace no consigue nada; por eso el enlace **se compone y no se persiste**. Tres reglas nuevas: **`RN-SP-026`** —la cuenta nace en `FTD_PENDIENTE`—, **`RN-SP-027`** —ningún cliente sin vendedor, porque la atribución vacía produce huérfanos que nadie descubre hasta el día de pagar una comisión— y **`RN-SP-028`**. **La decisión de fondo la tomó el responsable el mismo día: el cliente cuelga de su vendedor en `user_supervisors`, la MISMA estructura de la fuerza comercial, y no en una tabla propia.** Se había propuesto una tabla aparte con el argumento de que `RN-SP-020` exige un rol padre que un `CONSUMIDOR` nunca porta; se descartó, y la regla se enmienda en su lugar — **`RN-SP-020` gana su rama de consumidor**: si el subordinado es cliente, basta con que el superior porte **algún** rol `VENDEDOR`, sin parentesco que comprobar. Lo que se gana es **un solo árbol comercial**: subir de un cliente a su agente, su director y su manager es **un recorrido** en lugar de un join con un caso especial en la hoja, que es exactamente la forma que una liquidación multinivel necesita. **Y `RN-SP-022` se endurece sin tocar su texto**: «personas a cargo» pasa a incluir clientes, de modo que retirar a un agente exige reasignar también su cartera — enmienda de hecho a `RF-SP-028`, `RF-SP-029` y `RF-SP-031`, y `RF-SP-042` empieza a devolver clientes en el equipo a cargo (Art. I.7). **El catálogo de estados cambia** (`ck_users_status`): `PENDIENTE` —declarado y sin usar desde `V18` justamente para esto— es sustituido por **`FTD_PENDIENTE`**, el **primer estado que autentica sin estar `ACTIVO`**, lo que obliga a tocar `puedeEntrar()`, es decir, el camino de acceso de todo el sistema. Queda **enmendado `RF-SP-028`** también en su dominio: debe admitir la salida de `FTD_PENDIENTE` hacia `ACTIVO`, única mientras el webhook del bróker no exista. Y queda declarado lo que **no** resuelve: el camino de pago, la confirmación del depósito y que **la atribución es forjable** — no concede acceso, pero ensucia la base de comisiones, con la condición de reapertura escrita: en cuanto se liquide una comisión sobre una atribución, el enlace tiene que dejar de ser componible. | Responsable del proyecto |
| 1.32.0 | 02-09-2026 | **`RN-SP-025` pasa a vivir en el motor**, y con ello deja de estar solo declarada: nació el 28-08-2026 porque la pidió `CM` y **durante cinco días no la sostuvo nada** — una persona podía portar dos roles vendedores, y lo único que lo delataba era que `SellerRoleCatalog` reventara con `AmbiguousSellerRoleException` en lugar de elegir en silencio. Se decidió declararla **en el esquema y no en el caso de uso** (responsable del proyecto, 02-09-2026), **revirtiendo lo que `modules.md` §5.3 afirmaba**: que no se podía. Sí se puede, con el patrón que `V49` validó cuatro días después de escribir aquella frase — `user_roles` **copia el `role_type`**, una **clave foránea compuesta** `(role_id, role_type) → roles(id, role_type)` impide que la copia diverja, y un **índice único parcial** sobre `(user_id) WHERE role_type = 'VENDEDOR'` cierra la regla. Funciona **porque `role_type` no es editable**: `RF-SP-004` solo corrige nombre y descripción, de modo que la copia no puede quedarse atrás. **Lo que decidió no fue la elegancia sino un precedente**: `RN-SP-018` se comprobaba en el caso de uso, **no se sostuvo bajo concurrencia** y hubo que corregirla el 26-08-2026 — misma tabla, misma clase de comprobación. §10.8 gana la advertencia de que **«depende de otra tabla» no siempre significa «no se puede declarar»**, con la condición que lo permite —que el dato copiado sea inmutable en su origen— y la constancia de que **`RN-SP-013` y `RN-SP-018` la cumplen y podrían salir también** de la lista de reglas no expresables. Y al ir a construirla apareció un choque que nadie había cruzado: **`RF-SP-030` permite ascender asignando el rol nuevo, y la persona queda portando los dos** —está en su §13 y toda la mecánica de `CommercialStructure` lo supone—, de modo que la regla habría hecho fallar `CA-SP-399`, en verde desde el 24-08-2026. **Se resolvió haciendo que asignar SUSTITUYA** el rol vendedor que se porte, en la misma transacción, y no que rechace: retirar antes con `RF-SP-031` deja a la persona sin rol vendedor entre las dos llamadas, y si ese era su único rol **`RN-SP-023` rechaza el retiro** y el ascenso queda imposible. El precio queda escrito: **«asignar» pasa a poder retirar**, y la auditoría debe registrar el rol que entra **y el que sale**. | Responsable del proyecto |
| 1.33.0 | 04-09-2026 | **`RF-SP-039` publica el identificador del actor** (Art. I.7, sobre un requerimiento ya implementado). Lo pidió el frontend como `R-28`, y el motivo por el que no estaba —«quien pregunta ya sabe quién es»— **resultó falso al consumirse**: quien pregunta sabe su nombre de usuario, no su `uuid`, porque ese dato viaja dentro del token y leerlo obligaría al navegador a descomponer un JWT. La consecuencia era concreta y bloqueaba una pantalla entera: `POST /api/v1/movements` exige `clientId`, de modo que **quien compraba para sí mismo no podía decir quién era**, y el único rodeo —buscarse en el listado de usuarios— exige `users:read`, que un cliente no tiene. **No abre alcance**: es el identificador del propio actor, la operación sigue sin admitir parámetros y no hay forma de señalar a nadie más. Nace `CA-SP-473`. **Y queda declarado lo que este campo NO desbloquea**: `POST /api/v1/movements` exige `movements:create`, hoy reservado a `SUPERADMIN` (`requirements/mv.md` §6.1), de modo que un cliente sigue sin poder llamarlo. La compra propia es `RF-MV-002` —`POST /api/v1/movements/mine`, sin permiso—, que está especificada y aprobada desde el 02-09-2026 y **sin construir**. | Responsable del proyecto |
| 1.34.0 | 04-09-2026 | **La semilla de desarrollo cuelga por fin a los clientes de su vendedor**, tres días después de que `RN-SP-028` lo decidiera. Hasta hoy la semilla decía —y la nota de §10.7 lo citaba— que «`CLIENTE` queda fuera porque no son vendedores», y eso dejó de ser cierto el 01-09-2026: el cliente cuelga de su vendedor **en `user_supervisors`**, con el cliente en `user_id`. **La consecuencia de ese desfase no era cosmética: en desarrollo no había NI UNA CARTERA**, de modo que la mitad comercial de una venta —de quién es el cliente, a quién se le atribuye— no se podía ver funcionando en local. **Los tres clientes cuelgan a profundidad distinta**, y esa es la decisión: `cliente1` de un agente, `cliente2` de un director y `cliente3` de un manager. Lo permite la **rama de consumidor de `RN-SP-020`**, que solo exige que el superior porte **algún** rol `VENDEDOR` sin parentesco que comprobar — y colgarlos a los tres de un agente habría dejado sin existir en desarrollo el caso que el diseño admite y que obliga a decidir **a qué tarifa cobra quien está pegado al cliente cuando no es un agente**. `director1` pasa a tener cuatro a cargo —tres agentes y un cliente—, que es la mezcla que `RF-SP-042` tiene que saber devolver distinguiendo por rol. `ADMIN` sigue fuera: no es vendedor y no tiene cartera. | Responsable técnico |
| 1.35.0 | 05-09-2026 | **`user_memberships` pasa a ser un historial**, por decisión del responsable del proyecto: conceder una membresía es **una fila nueva** —se cierra la que había y se crea otra—, y no un `UPDATE` sobre la única fila de la persona. **`RN-SP-014` se reescribe**: de «una membresía por usuario» a «una membresía **vigente** por usuario, y todas las que tuvo conservadas». La regla dejaba una deuda que estaba escrita, aceptada y **citada por otro módulo**: `RN-MV-020` —nacida el día anterior— declaraba que quien necesitara saber en qué nivel estaba alguien en una fecha «tendrá que leerlo de las ventas confirmadas, no de `SP`». Esa deuda desaparece, y con ella el párrafo que la justificaba. **La decisión que carga el cambio son DOS columnas de fin y no una**: `ends_at` sigue siendo la **planificada** —hasta cuándo se pagó, nula si es indefinida— y nace **`closed_at`**, el cierre **real**. Una membresía de treinta días reemplazada el día doce termina con las dos fechas puestas y distintas, y las dos son ciertas; con una sola columna se pierde la diferencia entre **vencer** y **que te la sustituyan**, que es justo la que responde un reclamo. **La unicidad deja de poder vivir en la clave primaria** —que pasa a un `id` propio, porque `user_id` se repite— y se reparte entre **dos** restricciones que no se solapan en su trabajo: `uq_user_memberships_abierta`, único parcial sobre `WHERE closed_at IS NULL`, que es lo que impide dos filas actuales **y** lo que evita que el `LEFT JOIN` de `RF-SP-025` y `RF-SP-026` empiece a repetir personas; y `ex_user_memberships_sin_solape`, un `EXCLUDE USING gist` sobre `tstzrange(started_at, COALESCE(LEAST(ends_at, closed_at), 'infinity'))`, que es lo que impide que dos **periodos** se pisen — algo que el índice parcial no ve, porque dos filas cerradas con fechas solapadas lo satisfacen. Ninguno de los dos sobra. Es el patrón y la extensión que `V44` ya estrenó con `ex_commission_rates_sin_solape`. **De ahí sale la obligación menos evidente: conceder cierra SIEMPRE**, aunque la anterior estuviera vencida; si no, quedan dos filas abiertas. **`RF-SP-033` cierra y ya no borra**, con `closed_at` y sin tocar `ends_at`: el `DELETE` llevaba escrito su motivo —`RN-SP-015` dice que quien deja de ser consumidor **no tiene** membresía, no que tuviera una que terminó— y el historial lo invierte, porque la fila cerrada dice exactamente que la tuvo y se la quitaron. Es el criterio con el que `endSupervisor` nunca fue un `DELETE`. **Y una decisión técnica queda declarada para que no se tome por omisión**: `RF-SP-032` con la **misma** membresía y otra fecha **actualiza** la fila abierta y no genera historial —es una corrección administrativa, no un cambio de nivel—, mientras que con **otra** membresía cierra e inserta; es lo que conserva la distinción entre `FA-002` y `FA-003` que el dominio ya codificaba. La migración es `V56`. | Responsable del proyecto |
| 1.36.0 | 05-09-2026 | **Toda persona tiene membresía, y quien no recibe una arranca en `BECA`** — decisión del responsable del proyecto. Es un cambio de alcance, no un ajuste: la membresía deja de significar «esta persona es cliente» y pasa a ser **un atributo de todo usuario**, superadministrador y funcionarios incluidos. **`RN-SP-018` se reescribe** y **dos reglas críticas mueren con ella**: `RN-SP-013` —membresía solo para consumidores— y `RN-SP-015` —quedarse sin rol consumidor retira la membresía—. Las dos sostenían las mitades de una atadura entre el rol y el nivel que ya no existe; **sus filas se conservan tachadas y no se borran**, porque sus códigos estaban citados en respuestas de error, en cinco `plan.md` aprobados y en los flujos, y suprimirlos dejaría referencias colgando. **El suelo se resuelve por código y no por la forma de la cadena**, y esa es la decisión que más se piensa: `RN-SP-007` permite registrar una membresía **por debajo** de `BECA`, de modo que «la que no tiene padre» es un blanco móvil — con él, registrar un nivel nuevo cambiaría en silencio con qué arranca la gente. Se toma la de **código `BECA`**, sembrada por `V46`, única por `uq_memberships_code` e imposible de borrar por `RN-SP-008`. El precio queda escrito: si alguien registra una por debajo, **el suelo de la cadena y el nivel de arranque dejan de ser el mismo**. **Cuatro requerimientos cambian de comportamiento.** `RF-SP-024`: `membershipId` pasa a **opcional** y sin él la persona nace en `BECA`; deja de exigirse por portar un rol consumidor. `RF-SP-030`: **deja de admitir membresía**, y sus dos campos se retiran del cuerpo — quien ya tiene nivel no lo cambia por una puerta lateral, y cambiarlo es `RF-SP-032`, que tiene su propio permiso. `RF-SP-031`: **pierde la cascada** — quien deja de ser consumidor **conserva la membresía que tenía**, incluida una comprada; bajarla al suelo sería quitarle algo que pagó. `RF-SP-033`: **devuelve al suelo en lugar de dejar sin nada**, y con ello responde `200` con la membresía `BECA` en vez de `204` sin cuerpo. **Y lo que esto abre fuera de `SP` conviene tenerlo presente**: `RF-PM-007` y `RF-MV-002` deciden qué se ofrece y qué se puede comprar leyendo la membresía vigente, de modo que **funcionarios y vendedores pasan a tener oferta de upgrades**. Va en la misma dirección que la enmienda del 04-09-2026 que abrió la compra propia más allá de los clientes. La migración es `V57`, que **rellena y no altera el esquema**: no hay columna nueva, solo una fila `BECA` para toda persona que no tuviera ninguna abierta. **El invariante no se puede declarar en el motor** —«toda fila de `users` tiene una abierta en `user_memberships`» es una comprobación entre tablas que ningún `CHECK` alcanza—, y por eso lo sostienen el relleno y las tres operaciones que crean personas. | Responsable del proyecto |
| 1.37.0 | 07-09-2026 | **Nace el submódulo TASAS DE CAMBIO**, por decisión del responsable del proyecto: a cuánto se cambia una moneda por otra, desde cuándo y hasta cuándo. Cuatro requerimientos —`RF-SP-047` a `RF-SP-050`—, cuatro permisos `exchange-rates:` y una tabla, `exchange_rates` (§10.14). **Se administra por API, al revés que el catálogo de monedas**: `RN-SP-010` deja las monedas fuera del alcance de la API porque son un catálogo estable que nadie edita, y una tasa es lo contrario — cambia, y cambia seguido. **La decisión que carga el diseño es `RN-SP-032`: dos tasas vigentes del mismo par no se solapan**, y §5.2 explica por qué **un `UNIQUE` no puede expresarlo** — lo que no puede repetirse no es un valor, es un **solapamiento de rangos**: dos tasas `USD → COP` con fechas distintas pasarían cualquier unicidad y en el día que comparten habría **dos precios para el mismo cambio**. Se declara con el `EXCLUDE USING gist` que `V44` estrenó para las tasas de comisión, con `daterange(..., '[]')` —el intervalo cerrado, o dos tasas que se tocan en un extremo no se verían— y **parcial sobre las vivas y activas**, o retirar dejaría el periodo bloqueado para siempre. **De ahí sale la consecuencia que hay que aceptar entera**: si las inactivas no bloquean, **activar es la operación peligrosa y no el alta**, de modo que `RF-SP-047` y `RF-SP-049` tienen los dos que traducir esa violación a un `409` — dejarla subir daría un `500` sobre una regla de negocio. **El precio se declara `numeric(18,8)` y no `numeric(14,4)` como `products.price`**, y el motivo hay que leerlo: una tasa **no es un importe**. Con cuatro decimales `COP → USD` —del orden de `0,00024`— se guardaría redondeada, y una moneda más devaluada se guardaría como **cero**. **El estado es booleano y no un `varchar` con `CHECK`**, al revés que `products.status`: aquel creció porque su dominio era candidato a hacerlo, y aquí la única distinción que un tercer estado expresaría —«programada, aún no rige»— **ya la expresan las fechas**. **Y queda declarado lo que esto NO hace**: `MV` sigue exigiendo una sola moneda por venta (`RN-MV-012`). Lo que cambia es el **motivo** de esa regla — decía «este sistema no tiene ninguna tasa de cambio», y ahora las tiene: sigue sin convertir **por decisión** y no por ausencia. | Responsable del proyecto |
| 1.38.0 | 07-09-2026 | **Toda persona pertenece a un país**, por decisión del responsable del proyecto. Nace `RN-SP-034` y `users` gana `country_id`, `NOT NULL` y con clave foránea a `countries` (§10.10). Es la **primera columna de `users` que apunta a un catálogo**, y con ella `countries` recibe su segunda clave foránea entrante —la primera venía de `payment_method_exclusions` (`V55`)—: el sistema ya sabía **dónde no vale un medio de pago** y ahora sabe **dónde está quien va a pagar**, que es la asimetría que [`modelo-datos.md` §6](../modelo-datos.md) tenía anotada como pendiente 2. **El país es una columna y no una tabla puente**, al revés que la membresía y el superior comercial, y el criterio queda escrito: aquellas dos llevan tabla porque **tienen vigencia** —se conceden, vencen, se sustituyen— y el país no tiene ninguna; nadie pregunta en qué país estaba alguien el mes pasado, y el rastro del cambio ya lo guarda `audit_change_log`. **Se fija en el alta —administrativa (`RF-SP-024`) y por enlace (`RF-SP-045`)— y solo lo corrige un administrador con `users:update` (`RF-SP-027`); el titular no lo toca desde `RF-SP-044`**, porque el país decide qué medios de pago se le ofrecen (`RN-MV-019`) y cambiárselo uno mismo sería cambiarse de mercado. **La mitad de la regla que exige país activo NO se declara en el esquema**, y el motivo merece leerse porque parecía declarable: el patrón de clave foránea compuesta que `RN-SP-025` estrenó el 02-09-2026 exige que el dato copiado sea **inmutable en su origen**, y `countries.is_active` es justo **lo único que `RN-SP-009` deja cambiar** — declararlo haría que `RF-SP-022` fallara sobre cualquier país con usuarios, convirtiendo «retirarlo de los selectores» en una operación bloqueada por terceros. La comprobación es **de entrada y no permanente**: quien ya tenía el país lo conserva aunque se desactive. **Lo que cuesta queda escrito: el catálogo de países deja de nacer vacío.** `V22` siembra un superadministrador que hay que rellenar, de modo que la migración —**`V64`**, tras cederle `V65` y `V66` a las tasas de cambio el mismo día— **siembra Colombia** (`COL`) con identificador UUID v7 literal, igual que `V15` con `USD`. Y la elección **no tiene corrección posible** (`RN-SP-009`): un país mal sembrado solo se puede desactivar. Enmienda seis tripletas ya aprobadas (Art. I.7): `RF-SP-024`, `RF-SP-025` —el país aparece en el listado y se puede filtrar por él, con `ix_users_country_id`—, `RF-SP-026`, `RF-SP-027`, `RF-SP-039` y `RF-SP-045`. | Responsable técnico |
| 1.39.0 | 07-09-2026 | **`SP` publica dos lecturas nuevas por la vía de D-25**, y las dos las pide `RF-PM-008` —el hotlink público de `PM`—: **`PublicSellerLookup`**, que devuelve **nombre y apellido** por nombre de usuario, y **`ExchangeRateLookup`**, que devuelve **la tasa vigente hoy** entre dos monedas. Con ellas, las lecturas que este módulo publica pasan de tres a **cinco**. **Las dos llevan la regla dentro, y eso es lo que las hace correctas**: la primera devuelve **vacío cuando la persona no es fuerza comercial**, en lugar de devolver a cualquiera y dejar que `PM` filtre — la definición de quién es publicable depende de los **roles**, que son de este módulo, y partirla dejaría dos definiciones de las que la segunda se quedaría atrás **sin que nada fallara**. La segunda devuelve la tasa **ya elegida** y no la lista del par, por el mismo motivo por el que `CurrentMembershipLookup` devuelve la membresía **ya evaluada**: reimplementar «vigente» fuera es el defecto que produce resultados plausibles durante meses. **Ninguna tabla cambia y ningún requerimiento de `SP` se toca**: son dos puertos de lectura, y las tareas que los escriben pertenecen a `RF-PM-008` aunque el código viva aquí — exactamente como ocurrió con las tres de `RF-PM-001` y `RF-PM-007`. **Se numera 1.39.0 y no 1.38.0**: ese número lo tomó el mismo día el cambio del país (`RN-SP-034`), escrito en paralelo. | Responsable técnico |
| 1.40.0 | 07-09-2026 | **Los dos puertos de membresía que este módulo publica ganan el `color`** (`RN-SP-024`): `MembershipCatalog.MembershipView` y `CurrentMembershipLookup.CurrentMembershipView`. Lo pide `PM`, que lo publica en las cinco respuestas de su catálogo y en el hotlink. **Es un dato puramente estético y aun así cruza por el puerto**, y esa es la parte que merece quedar escrita: la tentación era resolverlo con un `JOIN` de conveniencia «porque solo es un color», y abrir esa excepción sería **la primera grieta en la única regla que sostiene D-25** — quien decide qué se sabe de una membresía es este módulo. **El cambio es aditivo y no rompe a ningún consumidor**: quien ya lee los cuatro campos sigue leyéndolos. **Ninguna tabla cambia**: `memberships.color` existe desde `V38`. | Responsable técnico |
| 1.41.0 | 08-09-2026 | **Toda persona se identifica con un documento y declara sus datos de contacto**, por decisión del responsable del proyecto. `users` gana seis columnas —`document_type_id`, `document_number`, `address_line1`, `address_line2`, `city` y `phone` (§10.16)— y nace el catálogo **`document_types`** (§10.15) con su requerimiento de consulta, **`RF-SP-051`**, y su permiso `document-types:read`. Tres reglas nuevas: `RN-SP-035` —identidad documental, par único que no se libera al eliminar—, `RN-SP-036` —el catálogo no se administra por API— y `RN-SP-037` —teléfono obligatorio, dirección opcional—. **La decisión que define el diseño es cómo se valida la mayoría de edad**: se pidió esa validación y **no se implementa como comprobación, sino como contenido** — el catálogo **solo lleva documentos de persona mayor de edad**, sin columna que marque cuáles sí y cuáles no. Con una columna, registrar a un menor sería *posible y rechazado*, y bastaría con que un caso de uso futuro olvidara mirarla; sin ella es **inexpresable**, porque no hay identificador que poner que signifique «Tarjeta de Identidad» y `fk_users_document_type` no admite otra cosa. **De ahí que `RN-SP-036` no sea simetría con `RN-SP-010` sino una necesidad**: un catálogo administrable por API dejaría que cualquiera añadiera el tipo que falta y **la validación desaparecería sin cambiar ninguna regla, sin migración y sin que nadie lo notara**. **Lo que esto no hace queda dicho**: el tipo de documento es un **indicio** de mayoría de edad y no una prueba —un pasaporte lo tiene un niño igual—, y la condición para abrir el campo de fecha de nacimiento queda escrita en la ficha de `RF-SP-051`. **Y la asimetría que hay que aceptar entera**: las seis columnas nacen **nulables en el esquema** aunque documento y teléfono sean **obligatorios en la API**, porque un número de documento de relleno no es un valor neutro como «Colombia» — **es una afirmación falsa sobre la identidad de una persona**, y `V22` siembra un superadministrador que no tiene ninguno. Existen y seguirán existiendo personas sin documento; lo que no puede ocurrir es que se creen más. Enmienda cinco tripletas aprobadas (Art. I.7): `RF-SP-024`, `RF-SP-026`, `RF-SP-027`, `RF-SP-039` y `RF-SP-044` —que gana los cuatro campos de contacto y **no** el documento, porque el documento es identidad y lo corrige un administrador— más `RF-SP-045`. | Responsable técnico |
| 1.42.0 | 08-09-2026 | **Nace el submódulo BROKERS**, por decisión del responsable del proyecto: el catálogo de los brokers con los que opera la plataforma y **la cuenta que cada persona tiene en cada uno**. El módulo pasa a **cincuenta y cuatro** requerimientos —`RF-SP-052` a `RF-SP-054`— y el modelo gana **dos tablas**, `brokers` y `user_brokers` (§10.17 y §10.18). **El catálogo se puebla por migración y solo se consulta** (`RN-SP-039`), como los de monedas y tipos de documento, y **de momento guarda solo el nombre** — de donde sale que **el nombre sea la clave de negocio**, con índice único funcional, y que renombrar un broker sea una migración. **La regla que carga el diseño es `RN-SP-038`**: el único va sobre **`(broker_id, external_id)`** y **no** sobre `(user_id, broker_id)`. La diferencia es el requerimiento entero — con el segundo se prohibiría lo que sí se admite (varias cuentas de la misma persona en el mismo broker, que es lo normal en el ramo) y se permitiría lo que no (que dos personas declaren la misma cuenta). Lo sostiene el índice y no una comprobación previa, porque dos altas simultáneas de la misma cuenta pasan cualquier comprobación previa. **`RN-SP-040` explica por qué `broker_username` admite nulo**: la cuenta se declara con el broker y el identificador —lo único que la persona conoce— y el nombre de usuario **lo rellena después el webhook del broker**; ese nulo significa «el broker todavía no lo ha confirmado» y no «no tiene». **Solo se construye `RF-SP-052`**: `RF-SP-053` —quién declara la cuenta— y `RF-SP-054` —el webhook— quedan **registrados y sin `spec.md`**, porque el actor del primero no está decidido y del segundo no está decidido **nada de lo que importa**: cómo se autentica el broker, qué pasa con un webhook de una cuenta que nadie declaró, si puede cambiar el identificador y cómo se hace idempotente ante la reentrega. Queda declarado que sería **la segunda ruta pública del sistema y la primera que ESCRIBE**. | Responsable del proyecto |
| 1.43.0 | 08-09-2026 | **Los tres catálogos del registro se abren SIN INICIAR SESIÓN**, por decisión del responsable del proyecto, y **el catálogo de brokers se siembra**: `IQOPTION`, `EXNOVA` y `EXOPTION` (`V76`). Nace `RN-SP-041`: el `GET` de países (`RF-SP-021`), tipos de documento (`RF-SP-051`) y brokers (`RF-SP-052`) es **público**. Lo que resuelve es un hueco declarado tres veces —en `V16`, `V72` y `V75`—: el registro público de `RF-SP-045` necesita elegir los tres y ninguno se podía leer sin una sesión que todavía no existe. **Solo el `GET`**, y esa precisión es la mitad del cambio: el alta y el cambio de estado de países siguen exigiendo su permiso, y siguen respondiendo `401` sin token en lugar de `403`. **Lo que publican no identifica a nadie** —son listas de opciones—, de modo que no hace falta aquí nada del diseño que el hotlink necesitó: no hay oráculo posible porque no hay nada que sondear. **Consecuencia declarada y no disimulada**: `countries:read`, `document-types:read` y `brokers:read` **dejan de gobernar esas lecturas** y quedan sembrados sin endpoint que los exija, como `products:hotlink`; no se retiran porque eliminarlos rompería los roles que ya los tengan, y hacerlo es una decisión del responsable del proyecto y no una limpieza técnica ([`security.md`](../security.md) v0.46.0 §6). **Los tres brokers se escriben como se dieron**, en mayúsculas: el nombre es la clave de negocio, de modo que la caja con la que entran es la que el desplegable pinta y cambiarla después es una migración. La migración lleva **guarda**: si no quedan tres, aborta — un catálogo a medias no falla en ningún sitio y deja el registro con menos opciones de las que existen. | Responsable del proyecto |
| 1.44.0 | 09-09-2026 | **`RF-SP-045` construido**, y con él nace `RN-SP-042`: **quien se registra por un enlace `BECA → BECA` declara su cuenta de broker**. La condición no es de forma sino **de encierro** — la cuenta nace en `FTD_PENDIENTE`, que autentica y no opera, y quien la saca de ahí es el depósito que confirma el webhook del broker; sin la cuenta declarada, nadie puede saber de quién es un depósito cuando llegue y la persona se queda encerrada. **Hoy la condición se cumple siempre** —todo producto admisible en el registro lleva a la membresía gratuita y `RN-PM-017` impide apuntar por debajo del origen—, y lo que compra es el día que se abra el camino de pago. **Con ella queda respondida la pregunta que `RF-SP-053` dejó abierta el 08-09-2026**: quien declara la cuenta es **el titular, al registrarse**. **`PENDIENTE` pasa a `FTD_PENDIENTE`** (`V77`), y es el primer estado distinto de `ACTIVO` que **autentica**. La ficha de `RF-SP-045` pasa a **En desarrollo** con `RN-SP-034` a `RN-SP-038`, `RN-SP-040` a `RN-SP-042` en sus reglas aplicables. | Responsable del proyecto |
| 1.45.0 | 09-09-2026 | **`RN-SP-042` se precisa: las cuentas de broker son UNA O MÁS**, y no una. Una persona puede operar con varios brokers, y el registro por enlace es **hoy la única vía** para declararlos. La regla exige **al menos una** cuando el enlace es `BECA → BECA`, y admite **dos del mismo broker** — lo que `RN-SP-038` acota es el par broker + identificador, no cuántas cuentas tiene alguien. **La repetida dentro de la misma petición se rechaza como dato inválido** y no con el conflicto del índice: ese mensaje diría «ya está declarada por otra persona», y ahí la otra persona sería ella misma. Y **si una cuenta choca, no queda nada** — ni la persona, ni su membresía, ni las cuentas anteriores del mismo formulario. | Responsable del proyecto |
| 1.46.0 | 09-09-2026 | **El registro por enlace anota su venta, y el camino de pago deja de estar cerrado.** Nacen `RN-SP-043` y `RN-SP-044`, por decisión del responsable del proyecto. **`RN-SP-043`**: el formulario declara el movimiento —producto, método de pago, vendedor y tipo— y el alta **lo anota siempre**, también en el enlace gratuito, porque todo alta deja rastro de qué se vendió. La venta **no se reimplementa**: la registra `RF-MV-001` con sus reglas enteras, de modo que sigue habiendo **una sola definición de vender** —una venta «simplificada» escrita en `SP` sería una segunda, y se quedaría atrás sin que nada fallara—. El producto del movimiento tiene que ser **el del enlace**, el vendedor **se verifica y no se impone** —`RN-MV-003` lo saca del superior que el registro acaba de asignar— y el método de pago es condicional **al importe** y no al producto (`RN-MV-022`). Todo en la **misma transacción**: si la venta se rechaza, no queda ni la persona. **`RN-SP-044`**: un enlace hacia una membresía de pago **ya no se rechaza** —muere `EX-004` de `RF-SP-045`— y lo que el producto decide pasa a ser **cómo nace la cuenta**: el gratuito la deja en `FTD_PENDIENTE` con la membresía **del producto**, y el de pago la deja **`ACTIVO`** con la membresía **del suelo**. La comprada **no se concede al registrarse**: la concede confirmar la venta (`RN-MV-020`), y darla aquí sería premiar un pago que nadie ha comprobado — es la contraparte exacta de `RN-MV-004`. De ahí sale que **estar activo y tener el nivel comprado sean dos cosas distintas**. Consecuencia declarada en `MV`: la venta del alta queda **exenta de `RN-MV-008`**, porque es la venta que **pone** a la cuenta en `FTD_PENDIENTE` y no una compra posterior desde ese estado; la exención es de paquete y solo la alcanza el adaptador del registro. | Responsable del proyecto |
| 1.47.0 | 09-09-2026 | **`RN-SP-043` se precisa: el bloque del movimiento ES el enlace.** Por decisión del responsable del proyecto mueren `product` y `referrer` del primer nivel del cuerpo — decían lo mismo que `movement.productId` y `movement.sellerUsername`. **Dos campos para un dato son dos valores que pueden discrepar**, y hacían falta dos comprobaciones para vigilarlo: `VAL-016` y la mitad de `EX-010`. Al quitar el duplicado **la divergencia dejó de poder expresarse** y las dos se retiran. `VAL-001` y `VAL-002` conservan su código y cambian de sitio. **Una sola declaración de vendedor gobierna dos cosas**: el superior comercial que el registro cuelga y, por `RN-MV-003`, el vendedor de la venta — coincidir dejó de ser algo que comprobar. **El producto pasa a ir por identificador**, como `brokerId` y por el mismo argumento; el enlace sigue llevando el código. | Responsable del proyecto |
| 1.48.0 | 10-09-2026 | **`RF-SP-042` filtra por roles y publica los roles de cada persona**, por decisión del responsable del proyecto, y son dos cambios que se sostienen el uno al otro. **Cada persona de la respuesta —la consultada, su superior y cada miembro del equipo— lleva ahora `roles`**: la lista completa de los que porta, con identificador, código y nombre, ordenada por código y presente aunque vaya vacía. **Sustituye a `roleCode`**, que devolvía uno solo y además solo si era de clasificación `VENDEDOR` — de modo que desde `RF-SP-045` la cartera de clientes llegaba con **el rol en nulo** y un cliente era indistinguible de un vendedor sin rol. §10.7 llevaba desde el 01-09-2026 afirmando que «cada fila lleva ya los roles de la persona, que es lo que permite distinguirlos»: **la afirmación era falsa y hoy pasa a ser cierta**, y el párrafo queda corregido para que no vuelva a leerse como una garantía que nunca existió. **El equipo directo admite un filtro `roles` por códigos, varios a la vez y con semántica O**; el total cuenta lo filtrado, un código inexistente devuelve la página vacía sin error —criterio de `RF-SP-025`— y **el filtro no toca al superior**, porque su ausencia ya significa «es la cúspide» y filtrarlo haría indistinguibles las dos cosas (`CA-SP-445`). **Enmienda de hecho a `RF-SP-041`** (Art. I.7): comparte `CommercialStructureResponse` y por tanto también pierde `roleCode` y gana `roles`. Se **invierte `CA-SP-455`** —que exigía la ausencia de filtros— en lugar de borrarlo, y se rehace la resolución 3 de su `spec.md` §14: el argumento de 22-08-2026 era que `RF-SP-025` ya filtra, y dejó de valer el día que la cartera de clientes entró en esta estructura, porque el listado general no sabe responder «de la gente que cuelga de este agente, enséñame solo los clientes». **`RF-SP-039` conserva su `roleCode`**: publica el superior del propio actor por otro DTO y no entra en este cambio. | Responsable del proyecto |
| 1.49.0 | 10-09-2026 | **La cuenta de broker declara en qué punto está, y el superior comercial puede consultarla**, por decisión del responsable del proyecto. Nacen **`RN-SP-045`** —`user_brokers.status` en `REGISTER` o `FIRST_DEPOSIT`, con `CHECK` en el motor (§5.2)— y **`RN-SP-046`** —quién ve esas cuentas—, y con ellas **dos requerimientos**, `RF-SP-055` (las cuentas de una persona) y `RF-SP-056` (las del equipo, en un listado plano con filtro por estado y por broker). El módulo pasa a **cincuenta y seis** requerimientos y el submódulo de brokers a `RF-SP-052`–`RF-SP-056`. **Tres decisiones cargan el diseño y quedan escritas para que no se relean como descuidos**: **(1) los dos valores van en inglés** contra la costumbre del resto de enumerados —`ACTIVO`, `FTD_PENDIENTE`, `CONFIRMADO`—, porque son el vocabulario del broker que los va a escribir; **(2) hoy nadie mueve la cuenta de `REGISTER`**, y no es un olvido: quien la mueve es el webhook de `RF-SP-054`, que sigue sin construirse, de modo que la columna **se lee desde el primer día y no se escribe todavía** — se declara aquí para que nadie lo descubra leyendo el código, y lo que la salva del defecto que `RF-SP-035` dejó escrito con la purga es que **el valor que devuelve es cierto**: mientras no haya webhook, ninguna cuenta tiene depósito confirmado; **(3) la estructura comercial concede alcance de datos por primera vez.** `V21` declaró lo contrario —«registrar la estructura no concede alcance de datos»— y la **D-22** sigue abierta: `RN-SP-046` **no la resuelve**, la acota a esta lectura, un solo nivel como `RF-SP-042`, y deja fuera al titular sobre sus propias cuentas. El acceso indebido responde **`404` y no `403`**, con el criterio del hotlink de `RF-PM-008`: quien puede probar identificadores ajenos no debe poder distinguir «no existe» de «no es tuyo». **El catálogo de permisos gana `broker-accounts:read`** —el cuarenta y cinco— para ver las cuentas de cualquiera, y **el listado del equipo no exige ninguno**: el alcance lo pone la estructura. | Responsable del proyecto |
| 1.50.0 | 10-09-2026 | **El administrador consulta TODAS las cuentas de broker y filtra por la red de un vendedor**, por decisión del responsable del proyecto. Nace `RF-SP-057` —`GET /api/v1/broker-accounts`, gobernado por `broker-accounts:read`, paginado, con `supervisorId`, `userId`, `status`, `brokerId`, `search` y el rango `from`/`to`— y con él `RN-SP-047`. El módulo pasa a **cincuenta y siete** requerimientos. **Cierra un hueco que `RF-SP-056` había declarado cerrado el día anterior**: su §14 decía «¿puede un administrador pedir el equipo de otro? **No por aquí** — un `?supervisorId=` es otro requerimiento y **nadie lo ha pedido**», y se pidió al día siguiente. Se registra así, con la frase entera, porque es la prueba de que aquella decisión estaba bien tomada: se dejó fuera **por no adivinar**, no por descuido. **Lo que carga la regla es la PROFUNDIDAD**: filtrar por un vendedor devuelve **toda su red** —sus subordinados, los de estos, hasta abajo—, y eso **rompe a propósito la cota de un solo nivel** que se imponen `RF-SP-042`, `RF-SP-055` y `RF-SP-056`. La asimetría no es un descuido y tiene una razón exacta: **aquellas las autoriza la estructura** —y devolver la rama entera publicaría la empresa a quien solo lleva un equipo—, **esta la autoriza el permiso**, que ya alcanza a todo el mundo, de modo que la profundidad **no concede nada que el actor no tuviera**: le ahorra recorrer el árbol a mano. Tres precisiones más quedan escritas: **la raíz no se incluye** —la red son los suyos, y sus propias cuentas se piden por `userId`, combinable—; **solo la estructura vigente** cuenta; y **el ciclo no puede colgar la consulta** aunque los datos lo tuvieran, porque la recursión acumula con `UNION` y no con `UNION ALL`. **Ningún permiso nuevo**: `broker-accounts:read` nació el mismo día y esto es exactamente lo que significa. §5.2 declara `ix_user_brokers_busqueda`, el índice de trigramas del filtro por texto, con las **expresiones del predicado** — la lección que `ix_users_busqueda` dejó escrita. | Responsable del proyecto |
| 1.51.0 | 10-09-2026 | **La semilla de desarrollo cuelga a los funcionarios: el árbol de personas llega de punta a punta**, por decisión del responsable del proyecto — `admin1` de `superadmin` y los tres managers de `admin1`, de modo que en local se ve `superadmin ← admin1 ← manager ← director ← agente` con la cartera de clientes en las hojas. Hasta hoy nacía **partida en dos**: la fuerza comercial colgaba de tres managers que no colgaban de nadie, y `ADMIN` quedaba suelto. **Las cuatro filas nuevas son DEUDA DECLARADA y quedan escritas como tal** en §10.7 y en la cabecera del guion: `RF-SP-041` las rechazaría con `409` —**`VAL-004`** para el manager, porque `RN-SP-019` lo exceptúa por ser la cúspide de la fuerza comercial \(su rol padre `ADMIN` no es `VENDEDOR`\), y **`VAL-003`** para el administrador, que no pertenece a la fuerza comercial—, y `RN-SP-020` tampoco las cubre, porque tiene rama de vendedor y rama de consumidor y un `FUNCIONARIO` no cae en ninguna. **No se enmienda ninguna regla**, y es deliberado: la forma sembrada **coincide** con el parentesco que declara §4.1, de modo que lo que falta es decidir si la estructura de personas deja de ser comercial para ser la jerarquía completa — y eso es un cambio de `RN-SP-019`, `RN-SP-020` y `CommercialStructure` que nadie ha pedido todavía. Mientras tanto esas filas **viven solo en la semilla**: ningún requerimiento debe apoyarse en ellas. Cambia además **quién es la cúspide en desarrollo**: la única persona sin superior pasa a ser `superadmin`, con lo que la omisión de `supervisor` que define `CA-SP-445` sigue siendo observable en **una** persona y no en cuatro. `DevelopmentSeedIT` invierte la comprobación que exigía «cero managers con superior» y añade la rama de funcionarios y la unicidad de la raíz. | Responsable del proyecto |
| 1.51.0 | 10-09-2026 | **Nacen los indicadores de la red comercial**, por decisión del responsable del proyecto: `RF-SP-058` —`GET /api/v1/broker-accounts/indicators`— devuelve **el árbol de la fuerza comercial con los números de cada nodo**, y `RN-SP-048` fija **cómo suman**. El módulo pasa a **cincuenta y ocho** requerimientos. **Ningún permiso nuevo y ningún cambio de esquema**: lo gobierna `broker-accounts:read` y todo sale de `user_brokers`, `user_supervisors` y `user_roles`. La regla es **una sola aplicada en todos los niveles** —los suyos directos más la suma de los de abajo—, y lo que la hace no trivial son **cuatro precisiones que el responsable del proyecto fijó o pidió cuidar**: **(1) la unidad es LA CUENTA y no la persona**, porque lo que se mide es el dinero que entró y un cliente puede tener dos cuentas depositadas (`RN-SP-038` lo admite); **(2) solo cuentan las cuentas de CONSUMIDORES** —«solo los roles de tipo consumidores tienen ftds»—, de modo que la cuenta personal de un vendedor no entra: se resuelve por `user_roles.role_type` y no por la posición en el árbol; **(3) el total de una persona YA CONTIENE el de sus subordinados**, y de ahí que la respuesta publique **los dos números por separado** —propios y red— y nunca uno solo: publicar solo el total invita a sumar la columna y contar dos veces, que es exactamente el cuidado que se pidió poner; **(4) los NODOS son la fuerza comercial y los consumidores no aparecen** —aportan el número y no son filas—, porque publicarlos convertiría un indicador de gestión en el listado de clientes de la empresa. De **(4)** sale la consecuencia que nadie habría notado hasta cuadrar cifras: lo que cuelga de quien **no** es fuerza comercial no entra en ningún nodo, y por eso la respuesta lleva además **lo no atribuido** — sin ese bloque el árbol sumaría menos que `RF-SP-057` y no habría forma de saber por qué. **La conversión es NULA y no cero cuando no hay cuentas**: cero se lee como «nadie convirtió» y la verdad es «no hay nada que convertir». **Y hoy el FTD será cero en todas partes**, porque el webhook de `RF-SP-054` no existe: el embudo se verá entero en pendientes, y eso es el estado real y no un fallo del indicador. | Responsable del proyecto |
| 1.52.0 | 10-09-2026 | **`RF-SP-057` gana un RESUMEN de lo filtrado**, por decisión del responsable del proyecto: cuántos registros hay y cuántos están en `FIRST_DEPOSIT`, **cada uno con su desglose por broker**. Ningún requerimiento nuevo, ningún permiso nuevo y ningún cambio de esquema — es una **enmienda** a `RF-SP-057` (Art. I.7). **Va dentro de la respuesta del listado** y no en un endpoint aparte: una sola llamada pinta la tabla y sus contadores, y así no pueden desincronizarse. **La decisión que carga la enmienda es que el resumen respeta TODOS los filtros, incluido `status`**, y se tomó **contra la recomendación técnica**: se ofreció que el conteo de FTD ignorara el filtro de estado —para que los dos números informaran a la vez— y el responsable del proyecto eligió la **coherencia**, que el resumen describa exactamente lo devuelto. Queda escrita la consecuencia, que es lo que importa: **con `?status=REGISTER` el total de `FIRST_DEPOSIT` vale siempre cero**, y ese cero **no significa «nadie ha depositado» sino «no pediste ninguno»** — la prosa del contrato lo dice con esas palabras, porque es la lectura que un tablero haría mal. **El desglose trae solo los brokers con al menos una cuenta**: un broker ausente no tiene ninguna, y así no hay ceros que interpretar. Consecuencia de implementación que conviene tener escrita: **el total de la página y el del resumen salen ahora de UNA sola consulta agrupada**, de modo que no pueden discrepar — antes el conteo iba por su cuenta y era una segunda fuente de verdad sobre lo mismo. | Responsable del proyecto |
| 1.53.0 | 10-09-2026 | **El resumen de `RF-SP-057` se corrige el mismo día en dos puntos**, por decisión del responsable del proyecto. **Uno: van los DOS estados.** Nace `summary.register` junto a `firstDeposit`, y `accounts` pasa a ser siempre su suma. El motivo no es la comodidad: obligar al cliente a restar —`accounts − firstDeposit`— le pide **rehacer una cuenta que el servidor ya tiene hecha**, y con dos valores posibles esa resta es trivial **hoy** — el día que `RN-SP-045` admita un tercer estado dejaría de serlo, **y quien la hubiera escrito no se enteraría**. **Dos: el desglose trae TODOS los brokers del catálogo, con cero donde no hay.** Nació al revés —solo los que tenían alguna— y se invierte por lo que cuesta en pantalla: **un arreglo cuya longitud depende del filtro obliga a rearmar las columnas en cada consulta**, y una columna que desaparece se lee como un dato que falta, no como un cero. **`CA-SP-671` se retira y su prueba se INVIERTE en `CA-SP-675`** en lugar de borrarse —criterio de `CA-SP-606` en `RF-SP-052`—, de modo que el día que alguien vuelva a acotar el desglose, falla ahí. Queda declarada la consecuencia: **un broker desactivado sigue apareciendo** con su cero o con lo que ya tuviera, que es lo correcto —apagarlo no borra lo declarado en él (`RF-SP-052` §13)— al precio de una columna muerta el día que se retire alguno; acotarlo a los activos sería otra decisión del responsable del proyecto. **Implementación**: el desglose se completa **reutilizando el catálogo de `RF-SP-052`** —`BrokerQueryRepository`— en vez de un `LEFT JOIN` contra la consulta filtrada, que habría obligado a repetir el predicado y la recursiva dentro de una unión externa. | Responsable del proyecto |
| 1.54.0 | 10-09-2026 | **Toda persona puede declarar DOS teléfonos: el personal y el de la empresa**, por decisión del responsable del proyecto. Nace la columna `users.company_phone` \(§10.16, `V83`\) con la misma forma que `phone` —mismo largo, misma normalización a dígitos con `+` opcional, misma restricción `ck_users_company_phone_format`— y **`RN-SP-037` queda enmendada** \(Art. I.7\): el teléfono **personal** sigue siendo obligatorio y el de la empresa es **opcional**, porque exigirlo bloquearía el alta de todo el que no tenga una. **Es una ampliación y NO una ruptura**: `phone` conserva su nombre y su significado en las seis posiciones del contrato, de modo que el frontend sigue funcionando sin tocar nada. **`company_phone` es el único de los cinco campos de contacto que se puede vaciar de vuelta**: al ser opcional, su nulo explícito es una orden de borrado en `RF-SP-027` y `RF-SP-044`, el mismo trato que ya recibe la dirección — y por eso entra en el dominio como `Patchable` y no como el `Optional` del personal. **`RF-SP-045` NO lo pide**, y es deliberado: el formulario público de registro por enlace da de alta a un cliente, y preguntarle por el teléfono de su empresa sería preguntar por algo que no tiene; puede añadirlo después desde su perfil. Quedan enmendadas las tripletas de `RF-SP-024`, `RF-SP-026`, `RF-SP-027`, `RF-SP-039` y `RF-SP-044`, y la de `RF-SP-045` para dejar escrito **por qué no**. | Responsable del proyecto |
| 1.55.0 | 14-09-2026 | **El perfil propio publica el nombre y el color de la membresía vigente** (`RF-SP-039` v0.4.0, `RN-SP-024`), por decisión del responsable del proyecto. Hasta hoy `membership` traía código, nivel y fin de vigencia, y la pantalla de «mi perfil» tenía que pedir la cadena de `RF-SP-017` para pintar el nivel. Son los dos campos que `PM` ya publica de una membresía en la oferta y el hotlink: la misma forma. Aditivo; sin consulta nueva —la sentencia del perfil ya traía el nombre y gana el color—. Nace `CA-SP-682`. | Responsable del proyecto |
| 1.56.0 | 16-09-2026 | **Un cliente tiene un agente principal y varios vendedores vinculados**, por decisión del responsable del proyecto. El vendedor sigue con **un solo superior**; el consumidor conserva **un principal** —su fila vigente de `user_supervisors`, la de quien lo registró— y gana **un vínculo por cada vendedor por cuyo hotlink compre**, en la tabla nueva `client_sellers` (§10.19): una fila por pareja, con origen y fecha, sin fin, y el principal también con su fila. Nace **`RN-SP-049`** y se acotan tres reglas que ya existían para decir que **la estructura comercial mira solo a `user_supervisors`**: el equipo y la protección al retirar (`RN-SP-022`), las cuentas de broker (`RN-SP-046`) y el principal como único superior del cliente (`RN-SP-021`); los indicadores (`RN-SP-048`) no cambian. Lo que el vínculo decide es **la atribución de la venta**: hotlink → su dueño, tienda → el principal (`requirements/mv.md` v0.15.0, `RF-MV-011`, `RN-MV-003`). Un vendedor solo vinculado ve a ese cliente únicamente en sus propios movimientos. Nace **`RF-SP-059`** —consultar los vendedores de un cliente— como pendiente. **Y se repara el documento**: desde antes del 12-09-2026 arrastraba **cuatro copias** de §10.9 a §11 —las tres últimas con el control de cambios detenido en 1.40.0 y §10.16 sin `company_phone`—; se conserva la copia vigente de cada parte y el archivo pasa de 2.755 a 1.874 líneas sin perder una fila de historia. | Responsable del proyecto |
| 1.57.0 | 16-09-2026 | **`RN-SEG-012` deja de alcanzar a los permisos de un rol de sistema**, por decisión del responsable del proyecto ([`security.md`](../security.md) v0.58.0). La regla y la semilla se contradecían desde el primer día: `V8` siembra a `MANAGER`, `DIRECTOR`, `AGENTE` y `CLIENTE` **sin permisos a propósito**, «a la espera de `RF-SP-005`», y `RF-SP-005` los rechazaba con `409` por ser de sistema. Ningún vendedor ni ningún cliente podía tener nunca un permiso, y nadie lo notó hasta intentar darle `products:sale` a `CLIENTE`. La regla queda acotada a **editar, reubicar, desactivar y eliminar** —lo que sostiene la jerarquía y lo que una migración necesita encontrar donde lo dejó— y sale de las reglas aplicables de `RF-SP-005` y `RF-SP-006`, que conservan sus tres cotas: `RN-SEG-003`, `RN-SEG-010` y `RN-SEG-011`. Consecuencia práctica que conviene tener escrita: **para que `AGENTE` tenga un permiso hay que dárselo antes a `MANAGER` y a `DIRECTOR`**, porque la contención se valida contra el padre inmediato (`RN-SEG-004`); `CLIENTE` cuelga de la raíz y no necesita escala. Quedan enmendadas las tripletas de `RF-SP-005` y `RF-SP-006`: `EX-004` y `EX-002` se retiran, `CA-SP-036` y `CA-SP-047` se invierten en `CA-SP-683` a `CA-SP-685`. Sin migración. | Responsable del proyecto |
| 1.58.0 | 17-09-2026 | **`SP` publica su primera interfaz de ESCRITURA hacia otro módulo** (§8; **D-26** cerrada por el responsable del proyecto; `architecture.md` v0.31.0 §15.2): `MembershipGrant`, «conceder el nivel comprado», que `MV` invoca al confirmar una venta (`RF-MV-003`, `RN-MV-020`). Hace lo que `RF-SP-032` hace a mano y con sus reglas —cierra la vigente, inserta la nueva (`RN-SP-014`), respeta el suelo (`RN-SP-018`), audita— y **no decide si conceder**, que es de `MV` (`RN-MV-029`). Síncrona y en la transacción del que llama. Sin requerimiento nuevo de `SP` y sin migración: la escribe `RF-MV-003`. | Responsable del proyecto |
| 1.60.0 | 18-09-2026 | **`SP` publica `PermissionHolderLookup`** (§8; D-25; `architecture.md` v0.32.0 §15.2): «¿esta persona porta este permiso?», la primera lectura que responde sobre un permiso y no sobre un dato. La pide `AC` para `RN-AC-006` —el instructor porta `courses:teach`— y la escribe `RF-AC-008` · `T-03` en paquetes de `SP`. **Un booleano sobre un código y no la lista**, para no dar con qué reconstruir fuera la autorización; «portar» es el predicado de `RN-SEG-010`, extraído a una constante que `JpaEffectivePermissions` y el puerto nuevo comparten. Ninguna tabla ni requerimiento de `SP` cambia. **Se numera 1.60.0 y no 1.59.0**: ese número lo tomó `feature/vendedores-de-un-cliente` el mismo día (el cliente sale de `user_supervisors`), escrito en paralelo. | Responsable técnico |
| 1.61.0 | 19-09-2026 | **El registro por enlace vende por el canal hotlink** (`RF-SP-045`; `RN-MV-007` enmendada en [`requirements/mv.md`](mv.md) v0.27.0), por decisión del responsable del proyecto. La venta que el alta anota (`RN-SP-043`) se validaba contra la oferta de la tienda y rechazaba con `EX-004` todo producto de alcance `HOTLINK`, que es lo que el enlace muestra: desde hoy se valida contra lo que el hotlink publica (`RN-PM-021`). **Y al revés**: un producto solo de tienda ya no se registra por el enlace. La tripleta de `RF-SP-045` gana `CA-SP-686` y `CA-SP-687`. Sin cambios en el formulario ni en el esquema. | Responsable del proyecto |
| 1.62.0 | 19-09-2026 | **Nace `RF-SP-060`, un permiso por operación**, por decisión del responsable del proyecto: cada operación de la API exige un permiso que ninguna otra exige, también listado y detalle. Veintiún códigos gobernaban más de una —`roles:update` cinco, con asignar y revocar permisos dentro— y cada uno **se queda con una** y estrecha su descripción; las demás reciben código nuevo. **Ningún código se renombra ni se retira**; el catálogo pasa de sesenta a **ciento once** con `V28`, y **todo rol que porte un código dividido recibe sus hijos** —nadie pierde nada—. Nace **`RN-SEG-014`** en `security.md` v0.63.0. §6.1 y §9 nombran los trece permisos nuevos de `SP`; `RF-SP-059` pasa a `users:read-sellers`. `SP` llega a **cincuenta y ocho** requerimientos. | Responsable del proyecto |
| 1.63.0 | 21-09-2026 | **El cliente sale de `user_supervisors`: su relación con los vendedores vive entera en `client_sellers`, y el principal es quien lo registró, para siempre**, por decisión del responsable del proyecto al pedir `RF-SP-059` —«quiero saber a qué agentes estoy asignado»—. **Se revierte `RN-SP-028`** (01-09-2026): `user_supervisors` vuelve a significar solo mando dentro de la fuerza comercial, y la atribución del cliente tiene tabla propia. Cinco reglas se acotan sin cambiar de fondo: `RN-SP-020` pierde la rama de consumidor (nunca construida), `RN-SP-021` habla solo de vendedores, **`RN-SP-022` deja de exigir reasignar la cartera al retirar a un vendedor** —el vínculo es un hecho que no se reasigna, y exigirlo haría irretirable a cualquiera que haya registrado a alguien—, `RN-SP-046` y `RN-SP-048` toman el principal del cliente de `client_sellers`. **`RN-SP-049` se enmienda**: el principal es la fila `REGISTRO` y **no se cambia** —no hay operación que lo reasigne ni historial que cerrar—; `client_sellers` gana un índice único parcial que lo garantiza. Enmiendas de hecho (Art. I.7) a cuatro requerimientos construidos: `RF-SP-041` no asigna vendedor a clientes, `RF-SP-042` deja de devolver la cartera (conserva `roles` y su filtro), `RF-SP-045` escribe `client_sellers` en vez de `user_supervisors`, y `RF-SP-055`/`RF-SP-058` resuelven al principal por la tabla nueva. **`RF-SP-059` trae la migración** —crea la tabla, copia las filas vigentes de clientes como `REGISTRO` sin venta y borra las de clientes de `user_supervisors`— y deja de depender de `RF-MV-011`. **Nace `RF-SP-061`** —consultar los clientes de un vendedor— como pendiente: es lo que `RF-SP-042` filtrado por `CLIENTE` respondía hasta hoy. Lo que se paga queda escrito en `RN-SP-028`: subir de un cliente a su manager es un salto de tabla y luego el recorrido, no un solo recorrido. `requirements/mv.md` v0.28.0 (`RN-MV-003`), `modelo-datos.md` v0.64.0. **El mismo día se redacta la tripleta de `RF-SP-059`** —diez criterios, dieciocho tareas, `V20` con la mudanza dentro— y pasa a `Tasks en revisión`; se enmiendan las tripletas de `RF-SP-042` (`CA-SP-625` → `CA-SP-710`), `RF-SP-045` (`CA-SP-513`, `CA-SP-525`, `CA-SP-526` → `CA-SP-711` a `CA-SP-713`; `T-02` deja de existir), `RF-SP-057` y `RF-SP-058` (criterios precisados), `RF-MV-001` y `RF-MV-013`. — redactada el 18-09-2026 en `feature/vendedores-de-un-cliente` e integrada sobre `feature/academia` el 21-09-2026 con el número renumerado; `RF-SP-060` de aquella rama pasa a `RF-SP-061` porque `RF-SP-060` nació en `feature/academia` el 19-09-2026 | Responsable del proyecto |
| 1.64.0 | 21-09-2026 | **`RF-SP-059` nace con `users:read-sellers`, como `RF-SP-060` había anunciado**: su rama se construyó el 18-09-2026 con `users:read` y al integrarla sobre `feature/academia` —donde `RN-SEG-014` ya rige— la operación `GET /users/{id}/sellers` recibe su permiso propio, sembrado por `V29` a `SUPERADMIN` y `ADMIN`; el catálogo pasa a **ciento doce** ([`security.md`](../security.md) v0.65.0). Enmienda de Art. I.7 sobre la tripleta de `RF-SP-059` (spec 0.2.0, plan y tasks con `T-19`); la ficha de `RF-SP-060` §9 anota que el anuncio se cumplió. | Responsable técnico |
| 1.65.0 | 21-09-2026 | **`RF-SP-059` pasa a `En desarrollo`**: la rama que lo construyó el 18-09-2026 queda integrada sobre `feature/academia`, con `V20` (la mudanza a `client_sellers`) y `V29` (`users:read-sellers`). §6.1 y la ficha cambian de estado; ninguna regla cambia. La ficha de `RF-SP-061` pasa detrás de la de `060` y anota lo que pidió el frontend el mismo día (R-45): que la cartera traiga el `id` del cliente. | Responsable técnico |
| 1.66.0 | 21-09-2026 | **`RF-SP-061` estrena tripleta y pasa a `Tasks en revisión`**, por petición del responsable del proyecto («un endpoint para consultar mis clientes o los clientes de un vendedor») y siguiendo `RN-SEG-014`: `GET /users/me/clients` (autenticado) y `GET /users/{id}/clients` con **`users:read-clients`**, permiso propio que sembrará `V30` y con el que el catálogo pasa a **ciento trece** ([`security.md`](../security.md) v0.66.0). La ficha decide lo que se publica de cada cliente —**con `id`** y con estado, al contrario que `RF-SP-059` y por la razón inversa—, el orden (los más recientes primero), el filtro `origin` y que ni la estructura de mando ni D-22 intervienen. §6.1 y §9 nombran las dos rutas. **Y se anota la renumeración de los criterios de `RF-SP-059`** (`CA-SP-700` a `713`, [`requirements.md`](../requirements.md) v0.181.0): chocaban con los de `RF-SP-045` y `RF-SP-060`. | Responsable del proyecto |
| 1.67.0 | 21-09-2026 | **`RF-SP-061` pasa a `En desarrollo`**, construido el mismo día que su tripleta: `V30` siembra `users:read-clients` y las dos rutas de §9 están en el contrato. §6.1 y la ficha cambian de estado; ninguna regla cambia. | Responsable técnico |
| 1.68.0 | 21-09-2026 | **Nace `RF-SP-062`, autenticarse no autoriza nada** (`RN-SEG-015`, [`security.md`](../security.md) v0.67.0), por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Siete operaciones de `SP` que se atendían con solo el token reciben permiso propio, sembrado por `V31`: `users:read-own-profile` (`RF-SP-039`), `users:update-own-profile` (`044`), `users:change-own-password` (`037`), `users:read-own-sellers` (`059`), `users:read-own-clients` (`061`), `broker-accounts:read-own-team` (`056`) y `broker-accounts:read-team-member` (`055`, que abre la ruta y deja el alcance a `RN-SP-046` sin cambiarla). §6.1, §9 y las fichas nombran el código; `SP` llega a **sesenta** requerimientos registrados. | Responsable del proyecto |
| 1.69.0 | 21-09-2026 | **`RF-SP-062` pasa a `En desarrollo`**, construido el mismo día que su tripleta: `V31` siembra los once y los once `@PreAuthorize` están en el contrato. §6.1 y la ficha cambian de estado; ninguna regla cambia. | Responsable técnico |
| 1.70.0 | 21-09-2026 | **`SP` publica `CommercialReach`** (§8; D-22; `architecture.md` v0.34.0 §15.2): hasta dónde llega una persona —todo, su red en profundidad con ella dentro, o ella misma— por su tipo de rol y `user_supervisors`. Lo pide `MV` para `RF-MV-015` (`RN-MV-031`) y lo escribe esa tripleta en paquetes de `SP`. Ninguna regla de `SP` cambia; la estructura sigue siendo mando (`RN-SP-028` revertida) y los clientes no se recorren. | Responsable técnico |
| 1.71.0 | 21-09-2026 | **Nace el submódulo Equipos: `RF-SP-063` a `RF-SP-070`**, por decisión del responsable del proyecto («un CRUD de equipos, sirve para organizar el máximo rango de vendedores»). Un equipo reúne **managers** —la cúspide que `RN-SP-019` exime de superior— y con ellos, por `user_supervisors`, toda su red; **no manda, agrupa**, y no concede alcance (D-22). Ficha sin código ni país: nombre único funcional y parcial, descripción, estado. **Uno vigente por manager, con historial** (la forma de `user_supervisors`). Seis reglas nuevas, `RN-SP-050` a `RN-SP-055`: nombre único, solo managers, uno vigente con historial, el equipo `INACTIVO` no recibe y conserva, no se elimina con vigentes, y **la pertenencia sigue al rol** —enmienda de Art. I.7 a `RF-SP-029` y `RF-SP-031`, declarada para aplicarse en la tripleta de `RF-SP-070`—. Ocho permisos `teams:*` (`RN-SEG-014`; [`security.md`](../security.md) v0.69.0, catálogo a **ciento treinta y tres** con `V34`), ocho rutas en §9 bajo `/api/v1/teams`, dos tablas en §10.20 y §10.21 (`teams`, `team_members`, las creará `V33`) y diez restricciones en §10.8. §1.3 y §2 registran el submódulo; `SP` llega a **sesenta y ocho** requerimientos. Sin tripletas todavía: son el paso siguiente, por bloques. La versión 1.70.0 es de `RF-MV-015` (`CommercialReach`, §8), redactada el mismo día en otra rama. | Responsable del proyecto |
| 1.72.0 | 22-09-2026 | **El bloque 1 de Equipos estrena tripleta**: `RF-SP-063` (registrar), `RF-SP-064` (listado) y `RF-SP-065` (detalle) pasan a `Tasks en revisión` con `spec.md` y `plan.md` aprobados. Las tres deciden lo que el catálogo del módulo dejaba abierto y **ninguna regla cambia**: el alta crea las dos tablas (`V33`) y siembra los ocho permisos (`V34`, catálogo a ciento treinta y tres), nace **vacío y activo** y devuelve la forma del detalle; el listado ordena **alfabéticamente** —el equipo no tiene orden propio— y publica `memberCount` de los **vigentes** en la misma sentencia; el detalle devuelve **también el eliminado**, con su motivo, porque el listado ya lo enseña con `includeDeleted`, y **no pagina** los miembros porque la cúspide son unas pocas personas. Veinticinco criterios, `CA-SP-731` a `CA-SP-755`. Queda declarada como pendiente la lectura inversa —en qué equipo está una persona—, que no se cuela como filtro del listado. | Responsable del proyecto |
| 1.73.0 | 22-09-2026 | **El bloque 2 de Equipos estrena tripleta**: `RF-SP-066` (editar), `RF-SP-067` (estado) y `RF-SP-068` (eliminar) pasan a `Tasks en revisión`. **Ninguna regla cambia**, y las tres precisan lo que `RN-SP-050`, `RN-SP-053` y `RN-SP-054` dejaban implícito: el **nombre sí se corrige** —no hay código que lo respalde, y una errata del alta sería permanente— y renombrar revalida la unicidad; suspender es **idempotente, sin motivo y sin evento de seguridad** —un equipo no concede permisos, al contrario que un rol (`RF-SP-007`)— y **no vacía**, porque cerrar pertenencias movería la atribución de una red entera sin decisión explícita; eliminar **se rechaza con miembros vigentes**, al revés que `RF-AC-005` con una categoría, porque un filtro no sostiene nada y un equipo sostiene la pertenencia de la cúspide. El historial cerrado sobrevive a la baja y el nombre queda libre, sin que el equipo nuevo herede nada. Veintidós criterios, `CA-SP-756` a `CA-SP-777`. | Responsable del proyecto |
| 1.74.0 | 22-09-2026 | **El bloque 3 de Equipos estrena tripleta y el submódulo queda entero en `Tasks en revisión`**: `RF-SP-069` (asignar miembros) y `RF-SP-070` (retirar). **Ninguna regla cambia.** La asignación se opera **desde el equipo**, admite hasta cien personas con un solo motivo y es **toda la lista o nada**; solo entra la cúspide (`RN-SP-051`, resuelta con `CommercialStructure.esCuspide` y no por el código `MANAGER`, para que un rango nuevo por encima no exija tocar código); asignar a otro equipo **cierra** la pertenencia anterior (`RN-SP-052`) y quien ya estaba no pierde antigüedad; el equipo `INACTIVO` responde `409` y el eliminado `404`. El retiro decide al revés en un punto —«no pertenece» **sí** es error (`422`)— y **se admite sobre un equipo `INACTIVO`**, porque `RN-SP-053` prohíbe recibir y no soltar: si también prohibiera soltar, un equipo suspendido con gente no podría vaciarse ni, por `RN-SP-054`, eliminarse. `RF-SP-070` trae además la **enmienda de Art. I.7 de `RN-SP-055`** a `RF-SP-029` y `RF-SP-031`, construidos: quien deja de ser manager o se elimina sale de su equipo **en la misma transacción y con la misma correlación**, resuelto con un puerto publicado por `teams` y no con un evento, y **cambiar el estado de una persona no la saca**. Veinte criterios, `CA-SP-778` a `CA-SP-797`. | Responsable del proyecto |
| 1.75.0 | 22-09-2026 | **`RF-SP-063` queda construido y el submódulo Equipos estrena esquema, catálogo y ruta**: pasa a `En desarrollo` con sus once tareas `Hecha`. **Ninguna regla cambia** —lo que cambia es que ya existen—: `V33` crea `teams` y `team_members` con las diez restricciones de §10.8, entre ellas `uq_teams_name` funcional y parcial, que es la que hace cumplir `RN-SP-050` **cuando dos altas simultáneas se cruzan** y no solo cuando la comprobación previa llega a tiempo; `V34` siembra los ocho `teams:` y los asocia a `SUPERADMIN` y `ADMIN`, con el catálogo de permisos de **ciento veinticinco a ciento treinta y tres** y `ADMIN` de ciento diecinueve a **ciento veintisiete**. `POST /api/v1/teams` es la primera ruta del submódulo, y **las otras siete siguen sin existir aunque su permiso ya esté sembrado**: el catálogo entero entra en una migración porque ningún requerimiento posterior del submódulo toca el esquema, que era la forma con la que `RF-AC-001` estrenó Academia. Quince pruebas de integración y siete unitarias. `RF-SP-064` a `RF-SP-070` siguen en `Tasks en revisión`. | Responsable técnico |
| 1.76.0 | 22-09-2026 | **`RF-SP-064` pasa a `En desarrollo`**: el listado de equipos está construido y `GET /api/v1/teams` publicado en el contrato con **`teams:list`**. Nada de la ficha cambia —la operación, su permiso y sus reglas son los que §6.1 y §9 ya declaraban—; lo que cambia es el estado. Queda escrito lo que la construcción confirmó: `memberCount` cuenta **vigentes** (`RN-SP-052`) y no depende del estado del equipo (`RN-SP-053`), de modo que hasta que `RF-SP-069` exista todas las filas dicen cero sin que el contrato tenga que cambiar después. | Responsable técnico |
| 1.77.0 | 22-09-2026 | **`RF-SP-065` pasa a `En desarrollo`**: el detalle del equipo está construido y `GET /api/v1/teams/{id}` publicado con **`teams:read`**. Nada de la ficha cambia. Queda escrito lo que la construcción confirmó: el `status` que viaja con cada miembro es el de la **persona** —una pertenencia vigente no tiene estados—, y por eso `RN-SP-055` es visible desde aquí sin abrir la ficha de nadie; y el equipo eliminado se devuelve con su motivo, que es lo que hace coherente enseñarlo en el listado con `includeDeleted`. | Responsable técnico |
| 1.78.0 | 22-09-2026 | **`RF-SP-066` pasa a `En desarrollo`**: la corrección de un equipo está construida y `PATCH /api/v1/teams/{id}` publicado con **`teams:update`**. Nada de la ficha cambia. Queda escrito lo que la construcción confirmó: `RN-SP-053` se lee también en sentido contrario —un equipo `INACTIVO` **sí** se corrige, porque «no recibe miembros» no es «no se toca»—, y `RN-SP-050` se revalida en cada renombrado **excluyendo al propio equipo**, de modo que renombrarse al nombre que ya se tiene es válido y no compite contra sí mismo. | Responsable técnico |
| 1.79.0 | 22-09-2026 | **`RF-SP-059` publica el teléfono de empresa y el estado del vendedor** (Art. I.7), en las **dos** rutas. **Ninguna regla cambia** —ni `RN-SP-049`, ni el permiso, ni la ruta— y no hay migración: `company_phone` existe desde el 10-09-2026 y `status` desde el origen, de modo que las dos columnas salen del `JOIN users` que la proyección ya hacía y **el número de sentencias no se mueve**. Lo que cambia es una decisión de §6.2 de su spec: se revierte «no publica nada que el cliente no sepa ya» **en dos de los cuatro campos** que excluía; el identificador y el correo siguen fuera. `status` hace visible algo que ya ocurría y no se veía: un vendedor **eliminado** sigue saliendo, porque el vínculo sobrevive al vendedor, y hasta hoy su fila era idéntica a la de uno activo. `CA-SP-701` invertido, `CA-SP-798` nuevo. | Responsable del proyecto |
