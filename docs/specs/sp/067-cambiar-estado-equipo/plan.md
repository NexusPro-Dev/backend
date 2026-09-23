# PLAN — `RF-SP-067` Cambiar el estado de un equipo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-067` |
| Especificación | [`spec.md`](spec.md), aprobada el 22-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 22-09-2026 |

---

## 1. Enfoque

**La operación más pequeña del bloque, y la que más cuidado pide en una línea: el atajo de la idempotencia.** Si el estado declarado es el que ya tiene, el servicio **sale sin escribir y sin auditar**, y devuelve el detalle. Escribir «por si acaso» produciría una fila de auditoría por cada petición repetida y haría avanzar `updated_at` sin que nada cambiara, que es cómo una auditoría deja de poder leerse.

**El estado vive en el agregado.** `Team.activate(ahora)` y `Team.deactivate(ahora)` devuelven si hubo cambio, y el servicio decide con eso si audita. Ninguna de las dos consulta nada: `RN-SP-053` no se comprueba aquí —es `RF-SP-069` quien rechaza asignar a un equipo suspendido—, y esa asimetría es deliberada: **la regla la aplica quien intenta entrar, no quien cierra la puerta**.

**Sin migración.** `ck_teams_status` ya acota el dominio en el motor desde `V33`, y el `enum` del dominio es el mismo conjunto.

## 2. Cambios de esquema

**Ninguno.** `teams.status`, su `CHECK` y el permiso `teams:change-status` existen desde `V33` y `V34`.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/models` | `Team` | Modificado | `activate(ahora)` y `deactivate(ahora)`: aplican y **devuelven si hubo cambio**; avanzan `updatedAt` solo entonces |
| `domain/service` | `ChangeTeamStatusService` | Nuevo | Resuelve el equipo no eliminado con bloqueo, aplica, audita **solo si cambió** y relee el detalle. `@Transactional` |
| `application` | `ChangeTeamStatusRequest` | Nuevo | `status` obligatorio, resuelto sin distinguir mayúsculas; rechaza campos desconocidos —en particular `reason`, que esta operación no admite |
| `interfaces` | `TeamController` | Modificado | `PATCH /api/v1/teams/{id}/status` con `teams:change-status` |
| Pruebas | `TeamStatusIT` | Nuevo | §11 |
| Pruebas | `EndpointPermissionsIT`, `OpenApiContractIT` | Modificado | La ruta y su extensión |

## 4. Contrato de API

| Método | Ruta | Descripción |
|---|---|---|
| `PATCH` | `/api/v1/teams/{id}/status` | Suspende o reactiva el equipo |

**Petición**

```json
{ "status": "INACTIVO" }
```

**Respuesta `200`**: la forma del detalle de `RF-SP-065`, con el estado nuevo y los mismos miembros.

**Errores**

| Código | Cuándo | `error_code` |
|---|---|---|
| `400` | `status` ausente o desconocido; `id` mal formado; campo no admitido | `VAL-001` a `VAL-003` |
| `403` | Sin `teams:change-status` | — |
| `404` | El equipo no existe o está eliminado | `EX-001` |

**Ruta propia `/status` y no un campo del `PATCH` general**, como en `roles`, `users`, `products` y `courses`: es lo que permite que el permiso signifique una operación (`RN-SEG-014`) y que el `405` de un método equivocado no se confunda con un `404`.

## 5. Autorización

| Endpoint | Permiso |
|---|---|
| `PATCH /api/v1/teams/{id}/status` | `teams:change-status` |

`CA-SP-769` prueba el `403` con `teams:update` puesto. **Es la separación que justifica el permiso**: un rol puede corregir nombres de equipos sin poder suspender ninguno, que es una decisión de negocio distinta y una vista distinta en el frontend.

## 6. Auditoría

| Operación | Registro | Contenido |
|---|---|---|
| Cambio efectivo | `audit_change_log` | `UPDATE` de `teams` con el estado anterior y el nuevo |
| Petición idempotente (`FA-001`) | — | **Nada.** No hubo cambio que registrar |
| — | `audit_security_log` | **Nada, nunca.** Un equipo no concede permisos: desactivarlo no retira acceso a nadie |

**Aquí está la diferencia de fondo con `RF-SP-007`**, que sí emite evento de seguridad: desactivar un rol deja a sus portadores sin permisos de inmediato (`RN-SEG-002`) y eso es un hecho de autorización. Desactivar un equipo no cambia lo que nadie puede hacer.

## 7. Transaccionalidad

| Elemento | Transacción |
|---|---|
| `UPDATE` del estado y su fila de auditoría | **La misma** (Art. V.14) |
| Relectura del detalle | La misma |

El equipo se resuelve **con bloqueo**: dos cambios simultáneos se ordenan y el segundo ve el estado que dejó el primero, de modo que uno escribe y el otro resulta idempotente en lugar de escribir los dos.

## 8. Impacto sobre otros módulos

Ninguno. Y dentro de `SP`, uno que conviene tener escrito: **`RF-SP-069` consulta este estado** para rechazar la asignación a un equipo suspendido (`RN-SP-053`). Es la única lectura del estado fuera de las tres de este bloque.

## 9. Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Alternar el estado** sin declarar destino | Dos peticiones idénticas dejarían resultados distintos según el orden: la idempotencia se pierde y el reintento de un cliente con mala red se vuelve peligroso |
| **Cerrar las pertenencias al desactivar** | Movería la atribución de una red entera sin decisión explícita (`RN-SP-053`, spec §14.1) |
| **Exigir motivo** | El motivo es la barrera de lo irreversible; suspender se deshace con una petición (spec §14.2) |
| **Prescindir del estado y usar solo la eliminación lógica** | «Ya no lo uso pero quiero verlo con su gente» no tendría cómo decirse, y la eliminación exige vaciar antes (`RN-SP-054`) |
| **Auditar también la petición idempotente** | Una fila por reintento; la auditoría dejaría de ser una historia de cambios |
| **Comprobar aquí que un equipo inactivo no recibe miembros** | La regla la aplica quien intenta entrar (`RF-SP-069`); comprobarla en los dos sitios duplicaría la condición y abriría la puerta a que divergieran |
| **Rechazar desactivar un equipo con miembros**, como `RN-SP-054` hace con eliminar | Dejaría sin forma de decir «no quiero más gente aquí» justo cuando más falta hace: en un equipo que se está desmontando y todavía tiene a alguien |

## 10. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Que el atajo idempotente se implemente escribiendo igual | Bajo en datos, medio en auditoría | `CA-SP-765` comprueba que no hay fila nueva **ni** `updatedAt` avanzado |
| Que alguien añada aquí la comprobación de `RN-SP-053` | Medio — dos sitios que pueden divergir | El plan lo declara (§9) y `CA-SP-766` prueba la regla **desde `RF-SP-069`**, que es donde vive |
| Que dos cambios simultáneos produzcan dos filas de auditoría | Bajo | Bloqueo en la lectura (§7) |

## 11. Estrategia de prueba

| Criterio | Nivel | Qué verifica |
|---|---|---|
| `CA-SP-763` | API (`TeamStatusIT`) | Desactivar y reactivar, con la forma del detalle en ambos |
| `CA-SP-764` | Integración (`TeamStatusIT`) | Los miembros siguen ahí tras desactivar, en el detalle y en el recuento del listado |
| `CA-SP-765` | Integración (`TeamStatusIT`) | Petición repetida: `200`, ninguna fila nueva en `audit_change_log` y `updatedAt` sin avanzar |
| `CA-SP-766` | API (`TeamStatusIT` con `RF-SP-069`) | Asignar a un equipo suspendido responde `409`; tras reactivarlo, pasa. **Se escribe cuando `RF-SP-069` exista** y hasta entonces queda declarada como la única dependencia cruzada del bloque |
| `CA-SP-767` | API (`TeamStatusIT`) | Estado ausente, estado desconocido, `id` mal formado, campo no admitido, equipo inexistente y eliminado |
| `CA-SP-768` | Integración (`TeamStatusIT`) | La fila `UPDATE` con los dos valores; `audit_security_log` sin filas nuevas |
| `CA-SP-769` | API + `EndpointPermissionsIT` | El `403` con `teams:update`, y la ruta con su código |

**Unitarias sin Spring** (`TeamTest`): que `activate` y `deactivate` devuelven si hubo cambio y solo entonces avanzan `updatedAt`.
