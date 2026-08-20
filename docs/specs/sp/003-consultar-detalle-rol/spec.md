# SPEC — `RF-SP-003` Consultar detalle de un rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-003` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

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
- Su rol padre y sus roles hijos directos.

### 4.2 No incluye

- Los usuarios que tienen el rol asignado → módulo `USR`.
- Los permisos efectivos de una persona, que son la unión de sus roles (`RN-SEG-009`) → módulo `USR`.

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
| Roles hijos | Roles cuyos privilegios acota este, si los tiene |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de roles.
- El rol existe.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el detalle de un rol.
2. El sistema recupera el rol y su lista de permisos declarados.
3. El sistema recupera su rol padre y sus roles hijos directos.
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
| `CA-SP-017` | El sistema devuelve el rol padre y los roles hijos directos |
| `CA-SP-018` | El sistema devuelve la lista de permisos vacía cuando el rol no declara ninguno |
| `CA-SP-019` | El sistema devuelve el rol padre vacío al consultar el rol raíz |
| `CA-SP-020` | El sistema informa que el rol no existe cuando está eliminado lógicamente |
| `CA-SP-021` | El sistema resuelve los permisos sin recorrer la cadena de ancestros |
| `CA-SP-022` | El sistema rechaza la consulta a un actor sin el permiso de lectura de roles |

## 13. Casos límite

- **Rol eliminado lógicamente:** se trata como inexistente, salvo que la pregunta abierta 1 decida lo contrario.
- **Rol con muchos permisos:** la lista se devuelve completa y sin paginar; los permisos de un rol son decenas, no miles.
- **Rol con muchos hijos:** conviene acotar o paginar; ver pregunta abierta 2.
- **Identificador con formato incorrecto:** se rechaza por validación, no se trata como rol inexistente.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿Debe poder consultarse el detalle de un rol eliminado lógicamente, para auditoría? | Responsable técnico | Abierta |
| 2 | ¿Los roles hijos se devuelven completos o se acotan? Si un rol tuviera decenas de hijos, el detalle crecería sin control | Responsable técnico | Abierta |
| 3 | ¿Debe indicarse cuántos usuarios tienen el rol asignado? Es dato de `USR`, pero es lo primero que se pregunta antes de modificarlo | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
