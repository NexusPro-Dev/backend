package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El agregado del catálogo (`RF-PM-001` · `T-03`, `T-18`).
 *
 * <p>Sin Spring: lo que se comprueba aquí es que un producto <b>mal formado no pueda existir dentro
 * del modelo</b>, venga por donde venga. El DTO valida a quien llega por HTTP; esto vale también
 * para una siembra o para otro caso de uso.
 */
class ProductTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 8, 27, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final UUID DESTINO = UUID.randomUUID();
  private static final UUID MONEDA = UUID.randomUUID();

  @Test
  @DisplayName("`RN-PM-012` — todo producto nace INACTIVO, y el estado no se puede pasar")
  void naceInactivo() {
    assertThat(upgrade("UPGRADE_ORO", DESTINO).getStatus()).isEqualTo(ProductStatus.INACTIVO);
    assertThat(servicio("ASESORIA").getStatus()).isEqualTo(ProductStatus.INACTIVO);
  }

  @Test
  @DisplayName("el código se recorta y se pasa a mayúsculas al escribir")
  void normalizaElCodigo() {
    assertThat(upgrade("  upgrade_oro  ", DESTINO).getCode()).isEqualTo("UPGRADE_ORO");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "1ORO", "_ORO", "ORO-PLUS", "ORO PLUS", "ORO+"})
  @DisplayName("`VAL-010` — el código que no cumple el formato se rechaza")
  void rechazaCodigoMalFormado(String codigo) {
    ValidationException fallo =
        catchThrowableOfType(() -> upgrade(codigo, DESTINO), ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-010");
    assertThat(fallo.errors())
        .singleElement()
        .satisfies(e -> assertThat(e.field()).isEqualTo("code"));
  }

  @Test
  @DisplayName("`RN-PM-002` — un upgrade SIN destino se rechaza con VAL-007")
  void upgradeSinDestino() {
    ValidationException fallo =
        catchThrowableOfType(() -> upgrade("UPGRADE_ORO", null), ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-007");
  }

  @Test
  @DisplayName(
      "`RN-PM-002` — un servicio CON destino se rechaza con VAL-008, que es la mitad que se olvida")
  void servicioConDestino() {
    ValidationException fallo =
        catchThrowableOfType(
            () ->
                Product.create(
                    UUID.randomUUID(),
                    "ASESORIA",
                    ProductType.SERVICIO,
                    "Asesoría",
                    null,
                    DESTINO,
                    new BigDecimal("49.99"),
                    MONEDA,
                    null,
                    AHORA),
            ValidationException.class);

    // No falla, promete: sin esta comprobación el servicio quedaría anunciando
    // un cambio de nivel que nadie va a aplicar.
    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-008");
  }

  @Test
  @DisplayName("`RN-PM-015` — la vigencia es opcional: sin ella el producto no caduca")
  void vigenciaOpcional() {
    assertThat(upgrade("UPGRADE_ORO", DESTINO).getValidityDays()).isNull();

    Product conVigencia =
        Product.create(
            UUID.randomUUID(),
            "UPGRADE_MES",
            ProductType.UPGRADE_MEMBRESIA,
            "Ascenso mensual",
            null,
            DESTINO,
            new BigDecimal("19.99"),
            MONEDA,
            30,
            AHORA);

    assertThat(conVigencia.getValidityDays()).isEqualTo(30);
  }

  @Test
  @DisplayName("el nombre y la descripción se recortan; el vacío queda nulo")
  void recortaTextos() {
    Product producto =
        Product.create(
            UUID.randomUUID(),
            "ASESORIA",
            ProductType.SERVICIO,
            "  Asesoría personalizada  ",
            "   ",
            null,
            new BigDecimal("10.00"),
            MONEDA,
            null,
            AHORA);

    assertThat(producto.getName()).isEqualTo("Asesoría personalizada");
    assertThat(producto.getDescription()).isNull();
  }

  @Test
  @DisplayName("un servicio sin destino y un upgrade con destino se construyen sin queja")
  void losDosCasosValidos() {
    assertThatCode(() -> servicio("ASESORIA")).doesNotThrowAnyException();
    assertThatCode(() -> upgrade("UPGRADE_ORO", DESTINO)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("`RF-PM-005` · `T-01` — activar devuelve SI HUBO CAMBIO, y no lanza si ya estaba")
  void activarDevuelveSiHuboCambio() {
    Product producto = servicio("ASESORIA");

    assertThat(producto.activate(AHORA)).as("de inactivo a activo, sí hubo cambio").isTrue();
    assertThat(producto.getStatus()).isEqualTo(ProductStatus.ACTIVO);

    // Y la segunda vez NO es un error: quien pulsa dos veces el mismo botón no
    // ha hecho nada malo. El valor devuelto es lo que decide si se audita, y un
    // evento por una petición que no cambió nada convierte el registro en ruido.
    assertThat(producto.activate(AHORA)).as("ya estaba activo, no hubo cambio").isFalse();
    assertThat(producto.getStatus()).isEqualTo(ProductStatus.ACTIVO);
  }

  @Test
  @DisplayName("`RF-PM-005` · `T-01` — y desactivar hace lo simétrico")
  void desactivarDevuelveSiHuboCambio() {
    Product producto = servicio("ASESORIA");

    // Nace inactivo, de modo que desactivarlo no cambia nada.
    assertThat(producto.deactivate(AHORA)).isFalse();

    producto.activate(AHORA);
    assertThat(producto.deactivate(AHORA)).isTrue();
    assertThat(producto.getStatus()).isEqualTo(ProductStatus.INACTIVO);
  }

  @Test
  @DisplayName("el cambio de estado mueve `updatedAt`, y no moverlo cuando no hay cambio")
  void elCambioMueveLaMarcaDeModificacion() {
    Product producto = servicio("ASESORIA");
    OffsetDateTime despues = AHORA.plusDays(1);

    producto.activate(despues);
    assertThat(producto.getUpdatedAt()).isEqualTo(despues);

    // Sin cambio, la marca se queda donde estaba: mover `updatedAt` sin que
    // nada cambie haría creer que alguien tocó el producto.
    producto.activate(despues.plusDays(1));
    assertThat(producto.getUpdatedAt()).isEqualTo(despues);
  }

  @Test
  @DisplayName("`RN-PM-014` — la descripción en blanco NO cuenta como descripción")
  void laDescripcionEnBlancoNoCuenta() {
    // `create` deja en nulo la que solo trae espacios, de modo que preguntar
    // por el nulo aquí es preguntar por lo mismo que se guardó.
    assertThat(servicio("ASESORIA").tieneDescripcion()).isFalse();

    Product conDescripcion =
        Product.create(
            UUID.randomUUID(),
            "ASESORIA",
            ProductType.SERVICIO,
            "Asesoría",
            "  Una hora con un asesor.  ",
            null,
            new BigDecimal("49.99"),
            MONEDA,
            null,
            AHORA);

    assertThat(conDescripcion.tieneDescripcion()).isTrue();
  }

  @Test
  @DisplayName("`RF-PM-006` · `T-02` — retirar NO toca el estado: un activo retirado sigue activo")
  void elRetiroNoTocaElEstado() {
    Product producto = servicio("ASESORIA");
    producto.activate(AHORA);

    producto.delete(AHORA.plusDays(1));

    // `CA-PM-052`: el registro tiene que poder decir si el producto ESTABA A LA
    // VENTA cuando se retiró. Desactivarlo «de paso» haría que todos los
    // registros dijeran «inactivo» y ese dato dejaría de significar nada — la
    // salvaguarda habría destruido la evidencia que protege.
    assertThat(producto.getStatus()).isEqualTo(ProductStatus.ACTIVO);
    assertThat(producto.getDeletedAt()).isEqualTo(AHORA.plusDays(1));
    assertThat(producto.estaRetirado()).isTrue();
  }

  @Test
  @DisplayName("`RF-PM-006` — retirar dos veces devuelve «sin cambio» y no pisa la primera fecha")
  void elSegundoRetiroNoPisaAlPrimero() {
    Product producto = servicio("ASESORIA");
    producto.delete(AHORA);

    assertThat(producto.delete(AHORA.plusDays(5))).isFalse();
    // La fecha del retiro es la del hecho real, no la del último intento.
    assertThat(producto.getDeletedAt()).isEqualTo(AHORA);
  }

  @Test
  @DisplayName("`RF-PM-006` · `T-03` — la instantánea describe el estado ANTERIOR al retiro")
  void laInstantaneaEsLaDelEstadoAnterior() {
    Product producto =
        Product.create(
            UUID.randomUUID(),
            "UPGRADE_ORO",
            ProductType.UPGRADE_MEMBRESIA,
            "Ascenso a Oro",
            "Sube al nivel oro.",
            DESTINO,
            new BigDecimal("49.99"),
            MONEDA,
            30,
            AHORA);
    producto.activate(AHORA);

    // Se captura ANTES de tocar nada, que es el orden que el caso de uso sigue.
    Map<String, Object> antes = producto.instantanea();
    producto.delete(AHORA.plusDays(1));

    // Capturarla después dejaría el registro diciendo qué QUEDÓ del producto y
    // no qué ERA. La diferencia no se ve —el registro «tiene datos» igual— y
    // por eso se comprueba contra el valor anterior y no contra el resultado.
    assertThat(antes.get("status")).isEqualTo("ACTIVO");
    assertThat(antes.get("code")).isEqualTo("UPGRADE_ORO");
    assertThat(antes.get("name")).isEqualTo("Ascenso a Oro");
    assertThat(antes.get("description")).isEqualTo("Sube al nivel oro.");
    assertThat(antes.get("type")).isEqualTo("UPGRADE_MEMBRESIA");
    assertThat(antes.get("target_membership_id")).isEqualTo(DESTINO.toString());
    assertThat(antes.get("currency_id")).isEqualTo(MONEDA.toString());
    assertThat(antes.get("validity_days")).isEqualTo(30);
    // El precio va como TEXTO: `BigDecimal` serializado a JSON puede perder la
    // escala, y en un registro de auditoría `49.99` y `49.990` no son lo mismo.
    assertThat(antes.get("price")).isEqualTo("49.99");
  }

  @Test
  @DisplayName("la instantánea del alta y la del retiro tienen LAS MISMAS claves")
  void lasDosInstantaneasHablanElMismoIdioma() {
    // Si cada caso de uso armara su mapa, el registro de creación y el de
    // eliminación describirían el mismo producto con claves distintas, y
    // comparar los dos —que es para lo que existen— dejaría de ser posible.
    Product producto = servicio("ASESORIA");
    Map<String, Object> alNacer = producto.instantanea();

    producto.activate(AHORA);
    producto.delete(AHORA.plusDays(1));

    assertThat(producto.instantanea()).containsOnlyKeys(alNacer.keySet().toArray(String[]::new));
  }

  private static Product upgrade(String codigo, UUID destino) {
    return Product.create(
        UUID.randomUUID(),
        codigo,
        ProductType.UPGRADE_MEMBRESIA,
        "Ascenso a Oro",
        null,
        destino,
        new BigDecimal("49.99"),
        MONEDA,
        null,
        AHORA);
  }

  private static Product servicio(String codigo) {
    return Product.create(
        UUID.randomUUID(),
        codigo,
        ProductType.SERVICIO,
        "Asesoría",
        null,
        null,
        new BigDecimal("49.99"),
        MONEDA,
        null,
        AHORA);
  }
}
