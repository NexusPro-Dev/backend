# SPEC — `RF-PM-002` Consultar productos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-002` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Ver y encontrar lo que hay en el catálogo, incluido lo que ya no se ofrece.

## 2. Contexto

Con `RF-PM-001` se puede **crear** un producto y no **verlo**: quien administra no tiene de dónde sacar la lista para corregir un precio, retirar una oferta o comprobar qué upgrade está publicado hacia cada nivel. Es el mismo hueco que `SP` tuvo entre `RF-SP-001` y `RF-SP-002`.

**Este listado no es la oferta.** Devuelve el catálogo completo —lo activo, lo inactivo y, si se pide, lo retirado— y lo lee quien administra o vende. Lo que un cliente puede comprar es `RF-PM-007`, que responde otra pregunta y a otro actor.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Consulta el catálogo para gobernarlo |
| Fuerza comercial | Consulta el catálogo para vender o para atender a un cliente |

## 4. Alcance

### 4.1 Incluye

- Devolver los productos **paginados**.
- Filtrar por tipo, por estado y por membresía destino.
- Buscar por nombre, sin distinguir mayúsculas ni acentos.
- Incluir o excluir los productos retirados.

### 4.2 No incluye

- **El detalle de un producto**, que es `RF-PM-003`.
- **La oferta de un cliente**, que es `RF-PM-007` y depende de su nivel.
- **Cuántas veces se ha vendido cada producto.** No existen las ventas todavía, y cuando existan será una pregunta de su módulo.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-010` | El producto no desaparece: el retiro es lógico | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Página y tamaño | No | Qué porción del catálogo se pide | Con un tamaño máximo; pedir más se **rechaza**, no se recorta |
| Tipo | No | Filtra por upgrade o por servicio | Uno de los dos valores admitidos |
| Estado | No | Filtra por activo o inactivo | Uno de los valores admitidos |
| Membresía destino | No | Filtra los upgrades que llevan a ese nivel | Un destino que no existe devuelve una colección vacía, no un error |
| Búsqueda | No | Coincidencia parcial sobre el nombre | En blanco equivale a ausente |
| Incluir retirados | No | Si se devuelven también los productos eliminados | Por omisión **no** se devuelven |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Productos | Identificador, tipo, nombre, descripción, precio con su moneda, estado y —en los upgrades— la membresía destino con su nombre y su nivel |
| Marca de retiro | En los retirados, que lo están y desde cuándo |
| Total | Cuántos productos cumplen el filtro |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de productos.

**Postcondiciones**

- Ninguna: la consulta no modifica nada.

## 8. Flujo principal

1. El actor pide el catálogo, con los filtros que quiera.
2. El sistema valida los parámetros de paginación y los valores de los filtros.
3. El sistema devuelve la página pedida y el total que cumple el filtro.

## 9. Flujos alternativos

### FA-001 — Catálogo vacío

**Cuándo ocurre:** no hay productos, o ninguno cumple el filtro.

1. El sistema devuelve una colección vacía con total cero. **No es un error**: un catálogo sin productos es un estado legítimo del sistema recién puesto en marcha.

### FA-002 — Página más allá de la última

**Cuándo ocurre:** el actor pide una página que no existe.

1. El sistema devuelve una colección vacía **con el total real**, no con un total deducido de la página pedida.

## 10. Excepciones

### EX-001 — Parámetros inválidos

**Condición:** la página o el tamaño están fuera de rango, o un filtro trae un valor que no pertenece a su dominio.
**Respuesta del sistema:** rechaza la consulta **enumerando todos los parámetros mal escritos a la vez**, para que quien se equivocó en cuatro no tenga que corregir la dirección cuatro veces.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Página y tamaño dentro de rango | Los parámetros de paginación están fuera del rango admitido. |
| `VAL-002` | Tipo dentro del dominio | El tipo indicado no es válido. |
| `VAL-003` | Estado dentro del dominio | El estado indicado no es válido. |
| `VAL-004` | Identificador de membresía con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-013` | El sistema devuelve el catálogo paginado con el total de productos que cumplen el filtro |
| `CA-PM-014` | El sistema filtra por tipo y devuelve solo los upgrades o solo los servicios |
| `CA-PM-015` | El sistema filtra por estado y devuelve también los inactivos cuando se piden |
| `CA-PM-016` | El sistema filtra los upgrades por su membresía destino |
| `CA-PM-017` | El sistema busca por nombre sin distinguir mayúsculas ni acentos, y la búsqueda en blanco equivale a no filtrar |
| `CA-PM-018` | El sistema **excluye los productos retirados** salvo que se pidan expresamente, y al pedirlos indica desde cuándo lo están |
| `CA-PM-019` | El sistema devuelve el destino de cada upgrade con su nombre y su nivel, sin exigir una segunda consulta |
| `CA-PM-020` | El sistema rechaza los parámetros inválidos **enumerándolos todos juntos** |
| `CA-PM-021` | El sistema devuelve una colección vacía y total cero cuando ningún producto cumple el filtro |
| `CA-PM-022` | El sistema rechaza la consulta a un actor sin el permiso de lectura de productos |

## 13. Casos límite

- **Filtro por un destino que no existe:** colección vacía, no error. El actor filtró por algo que no está, y eso no es una entrada inválida.
- **Filtro por destino combinado con tipo servicio:** ningún servicio tiene destino, de modo que el resultado es siempre vacío. Debe devolverse vacío y no rechazarse: la combinación es coherente aunque sea inútil.
- **Búsqueda con comodines del motor de búsqueda:** un nombre que contenga los caracteres de comodín no debe ampliar la búsqueda a todo el catálogo.
- **Un producto retirado y uno vivo con el mismo nombre:** es posible, porque la unicidad es entre los vivos. El listado debe mostrarlos como dos filas distintas y no colapsarlos.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | **¿Cuál es el orden por omisión?** Por nombre es lo previsible; por fecha de alta pone arriba lo último publicado; por nivel destino agrupa los upgrades en el orden de la cadena, que es como se leen. El orden importa porque es lo primero que ve quien abre el catálogo | Responsable del proyecto | **Abierta** |
| 2 | **¿El orden es configurable por el actor?** `RF-SP-002` lo permite en roles y `RF-SP-011` no lo permite en la auditoría, y en cada caso hay un motivo. Aquí no hay uno evidente | Responsable del proyecto | **Abierta** |
| 3 | **¿Ver los retirados exige un permiso aparte?** Hoy basta con el de lectura. Un producto retirado lleva su motivo en la auditoría, no en el catálogo, de modo que no expone nada sensible — pero sí revela decisiones comerciales pasadas a cualquiera que tenga lectura | Responsable del proyecto | **Abierta** |
| 4 | **¿Se puede filtrar por rango de precio?** No es lo que necesita quien administra, y sí lo que acabará necesitando una interfaz de venta. Añadirlo después es barato; escribirlo ahora sin que nadie lo pida es alcance que crece solo | Responsable del proyecto | **Abierta** |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
