package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * El alta del curso con sus categorías, sus servicios y sus membresías (`RF-AC-008` 0.4.0 y 0.6.0,
 * `CA-AC-227` a `CA-AC-232`): cada lista todo o nada, con las mismas escrituras que las operaciones
 * sueltas. Los productos llevan el prefijo {@code CRC_} y se borran al terminar, después de sus
 * filas de {@code course_products}.
 */
@AutoConfigureMockMvc
class CourseRegistrationCategoriesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID trading;
  private UUID cripto;

  @BeforeEach
  void sembrar() {
    limpiarTodo();
    instructor = CourseTestSupport.instructor(jdbc);
    trading = CourseCategoryTestSupport.categoria(jdbc, "Trading", 1);
    cripto = CourseCategoryTestSupport.categoria(jdbc, "Cripto", 0);
  }

  @AfterEach
  void limpiar() {
    limpiarTodo();
  }

  private void limpiarTodo() {
    CourseTestSupport.limpiar(jdbc);
    CourseCategoryTestSupport.limpiar(jdbc);
    jdbc.update("DELETE FROM products WHERE code LIKE 'CRC\\_%'");
  }

  @Test
  @DisplayName(
      "`CA-AC-230` y `CA-AC-232` — con productIds y membershipIds nace con sus servicios y sus"
          + " membresías, una fila y un CREATE por cada uno; nace INACTIVO y no se ofrece")
  void naceConQuienLoPuedeVer() throws Exception {
    UUID bot = producto("CRC_BOT", "BOT");
    UUID membresia =
        jdbc.queryForObject("SELECT id FROM memberships ORDER BY level LIMIT 1", UUID.class);

    String id =
        alta2("Velas", "\"" + bot + "\"", "\"" + membresia + "\"")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.products[*].code").value(contains("CRC_BOT")))
            .andExpect(jsonPath("$.memberships[*].id").value(contains(membresia.toString())))
            .andExpect(jsonPath("$.status").value("INACTIVO"))
            .andExpect(jsonPath("$.offerable").value(false))
            .andExpect(jsonPath("$.offerableReason").value("El curso está inactivo."))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1");
    UUID curso = UUID.fromString(id);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'AC' AND entity IN"
                    + " ('course_products', 'course_memberships') AND entity_id = ?",
                Integer.class,
                curso))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-AC-231` — un upgrade, un retirado y un inexistente son EX-005 nombrándolos todos; una"
          + " membresía inexistente es EX-006; y no queda nada")
  void llavesQueNoSirven() throws Exception {
    UUID upgrade = producto("CRC_UPGRADE", "UPGRADE_MEMBRESIA");
    UUID retirado = producto("CRC_RETIRADO", "BOT");
    jdbc.update("UPDATE products SET deleted_at = now() WHERE id = ?", retirado);
    UUID inexistente = UUID.randomUUID();

    alta2("Velas", "\"" + upgrade + "\", \"" + retirado + "\", \"" + inexistente + "\"", null)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"))
        .andExpect(jsonPath("$.errors[0].field").value("productIds"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("CRC_UPGRADE")))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("CRC_RETIRADO")))
        .andExpect(jsonPath("$.errors[0].message").value(containsString(inexistente.toString())));

    UUID otra = UUID.randomUUID();
    alta2("Velas", null, "\"" + otra + "\"")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString(otra.toString())));

    alta2("Velas", "\"" + upgrade + "\", \"" + upgrade + "\"", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"))
        .andExpect(jsonPath("$.errors[0].field").value("productIds"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM courses", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_products", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_memberships", Integer.class))
        .isZero();
  }

  /** El alta con servicios y membresías; {@code null} omite la lista. */
  private ResultActions alta2(String titulo, String productos, String membresias) throws Exception {
    String listas =
        (productos == null ? "" : ",\"productIds\":[" + productos + "]")
            + (membresias == null ? "" : ",\"membershipIds\":[" + membresias + "]");
    return mvc.perform(
        post("/api/v1/courses")
            .with(con("courses:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"title\":\"%s\",\"instructorId\":\"%s\",\"difficulty\":\"PRINCIPIANTE\",\"displayOrder\":0%s}"
                    .formatted(titulo, instructor, listas)));
  }

  private UUID producto(String codigo, String tipo) {
    UUID id = UUID.randomUUID();
    boolean upgrade = "UPGRADE_MEMBRESIA".equals(tipo);
    jdbc.update(
        "INSERT INTO products (id, code, type, name, price, currency_id, scope, implementation,"
            + " status, source_membership_id, target_membership_id)"
            + " VALUES (?, ?, ?, ?, 100, (SELECT id FROM currencies ORDER BY code LIMIT 1),"
            + " 'NINGUNO', 'AUTOMATICA', 'ACTIVO', "
            + (upgrade
                ? "(SELECT id FROM memberships WHERE code = 'BECA'),"
                    + " (SELECT id FROM memberships WHERE code <> 'BECA' ORDER BY level LIMIT 1))"
                : "NULL, NULL)"),
        id,
        codigo,
        tipo,
        "Servicio " + codigo);
    return id;
  }

  @Test
  @DisplayName(
      "`CA-AC-227` — con categoryIds nace en ellas, en su orden, con una fila y un CREATE por cada"
          + " una; sin la lista, o vacía, nace sin categorías")
  void naceEnSusCategorias() throws Exception {
    String id =
        alta("Velas", "\"" + trading + "\", \"" + cripto + "\"")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categories[*].name").value(contains("Cripto", "Trading")))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1");
    UUID curso = UUID.fromString(id);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM course_category_items WHERE course_id = ?",
                Integer.class,
                curso))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'AC'"
                    + " AND entity = 'course_category_items' AND entity_id = ? AND action = 'CREATE'",
                Integer.class,
                curso))
        .isEqualTo(2);

    alta("Sin lista", null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.categories", hasSize(0)));
    alta("Lista vacía", "")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.categories", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-AC-228` — una inexistente y una retirada: 422 EX-004 nombrándolas TODAS, y no queda"
          + " nada — ni curso, ni clasificaciones, ni auditoría")
  void todoONada() throws Exception {
    UUID inexistente = UUID.randomUUID();
    CourseCategoryTestSupport.retirar(jdbc, cripto);

    alta("Velas", "\"" + trading + "\", \"" + inexistente + "\", \"" + cripto + "\"")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"))
        .andExpect(jsonPath("$.errors[0].field").value("categoryIds"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString(inexistente.toString())))
        .andExpect(jsonPath("$.errors[0].message").value(containsString(cripto.toString())));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM courses", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_category_items", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'AC' AND entity IN"
                    + " ('courses', 'course_category_items')",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-229` — repetidas o con un nulo responden 400 VAL-008; el nulo, junto con los demás"
          + " errores de forma")
  void repetidasYNulas() throws Exception {
    alta("Velas", "\"" + trading + "\", \"" + trading + "\"")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"));

    mvc.perform(
            post("/api/v1/courses")
                .with(con("courses:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"instructorId\":\"%s\",\"difficulty\":\"PRINCIPIANTE\",\"displayOrder\":0,"
                            .formatted(instructor)
                        + "\"categoryIds\":[null]}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(org.hamcrest.Matchers.hasItems("VAL-001", "VAL-008")));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM courses", Integer.class)).isZero();
  }

  /** El alta con la lista dada; {@code null} la omite y {@code ""} la manda vacía. */
  private ResultActions alta(String titulo, String categorias) throws Exception {
    String lista = categorias == null ? "" : ",\"categoryIds\":[" + categorias + "]";
    return mvc.perform(
        post("/api/v1/courses")
            .with(con("courses:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"title\":\"%s\",\"instructorId\":\"%s\",\"difficulty\":\"PRINCIPIANTE\",\"displayOrder\":0%s}"
                    .formatted(titulo, instructor, lista)));
  }
}
