-- Genera db/dev-seed/semilla-avisos-de-brokers.sql con los avisos de los
-- brokers guardados en la base local (RF-SP-078). Se corre a mano, contra la
-- base del compose, y su salida ES el guion de la semilla:
--
--   docker cp scripts/exportar-avisos-de-brokers.sql nexus-db:/tmp/exportar.sql; docker exec nexus-db sh -c "psql -U nexus -d nexus -At -q -f /tmp/exportar.sql > /tmp/semilla.sql"; docker cp nexus-db:/tmp/semilla.sql src/main/resources/db/dev-seed/semilla-avisos-de-brokers.sql
--
-- Vale igual en PowerShell y en bash, y no pasa por la consola: ni la
-- codificación ni la marca BOM de PowerShell tocan el guion.
--
-- Volver a correrlo reemplaza la semilla con lo que haya en la base ese día.
SELECT concat_ws(E'\n',
'-- =============================================================================',
'-- Semilla de DESARROLLO: los avisos de los brokers (RF-SP-078, RN-SP-066).',
'--',
'-- Copia de los avisos reales guardados en la base local, generada con',
'-- scripts/exportar-avisos-de-brokers.sql el ' || to_char(now(), 'DD-MM-YYYY') || ': ' || count(*) || ' avisos.',
'-- Sirven para mirar y reprocesar lo que manda cada broker sin esperar a que',
'-- vuelva a avisar.',
'--',
'-- LA APLICA `DevelopmentDataSeeder` AL ARRANCAR, la última, y bajo las mismas',
'-- condiciones que las demás: nunca en `production`. Es repetible: un aviso que',
'-- ya está no se vuelve a insertar, y el de un broker que no existe se salta.',
'--',
'-- SE GUARDAN, NO SE INTERPRETAN: insertarlos aquí no crea cuentas, no mueve',
'-- estados ni cuenta operaciones. Eso solo lo hace un aviso que llega por la',
'-- dirección (RN-SP-072 a RN-SP-074). `event_id` se copia como lo hace V98.',
'-- =============================================================================',
'',
'INSERT INTO broker_notifications (id, broker_id, method, query_params, headers, body,',
'                                  content_type, ip_address, received_at, event_id)',
'SELECT v.id, v.broker_id, v.method, v.query_params, v.headers, v.body, v.content_type,',
'       v.ip_address, v.received_at,',
'       CASE WHEN jsonb_typeof(v.query_params -> ''event_id'') = ''array''',
'             AND jsonb_array_length(v.query_params -> ''event_id'') = 1',
'             AND length(v.query_params -> ''event_id'' ->> 0) BETWEEN 1 AND 100',
'            THEN v.query_params -> ''event_id'' ->> 0 END',
'  FROM (VALUES',
string_agg(
  format('    (%L::uuid, %L::uuid, %L::varchar, %L::jsonb, %L::jsonb, %L::text, %L::varchar, %L::varchar, %L::timestamptz)',
         id, broker_id, method, query_params::text, headers::text, body, content_type, ip_address, received_at),
  E',\n' ORDER BY received_at, id),
'       ) AS v (id, broker_id, method, query_params, headers, body, content_type, ip_address,',
'               received_at)',
'  JOIN brokers b ON b.id = v.broker_id',
'ON CONFLICT (id) DO NOTHING;',
'')
FROM broker_notifications;
