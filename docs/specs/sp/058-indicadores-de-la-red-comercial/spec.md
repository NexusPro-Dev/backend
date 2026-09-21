# SPEC — `RF-SP-058` Consultar los indicadores de la red comercial

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-058` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 10-09-2026 |

---

!!! note "Enmienda de Art. I.7 — 19-09-2026, `RF-SP-060`"

    Esta operación exige **`broker-accounts:read-indicators`** y no `broker-accounts:read` desde el 19-09-2026, por `RF-SP-060` —**un permiso por operación**, `RN-SEG-014` ([`security.md` §4.4](../../../security.md#44-catalogo-de-permisos))—: `broker-accounts:read` gobernaba varias operaciones y se queda con una; esta recibe código propio, sembrado por `V28` y dado a todo rol que portara `broker-accounts:read`. Las menciones de `broker-accounts:read` que siguen abajo hablan de su siembra original y se conservan como historia.

## 1. Objetivo

Saber **cuánto FTD lleva cada vendedor** —lo suyo y lo de toda su red—, con el embudo y la conversión.

## 2. Contexto

**Lo pidió el responsable del proyecto con la regla ya formulada**: «a los agentes se les suman los ftds directos, a los directores los propios más los ftds de los agentes, y a los managers los propios más la sumatoria de los directores». Y con una advertencia: **«ponle cuidado como suma la cosa»**.

**El cuidado está justificado, porque la regla es una sola y las trampas son cuatro.** Lo que describió son tres frases y una misma operación aplicada en tres niveles —**los suyos directos más la suma de los de abajo**—; lo que no se lee en las tres frases es qué es «uno», de quién es, quién puede sumarlo dos veces y qué pasa con lo que no cuelga de nadie. Esas cuatro cosas son `RN-SP-048`, y son la especificación entera.

**Nace sobre lo que `RF-SP-057` ya recorre.** La red en profundidad ya existe; lo que falta es **agregar** en lugar de listar, y presentarlo como árbol.

## 3. Actores

| Actor | Papel |
|---|---|
| **Administrador** con `broker-accounts:read-indicators` | Consulta los indicadores de toda la fuerza comercial, o de una rama |

## 4. Alcance

### 4.1 Incluye

- **El árbol de la fuerza comercial**, cada nodo con **dos bloques**: `own` y `network`.
- Por bloque: cuentas declaradas, con primer depósito, pendientes, **conversión** y cuántos consumidores.
- **Lo no atribuido**, para que los números cuadren con `RF-SP-057`.
- Acotar el árbol a **una rama**, con `rootId`.

### 4.2 No incluye

- **Los consumidores como nodos.** Aportan el número y no aparecen (`RN-SP-048`).
- **Dinero.** Se cuentan **cuentas**, no importes: el sistema no sabe cuánto se depositó, solo que se depositó.
- **Series temporales.** Es una foto de hoy, no una evolución. Un `from`/`to` sobre la fecha de declaración es otro requerimiento.
- **Que un vendedor consulte su propia red.** Hoy lo gobierna el permiso y nada más. Es una decisión del responsable del proyecto, y se declara como bloqueo abierto en lugar de adivinarla — igual que `RF-SP-056` hizo con el `supervisorId` que acabó pidiéndose.
- **Paginar.** Se decidió con la forma de árbol, que no se pagina.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-045` | Toda cuenta de broker declara en qué punto está | `requirements/sp.md` §5.1 |
| `RN-SP-047` | La red de un vendedor es TODO lo que cuelga de él | `requirements/sp.md` §5.1 |
| `RN-SP-048` | Cómo suman los indicadores de la red comercial | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| `rootId` | No | Acota el árbol a la rama de esa persona, **ella incluida como raíz** | `uuid` de alguien de la fuerza comercial |

**`rootId` sí incluye a la persona**, al revés que el `supervisorId` de `RF-SP-057`. La diferencia no es un descuido: allí se piden **las cuentas de su red** —y las suyas propias no son de su red—, y aquí se pide **el nodo del árbol que le corresponde**, que existe precisamente para llevar sus números.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| `nodes` | Las raíces del árbol. Cada nodo lleva su persona, su rol, `own`, `network` e `children` |
| `own` | Lo que cuelga **directamente** de esa persona |
| `network` | Ese nodo **y todo lo que cuelga de él**, a cualquier profundidad |
| `unassigned` | Las cuentas de consumidores **que no cuelgan de ningún vendedor**. Solo cuando no hay `rootId` |
| `totals` | La suma de las `network` de las raíces. **No incluye `unassigned`** |

**Cada bloque de números lleva lo mismo**: `accounts`, `ftd`, `pending`, `conversion` y `consumers`.

**Los dos bloques van siempre**, y esa duplicación es la respuesta al «cuidado» que se pidió: el `network` de un director **ya contiene** el de sus agentes, de modo que **sumar una columna de `network` cuenta dos veces**. Publicar solo el total invita a ese error; publicar los dos obliga a elegir, y elegir es acordarse.

**`conversion` es `ftd / accounts`, y es NULA cuando no hay cuentas** — no cero. Cero se lee como «nadie convirtió» y la verdad es «no hay nada que convertir». Es la misma distinción que `RN-SP-040` hace con el nulo del nombre de usuario en el broker.

**`consumers` cuenta PERSONAS y `accounts` cuenta CUENTAS**, y no tienen por qué coincidir: un cliente con dos cuentas es una persona y dos cuentas. Van los dos porque responden preguntas distintas —cuánta gente y cuánto dinero— y tenerlos juntos es lo que impide confundirlos.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor autenticado con `broker-accounts:read-indicators`.

**Postcondiciones:** ninguna. Es una lectura y **no audita**.

## 8. Flujo principal

1. El actor pide los indicadores, opcionalmente de una rama.
2. El sistema resuelve **la fuerza comercial vigente** y la arma como árbol.
3. El sistema cuenta, **por cada persona**, las cuentas de los consumidores que cuelgan **directamente** de ella.
4. El sistema acumula de abajo arriba: `network` = `own` + la suma de las `network` de sus hijos.
5. El sistema devuelve el árbol, los totales y lo no atribuido.

## 9. Flujos alternativos

| ID | Caso | Comportamiento |
|---|---|---|
| `FA-001` | No hay fuerza comercial | `200` con `nodes` vacío, y `unassigned` con lo que haya |
| `FA-002` | Un vendedor sin nadie a cargo | Aparece como **hoja**, con `own` igual a `network` |
| `FA-003` | `rootId` no designa a nadie de la fuerza comercial | `404` |
| `FA-004` | Un consumidor cuelga **directamente de un manager** | Cuenta en el `own` del manager. La regla es estructural y no exige que un cliente cuelgue de un agente |
| `FA-005` | Una persona de la estructura fue **eliminada** | Ni ella ni sus cuentas cuentan, y **su rama no se corta**: sus subordinados siguen colgando de ella en el árbol |
| `FA-006` | Un **vendedor** tiene cuenta de broker propia | **No cuenta** (`RN-SP-048`): solo cuentan las de consumidores |
| `FA-007` | Alguien porta rol de vendedor **y** de consumidor | **Es nodo y sus cuentas cuentan** — en el `own` de su superior. Es la consecuencia de resolverlo por rol y no por posición, y se declara en lugar de esconderla |

## 10. Seguridad

**Lo gobierna `broker-accounts:read`**, sin nada especial: es el mismo permiso de `RF-SP-057` sobre los mismos datos, agregados.

**Publica la estructura comercial entera**, y eso es nuevo: `RF-SP-042` la enseña un nivel cada vez y `RF-SP-057` la recorre sin publicarla. Se acepta acotado — **los consumidores no son nodos**, de modo que lo que sale es el organigrama **interno** y nunca la cartera de clientes.

**No lo puede llamar un vendedor sobre su propia red.** Hoy no está abierto, y abrirlo obligaría a recortar el árbol a su rama. Se deja como decisión pendiente y no se adivina.

## 11. Validaciones

| Campo | Regla | Código |
|---|---|---|
| `rootId` | `uuid` bien formado | `VAL-001` |
| `rootId` | Designa a alguien de la fuerza comercial, no eliminado | `VAL-002` |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-658` | El `own` de una persona cuenta **solo** las cuentas de los consumidores que cuelgan **directamente** de ella — **precisado el 18-09-2026**: los consumidores cuyo `REGISTRO` en `client_sellers` es ella (`RN-SP-048` (5)); un vínculo `HOTLINK` **no suma** |
| `CA-SP-659` | El `network` de una persona es **su `own` más la suma de las `network` de sus hijos**, a cualquier profundidad |
| `CA-SP-660` | **La unidad es la cuenta**: un consumidor con **dos** cuentas en `FIRST_DEPOSIT` suma **2** en `ftd` y **1** en `consumers` |
| `CA-SP-661` | La cuenta de broker de un **vendedor** no cuenta en ningún indicador (`RN-SP-048`) |
| `CA-SP-662` | Los **consumidores no aparecen como nodos** del árbol |
| `CA-SP-663` | `conversion` es **nula** cuando `accounts` es cero, y no `0` |
| `CA-SP-664` | `unassigned` recoge las cuentas de consumidores que **no cuelgan de ningún vendedor** —**precisado el 18-09-2026**: sin `REGISTRO` en `client_sellers`, o con uno cuyo vendedor no porta rol `VENDEDOR`—, y `totals` + `unassigned` **cuadra con el total de `RF-SP-057`** |
| `CA-SP-665` | `rootId` devuelve **esa rama con la persona como raíz**, y sus números son los mismos que tenía dentro del árbol completo |
| `CA-SP-666` | Una persona **eliminada** no aporta números **y su rama no se corta** |
| `CA-SP-667` | Quien **dejó** la estructura no aparece en el árbol ni aporta a nadie |
| `CA-SP-668` | Sin `broker-accounts:read-indicators`, `403` |

**`CA-SP-664` es el criterio que sostiene el requerimiento entero.** Es el único que afirma que **los números cuadran**, y es la traducción exacta del «ponle cuidado como suma la cosa»: cualquier error de doble conteo o de omisión rompe esa igualdad y **ninguna otra prueba lo notaría**.

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Un vendedor con red pero **sin ninguna cuenta debajo** | `accounts` cero y `conversion` **nula**. No es un cero de desempeño |
| Un consumidor **sin superior** | Va a `unassigned`, no a un nodo |
| Cadena de cuatro o más niveles | Se acumula igual: la regla es la misma en todos |
| Un ciclo en los datos | No cuelga: el recorrido lleva su propio registro de visitados. No debería poder ocurrir —`RN-SP-020`— y el cálculo no depende de ello |

## 14. Preguntas abiertas resueltas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿La unidad es la cuenta o la persona? | **La cuenta** (10-09-2026, responsable del proyecto). Mide el dinero que entró y cuadra con el broker. `consumers` va aparte para quien quiera personas |
| 2 | ¿Cuenta la cuenta propia de un vendedor? | **No.** «Solo los roles de tipo consumidores tienen ftds» — precisión del responsable del proyecto. Se resuelve por `role_type` |
| 3 | ¿Árbol, listado o dos números? | **Árbol** (10-09-2026, responsable del proyecto), sabiendo que no se pagina y que publica la jerarquía entera |
| 4 | ¿Qué más además del FTD? | **Pendientes, conversión y tamaño de la red**, los tres pedidos el mismo día |
| 5 | ¿Puede un vendedor ver la suya? | **Pendiente de decidir.** No se adivina: se declara como bloqueo, que es lo que `RF-SP-056` hizo con el `supervisorId` |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 10-09-2026 | Redacción inicial, a partir de la regla que dio el responsable del proyecto y de su advertencia —«ponle cuidado como suma la cosa»—. **Las tres frases que describió son una sola operación en tres niveles**; lo que la especificación añade son las cuatro cosas que esas frases no dicen y sin las cuales el número no significa nada: **qué es uno** —la cuenta, no la persona—, **de quién es** —solo de consumidores, por `role_type`—, **quién puede sumarlo dos veces** —de ahí `own` y `network` por separado— y **qué pasa con lo que no cuelga de nadie** —de ahí `unassigned`, y de ahí `CA-SP-664`, que es el único criterio que afirma que los números cuadran—. | Responsable del proyecto |
| 0.2.0 | 19-09-2026 | **Cambia el permiso: `broker-accounts:read-indicators` y no `broker-accounts:read`** (`RF-SP-060`, `RN-SEG-014`, un permiso por operación; [`security.md`](../../../security.md) v0.63.0). Enmienda de Art. I.7 sin cambio de comportamiento: la misma operación, el mismo actor, un código propio sembrado por `V28` y dado a todo rol que portara `broker-accounts:read`. | Responsable del proyecto |
| 0.3.0 | 21-09-2026 | **`CA-SP-658` y `CA-SP-664` precisados**: el cliente sale de `user_supervisors` (`RN-SP-028` revertida por el responsable del proyecto; `RF-SP-059`), y «cuelga de» pasa a ser la fila `REGISTRO` de `client_sellers` (`RN-SP-048` (5)). La suma no cambia de forma —tres consultas planas y el post-orden— ni de resultado sobre los mismos datos; cambia la tabla de la que salen los dos conteos directos y «lo no atribuido». Sin cambio de contrato; el código lo cambia `RF-SP-059 · T-13`. — redactada el 18-09-2026 en `feature/vendedores-de-un-cliente` e integrada sobre `feature/academia` el 21-09-2026 con el número renumerado; `RF-SP-060` de aquella rama pasa a `RF-SP-061` porque `RF-SP-060` nació en `feature/academia` el 19-09-2026 | Responsable del proyecto |
