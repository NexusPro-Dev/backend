-- =============================================================================
-- Semilla de DESARROLLO: la academia de prueba — cinco categorías y seis cursos
-- con sus módulos y lecciones.
--
-- LA APLICA `DevelopmentDataSeeder` AL ARRANCAR, DESPUÉS de las personas y del
-- catálogo de productos, y bajo las mismas condiciones: solo cuando
-- `ENVIRONMENT` NO es `production` y `DEV_SEED_ENABLED` no la apaga. Todo lo que
-- la cabecera de `semilla-desarrollo.sql` dice de aquella vale aquí: no es una
-- migración y no debe serlo nunca, no deja rastro en la auditoría, no pasa por
-- las reglas de negocio, es repetible y no lleva `BEGIN`/`COMMIT`.
--
-- LO QUE HAY QUE PODER PROBAR, y de ahí salen los seis:
--
--   · EL CURSO SIN LLAVES (`RN-AC-020`): «Fundamentos del trading» no declara
--     membresías ni servicios, de modo que lo abre cualquiera con
--     `courses:learn`, también quien está en BECA.
--   · LAS LLAVES POR MEMBRESÍA (`RN-AC-012`): «Análisis técnico» lo abren VIP,
--     PLATINO y ORO, y «Gestión del riesgo» solo PLATINO y ORO; con `cliente1`
--     (BECA), `cliente2` (VIP) y `cliente3` (PLATINO) cada uno ve un aula
--     distinta.
--   · LA LLAVE POR SERVICIO (`RN-AC-020`): «Opera con los bots de NEXUS» lo
--     abren dos bots de `semilla-productos.sql` O la membresía ORO.
--   · LA DEMOSTRACIÓN (`RN-AC-014`): una lección abierta dentro de un curso
--     cerrado, que BECA puede ver aunque su membresía no lo abra.
--   · LO QUE NO SE OFRECE (`RN-AC-015`): un módulo INACTIVO dentro de un curso
--     activo, una lección INACTIVA y una ACTIVO sin contenido que no se ofrece.
--   · EL CATÁLOGO ADMINISTRATIVO: un curso INACTIVO a medio armar y uno
--     RETIRADO —con sus módulos y lecciones retirados con él (`RN-AC-018`)—
--     para el filtro de retirados. Sin motivo: la semilla no pasa por la
--     auditoría, y `deletionReason` llega nulo.
--
-- LOS ACTIVOS NACEN ACTIVOS AQUÍ, y eso contradice a `RN-AC-009` a propósito,
-- como hace la semilla de productos con `RN-PM-012`: se escribe el estado FINAL,
-- respetando lo que la regla exige para activar —las dos descripciones del
-- curso, un módulo activo y una lección activa con contenido—.
--
-- LOS INSTRUCTORES SON `admin1` Y `superadmin`, que portan `courses:teach` por
-- `V22` (ADMIN y SUPERADMIN). Si alguno falta, su curso no se siembra.
--
-- SIN PORTADAS: `academy_images` guarda los bytes y una imagen de prueba no
-- aporta nada que no se pruebe subiéndola por la API (`RF-AC-006`).
--
-- ES REPETIBLE, como la de productos: el curso se reconoce por su título (único
-- entre los vivos, `uq_courses_title`) y SI YA EXISTE —vivo o retirado— SE DEJA
-- COMO ESTÁ CON TODO LO QUE CUELGA DE ÉL, también si alguien lo corrigió por la
-- API. Los módulos, las lecciones y las tres relaciones solo se escriben para
-- los cursos que nacen en esta misma pasada.
--
-- LO QUE ESTA SEMILLA NO HACE: dar `courses:learn` a CLIENTE. Esa concesión es
-- de quien administra roles (`RF-SP-006`); hasta que la haga, los tres clientes
-- no ven el aula y quien la recorre en local es `admin1`.
--
-- Para lanzarla A MANO contra el entorno local:
--
--   docker exec -i nexus-db psql -U nexus -d nexus -v ON_ERROR_STOP=1 -1 \
--     < src/main/resources/db/dev-seed/semilla-academia.sql
-- =============================================================================

CREATE OR REPLACE FUNCTION pg_temp.uuid_v7() RETURNS uuid AS $$
  SELECT (
      substr(ts, 1, 8) || '-' || substr(ts, 9, 4) || '-7' || substr(r, 1, 3)
      || '-a' || substr(r, 4, 3) || '-' || substr(r, 7, 12)
  )::uuid
  FROM (
    SELECT lpad(to_hex((extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0') AS ts,
           md5(random()::text || clock_timestamp()::text) AS r
  ) AS partes;
$$ LANGUAGE sql VOLATILE;


-- -----------------------------------------------------------------------------
-- Las cinco categorías. Se reconocen por nombre entre las vivas
-- (`uq_course_categories_name`); el color va en mayúsculas y sin `#`, y el
-- icono es un nombre (`RN-AC-003`).
-- -----------------------------------------------------------------------------

INSERT INTO course_categories (id, name, description, color, icon, display_order)
SELECT pg_temp.uuid_v7(), c.nombre, c.descripcion, c.color, c.icono, c.orden
  FROM (VALUES
          ('Primeros pasos',      'Lo imprescindible para empezar a operar.',            '2E7D32', 'footprints', 0),
          ('Análisis de mercado', 'Leer el gráfico y el contexto antes de entrar.',       '1565C0', 'chart-line', 1),
          ('Gestión del riesgo',  'Cuánto arriesgar, dónde salir y cómo sobrevivir.',     'C62828', 'shield',     2),
          ('Herramientas',        'Los bots y plataformas que NEXUS pone a tu alcance.',  '6A1B9A', 'bot',        3),
          ('Psicotrading',        'La disciplina y las emociones detrás de cada orden.',  'EF6C00', 'brain',      4)
       ) AS c (nombre, descripcion, color, icono, orden)
 WHERE NOT EXISTS (SELECT 1 FROM course_categories x
                    WHERE x.deleted_at IS NULL
                      AND f_unaccent(lower(x.name)) = f_unaccent(lower(c.nombre)));


-- -----------------------------------------------------------------------------
-- Los seis cursos con su árbol, en UNA sola sentencia: así los módulos y las
-- lecciones se cuelgan de los cursos que acaban de nacer —por el RETURNING— y
-- no de uno que ya existía con el mismo título.
--
-- `llaves` son códigos de membresía y `servicios` códigos de producto BOT; un
-- código que no exista se salta, igual que la semilla de productos salta el
-- upgrade cuya membresía falta.
-- -----------------------------------------------------------------------------

WITH cursos (titulo, instructor, dificultad, corta, larga, video, orden, estado, retirado,
             categorias, llaves, servicios) AS (
  VALUES
      ('Fundamentos del trading', 'admin1', 'PRINCIPIANTE',
       'Qué es un mercado, cómo se lee una vela y cómo se coloca la primera orden.',
       'El punto de partida de la academia. Recorre los mercados que opera la comunidad, el vocabulario básico y la mecánica de una orden, sin dar nada por sabido. Lo abre cualquier alumno, sea cual sea su membresía.',
       'https://www.youtube.com/watch?v=n-dc74-SopA', 0, 'ACTIVO', false,
       ARRAY['Primeros pasos'], ARRAY[]::text[], ARRAY[]::text[]),

      ('Análisis técnico', 'admin1', 'INTERMEDIO',
       'Soportes, resistencias, tendencias e indicadores para decidir con el gráfico.',
       'Cómo leer la acción del precio y apoyarse en indicadores sin depender de ellos. Lo abren VIP, Platino y Oro; la primera lección es una demostración abierta a todos.',
       'https://www.youtube.com/watch?v=n-dc74-SopA', 1, 'ACTIVO', false,
       ARRAY['Análisis de mercado'], ARRAY['VIP', 'PLATINO', 'ORO'], ARRAY[]::text[]),

      ('Gestión del riesgo', 'superadmin', 'AVANZADO',
       'Tamaño de posición, stop loss y la matemática que separa al que dura del que no.',
       'El curso que convierte una buena entrada en una cuenta que sobrevive. Riesgo por operación, relación beneficio-riesgo y gestión de rachas. Solo para Platino y Oro.',
       NULL, 2, 'ACTIVO', false,
       ARRAY['Gestión del riesgo', 'Análisis de mercado'], ARRAY['PLATINO', 'ORO'], ARRAY[]::text[]),

      ('Opera con los bots de NEXUS', 'admin1', 'PRINCIPIANTE',
       'Configura y supervisa el bot de señales y el de copy trading.',
       'Una guía práctica para quien ya tiene un bot contratado: conectar el broker, leer las señales y saber cuándo pausar la copia. Lo abren los dos bots o la membresía Oro.',
       'https://vimeo.com/76979871', 3, 'ACTIVO', false,
       ARRAY['Herramientas'], ARRAY['ORO'], ARRAY['BOT_SENALES', 'BOT_COPY_TRADING']),

      ('Psicología del trading', 'superadmin', 'INTERMEDIO',
       'Disciplina, miedo y codicia: el lado del trading que no sale en el gráfico.',
       NULL,
       NULL, 4, 'INACTIVO', false,
       ARRAY['Psicotrading'], ARRAY['VIP', 'PLATINO', 'ORO'], ARRAY[]::text[]),

      ('Opciones binarias', 'admin1', 'PRINCIPIANTE',
       'El primer curso de la academia, retirado del catálogo.',
       'Se retiró cuando la comunidad dejó de operar opciones binarias. Se conserva para el filtro de retirados.',
       NULL, 5, 'INACTIVO', true,
       ARRAY['Primeros pasos'], ARRAY[]::text[], ARRAY[]::text[])
),
modulos (curso, titulo, corta, larga, video, orden, estado) AS (
  VALUES
      ('Fundamentos del trading', 'Los mercados', 'Qué se opera y dónde.',
       'Divisas, índices, materias primas y criptomonedas: qué mueve a cada uno.', NULL, 0, 'ACTIVO'),
      ('Fundamentos del trading', 'Tu primera orden', 'De la idea a la orden ejecutada.',
       'Tipos de orden, apalancamiento y margen, explicados con un ejemplo de principio a fin.',
       'https://youtu.be/n-dc74-SopA', 1, 'ACTIVO'),

      ('Análisis técnico', 'Acción del precio', 'Lo que el gráfico dice sin indicadores.',
       'Velas, soportes, resistencias y estructura de mercado.', NULL, 0, 'ACTIVO'),
      ('Análisis técnico', 'Indicadores', 'Medias móviles, RSI y MACD.',
       'Para qué sirve cada indicador y cuándo engaña.', NULL, 1, 'ACTIVO'),
      ('Análisis técnico', 'Patrones avanzados', 'Hombro-cabeza-hombro, banderas y cuñas.',
       'En preparación: el módulo existe pero todavía no se ofrece.', NULL, 2, 'INACTIVO'),

      ('Gestión del riesgo', 'Tamaño de posición', 'Cuánto arriesgar en cada operación.',
       'El uno por ciento, el lotaje y por qué el stop se pone antes que el objetivo.', NULL, 0, 'ACTIVO'),
      ('Gestión del riesgo', 'Rachas y drawdown', 'Sobrevivir a las pérdidas seguidas.',
       'La matemática de la recuperación y cuándo dejar de operar.', NULL, 1, 'ACTIVO'),

      ('Opera con los bots de NEXUS', 'Puesta en marcha', 'Del cupón al bot funcionando.',
       'Activar el cupón, conectar el broker y comprobar la primera señal.', NULL, 0, 'ACTIVO'),

      ('Psicología del trading', 'Disciplina', 'El plan y cómo no saltárselo.',
       NULL, NULL, 0, 'INACTIVO'),

      ('Opciones binarias', 'Qué es una opción binaria', 'El producto y su riesgo.',
       NULL, NULL, 0, 'ACTIVO')
),
lecciones (curso, modulo, tipo, titulo, descripcion, contenido, segundos, orden, abierta, estado) AS (
  VALUES
      ('Fundamentos del trading', 'Los mercados', 'VIDEO', 'Qué es el trading',
       'Una introducción de diez minutos.', 'https://www.youtube.com/watch?v=n-dc74-SopA', 600, 0, false, 'ACTIVO'),
      ('Fundamentos del trading', 'Los mercados', 'TEXTO', 'Glosario básico',
       'Las palabras que vas a oír en la comunidad.',
       E'# Glosario básico\n\n- **Pip**: la unidad mínima de variación de un par de divisas.\n- **Lote**: el tamaño de la posición.\n- **Spread**: la diferencia entre el precio de compra y el de venta.\n',
       300, 1, false, 'ACTIVO'),
      ('Fundamentos del trading', 'Tu primera orden', 'VIDEO', 'Órdenes de mercado y pendientes',
       'Cuándo entrar al precio y cuándo esperar.', 'https://youtu.be/n-dc74-SopA', 900, 0, false, 'ACTIVO'),
      ('Fundamentos del trading', 'Tu primera orden', 'TEXTO', 'Apalancamiento y margen',
       'Por qué el apalancamiento multiplica también las pérdidas.',
       E'## Apalancamiento\n\nCon un apalancamiento de **1:100**, cada dólar de margen controla cien de posición.\n\n> El apalancamiento no cambia el riesgo de la idea: cambia cuánto te cuesta equivocarte.\n',
       420, 1, false, 'ACTIVO'),

      ('Análisis técnico', 'Acción del precio', 'VIDEO', 'Soportes y resistencias',
       'La demostración del curso: abierta a cualquier alumno.', 'https://www.youtube.com/watch?v=n-dc74-SopA', 1200, 0, true, 'ACTIVO'),
      ('Análisis técnico', 'Acción del precio', 'VIDEO', 'Estructura de mercado',
       'Máximos y mínimos crecientes y decrecientes.', 'https://vimeo.com/76979871', 960, 1, false, 'ACTIVO'),
      ('Análisis técnico', 'Acción del precio', 'TEXTO', 'Patrones de velas',
       'Todavía sin publicar.', E'# Patrones de velas\n\nBorrador.\n', 300, 2, false, 'INACTIVO'),
      ('Análisis técnico', 'Indicadores', 'VIDEO', 'Medias móviles',
       'Simple, exponencial y cruces.', 'https://www.youtube.com/watch?v=n-dc74-SopA', 840, 0, false, 'ACTIVO'),
      ('Análisis técnico', 'Indicadores', 'VIDEO', 'RSI y divergencias',
       'Activa pero sin video todavía: no se ofrece.', NULL, 600, 1, false, 'ACTIVO'),
      ('Análisis técnico', 'Patrones avanzados', 'VIDEO', 'Hombro-cabeza-hombro',
       'El patrón de giro más conocido.', 'https://www.youtube.com/watch?v=n-dc74-SopA', 720, 0, false, 'ACTIVO'),

      ('Gestión del riesgo', 'Tamaño de posición', 'TEXTO', 'La regla del uno por ciento',
       'Cuánto arriesgar por operación.',
       E'# La regla del uno por ciento\n\nNunca arriesgues más del **1 %** de la cuenta en una sola operación.\n\n| Cuenta | Riesgo máximo |\n|-------:|--------------:|\n| 1.000  | 10            |\n| 10.000 | 100           |\n',
       480, 0, false, 'ACTIVO'),
      ('Gestión del riesgo', 'Tamaño de posición', 'VIDEO', 'Calcular el lotaje',
       'Del riesgo en dinero al tamaño de la orden.', 'https://www.youtube.com/watch?v=n-dc74-SopA', 1080, 1, false, 'ACTIVO'),
      ('Gestión del riesgo', 'Rachas y drawdown', 'VIDEO', 'La matemática de la recuperación',
       'Por qué perder un 50 % exige ganar un 100 %.', 'https://vimeo.com/76979871', 900, 0, false, 'ACTIVO'),

      ('Opera con los bots de NEXUS', 'Puesta en marcha', 'VIDEO', 'Activar el cupón del bot',
       'Paso a paso desde «Mis productos».', 'https://www.youtube.com/watch?v=n-dc74-SopA', 360, 0, false, 'ACTIVO'),
      ('Opera con los bots de NEXUS', 'Puesta en marcha', 'TEXTO', 'Conectar el broker',
       'Lo que necesitas tener a mano.',
       E'## Antes de empezar\n\n1. Tu cuenta de broker registrada en NEXUS.\n2. El cupón del bot activado.\n3. Saldo suficiente para el lotaje mínimo.\n',
       240, 1, false, 'ACTIVO'),

      ('Psicología del trading', 'Disciplina', 'TEXTO', 'El plan de trading',
       'Sin contenido todavía.', NULL, 300, 0, false, 'INACTIVO'),

      ('Opciones binarias', 'Qué es una opción binaria', 'VIDEO', 'Arriba o abajo',
       'La lección que tenía el curso retirado.', 'https://www.youtube.com/watch?v=n-dc74-SopA', 600, 0, false, 'ACTIVO')
),
nuevos_cursos AS (
  INSERT INTO courses (id, title, instructor_id, difficulty, short_description, long_description,
                       intro_video_url, display_order, status, deleted_at)
  SELECT pg_temp.uuid_v7(), c.titulo, u.id, c.dificultad, c.corta, c.larga,
         c.video, c.orden, c.estado,
         CASE WHEN c.retirado THEN now() ELSE NULL END
    FROM cursos c
    JOIN users u ON u.username = c.instructor AND u.deleted_at IS NULL
   WHERE NOT EXISTS (SELECT 1 FROM courses x
                      WHERE f_unaccent(lower(x.title)) = f_unaccent(lower(c.titulo)))
  RETURNING id, title, deleted_at
),
nuevos_modulos AS (
  INSERT INTO course_modules (id, course_id, title, short_description, long_description,
                              presentation_video_url, display_order, status, deleted_at)
  SELECT pg_temp.uuid_v7(), n.id, m.titulo, m.corta, m.larga, m.video, m.orden, m.estado,
         n.deleted_at
    FROM modulos m
    JOIN nuevos_cursos n ON n.title = m.curso
  RETURNING id, course_id, title, deleted_at
),
nuevas_lecciones AS (
  INSERT INTO lessons (id, module_id, type, title, description, content, duration_seconds,
                       display_order, open, status, deleted_at)
  SELECT pg_temp.uuid_v7(), nm.id, l.tipo, l.titulo, l.descripcion, l.contenido, l.segundos,
         l.orden, l.abierta, l.estado, nm.deleted_at
    FROM lecciones l
    JOIN nuevos_cursos n   ON n.title = l.curso
    JOIN nuevos_modulos nm ON nm.course_id = n.id AND nm.title = l.modulo
  RETURNING id
),
clasificacion AS (
  INSERT INTO course_category_items (course_id, category_id)
  SELECT n.id, k.id
    FROM nuevos_cursos n
    JOIN cursos c ON c.titulo = n.title
    CROSS JOIN LATERAL unnest(c.categorias) AS nombre
    JOIN course_categories k ON k.name = nombre AND k.deleted_at IS NULL
  RETURNING course_id
),
por_membresia AS (
  INSERT INTO course_memberships (course_id, membership_id)
  SELECT n.id, m.id
    FROM nuevos_cursos n
    JOIN cursos c ON c.titulo = n.title
    CROSS JOIN LATERAL unnest(c.llaves) AS codigo
    JOIN memberships m ON m.code = codigo
  RETURNING course_id
)
INSERT INTO course_products (course_id, product_id)
SELECT n.id, p.id
  FROM nuevos_cursos n
  JOIN cursos c ON c.titulo = n.title
  CROSS JOIN LATERAL unnest(c.servicios) AS codigo
  JOIN products p ON p.code = codigo AND p.type = 'BOT' AND p.deleted_at IS NULL;
