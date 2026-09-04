# PLAN — `RF-MV-009` Consultar los métodos de pago

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-009` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 04-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 04-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

Este plan **hereda la mecánica de `RF-MV-001`** —el esquema del módulo, la traducción de errores, la forma del adaptador— y no la repite. Lo único que estrena es una tabla y una lectura.

---

## 1. Enfoque

Una consulta sin parámetros sobre un catálogo de tres filas, con una colección anidada.

**Lo único que este plan tiene que decidir es cómo se trae la colección anidada sin una `N+1`**, y qué forma tiene la tabla que la sostiene. El resto es la lectura de catálogo que `RF-SP-019` ya resolvió, y se copia su forma en lugar de inventar otra.

## 2. Cambios de esquema

**Una migración: `V55__create_payment_method_exclusions.sql`.**

Su forma la fija [`requirements/mv.md` §7.5](../../../requirements/mv.md) y no se repite aquí. Lo que este plan añade es el número y tres decisiones:

| Decisión | Por qué |
|---|---|
| **Clave primaria compuesta** `(payment_method_id, country_id)` | La fila **es** la relación y no tiene identidad propia que valga la pena nombrar. Es lo mismo que `role_permissions`, y es además lo que cierra «un método no se excluye dos veces del mismo país» sin que ninguna operación lo compruebe |
| **`ON DELETE CASCADE` hacia `payment_methods`** y **`RESTRICT` hacia `countries`** | No son simétricos a propósito. Una exclusión no significa nada sin su método, de modo que si el método desapareciera la fila sobraría; un país, en cambio, **no se borra nunca** —`RF-SP-022` cambia `is_active`—, y el `RESTRICT` está para que un borrado hecho a mano no se lleve por delante una restricción que alguien declaró |
| **Se crea vacía** | Los tres métodos de hoy valen en todos los países, y `RN-MV-019` dice que **la ausencia significa eso**. Sembrar exclusiones de ejemplo sería inventar una regla de negocio que nadie ha declarado |

**Ningún índice adicional.** La clave primaria ya cubre la búsqueda por método, que es la única que esta consulta hace; y la búsqueda por país no la hace nadie todavía. Añadirlo por adelantado cuesta en cada escritura y no ahorra en ninguna lectura.

!!! warning "Esta es la primera clave foránea que entra a `countries`"

    El catálogo existe desde `V16` y en veinte días **no lo había referenciado ni una tabla**: se consultaba para pintar selectores y nada más. `modelo-datos.md` §6 lo tenía anotado como observación abierta —«`countries` y `currencies` son islas»— y se cierra con esta migración.

    **Lo que hay que comprobar es que `RF-SP-022` sigue funcionando**: desactivar un país no borra su fila, de modo que la clave foránea no lo puede bloquear. La suite de países tiene que seguir en verde **sin un solo cambio**.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `application` | `PaymentMethodCatalogResponse`, `PaymentMethodResponse`, `ExcludedCountryResponse` | Nuevos | La respuesta, envuelta en `content` |
| `domain/repository` | `MovementRepository.findActivePaymentMethods` | **Modificado** | Una sentencia con la colección anidada |
| `domain/service` | `ListPaymentMethodsService` | Nuevo | `@Transactional(readOnly = true)` y nada más |
| `interfaces` | `PaymentMethodController` | Nuevo | `GET /api/v1/payment-methods` |

### 3.1 Un controlador propio y no un método más en `MovementController`

Los métodos de pago son **otro recurso**: no cuelgan de una venta y no se leen desde una. Colgarlos de `/movements` obligaría a inventar una ruta como `/movements/payment-methods`, que dice que un método de pago es parte de un movimiento — y es al revés.

Es además lo que `requirements/mv.md` §2 ya decía al separar «Medios de pago» como submódulo propio.

### 3.2 La colección anidada se trae en UNA sentencia

Tres métodos con sus exclusiones son cuatro consultas si se hace lo evidente: una por el catálogo y una por cada método. **Con tres filas no se nota, y ese es el problema** — no se notaría hasta que alguien añadiera métodos, y para entonces el patrón estaría copiado en las lecturas que vengan detrás.

Se resuelve con un `LEFT JOIN` y agregación en memoria: la consulta devuelve una fila por par método-país y el adaptador las agrupa. **`LEFT` y no interno**, porque hoy **ningún método tiene exclusiones** y con un `JOIN` interno la respuesta vendría vacía — el catálogo entero desaparecería sin error.

### 3.3 No se publica ninguna interfaz hacia otros módulos

Nadie fuera de `MV` consulta métodos de pago. El día que alguien lo haga, se publicará entonces: `PM` no publicó ninguna interfaz hasta que `CM` la necesitó, y es el mismo criterio (**D-25**).

## 4. Contrato de API

`GET /api/v1/payment-methods` · `200 OK`.

```json
{
  "content": [
    {
      "id": "…",
      "code": "PSE",
      "name": "Multiples medios de pago",
      "excludedCountries": [ { "id": "…", "code": "MX" } ]
    },
    {
      "id": "…",
      "code": "TRANSFERENCIA",
      "name": "Transferencia bancaria",
      "excludedCountries": []
    }
  ]
}
```

| Estado | Cuándo |
|---|---|
| `200` | Siempre que el actor esté autenticado |
| `401` | Sin token |

**No hay `403`, y su ausencia es la decisión de autorización** (§5). **No hay `404`**: un catálogo vacío es una lista vacía.

**Envuelto en `content` y sin metadatos de paginación**, copiando `RF-SP-019` literalmente: un arreglo desnudo en la raíz cierra la puerta a añadir cualquier metadato después sin romper a todos los clientes, y rellenar `totalPages: 1` diría que hay paginación donde no la hay.

**El orden es por código**, ascendente y estable. No es cosmético: sin orden declarado, dos peticiones pueden devolver los tres métodos en distinto orden y un selector cambiaría de posición entre recargas.

## 5. Autorización

**Autenticado y sin permiso**, como `requirements/mv.md` §4.1 declara.

**Y conviene decir por qué no lleva `movements:read`**, que es el que parecería tocarle: ese permiso gobierna **ver ventas** —quién compró qué y por cuánto—, y hoy está reservado al superadministrador (§6.1 de aquel documento). Exigirlo aquí dejaría **sin métodos de pago a todo el mundo salvo uno**, y con ello la pantalla de compra propia (`RF-MV-002`), que no exige ningún permiso, quedaría sin poder pintar su selector.

Es el mismo razonamiento por el que `RF-PM-007` no exige `products:read`: son dos preguntas y dos actores.

## 6. Auditoría

Ninguna. No escribe.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. Una sola lectura.

## 8. Impacto sobre otros módulos

**En el código, ninguno.** No se toca `SP` ni `PM`.

**En el esquema, uno y hay que declararlo:** `countries` recibe su primera clave foránea entrante (§2). No cambia su tabla ni su comportamiento, y la definición de terminado exige que **la suite de países siga en verde sin cambios**.

**En la documentación, dos enmiendas ya aplicadas** en el mismo pase que este plan:

| Documento | Enmienda |
|---|---|
| `requirements/mv.md` v0.4.0 | Nace `RN-MV-019`; §5.3 cierra lo que había aplazado el 02-09-2026; nace §7.5 con la tabla |
| `modelo-datos.md` v0.24.0 | La tabla entra como escrita, **se cierra la observación 2 de §6** —`countries` deja de ser una isla— y se corrige el inventario de §5.1, que no tenía fila para `MV` |

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Aceptar `countryId` y devolver solo lo que vale allí** | Una llamada por cada cambio de país, y sobre todo **haría creer que el servidor sabe qué país corresponde** — que es la pregunta abierta de `spec.md` §14 |
| **Validar el país al registrar la venta** | Exige `users.country_id`, tocar `RF-SP-024` y `RF-SP-045`, y decidir qué país tienen las personas que ya existen. Decisión del responsable del proyecto: hoy no |
| Declarar **dónde SÍ vale** cada método | Obliga a sembrar las combinaciones válidas de los tres métodos actuales y a revisar el catálogo cada vez que se añada un país. Se aceptó el coste contrario —olvidar una exclusión **ofrece** en lugar de fallar— porque esto no bloquea un cobro, solo pinta un selector (`requirements/mv.md` §7.5) |
| Una columna `excluded_countries` **de tipo arreglo** en `payment_methods` | PostgreSQL lo admite y **no hay clave foránea que valide un arreglo**: un identificador de país inexistente entraría sin que nada lo dijera. Una tabla puente lo impide por construcción |
| Un método **por consulta y por país** (`N+1`) | Con tres filas no se nota, y por eso se copia. Ver §3.2 |
| Colgar la ruta de `/movements` | Un método de pago no es parte de un movimiento; es al revés |
| **Devolver también los desactivados**, con su marca | Quien consume esto pinta un selector, y un elemento que no se puede elegir no va en un selector. Leer una venta vieja pagada con un método retirado es `RF-MV-007`, y esa lectura trae el método **de la venta**, no del catálogo |
| Paginar | Tres filas. `RF-SP-019` ya fijó la forma para un catálogo cerrado |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Que alguien lea esto como una validación** y dé por hecho que el servidor impide pagar con un método excluido | Es el riesgo principal, y no es técnico. Se mitiga con `CA-MV-034` —una prueba que **registra una venta con un método excluido y espera que entre**— y con el aviso de `spec.md` §2. Una prueba que afirma la ausencia es lo único que impide que alguien «arregle» esto sin decidirlo |
| 2 | El `JOIN` se escribe **interno** y el catálogo desaparece entero | Hoy ninguna fila tiene exclusiones, de modo que un `JOIN` interno devolvería **cero métodos**. Falla en la primera prueba, y por eso `CA-MV-030` —la lista vacía y presente— es la que lo detecta |
| 3 | La clave foránea a `countries` **rompa `RF-SP-022`** | No puede: desactivar un país es un `UPDATE`, no un `DELETE`. La definición de terminado lo exige comprobado, no razonado |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| El catálogo activo, con código y nombre | Integración | `CA-MV-027` |
| Un método desactivado **no aparece** | Integración | `CA-MV-028`: se siembra uno inactivo a propósito |
| Las exclusiones viajan | Integración | `CA-MV-029`: un método con dos países excluidos |
| **La lista vacía y presente** | Integración | `CA-MV-030` — es la que detecta el `JOIN` interno (riesgo 2) |
| Colección envuelta | Integración | `CA-MV-031`: hay `content`, no un arreglo en la raíz |
| Sin permiso, con token | Integración | `CA-MV-032`: un actor **sin ninguna autoridad** recibe `200` |
| Sin token | Integración | `CA-MV-033`: `401` |
| **La venta con método excluido SE REGISTRA** | Integración | `CA-MV-034` — la prueba que afirma la ausencia de validación |
| Una sola sentencia | Integración | Tres métodos con exclusiones no producen cuatro consultas |
| `RF-SP-022` sigue funcionando | Integración | La suite de países, **sin cambios** |
