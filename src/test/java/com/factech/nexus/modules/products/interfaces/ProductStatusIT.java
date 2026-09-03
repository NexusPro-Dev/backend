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

/**
 * Publicar y retirar de la oferta (`RF-PM-005` · `T-08`).
 *
 * <p>Cubre los criterios de `spec.md` §12. El que decide si el requerimiento está bien hecho no
 * está aquí sino en {@code ProductStatusConcurrencyIT}: estas pruebas pasan igual con una
 * implementación que solo compruebe antes de escribir, que es exactamente la que `RN-PM-004` no
 * admite.
 */
@AutoConfigureMockMvc
class ProductStatusIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;
  private UUID plata;
  private UUID free;

  @BeforeEach
  void sembrarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    oro = membresia("ORO", "Oro", 1);
    plata = membresia("PLATA", "Plata", 2, oro);
    // El SUELO de la cadena: el origen de todo upgrade que se siembre aqui.
    free = membresia("FREE", "Free", 3, plata);
  }

  @AfterEach
  void vaciarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName("`CA-PM-041` — activa un producto inactivo")
  void activa() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    mvc.perform(cambiar(bot, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"));

    assertThat(estadoDe(bot)).isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName("`CA-PM-040` — desactiva un producto activo, y sigue en el catálogo")
  void desactiva() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");
    mvc.perform(cambiar(bot, "ACTIVO")).andExpect(status().isOk());

    mvc.perform(cambiar(bot, "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        // Desactivar NO es retirar: la fila sigue viva y sin marca de retiro.
        .andExpect(jsonPath("$.deletedAt").doesNotExist());

    assertThat(sigueEnElCatalogo(bot)).isTrue();
  }

  @Test
  @DisplayName("el estado se admite en cualquier caja: `activo` es la misma petición")
  void elEstadoEnCualquierCaja() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    mvc.perform(cambiar(bot, "activo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"));
  }

  @Test
  @DisplayName("`VAL-002` — un estado fuera del dominio se rechaza enumerando los admitidos")
  void estadoInvalido() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    mvc.perform(cambiar(bot, "PUBLICADO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("ACTIVO")));

    // Y el estado obligatorio, que es otra validación y otro camino.
    mvc.perform(
            patch("/api/v1/products/{id}/status", bot)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-072` — no se publica un producto SIN descripción")
  void sinDescripcionNoSePublica() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", null);

    mvc.perform(cambiar(bot, "ACTIVO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"))
        .andExpect(jsonPath("$.errors[0].field").value("description"));

    assertThat(estadoDe(bot)).isEqualTo("INACTIVO");
  }

  @Test
  @DisplayName("`CA-PM-072` — y se publica en cuanto la descripción está puesta")
  void conDescripcionSiSePublica() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", null);
    mvc.perform(cambiar(bot, "ACTIVO")).andExpect(status().isBadRequest());

    // La descripción la pondrá `RF-PM-004`, que todavía no existe; lo que esta
    // prueba comprueba es que la regla NO es una puerta cerrada para siempre.
    jdbc.update(
        "UPDATE products SET description = 'Atención prioritaria.' WHERE id = CAST(? AS uuid)",
        bot.toString());

    mvc.perform(cambiar(bot, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"));
  }

  @Test
  @DisplayName("`CA-PM-073` — SÍ se desactiva un producto sin descripción")
  void sinDescripcionSiSeDesactiva() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", null);
    jdbc.update("UPDATE products SET status = 'ACTIVO' WHERE id = CAST(? AS uuid)", bot.toString());

    // La regla acota lo que se OFRECE, no lo que se retira. Exigir descripción
    // para desactivar dejaría atrapado en la oferta al producto peor
    // documentado, que es justo el que más urge quitar.
    mvc.perform(cambiar(bot, "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"));
  }

  @Test
  @DisplayName("`CA-PM-042` — no se activa un upgrade cuyo destino ya está ocupado, y se dice cuál")
  void destinoOcupado() throws Exception {
    UUID primero = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro, "Sube al nivel oro.");
    UUID segundo = upgrade("UPGRADE_ORO_2", "Ascenso a Oro premium", oro, "Otra vía al oro.");
    mvc.perform(cambiar(primero, "ACTIVO")).andExpect(status().isOk());

    mvc.perform(cambiar(segundo, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        // Un `409` que diga solo «ya hay uno» obliga a buscarlo. Lo único
        // accionable es saber CUÁL desactivar.
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("Ascenso a Oro")))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("UPGRADE_ORO")));

    assertThat(estadoDe(segundo)).isEqualTo("INACTIVO");
  }

  @Test
  @DisplayName("`CA-PM-043` — se activa después de desactivar el que ocupaba el destino")
  void seActivaTrasLiberarElDestino() throws Exception {
    UUID primero = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro, "Sube al nivel oro.");
    UUID segundo = upgrade("UPGRADE_ORO_2", "Ascenso a Oro premium", oro, "Otra vía al oro.");
    mvc.perform(cambiar(primero, "ACTIVO")).andExpect(status().isOk());

    mvc.perform(cambiar(primero, "INACTIVO")).andExpect(status().isOk());
    mvc.perform(cambiar(segundo, "ACTIVO")).andExpect(status().isOk());

    assertThat(cuantosActivosHacia(oro)).isEqualTo(1);
  }

  @Test
  @DisplayName("`FA-002` — desactivar NO comprueba el destino: liberar nunca produce conflicto")
  void desactivarNoComprueba() throws Exception {
    UUID primero = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro, "Sube al nivel oro.");
    mvc.perform(cambiar(primero, "ACTIVO")).andExpect(status().isOk());

    mvc.perform(cambiar(primero, "INACTIVO")).andExpect(status().isOk());
    assertThat(cuantosActivosHacia(oro)).isZero();
  }

  @Test
  @DisplayName("dos upgrades hacia destinos DISTINTOS pueden estar activos a la vez")
  void destinosDistintosNoChocan() throws Exception {
    UUID aOro = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro, "Sube al nivel oro.");
    UUID aPlata = upgrade("UPGRADE_PLATA", "Ascenso a Plata", plata, "Sube al nivel plata.");

    mvc.perform(cambiar(aOro, "ACTIVO")).andExpect(status().isOk());
    mvc.perform(cambiar(aPlata, "ACTIVO")).andExpect(status().isOk());

    // La regla es «uno por destino», no «uno en total».
    assertThat(cuantosActivosHacia(oro)).isEqualTo(1);
    assertThat(cuantosActivosHacia(plata)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`RN-PM-004` — mismo destino, orígenes DISTINTOS: los dos pueden estar activos a la vez")
  void mismoDestinoOrigenesDistintosNoChocan() throws Exception {
    // El caso que RN-PM-004 existe para permitir desde el 02-09-2026: dos
    // saltos hacia el mismo nivel no son el mismo producto si parten de
    // orígenes distintos. La unicidad es sobre la PAREJA, no sobre el destino.
    UUID desdePlata =
        upgrade("UPGRADE_ORO_DESDE_PLATA", "Ascenso a Oro", plata, oro, "Desde plata.");
    UUID desdeFree =
        upgrade("UPGRADE_ORO_DESDE_FREE", "Ascenso directo a Oro", free, oro, "Desde free.");

    mvc.perform(cambiar(desdePlata, "ACTIVO")).andExpect(status().isOk());
    mvc.perform(cambiar(desdeFree, "ACTIVO")).andExpect(status().isOk());

    assertThat(cuantosActivosHacia(oro)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`RN-PM-004` — mismo origen Y mismo destino: el segundo sí choca, aunque el primero"
          + " tenga otro origen activo hacia el mismo destino")
  void mismaParejaChocaAunqueOtroOrigenYaEsteActivo() throws Exception {
    UUID desdePlata =
        upgrade("UPGRADE_ORO_DESDE_PLATA", "Ascenso a Oro", plata, oro, "Desde plata.");
    UUID desdeFreeUno =
        upgrade("UPGRADE_ORO_DESDE_FREE", "Ascenso directo a Oro", free, oro, "Desde free.");
    UUID desdeFreeDos =
        upgrade("UPGRADE_ORO_DESDE_FREE_2", "Otro ascenso a Oro", free, oro, "También desde free.");

    mvc.perform(cambiar(desdePlata, "ACTIVO")).andExpect(status().isOk());
    mvc.perform(cambiar(desdeFreeUno, "ACTIVO")).andExpect(status().isOk());

    // El conflicto es con `desdeFreeUno` —misma pareja—, no con `desdePlata`.
    mvc.perform(cambiar(desdeFreeDos, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        // Entre paréntesis y no como subcadena suelta: el código del segundo
        // ("...FREE_2") contiene al del primero como prefijo, y una
        // comprobación menos precisa pasaría igual señalando al producto
        // equivocado.
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("(UPGRADE_ORO_DESDE_FREE)")));
  }

  @Test
  @DisplayName("varios BOTS activos a la vez: la regla del destino no les alcanza")
  void losBotsNoCompiten() throws Exception {
    UUID uno = bot("SOPORTE", "Soporte", "Atención prioritaria.");
    UUID otro = bot("ASESORIA", "Asesoría", "Una hora con un asesor.");

    mvc.perform(cambiar(uno, "ACTIVO")).andExpect(status().isOk());
    mvc.perform(cambiar(otro, "ACTIVO")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-PM-044` — pedir el estado que ya tiene es `200`, sin cambio y SIN evento")
  void sinCambioNoRegistraEvento() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");
    long antes = eventosDe(bot);

    // Nace INACTIVO: pedir INACTIVO no cambia nada y no es un error.
    mvc.perform(cambiar(bot, "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"));

    assertThat(eventosDe(bot)).as("no debía registrarse ningún evento").isEqualTo(antes);
  }

  @Test
  @DisplayName("`CA-PM-072` — y sin cambio TAMPOCO exige descripción: no hay nada que publicar")
  void sinCambioNoExigeDescripcion() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", null);
    jdbc.update("UPDATE products SET status = 'ACTIVO' WHERE id = CAST(? AS uuid)", bot.toString());

    // Ya está activo: pedir ACTIVO no publica nada, de modo que exigir la
    // descripción aquí rechazaría una petición que no cambia el estado.
    mvc.perform(cambiar(bot, "ACTIVO")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-PM-046` — el cambio se registra con su valor anterior y el nuevo")
  void registraElCambio() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    mvc.perform(cambiar(bot, "ACTIVO")).andExpect(status().isOk());

    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity_id = CAST(? AS uuid)"
                + " ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            bot.toString());

    assertThat(cambios).contains("status").contains("INACTIVO").contains("ACTIVO");
  }

  @Test
  @DisplayName("`CA-PM-045` — no se cambia el estado de un producto retirado")
  void elRetiradoNoVuelve() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");
    jdbc.update(
        "UPDATE products SET deleted_at = ? WHERE id = CAST(? AS uuid)",
        BASE.plusDays(1),
        bot.toString());

    // Un producto retirado no vuelve a la venta cambiándole el estado, y se
    // responde lo mismo que ante uno inexistente.
    mvc.perform(cambiar(bot, "ACTIVO")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`EX-001` — un identificador inexistente es `404`")
  void inexistente() throws Exception {
    mvc.perform(cambiar(UUID.randomUUID(), "ACTIVO")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`VAL-001` — el identificador no canónico es `400`, no `404`")
  void identificadorNoCanonico() throws Exception {
    mvc.perform(
            patch("/api/v1/products/{id}/status", "1-1-1-1-1")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVO\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-047` — sin `products:update`, la operación se rechaza")
  void sinPermiso() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    // `products:read` no basta: leer y publicar son decisiones distintas.
    mvc.perform(
            patch("/api/v1/products/{id}/status", bot)
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVO\"}"))
        .andExpect(status().isForbidden());

    assertThat(estadoDe(bot)).isEqualTo("INACTIVO");
  }

  @Test
  @DisplayName("`CA-PM-085` — no se exige motivo ni para activar ni para desactivar")
  void sinMotivo() throws Exception {
    UUID bot = bot("SOPORTE", "Soporte", "Atención prioritaria.");

    // El cuerpo lleva SOLO el estado. El Art. V.13 exige motivo en las
    // eliminaciones, y cambiar de estado no lo es.
    mvc.perform(cambiar(bot, "ACTIVO")).andExpect(status().isOk());
    mvc.perform(cambiar(bot, "INACTIVO")).andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder cambiar(UUID id, String estado) {
    return patch("/api/v1/products/{id}/status", id)
        .with(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"" + estado + "\"}");
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:update");
  }

  private String estadoDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT status FROM products WHERE id = CAST(? AS uuid)", String.class, id.toString());
  }

  private boolean sigueEnElCatalogo(UUID id) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM products WHERE id = CAST(? AS uuid) AND deleted_at IS NULL",
            Integer.class,
            id.toString());
    return filas != null && filas == 1;
  }

  private int cuantosActivosHacia(UUID destino) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM products WHERE target_membership_id = CAST(? AS uuid)"
                + " AND status = 'ACTIVO' AND deleted_at IS NULL",
            Integer.class,
            destino.toString());
    return filas == null ? 0 : filas;
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

  private UUID bot(String codigo, String nombre, String descripcion) {
    return producto(codigo, "BOT", nombre, descripcion, null, null);
  }

  // El origen por defecto es `free`: la mayoría de las pruebas de este
  // archivo no necesitan variarlo, solo que exista.
  private UUID upgrade(String codigo, String nombre, UUID destino, String descripcion) {
    return upgrade(codigo, nombre, free, destino, descripcion);
  }

  private UUID upgrade(
      String codigo, String nombre, UUID origen, UUID destino, String descripcion) {
    return producto(codigo, "UPGRADE_MEMBRESIA", nombre, descripcion, origen, destino);
  }

  private UUID producto(
      String codigo, String tipo, String nombre, String descripcion, UUID origen, UUID destino) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, CAST(? AS text),"
            + " CAST(? AS uuid), CAST(? AS uuid), 10.00,"
            + " CAST(? AS uuid), NULL, 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        tipo,
        nombre,
        descripcion,
        origen == null ? null : origen.toString(),
        destino == null ? null : destino.toString(),
        USD,
        BASE,
        BASE);
    return id;
  }
}
