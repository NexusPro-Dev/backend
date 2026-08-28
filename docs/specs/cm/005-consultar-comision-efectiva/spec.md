# SPEC — `RF-CM-005` Consultar la comisión efectiva de una persona sobre un producto en una fecha

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-005` |
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

Responder, sin ambigüedad, **cuánto le corresponde a una persona por vender un producto un día concreto** — y **por qué**.

## 2. Contexto

Las tarifas se declaran en cuatro grados y con vigencia, de modo que a un caso concreto pueden aplicarle varias. **Cuál gana es una regla del negocio, y vive aquí y en un solo sitio.**

**Que viva en un solo sitio es la razón de ser de este requerimiento.** Si cada consumidor —la liquidación, un informe, una pantalla— resolviera la precedencia por su cuenta, cada uno la implementaría un poco distinto y todos devolverían resultados **plausibles**. Un error así no se ve: no falla, paga mal. Es el mismo criterio con el que el sistema decidió que «la regla se queda con su dueño».

**Y responde por qué.** No basta el número: quien pregunta necesita saber **qué tarifa** se aplicó, porque de eso depende que pueda corregirla si está mal. Un porcentaje sin su origen obliga a reconstruir la precedencia a mano, que es justo lo que este requerimiento existe para evitar.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Comprueba qué comisión le corresponde a una persona por un producto, y con qué tarifa |

**Solo administrativa, por ahora.** Que un vendedor consulte **la suya** es otro actor y otra pregunta, y depende del modelo de alcance de datos, que sigue sin decidirse. Ver §14.

## 4. Alcance

### 4.1 Incluye

- Resolver el porcentaje aplicable a una **persona**, un **producto** y una **fecha**.
- Devolver **cuál** de las tarifas declaradas se aplicó y **en qué grado** estaba declarada.
- Distinguir «no comisiona» —una tarifa del cero por ciento— de «no hay tarifa declarada», que **no son lo mismo**.

### 4.2 No incluye

- **Calcular el importe de la comisión.** Este requerimiento devuelve el porcentaje, no un dinero: no hay venta ni importe sobre el que aplicarlo.
- **Listar las tarifas**, que es `RF-CM-002`.
- **Que un vendedor consulte la suya.** Ver §3 y §14.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-004` | Gana la tarifa más específica vigente en la fecha | `requirements/cm.md` §5.1 |
| `RN-CM-005` | La tarifa no desaparece | `requirements/cm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Persona | Sí | De quién se resuelve la comisión | Debe existir |
| Producto | Sí | Por vender qué | Debe existir. **Se admite aunque esté retirado**: se puede preguntar qué se pagaba por algo que ya no se vende |
| Fecha | No | En qué día. Sin ella, **hoy** | Una fecha |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Porcentaje | El que le corresponde, o **la declaración explícita de que no hay tarifa** |
| Tarifa aplicada | Cuál de las declaradas ganó: su identificador y su vigencia |
| Grado | En qué grado estaba declarada la que ganó: del rol, del rol para ese producto, de la persona, o de la persona para ese producto |
| Rol considerado | El rol vendedor de esa persona con el que se resolvió |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de tarifas de comisión.

**Postcondiciones**

- Ninguna: la consulta no cambia el estado del sistema.

## 8. Flujo principal

1. El actor indica la persona, el producto y, si quiere, la fecha.
2. El sistema determina el **rol vendedor** de esa persona.
3. El sistema busca, entre las tarifas **vivas y vigentes en esa fecha** para ese rol, la más específica que aplique, en este orden:
   1. La declarada para **esa persona y ese producto**.
   2. La declarada para **esa persona**, sin producto.
   3. La declarada para **ese producto**, sin persona.
   4. La declarada para **el rol**, sin producto ni persona.
4. El sistema devuelve el porcentaje de la primera que exista, junto con cuál fue y en qué grado estaba declarada.

## 9. Flujos alternativos

### FA-001 — No hay ninguna tarifa aplicable

**Cuándo ocurre:** ninguno de los cuatro grados tiene tarifa viva y vigente esa fecha.

1. El sistema responde que **no hay tarifa declarada** para ese caso.
2. **No es un error ni un «no encontrado»**: la persona existe, el producto existe, y la respuesta a la pregunta es «nadie lo ha declarado». Devolver un error obligaría a quien pregunta a distinguir un fallo de una respuesta legítima.
3. **No se devuelve cero.** Cero significa «no comisiona», que es una decisión declarada; la ausencia es que nadie decidió. Confundirlas haría indistinguible lo pensado de lo olvidado.

### FA-002 — La comisión declarada es cero

**Cuándo ocurre:** la tarifa que gana declara cero por ciento.

1. El sistema devuelve **cero**, con la tarifa que lo declaró.
2. Es una respuesta afirmativa: **esto no comisiona, y alguien lo decidió**.

### FA-003 — La persona no tiene rol vendedor

**Cuándo ocurre:** la persona existe y no porta ningún rol de tipo vendedor.

1. El sistema responde que **esa persona no comisiona**, distinguiéndolo de que no haya tarifa: no es que falte declararla, es que esa persona no vende.

## 10. Excepciones

### EX-001 — La persona no existe

**Condición:** la persona indicada no existe.
**Respuesta del sistema:** rechaza la consulta diciendo que la persona indicada no existe.

### EX-002 — El producto no existe

**Condición:** el producto indicado no existe.
**Respuesta del sistema:** rechaza la consulta diciendo que el producto indicado no existe. **Un producto retirado no entra aquí**: existe, y se resuelve con normalidad.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-006` | Formato de fecha | La fecha debe expresarse en el formato de fecha admitido. |
| `VAL-012` | Persona y producto obligatorios | La persona y el producto son obligatorios para resolver la comisión. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-039` | Con solo la tarifa por omisión del rol, el sistema la devuelve e indica ese grado |
| `CA-CM-040` | Con tarifa del rol y tarifa del rol para el producto, **gana la del producto** |
| `CA-CM-041` | Con tarifa del rol y excepción de la persona, **gana la de la persona** |
| `CA-CM-042` | Con los cuatro grados declarados, **gana la de la persona para ese producto** |
| `CA-CM-043` | El sistema **ignora** las tarifas que no rigen en la fecha consultada, y aplica la que sí |
| `CA-CM-044` | Sin fecha, el sistema resuelve **con la de hoy** |
| `CA-CM-045` | Con una fecha pasada, el sistema devuelve **la que regía entonces** y no la de hoy |
| `CA-CM-046` | El sistema **ignora** las tarifas retiradas, y aplica la siguiente en precedencia |
| `CA-CM-047` | Sin ninguna tarifa aplicable, el sistema responde «no hay tarifa declarada» y **no** cero |
| `CA-CM-048` | Con una tarifa del cero por ciento, el sistema devuelve **cero** e indica qué tarifa lo declaró |
| `CA-CM-049` | El sistema resuelve con normalidad sobre un producto **retirado** |
| `CA-CM-050` | Con una persona sin rol vendedor, el sistema lo dice, y lo distingue de que no haya tarifa |

## 13. Casos límite

- **Una tarifa más específica pero retirada:** se ignora, y gana la siguiente en precedencia. Retirar significa que no debió existir, de modo que no puede seguir ganando.
- **Una tarifa más específica pero vencida:** se ignora igual, y por otra razón: no rige esa fecha. Las dos exclusiones son distintas y las dos llevan al mismo sitio.
- **Fecha futura:** se resuelve con las tarifas que regirán ese día, incluidas las programadas. Es la forma de comprobar que un cambio ya declarado hará lo que se espera **antes** de que entre en vigor.
- **Fecha anterior a toda tarifa declarada:** responde «no hay tarifa declarada». No se extrapola hacia atrás la más antigua: una tarifa dice desde cuándo rige, y antes de esa fecha no regía.
- **La persona tiene rol vendedor y ninguna tarifa, ni siquiera la del rol:** responde «no hay tarifa declarada». Es distinto de `FA-003`, donde el problema es que no vende.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Que un vendedor consulte su propia comisión no es una pregunta abierta de este requerimiento: es un requerimiento que todavía no se abre.** Sería el equivalente de lo que en el catálogo de productos separó la oferta propia del catálogo administrativo — otro actor, otra pregunta, otro permiso—, y **depende de D-22**, el modelo de alcance de datos, que sigue sin decidirse. Cuando se cierre, se registrará como `RF-CM-006`.

**Una persona no puede tener dos roles vendedores** (`RN-SP-025`), y de eso depende que el paso 2 del flujo principal sea determinista. Esa regla la gobierna el módulo `SP` y **todavía no está implementada**: mientras no lo esté, este requerimiento no puede darse por terminado.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 28-08-2026 | Redacción inicial, sin preguntas abiertas. | Responsable técnico |
