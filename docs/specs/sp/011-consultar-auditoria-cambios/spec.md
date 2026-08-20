# SPEC — `RF-SP-011` Consultar auditoría de cambios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-011` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Responder quién creó o modificó un registro, cuándo, y qué cambió exactamente.

## 2. Contexto

Las tablas de negocio **no almacenan el actor** de cada cambio (Art. V.7). Esa decisión evita duplicar un dato que se desincroniza, pero convierte a este registro en la **única fuente** de la autoría: si un evento no se emitió, la información no existe en ningún otro sitio.

Por eso esta consulta no es una comodidad de diagnóstico, sino la forma en que el sistema responde «¿quién hizo esto?». Es lo que sustituye a las columnas `created_by` y `updated_by` que las tablas no tienen.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Auditor de negocio | Revisa qué cambió y quién lo cambió |
| Responsable de seguridad | Ídem, junto con los demás registros |
| Administrador | Ídem |

## 4. Alcance

### 4.1 Incluye

- Listado paginado de eventos de creación y edición.
- Filtro por módulo, entidad, identificador de registro, actor y rango de fechas.
- Detalle del cambio: en una creación, el estado inicial; en una edición, solo los campos modificados con su antes y su después.

### 4.2 No incluye

- Eliminaciones → `RF-SP-012`.
- Fallos → `RF-SP-013`.
- Eventos de control de acceso → `RF-SP-014`.
- Modificar o borrar registros de auditoría: son inmutables por diseño.

## 5. Reglas de negocio aplicables

Ninguna regla de negocio gobierna esta consulta. Su acceso lo controla el permiso de lectura de auditoría de cambios, que se concede por separado de los demás registros.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página y tamaño | No | Paginación | Máximo definido en configuración |
| Módulo | No | Filtro por módulo de origen | Código de módulo existente |
| Entidad | No | Filtro por tipo de registro | — |
| Identificador de registro | No | Filtro por un registro concreto | — |
| Actor | No | Filtro por quien ejecutó el cambio | — |
| Acción | No | Creación o edición | Uno de los dos valores |
| Desde y hasta | No | Rango de fechas del evento | La fecha inicial no puede ser posterior a la final |
| Identificador de correlación | No | Filtro por petición concreta | — |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Eventos | Momento, actor, módulo, entidad, registro afectado, acción y detalle del cambio |
| Origen | Dirección de red y cliente desde el que se originó, cuando la operación vino de una petición |
| Correlación | Identificador que enlaza el evento con la petición que lo produjo |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de auditoría de cambios.

**Postcondiciones**

- Ninguna sobre los datos consultados. La propia consulta puede quedar registrada en el registro de peticiones.

## 8. Flujo principal

1. El actor solicita el registro de cambios, con o sin filtros.
2. El sistema valida los parámetros de paginación, el rango de fechas y los filtros.
3. El sistema recupera los eventos que cumplen los filtros, ordenados del más reciente al más antiguo.
4. El sistema devuelve la página solicitada con su información de paginación.

## 9. Flujos alternativos

### FA-001 — Sin resultados

**Cuándo ocurre:** ningún evento cumple los filtros.

1. El sistema devuelve una colección vacía; no es un error.

### FA-002 — Evento sin origen de red

**Cuándo ocurre:** el cambio lo produjo un proceso interno, una migración o una tarea programada.

1. El sistema devuelve el evento con la correlación y la dirección de red **explícitamente vacías**.
2. Ambas están ausentes a la vez: una fila sin dirección significa inequívocamente que no vino de la red, nunca que se olvidó registrarla.

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
| `VAL-003` | Acción dentro de su dominio | El valor del filtro de acción no es válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-081` | El sistema devuelve los eventos de creación y edición paginados y ordenados del más reciente al más antiguo |
| `CA-SP-082` | En una edición, el detalle contiene únicamente los campos modificados, con su antes y su después |
| `CA-SP-083` | En una creación, el detalle contiene el estado inicial del registro |
| `CA-SP-084` | El sistema filtra por módulo, entidad, registro, actor, acción y rango de fechas |
| `CA-SP-085` | El sistema permite recuperar todos los eventos de una misma petición por su identificador de correlación |
| `CA-SP-086` | Los eventos sin origen de red devuelven correlación y dirección vacías a la vez |
| `CA-SP-087` | Ningún evento devuelve contraseñas, tokens ni cabeceras de autorización |
| `CA-SP-088` | El sistema rechaza la consulta a un actor que solo tenga permisos sobre otros registros de auditoría |

## 13. Casos límite

- **Registro que ya no existe:** el evento se conserva aunque su entidad se haya eliminado. Es su razón de ser.
- **Actor eliminado:** el identificador debe seguir resolviendo a un usuario, motivo por el cual los usuarios no se borran físicamente.
- **Rango muy amplio:** la paginación acota la respuesta, pero la consulta puede degradarse. Ver pregunta abierta 2.
- **Detalle del cambio con datos sensibles:** debe salir enmascarado, igual que al escribirse.
- **Fechas en zona horaria distinta:** los eventos se almacenan en tiempo universal; la conversión es del cliente.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿Debe existir una vista transversal que combine los cuatro registros por correlación, o se consultan siempre por separado? | Responsable técnico | Abierta |
| 2 | ¿Se limita el rango de fechas consultable de una sola vez? | Responsable técnico | Abierta |
| 3 | ¿La consulta de auditoría se registra a su vez como evento? Saber quién revisó la auditoría puede ser tan relevante como la auditoría misma | Responsable técnico | Abierta |
| 4 | ¿Debe poder exportarse el resultado? | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
