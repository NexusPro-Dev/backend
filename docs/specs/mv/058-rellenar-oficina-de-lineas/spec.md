# SPEC — `RF-MV-058` Rellenar la oficina de las líneas de venta que no la tienen

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-058` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que las líneas de venta **que no tienen oficina** —todas las anteriores a la regla, y las que nacieron cuando su vendedor no tenía director con equipo— la ganen **de una vez y a petición de administración**, con la oficina **vigente** del director de su vendedor, **sin tocar ninguna que ya la tenga**.

---

## 2. Contexto

Desde el 09-10-2026 cada línea de venta nace con **la oficina donde se vendió** y la conserva aunque su vendedor se traslade (`RN-MV-078`, [`requirements/mv.md`](../../../requirements/mv.md) v0.97.0 §4.13): la del primero de la cadena del vendedor que pertenece a un equipo, **con la estructura del día de la venta**. Ese mismo día los equipos pasaron a ser de directores ([`requirements/sp.md`](../../../requirements/sp.md) v1.119.0, `RN-SP-051`).

**La regla deja vacío todo lo anterior, y no por un defecto**: a la fecha de cualquier venta anterior a ella **ningún director estaba en un equipo** —la regla que lo permite nace con ella—, de modo que aplicada a lo ya vendido no encontraría nada. **La migración no puede rellenarlo** por lo mismo: los directores entran en los equipos **después**, cuando administración los asigna (`RF-SP-069`). Lo que queda es una orden que administración da **cuando haya asignado los directores**, y que usa la oficina **de hoy**: es lo más cercano que se puede saber de lo que nunca se registró.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Una orden, no un proceso** | Se ejecuta cuando administración lo pide; nada la dispara sola |
| **La oficina de hoy** | La **vigente** del director del vendedor —la misma regla de la cadena, con la estructura de ahora— y no la del día de la venta, que para lo anterior a la regla está vacía |
| **Solo las vacías** | Una línea con oficina **no se toca nunca**, aunque su vendedor esté hoy en otra: esa es la oficina donde se vendió |
| **Solo con vendedor** | Una línea sin vendedor no tiene de quién sacar la oficina; la gana al asignarse (`RF-MV-016`) |
| **Repetible** | Volver a ejecutarla rellena lo que antes no pudo —un director asignado tarde— y no mueve nada más |
| **Un permiso propio** | `RN-SEG-014`: rellenar oficinas no es asignar vendedores ni consultar ventas. Solo administración |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:fill-line-teams` —`SUPERADMIN` y `ADMIN`— | Ordena el relleno |

---

## 4. Alcance

### 4.1 Incluye

- Rellenar, de una vez, la oficina de **todas** las líneas de venta con vendedor y sin oficina, sea cual sea el estado de la venta.
- Decir cuántas líneas rellenó.

### 4.2 No incluye

- Cambiar la oficina de una línea que ya la tiene, o elegirla a mano.
- Rellenar líneas sin vendedor.
- Rellenar solo una venta, un vendedor o un periodo.
- Deshacer el relleno.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-078` | La oficina de la línea, y la excepción que este requerimiento es: **una sola vez, de vacía a una oficina** |
| `RN-MV-001` | Lo vendido no se edita; esta es una excepción acotada, como la entrega y el vendedor |
| `RN-SP-051` | Quién tiene equipo: los directores |
| `RN-SEG-014` | Un permiso propio |

---

## 6. Datos

### 6.1 Entrada

**Ninguna.** Qué líneas y con qué oficina lo deciden la regla y la estructura de hoy; la orden no lleva datos.

### 6.2 Salida

**Cuántas líneas rellenó.** Cero también es una respuesta correcta.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:fill-line-teams` |
| Postcondición | Cada línea de venta con vendedor y sin oficina, cuyo vendedor tiene **hoy** director con equipo, lleva esa oficina |
| Postcondición | Las demás líneas siguen exactamente como estaban |
| Postcondición | Cada venta tocada queda auditada con la oficina de sus líneas, antes y después |

---

## 8. Flujo principal

1. El actor ordena el relleno.
2. El sistema toma las líneas de venta con vendedor y sin oficina.
3. Para cada vendedor distinto, calcula **su oficina de hoy**: la del primero de su cadena actual que pertenece a un equipo.
4. Escribe esa oficina en las líneas de ese vendedor **que siguen** sin ella.
5. Audita lo rellenado, venta por venta.
6. Responde cuántas líneas rellenó.

**Todo o nada**: si algo falla, ninguna línea queda rellenada.

---

## 9. Flujos alternativos

| ID | Flujo |
|---|---|
| `FA-001` | No hay nada que rellenar: responde cero, sin error |
| `FA-002` | Un vendedor no tiene hoy director con equipo —o es un manager—: sus líneas siguen vacías y no cuentan. Una ejecución posterior las rellena si para entonces lo tiene |
| `FA-003` | Mientras se rellena, alguien corrige el vendedor de una línea (`RF-MV-016`): una de las dos espera a la otra; la línea queda con el vendedor y la oficina de la corrección, y el relleno no la pisa |

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Sin el permiso | Prohibido; sin autenticar, `401` |

---

## 11. Validaciones

Ninguna: la orden no tiene entrada.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-729` | Con directores asignados, la orden deja cada línea de venta **con vendedor y sin oficina** con **la oficina vigente del director de su vendedor** —la de su director si es un agente, la suya si es un director— y responde cuántas rellenó |
| `CA-MV-730` | Una línea **que ya tiene oficina no cambia**, aunque su vendedor esté hoy en otra oficina |
| `CA-MV-731` | Una línea **sin vendedor no se toca**: sigue sin oficina |
| `CA-MV-732` | Las líneas de un vendedor que **hoy** no tiene director con equipo, o de un manager, siguen vacías y **no cuentan** |
| `CA-MV-733` | La oficina es **la de hoy**, no la de la fecha de la venta: una venta anterior a que su director tuviera equipo queda con el equipo que el director tiene hoy |
| `CA-MV-734` | Ejecutarla **dos veces seguidas** responde cero la segunda y no cambia nada; ejecutarla **después de asignar tarde a un director** rellena solo las líneas de sus vendedores que seguían vacías |
| `CA-MV-735` | Rellena las líneas de ventas en **cualquier estado** —pendiente, confirmada, rechazada o anulada— |
| `CA-MV-736` | **Solo cambia la oficina**: el vendedor, el estado, los importes y las comisiones quedan como estaban |
| `CA-MV-737` | Sin nada que rellenar responde **éxito con cero**, no un error |
| `CA-MV-738` | Cada venta tocada queda **auditada** con la oficina de sus líneas rellenadas, antes y después |
| `CA-MV-739` | Sin `movements:fill-line-teams` responde prohibido —**también con `movements:assign-sellers`**—; sin autenticar, `401` |
| `CA-MV-740` | El permiso lo tienen **`SUPERADMIN` y `ADMIN`**, y ningún otro rol |

**`CA-MV-730` es el criterio que sostiene el requerimiento**: si el relleno tocara una línea con oficina, una sola ejecución después de un traslado movería lo vendido a la oficina de hoy, que es exactamente lo que `RN-MV-078` existe para impedir.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El vendedor cambió de director desde la venta | Toma la oficina de su director **de hoy**: es lo más cercano que se puede saber (§2.1) |
| Muchas ventas | Se rellenan todas en la misma orden, todo o nada |
| Se ejecuta antes de asignar ningún director | Responde cero y no cambia nada; se puede repetir después |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 09-10-2026 | Primera versión ([`requirements/mv.md`](../../../requirements/mv.md) v0.97.0 §4.13, `RN-MV-078`), con las decisiones del responsable del proyecto: una orden de administración, con `movements:fill-line-teams`, que rellena **una sola vez** las líneas de venta con vendedor y sin oficina con la oficina **vigente** del director de su vendedor, repetible y sin tocar las que ya la tienen. Criterios `CA-MV-729` a `CA-MV-740`. | Responsable del proyecto |
