-- =============================================================================
-- La membresía de arranque pasa de `FREE` a `BECA`.
--
-- Decisión del responsable del proyecto (09-09-2026). Cambia el CÓDIGO y el
-- NOMBRE de la fila que `V46` sembró como suelo de la cadena; no cambia su
-- nivel, ni su color, ni su lugar en el orden, ni una sola de las reglas que la
-- citan.
--
-- -----------------------------------------------------------------------------
-- POR QUÉ ESTO ES UNA MIGRACIÓN Y NO PODÍA SER UNA LLAMADA
--
-- `RN-SP-008` hace las membresías INMUTABLES: no hay operación de la API que
-- edite una, y `RF-SP-016` solo sabe crearlas. Ese requerimiento dejó escrito
-- que el precio de esa decisión es que un dato mal elegido «es permanente, y la
-- única salida es crear otra membresía, que reordena la cadena entera».
--
-- Crear una `BECA` nueva y retirar `FREE` NO ES UNA SALIDA AQUÍ: `RN-SP-008`
-- tampoco admite eliminarlas y no llevan indicador de activo, de modo que
-- quedarían las dos en la cadena y habría que mover a todo el mundo. Renombrar
-- por migración es lo que evita ese destrozo.
--
-- ES LA MISMA VÍA QUE `V42` USÓ con los códigos de país —otro catálogo que
-- `RN-SP-009` declara inmutable— y por el mismo motivo: lo que la API no puede
-- hacer, una migración revisada sí.
--
-- -----------------------------------------------------------------------------
-- LO QUE NO SE ROMPE, Y POR QUÉ
--
-- Nadie referencia esta fila por su código: `user_memberships` y `products`
-- apuntan por `id`, y el identificador NO CAMBIA. La cadena se ordena por
-- `level`, que tampoco cambia.
--
-- Lo único que sí depende del literal es la CONVENCIÓN con la que el código
-- resuelve el suelo —`MembershipCatalog.floor()`, la verificación al arrancar de
-- `RF-SP-045` y el registro por enlace—, y esos tres literales se cambian en el
-- mismo Pull Request. `RF-SP-045` `plan.md` §5 previó exactamente esto: la
-- comprobación al arrancar existe para que «alguien renombró el nivel» sea UN
-- ARRANQUE QUE FALLA y no un registro roto en producción.
--
-- Dicho al revés: si esta migración se despliega sin el código, el sistema NO
-- arranca — que es el comportamiento buscado.
-- =============================================================================

UPDATE memberships
   SET code = 'BECA',
       name = 'Beca',
       updated_at = now()
 WHERE code = 'FREE';


-- -----------------------------------------------------------------------------
-- Guarda: el suelo existe, se llama BECA, y no quedó ningún FREE.
--
-- Las tres mitades importan. Sin la primera, un despliegue sobre una base que
-- nunca tuvo `FREE` pasaría en silencio y dejaría el sistema sin suelo, que
-- `RN-SP-018` hace irrealizable —toda persona nace con nivel—. Sin la tercera,
-- una base con las dos filas quedaría con una cadena de cinco eslabones y dos
-- suelos.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    con_beca integer;
    con_free integer;
BEGIN
    SELECT count(*) INTO con_beca FROM memberships WHERE code = 'BECA';
    SELECT count(*) INTO con_free FROM memberships WHERE code = 'FREE';

    IF con_beca <> 1 THEN
        RAISE EXCEPTION
            'V79: se esperaba exactamente una membresía BECA y hay %. RN-SP-018 hace que toda '
            'persona nazca con el nivel de arranque, de modo que sin él el alta es irrealizable.',
            con_beca;
    END IF;

    IF con_free <> 0 THEN
        RAISE EXCEPTION
            'V79: quedan % membresías con código FREE. La cadena es un orden lineal (RN-SP-007) y '
            'dos suelos no significan nada.', con_free;
    END IF;
END $$;

COMMENT ON TABLE memberships IS
    'Niveles de acceso, en cadena lineal. El suelo es BECA (RN-SP-018); se llamó FREE hasta V79.';
