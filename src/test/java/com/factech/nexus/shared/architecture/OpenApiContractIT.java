package com.factech.nexus.shared.architecture;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El contrato publicado incluye todo endpoint declarado (Art. VIII.2, VIII.6).
 *
 * <p><b>Por qué existe.</b> Que un endpoint aparezca en la documentación depende de que springdoc
 * lo descubra, y ese descubrimiento es implícito: nadie lo declara en ningún sitio, de modo que su
 * ausencia no rompe nada y no se nota hasta que alguien abre Swagger y no encuentra lo que busca.
 * Un endpoint que existe y no está documentado incumple el Art. VIII.2 en silencio.
 *
 * <p>Esta prueba convierte ese silencio en un fallo. Cada requerimiento que estrene un endpoint
 * debería añadir aquí su ruta.
 */
@AutoConfigureMockMvc
class OpenApiContractIT extends IntegrationTestBase {

  /** Destino del contrato publicado. Relativo a la raíz del proyecto (ADR-001). */
  private static final Path DESTINO = Path.of("docs", "api", "openapi.json");

  /** El mismo contrato en YAML, que es el formato que asumen los generadores de cliente. */
  private static final Path DESTINO_YAML = Path.of("docs", "api", "openapi.yaml");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;

  @Test
  @DisplayName("el contrato declara CÓMO se autentica, o la documentación es inutilizable")
  void elContratoDeclaraElEsquemaDeSeguridad() throws Exception {
    // Sin esto, Swagger UI no muestra el botón «Authorize» y no hay forma de
    // adjuntar la cabecera desde la interfaz: TODA operación protegida responde
    // 401 y quien explora la API concluye que está rota. El contrato describía
    // cada endpoint y cada permiso, y callaba lo único que hacía falta para
    // probarlos.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
        // Global y no por operación: la regla del sistema es que todo exige
        // token salvo tres rutas, de modo que declararlo endpoint por endpoint
        // haría que cada uno nuevo naciera sin ella sin romper nada.
        .andExpect(jsonPath("$.security[0].bearerAuth").exists());
  }

  @Test
  @DisplayName("los tres endpoints de sesión NO heredan el esquema: pedirían el token para darlo")
  void laSesionNoExigeToken() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v1/auth/login'].post.security", org.hamcrest.Matchers.empty()))
        .andExpect(
            jsonPath(
                "$.paths['/api/v1/auth/refresh'].post.security", org.hamcrest.Matchers.empty()))
        .andExpect(
            jsonPath(
                "$.paths['/api/v1/auth/logout'].post.security", org.hamcrest.Matchers.empty()));
  }

  @Test
  @DisplayName("el contrato publica POST /api/v1/roles con su permiso y sus estados")
  void elAltaDeRolEstaDocumentada() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post.summary").value("Registrar un rol"))
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post.responses.201").exists())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post.responses.409").exists())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].post.responses.422").exists());
  }

  @Test
  @DisplayName("el contrato sigue publicando GET /api/v1/permissions")
  void elCatalogoDePermisosSigueDocumentado() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/permissions'].get").exists());
  }

  @Test
  @DisplayName("el contrato publica los tres endpoints de membresías")
  void lasMembresiasEstanDocumentadas() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/memberships'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/memberships'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/memberships/{id}'].get").exists());
  }

  @Test
  @DisplayName("membresías NO declara edición ni eliminación: RN-SP-008 las prohíbe")
  void lasMembresiasNoSeEditanNiSeEliminan() throws Exception {
    // La ausencia es la implementación: no se cumple con código que rechace, se
    // cumple porque no hay a qué llamar. Si algún día apareciera aquí un PUT,
    // sería que alguien lo añadió sin pasar por la compuerta.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/memberships'].put").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/memberships'].delete").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/memberships/{id}'].put").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/memberships/{id}'].patch").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/memberships/{id}'].delete").doesNotExist());
  }

  @Test
  @DisplayName("el contrato publica los dos endpoints de monedas")
  void lasMonedasEstanDocumentadas() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/currencies'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/currencies/{id}/status'].patch").exists());
  }

  @Test
  @DisplayName("monedas NO declara alta, edición ni eliminación: RN-SP-010 las prohíbe")
  void elCatalogoDeMonedasEsInmutable() throws Exception {
    // El estado se cambia sobre el subrecurso `/status`; el recurso completo no
    // está mapeado para ningún método, y esa ausencia ES la implementación.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/currencies'].post").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/currencies'].put").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/currencies'].delete").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/currencies/{id}']").doesNotExist());
  }

  @Test
  @DisplayName("el contrato publica los tres endpoints de países")
  void losPaisesEstanDocumentados() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/countries'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/countries'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/countries/{id}/status'].patch").exists());
  }

  @Test
  @DisplayName("países NO declara edición ni eliminación: RN-SP-009 las prohíbe")
  void elCatalogoDePaisesEsInmutable() throws Exception {
    // Ni siquiera existe la ruta del país individual: el estado se cambia sobre
    // el subrecurso, y por eso un PATCH sobre `/{id}` devuelve 404 y no 405.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/countries'].put").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/countries'].delete").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/countries/{id}']").doesNotExist());
  }

  @Test
  @DisplayName("el listado y el detalle de personas están publicados con sus parámetros")
  void lasConsultasDePersonasEstanDocumentadas() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users'].get").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/users'].get.parameters[?(@.name == 'search')]").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/users'].get.parameters[?(@.name == 'includeDeleted')]")
                .exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}'].get.responses.404").exists());
  }

  @Test
  @DisplayName("el listado NO publica ningún parámetro derivado de la credencial")
  void elListadoNoOfreceOrdenarPorLaCredencial() throws Exception {
    // El contrato es lo que la gente lee para saber qué puede pedir. Publicar un
    // parámetro que ordena por la marca de cambio obligatorio invitaría a pedir
    // la lista de quien no ha cambiado su contraseña inicial, y el rechazo
    // llegaría después de haberlo sugerido.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v1/users'].get.parameters[?(@.name == 'mustChangePassword')]")
                .doesNotExist())
        .andExpect(
            jsonPath("$.paths['/api/v1/users'].get.parameters[?(@.name == 'failedAttempts')]")
                .doesNotExist());
  }

  @Test
  @DisplayName("el ciclo de vida de una persona: PATCH para editar, subrecurso para estado y baja")
  void elCicloDeVidaEstaDocumentado() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}'].patch").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}'].patch.responses.409").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/status'].patch").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/deletion'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/deletion'].post.responses.204").exists());
  }

  @Test
  @DisplayName("una persona NO se elimina con DELETE ni se reemplaza con PUT")
  void laBajaNoEsUnDelete() throws Exception {
    // `DELETE` con cuerpo lo puede descartar un intermediario, y la petición se
    // convertiría en un rechazo por motivo ausente que el actor no entiende ni
    // puede corregir. `PUT` obligaría a enviar el recurso completo, incluidos el
    // nombre de usuario, el estado y los roles, que la edición no puede tocar.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}'].delete").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}'].put").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/deletion'].delete").doesNotExist());
  }

  @Test
  @DisplayName("los roles de una persona se asignan por POST y se retiran por un subrecurso")
  void losRolesDeUnaPersonaEstanDocumentados() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/roles'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/roles'].post.responses.409").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/roles'].post.responses.422").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/roles/revocations'].post").exists());
  }

  @Test
  @DisplayName("el retiro de roles NO se publica como DELETE ni como PUT sobre la lista")
  void elRetiroNoEsUnDelete() throws Exception {
    // `PUT` invitaría a leer la asignación como un reemplazo, y un reemplazo
    // haría retiros implícitos que se saltarían `RN-SP-001`, `RN-SP-015` y
    // `RN-SP-022`. `DELETE` con cuerpo lo puede descartar un intermediario sin
    // avisar, y el retiro llegaría sin roles: un fallo silencioso en la
    // operación que revoca sesiones.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/roles'].put").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/roles'].delete").doesNotExist())
        .andExpect(
            jsonPath("$.paths['/api/v1/users/{id}/roles/revocations'].delete").doesNotExist());
  }

  @Test
  @DisplayName("la membresía de una persona se fija con PUT y se retira con DELETE")
  void laMembresiaDeUnaPersonaEstaDocumentada() throws Exception {
    // `PUT` y no `POST` porque el cuerpo **sí** representa el estado final: la
    // persona tiene una membresía o ninguna. Y `DELETE` se conserva porque esta
    // operación no lleva cuerpo, de modo que el problema que obligó a cambiarlo
    // en el retiro de roles no existe aquí.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/membership'].put").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/membership'].put.responses.409").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/membership'].put.responses.422").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/membership'].delete").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/users/{id}/membership'].delete.responses.204").exists());
  }

  @Test
  @DisplayName("el retiro de membresía NO declara cuerpo de petición ni POST")
  void elRetiroDeMembresiaNoLlevaCuerpo() throws Exception {
    // Que no lleve cuerpo es justo lo que le permite seguir siendo un `DELETE`;
    // si algún día apareciera aquí un `requestBody`, esa justificación dejaría
    // de valer y habría que convertirlo en un subrecurso.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v1/users/{id}/membership'].delete.requestBody").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/membership'].post").doesNotExist());
  }

  @Test
  @DisplayName("la estructura comercial publica el PATCH del superior y el GET del equipo")
  void laEstructuraComercialEstaDocumentada() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/supervisor'].patch").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/users/{id}/supervisor'].patch.responses.409").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/team'].get").exists());
  }

  @Test
  @DisplayName("el superior NO se puede retirar ni fijar con PUT, y el equipo NO admite filtros")
  void losLimitesDeLaEstructuraComercial() throws Exception {
    // `PUT` invitaría a pensar que se puede enviar el periodo, que lo fija el
    // sistema. Un `DELETE` publicaría un «vendedor sin superior» que no existe.
    // Y un filtro sobre el equipo replicaría la semántica de `RF-SP-025` sobre un
    // subconjunto, obligando a mantener dos filtrados sincronizados.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/supervisor'].put").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/supervisor'].delete").doesNotExist())
        .andExpect(
            jsonPath("$.paths['/api/v1/users/{id}/team'].get.parameters[?(@.name == 'search')]")
                .doesNotExist())
        .andExpect(
            jsonPath("$.paths['/api/v1/users/{id}/team'].get.parameters[?(@.name == 'status')]")
                .doesNotExist());
  }

  @Test
  @DisplayName("las contraseñas y el perfil propio están publicados, cada uno en su sitio")
  void lasCredencialesPropiasEstanDocumentadas() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/auth/password'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/password'].post.responses.422").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/password'].post.responses.423").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/{id}/password-reset'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get").exists());
  }

  @Test
  @DisplayName("cambiar la propia contraseña SÍ exige token, aunque cuelgue de /auth")
  void elCambioDeContrasenaExigeToken() throws Exception {
    // Es el único de esa sección que lo exige, y por eso reintroduce el esquema
    // que la clase desheredó: sin alguien autenticado no hay sujeto.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v1/auth/password'].post.security[0].bearerAuth").exists());
  }

  @Test
  @DisplayName("el perfil propio NO declara parámetros de ningún tipo")
  void elPerfilPropioNoAdmiteParametros() throws Exception {
    // `me` es un literal, no un identificador: admitir uno lo convertiría en la
    // consulta de detalle sin su permiso.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.parameters").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.requestBody").doesNotExist())
        // Y ninguno de los tres estados que no le corresponden.
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses.403").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses.404").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses.400").doesNotExist());
  }

  @Test
  @DisplayName("la sesión publica sus tres endpoints con los estados que la distinguen")
  void laSesionEstaDocumentada() throws Exception {
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses.401").exists())
        // El 423 es lo que hace visible en el contrato la única excepción
        // consciente al mensaje genérico de credenciales.
        .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses.423").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.responses.401").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses.204").exists());
  }

  @Test
  @DisplayName("el cierre de sesión NO declara 401 ni 404: no distingue tokens")
  void elCierreNoDeclaraOraculo() throws Exception {
    // Que estos estados no existan en el contrato es la forma comprobable de
    // decir que el cierre no revela si un token es del sistema.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses.401").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses.404").doesNotExist());
  }

  @Test
  @DisplayName("el alta de rol NO declara manejadores que el requerimiento no tiene")
  void sinVerbosNoDeclarados() throws Exception {
    // `RF-SP-001` solo declara el POST. Si algún día aparece aquí un PUT o un
    // DELETE sin que su requerimiento lo declare, es que alguien lo añadió sin
    // pasar por la compuerta.
    mvc.perform(get("/v3/api-docs").with(user("doc")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].put").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].delete").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/roles'].patch").doesNotExist());
  }

  @Test
  @DisplayName("publica el contrato en docs/api/openapi.json, para que el frontend lo consuma")
  void publicaElContratoComoArchivoVersionado() throws Exception {
    // ADR-001. Hasta hoy el contrato no se publicaba en ninguna parte:
    // `/v3/api-docs` responde solo con `EXPOSE_API_DOCS` en verdadero y
    // `docs/api/` estaba vacío. El frontend no puede generar su cliente sin él,
    // y escribir sus tipos a mano infringe su propia constitución.
    //
    // Esta prueba NO falla si el contrato cambia: lo reescribe. Quien falla es
    // CI, al comprobar que lo comprometido coincide con lo generado. Así,
    // ejecutar la suite en local deja el archivo listo para commitear en lugar
    // de romper con un mensaje que no dice qué hacer.
    var cuerpo =
        mvc.perform(get("/v3/api-docs").with(user("doc")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    // Se reescribe con las claves ordenadas. Sin eso, el orden que produzca
    // springdoc puede variar entre ejecuciones y cada regeneración ensuciaría
    // el diff con cientos de líneas movidas, que es justo lo que impide revisar
    // un cambio de contrato.
    //
    // Se lee como estructura de mapas y no como árbol de nodos porque
    // ORDER_MAP_ENTRIES_BY_KEYS ordena mapas, no `ObjectNode`.
    var ordenado = json.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    Object contenido = ordenado.readValue(cuerpo, Object.class);

    Files.createDirectories(DESTINO.getParent());
    Files.writeString(
        DESTINO,
        ordenado.writerWithDefaultPrettyPrinter().writeValueAsString(contenido) + "\n",
        StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("publica también el YAML, que es lo que piden por defecto los generadores")
  void publicaElContratoTambienEnYaml() throws Exception {
    // El JSON basta para leerlo; el YAML es lo que la mayoría de los
    // generadores de cliente asume cuando se les da una URL. Publicar solo uno
    // obliga a cada consumidor a convertirlo, y una conversión hecha a mano en
    // el lado del cliente es una copia del contrato que envejece por su cuenta.
    //
    // Se pide a springdoc en lugar de convertir el JSON aquí: así el YAML lo
    // produce el mismo componente que sirve `/v3/api-docs.yaml` en ejecución, y
    // no una traducción propia que pudiera diferir de lo que ve quien consulta
    // la API en vivo.
    String cuerpo =
        mvc.perform(get("/v3/api-docs.yaml").with(user("doc")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    Files.createDirectories(DESTINO_YAML.getParent());
    Files.writeString(DESTINO_YAML, cuerpo, StandardCharsets.UTF_8);

    // Que no salga vacío es lo único que esta prueba puede afirmar por su
    // cuenta: la coincidencia con el JSON la garantiza el propio springdoc, y
    // que lo comprometido esté al día lo comprueba CI.
    org.assertj.core.api.Assertions.assertThat(cuerpo).contains("openapi:").contains("/api/v1/");
  }
}
