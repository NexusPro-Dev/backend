# SPEC — `RF-SP-005` Asignar permisos a un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-005` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 20-08-2026 |
| Enmendada | 21-08-2026 — `EX-006` y `CA-SP-173`, al aprobar `plan.md` (Art. I.7) |

---

## 1. Objetivo

Ampliar lo que un rol puede hacer, sin que llegue a exceder ni a su rol padre ni a quien realiza la asignación.

## 2. Contexto

Es el requerimiento donde se materializa el modelo de contención de privilegios. Todo lo demás del módulo lo rodea: aquí es donde alguien podría, si el sistema lo permitiera, concederse a sí mismo o a otros más poder del que le corresponde.

Dos reglas actúan a la vez y hay que entender por qué hacen falta las dos:

- `RN-SEG-003` impide que el rol supere a **su rol padre**.
- `RN-SEG-010` impide que el actor conceda lo que **él mismo no tiene**.

Sin la segunda, un administrador podría ampliar un rol que cuelga de un padre poderoso hasta darle permisos que él no posee, y luego asignárselo.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Asigna cualquier permiso del catálogo, acotado solo por el rol padre |
| Administrador | Asigna permisos acotados por el rol padre y por sus propios permisos efectivos |

## 4. Alcance

### 4.1 Incluye

- Agregar uno o varios permisos a un rol existente. La operación **solo agrega**: nunca retira ninguno.
- Verificación de contención respecto del rol padre y del actor.

### 4.2 No incluye

- Retirar permisos → `RF-SP-006`. Esta operación no puede usarse para reemplazar la lista: un reemplazo haría revocaciones implícitas, y revocar tiene reglas propias que `RN-SEG-005` impone.
- Crear permisos: el catálogo solo se modifica por migración (`RN-SP-004`).
- Propagar el permiso a los roles hijos: cada rol declara los suyos de forma explícita.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-003` | Los permisos son subconjunto de los del rol padre | `security.md` §4.3 |
| `RN-SEG-004` | La validación se hace contra el padre inmediato | `security.md` §4.3 |
| `RN-SEG-010` | Nadie otorga permisos que no posee | `security.md` §4.3 |
| `RN-SEG-011` | Nadie modifica un rol que tiene asignado | `security.md` §4.3 |
| `RN-SEG-012` | Los roles de sistema no se modifican por la API | `security.md` §4.3 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del rol | Sí | Rol al que se agregan permisos | Debe existir y no ser de sistema |
| Permisos | Sí | Permisos a agregar | Entre 1 y 100 por petición; cada uno debe existir en el catálogo |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Rol | Rol con su lista de permisos actualizada |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de roles.
- El rol existe, no está eliminado y no es de sistema.
- El actor no tiene ese rol asignado.
- Los permisos solicitados existen en el catálogo.

**Postcondiciones**

- Los permisos quedan asociados al rol.
- Los permisos del rol siguen contenidos en los de su rol padre.
- Los roles hijos **no** se modifican: su contención sigue siendo válida, porque el conjunto del padre solo creció.
- Queda constancia en la auditoría de cambios y en la de seguridad.

## 8. Flujo principal

1. El actor solicita agregar permisos a un rol.
2. El sistema verifica que el rol exista y no sea de sistema.
3. El sistema verifica que el actor no tenga ese rol asignado.
4. El sistema verifica que todos los permisos existan en el catálogo.
5. El sistema verifica que todos estén contenidos en los del rol padre.
6. El sistema verifica que todos estén contenidos en los permisos efectivos del actor.
7. El sistema asocia los permisos que aún no estaban asociados.
8. El sistema invalida la caché de resolución de permisos del rol.
9. El sistema registra el evento en la auditoría de cambios y en la de seguridad.
10. El sistema informa el rol con sus permisos actualizados.

## 9. Flujos alternativos

### FA-001 — Permisos ya asociados

**Cuándo ocurre:** alguno de los permisos ya lo declaraba el rol.

1. El sistema ignora los ya presentes y asocia solo los nuevos.
2. La operación es **idempotente**: repetirla no produce error ni duplicados.

### FA-002 — Rol raíz

**Cuándo ocurre:** el rol no tiene rol padre.

1. El sistema omite la verificación de `RN-SEG-003`, porque no hay cota superior.
2. Mantiene la verificación de `RN-SEG-010` frente al actor.

## 10. Excepciones

### EX-001 — Permiso fuera del rol padre

**Condición:** algún permiso no está entre los del rol padre.
**Respuesta del sistema:** rechaza la operación completa, cita `RN-SEG-003` e informa **qué permisos** lo incumplen y de qué rol padre se trata.

### EX-002 — Permiso fuera del alcance del actor

**Condición:** algún permiso no está entre los permisos efectivos del actor.
**Respuesta del sistema:** rechaza la operación completa, cita `RN-SEG-010` e informa qué permisos lo incumplen.

### EX-003 — Permiso inexistente

**Condición:** algún permiso no está en el catálogo.
**Respuesta del sistema:** rechaza la operación e informa cuáles no existen.

### EX-004 — Rol de sistema

**Condición:** el rol está marcado como de sistema.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-012`.

### EX-005 — El actor tiene el rol asignado

**Condición:** el rol está entre los del actor.
**Respuesta del sistema:** rechaza la operación y cita `RN-SEG-011`.

### EX-006 — Rol inexistente

**Condición:** no existe un rol vigente con el identificador indicado, o está eliminado lógicamente.
**Respuesta del sistema:** rechaza la operación e informa que el rol no existe, sin distinguir entre nunca haber existido y haber sido eliminado (Art. V.13). Añadida el 21-08-2026 al aprobar el `plan.md`: la especificación no declaraba la excepción del rol inexistente y el plan la referenciaba con el código de otra (Art. I.7).

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Al menos un permiso informado | Debe indicar al menos un permiso. |
| `VAL-002` | Identificadores de permiso con formato válido | El identificador de permiso no es válido. |
| `VAL-003` | Los permisos existen en el catálogo | Uno o más permisos no existen. |
| `VAL-004` | Los permisos están contenidos en el rol padre | El rol padre no concede uno o más de los permisos indicados. |
| `VAL-005` | Los permisos están contenidos en los del actor | No puede conceder permisos que usted no posee. |
| `VAL-006` | Como máximo 100 permisos por petición | No es posible asignar más de 100 permisos en una sola solicitud. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-031` | El sistema asocia permisos contenidos en el rol padre y en los del actor |
| `CA-SP-032` | El sistema rechaza la operación completa si un solo permiso incumple `RN-SEG-003`, e indica cuál |
| `CA-SP-033` | El sistema rechaza la operación si un permiso excede los permisos efectivos del actor |
| `CA-SP-034` | El sistema ignora los permisos ya asociados sin producir error ni duplicados |
| `CA-SP-035` | El sistema no exige contención cuando el rol no tiene rol padre |
| `CA-SP-036` | El sistema rechaza la operación sobre un rol de sistema |
| `CA-SP-037` | El sistema rechaza la operación sobre un rol que el propio actor tiene asignado |
| `CA-SP-038` | El sistema deja sin efecto la caché de permisos del rol, de modo que el cambio aplica de inmediato |
| `CA-SP-039` | El sistema registra el evento en la auditoría de cambios y en la de seguridad |
| `CA-SP-040` | El sistema valida contra el rol padre inmediato, sin recorrer ancestros |
| `CA-SP-153` | El sistema conserva los permisos que el rol ya declaraba: la operación nunca retira ninguno |
| `CA-SP-154` | El sistema rechaza una petición con más de 100 permisos |
| `CA-SP-173` | El sistema rechaza la operación sobre un rol inexistente o eliminado, sin distinguir ambos casos |

## 13. Casos límite

- **Operación parcialmente válida:** se rechaza **entera**. No se aplican los permisos válidos ignorando los que fallan: dejaría el rol en un estado que nadie pidió.
- **Permisos duplicados en la petición:** se normalizan a una sola ocurrencia.
- **El rol padre pierde el permiso después:** no afecta a esta operación; lo impide `RF-SP-006` mediante `RN-SEG-005`.
- **El actor es superadministrador:** posee todo el catálogo, de modo que `RN-SEG-010` nunca lo bloquea.
- **Cadena profunda de roles:** la validación sigue siendo de un solo nivel; la contención es transitiva.
- **Asignación concurrente del mismo permiso:** la clave primaria compuesta debe absorber el empate sin error interno.
- **Rol que necesita más de 100 permisos:** se resuelve en varias peticiones. Al ser la operación idempotente y aditiva, partirla no produce efectos distintos de hacerla de una vez.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 20-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Agrega o reemplaza la lista completa? | **Agrega, sin retirar nada.** Un reemplazo haría revocaciones implícitas, y revocar tiene reglas propias: `RN-SEG-005` rechaza retirar un permiso que un rol hijo declara. Reemplazar obligaría a reimplementar esa verificación aquí, o la saltaría en silencio. El coste asumido es que una interfaz de casillas debe calcular la diferencia y hacer dos llamadas |
| 2 | ¿Hay límite de permisos por petición? | **Sí, 100**, el mismo techo que el tamaño máximo de página, para no arrastrar dos límites distintos. Un rol que necesite más se resuelve en varias peticiones, sin riesgo por ser la operación idempotente y aditiva |
| 3 | ¿`RN-SEG-011` alcanza a los roles ancestros del actor? | **No**, solo a los asignados directamente. `RN-SEG-010` impide conceder permisos que el actor no posee, de modo que ampliar un rol ancestro no le aporta nada que no tuviera ya |
