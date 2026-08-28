package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
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
    assertThat(bot("ASESORIA").getStatus()).isEqualTo(ProductStatus.INACTIVO);
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
      "`RN-PM-002` — un bot CON destino se rechaza con VAL-008, que es la mitad que se olvida")
  void botConDestino() {
    ValidationException fallo =
        catchThrowableOfType(
            () ->
                Product.create(
                    UUID.randomUUID(),
                    "ASESORIA",
                    ProductType.BOT,
                    "Asesoría",
                    null,
                    null,
                    DESTINO,
                    new BigDecimal("49.99"),
                    MONEDA,
                    null,
                    AHORA),
            ValidationException.class);

    // No falla, promete: sin esta comprobación el bot quedaría anunciando
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
            ProductType.BOT,
            "  Asesoría personalizada  ",
            "   ",
            null,
            null,
            new BigDecimal("10.00"),
            MONEDA,
            null,
            AHORA);

    assertThat(producto.getName()).isEqualTo("Asesoría personalizada");
    assertThat(producto.getDescription()).isNull();
  }

  @Test
  @DisplayName("un bot sin destino y un upgrade con destino se construyen sin queja")
  void losDosCasosValidos() {
    assertThatCode(() -> bot("ASESORIA")).doesNotThrowAnyException();
    assertThatCode(() -> upgrade("UPGRADE_ORO", DESTINO)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("`RF-PM-005` · `T-01` — activar devuelve SI HUBO CAMBIO, y no lanza si ya estaba")
  void activarDevuelveSiHuboCambio() {
    Product producto = bot("ASESORIA");

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
    Product producto = bot("ASESORIA");

    // Nace inactivo, de modo que desactivarlo no cambia nada.
    assertThat(producto.deactivate(AHORA)).isFalse();

    producto.activate(AHORA);
    assertThat(producto.deactivate(AHORA)).isTrue();
    assertThat(producto.getStatus()).isEqualTo(ProductStatus.INACTIVO);
  }

  @Test
  @DisplayName("el cambio de estado mueve `updatedAt`, y no moverlo cuando no hay cambio")
  void elCambioMueveLaMarcaDeModificacion() {
    Product producto = bot("ASESORIA");
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
    assertThat(bot("ASESORIA").tieneDescripcion()).isFalse();

    Product conDescripcion =
        Product.create(
            UUID.randomUUID(),
            "ASESORIA",
            ProductType.BOT,
            "Asesoría",
            "  Una hora con un asesor.  ",
            null,
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
    Product producto = bot("ASESORIA");
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
    Product producto = bot("ASESORIA");
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
            null,
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
    Product producto = bot("ASESORIA");
    Map<String, Object> alNacer = producto.instantanea();

    producto.activate(AHORA);
    producto.delete(AHORA.plusDays(1));

    assertThat(producto.instantanea()).containsOnlyKeys(alNacer.keySet().toArray(String[]::new));
  }

  @Test
  @DisplayName("`RF-PM-004` · `T-03` — enviar los MISMOS valores devuelve un diff vacío")
  void elDiffVacioCuandoNadaCambia() {
    Product producto = bot("ASESORIA");
    OffsetDateTime antes = producto.getUpdatedAt();

    Map<String, Object> cambios =
        producto.update(
            Patchable.de("Asesoría"),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.de(new BigDecimal("49.9900")),
            Patchable.ausente(),
            Patchable.ausente(),
            AHORA.plusDays(1));

    // El precio se compara por VALOR y no por `equals`: `49.99` y `49.9900` son
    // el mismo precio con distinta escala, y `equals` los daría por distintos —
    // el registro se llenaría de cambios que no cambian nada.
    assertThat(cambios).isEmpty();
    // Y `updatedAt` no se mueve: moverla haría creer que alguien tocó el
    // producto.
    assertThat(producto.getUpdatedAt()).isEqualTo(antes);
  }

  @Test
  @DisplayName("`RF-PM-004` — el diff trae SOLO lo que cambió, con su valor anterior y el nuevo")
  void elDiffTraeSoloLoQueCambio() {
    Product producto = bot("ASESORIA");

    Map<String, Object> cambios =
        producto.update(
            Patchable.de("Asesoría premium"),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            AHORA.plusDays(1));

    assertThat(cambios).containsOnlyKeys("name");
    assertThat(cambios.get("name"))
        .isEqualTo(Map.of("before", "Asesoría", "after", "Asesoría premium"));
    assertThat(producto.getName()).isEqualTo("Asesoría premium");
    assertThat(producto.getUpdatedAt()).isEqualTo(AHORA.plusDays(1));
  }

  @Test
  @DisplayName("`CA-PM-031` — los campos AUSENTES no se tocan")
  void losAusentesNoSeTocan() {
    Product producto =
        Product.create(
            UUID.randomUUID(),
            "ASESORIA",
            ProductType.BOT,
            "Asesoría",
            "Una hora con un asesor.",
            null,
            null,
            new BigDecimal("49.99"),
            MONEDA,
            30,
            AHORA);

    producto.update(
        Patchable.de("Asesoría premium"),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        AHORA.plusDays(1));

    assertThat(producto.getDescription()).isEqualTo("Una hora con un asesor.");
    assertThat(producto.getPrice()).isEqualByComparingTo(new BigDecimal("49.99"));
    assertThat(producto.getValidityDays()).isEqualTo(30);
  }

  @Test
  @DisplayName("`CA-PM-032` · `CA-PM-094` — el nulo VACÍA la descripción y la vigencia")
  void elNuloVaciaLoQueAdmiteVaciarse() {
    Product producto =
        Product.create(
            UUID.randomUUID(),
            "ASESORIA",
            ProductType.BOT,
            "Asesoría",
            "Una hora con un asesor.",
            null,
            null,
            new BigDecimal("49.99"),
            MONEDA,
            30,
            AHORA);

    Map<String, Object> cambios =
        producto.update(
            Patchable.ausente(),
            Patchable.de(null),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.de(null),
            AHORA.plusDays(1));

    assertThat(producto.getDescription()).isNull();
    // Vaciar la vigencia convierte el producto en uno que NO CADUCA, que es un
    // cambio comercial y no una omisión.
    assertThat(producto.getValidityDays()).isNull();
    assertThat(cambios).containsOnlyKeys("description", "validity_days");
    // El nulo viaja al registro como cadena vacía y no como ausencia: `Map.of`
    // rechaza los nulos, y una clave que desaparece haría indistinguible «se
    // vació» de «no se tocó».
    assertThat(cambios.get("description"))
        .isEqualTo(Map.of("before", "Una hora con un asesor.", "after", ""));
  }

  @Test
  @DisplayName("cambiar el nombre solo en mayúsculas o acentos SÍ es un cambio")
  void laCajaYLosAcentosSonUnCambio() {
    Product producto = bot("ASESORIA");

    Map<String, Object> cambios =
        producto.update(
            Patchable.de("ASESORIA"),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            AHORA.plusDays(1));

    // La unicidad ignora caja y acentos, pero el VALOR guardado no: `Plan Oro`
    // y `Plan oro` son dos textos distintos y el actor pidió el segundo.
    assertThat(cambios).containsOnlyKeys("name");
    assertThat(producto.getName()).isEqualTo("ASESORIA");
  }

  @Test
  @DisplayName("`RN-PM-016` — un bot CON icono se rechaza con VAL-013")
  void botConIcono() {
    ValidationException fallo =
        catchThrowableOfType(
            () ->
                Product.create(
                    UUID.randomUUID(),
                    "ASESORIA",
                    ProductType.BOT,
                    "Asesoría",
                    null,
                    "crown",
                    null,
                    new BigDecimal("49.99"),
                    MONEDA,
                    null,
                    AHORA),
            ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-013");
    assertThat(fallo.errors()).extracting(FieldError::field).containsExactly("icon");
  }

  @Test
  @DisplayName("`RN-PM-016` — el icono es OPCIONAL en el upgrade: sin él el producto es válido")
  void iconoOpcionalEnUpgrade() {
    assertThat(upgrade("UPGRADE_ORO", DESTINO).getIcon()).isNull();
    assertThat(upgradeConIcono("crown").getIcon()).isEqualTo("crown");
  }

  @Test
  @DisplayName("el icono se guarda normalizado: recortado y en minúsculas")
  void iconoNormalizado() {
    assertThat(upgradeConIcono("  Crown  ").getIcon()).isEqualTo("crown");
    assertThat(upgradeConIcono("ARROW-UP-CIRCLE").getIcon()).isEqualTo("arrow-up-circle");

    // El vacío no es un formato malo: es un icono que no se declara.
    assertThat(upgradeConIcono("   ").getIcon()).isNull();
  }

  @Test
  @DisplayName("`VAL-012` — el icono con forma inválida se rechaza")
  void iconoConFormaInvalida() {
    for (String malo : new String[] {"-crown", "9crown", "crown_oro", "crown oro", "coróna"}) {
      ValidationException fallo =
          catchThrowableOfType(() -> upgradeConIcono(malo), ValidationException.class);

      assertThat(fallo).as("debía rechazar «%s»", malo).isNotNull();
      assertThat(fallo.errorCode()).isEqualTo("VAL-012");
    }
  }

  @Test
  @DisplayName("el icono se corrige y se VACÍA con nulo explícito, y el diff lo recoge")
  void iconoSeCorrigeYSeVacia() {
    Product producto = upgradeConIcono("crown");

    Map<String, Object> cambios =
        producto.update(
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.de("rocket"),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            AHORA.plusDays(1));

    assertThat(producto.getIcon()).isEqualTo("rocket");
    assertThat(cambios).containsKey("icon");
    assertThat(cambios.get("icon")).isEqualTo(Map.of("before", "crown", "after", "rocket"));

    Map<String, Object> vaciado =
        producto.update(
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.de(null),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            AHORA.plusDays(2));

    assertThat(producto.getIcon()).isNull();
    assertThat(vaciado.get("icon")).isEqualTo(Map.of("before", "rocket", "after", ""));
  }

  @Test
  @DisplayName("`RN-PM-016` no admite excepción por venir en un PATCH: el bot sigue sin icono")
  void iconoRechazadoTambienAlCorregirUnBot() {
    Product producto = bot("ASESORIA");

    ValidationException fallo =
        catchThrowableOfType(
            () ->
                producto.update(
                    Patchable.ausente(),
                    Patchable.ausente(),
                    Patchable.de("crown"),
                    Patchable.ausente(),
                    Patchable.ausente(),
                    Patchable.ausente(),
                    AHORA.plusDays(1)),
            ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-013");
    // Y el producto no se queda a medias: el rechazo ocurre antes de asignar.
    assertThat(producto.getIcon()).isNull();
  }

  private static Product upgradeConIcono(String icono) {
    return Product.create(
        UUID.randomUUID(),
        "UPGRADE_ORO",
        ProductType.UPGRADE_MEMBRESIA,
        "Ascenso a Oro",
        null,
        icono,
        DESTINO,
        new BigDecimal("49.99"),
        MONEDA,
        null,
        AHORA);
  }

  private static Product upgrade(String codigo, UUID destino) {
    return Product.create(
        UUID.randomUUID(),
        codigo,
        ProductType.UPGRADE_MEMBRESIA,
        "Ascenso a Oro",
        null,
        null,
        destino,
        new BigDecimal("49.99"),
        MONEDA,
        null,
        AHORA);
  }

  private static Product bot(String codigo) {
    return Product.create(
        UUID.randomUUID(),
        codigo,
        ProductType.BOT,
        "Asesoría",
        null,
        null,
        null,
        new BigDecimal("49.99"),
        MONEDA,
        null,
        AHORA);
  }
}
