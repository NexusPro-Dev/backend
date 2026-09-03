# TASKS — `RF-CM-005` Consultar la comisión efectiva

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-005` |
| Plan | [`plan.md`](plan.md), aprobado el 02-09-2026 |
| Versión | 0.3.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/flujos-de-pm-y-cm` (`T-01`–`T-18`) · `feature/comision-en-valor-fijo` (`T-19`–`T-24`) |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación.

!!! warning "Las tareas están hechas antes que este documento"

    El código se rehízo el 02-09-2026 y esta lista viene detrás. **No planifica: registra.** La tercera compuerta del Art. I.6 sigue pendiente y por eso el documento está `En revisión`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `RateSource`, con el orden de declaración como orden de precedencia | `RF-CM-001` · `T-05` | El javadoc dice que reordenarlo desalinea la regla | **Hecha el 02-09-2026** |
| `T-02` | **La sentencia**: `UNION ALL` con la prioridad en el `ORDER BY` | `RF-CM-006` · `T-02`, `RF-CM-007` · `T-01` | Una sola sentencia, y `LIMIT 1` | **Hecha el 02-09-2026** |
| `T-03` | La rama de la persona **sin filtrar por rol ni por producto** | `T-02` | Una personalizada gana sobre un producto sin asociaciones | **Hecha el 02-09-2026** |
| `T-04` | La rama del rol **exige la asociación**, sin ningún `OR ... IS NULL` | `T-02` | Una tasa de rol sin asociar no devuelve nada | **Hecha el 02-09-2026** |
| `T-05` | El `JOIN` a `commission_rates` **por la clave compuesta** | `T-04` | No puede leer el porcentaje de una tasa con otro rol | **Hecha el 02-09-2026** |
| `T-06` | **El rol admite nulo** y apaga la rama del rol | `T-02` | Quien no vende y tiene personalizada **cobra** | **Hecha el 02-09-2026** |
| `T-07` | Las dos ramas filtran `deleted_at IS NULL` | `T-02` | Una tasa retirada deja de resolver | **Hecha el 02-09-2026** |
| `T-08` | `ResolveCommissionService`: comprueba, busca el rol y **delega sin ordenar nada** | `T-02` | El caso de uso no contiene ninguna comparación de precedencia | **Hecha el 02-09-2026** |
| `T-09` | Clasificar la ausencia: «no comisiona» frente a «sin tarifa» | `T-08` | Con rol y sin tasa, «sin tarifa»; sin rol y sin nada, «no comisiona» | **Hecha el 02-09-2026** |
| `T-10` | `EffectiveCommissionResponse` con los tres desenlaces y **el porcentaje siempre presente** | `T-09` | El nulo llega como nulo, no ausente | **Hecha el 02-09-2026** |
| `T-11` | `GET /api/v1/commissions/effective` | `T-08`, `T-10` | `200` en los tres desenlaces, `400`, `403`, `422` | **Hecha el 02-09-2026** |
| `T-12` | Pruebas de los criterios de `spec.md` §12 | `T-11` | `CA-CM-038` a `CA-CM-050` | **Hecha el 02-09-2026** |
| `T-13` | **Prueba de que la tasa sin asociar no paga** | `T-12` | `CA-CM-039` | **Hecha el 02-09-2026** |
| `T-14` | **Prueba de `FA-003`**: quien no vende cobra su personalizada, con el rol nulo | `T-12` | `CA-CM-045` | **Hecha el 02-09-2026** |
| `T-15` | **Prueba del estado que `RN-CM-015` impide**, sembrado a mano | `T-12` | `CA-CM-048` | **Hecha el 02-09-2026** |
| `T-16` | Documentación OpenAPI, con los tres desenlaces y **el aviso del rol nulo** | `T-11` | La descripción explica que nulo y cero no son lo mismo | **Hecha el 02-09-2026** |
| `T-17` | Actualizar la matriz de `docs/requirements.md` | `T-12` | La fila refleja el estado y el bloqueo de `RN-SP-025` | **Hecha el 02-09-2026** |
| `T-18` | **Prueba de los dos roles vendedores**: que el puerto falle de forma visible en vez de elegir | `T-12` | **Sin objeto desde el 02-09-2026**: `V52` hace ese estado imposible de alcanzar | `Retirada` |

### 1.1 El valor fijo (`cm.md` v0.7.0)

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-19` | Las dos ramas proyectan `rate_type`, `percentage` y `fixed_amount` **tal cual** | `RF-CM-001` · `T-16` · `T-02` | **Ningún `COALESCE` dentro de las ramas** | **Hecha el 02-09-2026** |
| `T-20` | La fusión en **un** campo de valor, al armar la respuesta | `T-19` | Un solo sitio hace la fusión, no dos | **Hecha el 02-09-2026** |
| `T-21` | `EffectiveCommissionResponse`: `percentage` **se retira** y entra `value` con `rateType` | `T-20` | Un consumidor que lea `percentage` obtiene un campo inexistente, no un número | **Hecha el 02-09-2026** |
| `T-22` | Los tres desenlaces con **forma y valor nulos y presentes** cuando no hay tasa | `T-21` | El nulo vuelve a tener **una sola** causa | **Hecha el 02-09-2026** |
| `T-23` | Pruebas de los criterios nuevos, **`CA-CM-101` en las dos direcciones** | `T-21` | `CA-CM-100` a `CA-CM-104` | **Hecha el 02-09-2026** |
| `T-24` | OpenAPI: el **cambio incompatible**, y por qué este contrato no se parece al del catálogo | `T-21` | La descripción explica la asimetría en lugar de dejarla parecer un descuido | **Hecha el 02-09-2026** |

## 2. Orden de ejecución

**`T-02` va antes que `T-08`, y no al revés.** Escribir primero el caso de uso invita a encadenar dos consultas y decidir en Java — que es exactamente lo que `plan.md` §1 existe para impedir. Construyendo antes la sentencia, el caso de uso queda con lo único que le toca: comprobar, buscar el rol y delegar.

**`T-06` parece un detalle de firma y es una decisión de negocio.** Aceptar el rol nulo es lo que hace que `FA-003` ocurra. La v0.1.0 cortaba antes de consultar cuando la persona no portaba rol vendedor, y con el modelo nuevo **eso escondería que su personalizada sigue pagando**.

!!! danger "`T-19` toca la sentencia que sostiene la precedencia, y ninguna prueba existente se enteraría de romperla"

    `T-02` a `T-07` construyeron la única sentencia del módulo que decide qué se paga. `T-19` la reescribe **solo para añadir columnas** — y esa es la clase de cambio que se hace con menos cuidado del que merece.

    Las pruebas de precedencia que ya existen usan **la misma forma en las dos ramas**, porque cuando se escribieron solo había una. Si `T-19` invirtiera las ramas al reorganizar el `UNION ALL`, **todas seguirían pasando** y la consulta devolvería la tasa equivocada con una cifra plausible.

    `CA-CM-101` —dentro de `T-23`— es lo único que se entera, y por eso cruza las formas en **las dos direcciones**: personalizada en porcentaje contra rol en importe fijo, y al revés.

**`T-21` retira un campo del contrato publicado**, y es lo contrario de lo que suele hacerse. Añadir `fixedAmount` junto a `percentage` no habría roto a nadie — y habría devuelto el nulo con dos causas, que es lo que `spec.md` §6.2 existe para impedir. **Se rompe ruidosamente a propósito**: quien lea `percentage` obtiene un campo que no existe, en lugar de un número que se lee mal.

**`T-15` construye a mano un estado que el sistema no permite alcanzar**, y es la única tarea del módulo que lo hace. No prueba un comportamiento que alguien vaya a usar: prueba **qué pasaría si `RN-CM-015` no existiera**. Es la evidencia de que esa regla no es una precaución teórica.

**`T-18` se RETIRÓ el 02-09-2026, y no por haberse desbloqueado.** Existía para probar que el puerto revienta en lugar de elegir cuando alguien porta dos roles vendedores. Al declarar `RN-SP-025` **en el motor** (`V52`), ese estado dejó de poder alcanzarse: la inserción falla. La prueba no se puede escribir porque **no hay forma de llegar al escenario**, que es exactamente lo que se quería. `AmbiguousSellerRoleException` se queda: es lo que saltaría si algún día alguien retirara el índice.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-CM-038` | `T-04`, `T-05`, `T-12` |
| `CA-CM-039` | `T-04`, `T-13` |
| `CA-CM-040`, `CA-CM-041` | `T-04`, `T-05`, `T-12` |
| `CA-CM-042`, `CA-CM-043` | `T-03`, `T-12` |
| `CA-CM-044` | `T-03`, `T-12` |
| `CA-CM-045` | `T-06`, `T-14` |
| `CA-CM-046` | `T-09`, `T-12` |
| `CA-CM-047` | `T-10`, `T-12` |
| `CA-CM-048` | `T-07`, `T-15` |
| `CA-CM-049` | `T-08`, `T-12` |
| `CA-CM-050` | `T-08`, `T-11`, `T-12` |
| `CA-CM-100` | `T-19`, `T-20`, `T-21`, `T-23` |
| `CA-CM-101` | `T-19`, `T-23` |
| `CA-CM-102` | `T-22`, `T-23` |
| `CA-CM-103` | `T-20`, `T-23` |
| `CA-CM-104` | `T-19`, `T-23` |

**`CA-CM-047` sigue cubierto por `T-10` y no se renumera**, aunque `T-21` reescriba ese componente entero: el criterio —«el cero resuelve y se distingue de no tener tasa»— no cambió, y **es el que `T-21` tiene que seguir cumpliendo después de cambiar el campo**. Se deja como estaba para que se vea que sobrevive al cambio.

## 4. Bloqueos

| # | Bloqueo | Efecto |
|---|---|---|
| ~~1~~ | ~~**`RN-SP-025`** — una persona no puede tener dos roles vendedores~~ | **Levantado el 02-09-2026**, y no como se esperaba. Ver abajo |

!!! danger "El bloqueo se levantó y `T-18` no se desbloqueó: se quedó SIN OBJETO"

    `T-18` existía para probar que `SellerRoleCatalog` **revienta en lugar de elegir** cuando alguien porta dos roles vendedores. Estaba bloqueada porque `RN-SP-025` no la sostenía nadie, y sin la regla ese estado era alcanzable pero nadie lo declaraba imposible.

    Al construir la regla el 02-09-2026 se declaró **en el motor** (`V52`, índice único parcial), y con eso **el estado dejó de poder existir**: la inserción falla. La prueba ya no se puede escribir — no porque falte algo, sino porque **no hay forma de llegar al escenario**.

    **`AmbiguousSellerRoleException` se queda igualmente**, y eso no es código muerto por descuido: es lo que salta si algún día alguien retirara el índice. Que no se pueda probar hoy es exactamente lo que se quería conseguir.

**`T-19` depende de que `V50` esté aplicada** (`RF-CM-001` `T-16` a `T-19`), y no es un bloqueo: es el orden del bloque, con `RF-CM-001` primero.

**Y quedan declaradas dos deudas que no bloquean nada y que nadie va a resolver desde aquí:**

1. El tope de la suma de la cadena (`RN-CM-011`). Este requerimiento ve un nivel y no puede verlo; lo heredará la liquidación.
2. **La moneda de un importe fijo** (`RN-CM-017`). Esta consulta **es el único punto del sistema donde se podría haber devuelto** —recibe el producto—, y el responsable del proyecto decidió el 02-09-2026 que no. `spec.md` §14 recoge el argumento y lo que cuesta; `CA-CM-104` lo fija como comportamiento esperado en lugar de dejarlo sin escribir.

## 5. Definición de terminado

- Diecisiete de las dieciocho primeras tareas `Hecha` con su verificación pasando. **`T-18` queda `Retirada`**: `V52` hizo imposible el estado que probaba. `./mvnw clean verify` en verde. **Comprobado el 02-09-2026**: 278 unitarias y 876 de integración.
- **Las seis del valor fijo, `Hecha`**, con `CA-CM-101` pasando **en las dos direcciones** — que es lo único que verifica que la precedencia sobrevivió a reescribir la sentencia. **Comprobado el 02-09-2026**: 287 unitarias y 902 de integración, suite entera en verde.
- La matriz y el contrato publicado al día, **con los dos cambios incompatibles declarados**: el de `RF-CM-001` y la retirada de `percentage` de aquí.
