package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ListProductsRequest;
import com.factech.nexus.modules.products.application.ProductItem;
import com.factech.nexus.modules.products.application.ProductPageResponse;
import com.factech.nexus.modules.products.application.ProductSortField;
import com.factech.nexus.modules.products.domain.models.ProductStatus;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listado del catálogo (`RF-PM-002`).
 *
 * <p><b>Dos sentencias, y ninguna depende del número de filas</b>: la página —con su destino y su
 * moneda resueltos en el mismo {@code JOIN}— y el conteo. La alternativa —resolver el destino fila
 * a fila contra el puerto de `SP`— produce ciento una consultas para una página de cien, que es el
 * patrón que la guía prohíbe por su nombre.
 *
 * <p><b>No hay {@code 404} ni {@code 422}.</b> Un filtro sin coincidencias devuelve {@code 200} con
 * la colección vacía, y una página más allá de la última hace lo mismo: preguntar por algo que no
 * está no es un error, es una respuesta.
 *
 * <p><b>No audita.</b> Una consulta de catálogo no es un evento de seguridad; el único listado que
 * se audita a sí mismo es el de seguridad de `RF-SP-014`, donde mirar <b>es</b> información.
 */
@Service
public class ListProductsService {

  private final ProductQueryRepository consultas;
  private final Pagination paginacion;

  public ListProductsService(ProductQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public ProductPageResponse list(ListProductsRequest filtros) {
    List<FieldError> problemas = new ArrayList<>();

    Pagination.Slice trozo = resolverPaginacion(filtros, problemas);
    ProductSortField.Orden orden = resolverOrden(filtros, problemas);
    String tipo = canonico(filtros.type(), ProductType.values(), "type", "VAL-002", problemas);
    String estado =
        canonico(filtros.status(), ProductStatus.values(), "status", "VAL-003", problemas);

    // Los cuatro `400` se devuelven JUNTOS (`CA-PM-020`): quien se equivocó en
    // cuatro parámetros no tiene que corregir la dirección cuatro veces.
    //
    // La forma del identificador de destino NO entra aquí y sale sola: la
    // rechaza el editor canónico de `shared/error` antes de que la petición
    // llegue a este método. Es el precio de resolverlo una vez para todas las
    // rutas del sistema en lugar de endpoint por endpoint.
    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), resumen(problemas), problemas);
    }

    ListProductsRequest canonicos =
        new ListProductsRequest(
            filtros.page(),
            filtros.size(),
            filtros.sort(),
            tipo,
            estado,
            filtros.sourceMembershipId(),
            filtros.targetMembershipId(),
            filtros.search(),
            filtros.includeDeleted());

    List<ProductQueryRepository.ProductRow> filas =
        consultas.search(canonicos, orden.sql(), trozo.offset(), trozo.size());

    return ProductPageResponse.de(
        PageResponse.de(
            filas.stream().map(ProductItem::from).toList(),
            consultas.count(canonicos),
            trozo.page(),
            trozo.size()),
        orden.publico());
  }

  /**
   * La paginación, cuyos fallos se <b>recogen</b> en lugar de propagarse.
   *
   * <p>{@link Pagination} informa lanzando, que es lo correcto cuando es la única validación de la
   * petición. Aquí no lo es: dejar subir su excepción devolvería el fallo de la página <b>solo</b>,
   * y el actor que además escribió mal el tipo tendría que descubrirlo en la vuelta siguiente.
   *
   * <p>Cuando la paginación falla se sigue validando con un trozo ficticio que <b>nunca se usa</b>:
   * la excepción se lanza antes de consultar nada.
   *
   * <p><b>El código se reetiqueta a `VAL-001`</b>, que es como `spec.md` §11 numera esta
   * validación. {@link Pagination} es de {@code shared} y numera con su propia serie —la misma
   * `VAL-003` que aquí significa «estado fuera de dominio»—, de modo que dejarla pasar tal cual
   * haría que dos parámetros distintos llegaran al cliente con el mismo código. El campo y el
   * mensaje se respetan: lo que cambia es la etiqueta, no el diagnóstico.
   */
  private Pagination.Slice resolverPaginacion(
      ListProductsRequest filtros, List<FieldError> problemas) {
    try {
      return paginacion.resolver(filtros.page(), filtros.size());
    } catch (ValidationException fallo) {
      fallo.errors().stream()
          .map(error -> new FieldError(error.field(), "VAL-001", error.message()))
          .forEach(problemas::add);
      return new Pagination.Slice(0, 1);
    }
  }

  /** El orden, por el mismo motivo que la paginación: su fallo se acumula, no corta. */
  private static ProductSortField.Orden resolverOrden(
      ListProductsRequest filtros, List<FieldError> problemas) {
    try {
      return ProductSortField.resolver(filtros.sort());
    } catch (ValidationException fallo) {
      problemas.addAll(fallo.errors());
      return ProductSortField.resolver(null);
    }
  }

  /**
   * Comprueba que el valor pertenece a su dominio y devuelve su forma canónica.
   *
   * <p><b>Se admite en cualquier caja y se normaliza</b>: {@code status=activo} y {@code
   * status=ACTIVO} son la misma pregunta. Validar sin normalizar es el defecto sutil que hay que
   * evitar —el valor pasaría la comprobación y luego no coincidiría con ningún registro, de modo
   * que el actor recibiría {@code 200} con la colección vacía en lugar de sus productos activos—.
   *
   * @return el nombre canónico del valor, o {@code null} si no se filtró por él
   */
  private static <E extends Enum<E>> String canonico(
      String valor, E[] dominio, String campo, String codigo, List<FieldError> problemas) {

    if (valor == null) {
      return null;
    }
    return Arrays.stream(dominio)
        .map(Enum::name)
        .filter(nombre -> nombre.equalsIgnoreCase(valor))
        .findFirst()
        .orElseGet(
            () -> {
              String mensaje =
                  "El valor '"
                      + valor
                      + "' no pertenece al dominio de "
                      + campo
                      + ". Valores admitidos: "
                      + String.join(", ", Arrays.stream(dominio).map(Enum::name).toList())
                      + ".";
              problemas.add(new FieldError(campo, codigo, mensaje));
              return null;
            });
  }

  /**
   * El mensaje de cabecera cuando falla más de un parámetro.
   *
   * <p>Con uno solo se repite el suyo, que ya es específico. Con varios, la cabecera no puede decir
   * <b>cuál</b> falló —el arreglo {@code errors} lo dice campo por campo— y fingir lo contrario
   * escondería los demás.
   */
  private static String resumen(List<FieldError> problemas) {
    return problemas.size() == 1
        ? problemas.get(0).message()
        : "La consulta trae " + problemas.size() + " parámetros inválidos.";
  }
}
