# SPEC — `RF-AC-042` Consultar las clases en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-042` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que administración vea todas las clases, también las pasadas y las canceladas.

## 2. Contexto

El listado de administración. **Incluye lo cancelado y lo terminado**, porque es historia: cuándo hubo clase y cuánta gente se registró. El estado guardado es `PROGRAMADA` o `CANCELADA`; **`ended` se calcula** con la hora de fin (`RN-AC-029`) y es filtrable.

## 3. Actores

| Actor | Permiso |
|---|---|
| Administrador | `live-sessions:list` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-029` | El estado y `ended` |
| `RN-AC-028` | Administración ve todas |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `GET /api/v1/live-sessions` | — | Identificadores `uuid` mal formados: `400` |
| `page`, `size` | No | La paginación del sistema |
| `courseId` | No | `uuid` |
| `status` | No | `PROGRAMADA` o `CANCELADA` |
| `ended` | No | Booleano |
| `from`, `to` | No | Periodo semiabierto sobre `startsAt` |

### 5.2 Salida

La página del sistema; cada fila con `id`, `title`, `course {id, title}` o nulo, `startsAt`, `endsAt`, `status`, `ended`, `membershipCount`, `productCount` y `registrationCount`. Por `startsAt` descendente.

## 6. Excepciones

Ninguna propia.

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-273` | Lista todas las clases —programadas, canceladas y terminadas— por inicio descendente, con las cuentas de llaves y de registrados |
| `CA-AC-274` | Los filtros `courseId`, `status`, `ended` y el periodo acotan y se combinan; un `status` fuera del dominio, `400` |
| `CA-AC-275` | Sin `live-sessions:list`, `403` aunque porte `live-sessions:list-own`; la página cuesta dos sentencias fijas |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
