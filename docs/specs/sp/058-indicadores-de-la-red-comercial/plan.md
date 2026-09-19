# PLAN — `RF-SP-058` Consultar los indicadores de la red comercial

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-058` |
| Especificación | [`spec.md`](spec.md), aprobada el 10-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 10-09-2026 |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`broker-accounts:read-indicators`** y no `broker-accounts:read` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `broker-accounts:read` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `broker-accounts:read`. Las menciones de `broker-accounts:read` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Enfoque

**Tres consultas planas y la suma en Java**, y no una consulta que agregue por nodo.

Es la decisión que carga el plan, y va contra lo que parece: `RF-SP-057` acaba de estrenar la recursiva, y lo natural sería reusarla **por cada nodo**. Eso es una recursiva por vendedor — un `N + 1` de consultas caras que ninguna prueba detectaría, porque el resultado sería correcto.

Lo que se hace en su lugar:

| # | Consulta | Devuelve |
|---|---|---|
| 1 | La fuerza comercial vigente | Una fila por vendedor, con su superior |
| 2 | Cuentas por superior y estado | Cuántas cuentas de consumidores cuelgan **directamente** de cada persona |
| 3 | Consumidores por superior | Cuántas **personas** cuelgan directamente de cada una |

Y después **un recorrido en post-orden** que acumula. La aritmética entera —lo que el responsable del proyecto pidió cuidar— queda **en un solo método legible**, en lugar de repartida en un `SQL` que nadie puede leer en voz alta.

**El coste es lineal**: tres consultas fijas y un recorrido, sea cual sea el tamaño de la red.

## 2. Cambios de esquema

**Ninguno.** Todo sale de `user_brokers`, `user_supervisors` y `user_roles`, y los índices que hacen falta ya existen: `ix_user_supervisors_supervisor_vigente` (`V28`) y `ix_user_brokers_persona` (`V74`).

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `brokers/application` | `NetworkIndicatorsResponse` — el árbol, los totales y lo no atribuido | `SP` |
| `brokers/domain/repository` | `BrokerAccountQueryRepository` — **tres métodos más**, los tres planos | `SP` |
| `brokers/domain/service` | `GetNetworkIndicatorsService` — **la suma** | `SP` |
| `brokers/interfaces` | `BrokerAccountController` — `GET /api/v1/broker-accounts/indicators` | `SP` |

## 4. Las tres consultas

**La fuerza comercial** sale de `user_roles.role_type = 'VENDEDOR'`, que es una **copia** del tipo del rol en la propia fila (`V52`, `RN-SP-025`): no hace falta unir con `roles` para saberlo, y la unicidad de rol vendedor por persona —del mismo `RN-SP-025`— garantiza que **no salgan filas repetidas**. Sin esa garantía, una persona con dos roles vendedores se duplicaría en el árbol y sus números se contarían dos veces.

**Los consumidores se identifican igual**, por `role_type = 'CONSUMIDOR'`, y con un `EXISTS` y no con un `JOIN`: el `JOIN` multiplicaría la fila de la cuenta por cada rol de consumidor que la persona porte, y **cada cuenta se contaría tantas veces como roles**. Es el defecto que no falla — devuelve de más y parece un buen mes.

**Las tres filtran `deleted_at IS NULL` y `ended_at IS NULL`.**

## 5. La suma, que es lo que se pidió cuidar

```
network(nodo) = own(nodo) + Σ network(hijo)
```

Recorrido **en post-orden**: primero los hijos, luego el padre. Cuatro cuidados, y cada uno tiene su forma de fallar en silencio:

- **`own` se lee de la consulta 2 y NUNCA se deriva del árbol.** Un consumidor cuelga de una sola persona (`uq_user_supervisors_vigente`), de modo que cada cuenta entra en **un solo `own`** — y de ahí que la suma de todos los `own` sea el total, sin duplicados.
- **`network` se acumula UNA vez por nodo.** Un nodo visitado dos veces —por un ciclo en los datos— sumaría dos veces: el recorrido lleva su registro de visitados, y no depende de que `RN-SP-020` garantice que no hay ciclos.
- **`consumers` de `network` NO es la suma de los `consumers` de los hijos**… salvo que lo sea. Aquí sí lo es, y por un motivo que conviene escribir: una persona cuelga de **un** superior, de modo que los conjuntos de consumidores de dos ramas son **disjuntos** y sumar cardinales es correcto. Si algún día alguien pudiera colgar de dos, esta línea deja de valer.
- **`conversion` NO se acumula: se recalcula** en cada nodo sobre sus propios totales. Promediar las conversiones de los hijos daría un número que no es la conversión de nada — el error clásico de los promedios de promedios.

## 6. Lo no atribuido, y por qué es la pieza que hace que cuadre

```
Σ network(raíces) + unassigned = total de RF-SP-057
```

Las cuentas de consumidores que **no cuelgan de ningún vendedor** —sin superior, o colgando de un funcionario— no entran en ningún nodo. Sin publicarlas, el árbol sumaría **menos** que el listado global y no habría forma de saber si falta algo o si el cálculo está mal.

Es la traducción exacta de «ponle cuidado como suma la cosa», y por eso `CA-SP-664` **comprueba la igualdad de arriba** en lugar de comprobar números sueltos.

## 7. Contrato de API

`GET /api/v1/broker-accounts/indicators?rootId=…`

```json
{
  "nodes": [
    {
      "user": { "id": "…", "username": "rlopez", "firstName": "Ramón", "lastName": "…" },
      "roleCode": "MANAGER",
      "own":     { "accounts": 2, "ftd": 1, "pending": 1, "conversion": 0.5, "consumers": 2 },
      "network": { "accounts": 9, "ftd": 4, "pending": 5, "conversion": 0.4444, "consumers": 7 },
      "children": [ … ]
    }
  ],
  "totals":     { "accounts": 9, "ftd": 4, "pending": 5, "conversion": 0.4444, "consumers": 7 },
  "unassigned": { "accounts": 1, "ftd": 0, "pending": 1, "conversion": 0.0,    "consumers": 1 }
}
```

- **`conversion` es un número entre 0 y 1 y no un porcentaje**: quien lo pinte decide el formato, y un `44.44` obligaría a saber que ya viene multiplicado.
- **Se redondea a cuatro decimales.** Con más, dos clientes con el mismo cociente se verían distintos por el ruido del binario.
- **`children` va siempre, aunque vaya vacío**: una hoja y un nodo sin cargar no pueden parecer lo mismo.
- **`unassigned` se omite cuando hay `rootId`**: dentro de una rama no significa nada.

## 8. Autorización

`@PreAuthorize("hasAuthority('broker-accounts:read')")`, como el listado. **No se abre al vendedor sobre su propia red**: obligaría a recortar el árbol y es una decisión pendiente (`tasks.md` §4).

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Reusar la recursiva de `RF-SP-057` por cada nodo** | Una recursiva por vendedor. `N + 1` de consultas caras que **ninguna prueba detectaría**: el resultado sería correcto y solo lento |
| **Una sola consulta que agregue por nodo** | Un `SQL` con recursiva y agregación que nadie puede leer en voz alta, justo donde el responsable del proyecto pidió poner cuidado. La aritmética se quiere **legible y probada aparte** |
| **Publicar solo el total de cada nodo** | Invita a sumar la columna y **contar dos veces**. Es exactamente el error contra el que se avisó |
| **Contar personas en vez de cuentas** | Decisión del responsable del proyecto: la unidad es la cuenta. `consumers` va aparte |
| **Excluir del árbol a quien también es consumidor** | Sería resolver por posición lo que se decidió resolver por rol. Se declara el caso (`FA-007`) en lugar de esconderlo |
| **Devolver `conversion` como porcentaje** | Obliga a saber que ya viene multiplicado, y el primero que lo multiplique otra vez no se dará cuenta |
| **Promediar las conversiones de los hijos** | No es la conversión de nada. Se recalcula sobre los totales del nodo |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Doble conteo** en la acumulación | `CA-SP-664`: `totals` + `unassigned` tiene que **cuadrar con `RF-SP-057`**. Es lo único que lo detecta |
| 2 | El `JOIN` con `user_roles` **multiplica** las cuentas por rol | Se usa `EXISTS`. `CA-SP-660` lo prueba con un consumidor de dos cuentas, y una persona con dos roles lo destaparía |
| 3 | La cuenta propia de un vendedor **entra** en su indicador | `CA-SP-661`, con un vendedor que tiene cuenta depositada |
| 4 | `conversion` sale **cero** donde debería ser nula | `CA-SP-663`. Un cero de más se lee como mal desempeño |
| 5 | Una rama **se corta** al eliminar a alguien de en medio | `CA-SP-666`, la misma lección de `RF-SP-057` |
| 6 | El árbol publica **consumidores** como nodos | `CA-SP-662` |

## 11. Estrategia de prueba

- **La aritmética**, sobre una red de cuatro niveles con cuentas repartidas: cada `own` y cada `network` comprobados uno a uno.
- **La igualdad que cuadra**: `totals` + `unassigned` contra el `totalElements` de `GET /api/v1/broker-accounts`, en la misma prueba y con los mismos datos.
- **Las unidades**: un consumidor con dos cuentas depositadas — 2 en `ftd`, 1 en `consumers`.
- **Las exclusiones**: la cuenta de un vendedor, un eliminado en medio de la rama, y quien dejó la estructura.
- **La conversión**: nula sin cuentas, y recalculada —no promediada— en un nodo con hijos de conversiones distintas.
- **`rootId`**: la misma rama da los mismos números que dentro del árbol completo.
