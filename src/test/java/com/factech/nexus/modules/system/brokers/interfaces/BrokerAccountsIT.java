package com.factech.nexus.modules.system.brokers.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Las cuentas de broker de una persona y las del equipo (`RF-SP-055`, `RF-SP-056`).
 *
 * <p><b>Las dos van juntas porque son la misma pregunta desde los dos lados</b>, y porque lo que de
 * verdad hay que probar —quién puede ver qué— solo se distingue contrastándolas: la primera se
 * autoriza contra una persona nombrada y la segunda no admite nombrar a nadie.
 *
 * <p><b>Cadena de tres niveles a propósito</b>: {@code jefe} ← {@code medio} ← {@code base}. Sin el
 * tercer nivel no se puede afirmar lo que `CA-SP-645` exige —que el árbol descendente <b>no</b> se
 * publique—, y las dos consultas se parecen lo suficiente como para que la diferencia no se vea
 * leyendo el código.
 *
 * <p><b>El estado se siembra por SQL cuando hace falta {@code FIRST_DEPOSIT}</b>, y no por la API:
 * <b>no hay API que lo mueva</b>. Quien lo hará es el webhook de `RF-SP-054`, que no existe — de
 * modo que sembrarlo es la única manera de probar el filtro por estado sin esperar a que exista.
 */
@AutoConfigureMockMvc
class BrokerAccountsIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID jefe;
  private UUID medio;
  private UUID base;
  private UUID ajeno;
  private UUID cliente;

  private UUID brokerA;
  private UUID brokerB;

  @BeforeEach
  void preparar() {
    jdbc.update("DELETE FROM user_brokers");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM client_sellers");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    jefe = crearPersona("rlopez", MANAGER);
    medio = crearPersona("amartinez", DIRECTOR);
    base = crearPersona("lgarcia", AGENTE);
    ajeno = crearPersona("pruiz", AGENTE);

    reportar(medio, jefe);
    reportar(base, medio);

    // Un CLIENTE, y no está en `user_supervisors` (`RN-SP-028` revertida,
    // 18-09-2026): su principal es la fila REGISTRO de `client_sellers`, y es
    // `ajeno` —a propósito, para que `base` siga sin equipo (`CA-SP-644`)—.
    // `base` le vendió por hotlink: un vínculo que NO le abre nada (`CA-SP-693`).
    cliente = crearPersona("cperez", CLIENTE);
    registrar(cliente, ajeno);
    vincularPorHotlink(cliente, base);

    // Los dos brokers de la siembra de `V76`, REPUESTOS SI NO ESTÁN.
    //
    // No basta con leerlos: `UserBrokerAccountSchemaIT` vacía `brokers` en su
    // `@AfterEach` y NO repone la siembra, de modo que esta clase encuentra el
    // catálogo vacío o lleno según el orden en que la suite decida ejecutarlas
    // — y una prueba que depende del orden falla en la máquina de otro.
    brokerA = brokerAsegurado("01a081f0-6000-7102-9c4f-5e7adb000002", "EXNOVA");
    brokerB = brokerAsegurado("01a081f0-6000-7101-9c4f-5e7adb000001", "IQOPTION");

    declarar(medio, brokerA, "70000001");
    declarar(medio, brokerB, "70000002");
    declarar(base, brokerA, "70000003");
    declarar(ajeno, brokerA, "70000004");
    declarar(cliente, brokerA, "70000005");
  }

  /**
   * Deja {@code user_brokers} <b>vacía</b>, y no es cortesía: es lo que impide romper la suite.
   *
   * <p>{@code fk_user_brokers_user} es {@code RESTRICT}, de modo que una fila que sobreviva a esta
   * clase hace fallar el {@code DELETE FROM users} de <b>todas</b> las que se ejecuten después —y
   * el error señala a la clase equivocada—. <b>Limpiar solo en {@code @BeforeEach} no basta</b>:
   * protege a esta clase y deja el estropicio para las demás.
   */
  @AfterEach
  void vaciar() {
    jdbc.update("DELETE FROM user_brokers");
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-055` — las cuentas de una persona
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-629`, `CA-SP-630` — el superior vigente ve las cuentas, SIN ningún permiso")
  void elSuperiorVigenteVe() throws Exception {
    mvc.perform(get("/api/v1/users/" + medio + "/broker-accounts").with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        // Ordenadas por nombre de broker: EXNOVA antes que IQOPTION.
        .andExpect(jsonPath("$.content[0].broker.name").value("EXNOVA"))
        .andExpect(jsonPath("$.content[0].accountId").value("70000001"))
        .andExpect(jsonPath("$.content[0].broker.id").isNotEmpty())
        .andExpect(jsonPath("$.content[0].declaredAt").isNotEmpty())
        .andExpect(jsonPath("$.content[1].broker.name").value("IQOPTION"));
  }

  @Test
  @DisplayName("`CA-SP-631`, `CA-SP-632` — nace en REGISTER, y `brokerUsername` está y es nulo")
  void estadoInicialYNombreSinConfirmar() throws Exception {
    mvc.perform(get("/api/v1/users/" + medio + "/broker-accounts").with(comoPersona(jefe)))
        .andExpect(jsonPath("$.content[0].status").value("REGISTER"))
        // PRESENTE y en nulo: su nulo significa «el broker no lo ha confirmado»
        // (`RN-SP-040`), y omitir el campo lo confundiría con «confirmado sin
        // nombre».
        .andExpect(jsonPath("$.content[0].brokerUsername").value(org.hamcrest.Matchers.nullValue()))
        // Y PRESENTE, que es lo que `jsonPath` sobre un nulo no distingue: se
        // comprueba sobre el JSON crudo porque la diferencia entre «campo
        // ausente» y «campo en nulo» ES el requisito de `RN-SP-040`.
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("\"brokerUsername\":null")));
  }

  @Test
  @DisplayName("`CA-SP-633` — quien trae `broker-accounts:read` ve las de cualquiera")
  void conElPermisoSeVeCualquiera() throws Exception {
    // El superadministrador no es superior de nadie: lo único que le abre la
    // puerta es el permiso.
    mvc.perform(get("/api/v1/users/" + ajeno + "/broker-accounts").with(comoPersona(SUPERADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("70000004"));
  }

  @Test
  @DisplayName("`CA-SP-634` — el ajeno recibe un 404 INDISTINGUIBLE del de una persona que no es")
  void elAjenoNoDistingueLosDosCasos() throws Exception {
    String ajena =
        mvc.perform(get("/api/v1/users/" + ajeno + "/broker-accounts").with(comoPersona(jefe)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String inexistente =
        mvc.perform(
                get("/api/v1/users/" + UUID.randomUUID() + "/broker-accounts")
                    .with(comoPersona(jefe)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // ESTA IGUALDAD ES EL REQUISITO, no un detalle de la prueba: en cuanto los
    // dos cuerpos difieran, la ruta vuelve a ser un oráculo de identificadores
    // para cualquier vendedor. Es lo único que impide que mañana alguien
    // convierta el 404 en un 403 «para dar mejor información».
    //
    // Se comparan SIN `instance` ni `correlationId`: el primero repite la ruta
    // que el propio actor pidió —no le dice nada que no supiera— y el segundo
    // es distinto en cada respuesta por definición. Lo que tiene que coincidir
    // es todo lo demás: tipo, título, estado, detalle y la lista de errores.
    assertThat(sinLoVariable(ajena)).isEqualTo(sinLoVariable(inexistente));
  }

  @Test
  @DisplayName("`CA-SP-635` — quien FUE superior y ya no lo es recibe 404")
  void elExSuperiorNoVe() throws Exception {
    mvc.perform(get("/api/v1/users/" + medio + "/broker-accounts").with(comoPersona(jefe)))
        .andExpect(status().isOk());

    // El historial se conserva —dice a quién se atribuía cada resultado— y NO
    // concede lectura: quien deja de llevar a una persona deja de ver sus datos
    // el mismo día.
    jdbc.update(
        "UPDATE user_supervisors SET ended_at = now() WHERE user_id = ? AND supervisor_id = ?",
        medio,
        jefe);

    mvc.perform(get("/api/v1/users/" + medio + "/broker-accounts").with(comoPersona(jefe)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-SP-693` — el PRINCIPAL ve las cuentas de su cliente; el vinculado por hotlink, 404")
  void elPrincipalVeLasDeSuCliente() throws Exception {
    // `ajeno` no es superior de nadie en `user_supervisors`: lo único que le
    // abre esta lectura es la fila REGISTRO de `client_sellers`. Es la prueba
    // de que la autorización cambió de tabla (`RN-SP-046`, 18-09-2026).
    mvc.perform(get("/api/v1/users/" + cliente + "/broker-accounts").with(comoPersona(ajeno)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("70000005"));

    // `base` le vendió por hotlink y NO es su principal: el mismo 404 que un
    // extraño. El perímetro de `RN-SP-046` no crece con el vínculo.
    mvc.perform(get("/api/v1/users/" + cliente + "/broker-accounts").with(comoPersona(base)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("el equipo de `RF-SP-056` incluye a la CARTERA del principal, y no al vinculado")
  void elEquipoIncluyeLaCartera() throws Exception {
    // `ajeno` no tiene subordinados y sí un cliente: su «equipo» para las
    // cuentas de broker es la unión de las dos fuentes (`RN-SP-046`).
    mvc.perform(get("/api/v1/users/me/team/broker-accounts").with(comoPersona(ajeno)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].user.username").value("cperez"))
        .andExpect(jsonPath("$.content[0].accountId").value("70000005"));
  }

  @Test
  @DisplayName("`CA-SP-636` — una persona sin cuentas devuelve 200 con la colección vacía")
  void sinCuentasNoEsUnError() throws Exception {
    jdbc.update("DELETE FROM user_brokers WHERE user_id = ?", medio);

    mvc.perform(get("/api/v1/users/" + medio + "/broker-accounts").with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("el titular NO se ve a sí mismo por esta vía, y se prueba para que relajarlo decida")
  void elTitularNoSeVeASiMismo() throws Exception {
    // `RF-SP-055` §4.2, decisión del 10-09-2026: la lectura se definió sobre el
    // equipo. Si algún día el cliente debe ver sus cuentas, la vía es
    // `RF-SP-039` y su `GET /users/me`, no relajar esto por descuido.
    mvc.perform(get("/api/v1/users/" + medio + "/broker-accounts").with(comoPersona(medio)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("dos cuentas de la misma persona en el MISMO broker salen las dos (`RN-SP-038`)")
  void variasCuentasEnElMismoBroker() throws Exception {
    declarar(medio, brokerA, "70000099");

    mvc.perform(get("/api/v1/users/" + medio + "/broker-accounts").with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        // Desempate por identificador de cuenta dentro del mismo broker: sin él
        // el orden lo elegiría el motor y bailaría entre llamadas.
        .andExpect(jsonPath("$.content[0].accountId").value("70000001"))
        .andExpect(jsonPath("$.content[1].accountId").value("70000099"));
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-056` — las cuentas del equipo
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-637`, `CA-SP-638` — el equipo directo, cada fila con su titular")
  void elEquipoConSuTitular() throws Exception {
    mvc.perform(get("/api/v1/users/me/team/broker-accounts").with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].user.username").value("amartinez"))
        .andExpect(jsonPath("$.content[0].user.id").isNotEmpty())
        .andExpect(jsonPath("$.content[0].user.firstName").isNotEmpty())
        .andExpect(jsonPath("$.content[0].broker.name").value("EXNOVA"))
        .andExpect(jsonPath("$.content[0].status").value("REGISTER"));
  }

  @Test
  @DisplayName("`CA-SP-639`, `CA-SP-645` — ni el ajeno ni el NIETO aparecen")
  void niElAjenoNiElNieto() throws Exception {
    // `base` cuelga de `medio`, que cuelga de `jefe`. Una consulta recursiva
    // —«más útil»— publicaría la estructura entera de la empresa por una
    // lectura de cuentas de broker, y pasaría todas las demás pruebas.
    mvc.perform(get("/api/v1/users/me/team/broker-accounts").with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(
            jsonPath("$.content[?(@.user.username == 'lgarcia')]")
                .value(org.hamcrest.Matchers.empty()))
        .andExpect(
            jsonPath("$.content[?(@.user.username == 'pruiz')]")
                .value(org.hamcrest.Matchers.empty()));

    // Y el nieto sí lo ve su superior directo.
    mvc.perform(get("/api/v1/users/me/team/broker-accounts").with(comoPersona(medio)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].user.username").value("lgarcia"));
  }

  @Test
  @DisplayName("`CA-SP-640` — quien DEJÓ el equipo no aparece")
  void elQueDejoElEquipoNoAparece() throws Exception {
    jdbc.update(
        "UPDATE user_supervisors SET ended_at = now() WHERE user_id = ? AND supervisor_id = ?",
        medio,
        jefe);

    mvc.perform(get("/api/v1/users/me/team/broker-accounts").with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("una persona ELIMINADA no aparece en el listado del equipo")
  void elEliminadoNoAparece() throws Exception {
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", medio);

    mvc.perform(get("/api/v1/users/me/team/broker-accounts").with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-SP-641` — el filtro por estado acota, y el total cuenta LO FILTRADO")
  void filtroPorEstado() throws Exception {
    // Se siembra por SQL porque NO HAY API QUE LO MUEVA: quien lo hará es el
    // webhook de `RF-SP-054`. Sin esto, el filtro solo se podría probar con el
    // valor que todas las filas ya tienen — que no prueba que filtre.
    jdbc.update(
        "UPDATE user_brokers SET status = 'FIRST_DEPOSIT' WHERE user_id = ? AND external_id = ?",
        medio,
        "70000001");

    mvc.perform(
            get("/api/v1/users/me/team/broker-accounts?status=FIRST_DEPOSIT")
                .with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("70000001"))
        .andExpect(jsonPath("$.content[0].status").value("FIRST_DEPOSIT"));

    mvc.perform(
            get("/api/v1/users/me/team/broker-accounts?status=REGISTER").with(comoPersona(jefe)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].accountId").value("70000002"));
  }

  @Test
  @DisplayName(
      "`CA-SP-642` — el filtro por broker acota, y uno inexistente devuelve vacío SIN error")
  void filtroPorBroker() throws Exception {
    mvc.perform(
            get("/api/v1/users/me/team/broker-accounts?brokerId=" + brokerB)
                .with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].broker.name").value("IQOPTION"));

    // Un identificador que no designa nada es una PREGUNTA LEGÍTIMA con
    // respuesta vacía —criterio de `RF-SP-025`—, al revés que un estado
    // inválido, que es una pregunta mal escrita.
    mvc.perform(
            get("/api/v1/users/me/team/broker-accounts?brokerId=" + UUID.randomUUID())
                .with(comoPersona(jefe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-SP-643` — un estado fuera de los dos valores es 400, NO una página vacía")
  void estadoInvalido() throws Exception {
    // Una página vacía sería indistinguible de «nadie está en ese estado», y
    // quien la lea concluirá que su equipo no ha depositado cuando lo que pasa
    // es que escribió mal el filtro.
    mvc.perform(
            get("/api/v1/users/me/team/broker-accounts?status=depositado").with(comoPersona(jefe)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("status"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    // Ni siquiera en minúscula: el `CHECK` del motor solo admite mayúsculas, y
    // aceptarlo aquí crearía una forma de escribirlo que la base rechazaría.
    mvc.perform(
            get("/api/v1/users/me/team/broker-accounts?status=register").with(comoPersona(jefe)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-SP-644` — quien no tiene equipo recibe 200 con la página vacía")
  void sinEquipoNoEsUnError() throws Exception {
    // `base` tiene un vínculo HOTLINK con `cperez` y ningún subordinado ni
    // cliente REGISTRO: el vínculo de venta NO es equipo (`RN-SP-049`).
    mvc.perform(get("/api/v1/users/me/team/broker-accounts").with(comoPersona(base)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("la paginación no repite ni pierde filas entre dos páginas seguidas")
  void paginacionEstable() throws Exception {
    String primera =
        mvc.perform(
                get("/api/v1/users/me/team/broker-accounts?page=0&size=1").with(comoPersona(jefe)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String segunda =
        mvc.perform(
                get("/api/v1/users/me/team/broker-accounts?page=1&size=1").with(comoPersona(jefe)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(primera).contains("70000001").doesNotContain("70000002");
    assertThat(segunda).contains("70000002").doesNotContain("70000001");
  }

  @Test
  @DisplayName("el permiso NO abre el equipo ajeno: no hay forma de nombrar a otro superior")
  void elPermisoNoAbreElEquipoAjeno() throws Exception {
    // El superadministrador tiene `broker-accounts:read` y no tiene equipo: por
    // esta ruta recibe lo suyo, que es nada. Ver el equipo de otro se hace
    // persona a persona por `RF-SP-055`, y ese es el límite de `RN-SP-046`.
    mvc.perform(get("/api/v1/users/me/team/broker-accounts").with(comoPersona(SUPERADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  // ---------------------------------------------------------------------------
  // Esquema (`RN-SP-045`)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-631` en el esquema — una fila sin estado nace en REGISTER")
  void elDefaultHaceCiertasLasFilasViejas() {
    // Es lo que `V80` garantiza para las cuentas que `RF-SP-045` ya había
    // declarado: `REGISTER` no es un relleno, es su valor correcto mientras el
    // webhook no exista.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_brokers WHERE status = 'REGISTER'", Integer.class))
        // Cinco desde el 18-09-2026: las cuatro de la fuerza comercial y la de la
        // cliente registrada por `ajeno` (`CA-SP-693`).
        .isEqualTo(5);
  }

  @Test
  @DisplayName("el CHECK del motor rechaza un estado que el dominio no conoce")
  void elCheckRechazaLoDesconocido() {
    // El enumerado de Java gobierna lo que entra por la API y NO lo que entra
    // por una migración de datos o una corrección a mano.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update("UPDATE user_brokers SET status = 'register' WHERE user_id = ?", medio))
        .hasMessageContaining("ck_user_brokers_status");
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  /**
   * Un actor que <b>existe en {@code users}</b>, y sin autoridades en el token.
   *
   * <p>Es deliberado: {@code CurrentActor} lee los permisos <b>de la base</b> para toda persona
   * registrada, de modo que pasarlas aquí no cambiaría nada y daría la falsa impresión de que la
   * prueba concede algo. Lo que abre la puerta es la estructura comercial, o el rol de la persona.
   */
  private static RequestPostProcessor comoPersona(UUID persona) {
    return user(persona.toString());
  }

  private UUID crearPersona(String username, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
    return id;
  }

  private void reportar(UUID subordinado, UUID superior) {
    jdbc.update(
        """
        INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)
        VALUES (gen_random_uuid(), ?, ?, now())
        """,
        subordinado,
        superior);
  }

  /** El cliente y su principal: la fila REGISTRO de `client_sellers` (`RN-SP-049`). */
  private void registrar(UUID cliente, UUID vendedor) {
    jdbc.update(
        """
        INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)
        VALUES (?, ?, 'REGISTRO', NULL, now())
        """,
        cliente,
        vendedor);
  }

  private void vincularPorHotlink(UUID cliente, UUID vendedor) {
    jdbc.update(
        """
        INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)
        VALUES (?, ?, 'HOTLINK', NULL, now())
        """,
        cliente,
        vendedor);
  }

  /** Declara una cuenta <b>sin tocar {@code status}</b>: lo pone el `DEFAULT` de `V80`. */
  private void declarar(UUID persona, UUID broker, String cuenta) {
    jdbc.update(
        """
        INSERT INTO user_brokers (id, user_id, broker_id, external_id)
        VALUES (gen_random_uuid(), ?, ?, ?)
        """,
        persona,
        broker,
        cuenta);
  }

  /** El cuerpo del problema sin las dos partes que cambian entre dos peticiones cualesquiera. */
  private static String sinLoVariable(String problema) {
    return problema
        .replaceAll("\"instance\":\"[^\"]*\"", "\"instance\":\"…\"")
        .replaceAll("\"correlationId\":\"[^\"]*\"", "\"correlationId\":\"…\"");
  }

  /**
   * El broker de la siembra, con <b>su identificador literal</b> si hubiera que reponerlo.
   *
   * <p>Literal y no aleatorio: reponerlo con otro identificador dejaría dos catálogos distintos
   * según qué clase corriera antes, que es la misma clase de dependencia del orden que esta
   * reposición existe para quitar.
   */
  private UUID brokerAsegurado(String id, String nombre) {
    jdbc.update(
        "INSERT INTO brokers (id, name) VALUES (CAST(? AS uuid), ?) ON CONFLICT DO NOTHING",
        id,
        nombre);
    return jdbc.queryForObject("SELECT id FROM brokers WHERE name = ?", UUID.class, nombre);
  }
}
