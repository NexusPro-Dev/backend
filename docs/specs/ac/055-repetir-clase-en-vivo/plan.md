# PLAN — `RF-AC-055` Repetir una clase en vivo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-055` |
| Especificación | [`spec.md`](spec.md), aprobada el 13-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

---

## 1. Enfoque

**Las fechas se calculan en un solo sitio**, un modelo de dominio nuevo, `LiveSessionSeries`: de la primera fecha, la frecuencia y `repeatUntil` saca la lista de fechas en `America/Bogota`, la comprueba (`RN-AC-031`) y responde las tres preguntas que hacen las lecturas —**cuál es la fecha en curso o la siguiente**, **si alguna está en curso** y **cuándo termina la última**—. Programar y corregir la usan antes de llamar a Zoom; las lecturas, al armar la respuesta. **El fin de la última fecha se guarda** (`series_ends_at`) porque el listado y la vitrina filtran por él en SQL; lo demás se calcula.

**Zoom recibe la repetición en el mismo `MeetingSpec`**, con un campo opcional `repeat`: el cliente la traduce a una reunión de tipo 8 —recurrente con hora fija— con `recurrence` de tipo 1, 2 o 3, `repeat_interval` 1, `weekly_days` o `monthly_day` según el caso y **`end_times` con el número de fechas**: contar las fechas aquí y decírselo a Zoom evita que Zoom y la plataforma discrepen sobre la última por la zona horaria de `end_date_time`. Sin `repeat`, tipo 2 como hasta hoy. Al corregir se manda el tipo siempre, de modo que quitar la repetición devuelve la reunión al tipo 2.

## 2. Cambios de esquema

`V102`: `live_sessions` gana `recurrence` (`varchar(10)`, nula, `CHECK` de los tres valores), `repeat_until` (`date`, nula si y solo si `recurrence` lo es) y `series_ends_at` (`timestamptz`, no nula, rellenada con `ends_at`; igual a `ends_at` sin repetición y mayor con ella), y el índice `ix_live_sessions_series_ends_at`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/models | **`LiveSessionSeries`** (nuevo) |
| shared/zoom | `ZoomMeetings.MeetingSpec` con `Repeat`; `ZoomApiClient` |
| domain/service | `ScheduleLiveSessionService`, `UpdateLiveSessionService`, `LiveSessionSupport`, `ListLiveSessionsService`, `LiveClassroomService`, `HostLiveSessionService` |
| domain/repository | `LiveSessionRepository`, `LiveSessionQueryRepository`, `JpaLiveSessionRepository`: las tres columnas, `series_ends_at` en los filtros |
| application | `LiveSessionRequests` (`recurrence`, `repeatUntil`), `LiveSessionDetailResponse`, `LiveSessionResponses` |
| interfaces | La prosa de las `@Operation` de programar, corregir, listar, vitrina, entrar e iniciar |

## 4. Contrato de API

Sin rutas nuevas. Los cuerpos de programar y corregir ganan `recurrence` y `repeatUntil`; `LiveSessionDetailResponse`, `LiveSessionItem` y `AvailableLiveSessionItem` ganan `recurrence`, `repeatUntil`, `nextStartsAt` y `nextEndsAt`. **Compatible**: nada cambia para quien no los manda.

## 5. Autorización

La de cada ruta, sin cambios.

## 6. Auditoría

La instantánea de la clase gana `recurrence` y `repeatUntil`: el `UPDATE` de una repetición sale con su antes y su después.

## 7. Transaccionalidad

La de programar y corregir, sin cambios.

## 8. Impacto sobre otros módulos y documentos

`requirements/ac.md` v0.25.0 (`RN-AC-031`, `RF-AC-055`, §5.2.16, §8.10), `modelo-datos.md` v0.117.0, la matriz y `api/index.md`. Las tripletas de `RF-AC-042` a `RF-AC-054` no se reescriben: esta las enmienda y lo dice.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una clase por fecha, armada por la plataforma** | La que se ofreció primero; el responsable prefirió marcar la repetición (§5.2.16) |
| **Calcular la última fecha en SQL** con `generate_series` | La mensual con meses de distinta longitud y la zona de Bogotá en SQL; guardar una columna es más simple y se indexa |
| **`end_date_time` en Zoom** | Zoom la interpreta en UTC y podría dar una fecha más o menos que la plataforma |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Zoom rechaza corregir una serie ya empezada** con su `start_time` en el pasado | Se manda el inicio guardado tal cual; si Zoom lo rechaza, `503` y nada cambia. Probarlo con las credenciales reales (bloqueo de `RF-AC-044`) |
| 2 | **Zoom y la plataforma discrepan en el número de fechas** | Se manda `end_times` con el número que calcula la plataforma |

## 11. Estrategia de prueba

Unitaria de `LiveSessionSeries` (fechas, límites, la siguiente, en curso); unitaria de `ZoomApiClient` (el cuerpo de una reunión recurrente); integración de API en `LiveSessionAdminIT` y `LiveClassroomIT` con el doble de Zoom: `CA-AC-306` a `CA-AC-311`.
