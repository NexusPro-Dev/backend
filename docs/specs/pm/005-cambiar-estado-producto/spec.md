# SPEC — `RF-PM-005` Cambiar el estado de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-005` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Decidir si un producto se ofrece, sin borrarlo.

## 2. Contexto

Es la operación del día a día del catálogo: se deja de vender algo por una temporada, se prepara una oferta nueva antes de publicarla, se retira un servicio mientras se rehace. Sin ella la única forma de dejar de ofrecer un producto sería eliminarlo, y eliminar es definitivo.

**Desactivar no toca nada de lo ya vendido.** El producto sigue existiendo, sigue apareciendo en el catálogo administrativo y sigue pudiendo explicarse; lo único que cambia es que deja de ofrecerse (`RN-PM-009`).

**Reactivar vuelve a competir por el destino.** Un upgrade que se desactivó libera su destino, y en ese intervalo puede haberse publicado otro hacia el mismo nivel. Volver a activarlo exige comprobar `RN-PM-004` otra vez: es el momento en que dos precios simultáneos para lo mismo entrarían por la puerta de atrás.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Publica o retira de la venta un producto |

## 4. Alcance

### 4.1 Incluye

- Activar un producto inactivo y desactivar uno activo.
- Comprobar, al activar un upgrade, que ningún otro upgrade activo apunta a su mismo destino.
- Dejar constancia del cambio en la auditoría.

### 4.2 No incluye

- **Corregir los datos del producto**, que es `RF-PM-004`.
- **Retirarlo definitivamente**, que es `RF-PM-006`.
- **Programar la publicación para una fecha.** Un producto se publica cuando alguien lo publica; la vigencia con fechas es cosa de las promociones, que hoy están fuera de alcance.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-004` | Un solo upgrade activo por destino | `requirements/pm.md` §5.1 |
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del producto | Sí | Cuál cambia de estado | Debe existir y no estar retirado |
| Estado nuevo | Sí | Activo o inactivo | Uno de los dos valores admitidos |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Producto | El producto con su estado nuevo |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de productos.
- El producto existe y no está retirado.

**Postcondiciones**

- El producto queda en el estado pedido.
- Si quedó activo y es un upgrade, es el **único** upgrade activo hacia su destino.
- La auditoría de cambios contiene el cambio de estado con su valor anterior y el nuevo.

## 8. Flujo principal

1. El actor envía el identificador y el estado que quiere.
2. El sistema comprueba que el producto existe y no está retirado.
3. Si el estado pedido es «activo» y el producto es un upgrade, el sistema comprueba que ningún otro upgrade activo apunta a su destino.
4. El sistema aplica el estado y emite el evento de auditoría.
5. El sistema devuelve el producto.

## 9. Flujos alternativos

### FA-001 — El producto ya está en ese estado

**Cuándo ocurre:** se pide activar uno activo, o desactivar uno inactivo.

1. El sistema responde con normalidad y **no emite evento de auditoría**: no hubo cambio.
2. **No es un error.** Quien pulsa dos veces el mismo botón no ha hecho nada malo, y responder con un rechazo obligaría a la interfaz a consultar el estado antes de cada pulsación.

### FA-002 — Desactivar un upgrade

**Cuándo ocurre:** el estado pedido es «inactivo» y el producto es un upgrade.

1. No se comprueba nada sobre el destino: **liberarlo nunca produce conflicto**.
2. El destino queda libre para que otro upgrade pueda activarse.

## 10. Excepciones

### EX-001 — Producto inexistente o retirado

**Condición:** el identificador no corresponde a ningún producto, o corresponde a uno retirado.
**Respuesta del sistema:** rechaza la operación. Un producto retirado no vuelve a la venta cambiándole el estado.

### EX-002 — Ya hay un upgrade activo hacia ese destino

**Condición:** se pide activar un upgrade y otro upgrade activo apunta a la misma membresía.
**Respuesta del sistema:** rechaza la activación **nombrando el producto que ocupa el destino**, para que el actor sepa cuál desactivar.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Estado obligatorio y dentro del dominio | El estado indicado no es válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-040` | El sistema desactiva un producto activo, y este deja de aparecer en la oferta sin desaparecer del catálogo |
| `CA-PM-041` | El sistema activa un producto inactivo |
| `CA-PM-042` | El sistema rechaza activar un upgrade cuando otro upgrade activo apunta a su mismo destino, y el mensaje nombra a ese producto |
| `CA-PM-043` | El sistema permite activar un upgrade **después** de desactivar el que ocupaba su destino |
| `CA-PM-044` | El sistema acepta sin error una petición que deja el producto en el estado que ya tenía, y **no registra evento** |
| `CA-PM-045` | El sistema rechaza cambiar el estado de un producto retirado |
| `CA-PM-046` | El sistema registra el cambio de estado en la auditoría con su valor anterior y el nuevo |
| `CA-PM-047` | El sistema rechaza la operación a un actor sin el permiso de modificación de productos |

## 13. Casos límite

- **Dos activaciones simultáneas hacia el mismo destino:** dos upgrades inactivos hacia el mismo nivel se activan a la vez. Uno debe quedar y el otro ser rechazado; que queden los dos es exactamente el desenlace que `RN-PM-004` existe para impedir, y no basta con comprobarlo antes de escribir.
- **Desactivar y activar en carrera:** uno desactiva el upgrade que ocupa el destino mientras otro activa el suyo. Cualquiera de los dos desenlaces es correcto; lo que no puede quedar es ninguno activo por un rechazo mal ordenado.
- **Activar un producto de servicio:** no comprueba destino, porque no lo tiene. Debe probarse que la comprobación **no se ejecuta**, no solo que no falla.
- **Desactivar el único upgrade hacia el nivel más alto:** se admite. El sistema no exige que todo nivel tenga upgrade: que no se pueda comprar el ascenso a un nivel es una decisión comercial legítima.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | **¿Desactivar exige motivo?** `RF-SP-022` y `RF-SP-023` no lo exigen para países y monedas, y el Art. V.13 solo lo obliga en las eliminaciones. Pero retirar algo de la venta es una decisión comercial que alguien querrá poder explicar tres meses después | Responsable del proyecto | **Abierta** |
| 2 | **¿El estado admite un tercer valor, `BORRADOR`?** Está anticipado en el modelo de datos. Con él, un producto recién creado no está «inactivo» —que suena a retirado— sino «sin publicar todavía», y la diferencia se nota en el catálogo. Depende de cómo se resuelva la pregunta 1 de `RF-PM-001` | Responsable del proyecto | **Abierta** |
| 3 | **¿Desactivar un upgrade afecta a quien lo tenía en el carrito?** No hay carrito ni compra, de modo que hoy no aplica. Se registra para que quien escriba la compra no lo dé por resuelto | Responsable técnico | **Abierta** |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
