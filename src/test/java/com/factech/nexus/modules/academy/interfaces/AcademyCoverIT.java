package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Las portadas de academia: la de la categoría (`RF-AC-006`, `CA-AC-155` a `CA-AC-162`), la del
 * curso (`RF-AC-014`, `CA-AC-172` a `CA-AC-175`) y la ruta pública que las sirve (`RF-AC-032`,
 * `CA-AC-163` a `CA-AC-166`; `CA-AC-167` vive en {@code RateLimitIT}).
 *
 * <p><b>Las imágenes son firmas con relleno</b>: el detector mira los primeros bytes y nada más,
 * así que una firma de {@code PNG} seguida de ceros es un {@code PNG} a efectos del sistema — que
 * no trata la imagen.
 */
@AutoConfigureMockMvc
class AcademyCoverIT extends IntegrationTestBase {

  static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

  /** Con relleno hasta 16: el detector mira hasta el byte 12 y tres no llegan. */
  static final byte[] JPEG = Arrays.copyOf(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 16);

  static final byte[] GIF = "GIF89a".getBytes();
  static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
  static final byte[] TEXTO = "esto no es una imagen aunque se llame foto.png".getBytes();
  static final int TOPE = 5_242_880;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID categoria;
  private UUID velas;

  @BeforeEach
  void sembrar() {
    limpiarTodo();
    categoria = CourseCategoryTestSupport.categoria(jdbc, "Trading", 0);
    velas = curso(jdbc, "Velas", CourseTestSupport.instructor(jdbc), 0);
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
  }

  @AfterEach
  void limpiar() {
    limpiarTodo();
  }

  private void limpiarTodo() {
    CourseTestSupport.limpiar(jdbc);
    CourseCategoryTestSupport.limpiar(jdbc);
  }

  // --- RF-AC-006 -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-155` y `CA-AC-157` — sube un PNG a una categoría sin portada: 200, coverImageUrl con"
          + " su forma, los mismos bytes con image/png, y un UPDATE con cover_image_id y nada más")
  void subeLaPortadaDeUnaCategoria() throws Exception {
    byte[] png = relleno(PNG, 2_048);
    mvc.perform(subirACategoria(categoria, png, "foto.png", MediaType.IMAGE_PNG_VALUE))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.coverImageUrl")
                .value(matchesPattern("^/api/v1/academy-images/[0-9a-f-]{36}$")));

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT i.content_type, i.content FROM academy_images i JOIN course_categories k"
                + " ON k.cover_image_id = i.id WHERE k.id = ?",
            categoria);
    assertThat(fila.get("content_type")).isEqualTo("image/png");
    assertThat((byte[]) fila.get("content")).isEqualTo(png);

    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE module = 'AC'"
                + " AND entity = 'course_categories' AND entity_id = ? AND action = 'UPDATE'",
            String.class,
            categoria);
    assertThat(cambios).contains("cover_image_id").doesNotContain("\"name\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-156` — reemplazar la portada estrena dirección, borra la anterior y deja UNA fila")
  void reemplaza() throws Exception {
    UUID primera = portadaDeCategoria(relleno(PNG, 100));
    UUID segunda = portadaDeCategoria(relleno(JPEG, 100));

    assertThat(segunda).isNotEqualTo(primera);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM academy_images WHERE id = ?", Integer.class, primera))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM academy_images", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-158` — el tipo lo deciden los bytes: un JPEG como image/png se guarda image/jpeg; GIF,"
          + " SVG y texto como image/png se rechazan con VAL-003 nombrando file")
  void elTipoLoDecidenLosBytes() throws Exception {
    portadaDeCategoria(relleno(JPEG, 100));
    assertThat(jdbc.queryForObject("SELECT content_type FROM academy_images", String.class))
        .isEqualTo("image/jpeg");

    for (byte[] falso : new byte[][] {GIF, SVG, TEXTO}) {
      mvc.perform(subirACategoria(categoria, falso, "foto.png", MediaType.IMAGE_PNG_VALUE))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-003"))
          .andExpect(jsonPath("$.errors[0].field").value("file"));
    }
  }

  @Test
  @DisplayName(
      "`CA-AC-159` y `CA-AC-160` — 5 242 881 bytes es VAL-004 y 5 242 880 se admite; sin parte,"
          + " VAL-002; sin multipart, 400; 404 a la inexistente y a la retirada; lo rechazado no deja"
          + " fila")
  void losRechazos() throws Exception {
    mvc.perform(subirACategoria(categoria, relleno(PNG, TOPE + 1), "g.png", "image/png"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM academy_images", Integer.class)).isZero();

    mvc.perform(
            multipart(HttpMethod.PUT, "/api/v1/course-categories/{id}/cover", categoria)
                .with(con("course-categories:update")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(
            put("/api/v1/course-categories/{id}/cover", categoria)
                .with(con("course-categories:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().is4xxClientError());

    mvc.perform(subirACategoria(UUID.randomUUID(), PNG, "a.png", "image/png"))
        .andExpect(status().isNotFound());
    CourseCategoryTestSupport.retirar(jdbc, categoria);
    mvc.perform(subirACategoria(categoria, PNG, "a.png", "image/png"))
        .andExpect(status().isNotFound());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM academy_images", Integer.class)).isZero();

    UUID otra = CourseCategoryTestSupport.categoria(jdbc, "Mentalidad", 1);
    mvc.perform(subirACategoria(otra, relleno(PNG, TOPE), "g.png", "image/png"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-AC-162` — las tres cover_image_id tienen clave foránea y unicidad desde V44")
  void elEsquema() throws Exception {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE courses SET cover_image_id = ? WHERE id = ?", UUID.randomUUID(), velas))
        .hasMessageContaining("fk_courses_cover_image");
    // La unicidad es por columna: dos cursos no comparten portada.
    UUID imagen = portadaDeCategoria(PNG);
    jdbc.update("UPDATE course_categories SET cover_image_id = NULL WHERE id = ?", categoria);
    jdbc.update("UPDATE courses SET cover_image_id = ? WHERE id = ?", imagen, velas);
    UUID otro = curso(jdbc, "Otro", CourseTestSupport.instructor(jdbc), 1);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE courses SET cover_image_id = ? WHERE id = ?", imagen, otro))
        .hasMessageContaining("uq_courses_cover_image");
    for (String restriccion :
        new String[] {
          "fk_course_categories_cover_image", "uq_course_categories_cover_image",
          "fk_course_modules_cover_image", "uq_course_modules_cover_image"
        }) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM pg_constraint WHERE conname = ?",
                  Integer.class,
                  restriccion))
          .isEqualTo(1);
    }
  }

  // --- RF-AC-032 -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-163` — sin token: los bytes exactos, el tipo detectado y las cabeceras de caché; con"
          + " token responde lo mismo; una sola sentencia")
  void sirveLaImagenSinToken() throws Exception {
    byte[] jpeg = relleno(JPEG, 4_096);
    UUID imagen = portadaDeCategoria(jpeg);

    estadisticas.clear();
    MockHttpServletResponse respuesta =
        mvc.perform(get("/api/v1/academy-images/" + imagen))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/jpeg"))
            .andExpect(header().string("Content-Length", String.valueOf(jpeg.length)))
            .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn()
            .getResponse();
    assertThat(respuesta.getContentAsByteArray()).isEqualTo(jpeg);
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);

    mvc.perform(get("/api/v1/academy-images/" + imagen).with(con("courses:read")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/jpeg"));
  }

  @Test
  @DisplayName(
      "`CA-AC-164` y `CA-AC-165` — 404 al inexistente y al reemplazado, 400 al mal formado; sirve la"
          + " portada de una categoría retirada")
  void cuatroCientosYRetiradas() throws Exception {
    mvc.perform(get("/api/v1/academy-images/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
    UUID vieja = portadaDeCategoria(PNG);
    UUID nueva = portadaDeCategoria(JPEG);
    mvc.perform(get("/api/v1/academy-images/" + vieja)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/academy-images/no-es-uuid")).andExpect(status().isBadRequest());

    CourseCategoryTestSupport.retirar(jdbc, categoria);
    mvc.perform(get("/api/v1/academy-images/" + nueva)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-AC-166` — la ruta es pública solo en GET: un PUT sin token responde 401")
  void soloElGet() throws Exception {
    UUID imagen = portadaDeCategoria(PNG);
    mvc.perform(put("/api/v1/academy-images/" + imagen)).andExpect(status().isUnauthorized());
  }

  // --- RF-AC-014 -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-172` y `CA-AC-173` — sube y reemplaza la portada de un curso INACTIVO y vacío; el"
          + " detalle y el listado traen la dirección nueva; la anterior no existe; UPDATE de courses")
  void subeLaPortadaDeUnCurso() throws Exception {
    mvc.perform(subirACurso(velas, PNG, con("courses:update")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"));
    UUID primera =
        jdbc.queryForObject("SELECT cover_image_id FROM courses WHERE id = ?", UUID.class, velas);

    mvc.perform(subirACurso(velas, JPEG, con("courses:update")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(matchesPattern("^/api/v1/academy-images/.+")));
    UUID segunda =
        jdbc.queryForObject("SELECT cover_image_id FROM courses WHERE id = ?", UUID.class, velas);
    assertThat(segunda).isNotEqualTo(primera);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM academy_images WHERE id = ?", Integer.class, primera))
        .isZero();

    mvc.perform(get("/api/v1/courses/" + velas).with(con("courses:read")))
        .andExpect(jsonPath("$.coverImageUrl").value("/api/v1/academy-images/" + segunda));
    mvc.perform(get("/api/v1/courses").with(con("courses:read")))
        .andExpect(
            jsonPath("$.content[0].coverImageUrl").value("/api/v1/academy-images/" + segunda));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'AC' AND entity = 'courses'"
                    + " AND entity_id = ? AND action = 'UPDATE' AND changes::text LIKE"
                    + " '%cover_image_id%'",
                Integer.class, velas))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-AC-174` y `CA-AC-175` — los rechazos del archivo sin dejar fila; 404 al curso retirado; 403"
          + " sin courses:update aunque porte course-categories:update")
  void rechazosDelCurso() throws Exception {
    mvc.perform(subirACurso(velas, GIF, con("courses:update")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM academy_images", Integer.class)).isZero();

    mvc.perform(subirACurso(velas, PNG, con("course-categories:update")))
        .andExpect(status().isForbidden());
    CourseTestSupport.retirar(jdbc, velas);
    mvc.perform(subirACurso(velas, PNG, con("courses:update"))).andExpect(status().isNotFound());
  }

  // --- apoyo ---------------------------------------------------------------

  private UUID portadaDeCategoria(byte[] bytes) throws Exception {
    mvc.perform(subirACategoria(categoria, bytes, "foto", MediaType.IMAGE_PNG_VALUE))
        .andExpect(status().isOk());
    return jdbc.queryForObject(
        "SELECT cover_image_id FROM course_categories WHERE id = ?", UUID.class, categoria);
  }

  private org.springframework.test.web.servlet.RequestBuilder subirACategoria(
      UUID id, byte[] bytes, String nombre, String tipo) {
    return multipart(HttpMethod.PUT, "/api/v1/course-categories/{id}/cover", id)
        .file(new MockMultipartFile("file", nombre, tipo, bytes))
        .with(con("course-categories:update"));
  }

  private org.springframework.test.web.servlet.RequestBuilder subirACurso(
      UUID id,
      byte[] bytes,
      org.springframework.test.web.servlet.request.RequestPostProcessor actor) {
    return multipart(HttpMethod.PUT, "/api/v1/courses/{id}/cover", id)
        .file(new MockMultipartFile("file", "portada.png", "image/png", bytes))
        .with(actor);
  }

  /** La firma seguida de ceros hasta {@code tamano} bytes. */
  static byte[] relleno(byte[] firma, int tamano) {
    byte[] bytes = Arrays.copyOf(firma, Math.max(tamano, firma.length));
    return bytes;
  }
}
