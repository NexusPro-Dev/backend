# Flujos por caso de uso — `MV` Movimientos

| Campo | Valor |
|---|---|
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
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
    V3 -->|sí| V4{"¿trae producto?"}
    V4 -->|sí| E3["Un depósito no lleva producto<br/>RN-MV-006"]
    V4 -->|no| P1["Registra el movimiento<br/>tipo DEPOSITO, estado CONFIRMADO<br/>congela el vendedor del cliente"]
    P1 --> P2["Emite el comprobante"]
    P2 --> V5{"¿la cuenta estaba<br/>en FTD_PENDIENTE?"}
    V5 -.->|"no · ya operaba"| P4
    V5 -->|sí| P3["Habilita la cuenta<br/>FTD_PENDIENTE → ACTIVO"]
    P3 --> P4["Auditoría de cambios"]
    P4 --> FIN(["Informa el movimiento registrado"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    classDef esc fill:#F7F0E5,stroke:#8A6D2A,color:#4A3A16
    class E1,E2,E3 ex
    class FIN,FIN2 ok
    class P3 esc
```

**La reentrega devuelve lo que ya había y no falla**, y es lo más importante del diagrama. Toda pasarela reintenta cuando no recibe respuesta a tiempo, de modo que la doble entrega **es el caso normal**. Responder un error convertiría un reintento legítimo en una alarma; crear un segundo movimiento duplicaría el dinero. La unicidad la sostiene el esquema (`RN-MV-005`), no esta comprobación: dos entregas simultáneas la burlan.

**`V5` es `RN-MV-011`**, y no es «todo depósito habilita»: es la **transición** la que habilita. El segundo depósito de la misma persona no toca su estado.

**El paso ámbar depende de D-26.** Es la escritura en `users`, que hoy no tiene forma acordada.

---

### `RF-MV-002` · Registrar una compra

Qué producto pidió quién, y a qué precio. **Nace `PENDIENTE`**: el cobro está en marcha y todavía puede fallar.

```mermaid
flowchart TD
    A(["Cliente solicita comprar<br/>producto y cantidad"])
    A --> V1{"¿el producto existe,<br/>está ACTIVO y<br/>no está retirado?"}
    V1 -->|no| E1["Producto no disponible<br/>los tres casos comparten respuesta"]
    V1 -->|sí| V2{"¿es un upgrade<br/>con cantidad distinta de 1?"}
    V2 -->|sí| E2["RN-MV-007 · no se sube<br/>dos veces al mismo nivel"]
    V2 -->|no| V3{"¿el upgrade lleva a un nivel<br/>superior al que ya tiene?"}
    V3 -->|no| E3["No hay nada que comprar<br/>RN-PM-011"]
    V3 -->|sí| V4{"¿el método de pago<br/>existe y está activo?"}
    V4 -->|no| E4["Método no disponible"]
    V4 -->|sí| P1["COPIA del producto:<br/>precio, moneda, vigencia<br/>y membresía destino"]
    P1 --> P2["Congela el vendedor<br/>del cliente"]
    P2 --> P3["Registra el movimiento<br/>tipo COMPRA, estado PENDIENTE<br/>sin comprobante todavía"]
    P3 --> P4["Auditoría de cambios"]
    P4 --> FIN(["Informa el movimiento<br/>y a dónde ir a pagar"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    class E1,E2,E3,E4 ex
    class FIN ok
```

**`P1` es la regla que otros documentos impusieron a este módulo antes de que existiera** (`RN-MV-002`). No se guarda una referencia al producto para leer su precio después: se guarda el precio. Sin eso, corregir un precio en el catálogo reescribiría lo ya vendido — y `requirements/pm.md` §1.4 lo dejó escrito el 26-08-2026, meses antes de que hubiera dónde cumplirlo.

**No hay comprobante todavía**, y es deliberado: `RN-MV-008` lo emite al confirmar. Un comprobante sobre un cobro que puede fallar sería un número gastado y una promesa incumplida.

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
    P1 --> P2["Emite el comprobante<br/>correlativo y sin huecos"]
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
    V2 -->|no| V3{"¿es una REVERSION?"}
    V3 -->|sí| E3["Una reversión no se anula:<br/>sería anular una corrección"]
    V3 -->|no| P1["Inserta el movimiento inverso<br/>tipo REVERSION, apunta al original"]
    P1 --> P2["El original pasa a ANULADO<br/>sus importes NO se tocan"]
    P2 --> V4{"¿el original estaba<br/>CONFIRMADO y aplicó algo?"}
    V4 -.->|"no · estaba PENDIENTE"| P4
    V4 -->|sí| P3["Deshace lo aplicado<br/>DECISIÓN ABIERTA"]
    P3 --> P4["Auditoría de cambios<br/>+ registro de eliminación con el motivo"]
    P4 --> FIN(["Informa la reversión emitida"])

    classDef ex fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E
    classDef ok fill:#E5EEF0,stroke:#2D5A6B,color:#141B1E
    classDef abierto fill:#F7E9E5,stroke:#A33B2A,color:#7A2B1E,stroke-dasharray: 5 5
    class E1,E2,E3 ex
    class FIN ok
    class P3 abierto
```

**`P3` no está resuelto**, y el diagrama lo dice en lugar de fingir que sí. Anular una compra pendiente que la pasarela rechazó —el caso frecuente— no pasa por ahí: no había aplicado nada. Anular una compra ya aplicada plantea preguntas que nadie ha respondido: ¿se retira el nivel concedido? ¿y si entre medias compró otro? Ver `flujos-del-modulo.md` §6.3.

**Escribe en el registro de eliminación aunque no elimine.** El Art. V.13 exige motivo para deshacer, y esto deshace dinero.

---

## 3. Consultar

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
