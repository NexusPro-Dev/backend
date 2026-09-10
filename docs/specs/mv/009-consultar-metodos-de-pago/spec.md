# SPEC — `RF-MV-009` Consultar los métodos de pago

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-009` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 04-09-2026 |
| Enmendada | 09-09-2026 — `RN-MV-023`: lo `INTERNO` **no se devuelve nunca**, y con ello el catálogo deja de publicar el pago gratuito; `CA-MV-049` y `CA-MV-050` (Art. I.7) |
| Enmendada | 09-09-2026 — `RN-MV-024`: **la consulta deja de exigir sesión**. El `GET` es público, y lo que sale sigue siendo lo **activo y `PUBLICO`**; `CA-MV-033` se **invierte** y nacen `CA-MV-051` y `CA-MV-052` (Art. I.7) |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Decir **con qué se puede pagar**, y **dónde cada medio no sirve**.

## 2. Contexto

Es la lectura que le falta a la pantalla de venta. `RF-MV-001` exige un método de pago y **no hay forma de saber cuáles hay**: hoy los tres identificadores se sacan de la migración a mano, que es exactamente el acuerdo por fuera del contrato que el Art. VIII.7 prohíbe.

**Y trae lo que este módulo no tenía: la restricción por país** (`RN-MV-019`). No todos los medios operan en todas partes —`PSE` es colombiano y no significa nada en México—, y ofrecerle a alguien un medio con el que no va a poder pagar es el defecto que esta consulta existe para quitar.

!!! danger "La restricción se PUBLICA, y no se comprueba en ninguna parte"

    Esta es la decisión con más consecuencias del requerimiento, y la tomó el responsable del proyecto el 04-09-2026: el sistema **declara** dónde no vale cada método y lo devuelve; **quien decide qué mostrar es el cliente que consume esta respuesta**.

    **Registrar una venta no mira el país.** Una venta con un método excluido **se registra con normalidad**, y eso no es una fase pendiente: es lo que significa que la restricción sea informativa. La razón está en `requirements/mv.md` §5.3 — comprobarlo en el servidor exigiría antes decidir **de qué país se trata**, y hoy **nadie tiene país**: `users` no lo guarda.

    Quien lea esto buscando dónde se valida, que no siga buscando. **No se valida.**

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquiera, **con sesión o sin ella** | Consulta con qué se puede pagar. **No hace falta permiso, y desde el 09-09-2026 tampoco hace falta cuenta** (`RN-MV-024`): quien rellena el formulario de registro por enlace elige con qué paga antes de tener una. Para quien sí ha entrado no cambia nada — recibe la misma respuesta |

## 4. Alcance

### 4.1 Incluye

- Devolver los métodos de pago **activos y de visibilidad `PUBLICO`**.
- Devolver, de cada uno, **en qué países no vale**.
- Un orden estable.
- **Responder sin sesión** (`RN-MV-024`): la consulta es pública, y devuelve lo mismo a quien ha entrado y a quien no.

### 4.2 No incluye

- **Administrar el catálogo.** Ni alta, ni edición, ni activar o desactivar. Se siembra por migración (`requirements/mv.md` §5.3), y el día que haya pantalla será un requerimiento propio.
- **Declarar o retirar exclusiones.** Igual: se siembran.
- **Filtrar por país.** Se devuelven todas las exclusiones y el cliente aplica la suya. Ver §14.
- **Impedir pagar con un método excluido.** No ocurre en ninguna parte del sistema (§2).

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-MV-018` | Un método desactivado no invalida lo pagado con él | `requirements/mv.md` §5.1 |
| `RN-MV-019` | Un método puede estar excluido en países concretos, y esa exclusión se publica | `requirements/mv.md` §5.1 |
| `RN-MV-023` | Lo `INTERNO` no se ofrece nunca, y no hay parámetro que lo traiga | `requirements/mv.md` §5.1 |
| `RN-MV-024` | El catálogo se consulta **sin iniciar sesión**, y sigue saliendo solo lo activo y `PUBLICO` | `requirements/mv.md` §5.1 |

**Este requerimiento hace cumplir una y media.** `RN-MV-019` la cumple entera —es la única operación que la ejerce—. De `RN-MV-018` cumple **la mitad que le toca**: no ofrece lo desactivado. La otra mitad —que lo ya pagado siga valiendo— no es de aquí, es de quien lee una venta vieja.

**Y desde el 09-09-2026 hace cumplir dos más, las dos enteras.** `RN-MV-023` —lo `INTERNO` no se ofrece— y `RN-MV-024` —esto se responde sin sesión— **solo existen aquí**: ninguna otra operación del sistema las ejerce. Las dos hablan de lo mismo visto desde dos sitios, y por eso conviene leerlas juntas: **la segunda solo es aceptable porque la primera ya estaba escrita**. Abrir la ruta con el catálogo acotado por un solo eje habría puesto el pago gratuito delante de cualquiera.

## 6. Datos

### 6.1 Entrada

**Ninguna.** No hay parámetros, ni filtros, ni paginación.

**No se recibe el país**, y es la ausencia que define la operación. Aceptarlo convertiría esto en «qué puedo usar en Colombia», que es una pregunta que **el servidor no tiene por qué responder** cuando no sabe de qué país es quien pregunta — y que el cliente responde solo con lo que esta respuesta le da.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Métodos | Los **activos y de visibilidad `PUBLICO`**, cada uno con su identificador, su código y su nombre |
| — Países excluidos | De cada método, **dónde no vale**. Vacío significa que vale en todas partes |

**La lista de exclusiones va vacía y no ausente.** Es la misma decisión que `RF-MV-001` toma con el descuento: un cliente que tenga que distinguir «sin exclusiones» de «no vino el campo» acabará tratándolo como opcional para siempre.

!!! important "Lo `INTERNO` no sale de aquí bajo ninguna petición, y esa ausencia es la regla"

    `RN-MV-023` (09-09-2026). Un método con `visibility = INTERNO` **no aparece en esta respuesta**, y **no hay parámetro que lo traiga** — ni `includeHidden`, ni nada equivalente.

    **Es una asimetría triple y deliberada**, y conviene verla contra las tres cosas de las que se aparta:

    - **Contra `is_active`**: un método inactivo también se oculta, pero ocultarlo es una consecuencia de estar retirado. Lo `INTERNO` **sirve perfectamente** y aun así no se ofrece — son dos ejes, no uno.
    - **Contra `RN-MV-019`**: la exclusión por país **se publica y el cliente filtra**. Aquí el cliente no filtra porque **no lo ve**. La diferencia es quién elige: un método excluido lo elegiría una persona que no debería, y uno interno **no lo elige ninguna persona** — lo pone el sistema.
    - **Contra los catálogos de `SP`**: países y monedas sí devuelven lo inactivo bajo petición explícita. Aquí no, porque publicarlo no resuelve ninguna pregunta y sí da ocasión de ofrecerlo por error en el selector de pago.

    **El precio se acepta y queda escrito**: quien depure una venta gratuita verá un `paymentMethodId` que **este catálogo no resuelve**. La salida es mirar la base o el detalle de la venta, no ampliar esta consulta.

!!! important "La respuesta es la misma con sesión y sin ella, y eso es lo que se promete"

    `RN-MV-024` (09-09-2026). La consulta pasa a ser **pública**, y lo que se abre **no es un catálogo distinto**: es este mismo, con sus dos ejes puestos —**activo** y **`PUBLICO`**—.

    **No hay dos respuestas, y no puede haberlas.** Nada aparece por estar autenticado ni desaparece por no estarlo, y no se admite ningún parámetro que lo cambie. Un catálogo que enseñara más a quien ha entrado obligaría a mantener dos verdades sobre lo mismo, y a que el selector de pago del registro y el de la pantalla de compra pudieran discrepar.

    **Lo que sostiene que esto no publique nada es la regla anterior, no el filtro de seguridad.** Si `RN-MV-023` no estuviera escrita, abrir la ruta pondría el pago gratuito delante de cualquiera — y con él, la forma de anotar una venta como gratuita cuando no lo es. Por eso las dos reglas se leen juntas (§5).

**Y el nombre del país no viaja**, solo su identificador y su código. Quien pinta países ya tiene su catálogo (`RF-SP-021`), y repetir el nombre aquí lo dejaría desincronizado el día que se corrija una tilde.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- **Ninguna.** Desde el 09-09-2026 no hace falta ni cuenta ni sesión (`RN-MV-024`): la consulta es pública.

**Postcondiciones**

- **Ninguna.** No escribe nada, no audita nada y no cambia nada.

## 8. Flujo principal

1. Alguien —con sesión o sin ella— pide los métodos de pago.
2. El sistema devuelve los **activos y `PUBLICO`**, cada uno con los países en los que no vale.

**Dos pasos, y no hay más.** Se escribe entero para que quede claro que no hay un tercero en el que algo se compruebe.

## 9. Flujos alternativos

### FA-001 — Ningún método tiene exclusiones

**Cuándo ocurre:** es el estado de hoy, con los tres sembrados.

1. Cada método devuelve su lista de exclusiones **vacía**.
2. El cliente los ofrece todos. **No es un caso especial**, y por eso se enumera: la respuesta tiene la misma forma con exclusiones y sin ellas.

### FA-002 — Un método está excluido en todos los países

**Cuándo ocurre:** alguien declara la exclusión país por país.

1. Se devuelve igual, **activo y con todas las exclusiones**.
2. El sistema **no lo desactiva solo** ni lo oculta: desactivarlo es otra cosa —y otra columna—, y deducirlo de que la lista esté completa haría que añadir un país lo resucitara.

## 10. Excepciones

**Ninguna.** No hay dato de entrada que pueda ser inválido, ni recurso que pueda no existir. Un catálogo vacío es una lista vacía y no un error.

## 11. Validaciones

**Ninguna**, por lo mismo: no se recibe nada que validar.

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-027` | El sistema devuelve los métodos de pago **activos**, cada uno con su código y su nombre |
| `CA-MV-028` | **Un método desactivado no aparece** |
| `CA-MV-029` | Cada método trae **la lista de países en los que no vale**, con el identificador y el código de cada uno |
| `CA-MV-030` | Un método **sin exclusiones** trae la lista **vacía y presente**, no ausente |
| `CA-MV-031` | La colección va **envuelta**, no como un arreglo en la raíz |
| `CA-MV-032` | Responde a **cualquier actor autenticado**, sin exigir ningún permiso |
| `CA-MV-033` | **Sin autenticar responde `200`**, no `401`: la consulta es pública (`RN-MV-024`) |
| `CA-MV-049` | **El método `GRATIS` no aparece**, ni siquiera estando activo, y **no existe parámetro alguno que lo traiga** |
| `CA-MV-050` | La respuesta **no publica el campo de visibilidad**: si todo lo que sale es `PUBLICO`, declararlo sugiere que puede salir otra cosa |
| `CA-MV-051` | **Sin token sale lo mismo que con token**: la misma lista, en el mismo orden y con las mismas exclusiones |
| `CA-MV-052` | **Sin token tampoco sale lo desactivado ni lo `INTERNO`**: los dos ejes siguen puestos para el anónimo |
| `CA-MV-034` | **Registrar una venta con un método excluido en algún país SE REGISTRA igual**: esta consulta informa y no restringe |

**`CA-MV-049` es el segundo criterio de esta especificación que afirma que el sistema NO hace algo**, y es el que sostiene `RN-MV-023`. Se escribe contra **la ausencia de un parámetro** y no solo contra la respuesta por defecto: comprobar únicamente que no sale hoy dejaría verde el día que alguien añada un `includeHidden` «por simetría con los países».

**`CA-MV-033` está invertido y no borrado**, y es deliberado: hasta el 09-09-2026 exigía `401` sin token. Se conserva el número afirmando lo contrario —igual que `CA-SP-143` y `CA-SP-606` cuando `RN-SP-041` abrió los tres catálogos de `SP`—, para que **el día que alguien cierre la ruta por descuido falle aquí** y no en el formulario de registro de producción.

**`CA-MV-052` parece redundante con `CA-MV-028` y `CA-MV-049`, y no lo es.** Aquellos comprueban los dos ejes **con sesión**; este los comprueba **sin ella**, que es el único camino por el que un descuido futuro —un filtro que se relaje «solo para el público»— llegaría hasta alguien que no ha entrado.

**`CA-MV-034` afirma que el sistema NO hace algo**, y es el criterio que sostiene la decisión de §2. Sin él, «la restricción es informativa» es una frase de un documento; con él, es algo que falla si alguien añade la validación sin decidirlo.

## 13. Casos límite

- **Un país desactivado** (`RF-SP-022` pone `is_active` en falso): la exclusión **sigue devolviéndose**. Retirar un país de la circulación no dice nada sobre dónde vale un medio de pago, y filtrarlo aquí haría que reactivarlo cambiara en silencio lo que se ofrece.
- **Un método excluido y además desactivado**: no aparece. Lo decide `is_active`, y las exclusiones no se miran.
- **Catálogo vacío**: lista vacía. Hoy no puede ocurrir —la migración siembra tres— y no se añade una comprobación para algo que el esquema ya impide.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | **¿De qué país se decide, el día que esto tenga que impedir un cobro?** Del cliente, del vendedor, o uno declarado en la operación. Hoy no hace falta porque nada se valida, y hará falta el día que alguien quiera que sí | Responsable del proyecto | **Abierta**, y **no bloquea este requerimiento** |

**Por qué se devuelven las exclusiones en lugar de filtrar por un país recibido.** Se evaluó aceptar el país como parámetro y devolver solo lo que vale allí. Se descartó por dos motivos: obliga a una llamada por cada cambio de país en la pantalla, y sobre todo **haría creer que el servidor sabe qué país corresponde** — que es justamente la pregunta 1, abierta. Devolver la tabla entera deja la decisión donde hoy está de verdad.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 04-09-2026 | Redacción inicial. Nace por la petición del responsable del proyecto de que **no todos los métodos de pago valgan en todos los países**, y con la precisión que la definió: **la restricción es para el cliente, no para el servidor**. Ocho criterios, de los que `CA-MV-034` es el que importa, porque afirma que registrar una venta con un método excluido **se registra igual** — sin él, la decisión de que esto informe y no restrinja no sería verificable. Queda **una pregunta abierta que no bloquea**: de qué país se decidiría el día que haya que impedir un cobro de verdad. | Responsable del proyecto |
