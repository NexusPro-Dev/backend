# PLAN — `RF-AC-067` a `RF-AC-072` Las señales de trading

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-067`, `RF-AC-068`, `RF-AC-069`, `RF-AC-070`, `RF-AC-071`, `RF-AC-072` |
| Especificación | [`spec.md`](spec.md), aprobada el 14-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 14-10-2026 |

---

## 1. Enfoque

**La señal es una entidad JPA inmutable**, `Signal`, con `create` —que calcula `visibleUntil` con la zona del negocio (`BusinessCalendar`)— y `delete`. Sus comprobaciones son **estáticas y acumulan** en una lista de `FieldError` en vez de lanzar, para que **una, varias y un CSV pasen por el mismo camino**: `SignalService.registrar` recibe una lista de entradas —cada una con el prefijo de sus campos—, junta todos los errores de formato (`400`), después resuelve las membresías (`422`) y solo entonces escribe. Todo en una transacción: **todo o nada** sin más.

**El CSV se lee aparte**, en `SignalCsv`, a filas de texto con su número de línea; los valores los comprueba el servicio como los del JSON. Las membresías del CSV llegan por código y se resuelven contra `memberships`.

**Las lecturas van por SQL nativo con la entidad como resultado.** Las vigentes, en una sola sentencia: `FREE` siempre, `PREMIUM` si el nivel de la membresía es menor que el de BECA, `PERSONALIZED` si está en `signal_memberships`. La membresía vigente la da `CurrentMembershipLookup`, el puerto de `SP` que ya usa el aula.

## 2. Cambios de esquema

`V108`:

1. `signals` con sus tres `CHECK` e índices `ix_signals_visible` (parcial, vivas) e `ix_signals_created`.
2. `signal_memberships`, clave `(signal_id, membership_id)`, en cascada hacia `signals`.
3. Seis permisos (`…7020` a `…7025`, cola `000051` a `000056`): cinco a `SUPERADMIN` y `ADMIN`, `signals:view` donde está `live-sessions:learn`.
4. Guardas: catálogo de 250; los cinco de administración solo en los dos roles.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/models | **`Signal`**, **`SubscriptionType`** (nuevos) |
| domain/repository | **`SignalRepository`**, **`JpaSignalRepository`** (nuevos) |
| domain/service | **`SignalService`**, **`SignalCsv`** (nuevos) |
| application | **`SignalRequests`**, **`SignalResponses`** (nuevos) |
| interfaces | **`SignalController`** (nuevo) |

## 4. Contrato de API

| Método | Ruta | Permiso | Respuesta |
|---|---|---|---|
| `GET` | `/api/v1/signals` | `signals:list` | `200`, página |
| `GET` | `/api/v1/signals/available` | `signals:view` | `200`, lista |
| `POST` | `/api/v1/signals` | `signals:create` | `201` |
| `POST` | `/api/v1/signals/batch` | `signals:create-batch` | `201`, lista |
| `POST` | `/api/v1/signals/import` | `signals:import` | `201`, lista |
| `POST` | `/api/v1/signals/{id}/deletion` | `signals:delete` | `204` |

Compatible: todo es nuevo.

## 5. Autorización

Una ruta, un permiso (`RN-SEG-014`). `available` decide además qué señales por la membresía vigente del actor del token.

## 6. Auditoría

Entidad `signals`, módulo `AC`: `CREATE` por cada señal con par, hora, tipo, suscripción, membresías y `visible_until`; eliminación lógica con el motivo y la misma instantánea.

## 7. Transaccionalidad

Una transacción por petición: varias o un CSV escriben todo o nada. El instante de creación es el mismo para todas las de una petición.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Calcular la ventana al leer** | La señal no se corrige: guardarla deja la consulta de vigentes en un índice y el valor visible en administración |
| **Registrar las válidas y devolver las fallidas** | El responsable eligió todo o nada |
| **Membresías por identificador en el CSV** | Nadie escribe un UUID en una hoja de cálculo; el código es único |
| **Un módulo nuevo de señales** | Es poco y vive junto a las clases en vivo; partirlo después es barato |

## 9. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`signal_memberships` sin `ON DELETE` hacia `memberships`** rompe una suite que borre membresías | `CourseTestSupport.limpiar` borra las señales antes y después de cada suite de academia |
| 2 | **Las suites que cuentan el catálogo** | Se pasan a 250 (y `ADMIN` a 248) en el mismo cambio |

## 10. Estrategia de prueba

Unitarias: `SignalTest` (`CA-AC-340` y las comprobaciones) y `SignalCsvTest` (`CA-AC-343`, la forma del archivo). Integración de API: `SignalsIT` (`CA-AC-339`, `CA-AC-341` a `CA-AC-347`); las del catálogo, con la cifra nueva; `EndpointPermissionsIT`, con las seis rutas.
