package com.factech.nexus.modules.system.permissions.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.permissions.application.ListPermissionsQuery;
import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificación del adaptador de consulta (`RF-SP-010` · `T-06`).
 *
 * <p>Las pruebas de búsqueda son las que importan: las tres fallan con una implementación que
 * normalice en Java, que no escape los comodines o que olvide el {@code coalesce}.
 */
// Transacción de escritura y no de solo lectura: `permisoSinDescripcionSeEncuentra`
// necesita insertar una fila. Spring la revierte al terminar cada prueba, de modo
// que el catálogo sembrado queda intacto para las demás.
@Transactional
class JpaPermissionQueryRepositoryIT extends IntegrationTestBase {

  @Autowired private PermissionQueryRepository repository;
  @Autowired private JdbcTemplate jdbc;

  private List<String> codesOf(ListPermissionsQuery query) {
    return repository.find(query).stream().map(PermissionItem::code).toList();
  }

  @Test
  @DisplayName("sin filtros devuelve el catálogo completo")
  void sinFiltrosDevuelveTodo() {
    assertThat(repository.find(ListPermissionsQuery.all())).hasSize(37);
  }

  @Test
  @DisplayName("el orden es por recurso y acción, y es estable entre llamadas")
  void ordenEstable() {
    List<PermissionItem> primera = repository.find(ListPermissionsQuery.all());
    List<PermissionItem> segunda = repository.find(ListPermissionsQuery.all());

    assertThat(primera).isEqualTo(segunda);
    assertThat(primera)
        .isSortedAccordingTo(
            Comparator.comparing(PermissionItem::resource).thenComparing(PermissionItem::action));
  }

  @Test
  @DisplayName("el filtro por recurso es de igualdad, no de contención")
  void filtroPorRecursoEsIgualdad() {
    assertThat(codesOf(ListPermissionsQuery.of("roles", null, null)))
        .containsExactly("roles:create", "roles:delete", "roles:read", "roles:update");

    // Si el filtro fuera por contención, «role» arrastraría los de «roles» y
    // el cliente no tendría forma de pedir solo uno de los dos.
    assertThat(codesOf(ListPermissionsQuery.of("role", null, null))).isEmpty();
  }

  @Test
  @DisplayName("un recurso inexistente devuelve colección vacía, no error")
  void recursoInexistenteDevuelveVacio() {
    assertThat(repository.find(ListPermissionsQuery.of("inexistente", null, null))).isEmpty();
  }

  @Test
  @DisplayName("los filtros se combinan entre sí")
  void filtrosCombinados() {
    assertThat(codesOf(ListPermissionsQuery.of("users", "assign-supervisor", null)))
        .containsExactly("users:assign-supervisor");
  }

  @Test
  @DisplayName("la búsqueda ignora acentos y mayúsculas")
  void busquedaSinAcentosNiMayusculas() {
    // «logicamente» sin tilde debe encontrar las dos descripciones que dicen
    // «Eliminar lógicamente…». Falla si la normalización se hace en Java en
    // lugar de con f_unaccent en la base de datos.
    assertThat(codesOf(ListPermissionsQuery.of(null, null, "logicamente")))
        .containsExactly("roles:delete", "users:delete");

    // Da igual la caja del término y la de la fila, y el orden se mantiene.
    assertThat(codesOf(ListPermissionsQuery.of(null, null, "LÓGICAMENTE")))
        .containsExactly("roles:delete", "users:delete");
  }

  @Test
  @DisplayName("la búsqueda cubre el código y la descripción, no el nombre")
  void laBusquedaNoAlcanzaElNombre() {
    // `spec.md` §6.1 acota la búsqueda a «código y descripción». «auditoría»
    // aparece solo en el `name` de los cuatro permisos de auditoría, y por eso
    // no los encuentra. Queda aquí escrito para que quien amplíe la búsqueda
    // al nombre sepa que rompe esta prueba a propósito y no por accidente.
    assertThat(repository.find(ListPermissionsQuery.of(null, null, "auditoria"))).isEmpty();

    // Buscando por el código sí aparecen los cuatro.
    assertThat(codesOf(ListPermissionsQuery.of(null, null, "audit:"))).hasSize(4);
  }

  @Test
  @DisplayName("un comodín en el término no convierte la búsqueda en «devuélvemelo todo»")
  void elComodinSeEscapa() {
    assertThat(repository.find(ListPermissionsQuery.of(null, null, "%"))).isEmpty();
    assertThat(repository.find(ListPermissionsQuery.of(null, null, "_"))).isEmpty();
    assertThat(repository.find(ListPermissionsQuery.of(null, null, "\\"))).isEmpty();
  }

  @Test
  @DisplayName("un permiso sin descripción sigue apareciendo al buscar por su código")
  void permisoSinDescripcionSeEncuentra() {
    // El catálogo sembrado no tiene ninguno sin descripción; se añade uno para
    // esta prueba. Sin el coalesce del adaptador, NULL LIKE … es NULL y la
    // rama del OR nunca sería verdadera: el permiso no aparecería jamás.
    jdbc.update(
        """
        INSERT INTO permissions (id, code, resource, action, name, description)
        VALUES (gen_random_uuid(), 'testcoalesce:read', 'testcoalesce', 'read', 'Sin descripción', NULL)
        """);

    assertThat(codesOf(ListPermissionsQuery.of(null, null, "testcoalesce")))
        .containsExactly("testcoalesce:read");
  }

  @Test
  @DisplayName("la búsqueda alcanza tanto el código como la descripción")
  void busquedaSobreCodigoYDescripcion() {
    assertThat(codesOf(ListPermissionsQuery.of(null, null, "assign-supervisor")))
        .contains("users:assign-supervisor");

    assertThat(codesOf(ListPermissionsQuery.of(null, null, "estructura comercial")))
        .contains("users:assign-supervisor");
  }

  @Test
  @DisplayName("la proyección trae los seis campos y la descripción nula llega como nula")
  void proyeccionCompleta() {
    PermissionItem item =
        repository.find(ListPermissionsQuery.of("permissions", "read", null)).getFirst();

    assertThat(item.id()).isNotNull();
    assertThat(item.code()).isEqualTo("permissions:read");
    assertThat(item.resource()).isEqualTo("permissions");
    assertThat(item.action()).isEqualTo("read");
    assertThat(item.name()).isEqualTo("Consultar permisos");
    assertThat(item.description()).isNotBlank();
  }
}
