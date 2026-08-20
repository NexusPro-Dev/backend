# SPEC — `RF-SP-013` Consultar auditoría de error

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-013` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

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
| Página y tamaño | No | Paginación | Máximo definido en configuración |
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
- **Ráfaga de fallos idénticos:** una caída de un tercero puede generar miles de eventos iguales. Ver pregunta abierta 1.
- **Mensaje con datos de la petición:** debe salir enmascarado, igual que al escribirse.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿Se agrupan los fallos repetidos, o se registra cada ocurrencia? Sin agrupación, una caída de un tercero puede inundar el registro | Responsable técnico | Abierta |
| 2 | ¿Debe poder consultarse el conteo por tipo y severidad, para detectar una degradación sin leer evento por evento? | Responsable técnico | Abierta |
| 3 | ¿Cuál es la retención de este registro? Es el de mayor volumen y el de menor valor a largo plazo. Relacionado con D-10 | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
