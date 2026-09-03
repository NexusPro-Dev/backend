# PLAN — `RF-MV-002` Comprar un producto para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-002` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 02-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 02-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

Este plan **hereda entero** el de [`RF-MV-001`](../001-registrar-venta/plan.md) —esquema, lecturas cruzadas, código de comprobante, auditoría y transaccionalidad— y decide una sola cosa: **cuánto de esa maquinaria se comparte y dónde se separa**.

---

## 1. Enfoque

No hay esquema nuevo, no hay interfaz nueva y no hay regla nueva. Lo único que hay es **una segunda puerta** a la misma operación, con otro modelo de seguridad y dos campos menos.

La decisión de este plan es que **el caso de uso es uno solo**, y que lo que se duplica es exclusivamente la entrada.

## 2. Cambios de esquema

**Ninguno.** `V53` ya creó todo lo que esta operación necesita, y `V51` sembró los permisos — que esta operación no exige: la compra propia no lleva permiso.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/service` | `RegisterSaleService` | **Modificado** | Gana un modo en el que el cliente **es** el actor y la fecha del hecho es siempre ahora |
| `application` | `PurchaseRequest` | Nuevo | Método de pago y líneas. **Sin cliente y sin fecha** |
| `application` | `PurchaseResponse` | Nuevo | La respuesta de venta **sin el vendedor** |
| `interfaces` | `MovementController` | **Modificado** | Gana `POST /api/v1/movements/mine` |

### 3.1 Un solo caso de uso, y no dos

Las dos operaciones hacen **las mismas nueve verificaciones** sobre los mismos datos. Duplicar el servicio duplicaría esas nueve, y la copia que se quedara atrás **no fallaría**: seguiría registrando ventas, solo que sin comprobar algo — que es la clase de defecto que solo aparece cuando alguien lo aprovecha.

Lo que se separa es **lo que de verdad es distinto**: quién es el cliente y de dónde sale la fecha. Eso se resuelve **antes** de entrar al caso de uso, en la capa que conoce al actor, de modo que el servicio recibe siempre un cliente y una fecha ya decididos y **no tiene ninguna rama** que pregunte por dónde entró la petición.

**El resultado es lo que `CA-MV-026` exige**: la venta creada por las dos puertas es indistinguible, porque a partir del segundo paso **es literalmente el mismo código**.

### 3.2 La respuesta es otra clase, y no un campo nulo

`RF-MV-001` devuelve el vendedor y esta operación no (`spec.md` §4.3). Se resuelve con **dos representaciones de salida** y no con un campo que a veces viene vacío.

Un campo opcional obligaría a cada consumidor a preguntarse **por qué** falta —¿no hay vendedor? ¿no se pudo resolver?— cuando la respuesta es «porque a ti no se te enseña». Dos formas distintas no dejan esa pregunta abierta.

## 4. Contrato de API

`POST /api/v1/movements/mine` · `201 Created`, con `Location`.

**El sufijo `mine` y no `me`**, aunque `SP` use `/users/me`: allí el recurso **es** la persona; aquí el recurso son movimientos y lo que se acota es **cuáles**. `RF-MV-008` colgará del mismo sitio con `GET`, de modo que la ruta significará siempre lo mismo: *mis movimientos*.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-002` a `VAL-006`. **`VAL-001` y `VAL-007` no existen aquí**: los campos que validaban no se admiten |
| `401` | Sin autenticar. **No hay `403`**: no se exige ningún permiso |
| `409` | Las mismas ocho de `RF-MV-001`, con `EX-002` redactada en primera persona |
| `422` | `EX-011` y el método inexistente. **`EX-001` no existe**: el actor existe por definición |

!!! warning "Un cliente que envíe `clientId` no recibe un error: recibe una venta a su propio nombre"

    El campo no está en el contrato, de modo que llega y se ignora. **Es lo correcto y conviene que esté escrito**, porque la alternativa —rechazar la petición por traer un campo de más— convertiría un cliente desactualizado en un cliente roto.

    Lo que **no** puede pasar es que se use, y eso no depende de una comprobación sino de que el cliente se resuelve del actor **antes** de mirar el cuerpo (§3.1).

## 5. Autorización

**Ningún permiso.** Solo autenticación, como `RF-SP-039` y `RF-PM-007`.

**El endpoint no distingue roles**, y no es un olvido: quien no tiene membresía no tiene oferta, de modo que la única forma de que un funcionario compre por aquí es que alguien le haya dado un nivel — y entonces comprar sería correcto (`spec.md` `FA-006`).

## 6. Auditoría

La misma de `RF-MV-001`, **con el vendedor dentro**. El actor de la auditoría es el propio cliente, que aquí es también quien teclea: es el único caso del módulo en que las dos personas coinciden.

## 7. Transaccionalidad

La de `RF-MV-001`. Sin cambios.

## 8. Impacto sobre otros módulos

**Ninguno.** Las interfaces de `PM` y `SP` que hacen falta las publica `RF-MV-001`.

**En la documentación, ninguna enmienda propia**: las tres de `RF-MV-001` §8 cubren a los dos requerimientos, y se aplican una sola vez.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Un solo endpoint** con el cliente opcional, que si falta es el actor | Un mismo camino con dos modelos de seguridad. Quien tenga `movements:create` podría comprar a nombre de otro **por el endpoint de comprar para uno mismo**, y quien no lo tenga vería el mismo recurso comportarse distinto |
| **Duplicar el caso de uso** | Nueve verificaciones por duplicado, y la copia atrasada no falla: sigue vendiendo sin comprobar algo |
| Devolver el vendedor **como campo nulo** | Deja abierta la pregunta de por qué falta. Ver §3.2 |
| Rechazar la petición si trae `clientId` | Convierte un cliente desactualizado en un cliente roto, sin ganar nada: el campo ya se ignora |
| Exigir el rol `CONSUMIDOR` | Comprobación redundante con la oferta y **más frágil**: el día que un funcionario tenga membresía, la comprobación de rol le impediría comprar lo que el catálogo le ofrece |
| Admitir la fecha del hecho, como en `RF-MV-001` | Dejaría que quien compra elija el día de su venta — y con él, el periodo en que se comisiona (`spec.md` §4.2) |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El cliente se lea del cuerpo en lugar del actor** | Es el fallo grave de este requerimiento: permitiría comprar a nombre de otro. Lo cubre `CA-MV-021`, que envía un `clientId` ajeno y comprueba que la venta queda **a nombre de quien pidió** |
| 2 | Que las dos puertas **diverjan** con el tiempo | `CA-MV-026` compara las dos ventas creadas y exige que sean indistinguibles. Es la prueba que fallaría el día que alguien añada una rama a una sola de las dos |
| 3 | Una cuenta en `FTD_PENDIENTE` **pueda comprar** | Es el rechazo más frecuente de esta operación (`spec.md` §5). Lo cubre `CA-MV-023`, y el mensaje tiene que decirle qué le falta |

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Compra sin permisos | Integración | `CA-MV-019`: un actor **sin ningún permiso** obtiene `201` |
| Atribución invisible | Integración | `CA-MV-020`: la auditoría lleva el vendedor y **la respuesta no** |
| **`clientId` ajeno en el cuerpo** | Integración | `CA-MV-021`: la venta queda a nombre del actor — riesgo 1 |
| Sin fecha del hecho | Integración | `CA-MV-022` |
| Cuenta retenida | Integración | `CA-MV-023` |
| Oferta y nivel | Integración | `CA-MV-024` |
| Funcionario sin membresía | Integración | `CA-MV-025`: rechazado **por la oferta** |
| **Las dos puertas producen lo mismo** | Integración | `CA-MV-026`: se registra una venta por cada camino y se comparan campo a campo, salvo identificador, código y fechas |

**No se repiten las pruebas de composición** —productos repetidos, dos upgrades, monedas distintas—. Las cubre `RF-MV-001` sobre el mismo caso de uso, y duplicarlas aquí solo probaría que el enrutado funciona. Lo que sí se prueba es **todo lo que esta puerta hace distinto**, que son las seis primeras filas.
