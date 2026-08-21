# SPEC — `RF-SP-002` Consultar roles

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-002` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 20-08-2026 |

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
| Tamaño | No | Elementos por página | Por defecto 20, máximo 100 (`architecture.md` §7.4) |
| Orden | No | Campo y sentido de ordenamiento | Solo campos del propio rol |
| Estado | No | Filtro por activo o inactivo | Uno de los estados definidos |
| Clasificación | No | Filtro por funcionario, vendedor o consumidor | Uno de los valores definidos |
| Rol padre | No | Filtro por rol padre | Si se indica uno inexistente, el resultado es una colección vacía, no un error |
| Búsqueda | No | Texto libre sobre código y nombre | Insensible a mayúsculas y a acentos |
| Incluir eliminados | No | Incorpora los roles con borrado lógico | Por defecto no |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Roles | Código, nombre, descripción, clasificación, rol padre y estado de cada uno |
| Marca de eliminación | Presente solo cuando se piden los eliminados. Sin ella, el listado mezclaría vigentes y eliminados sin poder distinguirlos (`CA-SP-011`) |
| Marca de rol de sistema | Permite que la interfaz no ofrezca editar un rol que `RN-SEG-012` va a rechazar |
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
| `CA-SP-147` | La búsqueda encuentra un rol escribiendo su nombre sin acentos y en otra caja |
| `CA-SP-148` | El listado no incluye el número de usuarios asignados a cada rol |

## 13. Casos límite

- **Página más allá del último resultado:** devuelve colección vacía, no error.
- **Búsqueda con caracteres especiales:** no debe alterar la consulta ni provocar error; se trata como texto literal.
- **Búsqueda sin acentos:** «administracion» debe encontrar «Administración», y «CONTABILIDAD» debe encontrar «Contabilidad».
- **Búsqueda vacía o solo espacios:** equivale a no filtrar.
- **Filtro por rol padre inexistente:** devuelve colección vacía; no es un error de la consulta.
- **Catálogo con un único rol:** el rol raíz aparece con rol padre vacío.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 20-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La búsqueda distingue mayúsculas y acentos? | Insensible a ambos. En español lo normal es teclear sin tildes, y una búsqueda que no las ignora obliga a escribir el término exactamente como se registró. Exige la extensión `unaccent` y un índice funcional |
| 2 | ¿Tamaño de página por defecto y máximo? | 20 y 100, uniformes para todo el sistema. Fijado en `architecture.md` §7.4 |
| 3 | ¿El listado indica cuántos usuarios tiene cada rol? | No. La pregunta se hace sobre un rol concreto, no sobre la lista: en el detalle cuesta una consulta, aquí una por fila. Se resuelve en `RF-SP-003` |
