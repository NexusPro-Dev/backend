package com.factech.nexus.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.factech.nexus.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de {@code V1__create_shared_functions.sql} (`RF-SP-010` · `T-01`).
 *
 * <p>Lo que aquí se comprueba no es que la función exista, sino las dos propiedades que la hacen
 * útil: que normaliza los acentos y que <b>es indexable</b>. La segunda es la razón de ser del
 * envoltorio; sin ella, {@code unaccent} bastaría.
 */
class SharedFunctionsIT extends IntegrationTestBase {

  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void limpiarLaTablaDePrueba() {
    jdbc.execute("DROP TABLE IF EXISTS prueba_indice_f_unaccent");
  }

  @Test
  @DisplayName("la migración V1 figura aplicada en el historial de Flyway")
  void laMigracionFiguraAplicada() {
    Boolean aplicada =
        jdbc.queryForObject(
            """
            SELECT success
              FROM flyway_schema_history
             WHERE version = '1'
            """,
            Boolean.class);

    assertThat(aplicada).isTrue();
  }

  @Test
  @DisplayName("f_unaccent normaliza los acentos y respeta la caja")
  void normalizaLosAcentos() {
    assertThat(jdbc.queryForObject("SELECT f_unaccent('AUDITORÍA')", String.class))
        .isEqualTo("AUDITORIA");

    // La función quita tildes; no cambia mayúsculas por minúsculas. Quien
    // necesite ambas cosas compone con lower(), que es lo que hacen los
    // índices de búsqueda de RF-SP-002 y RF-SP-010.
    assertThat(jdbc.queryForObject("SELECT f_unaccent(lower('Administración'))", String.class))
        .isEqualTo("administracion");
  }

  @Test
  @DisplayName("f_unaccent no depende del search_path de quien la llama")
  void noDependeDelSearchPath() {
    // El diccionario va cualificado en la definición. Sin eso, esta llamada
    // fallaría con 42883 al no encontrar 'unaccent' fuera del search_path.
    jdbc.execute("SET search_path TO pg_catalog");
    try {
      assertThat(jdbc.queryForObject("SELECT public.f_unaccent('Panamá')", String.class))
          .isEqualTo("Panama");
    } finally {
      jdbc.execute("SET search_path TO public");
    }
  }

  @Test
  @DisplayName("un índice de expresión que invoca f_unaccent se crea sin error")
  void esIndexable() {
    jdbc.execute("CREATE TABLE prueba_indice_f_unaccent (name text)");

    // Esta es la comprobación que justifica el envoltorio: PostgreSQL rechaza
    // todo índice de expresión sobre una función no inmutable, y unaccent() de
    // un solo argumento es STABLE. Si alguien retirase el IMMUTABLE de la
    // migración, esta línea fallaría con 42P17.
    assertThatCode(
            () ->
                jdbc.execute(
                    """
                    CREATE INDEX ix_prueba_f_unaccent
                        ON prueba_indice_f_unaccent (f_unaccent(lower(name)))
                    """))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("la extensión pg_trgm queda disponible para los índices de búsqueda")
  void pgTrgmDisponible() {
    Integer instalada =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);

    assertThat(instalada).isEqualTo(1);
  }
}
