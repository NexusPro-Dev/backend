# PLAN — `RF-PM-005` Cambiar el estado de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-005` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobado por | — |
| Fecha de aprobación | — |

---

## 1. Enfoque

La operación más corta del módulo y la que concentra su invariante más caro. Cambiar una columna es trivial; lo que no lo es es que **`RN-PM-004` vive entera aquí**: desde que el producto nace inactivo (`RN-PM-012`), esta es la única puerta por la que un upgrade puede quedar activo, y por tanto el único sitio donde dos precios simultáneos para el mismo nivel podrían entrar.

## 2. Cambios de esquema

**Ninguno.** `uq_products_upgrade_target` se creó con la tabla en `V39`, aunque solo esta operación pueda violarla.

## 3. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `domain/models` | `Product.activate()`, `Product.deactivate()` | Devuelven **si hubo cambio**, para decidir si se audita |
| `domain/service` | `ChangeProductStatusService` | Orden de §5 |
| `domain/repository` | `JpaProductRepository` | Traduce `uq_products_upgrade_target` **por nombre de restricción** |
| `interfaces` | `ProductController` | `PATCH /api/v1/products/{id}/status` |

## 4. Contrato de API

`PATCH /api/v1/products/{id}/status` con `{"status": "ACTIVO"}`. Devuelve `200` con el producto.

**Recurso propio y no un campo de `RF-PM-004`**, por lo mismo que `RF-SP-007` separó el estado de un rol: publicar y corregir son decisiones distintas, con permisos que algún día podrán serlo también, y mezclarlas haría que una corrección de texto pudiera poner algo a la venta.

## 5. Orden de verificación

1. **Bloqueo** de la fila del producto.
2. Existe y **no está retirado** (`EX-001`).
3. Si el estado pedido es el que ya tiene: se responde `200` **sin tocar nada y sin auditar** (`FA-001`). No es un error: quien pulsa dos veces el mismo botón no ha hecho nada malo.
4. Si se activa: **tiene descripción** (`RN-PM-014`, `VAL-003`).
5. Si se activa **y es un upgrade**: ningún otro upgrade activo apunta a su destino (`EX-002`), y el mensaje **nombra al producto que lo ocupa** para que el actor sepa cuál desactivar.
6. Se aplica y se audita.

**Desactivar no comprueba nada del destino** (`FA-002`): liberarlo nunca produce conflicto. Y en un servicio, el paso 5 **no se ejecuta** — la prueba comprueba la ausencia, no solo que no falle.

## 6. La concurrencia es el requisito, no un detalle

Dos upgrades inactivos hacia el mismo nivel activados a la vez: **la verificación previa no basta**. Las dos transacciones leen que el destino está libre, las dos concluyen que pueden proceder, y sin nada más quedarían las dos activas — que es exactamente el desenlace que `RN-PM-004` existe para impedir.

**Lo que lo impide es `uq_products_upgrade_target`**, el índice único parcial: la segunda transacción falla al escribir y el adaptador traduce esa violación a `EX-002`, el mismo `409` que habría dado la verificación previa. La verificación previa **existe para dar un mensaje preciso** —qué producto ocupa el destino—; la garantía la da el índice.

Es la misma división de trabajo que `RF-SP-016` fijó para el alta de membresías: la restricción decide, la comprobación redacta.

!!! important "El índice es parcial y por tanto NO admite `DEFERRABLE`"

    `DEFERRABLE` es propiedad de una *restricción*, no de un índice, de modo que esta unicidad muerde en el `UPDATE` y no en el `COMMIT`. El adaptador la traduce ahí, con `flush` explícito, o llegaría al manejador global como fallo no controlado.

## 7. Auditoría

Un evento `UPDATE` con `status` y su valor anterior. **Ninguno cuando no hubo cambio** (paso 3).

**Sin motivo** (`spec.md` §14, resolución 1) y **sin evento de seguridad**: un producto no concede privilegios.

## 8. Transaccionalidad

Una transacción con bloqueo pesimista sobre la fila. El bloqueo serializa dos peticiones **sobre el mismo producto**; lo que serializa dos productos distintos compitiendo por el mismo destino es el índice.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Un campo `status` dentro del `PATCH` de `RF-PM-004` | Mezclaría corregir con publicar |
| Rechazar la petición que no cambia el estado | Obligaría a la interfaz a consultar antes de cada pulsación |
| Confiar solo en la verificación previa | Es la escritura sesgada que `SP` acaba de pagar con `RN-SP-018`: dos transacciones validan contra el estado que la otra va a cambiar y ninguna falla |
| Confiar solo en el índice | Daría un `409` correcto sin decir **qué producto** ocupa el destino, que es lo único accionable |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | La traducción por nombre de restricción se hace por el texto del driver y se rompe al actualizar PostgreSQL | Se traduce por `getConstraintName()`, nunca por el mensaje. Es la regla que `SP` ya sigue en tres adaptadores |
| 2 | La prueba concurrente se escribe con dos hilos que no llegan a solaparse y pasa sin probar nada | Se comprueba además que **exactamente uno** quedó activo, no solo que hubo un `409` |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Los ocho criterios de `spec.md` §12 | API | |
| Activar sin descripción | API | `400`, y admitido en cuanto `RF-PM-004` la pone |
| Desactivar sin descripción | API | **Se admite**: la regla acota lo que se publica |
| Reactivar tras liberar el destino | API | Desactivar el que lo ocupa y activar el otro |
| Petición que no cambia el estado | Integración | `200` y `audit_change_log` no crece |
| Servicio: el paso del destino **no se ejecuta** | Integración | Número de sentencias |
| **Dos activaciones simultáneas hacia el mismo destino** | Concurrencia | Exactamente uno queda activo; el otro recibe `409` con el nombre del que ocupa |
| Desactivar y activar en carrera | Concurrencia | Cualquiera de los dos desenlaces vale; lo que no vale es que no quede ninguno activo |
