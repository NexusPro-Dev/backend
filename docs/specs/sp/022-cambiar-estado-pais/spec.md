# SPEC — `RF-SP-022` Cambiar el estado de un país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-022` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Retirar de la circulación un país del catálogo, o reincorporarlo, sin borrar su registro ni alterar sus datos.

## 2. Contexto

`RN-SP-009` deja el catálogo de países sin edición y sin borrado: un país registrado con el nombre mal escrito queda así para siempre. Ese cierre es deliberado —hay datos que referencian al país y perderlos rompería la trazabilidad—, pero deja al alta sin ninguna salida, y `RF-SP-020` se aprobó con la condición de que existiera una.

Esta es esa salida, y **la única modificación admitida sobre el catálogo**. Un país inactivo deja de ofrecerse en `RF-SP-021`, de modo que nadie vuelve a seleccionarlo, pero su registro permanece y los datos que ya lo tenían asignado siguen resolviéndolo.

Conviene decir lo que **no** es: desactivar no corrige. El código y el nombre erróneos siguen ahí, y quien ya los tenía asignados los conserva. Lo que evita es que el error se propague a partir de ese momento. Un catálogo de doscientos elementos cuya corrección real exige una migración es un coste asumido conscientemente al aprobar `RF-SP-020`.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Activa o desactiva cualquier país |
| Administrador | Activa o desactiva cualquier país |

## 4. Alcance

### 4.1 Incluye

- Desactivar un país activo y reactivar uno inactivo.

### 4.2 No incluye

- Corregir el código o el nombre: `RN-SP-009` lo prohíbe y desactivar no lo sustituye.
- Eliminar el país del catálogo, ni lógica ni físicamente.
- Reasignar o limpiar los datos que ya referencian al país: siguen apuntando a él y siguen resolviéndolo.
- Registrar países → `RF-SP-020`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-009` | Los países no se editan ni eliminan; lo único que puede cambiarse es su estado | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del país | Sí | País cuyo estado cambia | Debe existir en el catálogo |
| Estado | Sí | Nuevo estado | Activo o inactivo |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| País | País con su código, su nombre y su estado actualizado |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de países.
- El país existe en el catálogo.

**Postcondiciones**

- El país queda en el estado solicitado.
- Si quedó inactivo, deja de aparecer en `RF-SP-021` salvo que se pidan explícitamente los inactivos, y deja de poder seleccionarse en cualquier alta.
- Su código y su nombre permanecen sin cambios, y los datos que ya lo referencian siguen resolviéndolo.
- Queda constancia en la auditoría de cambios.

## 8. Flujo principal

1. El actor solicita cambiar el estado de un país.
2. El sistema verifica que el país exista.
3. El sistema aplica el nuevo estado.
4. El sistema registra el evento en la auditoría de cambios, con el estado anterior y el nuevo.
5. El sistema informa el país actualizado.

## 9. Flujos alternativos

### FA-001 — El país ya está en ese estado

**Cuándo ocurre:** se solicita activar un país activo, o desactivar uno inactivo.

1. El sistema no aplica cambio ni registra evento de auditoría.
2. Devuelve el país sin tratarlo como error: la operación es idempotente, igual que en `RF-SP-007`.

## 10. Excepciones

### EX-001 — País inexistente

**Condición:** el identificador no corresponde a ningún país del catálogo.
**Respuesta del sistema:** rechaza la operación e informa que el país no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Estado obligatorio y dentro del dominio | El estado indicado no es válido. |
| `VAL-002` | País existente | El país solicitado no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-178` | El sistema desactiva un país activo y lo reactiva después |
| `CA-SP-179` | El país inactivo deja de aparecer en el listado por defecto de `RF-SP-021` |
| `CA-SP-180` | El código y el nombre del país no cambian al cambiar su estado |
| `CA-SP-181` | Los datos que ya referenciaban al país siguen resolviéndolo tras desactivarlo |
| `CA-SP-182` | El sistema no registra evento cuando el país ya estaba en el estado solicitado |
| `CA-SP-183` | El sistema registra el cambio de estado en la auditoría de cambios, con el valor anterior y el nuevo, y **no** en la de seguridad |
| `CA-SP-338` | La operación no solicita ni admite un motivo |
| `CA-SP-184` | El sistema rechaza la operación a un actor sin el permiso de modificación de países |

## 13. Casos límite

- **País referenciado por datos vigentes:** se desactiva igual. Es el caso para el que existe la operación: impedirlo dejaría sin salida al alta equivocada que ya se usó, que es justamente la que más urge retirar.
- **Reactivar un país desactivado hace meses:** se admite sin condiciones; el registro nunca dejó de existir.
- **País inexistente:** se rechaza como tal, no se trata como un cambio sin efecto.
- **Cambio concurrente del mismo país:** ambas peticiones se serializan sobre la fila; la segunda encuentra el estado ya aplicado y cae en `FA-001`.
- **Catálogo con todos los países inactivos:** es posible y no se impide. Ningún formulario podría seleccionar país, pero el catálogo se puebla manualmente y esa situación es reversible en una petición.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El cambio de estado se registra también en la auditoría de seguridad? | **Solo en la de cambios.** `RF-SP-007` registra en ambas porque un rol inactivo deja de conceder permisos, y eso es un cambio de privilegio. Aquí no hay privilegio en juego: un país inactivo solo deja de ofrecerse en un selector. Registrarlo en `audit_security_log` llenaría de eventos de catálogo el registro que se consulta buscando un incidente, y `security.md` §8.1 —que enumera un catálogo cerrado— tendría que crecer con eventos que no son de control de acceso. `CA-SP-183` lo verifica en los dos sentidos: que el evento está en cambios y que **no** está en seguridad |
| 2 | ¿Se exige motivo al desactivar? | **No.** El Art. V.13 obliga al motivo solo en las eliminaciones, y por una razón concreta: el registro desaparece. Aquí el país sigue existiendo, la operación es reversible en una petición y la auditoría ya guarda quién y cuándo. Es la misma resolución que en `RF-SP-007` y por el mismo argumento; exigirlo crearía un patrón nuevo —motivo fuera de una eliminación— que después habría que sostener en `RF-SP-023` y en `RF-SP-028`. Se añade `CA-SP-338` para que la ausencia quede verificada y no se cuele en el contrato al escribir el plan |
| 3 | ¿Debe advertirse cuántos datos referencian al país antes de desactivarlo? | **No ahora.** Hoy **ninguna tabla del modelo referencia a `countries`** (`modelo-datos.md` §2), de modo que el conteo no tiene de dónde salir: `SP` tendría que publicar una interfaz e invertir una dependencia hacia módulos que todavía no existen, para un dato que hoy valdría siempre cero. Se replantea cuando aparezca la primera clave foránea entrante, y mientras tanto el `plan.md` lo anota como riesgo con esa condición de disparo. La decisión de fondo no cambia: el país se desactiva igual, tenga referencias o no (`CA-SP-181`) |
