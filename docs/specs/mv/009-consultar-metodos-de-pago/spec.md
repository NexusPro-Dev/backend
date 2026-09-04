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
| Cualquiera autenticado | Consulta con qué se puede pagar. **No hace falta permiso**: es el mismo criterio que `RF-SP-039` y `RF-MV-008` — preguntar qué opciones hay para pagar lo propio no es una operación privilegiada |

## 4. Alcance

### 4.1 Incluye

- Devolver los métodos de pago **activos**.
- Devolver, de cada uno, **en qué países no vale**.
- Un orden estable.

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

**Este requerimiento hace cumplir una y media.** `RN-MV-019` la cumple entera —es la única operación que la ejerce—. De `RN-MV-018` cumple **la mitad que le toca**: no ofrece lo desactivado. La otra mitad —que lo ya pagado siga valiendo— no es de aquí, es de quien lee una venta vieja.

## 6. Datos

### 6.1 Entrada

**Ninguna.** No hay parámetros, ni filtros, ni paginación.

**No se recibe el país**, y es la ausencia que define la operación. Aceptarlo convertiría esto en «qué puedo usar en Colombia», que es una pregunta que **el servidor no tiene por qué responder** cuando no sabe de qué país es quien pregunta — y que el cliente responde solo con lo que esta respuesta le da.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Métodos | Los **activos**, cada uno con su identificador, su código y su nombre |
| — Países excluidos | De cada método, **dónde no vale**. Vacío significa que vale en todas partes |

**La lista de exclusiones va vacía y no ausente.** Es la misma decisión que `RF-MV-001` toma con el descuento: un cliente que tenga que distinguir «sin exclusiones» de «no vino el campo» acabará tratándolo como opcional para siempre.

**Y el nombre del país no viaja**, solo su identificador y su código. Quien pinta países ya tiene su catálogo (`RF-SP-021`), y repetir el nombre aquí lo dejaría desincronizado el día que se corrija una tilde.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado.

**Postcondiciones**

- **Ninguna.** No escribe nada, no audita nada y no cambia nada.

## 8. Flujo principal

1. El actor pide los métodos de pago.
2. El sistema devuelve los **activos**, cada uno con los países en los que no vale.

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
| `CA-MV-033` | **Sin autenticar responde `401`** |
| `CA-MV-034` | **Registrar una venta con un método excluido en algún país SE REGISTRA igual**: esta consulta informa y no restringe |

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
