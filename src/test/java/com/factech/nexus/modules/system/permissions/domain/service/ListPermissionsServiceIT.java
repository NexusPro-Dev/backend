package com.factech.nexus.modules.system.permissions.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.permissions.application.ListPermissionsQuery;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificación del caso de uso (`RF-SP-010` · `T-07`).
 *
 * <p>Dos propiedades, y ninguna se comprueba mirando el resultado: que la consulta cuesta
 * <b>una</b> sentencia con y sin filtros, y que la transacción es de solo lectura de verdad.
 */
class ListPermissionsServiceIT extends IntegrationTestBase {

  @Autowired private ListPermissionsService service;
  @Autowired private SessionFactory sessionFactory;

  private Statistics statistics;

  @BeforeEach
  void resetStatistics() {
    statistics = sessionFactory.getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
  }

  @Test
  @DisplayName("la consulta sin filtros cuesta exactamente una sentencia")
  void unaSentenciaSinFiltros() {
    service.list(ListPermissionsQuery.all());

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("la consulta con los tres filtros también cuesta una sola sentencia")
  void unaSentenciaConFiltros() {
    service.list(ListPermissionsQuery.of("users", "read", "usuarios"));

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("ninguna sentencia toca role_permissions")
  void noTocaRolePermissions() {
    // plan.md §4 decide no devolver cuántos roles declaran cada permiso, y no
    // tener la subconsulta es lo único que lo hace verificable.
    service.list(ListPermissionsQuery.all());

    assertThat(statistics.getQueries())
        .allSatisfy(sql -> assertThat(sql).doesNotContainIgnoringCase("role_permissions"));
  }

  @Test
  @DisplayName("el caso de uso declara su transacción de solo lectura")
  void transaccionDeSoloLectura() throws NoSuchMethodException {
    // Comprobación estructural, y se dice lo que es: verifica la declaración,
    // no el comportamiento del driver. El primer borrador de esta prueba
    // intentaba escribir dentro de una transacción de solo lectura para verla
    // fallar, y lo que conseguía era dejar la conexión abortada y arrastrar a
    // las demás pruebas de la clase con un «current transaction is aborted».
    Transactional anotacion =
        ListPermissionsService.class
            .getMethod("list", ListPermissionsQuery.class)
            .getAnnotation(Transactional.class);

    assertThat(anotacion).isNotNull();
    assertThat(anotacion.readOnly()).isTrue();
  }

  @Test
  @DisplayName("el servicio devuelve el catálogo completo, no una página")
  void devuelveElCatalogoCompleto() {
    assertThat(service.list(ListPermissionsQuery.all())).hasSize(32);
  }
}
