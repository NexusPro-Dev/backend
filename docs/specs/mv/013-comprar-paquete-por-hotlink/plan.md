# PLAN — `RF-MV-013` Comprar un paquete por el hotlink de un vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-013` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 16-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 16-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye** lo que `spec.md` decidió: componentes, contrato, transacción, riesgos y pruebas. Las decisiones de negocio no se revisan aquí.

!!! abstract "Este plan hereda de `RF-MV-012` y decide dos cosas"

    El caso de uso de la compra —resolver el paquete, validar, copiar, congelar, sumar y registrar— lo construye [`RF-MV-012`](../012-comprar-paquete/plan.md) y **no se duplica**. Aquí se decide **cómo se resuelve el enlace** y **quién escribe el vínculo**, que es la primera escritura de `MV` hacia una tabla de `SP`.

---

## 1. Enfoque

**Una puerta más sobre el mismo caso de uso.** Lo que cambia respecto de `RF-MV-012` ocurre **antes** de entrar —resolver el enlace, elegir el vendedor— y **después** de salir —el vínculo—. En medio es literalmente el mismo código, que es lo que hace cierto `CA-MV-066`.

**Y hay una decisión que no es de este requerimiento pero lo atraviesa**: `MV` tiene que **escribir** en `client_sellers`, que es de `SP`. Es la misma cuestión que **D-26** abrió para conceder la membresía al confirmar, con la diferencia de que aquí **ya hay precedente**: `RF-MV-011` la resuelve para la compra de un producto por hotlink, y este requerimiento **usa lo que aquel publique**, no inventa una segunda forma.

---

## 2. Cambios de esquema

**Ninguno.** `client_sellers` la crea `RF-MV-011` con su migración; las columnas de la venta las dejó `V14`.

**Si este requerimiento se construyera antes que `RF-MV-011`**, la tabla vendría con él: es el único caso en que este plan tocaría el esquema, y lo haría con la forma que `requirements/sp.md` §10 ya declara. **No se planifica ese orden**: `RF-MV-011` está registrado antes y es el más simple de los dos.

---

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `products/application` | `PackageCatalog` | **Modificado** | Gana la resolución **por nombre de usuario y código**, con las reglas de `RF-PM-026` |
| `movements/domain/service` | `BuyPackageService` | **Modificado** | Admite que el vendedor **venga dado** en lugar de deducirse del comprador |
| `movements/domain/service` | `BuyPackageByHotlinkService` | Nuevo | Resuelve el enlace, rechaza la autocompra, delega y **crea el vínculo** |
| `movements/interfaces` | `HotlinkPurchaseController` | **Modificado o nuevo** | `POST /api/v1/hotlinks/{username}/packages/{code}/purchases` |
| `system/users/application` | El puerto del vínculo | **Reutilizado de `RF-MV-011`** | `SP` publica «vincula a este cliente con este vendedor por esta venta» |

### 3.1 El vendedor se inyecta, no se deduce

`BuyPackageService` deduce hoy el vendedor de quien compra (`RN-MV-003`). Aquí **viene dado**, y la diferencia se resuelve **pasándolo** y no con una bandera: el servicio recibe quién vende, y quien lo llama decide si es el superior del comprador o el dueño del enlace.

**Es la forma que `RF-MV-002` eligió para el cliente** —resolver fuera lo que de verdad cambia, y entrar al caso de uso con todo resuelto— y por el mismo motivo: una bandera obligaría al servicio a saber por dónde entró la petición, que es exactamente lo que `CA-MV-066` exige que no ocurra.

### 3.2 El vínculo lo escribe `SP`, y `MV` lo pide

**`MV` no escribe en `client_sellers` directamente.** Pide a `SP` que lo haga, por la interfaz que `RF-MV-011` publique, con el mismo argumento de **D-26**: la regla de qué significa vincular —que no cambia el principal, que no se duplica, que guarda el movimiento que lo originó— vive en `SP` con sus pruebas.

**Y es idempotente por el esquema y no por una comprobación previa**: la pareja es la clave primaria de `client_sellers`, de modo que el segundo intento no crea nada. Comprobar antes de insertar dejaría la ventana entre la comprobación y la escritura, que es justo lo que `FA-002` y el caso límite de las dos compras simultáneas describen.

---

## 4. Contrato de API

`POST /api/v1/hotlinks/{username}/packages/{code}/purchases` · `201 Created`.

**La ruta es la del hotlink del paquete más `/purchases`**, que es la forma que `RF-MV-011` fija para el producto. El cuerpo y la respuesta son los de `RF-MV-012`.

| Estado | Cuándo |
|---|---|
| `401` | Sin autenticar. **Es la diferencia con el hotlink que lo publica**, que es público: verlo no exige cuenta, comprarlo sí |
| `404` | Todo lo que no procede del enlace o del catálogo: no existe, no se publica por hotlink, no se ofrece hoy, no le corresponde (`spec.md` §4.1) |
| `409` | Lo que es de la operación del propio actor: producto caído, upgrade que baja, cuenta que no opera, método que no cuadra, **y la autocompra** (`EX-009`) |
| `422` | El método de pago que no resuelve |

**`EX-009` es `409` y no `404`**, aunque venga por el camino del enlace: quien se compra a sí mismo **sabe que el enlace existe** —es suyo—, de modo que el `404` no protegería nada y solo escondería el motivo.

---

## 5. Autorización

**Autenticado, sin permiso**, como `RF-MV-012`. El vendedor no autoriza nada: su enlace es público y su papel es **cobrar**, no consentir.

---

## 6. Auditoría

La de `RF-MV-012` —la venta con su paquete y sus líneas—, **y la del vínculo la emite `SP`** cuando lo crea, con su propio módulo y su propia entidad. Dos registros para dos hechos.

---

## 7. Transaccionalidad

**Una transacción para las dos cosas.** La venta y el vínculo ocurren juntos o no ocurren: un vínculo sin la venta que lo originó apuntaría a nada, y una venta por hotlink sin vínculo dejaría al cliente sin el vendedor que lo trajo.

**El vínculo se escribe después de la venta** porque necesita su identificador (`spec.md` §8).

---

## 8. Impacto sobre otros módulos

| Módulo | Qué se le pide | Qué NO cambia |
|---|---|---|
| `PM` | Que `PackageCatalog` resuelva **también por usuario y código** | Las reglas del hotlink son las de `RF-PM-026`, sin tocar |
| `SP` | **Escribir el vínculo**, por la interfaz que `RF-MV-011` publica | `RN-SP-021` intacta: el agente **principal** no se toca |
| `CM` | Nada | — |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| **Un servicio propio que repita la compra** | Duplicaría diez verificaciones y rompería `CA-MV-066` en cuanto una de las dos copias se quedara atrás |
| **Una bandera «por hotlink» en `BuyPackageService`** | El servicio tendría que saber por dónde entró. Ver §3.1 |
| **Que `MV` escriba `client_sellers`** | Contradice **D-26** y el precedente de `RF-MV-011`. La regla del vínculo es de `SP` |
| **Comprobar si el vínculo existe antes de crearlo** | Deja una ventana entre la comprobación y la escritura. La clave primaria ya lo resuelve |
| **Admitir también el identificador del paquete en esta ruta** | Dos caminos con dos modelos de atribución para la misma venta (`spec.md` §6.1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Que la venta se atribuya al agente en lugar de al dueño del enlace** — el fallo grave | `CA-MV-061`: se compra por el enlace de un tercero **teniendo agente**, y se comprueba la atribución en las líneas y en la base |
| Que el `404` uniforme se rompa y filtre el catálogo ajeno | `CA-MV-064`: los tres casos responden lo mismo, comprobado en la misma prueba |
| Que el vínculo se duplique | La clave primaria, y `CA-MV-063` comprando **dos veces** |
| Que la compra mueva el agente principal | `CA-MV-062`, que mira `user_supervisors` después de comprar |
| Que este requerimiento se construya antes que `RF-MV-011` y se quede sin tabla | §2 lo declara: entonces la trae este, con la forma de `requirements/sp.md` §10 |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| La atribución al dueño del enlace, **con el comprador teniendo otro agente** | Integración | Es el criterio que sostiene el requerimiento |
| Que el principal no cambia | Integración | Se ve mirando `user_supervisors`, no la respuesta |
| El vínculo: nace una vez, con su venta y su origen | Integración | `CA-MV-063`, comprando dos veces |
| Los tres casos del `404` uniforme | Integración | Solo es observable por HTTP |
| La autocompra rechazada | Integración | `CA-MV-065` |
| Que un producto caído **sí** dice cuál es | Integración | `CA-MV-067`: la uniformidad no alcanza a lo que es del actor |
| Que la venta es indistinguible de la de `RF-MV-012` | Integración | `CA-MV-066`, comparando las dos ventas registradas |

**No se repiten las pruebas del paquete** —el precio, las rebajas, el rechazo por producto caído, la vigencia—: las cubre `RF-MV-012` sobre el mismo código. Lo que aquí se prueba es **lo que esta puerta añade**.
