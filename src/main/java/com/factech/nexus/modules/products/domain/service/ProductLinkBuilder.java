package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductLinkRequest;
import com.factech.nexus.modules.products.domain.models.ProductLink;
import com.factech.nexus.modules.products.domain.models.ProductLinkType;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Convierte la colección de enlaces recibida en enlaces del dominio (`RN-PM-048`).
 *
 * <p><b>Existe para no escribirlo dos veces.</b> El alta (`RF-PM-001`) y la corrección
 * (`RF-PM-004`) hacen exactamente lo mismo con los enlaces —comprobar el tipo repetido, construir y
 * validar cada uno—, y una regla de forma duplicada es una que un día se corrige en un solo lado.
 * Lo único que cambia entre las dos es <b>el número de la validación</b>, porque cada
 * especificación le dio el suyo, y por eso viaja como argumento.
 *
 * <p><b>El tipo repetido se comprueba aquí y no en la base</b>, aunque {@code pk_product_links} lo
 * impediría: el choque saldría como violación de clave y habría que traducirlo por nombre de
 * restricción para no devolver un {@code 500}, cuando la petición <b>ya enseña las dos entradas</b>
 * y compararlas es recorrer una lista de dos. Es la diferencia con la unicidad del nombre, donde el
 * conflicto es con otra fila que no se ve y solo la base puede resolver la carrera.
 */
final class ProductLinkBuilder {

  private ProductLinkBuilder() {}

  /**
   * Los códigos de validación que cada operación le da a las mismas comprobaciones.
   *
   * @param tipo el que falta o no está en el dominio: `VAL-019` en el alta, `VAL-014` en la
   *     corrección
   * @param repetido el tipo repetido: `VAL-020` en el alta, `VAL-015` en la corrección
   * @param obligatoria la dirección que falta: `VAL-021` y `VAL-016`
   * @param direccion la forma de la dirección: `VAL-017` y `VAL-009`
   * @param identificador la forma del identificador externo: `VAL-022` y `VAL-017`
   * @param cruzado el identificador sobre una dirección con {@code ?} o {@code #}: `VAL-023` y
   *     `VAL-018`
   */
  record Codigos(
      String tipo,
      String repetido,
      String obligatoria,
      String direccion,
      String identificador,
      String cruzado) {}

  static final Codigos ALTA =
      new Codigos("VAL-019", "VAL-020", "VAL-021", "VAL-017", "VAL-022", "VAL-023");
  static final Codigos CORRECCION =
      new Codigos("VAL-014", "VAL-015", "VAL-016", "VAL-009", "VAL-017", "VAL-018");

  /**
   * Construye los enlaces de un producto, ya validados.
   *
   * @param peticion la colección recibida; nula o vacía devuelve la lista vacía
   * @return los enlaces en el orden en que llegaron, sin ningún tipo repetido
   */
  static List<ProductLink> construir(
      UUID productId, List<ProductLinkRequest> peticion, OffsetDateTime ahora, Codigos codigos) {

    if (peticion == null || peticion.isEmpty()) {
      return List.of();
    }

    Set<ProductLinkType> vistos = EnumSet.noneOf(ProductLinkType.class);
    List<ProductLink> enlaces = new ArrayList<>(peticion.size());

    for (int indice = 0; indice < peticion.size(); indice++) {
      ProductLinkRequest entrada = peticion.get(indice);
      ProductLinkType tipo = tipoDe(entrada.type(), codigos.tipo(), indice);
      if (!vistos.add(tipo)) {
        // El tipo repetido se rechaza ANTES de escribir nada: ni el producto ni
        // el primer enlace llegan a la base (`CA-PM-382`, `CA-PM-391`).
        String mensaje = "Un producto no puede declarar dos enlaces del mismo tipo.";
        throw new ValidationException(
            codigos.repetido(),
            mensaje,
            List.of(new FieldError("links[" + indice + "].type", codigos.repetido(), mensaje)));
      }
      enlaces.add(
          ProductLink.create(
              productId,
              tipo,
              entrada.url(),
              entrada.externalId(),
              ahora,
              codigos.obligatoria(),
              codigos.direccion(),
              codigos.identificador(),
              codigos.cruzado(),
              indice));
    }
    return enlaces;
  }

  /**
   * El tipo del enlace, <b>contra los valores del enumerado</b> y no contra una lista escrita a
   * mano (`VAL-019` en el alta, `VAL-014` en la corrección).
   *
   * <p>El ausente, el nulo y el desconocido son <b>el mismo error</b> a propósito: los tres dicen
   * que la petición no declara QUÉ es ese enlace, y separarlos obligaría a quien integra a
   * distinguir dos rechazos que se corrigen igual. El mensaje enumera los admitidos, que es lo que
   * falta para corregirlo sin abrir el contrato.
   */
  private static ProductLinkType tipoDe(String valor, String codigo, int indice) {
    String recortado = valor == null ? null : valor.trim();
    if (recortado != null) {
      for (ProductLinkType candidato : ProductLinkType.values()) {
        if (candidato.name().equals(recortado)) {
          return candidato;
        }
      }
    }
    String mensaje =
        "El tipo del enlace es obligatorio y debe ser uno de los admitidos: "
            + Arrays.stream(ProductLinkType.values()).map(Enum::name).toList()
            + ".";
    throw new ValidationException(
        codigo, mensaje, List.of(new FieldError("links[" + indice + "].type", codigo, mensaje)));
  }
}
