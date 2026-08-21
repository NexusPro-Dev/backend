# SPEC — `RF-SP-017` Consultar membresías

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-017` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

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

- Listado completo de membresías, sin paginar, en el orden de la cadena, del nivel superior al inferior.
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
| Búsqueda | No | Texto libre sobre código y nombre | Insensible a mayúsculas y a acentos |

No se admite ordenamiento arbitrario: el orden de la cadena es la información.

La cadena **no se pagina**. Partir un orden lineal entre páginas destruye justamente lo que se viene a ver, y los niveles de membresía son unos pocos.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Membresías | Código, nombre, descripción y nivel de cada una, en orden de jerarquía, en una única colección |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de membresías.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el listado de membresías.
2. El sistema recupera las membresías ordenadas por su nivel, de mayor a menor.
3. El sistema devuelve la cadena completa.

## 9. Flujos alternativos

### FA-001 — Sin membresías definidas

**Cuándo ocurre:** todavía no se creó ninguna.

1. El sistema devuelve una colección vacía; no es un error.

## 10. Excepciones

Ninguna propia. Los fallos de autenticación y de autorización se resuelven en el borde, como en cualquier endpoint.

## 11. Validaciones

Ninguna. La búsqueda es opcional y un texto sin coincidencias produce una colección vacía, que no es un error.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-120` | El sistema devuelve la cadena completa, sin paginar, en orden del nivel superior al inferior |
| `CA-SP-121` | El sistema devuelve el nivel de cada membresía |
| `CA-SP-122` | El sistema devuelve una colección vacía, y no un error, cuando no hay membresías |
| `CA-SP-123` | El orden devuelto refleja el reordenamiento tras insertar una membresía intermedia |
| `CA-SP-124` | El sistema rechaza la consulta a un actor sin el permiso de lectura de membresías |

## 13. Casos límite

- **Una sola membresía:** se devuelve como superior, sin membresía por encima ni por debajo.
- **Cadena rota por un fallo de datos:** una membresía huérfana no debe romper el listado; conviene que se devuelva igualmente y que la incoherencia sea detectable.
- **Búsqueda sobre una cadena:** filtrar por texto devuelve membresías sueltas y no una cadena continua. Es correcto —se está buscando, no recorriendo— pero el consumidor no debe suponer que lo devuelto sea contiguo.

## 14. Preguntas abiertas

Ninguna. Las dos se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Tiene sentido paginar? | **No se pagina.** Es el caso más claro de los tres catálogos que se decidieron a la vez —con `RF-SP-010` y `RF-SP-021`—: aquí la información *es* el orden, y partirlo entre páginas destruye lo que se viene a consultar. Los niveles de membresía son unos pocos y la respuesta completa es pequeña |
| 2 | ¿Cuántas personas tienen cada membresía? | **No.** En `RF-SP-003` sí se aceptó el conteo de usuarios por rol, porque allí decidía si el rol podía desactivarse o eliminarse. Una membresía ni se elimina ni se desactiva (`RN-SP-008`), de modo que el conteo no condiciona ninguna decisión que se pueda tomar desde aquí. Es dato de `USR` y lo pedirá quien lo necesite |
