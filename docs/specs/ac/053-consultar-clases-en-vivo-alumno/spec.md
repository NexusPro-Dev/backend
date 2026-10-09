# SPEC — `RF-AC-053` Consultar las clases en vivo como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-053` |
| Módulo | `AC` — Academia |
| Versión | 1.0.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Objetivo

Que el alumno vea qué clases vienen y cuáles puede ver.

## 2. Contexto

**La vitrina, como el catálogo** (`ac.md` §5.2.13): todas las clases programadas que no han terminado, **con candado** en las que no se le abren y la lista de membresías y servicios que las abren —la invitación—; y **`onlyAccessible=true`** para quedarse con las suyas. **Nada de Zoom**: el enlace se pide al entrar (`RF-AC-054`).

## 3. Actores

| Actor | Permiso |
|---|---|
| Alumno | `live-sessions:learn` |

## 4. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-AC-025` | `accessible` con `StudentAccess` y las llaves de quien mira |
| `RN-AC-029` | Solo programadas que no terminaron |

## 5. Datos

### 5.1 Entrada

| Dato | Obligatorio | Regla |
|---|---|---|
| Ruta: `GET /api/v1/live-sessions/available` | — | Identificadores `uuid` mal formados: `400` |
| `courseId` | No | `uuid` |
| `onlyAccessible` | No | Booleano |

### 5.2 Salida

Una lista sin paginar, por inicio ascendente: `id`, `title`, `description`, `course {id, title}` o nulo, `startsAt`, `endsAt`, `inProgress`, `accessible`, `registered`, `memberships`, `products`.

## 6. Excepciones

Ninguna propia.

## 7. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-276` | Trae las programadas que no terminaron, por inicio; ni las canceladas ni las terminadas; `inProgress` en la que está en curso |
| `CA-AC-277` | `accessible` por la membresía o un servicio vigentes, o porque la clase no declara llaves; `onlyAccessible=true` deja solo esas; `registered` si ya pidió entrar |
| `CA-AC-278` | No trae ningún enlace; sin `live-sessions:learn`, `403` aunque porte `courses:learn`; cuesta tres sentencias fijas más los dos puertos |

## 8. Preguntas abiertas

Ninguna.

## 9. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 09-10-2026 | Nace con las clases en vivo (`ac.md` v0.23.1 §5.2.15). | Responsable técnico |
