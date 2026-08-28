package com.factech.nexus.modules.system.countries.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Criterios de aceptación del catálogo de países (`RF-SP-020`, `RF-SP-021`, `RF-SP-022`).
 *
 * <p>El catálogo se vacía antes de cada prueba: nace vacío —no hay migración de siembra— y el orden
 * de ejecución no debe decidir el resultado.
 */
@AutoConfigureMockMvc
class CountriesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  @BeforeEach
  void vaciarElCatalogo() {
    jdbc.update("DELETE FROM countries");
  }

  // ---------------------------------------------------------------------------
  // Alta — RF-SP-020
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-134 y CA-SP-171 — registra el país y queda activo, sin recibir el estado")
  void altaValida() throws Exception {
    mvc.perform(alta("PAN", "Panamá"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.code").value("PAN"))
        .andExpect(jsonPath("$.name").value("Panamá"))
        .andExpect(jsonPath("$.isActive").value(true))
        // El actor no vive en la tabla de negocio (Art. V.7).
        .andExpect(jsonPath("$.createdBy").doesNotExist());
  }

  @Test
  @DisplayName("el alta NO devuelve Location: no existe endpoint de detalle al que apuntar")
  void sinLocation() throws Exception {
    // Una cabecera que existe para llevar al cliente al recurso creado y lo
    // lleva a una URL que devuelve 404 es peor que no ponerla.
    mvc.perform(alta("COL", "Colombia"))
        .andExpect(status().isCreated())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .doesNotExist("Location"));
  }

  @Test
  @DisplayName("el código se normaliza a mayúsculas y se recorta: co, ' CO' y CO son el mismo país")
  void codigoNormalizado() throws Exception {
    // Diferencia deliberada con el código de un ROL, que se rechaza en
    // minúsculas: allí el actor lo inventa; aquí lo fija ISO 3166-1.
    mvc.perform(alta("col", "Colombia"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("COL"));

    // Y por tanto el segundo intento es un duplicado, no un país nuevo.
    mvc.perform(alta(" COL ", "Otra Colombia")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("CA-SP-135 — un código que no sean tres letras se rechaza con 400")
  void formatoDelCodigo() throws Exception {
    for (String malo : new String[] {"CO", "COLO", "C1X", "CO-", "   "}) {
      mvc.perform(alta(malo, "País " + malo)).andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("CA-SP-136 — código o nombre ya presentes devuelven 409 y dicen cuál")
  void duplicado() throws Exception {
    mvc.perform(alta("PAN", "Panamá")).andExpect(status().isCreated());

    mvc.perform(alta("PAN", "Otro nombre"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));

    mvc.perform(alta("PAX", "Panamá"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName(
      "el nombre duplicado se detecta ignorando acentos y mayúsculas, y el mensaje lo dice")
  void duplicadoPorFormaNormalizada() throws Exception {
    mvc.perform(alta("PAN", "Panamá")).andExpect(status().isCreated());

    // `RN-SP-009` no admite edición: `Panamá` y `Panama` conviviendo serían dos
    // opciones indistinguibles en cada selector, para siempre.
    mvc.perform(alta("PAX", "Panama"))
        .andExpect(status().isConflict())
        // El mensaje incluye el nombre ENVIADO, porque el rechazo se dispara
        // contra una fila cuyo nombre no es idéntico: sin esa precisión el actor
        // vería rechazado un «Panama» que no encuentra en ninguna parte.
        .andExpect(jsonPath("$.errors[0].message", org.hamcrest.Matchers.containsString("Panama")));

    mvc.perform(alta("PRY", "PANAMÁ")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("el nombre se recorta por fuera pero conserva sus espacios interiores")
  void recorteDelNombre() throws Exception {
    mvc.perform(alta("CRI", "  Costa Rica  "))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Costa Rica"));
  }

  @Test
  @DisplayName("CA-SP-171 — enviar el estado en el alta devuelve 400")
  void estadoNoAdmitidoEnElAlta() throws Exception {
    mvc.perform(
            post("/api/v1/countries")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"PA\",\"name\":\"Panamá\",\"isActive\":false}"))
        .andExpect(status().isBadRequest());

    assertThat(existe("PAN")).isFalse();
  }

  @Test
  @DisplayName("CA-SP-138 — el alta deja su evento en la auditoría de cambios")
  void auditoriaDelAlta() throws Exception {
    UUID correlacion = UUID.randomUUID();

    mvc.perform(alta("PAN", "Panamá").header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isCreated());

    String changes =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE correlation_id = ? AND entity = 'countries' AND action = 'CREATE'
            """,
            String.class,
            correlacion);
    assertThat(changes)
        .contains("\"code\": \"PAN\"")
        .contains("Panam")
        .contains("\"is_active\": true");
  }

  @Test
  @DisplayName("CA-SP-139 — sin el permiso de creación se responde 403")
  void sinPermisoDeAlta() throws Exception {
    mvc.perform(
            post("/api/v1/countries")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "countries:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"PA\",\"name\":\"Panamá\"}"))
        .andExpect(status().isForbidden());

    assertThat(existe("PAN")).isFalse();
  }

  @Test
  @DisplayName("CA-SP-137 — sin edición ni eliminación, y cada ruta responde lo que le corresponde")
  void catalogoInmutable() throws Exception {
    String pa = crear("PAN", "Panamá");

    // Sobre un país concreto: 404, porque esa ruta no está mapeada para ningún
    // método — no existe endpoint de detalle de país en el módulo.
    for (MockHttpServletRequestBuilder peticion :
        new MockHttpServletRequestBuilder[] {
          put("/api/v1/countries/" + pa),
          patch("/api/v1/countries/" + pa),
          delete("/api/v1/countries/" + pa)
        }) {
      mvc.perform(
              peticion.with(administrador()).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isNotFound());
    }

    // Sobre la colección: 405, porque sí está mapeada por el alta y el listado.
    mvc.perform(
            put("/api/v1/countries")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());

    mvc.perform(delete("/api/v1/countries").with(administrador()))
        .andExpect(status().isMethodNotAllowed());
  }

  // ---------------------------------------------------------------------------
  // Listado — RF-SP-021
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-140 — el catálogo va sin paginar y ordenado alfabéticamente por NOMBRE")
  void listadoOrdenado() throws Exception {
    crear("PER", "Perú");
    crear("PAN", "Panamá");
    crear("COL", "Colombia");

    mvc.perform(get("/api/v1/countries").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[0].name").value("Colombia"))
        .andExpect(jsonPath("$.content[1].name").value("Panamá"))
        .andExpect(jsonPath("$.content[2].name").value("Perú"))
        .andExpect(jsonPath("$.totalElements").doesNotExist())
        .andExpect(jsonPath("$.totalPages").doesNotExist());
  }

  @Test
  @DisplayName("el orden sigue la intercalación del español, no el de bytes")
  void ordenSegunElIdioma() throws Exception {
    // ESTA es la prueba que justifica declarar COLLATE en la columna. Con la
    // intercalación `C` —orden de bytes UTF-8— «Panamá» iría DESPUÉS de «Perú»,
    // porque la `á` tiene un valor mayor que cualquier letra sin acento, y el
    // selector parecería roto.
    crear("PER", "Perú");
    crear("PAN", "Panamá");

    mvc.perform(get("/api/v1/countries").with(lector()))
        .andExpect(jsonPath("$.content[0].name").value("Panamá"))
        .andExpect(jsonPath("$.content[1].name").value("Perú"));
  }

  @Test
  @DisplayName("CA-SP-141 — la búsqueda filtra por código y por nombre, ignorando acentos")
  void busqueda() throws Exception {
    crear("PAN", "Panamá");
    crear("COL", "Colombia");

    mvc.perform(get("/api/v1/countries?search=panama").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("PAN"));

    mvc.perform(get("/api/v1/countries?search=co").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("COL"));

    // En blanco equivale a ausente.
    mvc.perform(get("/api/v1/countries?search=   ").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName("CA-SP-142 — sin coincidencias devuelve la colección vacía, no un error")
  void sinCoincidencias() throws Exception {
    crear("PAN", "Panamá");

    mvc.perform(get("/api/v1/countries?search=noexiste").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(0));

    // Un término con comodín no devuelve el catálogo entero.
    mvc.perform(get("/api/v1/countries?search=%25").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("CA-SP-172 — los inactivos no aparecen salvo que se pidan, y entonces se AÑADEN")
  void inactivosBajoPeticion() throws Exception {
    crear("PAN", "Panamá");
    String co = crear("COL", "Colombia");
    mvc.perform(cambioDeEstado(co, false)).andExpect(status().isOk());

    mvc.perform(get("/api/v1/countries").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("PAN"));

    mvc.perform(get("/api/v1/countries?includeInactive=true").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName("la búsqueda y el estado son independientes")
  void busquedaYEstadoIndependientes() throws Exception {
    String pa = crear("PAN", "Panamá");
    crear("PRY", "Paraguay");
    mvc.perform(cambioDeEstado(pa, false)).andExpect(status().isOk());

    mvc.perform(get("/api/v1/countries?search=pa").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("PRY"));

    mvc.perform(get("/api/v1/countries?search=pa&includeInactive=true").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName("CA-SP-143 — sin el permiso de lectura se responde 403")
  void sinPermisoDeLectura() throws Exception {
    mvc.perform(
            get("/api/v1/countries")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "roles:read")))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Cambio de estado — RF-SP-022
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-178, CA-SP-179 y CA-SP-180 — se desactiva y reactiva sin tocar la definición")
  void desactivarYReactivar() throws Exception {
    String pa = crear("PAN", "Panamá");

    mvc.perform(cambioDeEstado(pa, false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(false))
        // El código y el nombre no cambian.
        .andExpect(jsonPath("$.code").value("PAN"))
        .andExpect(jsonPath("$.name").value("Panamá"));

    mvc.perform(get("/api/v1/countries").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(0));

    mvc.perform(cambioDeEstado(pa, true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(true));
  }

  @Test
  @DisplayName("CA-SP-181 — desactivar no borra: la fila sigue resolviendo su definición")
  void desactivarNoEsCorregir() throws Exception {
    String pa = crear("PAN", "Panamá");
    mvc.perform(cambioDeEstado(pa, false)).andExpect(status().isOk());

    // Quien ya referenciaba el país por su identificador lo sigue resolviendo.
    mvc.perform(get("/api/v1/countries?includeInactive=true").with(lector()))
        .andExpect(jsonPath("$.content[0].id").value(pa))
        .andExpect(jsonPath("$.content[0].name").value("Panamá"));
  }

  @Test
  @DisplayName("CA-SP-182 — pedir el estado que ya tiene no registra evento")
  void sinCambioSinEvento() throws Exception {
    String pa = crear("PAN", "Panamá");
    UUID correlacion = UUID.randomUUID();

    mvc.perform(cambioDeEstado(pa, true).header("X-Correlation-Id", correlacion.toString()))
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
  @DisplayName("CA-SP-183 — el cambio va a la auditoría de cambios y NO a la de seguridad")
  void auditoriaDelCambio() throws Exception {
    String pa = crear("PAN", "Panamá");
    UUID correlacion = UUID.randomUUID();

    mvc.perform(cambioDeEstado(pa, false).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());

    String changes =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE correlation_id = ? AND action = 'UPDATE' AND entity = 'countries'
            """,
            String.class,
            correlacion);
    assertThat(changes)
        .contains("is_active")
        .contains("\"before\": true")
        .contains("\"after\": false");
    // Solo `is_active`.
    assertThat(changes).doesNotContain("code").doesNotContain("updated_at");

    Integer seguridad =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(seguridad).as("un cambio de catálogo no es un evento de control de acceso").isZero();
  }

  @Test
  @DisplayName("CA-SP-338 — la operación no admite motivo ni ningún otro campo")
  void sinMotivo() throws Exception {
    String pa = crear("PAN", "Panamá");

    for (String cuerpo :
        new String[] {
          "{\"isActive\":false,\"reason\":\"porque sí\"}",
          "{\"isActive\":false,\"code\":\"XX\"}",
          "{\"isActive\":false,\"name\":\"Otro\"}"
        }) {
      mvc.perform(
              patch("/api/v1/countries/" + pa + "/status")
                  .with(administrador())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(cuerpo))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("el estado destino es obligatorio")
  void estadoObligatorio() throws Exception {
    String pa = crear("PAN", "Panamá");

    mvc.perform(
            patch("/api/v1/countries/" + pa + "/status")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.code == 'VAL-001')]").exists());
  }

  @Test
  @DisplayName("un país inexistente devuelve 404, no 422")
  void paisInexistente() throws Exception {
    mvc.perform(cambioDeEstado(UUID.randomUUID().toString(), false))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/no-encontrado"));
  }

  @Test
  @DisplayName("CA-SP-184 — sin el permiso de modificación se responde 403")
  void sinPermisoDeModificacion() throws Exception {
    String pa = crear("PAN", "Panamá");

    mvc.perform(
            patch("/api/v1/countries/" + pa + "/status")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "countries:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\":false}"))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Esquema
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("ck_countries_code_format y ck_countries_name_not_blank protegen el INSERT directo")
  void garantiasDelEsquema() {
    // El catálogo no se puede corregir después: sin estos CHECK, un INSERT
    // directo mete basura permanente.
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertarDirecto("c1x", "País"))
        .isInstanceOf(DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertarDirecto("XXX", "   "))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("uq_countries_name es funcional: rechaza el duplicado normalizado en base de datos")
  void unicidadNormalizadaEnElEsquema() {
    insertarDirecto("PAN", "Panamá");

    // La garantía no depende de que el servicio recuerde comprobarlo, que es lo
    // que resuelve el alta concurrente sin convertirla en un 500.
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertarDirecto("PAX", "panama"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("ix_countries_busqueda existe y es un GIN de trigramas")
  void indiceDeBusqueda() {
    // Con pocas filas el planificador preferirá el recorrido secuencial, y eso
    // no es un defecto: el índice existe para cuando el catálogo se pueble de
    // verdad. Lo que se comprueba aquí es que está declarado.
    String definicion =
        jdbc.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_countries_busqueda'",
            String.class);

    assertThat(definicion).contains("gin").contains("f_unaccent").contains("gin_trgm_ops");
  }

  @Test
  @DisplayName("la columna del nombre declara la intercalación del español")
  void intercalacionDeclarada() {
    // Si desapareciera, el listado seguiría funcionando y ordenaría mal, que es
    // la clase de defecto que nadie nota hasta que alguien mira el selector.
    String intercalacion =
        jdbc.queryForObject(
            """
            SELECT c.collname
              FROM pg_attribute a
              JOIN pg_class t ON t.oid = a.attrelid
              JOIN pg_collation c ON c.oid = a.attcollation
             WHERE t.relname = 'countries' AND a.attname = 'name'
            """,
            String.class);

    assertThat(intercalacion).isEqualTo("es-x-icu");
  }

  // ---------------------------------------------------------------------------

  private RequestPostProcessor lector() {
    return user(UUID.randomUUID().toString()).authorities(() -> "countries:read");
  }

  private RequestPostProcessor administrador() {
    return user(UUID.randomUUID().toString())
        .authorities(() -> "countries:read", () -> "countries:create", () -> "countries:update");
  }

  private MockHttpServletRequestBuilder alta(String code, String name) {
    return post("/api/v1/countries")
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}");
  }

  private MockHttpServletRequestBuilder cambioDeEstado(String id, boolean activo) {
    return patch("/api/v1/countries/" + id + "/status")
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"isActive\":" + activo + "}");
  }

  private String crear(String code, String name) throws Exception {
    String cuerpo =
        mvc.perform(alta(code, name))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(cuerpo).get("id").asText();
  }

  private void insertarDirecto(String code, String name) {
    jdbc.update(
        "INSERT INTO countries (id, code, name) VALUES (gen_random_uuid(), ?, ?)", code, name);
  }

  private boolean existe(String code) {
    Integer filas =
        jdbc.queryForObject("SELECT count(*) FROM countries WHERE code = ?", Integer.class, code);
    return filas != null && filas > 0;
  }
}
