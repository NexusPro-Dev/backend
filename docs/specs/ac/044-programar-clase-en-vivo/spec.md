# SPEC — `RF-AC-044` Programar una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-044` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Programar una clase en vivo **desde la plataforma**: la clase con su día, su hora de inicio y de fin, y quién puede entrar; y **su reunión en Zoom**, creada por la plataforma.

## 2. Contexto

El responsable del proyecto lo pidió el 09-10-2026 ([`requirements/ac.md`](../../../requirements/ac.md) §5.2.15): «crear las reuniones desde esta plataforma y que solo los que tienen el acceso a dicho evento puedan verlo». **El acceso de verdad lo da el registro de Zoom** (`RN-AC-026`): la reunión se crea con registro obligatorio, Zoom no deja entrar a nadie sin su enlace personal, y la plataforma solo registra a quien tiene acceso (`RF-AC-054`). Por eso **el enlace general no se guarda ni se publica**.

**Zoom manda**: la clase existe si y solo si existe su reunión. Se crea primero en Zoom; si falla, no se guarda nada; si la escritura local falla después, se borra la reunión.

Es el primer requerimiento del submódulo y **estrena** las cuatro tablas, los trece permisos (`V94`) y el cliente de Zoom.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Programa cualquier clase, suelta o de cualquier curso |

## 4. Alcance

### 4.1 Incluye

- La clase: título, descripción, curso opcional, inicio, fin, y sus listas de membresías y servicios.
- La reunión en Zoom: programada, en `America/Bogota`, con registro obligatorio y aprobación automática, sin entrar antes que el anfitrión y sin correos de Zoom.

### 4.2 No incluye

- Recordatorios (§1.3 de `ac.md`) ni grabación.
- Validar que se solape con otra clase (`ac.md` §5.2.15).

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-025` | Las listas y el curso |
| `RN-AC-026` | La reunión, el registro obligatorio, lo que se guarda |
| `RN-AC-029` | Inicio y fin |

## 6. Datos

### 6.1 Entrada

| Campo | Obligatorio | Regla |
|---|---|---|
| `title` | Sí | 1 a 150 caracteres, sin espacios a los lados |
| `description` | No | Texto |
| `courseId` | No | Curso existente y no retirado |
| `startsAt` | Sí | Fecha y hora con zona; sin zona, `America/Bogota`; no en el pasado |
| `endsAt` | Sí | Igual; de 15 minutos a 10 horas después de `startsAt` |
| `membershipIds` | No | Membresías existentes, sin repetir |
| `productIds` | No | Productos `BOT` no retirados, sin repetir |

### 6.2 Salida

El detalle de `RF-AC-043`.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| Precondición | `live-sessions:create`; credenciales de Zoom configuradas |
| Postcondición | Una reunión en Zoom y una fila `PROGRAMADA` en `live_sessions` que la señala, con sus listas; un registro de auditoría `CREATE` |

## 8. Flujo principal

1. Se validan los campos y las referencias.
2. Se crea la reunión en Zoom con título, inicio, duración resultante y zona.
3. Se guardan la clase y sus listas en una transacción, con el identificador de la reunión.
4. Se audita y se responde `201`.

## 9. Flujos alternativos

### FA-001 — Sin listas

La clase es de todos los alumnos con sesión (`RN-AC-025`).

### FA-002 — La escritura local falla después de crear la reunión

Se borra la reunión en Zoom y el error se propaga.

## 10. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | El curso no existe o está retirado | `422` |
| `EX-002` | Una membresía no existe, o un producto no existe, está retirado o no es `BOT` | `422`, nombrando cuál |
| `EX-003` | Zoom no está configurado, falla o no responde | `503`, nada guardado |

## 11. Validaciones

| Código | Campo | Regla |
|---|---|---|
| `VAL-001` | Cuerpo | Ilegible o identificador mal formado: `400` |
| `VAL-002` | `title`, `startsAt`, `endsAt` | Ausentes o fuera de forma: `400`, juntos |
| `VAL-003` | `startsAt`, `endsAt` | Inicio en el pasado, fin no posterior, menos de 15 minutos o más de 10 horas: `400` |
| `VAL-004` | `membershipIds`, `productIds` | Repetidos: `400` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-263` | Programa la clase: `201` con el detalle, la fila `PROGRAMADA` con sus listas, y Zoom recibe **una** reunión programada con registro obligatorio, aprobación automática, sin entrar antes que el anfitrión, sin correos, el título, el inicio en `America/Bogota` y la duración que resulta de inicio y fin |
| `CA-AC-264` | **Lo único de Zoom que se guarda es el identificador**: ni la respuesta ni la base llevan el enlace general ni la contraseña |
| `CA-AC-265` | Una hora sin zona se entiende en `America/Bogota`; inicio en el pasado, fin anterior o igual, menos de 15 minutos o más de 10 horas, `400` y Zoom no recibe nada |
| `CA-AC-266` | Curso inexistente o retirado, membresía inexistente, producto retirado o que no es `BOT`: `422`, y Zoom no recibe nada |
| `CA-AC-267` | **Zoom falla**: `503`, ninguna fila; **la escritura local falla después**: la reunión se borra en Zoom |
| `CA-AC-268` | Se audita como `CREATE` de `live_sessions`; sin `live-sessions:create`, `403` aunque porte `live-sessions:create-own` |

## 13. Casos límite

- Una clase sin curso y sin listas: de todos, y solo la administra administración.
- Dos clases a la misma hora: se aceptan.

## 14. Preguntas abiertas

Ninguna.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
