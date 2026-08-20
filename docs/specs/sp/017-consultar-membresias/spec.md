# SPEC — `RF-SP-017` Consultar membresías

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-017` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Ver los niveles de membresía definidos y, sobre todo, en qué orden están.

## 2. Contexto

En una membresía el dato relevante no es cuándo se creó, sino **qué lugar ocupa** en la cadena: de ello depende a qué contenido llega quien la tiene. Por eso este listado se presenta siempre en el orden de la jerarquía, del nivel superior al inferior, y no por fecha.

Es también la consulta previa a `RF-SP-016`: para insertar una membresía hay que saber entre cuáles va.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Consulta las membresías |
| Administrador | Consulta las membresías |

## 4. Alcance

### 4.1 Incluye

- Listado de membresías en el orden de la cadena, del nivel superior al inferior.
- Datos de identificación de cada una y su posición.

### 4.2 No incluye

- Qué contenidos habilita cada nivel: corresponde a los módulos de academia y productos.
- Cuántas personas tienen cada membresía → módulo `USR`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-006` | Toda membresía está sujeta a una de mayor nivel, salvo la superior | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página y tamaño | No | Paginación | Máximo definido en configuración |
| Búsqueda | No | Texto libre sobre código y nombre | — |

No se admite ordenamiento arbitrario: el orden de la cadena es la información.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Membresías | Código, nombre, descripción y nivel de cada una, en orden de jerarquía |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de membresías.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el listado de membresías.
2. El sistema valida los parámetros de paginación.
3. El sistema recupera las membresías ordenadas por su nivel, de mayor a menor.
4. El sistema devuelve la página solicitada con su información de paginación.

## 9. Flujos alternativos

### FA-001 — Sin membresías definidas

**Cuándo ocurre:** todavía no se creó ninguna.

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
| `CA-SP-120` | El sistema devuelve las membresías en el orden de la cadena, del nivel superior al inferior |
| `CA-SP-121` | El sistema devuelve el nivel de cada membresía |
| `CA-SP-122` | El sistema devuelve una colección vacía, y no un error, cuando no hay membresías |
| `CA-SP-123` | El orden devuelto refleja el reordenamiento tras insertar una membresía intermedia |
| `CA-SP-124` | El sistema rechaza la consulta a un actor sin el permiso de lectura de membresías |

## 13. Casos límite

- **Una sola membresía:** se devuelve como superior, sin membresía por encima ni por debajo.
- **Cadena rota por un fallo de datos:** una membresía huérfana no debe romper el listado; conviene que se devuelva igualmente y que la incoherencia sea detectable.
- **Paginación sobre una cadena:** paginar un orden lineal parte la cadena entre páginas. Ver pregunta abierta 1.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿Tiene sentido paginar? Los niveles de membresía son pocos, y paginarlos parte la cadena, que es justo lo que se quiere ver de una vez | Responsable técnico | Abierta |
| 2 | ¿Debe indicarse cuántas personas tienen cada membresía? Es dato de `USR`, pero es la primera pregunta antes de reorganizar niveles | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
