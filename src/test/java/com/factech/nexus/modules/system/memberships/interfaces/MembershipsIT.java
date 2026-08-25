package com.factech.nexus.modules.system.memberships.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Criterios de aceptación del submódulo de membresías (`RF-SP-016`, `RF-SP-017`, `RF-SP-018`).
 *
 * <p><b>La cadena se vacía antes de cada prueba.</b> Es única y global —`spec.md` §14, pregunta 4—,
 * de modo que sin limpiarla el orden de ejecución decidiría el resultado. Se borra de abajo hacia
 * arriba porque {@code fk_memberships_parent} declara {@code ON DELETE RESTRICT} y no admite
 * diferirse: borrar la cima primero fallaría, que es exactamente lo que esa restricción existe para
 * hacer.
 */
@AutoConfigureMockMvc
class MembershipsIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  @BeforeEach
  void vaciarLaCadena() {
    Integer maximo =
        jdbc.queryForObject("SELECT coalesce(max(level), 0) FROM memberships", Integer.class);
    for (int nivel = maximo == null ? 0 : maximo; nivel >= 1; nivel--) {
      jdbc.update("DELETE FROM memberships WHERE level = ?", nivel);
    }
  }

  // ---------------------------------------------------------------------------
  // Alta — RF-SP-016
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-111 — la primera membresía queda como superior, nivel 1 y sin vecinos")
  void primeraMembresia() throws Exception {
    mvc.perform(alta("ORO", "Oro", null))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/memberships/")))
        .andExpect(jsonPath("$.level").value(1))
        .andExpect(jsonPath("$.parentMembershipId").hasJsonPath())
        .andExpect(jsonPath("$.parentMembershipId").value(nullValue()))
        .andExpect(jsonPath("$.childMembershipId").hasJsonPath())
        .andExpect(jsonPath("$.childMembershipId").value(nullValue()));
  }

  @Test
  @DisplayName("CA-SP-113 — sin hija indicada, la membresía va al extremo inferior")
  void extremoInferior() throws Exception {
    String oro = crear("ORO", "Oro", null);

    mvc.perform(alta("PLATA", "Plata", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.level").value(2))
        .andExpect(jsonPath("$.parentMembershipId").value(oro))
        .andExpect(jsonPath("$.childMembershipId").hasJsonPath())
        .andExpect(jsonPath("$.childMembershipId").value(nullValue()));
  }

  @Test
  @DisplayName(
      "CA-SP-112 y CA-SP-115 — insertar en medio reordena la cadena y recalcula los niveles")
  void insercionEnMedio() throws Exception {
    String oro = crear("ORO", "Oro", null);
    String bronce = crear("BRONCE", "Bronce", null);

    mvc.perform(alta("PLATA", "Plata", bronce))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.level").value(2))
        .andExpect(jsonPath("$.parentMembershipId").value(oro))
        .andExpect(jsonPath("$.childMembershipId").value(bronce));

    // BRONCE bajó de 2 a 3 y ahora cuelga de PLATA.
    mvc.perform(get("/api/v1/memberships").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].code").value("ORO"))
        .andExpect(jsonPath("$.content[1].code").value("PLATA"))
        .andExpect(jsonPath("$.content[2].code").value("BRONCE"))
        .andExpect(jsonPath("$.content[2].level").value(3));
  }

  @Test
  @DisplayName("insertar por encima de la superior convierte a la nueva en la cima")
  void porEncimaDeLaCima() throws Exception {
    String oro = crear("ORO", "Oro", null);

    mvc.perform(alta("PLATINO", "Platino", oro))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.level").value(1))
        .andExpect(jsonPath("$.parentMembershipId").hasJsonPath())
        .andExpect(jsonPath("$.parentMembershipId").value(nullValue()))
        .andExpect(jsonPath("$.childMembershipId").value(oro));
  }

  @Test
  @DisplayName("CA-SP-114 — tras insertar, cada membresía sigue teniendo como mucho una hija")
  void sinBifurcacion() throws Exception {
    crear("ORO", "Oro", null);
    String bronce = crear("BRONCE", "Bronce", null);
    crear("PLATA", "Plata", bronce);

    // La restricción del esquema lo garantiza; esto comprueba que el estado
    // final la respeta y que no quedó ninguna superior duplicada.
    Integer superioresDuplicadas =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM (
              SELECT parent_membership_id FROM memberships
               GROUP BY parent_membership_id HAVING count(*) > 1
            ) AS bifurcaciones
            """,
            Integer.class);
    assertThat(superioresDuplicadas).isZero();

    Integer cimas =
        jdbc.queryForObject(
            "SELECT count(*) FROM memberships WHERE parent_membership_id IS NULL", Integer.class);
    assertThat(cimas).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-SP-116 — código o nombre ya en uso devuelven 409 y dicen cuál")
  void duplicado() throws Exception {
    crear("ORO", "Oro", null);

    mvc.perform(alta("ORO", "Otro nombre", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));

    mvc.perform(alta("OTRO_CODIGO", "Oro", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  @DisplayName("CA-SP-348 — el nombre que solo difiere en mayúsculas o acentos se rechaza")
  void nombreEquivalente() throws Exception {
    crear("PLATA", "Plata", null);

    // `RN-SP-008` hace la membresía inmutable: si estos convivieran, lo harían
    // para siempre y sin corrección posible por la API.
    mvc.perform(alta("PLATA_DOS", "plata", null)).andExpect(status().isConflict());
    mvc.perform(alta("PLATA_TRES", "PLÁTA", null)).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("CA-SP-347 — el código con formato inválido devuelve 400 con VAL-006")
  void formatoDelCodigo() throws Exception {
    mvc.perform(alta("codigo-malo", "Código malo", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.code == 'VAL-006')]").exists());
  }

  @Test
  @DisplayName("CA-SP-117 — una hija inexistente devuelve 422 con EX-002, no 404")
  void hijaInexistente() throws Exception {
    crear("ORO", "Oro", null);

    mvc.perform(alta("PLATA", "Plata", UUID.randomUUID().toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.type").value("https://nexus.factech.co/errors/entidad-no-procesable"))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].field").value("childMembershipId"));
  }

  @Test
  @DisplayName("enviar `level` o `parentMembershipId` devuelve 400: la posición no se fuerza")
  void posicionNoForzable() throws Exception {
    mvc.perform(
            post("/api/v1/memberships")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"ORO","name":"Oro","level":1}
                    """))
        .andExpect(status().isBadRequest());

    mvc.perform(
            post("/api/v1/memberships")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"ORO","name":"Oro","parentMembershipId":"%s"}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest());

    assertThat(existe("ORO")).isFalse();
  }

  @Test
  @DisplayName("CA-SP-119 — sin memberships:create se responde 403 y no se crea nada")
  void sinPermisoDeAlta() throws Exception {
    mvc.perform(
            post("/api/v1/memberships")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "memberships:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"ORO","name":"Oro"}
                    """))
        .andExpect(status().isForbidden());

    assertThat(existe("ORO")).isFalse();
  }

  @Test
  @DisplayName("CA-SP-118 — un evento por la creada y uno por cada desplazada, misma correlación")
  void auditoriaDelReordenamiento() throws Exception {
    crear("ORO", "Oro", null);
    String bronce = crear("BRONCE", "Bronce", null);

    UUID correlacion = UUID.randomUUID();
    mvc.perform(
            post("/api/v1/memberships")
                .with(admin())
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"PLATA","name":"Plata","childMembershipId":"%s"}
                    """
                        .formatted(bronce)))
        .andExpect(status().isCreated());

    // Dos eventos bajo la misma correlación: el CREATE de PLATA y el UPDATE de
    // BRONCE, que bajó de nivel y cambió de superior. ORO no se tocó.
    Integer creados =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE correlation_id = ? AND action = 'CREATE'",
            Integer.class,
            correlacion);
    assertThat(creados).isEqualTo(1);

    Integer modificados =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE correlation_id = ? AND action = 'UPDATE'",
            Integer.class,
            correlacion);
    assertThat(modificados).isEqualTo(1);

    // El UPDATE lleva el diff de lo que cambió, con before y after.
    String diff =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE correlation_id = ? AND action = 'UPDATE' AND entity_id = ?::uuid
            """,
            String.class,
            correlacion,
            bronce);
    assertThat(diff).contains("\"level\"").contains("\"before\": 2").contains("\"after\": 3");
    assertThat(diff).contains("parent_membership_id");
  }

  @Test
  @DisplayName("el alta NO emite evento de seguridad: una membresía no es un privilegio")
  void sinEventoDeSeguridad() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(
            post("/api/v1/memberships")
                .with(admin())
                .header("X-Correlation-Id", correlacion.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"ORO","name":"Oro"}
                    """))
        .andExpect(status().isCreated());

    // El catálogo de `security.md` §8.1 es cerrado y no incluye las membresías:
    // son un nivel de acceso a contenido, no un privilegio sobre el sistema. Es
    // la asimetría deliberada con RF-SP-001.
    Integer eventos =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    assertThat(eventos).isZero();
  }

  // ---------------------------------------------------------------------------
  // Listado — RF-SP-017
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-120 y CA-SP-121 — la cadena va sin paginar, ordenada por nivel")
  void listadoSinPaginar() throws Exception {
    crear("ORO", "Oro", null);
    crear("PLATA", "Plata", null);
    crear("BRONCE", "Bronce", null);

    mvc.perform(get("/api/v1/memberships").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[0].level").value(1))
        .andExpect(jsonPath("$.content[2].level").value(3))
        // Sin metadatos de paginación: rellenarlos diría que hay paginación.
        .andExpect(jsonPath("$.page").doesNotExist())
        .andExpect(jsonPath("$.totalElements").doesNotExist())
        .andExpect(jsonPath("$.totalPages").doesNotExist());
  }

  @Test
  @DisplayName("los parámetros de paginación y orden se ignoran y devuelven la cadena entera")
  void parametrosIgnorados() throws Exception {
    crear("ORO", "Oro", null);
    crear("PLATA", "Plata", null);

    mvc.perform(get("/api/v1/memberships?page=2&size=1&sort=name,asc").with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].code").value("ORO"));
  }

  @Test
  @DisplayName("el listado devuelve los dos vecinos de cada eslabón")
  void listadoConVecinos() throws Exception {
    String oro = crear("ORO", "Oro", null);
    String plata = crear("PLATA", "Plata", null);

    mvc.perform(get("/api/v1/memberships").with(lector()))
        .andExpect(jsonPath("$.content[0].childMembershipId").value(plata))
        .andExpect(jsonPath("$.content[1].parentMembershipId").value(oro))
        .andExpect(jsonPath("$.content[1].childMembershipId").hasJsonPath())
        .andExpect(jsonPath("$.content[1].childMembershipId").value(nullValue()));
  }

  @Test
  @DisplayName("la búsqueda ignora mayúsculas y acentos, y en blanco equivale a ausente")
  void busqueda() throws Exception {
    crear("PLATA", "Plata", null);
    crear("BRONCE", "Bronce", null);

    mvc.perform(get("/api/v1/memberships?search=PLA").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("PLATA"));

    mvc.perform(get("/api/v1/memberships?search=   ").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(2));

    // Un término con comodín no devuelve la cadena entera.
    mvc.perform(get("/api/v1/memberships?search=%25").with(lector()))
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  // ---------------------------------------------------------------------------
  // Detalle — RF-SP-018
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("el detalle expande los dos vecinos con su nivel")
  void detalleConVecinos() throws Exception {
    crear("ORO", "Oro", null);
    String plata = crear("PLATA", "Plata", null);
    crear("BRONCE", "Bronce", null);

    mvc.perform(get("/api/v1/memberships/" + plata).with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value(2))
        .andExpect(jsonPath("$.parentMembership.code").value("ORO"))
        .andExpect(jsonPath("$.parentMembership.level").value(1))
        .andExpect(jsonPath("$.childMembership.code").value("BRONCE"))
        .andExpect(jsonPath("$.childMembership.level").value(3))
        // Los vecinos no traen sus propios vecinos.
        .andExpect(jsonPath("$.parentMembership.parentMembership").doesNotExist());
  }

  @Test
  @DisplayName("CA-SP-126 y CA-SP-127 — los extremos devuelven el vecino ausente como null")
  void extremosSinVecino() throws Exception {
    String oro = crear("ORO", "Oro", null);
    String plata = crear("PLATA", "Plata", null);

    mvc.perform(get("/api/v1/memberships/" + oro).with(lector()))
        .andExpect(jsonPath("$.parentMembership").hasJsonPath())
        .andExpect(jsonPath("$.parentMembership").value(nullValue()));

    mvc.perform(get("/api/v1/memberships/" + plata).with(lector()))
        .andExpect(jsonPath("$.childMembership").hasJsonPath())
        .andExpect(jsonPath("$.childMembership").value(nullValue()));
  }

  @Test
  @DisplayName("en la única membresía del sistema ambos vecinos son nulos, y es válido")
  void unicaMembresia() throws Exception {
    String sola = crear("UNICA", "Única", null);

    mvc.perform(get("/api/v1/memberships/" + sola).with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value(1))
        .andExpect(jsonPath("$.parentMembership").hasJsonPath())
        .andExpect(jsonPath("$.parentMembership").value(nullValue()))
        .andExpect(jsonPath("$.childMembership").hasJsonPath())
        .andExpect(jsonPath("$.childMembership").value(nullValue()));
  }

  @Test
  @DisplayName("un identificador inexistente devuelve 404, no 422")
  void detalleInexistente() throws Exception {
    // Aquí lo que no existe es el recurso DE LA RUTA, que es el caso que
    // `development-guide.md` §7.1 reserva para el 404.
    mvc.perform(get("/api/v1/memberships/" + UUID.randomUUID()).with(lector()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/no-encontrado"));
  }

  @Test
  @DisplayName("sin memberships:read se responde 403 en el listado y en el detalle")
  void sinPermisoDeLectura() throws Exception {
    String sola = crear("UNICA", "Única", null);
    RequestPostProcessor sinPermiso =
        user(UUID.randomUUID().toString()).authorities(() -> "roles:read");

    mvc.perform(get("/api/v1/memberships").with(sinPermiso)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/memberships/" + sola).with(sinPermiso))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("ni el listado ni el detalle cruzan con user_memberships")
  void sinConteoDePersonas() throws Exception {
    String sola = crear("UNICA", "Única", null);

    // La ausencia es deliberada: una membresía ni se elimina ni se desactiva,
    // de modo que ese número no condiciona ninguna decisión tomable desde aquí.
    String listado =
        mvc.perform(get("/api/v1/memberships").with(lector()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(listado).doesNotContain("userCount").doesNotContain("users");

    String detalle =
        mvc.perform(get("/api/v1/memberships/" + sola).with(lector()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(detalle).doesNotContain("userCount").doesNotContain("users");
  }

  // ---------------------------------------------------------------------------
  // Esquema y coherencia de la cadena
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("uq_memberships_parent está DIFERIDA y con NULLS NOT DISTINCT")
  void restriccionCentralBienDeclarada() {
    // Es la restricción de la que depende todo el requerimiento, y las dos
    // propiedades son invisibles desde el comportamiento normal: sin
    // `NULLS NOT DISTINCT` admitiría varias cimas, y sin `DEFERRABLE` no habría
    // orden de sentencias válido para insertar por encima de la superior.
    // Comprobarlas sobre el catálogo del sistema es lo único que impide que una
    // migración futura las pierda por descuido.
    var fila =
        jdbc.queryForMap(
            """
            SELECT c.condeferrable, c.condeferred, i.indnullsnotdistinct
              FROM pg_constraint c
              JOIN pg_index i ON i.indexrelid = c.conindid
             WHERE c.conname = 'uq_memberships_parent'
            """);

    assertThat(fila.get("condeferrable")).as("debe poder diferirse").isEqualTo(true);
    assertThat(fila.get("condeferred")).as("debe estar diferida por omisión").isEqualTo(true);
    assertThat(fila.get("indnullsnotdistinct")).as("los nulos NO son distintos").isEqualTo(true);
  }

  @Test
  @DisplayName("uq_memberships_level también está diferida")
  void unicidadDeNivelDiferida() {
    // Sin diferirla, `SET level = level + 1` colisionaría consigo mismo:
    // PostgreSQL verifica la unicidad fila a fila dentro de la misma sentencia.
    var fila =
        jdbc.queryForMap(
            "SELECT condeferrable, condeferred FROM pg_constraint WHERE conname = 'uq_memberships_level'");

    assertThat(fila.get("condeferrable")).isEqualTo(true);
    assertThat(fila.get("condeferred")).isEqualTo(true);
  }

  @Test
  @DisplayName("tras veinte inserciones, la cadena y los niveles siguen siendo coherentes")
  void coherenciaTrasMuchasInserciones() throws Exception {
    // Se alternan las tres formas de inserción para que el recálculo se ejercite
    // por todos sus caminos, no solo por el que no toca nada.
    String primera = crear("N00", "Nivel 00", null);
    String ultima = primera;
    for (int i = 1; i < 20; i++) {
      String codigo = "N%02d".formatted(i);
      String nombre = "Nivel %02d".formatted(i);
      ultima =
          switch (i % 3) {
            case 0 -> crear(codigo, nombre, null); // extremo inferior
            case 1 -> crear(codigo, nombre, primera); // por encima de la cima
            default -> crear(codigo, nombre, ultima); // en medio
          };
    }

    // Los veinte niveles son 1..20 sin huecos ni repetidos.
    var niveles = jdbc.queryForList("SELECT level FROM memberships ORDER BY level", Integer.class);
    assertThat(niveles).hasSize(20);
    assertThat(niveles)
        .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList());

    // Exactamente una cima, y ninguna membresía con dos hijas.
    Integer cimas =
        jdbc.queryForObject(
            "SELECT count(*) FROM memberships WHERE parent_membership_id IS NULL", Integer.class);
    assertThat(cimas).isEqualTo(1);

    // Y la cadena de punteros coincide con el orden de niveles: la superior de
    // cada eslabón es exactamente la de un nivel menos.
    Integer incoherentes =
        jdbc.queryForObject(
            """
            SELECT count(*)
              FROM memberships h JOIN memberships p ON p.id = h.parent_membership_id
             WHERE p.level <> h.level - 1
            """,
            Integer.class);
    assertThat(incoherentes).as("un puntero no coincide con el orden de niveles").isZero();
  }

  @Test
  @DisplayName("un identificador que no es un UUID devuelve 400, no 500")
  void identificadorInvalido() throws Exception {
    // Antes de `RF-SP-018` esto salía como 500: Spring lanza la excepción al
    // convertir el argumento —antes de entrar al controlador—, y sin manejador
    // caía en el `catch` genérico. El cliente recibía un fallo del sistema por
    // un dedazo, y `audit_error_log` acumulaba UNHANDLED de severidad ALTA que
    // no eran fallos de nada.
    java.util.Map<String, Integer> respuestas = new java.util.LinkedHashMap<>();
    for (String invalido : new String[] {"abc", "018f3a2b7c4170009a3d1f2e5b8c9d30"}) {
      respuestas.put(
          invalido,
          mvc.perform(get("/api/v1/memberships/" + invalido).with(lector()))
              .andReturn()
              .getResponse()
              .getStatus());
    }

    assertThat(respuestas)
        .as("estado devuelto por cada forma inválida")
        .containsOnly(
            org.assertj.core.api.Assertions.entry("abc", 400),
            org.assertj.core.api.Assertions.entry("018f3a2b7c4170009a3d1f2e5b8c9d30", 400));
  }

  @Test
  @DisplayName("VAL-001 — un UUID no canónico se rechaza con 400, no con 404")
  void identificadorNoCanonico() throws Exception {
    // `UUID.fromString` del JDK acepta `1-1-1-1-1` y lo expande, de modo que la
    // conversión no fallaba y la respuesta acababa siendo un 404 — la respuesta
    // de «no existe» ante algo que nunca pudo existir.
    //
    // Fue un hueco declarado durante dos intentos fallidos: un `Converter`
    // suelto y otro registrado en el `FormatterRegistry`, y ninguno surtió
    // efecto porque `TypeConverterDelegate` captura el fallo del convertidor y
    // reintenta con el editor permisivo por omisión. Se cerró el 24-08-2026 con
    // un editor PERSONALIZADO, que se localiza antes y cortocircuita ese
    // reintento (`CanonicalUuidConverter`).
    mvc.perform(get("/api/v1/memberships/1-1-1-1-1").with(lector()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  // ---------------------------------------------------------------------------

  private RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString())
        .authorities(() -> "memberships:create", () -> "memberships:read");
  }

  private RequestPostProcessor lector() {
    return user(UUID.randomUUID().toString()).authorities(() -> "memberships:read");
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder alta(
      String code, String name, String hija) {
    String cuerpo =
        hija == null
            ? """
              {"code":"%s","name":"%s"}
              """
                .formatted(code, name)
            : """
              {"code":"%s","name":"%s","childMembershipId":"%s"}
              """
                .formatted(code, name, hija);
    return post("/api/v1/memberships")
        .with(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  /** Crea una membresía y devuelve su identificador. */
  private String crear(String code, String name, String hija) throws Exception {
    String cuerpo =
        mvc.perform(alta(code, name, hija))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode arbol = json.readTree(cuerpo);
    return arbol.get("id").asText();
  }

  private boolean existe(String code) {
    Integer filas =
        jdbc.queryForObject("SELECT count(*) FROM memberships WHERE code = ?", Integer.class, code);
    return filas != null && filas > 0;
  }
}
