# PLAN — `RF-CM-020` Liquidar las comisiones afftrack en el cierre

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-020` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 29-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 29-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

Hereda de [`RF-CM-009`](../009-cerrar-periodo-comisiones/plan.md) la transacción externa con su bloqueo y el `FOR UPDATE` de los lotes, y de [`RF-CM-013`](../013-devengar-comision-linea/plan.md) la secuencia del lote abierto (§4 de aquel plan), `SupervisorChain` y `BusinessCalendar`. **No los repite.**

---

## 1. Enfoque

**Un paso más dentro de la transacción externa del cierre**, entre el barrido y el cierre de los lotes:

```
tx externa (RF-CM-009)   bloqueo consultivo del cierre
  barrido                (sin cambios, en sus propias transacciones)
  corte   = now()                                          ← nuevo
  afftrack AfftrackSettlementService.settle(closingId, corte)  ← nuevo, MISMA tx
  cierre  ahora = max(now(), corte + 1 µs)                 ← antes era now()
          lotes ABIERTO → PENDIENTE con period_end = ahora
```

**En la misma transacción, y no en las suyas como el barrido** (`spec.md` `EX-001`): el remanente es la entrada del cierre siguiente, y una liquidación a medias lo corrompería. **El instante del cierre pasa a ser estrictamente posterior al corte** porque un lote que la liquidación abre nace con `period_start = corte`, y `ck_commission_batches_periodo` exige `period_end > period_start`.

### 1.1 La cuenta, por conjuntos

```
F   = ProductCatalog.ftdProductIds()                    — si vacío, fin
N   = líneas FTD nuevas: una sentencia de CM con JOIN de lectura a
      movement_details y movements (precedente de RF-CM-010 y RF-CM-014):
        m.type VENTA, m.status CONFIRMADA, md.seller_id NOT NULL,
        md.delivery_status = 'ENTREGADA', md.delivered_at < :corte,
        md.product_id IN (:F),
        NOT EXISTS afftrack_ftds WHERE movement_detail_id = md.id AND user_id = md.seller_id
Para cada línea de N: cadena = SupervisorChain.chainAt(seller, delivered_at)
      → pares (línea, persona, nivel)
C   = pares agrupados por (persona, producto) → nuevos
R   = remanentes: la última afftrack_settlements de cada (persona, producto) con carried_out > 0
Para cada (persona, producto) en C ∪ R:
      escala = AfftrackScale.of(persona, producto, díaDelCierre)   — §1.2
      cuenta de spec.md §2.1
      INSERT afftrack_settlements; INSERT afftrack_ftds de sus pares
      si alcanzó: lote abierto (RF-CM-013 §4) + INSERT commissions POR_AFFTRACK + suma al total
```

**La condición «no contada al vendedor» es la de toda la cadena**: los pares de una línea se escriben todos en la misma transacción, de modo que si existe la del vendedor existen las de los demás. Es lo que permite una sola sentencia con `NOT EXISTS` en lugar de preguntar persona por persona.

**`SupervisorChain.chainAt` se llama una vez por línea nueva**, no por persona: en un cierre mensual son las altas del mes. Si pesara, la cadena de muchos vendedores en un instante cabe en un `WITH RECURSIVE` por lotes sin cambiar el contrato (§10).

### 1.2 La escala

`AfftrackScale.of(userId, productId, día)` devuelve los escalones vigentes y **de qué fuente** salen:

1. Los de persona **vigentes ese día** —el predicado único de `RF-CM-019` `plan.md` §1—. Si hay alguno, son la escala, `PERSONALIZADA`.
2. Si no, los vivos de `afftrack_rates` del rol que devuelve `SellerRoleCatalog.sellerRoleOf(userId)` —el mismo que usa `RF-CM-005`—, `ROL`.
3. Si no, vacía.

**El día del cierre** es `BusinessCalendar.diaDe(corte − 1 µs)`: un cierre a las 00:00 del día 1 liquida con lo vigente el 30 (`RN-CM-039`).

**Las escalas se leen en bloque**, una sentencia por fuente para todas las personas del cierre, no una por persona.

---

## 2. Cambios de esquema

**Ninguno propio**: todo lo trae `V54` (`RF-CM-015`). Los índices de apoyo de esta cuenta —`ix_afftrack_settlements_user_product_created` para el remanente y la clave primaria de `afftrack_ftds` para el `NOT EXISTS`— están allí.

**Ningún índice en tablas de `MV`**: sería un índice de `MV` para una consulta de `CM`. La sentencia de `N` filtra por `product_id IN (:F)`, que con `F` de un elemento usa el índice de producto que `movement_details` ya tiene.

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio | Nota |
|---|---|---|---|---|
| `CM` | `domain/models` | `CommissionKind` | Nuevo | `POR_VENTA`, `POR_AFFTRACK` |
| `CM` | `domain/service` | `AfftrackScale` | Nuevo | §1.2 |
| `CM` | `domain/service` | `AfftrackTierPicker` | Nuevo, **puro** | De disponibles y escalones, el escalón y el remanente. Es donde vive `RN-CM-041` |
| `CM` | `domain/service` | `AfftrackSettlementService` | Nuevo | `settle(UUID closingId, OffsetDateTime corte)`, `Propagation.MANDATORY`: solo corre dentro del cierre |
| `CM` | `domain/repository` | `AfftrackSettlementRepository` y adaptador | Nuevos | FTD nuevos, remanentes, escrituras de liquidación y FTD |
| `CM` | `domain/repository` | `CommissionBatchRepository` | Gana la inserción de una comisión `POR_AFFTRACK` | Reutiliza la toma del lote abierto de `RF-CM-013` |
| `CM` | `domain/service` | `CloseCommissionPeriodService` | Modificado | El paso de §1 y el instante del cierre posterior al corte |

---

## 4. Contrato de API

**Ninguno.** No tiene ruta. **`POST /commission-batches/closing` no cambia su respuesta**: la constancia del cierre no gana contadores afftrack; lo liquidado se lee por `RF-CM-021`, filtrando por el cierre.

---

## 5. Autorización

**Ninguna propia** (`cm.md` §6): corre dentro del cierre, y quien lanza el manual ya pasó por `commission-batches:settle`.

---

## 6. Auditoría

**Las liquidaciones y las comisiones no se auditan fila a fila**, por lo mismo que las comisiones de venta (`RF-CM-013` §7): son hechos derivados, y la liquidación **es** su constancia —con su cierre, su remanente de entrada y de salida y el escalón que pagó—.

---

## 7. Transaccionalidad

**La transacción externa del cierre** (§1). Tres consecuencias:

- **El bloqueo del cierre serializa los cierres**, de modo que dos liquidaciones no pueden leer el mismo remanente.
- **La toma del lote abierto con `FOR UPDATE`** serializa con un devengo por venta simultáneo, como en `RF-CM-009` `CA-CM-178`.
- **Un fallo revierte el cierre entero** (`spec.md` `EX-001`); la constancia, en su propia transacción, queda sin `closed_at`.

---

## 8. Impacto sobre otros módulos

| Módulo | Qué | Enmienda |
|---|---|---|
| `PM` | `ftdProductIds` (de `RF-CM-015`) | Ninguna más |
| `SP` | `SupervisorChain` se llama con la fecha de activación | Ninguna: la interfaz ya recibe un instante |
| `MV` | Se leen sus líneas por `JOIN`, sin interfaz nueva | Ninguna |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| La liquidación en su propia transacción, como el barrido | Un fallo dejaría el cierre hecho y la liquidación sin hacer: el siguiente cierre pagaría los FTD de dos periodos con el remanente de uno |
| Una tabla de saldos por persona y producto | `cm.md` §5.8: la última liquidación ya dice el remanente |
| Contar los FTD a cada persona recorriendo **su red hacia abajo** (`CommercialReach`) | Daría la red de **hoy**; `RN-CM-042` pide la del día de la activación, y subir desde el vendedor con `SupervisorChain` es lo que la da |
| Añadir contadores afftrack a la constancia del cierre | Duplicaría lo que las liquidaciones del cierre ya dicen, con un `closing_id` para agruparlas |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Muchas líneas nuevas en un cierre alargan la transacción que sostiene el bloqueo | En un cierre mensual son las altas del mes; si pesa, la cadena por lotes (§1.1) |
| El instante del cierre ya no es `now()` exacto | Es `corte + 1 µs` como mucho; `CA-CM-170` y `CA-CM-171` siguen comprobando la frontera |
| Que la escala del listado (`RF-CM-019`) y la del cierre difieran | El predicado de vigencia vive una vez |

---

## 11. Estrategia de prueba

- **Unitarias** de `AfftrackTierPicker`: las cuatro filas de `spec.md` §2.1, el escalón de cero, escalones que pagan menos cuanto más alto.
- **`AfftrackSettlementIT`**: `CA-CM-241` a `CA-CM-252`, activando líneas `BECA → BECA` por la API de `MV` —`RF-MV-010`— y **cerrando por la API** (`POST /commission-batches/closing`), para que el paso entre de verdad en la transacción del cierre. `CA-CM-246` con historial de `user_supervisors`. `CA-CM-252` **provocando el fallo con un dato** —un escalón cuyo `límite × valor` desborda `numeric(14,4)` al escribir la comisión— y **no con `@MockitoSpyBean`**, que crea otro contexto de Spring y agota las conexiones de la base.
- **`CommissionAccrualIT`** gana `CA-CM-253` (enmienda de `RF-CM-013`).
- **`CommissionKindSchemaIT`**: `CA-CM-254`, con inserciones directas que el esquema tiene que rechazar.
