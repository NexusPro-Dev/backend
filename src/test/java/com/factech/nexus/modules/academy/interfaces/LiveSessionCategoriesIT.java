package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.CLASES;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.SENALES;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.TRADING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Las categorías de las clases en vivo (`RF-AC-063` a `RF-AC-066`, `RN-AC-034`): `CA-AC-330` a
 * `CA-AC-334` y `CA-AC-338`. Lo que pasa con las clases vive en {@link
 * LiveSessionCategoryAssignmentIT}.
 */
@AutoConfigureMockMvc
class LiveSessionCategoriesIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/live-session-categories";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID admin;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    admin = CourseTestSupport.persona(jdbc, "Ada", "Admin", ProgressTestSupport.ADMIN);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-330` — V107: Trading, Clases y Señales con orden 0, 1 y 2; category_id obligatoria"
          + " y con su clave; nombre único entre las vivas")
  void elEsquema() {
    assertThat(
            jdbc.queryForList(
                "SELECT name FROM live_session_categories WHERE id IN (?, ?, ?)"
                    + " ORDER BY display_order",
                String.class,
                TRADING,
                CLASES,
                SENALES))
        .containsExactly("Trading", "Clases", "Señales");
    assertThat(
            jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name ="
                    + " 'live_sessions' AND column_name = 'category_id'",
                String.class))
        .isEqualTo("NO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname ="
                    + " 'uq_live_session_categories_name'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints WHERE constraint_name ="
                    + " 'fk_live_sessions_category'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-331` — consultar trae solo las vivas, por orden, nombre e identificador; sin el"
          + " permiso, 403")
  void consulta() throws Exception {
    UUID nueva = idDe(registrar("{\"name\":\"Avanzado\",\"displayOrder\":1}"));
    retirar(SENALES, "Ya no").andExpect(status().isNoContent());

    mvc.perform(get(RUTA).with(con("live-session-categories:list")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", contains("Trading", "Avanzado", "Clases")))
        .andExpect(jsonPath("$[1].id").value(nueva.toString()))
        .andExpect(jsonPath("$[1].displayOrder").value(1));
    mvc.perform(get(RUTA).with(con("live-sessions:list"))).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-332` — registrar responde 201 y audita; nombre vacío o largo, VAL-001; orden nulo"
          + " o negativo, VAL-002; nombre de otra viva sin acentos ni mayúsculas, 409 EX-001; el"
          + " de una retirada se admite")
  void registra() throws Exception {
    String respuesta =
        registrar("{\"name\":\"  Psicotrading \",\"displayOrder\":3}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Psicotrading"))
            .andExpect(jsonPath("$.displayOrder").value(3))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(JsonPath.read(respuesta, "$.id"));
    assertThat(cambios(id, "CREATE")).isEqualTo(1);

    registrar("{\"name\":\" \",\"displayOrder\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
    registrar("{\"name\":\"" + "x".repeat(101) + "\",\"displayOrder\":0}")
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
    registrar("{\"name\":\"Otra\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("displayOrder"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    registrar("{\"name\":\"Otra\",\"displayOrder\":-1}")
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    registrar("{\"name\":\"SEÑALES\",\"displayOrder\":0}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
    registrar("{\"name\":\"senales\",\"displayOrder\":0}").andExpect(status().isConflict());

    retirar(SENALES, "Se renombra").andExpect(status().isNoContent());
    registrar("{\"name\":\"Señales\",\"displayOrder\":2}").andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "`CA-AC-333` — corregir cambia nombre y orden y audita solo lo que cambió; sin campos,"
          + " VAL-003; nombre nulo, VAL-001; inexistente o retirada, 404; nombre de otra viva, 409"
          + " EX-002")
  void corrige() throws Exception {
    corregir(TRADING, "{\"name\":\"Trading en vivo\",\"displayOrder\":5}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Trading en vivo"))
        .andExpect(jsonPath("$.displayOrder").value(5));
    assertThat(cambios(TRADING, "UPDATE")).isEqualTo(1);
    corregir(TRADING, "{\"name\":\"Trading en vivo\"}").andExpect(status().isOk());
    assertThat(cambios(TRADING, "UPDATE")).isEqualTo(1);

    corregir(TRADING, "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    corregir(TRADING, "{\"name\":null}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
    corregir(TRADING, "{\"displayOrder\":null}")
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    corregir(UUID.randomUUID(), "{\"name\":\"X\"}").andExpect(status().isNotFound());
    corregir(TRADING, "{\"name\":\"clases\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    retirar(SENALES, "Fuera").andExpect(status().isNoContent());
    corregir(SENALES, "{\"name\":\"Vuelve\"}").andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-334` — retirar con motivo responde 204 y audita; sin motivo, 400; inexistente,"
          + " 404; ya retirada, 409 EX-002")
  void retira() throws Exception {
    mvc.perform(
            post(RUTA + "/" + CLASES + "/deletion")
                .with(con(admin, "live-session-categories:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    retirar(UUID.randomUUID(), "No existe").andExpect(status().isNotFound());

    retirar(CLASES, "Se junta con Trading").andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM live_session_categories WHERE id = ?",
                Boolean.class,
                CLASES))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE module = 'AC' AND entity ="
                    + " 'live_session_categories' AND entity_id = ?",
                Integer.class,
                CLASES))
        .isEqualTo(1);
    retirar(CLASES, "Otra vez")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName(
      "`CA-AC-338` — los cuatro permisos: las escrituras solo a SUPERADMIN y ADMIN; el listado"
          + " también donde está courses:teach o live-sessions:learn")
  void losPermisos() throws Exception {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM permissions WHERE code LIKE 'live-session-categories:%'",
                Integer.class))
        .isEqualTo(4);
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT r.code FROM role_permissions rp JOIN roles r ON r.id ="
                    + " rp.role_id JOIN permissions p ON p.id = rp.permission_id WHERE p.code IN"
                    + " ('live-session-categories:create', 'live-session-categories:update',"
                    + " 'live-session-categories:delete') ORDER BY r.code",
                String.class))
        .containsExactly("ADMIN", "SUPERADMIN");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT rp.role_id) FROM role_permissions rp JOIN permissions t"
                    + " ON t.id = rp.permission_id AND t.code IN ('courses:teach',"
                    + " 'live-sessions:learn') WHERE NOT EXISTS (SELECT 1 FROM role_permissions x"
                    + " JOIN permissions l ON l.id = x.permission_id AND l.code ="
                    + " 'live-session-categories:list' WHERE x.role_id = rp.role_id)",
                Integer.class))
        .isZero();
    mvc.perform(post(RUTA).with(con(admin, "live-session-categories:list")))
        .andExpect(status().isForbidden());
  }

  private ResultActions registrar(String cuerpo) throws Exception {
    return mvc.perform(
        post(RUTA)
            .with(con(admin, "live-session-categories:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions corregir(UUID id, String cuerpo) throws Exception {
    return mvc.perform(
        patch(RUTA + "/" + id)
            .with(con(admin, "live-session-categories:update"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions retirar(UUID id, String motivo) throws Exception {
    return mvc.perform(
        post(RUTA + "/" + id + "/deletion")
            .with(con(admin, "live-session-categories:delete"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"" + motivo + "\"}"));
  }

  private UUID idDe(ResultActions respuesta) throws Exception {
    return UUID.fromString(
        JsonPath.read(
            respuesta
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id"));
  }

  private int cambios(UUID id, String accion) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE module = 'AC' AND entity ="
                + " 'live_session_categories' AND entity_id = ? AND action = ?",
            Integer.class,
            id,
            accion);
    return filas == null ? 0 : filas;
  }
}
