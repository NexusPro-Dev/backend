# TASKS — `RF-CM-005` Consultar la comisión efectiva

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-005` |
| Plan | [`plan.md`](plan.md), aprobado el 28-08-2026 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/modulo-comisiones` |
| Bloqueado por | **`RN-SP-025`**, sin implementar — ver §4 |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `resolve(...)` en el puerto de consulta: **una sola sentencia** con el orden de precedencia de `plan.md` §1 | `RF-CM-001` · `T-01` | Los cuatro grados se ordenan en SQL, no en Java | **Hecha el 28-08-2026** |
| `T-02` | `ResolveCommissionService`: determina el rol vendedor y delega la precedencia | `T-01`, `RF-CM-001` · `T-05` | El caso de uso **no reordena** nada | **Hecha el 28-08-2026** |
| `T-03` | Respuesta con porcentaje, tarifa aplicada, **grado** y rol considerado | `T-02` | El grado sale de la tarifa que ganó | **Hecha el 28-08-2026** |
| `T-04` | Los **tres desenlaces** distinguidos: hay tarifa, no hay tarifa declarada, la persona no comisiona | `T-03` | `percentage` **nulo y presente** en los dos últimos, **nunca cero** | **Hecha el 28-08-2026** |
| `T-05` | `GET /api/v1/commissions/effective`, en controlador propio | `T-03`, `T-04` | `200` en los tres desenlaces; `422` si la persona o el producto no existen; **ningún `404`** | **Hecha el 28-08-2026** |
| `T-06` | DTO de entrada con `VAL-006` y `VAL-012`, y la fecha **por omisión hoy** | `T-05` | Sin fecha resuelve con la de hoy | **Hecha el 28-08-2026** |
| `T-07` | Pruebas de los criterios de `spec.md` §12 | `T-05` | `CA-CM-039` a `CA-CM-050` | **Hecha el 28-08-2026** |
| `T-08` | **Prueba de la pareja cero / ausencia** | `T-07` | `CA-CM-047` y `CA-CM-048`: la que este endpoint no puede confundir | **Hecha el 28-08-2026** |
| `T-09` | **Prueba de los dos roles vendedores**: el puerto falla de forma visible | `T-02` | **Lanza**, no elige. Es la prueba del bloqueo de §4 | `Pendiente` |
| `T-10` | Documentación OpenAPI del endpoint | `T-05` | Los tres parámetros y los tres desenlaces | **Hecha el 28-08-2026** |
| `T-11` | Actualizar la matriz de `docs/requirements.md` | `T-07` | La fila de `RF-CM-005` refleja el estado **y su bloqueo** | **Hecha el 28-08-2026** |

## 2. Orden de ejecución

`T-01` primero y sola: **es la regla `RN-CM-004` escrita una vez**, y todo lo demás cuelga de que esté bien. `T-08` y `T-09` se escriben aparte de las funcionales porque verifican lo que no se ve en la respuesta — un porcentaje devuelto es plausible aunque venga del grado equivocado.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-039` a `CA-CM-042` | `T-01`, `T-07` |
| `CA-CM-043` a `CA-CM-045` | `T-01`, `T-06`, `T-07` |
| `CA-CM-046` | `T-01`, `T-07` |
| `CA-CM-047`, `CA-CM-048` | `T-04`, `T-08` |
| `CA-CM-049`, `CA-CM-050` | `T-02`, `T-07` |

## 4. Bloqueos

| # | Bloqueo | Efecto | Quién lo levanta |
|---|---|---|---|
| 1 | **`RN-SP-025` no está implementada** — una persona todavía puede portar dos roles de tipo `VENDEDOR` | **Este requerimiento no puede darse por terminado.** El paso 2 de su flujo deja de ser determinista, y con él toda la resolución. Se construye igual, y `T-09` fija que ante dos roles el sistema **falle de forma visible** en lugar de elegir en silencio | Requiere un pase sobre `RF-SP-030`, en `SP`. No es trabajo de este módulo |

**Por qué se construye igual y no se espera.** El bloqueo está aislado en **una** pregunta —cuál es el rol vendedor de esta persona—, exactamente como `RF-SP-040` aisló D-23 en una sola tarea. Todo lo demás —la precedencia, las fechas, los tres desenlaces— es construible y probable hoy con personas de un solo rol vendedor.

## 5. Definición de terminado

- Las once tareas `Hecha` con su verificación pasando.
- `./mvnw clean verify` en verde, incluidas `T-08` y `T-09`.
- **`RN-SP-025` implementada en `SP`.** Hasta entonces, este requerimiento queda `Bloqueado` aunque sus once tareas estén hechas — y la matriz debe decirlo, en lugar de disimularlo.
