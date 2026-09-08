# SPEC — `RF-SP-052` Consultar el catálogo de brokers

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-052` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 08-09-2026 |

---

## 1. Objetivo

Disponer de la lista de brokers con los que opera la plataforma, para poder declarar en cuál se tiene cuenta.

## 2. Contexto

**El catálogo nace junto a la tabla que lo consume**, `user_brokers`, y solo se construye él. Quién declara una cuenta (`RF-SP-053`) y cómo la confirma el broker (`RF-SP-054`) están **registrados y sin decidir**, y ese orden es deliberado: la tabla y el catálogo son lo que no cambia según se responda a lo otro.

**De momento un broker guarda solo su nombre**, por decisión del responsable del proyecto. Es poco a propósito —el resto se añade cuando haga falta— y tiene una consecuencia que este documento prefiere dejar escrita antes que descubrir después: **si el nombre es lo único, el nombre es la clave**.

## 3. Actores

| Actor | Papel |
|---|---|
| Cualquier rol autenticado con `brokers:read` | Consulta el catálogo |

## 4. Alcance

### 4.1 Incluye

- Devolver **todos los brokers activos**, con su identificador, su nombre y su estado.
- Admitir que se pidan **también los inactivos**, de forma explícita.

### 4.2 No incluye

- **Crear, editar, eliminar ni cambiar de estado un broker** (`RN-SP-039`). El catálogo se puebla por migración.
- **Paginar.** Son unos pocos y se devuelven enteros, como los tipos de documento y las monedas.
- **Las cuentas de las personas.** Eso es `RF-SP-053`, y no existe.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-039` | El catálogo de brokers no se administra por API | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `includeInactive` | No | Si se devuelven también los brokers apagados | Booleano; por omisión `false` |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Brokers | Identificador, **nombre** y estado, ordenados por nombre |

**El orden lo fija el servidor y el cliente no puede cambiarlo**, igual que en el catálogo de monedas: un desplegable no necesita ordenarse de dos maneras, y admitir un parámetro de orden sería superficie que alguien tendría que validar.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `brokers:read`.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. El actor pide el catálogo.
2. El sistema devuelve los brokers **activos**, ordenados por nombre.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | Se piden también los inactivos | Se **añaden** a los activos, no los sustituyen |
| `FA-002` | El catálogo está vacío | Se devuelve la colección vacía con `200`. **No es un error de esta consulta**, aunque sí una señal de que falta la siembra |

## 10. Seguridad

Permiso `brokers:read`. **Sin él, `403`.**

**El catálogo no publica nada sensible** —el nombre de un broker es público en su propia web—, y aun así se exige permiso: es la misma decisión que toman los otros catálogos del módulo, y lo contrario abriría una ruta más sin autenticación por una comodidad que nadie ha pedido.

## 11. Validaciones

| Campo | Regla | Código |
|---|---|---|
| `includeInactive` | Booleano | `VAL-001` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-602` | El sistema devuelve el catálogo con **identificador, nombre y estado** de cada broker |
| `CA-SP-603` | Los brokers inactivos **no aparecen** salvo que se pidan explícitamente, y entonces se **añaden** |
| `CA-SP-604` | El sistema **no expone** ninguna operación de creación, edición, eliminación ni cambio de estado sobre este catálogo (`RN-SP-039`) |
| `CA-SP-605` | El orden es **por nombre** y el cliente no puede cambiarlo |
| `CA-SP-606` | El sistema rechaza la consulta a un actor sin `brokers:read` |
| `CA-SP-607` | **Dos brokers no pueden llamarse igual**, ni distinguiéndose por mayúsculas o acentos |

**`CA-SP-607` no es un criterio del endpoint sino del esquema**, y se escribe aquí porque es lo único que sostiene que el nombre sirva de clave: sin ese único, «Exness» y «exness» serían dos brokers y `user_brokers` acabaría repartida entre los dos.

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Catálogo vacío | `200` con la colección vacía. Es el estado inicial hasta que se siembre |
| Un broker apagado con cuentas declaradas | Las cuentas **se conservan**: apagar un broker deja de ofrecerlo, no borra lo que ya se declaró |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se administra por API? | **No** (08-09-2026, responsable del proyecto). Se puebla por migración, como monedas y tipos de documento |
| 2 | ¿Lleva código además del nombre? | **No, de momento**. El nombre es la clave de negocio, con índice único funcional. Añadir `code` es una migración el día que un integrador los pida por algo estable frente a un renombramiento |
| 3 | ¿Se pagina? | **No.** Son unos pocos y el cliente los quiere enteros |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 08-09-2026 | Redacción inicial. **Nace el submódulo de brokers**, y de él solo se especifica el catálogo: vincular una cuenta y confirmarla por webhook quedan registrados y sin decidir. La decisión que gobierna esta tripleta es que **el catálogo guarde solo el nombre**, de donde sale que el nombre sea la clave de negocio y que `CA-SP-607` —un criterio de esquema— viva en esta lista. | Responsable del proyecto |
