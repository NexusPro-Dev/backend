package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.instante;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.zoom.FakeZoomMeetings;
import com.factech.nexus.shared.zoom.ZoomMeetings.Frequency;
import com.factech.nexus.shared.zoom.ZoomMeetings.MeetingSpec;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
 * Las clases en vivo que se repiten: `RF-AC-055` (`CA-AC-306` a `CA-AC-311`, `RN-AC-031`). Zoom es
 * el doble de la suite; las series ya empezadas se escriben directo en la tabla.
 */
@AutoConfigureMockMvc
class LiveSessionRecurrenceIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/live-sessions";
  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

  /** El 15 del mes después del siguiente: futuro, y un día que tienen todos los meses. */
  private static final LocalDate DIA = LocalDate.now(BOGOTA).plusMonths(2).withDayOfMonth(15);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeZoomMeetings zoom;

  private UUID admin;
  private UUID alumno;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
    admin = CourseTestSupport.persona(jdbc, "Ada", "Admin", ProgressTestSupport.ADMIN);
    alumno = ClassroomTestSupport.alumno(jdbc);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
  }

  private static String cuerpo(LocalDate dia, String repeticion) {
    return """
        {"title":"Serie","startsAt":"%sT19:00:00","endsAt":"%sT20:00:00"%s}
        """
        .formatted(dia, dia, repeticion.isEmpty() ? "" : "," + repeticion);
  }

  private ResultActions programar(String cuerpo) throws Exception {
    return mvc.perform(
        post(RUTA)
            .with(con(admin, "live-sessions:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions corregir(UUID id, String cuerpo) throws Exception {
    return mvc.perform(
        patch(RUTA + "/" + id)
            .with(con(admin, "live-sessions:update"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  /** La única reunión del doble: cada prueba lo reinicia antes de programar. */
  private MeetingSpec ultimaReunion() {
    assertThat(zoom.reuniones()).hasSize(1);
    return zoom.reuniones().values().iterator().next();
  }

  private static OffsetDateTime a(LocalDate dia, int hora) {
    return dia.atTime(hora, 0).atZone(BOGOTA).toOffsetDateTime();
  }

  @Test
  @DisplayName(
      "`CA-AC-306` — diaria, semanal con el día de la semana del inicio y mensual con el día del"
          + " mes: una reunión recurrente con el número de fechas")
  void programa() throws Exception {
    String diaria =
        programar(
                cuerpo(DIA, "\"recurrence\":\"DAILY\",\"repeatUntil\":\"" + DIA.plusDays(4) + "\""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recurrence").value("DAILY"))
            .andExpect(jsonPath("$.repeatUntil").value(DIA.plusDays(4).toString()))
            .andExpect(jsonPath("$.ended").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(instante(diaria, "$.nextStartsAt")).isEqualTo(a(DIA, 19));
    assertThat(instante(diaria, "$.nextEndsAt")).isEqualTo(a(DIA, 20));
    MeetingSpec reunion = ultimaReunion();
    assertThat(reunion.repeat().frequency()).isEqualTo(Frequency.DAILY);
    assertThat(reunion.repeat().times()).isEqualTo(5);
    assertThat(reunion.durationMinutes()).isEqualTo(60);
    UUID id = UUID.fromString(JsonPath.read(diaria, "$.id"));
    assertThat(
            jdbc.queryForObject(
                "SELECT series_ends_at FROM live_sessions WHERE id = ?", OffsetDateTime.class, id))
        .isEqualTo(a(DIA.plusDays(4), 20));

    zoom.reiniciar();
    programar(cuerpo(DIA, "\"recurrence\":\"WEEKLY\",\"repeatUntil\":\"" + DIA.plusDays(21) + "\""))
        .andExpect(status().isCreated());
    assertThat(ultimaReunion().repeat().times()).isEqualTo(4);
    assertThat(ultimaReunion().repeat().dayOfWeek()).isEqualTo(DIA.getDayOfWeek());

    zoom.reiniciar();
    programar(
            cuerpo(DIA, "\"recurrence\":\"MONTHLY\",\"repeatUntil\":\"" + DIA.plusMonths(3) + "\""))
        .andExpect(status().isCreated());
    assertThat(ultimaReunion().repeat().times()).isEqualTo(4);
    assertThat(ultimaReunion().repeat().dayOfMonth()).isEqualTo(15);
  }

  @Test
  @DisplayName(
      "`CA-AC-307` — sin repetición, la de siempre; frecuencia desconocida, fecha final ausente,"
          + " mal formada o sola, menos de 2 o más de 60 fechas, mensual después del 28: 400")
  void valida() throws Exception {
    programar(cuerpo(DIA, ""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recurrence").value(nullValue()))
        .andExpect(jsonPath("$.repeatUntil").value(nullValue()));
    assertThat(ultimaReunion().repeat()).isNull();
    zoom.reiniciar();

    programar(cuerpo(DIA, "\"recurrence\":\"YEARLY\",\"repeatUntil\":\"" + DIA.plusDays(3) + "\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("recurrence"));
    programar(cuerpo(DIA, "\"recurrence\":\"NONE\",\"repeatUntil\":\"" + DIA.plusDays(3) + "\""))
        .andExpect(status().isBadRequest());
    programar(cuerpo(DIA, "\"recurrence\":\"DAILY\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[0].field").value("repeatUntil"));
    programar(cuerpo(DIA, "\"recurrence\":\"DAILY\",\"repeatUntil\":\"15-12-2026\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("repeatUntil"));
    programar(cuerpo(DIA, "\"repeatUntil\":\"" + DIA.plusDays(3) + "\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    programar(cuerpo(DIA, "\"recurrence\":\"DAILY\",\"repeatUntil\":\"" + DIA + "\""))
        .andExpect(status().isBadRequest());
    programar(cuerpo(DIA, "\"recurrence\":\"DAILY\",\"repeatUntil\":\"" + DIA.plusDays(60) + "\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("repeatUntil"));
    LocalDate dia29 = DIA.withDayOfMonth(29);
    programar(
            cuerpo(
                dia29,
                "\"recurrence\":\"MONTHLY\",\"repeatUntil\":\"" + dia29.plusMonths(2) + "\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("startsAt"));
    assertThat(zoom.llamadas()).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM live_sessions", Long.class)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-308` — corregir la fecha final o la frecuencia corrige la reunión; NONE la quita; se"
          + " audita")
  void corrige() throws Exception {
    String creada =
        programar(
                cuerpo(
                    DIA, "\"recurrence\":\"WEEKLY\",\"repeatUntil\":\"" + DIA.plusDays(14) + "\""))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(JsonPath.read(creada, "$.id"));
    long reunion = ((Number) JsonPath.read(creada, "$.zoomMeetingId")).longValue();
    zoom.reiniciar();

    corregir(id, "{\"repeatUntil\":\"" + DIA.plusDays(35) + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repeatUntil").value(DIA.plusDays(35).toString()));
    assertThat(zoom.llamadas()).containsExactly("update:" + reunion);
    assertThat(zoom.reuniones().get(reunion).repeat().times()).isEqualTo(6);

    // Cambiar la frecuencia conserva la fecha final: de semanal a diaria, 36 fechas.
    corregir(id, "{\"recurrence\":\"DAILY\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repeatUntil").value(DIA.plusDays(35).toString()));
    assertThat(zoom.reuniones().get(reunion).repeat().times()).isEqualTo(36);
    corregir(id, "{\"recurrence\":\"DAILY\",\"repeatUntil\":\"" + DIA.plusDays(9) + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recurrence").value("DAILY"));
    assertThat(zoom.reuniones().get(reunion).repeat().times()).isEqualTo(10);

    corregir(id, "{\"recurrence\":\"NONE\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recurrence").value(nullValue()))
        .andExpect(jsonPath("$.repeatUntil").value(nullValue()));
    assertThat(zoom.reuniones().get(reunion).repeat()).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT series_ends_at = ends_at FROM live_sessions WHERE id = ?",
                Boolean.class,
                id))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'live_sessions'"
                    + " AND entity_id = ? AND action = 'UPDATE' AND (changes -> 'repeatUntil' IS NOT NULL OR changes -> 'recurrence' IS NOT NULL)",
                Long.class,
                id))
        .isEqualTo(4);
  }

  @Test
  @DisplayName(
      "`CA-AC-309` — una serie empezada no ha terminado: se corrige sin inicio nuevo y entrar da la"
          + " fecha en curso; pasada la última, terminada y 409")
  void empezada() throws Exception {
    UUID serie = LiveSessionTestSupport.serieDiaria(jdbc, admin, -10, 50, 3, 2, 31L);
    OffsetDateTime hoy = OffsetDateTime.now(ZoneOffset.ofHours(-5));

    String detalle =
        mvc.perform(get(RUTA + "/" + serie).with(con(admin, "live-sessions:read")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ended").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(instante(detalle, "$.nextStartsAt").toLocalDate())
        .isEqualTo(hoy.plusMinutes(-10).toLocalDate());
    assertThat(instante(detalle, "$.nextStartsAt")).isBefore(hoy);
    assertThat(instante(detalle, "$.startsAt")).isBefore(hoy.minusDays(2));

    corregir(serie, "{\"repeatUntil\":\"" + hoy.plusDays(5).toLocalDate() + "\"}")
        .andExpect(status().isOk());

    String entrada =
        mvc.perform(
                post(RUTA + "/available/" + serie + "/registration")
                    .with(con(alumno, "live-sessions:join")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(instante(entrada, "$.startsAt"))
        .isEqualTo(instante(detalle, "$.nextStartsAt"))
        .isBefore(hoy);
    assertThat(instante(entrada, "$.endsAt")).isAfter(hoy);

    UUID terminada = LiveSessionTestSupport.serieDiaria(jdbc, admin, -10, 50, 5, -1, 32L);
    mvc.perform(get(RUTA + "/" + terminada).with(con(admin, "live-sessions:read")))
        .andExpect(jsonPath("$.ended").value(true))
        .andExpect(jsonPath("$.nextStartsAt").value(nullValue()));
    corregir(terminada, "{\"title\":\"Tarde\"}").andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "`CA-AC-310` — la vitrina trae la serie hasta su última fecha, en curso si lo está alguna, y"
          + " por la fecha siguiente")
  void vitrina() throws Exception {
    UUID sola = LiveSessionTestSupport.clase(jdbc, null, admin, 60, 120, 41L);
    UUID enCurso = LiveSessionTestSupport.serieDiaria(jdbc, admin, -10, 50, 3, 2, 42L);
    UUID manana = LiveSessionTestSupport.serieDiaria(jdbc, admin, -200, -140, 2, 2, 43L);
    LiveSessionTestSupport.serieDiaria(jdbc, admin, -10, 50, 5, -1, 44L);

    mvc.perform(get(RUTA + "/available").with(con(alumno, "live-sessions:learn")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].id")
                .value(contains(enCurso.toString(), sola.toString(), manana.toString())))
        .andExpect(jsonPath("$[0].inProgress").value(true))
        .andExpect(jsonPath("$[0].recurrence").value("DAILY"))
        .andExpect(jsonPath("$[2].inProgress").value(false));
  }

  @Test
  @DisplayName(
      "`CA-AC-311` — el listado: terminada por la última fecha, y el rango trae la serie que se"
          + " cruza con él")
  void listado() throws Exception {
    // Una clase sola de hoy: no cae en el rango de mañana.
    LiveSessionTestSupport.clase(jdbc, null, admin, 60, 120, 51L);
    UUID viva = LiveSessionTestSupport.serieDiaria(jdbc, admin, -200, -140, 2, 2, 52L);
    UUID terminada = LiveSessionTestSupport.serieDiaria(jdbc, admin, -10, 50, 5, -1, 53L);

    mvc.perform(get(RUTA).param("ended", "true").with(con(admin, "live-sessions:list")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(terminada.toString()))
        .andExpect(jsonPath("$.content[0].nextStartsAt").value(nullValue()));
    mvc.perform(get(RUTA).param("ended", "false").with(con(admin, "live-sessions:list")))
        .andExpect(jsonPath("$.totalElements").value(2));

    OffsetDateTime manana = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
    mvc.perform(
            get(RUTA)
                .param("from", manana.toString())
                .param("to", manana.plusHours(1).toString())
                .with(con(admin, "live-sessions:list")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(viva.toString()))
        .andExpect(jsonPath("$.content[0].recurrence").value("DAILY"));
  }
}
