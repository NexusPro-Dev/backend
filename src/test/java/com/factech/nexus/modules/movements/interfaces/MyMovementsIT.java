package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * `RF-MV-008` — los movimientos propios.
 *
 * <p><b>Los movimientos se insertan por SQL y no se registran por la API.</b> Lo que este
 * requerimiento hace es <b>leer</b>, y montarlos con `RF-MV-001` obligaría a arrastrar su catálogo
 * entero —productos, vigencias, reglas de composición— para probar una consulta. Lo que sí se
 * ejercita por HTTP es todo lo que este requerimiento decide: el alcance, el papel y el detalle.
 */
@AutoConfigureMockMvc
class MyMovementsIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";

  /**
   * Un SEGUNDO tipo que solo existe en esta prueba (`CA-MV-120`), como en `MovementsIT`: con uno
   * solo en el catálogo, filtrar por `VENTA` devolvería todo y no probaría que el filtro
   * discrimina. Se siembra al usarlo y se retira en `limpiar`.
   */
  private static final String TIPO_DE_PRUEBA = "01a061ba-3400-7001-9c4f-5e7ad70000f2";

  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String PSE = "01a061ba-3400-7003-9c4f-5e7ad7000022";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedor;
  private UUID cliente;
  private UUID ajeno;
  private UUID producto;

  /** La venta del vendedor al cliente. */
  private UUID vendida;

  /** La que el vendedor se compró a sí mismo: es cliente Y vendedor. */
  private UUID propia;

  /**
   * Una en la que no participa ninguno de los dos, y SIN vendedor en su línea. Ninguna venta se
   * registra así desde el 16-09-2026 (`RN-MV-003`); se siembra en crudo porque es la forma que
   * tendrán los tipos de movimiento que no venden nada, y `FA-003` la declara.
   */
  private UUID deOtros;

  @BeforeEach
  void sembrar() {
    limpiar();

    vendedor = persona("mine-vendedor");
    cliente = persona("mine-cliente");
    ajeno = persona("mine-ajeno");
    producto = producto();

    vendida = movimiento(cliente, vendedor, "PENDIENTE", BASE);
    propia = movimiento(vendedor, vendedor, "CONFIRMADA", BASE.plusDays(1));
    deOtros = movimiento(ajeno, null, "PENDIENTE", BASE.plusDays(2));
  }

  // ---------------------------------------------------------------------------
  // Solo lo comprado — lo que sostiene el requerimiento desde el 22-09-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-137 — el listado trae SOLO lo comprado: el vendedor no ve lo que vendió a otro, y sí"
          + " lo que se compró a sí mismo, una vez")
  void soloLoComprado() throws Exception {
    // `vendida` la compró el cliente y la vendió el vendedor; `propia` se la
    // compró el vendedor a sí mismo (`RN-MV-003`: es su propio vendedor).
    // Hasta el 22-09-2026 el vendedor veía las dos, con papel SELLER y BOTH.
    mvc.perform(get("/api/v1/movements/mine").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(propia.toString()));

    // Y el comprador ve la suya, que es lo único que este listado responde ya.
    mvc.perform(get("/api/v1/movements/mine").with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(vendida.toString()));
  }

  @Test
  @DisplayName("CA-MV-139 — ninguna fila lleva `role`: se retiró con la mitad de vendedor")
  void laFilaNoLlevaPapel() throws Exception {
    String cuerpo =
        mvc.perform(get("/api/v1/movements/mine").with(como(cliente)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].role").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // EN CRUDO: un jsonPath que pase con la clave ausente no distinguiría
    // «retirado» de «nulo».
    assertThat(cuerpo).doesNotContain("\"role\"");
  }

  // ---------------------------------------------------------------------------
  // El alcance — lo que sostiene el requerimiento
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-038 — no aparece nada ajeno, NI SIQUIERA con movements:read")
  void elPermisoDeAdministracionNoAmplia() throws Exception {
    // CON EL PERMISO PUESTO, a propósito. Si algún día alguien conecta
    // `movements:read` a esta ruta, esto lo delata en lugar de dejar que ocurra
    // por omisión — que es la única forma en que este requerimiento se rompe.
    mvc.perform(get("/api/v1/movements/mine").with(conPermisoDeLectura(vendedor)))
        .andExpect(status().isOk())
        // Una desde el 22-09-2026: solo lo comprado (`CA-MV-137`).
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[?(@.id == '" + deOtros + "')]").isEmpty());
  }

  @Test
  @DisplayName("CA-MV-039 — quien no participó en ninguno recibe una página vacía, no un error")
  void sinMovimientos() throws Exception {
    UUID recienLlegado = persona("mine-nuevo");

    mvc.perform(get("/api/v1/movements/mine").with(como(recienLlegado)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  // ---------------------------------------------------------------------------
  // Forma de la respuesta
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-040 y CA-MV-043 — va envuelto, y trae al sujeto y a sus vendedores")
  void formaDeLaFila() throws Exception {
    mvc.perform(get("/api/v1/movements/mine").with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").isNumber())
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.content[0].user.username").value("mine-cliente"))
        // Desde el 21-09-2026 la fila dice su tipo (`CA-MV-121`): es lo que
        // hace comprobable el filtro por tipo.
        .andExpect(jsonPath("$.content[0].type").value("VENTA"))
        // Los vendedores son de las líneas y van SIN REPETIR: una lista, porque
        // una venta podría llevar varios. Hoy lleva uno.
        .andExpect(jsonPath("$.content[0].sellers.length()").value(1))
        .andExpect(jsonPath("$.content[0].sellers[0].username").value("mine-vendedor"))
        .andExpect(jsonPath("$.content[0].client").doesNotExist())
        .andExpect(jsonPath("$.content[0].seller").doesNotExist())
        .andExpect(jsonPath("$.content[0].currency.code").value("USD"))
        .andExpect(jsonPath("$.content[0].paymentMethod").isNotEmpty())
        .andExpect(jsonPath("$.content[0].payableAmount").value(100.00))
        // Las líneas NO viajan en el listado: están en el detalle.
        .andExpect(jsonPath("$.content[0].lines").doesNotExist());
  }

  @Test
  @DisplayName("CA-MV-043 — la lista de vendedores viaja VACÍA Y PRESENTE cuando no hay ninguno")
  void movimientoSinVendedor() throws Exception {
    String cuerpo =
        mvc.perform(get("/api/v1/movements/mine").with(como(ajeno)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].sellers").isArray())
            .andExpect(jsonPath("$.content[0].sellers").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // EN CRUDO: «no hay vendedor» tiene que distinguirse de «este endpoint no
    // informa del vendedor», y la lista vacía lo dice sin que nadie interprete
    // un nulo. `isEmpty()` sobre un jsonPath pasaría también con la clave ausente.
    assertThat(cuerpo).contains("\"sellers\":[]");
  }

  @Test
  @DisplayName("CA-MV-043 — la venta con dos líneas del mismo vendedor cuenta UNA vez")
  void variasLineasNoMultiplican() throws Exception {
    // Es el caso que un JOIN con las líneas habría multiplicado, y sigue vivo
    // aunque el listado mire una sola columna: los vendedores se leen en una
    // segunda consulta, y esa sí podría duplicar la fila.
    segundaLinea(vendida, vendedor);

    mvc.perform(get("/api/v1/movements/mine").with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(vendida.toString()))
        .andExpect(jsonPath("$.content[0].sellers.length()").value(1));
  }

  @Test
  @DisplayName("CA-MV-041 — el orden es estable entre páginas")
  void ordenEstable() throws Exception {
    // Dos compras del mismo vendedor: desde el 22-09-2026 el listado solo trae
    // lo comprado, de modo que la segunda se siembra aquí.
    UUID otraCompra = movimiento(vendedor, vendedor, "PENDIENTE", BASE.plusDays(3));

    mvc.perform(get("/api/v1/movements/mine?page=0&size=1").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content[0].id").value(otraCompra.toString()));

    mvc.perform(get("/api/v1/movements/mine?page=1&size=1").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(propia.toString()));
  }

  @Test
  @DisplayName("CA-MV-042 — el filtro por estado devuelve solo los de ese estado")
  void filtroPorEstado() throws Exception {
    mvc.perform(get("/api/v1/movements/mine?status=PENDIENTE").with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(vendida.toString()));

    // Y el vendedor, que compró CONFIRMADA, no tiene ninguna pendiente propia.
    mvc.perform(get("/api/v1/movements/mine?status=PENDIENTE").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-120 — el filtro por tipo discrimina lo propio, escrito como sea, y se combina")
  void filtroPorTipo() throws Exception {
    // Un movimiento del segundo tipo a nombre del vendedor, pendiente: lo único
    // que un filtro por ese tipo debe devolverle, y lo que `status=PENDIENTE`
    // solo no distingue de la venta pendiente que hizo.
    UUID deposito = movimiento(vendedor, null, "PENDIENTE", BASE.plusDays(5), TIPO_DE_PRUEBA);

    mvc.perform(get("/api/v1/movements/mine?type=prueba_deposito").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(deposito.toString()))
        .andExpect(jsonPath("$.content[0].type").value("PRUEBA_DEPOSITO"));

    // Por `VENTA`, la compra propia y no el depósito.
    mvc.perform(get("/api/v1/movements/mine?type=VENTA").with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(propia.toString()));

    // Combinado con el estado, sobre el comprador de la venta pendiente.
    mvc.perform(
            get("/api/v1/movements/mine")
                .param("type", "VENTA")
                .param("status", "PENDIENTE")
                .with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(vendida.toString()));

    // Y el alcance no se mueve: el cliente no ve el depósito del vendedor.
    mvc.perform(get("/api/v1/movements/mine?type=PRUEBA_DEPOSITO").with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("CA-MV-120 y VAL-004 — un tipo que no existe es 400 y no una página vacía")
  void tipoInexistente() throws Exception {
    // Como el estado y al revés que las personas: el catálogo es cerrado
    // (`RN-MV-017`), y una página vacía diría «no tienes ninguno así».
    mvc.perform(get("/api/v1/movements/mine?type=INVENTADO").with(como(vendedor)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("type"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
  }

  @Test
  @DisplayName("CA-MV-133 — el filtro por método de pago; uno que no existe da página vacía")
  void filtroPorMetodoDePago() throws Exception {
    // La propia pasa a PSE; las demás siguen con tarjeta.
    jdbc.update(
        "UPDATE movements SET payment_method_id = CAST(? AS uuid) WHERE id = ?", PSE, propia);

    mvc.perform(get("/api/v1/movements/mine").param("paymentMethodId", PSE).with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(propia.toString()));
    mvc.perform(
            get("/api/v1/movements/mine")
                .param("paymentMethodId", UUID.randomUUID().toString())
                .with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-134 — el filtro por código encuentra el comprobante escrito como sea, y NADA si es ajeno")
  void filtroPorCodigo() throws Exception {
    String codigo =
        jdbc.queryForObject("SELECT code FROM movements WHERE id = ?", String.class, vendida);
    String ajeno =
        jdbc.queryForObject("SELECT code FROM movements WHERE id = ?", String.class, deOtros);

    mvc.perform(
            get("/api/v1/movements/mine").param("code", codigo.toLowerCase()).with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(vendida.toString()));
    // El alcance va antes que el filtro: conocer un código no abre lo que no es propio.
    mvc.perform(get("/api/v1/movements/mine").param("code", ajeno).with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    // Y desde el 22-09-2026 tampoco lo que VENDIÓ: el vendedor conoce el código
    // de `vendida` y su listado no se lo devuelve (`CA-MV-137`).
    mvc.perform(get("/api/v1/movements/mine").param("code", codigo).with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-135 — el periodo incluye `from`, excluye `to`, se combina, y el invertido es 400 VAL-005")
  void filtroPorPeriodo() throws Exception {
    // vendida el 1 de agosto, propia el 2: [1, 2) es solo la primera, y quien la
    // ve es su comprador.
    mvc.perform(
            get("/api/v1/movements/mine")
                .param("from", BASE.toString())
                .param("to", BASE.plusDays(1).toString())
                .with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(vendida.toString()));
    // Combinado con el estado: [1, 3) son las dos, y confirmada solo la propia.
    mvc.perform(
            get("/api/v1/movements/mine")
                .param("from", BASE.toString())
                .param("to", BASE.plusDays(2).toString())
                .param("status", "CONFIRMADA")
                .with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(propia.toString()));
    mvc.perform(
            get("/api/v1/movements/mine")
                .param("from", BASE.plusDays(2).toString())
                .param("to", BASE.toString())
                .with(como(vendedor)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("from"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
  }

  @Test
  @DisplayName("VAL-003 — un estado que no existe es 400 y no una página vacía")
  void estadoInexistente() throws Exception {
    // La diferencia importa: una página vacía diría «no tienes ninguno así», que
    // es una respuesta falsa a una pregunta mal escrita.
    mvc.perform(get("/api/v1/movements/mine?status=INVENTADO").with(como(vendedor)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
  }

  // ---------------------------------------------------------------------------
  // El detalle
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-044 — el detalle propio devuelve sus líneas")
  void detallePropio() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/{id}", vendida).with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(vendida.toString()))
        .andExpect(jsonPath("$.code").isNotEmpty())
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.lines[0].productCode").value("MINE_BOT"))
        // EL NOMBRE Y LA DESCRIPCION SALEN DE LA LINEA (`RN-MV-002`): son copias,
        // y por eso el producto del catálogo puede renombrarse sin cambiar esto.
        .andExpect(jsonPath("$.lines[0].productName").value("Bot de prueba"))
        .andExpect(jsonPath("$.lines[0].productDescription").value("Lo que decia el catalogo"))
        .andExpect(jsonPath("$.lines[0].quantity").value(1))
        .andExpect(jsonPath("$.lines[0].unitPrice").value(100.00))
        // El vendedor es de la línea (`RN-MV-003`), y el detalle lo trae ahí.
        .andExpect(jsonPath("$.lines[0].seller.username").value("mine-vendedor"))
        .andExpect(jsonPath("$.user.username").value("mine-cliente"));
  }

  @Test
  @DisplayName(
      "CA-MV-138 — el detalle SÍ abre lo vendido: la venta que el vendedor no ve en su listado la"
          + " abre por su identificador")
  void elDetalleAbreLoVendido() throws Exception {
    // LA ASIMETRÍA ES DELIBERADA (`spec.md` §2): acotar también el detalle
    // dejaría al vendedor sin ninguna vía para abrir una venta suya, porque
    // `RF-MV-007` no existe y `RF-MV-015` es solo listado. Quien venga a
    // «ponerlo coherente con el listado» hace fallar esta prueba.
    mvc.perform(get("/api/v1/movements/mine").with(como(vendedor)))
        .andExpect(jsonPath("$.content[?(@.id == '" + vendida + "')]").isEmpty());

    mvc.perform(get("/api/v1/movements/mine/{id}", vendida).with(como(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(vendida.toString()))
        .andExpect(jsonPath("$.lines[0].seller.username").value("mine-vendedor"));
  }

  @Test
  @DisplayName("CA-MV-045 y EX-002 — el detalle AJENO responde 404, igual que uno inexistente")
  void detalleAjeno() throws Exception {
    // LAS DOS RESPUESTAS SON LA MISMA, y esa es la decisión: un `403` diría
    // «existe pero no es tuyo», y con un identificador que alguien esté probando
    // eso ya es información.
    mvc.perform(get("/api/v1/movements/mine/{id}", deOtros).with(como(vendedor)))
        .andExpect(status().isNotFound());

    mvc.perform(get("/api/v1/movements/mine/{id}", UUID.randomUUID()).with(como(vendedor)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-MV-045 — tener movements:read tampoco abre el detalle ajeno")
  void detalleAjenoConPermiso() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/{id}", deOtros).with(conPermisoDeLectura(vendedor)))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------
  // Rutas y acceso
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-046 — responde a quien porta movements:list-own y ningún otro permiso; sin él, 403"
          + " (RF-SP-062)")
  void sinPermisoResponde() throws Exception {
    mvc.perform(
            get("/api/v1/movements/mine")
                .with(user(cliente.toString()).authorities(() -> "movements:list-own")))
        .andExpect(status().isOk());
    // Hasta el 21-09-2026 bastaba con estar autenticado; ya no.
    mvc.perform(get("/api/v1/movements/mine").with(user(cliente.toString()).authorities()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CA-MV-047 — sin autenticar responde 401")
  void sinAutenticar() throws Exception {
    mvc.perform(get("/api/v1/movements/mine")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/movements/mine/{id}", vendida)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("`mine` no lo captura una variable de ruta")
  void mineNoEsUnIdentificador() throws Exception {
    // Hoy este controlador no tiene `/{id}`, pero `RF-MV-007` lo traerá. El
    // síntoma de romperlo sería un `400` por identificador inválido en la ruta
    // que más se usa, y esta prueba lo convierte en un fallo con nombre.
    mvc.perform(get("/api/v1/movements/mine").with(como(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  // Desde RF-SP-062 (21-09-2026) lo propio exige permiso —autenticarse no autoriza
  // nada—: el actor porta la familia de alcance propio de MV, que es lo que V31
  // da a todo rol. Hasta entonces bastaba con `user(id)`.
  private RequestPostProcessor como(UUID persona) {
    return user(persona.toString())
        .authorities(
            () -> "movements:list-own",
            () -> "movements:read-own",
            () -> "movements:read-own-products");
  }

  // El permiso de administración ADEMÁS de los propios: lo que se afirma con él es
  // que no amplía el alcance, y para llegar a la consulta hay que poder entrar
  // (RF-SP-062). Solo con movements:read la ruta es 403 —OwnScopePermissionsIT—.
  private RequestPostProcessor conPermisoDeLectura(UUID persona) {
    return user(persona.toString())
        .authorities(
            () -> "movements:read",
            () -> "movements:list-own",
            () -> "movements:read-own",
            () -> "movements:read-own-products");
  }

  // Tambien AL TERMINAR: la ultima prueba dejaba movimientos y sus detalles apuntando
  // a los productos de esta clase, y cualquier suite posterior que empiece con
  // "DELETE FROM products" a secas —treinta y siete lo hacen— caia por la clave
  // foranea de movement_details. Con el orden local no se veia; en CI si (21-09-2026).
  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    // DESPUÉS de los movimientos, que lo referencian; y siempre, para que el
    // catálogo quede con su única fila.
    jdbc.update(
        "DELETE FROM movement_type_statuses WHERE movement_type_id IN"
            + " (SELECT id FROM movement_types WHERE code = 'PRUEBA_DEPOSITO')");
    jdbc.update("DELETE FROM movement_types WHERE code = 'PRUEBA_DEPOSITO'");
    jdbc.update("DELETE FROM products WHERE code LIKE 'MINE_%'");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'mine-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'mine-%'");
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    darElSuelo(jdbc, id);
    return id;
  }

  private UUID producto() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, 'MINE_BOT', 'BOT', 'Bot de prueba', 'Producto de prueba', NULL,"
            + " NULL, CAST(? AS numeric), CAST(? AS uuid), NULL, 'ACTIVO')",
        id,
        "100.00",
        USD);
    return id;
  }

  private UUID movimiento(UUID cliente, UUID vendedor, String estado, OffsetDateTime cuando) {
    return movimiento(cliente, vendedor, estado, cuando, VENTA);
  }

  /**
   * Con el tipo elegido. Si es {@link #TIPO_DE_PRUEBA}, lo siembra antes: el catálogo no se toca
   * por API (`RN-MV-017`) y ninguna migración lo trae.
   */
  private UUID movimiento(
      UUID cliente, UUID vendedor, String estado, OffsetDateTime cuando, String tipo) {
    if (TIPO_DE_PRUEBA.equals(tipo)) {
      jdbc.update(
          """
          INSERT INTO movement_types (id, code, name, prefix)
          VALUES (CAST(? AS uuid), 'PRUEBA_DEPOSITO', 'Deposito de prueba', 'DEP')
          ON CONFLICT (id) DO NOTHING
          """,
          tipo);
      // Todo movimiento lleva el estado de SU tipo (`RN-MV-033`, clave compuesta
      // de `V36`): el tipo de prueba necesita el suyo.
      jdbc.update(
          """
          INSERT INTO movement_type_statuses (id, movement_type_id, code, name)
          VALUES (gen_random_uuid(), CAST(? AS uuid), 'VALIDADO', 'Validado')
          ON CONFLICT ON CONSTRAINT uq_movement_type_statuses_code DO NOTHING
          """,
          tipo);
    }
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, payment_method_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at)
        VALUES (?, CAST(? AS uuid), (SELECT s.id FROM movement_type_statuses s WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'), ?, CAST(? AS uuid), CAST(? AS uuid), ?, ?,
                100.00, 0, 100.00, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) ELSE NULL END)
        """,
        id,
        tipo,
        tipo,
        cliente,
        TARJETA,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        cuando.toString(),
        // `ck_movements_confirmed` ata las dos columnas: confirmada implica fecha de
        // confirmacion, y al reves. Ponerla siempre —o nunca— hace fallar la mitad.
        estado,
        cuando.toString());

    linea(id, producto, vendedor);
    return id;
  }

  /** Una segunda línea, de otro producto, para el mismo vendedor. */
  private void segundaLinea(UUID movimiento, UUID vendedor) {
    UUID otro = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, 'MINE_BOT_2', 'BOT', 'Otro bot', 'Producto de prueba', NULL,"
            + " NULL, CAST(? AS numeric), CAST(? AS uuid), NULL, 'ACTIVO')",
        otro,
        "100.00",
        USD);
    linea(movimiento, otro, vendedor);
  }

  private void linea(UUID movimiento, UUID producto, UUID vendedor) {
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price,
                                      line_amount, validity_days, implementation)
        VALUES (?, ?, ?, ?, 'Bot de prueba', 'Lo que decia el catalogo', 1, 100.00, 100.00, NULL,
                'AUTOMATICA')
        """,
        UUID.randomUUID(),
        movimiento,
        producto,
        vendedor);
  }
}
