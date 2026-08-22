# Requerimientos y Trazabilidad — NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `requirements.md` |
| Versión | 0.31.0 |
| Estado | Borrador |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 20-08-2026 |
| Última actualización | 22-08-2026 |
| Documento superior | `constitution.md` v0.5.0 |
| Documentos relacionados | `modules.md` v0.3.0 |

---

## 1. Propósito

Este documento es el **índice de los requerimientos** del sistema y su **matriz de trazabilidad**. Responde en qué estado está cada requerimiento y qué elementos técnicos lo implementan.

**No es el inventario de módulos.** Ese vive en [`modules.md`](modules.md), que es su autoridad única. Aquí se registran los requerimientos **de** esos módulos.

---

## 2. Documentos de requerimientos por módulo

Cada módulo del inventario tiene su documento en `docs/requirements/`, redactado con la plantilla de requerimientos por módulo.

| Módulo | Documento | Estado |
|---|---|---|
| `SP` — Sistema Principal | [`requirements/sp.md`](requirements/sp.md) | **Aprobado** |


Para agregar un módulo nuevo, seguir el procedimiento de [`modules.md` §8](modules.md#8-como-se-incorpora-un-modulo).

---

## 3. Nomenclatura de identificadores

### 3.1 Formatos

| Tipo | Formato | Ejemplo |
|---|---|---|
| Requerimiento funcional | `RF-[MÓDULO]-NNN` | `RF-SP-001` |
| Requerimiento no funcional | `RNF-[CATEGORÍA]-NNN` | `RNF-SEG-001` |
| Regla de negocio | `RN-[MÓDULO]-NNN` | `RN-SP-003` |
| Criterio de aceptación | `CA-[MÓDULO]-NNN` | `CA-SP-001` |
| Validación | `VAL-NNN` | `VAL-001` |
| Prueba | `T-[MÓDULO]-NNN` | `T-SP-001` |

Reglas:

- La numeración es correlativa **dentro de cada módulo** y **nunca se reutiliza**, ni siquiera si el requerimiento se descarta. Un identificador retirado se marca como `Descartado` en la matriz y su número queda consumido.
- Los submódulos **no** intervienen en el identificador ([`modules.md` §2.2](modules.md#22-por-que-los-submodulos-no-llevan-codigo-propio)): mover una funcionalidad entre submódulos no debe cambiar su identificador.
- El código de módulo, una vez usado en un identificador, **no se cambia jamás**.
- **Las reglas de negocio admiten dos espacios.** `RN-[MÓDULO]-NNN` para las reglas propias de un módulo, y `RN-SEG-NNN` para las **reglas transversales de seguridad**, que gobiernan la autorización en todo el sistema y alcanzan a varios módulos a la vez. `SEG` es aquí un espacio de reglas transversales, no un código de módulo; el prefijo `RN-` frente a `RNF-` lo desambigua de la categoría de requerimiento no funcional.
- **Las historias de usuario quedan fuera de la trazabilidad.** El documento de historias sirve para levantar requerimientos, pero la cadena trazable es `RF` → tripleta → Pull Request → código → prueba (Art. III.1). Una historia no equivale a un requerimiento funcional: suele originar varios.
- **No existe un identificador de especificación.** Al corresponder cada tripleta a exactamente un requerimiento (Art. I.2), un `SPEC-SP-001` solo podría referirse a `RF-SP-001`: serían dos identificadores para la misma cosa, y el segundo acabaría desincronizándose. La tripleta se referencia por su ruta: `docs/specs/sp/001-registrar-rol/`.

### 3.2 Categorías de requerimientos no funcionales

Las categorías **no son módulos**: comparten el espacio de nombres pero clasifican atributos de calidad según ISO/IEC 25010.

| Categoría | Atributo |
|---|---|
| `SEG` | Seguridad |
| `PERF` | Rendimiento |
| `USA` | Usabilidad |
| `MAN` | Mantenibilidad |
| `PORT` | Portabilidad |
| `FIA` | Fiabilidad |
| `COMP` | Compatibilidad |

!!! note "Sobre `SEG`"

    `SEG` se usa como categoría de requerimiento no funcional (`RNF-SEG-001`) y también como prefijo de las reglas de negocio de seguridad en `security.md` (`RN-SEG-003`). Son espacios distintos y el prefijo `RNF-` o `RN-` los desambigua, pero conviene tenerlo presente al buscar.

---

## 4. Matriz de trazabilidad

Implementa el Art. III.1. Se actualiza **como parte del cambio**, no después (Art. III.6).

| ID | Requerimiento | Módulo | Tripleta | Issue | PR | Pruebas | Estado |
|---|---|---|---|---|---|---|---|
| `RF-SP-001` | Registrar rol | `SP` | [`specs/sp/001-registrar-rol/`](specs/sp/001-registrar-rol/tasks.md) | Pendiente de crear | `feature/registrar-rol` | 32 unitarias y 60 de integración en verde (`./mvnw clean verify`, 22-08-2026) | **En desarrollo** |
| `RF-SP-002` | Consultar roles | `SP` | `specs/sp/002-consultar-roles/` | — | — | — | **Plan aprobado** |
| `RF-SP-003` | Consultar detalle de un rol | `SP` | `specs/sp/003-consultar-detalle-rol/` | — | — | — | **Plan aprobado** |
| `RF-SP-004` | Editar rol | `SP` | `specs/sp/004-editar-rol/` | — | — | — | **Plan aprobado** |
| `RF-SP-005` | Asignar permisos a un rol | `SP` | `specs/sp/005-asignar-permisos/` | — | — | — | **Plan aprobado** |
| `RF-SP-006` | Revocar permisos de un rol | `SP` | `specs/sp/006-revocar-permisos/` | — | — | — | **Plan aprobado** |
| `RF-SP-007` | Cambiar el estado de un rol | `SP` | `specs/sp/007-cambiar-estado-rol/` | — | — | — | **Plan aprobado** |
| `RF-SP-008` | Cambiar el rol padre de un rol | `SP` | `specs/sp/008-cambiar-rol-padre/` | — | — | — | **Plan aprobado** |
| `RF-SP-009` | Eliminar rol | `SP` | `specs/sp/009-eliminar-rol/` | — | — | — | **Plan aprobado** |
| `RF-SP-010` | Consultar catálogo de permisos | `SP` | [`specs/sp/010-consultar-permisos/`](specs/sp/010-consultar-permisos/tasks.md) | Pendiente de crear | — | 14 unitarias y 38 de integración en verde (`./mvnw clean verify`, 22-08-2026) | **En implementación** |
| `RF-SP-011` | Consultar auditoría de cambios | `SP` | `specs/sp/011-consultar-auditoria-cambios/` | — | — | — | **Plan aprobado** |
| `RF-SP-012` | Consultar auditoría de eliminación | `SP` | `specs/sp/012-consultar-auditoria-eliminacion/` | — | — | — | **Plan aprobado** |
| `RF-SP-013` | Consultar auditoría de error | `SP` | `specs/sp/013-consultar-auditoria-error/` | — | — | — | **Plan aprobado** |
| `RF-SP-014` | Consultar auditoría de seguridad | `SP` | `specs/sp/014-consultar-auditoria-seguridad/` | — | — | — | **Plan aprobado** |
| `RF-SP-015` | Consultar detalle de un permiso | `SP` | `specs/sp/015-consultar-detalle-permiso/` | — | — | — | **Plan aprobado** |
| `RF-SP-016` | Registrar membresía | `SP` | `specs/sp/016-registrar-membresia/` | — | — | — | **Plan aprobado** |
| `RF-SP-017` | Consultar membresías | `SP` | `specs/sp/017-consultar-membresias/` | — | — | — | **Plan aprobado** |
| `RF-SP-018` | Consultar detalle de una membresía | `SP` | `specs/sp/018-consultar-detalle-membresia/` | — | — | — | **Plan aprobado** |
| `RF-SP-019` | Consultar monedas | `SP` | `specs/sp/019-consultar-monedas/` | — | — | — | **Plan aprobado** |
| `RF-SP-020` | Registrar país | `SP` | `specs/sp/020-registrar-pais/` | — | — | — | **Plan aprobado** |
| `RF-SP-021` | Consultar países | `SP` | `specs/sp/021-consultar-paises/` | — | — | — | **Plan aprobado** |
| `RF-SP-022` | Cambiar el estado de un país | `SP` | `specs/sp/022-cambiar-estado-pais/` | — | — | — | **Plan aprobado** |
| `RF-SP-023` | Cambiar el estado de una moneda | `SP` | `specs/sp/023-cambiar-estado-moneda/` | — | — | — | **Plan aprobado** |
| `RF-SP-024` | Registrar usuario | `SP` | `specs/sp/024-registrar-usuario/` | — | — | — | **Plan aprobado** |
| `RF-SP-025` | Consultar usuarios | `SP` | `specs/sp/025-consultar-usuarios/` | — | — | — | **Plan aprobado** |
| `RF-SP-026` | Consultar detalle de un usuario | `SP` | `specs/sp/026-consultar-detalle-usuario/` | — | — | — | **Plan aprobado** |
| `RF-SP-027` | Editar usuario | `SP` | `specs/sp/027-editar-usuario/` | — | — | — | **Plan aprobado** |
| `RF-SP-028` | Cambiar el estado de un usuario | `SP` | `specs/sp/028-cambiar-estado-usuario/` | — | — | — | **Plan aprobado** |
| `RF-SP-029` | Eliminar usuario | `SP` | `specs/sp/029-eliminar-usuario/` | — | — | — | **Plan aprobado** |
| `RF-SP-030` | Asignar roles a un usuario | `SP` | `specs/sp/030-asignar-roles-usuario/` | — | — | — | **Plan aprobado** |
| `RF-SP-031` | Retirar roles de un usuario | `SP` | `specs/sp/031-retirar-roles-usuario/` | — | — | — | **Plan aprobado** |
| `RF-SP-032` | Asignar membresía a un usuario | `SP` | `specs/sp/032-asignar-membresia-usuario/` | — | — | — | **Plan aprobado** |
| `RF-SP-033` | Retirar la membresía de un usuario | `SP` | `specs/sp/033-retirar-membresia-usuario/` | — | — | — | **Plan aprobado** |
| `RF-SP-034` | Iniciar sesión | `SP` | `specs/sp/034-iniciar-sesion/` | — | — | — | **Spec aprobada** |
| `RF-SP-035` | Refrescar el token de acceso | `SP` | `specs/sp/035-refrescar-token/` | — | — | — | **Spec aprobada** |
| `RF-SP-036` | Cerrar sesión | `SP` | `specs/sp/036-cerrar-sesion/` | — | — | — | **Spec aprobada** |
| `RF-SP-037` | Cambiar la propia contraseña | `SP` | `specs/sp/037-cambiar-contrasena/` | — | — | — | **Spec aprobada** |
| `RF-SP-038` | Restablecer la contraseña de un usuario | `SP` | `specs/sp/038-restablecer-contrasena/` | — | — | — | **Spec aprobada** |
| `RF-SP-039` | Consultar el propio perfil | `SP` | `specs/sp/039-consultar-perfil-propio/` | — | — | — | **Spec aprobada** |
| `RF-SP-040` | Restablecer la propia contraseña olvidada | `SP` | `specs/sp/040-restablecer-contrasena-olvidada/` | — | — | — | **Spec aprobada** |
| `RF-SP-041` | Asignar o cambiar el superior comercial de un usuario | `SP` | `specs/sp/041-asignar-superior-comercial/` | — | — | — | **Spec aprobada** |
| `RF-SP-042` | Consultar el equipo a cargo de un usuario | `SP` | `specs/sp/042-consultar-equipo-a-cargo/` | — | — | — | **Spec aprobada** |


**Estados**, que reflejan las tres compuertas del Art. I.6:

| Estado | Significa |
|---|---|
| `Pendiente` | Registrado, sin `spec.md` |
| `Spec en revisión` | `spec.md` escrita, en su Pull Request |
| `Spec aprobada` | Compuerta 1 superada; puede escribirse `plan.md` |
| `Plan aprobado` | Compuerta 2 superada; pueden escribirse las tareas |
| `Tasks aprobadas` | Compuerta 3 superada; puede escribirse código |
| `En desarrollo` | Implementación en curso |
| `Implementado` | Integrado y con la definición de terminado cumplida |
| `Descartado` | Retirado; su número queda consumido |

Un requerimiento solo pasa a `Implementado` cuando cumple **todas** las condiciones de la definición de terminado (§16 de la constitución).

---

## 5. Estado general

| Indicador | Valor |
|---|---|
| Requerimientos registrados | 42 |
| Requerimientos con `spec.md` redactada | 42 |
| Requerimientos con `spec.md` aprobada | 42 |
| Requerimientos con `plan.md` aprobado | 33 |
| Requerimientos implementados | 0 |

Los 42 corresponden al módulo `SP`, que absorbió los usuarios, sus roles y su acceso al retirarse `USR` (`modules.md` v0.9.0). **Los cuarenta y dos tienen `spec.md` y los cuarenta y dos han superado la primera compuerta** del Art. I.6: treinta y ocho el 21-08-2026, y `RF-SP-041`, `RF-SP-039`, `RF-SP-042` y `RF-SP-040` el 22-08-2026. No queda ninguna pregunta abierta en el módulo. **Diez requerimientos superaron la segunda compuerta el 22-08-2026** —`RF-SP-024` a `RF-SP-033`: todo el submódulo de Usuarios y el de asignación de roles y membresías—, con lo que **restan nueve `plan.md`**: de `RF-SP-034` a `RF-SP-042`, y uno de ellos —el de `RF-SP-040`— no puede escribirse hasta cerrar **D-23**.

**`RF-SP-040` está aprobada pero todavía no es implementable.** El 22-08-2026 se decidió la **forma** del canal de envío —infraestructura transversal con puerto publicado, ni submódulo ni módulo aparte (`architecture.md` §15.1)—, y eso bastaba para cerrar su especificación. Falta el **mecanismo**: proveedor, desacople, reintentos y rebotes, registrados como **D-23**. Su `plan.md` no puede escribirse antes de esa decisión, porque la mitad del requerimiento consiste en hacer llegar algo a alguien. Es el **único** de los cuarenta y dos con la segunda compuerta bloqueada por una decisión pendiente.

Las diecisiete especificaciones que faltaban se redactaron el 21-08-2026 con **sesenta y cuatro preguntas abiertas**, y las sesenta y cuatro quedaron resueltas. Lo que sigue es lo que esas resoluciones fijaron para todo el módulo, porque ninguna se agota en su propio requerimiento.

**Cambio de estado de un catálogo** (`RF-SP-022`, `RF-SP-023`). Se registra solo en la auditoría de cambios y no en la de seguridad, porque no hay privilegio en juego; no se exige motivo, porque el Art. V.13 lo obliga solo en las eliminaciones; el elemento se desactiva aunque tenga datos que lo referencien o importes expresados en él, porque impedirlo dejaría sin salida justo al caso que más urge retirar; y no se devuelve el conteo de esas referencias. No se crea todavía un requerimiento para cambiar la moneda por defecto: mientras haya una sola no hay nada que elegir.

**Identidad de las personas** (`RF-SP-024`). Cada persona lleva **dos identidades y con cualquiera de las dos inicia sesión**: el correo, que puede corregirse y cuyo cambio emite evento de seguridad por ser una vía de acceso, y el **nombre de usuario**, inmutable, sin el carácter `@` —lo que impide que un valor se confunda con el otro— y que es el que la auditoría referencia. El alta fija la contraseña inicial y deja la cuenta `ACTIVO` y **marcada para cambio obligatorio**; `PENDIENTE` queda declarado y sin usar.

**El rol de consumidor y la membresía son inseparables** (`RN-SP-018`, nacida al aprobar `RF-SP-033`). No existe el estado «consumidor sin nivel»: se conceden juntos —`RF-SP-024` y `RF-SP-030` exigen indicar la membresía al dar el primer rol de consumidor— y se sueltan juntos —`RF-SP-031` la retira al quitar el último—. Resolverlo de otro modo producía un bloqueo mutuo del que nadie podía salir, y obligó a devolver a su compuerta tres especificaciones ya aprobadas (Art. I.7). La membresía admite además **fecha de fin**, cuya vigencia se evalúa al consultarla, sin proceso que la retire.

**Retirar el acceso es inmediato; concederlo puede esperar** (`RF-SP-028`, `RF-SP-030`, `RF-SP-031`). Desactivar, bloquear o retirar un rol revoca las sesiones de la persona y su token de acceso deja de admitirse; asignar un rol acepta la latencia de hasta quince minutos que `security.md` §4.5 declara. `INACTIVO` y `BLOQUEADO` pasan a tener significados separados —organizativo frente a seguridad—, el bloqueo admite origen **manual** sin expiración, y retirar el acceso exige motivo mientras que devolverlo no lo admite.

**Sesiones** (`RF-SP-034` a `RF-SP-038`). Bloqueo a los cinco intentos con progresión y techo; la cuenta bloqueada recibe respuesta distinta, como excepción consciente al mensaje genérico; cada revocación registra su **motivo**, y solo la rotación dispara la alarma de robo; la familia de tokens tiene una **duración máxima de sesión** contada desde el inicio; el cierre de sesión es público y admite cerrar todas; y la credencial provisional que fija un administrador **caduca**.

**Quién está a cargo de quién** (`RF-SP-041`, `RF-SP-042`, registrados el 22-08-2026). La estructura comercial **persona → persona** deja de estar aparcada y pasa a tener tabla propia, `user_supervisors`, con historial. Todo vendedor declara un superior salvo la cúspide, y ese superior debe portar el rol padre inmediato del rol del subordinado (`RN-SP-020`): la jerarquía de personas ya no puede contradecir la de roles. Obligó a devolver a su compuerta **cinco** especificaciones aprobadas (Art. I.7): las tres que ya tocó `RN-SP-018` —`RF-SP-024`, `RF-SP-030` y `RF-SP-031`— más `RF-SP-028` y `RF-SP-029`, porque `RN-SP-022` impide retirar el acceso o eliminar a quien tiene gente a cargo. **Lo que no cambia es el alcance de datos:** registrar la estructura no concede a nadie visibilidad sobre los datos de su equipo, y **D-22 sigue abierta**.

**Lo que la interfaz puede saber de quien mira** (`RF-SP-039`, aprobada el 22-08-2026). El sistema publica por fin los **permisos efectivos del actor** en un endpoint sin permiso exigido y **sin parámetro alguno** —el actor y solo el actor—, que es lo que permite a una interfaz decidir qué mostrar sin duplicar en el navegador una regla que vive en el servidor. Cierra el hallazgo `DF-04` de la documentación del frontend. Fija además tres límites del módulo: el perfil propio devuelve **solo el superior comercial**, nunca el equipo, porque a quién reporta uno es un dato del actor y quiénes dependen de uno es un conjunto de terceros —la distinción que sostiene la reserva de D-22—; el último inicio de sesión se devuelve **como dato informativo y no como señal de intrusión**, porque `RF-SP-034` sobrescribe ese valor al entrar y conservar el anterior habría obligado a reabrirla (Art. I.7); y **la autoedición del propio perfil no existe ni se registra**: quien necesite corregir su correo o su nombre se lo pide a quien administra usuarios (`RF-SP-027`). Este último es un hueco aceptado a conciencia, con su síntoma declarado —tráfico de soporte por correcciones triviales— como condición para reabrirlo.

**Lo que sigue abierto.** El canal de envío ya tiene forma decidida, pero **no tiene mecanismo**: mientras **D-23** no se cierre, no se envía nada, y de ahí siguen colgando las mismas tres cosas —el `plan.md` de `RF-SP-040`, la verificación del correo al cambiarlo (`RF-SP-027`, anotada como riesgo con su condición de disparo) y el aviso a quien le restablecen la contraseña (`RF-SP-038`)—. Mientras siga así, quien olvide su contraseña depende de que un administrador se la restablezca conociéndola durante un tiempo. `RF-SP-027` y `RF-SP-038` **no se reabren hoy** (Art. I.7): se decidió el 22-08-2026 revisarlas al escribir sus planes, cuando el canal exista de verdad.

Sigue abierta además **D-22** —el alcance de datos por persona—, que es lo que mantiene fuera de `RF-SP-042` el árbol descendente y la variante «mi equipo», y fuera de `RF-SP-039` cualquier lectura hacia abajo.


**`RF-SP-001` cruza la tercera compuerta y se implementa entero.** Sus `tasks.md` se aprobaron el 22-08-2026 y sus **veintidós tareas están hechas**: las cuatro migraciones (`V4` a `V7`), la infraestructura compartida que todo módulo posterior reutiliza —los cuatro registros de auditoría con su mecánica transaccional, la jerarquía de errores, el generador de identificadores, el contexto de origen de la petición y la publicación del actor autenticado— y el caso de uso completo con sus once criterios de aceptación en verde. **No pasa a `Implementado`** por el bloqueo 3 de sus tareas: `RN-SEG-010` lee los permisos efectivos del `Authentication` y no de la base de datos, porque `users` y `user_roles` los crea `RF-SP-024`. Al ejecutarlas se resolvió además la contradicción que `architecture.md` §5.1 dejó planteada el 22-08-2026 —`T-11`, `T-12` y `T-20` estaban escritas sobre la disposición de capas anterior— por la vía que ese documento admite, y se corrigió `ck_deletion_reason`, cuyo `OR` con un motivo nulo evaluaba a `NULL` y hacía que un `CHECK` aceptara la fila.

**`RF-SP-010` fue el primero que cruzó la tercera compuerta.** Sus `tasks.md` se aprobaron el 22-08-2026 y su implementación está en curso: `T-01` a `T-08`, `T-12` y `T-14` **hechas** —la suite completa termina en verde el 22-08-2026 con **14 pruebas unitarias y 38 de integración**, con lo que el bloqueo del entorno queda cerrado—, y `T-09` a `T-11` bloqueadas por su dependencia declarada de `RF-SP-001`, que arrastra consigo a `T-13`. Al aprobar sus tareas se enmendó `plan.md` §2 para sembrar **veinticuatro** permisos y no veintitrés: faltaba `users:assign-supervisor`, que `requirements/sp.md` §9 declara desde el 22-08-2026 y que este plan, aprobado el 21, no alcanzó a recoger.

De `RF-SP-001` a `RF-SP-024` han superado además la **segunda** compuerta: los veintitrés primeros `plan.md` se aprobaron el 21-08-2026 —y sus `tasks.md` están escritas y **en revisión**, a la espera de la tercera, salvo las de `RF-SP-010`—, y el de `RF-SP-024` el 22-08-2026, todavía sin `tasks.md`. Ese último es el que **crea el sujeto del módulo**: hasta él, `SP` tenía roles, permisos y catálogos, y el `actor_id` de los cuatro registros de auditoría no resolvía a ninguna fila. La matriz arrastraba nueve filas —`RF-SP-014` a `RF-SP-019`, `RF-SP-021`, `RF-SP-022` y `RF-SP-023`— marcadas como «Spec aprobada» cuando sus planes ya estaban aprobados en su propio documento; se corrigió el 22-08-2026, que es lo que el Art. III.6 exige hacer **con** el cambio y no después. El de `RF-SP-020` es el primero que toca `countries`: crea la tabla en `V16`, le añade `updated_at` —que `requirements/sp.md` §10.6 omitía pese al Art. V.7— y declara la unicidad del nombre sobre `f_unaccent(lower(name))` y no sobre el literal, porque `RN-SP-009` no admite edición y un `Panamá`/`Panama` duplicado sería permanente. El de `RF-SP-011` amplió `PageResponse<T>` con `totalIsExact`, porque los cuatro listados de auditoría cuentan hasta un techo configurable en lugar de recorrer una tabla que crece sin límite; los listados de conteo exacto lo devuelven siempre en `true`. La aprobación del plan de `RF-SP-010` corrió la numeración de las migraciones —`V1` pasa a ser las funciones compartidas y todas las demás avanzan un número—, porque ese requerimiento se implementa primero y necesita `f_unaccent` antes que `RF-SP-002`, que era quien la creaba. Enmendó también `security.md` §4.4, cuyo catálogo omitía cuatro bloques de permisos, y fijó que cada módulo siembra los suyos.

La aprobación de los nueve primeros devolvió además cinco especificaciones a su compuerta (Art. I.7): `RF-SP-005`, `RF-SP-008` y `RF-SP-009` ganaron `EX-006` —ninguna declaraba la excepción del rol inexistente, que los planes referenciaban con el código de otra— y `RF-SP-006` ganó `VAL-004`, el límite de cien permisos por petición que su plan aplicaba sin respaldo. Enmendaron también `requirements/sp.md` (`CLIENTE` y la longitud de `description`), `architecture.md` (serie `INT-nnn`) y `development-guide.md` (`422` y convención `f_`).

El inventario y el estado de los módulos se consultan en [`modules.md` §4](modules.md#4-inventario-de-modulos).

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 20-08-2026 | Creación inicial con catálogo de módulos y matriz de trazabilidad. | Responsable técnico |
| 0.2.0 | 20-08-2026 | El inventario de módulos se traslada a `modules.md`, que pasa a ser su autoridad única. Este documento conserva nomenclatura y trazabilidad. | Responsable técnico |
| 0.3.0 | 20-08-2026 | Se retira el identificador `SPEC-[MÓDULO]-NNN` por redundante con el `RF`. La matriz refleja las tres compuertas de aprobación de la tripleta. | Responsable técnico |
| 0.4.0 | 20-08-2026 | §3.1 documenta el espacio de reglas transversales `RN-SEG` y deja las historias de usuario fuera de la cadena de trazabilidad. | Responsable técnico |
| 0.5.0 | 20-08-2026 | Se registran en la matriz los 21 requerimientos del módulo `SP`, cuyo documento queda aprobado. | Responsable técnico |
| 0.6.0 | 20-08-2026 | Los 21 requerimientos de `SP` pasan a estado «Spec en revisión»: su especificación está redactada y pendiente de la primera compuerta. | Responsable técnico |
| 0.7.0 | 21-08-2026 | De `RF-SP-010` a `RF-SP-021` superan la primera compuerta: sus 35 preguntas abiertas quedan resueltas y sus especificaciones aprobadas. Se registran `RF-SP-022` y `RF-SP-023`, derivados de esas resoluciones. | Responsable técnico |
| 0.8.0 | 21-08-2026 | Se registran los quince requerimientos que `SP` absorbe al desaparecer `USR`. | Responsable técnico |
| 0.9.0 | 21-08-2026 | Se redactan las diecisiete especificaciones que faltaban, de `RF-SP-022` a `RF-SP-038`, y pasan a estado «Spec en revisión» con 64 preguntas abiertas. Todo requerimiento registrado tiene ya su `spec.md`. | Responsable técnico |
| 0.10.0 | 21-08-2026 | `RF-SP-022` y `RF-SP-023` superan la primera compuerta. Entre ambas fijan el criterio del módulo para el cambio de estado de un catálogo: solo auditoría de cambios, sin motivo, sin conteo de referencias y sin rechazar el elemento ya referenciado. Se descarta por ahora un requerimiento para cambiar la moneda por defecto. | Responsable técnico |
| 0.11.0 | 21-08-2026 | `RF-SP-024` supera la primera compuerta y fija la identidad de las personas: nombre de usuario y correo, ambos válidos para iniciar sesión, el primero inmutable y sin `@`. El alta fija la contraseña inicial y marca la cuenta para cambio obligatorio. Enmienda `RN-SP-016`, que se contradecía a sí misma (`requirements/sp.md` v1.4.0). | Responsable técnico |
| 0.12.0 | 21-08-2026 | `RF-SP-025` y `RF-SP-026` superan la primera compuerta. El listado devuelve los roles completos de cada persona y admite búsqueda por fragmento de correo; el detalle resuelve los permisos efectivos y no expone el contador de intentos fallidos. Se registra `RF-SP-039`, consultar el propio perfil, derivado de esa segunda aprobación. | Responsable técnico |
| 0.13.0 | 21-08-2026 | `RF-SP-027` supera la primera compuerta. El correo es editable y el anterior queda **liberado** —la reserva permanente alcanza solo a la eliminación—, su cambio emite evento de seguridad por ser una vía de acceso, y la verificación del correo queda como riesgo con condición de disparo. Enmienda `RN-SP-016` y el catálogo cerrado de `security.md` §8.1. | Responsable técnico |
| 0.14.0 | 21-08-2026 | `RF-SP-028` supera la primera compuerta. Se admite el **bloqueo manual** de una cuenta, sin expiración, y `INACTIVO` y `BLOQUEADO` pasan a tener significados separados —organizativo frente a seguridad—. Retirar el acceso exige motivo; devolverlo no lo admite. Enmienda `RN-SP-001`, que se mide sobre usuarios **activos**, y el catálogo cerrado de `security.md` §8.1. | Responsable técnico |
| 0.15.0 | 21-08-2026 | `RF-SP-029` supera la primera compuerta. Eliminar a una persona **retira sus roles y su membresía**, con lo que un rol que solo portaba alguien eliminado vuelve a poder borrarse sin que `RF-SP-009` necesite enmienda. La auditoría de eliminación pasa a ser la única fuente de qué tenía esa persona, y captura el estado **antes** de retirar las asignaciones. La anonimización queda como riesgo por decidir en `docs/security/`. | Responsable técnico |
| 0.16.0 | 21-08-2026 | `RF-SP-030` y `RF-SP-031` superan la primera compuerta. `RN-SEG-010` se comprueba **comparando permisos** y alcanza también al retiro. Asignar no revoca sesiones y retirar sí: asimetría deliberada que enmienda la tabla de latencias de `security.md` §4.5. Sin tope de roles por persona; el riesgo del tamaño del token se anota en el plan. | Responsable técnico |
| 0.17.0 | 21-08-2026 | `RF-SP-032` y `RF-SP-033` superan la primera compuerta. La membresía admite **fecha de fin**, cuya vigencia se evalúa al consultarla sin proceso que la retire. Nace `RN-SP-018` —todo consumidor tiene membresía—, que devuelve a su compuerta `RF-SP-024`, `RF-SP-030` y `RF-SP-031` \(Art. I.7\). `RF-SP-033` queda como operación correctiva. | Responsable técnico |
| 0.18.0 | 21-08-2026 | `RF-SP-034` supera la primera compuerta. Se entra con nombre de usuario **o** correo; bloqueo a los cinco intentos con progresión y techo; la cuenta bloqueada recibe respuesta distinta \(excepción consciente\); la contraseña con cambio obligatorio **autentica y advierte**; sin tope de sesiones simultáneas. | Responsable técnico |
| 0.19.0 | 21-08-2026 | `RF-SP-036`, `RF-SP-037` y `RF-SP-038` superan la primera compuerta, y con ellas **las treinta y ocho especificaciones del módulo quedan aprobadas**: no queda ninguna pregunta abierta. El cierre de sesión pasa a público y admite cerrar todas; los intentos fallidos al cambiar la contraseña cuentan para el bloqueo; la credencial provisional caduca. Se registra `RF-SP-040`, restablecer la propia contraseña olvidada. | Responsable técnico |
| 0.20.0 | 22-08-2026 | Se registran `RF-SP-041` y `RF-SP-042`: la **estructura comercial persona → persona** deja de estar aparcada y pasa a `user_supervisors`, con su historial y sus reglas `RN-SP-019` a `RN-SP-022`. Devuelve a su compuerta cinco specs aprobadas \(Art. I.7\): `RF-SP-024`, `RF-SP-028`, `RF-SP-029`, `RF-SP-030` y `RF-SP-031`. **Corrección de la matriz:** nueve filas —`RF-SP-014` a `RF-SP-019`, `RF-SP-021` a `RF-SP-023`— figuraban en «Spec aprobada» con sus planes ya aprobados; pasan a «Plan aprobado», y §5 incorpora el indicador de planes aprobados para que el desfase vuelva a ser visible de un vistazo. D-22 sigue abierta: la estructura se registra, no concede alcance. | Responsable técnico |
| 0.21.0 | 22-08-2026 | `RF-SP-041` supera la primera compuerta, y con ella son **treinta y nueve** las specs aprobadas. El cambio de superior exige **motivo**, **no admite fecha declarada** —rige desde que se ejecuta— y **no emite evento de seguridad**, con condición de disparo anotada para cuando D-22 haga depender el alcance de datos de esta relación. `RN-SP-017` se amplía al cambio de superior: es la tercera operación en que el actor tendría interés directo sobre su propia cuenta. | Responsable técnico |
| 0.22.0 | 22-08-2026 | Se redactan las tres especificaciones que faltaban —`RF-SP-039`, `RF-SP-040` y `RF-SP-042`—, con lo que **los cuarenta y dos requerimientos del módulo tienen `spec.md`**. Las tres quedan **en revisión**, con doce preguntas abiertas entre ellas. `RF-SP-040` se redacta sabiendo que **no puede aprobarse**: depende del canal de envío, que no existe en ningún requerimiento y cuya forma de incorporación —RNF, submódulo de `SP` o módulo aparte— es su pregunta 1. | Responsable técnico |
| 0.23.0 | 22-08-2026 | `RF-SP-039` supera la primera compuerta, y con ella son **cuarenta** las specs aprobadas. El perfil propio publica los **permisos efectivos del actor** sin exigir permiso y sin admitir parámetro, con lo que la interfaz deja de tener que deducirlos: cierra el hallazgo `DF-04` del frontend. Devuelve **solo el superior comercial**, nunca el equipo —la distinción que sostiene la reserva de D-22—; el último inicio de sesión queda como dato **informativo**, no como señal de intrusión, al comprobarse que `RF-SP-034` lo sobrescribe en cada entrada y que conservar el anterior habría obligado a reabrirla \(Art. I.7\); y la **autoedición del propio perfil no existe ni se registra**, hueco aceptado a conciencia con su síntoma declarado. | Responsable técnico |
| 0.24.0 | 22-08-2026 | `RF-SP-042` supera la primera compuerta, y con ella son **cuarenta y una** las specs aprobadas: solo `RF-SP-040` queda por aprobar. Las cuatro resoluciones fueron restrictivas y en la misma dirección: **solo lo vigente** —sin historial de superiores—, **sin conteo de la rama indirecta** ni siquiera como número, **sin filtros** sobre el equipo directo, y la persona eliminada se trata como inexistente. El criterio común es no dejar que esta consulta se convierta en el sustituto informal del modelo de alcance que falta (**D-22**). Queda un hueco declarado: el historial que `RN-SP-021` conserva **no tiene ninguna vía de lectura** hasta que exista la auditoría del reparto de comisiones. | Responsable técnico |
| 0.25.0 | 22-08-2026 | `RF-SP-040` supera la primera compuerta y con ella **las cuarenta y dos especificaciones del módulo quedan aprobadas**: no queda ninguna pregunta abierta en todo `SP`. La resolución que lo desbloqueó no vive aquí: el **canal de envío** queda decidido como **infraestructura transversal con puerto publicado** \(`architecture.md` §15.1\), ni submódulo ni módulo propio, y cada módulo declara qué envía y cuándo. El envío es **desacoplado de la respuesta** \(`RNF-FIA-001`\) por seguridad, no por rendimiento. La vigencia del permiso temporal se declara en `security.md` §3.2. El restablecimiento **avisa al titular**, con lo que se cierra la condición de disparo de `RF-SP-038`. **Queda D-23**: el mecanismo concreto de envío, sin el cual no puede escribirse el `plan.md` de este requerimiento. | Responsable técnico |
| 0.26.0 | 22-08-2026 | **`RF-SP-024` supera la segunda compuerta**: es el primer plan del bloque de usuarios y el primero que crea el sujeto del módulo. Cinco migraciones, `V18` a `V22`: `users`, `user_roles`, `user_memberships`, `user_supervisors` y la **semilla del superadministrador**, cuya credencial entra por marcador de posición de Flyway —sin él la migración falla, en lugar de arrancar con una contraseña conocida—. Decisiones que alcanzan al módulo entero: la unicidad de nombre de usuario y correo es **total y no parcial**, porque `RN-SP-016` no libera nada al eliminar; el nombre de usuario **conserva su caja** y su unicidad va sobre `lower(username)`, lo que obliga a `RF-SP-034` a comparar sin distinguir mayúsculas; `RN-SEG-010` se **extrae a un componente único** compartido con `RF-SP-005` y `RF-SP-030`; y la política de contraseña gana la prohibición de contener el nombre de usuario o el correo \(`security.md` §3.2\). Enmienda `security.md` §8.1 —**el alta de un usuario entra en el catálogo cerrado de eventos**— y `requirements/sp.md` §10. **Corrige dos planes aprobados**: `user_roles` la crea este requerimiento y no `RF-SP-030`, porque el alta ya escribe asignaciones. | Responsable técnico |
| 0.27.0 | 22-08-2026 | **Revisión de los seis planes reabiertos** —`RF-SP-004` a `RF-SP-009`— y su reaprobación. La corrección que motivó la reapertura era real y queda verificada: `ck_audit_error_log_status` \(`RF-SP-013` §2\) prohíbe `400`, `401`, `403` y `404` en `audit_error_log`, y los seis mandaban ahí el `403` de `RN-SEG-011` y el `404` del rol inexistente; el `403` va a `audit_security_log` con severidad **Alta** y lo emite el caso de uso, no la capa de seguridad. **Un defecto nuevo en `RF-SP-005`:** su §2 afirmaba que la clave primaria compuesta absorbía la asignación concurrente «porque la segunda encuentra la fila ya presente». No es cierto —PostgreSQL lanza `23505` al confirmar la primera, y el caso salía como `500`—; se resuelve con `INSERT … ON CONFLICT DO NOTHING` en sentencia nativa, y `RF-SP-005` y `RF-SP-006` ganan la prueba de concurrencia que sus specs exigían en §13 y sus planes no tenían. **`RF-SP-014` se reabre y se reaprueba**: su `CHECK` de `event_type` era un dominio cerrado de dieciséis códigos que no incluía `USER_CREATED` —que `RF-SP-024` empieza a emitir— ni `EMAIL_CHANGED` —declarado en `security.md` §8.1 desde `RF-SP-027`—; con dieciocho, el evento del alta deja de ser una violación de integridad dentro de la transacción de auditoría. | Responsable técnico |
| 0.28.0 | 22-08-2026 | **Cinco planes nuevos y las tareas de `RF-SP-024`**: el submódulo de **Usuarios** queda con su tripleta completa \(`RF-SP-024` a `RF-SP-029`\). Cuatro decisiones alcanzan al módulo entero. **`deleted_at` nace con `users`, no con `RF-SP-029`**: `architecture.md` §6.4 la declara columna obligatoria de toda tabla de negocio y **diez requerimientos la leen antes de que alguien la escriba** —`RF-SP-003` y `RF-SP-009` ya la daban por existente—; corrige el `plan.md` de `RF-SP-024` \(Art. I.7\). **Las tres columnas de control de acceso son de `RF-SP-034`**, no repartidas con `RF-SP-028`, que solo las lee y las limpia. **`RF-SP-034` precede a `RF-SP-026`, `RF-SP-028` y `RF-SP-029`**: sin `refresh_tokens` no hay sesiones que revocar y tres criterios no son verificables; §6.1 de `requirements/sp.md` gana las precedencias del bloque. Y **`USER_DELETED` entra en el catálogo cerrado** de `security.md` §8.1 y en el `CHECK` de `V4`, que pasa a diecinueve literales: la baja de una persona no estaba en el catálogo pese a que la de un rol sí, y `RF-SP-014` §2 la había resuelto reutilizando `USER_STATUS_CHANGED`. Otras decisiones: el detalle de una persona resuelve sus permisos efectivos **con el mismo componente que autoriza**, para que la pantalla no pueda describir un sistema distinto del que atiende las peticiones; el corte inmediato del token de acceso se resuelve con un **registro de invalidación por usuario** sembrado al arrancar, no consultando el estado en cada petición; y `RN-SP-001` se serializa **bloqueando el conjunto de portadores activos del rol raíz**, no la fila del afectado, que era el caso que `RF-SP-028` §13 anticipaba. | Responsable técnico |
| 0.29.0 | 22-08-2026 | **El bloqueo del entorno se cierra y `RF-SP-010` avanza ocho tareas.** La estación de trabajo cumple ya `development-guide.md` §2.1 —JDK 21.0.12, Docker 29.7.2 y el **envoltorio de Maven** 3.9.9—, con lo que decae el bloqueo 3 de sus `tasks.md`, abierto por no haber JDK ni Maven instalados. `./mvnw clean verify` termina en verde: **14 pruebas unitarias y 38 de integración** sobre Testcontainers, incluidas las veinticuatro filas sembradas y las ocho de recurso `users`. `T-01` a `T-04` y `T-06` a `T-08` pasan a **Hecha**. Dos correcciones en esas tareas: la verificación de `T-08` decía «prueba de API» y lo que existe es una **prueba de serialización sobre el DTO** —el controlador no puede integrarse hasta `RF-SP-001`, bloqueo 5—, y `T-13` figuraba «En curso» dependiendo de `T-10`, que está **bloqueada**; pasa a **Bloqueada**. Se versionan además `mvnw`, `mvnw.cmd` y `.mvn/`, que estaban sin rastrear pese a ser la vía por la que el proyecto se construye, y nace `.gitattributes` para que `mvnw` salga siempre con finales de línea LF. | Responsable técnico |
| 0.30.0 | 22-08-2026 | **Cuatro planes nuevos con sus tareas** —`RF-SP-030` a `RF-SP-033`—: el submódulo de asignación de **roles y membresías** queda con su tripleta completa, y con él **treinta y tres** de los cuarenta y dos requerimientos han superado la segunda compuerta. Cuatro decisiones alcanzan más allá de su tripleta. **`RF-SP-031` cambia de verbo**: `DELETE` con la lista en el cuerpo pasa a `POST …/roles/revocations`, la misma corrección que `RF-SP-006` hizo el 21-08-2026 y por el mismo motivo — RFC 9110 no le define semántica al cuerpo de un `DELETE`, y un intermediario que lo descarte produce un retiro **sin roles**, en una operación que además revoca sesiones. `RF-SP-033` conserva el suyo: no lleva cuerpo. **Tres componentes de dominio quedan declarados compartidos** y ninguno puede escribirse dos veces: `RoleGrantPolicy` \(`RN-SEG-010`\), `RootRoleGuard` \(`RN-SP-001`, con el bloqueo sobre el conjunto de portadores activos dentro\) y `CommercialRank`, que resuelve el rol vendedor de **mayor rango** y es lo único que distingue un ascenso de una asignación lateral. **La definición de «vigente» de una membresía pasa a vivir en un solo componente de dominio**, probado sobre sus tres bordes, en lugar de repetirse como `WHERE` en las consultas de `RF-SP-026` y `RF-SP-031`. Y se enmienda `spec.md` de `RF-SP-030` §11 \(Art. I.7\): los cuatro casos condicionales de membresía y superior responden **`422` y no `400`**, porque ninguno se decide mirando solo el cuerpo — un `400` no debe poder salir del caso de uso, que es la frontera que hace legible el manejador global. `requirements/sp.md` sube a **1.18.0** con la corrección de §9. | Responsable técnico |
| 0.31.0 | 22-08-2026 | **`RF-SP-001` cruza la tercera compuerta y queda implementado**: sus `tasks.md` se aprueban y sus **veintidós tareas pasan a `Hecha`**. Entra con él la infraestructura que todo módulo posterior reutiliza —las migraciones `V4` a `V7`, los cuatro registros de auditoría con su mecánica transaccional, la jerarquía de errores con el manejador global, el generador UUID v7, el contexto de origen de la petición y la publicación del actor autenticado—. `./mvnw clean verify` en verde con **46 pruebas unitarias y 98 de integración**. **No pasa a `Implementado`**: `RN-SEG-010` lee los permisos efectivos del `Authentication` y no de la base de datos, porque `users` y `user_roles` los crea `RF-SP-024`; queda como bloqueo 3 de sus tareas. Se resuelve además la contradicción que `architecture.md` §5.1 dejó planteada —`T-11`, `T-12` y `T-20` estaban escritas sobre la disposición de capas anterior— por la vía que ese mismo documento admite: no hay `RoleEntity` ni `RoleJpaMapper`, el agregado es el modelo persistente y la regla de ArchUnit se acota a lo que §5.1 permite. **Y se corrige `ck_deletion_reason`**: tal como lo transcribe `architecture.md` §6.6.3, un motivo nulo daba `FALSE OR NULL` = `NULL` y el `CHECK` aceptaba la fila, de modo que la obligación del Art. V.13 no existía; `V4` la declara con `reason IS NOT NULL` y una prueba la ejercita. Queda anotado un hueco: **el contexto de origen de la petición no tiene tarea en ninguna tripleta** pese a que las cuatro tablas de auditoría lo exigen. **Y se descubre que el contrato OpenAPI no se generaba**: `/v3/api-docs` devolvía `500` por una incompatibilidad de springdoc 2.6.0 con Spring Framework 6.2, latente desde que se fijó la versión y visible solo al existir el primer `@ControllerAdvice`. Se sube a 2.8.9 y se añade `OpenApiContractIT` para que la ausencia de un endpoint en el contrato vuelva a ser un fallo y no un silencio (Art. VIII.2). | Responsable técnico |
