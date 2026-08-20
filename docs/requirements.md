# Requerimientos y Trazabilidad — NEXUS

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `requirements.md` |
| Versión | 0.4.0 |
| Estado | Borrador |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 20-08-2026 |
| Última actualización | 20-08-2026 |
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
| `SP` — Sistema Principal | `requirements/sp.md` | Pendiente de redactar |
| `USR` — Usuarios | `requirements/usr.md` | Pendiente de redactar |

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
| — | *(sin requerimientos registrados)* | — | — | — | — | — | — |

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
| Requerimientos registrados | 0 |
| Requerimientos especificados | 0 |
| Requerimientos implementados | 0 |

El inventario y el estado de los módulos se consultan en [`modules.md` §4](modules.md#4-inventario-de-modulos).

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 20-08-2026 | Creación inicial con catálogo de módulos y matriz de trazabilidad. | Responsable técnico |
| 0.2.0 | 20-08-2026 | El inventario de módulos se traslada a `modules.md`, que pasa a ser su autoridad única. Este documento conserva nomenclatura y trazabilidad. | Responsable técnico |
| 0.3.0 | 20-08-2026 | Se retira el identificador `SPEC-[MÓDULO]-NNN` por redundante con el `RF`. La matriz refleja las tres compuertas de aprobación de la tripleta. | Responsable técnico |
| 0.4.0 | 20-08-2026 | §3.1 documenta el espacio de reglas transversales `RN-SEG` y deja las historias de usuario fuera de la cadena de trazabilidad. | Responsable técnico |
