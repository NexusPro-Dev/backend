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
 * La visibilidad por membresía: `RF-AC-020` (`CA-AC-135` a `CA-AC-140`) y `RF-AC-021` (`CA-AC-141`
 * a `CA-AC-144`), como la de servicios.
 *
 * <p><b>Las membresías son las sembradas</b> y no se tocan: se eligen las dos primeras de la cadena
 * al empezar. <b>Las filas de {@code course_memberships} se borran al terminar cada prueba</b>:
 * varias suites de `MV` montan su cadena con {@code DELETE FROM memberships}, y la clave foránea no
 * tiene {@code ON DELETE}. Lo que el aula hará con la lista —que {@code ORO} no abra a {@code
 * PLATINO}— se prueba con el aula; aquí, que la lista es exactamente la que se dio.
 */
@AutoConfigureMockMvc
class CourseMembershipVisibilityIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID velas;
  private UUID alta;
  private String codigoAlta;
  private UUID baja;
  private String codigoBaja;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    velas = curso(jdbc, "Velas", instructor, 0);
    // Nivel superior es número MENOR (`sp.md` §10.4): la primera es la cima.
    List<Map<String, Object>> cadena =
        jdbc.queryForList("SELECT id, code FROM memberships ORDER BY level LIMIT 2");
    alta = (UUID) cadena.get(0).get("id");
    codigoAlta = (String) cadena.get(0).get("code");
    baja = (UUID) cadena.get(1).get("id");
    codigoBaja = (String) cadena.get(1).get("code");
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  // --- RF-AC-020 -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-135` — da la visibilidad con 201 y Location, con la membresía en memberships —id,"
          + " código, nombre, color—; un curso INACTIVO la recibe igual; en el orden de la cadena")
  void daLaVisibilidad() throws Exception {
    mvc.perform(dar(velas, baja))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/courses/" + velas))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.memberships", hasSize(1)))
        .andExpect(jsonPath("$.memberships[0].id").value(baja.toString()))
        .andExpect(jsonPath("$.memberships[0].code").value(codigoBaja))
        .andExpect(jsonPath("$.memberships[0].name").exists())
        .andExpect(jsonPath("$.memberships[0].color").exists());

    mvc.perform(dar(velas, alta))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberships[*].code").value(contains(codigoAlta, codigoBaja)));
  }

  @Test
  @DisplayName(
      "`CA-AC-136` — 409 a la pareja repetida nombrando la membresía; 422 a la inexistente; 404 al"
          + " curso inexistente o retirado; 400 de forma")
  void rechazos() throws Exception {
    mvc.perform(dar(velas, alta)).andExpect(status().isCreated());
    mvc.perform(dar(velas, alta))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("La membresía " + codigoAlta + " ya abre este curso."));

    mvc.perform(dar(velas, UUID.randomUUID()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    mvc.perform(dar(UUID.randomUUID(), alta)).andExpect(status().isNotFound());
    UUID retirado = curso(jdbc, "Retirado", instructor, 1);
    CourseTestSupport.retirar(jdbc, retirado);
    mvc.perform(dar(retirado, alta)).andExpect(status().isNotFound());

    for (String cuerpo :
        new String[] {
          "{}",
          "{\"membershipId\":\"no-es-uuid\"}",
          "{\"membershipId\":\"" + alta + "\",\"extra\":1}"
        }) {
      mvc.perform(
              post("/api/v1/courses/" + velas + "/memberships")
                  .with(con("courses:update"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(cuerpo))
          .andExpect(status().isBadRequest());
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_memberships", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-137` — una fila CREATE de course_memberships con el curso como entidad, el actor y el"
          + " código de la membresía")
  void auditaElAlta() throws Exception {
    UUID actor = UUID.randomUUID();
    mvc.perform(
            post("/api/v1/courses/" + velas + "/memberships")
                .with(con(actor, "courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"membershipId\":\"" + alta + "\"}"))
        .andExpect(status().isCreated());
    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'AC' AND entity = 'course_memberships' AND entity_id = ?",
            velas);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat((String) fila.get("changes")).contains(alta.toString()).contains(codigoAlta);
  }

  @Test
  @DisplayName(
      "`CA-AC-138` — un curso activo y armado SIN llaves ya se ofrece —es de todos— y sigue a"
          + " ofreciéndose con su primera membresía, en el detalle, el listado y su categoría")
  void seOfrecePorSuMembresia() throws Exception {
    UUID armado = curso(jdbc, "Armado", instructor, 2, "PRINCIPIANTE", "C", "L", "ACTIVO");
    UUID modulo = CourseTestSupport.modulo(jdbc, armado, "Uno", 0, "ACTIVO");
    CourseTestSupport.leccionActiva(jdbc, modulo, "Primera", 5);
    UUID cajon = CourseCategoryTestSupport.categoria(jdbc, "Cajón CMV", 0);
    jdbc.update(
        "INSERT INTO course_category_items (course_id, category_id) VALUES (?, ?)", armado, cajon);

    mvc.perform(get("/api/v1/courses/" + armado).with(con("courses:read")))
        .andExpect(jsonPath("$.offerable").value(true))
        .andExpect(jsonPath("$.offerableReason").doesNotExist());
    mvc.perform(dar(armado, alta))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.offerable").value(true));
    mvc.perform(get("/api/v1/courses?q=Armado").with(con("courses:read")))
        .andExpect(jsonPath("$.content[0].offerable").value(true));
    mvc.perform(get("/api/v1/course-categories/" + cajon).with(con("course-categories:read")))
        .andExpect(jsonPath("$.courses[0].offerable").value(true));

    CourseTestSupport.limpiar(jdbc);
    CourseCategoryTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-139` — la instantánea del retiro del curso lleva membership_ids y las filas permanecen")
  void enmiendaDelRetiro() throws Exception {
    mvc.perform(dar(velas, alta)).andExpect(status().isCreated());
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
        .contains("\"membership_ids\": [\"" + alta + "\"]");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM course_memberships WHERE course_id = ?",
                Integer.class,
                velas))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-AC-140` — dos altas simultáneas de la misma pareja: una fila y un 409")
  void dosAltas() throws Exception {
    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(dar(velas, alta)));
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  // --- RF-AC-021 -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-141` y `CA-AC-142` — quita con 200 y el detalle sin la membresía; una fila"
          + " ASSOCIATION sin motivo con el código")
  void quita() throws Exception {
    mvc.perform(dar(velas, alta)).andExpect(status().isCreated());
    mvc.perform(dar(velas, baja)).andExpect(status().isCreated());
    UUID actor = UUID.randomUUID();

    mvc.perform(
            delete("/api/v1/courses/" + velas + "/memberships/" + alta)
                .with(con(actor, "courses:update")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberships[*].code").value(contains(codigoBaja)));

    Map<String, Object> baja_ =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE module = 'AC' AND entity = 'course_memberships'"
                + " AND entity_id = ?",
            velas);
    assertThat(baja_.get("actor")).isEqualTo(actor.toString());
    assertThat(baja_.get("deletion_type")).isEqualTo("ASSOCIATION");
    assertThat(baja_.get("reason")).isNull();
    assertThat((String) baja_.get("snapshot"))
        .contains("\"membership_code\": \"" + codigoAlta + "\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-143` y `CA-AC-144` — quitar la última de un curso sin servicios lo deja abierto a todos y"
          + " ACTIVO; 404 al curso y a la pareja con mensajes distintos; dos retiros: 200, 404 y UNA"
          + " fila")
  void laUltimaYLosRechazos() throws Exception {
    UUID armado = curso(jdbc, "Armado", instructor, 2, "PRINCIPIANTE", "C", "L", "ACTIVO");
    UUID modulo = CourseTestSupport.modulo(jdbc, armado, "Uno", 0, "ACTIVO");
    CourseTestSupport.leccionActiva(jdbc, modulo, "Primera", 5);
    mvc.perform(dar(armado, alta)).andExpect(jsonPath("$.offerable").value(true));
    mvc.perform(quitar(armado, alta))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        // Desde el 25-09-2026 (`ac.md` §5.2.12): sin llaves queda abierto a todos.
        .andExpect(jsonPath("$.offerable").value(true));

    mvc.perform(quitar(UUID.randomUUID(), alta))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un curso vivo con ese identificador."));
    mvc.perform(quitar(velas, alta))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("La membresía indicada no abre este curso."));

    mvc.perform(dar(velas, baja)).andExpect(status().isCreated());
    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(quitar(velas, baja)));
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 200).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 404).count())
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity = 'course_memberships'"
                    + " AND entity_id = ?",
                Integer.class,
                velas))
        .isEqualTo(1);
  }

  private MockHttpServletRequestBuilder dar(UUID curso, UUID membresia) {
    return post("/api/v1/courses/" + curso + "/memberships")
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"membershipId\":\"" + membresia + "\"}");
  }

  private MockHttpServletRequestBuilder quitar(UUID curso, UUID membresia) {
    return delete("/api/v1/courses/" + curso + "/memberships/" + membresia)
        .with(con("courses:update"));
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }
}
