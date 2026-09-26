# SPEC — `RF-MV-011` Comprar un producto por el hotlink de un vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-011` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 24-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

---

## 1. Objetivo

Que un cliente **con cuenta** compre el producto que le llegó por el enlace de un vendedor, y que **ese** vendedor cobre la venta aunque no sea su agente.

## 2. Contexto

`RF-PM-008` publica el enlace **sin token** y `RF-SP-045` registra por él a quien **no** tiene cuenta. Faltaba el caso de en medio, que es el más corriente en cuanto la red crece: **quien ya tiene cuenta y compra por el enlace de otro vendedor**.

**Hoy ese caso produce el resultado equivocado y no falla**, que es lo que lo hace urgente. El enlace es **solo un escaparate**: `GET /api/v1/hotlinks/{username}/{code}` muestra el producto y no crea nada. Quien lo recibe acaba comprando por la vía ordinaria, cuya petición **no lleva rastro del enlace** —no hay dónde ponerlo—, y la venta se atribuye por `client_sellers`: al agente principal si tiene uno, o a nadie —`VALIDAR_COMISIONES`— si tiene varios. El vendedor que repartió el enlace no cobra, y **ningún registro dice que ocurrió**.

## 3. Actores

| Actor | Qué hace |
|---|---|
| **Cliente con cuenta** | Compra para sí mismo el producto del enlace que recibió |
| **Vendedor dueño del enlace** | No interviene: la venta se le atribuye sin que haga nada |

## 4. Lo que este requerimiento añade sobre `RF-MV-002`

Produce **la misma venta** que comprar un producto para uno mismo —tipo `VENTA`, estado `PENDIENTE`, una línea con el precio y la vigencia copiados—, y se hereda de aquella spec todo lo que no se nombre aquí. Cambian **dos** cosas y solo dos:

1. **Quién es el vendedor de la línea**: el dueño del enlace, siempre (`RN-MV-025`). También cuando quien compra es cliente de otro agente, y también cuando el dueño del enlace **es** su agente — en ese caso el resultado coincide con `RF-MV-002` y el vínculo ya existía.
2. **Nace el vínculo**: si no existía, se crea la fila `HOTLINK` en `client_sellers` (`RN-SP-049`) con **esta venta** como `first_movement_id`, en la misma transacción.

**Y una tercera cosa que NO cambia, y es la que más se confunde:** la compra **no toca el agente principal**. La fila `REGISTRO` de `client_sellers` sigue siendo la misma persona y con la misma fecha. Lo único que cambia de manos es **esta venta**.

## 5. Reglas de negocio aplicables

| Regla | Qué aporta aquí |
|---|---|
| `RN-MV-025` | **La regla del requerimiento.** El `seller_id` de cada línea es quien reparte el enlace, y nace el vínculo con esta venta como primera |
| `RN-SP-049` | La forma del vínculo: `REGISTRO` uno y solo uno, `HOTLINK` los que traiga cada enlace |
| `RN-MV-001` a `RN-MV-016` | Lo que hace que esto sea una venta como las demás: no se edita ni se borra, el precio y la vigencia se congelan, el código es único |
| `RN-MV-022`, `RN-MV-026` | La cabecera lleva un sujeto, y el sujeto de una compra propia es quien compra |
| `RN-PM-021`, `RN-PM-022` | Qué producto se puede repartir por enlace y qué publica el enlace de él |
| `RN-SEG-015` | **Esta ruta exige permiso**, como toda otra: `products:buy-by-hotlink`. Ver §10 |

## 6. Datos

**De la petición**: el método de pago, y nada más. El vendedor **no viaja en el cuerpo** — sale de la ruta, y esa es la diferencia entera con `RF-MV-002`. Dejar que el cliente nombre a su vendedor convertiría la atribución en algo que se pide, no en algo que se prueba.

**De la ruta**: `username` del vendedor y `code` del producto, que son los dos datos que el enlace ya lleva y que `RF-PM-008` resuelve.

## 7. Precondiciones y postcondiciones

| | |
|---|---|
| **Pre** | Quien llama está autenticado, porta `products:buy-by-hotlink`, el enlace resuelve a un vendedor y a un producto ofrecible por enlace, y **quien compra no es el dueño del enlace** |
| **Post** | Existe una venta `PENDIENTE` con una línea cuyo `seller_id` es el dueño del enlace; existe la fila `HOTLINK` en `client_sellers`, con esta venta como `first_movement_id` si es la que la creó; el agente principal no cambió |

## 8. Flujo principal

1. Quien compra abre el enlace y pide comprar.
2. El sistema resuelve el enlace: el vendedor por su nombre de usuario y el producto por su código, **con las mismas comprobaciones que `RF-PM-008`** y el mismo `404` único.
3. Rechaza si quien compra es el dueño del enlace (`EX-003`).
4. Registra la venta: `PENDIENTE`, sujeto quien compra, una línea con el producto, su precio y su vigencia congelados, y `seller_id` el dueño del enlace.
5. Crea el vínculo `HOTLINK` si no existía, con esta venta.
6. Devuelve la venta, con la misma forma que `RF-MV-002`.

## 9. Flujos alternativos

| | Cuándo | Qué pasa |
|---|---|---|
| `FA-001` | El dueño del enlace **ya es** vendedor del cliente | La venta se atribuye igual y **no se crea nada**: el vínculo existe y conserva su `first_movement_id` original. No es un caso de error |
| `FA-002` | El dueño del enlace **es** el agente principal | Igual que `FA-001`. El resultado coincide con `RF-MV-002`, y llegar por aquí no lo hace distinto |
| `FA-003` | El cliente no tenía ningún vendedor | Nace el vínculo `HOTLINK` y **el cliente sigue sin `REGISTRO`**: comprar no registra a nadie |

## 10. Excepciones

| Código | Cuándo | Respuesta |
|---|---|---|
| `EX-001` | El enlace no resuelve, por cualquiera de sus motivos | `404`, **con el mismo mensaje único de `RF-PM-008`**: no dice cuál de los casos ocurrió, y esa uniformidad es la decisión de seguridad que se hereda |
| `EX-002` | Quien llama no porta `products:buy-by-hotlink` | `403` (`AUTH-002`) |
| `EX-003` | Quien compra **es el dueño del enlace** | `422`. Un vendedor no es su propio cliente, y admitirlo le dejaría atribuirse ventas propias |
| `EX-004` | El método de pago no existe o no se ofrece en su país | El de `RF-MV-002`, sin cambio |

## 11. Validaciones

Las de `RF-MV-002` sobre el cuerpo, más la de `EX-003`. **El enlace no se valida aparte**: resolverlo es la validación, y su fallo es `EX-001`.

## 12. Criterios de aceptación

| Código | Criterio |
|---|---|
| `CA-MV-189` | Un cliente **con agente principal** compra por el enlace de OTRO vendedor: la línea queda con el **dueño del enlace** y no con su principal |
| `CA-MV-190` | Esa misma compra deja la fila `HOTLINK` en `client_sellers`, con **esta venta** como `first_movement_id` |
| `CA-MV-191` | Y **el principal no se mueve**: la fila `REGISTRO` sigue con la misma persona y la misma fecha que antes de comprar |
| `CA-MV-192` | La venta nace **`VALIDADO`** aunque el cliente quede con dos vendedores — es la excepción a `RN-MV-034`, y el enlace es la prueba de quién la trajo |
| `CA-MV-193` | Comprar **dos veces** por el mismo enlace deja **un solo** vínculo, con el `first_movement_id` de la **primera** compra |
| `CA-MV-194` | Comprarse a uno mismo por el propio enlace responde `422` (`EX-003`) y **no deja venta ni vínculo** |
| `CA-MV-195` | Un enlace que no resuelve responde `404` con el mensaje único, **sin distinguir** si falló el vendedor o el producto |
| `CA-MV-196` | Sin `products:buy-by-hotlink`, `403`; con él y autenticado, `201` |

## 13. Casos límite

- **El dueño del enlace deja de ser vendedor entre abrir el enlace y comprar**: la resolución falla y responde `EX-001`. No se conserva ninguna atribución a medias.
- **El cliente tiene ya tres vendedores por enlace**: se le suma el cuarto. `client_sellers` no acota cuántos, y acotarlo sería inventar un límite que nadie pidió.
- **Dos compras simultáneas por el mismo enlace**: el vínculo lo decide el esquema —la pareja es la clave primaria—, no una comprobación previa. Ver `plan.md` §7.

## 14. Preguntas abiertas

Ninguna. Las dos que este requerimiento tenía abiertas al registrarse las cerró el documento del módulo: **el permiso** —`products:buy-by-hotlink`, por `RN-SEG-015`, que deja sin efecto la línea «Autenticado, sin permiso» de la ficha del 16-09-2026— y **quién escribe el vínculo**, que es `SP` por su interfaz publicada y no `MV` por su cuenta (**D-26**).
