# SPEC — `RF-SP-021` Consultar países

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-021` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Disponer del catálogo de países para poder seleccionarlos donde se necesiten.

## 2. Contexto

Es una consulta de apoyo: alimenta los selectores de país en el alta de personas y en cualquier dato que requiera ubicación. Su valor está en la disponibilidad y en el orden, no en la riqueza de los datos.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier rol autenticado con el permiso | Consulta el catálogo para seleccionar un país |

## 4. Alcance

### 4.1 Incluye

- Listado completo de países, sin paginar, con su código y su nombre, ordenado alfabéticamente por nombre.
- Por defecto solo los países activos; los inactivos se piden explícitamente.
- Búsqueda por código o por nombre.

### 4.2 No incluye

- Crear países → `RF-SP-020`.
- Editar o eliminar países (`RN-SP-009`).
- Cambiar el estado de un país → `RF-SP-022`.
- Divisiones internas del país.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-009` | Los países no se editan ni eliminan; lo único que puede cambiarse es su estado | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Búsqueda | No | Texto libre sobre código y nombre | Insensible a mayúsculas y a acentos |
| Incluir inactivos | No | Incorpora al resultado los países desactivados | Por defecto no |

El catálogo **no se pagina**: alimenta selectores, y un selector necesita todas sus opciones. Son menos de doscientos elementos y el resultado es cacheable.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Países | Código, nombre y estado de cada uno, ordenados alfabéticamente por nombre |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de países.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el catálogo de países, con o sin búsqueda.
2. El sistema recupera los países activos que cumplen la búsqueda, o todos si se pidieron también los inactivos, ordenados por nombre.
3. El sistema devuelve el catálogo completo resultante.

## 9. Flujos alternativos

### FA-001 — Sin resultados

**Cuándo ocurre:** ningún país coincide con la búsqueda.

1. El sistema devuelve una colección vacía; no es un error.

## 10. Excepciones

Ninguna propia. Los fallos de autenticación y de autorización se resuelven en el borde, como en cualquier endpoint.

## 11. Validaciones

Ninguna. Los dos parámetros son opcionales y una búsqueda sin coincidencias produce una colección vacía, que no es un error.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-140` | El sistema devuelve el catálogo completo, sin paginar, ordenado alfabéticamente por nombre |
| `CA-SP-172` | Los países inactivos no aparecen salvo que se soliciten explícitamente |
| `CA-SP-141` | El sistema filtra por código y por nombre mediante la búsqueda |
| `CA-SP-142` | El sistema devuelve una colección vacía, y no un error, cuando no hay coincidencias |
| `CA-SP-143` | El sistema rechaza la consulta a un actor sin el permiso de lectura de países |

## 13. Casos límite

- **Catálogo vacío:** es el estado real al arrancar, porque los países se dan de alta por la API (`RF-SP-020`) y no se siembran. Devuelve colección vacía, no un error.
- **Búsqueda insensible a acentos:** buscar «panama» encuentra «Panamá».
- **País inactivo ya referenciado:** deja de ofrecerse en el listado, pero los datos que ya lo tenían asignado siguen resolviéndolo. Por eso desactivar no es borrar.
- **Ordenamiento con acentos:** el orden alfabético debe seguir la configuración regional del idioma, no el orden de bytes.
- **Búsqueda con caracteres especiales:** se trata como texto literal.

## 14. Preguntas abiertas

Ninguna. La primera se resolvió el 20-08-2026 y las otras dos el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La búsqueda es insensible a acentos y mayúsculas? | **Sí a ambos**, igual que en `RF-SP-002`. Buscar «panama» encuentra «Panamá» |
| 2 | ¿Tiene sentido paginar un catálogo que alimenta un selector? | **No se pagina.** Un selector necesita todas sus opciones, y menos de doscientos elementos con código y nombre es una respuesta pequeña y cacheable. Es la misma decisión que en `RF-SP-010` y `RF-SP-017`, tomada a la vez y por el mismo motivo. Se conserva la búsqueda, que es lo que de verdad acota |
| 3 | ¿El nombre contempla traducciones? | **Un solo idioma.** Traducir un catálogo es parte de una decisión de internacionalización que alcanza a toda la interfaz —mensajes, formatos, monedas— y no puede resolverse bien dentro de un catálogo suelto. Cuando exista esa decisión, este catálogo la seguirá |
