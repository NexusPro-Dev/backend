package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.eliminar;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.persona;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenencia;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenenciaCerrada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El detalle de un equipo (`RF-SP-065` · `T-08`, `T-09`, `T-12` y `T-13`): `CA-SP-748` a
 * `CA-SP-755`, `CA-SP-993` y `CA-SP-994`.
 *
 * <p><b>Desde el 09-10-2026 un equipo tiene como mucho un director vigente</b> (`RN-SP-052`), y el
 * fixture lo respeta: el equipo principal tiene a su director y, en el historial, dos pertenencias
 * <b>cerradas</b>; el inactivo tiene a un director desactivado —el {@code status} es el de la
 * persona— y otra cerrada; un tercero solo tiene historial. Ninguna pertenencia cerrada puede salir
 * en {@code members}.
 *
 * <p>Los dos equipos eliminados son dos casos distintos a propósito: uno con su registro de
 * auditoría y otro <b>sin él</b>, porque el detalle no puede caerse por un hueco en la auditoría
 * (`FA-004`).
 */
@AutoConfigureMockMvc
class TeamDetailIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;

  /** Nombres propios de esta clase: {@code users} se comparte con la semilla y con otras suites. */
  private static final String[] GENTE = {
    "detalleequipo1",
    "detalleequipo2",
    "detalleequipo3",
    "detalleequipo4",
    "detalleequipo5",
    "detalleequipo6",
    "detalleequipo7"
  };

  private UUID norte;
  private UUID sur;
  private UUID sinDirector;
  private UUID eliminadoConMotivo;
  private UUID eliminadoSinRegistro;
  private UUID directorDelNorte;
  private UUID directorDelSur;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);

    norte = equipo(jdbc, "Equipo Norte", "ACTIVO", "La oficina del norte");
    sur = equipo(jdbc, "Equipo Sur", "INACTIVO", null);
    sinDirector = equipo(jdbc, "Equipo Sin Director");
    eliminadoConMotivo = equipo(jdbc, "Equipo Disuelto");
    eliminadoSinRegistro = equipo(jdbc, "Equipo Sin Rastro");

    directorDelNorte = persona(jdbc, GENTE[0]);
    UUID exDelNorte = persona(jdbc, GENTE[1]);
    UUID otroExDelNorte = persona(jdbc, GENTE[2]);
    directorDelSur = persona(jdbc, GENTE[3]);
    UUID exDelSur = persona(jdbc, GENTE[4]);
    UUID exDelVacio = persona(jdbc, GENTE[5]);
    UUID otroExDelVacio = persona(jdbc, GENTE[6]);

    // El norte: su director vigente, y dos que estuvieron.
    pertenencia(jdbc, norte, directorDelNorte);
    pertenenciaCerrada(jdbc, norte, exDelNorte);
    pertenenciaCerrada(jdbc, norte, otroExDelNorte);

    // El sur, INACTIVO, con un director desactivado: sigue dentro.
    pertenencia(jdbc, sur, directorDelSur);
    pertenenciaCerrada(jdbc, sur, exDelSur);
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", directorDelSur);

    // Sin director vigente, con varios en el historial (`CA-SP-993`).
    pertenenciaCerrada(jdbc, sinDirector, exDelVacio);
    pertenenciaCerrada(jdbc, sinDirector, otroExDelVacio);

    eliminar(jdbc, eliminadoConMotivo);
    jdbc.update(
        """
        INSERT INTO audit_deletion_log (id, occurred_at, actor_id, module, entity, entity_id,
                                        deletion_type, reason, snapshot)
        VALUES (gen_random_uuid(), now(), NULL, 'SP', 'teams', ?, 'LOGICAL',
                'La región se reorganizó.', '{}'::jsonb)
        """,
        eliminadoConMotivo);

    // Eliminado a mano y SIN registro: el hueco en la auditoría que `FA-004`
    // exige que no reviente la lectura.
    eliminar(jdbc, eliminadoSinRegistro);

    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);
  }

  @Test
  @DisplayName(
      "`CA-SP-748` y `CA-SP-993` — la ficha entera, y members con UNA persona: su director, con"
          + " sus seis columnas, y memberCount en uno")
  void laFichaYSuDirector() throws Exception {
    mvc.perform(abrir(norte))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(norte.toString()))
        .andExpect(jsonPath("$.name").value("Equipo Norte"))
        .andExpect(jsonPath("$.description").value("La oficina del norte"))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.memberCount").value(1))
        .andExpect(jsonPath("$.members", hasSize(1)))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists())
        .andExpect(jsonPath("$.deletedAt").doesNotExist())
        .andExpect(jsonPath("$.deletionReason").doesNotExist())
        .andExpect(jsonPath("$.members[0].id").value(directorDelNorte.toString()))
        .andExpect(jsonPath("$.members[0].username").value(GENTE[0]))
        .andExpect(jsonPath("$.members[0].firstName").value("Persona"))
        .andExpect(jsonPath("$.members[0].lastName").value("De prueba"))
        .andExpect(jsonPath("$.members[0].status").value("ACTIVO"))
        .andExpect(jsonPath("$.members[0].joinedAt").exists());
  }

  @Test
  @DisplayName(
      "`CA-SP-993` — sin director vigente, members vacío y memberCount en cero, aunque el historial"
          + " tenga varios directores cerrados")
  void sinDirectorVigente() throws Exception {
    mvc.perform(abrir(sinDirector))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberCount").value(0))
        .andExpect(jsonPath("$.members", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-SP-750` — members trae SOLO al vigente: ni quien tuvo una pertenencia cerrada aquí ni"
          + " quien hoy está en otro equipo")
  void soloElVigente() throws Exception {
    mvc.perform(abrir(norte))
        .andExpect(jsonPath("$.members", hasSize(1)))
        .andExpect(jsonPath("$.members[?(@.username == '" + GENTE[1] + "')]", hasSize(0)))
        .andExpect(jsonPath("$.members[?(@.username == '" + GENTE[3] + "')]", hasSize(0)));

    mvc.perform(abrir(sur))
        .andExpect(jsonPath("$.members", hasSize(1)))
        .andExpect(jsonPath("$.members[?(@.username == '" + GENTE[4] + "')]", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-SP-751` — memberCount coincide con el tamaño de members y con el número que da el"
          + " listado para el mismo equipo")
  void elRecuentoCuadraConLosDosSitios() throws Exception {
    mvc.perform(abrir(norte))
        .andExpect(jsonPath("$.memberCount").value(1))
        .andExpect(jsonPath("$.members", hasSize(1)));

    mvc.perform(get("/api/v1/teams").param("q", "Equipo Norte").with(con("teams:list")))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].memberCount").value(1));
  }

  @Test
  @DisplayName(
      "`CA-SP-752` — el INACTIVO se devuelve con su director, desactivado; el eliminado, con"
          + " deletedAt, deletionReason y members vacío; el inexistente es 404 y el uuid mal"
          + " formado, 400")
  void inactivoEliminadoInexistenteYMalFormado() throws Exception {
    mvc.perform(abrir(sur))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.description").value(nullValue()))
        .andExpect(jsonPath("$.memberCount").value(1))
        .andExpect(jsonPath("$.members", hasSize(1)))
        // El status es el de la PERSONA: un director desactivado sigue dentro.
        .andExpect(jsonPath("$.members[0].id").value(directorDelSur.toString()))
        .andExpect(jsonPath("$.members[0].status").value("INACTIVO"));

    mvc.perform(abrir(eliminadoConMotivo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.deletionReason").value("La región se reorganizó."))
        .andExpect(jsonPath("$.memberCount").value(0))
        .andExpect(jsonPath("$.members", hasSize(0)));

    mvc.perform(abrir(UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un equipo con ese identificador."));

    mvc.perform(get("/api/v1/teams/no-es-un-uuid").with(con("teams:read")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`CA-SP-753` — un equipo eliminado SIN registro de eliminación responde 200 con el motivo"
          + " ausente, y no 500")
  void eliminadoSinRegistroDeAuditoria() throws Exception {
    mvc.perform(abrir(eliminadoSinRegistro))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        // OMITIDO, no presente en nulo: `deletionReason` es NON_NULL en la
        // respuesta —lo era ya cuando `RF-SP-063` la escribió— y el sistema entero
        // publica así los campos ausentes; `RF-AC-003` afirma exactamente lo mismo
        // para la categoría retirada sin registro. Lo que el criterio protege
        // —que un hueco en la auditoría NO tumbe la lectura— se verifica entero.
        .andExpect(jsonPath("$.deletionReason").doesNotExist())
        .andExpect(jsonPath("$.members", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-SP-994` — el número de sentencias es fijo: dos en el vivo, con director o sin él, y no"
          + " más de tres en el eliminado")
  void lasSentenciasSonFijas() throws Exception {
    estadisticas.clear();
    mvc.perform(abrir(norte)).andExpect(jsonPath("$.members", hasSize(1)));
    long conDirector = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    mvc.perform(abrir(sinDirector)).andExpect(jsonPath("$.members", hasSize(0)));
    long sinElDirector = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    mvc.perform(abrir(eliminadoConMotivo)).andExpect(status().isOk());
    long delEliminado = estadisticas.getPrepareStatementCount();

    assertThat(conDirector).isEqualTo(2);
    // Sin vigentes, el lector se ahorra la consulta de miembros y lee igual dos:
    // la ficha y, en su lugar, nada más —o el motivo, si está eliminado—.
    assertThat(sinElDirector).isLessThanOrEqualTo(2);
    // DOS, y no las tres que el criterio anticipa: un equipo eliminado nunca
    // tiene vigentes (`RN-SP-054`), y la consulta del motivo sustituye a la de
    // los miembros en vez de sumarse. Se comprueba el valor de hoy y el techo.
    assertThat(delEliminado).isEqualTo(2);
    assertThat(delEliminado).isLessThanOrEqualTo(3);
  }

  @Test
  @DisplayName("`CA-SP-755` — sin teams:read responde 403 aunque el actor porte teams:list")
  void permisoPropio() throws Exception {
    mvc.perform(get("/api/v1/teams/" + norte).with(con("teams:list")))
        .andExpect(status().isForbidden());

    mvc.perform(abrir(norte)).andExpect(status().isOk());
  }

  private MockHttpServletRequestBuilder abrir(UUID id) {
    return get("/api/v1/teams/" + id).with(con("teams:read"));
  }
}
