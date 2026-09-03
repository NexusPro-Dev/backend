package com.factech.nexus.modules.products.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hamcrest.Matchers;
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
 * El listado del catálogo (`RF-PM-002` · `T-10`).
 *
 * <p>Cubre los criterios de `spec.md` §12. El catálogo se siembra <b>por la base</b> y no por el
 * endpoint de alta: hace falta fijar el instante de creación, el estado y la marca de retiro, y
 * ninguno de los tres se puede pasar por HTTP —el alta los decide ella—.
 *
 * <p>Los filtros van por {@code param} y no escritos en la dirección: un término con {@code %} o
 * con espacios en la plantilla de la URL se codifica dos veces, y la prueba pasaría a comprobar el
 * codificador de la prueba en lugar del escape del repositorio.
 */
@AutoConfigureMockMvc
class ProductListIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`, estable en todos los entornos. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;
  private UUID plata;
  private UUID free;

  @BeforeEach
  void sembrarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    // La cadena va encadenada de verdad: `uq_memberships_parent` es UNIQUE
    // NULLS NOT DISTINCT, de modo que solo UNA membresía puede no tener
    // superior. Dos raíces se aceptarían fila a fila y reventarían en el
    // COMMIT, lejos de donde se escribieron.
    oro = membresia("ORO", "Oro", 1, null);
    plata = membresia("PLATA", "Plata", 2, oro);
    // El SUELO de la cadena: el origen de todo upgrade que se siembre aqui.
    free = membresia("FREE", "Free", 3, plata);

    // Cinco productos, cada uno una hora después del anterior: el orden de alta
    // queda determinado y las pruebas de orden pueden afirmar cuál va primero.
    upgrade("UPGRADE_ORO", "Ascenso a Oro", oro, "49.99", 30, "ACTIVO", BASE);
    upgrade(
        "UPGRADE_PLATA", "Ascenso a Plata", plata, "19.99", null, "INACTIVO", BASE.plusHours(1));
    bot("ASESORIA", "Asesoría personalizada", "10.00", null, "ACTIVO", BASE.plusHours(2));
    bot("MEMBRESIA_EXTRA", "Membresía de cortesía", "5.00", 7, "INACTIVO", BASE.plusHours(3));
    bot("SOPORTE", "Soporte prioritario", "99.50", null, "ACTIVO", BASE.plusHours(4));
  }

  @Test
  @DisplayName("`CA-PM-013` — devuelve el catálogo paginado con el total que cumple el filtro")
  void catalogoPaginado() throws Exception {
    mvc.perform(listado().param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalIsExact").value(true));
  }

  @Test
  @DisplayName("`CA-PM-014` — filtra por tipo, y el tipo se admite en cualquier caja")
  void filtraPorTipo() throws Exception {
    mvc.perform(listado().param("type", "UPGRADE_MEMBRESIA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(
            jsonPath("$.content[*].type")
                .value(Matchers.everyItem(Matchers.is("UPGRADE_MEMBRESIA"))));

    // En minúsculas es la MISMA pregunta. Validar sin normalizar la aceptaría y
    // devolvería la colección vacía, que es el peor de los dos resultados: un
    // `200` que miente en lugar de un `400` que corrige.
    mvc.perform(listado().param("type", "bot"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  @DisplayName("`CA-PM-015` — filtra por estado y devuelve también los inactivos cuando se piden")
  void filtraPorEstado() throws Exception {
    mvc.perform(listado().param("status", "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3));

    mvc.perform(listado().param("status", "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("`CA-PM-016` — filtra los upgrades por su membresía destino")
  void filtraPorDestino() throws Exception {
    mvc.perform(listado().param("targetMembershipId", oro.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].code").value("UPGRADE_ORO"));

    mvc.perform(listado().param("targetMembershipId", plata.toString()))
        .andExpect(jsonPath("$.content[0].code").value("UPGRADE_PLATA"));
  }

  @Test
  @DisplayName("un destino que no existe devuelve vacío y NO un error: filtró por algo que no está")
  void destinoInexistenteNoEsError() throws Exception {
    mvc.perform(listado().param("targetMembershipId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    // Y la combinación inútil tampoco se rechaza: ningún bot tiene
    // destino, de modo que el resultado es siempre vacío y eso es coherente.
    mvc.perform(listado().param("type", "BOT").param("targetMembershipId", oro.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-PM-017` — busca por nombre sin distinguir mayúsculas ni acentos")
  void busquedaInsensible() throws Exception {
    // «MEMBRESIA» sin tilde y en mayúsculas encuentra «Membresía»: la
    // normalización la hace la base con la misma función que alimenta el
    // índice.
    mvc.perform(listado().param("search", "MEMBRESIA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].code").value("MEMBRESIA_EXTRA"));

    mvc.perform(listado().param("search", "asesor"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    // En blanco equivale a ausente: buscar por espacios es no buscar.
    mvc.perform(listado().param("search", "   "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5));
  }

  @Test
  @DisplayName("la búsqueda con comodines NO amplía la consulta a todo el catálogo")
  void busquedaConComodines() throws Exception {
    // Sin escapar, `%` devolvería los cinco.
    mvc.perform(listado().param("search", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    // Y `_` devolvería todo lo que tenga un carácter en esa posición, que es
    // todo. Ningún NOMBRE lo contiene —los códigos sí, y la búsqueda no va
    // sobre ellos—.
    mvc.perform(listado().param("search", "_"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-PM-018` — los retirados quedan fuera salvo que se pidan, y con su fecha")
  void retirados() throws Exception {
    retirar("SOPORTE");

    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[*].code").value(Matchers.not(Matchers.hasItem("SOPORTE"))));

    mvc.perform(listado().param("includeDeleted", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5));

    mvc.perform(listado().param("includeDeleted", "true").param("search", "Soporte"))
        .andExpect(jsonPath("$.content[0].code").value("SOPORTE"))
        .andExpect(jsonPath("$.content[0].deletedAt").exists());
  }

  @Test
  @DisplayName("un retirado y uno vivo pueden compartir nombre, y son dos filas y no una")
  void retiradoYVivoConElMismoNombre() throws Exception {
    // La unicidad del nombre es entre los VIVOS: `uq_products_name` es parcial.
    retirar("SOPORTE");
    bot("SOPORTE_2", "Soporte prioritario", "120.00", null, "ACTIVO", BASE.plusHours(5));

    mvc.perform(listado().param("includeDeleted", "true").param("search", "Soporte"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName(
      "`CA-PM-077` — los retirados no exigen permiso propio, y el listado NO lleva el motivo")
  void retiradosSinPermisoPropioYSinMotivo() throws Exception {
    retirar("SOPORTE");

    // Basta `products:read`, el mismo permiso con el que se ve el resto.
    mvc.perform(listado().param("includeDeleted", "true").param("search", "Soporte"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].deletedAt").exists())
        // Uno a uno el motivo es una consulta y lo devuelve `RF-PM-003`; en
        // bloque sería una exportación de decisiones comerciales.
        .andExpect(jsonPath("$.content[0].deletionReason").doesNotExist())
        .andExpect(jsonPath("$.content[0].reason").doesNotExist());
  }

  @Test
  @DisplayName("`CA-PM-019` — el destino de cada upgrade llega con su nombre y su nivel")
  void destinoResuelto() throws Exception {
    mvc.perform(listado().param("search", "Ascenso a Oro"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].targetMembership.code").value("ORO"))
        .andExpect(jsonPath("$.content[0].targetMembership.name").value("Oro"))
        .andExpect(jsonPath("$.content[0].targetMembership.level").value(1))
        .andExpect(jsonPath("$.content[0].currency.code").value("USD"))
        .andExpect(jsonPath("$.content[0].currency.decimalPlaces").value(2))
        // El precio en la escala de SU MONEDA y no en la de la columna: 49.99 y
        // no 49.9900.
        .andExpect(jsonPath("$.content[0].price").value(49.99));
  }

  @Test
  @DisplayName("un bot trae el destino NULO Y PRESENTE, no ausente")
  void botSinDestino() throws Exception {
    mvc.perform(listado().param("search", "Soporte"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].targetMembership").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].targetMembership").hasJsonPath());
  }

  @Test
  @DisplayName("`T-15` — la vigencia viaja en cada fila, vacía y presente cuando no caduca")
  void vigenciaEnElListado() throws Exception {
    mvc.perform(listado().param("search", "Ascenso a Oro"))
        .andExpect(jsonPath("$.content[0].validityDays").value(30));

    // La diferencia comercial más importante entre dos filas por lo demás
    // idénticas: un ascenso permanente y uno de treinta días.
    mvc.perform(listado().param("search", "Soporte"))
        .andExpect(jsonPath("$.content[0].validityDays").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].validityDays").hasJsonPath());
  }

  @Test
  @DisplayName("`CA-PM-020` — los parámetros inválidos se enumeran TODOS JUNTOS")
  void rechazaTodosLosParametrosJuntos() throws Exception {
    mvc.perform(
            listado()
                .param("page", "-1")
                .param("size", "5000")
                .param("type", "BASURA")
                .param("status", "BASURA")
                .param("sort", "basura"))
        .andExpect(status().isBadRequest())
        // Cinco: la página, el tamaño, el tipo, el estado y el orden. De uno en
        // uno serían cinco vueltas para corregir una sola dirección.
        .andExpect(jsonPath("$.errors.length()").value(5))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(Matchers.hasItems("page", "size", "type", "status", "sort")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(Matchers.hasItems("VAL-001", "VAL-002", "VAL-003", "VAL-005")));
  }

  @Test
  @DisplayName("`VAL-004` — el identificador mal formado se rechaza, y no se busca lo inexistente")
  void identificadorMalFormado() throws Exception {
    // Lo rechaza el editor canónico de `shared/error`: `UUID.fromString` acepta
    // `1-1-1-1-1` y lo convertiría en un identificador válido que no existe, de
    // modo que el actor recibiría una lista vacía en lugar de su error.
    mvc.perform(listado().param("targetMembershipId", "1-1-1-1-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-021` — un filtro sin coincidencias devuelve colección vacía y total cero")
  void sinCoincidencias() throws Exception {
    mvc.perform(listado().param("search", "inexistente"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));
  }

  @Test
  @DisplayName("`FA-002` — la página más allá de la última devuelve el total REAL, no uno deducido")
  void paginaMasAllaDeLaUltima() throws Exception {
    mvc.perform(listado().param("page", "99").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        // Y no 1980, que es lo que daría deducir el total del desplazamiento.
        .andExpect(jsonPath("$.totalElements").value(5));
  }

  @Test
  @DisplayName("`CA-PM-022` — sin el permiso de lectura, la consulta se rechaza")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/products")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:create")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("`CA-PM-074` — sin `sort`, el catálogo va en orden de alta descendente")
  void ordenPorOmision() throws Exception {
    mvc.perform(listado())
        .andExpect(status().isOk())
        // El último dado de alta primero: quien gobierna el catálogo trabaja
        // sobre lo último que entró.
        .andExpect(jsonPath("$.content[0].code").value("SOPORTE"))
        .andExpect(jsonPath("$.content[4].code").value("UPGRADE_ORO"))
        // Y la respuesta DICE sobre qué está paginando.
        .andExpect(jsonPath("$.sort").value("createdAt,desc"));
  }

  @Test
  @DisplayName("`CA-PM-075` — ordena por nombre, por precio y por fecha cuando se le pide")
  void ordenConfigurable() throws Exception {
    mvc.perform(listado().param("sort", "name,asc"))
        .andExpect(jsonPath("$.content[0].name").value("Ascenso a Oro"))
        .andExpect(jsonPath("$.sort").value("name,asc"));

    mvc.perform(listado().param("sort", "price,desc"))
        .andExpect(jsonPath("$.content[0].code").value("SOPORTE"))
        .andExpect(jsonPath("$.content[4].code").value("MEMBRESIA_EXTRA"));

    mvc.perform(listado().param("sort", "createdAt,asc"))
        .andExpect(jsonPath("$.content[0].code").value("UPGRADE_ORO"));
  }

  @Test
  @DisplayName("`CA-PM-075` — un campo fuera de la lista se RECHAZA, no se ignora")
  void ordenFueraDeLaLista() throws Exception {
    mvc.perform(listado().param("sort", "price; DROP TABLE products"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"))
        .andExpect(jsonPath("$.errors[0].field").value("sort"));

    // Ordenar por el código no está admitido aunque la columna exista: es un
    // identificador, y ordenar por él no responde ninguna pregunta.
    mvc.perform(listado().param("sort", "code,asc")).andExpect(status().isBadRequest());

    // Y el sentido también es dominio cerrado.
    mvc.perform(listado().param("sort", "name,arriba")).andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/products")
        .with(user(UUID.randomUUID().toString()).authorities(() -> "products:read"));
  }

  private void retirar(String codigo) {
    jdbc.update("UPDATE products SET deleted_at = ? WHERE code = ?", BASE.plusHours(9), codigo);
  }

  private UUID membresia(String codigo, String nombre, int nivel, UUID superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (CAST(? AS uuid), ?, ?, CAST(? AS uuid), ?,"
            + " upper(lpad(to_hex(? * 4919), 6, '0')))",
        id.toString(),
        codigo,
        nombre,
        superior == null ? null : superior.toString(),
        nivel,
        nivel);
    return id;
  }

  private void upgrade(
      String codigo,
      String nombre,
      UUID destino,
      String precio,
      Integer vigencia,
      String estado,
      OffsetDateTime creado) {
    insertar(codigo, "UPGRADE_MEMBRESIA", nombre, destino, precio, vigencia, estado, creado);
  }

  private void bot(
      String codigo,
      String nombre,
      String precio,
      Integer vigencia,
      String estado,
      OffsetDateTime creado) {
    insertar(codigo, "BOT", nombre, null, precio, vigencia, estado, creado);
  }

  /**
   * Siembra un producto con su instante de alta fijado.
   *
   * <p>Los parámetros nulos van con {@code CAST} explícito: sin el tipo, el controlador no puede
   * decidir a qué convierte el nulo y PostgreSQL rechaza la sentencia entera.
   */
  private void insertar(
      String codigo,
      String tipo,
      String nombre,
      UUID destino,
      String precio,
      Integer vigencia,
      String estado,
      OffsetDateTime creado) {

    // Origen y destino VIAJAN JUNTOS: un upgrade declara los dos
    // (`RN-PM-002`) y un bot no declara ninguno. Por eso el origen se
    // deriva del destino en lugar de ser un parametro mas — nunca puede
    // quedar uno sin el otro, que es lo que `ck_products_type_target` mira.
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, NULL,"
            + " CAST(? AS uuid), CAST(? AS uuid), CAST(? AS numeric),"
            + " CAST(? AS uuid), CAST(? AS integer), ?, ?, ?)",
        UUID.randomUUID().toString(),
        codigo,
        tipo,
        nombre,
        destino == null ? null : free.toString(),
        destino == null ? null : destino.toString(),
        precio,
        USD,
        vigencia,
        estado,
        creado,
        creado);
  }

  /**
   * Deja `products` vacía al terminar CADA prueba.
   *
   * <p><b>No es higiene: es lo que impide romper a otras clases.</b> Un producto que sobreviva a
   * esta clase mantiene una clave foránea sobre `memberships`, y varias pruebas de `SP` empiezan
   * con `DELETE FROM memberships WHERE level > 0`. Ese borrado falla con violación de integridad, y
   * el fallo aparece <b>lejos de aquí</b> —en la clase que borra— y solo cuando el orden de
   * ejecución las pone en ese orden, que es la peor forma de romper una suite.
   */
  @AfterEach
  void vaciarCatalogo() {
    jdbc.update("DELETE FROM products");
  }
}
