# Constitución del Proyecto NEXUS


| Campo                | Valor                                                                                      |
| -------------------- | ------------------------------------------------------------------------------------------ |
| Proyecto             | NEXUS — Renovación de plataforma                                                           |
| Empresa              | FACTECH GROUP SAS                                                                          |
| Documento            | `constitution.md`                                                                          |
| Versión              | 0.2.0                                                                                      |
| Estado               | Borrador                                                                                   |
| Responsable técnico  | Bonilla Diaz William Steven                                                                |
| Fecha de creación    | 19-08-2026                                                                                 |
| Última actualización | 19-08-2026                                                                                 |
| Ámbito               | Backend (aplicable como referencia al frontend hasta que este disponga de su propia copia) |


---

## 0. Propósito y autoridad

Este documento define los **principios no negociables** que gobiernan el desarrollo del sistema NEXUS. Deriva del *Documento de Especificación de Requerimientos y Estándares de Desarrollo de Software v1.0* (en adelante, **el Documento Marco**) y lo traduce en reglas verificables y aplicables sobre el código.

### 0.1 Jerarquía normativa

En caso de conflicto, prevalece el documento de mayor jerarquía:

1. **Constitución** (este documento) — principios no negociables.
2. **Documento Marco** — estándares y metodología.
3. **Especificación del módulo** (`docs/specs/`) — comportamiento esperado de cada funcionalidad.
4. **Decisiones de arquitectura** (`docs/architecture/`) — decisiones técnicas registradas.
5. **Implementación** — el código.

Ningún elemento de nivel inferior puede contradecir a uno superior. Si la implementación necesita contradecir una regla constitucional, **primero se enmienda la constitución** (§17), nunca al revés.

### 0.2 Interpretación del lenguaje normativo


| Término                | Significado                                                                       |
| ---------------------- | --------------------------------------------------------------------------------- |
| **DEBE** / **NO DEBE** | Obligación absoluta. Su incumplimiento bloquea la integración del cambio.         |
| **DEBERÍA**            | Recomendación fuerte. Apartarse exige justificación explícita en el Pull Request. |
| **PUEDE**              | Opcional, a criterio del equipo.                                                  |




### 0.3 Alcance

Aplica a todo el código, la documentación, las pruebas, la configuración y los procesos del proyecto, sin importar si fueron producidos por una persona o asistidos por inteligencia artificial.

---



## Artículo I — La especificación precede a la implementación

> *Principio: no se escribe código para resolver un problema que no ha sido especificado.*

**Reglas**

- **I.1** Toda funcionalidad DEBE contar con una especificación aprobada en `docs/specs/` antes de iniciar su implementación.
- **I.2** Toda especificación DEBE originarse en un requerimiento identificado bajo la nomenclatura `RF-[MÓDULO]-NNN`, `RNF-[CATEGORÍA]-NNN` o `RN-[MÓDULO]-NNN`.
- **I.3** La especificación DEBE contener, como mínimo: objetivo, contexto, requerimiento asociado, reglas de negocio, datos, flujo, validaciones, criterios de aceptación, casos límite, consideraciones técnicas y pruebas.
- **I.4** Si durante la implementación se descubre que la especificación es incompleta o incorrecta, se DEBE detener el desarrollo, corregir la especificación y luego continuar. NO DEBE resolverse la ambigüedad únicamente en el código.
- **I.5** Las correcciones triviales sin impacto funcional (formato, typos, comentarios) PUEDEN realizarse sin especificación previa.

**Verificación:** el Pull Request referencia el identificador del requerimiento y la ruta de su especificación.

---



## Artículo II — Todo requerimiento es verificable

> *Principio: un requerimiento que no puede probarse no es un requerimiento, es una intención.*

**Reglas**

- **II.1** Todo requerimiento funcional DEBE tener criterios de aceptación explícitos y observables (`CA-[COD-MÓDULO]-NNN`).
- **II.2** Los criterios de aceptación NO DEBEN redactarse en términos subjetivos ("rápido", "fácil", "intuitivo"); DEBEN expresarse como condiciones comprobables.
- **II.3** Todo requerimiento no funcional DEBE declarar una métrica y un umbral medible.
- **II.4** Cada criterio de aceptación DEBE tener al menos una prueba automatizada asociada.

**Verificación:** existe una prueba que falla si el criterio de aceptación deja de cumplirse.

---



## Artículo III — Todo cambio es trazable

> *Principio: siempre debe poder responderse "¿por qué existe esta línea de código?".*

**Reglas**

- **III.1** La cadena de trazabilidad DEBE mantenerse completa: **Requerimiento → Especificación → Issue → Pull Request → Código → Prueba → Resultado**.
- **III.2** Todo cambio DEBE integrarse mediante Pull Request. NO DEBE hacerse push directo a `main` ni a `develop`.
- **III.3** Los mensajes de commit DEBEN referenciar el identificador del requerimiento cuando aplique.
- **III.4** Todo Pull Request DEBE incluir: descripción del cambio, requerimiento relacionado, descripción de la solución, evidencia de pruebas, impacto técnico y consideraciones adicionales.
- **III.5** Todo Pull Request DEBE ser revisado y aprobado por al menos una persona distinta de su autor antes de integrarse.
- **III.6** La matriz de trazabilidad DEBE actualizarse como parte del cambio, no después.

**Verificación:** dado cualquier archivo del repositorio, es posible reconstruir el requerimiento que lo originó.

---



## Artículo IV — La seguridad se diseña, no se agrega

> *Principio: la seguridad es una condición de diseño, no una fase posterior.*

**Reglas**

- **IV.1** Todo endpoint DEBE declarar explícitamente su requerimiento de autenticación y los permisos necesarios. El comportamiento por defecto DEBE ser **denegar**.
- **IV.2** La autorización DEBE basarse en roles y permisos, aplicando el principio de mínimo privilegio.
- **IV.3** Los secretos y credenciales NO DEBEN almacenarse en el repositorio bajo ninguna circunstancia, ni siquiera en ramas temporales, archivos de ejemplo con valores reales, pruebas o comentarios.
- **IV.4** Toda entrada proveniente del exterior DEBE validarse y sanearse antes de utilizarse.
- **IV.5** Las consultas a base de datos DEBEN usar sentencias parametrizadas. NO DEBE construirse SQL por concatenación de cadenas.
- **IV.6** Las comunicaciones DEBEN cifrarse en tránsito en los entornos de *testing* y *production*.
- **IV.7** Los eventos relevantes de seguridad (autenticación, autorización denegada, cambios de permisos, operaciones sobre datos sensibles) DEBEN registrarse en la auditoría.
- **IV.8** Los registros de log NO DEBEN contener contraseñas, tokens ni datos personales sensibles.
- **IV.9** Toda decisión que relaje un control de seguridad DEBE documentarse en `docs/security/` con su justificación y responsable.

**Verificación:** revisión de seguridad obligatoria en el Pull Request de cualquier cambio que toque autenticación, autorización, persistencia o entrada de datos.

---



## Artículo V — Persistencia y datos

> *Principio: el esquema de datos es código, y como tal se versiona y se revisa.*

**Reglas**

- **V.1** El **único** motor de base de datos del proyecto es **PostgreSQL**. NO DEBE introducirse ningún otro motor relacional.
- **V.2** El código NO DEBE depender de características exclusivas de otro motor ni asumir semánticas ajenas a PostgreSQL.
- **V.3** La **fuente de verdad** del esquema son las migraciones versionadas dentro del repositorio. Cualquier modelo gráfico o diagrama externo es material de referencia y NO DEBE tratarse como autoridad sobre el esquema.
- **V.4** Todo cambio de esquema DEBE realizarse mediante una migración versionada, incremental y revisada en Pull Request. NO DEBE modificarse el esquema manualmente en ningún entorno.
- **V.5** Una migración ya integrada en `main` NO DEBE editarse; las correcciones se hacen con una migración nueva.
- **V.6** El esquema DEBE aplicar integridad referencial explícita (claves foráneas, restricciones de unicidad, `NOT NULL`) en lugar de delegar la integridad únicamente a la capa de aplicación.
- **V.7** Toda tabla de negocio DEBE incluir metadatos de auditoría: fecha de creación, fecha de última modificación y actor responsable.
- **V.8** La auditoría de negocio DEBE resolverse mediante un registro unificado de eventos (entidad, identificador, acción, actor, marca de tiempo y cambio aplicado), NO mediante una tabla por tipo de operación. Este registro se complementa con el registro de peticiones definido en el Art. XV, del cual se mantiene separado y con el cual DEBE poder correlacionarse.
- **V.9** Las convenciones de nombres del esquema DEBEN ser uniformes: `snake_case`, tablas en plural, claves foráneas como `<entidad_singular>_id`.
- **V.10** El borrado de información de negocio DEBERÍA ser lógico y reversible; el borrado físico DEBE justificarse en la especificación.
- **V.11** Toda clave primaria DEBE ser de tipo `uuid` nativo de PostgreSQL, generada como **UUID v7** (ordenado por tiempo) para preservar la localidad de los índices. NO DEBEN exponerse identificadores secuenciales en la API.
- **V.12** Las migraciones DEBEN gestionarse con **Flyway**, escritas en SQL plano de PostgreSQL, versionadas de forma incremental y almacenadas en el repositorio del backend.

**Verificación:** una base de datos vacía puede reconstruirse íntegramente ejecutando las migraciones del repositorio, en orden y sin intervención manual.

---



## Artículo VI — Calidad y estructura del código

> *Principio: el código se escribe una vez y se lee cientos de veces.*

**Reglas**

- **VI.1** El código DEBE organizarse en módulos con responsabilidades claramente separadas.
- **VI.2** La lógica de negocio NO DEBE residir en controladores, vistas ni en el esquema de base de datos.
- **VI.3** Las reglas de negocio (`RN-…`) DEBEN implementarse de forma identificable y aislada de los detalles de infraestructura.
- **VI.4** El manejo de errores DEBE ser explícito y consistente en todo el sistema. NO DEBEN silenciarse excepciones.
- **VI.5** Los mensajes de error dirigidos al cliente NO DEBEN exponer detalles internos (trazas, consultas, rutas, versiones).
- **VI.6** El código DEBE pasar las verificaciones automáticas de linting y formato antes de integrarse.
- **VI.7** Los nombres DEBEN ser descriptivos y consistentes con el lenguaje del dominio definido en las especificaciones.
- **VI.8** NO DEBE integrarse código comentado, código muerto ni dependencias sin uso.
- **VI.9** La duplicación de lógica DEBERÍA eliminarse mediante abstracciones justificadas, evitando abstracciones prematuras.

**Verificación:** pipeline de CI con linting, formato y compilación en verde.

---



## Artículo VII — Las pruebas son parte del entregable

> *Principio: una funcionalidad sin pruebas está incompleta, no "pendiente de probar".*

**Reglas**

- **VII.1** Todo cambio funcional DEBE incorporar pruebas automatizadas en el mismo Pull Request.
- **VII.2** El proyecto DEBE cubrir los niveles definidos por el Documento Marco: unitarias, de integración, de API, funcionales y de aceptación.
- **VII.3** Las pruebas DEBEN cubrir el flujo principal, los flujos alternativos, las excepciones y los casos límite declarados en la especificación.
- **VII.4** Las pruebas DEBEN ser deterministas y ejecutables sin dependencias de servicios externos no controlados.
- **VII.5** NO DEBE integrarse código con pruebas en fallo o deshabilitadas sin justificación registrada.
- **VII.6** Ante la corrección de un defecto, DEBE agregarse primero una prueba que lo reproduzca.
- **VII.7** La cobertura es un indicador, no un objetivo: NO DEBEN escribirse pruebas cuyo único fin sea elevar la métrica.

**Verificación:** la suite completa se ejecuta en CI en cada Pull Request y es condición de integración.

---



## Artículo VIII — Contratos de API estables

> *Principio: una API publicada es una promesa.*

**Reglas**

- **VIII.1** La API DEBE seguir el estilo REST sobre HTTP y versionarse en la ruta (`/api/v1/...`).
- **VIII.2** Todo endpoint DEBE estar documentado mediante Swagger/OpenAPI, incluyendo autenticación, parámetros, respuestas y errores.
- **VIII.3** Los códigos de estado HTTP DEBEN usarse conforme a su semántica estándar.
- **VIII.4** El formato de error DEBE ser uniforme en toda la API.
- **VIII.5** Un cambio incompatible en un contrato publicado DEBE introducirse como nueva versión, no modificando la existente.
- **VIII.6** El contrato documentado DEBE corresponder al comportamiento real; una divergencia se trata como defecto.
- **VIII.7** Backend y frontend residen en repositorios independientes. La especificación OpenAPI publicada es el **único** contrato entre ambos: NO DEBEN acordarse comportamientos por fuera de ella.

**Verificación:** pruebas de API que validan el contrato publicado.

---



## Artículo IX — Configuración y entornos

> *Principio: el mismo artefacto debe poder ejecutarse en cualquier entorno cambiando solo su configuración.*

**Reglas**

- **IX.1** Toda configuración dependiente del entorno DEBE proveerse mediante variables de entorno.
- **IX.2** NO DEBEN existir valores de entorno embebidos en el código (URLs, credenciales, rutas, banderas).
- **IX.3** El repositorio DEBE mantener un archivo de ejemplo con todas las variables requeridas y **sin valores reales**.
- **IX.4** Los entornos mínimos son `development`, `testing` y `production`.
- **IX.5** La aplicación DEBE fallar de forma explícita al iniciar si falta una variable obligatoria, en lugar de asumir un valor por defecto inseguro.

**Verificación:** el proyecto arranca en un entorno limpio siguiendo únicamente el archivo de ejemplo y la documentación.

---



## Artículo X — Reproducibilidad

> *Principio: "en mi máquina funciona" no es un estado válido del proyecto.*

**Reglas**

- **X.1** El sistema DEBE poder ejecutarse mediante contenedores Docker.
- **X.2** El entorno local completo DEBE poder levantarse con Docker Compose.
- **X.3** Las versiones de dependencias y de las herramientas base DEBEN estar fijadas de forma explícita y versionadas.
- **X.4** El proceso de construcción, ejecución y prueba DEBE estar automatizado y documentado.
- **X.5** La plataforma de ejecución del backend es **Java 21 LTS** con **Spring Boot 3.x**, construido mediante **Maven**. Las versiones exactas DEBEN declararse en `architecture.md` y fijarse en el descriptor de construcción.

**Verificación:** un entorno nuevo llega a un sistema funcional siguiendo el README, sin pasos no documentados.

---



## Artículo XI — Control de versiones

> *Principio: la historia del repositorio es documentación.*

**Reglas**

- **XI.1** El flujo de ramas es: `main`, `develop`, `feature/*`, `fix/*`, `refactor/*`, `hotfix/*`.
- **XI.2** `main` DEBE mantenerse siempre en estado desplegable.
- **XI.3** Las ramas de trabajo DEBEN nombrarse de forma descriptiva y en minúsculas (`feature/registrar-rol`, `fix/error-inventario`).
- **XI.4** Los commits DEBEN ser atómicos y con mensajes que expliquen el *por qué* del cambio.
- **XI.5** NO DEBEN versionarse artefactos generados, dependencias instaladas ni archivos locales del entorno de desarrollo.
- **XI.6** Las versiones DEBEN seguir el esquema `MAJOR.MINOR.PATCH`.

**Verificación:** `main` protegida, integración exclusivamente vía Pull Request aprobado y CI en verde.

---



## Artículo XII — La documentación evoluciona con el código

> *Principio: documentación desactualizada es peor que ausencia de documentación.*

**Reglas**

- **XII.1** La documentación DEBE residir dentro del repositorio y versionarse con Git.
- **XII.2** La documentación DEBERÍA escribirse en formato Markdown, para permitir diff y revisión en Pull Request.
- **XII.3** Un cambio que altere comportamiento, contrato o esquema DEBE actualizar la documentación afectada **en el mismo Pull Request**.
- **XII.4** Las decisiones arquitectónicas relevantes DEBEN registrarse en `docs/architecture/` indicando contexto, decisión, alternativas consideradas y consecuencias.
- **XII.5** La estructura documental del repositorio es la definida en §18 de este documento.
- **XII.6** Cada repositorio DEBE mantener su propia documentación. Los documentos transversales (constitución, arquitectura, seguridad) tienen su copia autoritativa en el backend; el frontend DEBE referenciarla en lugar de duplicarla.

**Verificación:** la revisión de Pull Request rechaza cambios que dejen la documentación inconsistente.

---



## Artículo XIII — La IA asiste, no decide

> *Principio: la responsabilidad técnica no es delegable a una herramienta.*

**Reglas**

- **XIII.1** La inteligencia artificial PUEDE utilizarse para generación de código, análisis, refactorización, pruebas, documentación y propuestas de implementación.
- **XIII.2** Todo código generado o modificado con asistencia de IA DEBE ser revisado, comprendido, probado y validado por una persona antes de integrarse.
- **XIII.3** NO DEBE integrarse código que su autor no sea capaz de explicar.
- **XIII.4** La IA NO DEBE utilizarse como sustituto de la revisión técnica, de las pruebas ni de la validación de requerimientos.
- **XIII.5** NO DEBEN enviarse credenciales, secretos ni datos sensibles reales a herramientas de IA.

**Verificación:** la autoría y la responsabilidad del cambio recaen siempre en la persona que abre el Pull Request.

---



## Artículo XIV — Desarrollo incremental

> *Principio: se entrega valor en incrementos pequeños, verificables y reversibles.*

**Reglas**

- **XIV.1** El software DEBE construirse de forma incremental; cada incremento DEBE dejar el sistema en estado funcional.
- **XIV.2** Los Pull Requests DEBERÍAN mantenerse pequeños y enfocados en un requerimiento.
- **XIV.3** NO DEBEN mezclarse en un mismo Pull Request cambios funcionales con refactorizaciones amplias o cambios de formato masivos.
- **XIV.4** NO DEBE implementarse funcionalidad no requerida en anticipación a necesidades futuras.

**Verificación:** cada Pull Request es comprensible en una sola revisión y puede revertirse de forma aislada.

---



## Artículo XV — Observabilidad y registro de operación

> *Principio: lo que no se observa no se puede operar, ni auditar, ni explicar.*

**Reglas**

- **XV.1** Toda petición HTTP DEBE recibir un **identificador de correlación** único, generado por el sistema si el cliente no lo provee, propagado a todo log y evento derivado, y devuelto al cliente en la respuesta.
- **XV.2** El sistema DEBE mantener un **registro de peticiones** (`request_log`) que contenga como mínimo: identificador de correlación, actor autenticado (o su condición de anónimo), método HTTP, ruta, parámetros, código de estado, duración en milisegundos, origen (IP y agente de usuario), marca de tiempo y resultado de la respuesta.
- **XV.3** El `request_log` y el `audit_log` son registros distintos y DEBEN mantenerse separados: el primero responde **qué se le pidió al sistema y qué respondió**; el segundo, **qué cambió en el negocio y quién lo cambió**. Ambos DEBEN poder correlacionarse mediante el identificador de correlación.
- **XV.4** Las peticiones fallidas (`4xx` y `5xx`) DEBEN registrarse con el detalle del error, incluyendo el motivo del rechazo cuando se trate de una denegación de autenticación o autorización.
- **XV.5** El contenido de peticiones y respuestas DEBE enmascararse antes de persistirse: contraseñas, tokens, cabeceras de autorización y datos personales sensibles NO DEBEN quedar almacenados en ningún registro (Art. IV.8). Los cuerpos que superen el tamaño máximo definido DEBEN truncarse de forma explícita.
- **XV.6** Los logs de aplicación DEBEN emitirse en formato estructurado, con nivel explícito y el identificador de correlación incorporado.
- **XV.7** El registro de peticiones NO DEBE alterar el resultado ni el contrato de la operación: un fallo al registrar NO DEBE provocar el fallo de la petición de negocio, salvo en operaciones donde la auditoría constituya un requisito legal declarado en la especificación.
- **XV.8** `request_log` y `audit_log` DEBEN tener una política de retención definida y automatizada. El `audit_log` NO DEBE purgarse sin una decisión documentada en `docs/security/`.
- **XV.9** Umbrales de rendimiento del sistema (RNF): operaciones de lectura **p95 < 500 ms**; operaciones de escritura **p95 < 1 s**. El límite de 5 s del Documento Marco se interpreta como **techo absoluto**, nunca como objetivo. Toda operación que no pueda cumplir estos umbrales DEBE justificarlo y declarar su propio umbral en la especificación correspondiente.
- **XV.10** El sistema DEBE exponer un endpoint de estado de salud, sin información sensible y sin requerir autenticación de negocio.

**Verificación:** dado un identificador de correlación es posible reconstruir la petición completa, quién la ejecutó, qué respondió el sistema y qué cambió en el negocio.

---



## 16. Definición de terminado

Un requerimiento se considera **terminado** únicamente cuando se cumplen **todas** las condiciones:

- [ ] El requerimiento está aprobado.
- [ ] La especificación está documentada.
- [ ] Los criterios de aceptación están definidos.
- [ ] El código está implementado.
- [ ] Las pruebas correspondientes fueron escritas y ejecutadas satisfactoriamente.
- [ ] El código fue revisado y aprobado.
- [ ] No existen errores críticos conocidos.
- [ ] La documentación fue actualizada.
- [ ] La matriz de trazabilidad fue actualizada.
- [ ] El Pull Request fue aprobado e integrado en la rama correspondiente.

---



## 17. Proceso de enmienda

- **17.1** Esta constitución PUEDE modificarse, pero nunca de forma implícita ni por la vía de los hechos.
- **17.2** Toda enmienda DEBE tramitarse mediante Pull Request dedicado, con justificación, impacto sobre el código existente y responsable.
- **17.3** Toda enmienda DEBE ser aprobada por el responsable técnico del proyecto.
- **17.4** Agregar o eliminar un artículo, o invertir el sentido de una regla, incrementa la versión **MAJOR**. Agregar reglas dentro de un artículo existente incrementa la **MINOR**. Las aclaraciones de redacción sin cambio de fondo incrementan la **PATCH**.
- **17.5** Toda enmienda DEBE registrarse en §19.
- **17.6** Mientras este documento permanezca en estado **Borrador** (versiones `0.x`), las enmiendas se acumulan como **MINOR**. La versión `1.0.0` se emite al ser aprobada por el stakeholder; desde ese punto aplica la regla 17.4 sin excepciones.

---



## 18. Estructura documental del repositorio

```
docs/
├── constitution.md          Este documento — principios no negociables
├── requirements.md          Índice de requerimientos y matriz de trazabilidad
├── architecture.md          Visión general de la arquitectura
├── security.md              Modelo de seguridad, roles y permisos
├── testing-strategy.md      Estrategia y niveles de prueba
├── development-guide.md     Guía práctica de desarrollo y estándares de código
├── requirements/            Requerimientos por módulo
├── specs/                   Especificaciones por funcionalidad
├── architecture/            Decisiones de arquitectura registradas
├── api/                     Contratos y documentación de API
├── testing/                 Planes y casos de prueba
└── security/                Análisis, controles y excepciones de seguridad
```

---



## 19. Control de cambios


| Versión | Fecha      | Cambio                                               | Responsable         |
| ------- | ---------- | ---------------------------------------------------- | ------------------- |
| 0.1.0   | 19-08-2026 | Creación inicial. Derivada del Documento Marco v1.0. | Responsable técnico |
| 0.2.0   | 19-08-2026 | Cierre de las decisiones D-01 a D-07. Nuevo Art. XV (observabilidad y registro de operación). Nuevas reglas V.11, V.12, VIII.7, X.5, XII.6 y 17.6. | Responsable técnico |


---



## 20. Decisiones cerradas

Registro de las decisiones técnicas resueltas. Cada una está incorporada como regla en el artículo indicado; esta tabla conserva la resolución y su justificación para efectos de trazabilidad (Art. III).

| # | Decisión | Resolución | Justificación | Regla |
|---|---|---|---|---|
| D-01 | Framework y versión de Java | Spring Boot 3.x sobre Java 21 LTS | Ecosistema maduro, tooling estable y mayor disponibilidad de talento; LTS con soporte prolongado. | X.5 |
| D-02 | Herramienta de construcción | Maven | POM declarativo y de convención estricta: legible y auditable en revisión de PR. | X.5 |
| D-03 | Migraciones | Flyway con SQL plano de PostgreSQL | Migraciones versionadas e inmutables, sin capa de abstracción sobre un motor único. | V.12 |
| D-04 | Clave primaria | `uuid` nativo generado como UUID v7 | Identificadores opacos como exige la plantilla, pero ordenados por tiempo para no fragmentar los índices. | V.11 |
| D-05 | Auditoría | `audit_log` de eventos de negocio más `request_log` de peticiones, correlacionados | Separa el cambio de negocio de la actividad sobre la API: permite responder quién pidió qué, qué respondió el sistema y qué cambió. | V.8, Art. XV |
| D-06 | Rendimiento | p95 < 500 ms en lectura y p95 < 1 s en escritura; 5 s como techo absoluto | Convierte el RNF en una métrica verificable (Art. II.3) sin contradecir el Documento Marco. | XV.9 |
| D-07 | Repositorios | Separados, sincronizados por contrato OpenAPI | Conserva los repositorios existentes, con ciclos de vida, CI y permisos independientes. | VIII.7, XII.6 |

No quedan decisiones pendientes que bloqueen la redacción de `architecture.md`.
