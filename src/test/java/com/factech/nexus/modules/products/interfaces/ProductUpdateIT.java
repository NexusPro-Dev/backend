package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * La corrección de un producto (`RF-PM-004` · `T-10`).
 *
 * <p>Cubre los criterios de `spec.md` §12. La prueba que sostiene todo lo demás es la de los
 * <b>tres estados</b>: vaciar la descripción la borra, enviar el nombre vacío se rechaza, y el
 * campo ausente no se toca. Si los tres no se distinguieran, la operación borraría lo que nadie
 * pidió borrar.
 */
@AutoConfigureMockMvc
class ProductUpdateIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, con dos decimales. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;
  private UUID free;
  private UUID producto;

  @BeforeEach
  void sembrarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
    oro = membresia("ORO", "Oro", 1);
    // El SUELO de la cadena: es el origen de todo upgrade que se siembre
    // aqui. Va encadenado bajo `oro` porque `uq_memberships_parent` es
    // UNIQUE NULLS NOT DISTINCT — dos raices revientan en el COMMIT.
    free = membresia("BECA", "Beca", 2, oro);
    producto = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro, "Sube al nivel oro.", 30);
  }

  @AfterEach
  void vaciarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM product_images");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName("`CA-PM-030` — corrige nombre, descripción, precio y moneda; conserva lo inmutable")
  void corrigeLoCorregible() throws Exception {
    String euro = monedaAlterna();

    mvc.perform(
            corregir(
                producto,
                """
                {"name":"Ascenso a Oro premium","description":"Ahora con soporte.",
                 "price":59.99,"currencyId":"%s"}
                """
                    .formatted(euro)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ascenso a Oro premium"))
        .andExpect(jsonPath("$.description").value("Ahora con soporte."))
        .andExpect(jsonPath("$.price").value(59.99))
        .andExpect(jsonPath("$.currency.code").value("EUR"))
        // Lo que no se puede corregir sigue donde estaba.
        .andExpect(jsonPath("$.code").value("UPGRADE_ORO"))
        .andExpect(jsonPath("$.type").value("UPGRADE_MEMBRESIA"))
        .andExpect(jsonPath("$.targetMembership.id").value(oro.toString()));
  }

  @Test
  @DisplayName("`CA-PM-031` — aplica SOLO lo enviado y deja intacto lo ausente")
  void soloLoEnviado() throws Exception {
    mvc.perform(corregir(producto, "{\"name\":\"Otro nombre\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Otro nombre"))
        .andExpect(jsonPath("$.description").value("Sube al nivel oro."))
        .andExpect(jsonPath("$.price").value(49.99))
        .andExpect(jsonPath("$.validityDays").value(30));
  }

  @Test
  @DisplayName("`CA-PM-032` — vaciar la descripción la BORRA; el nombre vacío se RECHAZA")
  void ausenteNoEsLoMismoQueVacio() throws Exception {
    mvc.perform(corregir(producto, "{\"description\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value(Matchers.nullValue()));

    // El nombre no admite vaciarse: su columna es NOT NULL, y aceptar el nulo
    // produciría una violación de integridad traducida a 500 en lugar del 400
    // que corresponde.
    mvc.perform(corregir(producto, "{\"name\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));

    mvc.perform(corregir(producto, "{\"name\":\"   \"}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-099` — el icono se corrige; y se vacía con nulo explícito SI hay portada")
  void corregirElIcono() throws Exception {
    mvc.perform(corregir(producto, "{\"icon\":\"  CROWN  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icon").value("crown"));

    // Desde el 14-09-2026 (`RN-PM-034`) el vaciado exige portada: `CA-PM-235`.
    ponerPortada(producto);
    mvc.perform(corregir(producto, "{\"icon\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icon").value(Matchers.nullValue()))
        .andExpect(
            jsonPath("$.coverImageUrl").value(Matchers.startsWith("/api/v1/product-images/")));
    assertThat(ultimoCambio(producto)).contains("\"icon\"");
  }

  @Test
  @DisplayName(
      "`CA-PM-350` — el alcance se corrige a los cuatro valores; NINGUNO no desactiva; HOTLINKS no")
  void elAlcanceSeCorrigeALosCuatro() throws Exception {
    jdbc.update(
        "UPDATE products SET status = 'ACTIVO' WHERE id = CAST(? AS uuid)", producto.toString());
    for (String alcance : new String[] {"HOTLINK", "AMBOS", "NINGUNO", "TIENDA"}) {
      mvc.perform(corregir(producto, "{\"scope\":\"" + alcance + "\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scope").value(alcance))
          // Corregir a `NINGUNO` no desactiva: sigue activo y deja de ofrecerse.
          .andExpect(jsonPath("$.status").value("ACTIVO"));
    }

    // Como cualquier valor fuera del dominio del enumerado: 400 sin aplicar nada.
    mvc.perform(corregir(producto, "{\"name\":\"Otro\",\"scope\":\"HOTLINKS\"}"))
        .andExpect(status().isBadRequest());
    assertThat(nombreDe(producto)).isEqualTo("Ascenso a Oro");
  }

  @Test
  @DisplayName("`CA-PM-234` — `RN-PM-034`: sin portada, el icono de un upgrade NO se vacía")
  void elIconoNoSeVaciaSinPortada() throws Exception {
    mvc.perform(corregir(producto, "{\"icon\":\"crown\"}")).andExpect(status().isOk());
    long antes = eventosDe(producto);

    for (String vacio : new String[] {"null", "\"\"", "\"   \""}) {
      mvc.perform(corregir(producto, "{\"name\":\"Otro nombre\",\"icon\":" + vacio + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-010"))
          .andExpect(jsonPath("$.errors[0].field").value("icon"));
    }
    // Nada de la misma petición se aplicó, y no hay auditoría del intento.
    assertThat(nombreDe(producto)).isEqualTo("Ascenso a Oro");
    assertThat(iconoDe(producto)).isEqualTo("crown");
    assertThat(eventosDe(producto)).isEqualTo(antes);

    // Corregirlo por otro sigue admitiéndose: no deja al producto sin nada.
    mvc.perform(corregir(producto, "{\"icon\":\"rocket\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icon").value("rocket"));
  }

  @Test
  @DisplayName("`CA-PM-236` — la respuesta trae `coverImageUrl`, y en el cuerpo se ignora")
  void laRespuestaTraeLaPortadaYElCuerpoLaIgnora() throws Exception {
    mvc.perform(corregir(producto, "{\"name\":\"Ascenso a Oro II\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(Matchers.nullValue()));

    // Un `coverImageUrl` en el cuerpo no es un campo del producto que se
    // corrija por JSON: se ignora como cualquier desconocido, y no cambia nada.
    mvc.perform(
            corregir(
                producto,
                "{\"coverImageUrl\":\"/api/v1/product-images/" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest());
    assertThat(portadaDe(producto)).isNull();

    UUID imagen = ponerPortada(producto);
    mvc.perform(corregir(producto, "{\"name\":\"Ascenso a Oro III\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value("/api/v1/product-images/" + imagen));
  }

  @Test
  @DisplayName("`CA-PM-100` — `RN-PM-016` no admite excepción por venir en un PATCH")
  void elBotSigueSinPoderLlevarIcono() throws Exception {
    UUID asesoria = bot("ASESORIA", "Asesoría", null);

    mvc.perform(corregir(asesoria, "{\"icon\":\"crown\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-013"))
        .andExpect(jsonPath("$.errors[0].field").value("icon"));

    // Vaciar el que nunca tuvo no es un cambio, y no es un error: el nulo es el
    // único valor que un bot puede llevar.
    mvc.perform(corregir(asesoria, "{\"icon\":null}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-PM-094` — vaciar la vigencia convierte el producto en uno que no caduca")
  void vaciarLaVigencia() throws Exception {
    mvc.perform(corregir(producto, "{\"validityDays\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validityDays").value(Matchers.nullValue()));

    assertThat(vigenciaDe(producto)).isNull();
  }

  @Test
  @DisplayName("`VAL-011` — una vigencia de cero o negativa se rechaza")
  void vigenciaNoPositiva() throws Exception {
    mvc.perform(corregir(producto, "{\"validityDays\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));

    mvc.perform(corregir(producto, "{\"validityDays\":-5}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-033` — el tipo, el código y el destino se RECHAZAN, no se ignoran")
  void losInmutablesSeRechazan() throws Exception {
    mvc.perform(corregir(producto, "{\"code\":\"OTRO_CODIGO\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));

    mvc.perform(corregir(producto, "{\"type\":\"BOT\"}")).andExpect(status().isBadRequest());
    mvc.perform(corregir(producto, "{\"targetMembershipId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest());

    // Y el rechazo NO aplica lo demás que venía en la misma petición.
    mvc.perform(corregir(producto, "{\"name\":\"Nombre nuevo\",\"code\":\"OTRO\"}"))
        .andExpect(status().isBadRequest());
    assertThat(nombreDe(producto)).isEqualTo("Ascenso a Oro");
  }

  @Test
  @DisplayName("los tres inmutables enviados juntos se enumeran los tres")
  void losTresInmutablesJuntos() throws Exception {
    mvc.perform(
            corregir(
                producto,
                """
                {"type":"BOT","code":"OTRO","targetMembershipId":"%s"}
                """
                    .formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(3));
  }

  @Test
  @DisplayName("`CA-PM-034` — el nombre de OTRO producto vivo se rechaza, y no aplica nada más")
  void nombreDuplicado() throws Exception {
    bot("SOPORTE", "Soporte prioritario", "Atención prioritaria.");

    mvc.perform(
            corregir(producto, "{\"name\":\"Soporte prioritario\",\"description\":\"Otra cosa.\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));

    // NINGUNO de los cambios enviados se aplica.
    assertThat(nombreDe(producto)).isEqualTo("Ascenso a Oro");
    assertThat(descripcionDe(producto)).isEqualTo("Sube al nivel oro.");
  }

  @Test
  @DisplayName("enviar el nombre que YA TIENE no es un duplicado consigo mismo")
  void elPropioNombreNoEsDuplicado() throws Exception {
    // Sin excluir al propio producto, corregir la descripción enviando también
    // el nombre actual acabaría rechazándose.
    mvc.perform(corregir(producto, "{\"name\":\"Ascenso a Oro\",\"description\":\"Otra cosa.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Otra cosa."));
  }

  @Test
  @DisplayName("un nombre que solo cambia en mayúsculas o acentos SÍ se admite")
  void cambiarSoloLaCaja() throws Exception {
    // El choque es contra OTROS productos, no contra uno mismo: `Ascenso a Oro`
    // y `ASCENSO A ORO` normalizan igual, y aun así este cambio es legítimo.
    mvc.perform(corregir(producto, "{\"name\":\"ASCENSO A ORO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("ASCENSO A ORO"));
  }

  @Test
  @DisplayName("`CA-PM-035` — una moneda inactiva se rechaza y no aplica ningún cambio")
  void rechazaMonedaDesactivada() throws Exception {
    String inactiva = crearMonedaInactiva();

    mvc.perform(
            corregir(
                producto, "{\"currencyId\":\"" + inactiva + "\",\"description\":\"Otra cosa.\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(descripcionDe(producto)).isEqualTo("Sube al nivel oro.");
  }

  @Test
  @DisplayName("`EX-003` — una moneda inexistente también se rechaza")
  void monedaInexistente() throws Exception {
    mvc.perform(corregir(producto, "{\"currencyId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
  }

  @Test
  @DisplayName("`VAL-005` — el precio se valida contra la moneda NUEVA, no contra la anterior")
  void elPrecioContraLaMonedaNueva() throws Exception {
    String pesos = monedaSinDecimales();

    // `49.99` cabe en USD, que declara dos decimales, y NO cabe en una moneda
    // de cero. Validar contra la anterior lo dejaría entrar.
    mvc.perform(corregir(producto, "{\"currencyId\":\"" + pesos + "\",\"price\":49.99}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));

    // Con un importe sin decimales, la misma moneda sí lo admite.
    mvc.perform(corregir(producto, "{\"currencyId\":\"" + pesos + "\",\"price\":50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency.decimalPlaces").value(0));
  }

  @Test
  @DisplayName(
      "cambiar solo la moneda NO convierte el importe: el sistema no hace cambio de divisa")
  void cambiarMonedaNoConvierte() throws Exception {
    String euro = monedaAlterna();

    mvc.perform(corregir(producto, "{\"currencyId\":\"" + euro + "\"}"))
        .andExpect(status().isOk())
        // Cambiar de moneda es declarar que ese número SIEMPRE estuvo en la
        // otra, no convertirlo.
        .andExpect(jsonPath("$.price").value(49.99))
        .andExpect(jsonPath("$.currency.code").value("EUR"));
  }

  @Test
  @DisplayName("`VAL-004` — un precio NEGATIVO se rechaza. El cero se admite desde el 08-09-2026")
  void precioNegativo() throws Exception {
    mvc.perform(corregir(producto, "{\"price\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));

    // `RN-PM-006` dejó de exigir «mayor que cero» con la renovación: un
    // `BECA → BECA` es un producto legítimo que vale cero.
    mvc.perform(corregir(producto, "{\"price\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(0));
  }

  // ---------------------------------------------------------------------------
  // El precio de compra (`RN-PM-023`) — 08-09-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-153` — corregir el precio de compra no toca el del sistema, y se audita")
  void corrigeElPrecioDeCompra() throws Exception {
    mvc.perform(corregir(producto, "{\"purchasePrice\":59.99}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(49.99))
        .andExpect(jsonPath("$.purchasePrice").value(59.99));

    assertThat(ultimoCambio()).contains("purchase_price").contains("59.99");
  }

  @Test
  @DisplayName("`CA-PM-154` — el nulo explícito VACÍA el precio de compra, y no lo pone a cero")
  void vaciaElPrecioDeCompra() throws Exception {
    mvc.perform(corregir(producto, "{\"purchasePrice\":59.99}")).andExpect(status().isOk());

    mvc.perform(corregir(producto, "{\"purchasePrice\":null}"))
        .andExpect(status().isOk())
        // El costo pasa a «no se conoce». Ponerlo a cero lo habría dejado
        // diciendo que no costó nada, que es lo contrario.
        .andExpect(jsonPath("$.purchasePrice").value(Matchers.nullValue()));

    assertThat(precioDeCompraDe(producto)).isNull();
  }

  @Test
  @DisplayName("`CA-PM-155` — el precio del sistema NO admite vaciarse, al revés que el de compra")
  void elPrecioDelSistemaNoSeVacia() throws Exception {
    mvc.perform(corregir(producto, "{\"price\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"))
        .andExpect(jsonPath("$.errors[0].field").value("price"));

    // Y no se aplica nada: la columna es `NOT NULL` y «bórralo» no tiene
    // ningún estado al que llevar el producto.
    assertThat(precioDe(producto)).isEqualByComparingTo("49.99");
  }

  @Test
  @DisplayName("`CA-PM-156` — corregir el precio de compra a CERO es un cambio, no un vaciado")
  void elPrecioDeCompraACero() throws Exception {
    mvc.perform(corregir(producto, "{\"purchasePrice\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purchasePrice").value(0));

    assertThat(ultimoCambio()).contains("purchase_price");
  }

  @Test
  @DisplayName(
      "`CA-PM-157` — cambiar SOLO la moneda mide también el precio de compra que nadie tocó")
  void elPrecioDeCompraContraLaMonedaNueva() throws Exception {
    String pesos = monedaSinDecimales();

    // El del sistema se deja en un importe que SÍ cabe en una moneda de cero
    // decimales, y el de compra en uno que no. Con dos importes, el caso que se
    // olvida es este: se valida el que llega y se deja pasar el otro.
    mvc.perform(corregir(producto, "{\"price\":50,\"purchasePrice\":59.99}"))
        .andExpect(status().isOk());

    mvc.perform(corregir(producto, "{\"currencyId\":\"" + pesos + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"))
        // Y el rechazo NOMBRA el campo que no cabe: el del sistema sí cabía.
        .andExpect(jsonPath("$.errors[0].field").value("purchasePrice"));

    // El defecto que esto evita NO FALLA: guardaría un importe con más
    // decimales de los que su moneda admite.
    assertThat(precioDeCompraDe(producto)).isEqualByComparingTo("59.99");
  }

  // ---------------------------------------------------------------------------
  // El enlace del video (`RN-PM-032`) — 14-09-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-225` — el enlace del video se corrige, también en un BOT, y se audita")
  void corrigeElVideo() throws Exception {
    // En un bot a propósito: es donde el icono se rechaza (`CA-PM-100`) y el
    // video no — no hay condición cruzada que lo acompañe.
    UUID asesoria = bot("ASESORIA", "Asesoría", null);

    mvc.perform(corregir(asesoria, "{\"videoUrl\":\"  https://vimeo.com/123456  \"}"))
        .andExpect(status().isOk())
        // Recortado y NADA MÁS: ni minúsculas ni barra final.
        .andExpect(jsonPath("$.videoUrl").value("https://vimeo.com/123456"));

    mvc.perform(corregir(asesoria, "{\"videoUrl\":\"https://Vimeo.com/999/\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videoUrl").value("https://Vimeo.com/999/"));

    assertThat(ultimoCambio())
        .contains("video_url")
        .contains("https://vimeo.com/123456")
        .contains("https://Vimeo.com/999/");
  }

  @Test
  @DisplayName(
      "`CA-PM-226` — el nulo explícito Y la cadena vacía VACÍAN el video; el mismo enlace no es"
          + " cambio")
  void vaciaElVideo() throws Exception {
    mvc.perform(corregir(producto, "{\"videoUrl\":\"https://vimeo.com/123456\"}"))
        .andExpect(status().isOk());

    // El mismo enlace otra vez no es un cambio: `audit_change_log` no crece.
    long antes = eventosDe(producto);
    mvc.perform(corregir(producto, "{\"videoUrl\":\"https://vimeo.com/123456\"}"))
        .andExpect(status().isOk());
    assertThat(eventosDe(producto)).isEqualTo(antes);

    mvc.perform(corregir(producto, "{\"videoUrl\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videoUrl").value(Matchers.nullValue()));
    assertThat(videoDe(producto)).isNull();

    // Y `""` es un vaciado, no un enlace con forma inválida: quien borra el
    // contenido del campo en un formulario está vaciando.
    mvc.perform(corregir(producto, "{\"videoUrl\":\"https://vimeo.com/123456\"}"))
        .andExpect(status().isOk());
    mvc.perform(corregir(producto, "{\"videoUrl\":\"   \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videoUrl").value(Matchers.nullValue()));
    assertThat(videoDe(producto)).isNull();
  }

  @Test
  @DisplayName(
      "`CA-PM-227` — un enlace sin forma se rechaza con VAL-009, nombra `videoUrl` y NO aplica lo"
          + " demás")
  void videoConFormaInvalidaNoAplicaNada() throws Exception {
    mvc.perform(
            corregir(
                producto, "{\"name\":\"Otro nombre\",\"videoUrl\":\"www.youtube.com/watch?v=x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"))
        .andExpect(jsonPath("$.errors[0].field").value("videoUrl"));

    // El nombre válido que venía en la misma petición no se aplicó.
    assertThat(nombreDe(producto)).isEqualTo("Ascenso a Oro");
    assertThat(videoDe(producto)).isNull();
  }

  private String iconoDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT icon FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  private UUID portadaDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT cover_image_id FROM products WHERE id = CAST(? AS uuid)",
        UUID.class,
        id.toString());
  }

  /** Una portada por la base, sin pasar por `RF-PM-014`: aquí se prueba la corrección. */
  private UUID ponerPortada(UUID producto) {
    UUID imagen = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_images (id, content_type, content) VALUES (CAST(? AS uuid),"
            + " 'image/png', decode('89504E470D0A1A0A00', 'hex'))",
        imagen.toString());
    jdbc.update(
        "UPDATE products SET cover_image_id = CAST(? AS uuid) WHERE id = CAST(? AS uuid)",
        imagen.toString(),
        producto.toString());
    return imagen;
  }

  private String videoDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT video_url FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  /** El `changes` del último evento de corrección de este producto. */
  private String ultimoCambio() {
    return jdbc.queryForObject(
        """
        SELECT changes::text FROM audit_change_log
         WHERE module = 'PM' AND entity = 'products' AND action = 'UPDATE'
         ORDER BY occurred_at DESC LIMIT 1
        """,
        String.class);
  }

  @Test
  @DisplayName("`CA-PM-036` — un producto retirado NO se corrige")
  void elRetiradoNoSeCorrige() throws Exception {
    jdbc.update(
        "UPDATE products SET deleted_at = ? WHERE id = CAST(? AS uuid)",
        BASE.plusDays(1),
        producto.toString());

    // Lo que se retiró debe quedar como estaba para que lo que lo referencie
    // siga diciendo la verdad.
    mvc.perform(corregir(producto, "{\"name\":\"Otro nombre\"}")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`CA-PM-083` — SÍ se corrige un producto inactivo, que es el estado en el que nace")
  void elInactivoSeCorrige() throws Exception {
    // Sin esto no habría forma de ponerle la descripción que `RF-PM-005` exige
    // para publicarlo: el producto quedaría atrapado en el estado inicial.
    assertThat(estadoDe(producto)).isEqualTo("INACTIVO");

    mvc.perform(corregir(producto, "{\"description\":\"Una descripción para publicar.\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-PM-037` — el registro guarda SOLO los campos que cambiaron")
  void auditaSoloLoQueCambio() throws Exception {
    mvc.perform(corregir(producto, "{\"name\":\"Otro nombre\",\"validityDays\":30}"))
        .andExpect(status().isOk());

    String cambios = ultimoCambio(producto);

    // La vigencia se envió con el MISMO valor que ya tenía: no cambió, y no
    // debe aparecer.
    assertThat(cambios).contains("name").contains("Ascenso a Oro").contains("Otro nombre");
    assertThat(cambios).doesNotContain("validity_days");
  }

  @Test
  @DisplayName("`CA-PM-038` — la petición que no cambia nada devuelve 200 y NO registra evento")
  void sinCambioNoRegistraEvento() throws Exception {
    long antes = eventosDe(producto);

    mvc.perform(corregir(producto, "{\"name\":\"Ascenso a Oro\",\"price\":49.99}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ascenso a Oro"));

    assertThat(eventosDe(producto)).isEqualTo(antes);
  }

  @Test
  @DisplayName("`VAL-002` — un cuerpo sin ningún campo corregible se rechaza")
  void cuerpoVacio() throws Exception {
    mvc.perform(corregir(producto, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName("una propiedad desconocida se rechaza, y no se ignora en silencio")
  void propiedadDesconocida() throws Exception {
    mvc.perform(corregir(producto, "{\"status\":\"ACTIVO\"}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-084` — no se exige motivo, ni siquiera al cambiar el precio")
  void sinMotivo() throws Exception {
    mvc.perform(corregir(producto, "{\"price\":99.99}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`VAL-001` — el identificador no canónico es 400, no 404")
  void identificadorNoCanonico() throws Exception {
    mvc.perform(
            patch("/api/v1/products/{id}", "1-1-1-1-1")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Otro\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`EX-001` — un identificador inexistente es 404")
  void inexistente() throws Exception {
    mvc.perform(corregir(UUID.randomUUID(), "{\"name\":\"Otro\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`CA-PM-039` — sin `products:update`, la corrección se rechaza y no aplica nada")
  void sinPermiso() throws Exception {
    mvc.perform(
            patch("/api/v1/products/{id}", producto)
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Otro nombre\"}"))
        .andExpect(status().isForbidden());

    assertThat(nombreDe(producto)).isEqualTo("Ascenso a Oro");
  }

  @Test
  @DisplayName("`CA-PM-119` — corrige alcance e implementación, y el diff registra antes y después")
  void corrigeAlcanceEImplementacion() throws Exception {
    mvc.perform(corregir(producto, "{\"scope\":\"AMBOS\",\"implementation\":\"AUTOMATICA\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("AMBOS"))
        .andExpect(jsonPath("$.implementation").value("AUTOMATICA"));

    // Van del lado CORREGIBLE porque ninguna define qué derecho otorga el
    // producto: una dice dónde se ve y la otra quién lo entrega.
    assertThat(ultimoCambio(producto))
        .contains("scope")
        .contains("TIENDA")
        .contains("AMBOS")
        .contains("implementation")
        .contains("AUTOMATICA");
  }

  @Test
  @DisplayName("`CA-PM-120` — el nulo explícito NO las vacía: se rechaza, al revés que el icono")
  void nulaExplicitaSeRechaza() throws Exception {
    mvc.perform(corregir(producto, "{\"scope\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("scope"));

    mvc.perform(corregir(producto, "{\"implementation\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("implementation"));
  }

  @Test
  @DisplayName("`CA-PM-121` — el valor fuera de dominio se rechaza y NO aplica lo demás")
  void fueraDeDominioNoAplicaNada() throws Exception {
    mvc.perform(corregir(producto, "{\"name\":\"Otro nombre\",\"scope\":\"TIENDAS\"}"))
        .andExpect(status().isBadRequest());

    // `CA-PM-034` con otro disparador: ningún rechazo deja el producto a medias.
    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM products WHERE id = CAST(? AS uuid)",
                String.class,
                producto.toString()))
        .isEqualTo("Ascenso a Oro");
  }

  @Test
  @DisplayName("`CA-PM-122` — enviar el MISMO alcance no cambia nada y no registra evento")
  void elMismoValorNoEsCambio() throws Exception {
    long antes = eventosDe(producto);

    mvc.perform(corregir(producto, "{\"scope\":\"TIENDA\",\"implementation\":\"MANUAL\"}"))
        .andExpect(status().isOk());

    assertThat(eventosDe(producto)).as("`audit_change_log` no debía crecer").isEqualTo(antes);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder corregir(UUID id, String cuerpo) {
    return patch("/api/v1/products/{id}", id)
        .with(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:update");
  }

  private String nombreDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT name FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  private java.math.BigDecimal precioDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT price FROM products WHERE id = CAST(? AS uuid)",
        java.math.BigDecimal.class,
        id.toString());
  }

  private java.math.BigDecimal precioDeCompraDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT purchase_price FROM products WHERE id = CAST(? AS uuid)",
        java.math.BigDecimal.class,
        id.toString());
  }

  private String descripcionDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT description FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  private Integer vigenciaDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT validity_days FROM products WHERE id = CAST(? AS uuid)",
        Integer.class,
        id.toString());
  }

  private String estadoDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT status FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  private String ultimoCambio(UUID id) {
    return jdbc.queryForObject(
        "SELECT changes::text FROM audit_change_log WHERE entity_id = CAST(? AS uuid)"
            + " AND action = 'UPDATE' ORDER BY occurred_at DESC LIMIT 1",
        String.class,
        id.toString());
  }

  private long eventosDe(UUID id) {
    Long filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity_id = CAST(? AS uuid)",
            Long.class,
            id.toString());
    return filas == null ? 0 : filas;
  }

  private UUID membresia(String codigo, String nombre, int nivel) {
    return membresia(codigo, nombre, nivel, null);
  }

  private UUID membresia(String codigo, String nombre, int nivel, UUID superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (CAST(? AS uuid), ?, ?, CAST(? AS uuid), ?,"
            + " upper(lpad(to_hex(? * 4919), 6, '0')))",
        id.toString(),
        codigo,
        nombre,
        superior == null ? null : superior.toString(),
        nivel,
        nivel);
    return id;
  }

  /** Una moneda activa de dos decimales, distinta de la de por defecto. */
  private String monedaAlterna() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (CAST(? AS uuid), 'EUR', 'Euro', 'E', 2, false, true)",
        id.toString());
    return id.toString();
  }

  private String monedaSinDecimales() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (CAST(? AS uuid), 'COP', 'Peso colombiano', '$', 0, false, true)",
        id.toString());
    return id.toString();
  }

  private String crearMonedaInactiva() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (CAST(? AS uuid), 'GBP', 'Libra', 'L', 2, false, false)",
        id.toString());
    return id.toString();
  }

  private UUID upgrade(
      String codigo, String nombre, UUID destino, String descripcion, Integer vigencia) {
    return crear(codigo, "UPGRADE_MEMBRESIA", nombre, descripcion, destino, vigencia);
  }

  private UUID bot(String codigo, String nombre, String descripcion) {
    return crear(codigo, "BOT", nombre, descripcion, null, null);
  }

  private UUID crear(
      String codigo,
      String tipo,
      String nombre,
      String descripcion,
      UUID destino,
      Integer vigencia) {

    UUID id = UUID.randomUUID();
    // Origen y destino VIAJAN JUNTOS: un upgrade declara los dos
    // (`RN-PM-002`) y un bot no declara ninguno. Por eso el origen se
    // deriva del destino en lugar de ser un parametro mas — nunca puede
    // quedar uno sin el otro, que es lo que `ck_products_type_target` mira.
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES ('TIENDA', 'MANUAL', CAST(? AS uuid), ?, ?, ?, CAST(? AS text),"
            + " CAST(? AS uuid), CAST(? AS uuid), 49.99,"
            + " CAST(? AS uuid), CAST(? AS integer), 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        tipo,
        nombre,
        descripcion,
        destino == null ? null : free.toString(),
        destino == null ? null : destino.toString(),
        USD,
        vigencia,
        BASE,
        BASE);
    return id;
  }
}
