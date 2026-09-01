# Flujos por caso de uso — `MV` Movimientos

| Campo | Valor |
|---|---|
| Módulo | `MV` — Movimientos |
| Versión | 0.5.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 01-09-2026 |
| Última actualización | 01-09-2026 |

!!! info "Qué va en este documento"

    Un diagrama por caso de uso: qué puede hacer el actor, qué verifica el sistema en cada paso y por dónde sale la operación cuando una verificación falla.

    **Se dibuja antes de que existan las tripletas**, al revés que en `SP`. De modo que aquí los diagramas **no transcriben** una `§8 Flujo principal` ya aprobada: la **proponen**, a partir de las reglas de [`requirements/mv.md`](../../requirements/mv.md). Cuando cada `spec.md` se escriba, mandará ella.

!!! note "Convención de los diagramas"

    | Forma | Significado |
    |---|---|
    | Cápsula | Acción del actor, o respuesta final del sistema |
    | Rombo | Verificación del sistema |
    | Rectángulo | Paso del sistema que produce efecto |
    | Recuadro rojo | Rechazo, con la regla que lo motiva |
    | Recuadro ámbar | Escritura **fuera de `MV`** — depende de **D-26** |
    | Línea punteada | Camino alternativo: no es error |

---

## 1. Registrar

### `RF-MV-001` · Registrar un depósito

El dinero que entra a nombre de un cliente. **Nace `CONFIRMADO`**: es un hecho que ya ocurrió y del que un tercero avisa.

```mermaid
flowchart TD
    A(["Bróker o administrador<br/>notifica un depósito"])
    A --> V1{"¿la referencia externa<br/>ya se registró para<br/>esta pasarela?"}
    V1 -.->|"sí · reentrega"| FIN2(["Devuelve el movimiento<br/>que ya existía"])
    V1 -->|no| V2{"¿el cliente existe<br/>y no está eliminado?"}
    V2 -->|no| E1["Cliente inexistente<br/>RN-MV-006"]
    V2 -->|sí| V3{"¿importe mayor que cero<br/>y con los decimales<br/>de su moneda?"}
    V3 -->|no| E2["Importe inválido<br/>RN-MV-009"]
    V3 -->|sí| V4{"¿es el PRIMER depósito<br/>de esta persona?"}
    V4 -->|"es el FTD"| P0["Añade UNA línea con el producto<br/>de la membresía gratuita · RN-MV-024"]
    V4 -->|no| P1
    P0 --> P1["Registra el movimiento<br/>tipo DEPOSITO, estado CONFIRMADO<br/>codigo DEP-AAAAMMDD-XXXXXX<br/>congela el vendedor del cliente"]
    P1 --> P2["Emite el comprobante"]
    P2 --> V5{"¿la cuenta estaba<br/>en FTD_PENDIENTE?"}
    V5 -.->|"no · ya operaba"| P4
    V5 -->|sí| V6{"¿el importe alcanza el precio<br/>del producto gratuito?<br/>RN-MV-030"}
    V6 -->|no| D0["No habilita todavía:<br/>el depósito queda registrado"]
    V6 -->|sí| P3["SP habilita la cuenta · D-26<br/>FTD_PENDIENTE → ACTIVO"]
    P3 --> P4["Auditoría de cambios"]
    P4 --> FIN(["Informa el movimiento registrado"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    classDef esc fill:#F7F0E5,stroke:#8A6D2A,color:#4A3A16
    class E1,E2 ex
    class FIN,FIN2 ok
    class P3 esc
    class D0 pend
    classDef pend fill:#F7F0E5,stroke:#8A6D2A,color:#4A3A16
```

**La reentrega devuelve lo que ya había y no falla**, y es lo más importante del diagrama. Toda pasarela reintenta cuando no recibe respuesta a tiempo, de modo que la doble entrega **es el caso normal**. Responder un error convertiría un reintento legítimo en una alarma; crear un segundo movimiento duplicaría el dinero. La unicidad la sostiene el esquema (`RN-MV-005`), no esta comprobación: dos entregas simultáneas la burlan.

**`V5` es `RN-MV-011`**, y no es «todo depósito habilita»: es la **transición** la que habilita. El segundo depósito de la misma persona no toca su estado.

**El paso ámbar es D-26, ya cerrada** (01-09-2026): `SP` publica «habilitar cuenta por depósito» y `MV` la invoca, **síncrona y en la misma transacción** — si esa escritura falla, falla el registro entero y no queda un depósito confirmado con la cuenta retenida.

**`V4` y `V6` son la novedad del 01-09-2026.** El **FTD lleva una línea** con el producto de la membresía gratuita —el mismo que fija su importe—, y por eso se comisiona como cualquier otra venta sin tocarle la firma a `RF-CM-005`. Y habilita **si el importe alcanza ese precio** (`RN-MV-030`): **al menos y no exactamente**, porque exigir el importe exacto convertiría una comisión bancaria en un bloqueo.

---

### `RF-MV-002` · Registrar una compra

Qué producto pidió quién, y a qué precio. **Nace `PENDIENTE`**: el cobro está en marcha y todavía puede fallar.

```mermaid
flowchart TD
    A(["Cliente solicita comprar<br/>UNA O VARIAS líneas:<br/>producto y cantidad"])
    A --> V0{"¿al menos<br/>una línea?"}
    V0 -->|no| E5["RN-MV-024 · una compra<br/>sin líneas no dice qué se compró"]
    V0 -->|sí| V1{"POR CADA LÍNEA:<br/>¿el producto existe,<br/>está ACTIVO y no retirado?"}
    V1 -->|no| E1["Producto no disponible<br/>los tres casos comparten respuesta"]
    V1 -->|sí| V2{"¿es un upgrade<br/>con cantidad distinta de 1?"}
    V2 -->|sí| E2["RN-MV-007 · no se sube<br/>dos veces al mismo nivel"]
    V2 -->|no| V2b{"¿hay MÁS DE UN<br/>upgrade entre las líneas?"}
    V2b -->|sí| E6["RN-MV-025 · dos cambios de nivel<br/>en una sola operación"]
    V2b -->|no| V2c{"¿todas las líneas comparten<br/>la misma moneda?"}
    V2c -->|no| E7["RN-MV-026 · sin tasa de cambio<br/>no hay total que calcular"]
    V2c -->|sí| V2d{"¿se repite algún<br/>producto entre las líneas?"}
    V2d -->|sí| E8["RN-MV-028 · el mismo producto<br/>dos veces es uno con doble cantidad"]
    V2d -->|no| V3{"¿el upgrade lleva a un nivel<br/>superior al que ya tiene?"}
    V3 -->|no| E3["No hay nada que comprar<br/>RN-PM-011"]
    V3 -->|sí| V4{"¿el método de pago<br/>existe y está activo?"}
    V4 -->|no| E4["Método no disponible"]
    V4 -->|sí| P1["COPIA POR LÍNEA:<br/>precio, vigencia y<br/>membresía destino"]
    P1 --> P2["Congela el vendedor<br/>del cliente"]
    P2 --> P3["Registra la cabecera y sus líneas<br/>tipo COMPRA, estado PENDIENTE<br/>total = SUMA de las líneas, congelado<br/>codigo COM-AAAAMMDD-XXXXXX"]
    P3 --> P4["Auditoría de cambios"]
    P4 --> FIN(["Informa el movimiento<br/>y a dónde ir a pagar"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1,E2,E3,E4,E5,E6,E7,E8 ex
    class FIN ok
```

**`P1` es la regla que otros documentos impusieron a este módulo antes de que existiera** (`RN-MV-002`). No se guarda una referencia al producto para leer su precio después: se guarda el precio. Sin eso, corregir un precio en el catálogo reescribiría lo ya vendido — y `requirements/pm.md` §1.4 lo dejó escrito el 26-08-2026, meses antes de que hubiera dónde cumplirlo.

**No hay comprobante todavía**, y es deliberado: `RN-MV-008` lo emite al confirmar. Un comprobante sobre un cobro que puede fallar sería un número gastado.

**Las cuatro verificaciones nuevas del centro son el precio de admitir varias líneas**, y ninguna existía cuando un movimiento llevaba un solo producto. La que importa es `RN-MV-025`: **dos upgrades en la misma compra son dos cambios de nivel en una sola operación**, y no hay forma no arbitraria de decidir en cuál queda la persona. Las cuatro **cruzan dos tablas**, así que ningún `CHECK` las sostiene y viven en el caso de uso.

**El código sí se emite ya**, y esa asimetría es el motivo de que sean dos códigos distintos: el del movimiento identifica algo que **ya existe** aunque no se haya pagado; el del comprobante numera un documento que **solo existe si se pagó**. La fecha del código sale de `occurred_at` (`RN-MV-017`), no del reloj.

---

## 2. Confirmar y anular

### `RF-MV-003` · Confirmar un movimiento pendiente

Donde una compra se vuelve real, y donde se produce **todo** el efecto.

```mermaid
flowchart TD
    A(["Pasarela responde,<br/>o un actor confirma a mano"])
    A --> V1{"¿el movimiento existe?"}
    V1 -->|no| E1["No existe"]
    V1 -->|sí| V2{"¿está PENDIENTE?"}
    V2 -.->|"ya CONFIRMADO"| FIN2(["Devuelve el movimiento<br/>sin volver a aplicar nada"])
    V2 -->|"ANULADO"| E2["Un movimiento anulado<br/>no se confirma"]
    V2 -->|sí| P1["Estado → CONFIRMADO<br/>con su instante"]
    P1 --> P2["Emite el comprobante<br/>consecutivo ÚNICO POR TIPO"]
    P2 --> V3{"¿lleva membresía<br/>destino?"}
    V3 -.->|no| P4
    V3 -->|sí| P3["Aplica el upgrade<br/>con la vigencia COPIADA"]
    P3 --> P4["Auditoría de cambios"]
    P4 --> FIN(["Informa el movimiento confirmado"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    classDef esc fill:#F7F0E5,stroke:#8A6D2A,color:#4A3A16
    class E1,E2 ex
    class FIN,FIN2 ok
    class P3 esc
```

**Confirmar dos veces no aplica dos veces.** Es la misma defensa que `RF-MV-001`, en el otro extremo del mismo problema: la pasarela reintenta su notificación, y sin este camino punteado el cliente recibiría dos upgrades por un pago.

**`P3` usa la vigencia del movimiento, no la del producto.** Si entre la compra y la confirmación alguien cambió `validity_days` en el catálogo, manda lo que se compró.

**El paso ámbar es D-26 otra vez**, y es el segundo de los dos únicos puntos donde `MV` escribe fuera.

---

### `RF-MV-007` · Anular un movimiento

No borra. **Inserta.**

```mermaid
flowchart TD
    A(["Actor solicita anular<br/>con motivo obligatorio"])
    A --> V1{"¿el movimiento existe?"}
    V1 -->|no| E1["No existe"]
    V1 -->|sí| V2{"¿ya está ANULADO?"}
    V2 -->|sí| E2["No se anula dos veces"]
    V2 -->|no| V3{"¿YA PRODUJO EFECTOS?<br/>nivel concedido, comisión causada"}
    V3 -->|sí| E4["RN-MV-031 · lo aplicado NO se anula.<br/>Deshacerlo es otra operación,<br/>y NINGUNA existe todavía"]
    V3 -->|no| V3b{"¿es una REVERSION?"}
    V3b -->|sí| E3["Una reversión no se anula:<br/>sería anular una corrección"]
    V3b -->|no| P1["Inserta el movimiento inverso<br/>tipo REVERSION, apunta al original"]
    P1 --> P2["El original pasa a ANULADO<br/>sus importes NO se tocan"]
    P2 --> P4["Auditoría de cambios<br/>+ registro de eliminación con el motivo"]
    P4 --> FIN(["Informa la reversión emitida"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1,E2,E3,E4 ex
    class FIN ok
```

**`V3` es la puerta que el responsable decidió cerrar** (`RN-MV-031`, 01-09-2026). Anular una compra pendiente que la pasarela rechazó —el caso frecuente— pasa de largo: no había aplicado nada. Anular una compra **ya aplicada** ya no se admite por esta vía.

**Y eso deja un hueco a la vista en lugar de resolverlo**: retirar un nivel concedido o revertir una comisión causada son **operaciones distintas que no existen**. Se eligió así sobre deshacerlo todo —que obligaría a quitarle el nivel a quien lleva un mes usándolo— y sobre revertir solo el dinero —que dejaría el libro y el acceso discrepando—. **El precio: hoy un movimiento aplicado por error no tiene corrección por ninguna vía.**

**Escribe en el registro de eliminación aunque no elimine.** El Art. V.13 exige motivo para deshacer, y esto deshace dinero.

---

## 3. Lo que llega de fuera

### `RF-MV-009` · Recibir una notificación de un sistema externo

El único endpoint del módulo que **no lo llama una persona**. Y el orden de sus pasos es la mitad del requerimiento.

```mermaid
flowchart TD
    A(["Pasarela o bróker<br/>envía su notificación"])
    A --> V1{"¿dentro del<br/>límite de tasa?"}
    V1 -->|no| E1["429 · sin esta puerta<br/>el endpoint es un vertedero"]
    V1 -->|sí| P1["Verifica la firma<br/>NUNCA se guarda el secreto"]
    P1 --> P2["GUARDA la notificación<br/>verbatim, con la firma marcada<br/>válida o no"]
    P2 --> FIN(["Responde 200 · ya está a salvo"])
    FIN --> V2{"¿la firma<br/>era válida?"}
    V2 -->|no| D1["Estado DESCARTADA<br/>evidencia de intento de falsificación"]
    V2 -->|sí| V3{"¿este identificador de evento<br/>ya se recibió de este emisor?"}
    V3 -.->|"sí · reentrega"| D2["Estado DESCARTADA<br/>no se vuelve a interpretar"]
    V3 -->|no| V4{"¿el tipo de evento<br/>nos interesa?"}
    V4 -.->|no| D3["Estado DESCARTADA<br/>no todo lo que avisan nos afecta"]
    V4 -->|sí| P3["Interpreta y produce el movimiento<br/>RF-MV-001 o RF-MV-003"]
    P3 --> V5{"¿se pudo?"}
    V5 -->|no| D4["Estado FALLIDA<br/>con el motivo · reprocesable"]
    V5 -->|sí| D5["Estado PROCESADA<br/>apunta al movimiento"]

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    classDef guarda fill:#E8F0E5,stroke:#3D6B2D,color:#1B2E14
    class E1 ex
    class FIN ok
    class P2 guarda
```

**Todo lo que hay debajo de la cápsula verde ocurre después de responder**, y ese es el punto entero del diagrama. `RN-MV-012`: guardar va **antes** de interpretar, y responder va antes de trabajar.

- **Guardar después de procesar** sería tener la notificación en todos los casos **menos en el único que importa**: aquel en que procesarla falló.
- **Responder después de procesar** convertiría cada operación lenta en una reentrega, porque las pasarelas tienen espera corta y reintentan. Y cada reentrega es otro procesamiento — la forma más fácil de duplicar dinero.

**La firma se verifica antes de guardar y no impide guardar.** Es `RN-MV-014`: lo que no valida se conserva marcado, porque «alguien está intentando falsificar confirmaciones de pago» vale más visible en una tabla que en un `401` que nadie mira. Por eso el límite de tasa es la primera puerta y no una mejora posterior.

**Cuatro de los cinco desenlaces no producen movimiento**, y ninguno es un error del emisor. Es lo que justifica que la notificación sea una tabla y no una columna de `movements`.

---

### `RF-MV-011` · Reprocesar una notificación

```mermaid
flowchart TD
    A(["Actor con movements:reprocess"])
    A --> V1{"¿la notificación existe?"}
    V1 -->|no| E1["No existe"]
    V1 -->|sí| V2{"¿conserva su<br/>documento crudo?"}
    V2 -->|no| E2["Purgado a los 180 días<br/>RN-MV-015 · ya no se puede"]
    V2 -->|sí| V3{"¿está PROCESADA?"}
    V3 -->|sí| E3["Ya produjo su movimiento<br/>reprocesar duplicaría dinero"]
    V3 -->|no| V4{"¿la firma<br/>era válida?"}
    V4 -->|no| E4["Lo descartado por firma<br/>no se reprocesa nunca"]
    V4 -->|sí| P1["Vuelve a interpretar<br/>el mismo documento"]
    P1 --> P2["Actualiza el estado<br/>y apunta al movimiento"]
    P2 --> FIN(["Informa el resultado"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1,E2,E3,E4 ex
    class FIN ok
```

**Las cuatro salidas son negativas y las cuatro importan.** Esta es la operación con más capacidad de hacer daño del módulo —reprocesar algo ya procesado duplica dinero— y por eso cada puerta está antes de tocar nada.

**La purga la inutiliza, y es correcto.** A los 180 días el documento ya no está y la fila sí: se sabe qué llegó y qué produjo, pero no se puede volver a interpretar. Es el precio declarado de `RN-MV-015`, y el motivo de que el plazo cubra la ventana de contracargo con margen.

**Lo descartado por firma inválida no se reprocesa jamás**, ni corrigiendo la clave. Si la firma no validó, no consta que el mensaje viniera de quien dice: reprocesarlo sería procesar algo que nadie autenticó.

---

### `RF-MV-010` · Consultar las notificaciones recibidas

```mermaid
flowchart TD
    A(["Actor con movements:read<br/>filtra por emisor, estado o fechas"])
    A --> P1["Devuelve las notificaciones<br/>con su estado y qué movimiento produjeron"]
    P1 --> V1{"¿conserva su<br/>documento crudo?"}
    V1 -.->|"no · purgado"| FIN
    V1 -->|sí| P2["Incluye el documento verbatim"]
    P2 --> FIN(["Página de notificaciones"])

    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class FIN ok
```

**Es la consulta de conciliación**: la que responde «la pasarela dice que nos avisó, ¿lo hizo?». Sin ella, la tabla guarda evidencia que nadie puede mirar.

**El documento lleva datos personales de terceros**, y por eso exige `movements:read` y no basta estar autenticado — al revés que `RF-MV-006`, que solo devuelve lo propio.

---

## 4. Consultar

### `RF-MV-004` · Consultar movimientos

```mermaid
flowchart TD
    A(["Actor con movements:read<br/>filtra por tipo, estado,<br/>cliente, vendedor o fechas"])
    A --> V1{"¿paginación y filtros<br/>dentro de su dominio?"}
    V1 -->|no| E1["Filtros inválidos"]
    V1 -->|sí| P1["Consulta con alcance GLOBAL<br/>quien tiene el permiso ve todo"]
    P1 --> FIN(["Página de movimientos"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1 ex
    class FIN ok
```

**El alcance global es una decisión aplazada, no un descuido.** Que un director vea los movimientos de todos y no solo los de su equipo depende de **D-22**, abierta desde hace semanas. Es el mismo aplazamiento explícito que `requirements/cm.md` §5.3 hizo con las tarifas, y esta consulta es la que habrá que revisar el día que D-22 se cierre.

---

### `RF-MV-005` · Consultar el detalle, con su comprobante

```mermaid
flowchart TD
    A(["Actor con movements:read<br/>pide un movimiento"])
    A --> V1{"¿existe?"}
    V1 -->|no| E1["No existe"]
    V1 -->|sí| P1["Devuelve el movimiento<br/>con los datos CONGELADOS:<br/>precio, moneda, vigencia y vendedor<br/>del momento, no los de hoy"]
    P1 --> V2{"¿está CONFIRMADO?"}
    V2 -.->|no| FIN
    V2 -->|sí| P2["Añade el comprobante<br/>NO es una factura fiscal"]
    P2 --> FIN(["Detalle del movimiento"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1 ex
    class FIN ok
```

**Devuelve datos, no un PDF.** El backend no maneja ningún binario: ningún controlador acepta ni devuelve ficheros, y abrirlo es una decisión de infraestructura que ningún requerimiento respalda. Quien lo pinte, lo pinta.

**Un movimiento retirado o de un producto retirado se consulta con normalidad.** Preguntar qué se pagó por algo que ya no se vende es exactamente lo que una revisión atrasada necesita — el mismo criterio que `RF-CM-005` aplicó a las tarifas.

---

### `RF-MV-006` · Consultar los movimientos propios

```mermaid
flowchart TD
    A(["Cualquier persona autenticada"])
    A --> P1["El actor sale del token<br/>NO hay parámetro de persona"]
    P1 --> P2["Sus movimientos, con su comprobante"]
    P2 --> FIN(["Página de sus movimientos"])

    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class FIN ok
```

**No exige permiso y no admite parámetro**, por el mismo criterio con el que `RF-SP-039` publicó el perfil propio y `RF-PM-007` la oferta propia: pedir `movements:read` para que alguien vea sus tres compras le daría el libro entero. Y sin parámetro no hay forma de preguntar por un tercero — **es lo único que hace que esta consulta no dependa de D-22**.

---

### `RF-MV-008` · Consultar los métodos de pago

```mermaid
flowchart TD
    A(["Cualquier persona autenticada"])
    A --> P1["Devuelve los métodos ACTIVOS"]
    P1 --> FIN(["Catálogo de métodos de pago"])

    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class FIN ok
```

**Solo los activos, y sin permiso.** Quien va a pagar necesita saber con qué puede pagar, y un método desactivado no se ofrece — aunque **siga siendo válido en los movimientos que lo usaron**, por el mismo criterio de `RN-PM-008` con las monedas.

**Método no es pasarela** (`RN-MV-010`): esta consulta devuelve con qué se puede pagar, no quién lo procesa. Qué proveedor atiende cada método es configuración, no catálogo de negocio.
