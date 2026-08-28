# PLAN — `RF-CM-005` Consultar la comisión efectiva

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-005` |
| Especificación | [`spec.md`](spec.md) |
| `spec.md` aprobada el | 28-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-08-2026 |
| Bloqueado por | **`RN-SP-025`**, sin implementar — ver §10 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Las decisiones técnicas que la especificación deliberadamente no toma.

    **Prueba de pertenencia:** si al negocio no le importa ni lo entendería, va aquí.

El comportamiento es el de [`spec.md`](spec.md) y no se repite. La mecánica común la fijó el plan de [`RF-CM-001`](../001-registrar-tarifa-comision/plan.md) y **este documento la hereda sin repetirla**.

---

## 1. Enfoque

**La precedencia se resuelve en la base y en una sola sentencia**, no leyendo cuatro veces y eligiendo en Java.

El motivo no es rendimiento: son cuatro consultas contra una tabla pequeña y la diferencia sería inapreciable. Es que **una sola sentencia hace imposible el orden equivocado**. Con cuatro lecturas y un `if`, el orden vive en el flujo de control y una refactorización puede alterarlo sin que nada falle — devolvería un porcentaje **plausible**, que es el error que `spec.md` §2 existe para evitar.

Se ordenan las candidatas por especificidad y **se toma la primera**:

```sql
SELECT ...
  FROM commission_rates
 WHERE deleted_at IS NULL
   AND role_id = :rol
   AND valid_from <= :fecha
   AND (valid_to IS NULL OR valid_to >= :fecha)
   AND (product_id = :producto OR product_id IS NULL)
   AND (user_id    = :persona  OR user_id    IS NULL)
 ORDER BY (user_id IS NOT NULL) DESC, (product_id IS NOT NULL) DESC
 LIMIT 1
```

**El orden es la regla `RN-CM-004` escrita una vez.** La persona pesa más que el producto, y por eso su criterio va primero: una excepción de persona sin producto gana a una tarifa de producto sin persona.

## 2. Cambios de esquema

**Ninguno.** El índice GiST de `V44` tiene `role_id` como primera columna y sostiene el acceso.

## 3. Componentes afectados

| Capa | Componente | Nuevo / Modificado | Responsabilidad |
|---|---|---|---|
| `domain/repository` | `CommissionRateQueryRepository` | **Modificado** | Gana `resolve(rol, producto, persona, fecha)` |
| `domain/service` | `ResolveCommissionService` | Nuevo | Caso de uso: determina el rol vendedor y delega la precedencia |
| `application` | `EffectiveCommissionResponse` | Nuevo | El porcentaje, la tarifa aplicada, el **grado** y el rol considerado |
| `interfaces` | `CommissionResolutionController` | Nuevo | `GET /api/v1/commissions/effective` |

**Controlador aparte y no un método más del de tarifas**, porque es otro recurso: no devuelve una tarifa del catálogo sino **una respuesta calculada**. Es el mismo corte que separó la oferta propia del catálogo en `PM`.

## 4. Contrato de API

`GET /api/v1/commissions/effective?userId=&productId=&onDate=` · `200 OK`.

**La respuesta distingue tres desenlaces, y ninguno es un error:**

| Desenlace | Cómo se ve |
|---|---|
| Hay tarifa | `percentage` con valor, `rate` con la que ganó, `scope` con su grado |
| **No hay tarifa declarada** | `percentage` **nulo y presente**, `rate` nulo, y un motivo que lo dice |
| **La persona no comisiona** | Igual, con el motivo propio de `FA-003`: no porta rol vendedor |

**`percentage` nulo y cero son cosas distintas y el contrato no las confunde.** Cero es una decisión declarada —no comisiona—; nulo es que nadie la tomó. Devolver cero en la ausencia haría indistinguible lo pensado de lo olvidado, y es el error que este endpoint no puede cometer porque quien lo consuma va a pagar con esa cifra.

| Estado | Cuándo |
|---|---|
| `400` | `VAL-006`, `VAL-012` |
| `403` | Sin el permiso `commissions:read` |
| `422` | `EX-001`, `EX-002`: la persona o el producto no existen |

**No hay `404`.** Que no haya tarifa no es un recurso ausente: la pregunta tiene respuesta y la respuesta es «nadie lo declaró».

## 5. Autorización

Permiso `commissions:read`. **Solo administrativa** (`spec.md` §3): no hay variante «la mía», y no la habrá hasta que se cierre D-22.

## 6. Auditoría

Ninguna. Es una lectura.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Impacto sobre otros módulos

Consume tres de los cuatro puertos de `RF-CM-001` §8: `UserCatalog` para `EX-001`, `ProductCatalog` para `EX-002` —**sin rechazar los retirados**, que aquí se resuelven con normalidad— y `SellerRoleCatalog` para el paso 2.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Cuatro consultas y elegir en Java | El orden viviría en el flujo de control y una refactorización podría alterarlo sin que nada falle |
| Devolver **cero** cuando no hay tarifa | Haría indistinguible «no comisiona» de «nadie lo declaró», y quien consuma esto va a pagar con esa cifra |
| `404` cuando no hay tarifa | La pregunta tiene respuesta; obligaría a distinguir un fallo de un resultado legítimo |
| Rechazar el producto retirado | Preguntar qué se pagaba por algo que ya no se vende es legítimo, y es la consulta que una liquidación atrasada necesita |
| Devolver solo el porcentaje | Sin saber **qué tarifa** ganó, corregirla obliga a reconstruir la precedencia a mano — justo lo que este endpoint evita |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`RN-SP-025` no está implementada.** Sin ella una persona puede portar dos roles vendedores, y el paso 2 deja de ser determinista | **Bloqueo declarado.** Este requerimiento no puede darse por terminado antes. Mientras tanto, `SellerRoleCatalog` debe **fallar de forma visible** si encuentra más de uno, en lugar de elegir en silencio |
| 2 | Un consumidor futuro reimplemente la precedencia por su cuenta | Es la razón de ser del endpoint. Se mitiga con documentación y con que la consulta no esté disponible de otra forma |

**Sobre el riesgo 1, la decisión concreta:** ante dos roles vendedores, el puerto **lanza**. Devolver uno cualquiera sería el defecto que este módulo entero intenta evitar —un resultado plausible— y devolver vacío diría «no comisiona», que es falso. Fallar ruidosamente es la única de las tres que no miente.

## 11. Estrategia de prueba

| Qué | Nivel | Detalle |
|---|---|---|
| Los cuatro grados de precedencia | Integración | `CA-CM-039` a `CA-CM-042`, uno por escalón y el último con los cuatro declarados a la vez |
| La fecha | Integración | `CA-CM-043` a `CA-CM-045`: se ignora lo que no rige, sin fecha es hoy, y con fecha pasada gana la de entonces |
| Retiradas | Integración | `CA-CM-046`: se ignoran y gana la siguiente en precedencia |
| **Cero frente a ausencia** | Integración | `CA-CM-047` y `CA-CM-048`, que es la pareja que este endpoint no puede confundir |
| Producto retirado | Integración | `CA-CM-049` |
| Sin rol vendedor | Integración | `CA-CM-050`, distinguido de que no haya tarifa |
| **Dos roles vendedores** | Integración | El puerto **falla de forma visible**, y no elige. Es la prueba del bloqueo de §10 |
