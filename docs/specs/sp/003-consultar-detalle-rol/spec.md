# SPEC — `RF-SP-003` Consultar detalle de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-003` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 20-08-2026 |

---

## 1. Objetivo

Conocer el alcance exacto de un rol —qué permisos concede y dónde está en la jerarquía— antes de asignarlo a alguien o de modificarlo.

## 2. Contexto

El modelo no usa herencia: cada rol declara sus permisos de forma explícita. Esa decisión existe precisamente para que esta consulta pueda responderse leyendo **una sola lista**, sin recorrer el árbol de ancestros.

Es la pantalla que responde «¿qué puede hacer alguien con este rol?», y de ella depende que asignar un rol sea una decisión informada.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Consulta cualquier rol |
| Administrador | Consulta cualquier rol |

## 4. Alcance

### 4.1 Incluye

- Datos del rol: código, nombre, descripción, clasificación y estado.
- Lista completa de sus permisos declarados.
- Su rol padre, y **cuántos** roles hijos directos tiene.
- El número de usuarios que lo tienen asignado, obtenido de `USR` a través de la interfaz que este publica.

### 4.2 No incluye

- El listado de los usuarios que lo tienen asignado → módulo `USR`. Aquí solo se devuelve **cuántos** son.
- Los permisos efectivos de una persona, que son la unión de sus roles (`RN-SEG-009`) → módulo `USR`.
- El **listado** de roles hijos: se obtiene con `RF-SP-002` filtrando por rol padre, que ya existe y ya está paginado.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SEG-004` | La resolución no recorre la cadena de ancestros | `security.md` §4.3 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador | Sí | Rol que se consulta | Debe existir |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Rol | Código, nombre, descripción, clasificación y estado |
| Permisos | Lista explícita de los permisos que declara |
| Rol padre | Rol que acota sus privilegios, vacío en el rol raíz |
| Roles hijos | **Cuántos** roles cuelgan de este. El listado se obtiene con `RF-SP-002` filtrando por rol padre |
| Usuarios asignados | Cuántos usuarios tienen el rol, obtenido de la interfaz que publica `USR` |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de roles.
- El rol existe.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el detalle de un rol.
2. El sistema recupera el rol y su lista de permisos declarados.
3. El sistema recupera su rol padre, cuenta sus roles hijos directos y consulta a `USR` cuántos usuarios lo tienen asignado.
4. El sistema devuelve el detalle completo.

## 9. Flujos alternativos

### FA-001 — Rol sin permisos declarados

**Cuándo ocurre:** el rol se creó sin permisos o se le revocaron todos.

1. El sistema devuelve el rol con una lista de permisos vacía.
2. Es un estado válido: el rol existe pero no concede nada.

### FA-002 — Rol raíz

**Cuándo ocurre:** se consulta el rol sin rol padre.

1. El sistema devuelve el rol con el rol padre vacío.

## 10. Excepciones

### EX-001 — Rol inexistente

**Condición:** el identificador no corresponde a ningún rol, o el rol está eliminado lógicamente.
**Respuesta del sistema:** informa que el rol no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador del rol no es válido. |
| `VAL-002` | Rol existente y no eliminado | El rol solicitado no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-016` | El sistema devuelve el rol con su lista completa de permisos declarados |
| `CA-SP-017` | El sistema devuelve el rol padre y el **número** de roles hijos directos |
| `CA-SP-018` | El sistema devuelve la lista de permisos vacía cuando el rol no declara ninguno |
| `CA-SP-019` | El sistema devuelve el rol padre vacío al consultar el rol raíz |
| `CA-SP-020` | El sistema informa que el rol no existe cuando está eliminado lógicamente |
| `CA-SP-021` | El sistema resuelve los permisos sin recorrer la cadena de ancestros |
| `CA-SP-022` | El sistema rechaza la consulta a un actor sin el permiso de lectura de roles |
| `CA-SP-149` | El sistema devuelve el número de usuarios que tienen el rol asignado |
| `CA-SP-150` | El tamaño de la respuesta no depende de cuántos roles hijos tenga el rol |

## 13. Casos límite

- **Rol eliminado lógicamente:** se trata como inexistente. Reconstruir qué era corresponde a la auditoría de eliminación, que conserva su estado (Art. V.13).
- **Rol con muchos permisos:** la lista se devuelve completa y sin paginar; los permisos de un rol son decenas, no miles.
- **Rol con muchos hijos:** no afecta al tamaño de la respuesta, porque solo se devuelve el conteo.
- **`USR` no disponible:** el conteo de usuarios depende de otro módulo. Hay que decidir en el plan si el detalle falla o si devuelve el conteo vacío indicando que no pudo obtenerse; degradar es preferible a que una consulta de roles caiga por un módulo ajeno.
- **Identificador con formato incorrecto:** se rechaza por validación, no se trata como rol inexistente.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 20-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se consulta el detalle de un rol eliminado? | No, se trata como inexistente. La auditoría de eliminación ya conserva el estado del rol al borrarse (Art. V.13), que es el mecanismo diseñado para reconstruir qué era. Duplicarlo aquí añadiría una rama al endpoint y una segunda fuente del mismo dato |
| 2 | ¿Los roles hijos se devuelven completos? | No, solo el conteo. El listado se obtiene con `RF-SP-002` filtrando por rol padre, que ya existe y ya está paginado. Así el tamaño de la respuesta no depende de cuántos hijos tenga el rol |
| 3 | ¿Se indica cuántos usuarios tienen el rol? | Sí. Es la pregunta que se hace antes de desactivar o eliminar un rol. Cuesta una consulta, y `RF-SP-009` ya obliga a que `USR` publique esa interfaz |
