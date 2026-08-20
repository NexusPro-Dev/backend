# SPEC — `RF-SP-002` Consultar roles

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-002` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Permitir ver qué roles existen en el sistema, en qué estado están y cómo se ordenan entre sí.

## 2. Contexto

Antes de crear un rol, de asignarlo a alguien o de modificar sus permisos hay que saber qué hay. Es también la entrada natural a la administración de accesos: de aquí se navega al detalle de cada rol.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Consulta todos los roles |
| Administrador | Consulta todos los roles |

## 4. Alcance

### 4.1 Incluye

- Listado paginado de roles con sus datos de identificación y estado.
- Filtro por estado, por clasificación y por rol padre.
- Búsqueda por código o nombre.

### 4.2 No incluye

- Los permisos de cada rol → `RF-SP-003`, para no cargar el listado con datos que casi nunca se usan en él.
- Los roles eliminados lógicamente, salvo que se soliciten de forma explícita.

## 5. Reglas de negocio aplicables

Ninguna regla de negocio gobierna esta consulta. El alcance de los datos es global: los roles son visibles para cualquier actor con el permiso de lectura.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página | No | Página solicitada | Por defecto la primera |
| Tamaño | No | Elementos por página | Por defecto y máximo definidos en configuración |
| Orden | No | Campo y sentido de ordenamiento | Solo campos del propio rol |
| Estado | No | Filtro por activo o inactivo | Uno de los estados definidos |
| Clasificación | No | Filtro por funcionario, vendedor o consumidor | Uno de los valores definidos |
| Rol padre | No | Filtro por rol padre | Debe existir |
| Búsqueda | No | Texto libre sobre código y nombre | — |
| Incluir eliminados | No | Incorpora los roles con borrado lógico | Por defecto no |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Roles | Código, nombre, descripción, clasificación, rol padre y estado de cada uno |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de roles.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el listado de roles, con o sin filtros.
2. El sistema valida los parámetros de paginación, orden y filtros.
3. El sistema recupera los roles que cumplen los filtros, excluyendo los eliminados salvo indicación contraria.
4. El sistema devuelve la página solicitada junto con la información de paginación.

## 9. Flujos alternativos

### FA-001 — Sin resultados

**Cuándo ocurre:** ningún rol cumple los filtros.

1. El sistema devuelve una colección vacía con la paginación en cero.
2. **No** se trata como error: la ausencia de resultados es una respuesta válida.

## 10. Excepciones

### EX-001 — Parámetro de paginación inválido

**Condición:** la página es negativa, o el tamaño excede el máximo configurado.
**Respuesta del sistema:** rechaza la consulta e informa el parámetro inválido y su límite.

### EX-002 — Campo de ordenamiento desconocido

**Condición:** se solicita ordenar por un campo que no pertenece al rol.
**Respuesta del sistema:** rechaza la consulta e informa qué campos admite.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Página no negativa | La página solicitada no es válida. |
| `VAL-002` | Tamaño dentro del máximo configurado | El tamaño de página excede el máximo permitido. |
| `VAL-003` | Campo de ordenamiento permitido | No es posible ordenar por ese campo. |
| `VAL-004` | Estado y clasificación dentro de su dominio | El valor del filtro no es válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-009` | El sistema devuelve los roles paginados, con total de elementos y de páginas |
| `CA-SP-010` | El sistema excluye por defecto los roles eliminados lógicamente |
| `CA-SP-011` | El sistema incluye los eliminados cuando se solicita de forma explícita |
| `CA-SP-012` | El sistema filtra correctamente por estado, clasificación y rol padre |
| `CA-SP-013` | El sistema devuelve una colección vacía, y no un error, cuando no hay coincidencias |
| `CA-SP-014` | El sistema rechaza un tamaño de página superior al máximo configurado |
| `CA-SP-015` | El sistema rechaza la consulta a un actor sin el permiso de lectura de roles |

## 13. Casos límite

- **Página más allá del último resultado:** devuelve colección vacía, no error.
- **Búsqueda con caracteres especiales:** no debe alterar la consulta ni provocar error; se trata como texto literal.
- **Búsqueda vacía o solo espacios:** equivale a no filtrar.
- **Filtro por rol padre inexistente:** devuelve colección vacía; no es un error de la consulta.
- **Catálogo con un único rol:** el rol raíz aparece con rol padre vacío.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿La búsqueda distingue mayúsculas y acentos, o es insensible a ambos? | Responsable técnico | Abierta |
| 2 | ¿Cuál es el tamaño de página por defecto y el máximo? | Responsable técnico | Abierta |
| 3 | ¿El listado debe indicar cuántos usuarios tiene asignados cada rol? Sería útil antes de eliminarlo, pero encarece la consulta | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
