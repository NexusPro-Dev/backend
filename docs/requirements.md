# Requerimientos y Trazabilidad — NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `requirements.md` |
| Versión | 0.19.0 |
| Estado | Borrador |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 20-08-2026 |
| Última actualización | 21-08-2026 |
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
| `RF-SP-001` | Registrar rol | `SP` | `specs/sp/001-registrar-rol/` | — | — | — | **Plan aprobado** |
| `RF-SP-002` | Consultar roles | `SP` | `specs/sp/002-consultar-roles/` | — | — | — | **Plan aprobado** |
| `RF-SP-003` | Consultar detalle de un rol | `SP` | `specs/sp/003-consultar-detalle-rol/` | — | — | — | **Plan aprobado** |
| `RF-SP-004` | Editar rol | `SP` | `specs/sp/004-editar-rol/` | — | — | — | **Plan aprobado** |
| `RF-SP-005` | Asignar permisos a un rol | `SP` | `specs/sp/005-asignar-permisos/` | — | — | — | **Plan aprobado** |
| `RF-SP-006` | Revocar permisos de un rol | `SP` | `specs/sp/006-revocar-permisos/` | — | — | — | **Plan aprobado** |
| `RF-SP-007` | Cambiar el estado de un rol | `SP` | `specs/sp/007-cambiar-estado-rol/` | — | — | — | **Plan aprobado** |
| `RF-SP-008` | Cambiar el rol padre de un rol | `SP` | `specs/sp/008-cambiar-rol-padre/` | — | — | — | **Plan aprobado** |
| `RF-SP-009` | Eliminar rol | `SP` | `specs/sp/009-eliminar-rol/` | — | — | — | **Plan aprobado** |
| `RF-SP-010` | Consultar catálogo de permisos | `SP` | `specs/sp/010-consultar-permisos/` | — | — | — | **Plan aprobado** |
| `RF-SP-011` | Consultar auditoría de cambios | `SP` | `specs/sp/011-consultar-auditoria-cambios/` | — | — | — | **Plan aprobado** |
| `RF-SP-012` | Consultar auditoría de eliminación | `SP` | `specs/sp/012-consultar-auditoria-eliminacion/` | — | — | — | **Plan aprobado** |
| `RF-SP-013` | Consultar auditoría de error | `SP` | `specs/sp/013-consultar-auditoria-error/` | — | — | — | **Plan aprobado** |
| `RF-SP-014` | Consultar auditoría de seguridad | `SP` | `specs/sp/014-consultar-auditoria-seguridad/` | — | — | — | **Spec aprobada** |
| `RF-SP-015` | Consultar detalle de un permiso | `SP` | `specs/sp/015-consultar-detalle-permiso/` | — | — | — | **Spec aprobada** |
| `RF-SP-016` | Registrar membresía | `SP` | `specs/sp/016-registrar-membresia/` | — | — | — | **Spec aprobada** |
| `RF-SP-017` | Consultar membresías | `SP` | `specs/sp/017-consultar-membresias/` | — | — | — | **Spec aprobada** |
| `RF-SP-018` | Consultar detalle de una membresía | `SP` | `specs/sp/018-consultar-detalle-membresia/` | — | — | — | **Spec aprobada** |
| `RF-SP-019` | Consultar monedas | `SP` | `specs/sp/019-consultar-monedas/` | — | — | — | **Spec aprobada** |
| `RF-SP-020` | Registrar país | `SP` | `specs/sp/020-registrar-pais/` | — | — | — | **Plan aprobado** |
| `RF-SP-021` | Consultar países | `SP` | `specs/sp/021-consultar-paises/` | — | — | — | **Spec aprobada** |
| `RF-SP-022` | Cambiar el estado de un país | `SP` | `specs/sp/022-cambiar-estado-pais/` | — | — | — | **Spec aprobada** |
| `RF-SP-023` | Cambiar el estado de una moneda | `SP` | `specs/sp/023-cambiar-estado-moneda/` | — | — | — | **Spec aprobada** |
| `RF-SP-024` | Registrar usuario | `SP` | `specs/sp/024-registrar-usuario/` | — | — | — | **Spec aprobada** |
| `RF-SP-025` | Consultar usuarios | `SP` | `specs/sp/025-consultar-usuarios/` | — | — | — | **Spec aprobada** |
| `RF-SP-026` | Consultar detalle de un usuario | `SP` | `specs/sp/026-consultar-detalle-usuario/` | — | — | — | **Spec aprobada** |
| `RF-SP-027` | Editar usuario | `SP` | `specs/sp/027-editar-usuario/` | — | — | — | **Spec aprobada** |
| `RF-SP-028` | Cambiar el estado de un usuario | `SP` | `specs/sp/028-cambiar-estado-usuario/` | — | — | — | **Spec aprobada** |
| `RF-SP-029` | Eliminar usuario | `SP` | `specs/sp/029-eliminar-usuario/` | — | — | — | **Spec aprobada** |
| `RF-SP-030` | Asignar roles a un usuario | `SP` | `specs/sp/030-asignar-roles-usuario/` | — | — | — | **Spec aprobada** |
| `RF-SP-031` | Retirar roles de un usuario | `SP` | `specs/sp/031-retirar-roles-usuario/` | — | — | — | **Spec aprobada** |
| `RF-SP-032` | Asignar membresía a un usuario | `SP` | `specs/sp/032-asignar-membresia-usuario/` | — | — | — | **Spec aprobada** |
| `RF-SP-033` | Retirar la membresía de un usuario | `SP` | `specs/sp/033-retirar-membresia-usuario/` | — | — | — | **Spec aprobada** |
| `RF-SP-034` | Iniciar sesión | `SP` | `specs/sp/034-iniciar-sesion/` | — | — | — | **Spec aprobada** |
| `RF-SP-035` | Refrescar el token de acceso | `SP` | `specs/sp/035-refrescar-token/` | — | — | — | **Spec aprobada** |
| `RF-SP-036` | Cerrar sesión | `SP` | `specs/sp/036-cerrar-sesion/` | — | — | — | **Spec aprobada** |
| `RF-SP-037` | Cambiar la propia contraseña | `SP` | `specs/sp/037-cambiar-contrasena/` | — | — | — | **Spec aprobada** |
| `RF-SP-038` | Restablecer la contraseña de un usuario | `SP` | `specs/sp/038-restablecer-contrasena/` | — | — | — | **Spec aprobada** |
| `RF-SP-039` | Consultar el propio perfil | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-040` | Restablecer la propia contraseña olvidada | `SP` | Pendiente de redactar | — | — | — | Pendiente |


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
| Requerimientos registrados | 40 |
| Requerimientos con `spec.md` redactada | 38 |
| Requerimientos con `spec.md` aprobada | 38 |
| Requerimientos implementados | 0 |

Los 40 corresponden al módulo `SP`, que absorbió los usuarios, sus roles y su acceso al retirarse `USR` (`modules.md` v0.9.0). **Treinta y ocho tienen `spec.md` y las treinta y ocho han superado la primera compuerta** del Art. I.6: el 21-08-2026 no queda ninguna especificación con preguntas abiertas, y todas pueden pasar a `plan.md`. Los dos restantes —`RF-SP-039`, consultar el propio perfil, y `RF-SP-040`, restablecer la propia contraseña olvidada— nacieron ese mismo día de las aprobaciones de `RF-SP-026` y `RF-SP-037`, y todavía no tienen especificación.

Las diecisiete especificaciones que faltaban se redactaron el 21-08-2026 con **sesenta y cuatro preguntas abiertas**, y las sesenta y cuatro quedaron resueltas. Lo que sigue es lo que esas resoluciones fijaron para todo el módulo, porque ninguna se agota en su propio requerimiento.

**Cambio de estado de un catálogo** (`RF-SP-022`, `RF-SP-023`). Se registra solo en la auditoría de cambios y no en la de seguridad, porque no hay privilegio en juego; no se exige motivo, porque el Art. V.13 lo obliga solo en las eliminaciones; el elemento se desactiva aunque tenga datos que lo referencien o importes expresados en él, porque impedirlo dejaría sin salida justo al caso que más urge retirar; y no se devuelve el conteo de esas referencias. No se crea todavía un requerimiento para cambiar la moneda por defecto: mientras haya una sola no hay nada que elegir.

**Identidad de las personas** (`RF-SP-024`). Cada persona lleva **dos identidades y con cualquiera de las dos inicia sesión**: el correo, que puede corregirse y cuyo cambio emite evento de seguridad por ser una vía de acceso, y el **nombre de usuario**, inmutable, sin el carácter `@` —lo que impide que un valor se confunda con el otro— y que es el que la auditoría referencia. El alta fija la contraseña inicial y deja la cuenta `ACTIVO` y **marcada para cambio obligatorio**; `PENDIENTE` queda declarado y sin usar.

**El rol de consumidor y la membresía son inseparables** (`RN-SP-018`, nacida al aprobar `RF-SP-033`). No existe el estado «consumidor sin nivel»: se conceden juntos —`RF-SP-024` y `RF-SP-030` exigen indicar la membresía al dar el primer rol de consumidor— y se sueltan juntos —`RF-SP-031` la retira al quitar el último—. Resolverlo de otro modo producía un bloqueo mutuo del que nadie podía salir, y obligó a devolver a su compuerta tres especificaciones ya aprobadas (Art. I.7). La membresía admite además **fecha de fin**, cuya vigencia se evalúa al consultarla, sin proceso que la retire.

**Retirar el acceso es inmediato; concederlo puede esperar** (`RF-SP-028`, `RF-SP-030`, `RF-SP-031`). Desactivar, bloquear o retirar un rol revoca las sesiones de la persona y su token de acceso deja de admitirse; asignar un rol acepta la latencia de hasta quince minutos que `security.md` §4.5 declara. `INACTIVO` y `BLOQUEADO` pasan a tener significados separados —organizativo frente a seguridad—, el bloqueo admite origen **manual** sin expiración, y retirar el acceso exige motivo mientras que devolverlo no lo admite.

**Sesiones** (`RF-SP-034` a `RF-SP-038`). Bloqueo a los cinco intentos con progresión y techo; la cuenta bloqueada recibe respuesta distinta, como excepción consciente al mensaje genérico; cada revocación registra su **motivo**, y solo la rotación dispara la alarma de robo; la familia de tokens tiene una **duración máxima de sesión** contada desde el inicio; el cierre de sesión es público y admite cerrar todas; y la credencial provisional que fija un administrador **caduca**.

**Lo que sigue abierto.** No existe **canal de correo** en ningún requerimiento, y de ahí cuelgan tres cosas: `RF-SP-040` sin especificar, la verificación del correo al cambiarlo (`RF-SP-027`, anotada como riesgo con su condición de disparo) y la notificación a quien le restablecen la contraseña (`RF-SP-038`). Mientras siga así, quien olvide su contraseña depende de que un administrador se la restablezca conociéndola durante un tiempo.


De `RF-SP-001` a `RF-SP-013`, más `RF-SP-020`, han superado además la **segunda** compuerta: sus `plan.md` se aprobaron el 21-08-2026 y pueden pasar a `tasks.md`. El de `RF-SP-020` es el primero que toca `countries`: crea la tabla en `V16`, le añade `updated_at` —que `requirements/sp.md` §10.6 omitía pese al Art. V.7— y declara la unicidad del nombre sobre `f_unaccent(lower(name))` y no sobre el literal, porque `RN-SP-009` no admite edición y un `Panamá`/`Panama` duplicado sería permanente. El de `RF-SP-011` amplió `PageResponse<T>` con `totalIsExact`, porque los cuatro listados de auditoría cuentan hasta un techo configurable en lugar de recorrer una tabla que crece sin límite; los listados de conteo exacto lo devuelven siempre en `true`. La aprobación del plan de `RF-SP-010` corrió la numeración de las migraciones —`V1` pasa a ser las funciones compartidas y todas las demás avanzan un número—, porque ese requerimiento se implementa primero y necesita `f_unaccent` antes que `RF-SP-002`, que era quien la creaba. Enmendó también `security.md` §4.4, cuyo catálogo omitía cuatro bloques de permisos, y fijó que cada módulo siembra los suyos.

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
