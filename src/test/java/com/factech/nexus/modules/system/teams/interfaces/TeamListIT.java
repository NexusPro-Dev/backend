package com.factech.nexus.modules.system.teams.interfaces;

import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.con;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.eliminar;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.equipo;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.persona;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenencia;
import static com.factech.nexus.modules.system.teams.interfaces.TeamTestSupport.pertenenciaCerrada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.teams.domain.repository.TeamQueryRepository;
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
 * El listado de equipos (`RF-SP-064` · `T-07` y `T-08`): `CA-SP-740` a `CA-SP-747`.
 *
 * <p><b>Un solo fixture hace verificables cuatro criterios a la vez</b>: cuatro equipos —dos
 * activos, uno inactivo con miembros y uno eliminado—, dos nombres que se solapan en la subcadena
 * «norte» con acento y sin él, y un equipo con <b>dos pertenencias cerradas y una abierta</b>, que
 * es lo que distingue «cuántos hay» de «cuántos pasaron».
 *
 * <p>Las filas de {@code team_members} las escribe el fixture a mano: `RF-SP-069` todavía no existe
 * y el recuento ya es el definitivo. Es lo que hizo `RF-SP-061` con las filas `HOTLINK`.
 */
@AutoConfigureMockMvc
class TeamListIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private TeamQueryRepository consultas;

  private Statistics estadisticas;

  /**
   * Nombres propios de esta clase, y no {@code manager1}: las personas viven en una tabla que esta
   * suite comparte con la semilla de desarrollo y con las demás clases, de modo que un nombre
   * genérico choca contra {@code uq_users_email} sin decir de dónde viene el choque.
   */
  private static final String[] MANAGERS = {
    "listadoequipos1", "listadoequipos2", "listadoequipos3", "listadoequipos4"
  };

  private UUID norte;
  private UUID regionNorte;
  private UUID sur;
  private UUID viejo;

  @BeforeEach
  void sembrar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, MANAGERS);

    norte = equipo(jdbc, "Equipo Norte");
    regionNorte = equipo(jdbc, "Región NORTE");
    sur = equipo(jdbc, "Equipo Sur", "INACTIVO", "El del sur");
    viejo = equipo(jdbc, "Equipo Viejo");

    UUID primero = persona(jdbc, MANAGERS[0]);
    UUID segundo = persona(jdbc, MANAGERS[1]);
    UUID tercero = persona(jdbc, MANAGERS[2]);
    UUID cuarto = persona(jdbc, MANAGERS[3]);

    // «Equipo Norte» con DOS cerradas y UNA abierta: dice uno, no tres.
    pertenenciaCerrada(jdbc, norte, primero);
    pertenenciaCerrada(jdbc, norte, segundo);
    pertenencia(jdbc, norte, tercero);

    // El INACTIVO conserva a los suyos y los sigue contando (`RN-SP-053`).
    pertenencia(jdbc, sur, primero);
    pertenencia(jdbc, sur, segundo);

    // El eliminado solo puede tener historial: `RN-SP-054` impide eliminarlo con
    // vigentes, de modo que su recuento es cero por construcción.
    pertenenciaCerrada(jdbc, viejo, cuarto);
    eliminar(jdbc, viejo);

    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void limpiar() {
    TeamTestSupport.limpiar(jdbc);
    TeamTestSupport.borrarPersonas(jdbc, MANAGERS);
  }

  @Test
  @DisplayName(
      "`CA-SP-740` — la fila trae id, name, status, memberCount y createdAt, sin deletedAt en los"
          + " vivos, y el recuento cuadra con los miembros vigentes de cada equipo")
  void laFilaYSuRecuento() throws Exception {
    mvc.perform(listar(""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalIsExact").value(true))
        .andExpect(jsonPath("$.content[0].id").value(norte.toString()))
        .andExpect(jsonPath("$.content[0].name").value("Equipo Norte"))
        .andExpect(jsonPath("$.content[0].status").value("ACTIVO"))
        .andExpect(jsonPath("$.content[0].memberCount").value(1))
        .andExpect(jsonPath("$.content[0].createdAt").exists())
        .andExpect(jsonPath("$.content[0].deletedAt").doesNotExist())
        // Ni descripción ni miembros: eso es el detalle (`RF-SP-065`).
        .andExpect(jsonPath("$.content[0].description").doesNotExist())
        .andExpect(jsonPath("$.content[0].members").doesNotExist());

    // Lo que publica la fila y lo que devolverá el detalle salen de la misma
    // verdad: el recuento del listado contra los miembros vigentes de cada
    // equipo. Cuando `RF-SP-065` exista, esta comparación se hará contra su
    // respuesta, que es lo que `CA-SP-740` pide en su forma final.
    assertThat(consultas.findActiveMembers(norte)).hasSize(1);
    assertThat(consultas.findActiveMembers(sur)).hasSize(2);
    assertThat(consultas.findActiveMembers(regionNorte)).isEmpty();
  }

  @Test
  @DisplayName(
      "`CA-SP-741` — el orden por omisión es el nombre ascendente sin acentos; createdAt se admite"
          + " y cualquier otro campo es 400 (`VAL-003`)")
  void ordenPorNombre() throws Exception {
    mvc.perform(listar("?includeDeleted=true"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[*].name")
                .value(contains("Equipo Norte", "Equipo Sur", "Equipo Viejo", "Región NORTE")));

    mvc.perform(listar("?sort=createdAt")).andExpect(status().isOk());
    mvc.perform(listar("?sort=name,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Región NORTE"));

    mvc.perform(listar("?sort=memberCount"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"))
        .andExpect(jsonPath("$.errors[0].field").value("sort"));
  }

  @Test
  @DisplayName(
      "`CA-SP-742` — los eliminados quedan fuera salvo includeDeleted=true, y entonces traen su"
          + " deletedAt y memberCount en cero")
  void eliminadosFueraSalvoQueSePidan() throws Exception {
    mvc.perform(listar(""))
        .andExpect(
            jsonPath("$.content[*].id")
                .value(org.hamcrest.Matchers.not(hasItems(viejo.toString()))))
        .andExpect(jsonPath("$.totalElements").value(3));

    mvc.perform(listar("?includeDeleted=true"))
        .andExpect(jsonPath("$.content", hasSize(4)))
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[2].name").value("Equipo Viejo"))
        .andExpect(jsonPath("$.content[2].deletedAt").exists())
        .andExpect(jsonPath("$.content[2].memberCount").value(0));

    mvc.perform(listar("?includeDeleted=quiza"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"))
        .andExpect(jsonPath("$.errors[0].field").value("includeDeleted"));
  }

  @Test
  @DisplayName(
      "`CA-SP-743` — status acota la página y el total, se admite en cualquier caja, y un valor"
          + " que no existe es 400 (`VAL-002`)")
  void filtroPorEstado() throws Exception {
    mvc.perform(listar("?status=ACTIVO"))
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.totalElements").value(2));

    mvc.perform(listar("?status=inactivo"))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Equipo Sur"));

    // Los dos filtros deciden por separado: pedir los inactivos NO trae el
    // eliminado, aunque estuviera inactivo.
    mvc.perform(listar("?status=ACTIVO&includeDeleted=true"))
        .andExpect(jsonPath("$.content", hasSize(3)));

    mvc.perform(listar("?status=SUSPENDIDO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[0].field").value("status"));
  }

  @Test
  @DisplayName(
      "`CA-SP-744` — la búsqueda acota por contenido, sin distinguir mayúsculas ni acentos:"
          + " «norte» encuentra «Equipo Norte» y «Región NORTE»")
  void busquedaPorContenidoSinAcentos() throws Exception {
    mvc.perform(buscar("norte"))
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[*].name").value(contains("Equipo Norte", "Región NORTE")));

    mvc.perform(buscar("región")).andExpect(jsonPath("$.content", hasSize(1)));
    mvc.perform(buscar("REGION")).andExpect(jsonPath("$.content", hasSize(1)));

    // Un comodín escrito por quien busca es un literal, no un comodín.
    mvc.perform(buscar("%")).andExpect(jsonPath("$.content", hasSize(0)));
    mvc.perform(buscar("zzz")).andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-SP-745` — memberCount cuenta solo los vigentes: dos cerradas y una abierta dicen uno, y"
          + " un equipo INACTIVO sigue contando a los suyos")
  void recuentoDeVigentes() throws Exception {
    mvc.perform(listar("?q=equipo norte")).andExpect(jsonPath("$.content[0].memberCount").value(1));

    mvc.perform(listar("?status=INACTIVO"))
        .andExpect(jsonPath("$.content[0].name").value("Equipo Sur"))
        .andExpect(jsonPath("$.content[0].memberCount").value(2));

    mvc.perform(listar("?q=región")).andExpect(jsonPath("$.content[0].memberCount").value(0));
  }

  @Test
  @DisplayName("`CA-SP-746` — la página de uno y la de veinte cuestan lo mismo: dos sentencias")
  void lasSentenciasNoCrecen() throws Exception {
    estadisticas.clear();
    mvc.perform(listar("?size=1")).andExpect(status().isOk());
    long deUno = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    mvc.perform(listar("?size=20&includeDeleted=true"))
        .andExpect(jsonPath("$.content", hasSize(4)));
    long deVeinte = estadisticas.getPrepareStatementCount();

    assertThat(deUno).isEqualTo(2);
    assertThat(deVeinte).isEqualTo(deUno);
  }

  @Test
  @DisplayName(
      "`CA-SP-747` — sin teams:list responde 403 aunque el actor porte teams:read, y los inválidos"
          + " se devuelven juntos")
  void permisoPropioYErroresJuntos() throws Exception {
    mvc.perform(get("/api/v1/teams").with(con("teams:read"))).andExpect(status().isForbidden());

    mvc.perform(
            get("/api/v1/teams?sort=miembros&size=0&status=NINGUNO&includeDeleted=quiza")
                .with(con("teams:list")))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(hasItems("sort", "size", "status", "includeDeleted")));
  }

  private MockHttpServletRequestBuilder listar(String query) {
    return get("/api/v1/teams" + query).with(con("teams:list"));
  }

  /**
   * La búsqueda va por parámetro y no pegada a la URL: el término lleva acentos y comodines, y
   * escribirlo en la cadena obligaría a codificarlo a mano —que es como se cuela un fallo que
   * parece de la consulta y es del constructor de la petición—.
   */
  private MockHttpServletRequestBuilder buscar(String termino) {
    return get("/api/v1/teams").param("q", termino).with(con("teams:list"));
  }
}
