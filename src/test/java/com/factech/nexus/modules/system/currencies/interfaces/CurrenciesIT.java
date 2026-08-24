package com.factech.nexus.modules.system.currencies.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Criterios de aceptación del catálogo de monedas (`RF-SP-019`, `RF-SP-023`).
 *
 * <p>La moneda sembrada —`USD`— es la moneda por defecto y <b>no se puede desactivar</b>, de modo
 * que las pruebas del cambio de estado necesitan una segunda moneda. Se inserta por SQL y no por la
 * API, porque no existe endpoint de alta: es exactamente lo que `RN-SP-010` decide y lo que
 * `CA-SP-131` verifica.
 */
@AutoConfigureMockMvc
class CurrenciesIT extends IntegrationTestBase {

  /** Identificador literal de la siembra, estable en todos los entornos. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void dejarSoloLaSembrada() {
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
  }

  // ---------------------------------------------------------------------------
  // Catálogo — RF-SP-019
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-SP-130, CA-SP-132 y CA-SP-168 — el catálogo devuelve la moneda del sistema entera")
  void catalogoCompleto() throws Exception {
    mvc.perform(get("/api/v1/currencies").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(USD))
        .andExpect(jsonPath("$.content[0].code").value("USD"))
        .andExpect(jsonPath("$.content[0].name").value("Dólar estadounidense"))
        .andExpect(jsonPath("$.content[0].symbol").value("$"))
        // El campo más importante de la respuesta: de él depende el redondeo de
        // todo cálculo financiero.
        .andExpect(jsonPath("$.content[0].decimalPlaces").value(2))
        .andExpect(jsonPath("$.content[0].isDefault").value(true))
        .andExpect(jsonPath("$.content[0].isActive").value(true))
        // Sin marcas temporales: `createdAt` diría cuándo se aplicó la migración
        // de siembra, distinto en cada entorno.
        .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist());
  }

  @Test
  @DisplayName("CA-SP-169 — exactamente una moneda está marcada como moneda por defecto")
  void unaSolaPorDefecto() throws Exception {
    insertarMoneda("EUR", "Euro", true);

    mvc.perform(get("/api/v1/currencies?includeInactive=true").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[?(@.isDefault == true)]", org.hamcrest.Matchers.hasSize(1)));
  }

  @Test
  @DisplayName("CA-SP-170 — las inactivas no aparecen salvo que se pidan, y entonces se AÑADEN")
  void inactivasBajoPeticion() throws Exception {
    insertarMoneda("EUR", "Euro", false);

    mvc.perform(get("/api/v1/currencies").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("USD"));

    // `includeInactive` añade, no sustituye: un filtro que ocultara las activas
    // respondería una pregunta que nadie hace.
    mvc.perform(get("/api/v1/currencies?includeInactive=true").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].code").value("EUR"))
        .andExpect(jsonPath("$.content[1].code").value("USD"));
  }

  @Test
  @DisplayName("el orden es por código y el cliente no puede cambiarlo")
  void ordenFijo() throws Exception {
    insertarMoneda("EUR", "Euro", true);
    insertarMoneda("COP", "Peso colombiano", true);

    // Los parámetros de paginación y orden se ignoran: el DTO declara un solo
    // campo, de modo que la garantía no depende de que nadie los envíe.
    mvc.perform(get("/api/v1/currencies?sort=name,desc&page=3&size=1").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[0].code").value("COP"))
        .andExpect(jsonPath("$.content[1].code").value("EUR"))
        .andExpect(jsonPath("$.content[2].code").value("USD"))
        .andExpect(jsonPath("$.totalElements").doesNotExist())
        .andExpect(jsonPath("$.totalPages").doesNotExist());
  }

  @Test
  @DisplayName("el símbolo ausente viaja como null, no omitido")
  void simboloNulo() throws Exception {
    jdbc.update(
        """
        INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)
        VALUES (gen_random_uuid(), 'JPY', 'Yen japonés', NULL, 0, false, true)
        """);

    mvc.perform(get("/api/v1/currencies").with(lector()))
        .andExpect(jsonPath("$.content[0].code").value("JPY"))
        .andExpect(jsonPath("$.content[0].symbol").hasJsonPath())
        .andExpect(jsonPath("$.content[0].symbol").value(nullValue()))
        // Cero decimales es legítimo y distinto de «no se sabe».
        .andExpect(jsonPath("$.content[0].decimalPlaces").value(0));
  }

  @Test
  @DisplayName("CA-SP-131 — no hay alta, edición ni eliminación sobre el catálogo")
  void catalogoInmutablePorApi() throws Exception {
    // La ausencia es la implementación: nadie escribió código que rechace.
    mvc.perform(
            post("/api/v1/currencies")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());

    // Sobre la colección, que sí está mapeada para GET, el verbo no admitido da
    // 405; sobre el recurso individual da 404, porque esa ruta no está mapeada
    // para ningún método en absoluto. Las dos respuestas son correctas y dicen
    // cosas distintas.
    mvc.perform(
            put("/api/v1/currencies")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());

    mvc.perform(delete("/api/v1/currencies/" + USD).with(administrador()))
        .andExpect(status().isNotFound());

    // El recurso completo no está mapeado para ningún método: el estado se
    // cambia sobre el subrecurso /status.
    mvc.perform(
            put("/api/v1/currencies/" + USD)
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());

    mvc.perform(
            patch("/api/v1/currencies/" + USD)
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-SP-133 — sin el permiso de lectura se responde 403")
  void sinPermisoDeLectura() throws Exception {
    mvc.perform(
            get("/api/v1/currencies")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "roles:read")))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Cambio de estado — RF-SP-023
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-185 y CA-SP-187 — se desactiva una moneda no predeterminada y se reactiva")
  void desactivarYReactivar() throws Exception {
    String eur = insertarMoneda("EUR", "Euro", true);

    mvc.perform(cambioDeEstado(eur, false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(false));

    // Deja de aparecer en el listado por defecto.
    mvc.perform(get("/api/v1/currencies").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("USD"));

    mvc.perform(cambioDeEstado(eur, true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(true));
  }

  @Test
  @DisplayName("CA-SP-186 — desactivar la moneda por defecto se rechaza con 409 y RN-SP-010")
  void noSePuedeDesactivarLaPorDefecto() throws Exception {
    mvc.perform(cambioDeEstado(USD, false))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/regla-de-negocio"))
        // El mensaje nombra la moneda y explica la consecuencia, no solo niega.
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("USD")))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("migración")));

    // Y sigue activa.
    mvc.perform(get("/api/v1/currencies").with(lector()))
        .andExpect(jsonPath("$.content[0].isActive").value(true));
  }

  @Test
  @DisplayName("el rechazo por RN-SP-010 se audita como regla de negocio con severidad MEDIA")
  void rechazoAuditado() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(cambioDeEstado(USD, false).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isConflict());

    // No es ALTA: intentar desactivar la moneda por defecto es un error de
    // operación, no un intento de escalada de privilegios.
    Integer filas =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_error_log
             WHERE correlation_id = ? AND error_code = 'RN-SP-010'
               AND error_type = 'BUSINESS_RULE' AND http_status = 409 AND severity = 'MEDIA'
            """,
            Integer.class,
            correlacion);
    assertThat(filas).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-SP-190 — pedir el estado que ya tiene no registra evento")
  void sinCambioSinEvento() throws Exception {
    String eur = insertarMoneda("EUR", "Euro", true);
    UUID correlacion = UUID.randomUUID();

    mvc.perform(cambioDeEstado(eur, true).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(true));

    Integer eventos =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(eventos).as("un cambio sin efecto dejó evento fantasma").isZero();
  }

  @Test
  @DisplayName(
      "CA-SP-339 — el cambio se registra en la auditoría de cambios y NO en la de seguridad")
  void auditoriaDelCambio() throws Exception {
    String eur = insertarMoneda("EUR", "Euro", true);
    UUID correlacion = UUID.randomUUID();

    mvc.perform(cambioDeEstado(eur, false).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    String changes =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE correlation_id = ? AND action = 'UPDATE' AND entity = 'currencies'
            """,
            String.class,
            correlacion);
    assertThat(changes)
        .contains("is_active")
        .contains("\"before\": true")
        .contains("\"after\": false");
    // Solo `is_active`: `updated_at` es consecuencia de la escritura, no un dato
    // que alguien decidiera cambiar.
    assertThat(changes).doesNotContain("updated_at").doesNotContain("code");

    Integer seguridad =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(seguridad).as("un cambio de catálogo no es un evento de control de acceso").isZero();
  }

  @Test
  @DisplayName("CA-SP-188 — la definición no cambia, y un cuerpo con otros campos devuelve 400")
  void definicionIntocable() throws Exception {
    String eur = insertarMoneda("EUR", "Euro", true);

    mvc.perform(cambioDeEstado(eur, false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("EUR"))
        .andExpect(jsonPath("$.name").value("Euro"))
        .andExpect(jsonPath("$.decimalPlaces").value(2))
        .andExpect(jsonPath("$.isDefault").value(false));

    // Sin el rechazo de campos desconocidos, este criterio no comprobaría nada.
    for (String cuerpo :
        new String[] {
          "{\"isActive\":true,\"decimalPlaces\":4}",
          "{\"isActive\":true,\"symbol\":\"X\"}",
          "{\"isActive\":true,\"isDefault\":true}",
          "{\"isActive\":true,\"name\":\"Otro\"}"
        }) {
      mvc.perform(
              patch("/api/v1/currencies/" + eur + "/status")
                  .with(administrador())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(cuerpo))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("CA-SP-340 — la operación no admite motivo")
  void sinMotivo() throws Exception {
    // El Art. V.13 lo exige solo en las eliminaciones, y esto no elimina nada.
    mvc.perform(
            patch("/api/v1/currencies/" + USD + "/status")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\":true,\"reason\":\"porque sí\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("el estado destino es obligatorio y debe ser booleano")
  void estadoObligatorio() throws Exception {
    mvc.perform(
            patch("/api/v1/currencies/" + USD + "/status")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.code == 'VAL-001')]").exists());

    mvc.perform(
            patch("/api/v1/currencies/" + USD + "/status")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\":\"quizá\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("una moneda inexistente devuelve 404 y no se audita")
  void monedaInexistente() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(
            cambioDeEstado(UUID.randomUUID().toString(), false)
                .header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isNotFound());

    // `architecture.md` §6.6.4 deja el 404 fuera, y
    // `ck_audit_error_log_status` lo impediría en el esquema de todos modos.
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_error_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(filas).isZero();
  }

  @Test
  @DisplayName("CA-SP-191 — sin el permiso de modificación se responde 403")
  void sinPermisoDeModificacion() throws Exception {
    mvc.perform(
            patch("/api/v1/currencies/" + USD + "/status")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "currencies:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\":true}"))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Esquema
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("ck_currencies_default_active impide dejar inactiva la moneda por defecto")
  void laGarantiaNoDependeDelCodigo() {
    // La verificación en el dominio existe para dar un mensaje comprensible; la
    // restricción existe para que la garantía no dependa de que alguien la
    // escriba. Protege también contra una migración descuidada, que es el otro
    // camino de escritura de esta tabla.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.update("UPDATE currencies SET is_active = false WHERE is_default"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("uq_currencies_single_default impide una segunda moneda por defecto")
  void unaSolaPorDefectoEnElEsquema() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO currencies (id, code, name, decimal_places, is_default, is_active)
                    VALUES (gen_random_uuid(), 'EUR', 'Euro', 2, true, true)
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("ck_currencies_decimal_places acota entre cero y cuatro")
  void decimalesAcotados() {
    // Sin cota, una errata de siembra produce redondeos silenciosamente
    // erróneos en todo cálculo posterior.
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertarConDecimales("XXA", 5))
        .isInstanceOf(DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertarConDecimales("XXB", -1))
        .isInstanceOf(DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatCode(() -> insertarConDecimales("XXC", 0))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("ck_currencies_code_format exige tres letras mayúsculas")
  void formatoDelCodigo() {
    for (String malo : new String[] {"us", "U5D", "U-D"}) {
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      INSERT INTO currencies (id, code, name, decimal_places)
                      VALUES (gen_random_uuid(), ?, ?, 2)
                      """,
                      malo,
                      "Moneda " + malo))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  @DisplayName("la siembra dejó su fila de auditoría, a diferencia de la de permisos")
  void laSiembraSeAudita() {
    // Una moneda SÍ tiene línea de tiempo: `RF-SP-023` puede desactivarla, y ese
    // evento aparecería en `RF-SP-011` como el segundo capítulo de una historia
    // cuyo primero faltaría.
    Integer filas =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_change_log
             WHERE entity = 'currencies' AND action = 'CREATE' AND entity_id = ?::uuid
               AND actor_id IS NULL AND correlation_id IS NULL AND ip_address IS NULL
            """,
            Integer.class,
            USD);
    assertThat(filas).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private RequestPostProcessor lector() {
    return user(UUID.randomUUID().toString()).authorities(() -> "currencies:read");
  }

  private RequestPostProcessor administrador() {
    return user(UUID.randomUUID().toString())
        .authorities(() -> "currencies:read", () -> "currencies:update");
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder cambioDeEstado(
      String id, boolean activa) {
    return patch("/api/v1/currencies/" + id + "/status")
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"isActive\":" + activa + "}");
  }

  private String insertarMoneda(String code, String name, boolean activa) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)
        VALUES (?, ?, ?, '#', 2, false, ?)
        """,
        id,
        code,
        name,
        activa);
    return id.toString();
  }

  private void insertarConDecimales(String code, int decimales) {
    jdbc.update(
        """
        INSERT INTO currencies (id, code, name, decimal_places)
        VALUES (gen_random_uuid(), ?, ?, ?)
        """,
        code,
        "Moneda " + code,
        decimales);
  }
}
