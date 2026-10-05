# SPEC — `RF-CM-013` Devengar las comisiones de una línea de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-013` |
| Módulo | `CM` — Comisiones |
| Versión | 0.6.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendada el | 29-09-2026 — **una línea FTD no devenga por venta**: quinta condición de `RN-CM-022`; y la comisión se escribe con su clase, `POR_VENTA` (`RN-CM-044`) |
| Enmendada el | 29-09-2026 — **quien no es el último eslabón cobra su venta propia con la directa del producto**, salvo que tenga personalizada vigente (`RN-CM-045`) |
| Enmendada el | 30-09-2026 — **una línea cuya cadena se revirtió se devenga otra vez** (`RN-CM-047`) |
| Enmendada el | 05-10-2026 — **la comisión se guarda en centésimas, redondeada al guardarse** ([`ADR-006`](../../../architecture/ADR-006-importes-en-unidades-minimas.md)): `CA-CM-336` y `CA-CM-337` |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que **cada línea de venta cobrada y con vendedor produzca su comisión en el momento**, para cada persona de la cadena comercial, sin que nadie tenga que lanzar nada, y que el vendedor la vea crecer el mismo día.

---

## 2. Contexto

El 24-09-2026 `CM` pasó a liquidar ([`requirements/cm.md`](../../../requirements/cm.md) v0.17.0, §5.6): calcular lo que se debe a cada persona de la cadena y **congelarlo**. Se diseñó como una operación que alguien lanzaba sobre un periodo. **El 28-09-2026 el responsable del proyecto decidió que se hiciera sola** (v0.19.0, §5.7): «venta con estado confirmado, si el detalle tiene un vendedor asignado, se le crea la comisión».

**Este requerimiento es el que calcula.** Todo lo que §5.6 decidió sobre **cómo** —la base bruta, el fijo por unidad, la cadena entera, la tasa y la cadena del día de la venta, el tope por línea, la copia de lo aplicado— se hace aquí. Lo que cambió es **cuándo**: en el instante en que la línea queda cobrada y atribuida, y no al final del periodo.

**No tiene ruta ni actor humano.** Lo dispara un aviso de `MV` (`RN-MV-049`) y lo repite el cierre del periodo sobre lo que el aviso no alcanzó (`RF-CM-009`, `RN-CM-034`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **La línea, no la venta** | Devenga cada línea con vendedor de una venta confirmada, aunque a otra línea de la misma venta le falte el suyo (`RN-CM-022`) |
| **Dos momentos, el que llegue último** | Al confirmarse la venta, las líneas que ya tenían vendedor; al asignarse un vendedor en una venta ya confirmada, esa línea (`RN-CM-031`) |
| **Después de la venta, y aparte** | El cálculo ocurre cuando la venta ya quedó confirmada, y si falla la venta sigue confirmada (`RN-CM-031`) |
| **Un desenlace por línea** | Devengada, sin comisión o rechazada — y solo la rechazada se vuelve a intentar (`RN-CM-032`) |
| **El lote abierto** | Cada comisión se suma al lote abierto de su persona y moneda, y si no lo hay, lo abre (`RN-CM-033`) |
| **Dos fechas distintas** | La tasa y la cadena se resuelven con el día de la venta; el lote lo decide el momento del devengo (`RN-CM-024`, `RN-CM-033`) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| `MV`, al confirmar una venta (`RF-MV-003`) o asignar vendedores (`RF-MV-016`) | Avisa de que unas líneas quedaron comisionables. No sabe quién escucha |
| El cierre del periodo (`RF-CM-009`) | Devenga lo que no tiene desenlace y reintenta lo rechazado (`RN-CM-034`) |

**Ningún actor humano, y por tanto ningún permiso.** Quien confirma la venta o asigna el vendedor ya fue autorizado por `MV`.

---

## 4. Alcance

### 4.1 Incluye

- Reconstruir la **cadena comercial** de la línea el día de la venta, desde su vendedor hacia arriba, en toda la profundidad.
- Resolver la **tasa de cada persona** de la cadena sobre el producto de la línea, el día de la venta.
- Calcular lo que devenga cada nivel y **comprobar que la cadena no pasa del importe de la línea**.
- **Congelar** una comisión por nivel, con lo que aplicó, y **sumarla al lote abierto** de su persona y moneda.
- Anotar el **desenlace** de la línea.
- **Reintentar** una línea rechazada, cuando el cierre lo pida.

### 4.2 No incluye

- **Cerrar el periodo**: es `RF-CM-009`.
- **Pagar**: es `RF-CM-011`.
- **Corregir una comisión ya devengada.** No se recalcula nunca (`RN-CM-029`).
- **Devengar lo que no es una venta**: solo `VENTA` (`RN-CM-022`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-022` | Solo la línea de una venta confirmada, con vendedor y sin desenlace — **y que no sea un FTD** (29-09-2026) |
| `RN-CM-044` | Cada comisión que escribe es de clase `POR_VENTA` (29-09-2026) |
| `RN-CM-045` | En el nivel `0`, si quien vendió no es el último eslabón y no tiene personalizada vigente, **la directa del producto sustituye su tasa de rol** (29-09-2026) |
| `RN-CM-031` | Se dispara solo, después de la venta, y un fallo no la deshace |
| `RN-CM-032` | Un desenlace por línea; solo el rechazo se reintenta |
| `RN-CM-033` | El lote abierto de la persona y la moneda; el periodo lo decide el devengo |
| `RN-CM-011`, `RN-CM-025` | Cobra toda la cadena del día de la venta; quien no tiene tasa no cobra y no la interrumpe |
| `RN-CM-004`, `RN-CM-024` | La tasa la resuelve `RF-CM-005`, con la fecha de la venta leída en la zona del negocio |
| `RN-CM-023` | Base bruta; el fijo paga por unidad |
| `RN-CM-026` | Si la cadena pasa del importe de la línea, nadie cobra por ella, y la línea queda rechazada con su motivo |
| `RN-CM-027` | Una línea se devenga una sola vez por persona |
| `RN-CM-008`, `RN-CM-017` | Se copia la forma, el valor, la base, la moneda y la tasa exacta |
| `RN-CM-012` | Sin tasa en toda la cadena no se paga, y no es un error |

**Ninguna regla nueva**: todas nacieron en `requirements/cm.md` v0.17.0 y v0.19.0.

---

## 6. Datos

### 6.1 Entrada

| Dato | Descripción |
|---|---|
| Líneas | Las líneas de venta que quedaron comisionables, identificadas. De cada una este módulo necesita saber, **por la lectura que `MV` publica**: su venta, si está confirmada, su vendedor, su producto, su precio unitario, su cantidad, su moneda y el instante de la venta |

### 6.2 Salida

**Ninguna hacia quien avisa**: `MV` no espera respuesta. Lo que queda son las comisiones, el lote abierto actualizado y el desenlace de cada línea. Hacia el cierre, **cuántas líneas devengaron**, para su resumen (`RF-CM-009`).

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La venta ya quedó confirmada o la línea ya tiene vendedor —**escrito**, no en curso— |
| Postcondición | Cada línea atendida tiene **un** desenlace. Si devengó, hay una comisión por cada persona de la cadena con tasa, y cada una está sumada al lote abierto de su persona y moneda |

---

## 8. Flujo principal

1. Llega el aviso de que unas líneas quedaron comisionables.
2. Para cada línea, se relee lo que `MV` publica y se comprueba que **sigue** cumpliendo `RN-CM-022` —confirmada, con vendedor, de tipo venta **y no FTD**, 29-09-2026— y que **no tiene desenlace**. Si no, se salta sin error (`FA-001`).
3. Se reconstruye la cadena: el vendedor y sus superiores **vigentes el día de la venta**, hasta el que no tiene superior.
4. Para cada persona de la cadena se resuelve su tasa sobre el producto, el día de la venta. Quien no tiene, no cobra y se sigue subiendo. **En el nivel `0`, desde el 29-09-2026**: si quien vendió **no es el último eslabón** y lo que ganó **no es una personalizada**, cobra la **directa del producto** (`RN-CM-045`).
5. Se calcula lo de cada nivel: porcentaje sobre la base bruta, o importe fijo por unidad.
6. Si **nadie** tiene tasa, la línea queda **sin comisión** y termina (`FA-002`).
7. Si la suma de la cadena **pasa del importe de la línea**, la línea queda **rechazada** con su motivo y nadie cobra (`FA-003`).
8. Si no, se escribe una comisión por nivel, cada una **sumada al lote abierto** de su persona y moneda —abriéndolo si no existe—, y la línea queda **devengada**.
9. Cada línea se atiende **por separado**: que una falle no impide las demás.

---

## 9. Flujos alternativos

### FA-001 — La línea ya no cumple, o ya se atendió

Se ignora. Es lo esperado cuando el aviso y el barrido del cierre llegan a la vez, o cuando el mismo aviso se repite.

### FA-002 — Nadie de la cadena tiene tasa

La línea queda **sin comisión**, y **es definitivo**: no se reintenta aunque mañana alguien registre una tasa (`RN-CM-032`).

### FA-003 — La cadena pasa del 100 %

La línea queda **rechazada**, con un motivo que dice **cuánto sumaba la cadena y cuánto valía la línea**. Ninguna comisión se escribe. El cierre la volverá a intentar (`RN-CM-034`).

### FA-004 — Reintento de una rechazada

Se repite el flujo principal desde el paso 3. Si ahora devenga, pasa a **devengada** y sus comisiones entran en el lote abierto **de este momento**. Si vuelve a pasar del 100 %, sigue rechazada con el motivo actualizado. Si ahora **nadie** tiene tasa —se retiraron las que sobraban—, pasa a **sin comisión**.

### FA-005 — Dos avisos de la misma línea a la vez

Uno la atiende; el otro no hace nada. **Nunca se devenga dos veces** (`RN-CM-027`, `RN-CM-032`).

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Falla algo inesperado al atender una línea —la base cae, una lectura de otro módulo falla— | Esa línea **no** queda con desenlace, se registra el error, y las demás siguen. **La venta no se entera.** El barrido del siguiente cierre la recogerá (`RN-CM-034`) |
| `EX-002` | Un vendedor de la cadena porta dos roles vendedores | Lo mismo que `EX-001`: es el fallo visible de `RF-CM-005` (`RN-SP-025`), y la línea espera sin desenlace hasta que se corrija |

**No hay excepciones de negocio hacia nadie**: no hay quien las reciba.

---

## 11. Validaciones

**Ninguna de entrada.** Lo que se comprueba es el estado de la línea al releerla (paso 2), no lo que dice el aviso: el aviso solo dice **dónde mirar**.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-154` | Al **confirmar** una venta cuyas líneas tienen vendedor, cada línea produce en el momento una comisión por cada persona de su cadena con tasa, con la forma, el valor, la base, la moneda, la tasa exacta, la fecha de la venta y el instante del devengo; y la línea queda **devengada** |
| `CA-CM-155` | En una venta confirmada con **una línea sin vendedor**, las líneas que sí lo tienen devengan al confirmar, y la otra **no devenga** hasta que se le asigna; al asignársela, devenga **ella sola** |
| `CA-CM-156` | Una venta **no confirmada** no devenga, aunque todas sus líneas tengan vendedor |
| `CA-CM-157` | La **cadena** se reconstruye con los superiores **del día de la venta**: un cambio de superior posterior no altera a quién se le paga |
| `CA-CM-158` | Quien no tiene tasa sobre el producto **no cobra y no interrumpe** la cadena: su superior cobra igual |
| `CA-CM-159` | La tasa es la que regía **el día de la venta en `America/Bogota`**: una venta a las 20:00 del día 10, hora de Bogotá, con una personalizada que empieza el día 11, cobra por la tasa de rol |
| `CA-CM-160` | La **base es bruta** —el descuento de la línea no la reduce— y el **fijo paga por unidad** |
| `CA-CM-161` | Si la cadena **pasa del importe de la línea**, no se escribe ninguna comisión y la línea queda **rechazada** con un motivo que dice la suma y el importe |
| `CA-CM-162` | Si **nadie** tiene tasa, la línea queda **sin comisión** y no se escribe ninguna comisión |
| `CA-CM-163` | Cada comisión se **suma al lote abierto** de su persona y moneda, que la primera abre; dos comisiones de la misma persona **en monedas distintas** van a **dos lotes** |
| `CA-CM-164` | **Dos avisos simultáneos** de la misma línea la devengan **una sola vez**, y el total del lote sube una sola vez |
| `CA-CM-165` | Dos comisiones **simultáneas** de la misma persona y moneda, de líneas distintas, **suman las dos** al total del lote |
| `CA-CM-166` | Si devengar **falla**, la venta **sigue confirmada**, la línea queda **sin desenlace** y las demás líneas del mismo aviso **se devengan** |
| `CA-CM-167` | Un **reintento** de una línea rechazada, tras corregir la tasa que sobraba, la deja **devengada** en el lote abierto de ese momento; si sigue pasándose, sigue **rechazada** con el motivo actualizado y los intentos sumados |
| `CA-CM-168` | Una línea **sin comisión** **no se reintenta**, aunque después se registre una tasa |
| `CA-CM-169` | Una línea de un movimiento que **no es una venta** no devenga |
| `CA-CM-264` | Un **`DIRECTOR`** que vende, sin personalizada, cobra en el nivel `0` **la directa del producto** —`source = DIRECTA`, `rate_id` el producto, su forma y su valor copiados— y su `MANAGER` cobra **su tasa de rol** en el nivel `1` (29-09-2026) |
| `CA-CM-265` | Un **`AGENTE`** que vende cobra **su tasa de rol**, como antes; su `DIRECTOR` y su `MANAGER`, las suyas: **nadie cobra la directa** |
| `CA-CM-266` | Un `DIRECTOR` con **personalizada vigente** sobre el producto cobra **la personalizada**, no la directa |
| `CA-CM-267` | Un `DIRECTOR` **sin tasa de rol** sobre el producto cobra igualmente la directa; y un **`MANAGER`** que vende la cobra y **no hay nadie por encima** |
| `CA-CM-268` | Si la directa más las tasas de los superiores **pasan del 100 %** de la línea, la línea queda **`RECHAZADA`** y nadie cobra (`RN-CM-026`) |
| `CA-CM-269` | Una directa **de cero** deja una comisión de importe cero y la línea **`DEVENGADA`**, como una tasa de cero |
| `CA-CM-270` | Corregir la directa **después** de devengar **no cambia** lo devengado (`RN-CM-008`) |
| `CA-CM-304` | Una línea cuya cadena se **revirtió** al corregirse su vendedor (`RF-CM-024`) se devenga **otra vez** con el aviso de la corrección, como una línea recién atribuida: con la tasa y la cadena **del día de la venta**, en el lote **abierto** de cada persona; y quien estaba en la cadena vieja cobra la nueva aunque tenga una comisión **revertida** de la misma línea (30-09-2026) |
| `CA-CM-305` | Si el aviso de la corrección **se pierde**, la línea queda sin desenlace y **el barrido del siguiente cierre** la devenga |
| `CA-CM-328` | Desde el 05-10-2026 la directa es **la de la tasa de rol del vendedor** sobre el producto (`RN-CM-050`): un `DIRECTOR` y un `MANAGER` que venden el mismo producto cobran **cada uno la suya**, y `rate_id` es **la tasa de rol**. Enmienda `CA-CM-264` |
| `CA-CM-329` | Un `DIRECTOR` cuya tasa de rol **no declara directa** cobra en el nivel `0` **su tasa de rol**, con `source = ROL` |
| `CA-CM-330` | Un `DIRECTOR` **sin tasa de rol** sobre el producto **no cobra** en el nivel `0` —no hay directa donde leerla—; sus superiores cobran su override. Enmienda `CA-CM-267` |
| `CA-CM-336` | La comisión **se calcula con cuatro decimales y se guarda redondeada a dos con `HALF_UP`**: el 10 % de una línea de `0.05` da `0.005`, se guarda `1` (en centésimas) y se lee `0.01`. El redondeo lo hace el dominio al construir la fila, no el convertidor (05-10-2026) |
| `CA-CM-337` | El **total del lote** es la suma **exacta** de sus comisiones en centésimas, y el abono al pagarlo (`RF-CM-011`, `RF-MV-024`) es **exactamente ese total**, sin redondeo adicional (05-10-2026) |

**`CA-CM-166` es el que sostiene la decisión de §2.1**: el cobro no depende de la configuración de comisiones.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La cadena tiene una sola persona —el vendedor no tiene superior— | Devenga ella sola |
| Una tasa del 0 % | Escribe la comisión de cero: la persona **tenía** tasa, y eso es lo que se copia |
| Un producto gratuito con importe fijo | Devenga el fijo por unidad (`RN-CM-020`); la línea vale cero y el tope de `RN-CM-026` **no se aplica** a una línea de importe cero con importes fijos, igual que `RN-CM-020` no lo aplica al configurar |
| La persona de un nivel está eliminada hoy | Cobra igual: el día de la venta estaba en la cadena |
| El vendedor de la línea ya no porta rol vendedor hoy | Resuelve su personalizada si la tiene; si no, no cobra y la cadena sigue (`RN-CM-004`, `RN-CM-012`) |
| La venta se confirmó con la aplicación cayéndose justo después | La línea queda sin desenlace y la recoge el siguiente cierre |

---

## 14. Preguntas abiertas

**El rol con el que resuelve cada nivel es el de hoy, no el del día de la venta.** `RF-CM-005` resuelve la tasa de rol con el rol vendedor que la persona **porta ahora**, y los roles no guardan historial. Una persona ascendida entre la venta y el devengo cobraría por su rol nuevo. Con el devengo en el momento la ventana es de minutos u horas y no de un mes, de modo que **se acepta** y se registra aquí en lugar de resolverse.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 28-09-2026 | Primera versión, con el devengo automático ([`requirements/cm.md`](../../../requirements/cm.md) v0.19.0, §5.7). **Sin ruta ni permiso**: la disparan el aviso de `MV` y el barrido del cierre. Criterios `CA-CM-154` a `CA-CM-169`. | Responsable del proyecto |

| 0.2.0 | 29-09-2026 | **Una línea FTD no devenga por venta** (`RN-CM-022` con su quinta condición, [`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8): no queda con desenlace, ni `SIN_COMISION` ni ningún otro, y el barrido no la recoge; lo que paga lo decide `RF-CM-020`. Y cada comisión se escribe con su clase, `POR_VENTA` (`RN-CM-044`). **El criterio es `CA-CM-253`, de `RF-CM-020`**, que se prueba en la suite de este requerimiento. | Responsable del proyecto |
| 0.3.0 | 29-09-2026 | **Quien no es el último eslabón cobra su venta propia con la directa del producto** (`RN-CM-045`, [`requirements/cm.md`](../../../requirements/cm.md) v0.24.0 §5.9): en el nivel `0`, si no tiene personalizada vigente, la directa sustituye su tasa de rol; los niveles de encima no cambian. El último eslabón se lee en la jerarquía de roles. `CA-CM-264` a `CA-CM-270`. | Responsable del proyecto |
| 0.4.0 | 30-09-2026 | **Una línea cuya cadena se revirtió se devenga otra vez** (`RN-CM-047`, [`requirements/cm.md`](../../../requirements/cm.md) v0.26.0 §5.10): `RF-CM-024` borra su desenlace, y el aviso de `MV` al corregir el vendedor la trae aquí como a cualquier línea recién atribuida. **La unicidad cuenta solo las vivas** (`RN-CM-027`), de modo que quien está en las dos cadenas cobra la nueva. `CA-CM-304`, `CA-CM-305`. | Responsable del proyecto |
| 0.5.0 | 05-10-2026 | **La directa se lee en la tasa de rol del vendedor** (`RN-CM-050`, [`requirements/cm.md`](../../../requirements/cm.md) v0.30.0 §5.11) y no en el producto: una por rol, opcional, y `rate_id` apunta a la tasa. `CA-CM-328` a `CA-CM-330`; se enmiendan `CA-CM-264` (la directa y su `rate_id`) y `CA-CM-267` (sin tasa de rol ya no hay directa). | Responsable del proyecto |
| 0.6.0 | 05-10-2026 | **La comisión se guarda en centésimas** ([`ADR-006`](../../../architecture/ADR-006-importes-en-unidades-minimas.md), [`requirements/cm.md`](../../../requirements/cm.md) v0.31.0), por decisión del responsable del proyecto. `commission_amount`, `fixed_amount` y `unit_price` de `commissions` y `total_amount` del lote pasan a `bigint`. **El cálculo no cambia: sigue con cuatro decimales en memoria**, y lo que cambia es dónde se pierde la precisión, que ahora es **al guardar la fila, con `HALF_UP`**, y no al pagar. `CA-CM-336` fija ese redondeo y `CA-CM-337` su consecuencia: el lote suma enteros, y el abono es su total sin tocarlo. **`CA-CM-166` no cambia de enunciado**, pero el dato que lo provocaba ya no provoca nada, y `tasks.md` §10 lo declara. | Responsable del proyecto |
