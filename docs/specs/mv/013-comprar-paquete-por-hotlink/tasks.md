# TASKS — `RF-MV-013` Comprar un paquete por el hotlink de un vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-013` |
| Plan | [`plan.md`](plan.md), aprobado el 16-09-2026 |
| Versión | 0.1.0 |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobadas por | Pendiente |
| Fecha de aprobación | Pendiente |
| Issue | Pendiente de crear |
| Rama | `feature/venta-de-productos` |

!!! info "Qué va en este documento"

    **En qué pasos se construye** lo que `plan.md` decidió, con su dependencia y su verificación. Ninguna tarea se da por `Hecha` sin que su verificación pase.

!!! abstract "Seis tareas, y ninguna vuelve a comprar un paquete"

    La compra la construye `RF-MV-012`. Lo que queda es **resolver el enlace**, **inyectar el vendedor** y **pedir el vínculo**.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PackageCatalog` gana la resolución **por nombre de usuario y código**, con las reglas de `RF-PM-026` | `RF-MV-012` · `T-01` | Un paquete que no es de alcance hotlink, uno vencido y un vendedor inexistente devuelven **lo mismo**: vacío | **Pendiente** |
| `T-02` | `BuyPackageService`: el vendedor **se recibe** en lugar de deducirse; quien llama decide cuál es | `RF-MV-012` · `T-04` | La compra propia sigue pasando sus doce criterios **sin cambios**: el servicio no sabe por dónde entró | **Pendiente** |
| `T-03` | `BuyPackageByHotlinkService`: resuelve el enlace, **rechaza la autocompra**, delega la compra y **pide el vínculo** | `T-01`, `T-02` | `CA-MV-061` y `CA-MV-065`; el vínculo se pide **después** de registrar la venta y en la misma transacción | **Pendiente** |
| `T-04` | `HotlinkPurchaseController`: `POST /api/v1/hotlinks/{username}/packages/{code}/purchases`, **sin permiso** y **con sesión** | `T-03` | Documentado; `401` sin token, y la ruta entra en la lista blanca de permisos — **no en la de rutas públicas** | **Pendiente** |
| `T-05` | `BuyPackageByHotlinkIT`: los siete criterios, `CA-MV-061` a `CA-MV-067` | `T-04` | Incluidas las dos que importan: la atribución al dueño del enlace **teniendo otro agente**, y el `404` uniforme | **Pendiente** |
| `T-06` | Contrato OpenAPI y matriz: `RF-MV-013` pasa de `Pendiente` a `En desarrollo` | `T-05` | La prosa dice que **ver el enlace es público y comprarlo no**, y que el rechazo del catálogo es `404` a propósito | **Pendiente** |

**`T-02` es la tarea delicada, y su verificación lo dice**: lo que hay que comprobar no es que la compra por hotlink funcione, sino que **la compra propia siga funcionando igual** después de abrir el hueco por donde entra el vendedor.

**`T-04` distingue dos listas blancas, y conviene no confundirlas**: esta ruta **no exige permiso** —va a la de permisos— pero **sí exige sesión**, de modo que **no** va a la de rutas públicas. Es justo lo contrario del hotlink que la publica.

---

## 2. Orden de ejecución

`T-01` y `T-02` son independientes entre sí y pueden ir a la vez; `T-03` las necesita a las dos. Después, `T-04` publica, `T-05` verifica y `T-06` documenta.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-MV-061`, `CA-MV-062` | `T-02`, `T-03`, `T-05` |
| `CA-MV-063` | `T-03`, `T-05` |
| `CA-MV-064` | `T-01`, `T-05` |
| `CA-MV-065` | `T-03`, `T-05` |
| `CA-MV-066` | `T-02`, `T-05` |
| `CA-MV-067` | `T-03`, `T-05` |

---

## 4. Bloqueos

**`RF-MV-012` — la compra del paquete.** Sin ella no hay nada que delegar. Es un bloqueo **de construcción y no de diseño**: esta tripleta se escribe igual, y se construye después.

**`RF-MV-011` — la compra por hotlink de un producto.** De ahí salen **la tabla `client_sellers` y la interfaz que `SP` publica para escribirla**. Si este requerimiento se construyera antes, tendría que traerlas él (`plan.md` §2), y **no se planifica así**.

**`RF-SP-045` — el registro de clientes por enlace.** El mismo de siempre: sin clientes colgados de un vendedor no hay camino feliz que probar.

---

## 5. Definición de terminado

- [ ] Las seis tareas en `Hecha`, con su verificación pasando.
- [ ] **`CA-MV-061` pasando**, que es la que garantiza que cobra quien trajo la venta.
- [ ] **`CA-MV-066` pasando**, que es la que garantiza que abrir esta puerta no cambió la otra.
- [ ] Los doce criterios de `RF-MV-012` **siguen en verde sin tocarlos**.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **también en la prosa**.
- [ ] La matriz de trazabilidad al día.
