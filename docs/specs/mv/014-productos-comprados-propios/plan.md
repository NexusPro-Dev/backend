# PLAN — `RF-MV-014` Consultar los productos comprados propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-014` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 17-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |
| Enmendado | 21-09-2026 — exige **`movements:read-own-products`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`movements:read-own-products`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo.



## 1. Enfoque

**Una consulta de lectura sobre las líneas, con el estado calculado en la sentencia.** Hereda de `RF-MV-008` todo lo que importa —el actor sale de la credencial, el alcance va **dentro** de la sentencia, sin permiso, orden fijo, total exacto— y cambia una cosa: **la fila es la línea, no la venta**, y el alcance es solo `movements.user_id = :actor` porque «propio» aquí es un papel (`spec.md` §3).

**El estado se calcula en SQL y no en Java**, por lo mismo que el papel en `RF-MV-008`: el filtro por estado tiene que aplicarse en la sentencia para que el total cuente lo que devuelve, y calcularlo en Java obligaría a traer todo y descartar. Es un `CASE` sobre cinco columnas —`movements.status`, `delivery_status`, `implementation`, `delivered_at`, `validity_days`— y el reloj, y **el «hasta» se calcula en la misma expresión** para que `VENCIDO` y el «hasta» que se devuelve no puedan discrepar.

**El reloj entra como parámetro**, no como `now()` de la base: es lo que permite probar el vencimiento sin esperar y lo que deja el borde fijado —una vigencia que vence exactamente ahora ya venció, como la membresía en `SP`—.

---

## 2. Cambios de esquema

**Ninguno.** Todo sale de `movements`, `movement_details` (con las columnas de `V16`, `RF-MV-003` · `T-01`) y `products` para el código. `ix_movements_user` (`V12`) responde al alcance; las líneas se cruzan por `idx_movement_details_movement` (`V7`).

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `MyProductsRequest` | Nuevo | Página, tamaño y estado. Sin identificador de persona |
| `application` | `MyProductResponse` | Nuevo | La fila de §4.1 |
| `application` | `PurchasedProductState` | Nuevo | Los seis estados, cerrados |
| `domain/repository` | `MovementRepository` | Gana `findMyProducts` y `countMyProducts` | Una sentencia con el `CASE`, escrita una vez para la página y el conteo |
| `domain/service` | `ListMyProductsService` | Nuevo | Resuelve el actor, valida el estado, pagina y mapea |
| `interfaces` | `MovementController` | `GET /mine/products` | Antes de cualquier `/mine/{id}`, como `/mine` antes de `/{id}` |

**`/mine/products` va declarado antes que `/mine/{id}`, y una prueba lo fija.** Hoy `/mine/{id}` existe y `products` no es un identificador válido: Spring resolvería por especificidad igual, pero el síntoma de romperlo sería un `400 VAL-001` en la ruta que se acaba de estrenar.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/mine/products` | Autenticado |

**Bajo `/movements/mine` y no bajo `/products`**: el dato es del libro —qué compré y si me lo entregaron—, no del catálogo. `PM` no sabe quién tiene qué.

### 4.1 La fila

| Campo | Tipo | Nota |
|---|---|---|
| `movementId`, `movementCode`, `movementStatus` | | De qué venta viene; para abrirla en `/mine/{id}` |
| `product` | objeto | `id`, `code`, `name` — el nombre es **el copiado en la línea** |
| `quantity` | entero | |
| `implementation` | `AUTOMATICA` \| `MANUAL` | |
| `state` | uno de los seis | Calculado |
| `purchasedAt` | instante | `occurred_at` de la venta |
| `deliveredAt` | instante **o nulo** | `types = {"string","null"}` |
| `validUntil` | instante **o nulo** | Nulo si no se entregó o si no caduca |
| `deliveryNote` | texto **o nulo** | Solo en `RETENIDO` |

### 4.2 Parámetros

| Parámetro | Nota |
|---|---|
| `page`, `size` | `Pagination` |
| `state` | Opcional; se valida contra `PurchasedProductState`, no contra una lista escrita a mano |

**Sin ordenamiento**: de la compra más reciente a la más antigua, con desempate por venta y por código de producto para que sea estable.

**Total exacto**, como en `RF-MV-008`: es el conjunto de una persona.

---

## 5. Autorización

Ninguna anotación; la ruta entra en la lista blanca de `EndpointPermissionsIT`, como `/mine`. El alcance lo pone la sentencia, y `CA-MV-099` lo prueba con el actor que **vendió** líneas a otros: no aparecen.

---

## 6. Auditoría

Ninguna (`spec.md` §7).

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`; página y conteo en la misma transacción.

---

## 8. Impacto sobre otros módulos

Ninguno. Se cruza `products` solo por el código, que es inmutable (`RN-PM-013`).

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Una tabla de «productos de la persona», escrita al confirmar y al autorizar | Copia que se desincroniza sin fallar; `spec.md` §2 |
| Calcular el estado en Java | El filtro por estado dejaría el total contando lo que no devuelve |
| `now()` de la base en el `CASE` | El vencimiento no se podría probar sin esperar, y el borde quedaría sin fijar |
| Agrupar por producto | Obliga a elegir qué vigencia manda cuando hay dos compras |
| Ponerlo bajo `/products` | El dato es del libro, no del catálogo |
| Reutilizar `/mine/{id}` y que el cliente recorra ventas | Es exactamente lo que este requerimiento existe para evitar |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que el `CASE` y el «hasta» discrepen | Una sola expresión para los dos; `CA-MV-101` comprueba los dos lados del borde |
| Que `/mine/products` lo capture `/mine/{id}` | Orden en el controlador y prueba propia |
| Que aparezca lo vendido a otros | Alcance en la sentencia; `CA-MV-099` con un actor vendedor |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Los seis estados, con el reloj fijado a los dos lados del vencimiento | Integración, sembrando líneas por SQL con `delivered_at` | Depende de la sentencia |
| Solo lo propio como sujeto; lo vendido no | Integración | |
| Filtro por estado, estado inválido `400` | Integración | |
| Orden, paginación, una fila por línea | Integración | |
| `/mine/products` no es un `{id}` | Integración | |
| `401` sin token; `200` sin permiso | Integración | |
