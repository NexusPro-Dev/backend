# PLAN — `RF-SP-078` Recibir los avisos de los brokers

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-078` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 08-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Un solo tiempo y una sola transacción**, al revés que las pasarelas de pago (`RF-MV-041` §1): allí hay un segundo tiempo porque hay algo que aplicar, y aquí no lo hay. Comprobar, guardar y responder `200`.

**El cuerpo se lee del flujo de la petición, como bytes, y no con `@RequestBody`.** Es la decisión que más pesa, y sale de no saber qué mandará cada broker: con un formulario (`application/x-www-form-urlencoded`), el contenedor y Spring **reconstruyen el cuerpo a partir de los parámetros**, mezclando los de la dirección con los del cuerpo, y cualquier `getParameter` previo lo consume. Leer `getInputStream()` directamente da los bytes exactos que llegaron, y por la misma razón **la dirección se lee de `getQueryString()` y se desarma a mano** —nunca con `@RequestParam`, que llama a `getParameter` y vaciaría el cuerpo de un formulario—. El orden de las comprobaciones es el de la spec §8: broker, secreto configurado, secreto, tamaño; nada se lee del cuerpo antes de saber que el aviso es del broker.

**El secreto se compara con `MessageDigest.isEqual`** sobre los bytes UTF-8 (`VAL-001`). El valor de `token` **sale de la dirección antes de guardarla**, y un `token` repetido es `EX-001`.

---

## 2. Los secretos

**En el entorno, uno por broker, y por identificador**:

```yaml
nexus:
  brokers:
    notification-tokens:
      "[01a081f0-6000-7101-9c4f-5e7adb000001]": ${BROKER_TOKEN_IQOPTION:}
      "[01a081f0-6000-7102-9c4f-5e7adb000002]": ${BROKER_TOKEN_EXNOVA:}
      "[01a081f0-6000-7103-9c4f-5e7adb000003]": ${BROKER_TOKEN_EXOPTION:}
```

Los identificadores son los que fija `V9`, iguales en todos los entornos, y por eso pueden ir escritos en `application.yml`; **el secreto no**, que llega por variable y vacío por omisión. **Vacío es «sin configurar»** (`EX-002`): la suite corre así salvo donde la prueba lo fija. Un broker que se añada al catálogo necesitará su línea aquí y su variable; sin ellas responde `503`, que es el comportamiento seguro.

**No van a la base**, que sería lo natural para algo «por broker»: el catálogo se puebla por migración (`RN-SP-039`), y un secreto en una migración acaba en el repositorio. Es la misma razón por la que las credenciales de Stripe viven en el entorno.

---

## 3. Cambios de esquema

**`V85__sp_avisos_de_brokers.sql`** crea `broker_notifications` con la forma de [`requirements/sp.md` §10.25](../../../requirements/sp.md): `ck_broker_notifications_method` (`GET`, `POST`), `fk_broker_notifications_broker` sin `ON DELETE` e `ix_broker_notifications_broker (broker_id, received_at DESC)`. Comentarios en la tabla y en las columnas que lo necesitan. **Ningún permiso**: el catálogo no se mueve.

---

## 4. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `infrastructure` | `BrokerNotificationSettings` | Nuevo | `@ConfigurationProperties("nexus.brokers")`, `Map<UUID, String> notificationTokens`; `tokenOf(id)` devuelve vacío si falta o está en blanco |
| `application` | `BrokerNotice` | Nuevo | Lo que llegó, sin tipos del servlet: método, cadena de consulta cruda, cabeceras, cuerpo, tipo de contenido, IP |
| `domain/repository` | `BrokerNotificationRepository`, `JpaBrokerNotificationRepository` | Nuevos | `isActive(brokerId)` y `insert(...)`, SQL nativo con `CAST(:x AS jsonb)`, como `JpaGatewayEventRepository` |
| `domain/service` | `ReceiveBrokerNotificationService` | Nuevo | Las comprobaciones de §1, desarmar la consulta, filtrar cabeceras y guardar. Identificador con `UuidV7Generator` |
| `interfaces` | `BrokerNotificationController` | Nuevo | `GET` y `POST /api/v1/brokers/{id}/notifications`; lee el flujo con tope de 64 KiB + 1 y la IP de `RequestContext` |
| `shared/security` | `SecurityConfig` | `"/api/v1/brokers/*/notifications"` en `RUTAS_PUBLICAS` | Fuera de `RateLimitFilter` —que solo cubre las rutas que nombra— y sin CORS |
| `shared/observability` | `RequestLogFilter` | Oculta el valor de todo parámetro `token` de la cadena de consulta | `token=[OCULTO]`; el resto de la cadena, igual |
| `resources` | `application.yml` | El bloque de §2 | |

**Las cabeceras que no se guardan**: `authorization`, `proxy-authorization` y `cookie`. Los nombres van en minúsculas —HTTP no distingue— y los valores repetidos, en lista. **La consulta**: cada par `nombre=valor` se descodifica como `application/x-www-form-urlencoded` (UTF-8), en el orden en que llegó, y un nombre sin `=` vale cadena vacía.

---

## 5. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/brokers/{id}/notifications?token=…` | **Ninguno: pública**, autenticada por el secreto del broker |
| `POST` | `/api/v1/brokers/{id}/notifications?token=…` | **Ninguno: pública**, autenticada por el secreto del broker |

`POST` admite **cualquier tipo de contenido** (`consumes = "*/*"`), y los dos admiten **cualquier otro parámetro** en la dirección.

| Código | Cuándo |
|---|---|
| `200` | Guardado; cuerpo vacío |
| `400` | Cuerpo de más de 64 KiB (`EX-004`), o `{id}` que no es un UUID (`VAL-001` del manejador global) |
| `401` | Sin `token`, con `token` repetido o con uno que no es el de ese broker (`EX-001`) |
| `404` | Broker inexistente o inactivo (`EX-003`) |
| `503` | El broker no tiene secreto configurado (`EX-002`) |

**Se documenta en el contrato como ruta de los brokers**, con una etiqueta propia, para que nadie la llame desde el frontend.

---

## 6. Autorización

**Ninguna de las del sistema**, y es deliberado ([`security.md`](../../../security.md) §6). Lo autentica el secreto, y **`EndpointPermissionsIT` declara las dos firmas en `PUBLICAS`**, con el motivo.

---

## 7. Auditoría

**El aviso mismo es la constancia**, en `broker_notifications`, y **no se audita aparte**: no cambia nada que auditar. Un rechazo deja su fila en `request_log` —con el `token` oculto— como cualquier petición. **El cuerpo no va al log de aplicación**: puede traer datos de las personas.

---

## 8. Enmiendas a otros documentos

Aplicadas el 08-10-2026, antes que esta tripleta: [`requirements/sp.md`](../../../requirements/sp.md) v1.107.0 (`RN-SP-066`, la ficha, §8, §9, §10.25, y `RF-SP-054` partido), [`modelo-datos.md`](../../../modelo-datos.md) v0.106.0, [`security.md`](../../../security.md) v0.121.0 y la matriz de [`requirements.md`](../../../requirements.md) v0.324.0. **Con el código**: [`deployment.md`](../../../deployment.md) gana las tres variables y cómo configurar el panel, y [`api/index.md`](../../../api/index.md) su fila.

---

## 9. Impacto sobre otros módulos

**Ninguno.** Nada lee la tabla; `RF-SP-054` será quien lo haga.

---

## 10. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Interpretar ya con un formato supuesto | Es lo que la decisión del 08-10-2026 evita: un supuesto equivocado completa la cuenta de otra persona |
| El broker por nombre en la ruta (`/brokers/iqoption/…`) | §10.17 de `requirements/sp.md`: el nombre es la clave de negocio y se renombra por migración; la dirección quedaría rota en el panel del broker |
| El secreto en una cabecera | Los paneles de afiliados no dejan añadir cabeceras |
| Un solo secreto para los tres | El de uno abriría la ruta de los otros, y cambiarlo obligaría a tocar los tres paneles |
| Descartar repetidos con un resumen del contenido | Dos depósitos iguales del mismo cliente se parecen tanto como una reentrega; descartar es decidir sin saber (`FA-001`) |
| Leer el cuerpo con `@RequestBody` y la dirección con `@RequestParam` | Con un formulario, Spring mezcla los dos y el cuerpo llega reconstruido o vacío (§1) |
| Guardar el cuerpo como `jsonb` | Solo vale si es JSON, y no se sabe si lo será |

---

## 11. Riesgos

| Riesgo | Mitigación |
|---|---|
| El secreto se filtra por la dirección, que queda en registros | No se guarda con el aviso; `request_log` lo oculta. El borde de la plataforma puede registrarla igual: por eso el secreto es uno por broker y se cambia sin tocar código: la variable y la dirección del panel |
| Que alguien con el secreto llene la tabla | El secreto es solo del broker; el tope de 64 KiB por aviso; y si pasa, se cambia el secreto. La cota de tasa estorbaría a la reentrega |
| Que un filtro previo consuma el cuerpo de un formulario | `CA-SP-899` lo prueba de punta a punta, con la cadena de filtros real |

---

## 12. Estrategia de prueba

Integración, **`BrokerNotificationsIT`**, con `MockMvc` y la cadena de filtros real, y los secretos de `IQOPTION` y `EXNOVA` fijados por `@TestPropertySource` —`EXOPTION` queda sin secreto para `CA-SP-902`—: `CA-SP-897` a `CA-SP-907`, leyendo `broker_notifications` y `request_log` con SQL. `CA-SP-903` desactiva un broker en la prueba y lo repone al terminar, para no dejar el catálogo distinto a las suites que vienen detrás. `CA-SP-907` declara una cuenta y comprueba que no se mueve. **La tabla se vacía al empezar y al terminar** cada prueba. `EndpointPermissionsIT` gana las dos entradas.
