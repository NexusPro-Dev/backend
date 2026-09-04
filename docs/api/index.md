# El contrato de la API

| Campo | Valor |
|---|---|
| Proyecto | NEXUS — Renovación de plataforma |
| Empresa | FACTECH GROUP SAS |
| Documento | `api/index.md` |
| Versión | 1.1.0 |
| Estado | Publicado |
| Responsable técnico | Bonilla Diaz William Steven |
| Fecha de creación | 25-08-2026 |
| Última actualización | 04-09-2026 |
| Documento superior | `architecture.md` v0.14.0 |

---

## 1. Dónde está

La especificación OpenAPI de **todas** las rutas se publica como archivo versionado, en los dos formatos, y **no exige credenciales ni que haya nada en ejecución**:

| Formato | URL |
|---|---|
| JSON | <https://nexuspro-dev.github.io/backend/api/openapi.json> |
| YAML | <https://nexuspro-dev.github.io/backend/api/openapi.yaml> |

En el repositorio son `docs/api/openapi.json` y `docs/api/openapi.yaml`.

El Art. VIII.7 hace de esta especificación el **único** contrato entre backend y frontend: no deben acordarse comportamientos por fuera de ella. Si algo que el frontend necesita no está aquí, la respuesta correcta no es escribirlo a mano en el cliente sino **abrir un issue en este repositorio**.

## 2. Por qué un archivo y no una instancia en ejecución

Porque atar la construcción del frontend a que haya un backend levantado hace que un Pull Request suyo deje de ser reproducible, y en un entorno sin red no se pueda construir. Es la decisión de [`ADR-001`](../architecture/ADR-001-publicacion-del-contrato-openapi.md).

**`/v3/api-docs` y Swagger UI siguen cerrados en los entornos desplegados** (`EXPOSE_API_DOCS` en `false`). Que el contrato sea legible aquí no autoriza a dejar Swagger abierto donde hay datos reales, donde además invita a probar contra ellos. En **local** sí están abiertos: `docker-compose.yml` fija la bandera en `true`, de modo que quien levante el entorno tiene `http://localhost:8080/swagger-ui.html`.

## 3. Cómo se mantiene al día

No a mano. `OpenApiContractIT` **reescribe** los dos archivos durante `mvn verify`, y CI falla si lo comprometido no coincide con lo generado (Art. VIII.6). Eso convierte en pipeline en rojo lo que de otro modo sería un contrato que envejece sin que nadie lo note.

En la práctica: **quien cambia un endpoint ejecuta `mvn verify` y commitea `docs/api/`**. No hay un paso manual de publicación.

## 4. Lo que el cliente necesita saber

**Base.** Todas las rutas cuelgan de `/api/v1`. La URL del servidor depende del entorno y **no** viaja en el contrato: se configura en el cliente.

**Autenticación.** `Bearer` con JWT en la cabecera `Authorization`. El contrato lo declara como esquema global, de modo que **toda operación lo exige salvo tres**: `POST /auth/login`, `POST /auth/refresh` y `POST /auth/logout`, que no pueden pedir el token que aún no se tiene —o que ya no se tiene—.

**El token de acceso dura quince minutos** y no es revocable: solo expira. El refresco rota el token de refresco en cada uso, así que el cliente debe **guardar el nuevo y descartar el anterior**; reutilizar uno ya usado se interpreta como robo y revoca la familia entera.

**CORS.** El navegador solo puede leer la respuesta desde un origen autorizado, y la lista se declara por entorno en `CORS_ALLOWED_ORIGINS` (`security.md` §6.1). En un despliegue nuevo **está vacía**, que es el valor seguro: si el frontend no puede llamar, es lo primero que hay que mirar. La respuesta expone `Location` y `X-Correlation-Id`.

**Errores.** Formato RFC 9457 uniforme (`architecture.md` §7.3), con `correlationId` siempre presente: es el identificador que conviene mostrar al usuario cuando algo falla, porque es con el que el equipo lo localiza.

**Paginación.** Los listados devuelven `content`, `page`, `size`, `totalElements`, `totalPages` y **`totalIsExact`**. Ese último importa: en los registros de auditoría el total es exacto **hasta un techo** y aproximado por encima, y `totalPages` es entonces una cota inferior — pedir una página más allá sigue funcionando (`architecture.md` §7.4).

**Límite de tasa.** Los tres endpoints públicos de autenticación están acotados y responden `429` con `Retry-After` y `retryAfterSeconds`. El cliente debe **descontar** esos segundos y no calcular la espera con su propio reloj.

## 5. Generar el cliente

Con cualquier generador que acepte una URL. Por ejemplo:

```bash
npx @hey-api/openapi-ts \
  -i https://nexuspro-dev.github.io/backend/api/openapi.yaml \
  -o src/api
```

Se recomienda **fijar la generación en la construcción** del frontend y no commitear el cliente generado editado a mano: en cuanto se toca, deja de ser un reflejo del contrato y vuelve el acuerdo por fuera que el Art. VIII.7 prohíbe.

## 6. Lo que el contrato todavía no dice

**Las tres advertencias que esta sección traía desde el 25-08-2026 ya no valen, y se retiran en lugar de dejarse como fósil**: el `423` de cuenta bloqueada está documentado en `POST /auth/login` y `POST /auth/password`; el `429` lo está en las dos rutas de recuperación; y la recuperación de contraseña **existe** desde que se cerró **D-23** el 26-08-2026 —`POST /auth/password-recovery`, su `/confirmation` y `POST /auth/password`—.

Lo que sigue siendo cierto, y conviene saberlo antes de tropezar:

- **El `429` no está documentado en `POST /auth/login` ni en `/auth/refresh`**, aunque el filtro también las acota. Lo produce un filtro, que no pasa por las anotaciones del controlador: el comportamiento existe y es el descrito arriba, y la anotación falta en esas dos.
- **Los `operationId` llevan sufijos numéricos** —`registrar_3`, `listar_4`, `corregir_2`— en 24 de las 65 operaciones. Los genera springdoc al chocar dos métodos con el mismo nombre en controladores distintos, y **no son estables**: añadir otro `registrar` en cualquier módulo puede reasignar el número. `openapi-typescript` indexa por ruta y método, de modo que hoy no afecta al cliente generado; **cualquier generador que use el `operationId` como nombre de función sí se rompería en silencio**. Si el frontend cambia de generador, esto hay que resolverlo antes.
- **Los nombres de esquema tienen el mismo riesgo**, y por eso los de `MV` se declaran a mano: un registro anidado se publicaría como `Line`, `Party` o `Money` —genéricos en un espacio de nombres plano y compartido por todos los módulos—, de modo que se publican como `SaleLineRequest`, `SaleParty` y `SaleCurrency`. **Quedan sin resolver los que ya existían**: `Item`, `Neighbor`, `Person`, `RoleRef`, `UserRef` y el `Offered` de `RF-PM-007`. No es urgente y no es gratis: renombrarlos cambia tipos que el frontend ya usa.

## 7. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0.0 | 25-08-2026 | Se publica esta página. El contrato ya se versionaba desde `ADR-001`, pero **nada decía dónde encontrarlo ni cómo consumirlo**, de modo que el frontend tenía el archivo y no la instrucción. Se añade además el **YAML**, que es el formato que asumen por defecto los generadores de cliente: publicar solo el JSON obligaba a cada consumidor a convertirlo, y una conversión hecha en el lado del cliente es una copia del contrato que envejece por su cuenta. | Responsable técnico |
| 1.1.0 | 04-09-2026 | **Se retiran las tres advertencias de §6, que llevaban desde el 25-08-2026 diciendo lo contrario de lo que ocurre.** El `423` está documentado, el `429` también en las dos rutas de recuperación, y **la recuperación de contraseña existe** desde que D-23 se cerró el 26-08-2026 — el frontend leía aquí que no había endpoint mientras el backend lo publicaba. Se retiran en lugar de dejarse tachadas: una advertencia falsa cuesta más que ninguna. **En su sitio quedan dos riesgos reales del contrato generado**, que hasta hoy no estaban escritos en ninguna parte. El primero: **24 de los 65 `operationId` llevan sufijo numérico** —`registrar_3`, `listar_4`— porque springdoc los genera al chocar dos métodos con el mismo nombre en controladores distintos, y **el número no es estable**; hoy no afecta porque `openapi-typescript` indexa por ruta y método, pero cualquier generador que use el `operationId` como nombre de función se rompería **en silencio** al añadirse otro `registrar`. El segundo: **los nombres de esquema tienen el mismo problema**, porque el espacio de nombres es plano y compartido por todos los módulos. Por eso los tres registros anidados que estrena `MV` se publican con nombre declarado a mano —`SaleLineRequest`, `SaleParty` y `SaleCurrency` en lugar de `Line`, `Party` y `Money`—: mientras nadie los consume es gratis, y después es un cambio incompatible. Queda dicho lo que **no** se tocó y por qué: `Item`, `Neighbor`, `Person`, `RoleRef`, `UserRef` y `Offered` ya existen y renombrarlos cambiaría tipos que el frontend usa. | Responsable técnico |
