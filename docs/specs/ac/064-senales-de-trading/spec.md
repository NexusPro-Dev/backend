# SPEC — `RF-AC-067` a `RF-AC-072` Las señales de trading

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-067`, `RF-AC-068`, `RF-AC-069`, `RF-AC-070`, `RF-AC-071`, `RF-AC-072` |
| Módulo | `AC` — Academia |
| Versión | 1.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 14-10-2026 |

---

## 1. Objetivo

Que administración publique **señales de trading**, una a una, en un arreglo o desde un CSV, y que cada alumno vea **las que le tocan mientras valen**.

## 2. Contexto

El responsable del proyecto lo pidió el 14-10-2026 ([`requirements/ac.md`](../../../requirements/ac.md) §5.2.21): par de divisas, hora de la señal, tipo numérico y suscripción `free`, `premium` o `personalized`. La señal **se muestra hasta que su hora vuelve a llegar**: creada hoy a las 11 con hora 10, hasta mañana a las 10. Respondió el mismo día que el tipo es un **código entero**, que **las `FREE` las ve toda membresía vigente**, que registrar varias es **todo o nada** y que la señal **no se corrige**.

Nace **`RN-AC-035`**.

## 3. Actores

| Actor | Permisos |
|---|---|
| Administrador | `signals:list`, `:create`, `:create-batch`, `:import`, `:delete` |
| Alumno | `signals:view` |

## 4. Alcance

### 4.1 Incluye

- **Consultar** (`RF-AC-067`): `GET /signals`, paginado, con filtros.
- **Registrar una** (`RF-AC-068`): `POST /signals`.
- **Registrar varias** (`RF-AC-069`): `POST /signals/batch`, un arreglo.
- **Importar un CSV** (`RF-AC-070`): `POST /signals/import`, `multipart/form-data`.
- **Retirar** (`RF-AC-071`): `POST /signals/{id}/deletion`, con motivo.
- **Ver las vigentes** (`RF-AC-072`): `GET /signals/available`.
- `V108`: `signals`, `signal_memberships` y los seis permisos.

### 4.2 No incluye

- Corregir una señal.
- Un catálogo de tipos de señal: `signalType` es un número que interpreta el frontend.
- Notificar a los alumnos cuando nace una señal.
- Relación con la categoría «Señales» de las clases en vivo.

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-035` | Los campos, la ventana `visibleUntil`, quién ve cada suscripción, todo o nada al registrar varias, baja lógica |
| `RN-AC-012` | La pertenencia a una `PERSONALIZED` es exacta: una de VIP no la ve ORO |

## 6. Datos

### 6.1 Entrada

| Campo | Regla |
|---|---|
| `currencyPair` | Obligatorio; hasta 20 caracteres de letras, cifras y `/ . _ -` tras recortar; se guarda en mayúsculas |
| `signalTime` | Obligatoria; `H:mm` o `HH:mm`, de 00:00 a 23:59, hora de America/Bogota |
| `signalType` | Obligatorio; entero mayor o igual que cero |
| `direction` | Obligatoria; `UP` si sube o `DOWN` si baja. Se aceptan, sin distinguir mayúsculas, `SUBE`/`BAJA`, `CALL`/`PUT` y `BUY`/`SELL`; se guarda y sale `UP` o `DOWN` (`ac.md` §5.2.22) |
| `subscriptionType` | Obligatoria; `FREE`, `PREMIUM` o `PERSONALIZED`, sin distinguir mayúsculas |
| `membershipIds` | Con `PERSONALIZED`, una o más, existentes; los repetidos cuentan una vez. Con las otras dos, ninguna |
| `file` | El CSV: hasta 1 MB y 500 filas, UTF-8 o Windows-1252, separado por `,` o `;` según la cabecera |
| `reason` | Al retirar, como todo retiro (`DeletionReason`) |
| `subscriptionType`, `visible`, `includeDeleted`, `page`, `size` | Filtros del listado, opcionales |

**El CSV**: cabecera obligatoria con `currency_pair`, `signal_time`, `signal_type`, `direction` y `subscription_type`, en cualquier orden y sin distinguir mayúsculas ni guiones bajos —`signalTime` también vale—; `memberships` opcional, con los **códigos** separados por `|` (`ORO|VIP`). Las columnas desconocidas se ignoran y las líneas en blanco se saltan; un campo entre comillas dobles puede llevar el separador. El ejemplo que se entrega a quien importa es [`ejemplo-senales.csv`](ejemplo-senales.csv), y una prueba lo lee.

### 6.2 Salida

- **Administración** (`SignalResponse`): `{ id, currencyPair, signalTime, signalType, direction, subscriptionType, memberships: [{ id, code, name }], visibleUntil, visible, createdAt, deletedAt }`. `direction` es `null` solo en las retiradas por `V109`. `signalTime` sale siempre `HH:mm`.
- **Alumno** (`AvailableSignal`): `{ id, currencyPair, signalTime, signalType, direction, subscriptionType, visibleUntil, createdAt }`, sin las membresías.
- Registrar varias e importar responden la lista de las registradas, en el orden recibido.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | El permiso de cada ruta |
| Postcondición | Cada señal registrada audita `CREATE` con su instantánea; retirar audita la eliminación con el motivo |

## 8. Flujo principal (importar un CSV)

1. Se lee el archivo: tamaño, codificación, cabecera y filas (`VAL-007` a `VAL-010`).
2. Se comprueba **cada fila** con las reglas de §6.1 y se juntan **todos** los errores, cada uno con `lines[n].columna`. Si hay alguno, `400` y no se registra nada.
3. Se resuelven los códigos de membresía; si alguno no existe, `422` `EX-002` con todos los que faltan, y no se registra nada.
4. Se registra cada señal con `visibleUntil` calculado desde el mismo instante, y se audita cada una.
5. `201` con la lista.

## 9. Flujos alternativos

### FA-001 — Registrar una

Como §8 sin archivo, con los campos sin prefijo (`signalTime`).

### FA-002 — Registrar varias

Como §8 con un arreglo; los campos llevan su posición (`[3].signalTime`).

### FA-003 — Ver las vigentes sin membresía vigente

`200` con la lista vacía.

## 10. Excepciones

| Código | Dónde | Cuándo | Respuesta |
|---|---|---|---|
| `EX-002` | Registrar, varias, importar | Una membresía —identificador o código— no existe | `422`, con el campo de cada una |
| `EX-001` | Retirar | No existe | `404` |
| `EX-002` | Retirar | Ya retirada | `409` |

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-001` | `currencyPair` | Vacío, largo o con otros caracteres |
| `VAL-002` | `signalTime` | Vacía o fuera de `HH:mm` |
| `VAL-003` | `signalType` | Nulo, negativo o no entero |
| `VAL-004` | `subscriptionType` | Vacía o fuera de las tres; también en el filtro |
| `VAL-005` | `membershipIds` / `memberships` | `PERSONALIZED` sin ninguna |
| `VAL-006` | `membershipIds` / `memberships` | Membresías en una `FREE` o `PREMIUM` |
| `VAL-007` | `body` / `file` | Arreglo vacío o CSV sin filas |
| `VAL-008` | `body` / `file` | Más de 500 |
| `VAL-009` | `file` | Sin archivo, vacío o de más de 1 MB |
| `VAL-010` | `file` | Faltan columnas en la cabecera |
| `VAL-011` | `direction` | Vacía o fuera de `UP`/`DOWN` y sus sinónimos |
| `VAL-003` | `page`, `size` | La paginación de siempre |
| `VAL-002`, `VAL-003` | `reason` | Las del motivo de todo retiro |

Con varios errores, el código de la respuesta es el del primero y `errors` los trae todos.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-339` | **`V108`**: los seis permisos existen; los cinco de administración, solo `SUPERADMIN` y `ADMIN`; `signals:view` en todo rol con `live-sessions:learn`; catálogo de **250** |
| `CA-AC-340` | **`visibleUntil`** es la próxima vez que llega `signalTime` en Bogotá, estrictamente después de crearse: creada a las 11:00 con hora 10:00, mañana a las 10:00; a las 09:00 con hora 10:30, hoy a las 10:30; justo a su hora, mañana |
| `CA-AC-341` | **Registrar una** responde `201` con el par en mayúsculas, `HH:mm`, `visibleUntil`, `visible` y las membresías de una `PERSONALIZED`, y audita `CREATE`; los campos inválidos, `400` con todos los errores (`VAL-001` a `VAL-006`); una membresía inexistente, `422` `EX-002`; no registra nada si falla |
| `CA-AC-342` | **Registrar varias** responde `201` con todas en orden; con una inválida, `400` con `[i].campo` de cada error y **ninguna** registrada; membresía inexistente, `422` en su posición; vacío, `VAL-007`; más de 500, `VAL-008` |
| `CA-AC-343` | **Importar** un CSV con punto y coma o comas, BOM, cabecera en cualquier orden y `memberships` por código registra todas; con errores, `400` con `lines[n].columna` y **ninguna**; código inexistente, `422`; sin columnas, `VAL-010`; sin archivo o vacío, `VAL-009`; sin filas, `VAL-007`; más de 500, `VAL-008` |
| `CA-AC-344` | **Consultar** trae las más nuevas primero, sin las retiradas salvo con `includeDeleted`; `visible` filtra las que se muestran o no; `subscriptionType` filtra sin distinguir mayúsculas e inválido da `400` `VAL-004` |
| `CA-AC-345` | **Retirar** con motivo responde `204`, guarda `deleted_at` y audita la eliminación; sin motivo, `400`; ya retirada, `409` `EX-002`; inexistente, `404` |
| `CA-AC-346` | **Las vigentes**: con BECA, las `FREE` y las `PERSONALIZED` de BECA; con VIP, además las `PREMIUM` y las `PERSONALIZED` de VIP, no las de BECA; nunca las vencidas ni las retiradas; con la membresía vencida, ninguna |
| `CA-AC-347` | **Cada ruta exige su permiso**: con el de otra, `403` |
| `CA-AC-348` | **La dirección** (§5.2.22): registrar una, varias o un CSV exige `direction`; `UP`, `down`, `Sube`, `BAJA`, `call`, `PUT`, `buy` y `sell` se guardan como `UP` o `DOWN` y así salen en la respuesta, el listado y las vigentes; sin ella o con otro valor, `400` `VAL-011` con su posición; un CSV sin la columna, `VAL-010` |
| `CA-AC-349` | **`V109`** retira —`deleted_at`— las señales vivas que no tienen dirección; la base rechaza una señal sin dirección que no esté retirada (`ck_signals_direction`) |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| La misma membresía dos veces | Cuenta una |
| Un CSV de Excel en Windows-1252 | Se lee: si no es UTF-8 válido, se lee como Windows-1252 |
| Una hora `9:05` | Se acepta y sale `09:05` |
| Membresía que deja de existir | No pasa: las membresías no se borran, y la clave foránea lo impide |

## 14. Preguntas abiertas

Ninguna. Las decisiones tomadas sin preguntar están en `ac.md` §5.2.21 y §5.2.22, abiertas a revisión.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 14-10-2026 | Nace con las señales de trading (`ac.md` v0.30.0 §5.2.21, `RN-AC-035`). | Responsable técnico |
| 1.1.0 | 14-10-2026 | **La dirección** (`ac.md` v0.31.0 §5.2.22, a petición del responsable del proyecto): `direction` `UP` o `DOWN` en la entrada, el CSV y las dos salidas; `VAL-011`; `CA-AC-348` y `CA-AC-349`. Aprobada por el responsable del proyecto con sus dos respuestas. | Responsable técnico |
