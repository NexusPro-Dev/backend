# ADR-005 — Modelo de alcance de datos

| Campo | Valor |
|---|---|
| Estado | **Propuesta — pendiente de decisión** |
| Fecha | 27-08-2026 |
| Decide | Responsable del proyecto |
| Redacta | Bonilla Diaz William Steven |
| Cerraría | **D-22** — Cómo se determina de quién puede ver los datos un usuario |
| Issue | #28 |
| Documentos afectados | `security.md` §6 y §12 · `architecture.md` §16 · `requirements/sp.md` §10.2 y §10.7 |

---

## Contexto

**El alcance es un eje ortogonal al permiso, y hoy solo existe el permiso.** Manager, director y agente necesitan el **mismo** `users:read` sobre **conjuntos de datos distintos**. Con lo que hay, o lo ven todo o no lo ven.

Es la decisión abierta que más superficie bloquea del módulo, y desde el 22-08-2026 tiene su **dato de partida** —`user_supervisors`, la estructura persona → persona— pero **no su diseño**: falta cómo se declara el alcance por requerimiento y cómo se verifica.

**Qué queda fuera mientras tanto:**

| Bloqueado | Detalle |
|---|---|
| `RF-SP-042` | No publica el árbol descendente ni la variante «mi equipo»: devuelve solo el superior comercial, nunca el equipo |
| `RF-SP-039` | No admite ninguna lectura hacia abajo |
| Comisiones y finanzas | **No se pueden especificar**: casi todos sus roles se definen por el alcance y no por el permiso |

**Y una deuda que se acumula, que es lo que hace que esto no pueda esperar mucho más.** El listado de roles, el detalle y los cuatro listados de auditoría se resolvieron **sin recibir al actor en el predicado**, y así está escrito en cada uno. El día que D-22 se cierre, son los primeros que hay que revisar — y cuantos más endpoints se publiquen antes, más larga es esa lista.

**El riesgo concreto que `security.md` §6 ya anota:** existiendo `user_supervisors`, es fácil que un requerimiento futuro **resuelva su alcance consultándola por su cuenta**. Eso dejaría el modelo repartido en lugar de definido, que es exactamente lo que D-22 existe para evitar.

## Las tres preguntas que hay que responder

Una decisión de alcance no es «cómo filtro»: son tres cosas, y confundirlas es lo que produce modelos que no se pueden verificar.

1. **Qué determina el alcance de una persona.** ¿Su posición en `user_supervisors`? ¿Su membresía? ¿Una combinación?
2. **Cómo se declara por requerimiento.** Un mismo endpoint puede necesitar alcance distinto según quién pregunte.
3. **Cómo se verifica de forma automatizada.** Sin esto, el modelo es una convención, y una convención de alcance se rompe en silencio: la respuesta sigue siendo un `200` con datos de más.

## Opciones para (2), que es donde se decide todo

### A · Cada caso de uso filtra por su cuenta

El servicio recibe al actor y añade su predicado.

**Coste:** ninguno de arranque. **Lo que se paga:** es exactamente el escenario que `security.md` §6 advierte. Cuarenta y dos endpoints, cuarenta y dos interpretaciones, y el que se olvide **no falla: concede**. No hay forma de verificarlo salvo leyendo los cuarenta y dos.

### B · Un `ScopeResolver` con alcance declarado por requerimiento (recomendada)

Un puerto que, dado el actor y un **tipo de alcance declarado**, devuelve el conjunto de identidades que ese actor alcanza. El caso de uso lo recibe y lo aplica; no lo calcula.

**Coste:** un componente nuevo y una declaración por endpoint que necesite alcance.

**Lo que se compra, y es lo que decide:** que se pueda **verificar por ausencia**. Un endpoint que debería declarar alcance y no lo declara es una comprobación de arquitectura —del mismo tipo que las que ya existen en `LayerRulesTest`—, no una revisión manual. El modelo pasa de convención a regla.

### C · Seguridad a nivel de fila en PostgreSQL

`ROW LEVEL SECURITY` con el actor en una variable de sesión.

**Coste:** el alcance se va a la base de datos, donde **no se puede probar sin levantarla** (Art. VI.3). Es la misma razón por la que `RF-SP-008` descartó detectar ciclos con un disparador.

**Lo que se paga:** garantía muy fuerte —ninguna ruta de escritura la esquiva— a cambio de que las reglas de negocio vivan donde no se leen ni se prueban. Y con una sola conexión de pool compartida, poner el actor en la sesión es una fuente de errores difíciles de reproducir.

## Recomendación

**B**, y con un orden que importa más que la elección:

1. **Primero la comprobación de arquitectura, aunque no haya alcance que aplicar.** Una regla que obligue a todo endpoint de lectura a declarar su alcance —incluido `GLOBAL`, explícito— convierte los cuarenta y dos actuales en una lista que el compilador mantiene, en lugar de una que hay que redescubrir.
2. **Después el resolvedor**, con un solo tipo de alcance: «mi subárbol en `user_supervisors`». Es el que desbloquea `RF-SP-042` y `RF-SP-039`.
3. **Y solo entonces** los requerimientos de comisiones y finanzas.

**Lo que este ADR no propone es diseñar el modelo completo hoy.** Un modelo de alcance que prevea los casos de comisiones antes de que existan sus requerimientos acabará prediciendo mal — y a diferencia de un permiso de más, un alcance mal previsto se descubre cuando alguien ve datos que no le tocan.

## Qué hace falta para cerrarla

1. Confirmar que el alcance se determina por **`user_supervisors`** y no por membresía. Es la pregunta (1) y es de negocio.
2. Decidir si un actor **se ve a sí mismo** dentro de su alcance. Parece obvio y no lo es: cambia todos los predicados.
3. Decidir qué pasa con quien **no tiene superior ni subordinados**: ¿alcance vacío o alcance de sí mismo?
4. Aceptar o rechazar el orden de la recomendación, en particular que la comprobación de arquitectura vaya **antes** que el resolvedor.
