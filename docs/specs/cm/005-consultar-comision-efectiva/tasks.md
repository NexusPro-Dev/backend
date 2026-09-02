# TASKS — `RF-CM-005` Consultar la comisión efectiva

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-005` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.2.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/flujos-de-pm-y-cm` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación.

!!! warning "Las tareas están hechas antes que este documento"

    El código se rehízo el 02-09-2026 y esta lista viene detrás. **No planifica: registra.** La tercera compuerta del Art. I.6 sigue pendiente y por eso el documento está `En revisión`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `RateSource`, con el orden de declaración como orden de precedencia | `RF-CM-001` · `T-05` | El javadoc dice que reordenarlo desalinea la regla | **Hecha el 02-09-2026** |
| `T-02` | **La sentencia**: `UNION ALL` con la prioridad en el `ORDER BY` | `RF-CM-006` · `T-02`, `RF-CM-007` · `T-01` | Una sola sentencia, y `LIMIT 1` | **Hecha el 02-09-2026** |
| `T-03` | La rama de la persona **sin filtrar por rol ni por producto** | `T-02` | Una personalizada gana sobre un producto sin asociaciones | **Hecha el 02-09-2026** |
| `T-04` | La rama del rol **exige la asociación**, sin ningún `OR ... IS NULL` | `T-02` | Una tasa de rol sin asociar no devuelve nada | **Hecha el 02-09-2026** |
| `T-05` | El `JOIN` a `commission_rates` **por la clave compuesta** | `T-04` | No puede leer el porcentaje de una tasa con otro rol | **Hecha el 02-09-2026** |
| `T-06` | **El rol admite nulo** y apaga la rama del rol | `T-02` | Quien no vende y tiene personalizada **cobra** | **Hecha el 02-09-2026** |
| `T-07` | Las dos ramas filtran `deleted_at IS NULL` | `T-02` | Una tasa retirada deja de resolver | **Hecha el 02-09-2026** |
| `T-08` | `ResolveCommissionService`: comprueba, busca el rol y **delega sin ordenar nada** | `T-02` | El caso de uso no contiene ninguna comparación de precedencia | **Hecha el 02-09-2026** |
| `T-09` | Clasificar la ausencia: «no comisiona» frente a «sin tarifa» | `T-08` | Con rol y sin tasa, «sin tarifa»; sin rol y sin nada, «no comisiona» | **Hecha el 02-09-2026** |
| `T-10` | `EffectiveCommissionResponse` con los tres desenlaces y **el porcentaje siempre presente** | `T-09` | El nulo llega como nulo, no ausente | **Hecha el 02-09-2026** |
| `T-11` | `GET /api/v1/commissions/effective` | `T-08`, `T-10` | `200` en los tres desenlaces, `400`, `403`, `422` | **Hecha el 02-09-2026** |
| `T-12` | Pruebas de los criterios de `spec.md` §12 | `T-11` | `CA-CM-038` a `CA-CM-050` | **Hecha el 02-09-2026** |
| `T-13` | **Prueba de que la tasa sin asociar no paga** | `T-12` | `CA-CM-039` | **Hecha el 02-09-2026** |
| `T-14` | **Prueba de `FA-003`**: quien no vende cobra su personalizada, con el rol nulo | `T-12` | `CA-CM-045` | **Hecha el 02-09-2026** |
| `T-15` | **Prueba del estado que `RN-CM-015` impide**, sembrado a mano | `T-12` | `CA-CM-048` | **Hecha el 02-09-2026** |
| `T-16` | Documentación OpenAPI, con los tres desenlaces y **el aviso del rol nulo** | `T-11` | La descripción explica que nulo y cero no son lo mismo | **Hecha el 02-09-2026** |
| `T-17` | Actualizar la matriz de `docs/requirements.md` | `T-12` | La fila refleja el estado y el bloqueo de `RN-SP-025` | **Hecha el 02-09-2026** |
| `T-18` | **Prueba de los dos roles vendedores**: que el puerto falle de forma visible en vez de elegir | `T-12` | Bloqueada por `RN-SP-025` | `Bloqueada` |

## 2. Orden de ejecución

**`T-02` va antes que `T-08`, y no al revés.** Escribir primero el caso de uso invita a encadenar dos consultas y decidir en Java — que es exactamente lo que `plan.md` §1 existe para impedir. Construyendo antes la sentencia, el caso de uso queda con lo único que le toca: comprobar, buscar el rol y delegar.

**`T-06` parece un detalle de firma y es una decisión de negocio.** Aceptar el rol nulo es lo que hace que `FA-003` ocurra. La v0.1.0 cortaba antes de consultar cuando la persona no portaba rol vendedor, y con el modelo nuevo **eso escondería que su personalizada sigue pagando**.

**`T-15` construye a mano un estado que el sistema no permite alcanzar**, y es la única tarea del módulo que lo hace. No prueba un comportamiento que alguien vaya a usar: prueba **qué pasaría si `RN-CM-015` no existiera**. Es la evidencia de que esa regla no es una precaución teórica.

**`T-18` sigue bloqueada**, como en la v0.1.0. `RN-SP-025` —una persona no tiene dos roles vendedores— es la invariante de la que esta resolución depende para ser determinista, y hasta que `SP` la sostenga no hay forma de probar que el puerto falle de forma visible en lugar de elegir uno en silencio.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-038` | `T-04`, `T-05`, `T-12` |
| `CA-CM-039` | `T-04`, `T-13` |
| `CA-CM-040`, `CA-CM-041` | `T-04`, `T-05`, `T-12` |
| `CA-CM-042`, `CA-CM-043` | `T-03`, `T-12` |
| `CA-CM-044` | `T-03`, `T-12` |
| `CA-CM-045` | `T-06`, `T-14` |
| `CA-CM-046` | `T-09`, `T-12` |
| `CA-CM-047` | `T-10`, `T-12` |
| `CA-CM-048` | `T-07`, `T-15` |
| `CA-CM-049` | `T-08`, `T-12` |
| `CA-CM-050` | `T-08`, `T-11`, `T-12` |

## 4. Bloqueos

| # | Bloqueo | Efecto |
|---|---|---|
| 1 | **`RN-SP-025`** — una persona no puede tener dos roles vendedores | `T-18` queda `Bloqueada`. El requerimiento funciona; lo que no se puede probar es que el puerto **falle de forma visible** en lugar de elegir uno |

**Y queda declarada una deuda que no bloquea nada y que nadie va a resolver desde aquí**: el tope de la suma de la cadena (`RN-CM-011`). Este requerimiento ve un nivel y no puede verlo; lo heredará la liquidación.

## 5. Definición de terminado

- Diecisiete de las dieciocho tareas `Hecha` con su verificación pasando. **`T-18` queda `Bloqueada` y declarada.**
- `./mvnw clean verify` en verde. **Comprobado el 02-09-2026**: 278 unitarias y 876 de integración.
- La matriz y el contrato publicado al día.
