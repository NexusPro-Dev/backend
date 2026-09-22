# SPEC — `RF-SP-013` Consultar auditoría de error

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-013` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada el | 28-08-2026 — ver el control de cambios |

---

## 1. Objetivo

Responder a quién le falló qué, sobre qué recurso y por qué motivo, para poder diagnosticar sin acceder a los registros técnicos.

## 2. Contexto

Este registro no recoge todo lo que falla. El registro de peticiones ya deja constancia de cada petición fallida; duplicarlo entero no aportaría información y sí volumen.

Aquí entra solo lo que **hay que investigar o explicar**: fallos no controlados, rechazos por regla de negocio y fallos de integración con terceros. Quedan fuera las validaciones de formato y los recursos inexistentes, que son ruido de formulario.

La frontera que más importa: una **denegación de autorización no es un error**. Es el sistema funcionando, y va a la auditoría de seguridad. Registrarla aquí contaminaría la búsqueda de fallos reales.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Soporte técnico | Diagnostica incidencias reportadas |
| Responsable de seguridad | Ídem, junto con los demás registros |

## 4. Alcance

### 4.1 Incluye

- Listado paginado de fallos no controlados, rechazos por regla de negocio y fallos de integración.
- Filtro por tipo de error, severidad, código, recurso, actor y rango de fechas.

### 4.2 No incluye

- La traza técnica completa, que vive en el registro de aplicación y se alcanza por el identificador de correlación.
- Validaciones de formato, recursos inexistentes y fallos de autenticación: los cubre el registro de peticiones.
- Denegaciones de autorización → `RF-SP-014`.

## 5. Reglas de negocio aplicables

Ninguna. Su acceso lo controla el permiso de lectura de auditoría de error, que suele concederse a soporte técnico sin darle acceso a los demás registros.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página y tamaño | No | Paginación | Por defecto 20, máximo 100 (`architecture.md` §7.4) |
| Tipo de error | No | Regla de negocio, integración o no controlado | Uno de los valores definidos |
| Severidad | No | Media o alta | Uno de los valores definidos |
| Código de error | No | Código del contrato o de la regla incumplida | — |
| Recurso | No | Entidad o ruta afectada | — |
| Actor | No | Quien sufrió el fallo | — |
| Desde y hasta | No | Rango de fechas | La fecha inicial no puede ser posterior a la final |
| Identificador de correlación | No | Filtro por petición concreta | — |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Eventos | Momento, actor, recurso, operación, código, tipo, severidad, código de estado devuelto y mensaje saneado |
| Actor resuelto | De cada evento, el **nombre de usuario** de quien lo hizo —inmutable (`RN-SP-016`)— y su nombre completo **actual**. El identificador sigue viajando y es el dato probatorio |
| Origen | Dirección de red y cliente, cuando el fallo vino de una petición |
| Correlación | Identificador que enlaza con la petición y con el registro técnico |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de auditoría de error.

**Postcondiciones**

- Ninguna sobre los datos consultados.

## 8. Flujo principal

1. El actor solicita el registro de errores, con o sin filtros.
2. El sistema valida la paginación, el rango de fechas y los filtros.
3. El sistema recupera los eventos que cumplen los filtros, del más reciente al más antiguo.
4. El sistema devuelve la página solicitada con su información de paginación.

## 9. Flujos alternativos

### FA-001 — Diagnóstico por correlación

**Cuándo ocurre:** un usuario reporta un error citando el identificador que recibió en la respuesta.

1. El actor filtra por ese identificador de correlación.
2. El sistema devuelve el fallo asociado, junto con la información necesaria para localizar la traza técnica.
3. Es el flujo para el que existe esta consulta.

### FA-002 — Sin resultados

**Cuándo ocurre:** ningún evento cumple los filtros.

1. El sistema devuelve una colección vacía; no es un error.

## 10. Excepciones

### EX-001 — Rango de fechas inválido

**Condición:** la fecha inicial es posterior a la final.
**Respuesta del sistema:** rechaza la consulta e informa el problema del rango.

### EX-002 — Parámetro de paginación inválido

**Condición:** la página es negativa o el tamaño excede el máximo configurado.
**Respuesta del sistema:** rechaza la consulta e informa el límite aplicable.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Rango de fechas coherente | La fecha inicial no puede ser posterior a la final. |
| `VAL-002` | Tamaño dentro del máximo configurado | El tamaño de página excede el máximo permitido. |
| `VAL-003` | Tipo y severidad dentro de su dominio | El valor del filtro no es válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-096` | El sistema devuelve los fallos paginados, del más reciente al más antiguo |
| `CA-SP-097` | El sistema permite localizar un fallo concreto por su identificador de correlación |
| `CA-SP-098` | El sistema filtra por tipo, severidad, código, recurso, actor y rango de fechas |
| `CA-SP-099` | Los mensajes devueltos no contienen trazas, sentencias, rutas ni versiones |
| `CA-SP-100` | Las denegaciones de autorización no aparecen en este registro |
| `CA-SP-101` | Las validaciones de formato y los recursos inexistentes no aparecen en este registro |
| `CA-SP-102` | El sistema rechaza la consulta a un actor sin el permiso de lectura de auditoría de error |

## 13. Casos límite

- **Fallo sin actor:** un error en un proceso interno no tiene actor; el campo queda explícitamente vacío.
- **Fallo al escribir la propia auditoría:** no debe provocar una cadena de eventos. El registro se escribe en transacción independiente precisamente para no arrastrar el fallo del negocio.
- **Ráfaga de fallos idénticos:** una caída de un tercero puede generar miles de eventos iguales. Se registra cada ocurrencia por separado: agrupar borraría cuándo empezó y cuándo terminó, que es justo lo que se necesita saber.
- **Mensaje con datos de la petición:** debe salir enmascarado, igual que al escribirse.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se agrupan los fallos repetidos? | **No: cada ocurrencia es un evento.** Agrupar convierte mil fallos en una fila con un contador y borra cuándo empezó la caída y cuándo terminó, que es lo primero que se pregunta al diagnosticarla. El volumen se acota con la retención, no falseando el registro |
| 2 | ¿Debe consultarse el conteo por tipo y severidad? | **No aquí.** Detectar una degradación en curso es observabilidad —métricas y alertas sobre la serie temporal—, y este registro sirve para reconstruir un fallo concreto ya ocurrido. Añadirle agregados lo convertiría en un panel de métricas peor que el panel de métricas |
| 3 | ¿Cuál es la retención de este registro? (D-10) | **No pertenece a esta especificación.** La consulta se comporta igual con noventa días de historia que con dos años: la retención es una decisión de infraestructura, y D-10 queda abierta en `architecture.md` hasta la migración de observabilidad. Lo único que la spec fija es que el rango consultable no se limita |
| 0.3.0 | 28-08-2026 | **El actor llega resuelto**, por decisión del responsable del proyecto. Hasta hoy la respuesta traía solo `actorId`, y el motivo escrito era que un nombre es una foto del momento en que se **consulta** y no del momento en que **ocurrió** el evento. Ese argumento no se descarta, se acota: la identidad que se devuelve es el **`username`**, que es **inmutable** (`RN-SP-016`) y dice hoy lo mismo que decía entonces; el nombre completo se devuelve **declarado como actual** y por comodidad, no como evidencia. `actorId` sigue viajando y sigue siendo el dato probatorio, de modo que el cambio es **aditivo**. Se resuelve con un `LEFT JOIN` en la **misma** sentencia —una consulta por fila serían cien consultas por página— que **no filtra por `deleted_at`**: una auditoría que dejara de decir quién hizo algo porque esa persona fue dada de baja perdería su valor justo donde más se consulta. | Responsable técnico |
