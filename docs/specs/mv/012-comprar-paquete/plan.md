# PLAN — `RF-MV-012` Comprar un paquete para uno mismo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-012` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 16-09-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 16-09-2026 |
| Enmendado | 21-09-2026 — exige **`packages:buy`** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` |
| Reabierto el | 17-09-2026 — **el paquete entra por su código**: la ruta pasa a `/packages/{code}/purchases` y `PackageCatalog` resuelve por código, ver §3, §4 y §8 (Art. I.7) |
| Reaprobado el | 17-09-2026 — Responsable del proyecto |

!!! info "Qué va en este documento"

    **Cómo se construye** lo que `spec.md` decidió: componentes, contrato, transacción, riesgos y pruebas. Las decisiones de negocio no se revisan aquí.

!!! abstract "Este plan hereda de `RF-MV-001` y `RF-MV-002`, y decide tres cosas nuevas"

    El registro de la venta —el agregado, el código, la auditoría, el reintento del comprobante— ya está construido y no se toca. Lo que este plan decide es: **cómo le pregunta `MV` a `PM` por un paquete**, **quién calcula la rebaja de cada línea**, y **por dónde entra la petición**.

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`packages:buy`** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo.



## 1. Enfoque

**Una lectura nueva y un caso de uso nuevo; ni una tabla.** El esquema que esta operación necesita lo dejó puesto `V14` el mismo día en que se decidió la compra: `movements.package_id`, las cuatro columnas de la línea y `movement_detail_discounts`. Este requerimiento es **el primero que escribe en ellas**.

**Lo que hay que resolver es la frontera con `PM`.** `MV` no puede leer `product_packages` —la regla de ArchUnit que `RF-MV-001` · `T-19` fijó lo prohíbe, y es la que sostiene que «pregunta la oferta, no la recalcules» no sea una frase de un documento—. De modo que `PM` tiene que **publicar** lo que hace falta para vender un paquete, igual que publica `ProductCatalog.saleViewOf` para vender un producto.

---

## 2. Cambios de esquema

**Ninguno.** Es la primera vez en este módulo que un requerimiento se construye sobre columnas que ya existen, y conviene decir por qué: `V14` se escribió **antes** que esta tripleta, el 16-09-2026, cuando el responsable del proyecto decidió la forma del descuento y del paquete. No hay migración, no hay número que reservar y no hay orden de despliegue que coordinar.

---

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `products/application` | `PackageCatalog` | **Nuevo, dentro de `PM`** | La lectura publicada: qué contiene un paquete y si hoy se le puede vender a alguien |
| `products/domain/repository` | `PublishedPackageCatalog` | **Nuevo, dentro de `PM`** | La implementa, con la misma sentencia que ya resuelve el detalle del paquete |
| `movements/application` | `BuyPackageRequest` | Nuevo | **Solo el método de pago**: ni paquete en el cuerpo —va en la ruta—, ni cantidad, ni fecha |
| `movements/domain/service` | `BuyPackageService` | Nuevo | El caso de uso: resuelve, valida, copia, congela y registra |
| `movements/interfaces` | `PackagePurchaseController` | Nuevo | `POST /api/v1/packages/{code}/purchases`, **sin permiso** |
| `movements/domain/models` | `MovementLine`, `LineDiscount` | Sin cambio | Ya saben construir una línea con rebajas: los escribió `V14` · `T-32` |

### 3.1 `PM` publica el paquete, y publica lo que ya sabe

`PackageCatalog` devuelve, para un paquete —**por su código**, comparado sin distinguir mayúsculas como el hotlink (`RF-PM-026`)— y una persona: **si hoy se le puede ofrecer** —con su motivo cuando no— y **sus productos**, cada uno con lo que `SaleView` ya trae (código, nombre, descripción, precio, moneda, vigencia, si es upgrade y a qué nivel lleva) **más el descuento que el paquete le declara**: su tipo y su valor.

**Es la misma decisión que `RF-MV-001` tomó con la oferta**: `MV` **pregunta** y no recalcula. Quién puede ver un paquete lo decide `RN-PM-039`, `RN-PM-044` y `RN-PM-047`, y esas tres reglas viven en `PM` con sus pruebas; duplicar el predicado aquí significaría que el día que `PM` lo cambie, la venta seguiría con el viejo **sin que nada falle**.

**El motivo del rechazo viaja, y no solo el «no».** El catálogo ya calcula `offerable` con su causa para el detalle del paquete (`RF-PM-019`); reutilizarla es lo que permite que `EX-002` diga **qué** falla —vencido, inactivo, sin productos— en lugar de un «no se puede» que obligue al cliente a adivinar.

### 3.2 Quién calcula la rebaja, y por qué se comprueba con una prueba

**`PM` publica la rebaja declarada —tipo y valor— y `MV` la congela en dinero** con `LineDiscount`, que aplica la fórmula de `RN-PM-036`: se redondea **la rebaja** y no el resultado, a los decimales de la moneda y a la mitad hacia arriba.

**Eso es la misma cuenta escrita en dos sitios, y se acepta a conciencia** porque la alternativa es peor: que `PM` devuelva el importe ya rebajado obligaría a `MV` a **restar para saber cuánto se rebajó**, y a guardar como «descuento» un número que nadie declaró — con el redondeo apareciendo dos veces y la posibilidad de que la resta no cuadre con la declaración.

**Lo que impide que las dos cuentas diverjan no es la disciplina, es `CA-MV-050`**: una prueba de integración compara **lo que esta operación cobra** con **el `price` que el catálogo publica** para el mismo paquete. Si alguien cambia el redondeo en un lado, esa prueba falla.

### 3.3 Un caso de uso propio, y no un modo de `RegisterSaleService`

`RF-MV-002` argumentó que registrar y comprar comparten servicio porque hacen **las mismas nueve verificaciones sobre los mismos datos** (`plan.md` §3.1). **Aquí no se cumple la premisa**: lo que se resuelve es un paquete y no una lista de líneas, las comprobaciones de composición no aplican (`spec.md` §5), y aparece una que no existía —que el paquete se pueda ofrecer—.

Lo que **sí** se comparte es todo lo de después: copiar, congelar, sumar, emitir el código, guardar y auditar. Eso vive en `Movement`, `MovementLine` y el repositorio, y **se reutiliza tal cual**. `BuyPackageService` arma las líneas y llama a lo mismo que llama `RegisterSaleService`, que es lo que hace cierto `CA-MV-059`: la venta resultante es indistinguible.

---

## 4. Contrato de API

`POST /api/v1/packages/{code}/purchases` · `201 Created`, con `Location` a `/api/v1/movements/mine/{id}`.

**El paquete va por su código y no por su identificador** (desde el 17-09-2026, `spec.md` v0.2.0 §6.1). Las demás rutas de `/api/v1/packages` son de administración y usan el identificador; esta es la del cliente, y el cliente tiene el código —es lo que la oferta y el hotlink publican— y no el identificador. Un código que no resuelve es `EX-001`, y como no hay forma bien formada de no enviarlo, `VAL-001` deja de alcanzarse por HTTP.

**Cuerpo**: `paymentMethodId`, y nada más. **Respuesta**: la venta, con su paquete, sus líneas y sus descuentos explicados (`spec.md` §6.2).

| Estado | Cuándo |
|---|---|
| `401` | Sin autenticar |
| `409` | Lo que solo se sabe **después de resolver**: el paquete no se ofrece hoy (`EX-002`), no le corresponde (`EX-003`), un producto no procede (`EX-004`), el upgrade baja (`EX-005`), la cuenta no opera (`EX-006`), el método no cuadra con el importe (`EX-007`) o está inactivo (`EX-008`) |
| `422` | `EX-001` y el método de pago inexistente: **una referencia bien formada que no resuelve** |

**El recurso es el paquete y no el movimiento**, al revés que `RF-MV-002` —que entra por `POST /api/v1/movements/mine`—. Se sigue el precedente de `RF-MV-011`, que compra por `POST /api/v1/hotlinks/{username}/{code}/purchases`: **lo que se compra manda sobre lo que se produce**, porque es lo que el cliente tiene delante cuando pulsa. Lo que se produce —una venta— viaja en la respuesta y en el `Location`.

!!! warning "Este endpoint cuelga de `/packages` y NO exige `packages:read`"

    Las demás rutas de `/api/v1/packages` son de administración y exigen permiso. Esta **no exige ninguno**: es una compra propia, como `RF-MV-002` y `RF-PM-007`.

    **Compartir prefijo con rutas protegidas es exactamente donde se cuela un permiso que sobra o que falta**, de modo que la ausencia se declara en la lista blanca de `EndpointPermissionsIT` y no se deja a la interpretación de quien lea el controlador.

---

## 5. Autorización

**Autenticado, sin permiso.** El alcance lo da la credencial: no hay forma de comprar a nombre de otro porque **el sujeto no viaja en la petición** (`RF-MV-002` · §4.1, que vale aquí).

---

## 6. Auditoría

Registro de **cambios**, acción de creación, con la instantánea completa: el sujeto, **el paquete**, el método, los tres importes, el código y **cada línea con lo copiado, su vendedor y sus rebajas**.

**La instantánea la produce `Movement` y no este caso de uso**, de modo que no hay nada que decidir aquí: `V14` · `T-32` ya puso el paquete en la cabecera y las rebajas en cada línea.

---

## 7. Transaccionalidad

**Una transacción, como `RF-MV-001`.** Resolver el paquete, validar, escribir la cabecera, las líneas y sus rebajas, y auditar, ocurren juntos o no ocurren.

**No hay bloqueo sobre el paquete**, y es deliberado: que alguien corrija un descuento mientras esta venta se registra produce **una venta con el descuento de antes o con el de después**, y las dos son correctas — lo que se cobró queda congelado en cualquier caso. Bloquear daría la impresión de que existe un instante canónico que no existe.

---

## 8. Impacto sobre otros módulos

| Módulo | Qué se le pide | Qué NO cambia |
|---|---|---|
| `PM` | **Publicar `PackageCatalog`**: una interfaz nueva en `application`, con su implementación | Ninguna regla de paquetes, ninguna tabla, ningún endpoint suyo. Su suite debe seguir en verde **sin cambios** |
| `SP` | Nada nuevo: el sujeto y su estado ya los publica `ClientCatalog` | — |
| `CM` | Nada. **Sigue sin consumir a `MV`** | La decisión de sobre qué importe comisiona sigue abierta y esta venta guarda **las dos cifras** |

**La regla de ArchUnit no se relaja**: `MV` sigue sin poder depender de `products..domain..`, y lo que consume es la interfaz de `application`, como con `ProductCatalog`.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| **Que `MV` lea `product_packages` directamente** | Rompe la frontera que `T-19` fija por prueba, y duplicaría el predicado de ofrecibilidad — que cambiaría en `PM` sin que la venta se entere |
| **Que `PM` devuelva el precio ya rebajado de cada producto** | Obligaría a `MV` a restar para saber el descuento y a guardar como declaración un número derivado. Ver §3.2 |
| **Que la compra entre por `POST /movements/mine` con un `packageId` en el cuerpo** | Un endpoint que admite «productos o un paquete» tiene dos formas de petición y dos caminos de validación; separarlos es lo que hace que ninguno crezca hacia el otro |
| **Reutilizar `RegisterSaleService` con un modo «paquete»** | La premisa de `RF-MV-002` §3.1 no se cumple: no son las mismas verificaciones. Ver §3.3 |
| **Copiar el precio del paquete en la venta** | Dos números para lo mismo. El importe a pagar **es** la suma de las líneas, y `CA-MV-050` lo ata al catálogo |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Que lo cobrado no coincida con lo publicado** — el riesgo que importa | `CA-MV-050`: la prueba compara el importe a pagar con el `price` del catálogo para el mismo paquete |
| Que el redondeo diverja entre `PM` y `MV` | El mismo criterio, y la misma prueba: se redondea la rebaja, no el resultado (§3.2) |
| Que se venda un paquete que no se podía ofrecer | El predicado lo resuelve `PM` (§3.1), y `CA-MV-055` y `CA-MV-056` lo ejercitan con el vencido y con el que no corresponde |
| Que un producto caído deje pasar una venta parcial | `CA-MV-054`: con un producto inactivo, **nada se registra** — se comprueba que no queda ni la cabecera |
| Que el endpoint herede el permiso de sus vecinos de `/packages` | Lista blanca explícita en `EndpointPermissionsIT`, y `CA-MV-049` compra **sin ningún permiso** |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| El camino feliz: una línea por producto, con sus rebajas | Integración | Cruza `PM`, `MV` y el esquema |
| **El importe cobrado contra el `price` publicado** | Integración | Es el criterio que sostiene la operación, y solo se ve comparando las dos respuestas |
| Las tres cifras de la cabecera | Integración | `CA-MV-051` |
| El paquete inactivo, vencido y el que no corresponde | Integración | Son tres rechazos distintos con tres mensajes distintos |
| Un producto del paquete inactivo → **nada se registra** | Integración | La ausencia de filas solo se comprueba contra la base |
| El upgrade que baja y el que renueva | Integración | `CA-MV-057`, con el precedente de `CA-MV-048` |
| Que corregir el descuento después no cambia lo cobrado | Integración | `CA-MV-053`: la copia solo se verifica cambiando el original |
| El cálculo de la rebaja por unidad | Unitaria | Ya cubierto por las pruebas de `LineDiscount` (`V14` · `T-32`); aquí no se repite |
| Que la ruta no exige permiso | Integración | Lista blanca, y compra con un actor sin permisos |

**No hay prueba concurrente**, y su ausencia es una afirmación: ninguna regla de esta operación puede burlarse con dos peticiones simultáneas, porque **ninguna venta concede nada al registrarse** (`RN-MV-004`). El conflicto de dos upgrades iguales aparece **al confirmar**, y es de `RF-MV-003`.
