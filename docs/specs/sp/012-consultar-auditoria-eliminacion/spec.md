# SPEC — `RF-SP-012` Consultar auditoría de eliminación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-012` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Responder quién eliminó qué, **por qué** lo eliminó y qué era exactamente lo eliminado.

## 2. Contexto

La eliminación se audita aparte de los demás cambios porque exige dos datos que ninguna otra operación necesita: el **motivo** declarado y el **estado del registro** al momento de borrarse.

Ese segundo dato es lo que da valor al registro. Sin él, la fila diría que el rol `018f3a…` fue eliminado y nadie recordaría qué rol era.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Auditor de negocio | Revisa qué se eliminó y con qué justificación |
| Responsable de seguridad | Ídem, junto con los demás registros |

## 4. Alcance

### 4.1 Incluye

- Listado paginado de eliminaciones, lógicas y físicas.
- El motivo declarado y el estado del registro al eliminarse.
- Filtro por módulo, entidad, registro, actor, tipo de eliminación y rango de fechas.

### 4.2 No incluye

- Restaurar lo eliminado: la auditoría es un registro, no una papelera.
- Los demás tipos de evento → `RF-SP-011`, `RF-SP-013` y `RF-SP-014`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-005` | La revocación de un permiso se audita aquí, sin motivo | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página y tamaño | No | Paginación | Máximo definido en configuración |
| Módulo, entidad, registro | No | Filtros de procedencia | — |
| Actor | No | Quien ejecutó la eliminación | — |
| Tipo de eliminación | No | Lógica, física o de asociación | Uno de los tres valores |
| Desde y hasta | No | Rango de fechas | La fecha inicial no puede ser posterior a la final |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Eventos | Momento, actor, módulo, entidad, registro, tipo de eliminación, motivo y estado del registro |
| Origen | Dirección de red y cliente, cuando la operación vino de una petición |
| Paginación | Total de elementos, total de páginas y página actual |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de auditoría de eliminación.

**Postcondiciones**

- Ninguna sobre los datos consultados.

## 8. Flujo principal

1. El actor solicita el registro de eliminaciones, con o sin filtros.
2. El sistema valida la paginación, el rango de fechas y los filtros.
3. El sistema recupera los eventos que cumplen los filtros, del más reciente al más antiguo.
4. El sistema devuelve la página solicitada con su información de paginación.

## 9. Flujos alternativos

### FA-001 — Eliminación de una asociación

**Cuándo ocurre:** el evento corresponde a retirar un permiso de un rol.

1. El sistema devuelve el evento con el **motivo vacío**, que es lo correcto: las asociaciones están exentas de declararlo (Art. V.13).
2. El estado conservado son los dos extremos de la asociación.

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
| `VAL-003` | Tipo de eliminación dentro de su dominio | El valor del filtro no es válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-089` | El sistema devuelve las eliminaciones paginadas, con su motivo y el estado del registro eliminado |
| `CA-SP-090` | Los eventos de eliminación de entidades de negocio siempre traen motivo |
| `CA-SP-091` | Los eventos de eliminación de asociaciones traen el motivo vacío, sin que se considere un defecto |
| `CA-SP-092` | El sistema filtra por tipo de eliminación, módulo, entidad, actor y rango de fechas |
| `CA-SP-093` | El estado conservado permite reconstruir qué era el registro eliminado |
| `CA-SP-094` | El estado conservado no contiene contraseñas, tokens ni datos personales sensibles |
| `CA-SP-095` | El sistema rechaza la consulta a un actor sin el permiso de lectura de auditoría de eliminación |

## 13. Casos límite

- **Registro eliminado y su código reutilizado:** el evento conserva el estado original, de modo que la reutilización no lo corrompe.
- **Estado voluminoso:** un registro con muchos campos genera un estado grande; conviene medir el crecimiento del registro.
- **Actor eliminado:** su identificador debe seguir resolviendo a un usuario.
- **Eliminación en cascada desde la base de datos:** no produciría evento. Ver pregunta abierta 2.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | ¿El motivo debe poder buscarse por texto? Es la pregunta más frecuente en una auditoría real | Responsable técnico | Abierta |
| 2 | ¿Se admite alguna eliminación en cascada desde la base de datos? Quedaría sin evento y rompería la garantía del registro | Responsable técnico | Abierta |
| 3 | Relacionado con D-20: ¿el motivo se tipifica con un catálogo de códigos además del texto libre? Permitiría filtrar sin buscar por texto | Responsable técnico | Abierta |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
