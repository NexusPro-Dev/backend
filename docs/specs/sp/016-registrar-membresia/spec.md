# SPEC — `RF-SP-016` Registrar membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-016` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada el | 26-08-2026 — ver §15 |

---

## 1. Objetivo

Definir un nivel de acceso para los consumidores del sistema, situándolo en el orden correcto respecto de los ya existentes.

## 2. Contexto

La membresía determina a qué servicios y contenidos llega un cliente: hay cursos abiertos a todos y cursos reservados a niveles superiores. Es al consumidor lo que el rol es al funcionario, pero opera en un eje distinto: el rol dice **qué puede hacer**, la membresía **hasta dónde alcanza**.

Las membresías forman una **cadena ordenada, no un árbol**: cada una está sujeta a una de mayor nivel y solo la superior queda libre. Al crear una se indica cuál será su **hija**, de modo que la nueva membresía se **inserta en medio** y la cadena se reordena.

Esa mecánica de inserción es lo que distingue este requerimiento de un alta corriente: no añade un elemento al final, lo intercala.

La cadena es **única para todo el sistema**: no hay una cadena por rol ni membresías reservadas a roles concretos. Es lo que hace que «nivel 3» signifique lo mismo dicho desde cualquier parte del sistema, y lo que permite que un módulo de contenidos exija un nivel mínimo sin preguntar además de qué rol se trata.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Registra membresías |
| Administrador | Registra membresías |

## 4. Alcance

### 4.1 Incluye

- Alta de una membresía con su código, nombre y descripción.
- Indicación de su membresía hija y reordenamiento de la cadena.

### 4.2 No incluye

- Editar o eliminar membresías: son inmutables una vez creadas (`RN-SP-008`).
- Asignar membresías a personas → `RF-SP-032` y `RF-SP-033`.
- Definir qué contenido exige qué nivel: corresponde a los módulos de academia y productos.
- Cadenas distintas por rol: la cadena es única y alcanza a cualquier consumidor.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-006` | Toda membresía está sujeta a una de mayor nivel, salvo la superior | `requirements/sp.md` §5.1 |
| `RN-SP-007` | Al crear se indica la membresía hija, si la hay, y el sistema reordena la jerarquía | `requirements/sp.md` §5.1 |
| `RN-SP-024` | La membresía declara el color con el que el frontend la pinta: seis dígitos hexadecimales sin `#` | `requirements/sp.md` §5.1 |
| `RN-SP-008` | Las membresías no se editan ni eliminan | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Código | Sí | Identificador corto y estable | Único |
| Nombre | Sí | Nombre legible del nivel | Único |
| Descripción | No | Qué alcance concede | — |
| Color | Sí | Color con el que el frontend pinta el nivel | Seis dígitos hexadecimales **sin `#`**; se normaliza a mayúsculas y no puede repetirse entre membresías |
| Membresía hija | No | Membresía que quedará por debajo de la nueva | Debe existir; su superior actual pasará a ser la nueva membresía |

Si no se indica membresía hija, la nueva se sitúa en el extremo inferior de la cadena.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Membresía | Membresía creada, con su nivel, su posición en la cadena y **su color tal como quedó almacenado** —en mayúsculas y sin `#`—, para que quien la registró vea qué se guardó y no lo que envió |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de membresías.
- Si se indica membresía hija, esta existe.

**Postcondiciones**

- La membresía queda insertada en la posición correspondiente.
- La cadena sigue siendo lineal: cada membresía tiene como mucho una hija.
- Los niveles de las membresías afectadas quedan recalculados.
- Queda constancia en la auditoría de cambios de la membresía creada **y de cada membresía que el reordenamiento haya modificado**, todos los eventos bajo el mismo identificador de correlación.

## 8. Flujo principal

1. El actor solicita registrar una membresía y proporciona sus datos.
2. El sistema valida el formato y la obligatoriedad.
3. El sistema verifica que el código y el nombre no estén en uso.
4. El sistema verifica que la membresía hija indicada exista.
5. El sistema sitúa la nueva membresía por encima de la hija indicada y por debajo de la superior actual de esa hija.
6. El sistema recalcula los niveles de las membresías afectadas.
7. El sistema registra en la auditoría de cambios el alta de la nueva membresía y la modificación de cada membresía afectada por el reordenamiento.
8. El sistema informa la membresía creada.

## 9. Flujos alternativos

### FA-001 — Primera membresía del sistema

**Cuándo ocurre:** no existe ninguna membresía todavía.

1. La nueva se convierte en la membresía superior, sin membresía por encima.
2. No se indica hija, porque no hay ninguna.

### FA-002 — Inserción en el extremo inferior

**Cuándo ocurre:** no se indica membresía hija.

1. La nueva se sitúa por debajo de todas las existentes.
2. No hay reordenamiento: nada queda por debajo de ella.

## 10. Excepciones

### EX-001 — Código o nombre ya en uso

**Condición:** existe otra membresía con el mismo código o nombre.
**Respuesta del sistema:** rechaza el alta e informa cuál está duplicado.

### EX-002 — Membresía hija inexistente

**Condición:** la membresía hija indicada no existe.
**Respuesta del sistema:** rechaza el alta e informa que la membresía indicada no es válida.

### EX-003 — La cadena cambió durante la operación

**Condición:** otra alta simultánea reordenó la cadena mientras esta se resolvía, de modo que la posición calculada ya no es válida.
**Respuesta del sistema:** rechaza el alta sin escribir nada e informa que debe reintentarse. No es un dato inválido: la misma petición, repetida, es correcta.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Código obligatorio | El código de la membresía es obligatorio. |
| `VAL-002` | Nombre obligatorio | El nombre de la membresía es obligatorio. |
| `VAL-003` | Código único | Ya existe una membresía con ese código. |
| `VAL-004` | Nombre único, **sin distinguir mayúsculas ni acentos** | Ya existe una membresía con ese nombre. |
| `VAL-005` | Membresía hija existente | La membresía indicada no existe. |
| `VAL-006` | Formato del código | El código solo admite letras mayúsculas, dígitos y guion bajo, y debe empezar por letra. |
| `VAL-007` | Color obligatorio | El color de la membresía es obligatorio. |
| `VAL-008` | Formato del color | El color admite exactamente seis dígitos hexadecimales, sin el carácter `#`. |
| `VAL-009` | Color único | Ya existe una membresía con ese color. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-111` | El sistema registra la primera membresía como la superior de la cadena |
| `CA-SP-112` | El sistema inserta una membresía por encima de la hija indicada y reordena la cadena |
| `CA-SP-113` | El sistema sitúa la membresía en el extremo inferior cuando no se indica hija |
| `CA-SP-114` | Tras la inserción, cada membresía sigue teniendo como mucho una hija |
| `CA-SP-115` | El sistema recalcula los niveles de las membresías afectadas |
| `CA-SP-116` | El sistema rechaza el alta con código o nombre ya en uso |
| `CA-SP-117` | El sistema rechaza el alta con una membresía hija inexistente |
| `CA-SP-118` | El sistema registra en la auditoría de cambios un evento por la membresía creada y uno por cada membresía que el reordenamiento haya modificado, todos con el mismo identificador de correlación |
| `CA-SP-119` | El sistema rechaza el alta a un actor sin el permiso de creación de membresías |
| `CA-SP-347` | El sistema rechaza un código que no cumpla el formato de mayúsculas, dígitos y guion bajo |
| `CA-SP-348` | El sistema rechaza el nombre que solo difiere de otro existente en mayúsculas o acentos |
| `CA-SP-349` | El sistema informa el empate concurrente con un error propio, distinto del de membresía hija inexistente |
| `CA-SP-487` | El sistema rechaza el alta sin color, y la rechaza con un color que no sean exactamente seis dígitos hexadecimales — incluido el que llega con `#` delante |
| `CA-SP-488` | El sistema almacena y devuelve el color **en mayúsculas**, sea cual sea la caja con la que se envió |
| `CA-SP-489` | El sistema rechaza el alta con un color ya usado por otra membresía, y lo distingue del rechazo por código o nombre repetido |

## 13. Casos límite

- **Insertar por encima de la membresía superior:** convierte a la nueva en la superior. Debe admitirse.
- **La hija indicada ya tiene otra superior:** es el caso normal, no un error. La inserción reasigna la superior de esa hija; por eso la operación se llama insertar y no añadir. Si la restricción única del esquema llegara a rechazarlo, sería un defecto del sistema y no una validación de negocio.
- **Inserción concurrente sobre la misma hija:** ambas pretenderían ser su superior. La restricción única debe resolver el empate sin dejar la cadena bifurcada.
- **Cadena con una sola membresía:** insertar por encima o por debajo son las dos únicas posibilidades.
- **Consumidores ya asignados:** insertar un nivel intermedio cambia el alcance relativo de quienes ya tenían membresía, y eso es deliberado: conservan la suya y el acceso se sigue evaluando por nivel.
- **Nombre mal escrito:** no hay corrección posible. Es la consecuencia asumida de `RN-SP-008`, y toda la defensa está en el momento del alta.

## 14. Preguntas abiertas

Ninguna. Las cuatro se resolvieron el 21-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se admite alguna corrección de un nombre mal escrito? | **No: la membresía sigue siendo inmutable.** Se estudió darle un indicador de activo, como el que sí reciben países y monedas, y se descartó por el efecto sobre la cadena: desactivar un eslabón intermedio deja un hueco en un orden lineal y obliga a decidir qué le pasa a quien lo tenía asignado. Una membresía mal escrita se corrige por migración, que es una operación excepcional y trazable |
| 2 | ¿Se audita cada membresía afectada por el reordenamiento? | **Cada una por separado**, todas bajo el mismo identificador de correlación. Es la única forma de que la auditoría de cambios responda «quién cambió el nivel de esta membresía», que es como se pregunta en la práctica: un único evento sobre la creada dejaría los cambios de las demás sin autor, y `RF-SP-011` es la única fuente de esa autoría |
| 3 | ¿Se recalcula el acceso de quienes ya tenían membresía? | **No: conservan la suya.** El acceso se evalúa siempre por nivel, de modo que insertar un intermedio cambia el alcance relativo de los ya asignados. Eso no es un efecto secundario, es para lo que sirve insertar: si el alcance de cada persona se congelara, la cadena dejaría de significar nada |
| 4 | ¿Cada membresía se asocia a roles concretos? | **No: la cadena es única y global.** «Crear nuevas membresías para ciertos roles» de la guía describe a quién alcanzan —a los consumidores—, no que cada membresía se declare para un rol. Varias cadenas obligarían a comparar niveles entre cadenas distintas, que es una comparación sin significado, y `RN-SP-006` presupone una sola |

### Corrección posterior a la aprobación

Aplicada el 21-08-2026 al aprobar el `plan.md`, conforme al Art. I.7: la especificación vuelve a su compuerta, se corrige y se deja constancia.

| # | Defecto | Corrección |
|---|---|---|
| 1 | El empate concurrente del tercer caso límite de §13 no tenía excepción propia, y el plan lo referenciaba con `EX-002`, que es la membresía hija inexistente. Dos hechos distintos con un solo código, y con estados HTTP distintos | Se añade `EX-003` con su condición y su respuesta, y `CA-SP-349` para que la distinción quede verificada. Es la misma corrección que se aplicó en `RF-SP-008` y `RF-SP-009` |
| 2 | `VAL-004` exigía nombre único sin decir cómo se compara, y no había validación de formato para el código. Con `RN-SP-008` haciendo la membresía inmutable, `Plata` y `plata` habrían podido convivir para siempre, y un código en minúsculas habría quedado sin corrección posible | `VAL-004` pasa a comparar **sin distinguir mayúsculas ni acentos**, y se añade `VAL-006` con el formato de código ya aprobado para los roles. Se añaden `CA-SP-347` y `CA-SP-348`. Tenía que decidirse ahora: después de la primera membresía, la corrección exige migrar datos |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **La membresía declara su color** (`RN-SP-024`), por decisión del responsable del proyecto: seis dígitos hexadecimales **sin `#`**, obligatorio y único, normalizado a mayúsculas al escribir. El `#` no viaja porque es notación de CSS y no parte del valor. Entra como dato de entrada en §6.1, sale en §6.2 **tal como quedó almacenado** —para que quien registra vea lo guardado y no lo enviado—, y trae `VAL-007` a `VAL-009` y `CA-SP-487` a `CA-SP-489`. Es el **primer campo puramente estético** del módulo, y con `RN-SP-008` intacta **no se podrá corregir**: el hueco se acepta a conciencia y su condición de reapertura está escrita en `requirements/sp.md` §5.1. | Responsable técnico |
| 0.1.0 | 21-08-2026 | Redacción inicial. Las cuatro preguntas abiertas se resolvieron antes de aprobar; ver §14. | Responsable técnico |
