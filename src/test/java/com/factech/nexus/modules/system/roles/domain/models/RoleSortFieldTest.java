package com.factech.nexus.modules.system.roles.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Lista blanca de ordenamiento del listado de roles (`RF-SP-002` · `T-04`).
 *
 * <p>Lo que se comprueba aquí no es el orden resultante sino <b>que la cadena del cliente no llega
 * a la sentencia</b>: un nombre no reconocido produce un rechazo antes de construir consulta
 * alguna. Es una prueba unitaria a propósito — si hiciera falta una base de datos para verificarlo,
 * sería porque el valor ya habría llegado demasiado lejos.
 */
class RoleSortFieldTest {

  @Test
  @DisplayName("ausente equivale a code,asc y siempre desempata por identificador")
  void ordenPorOmision() {
    // El desempate no es cosmético: sin él, dos páginas consecutivas pueden
    // repetir un rol y omitir otro.
    assertThat(RoleSortField.resolver(null)).isEqualTo("r.code ASC, r.id ASC");
    assertThat(RoleSortField.resolver("   ")).isEqualTo("r.code ASC, r.id ASC");
  }

  @Test
  @DisplayName("los seis campos de la lista blanca resuelven, en los dos sentidos")
  void listaBlanca() {
    assertThat(RoleSortField.resolver("name,desc")).isEqualTo("r.name DESC, r.id ASC");
    assertThat(RoleSortField.resolver("roleType,asc")).isEqualTo("r.role_type ASC, r.id ASC");
    assertThat(RoleSortField.resolver("status,desc")).isEqualTo("r.status DESC, r.id ASC");
    assertThat(RoleSortField.resolver("createdAt,asc")).isEqualTo("r.created_at ASC, r.id ASC");
    assertThat(RoleSortField.resolver("updatedAt,desc")).isEqualTo("r.updated_at DESC, r.id ASC");
    // Sin sentido explícito, ascendente.
    assertThat(RoleSortField.resolver("code")).isEqualTo("r.code ASC, r.id ASC");
    // El nombre público no distingue caja; la columna resultante sí es fija.
    assertThat(RoleSortField.resolver("NAME,ASC")).isEqualTo("r.name ASC, r.id ASC");
  }

  @ParameterizedTest(name = "«{0}» no ordena")
  @DisplayName("todo lo que no está en la lista se rechaza con VAL-003")
  @ValueSource(
      strings = {
        "deleted_at", // columna real, deliberadamente fuera: agrupa a los eliminados
        "description", // texto libre sin índice: ordenar por él no responde nada
        "parentRoleId", // UUID opaco
        "permissions.code", // no es del rol
        "(select 1)", // el caso que importa: no debe llegar al ORDER BY
        "code; DROP TABLE roles"
      })
  void fueraDeLaLista(String campo) {
    assertThatThrownBy(() -> RoleSortField.resolver(campo + ",asc"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("No se puede ordenar por");
  }

  @Test
  @DisplayName("un sentido que no sea asc o desc también se rechaza")
  void sentidoInvalido() {
    // Sin esta comprobación, el sentido desconocido se trataría como ascendente
    // y quien escribió `ascendente` recibiría un orden que no pidió.
    assertThatThrownBy(() -> RoleSortField.resolver("code,ascendente"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  @DisplayName("el rechazo enumera los campos admitidos")
  void elRechazoEnumeraLoAdmitido() {
    // Un filtro rechazado sin decir qué se admite obliga a buscar la lista en la
    // documentación — o a adivinarla probando.
    assertThatThrownBy(() -> RoleSortField.resolver("inventado,asc"))
        .hasMessageContaining("code")
        .hasMessageContaining("roleType")
        .hasMessageContaining("updatedAt");
  }
}
