# SPEC — `RF-SP-011` Consultar auditoría de cambios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-011` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada el | 28-08-2026 — ver el control de cambios |

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
| Página y tamaño | No | Paginación | Por defecto 20, máximo 100 (`architecture.md` §7.4) |
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
| Actor resuelto | De cada evento, el **nombre de usuario** de quien lo hizo —inmutable (`RN-SP-016`)— y su nombre completo **actual**. El identificador sigue viajando y es el dato probatorio |
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
- **Rango muy amplio:** el rango consultable no se limita, y la paginación ya acota la respuesta; lo que puede degradarse es la consulta. Sostenerla es cuestión de índices, y eso se resuelve en `plan.md`.
- **Detalle del cambio con datos sensibles:** debe salir enmascarado, igual que al escribirse.
- **Fechas en zona horaria distinta:** los eventos se almacenan en tiempo universal; la conversión es del cliente.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Vista transversal que combine los cuatro registros? | **No como requerimiento.** Los cuatro registros se consultan por separado, y el identificador de correlación ya permite cruzarlos con dos consultas. La vista `v_audit_timeline` de `architecture.md` §6.6.6 existe en el modelo de datos para el diagnóstico directo sobre la base, y exige los cuatro permisos; no se expone como consulta de la API en este alcance |
| 2 | ¿Se limita el rango de fechas consultable? | **No.** La paginación ya acota el tamaño de la respuesta, y limitar el rango obligaría a trocear justo la consulta que más valor tiene: la línea de tiempo completa de un registro. Que la consulta se sostenga es cuestión de índices, no de negocio |
| 3 | ¿La consulta de auditoría se audita a su vez? | **No este registro.** Toda consulta deja rastro en el registro de peticiones, que basta para saber quién la hizo. Solo `RF-SP-014` genera además un evento propio, porque ahí el acto de mirar es en sí mismo información de seguridad |
| 4 | ¿Debe poder exportarse el resultado? | **No.** Exportar tiene reglas propias —formato, tamaño, retención del fichero generado y quién puede llevarse un volcado de la auditoría fuera del sistema— y no cabe como opción de esta consulta |
| 0.3.0 | 28-08-2026 | **El actor llega resuelto**, por decisión del responsable del proyecto. Hasta hoy la respuesta traía solo `actorId`, y el motivo escrito era que un nombre es una foto del momento en que se **consulta** y no del momento en que **ocurrió** el evento. Ese argumento no se descarta, se acota: la identidad que se devuelve es el **`username`**, que es **inmutable** (`RN-SP-016`) y dice hoy lo mismo que decía entonces; el nombre completo se devuelve **declarado como actual** y por comodidad, no como evidencia. `actorId` sigue viajando y sigue siendo el dato probatorio, de modo que el cambio es **aditivo**. Se resuelve con un `LEFT JOIN` en la **misma** sentencia —una consulta por fila serían cien consultas por página— que **no filtra por `deleted_at`**: una auditoría que dejara de decir quién hizo algo porque esa persona fue dada de baja perdería su valor justo donde más se consulta. | Responsable técnico |
