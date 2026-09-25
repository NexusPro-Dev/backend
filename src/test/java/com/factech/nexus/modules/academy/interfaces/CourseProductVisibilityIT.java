package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * La visibilidad por servicio: `RF-AC-037` (`CA-AC-216` a `CA-AC-222`) y `RF-AC-038` (`CA-AC-223` a
 * `CA-AC-226`) en una suite, como la clasificación.
 *
 * <p><b>La que define el requerimiento es `CA-AC-219`</b>: un curso armado y activo, <b>sin
 * membresías</b>, que se ofrece por primera vez al recibir su servicio.
 *
 * <p><b>Los productos se siembran con el prefijo {@code ACS_} y se borran al terminar</b>, con sus
 * filas de {@code course_products} antes: la clave foránea no tiene {@code ON DELETE}, y decenas de
 * suites de `PM` y `MV` empiezan con {@code DELETE FROM products}.
 */
@AutoConfigureMockMvc
class CourseProductVisibilityIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID velas;
  private UUID bot;
  private UUID otroBot;

  @BeforeEach
  void sembrar() {
    limpiarTodo();
    instructor = CourseTestSupport.instructor(jdbc);
    velas = curso(jdbc, "Velas", instructor, 0);
    bot = producto("ACS_BOT_A", "BOT", "ACTIVO");
    otroBot = producto("ACS_BOT_B", "BOT", "ACTIVO");
  }

  @AfterEach
  void limpiar() {
    limpiarTodo();
  }

  private void limpiarTodo() {
    CourseTestSupport.limpiar(jdbc);
    CourseCategoryTestSupport.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM course_products WHERE product_id IN"
            + " (SELECT id FROM products WHERE code LIKE 'ACS\\_%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'ACS\\_%'");
  }

  // --- RF-AC-037 -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-216` — da la visibilidad con 201 y Location, con el servicio en products —id, código,"
          + " nombre—; un curso INACTIVO y un servicio INACTIVO se admiten")
  void daLaVisibilidad() throws Exception {
    mvc.perform(dar(velas, bot))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/courses/" + velas))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.products", hasSize(1)))
        .andExpect(jsonPath("$.products[0].id").value(bot.toString()))
        .andExpect(jsonPath("$.products[0].code").value("ACS_BOT_A"))
        .andExpect(jsonPath("$.products[0].name").value("Servicio ACS_BOT_A"));

    UUID inactivo = producto("ACS_BOT_INACTIVO", "BOT", "INACTIVO");
    mvc.perform(dar(velas, inactivo))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.products[*].code").value(contains("ACS_BOT_A", "ACS_BOT_INACTIVO")));
  }

  @Test
  @DisplayName(
      "`CA-AC-217` — 409 a la pareja repetida nombrando el servicio; 422 EX-002 al inexistente o"
          + " retirado; 422 EX-003 al upgrade; 404 al curso inexistente o retirado; 400 de forma")
  void rechazos() throws Exception {
    mvc.perform(dar(velas, bot)).andExpect(status().isCreated());
    mvc.perform(dar(velas, bot))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"))
        .andExpect(
            jsonPath("$.errors[0].message").value("El servicio ACS_BOT_A ya abre este curso."));

    mvc.perform(dar(velas, UUID.randomUUID()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    jdbc.update("UPDATE products SET deleted_at = now() WHERE id = ?", otroBot);
    mvc.perform(dar(velas, otroBot))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    UUID upgrade = producto("ACS_UPGRADE", "UPGRADE_MEMBRESIA", "ACTIVO");
    mvc.perform(dar(velas, upgrade))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(
                    "Solo un servicio abre un curso: el producto ACS_UPGRADE es un upgrade de"
                        + " membresía."));

    mvc.perform(dar(UUID.randomUUID(), bot)).andExpect(status().isNotFound());
    UUID retirado = curso(jdbc, "Retirado", instructor, 1);
    CourseTestSupport.retirar(jdbc, retirado);
    mvc.perform(dar(retirado, bot)).andExpect(status().isNotFound());

    for (String cuerpo :
        new String[] {
          "{}", "{\"productId\":\"no-es-uuid\"}", "{\"productId\":\"" + bot + "\",\"extra\":1}"
        }) {
      mvc.perform(
              post("/api/v1/courses/" + velas + "/products")
                  .with(con("courses:update"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(cuerpo))
          .andExpect(status().isBadRequest());
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_products", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-218` — una fila CREATE de course_products con el curso como entidad, el actor y la"
          + " pareja con el código")
  void auditaElAlta() throws Exception {
    UUID actor = UUID.randomUUID();
    mvc.perform(
            post("/api/v1/courses/" + velas + "/products")
                .with(con(actor, "courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + bot + "\"}"))
        .andExpect(status().isCreated());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'AC' AND entity = 'course_products' AND entity_id = ?",
            velas);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat((String) fila.get("changes")).contains(bot.toString()).contains("ACS_BOT_A");
  }

  @Test
  @DisplayName(
      "`CA-AC-219` — un curso activo y armado SIN membresías pasa de «sin membresías ni servicios» a"
          + " ofrecible al recibir su primer servicio, en el detalle, el listado y su categoría")
  void seOfrecePorSuServicio() throws Exception {
    UUID armado = curso(jdbc, "Armado", instructor, 2, "PRINCIPIANTE", "C", "L", "ACTIVO");
    UUID modulo = CourseTestSupport.modulo(jdbc, armado, "Uno", 0, "ACTIVO");
    CourseTestSupport.leccionActiva(jdbc, modulo, "Primera", 5);
    UUID cajon = CourseCategoryTestSupport.categoria(jdbc, "Cajón ACS", 0);
    jdbc.update(
        "INSERT INTO course_category_items (course_id, category_id) VALUES (?, ?)", armado, cajon);

    mvc.perform(get("/api/v1/courses/" + armado).with(con("courses:read")))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El curso no tiene ninguna membresía ni ningún servicio que lo abra."));

    mvc.perform(dar(armado, bot))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.offerable").value(true))
        .andExpect(jsonPath("$.offerableReason").doesNotExist());
    mvc.perform(get("/api/v1/courses?q=Armado").with(con("courses:read")))
        .andExpect(jsonPath("$.content[0].offerable").value(true));
    mvc.perform(get("/api/v1/course-categories/" + cajon).with(con("course-categories:read")))
        .andExpect(jsonPath("$.courses[0].offerable").value(true));
  }

  @Test
  @DisplayName(
      "`CA-AC-220` — el detalle trae el servicio que PM retiró después; la instantánea del retiro"
          + " del curso lleva product_ids y las filas permanecen")
  void enmiendasDelDetalleYDelRetiro() throws Exception {
    mvc.perform(dar(velas, bot)).andExpect(status().isCreated());
    jdbc.update("UPDATE products SET deleted_at = now() WHERE id = ?", bot);
    mvc.perform(get("/api/v1/courses/" + velas).with(con("courses:read")))
        .andExpect(jsonPath("$.products[*].code").value(contains("ACS_BOT_A")));

    mvc.perform(
            post("/api/v1/courses/" + velas + "/deletion")
                .with(con("courses:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ya no se dicta.\"}"))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT snapshot::text FROM audit_deletion_log WHERE entity = 'courses'"
                    + " AND entity_id = ?",
                String.class,
                velas))
        .contains("\"product_ids\": [\"" + bot + "\"]");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM course_products WHERE course_id = ?", Integer.class, velas))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-AC-221` — dos altas simultáneas de la misma pareja: una fila y un 409")
  void dosAltas() throws Exception {
    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(dar(velas, bot)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_products", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-AC-222` — sin courses:update responde 403 aunque porte products:update")
  void sinPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/courses/" + velas + "/products")
                .with(con("products:update", "courses:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + bot + "\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/v1/courses/" + velas + "/products/" + bot)
                .with(con("products:update", "courses:read")))
        .andExpect(status().isForbidden());
  }

  // --- RF-AC-038 -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-223` y `CA-AC-224` — quita con 200 y el detalle sin el servicio; la fila no existe;"
          + " una fila ASSOCIATION sin motivo con el código")
  void quita() throws Exception {
    mvc.perform(dar(velas, bot)).andExpect(status().isCreated());
    mvc.perform(dar(velas, otroBot)).andExpect(status().isCreated());
    UUID actor = UUID.randomUUID();

    mvc.perform(
            delete("/api/v1/courses/" + velas + "/products/" + bot)
                .with(con(actor, "courses:update")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.products[*].code").value(contains("ACS_BOT_B")));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM course_products WHERE course_id = ? AND product_id = ?",
                Integer.class,
                velas,
                bot))
        .isZero();
    Map<String, Object> baja =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE module = 'AC' AND entity = 'course_products'"
                + " AND entity_id = ?",
            velas);
    assertThat(baja.get("actor")).isEqualTo(actor.toString());
    assertThat(baja.get("deletion_type")).isEqualTo("ASSOCIATION");
    assertThat(baja.get("reason")).isNull();
    assertThat((String) baja.get("snapshot")).contains("\"product_code\": \"ACS_BOT_A\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-225` — 404 al curso y a la pareja con mensajes distintos; un servicio retirado se"
          + " quita igual; quitar el último de un curso sin membresías lo deja sin ofrecer")
  void quitarRechazosYUltimo() throws Exception {
    mvc.perform(quitar(UUID.randomUUID(), bot))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un curso vivo con ese identificador."));
    mvc.perform(quitar(velas, bot))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Ese servicio no abre este curso."));

    UUID armado = curso(jdbc, "Armado", instructor, 2, "PRINCIPIANTE", "C", "L", "ACTIVO");
    UUID modulo = CourseTestSupport.modulo(jdbc, armado, "Uno", 0, "ACTIVO");
    CourseTestSupport.leccionActiva(jdbc, modulo, "Primera", 5);
    mvc.perform(dar(armado, bot)).andExpect(jsonPath("$.offerable").value(true));
    jdbc.update("UPDATE products SET deleted_at = now() WHERE id = ?", bot);
    mvc.perform(quitar(armado, bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products", hasSize(0)))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El curso no tiene ninguna membresía ni ningún servicio que lo abra."));
  }

  @Test
  @DisplayName("`CA-AC-226` — dos retiros simultáneos: un 200, un 404 y UNA fila de auditoría")
  void dosRetiros() throws Exception {
    mvc.perform(dar(velas, bot)).andExpect(status().isCreated());
    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(quitar(velas, bot)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 200).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 404).count())
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity = 'course_products'"
                    + " AND entity_id = ?",
                Integer.class,
                velas))
        .isEqualTo(1);
  }

  /** Un producto directo en la tabla; el upgrade va del suelo a la primera membresía de arriba. */
  private UUID producto(String codigo, String tipo, String estado) {
    UUID id = UUID.randomUUID();
    boolean upgrade = "UPGRADE_MEMBRESIA".equals(tipo);
    jdbc.update(
        "INSERT INTO products (id, code, type, name, price, currency_id, scope, implementation,"
            + " status, source_membership_id, target_membership_id)"
            + " VALUES (?, ?, ?, ?, 100, (SELECT id FROM currencies ORDER BY code LIMIT 1),"
            + " 'NINGUNO', 'AUTOMATICA', ?, "
            + (upgrade
                ? "(SELECT id FROM memberships WHERE code = 'BECA'),"
                    + " (SELECT id FROM memberships WHERE code <> 'BECA' ORDER BY level LIMIT 1))"
                : "NULL, NULL)"),
        id,
        codigo,
        tipo,
        "Servicio " + codigo,
        estado);
    return id;
  }

  private MockHttpServletRequestBuilder dar(UUID curso, UUID producto) {
    return post("/api/v1/courses/" + curso + "/products")
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"productId\":\"" + producto + "\"}");
  }

  private MockHttpServletRequestBuilder quitar(UUID curso, UUID producto) {
    return delete("/api/v1/courses/" + curso + "/products/" + producto).with(con("courses:update"));
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }
}
