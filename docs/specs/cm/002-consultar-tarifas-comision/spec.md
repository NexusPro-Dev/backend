# SPEC — `RF-CM-002` Consultar las tarifas de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-002` |
| Módulo | `CM` — Comisiones |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 28-08-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

    Debe poder leerlo alguien del negocio y entenderlo completo. Es la primera compuerta del Art. I.6: hasta que no esté aprobada, no se escribe `plan.md`.

---

## 1. Objetivo

Ver qué comisiones hay declaradas, para poder revisarlas, corregirlas y entender por qué alguien cobra lo que cobra.

## 2. Contexto

Es el listado administrativo del módulo, y **devuelve las tarifas tal como se declararon**. No resuelve nada: si un rol tiene una tarifa por omisión y una persona tiene su excepción, aquí aparecen **las dos**, cada una en su fila. Cuál de ellas se aplica a un caso concreto lo responde `RF-CM-005`, y son preguntas distintas.

**El historial es parte de la respuesta.** Con vigencia, una misma combinación puede tener varias tarifas consecutivas —lo que se pagaba el año pasado y lo que se paga ahora—, y todas están declaradas. Un listado que devolviera solo las de hoy escondería justamente el dato que la vigencia existe para conservar.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Revisa las comisiones declaradas, busca una para corregirla, y comprueba qué se pagaba en un periodo |

## 4. Alcance

### 4.1 Incluye

- Listar las tarifas, **de la más reciente a la más antigua** por su inicio de vigencia.
- Filtrar por **rol**, por **producto**, por **persona** y por **fecha**.
- Distinguir en cada fila si rige para todos, para un producto, para una persona, o para los dos.
- Devolver también las **vencidas**, que son historial.
- Marcar las **retiradas**, sin excluirlas.
- Paginar el resultado.

### 4.2 No incluye

- **Resolver cuál se aplica.** Es `RF-CM-005`, y es otra pregunta.
- **El motivo del retiro** de una tarifa retirada. Uno a uno es una consulta legítima; en bloque sería una exportación de decisiones comerciales. Mismo criterio que `RF-PM-002` aplicó al catálogo.
- **Modificar nada.**

## 5. Reglas de negocio aplicables

Ninguna. Es una consulta: no decide nada y no cambia nada. Lo que gobierna el resultado son los datos que `RF-CM-001`, `RF-CM-003` y `RF-CM-004` dejaron escritos.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Rol | No | Filtra las tarifas de un rol | Si el rol no existe, la colección vuelve vacía y **no es un error** |
| Producto | No | Filtra las tarifas acotadas a un producto | — |
| Persona | No | Filtra las tarifas acotadas a una persona | — |
| Solo las que rigen en una fecha | No | Devuelve únicamente las vigentes ese día | Una fecha |
| Incluir retiradas | No | Si se piden también las retiradas | Por omisión, **no** se incluyen |
| Página y tamaño | No | Paginación | Los límites del sistema |

**No hay filtro «solo las que rigen hoy» como interruptor aparte:** es el filtro por fecha con la de hoy. Un interruptor y una fecha podrían contradecirse, y esa contradicción no la detecta nada.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Tarifas | De cada una: su identificador, su porcentaje, su vigencia, y el **grado** en que fue declarada |
| Rol resuelto | Código y nombre del rol |
| Producto resuelto | Código y nombre, **vacío y presente** cuando la tarifa rige para todo el catálogo |
| Persona resuelta | Nombre de usuario y nombre, **vacía y presente** cuando la tarifa rige para todos los del rol |
| Marca de retiro | En las retiradas, que lo están y desde cuándo |
| Total | Cuántas tarifas cumplen el filtro |
| Orden | El aplicado, para que quien recibe la página sepa sobre qué está paginando |

**El vacío y presente no es un capricho:** un producto ausente significa «rige para todos», y un campo que desaparece del resultado es indistinguible de uno que el cliente no conoce. Es la misma decisión que `RF-PM-002` tomó con el destino de un producto.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de tarifas de comisión.

**Postcondiciones**

- Ninguna: la consulta no cambia el estado del sistema.

## 8. Flujo principal

1. El actor solicita las tarifas, con o sin filtros.
2. El sistema aplica los filtros que se enviaron y omite los que no.
3. El sistema devuelve la página, ordenada del inicio de vigencia más reciente al más antiguo, con el total y el orden aplicado.

## 9. Flujos alternativos

### FA-001 — Sin ninguna tarifa declarada

**Cuándo ocurre:** el sistema no tiene tarifas, o ninguna cumple el filtro.

1. Se devuelve la **colección vacía** con total cero. **No es un error**: que no haya comisiones declaradas es un estado legítimo del sistema, sobre todo al principio.

### FA-002 — Consulta del historial de un caso

**Cuándo ocurre:** se filtra por rol, producto y persona a la vez, sin fecha.

1. Se devuelven **todas** las tarifas de ese caso, vigentes y vencidas, en orden cronológico inverso.
2. Es la forma de responder «cuánto se ha pagado por esto a lo largo del tiempo», y por eso el filtro por fecha es opcional.

## 10. Excepciones

### EX-001 — Filtro con un valor inexistente

**Condición:** se filtra por un rol, un producto o una persona que no existe.
**Respuesta del sistema:** devuelve la **colección vacía**, no un error. Filtrar por algo que no existe es una pregunta legítima con respuesta vacía; convertirla en error obligaría a validar contra tres catálogos una consulta que no cambia nada.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-006` | Formato de fecha | La fecha debe expresarse en el formato de fecha admitido. |
| `VAL-011` | Paginación | Los parámetros de paginación deben estar dentro de los límites admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-014` | El sistema devuelve las tarifas de la más reciente a la más antigua por su inicio de vigencia |
| `CA-CM-015` | El sistema filtra por rol, por producto y por persona, y combina los tres filtros |
| `CA-CM-016` | El sistema devuelve **vacío y presente** el producto y la persona en una tarifa que rige para todos |
| `CA-CM-017` | El sistema devuelve las tarifas **vencidas** junto a las vigentes cuando no se filtra por fecha |
| `CA-CM-018` | El sistema devuelve **solo** las que rigen en la fecha indicada cuando se filtra por fecha |
| `CA-CM-019` | El sistema **excluye** las tarifas retiradas por omisión, y las incluye marcadas cuando se piden |
| `CA-CM-020` | El sistema devuelve la colección vacía, y no un error, cuando ningún registro cumple el filtro |
| `CA-CM-021` | El sistema devuelve el total y el orden aplicado junto a la página |
| `CA-CM-022` | El sistema devuelve el historial completo de un caso cuando se filtra por rol, producto y persona sin fecha |

## 13. Casos límite

- **Filtro por fecha en el futuro:** devuelve las que regirán ese día, incluidas las programadas que aún no han empezado. Es una pregunta legítima —«qué se va a pagar en enero»— y no un error.
- **Una tarifa retirada que además está vencida:** aparece marcada como retirada. Las dos cosas son ciertas a la vez y no se excluyen: una dice que dejó de regir, la otra que no debió existir.
- **Filtro por persona sobre una tarifa que rige para todos:** **no** la devuelve. Filtrar por persona significa «las declaradas para esa persona», no «las que le aplican» — eso último es `RF-CM-005`, y confundirlos haría que este listado empezara a resolver precedencias por su cuenta.
- **Filtro por producto sobre la tarifa por omisión del rol:** mismo criterio y mismo motivo que el anterior.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Este es el requerimiento del módulo que D-22 puede obligar a cambiar.** Se especifica con **alcance global explícito**: quien tiene el permiso ve todas las tarifas. El día que se cierre el modelo de alcance habrá que decidir si un manager ve solo las de su equipo, y esa decisión afecta a esta consulta y no a la tabla.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial, sin preguntas abiertas. | Responsable técnico |
