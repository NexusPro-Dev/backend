package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductLinkType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un enlace declarado en el alta o en la corrección de un producto (`RN-PM-048`).
 *
 * <p><b>El tipo viaja como texto y no como enumerado</b>, y lo resuelve el caso de uso. Declararlo
 * {@code ProductLinkType} dejaría el rechazo en manos de Jackson, que responde {@code 400} <b>sin
 * {@code errors} y sin código</b> —es el cuerpo ilegible de {@code GlobalExceptionHandler}—, y
 * además no podría distinguir el alta de la corrección, que le dan números distintos al mismo
 * error: `VAL-019` y `VAL-014`. Es lo mismo que hace el filtro de estado de `RF-MV-014` con {@code
 * VAL-002}: el valor se comprueba <b>contra los del enumerado</b>, no contra una lista escrita a
 * mano, y el mensaje los enumera. El contrato sigue publicando los dos valores porque el esquema
 * los declara aquí.
 *
 * <p><b>La dirección y el identificador los comprueba el dominio</b>, en {@code ProductLink}, y por
 * el mismo motivo por el que el enlace del video lo hacía antes: {@code @URL} de Hibernate
 * Validator admite cualquier esquema y no distingue una relativa, {@code @Pattern} no puede decir
 * «hasta 500» sin repetir el tope, y la incompatibilidad entre el identificador y una dirección con
 * {@code ?} <b>cruza dos campos</b> — una anotación no puede expresar «prohibido si otro campo está
 * informado» sin un validador de clase, y el mensaje que produciría no diría cuál de los dos sobra.
 *
 * <p><b>Y el tipo repetido lo comprueba el caso de uso</b>, sobre el cuerpo recibido y antes de
 * escribir: podría dejarse a {@code pk_product_links}, y no se deja, porque el choque saldría como
 * violación de clave y habría que traducirlo por nombre de restricción para no devolver un {@code
 * 500}, cuando la petición <b>ya enseña las dos entradas</b>. Es la diferencia con la unicidad del
 * nombre, donde el conflicto es con <b>otra fila que no se ve</b> y solo la base puede resolver la
 * carrera.
 */
@Schema(name = "ProductLinkRequest")
public record ProductLinkRequest(
    @Schema(
            implementation = ProductLinkType.class,
            description =
                "Qué es el enlace: VIDEO_PRESENTACION, el video que presenta el producto, o"
                    + " CUPON_BOT, donde registra su cuenta quien ya lo compró. Obligatorio, y uno"
                    + " solo por tipo.")
        String type,
    String url,
    String externalId) {}
