# TASKS — `RF-CM-001` Registrar una tarifa de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-001` |
| Plan | [`plan.md`](plan.md), aprobado el 28-08-2026 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/modulo-comisiones` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V44__create_commission_rates.sql`: la tabla, los dos `CHECK`, las tres claves foráneas, la extensión `btree_gist` y `ex_commission_rates_sin_solape` | — | Flyway aplica sobre base limpia. Un `INSERT` directo con dos tarifas por omisión solapadas **lo rechaza el motor** | `Pendiente` |
| `T-02` | Migración `V45__seed_commissions_permissions.sql`: los cuatro permisos de `requirements/cm.md` §6 | `T-01` | Los cuatro existen tras migrar, con su recurso y su acción | `Pendiente` |
| `T-03` | `SP` publica `RoleCatalog` en `roles/application`: el rol con su **tipo** | — | Devuelve vacío para un rol inexistente en lugar de fallar. Regla de ArchUnit en verde | `Pendiente` |
| `T-04` | `SP` publica `UserCatalog` en `users/application`: nombre de usuario y nombre | — | Ídem | `Pendiente` |
| `T-05` | `SP` publica `SellerRoleCatalog` en `users/application`: qué rol vendedor porta una persona, y **falla de forma visible** ante dos | — | Con dos roles vendedores **lanza**, no elige. Ver el bloqueo de §4 | `Pendiente` |
| `T-06` | `PM` publica `ProductCatalog` en `products/application`: código, nombre y **si está retirado** | — | Distingue el producto retirado del inexistente | `Pendiente` |
| `T-07` | `domain/models/CommissionRate`: el agregado, con `RN-CM-007` y `RN-CM-009` | `T-01` | Prueba unitaria sin Spring: porcentaje fuera de rango, fin anterior al inicio, y el **cero como valor válido** | `Pendiente` |
| `T-08` | `domain/models/RateScope`: el grado, **calculado** y no persistido | `T-07` | Los cuatro grados se derivan de qué campos vienen | `Pendiente` |
| `T-09` | Puerto y adaptador de escritura, con la **traducción** de la violación de `ex_commission_rates_sin_solape` a `EX-007` | `T-01`, `T-07` | El solape devuelve `409` y **no** `500` | `Pendiente` |
| `T-10` | `RegisterCommissionRateService`, con el orden de verificación de `plan.md` §4 | `T-03` a `T-09` | Cada excepción sale con su código y en su orden | `Pendiente` |
| `T-11` | DTO de entrada con `VAL-001` a `VAL-006` | `T-10` | Los seis mensajes, con su campo | `Pendiente` |
| `T-12` | DTO de salida con rol, producto y persona **resueltos**, y los ausentes **nulos y presentes** | `T-10` | Un campo ausente no desaparece del JSON | `Pendiente` |
| `T-13` | `POST /api/v1/commission-rates`, con permiso declarativo y `Location` | `T-11`, `T-12` | `201` con cabecera, `403` sin permiso | `Pendiente` |
| `T-14` | Evento de auditoría de creación, con el estado inicial completo | `T-10` | El evento aparece en `RF-SP-011` con módulo `CM` | `Pendiente` |
| `T-15` | Pruebas de los criterios de `spec.md` §12 | `T-13` | `CA-CM-001` a `CA-CM-013` | `Pendiente` |
| `T-16` | **Prueba concurrente del solapamiento**: dos altas simultáneas del mismo caso y periodo | `T-15` | Una `201` y una `409`, **una sola fila**. Es la que verifica que la restricción está en el motor | `Pendiente` |
| `T-17` | Prueba del **día de corte**: termina el 31 y empieza el 31 chocan; empezando el 1, no | `T-15` | Los dos casos | `Pendiente` |
| `T-18` | Documentación OpenAPI del endpoint | `T-13` | El contrato publicado declara los seis campos y los cuatro estados | `Pendiente` |
| `T-19` | Actualizar la matriz de `docs/requirements.md` | `T-15` | La fila de `RF-CM-001` refleja el estado y enlaza esta tripleta | `Pendiente` |

## 2. Orden de ejecución

`T-01` primero: sin tabla no hay nada que probar de extremo a extremo. `T-03` a `T-06` son independientes del resto y conviene escribirlas pronto, **porque son la parte que toca otros dos módulos** y la que puede destapar que una lectura que se daba por disponible no lo está.

`T-07` a `T-09` no dependen entre sí más que del esquema. `T-10` es la que las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-001` a `CA-CM-005` | `T-10`, `T-12`, `T-15` |
| `CA-CM-006` a `CA-CM-008` | `T-03`, `T-05`, `T-06`, `T-10`, `T-15` |
| `CA-CM-009`, `CA-CM-010`, `CA-CM-013` | `T-01`, `T-09`, `T-15`, `T-17` |
| `CA-CM-011`, `CA-CM-012` | `T-07`, `T-11` |

## 4. Bloqueos

| # | Bloqueo | Efecto | Quién lo levanta |
|---|---|---|---|
| 1 | **`V43` está en un PR sin mergear** (`plan.md` §2) | Si aquel PR no entra antes, hay que renumerar **antes de aplicar nada** | Responsable del proyecto, al mergear |
| 2 | **`RN-SP-025` no está implementada** | `T-05` se construye igual y **falla ruidosamente** ante dos roles vendedores, en lugar de elegir. No bloquea este requerimiento; bloquea `RF-CM-005` | Requiere un pase sobre `RF-SP-030`, en `SP` |

## 5. Definición de terminado

- Las diecinueve tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde, incluida la prueba concurrente de `T-16`.
- La matriz y el contrato publicado al día.
- **Ningún requerimiento pasa a `Implementado`**, conforme a `development-guide.md` §12.3.1.
