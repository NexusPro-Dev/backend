# PLAN — `RF-MV-010` Activar un producto comprado de implementación manual

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-010` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 28-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Es la mitad de `RF-MV-003` que se quedó sin hacer**: lo que confirmar hace con una línea automática, hecho con una manual y a petición de quien compró. La entrega de una línea —retener si baja de nivel, conceder por `MembershipGrant`, marcar entregada— **se saca de `ConfirmSaleService` a un componente propio**, `LineDelivery`, y las dos operaciones lo llaman: escrita dos veces, la regla de no bajar de nivel tendría dos ocasiones de divergir.

**El alcance va dentro de la sentencia**, como en `RF-MV-008` y `RF-MV-014`: la línea se busca con `m.user_id = :actor`, de modo que una ajena **no existe** para quien pregunta y responde `404` sin una comprobación aparte. La búsqueda **bloquea la fila** (`FOR UPDATE OF d`): una segunda activación simultánea espera, lee la línea ya entregada y responde `409`. Por eso `markDelivered` —que lanza si la línea no estaba pendiente— no puede fallar aquí con un `500`.

---

## 2. Cambios de esquema

**Ninguno de tabla**: la línea ya tiene `delivery_status`, `delivered_at` y `delivery_note` desde `V16`, y la posesión, `user_products`, desde `V38`. **`V50`** siembra **`movements:activate-own-product`** a todo rol de tipo `FUNCIONARIO`, `VENDEDOR` y `CONSUMIDOR`, como `movements:read-own-products` (`RN-SEG-015`). `movements:implement` no se siembra: desaparece.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/service` | `LineDelivery` | **Nuevo**, extraído de `ConfirmSaleService` | Entrega una línea: retiene si baja de nivel (`RN-MV-029`), si no concede y marca entregada. Devuelve el asiento de auditoría |
| `domain/service` | `ConfirmSaleService` | Usa `LineDelivery` | Conserva solo la rama manual —que no entrega— |
| `domain/repository` | `MovementRepository` / `JpaMovementRepository` | Ganan `findOwnLineForActivation` y `findMyProduct` | La primera con el actor y `FOR UPDATE OF d`; la segunda es `PRODUCTOS_PROPIOS` acotada a una línea |
| `domain/repository` | `MyProductRow` | Gana `lineId` | |
| `domain/service` | `ActivateMyProductService` | **Nuevo** | Busca, comprueba, entrega, audita, devuelve |
| `domain/service` | `ListMyProductsService` | Gana `get(lineId)` | La misma traducción a respuesta —cupón incluido— que el listado |
| `application` | `MyProductResponse` | Gana **`lineId`** | Es lo que se activa: hasta hoy la fila no decía cuál era |
| `application` | `PurchasedProductState` | `PENDIENTE_AUTORIZACION` → **`PENDIENTE_ACTIVACION`** | Incompatible y declarado |
| `interfaces` | `MovementController` | `POST /mine/products/{lineId}/activation` | Declarado **antes** de `/mine/{id}` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/mine/products/{lineId}/activation` | `movements:activate-own-product` |

**Sin cuerpo.** El sustantivo en la ruta, como `/confirmation` o `/withdrawal-approval`: activar es un hecho sobre la línea, no un formulario.

| Código | Cuándo |
|---|---|
| `200` | Activada —o retenida por bajar de nivel—, con el producto comprado (`MyProduct`) |
| `400` | Identificador malformado |
| `401` / `403` | Sin token / sin `movements:activate-own-product` |
| `404` | No existe **o no es de una compra suya** (`EX-001`) |
| `409` | La venta no está confirmada (`EX-002`), la línea no es manual (`EX-003`) o ya no está pendiente (`EX-004`), con el estado en el mensaje |

**`200` y no `201`**: no nace ningún recurso con dirección propia; la línea cambia.

**Y cambian dos cosas de `GET /mine/products`**: la fila trae `lineId` —compatible— y el estado `PENDIENTE_AUTORIZACION` pasa a llamarse **`PENDIENTE_ACTIVACION`** —incompatible, declarado en la prosa de la operación—.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:activate-own-product')")` abre la ruta; **el alcance lo pone la sentencia**, y ningún permiso lo ensancha. `CA-MV-278` se ejercita con el superadministrador, y `CA-MV-283` con `movements:read-own-products` puesto y el de activar quitado. Entra en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `movement_details`, `UPDATE`, de `PENDIENTE` al estado que quedó, con el asiento que `LineDelivery` devuelve —el mismo que confirmar escribe por línea—. `MembershipGrant` audita la concesión por su lado, como en `RF-MV-003`. Los `409` los audita el manejador global como `BUSINESS_RULE`.

---

## 7. Transaccionalidad

`@Transactional`: el bloqueo, la posesión, el nivel y la marca de la línea en una transacción. Si conceder falla, la línea sigue pendiente.

---

## 8. Impacto sobre otros módulos

**Ninguno de código**: `MembershipGrant` y `CurrentMembershipLookup` de `SP` se usan como ya los usa `RF-MV-003`, y el cupón se pide a `ProductCatalog` como en `RF-MV-014`. **Documental**: `requirements/pm.md` v0.44.0 (`RN-PM-020`) y `security.md` (el permiso, al sembrarse).

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Activar la **venta** entera | Una venta puede llevar varias líneas manuales, y la vigencia de cada una es de quien la use |
| `403` para la línea ajena | Confirmaría que el identificador existe (`RF-MV-008`, `EX-002`) |
| Comprobar el dueño en Java tras leer la línea | Dos pasos donde basta uno, y el primero ya habría leído lo ajeno |
| Reusar `markDelivered` sin bloquear la fila | Dos activaciones simultáneas: la segunda lanzaría `IllegalStateException` y respondería `500` en vez de `409` |
| Copiar `entregar` en el servicio nuevo | La regla de no bajar de nivel quedaría escrita dos veces |
| Mantener `PENDIENTE_AUTORIZACION` | El nombre diría que alguien autoriza, y nadie lo hace (decisión del responsable, 28-09-2026) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| El frontend filtra o pinta por `PENDIENTE_AUTORIZACION` | Declarado en la prosa de la operación y en el control de cambios; el contrato regenerado lo enumera |
| Mover `entregar` rompe la confirmación | `ConfirmSaleIT` sigue en verde sin tocarse |

---

## 11. Estrategia de prueba

Integración, **`ActivateMyProductIT`**: `CA-MV-275` a `CA-MV-283`. La venta se registra y se confirma por sus rutas; `CA-MV-276` envejece la confirmación en la base antes de activar. `MyProductsIT` cambia `CA-MV-103` al nuevo nombre y comprueba `lineId`. `PermissionIT` cuenta uno más.
