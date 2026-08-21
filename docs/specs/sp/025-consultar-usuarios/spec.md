# SPEC — `RF-SP-025` Consultar usuarios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-025` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Permitir ver quién tiene acceso al sistema, en qué estado está cada persona y qué roles porta.

## 2. Contexto

Es la pantalla desde la que se administra el acceso: de aquí se navega al detalle de una persona, se decide a quién desactivar y se comprueba quién porta un rol antes de tocarlo. `RF-SP-003` devuelve **cuántos** usuarios tiene un rol y remite aquí para saber **quiénes** son.

También es la consulta que más datos personales expone de una sola vez, y por eso el filtro por rol importa tanto como la búsqueda: sin él, averiguar quién es administrador obligaría a recorrer la lista entera.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Consulta todos los usuarios |
| Administrador | Consulta todos los usuarios |

## 4. Alcance

### 4.1 Incluye

- Listado paginado de usuarios con sus datos de identificación, su estado y sus roles.
- Filtro por estado, por rol y por membresía.
- Búsqueda por nombre de usuario, correo y nombre de la persona.

### 4.2 No incluye

- Los permisos efectivos de cada persona → `RF-SP-026`. Son la unión de los de sus roles (`RN-SEG-009`) y calcularlos por fila haría del listado una consulta cara.
- Cualquier dato de la credencial. Ni el hash, ni su antigüedad, ni nada derivado de ella.
- Los usuarios eliminados lógicamente, salvo que se soliciten de forma explícita.
- Crear, editar o cambiar el estado de un usuario → `RF-SP-024`, `RF-SP-027` y `RF-SP-028`.

## 5. Reglas de negocio aplicables

Ninguna regla de negocio gobierna esta consulta. El alcance de los datos es global: cualquier actor con el permiso de lectura ve a todos los usuarios.

Conviene dejarlo dicho de forma explícita, porque **el día que exista alcance comercial dejará de ser cierto**: un agente no debería ver a los clientes de otro. Esa restricción es la decisión D-22 de `requirements/sp.md` §4.1, todavía sin resolver, y cuando llegue afectará a esta consulta antes que a ninguna otra.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página | No | Página solicitada | Por defecto la primera |
| Tamaño | No | Elementos por página | Por defecto 20, máximo 100 (`architecture.md` §7.4) |
| Orden | No | Campo y sentido de ordenamiento | Solo campos del propio usuario |
| Estado | No | Filtro por estado del usuario | Uno de los estados definidos en `security.md` §3.1 |
| Rol | No | Filtro por rol asignado | Si se indica uno inexistente, el resultado es una colección vacía, no un error |
| Membresía | No | Filtro por membresía vigente | Solo tiene sentido sobre consumidores |
| Búsqueda | No | Texto libre sobre nombre de usuario, correo y nombre | Insensible a mayúsculas y a acentos |
| Incluir eliminados | No | Incorpora los usuarios con borrado lógico | Por defecto no |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Usuarios | Nombre de usuario, correo, nombre, estado y roles de cada uno |
| Membresía | Membresía vigente, cuando la persona tiene una |
| Marca de eliminación | Presente solo cuando se piden los eliminados, para poder distinguirlos de los vigentes |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de usuarios.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el listado de usuarios, con o sin filtros.
2. El sistema valida los parámetros de paginación, orden y filtros.
3. El sistema recupera los usuarios que cumplen los filtros, excluyendo los eliminados salvo indicación contraria.
4. El sistema devuelve la página solicitada junto con la información de paginación.

## 9. Flujos alternativos

### FA-001 — Sin resultados

**Cuándo ocurre:** ningún usuario cumple los filtros.

1. El sistema devuelve una colección vacía con la paginación en cero.
2. **No** se trata como error: la ausencia de resultados es una respuesta válida.

## 10. Excepciones

### EX-001 — Parámetro de paginación inválido

**Condición:** la página es negativa, o el tamaño excede el máximo configurado.
**Respuesta del sistema:** rechaza la consulta e informa el parámetro inválido y su límite.

### EX-002 — Campo de ordenamiento desconocido

**Condición:** se solicita ordenar por un campo que no pertenece al usuario.
**Respuesta del sistema:** rechaza la consulta e informa qué campos admite. En particular, no se admite ordenar por ningún campo de la credencial.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Página no negativa | La página solicitada no es válida. |
| `VAL-002` | Tamaño dentro del máximo configurado | El tamaño de página excede el máximo permitido. |
| `VAL-003` | Campo de ordenamiento permitido | No es posible ordenar por ese campo. |
| `VAL-004` | Estado dentro de su dominio | El valor del filtro no es válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-203` | El sistema devuelve los usuarios paginados, con total de elementos y de páginas |
| `CA-SP-204` | El sistema excluye por defecto los usuarios eliminados lógicamente, e incluye la marca de eliminación cuando se solicitan |
| `CA-SP-205` | El sistema filtra por estado, por rol y por membresía |
| `CA-SP-206` | La búsqueda encuentra a una persona escribiendo su nombre sin acentos y en otra caja |
| `CA-SP-207` | El sistema devuelve una colección vacía, y no un error, cuando no hay coincidencias |
| `CA-SP-208` | La respuesta no contiene ningún dato de la credencial, ni siquiera transformado |
| `CA-SP-209` | El listado no incluye los permisos efectivos de cada persona |
| `CA-SP-343` | Cada fila devuelve la **lista completa** de roles de la persona, no su conteo, y vacía cuando no tiene ninguno |
| `CA-SP-344` | La búsqueda encuentra a una persona por un **fragmento** de su correo |
| `CA-SP-345` | El listado no devuelve el momento en que expira un bloqueo, que solo aparece en `RF-SP-026` |
| `CA-SP-210` | El sistema rechaza un tamaño de página superior al máximo configurado |
| `CA-SP-211` | El sistema rechaza la consulta a un actor sin el permiso de lectura de usuarios |

## 13. Casos límite

- **Página más allá del último resultado:** devuelve colección vacía, no error.
- **Búsqueda sin acentos:** «peres» no debe encontrar «Pérez», pero «perez» sí. Es el mismo criterio de `RF-SP-002` y exige el mismo índice funcional.
- **Búsqueda vacía o solo espacios:** equivale a no filtrar.
- **Filtro por rol inexistente:** devuelve colección vacía; no es un error de la consulta.
- **Usuario sin roles:** aparece en el listado con la lista de roles vacía. Es un estado válido tras `RF-SP-024`.
- **Usuario con muchos roles:** la lista de roles se devuelve completa por fila. Los roles de una persona son unos pocos, no decenas; si dejaran de serlo, esta decisión habría que revisarla.
- **Búsqueda por correo parcial:** encuentra por fragmento, lo que convierte el listado en una forma de comprobar si un correo está registrado. Es aceptable porque el endpoint exige `users:read`, a diferencia de los de autenticación, que no deben revelarlo (`security.md` §5.5).

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El listado devuelve los roles de cada usuario, o solo el conteo? | **Los roles completos.** Son unos pocos por persona, son el dato que se mira al administrar accesos, y sin ellos el filtro por rol devuelve una lista que no explica por qué cada fila está en ella. Es la decisión **contraria** a la de `RF-SP-002`, cuyo `CA-SP-148` deja fuera el número de usuarios por rol, y la asimetría es deliberada: allí el dato costaba una consulta por fila y aquí sale de una tabla de asociación que ya se recorre. `CA-SP-343` lo verifica, incluido el caso de la persona sin ningún rol, que devuelve la lista vacía. Si algún día una persona llegara a portar decenas de roles, esta decisión habría que revisarla (`§13`) |
| 2 | ¿Se puede filtrar y buscar por correo? | **Sí, y por fragmento.** El endpoint exige `users:read`, que es un permiso de administración; la prohibición de `security.md` §5.5 de no revelar si una cuenta existe alcanza a los **endpoints de autenticación**, que son públicos, y no a este. El coste asumido es que quien tenga el permiso puede comprobar si una dirección está registrada, y es aceptable porque ese mismo actor puede ver la lista entera de todos modos. `CA-SP-344` lo verifica |
| 3 | ¿Debe existir un filtro por usuarios bloqueados que además muestre hasta cuándo lo están? | **El filtro sí, el momento de expiración no.** `BLOQUEADO` ya es un valor del filtro por estado (`CA-SP-205`), que es lo que resuelve la consulta operativa: quién no puede entrar. El momento en que expira el bloqueo es un dato nulo en la inmensa mayoría de las filas y se devuelve **solo en el detalle**, `RF-SP-026` (`CA-SP-217`). `CA-SP-345` deja verificado que el listado no lo incluye |
