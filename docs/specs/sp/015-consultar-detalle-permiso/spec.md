# SPEC — `RF-SP-015` Consultar detalle de un permiso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-015` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |

---

## 1. Objetivo

Conocer con precisión qué habilita un permiso concreto antes de incorporarlo a un rol.

## 2. Contexto

El código de un permiso (`roles:create`) es compacto pero no siempre explícito. Antes de concederlo hay que poder leer qué significa exactamente, sobre todo cuando el nombre del recurso no coincide con el vocabulario del negocio.

Es una consulta de apoyo a `RF-SP-005`: se llega a ella desde el catálogo.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Consulta cualquier permiso |
| Administrador | Consulta cualquier permiso |

## 4. Alcance

### 4.1 Incluye

- Datos completos de un permiso: código, recurso, acción, nombre y descripción.

### 4.2 No incluye

- Modificarlo: el catálogo es inmutable por API (`RN-SP-004`).
- Los roles que lo declaran: es el recorrido inverso del catálogo y corresponde a una consulta propia, igual que en `RF-SP-010`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-004` | Los permisos no se crean, editan ni eliminan por la API | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador | Sí | Permiso que se consulta | Debe existir. Se accede por identificador, no por código |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Permiso | Código, recurso, acción, nombre y descripción |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura del catálogo.
- El permiso existe.

**Postcondiciones**

- Ninguna: la consulta no altera el estado del sistema.

## 8. Flujo principal

1. El actor solicita el detalle de un permiso.
2. El sistema recupera el permiso.
3. El sistema devuelve sus datos.

## 9. Flujos alternativos

Ninguno.

## 10. Excepciones

### EX-001 — Permiso inexistente

**Condición:** el identificador no corresponde a ningún permiso del catálogo.
**Respuesta del sistema:** informa que el permiso no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador del permiso no es válido. |
| `VAL-002` | Permiso existente | El permiso solicitado no existe. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-078` | El sistema devuelve el permiso con su código, recurso, acción, nombre y descripción |
| `CA-SP-079` | El sistema informa que el permiso no existe cuando el identificador no corresponde a ninguno |
| `CA-SP-080` | El sistema rechaza la consulta a un actor sin el permiso de lectura del catálogo |

## 13. Casos límite

- **Permiso sin descripción:** se devuelve vacía, sin error.
- **Identificador con formato incorrecto:** se rechaza por validación, no como permiso inexistente.

## 14. Preguntas abiertas

Ninguna. Las dos se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Debe devolver qué roles declaran el permiso? | **No.** Misma resolución y mismo motivo que la pregunta 2 de `RF-SP-010`: es el recorrido inverso del catálogo, encarece una consulta trivial y la relación se consulta desde el lado que ya la tiene. Si hace falta, será una consulta propia con su requerimiento |
| 2 | ¿Se accede por identificador o también por código? | **Solo por identificador**, como en `RF-SP-003`. Admitir dos formas de direccionar el mismo recurso obliga a distinguir en cada petición si lo recibido es un identificador o un código, y a decidir qué ocurre cuando un código parece un identificador. El código sigue siendo la vía legible para *encontrar* el permiso: es filtro y búsqueda en `RF-SP-010` |
