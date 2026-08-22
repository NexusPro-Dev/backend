package com.factech.nexus.modules.system.permissions.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Normalización de los criterios de consulta (`RF-SP-010` · `T-05`).
 *
 * <p>Prueba unitaria pura: sin Spring y sin base de datos. Lo que se verifica es la regla de
 * `plan.md` §4 —un término en blanco equivale a ausente— antes de que llegue a construirse ningún
 * predicado.
 */
class ListPermissionsQueryTest {

  @Test
  @DisplayName("un término de búsqueda nulo equivale a ausente")
  void terminoNuloEsAusente() {
    assertThat(ListPermissionsQuery.of(null, null, null).searchTerm()).isEmpty();
  }

  @Test
  @DisplayName("un término vacío o solo con espacios equivale a ausente")
  void terminoEnBlancoEsAusente() {
    assertThat(ListPermissionsQuery.of(null, null, "").searchTerm()).isEmpty();
    assertThat(ListPermissionsQuery.of(null, null, "   ").searchTerm()).isEmpty();
    assertThat(ListPermissionsQuery.of(null, null, "\t\n ").searchTerm()).isEmpty();
  }

  @Test
  @DisplayName("un término con contenido se conserva recortado")
  void terminoConContenidoSeRecorta() {
    assertThat(ListPermissionsQuery.of(null, null, "  auditoria  ").searchTerm())
        .contains("auditoria");
  }

  @Test
  @DisplayName("el recorte no altera el interior del término")
  void elRecorteNoTocaElInterior() {
    // Buscar dos palabras es legítimo: el predicado es de contención.
    assertThat(ListPermissionsQuery.of(null, null, " read changes ").searchTerm())
        .contains("read changes");
  }

  @Test
  @DisplayName("recurso y acción siguen la misma regla que la búsqueda")
  void recursoYAccionSeNormalizanIgual() {
    ListPermissionsQuery enBlanco = ListPermissionsQuery.of("  ", "", null);

    assertThat(enBlanco.resource()).isEmpty();
    assertThat(enBlanco.action()).isEmpty();

    ListPermissionsQuery conValor = ListPermissionsQuery.of(" roles ", " read ", null);

    assertThat(conValor.resource()).contains("roles");
    assertThat(conValor.action()).contains("read");
  }

  @Test
  @DisplayName("la consulta sin filtros no lleva ninguno de los tres")
  void consultaSinFiltros() {
    ListPermissionsQuery todos = ListPermissionsQuery.all();

    assertThat(todos.resource()).isEmpty();
    assertThat(todos.action()).isEmpty();
    assertThat(todos.searchTerm()).isEmpty();
  }

  @Test
  @DisplayName("los tres criterios son independientes entre sí")
  void criteriosIndependientes() {
    ListPermissionsQuery soloRecurso = ListPermissionsQuery.of("audit", "  ", null);

    assertThat(soloRecurso.resource()).contains("audit");
    assertThat(soloRecurso.action()).isEmpty();
    assertThat(soloRecurso.searchTerm()).isEmpty();
  }

  @Test
  @DisplayName("ningún criterio se devuelve como null")
  void ningunCriterioEsNulo() {
    ListPermissionsQuery query = ListPermissionsQuery.of(null, null, null);

    assertThat(query.resource()).isNotNull().isInstanceOf(Optional.class);
    assertThat(query.action()).isNotNull();
    assertThat(query.searchTerm()).isNotNull();
  }
}
