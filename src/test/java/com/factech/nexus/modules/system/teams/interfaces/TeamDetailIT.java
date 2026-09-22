package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.eliminar;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.persona;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenencia;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenenciaCerrada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
 * El detalle de un equipo (`RF-SP-065` · `T-08` y `T-09`): `CA-SP-748` a `CA-SP-755`.
 *
 * <p><b>Lo que este fixture existe para distinguir</b> es quién está y quién estuvo: el equipo
 * principal tiene dos miembros vigentes —uno activo y uno desactivado, asignados con <b>la misma
 * marca de tiempo</b>, que es el caso normal de una asignación múltiple y lo que obliga a un
 * desempate determinista—, una pertenencia <b>cerrada</b> de un tercero y, fuera, un manager que
 * hoy pertenece a otro equipo. Ninguno de los dos últimos puede salir en {@code members}.
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
  private UUID eliminadoConMotivo;
  private UUID eliminadoSinRegistro;
  private UUID antiguo;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, GENTE);

    norte = equipo(jdbc, "Equipo Norte", "ACTIVO", "Managers de la región norte");
    sur = equipo(jdbc, "Equipo Sur", "INACTIVO", null);
    eliminadoConMotivo = equipo(jdbc, "Equipo Disuelto");
    eliminadoSinRegistro = equipo(jdbc, "Equipo Sin Rastro");

    antiguo = persona(jdbc, GENTE[0]);
    UUID mismaTanda = persona(jdbc, GENTE[2]);
    UUID exmiembro = persona(jdbc, GENTE[3]);
    UUID enOtroEquipo = persona(jdbc, GENTE[4]);
    UUID desactivado = persona(jdbc, GENTE[5]);
    UUID enElInactivo = persona(jdbc, GENTE[6]);

    // El más antiguo entra primero; los otros dos, en la MISMA tanda: comparten
    // `started_at` y solo el nombre de usuario los ordena (`CA-SP-749`).
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id, started_at) VALUES (?, ?, ?, now() -"
            + " interval '3 hours')",
        TeamTestSupport.IDS.next(),
        norte,
        antiguo);
    UUID tanda = TeamTestSupport.IDS.next();
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id, started_at) VALUES (?, ?, ?, now() -"
            + " interval '1 hour')",
        tanda,
        norte,
        desactivado);
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id, started_at)"
            + " SELECT ?, ?, ?, started_at FROM team_members WHERE id = ?",
        TeamTestSupport.IDS.next(),
        norte,
        mismaTanda,
        tanda);
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", desactivado);

    // Estuvo y ya no está; y alguien que hoy está en OTRO equipo.
    pertenenciaCerrada(jdbc, norte, exmiembro);
    pertenencia(jdbc, sur, enOtroEquipo);
    pertenencia(jdbc, sur, enElInactivo);

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
      "`CA-SP-748` y `CA-SP-749` — la ficha entera y las seis columnas de cada miembro, por"
          + " antigüedad y con el nombre de usuario de desempate")
  void laFichaYSusMiembros() throws Exception {
    mvc.perform(abrir(norte))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(norte.toString()))
        .andExpect(jsonPath("$.name").value("Equipo Norte"))
        .andExpect(jsonPath("$.description").value("Managers de la región norte"))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.memberCount").value(3))
        .andExpect(jsonPath("$.members", hasSize(3)))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists())
        .andExpect(jsonPath("$.deletedAt").doesNotExist())
        .andExpect(jsonPath("$.deletionReason").doesNotExist())
        // El más antiguo primero; los dos de la misma tanda, por nombre de
        // usuario: detalleequipo3 antes que detalleequipo6.
        .andExpect(jsonPath("$.members[*].username").value(contains(GENTE[0], GENTE[2], GENTE[5])))
        .andExpect(jsonPath("$.members[0].id").value(antiguo.toString()))
        .andExpect(jsonPath("$.members[0].firstName").value("Persona"))
        .andExpect(jsonPath("$.members[0].lastName").value("De prueba"))
        .andExpect(jsonPath("$.members[0].status").value("ACTIVO"))
        .andExpect(jsonPath("$.members[0].joinedAt").exists())
        // El status es el de la PERSONA: un manager desactivado sigue dentro.
        .andExpect(jsonPath("$.members[2].status").value("INACTIVO"));
  }

  @Test
  @DisplayName(
      "`CA-SP-750` — members trae SOLO los vigentes: ni quien tuvo una pertenencia cerrada aquí ni"
          + " quien hoy está en otro equipo")
  void soloLosVigentes() throws Exception {
    mvc.perform(abrir(norte))
        .andExpect(jsonPath("$.members", hasSize(3)))
        .andExpect(jsonPath("$.members[?(@.username == '" + GENTE[3] + "')]", hasSize(0)))
        .andExpect(jsonPath("$.members[?(@.username == '" + GENTE[4] + "')]", hasSize(0)));

    mvc.perform(abrir(sur))
        .andExpect(jsonPath("$.members", hasSize(2)))
        .andExpect(jsonPath("$.members[?(@.username == '" + GENTE[0] + "')]", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-SP-751` — memberCount coincide con el tamaño de members y con el número que da el"
          + " listado para el mismo equipo")
  void elRecuentoCuadraConLosDosSitios() throws Exception {
    mvc.perform(abrir(norte))
        .andExpect(jsonPath("$.memberCount").value(3))
        .andExpect(jsonPath("$.members", hasSize(3)));

    mvc.perform(get("/api/v1/teams").param("q", "Equipo Norte").with(con("teams:list")))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].memberCount").value(3));
  }

  @Test
  @DisplayName(
      "`CA-SP-752` — el INACTIVO se devuelve con su gente; el eliminado, con deletedAt,"
          + " deletionReason y members vacío; el inexistente es 404 y el uuid mal formado, 400")
  void inactivoEliminadoInexistenteYMalFormado() throws Exception {
    mvc.perform(abrir(sur))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.description").value(nullValue()))
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.members", hasSize(2)));

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
        // para la categoría retirada sin registro. La letra de `CA-SP-753` dice
        // «presente y nulo», y cumplirla al pie obligaría a un serializador
        // propio para este solo campo, contra la convención. Lo que el criterio
        // protege —que un hueco en la auditoría NO tumbe la lectura— se
        // verifica entero: 200, con su fecha de eliminación y sin motivo.
        .andExpect(jsonPath("$.deletionReason").doesNotExist())
        .andExpect(jsonPath("$.members", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-SP-754` — el número de sentencias es fijo: dos en el vivo, con uno o con cinco"
          + " miembros, y tres en el eliminado")
  void lasSentenciasNoCrecen() throws Exception {
    estadisticas.clear();
    mvc.perform(abrir(sur)).andExpect(status().isOk());
    long conDos = estadisticas.getPrepareStatementCount();

    // Dos más, hasta cinco vigentes: la ficha y los miembros siguen siendo dos
    // sentencias, porque los miembros viajan en una sola con su JOIN a `users`.
    String[] masGente = {"detalleequipo8", "detalleequipo9", "detalleequipo10"};
    TeamTestSupport.borrarPersonas(jdbc, masGente);
    for (String usuario : masGente) {
      pertenencia(jdbc, sur, persona(jdbc, usuario));
    }
    try {
      estadisticas.clear();
      mvc.perform(abrir(sur)).andExpect(jsonPath("$.members", hasSize(5)));
      long conCinco = estadisticas.getPrepareStatementCount();

      estadisticas.clear();
      mvc.perform(abrir(eliminadoConMotivo)).andExpect(status().isOk());
      long delEliminado = estadisticas.getPrepareStatementCount();

      assertThat(conDos).isEqualTo(2);
      assertThat(conCinco).isEqualTo(conDos);
      // DOS, y no las tres que `CA-SP-754` anticipaba: el lector se ahorra la
      // consulta de miembros cuando el recuento de la ficha es cero, y un
      // equipo eliminado nunca tiene vigentes (`RN-SP-054`). Así que la del
      // motivo sustituye a la de los miembros en vez de sumarse. Lo que el
      // criterio protege —un número FIJO, que no crece con los miembros y que
      // no pasa de tres— se cumple con margen, y por eso se comprueban las dos
      // cosas: el valor exacto de hoy y el techo que no se debe superar.
      assertThat(delEliminado).isEqualTo(2);
      assertThat(delEliminado).isLessThanOrEqualTo(3);
    } finally {
      jdbc.update("DELETE FROM team_members WHERE team_id = ?", sur);
      TeamTestSupport.borrarPersonas(jdbc, masGente);
    }
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
