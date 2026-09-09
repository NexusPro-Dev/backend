package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * Registro de clientes por enlace (`RF-SP-045`).
 *
 * <p><b>Es el primer endpoint público del sistema que ESCRIBE</b>, y de ahí sale lo que estas
 * pruebas vigilan: que la cuenta nazca sin poder operar, que los rechazos no delaten el catálogo ni
 * la plantilla, y que <b>ningún rechazo deje nada escrito</b>.
 *
 * <p>La siembra la hace SQL: hace falta un producto `FREE → FREE`, un vendedor con rol de tipo
 * {@code VENDEDOR} y un broker, y ninguno de los tres se puede crear por HTTP sin credenciales.
 */
@AutoConfigureMockMvc
class SelfRegistrationIT extends IntegrationTestBase {

  /** `AGENTE`, sembrado por `V7` con `role_type = VENDEDOR`. */
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  /** `CLIENTE`, sembrado por `V30`: es de tipo `CONSUMIDOR`. */
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  /** `IQOPTION`, sembrado por `V76`. */
  private static final String BROKER = "01a081f0-6000-7101-9c4f-5e7adb000001";

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID free;
  private UUID oro;

  @BeforeEach
  void sembrar() {
    limpiar();

    // La cadena encadenada: `uq_memberships_parent` es UNIQUE NULLS NOT
    // DISTINCT, de modo que solo UNA puede no tener superior.
    // Colores distintos: `uq_memberships_color` es único, y dos iguales revientan
    // en el segundo INSERT y no en lo que la prueba comprueba.
    oro = membresia("ORO", "Oro", 1, null, "D4AF37");
    free = membresia("FREE", "Free", 2, oro, "9E9E9E");

    // El producto del enlace: `FREE → FREE`, que es la renovación y el único
    // que este registro admite hoy.
    producto("REG_FREE", free, free, 30);
    // Uno que lleva a una membresía DE PAGO: lo rechaza `EX-004`.
    producto("REG_ORO", free, oro, 365);

    vendedor("reg-agente");
    // Una persona que existe y NO es fuerza comercial: `EX-002` no la distingue
    // de una inexistente.
    cliente("reg-cliente");
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // El camino feliz
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-507`, `CA-SP-508` y `CA-SP-512` — nace SIN TOKEN, en FTD_PENDIENTE")
  void registroValido() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("ana.ruiz"))
        .andExpect(jsonPath("$.status").value("FTD_PENDIENTE"))
        // Lo que falta para operar, dicho en la respuesta: sin esto, quien se
        // registra no sabe por qué no puede hacer nada.
        .andExpect(jsonPath("$.pending").isNotEmpty());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT status, must_change_password FROM users WHERE username = 'ana.ruiz'");

    assertThat(fila.get("status")).isEqualTo("FTD_PENDIENTE");
    // La contraseña LA ELIGIÓ ELLA: se marca lo que fijó otra persona, no lo
    // que uno fijó.
    assertThat(fila.get("must_change_password")).isEqualTo(false);
  }

  @Test
  @DisplayName("`CA-SP-509`, `CA-SP-510` y `CA-SP-513` — rol, membresía con vigencia y atribución")
  void losCuatroHechos() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id"
                    + " WHERE u.username = 'ana.ruiz' AND ur.role_id = ?::uuid",
                Integer.class,
                CLIENTE))
        .isOne();

    // La vigencia sale del producto: treinta días, no nula.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_memberships um JOIN users u ON u.id = um.user_id"
                    + " WHERE u.username = 'ana.ruiz' AND um.membership_id = ?::uuid"
                    + " AND um.ends_at IS NOT NULL",
                Integer.class,
                free.toString()))
        .isOne();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_supervisors us JOIN users u ON u.id = us.user_id"
                    + " JOIN users v ON v.id = us.supervisor_id"
                    + " WHERE u.username = 'ana.ruiz' AND v.username = 'reg-agente'",
                Integer.class))
        .isOne();
  }

  @Test
  @DisplayName("`CA-SP-511` — un producto sin vigencia produce una membresía sin fecha de fin")
  void sinVigencia() throws Exception {
    jdbc.update("UPDATE products SET validity_days = NULL WHERE code = 'REG_FREE'");

    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    // Nula significa que NO CADUCA, no que caduque hoy.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_memberships um JOIN users u ON u.id = um.user_id"
                    + " WHERE u.username = 'ana.ruiz' AND um.ends_at IS NULL",
                Integer.class))
        .isOne();
  }

  @Test
  @DisplayName("`CA-SP-514` — el producto se admite por CÓDIGO y por IDENTIFICADOR")
  void productoPorCodigoOIdentificador() throws Exception {
    String id =
        jdbc.queryForObject("SELECT id::text FROM products WHERE code = 'REG_FREE'", String.class);

    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    mvc.perform(registro(cuerpoCon(id, "beto.paz", "beto@ejemplo.com", "87654321", BROKER)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("`CA-SP-521` — el registro NO devuelve credenciales de sesión")
  void sinCredenciales() throws Exception {
    String respuesta =
        mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Registrarse no es iniciar sesión: quien acaba de crear su cuenta pasa por
    // `RF-SP-034` como todo el mundo.
    assertThat(respuesta).doesNotContain("token").doesNotContain("Bearer");
  }

  @Test
  @DisplayName("`CA-SP-522` — la persona registrada PUEDE autenticarse pese a no estar ACTIVO")
  void autenticaSinEstarActivo() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    // Es la primera vez que un estado distinto de `ACTIVO` autentica. Impedirlo
    // dejaría una cuenta que existe y a la que su titular no puede asomarse.
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"ana.ruiz\",\"password\":\"ClaveSegura2026!\"}"))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // La cuenta de broker — `RN-SP-042`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-609` — el enlace FREE → FREE exige broker e identificador de cuenta")
  void elBrokerEsObligatorio() throws Exception {
    mvc.perform(registro(cuerpoCon("REG_FREE", "ana.ruiz", "ana@ejemplo.com", null, BROKER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-013"));

    mvc.perform(registro(cuerpoCon("REG_FREE", "ana.ruiz", "ana@ejemplo.com", "12345678", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-012"));

    // Y ninguno de los dos rechazos deja nada escrito.
    assertThat(cuantasPersonas()).isZero();
  }

  @Test
  @DisplayName("`CA-SP-610` — la cuenta queda declarada, con el usuario del broker EN NULO")
  void laCuentaQuedaDeclarada() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT ub.external_id, ub.broker_username FROM user_brokers ub"
                + " JOIN users u ON u.id = ub.user_id WHERE u.username = 'ana.ruiz'");

    assertThat(fila.get("external_id")).isEqualTo("12345678");
    // Nulo significa «el broker todavía no lo ha confirmado» (`RN-SP-040`): lo
    // rellenará el webhook de `RF-SP-054`.
    assertThat(fila.get("broker_username")).isNull();
  }

  @Test
  @DisplayName("`CA-SP-611` — un broker inexistente y uno inactivo se rechazan IGUAL")
  void brokerQueNoProcede() throws Exception {
    String inexistente = UUID.randomUUID().toString();
    jdbc.update("UPDATE brokers SET is_active = false WHERE id = ?::uuid", BROKER);

    String conInexistente =
        mvc.perform(
                registro(
                    cuerpoCon("REG_FREE", "ana.ruiz", "ana@ejemplo.com", "12345678", inexistente)))
            .andExpect(status().isUnprocessableEntity())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String conInactivo =
        mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
            .andExpect(status().isUnprocessableEntity())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // El mismo cuerpo salvo la correlación: distinguirlos permitiría averiguar
    // con qué brokers opera la plataforma probando identificadores.
    assertThat(normalizar(conInactivo)).isEqualTo(normalizar(conInexistente));
    assertThat(cuantasPersonas()).isZero();
  }

  @Test
  @DisplayName("`CA-SP-612` — una cuenta ya declarada se rechaza, y NO deja nada escrito")
  void cuentaYaDeclarada() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    // Aquí SÍ se dice qué pasó, al revés que con el documento repetido: quien
    // declara una cuenta de broker es su titular y necesita saber que alguien
    // se la atribuyó.
    mvc.perform(registro(cuerpo("beto.paz", "beto@ejemplo.com", "12345678")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-009"));

    // La transacción entera se deshace: ni cuenta, ni membresía, ni atribución.
    assertThat(cuantasPersonas()).isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE username = 'beto.paz'", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("`CA-SP-613` — la misma cuenta en OTRO broker sí se admite")
  void mismaCuentaEnOtroBroker() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    // Lo único que no se repite es el par broker + identificador: dos brokers
    // numeran sus cuentas por su cuenta.
    String otro =
        jdbc.queryForObject("SELECT id::text FROM brokers WHERE name = 'EXNOVA'", String.class);

    mvc.perform(registro(cuerpoCon("REG_FREE", "beto.paz", "beto@ejemplo.com", "12345678", otro)))
        .andExpect(status().isCreated());
  }

  // ---------------------------------------------------------------------------
  // Los rechazos que no delatan
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-515` — producto inexistente, inactivo y retirado se rechazan IGUAL")
  void productoQueNoProcede() throws Exception {
    String inexistente = respuestaDe(cuerpoCon("NO_EXISTE", "a.b", "a@b.co", "1", BROKER));

    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE code = 'REG_FREE'");
    String inactivo = respuestaDe(cuerpo("a.b", "a@b.co", "1"));

    jdbc.update(
        "UPDATE products SET status = 'ACTIVO', deleted_at = now() WHERE code = 'REG_FREE'");
    String retirado = respuestaDe(cuerpo("a.b", "a@b.co", "1"));

    assertThat(normalizar(inactivo)).isEqualTo(normalizar(inexistente));
    assertThat(normalizar(retirado)).isEqualTo(normalizar(inexistente));
  }

  @Test
  @DisplayName("`CA-SP-516` — un producto de tipo BOT se rechaza")
  void elBotSeRechaza() throws Exception {
    jdbc.update(
        "UPDATE products SET type = 'BOT', source_membership_id = NULL,"
            + " target_membership_id = NULL WHERE code = 'REG_FREE'");

    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
  }

  @Test
  @DisplayName("`CA-SP-517` — un producto hacia una membresía DE PAGO dice que exige pago")
  void elProductoDePagoSeRechazaDiciendolo() throws Exception {
    // Es la única excepción del enlace que sí dice qué pasó: quien llega con un
    // producto de pago tiene un enlace legítimo.
    mvc.perform(registro(cuerpoCon("REG_ORO", "ana.ruiz", "ana@ejemplo.com", "12345678", BROKER)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
  }

  @Test
  @DisplayName("`CA-SP-518` — vendedor inexistente y sin rol VENDEDOR se rechazan IGUAL")
  void vendedorQueNoProcede() throws Exception {
    String inexistente = respuestaDe(cuerpoConVendedor("no-existe"));
    String noEsVendedor = respuestaDe(cuerpoConVendedor("reg-cliente"));

    // Probando nombres de usuario se averiguaría quién trabaja aquí.
    assertThat(normalizar(noEsVendedor)).isEqualTo(normalizar(inexistente));
  }

  @Test
  @DisplayName("`CA-SP-519` — un rechazo no deja NADA escrito")
  void elRechazoNoDejaNada() throws Exception {
    mvc.perform(registro(cuerpoCon("NO_EXISTE", "ana.ruiz", "ana@ejemplo.com", "1", BROKER)))
        .andExpect(status().isUnprocessableEntity());

    assertThat(cuantasPersonas()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_brokers", Integer.class)).isZero();
  }

  @Test
  @DisplayName("`CA-SP-520` — el nombre de usuario y el correo en uso SÍ dicen cuál chocó")
  void identidadesEnUso() throws Exception {
    mvc.perform(registro(cuerpo("ana.ruiz", "ana@ejemplo.com", "12345678")))
        .andExpect(status().isCreated());

    mvc.perform(registro(cuerpo("ana.ruiz", "otra@ejemplo.com", "99999999")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("username"));

    mvc.perform(registro(cuerpo("otra.persona", "ana@ejemplo.com", "99999999")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  @DisplayName("`CA-SP-583` — un país inexistente y uno inactivo se rechazan IGUAL")
  void paisQueNoProcede() throws Exception {
    String inventado = respuestaDe(cuerpoConPais("ZZZ"));

    String codigoReal =
        jdbc.queryForObject("SELECT code FROM countries ORDER BY code LIMIT 1", String.class);
    jdbc.update("UPDATE countries SET is_active = false WHERE code = ?", codigoReal);
    String inactivo = respuestaDe(cuerpoConPais(codigoReal));

    // El cuerpo no dice qué países existen.
    assertThat(normalizar(inactivo)).isEqualTo(normalizar(inventado));
    assertThat(inactivo).doesNotContain(codigoReal);
  }

  @Test
  @DisplayName("`CA-SP-601` — no existe abreviación que registre a un menor")
  void elMenorNoSePuedeExpresar() throws Exception {
    // `TI` es la tarjeta de identidad y NO está en el catálogo: se rechaza con
    // la misma respuesta que una abreviación inventada, porque lo que no está
    // no está.
    String tarjeta = respuestaDe(cuerpoConDocumento("TI"));
    String inventada = respuestaDe(cuerpoConDocumento("XX"));

    assertThat(normalizar(tarjeta)).isEqualTo(normalizar(inventada));
  }

  // ---------------------------------------------------------------------------
  // Preparación
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder registro(String cuerpo) {
    return post("/api/v1/auth/registration")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private String respuestaDe(String cuerpo) throws Exception {
    return mvc.perform(registro(cuerpo)).andReturn().getResponse().getContentAsString();
  }

  /** Sin la correlación, que cambia en cada petición. */
  private static String normalizar(String cuerpo) {
    return cuerpo
        .replaceAll("\"correlationId\"\\s*:\\s*\"[^\"]*\"", "\"correlationId\":\"?\"")
        .replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "\"timestamp\":\"?\"");
  }

  private String cuerpo(String usuario, String correo, String cuenta) {
    return cuerpoCon("REG_FREE", usuario, correo, cuenta, BROKER);
  }

  private String cuerpoCon(
      String producto, String usuario, String correo, String cuenta, String broker) {
    return plantilla(producto, "reg-agente", usuario, correo, cuenta, broker, paisSembrado(), "CC");
  }

  private String cuerpoConVendedor(String vendedor) {
    return plantilla(
        "REG_FREE",
        vendedor,
        "ana.ruiz",
        "ana@ejemplo.com",
        "12345678",
        BROKER,
        paisSembrado(),
        "CC");
  }

  private String cuerpoConPais(String pais) {
    return plantilla(
        "REG_FREE", "reg-agente", "ana.ruiz", "ana@ejemplo.com", "12345678", BROKER, pais, "CC");
  }

  private String cuerpoConDocumento(String abreviacion) {
    return plantilla(
        "REG_FREE",
        "reg-agente",
        "ana.ruiz",
        "ana@ejemplo.com",
        "12345678",
        BROKER,
        paisSembrado(),
        abreviacion);
  }

  private static String plantilla(
      String producto,
      String vendedor,
      String usuario,
      String correo,
      String cuenta,
      String broker,
      String pais,
      String documento) {

    return """
        {"product":"%s","referrer":"%s",
         "firstName":"Ana","lastName":"Ruiz",
         "username":"%s","email":"%s","password":"ClaveSegura2026!",
         "countryCode":"%s","documentType":"%s","documentNumber":"%s",
         "phone":"+573001234567",
         "addressLine1":null,"addressLine2":null,"city":null,
         "brokerId":%s,"brokerAccountId":%s}
        """
        .formatted(
            producto,
            vendedor,
            usuario,
            correo,
            pais,
            documento,
            // El número de documento se deriva del nombre de usuario para que
            // dos registros distintos no choquen por `uq_users_document`.
            Integer.toString(Math.abs(usuario.hashCode())),
            broker == null ? "null" : "\"" + broker + "\"",
            cuenta == null ? "null" : "\"" + cuenta + "\"");
  }

  private String paisSembrado() {
    return jdbc.queryForObject(
        "SELECT code FROM countries WHERE is_active ORDER BY code LIMIT 1", String.class);
  }

  private int cuantasPersonas() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE username IN ('ana.ruiz', 'beto.paz', 'a.b',"
            + " 'otra.persona')",
        Integer.class);
  }

  private UUID membresia(String codigo, String nombre, int nivel, UUID superior, String color) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, level, color, parent_membership_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS uuid))",
        id.toString(),
        codigo,
        nombre,
        nivel,
        color,
        superior == null ? null : superior.toString());
    return id;
  }

  private void producto(String codigo, UUID origen, UUID destino, Integer vigencia) {
    jdbc.update(
        "INSERT INTO products (id, code, type, name, source_membership_id, target_membership_id,"
            + " price, currency_id, validity_days, status, scope, implementation)"
            + " VALUES (CAST(? AS uuid), ?, 'UPGRADE_MEMBRESIA', ?, CAST(? AS uuid),"
            + " CAST(? AS uuid), 0.00, CAST(? AS uuid), CAST(? AS integer), 'ACTIVO', 'TIENDA',"
            + " 'AUTOMATICA')",
        UUID.randomUUID().toString(),
        codigo,
        "Producto " + codigo,
        origen.toString(),
        destino.toString(),
        USD,
        vigencia);
  }

  private void vendedor(String usuario) {
    persona(usuario, AGENTE);
  }

  private void cliente(String usuario) {
    persona(usuario, CLIENTE);
  }

  private void persona(String usuario, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, 'Nombre', 'Apellido', 'x', 'ACTIVO',"
            + " (SELECT id FROM countries ORDER BY code LIMIT 1))",
        id.toString(),
        usuario,
        usuario + "@nexus.test");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT CAST(? AS uuid), CAST(? AS uuid), role_type FROM roles WHERE id = CAST(? AS"
            + " uuid)",
        id.toString(),
        rol,
        rol);
  }

  private void limpiar() {
    jdbc.update("DELETE FROM user_brokers");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'reg-%' OR username IN ('ana.ruiz', 'beto.paz', 'a.b', 'otra.persona'))");
    jdbc.update(
        "DELETE FROM users WHERE username LIKE 'reg-%' OR username IN ('ana.ruiz', 'beto.paz',"
            + " 'a.b', 'otra.persona')");
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    jdbc.update("UPDATE countries SET is_active = true");
    jdbc.update("UPDATE brokers SET is_active = true");
  }
}
