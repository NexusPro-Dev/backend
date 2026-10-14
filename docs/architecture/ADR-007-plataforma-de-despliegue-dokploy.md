# ADR-007 — Plataforma de despliegue: VPS propio con Dokploy

| Campo | Valor |
|---|---|
| Estado | **Aceptada** |
| Fecha | 14-10-2026 |
| Decide | Responsable del proyecto |
| Redacta | Bonilla Diaz William Steven |
| Sustituye | [`ADR-002`](ADR-002-plataforma-de-despliegue-railway.md) — Plataforma de despliegue: Railway |
| Documentos afectados | `architecture.md` §12 y §16 · `security.md` §12 (D-21) · `deployment.md` · `manual-de-despliegue.md` · `Dockerfile` · `.github/workflows/ci.yml` · en el frontend, `DF-13`, su `deployment.md` y su manual |

---

## Contexto

[`ADR-002`](ADR-002-plataforma-de-despliegue-railway.md) eligió Railway para `testing` y `production` y dejó escritas las condiciones para reabrirlo. **El 14-10-2026 el responsable del proyecto decide llevar los dos entornos a un servidor propio**, administrado con [Dokploy](https://dokploy.com): una plataforma de despliegue de código abierto que se instala en el VPS y ofrece lo que Railway daba hecho —dominios con TLS, variables por entorno, despliegue desde un registro o un repositorio, bases de datos con copias programadas— sobre Docker Swarm y Traefik.

Hay que decirlo antes que nada, porque `ADR-002` lo descartó: **«VPS con `docker-compose.yml`» era la peor de las opciones**, y lo sigue siendo. Los motivos eran cuatro, y conviene ver cuáles resuelve Dokploy y cuál no:

| Objeción de `ADR-002` | Con Dokploy |
|---|---|
| Convierte el archivo de desarrollo en infraestructura de producción | **Resuelta.** El `docker-compose.yml` sigue sin intervenir en ningún despliegue: Dokploy ejecuta la **imagen** como un servicio de Swarm, con sus propias variables |
| Con sus credenciales versionadas | **Resuelta.** Los secretos viven en Dokploy, no en el repositorio (Art. IV.3) |
| Traslada a una persona los certificados y las copias de seguridad | **Resuelta en el mecanismo:** Traefik pide y renueva los certificados de Let's Encrypt, y Dokploy programa copias de PostgreSQL hacia un almacenamiento S3. **No en la verificación:** que una copia se restaure sigue siendo trabajo de alguien |
| Y el sistema operativo y sus actualizaciones | **No resuelta.** Es el coste real de esta decisión, y se acepta con nombre (§Consecuencias) |

Lo que el sistema necesita no ha cambiado desde `ADR-002`: **una instancia del backend, una base de datos, HTTPS y despliegue automático desde la rama**, con la réplica única como restricción de diseño (`deployment.md` §2.1).

## Decisión

**El backend y el frontend se despliegan en un VPS propio administrado con Dokploy, en un proyecto con los dos entornos que exige el Art. IX.4. Las imágenes las construye GitHub Actions y las publica en GHCR; Dokploy no compila nada.**

Seis piezas:

1. **Un servidor**, Ubuntu 24.04 LTS, con **4 vCPU, 8 GB de RAM y 80 GB de disco NVMe como mínimo**. Lleva los dos entornos a la vez —`testing` y `production`— con **límites de memoria por servicio** (`deployment.md` §5.4), de modo que pruebas no puede quitarle memoria a producción. La medida sale de sumar lo que corre: dos backends de Java 21, dos PostgreSQL 17, dos nginx y el propio Dokploy —su panel, su PostgreSQL, su Redis y Traefik—, que se acerca a 6 GB con los límites puestos.
2. **Un proyecto de Dokploy, `nexus`, con dos entornos**, cada uno con su servicio `backend`, su base `postgres` y su `frontend`. No se comparte base, ni secreto de firma, ni credencial de proveedor — igual que en `ADR-002`.
3. **La imagen se construye una vez, fuera del servidor.** Cuando el CI de una rama vigilada queda en verde, GitHub Actions construye la imagen con el `Dockerfile` del repositorio, la publica en `ghcr.io/nexuspro-dev/<repositorio>` con dos etiquetas —la de la rama y `sha-<commit>`— y llama a la API de Dokploy para desplegarla. **Compilar en el VPS queda descartado:** Maven y el JDK piden más de un gigabyte durante varios minutos, en la misma máquina que sirve producción.
4. **Desplegar sigue siendo integrar.** `develop` despliega `testing` y `main` despliega `production`, sin acción manual. Lo nuevo es que **el despliegue espera al CI**: en Railway la plataforma construía al recibir el commit, con independencia de que el pipeline estuviera en rojo.
5. **Lo que se puede versionar, se versiona.** La sonda de salud pasa del `railway.json` al `HEALTHCHECK` del `Dockerfile`, que Swarm obedece sin que nadie la configure. El resto de la configuración del servicio —réplica única, orden del relevo, plazo de apagado, límites— **Dokploy la guarda en su base y no la lee de ningún archivo del repositorio**. Por eso `deployment.md` §5 transcribe cada valor, uno por uno.
6. **PostgreSQL 17 como servicio de base de datos de Dokploy**, con su volumen y **copias programadas hacia un almacenamiento compatible con S3**, fuera del servidor. Una copia que vive en el mismo disco que la base no sobrevive a lo que suele obligar a restaurar.

**Lo que esta decisión NO es:** no es alta disponibilidad. **Un servidor es un único punto de fallo** para los dos entornos y para los dos repositorios a la vez, y se acepta como se aceptó la réplica única: el sistema todavía no tiene un requisito de disponibilidad que lo impida.

## Alternativas consideradas

| Alternativa | Por qué se descarta |
|---|---|
| **Seguir en Railway** | Es lo que había y funcionaba. El cambio es decisión del responsable del proyecto: tener la máquina y su coste bajo control propio. `ADR-002` lo dejó previsto en su cuarta condición de revisión |
| **VPS con `docker-compose.yml`** | Lo que `ADR-002` ya descartó, por los motivos de la tabla de §Contexto. Dokploy es justo la diferencia entre esto y la decisión tomada |
| **Coolify** | El equivalente más directo, con más años y más plantillas. Se prefiere Dokploy porque las tres cosas que este proyecto necesita —una imagen desde un registro, una API para desplegarla desde CI y la configuración de Swarm del servicio— están a la vista en su panel, sin una capa de abstracción que traducir. **Es una preferencia, no una incompatibilidad**: lo que dice `deployment.md` se trasladaría a Coolify casi sin cambios |
| **Dokploy compilando desde GitHub** | Es el camino por defecto de Dokploy y el más corto de montar. Se descarta por la pieza 3: la compilación compite por memoria y CPU con producción, y además **no espera al CI** |
| **Un servidor por entorno** | Es lo correcto en cuanto haya usuarios reales, y Dokploy lo permite desde el mismo panel. Hoy duplica el coste para aislar unas pruebas que nadie está haciendo bajo carga. Queda como condición de revisión |

## Consecuencias

### Lo que se gana

- **El despliegue espera al CI.** Una rama en rojo no llega a ningún entorno, y el Art. XI.2 deja de depender de que nadie integre en rojo.
- **El artefacto desplegado es exactamente el que se construyó una vez.** La etiqueta `sha-<commit>` identifica qué corre en cada entorno, y volver a una anterior es desplegar esa etiqueta (`deployment.md` §12).
- **D-21 tiene por fin un valor que poner** (§«La IP de la auditoría», abajo).
- **La salida C de [`ADR-004`](ADR-004-raspado-de-metricas-y-alertas.md) se vuelve verificable.** Un `management.server.port` aparte solo es alcanzable desde la red interna de Swarm: Traefik no lo enruta si no se le asigna un dominio, y Swarm no lo publica si no figura en *Ports*. Este ADR no decide `ADR-004`, pero le quita la incógnita que tenía.

### Lo que cuesta

- **El sistema operativo es nuestro.** Actualizaciones de seguridad —`unattended-upgrades` como mínimo—, acceso SSH solo con llave, y un firewall **del proveedor** delante. Esto último no es opcional: **Docker publica puertos saltándose UFW**, de modo que un `ufw deny` en el propio servidor no cierra lo que Docker abre.
- **El panel de Dokploy es una puerta nueva**, con acceso total a todos los entornos y sus secretos. Lleva dominio propio con HTTPS, doble factor, y el puerto `3000` cerrado en cuanto el dominio responde.
- **La configuración del servicio vive fuera del repositorio.** `railway.json` se revisaba en un diff; la configuración de Swarm en Dokploy, no. La mitigación es que `deployment.md` §5 la transcribe y que la verificación de §11 comprueba que sigue así. **Un valor cambiado en el panel y no en el documento es un defecto del documento.**
- **Las copias de seguridad son nuestras de punta a punta.** Dokploy las programa; restaurar una antes de que haya datos reales sigue siendo una obligación, igual que lo era con Railway (`deployment.md` §12).
- **Swarm mata el proceso a los diez segundos por defecto**, y el apagado ordenado de la aplicación tiene treinta. Sin ajustar el plazo, el relevo corta peticiones en curso, que es justo lo que `server.shutdown: graceful` existe para impedir. Se fija en `deployment.md` §5.3.

### La IP de la auditoría

En Railway, D-21 no tenía valor posible: la dirección del borde no era fija ni publicada. **En Dokploy sí lo tiene**, y vale la pena explicar por qué, porque la explicación es lo que hay que verificar.

Dokploy ejecuta Traefik como **un contenedor normal**, no como servicio de Swarm, con los puertos `80` y `443` publicados en el anfitrión y conectado a la red `dokploy-network`. De ahí se siguen tres cosas:

1. **Traefik ve la IP real de quien llama.** Al no pasar por la malla de entrada de Swarm, la conexión llega a Traefik con su origen intacto.
2. **Traefik descarta el `X-Forwarded-For` que llega de fuera** —es su comportamiento por defecto cuando no se le declaran IP de confianza— y escribe el suyo. Lo que recibe el backend es la IP que Traefik vio, no la que el cliente escribió.
3. **El backend ve a Traefik con una dirección de la subred de `dokploy-network`.** Esa dirección cambia si el contenedor se recrea; **la subred no**.

**`TRUSTED_PROXIES` lleva la subred de `dokploy-network`**, que se lee en el servidor con `docker network inspect` (`deployment.md` §11.2). Con eso, el Art. V.15 —desde dónde se hizo cada operación— **se cumple en un entorno desplegado por primera vez**.

El precio, dicho como en `security.md` §12: **confiar en esa subred es confiar en todo lo que esté conectado a ella**, que son Traefik, el propio Dokploy y los servicios de los dos entornos. Ninguno de ellos llama al backend con una cabecera inventada, y quien pudiera hacerlo desde dentro ya tiene el control del servidor. Es la declaración más estrecha que esta plataforma permite.

**Lo que esta decisión NO autoriza:**

- **Subir las réplicas del backend.** Las tres piezas en memoria de `deployment.md` §2.1 siguen ahí. Swarm lo permite con un número en el panel, y por eso mismo hay que recordarlo.
- **Abrir `EXPOSE_API_DOCS` en un entorno desplegado.** Sigue en `false` por lo que argumenta [`ADR-001`](ADR-001-publicacion-del-contrato-openapi.md).
- **Copiar valores del `docker-compose.yml`.** Son credenciales de desarrollo en un archivo versionado.
- **Publicar el puerto de PostgreSQL.** La base se alcanza por la red interna. Para entrar a mano, por SSH al servidor (`deployment.md` §12).

**Condición de revisión.** Se reabre si ocurre cualquiera de estas cinco cosas:

1. Hay **usuarios reales** y `testing` comparte servidor con `production`: pruebas pasa a su propio servidor, desde el mismo panel.
2. El sistema necesita **más de una instancia**, y las tres piezas en memoria ya se resolvieron.
3. Aparece un requisito de **disponibilidad** que un solo servidor no cumple.
4. Aparece un requisito de **cumplimiento, residencia de datos o aislamiento de red** que el proveedor del VPS no cubra.
5. **Administrar el servidor** —parches, copias, incidentes— deja de ser marginal frente al coste de una plataforma gestionada.

La portabilidad sigue intacta por construcción: el artefacto es una imagen en un registro y toda la configuración entra por variables de entorno (Art. IX.1). Volver a Railway, o ir a AWS, es rehacer infraestructura, no reescribir la aplicación.

## Seguimiento

| # | Pendiente | Dónde |
|---|---|---|
| 1 | **Retirar `railway.json` de los dos repositorios** cuando los servicios de Railway se apaguen. Hasta entonces se conservan, para no tumbar un entorno que siga vivo allí | Este ADR |
| 2 | **Restaurar una copia de seguridad** en un servicio de pruebas antes de que haya datos reales | `deployment.md` §12 |
| 3 | **Canal compartido detrás de `AccessRevocationPublisher`**, condición previa a cualquier segunda instancia. Heredado de `ADR-002` | `security.md` §4.5 |
| 4 | **Raspado de métricas y alertas.** Heredado de `ADR-002`; la salida C de `ADR-004` pasa a ser comprobable | [`ADR-004`](ADR-004-raspado-de-metricas-y-alertas.md) |
| 5 | **Comprobar la IP de la auditoría** sobre el despliegue real: una petición desde una IP conocida tiene que quedar con esa IP en `request_log` | `deployment.md` §11.2 |
