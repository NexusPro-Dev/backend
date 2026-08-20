# SPEC — `RF-SP-021` Consultar países

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-021` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

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

- Listado de países con su código y su nombre, ordenado alfabéticamente por nombre.
- Búsqueda por código o por nombre.

### 4.2 No incluye

- Crear países → `RF-SP-020`.
- Editar o eliminar países (`RN-SP-009`).
- Divisiones internas del país.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-009` | Los países no se editan ni eliminan | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página y tamaño | No | Paginación | Por defecto 20, máximo 100 (`architecture.md` §7.4) |
| Búsqueda | No | Texto libre sobre código y nombre | Insensible a mayúsculas y a acentos |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Países | Código y nombre de cada uno, ordenados alfabéticamente por nombre |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de países.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el catálogo de países, con o sin búsqueda.
2. El sistema valida los parámetros de paginación.
3. El sistema recupera los países que cumplen la búsqueda, ordenados por nombre.
4. El sistema devuelve la página solicitada con su información de paginación.

## 9. Flujos alternativos

### FA-001 — Sin resultados

**Cuándo ocurre:** ningún país coincide con la búsqueda.

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
| `CA-SP-140` | El sistema devuelve los países paginados y ordenados alfabéticamente por nombre |
| `CA-SP-141` | El sistema filtra por código y por nombre mediante la búsqueda |
| `CA-SP-142` | El sistema devuelve una colección vacía, y no un error, cuando no hay coincidencias |
| `CA-SP-143` | El sistema rechaza la consulta a un actor sin el permiso de lectura de países |

## 13. Casos límite

- **Catálogo vacío:** devuelve colección vacía. Si el catálogo se siembra por migración, no debería ocurrir.
- **Búsqueda insensible a acentos:** buscar «panama» debería encontrar «Panamá». Ver pregunta abierta 1.
- **Ordenamiento con acentos:** el orden alfabético debe seguir la configuración regional del idioma, no el orden de bytes.
- **Búsqueda con caracteres especiales:** se trata como texto literal.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿La búsqueda es insensible a acentos y mayúsculas? | **Resuelta el 20-08-2026: sí a ambos**, igual que en `RF-SP-002`. Buscar «panama» encuentra «Panamá» |
| 2 | ¿Tiene sentido paginar un catálogo de menos de doscientos elementos que alimenta un selector? | Responsable técnico | Abierta |
| 3 | ¿El nombre se devuelve en un solo idioma, o el catálogo contempla traducciones? | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
