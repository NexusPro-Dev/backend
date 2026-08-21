# Requerimientos y Trazabilidad — NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `requirements.md` |
| Versión | 0.8.0 |
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
| `RF-SP-020` | Registrar país | `SP` | `specs/sp/020-registrar-pais/` | — | — | — | **Spec aprobada** |
| `RF-SP-021` | Consultar países | `SP` | `specs/sp/021-consultar-paises/` | — | — | — | **Spec aprobada** |
| `RF-SP-022` | Cambiar el estado de un país | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-023` | Cambiar el estado de una moneda | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-024` | Registrar usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-025` | Consultar usuarios | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-026` | Consultar detalle de un usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-027` | Editar usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-028` | Cambiar el estado de un usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-029` | Eliminar usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-030` | Asignar roles a un usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-031` | Retirar roles de un usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-032` | Asignar membresía a un usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-033` | Retirar la membresía de un usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-034` | Iniciar sesión | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-035` | Refrescar el token de acceso | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-036` | Cerrar sesión | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-037` | Cambiar la propia contraseña | `SP` | Pendiente de redactar | — | — | — | Pendiente |
| `RF-SP-038` | Restablecer la contraseña de un usuario | `SP` | Pendiente de redactar | — | — | — | Pendiente |


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
| Requerimientos registrados | 23 |
| Requerimientos con `spec.md` redactada | 21 |
| Requerimientos con `spec.md` aprobada | 20 |
| Requerimientos implementados | 0 |

Los 23 corresponden al módulo `SP`. Veintiuno han superado la primera compuerta del Art. I.6; quedan fuera `RF-SP-022` y `RF-SP-023`, nacidos de la aprobación de `RF-SP-020` y `RF-SP-019` y todavía sin especificación.

De `RF-SP-001` a `RF-SP-013` han superado además la **segunda** compuerta: sus `plan.md` se aprobaron el 21-08-2026 y pueden pasar a `tasks.md`. El de `RF-SP-011` amplió `PageResponse<T>` con `totalIsExact`, porque los cuatro listados de auditoría cuentan hasta un techo configurable en lugar de recorrer una tabla que crece sin límite; los listados de conteo exacto lo devuelven siempre en `true`. La aprobación del plan de `RF-SP-010` corrió la numeración de las migraciones —`V1` pasa a ser las funciones compartidas y todas las demás avanzan un número—, porque ese requerimiento se implementa primero y necesita `f_unaccent` antes que `RF-SP-002`, que era quien la creaba. Enmendó también `security.md` §4.4, cuyo catálogo omitía cuatro bloques de permisos, y fijó que cada módulo siembra los suyos.

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
