# ADR-001 — Publicación del contrato OpenAPI

| Campo | Valor |
|---|---|
| Estado | **Aceptada** |
| Fecha | 24-08-2026 |
| Responsable técnico | Bonilla Diaz William Steven |
| Documentos afectados | `architecture.md` · `security.md` §6 |
| Origen | `R-01` del frontend (`NexusPro-Dev/frontend`, `architecture.md` §12) |

---

## Contexto

El Art. VIII.7 hace de la especificación OpenAPI publicada el **único** contrato entre backend y frontend. Hasta hoy, sin embargo, **no se publicaba en ninguna parte**:

- `/v3/api-docs` responde solo si `EXPOSE_API_DOCS` vale `true`, y su valor por defecto es `false`.
- `docs/api/` está vacío.

El frontend no puede generar su cliente sin el contrato, y escribir sus tipos a mano infringe su propia constitución. El resultado es que **cuarenta y dos de sus cuarenta y cuatro requerimientos están bloqueados** por algo que vive aquí.

Exponer el endpoint en un entorno no resuelve el problema: obligaría a levantar el backend en cada Pull Request del frontend solo para generar tipos. Lo que hace falta es un **archivo versionado**, obtenible sin que haya nada en ejecución.

## Decisión

**El contrato se publica como archivo versionado en `docs/api/openapi.json`, generado por una prueba de integración y verificado en CI.**

Tres piezas:

1. **`OpenApiContractIT`** —la clase que **ya existía** en `shared/architecture/` para comprobar que el contrato declara lo que debe— gana una prueba más: pide `/v3/api-docs` durante `mvn verify` y escribe el resultado, con las claves **ordenadas**, en `docs/api/openapi.json`.

    Se añade ahí y no en una clase nueva por dos razones. La primera es que es el mismo asunto: aquella clase ya vela por que el contrato diga la verdad, y publicarlo es el paso siguiente. La segunda es más concreta: sus pruebas obtienen `/v3/api-docs` **autenticando con un usuario de prueba**, no habilitando `EXPOSE_API_DOCS`. Eso evita un contexto de Spring adicional, que es lo que habría costado una clase con su propia propiedad.
2. **El archivo se versiona** y se revisa como cualquier otro cambio: un contrato que cambia se ve en el diff del Pull Request.
3. **CI falla si el archivo comprometido no coincide con el generado.** Es el Art. VIII.6 —*«el contrato documentado DEBE corresponder al comportamiento real; una divergencia se trata como defecto»*— convertido en algo verificable.

`EXPOSE_API_DOCS` **no cambia**: sigue en `false` por defecto. La prueba lo habilita solo para sí misma.

## Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Exponer `/v3/api-docs` y que el frontend lo consuma en tiempo de construcción** | Ata la construcción del frontend a que haya un backend levantado y accesible. Un Pull Request del frontend dejaría de ser reproducible, y en un entorno sin red no se podría construir |
| **`springdoc-openapi-maven-plugin`** | Hace lo mismo, pero arrancando la aplicación **una segunda vez** en la fase `integration-test`. La suite ya la arranca con Testcontainers: el plugin duplica el ciclo de vida más caro del pipeline para obtener un archivo |
| **Copiar el JSON a mano cuando cambie** | Se desincroniza el primer día que alguien olvide hacerlo, y nada lo detecta. Es justo la divergencia que el Art. VIII.6 trata como defecto |
| **Publicarlo solo como artefacto de CI**, sin versionarlo | Evita hacer público el contrato (ver §Consecuencias), pero el frontend deja de poder generar tipos sin credencial contra este repositorio, y el contrato deja de revisarse en el diff. Queda como salida si la consecuencia de seguridad se considera inaceptable |

## Consecuencias

### Lo que se gana

- El frontend puede generar su cliente de forma **reproducible**, sin nada en ejecución.
- Un cambio de contrato **se ve en el diff** de su Pull Request, que es donde se revisa.
- Una divergencia entre lo documentado y lo real **rompe el pipeline** en lugar de descubrirse en integración.

### Lo que cuesta

- Ejecutar `mvn verify` en local **deja el archivo modificado** en el árbol de trabajo si el contrato cambió. Es intencionado: queda listo para commitear.
- El archivo son unos **83 KB de JSON versionado**, que crecerá con cada endpoint. A cambio, su diff es legible: el orden es estable, y se comprobó reejecutando la suite dos veces y verificando que la huella del archivo no cambia.
- El archivo hay que **llevarlo al frontend**, que versiona su propia copia (`DF-04` de aquel repositorio). Hoy es un paso manual.

### La consecuencia que hay que mirar de frente

`security.md` §6 mantiene `/v3/api-docs` cerrado por defecto con este argumento: **«el contrato describe cada endpoint y cada permiso del sistema»**.

Comprometer `docs/api/openapi.json` **publica exactamente esa misma información** si el repositorio es público. Y el sitio de documentación ya se publica en GitHub Pages.

Se acepta a conciencia, y conviene que el argumento quede escrito y no se dé por supuesto:

- **La reserva del contrato nunca fue un control de seguridad**, sino defensa en profundidad. Todo endpoint deniega por defecto y exige su permiso (Art. IV.1); conocer una ruta no acerca a nadie a poder usarla.
- Lo que la reserva sí hacía era **encarecer el reconocimiento** de un atacante. Se renuncia a eso.
- **Ningún secreto viaja en el contrato**: describe rutas, formas y códigos de error, no credenciales ni datos.

**Lo que esta decisión NO autoriza:** `EXPOSE_API_DOCS` sigue en `false`. Que el contrato sea legible en el repositorio no es razón para dejar Swagger abierto en un entorno en ejecución, donde además invita a probar contra datos reales.

**Condición de revisión:** si el repositorio pasa a ser privado, o si alguna vez el contrato llegara a describir algo que no deba conocerse, esta decisión se reabre y la salida es el artefacto de CI de §Alternativas.

## Seguimiento

| # | Pendiente | Dónde |
|---|---|---|
| 1 | Llevar el contrato al frontend de forma automática, en lugar de copiarlo | Un flujo que abra Pull Request en `NexusPro-Dev/frontend` al cambiar el archivo |
| 2 | **CORS no está declarado** en ninguna parte de este repositorio | `R-02` del frontend. Sin él, publicar el contrato no basta: el navegador seguirá sin poder llamar |
| 3 | El `423` y el `429` de `RF-SP-034` **no declaran el cuerpo** de su respuesta, y la interfaz necesita el momento de expiración y el de espera como dato | `R-07` del frontend. Requiere requerimiento propio aquí |
