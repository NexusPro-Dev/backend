# SPEC — `RF-SP-010` Consultar catálogo de permisos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-010` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

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

- Listado paginado de permisos con su código, recurso, acción y descripción legible.
- Filtro por recurso y por acción.
- Búsqueda por código o descripción.

### 4.2 No incluye

- Crear, editar o eliminar permisos: el catálogo es inmutable por API (`RN-SP-004`).
- Qué roles declaran cada permiso: ver pregunta abierta 2.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-004` | Los permisos no se crean, editan ni eliminan por la API | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página | No | Página solicitada | Por defecto la primera |
| Tamaño | No | Elementos por página | Máximo definido en configuración |
| Recurso | No | Filtro por recurso | — |
| Acción | No | Filtro por acción | — |
| Búsqueda | No | Texto libre sobre código y descripción | — |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Permisos | Código, recurso, acción, nombre y descripción de cada uno |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura del catálogo de permisos.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el catálogo, con o sin filtros.
2. El sistema valida los parámetros de paginación y los filtros.
3. El sistema recupera los permisos que cumplen los filtros.
4. El sistema devuelve la página solicitada con su información de paginación.

## 9. Flujos alternativos

### FA-001 — Sin resultados

**Cuándo ocurre:** ningún permiso cumple los filtros.

1. El sistema devuelve una colección vacía; no es un error.

## 10. Excepciones

### EX-001 — Parámetro de paginación inválido

**Condición:** la página es negativa o el tamaño excede el máximo configurado.
**Respuesta del sistema:** rechaza la consulta e informa el límite aplicable.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Página no negativa | La página solicitada no es válida. |
| `VAL-002` | Tamaño dentro del máximo configurado | El tamaño de página excede el máximo permitido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-073` | El sistema devuelve el catálogo paginado con código, recurso, acción y descripción |
| `CA-SP-074` | El sistema filtra por recurso y por acción |
| `CA-SP-075` | El sistema devuelve una colección vacía, y no un error, cuando no hay coincidencias |
| `CA-SP-076` | El sistema no expone ninguna operación de escritura sobre el catálogo |
| `CA-SP-077` | El sistema rechaza la consulta a un actor sin el permiso de lectura del catálogo |

## 13. Casos límite

- **Catálogo vacío:** solo ocurriría si faltara la migración de siembra; devuelve colección vacía, y conviene que el sistema lo detecte al arrancar.
- **Permiso sin descripción:** la descripción es opcional; se devuelve vacía sin error.
- **Búsqueda con caracteres especiales:** se trata como texto literal.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿El catálogo debe poder consultarse sin paginar? Al ser decenas de elementos y usarse para componer un rol, paginar puede estorbar más que ayudar | Responsable técnico | Abierta |
| 2 | ¿Debe indicarse cuántos roles declaran cada permiso? Es útil antes de reorganizar, pero encarece la consulta | Responsable técnico | Abierta |
| 3 | ¿Se agrupan los permisos por recurso en la respuesta, o se devuelven planos? | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
