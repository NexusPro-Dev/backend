package com.factech.nexus.modules.products.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.shared.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El dominio cerrado del ordenamiento (`RF-PM-002` · `T-02`).
 *
 * <p>Sin Spring, porque lo que se comprueba es que la cadena del cliente <b>no llega al SQL</b>: se
 * resuelve contra el enumerado antes de construir la sentencia, y lo que no está en él no es
 * representable.
 */
class ProductSortFieldTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("`CA-PM-074` — ausente equivale al orden de alta descendente, con desempate")
  void porOmisionElOrdenDeAlta(String sort) {
    ProductSortField.Orden orden = ProductSortField.resolver(sort);

    assertThat(orden.sql()).isEqualTo("p.created_at DESC, p.id DESC");
    assertThat(orden.publico()).isEqualTo("createdAt,desc");
  }

  @Test
  @DisplayName("los tres campos admitidos se resuelven, y el sentido por omisión es ascendente")
  void losTresAdmitidos() {
    assertThat(ProductSortField.resolver("name").sql()).isEqualTo("p.name ASC, p.id ASC");
    assertThat(ProductSortField.resolver("price,desc").sql()).isEqualTo("p.price DESC, p.id DESC");
    assertThat(ProductSortField.resolver("createdAt,asc").sql())
        .isEqualTo("p.created_at ASC, p.id ASC");
  }

  @Test
  @DisplayName("el nombre del campo se admite en cualquier caja, y el sentido también")
  void insensibleALaCaja() {
    assertThat(ProductSortField.resolver("CreatedAt,DESC").sql())
        .isEqualTo("p.created_at DESC, p.id DESC");
  }

  @Test
  @DisplayName("el desempate acompaña SIEMPRE, y en el mismo sentido que el campo pedido")
  void elDesempateSiempreAcompana() {
    // Es lo que hace TOTAL el orden. Sin él, dos productos con el mismo precio
    // pueden repetirse o saltarse entre páginas, y eso se descubre como «faltan
    // productos» sin ningún error de por medio.
    for (String campo : new String[] {"name", "price", "createdAt"}) {
      assertThat(ProductSortField.resolver(campo).sql()).contains("p.id ASC");
      assertThat(ProductSortField.resolver(campo + ",desc").sql()).contains("p.id DESC");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "code,asc",
        "status,asc",
        "deletedAt,desc",
        "targetMembership,asc",
        "updatedAt,asc",
        "price; DROP TABLE products",
        "(SELECT 1)"
      })
  @DisplayName("`VAL-005` — un campo fuera de la lista se RECHAZA, no se ignora")
  void rechazaLoQueNoEstaEnLaLista(String sort) {
    ValidationException fallo =
        catchThrowableOfType(() -> ProductSortField.resolver(sort), ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-005");
    assertThat(fallo.errors())
        .singleElement()
        .satisfies(error -> assertThat(error.field()).isEqualTo("sort"));
    // El mensaje enumera lo admitido: quien se equivocó no tiene que ir a
    // buscar la lista a la documentación.
    assertThat(fallo.getMessage()).contains("name", "price", "createdAt");
  }

  @ParameterizedTest
  @ValueSource(strings = {"name,arriba", "price,ascendente", "createdAt,"})
  @DisplayName("`VAL-005` — el sentido también es dominio cerrado")
  void rechazaElSentidoInventado(String sort) {
    assertThat(
            catchThrowableOfType(() -> ProductSortField.resolver(sort), ValidationException.class))
        .isNotNull();
  }
}
