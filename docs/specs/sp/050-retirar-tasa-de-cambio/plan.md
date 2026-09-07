# PLAN — `RF-SP-050` Retirar una tasa de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-050` |
| Especificación | [`spec.md`](spec.md), aprobada el 07-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |

---

## 1. Enfoque

Hereda entero el diseño de `RF-PM-006`, que es el retiro más parecido del sistema, y **no repite su argumentación**: `POST` sobre un subrecurso, motivo obligatorio, instantánea antes de marcar y estado intacto. Lo que aquí es distinto es **qué libera el retiro**.

## 2. Cambios de esquema

**Ninguno.** `deleted_at` la crea `RF-SP-047`, y **el `EXCLUDE` ya la mira**: su `WHERE` es `(is_active AND deleted_at IS NULL)`. Retirar libera el periodo **sin una línea de `SQL` propia**, que es la mitad del valor de haber escrito la restricción parcial.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| `application` | `DeleteExchangeRateRequest` — solo el motivo |
| `domain/models` | `ExchangeRate.delete(...)` y `instantanea()`, **compartida con el alta** |
| `domain/service` | `DeleteExchangeRateService` |
| `interfaces` | `POST /api/v1/exchange-rates/{id}/deletion` |
| `shared/audit` | El escritor de eliminaciones, ya existente |

**`instantanea()` la comparte con `RF-SP-047`**: si cada caso de uso armara su mapa, el registro de creación y el de eliminación describirían la misma tasa con claves distintas, y compararlos —que es para lo que existen— dejaría de ser posible.

## 4. Contrato de API

`POST /api/v1/exchange-rates/{id}/deletion`

```json
{ "reason": "Tasa cargada con el par invertido." }
```

**No es un `DELETE`**, y el motivo está escrito en `spec.md` §6.1: la RFC 9110 no define semántica para el cuerpo de un `DELETE` y un intermediario puede descartarlo, con lo que la petición llegaría sin el motivo que el Art. V.13 exige — y se convertiría en un rechazo que quien la envió no puede entender ni corregir.

**Respuesta `200`** con la confirmación y la fecha del retiro. **El motivo no vuelve.**

## 5. Orden de verificación

1. **Motivo** presente y con longitud válida. Va lo primero: una petición sin motivo no debe costar una consulta.
2. **Bloqueo** de la fila.
3. La tasa **existe y no está ya retirada**.
4. **Instantánea**, capturada **antes** de marcar.
5. Marca de `deleted_at` y escritura del registro de eliminación, **en la misma transacción**.

!!! danger "Los dos errores de este requerimiento son los mismos que `RF-PM-006` documentó, y los dos producen un sistema que funciona"

    **Capturar la instantánea después de marcar**: el registro guardaría la fila ya retirada y diría que se retiró algo que ya lo estaba. Nada falla.

    **Desactivar «de paso» al retirar**: todas las instantáneas dirían `is_active = false`, y ese dato dejaría de distinguir la tasa que estaba rigiendo de la que ya estaba suspendida. La salvaguarda habría destruido la evidencia que protege. `CA-SP-569` lo comprueba.

## 6. Autorización

`@PreAuthorize("hasAuthority('exchange-rates:delete')")`.

## 7. Auditoría

Un registro en `audit_deletion_log` con el motivo, el módulo, la entidad, el identificador y la **instantánea**. **En la misma transacción que la marca**: si una de las dos quedara fuera, habría una fila retirada sin motivo o un motivo sin fila retirada.

**Sin evento de seguridad**: una tasa no concede privilegios.

## 8. Transaccionalidad

Una transacción con bloqueo pesimista sobre la fila. Dos retiros simultáneos se serializan y el segundo recibe `404`.

## 9. Impacto sobre otros módulos

Ninguno. **Y sobre `RF-SP-048`**: las retiradas dejan de aparecer salvo que se pidan, lo que aquel requerimiento ya contempla (`CA-SP-549`).

## 10. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`DELETE` con el motivo en el cuerpo** | La RFC 9110 no lo define y un intermediario puede descartarlo |
| **Eliminación física** | `RN-SP-033`: la fila permanece para que una conversión futura pueda decir con qué tasa se hizo |
| **Reutilizar la suspensión de `RF-SP-049`** | Responden dos preguntas distintas, y fundirlas borraría la señal que separa una corrección de error de una decisión comercial |
| **Retiro idempotente** | Retirar dos veces con dos motivos dejaría el segundo escrito sobre un hecho que ocurrió antes y por otra razón |
| **Una operación de restauración** | Convertiría el registro de eliminación en un borrador |

## 11. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | La instantánea se captura **después** de marcar | `CA-SP-567` comprueba su contenido, y la prueba lee `is_active` dentro de ella |
| 2 | El retiro **desactiva de paso** | `CA-SP-569`: una tasa activa se retira **activa** |
| 3 | El periodo **no se libera** porque alguien reescribió el `EXCLUDE` sin el `WHERE` | `CA-SP-568` registra otra tasa sobre los mismos días después de retirar |
| 4 | La marca y el registro caen en transacciones distintas | Una prueba que fuerza el fallo del segundo comprueba que **la marca tampoco queda** |

## 12. Estrategia de prueba

- **Unitaria del agregado**: `delete` devuelve si hubo cambio, y **no es idempotente**.
- **Integración**: los siete criterios de `spec.md` §12.
- **De liberación del periodo**: retirar y volver a registrar sobre los mismos días — es la que prueba que el `WHERE` del `EXCLUDE` está donde debe.
- **Concurrente**: dos retiros simultáneos — uno retira, el otro recibe `404`.
