# SPEC — `RF-SP-010` Consultar catálogo de permisos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-010` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Saber qué permisos existen en el sistema, para poder componer roles con ellos.

## 2. Contexto

El catálogo de permisos es **datos, no código**: se puebla y se modifica por migración, nunca por la API (`RN-SP-004`). Esa decisión es la que permite crear un rol nuevo sin desplegar, pero solo funciona si quien administra los roles puede ver qué permisos hay disponibles y qué significa cada uno.

Es prerrequisito de `RF-SP-001` y de `RF-SP-005`: sin catálogo visible, componer un rol es adivinar.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Consulta el catálogo completo |
| Administrador | Consulta el catálogo completo |

## 4. Alcance

### 4.1 Incluye

- Catálogo completo de permisos, sin paginar, con su código, recurso, acción y descripción legible.
- Filtro por recurso y por acción.
- Búsqueda por código o descripción.

### 4.2 No incluye

- Crear, editar o eliminar permisos: el catálogo es inmutable por API (`RN-SP-004`).
- Qué roles declaran cada permiso: es el recorrido inverso del catálogo y corresponde a una consulta propia, no a esta.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-004` | Los permisos no se crean, editan ni eliminan por la API | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Recurso | No | Filtro por recurso | Si se indica uno que no existe, el resultado es una colección vacía, no un error |
| Acción | No | Filtro por acción | Ídem |
| Búsqueda | No | Texto libre sobre código y descripción | Insensible a mayúsculas y a acentos |

El catálogo **no se pagina**. Son decenas de elementos y su uso es componer un rol de una sola vez: paginarlo convierte «qué permisos hay» en un recorrido de páginas.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Permisos | Código, recurso, acción, nombre y descripción de cada uno, en una única colección |

Los permisos se devuelven **planos**, no agrupados por recurso: agrupar es una decisión de presentación, y el filtro por recurso ya cubre la necesidad de acotar.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura del catálogo de permisos.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el catálogo, con o sin filtros.
2. El sistema recupera los permisos que cumplen los filtros.
3. El sistema devuelve el catálogo completo resultante.

## 9. Flujos alternativos

### FA-001 — Sin resultados

**Cuándo ocurre:** ningún permiso cumple los filtros.

1. El sistema devuelve una colección vacía; no es un error.

## 10. Excepciones

Ninguna propia. Los fallos de autenticación y de autorización se resuelven en el borde, como en cualquier endpoint.

## 11. Validaciones

Ninguna. Los tres filtros son opcionales, y un valor que no corresponda a ningún permiso produce una colección vacía, que no es un error.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-073` | El sistema devuelve el catálogo completo, sin paginar y sin agrupar, con código, recurso, acción y descripción |
| `CA-SP-074` | El sistema filtra por recurso y por acción |
| `CA-SP-075` | El sistema devuelve una colección vacía, y no un error, cuando no hay coincidencias |
| `CA-SP-076` | El sistema no expone ninguna operación de escritura sobre el catálogo |
| `CA-SP-077` | El sistema rechaza la consulta a un actor sin el permiso de lectura del catálogo |

## 13. Casos límite

- **Catálogo vacío:** solo ocurriría si faltara la migración de siembra; devuelve colección vacía, y conviene que el sistema lo detecte al arrancar.
- **Permiso sin descripción:** la descripción es opcional; se devuelve vacía sin error.
- **Búsqueda con caracteres especiales:** se trata como texto literal.
- **Crecimiento del catálogo:** al devolverse entero, la respuesta crece con el sistema. Conviene medir su tamaño; si dejara de ser razonable, la decisión de no paginar habría que revisarla.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El catálogo debe poder consultarse sin paginar? | **No se pagina en absoluto.** El catálogo alimenta la composición de un rol, y esa tarea necesita verlo entero. Se conservan el filtro y la búsqueda, que son lo que de verdad acota. La misma decisión se tomó en `RF-SP-017` y `RF-SP-021`, por el mismo motivo: son catálogos que alimentan un selector |
| 2 | ¿Debe indicarse cuántos roles declaran cada permiso? | **No.** Es el recorrido inverso del catálogo y encarece una consulta hoy trivial. Es el criterio con el que `RF-SP-003` dejó fuera el listado de roles hijos: la relación se consulta desde el lado que ya la tiene. Si hace falta, será una consulta propia con su requerimiento |
| 3 | ¿Se agrupan los permisos por recurso? | **Se devuelven planos.** Agrupar es presentación, y el filtro por recurso ya permite acotar. Una respuesta plana es además la que menos supone sobre cómo se pinta |
