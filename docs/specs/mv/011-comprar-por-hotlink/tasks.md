# TASKS — `RF-MV-011` Comprar un producto por el hotlink de un vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-011` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md) |
| `plan.md` aprobado el | 24-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/comprar-por-el-hotlink` |

!!! info "Qué va en este documento"

    **En qué pasos, en qué orden y cómo se verifica cada uno.**

    **Prueba de pertenencia:** si no puede marcarse como hecho, no es una tarea.

    **Es la fuente de verdad de las tareas.** El Issue de GitHub coordina y enlaza aquí; no la sustituye ni la duplica.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V41`**: siembra `products:buy-by-hotlink` y lo reparte **por tipo de rol**, como `V31` | — | El catálogo sube en uno; un rol de consumidor nuevo lo porta sin tocar la migración | **Pendiente** |
| `T-02` | `ClientSellerRepository` gana `attachByHotlink`, con `ON CONFLICT DO NOTHING` y **sin lectura previa** | — | Insertar dos veces la misma pareja no falla y deja **una** fila | **Pendiente** |
| `T-03` | `ClientSellerBond`: el puerto publicado de `SP`, con su orden plana —cliente, vendedor, venta— | `T-02` | No cruza ninguna entidad: `SP` no sabe qué es una venta, guarda su identificador | **Pendiente** |
| `T-04` | `PublishedClientSellerBond`: `MANDATORY`, escribe el vínculo y **audita solo si nació** | `T-03` | Sin transacción del que llama, falla antes de tocar nada. Repetir no deja asiento | **Pendiente** |
| `T-05` | `BuyByHotlinkService`: resuelve el enlace con lo de `RF-PM-008`, rechaza `EX-003`, registra la venta con `SaleAttribution.delEnlace` y pide el vínculo | `T-04` | `CA-MV-189`, `CA-MV-190`, `CA-MV-192` | **Pendiente** |
| `T-06` | `HotlinkPurchaseController` con `POST /api/v1/hotlinks/{username}/{code}/purchases` y `products:buy-by-hotlink` | `T-01`, `T-05` | `CA-MV-196`; el contrato lo publica con su `x-required-permission` | **Pendiente** |
| `T-07` | El `404` único de `RF-PM-008`, sin distinguir qué falló | `T-05` | `CA-MV-195`: vendedor inexistente y producto inexistente devuelven **el mismo cuerpo** | **Pendiente** |
| `T-08` | Prueba de que **el principal no se mueve** | `T-05` | `CA-MV-191`, mirando la fila `REGISTRO` antes y después | **Pendiente** |
| `T-09` | Prueba de idempotencia: dos compras por el mismo enlace | `T-05` | `CA-MV-193`: un vínculo, y el `first_movement_id` de la **primera** | **Pendiente** |
| `T-10` | Prueba de concurrencia: dos compras simultáneas por el mismo enlace | `T-09` | Ninguna `500`, un solo vínculo. Lo serializa el esquema y no una comprobación | **Pendiente** |
| `T-11` | Prueba del rechazo a sí mismo | `T-05` | `CA-MV-194`: `422`, y **cero** filas nuevas en `movements` y en `client_sellers` | **Pendiente** |
| `T-12` | Las enmiendas de `plan.md` §8, **en el mismo pase** | `T-06` | `requirements/mv.md`, `security.md`, `architecture.md` §15.2.1 y la matriz, cada uno con su fila de control de cambios | **Pendiente** |

## 2. Orden de ejecución

`T-01` y `T-02` son independientes y van primero. El puerto (`T-03`, `T-04`) antes que el servicio, porque el servicio lo consume. El controlador al final, cuando hay qué publicar.

**`T-08` y `T-09` no son opcionales ni tardías**, y por eso no van al final de la lista por cortesía: son las dos afirmaciones que el requerimiento hace y que **no fallan solas**. Una atribución correcta con el principal movido, o un `first_movement_id` que avanza con cada compra, devuelven `201` igual.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-189`, `CA-MV-190`, `CA-MV-192` | `T-05` |
| `CA-MV-191` | `T-08` |
| `CA-MV-193` | `T-09` |
| `CA-MV-194` | `T-11` |
| `CA-MV-195` | `T-07` |
| `CA-MV-196` | `T-06` |

## 4. Bloqueos

**Ninguno para construir.** `client_sellers` existe desde `V20` y `SaleAttribution.delEnlace` desde el 23-09-2026.

**Lo que este requerimiento desbloquea:** `RF-MV-013` —comprar un paquete por el hotlink—, que la matriz declara *bloqueado por construcción* por él y que **reutiliza el puerto de `T-03`** sin escribir otro.

**Una nota sobre `RF-MV-002`**, que la ficha declara como dependencia: comprar un producto **para uno mismo** tampoco está construido. Este requerimiento **no lo necesita** —registra la venta por su cuenta, con la misma forma— y no lo construye: son dos puertas con dos atribuciones, y mezclarlas en una tarea dejaría sin decidir cuál se abre.

## 5. Definición de terminado

- [ ] Las doce tareas Hechas, con su verificación.
- [ ] `mvn verify` completo en verde.
- [ ] Las enmiendas de §8 aplicadas, cada una con su versión subida.
- [ ] El contrato regenerado publica la ruta con su permiso.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
