# SPEC — `RF-SP-048` Consultar las tasas de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-048` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |

---

## 1. Objetivo

Ver **qué tasas hay, cuál rige hoy y cuáles rigieron**.

## 2. Contexto

La pregunta que se hace a diario es *«¿a cuánto está el cambio hoy?»*, y lleva una fecha implícita. Un listado que no supiera responderla obligaría a traerse todas las tasas del par y comparar fechas en el navegador — que es exactamente la clase de regla que este proyecto no deja salir del servidor.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Ve el catálogo entero para gobernarlo |
| Funcionario · fuerza comercial | Consulta a cuánto está el cambio |

## 4. Alcance

### 4.1 Incluye

- Listado **paginado**, con las dos monedas resueltas.
- Filtros por **origen**, **destino**, **estado** y **vigencia a una fecha**.
- Ordenamiento sobre una **lista cerrada**.
- Las **vencidas** se devuelven; las **retiradas** solo si se piden.

### 4.2 No incluye

- **Un endpoint de detalle.** Una tasa tiene ocho campos y ninguno se resuelve aparte: el listado ya los trae todos, y un `GET /{id}` sería la misma fila con otra ruta. Ver §14, resolución 1.
- **Convertir un importe.** «Cuánto son cien dólares en pesos» es una operación, no una consulta de catálogo, y nadie la ha pedido.
- **El motivo del retiro.** Vive en la auditoría de eliminación, como en `RF-PM-002`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-033` | La tasa no desaparece: el retiro es lógico | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Página y tamaño | No | Qué porción se pide | Con tamaño máximo; pedir más **se rechaza**, no se recorta |
| Moneda de origen | No | Filtra por el origen | Un identificador inexistente devuelve la colección vacía y **no es un error** |
| Moneda de destino | No | Filtra por el destino | Igual |
| Estado | No | Activas o suspendidas | Uno de los dos valores |
| **Vigente el** | No | Devuelve **solo las que rigen ese día** | Una fecha. Es el filtro que responde «a cuánto está» |
| Incluir retiradas | No | Si se devuelven también las retiradas | Por omisión **no** |
| Orden | No | Por qué campo y en qué sentido | **Lista cerrada**: fecha de inicio, precio. Cualquier otro se rechaza |

**«Vigente el» es el filtro que justifica este requerimiento**, y su semántica hay que escribirla: devuelve las tasas cuyo `valid_from` es menor o igual a la fecha **y** cuyo `valid_to` es nulo o mayor o igual a ella. **Los dos extremos entran**, igual que en el `EXCLUDE` que impide el solapamiento — si aquí se usara un intervalo abierto y allí uno cerrado, habría un día que ninguna tasa cubriría según esta consulta y dos según aquella restricción.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tasas | Identificador, precio, vigencia completa y estado |
| Monedas resueltas | Origen y destino con código, nombre y decimales |
| Marca de retiro | En las retiradas, que lo están y **desde cuándo**. El motivo no viaja |
| Total | Cuántas cumplen el filtro |
| Orden | El aplicado, para que quien recibe la página sepa sobre qué está paginando |

## 7. Precondiciones y postcondiciones

**Precondiciones:** el actor está autenticado y porta `exchange-rates:read`.

**Postcondiciones:** ninguna. Es una lectura y **no reserva nada**: que una tasa aparezca aquí no promete que siga vigente cuando alguien la use.

## 8. Flujo principal

1. El actor pide el listado con los filtros que quiera.
2. El sistema valida **todos** los parámetros y devuelve **juntos** los que fallen.
3. El sistema consulta la página y el total con **el mismo predicado**.
4. El sistema devuelve las filas con las dos monedas resueltas en la **misma** sentencia.

## 9. Flujos alternativos

### FA-001 — Ninguna tasa cumple el filtro

**Comportamiento:** `200` con la colección **vacía** y total cero. Preguntar por algo que no está no es un error.

### FA-002 — Página más allá de la última

**Comportamiento:** `200`, colección vacía y **el total real**. No se deduce del desplazamiento: eso daría un número inventado sin ningún error que lo delate.

### FA-003 — Un día sin ninguna tasa vigente

**Comportamiento:** con «vigente el» sobre un día que ninguna tasa cubre, la colección llega vacía. **No es un error y no se rellena con la más cercana**: inventar la tasa del día anterior es exactamente el número plausible y falso que este submódulo evita.

## 10. Excepciones

### EX-001 — Parámetros inválidos

**Condición:** paginación fuera de rango, estado fuera de dominio, orden fuera de la lista o fecha con formato inválido.
**Respuesta del sistema:** los rechaza **todos juntos**, cada uno con su campo.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Página y tamaño dentro de rango | Los parámetros de paginación están fuera del rango admitido. |
| `VAL-002` | Estado dentro del dominio | El estado indicado no es válido. |
| `VAL-003` | Campo de ordenamiento dentro de la lista admitida | El campo de ordenamiento indicado no es válido. |
| `VAL-004` | Fecha con formato válido | La fecha indicada no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-543` | El sistema devuelve las tasas paginadas con el total que cumple el filtro |
| `CA-SP-544` | El sistema resuelve **las dos monedas** de cada tasa —código, nombre y decimales— **sin exigir una segunda consulta** |
| `CA-SP-545` | El sistema filtra por **origen** y por **destino**, y los dos filtros **se combinan** |
| `CA-SP-546` | El sistema filtra por **vigencia a una fecha** y devuelve solo las que rigen ese día, **con los dos extremos incluidos** |
| `CA-SP-547` | El sistema devuelve la colección **vacía** para un día que ninguna tasa cubre, y **no rellena** con la más cercana |
| `CA-SP-548` | El sistema devuelve las tasas **vencidas** con normalidad: dejar de regir no es dejar de existir |
| `CA-SP-549` | El sistema **excluye las retiradas** salvo que se pidan, y al pedirlas indica **desde cuándo** lo están, **sin el motivo** |
| `CA-SP-550` | El sistema rechaza los parámetros inválidos **enumerándolos todos juntos** |
| `CA-SP-551` | El sistema devuelve el precio **con sus ocho decimales**, sin redondear a la escala de ninguna moneda |
| `CA-SP-552` | El sistema rechaza la consulta a un actor sin `exchange-rates:read` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos tasas del mismo par vigentes el mismo día | **No puede ocurrir entre las activas** (`RN-SP-032`), y **sí entre una activa y una suspendida**: el `EXCLUDE` es parcial. El filtro por vigencia devuelve las dos, y el filtro por estado es el que las separa |
| «Vigente el» sobre una fecha futura | **Se admite**, y es útil: responde «qué tasa regirá el mes que viene» |
| Un origen que no existe | Colección vacía, **no un error**. Validarlo costaría una consulta por petición para producir un fallo que esta spec no quiere |
| El precio en el JSON | Viaja como **número**, y queda declarado lo que cuesta: un número JSON pasa por coma flotante de doble precisión en cualquier cliente JavaScript, de modo que **ninguna conversión calculada en el navegador puede ser la que se cobre**. Es la misma advertencia que `RF-PM-003` dejó escrita para el precio de un producto, y aquí pesa más porque una tasa tiene ocho decimales |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Hace falta un endpoint de detalle, como `RF-PM-003`? | **No.** Aquel existe porque el detalle de un producto trae cosas que el listado no —el motivo del retiro, las membresías resueltas—; aquí una tasa tiene ocho campos y **el listado ya los trae todos**. Un `GET /{id}` sería la misma fila con otra ruta, y dos sitios donde mantener la misma proyección |
| 2 | ¿El filtro por vigencia incluye los extremos? | **Sí, los dos.** Y no es una preferencia: el `EXCLUDE` de `RN-SP-032` usa `daterange(..., '[]')`, cerrado. Si esta consulta usara un intervalo abierto, habría días que la restricción considera cubiertos y esta consulta no — dos verdades sobre la misma fila |
| 3 | ¿Se pagina? | **Sí**, desde el principio. A diferencia de la oferta de `RF-PM-007`, esta tabla **crece sin techo**: una tasa por par y por periodo, indefinidamente. Empezar sin paginación obligaría a añadirla rompiendo a quien ya consumiera |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 07-09-2026 | Redacción inicial. **La decisión que carga el requerimiento es el filtro «vigente el»**, que es el que responde la pregunta que se hace a diario y el que impide que esa comparación de fechas acabe en el navegador. Sus **dos extremos entran**, y no por gusto: el `EXCLUDE` de `RN-SP-032` usa un `daterange` cerrado, y un intervalo abierto aquí produciría días que la restricción da por cubiertos y esta consulta no. **Se descarta el endpoint de detalle** —una tasa tiene ocho campos y el listado los trae todos— y **se pagina desde el principio**, porque esta tabla crece sin techo. Queda escrito además que **un día sin tasa devuelve vacío y no se rellena con la más cercana**: inventar la del día anterior sería el número plausible y falso que este submódulo existe para no producir. | Responsable del proyecto |
