package com.factech.nexus.modules.system.permissions.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificación del mapeo de {@code permissions} (`RF-SP-010` · `T-04`).
 *
 * <p>La prueba más valiosa de esta clase no está escrita: es que el contexto de Spring
 * **arranque**. Con {@code spring.jpa.hibernate.ddl-auto: validate}, Hibernate compara el mapeo con
 * el esquema real al iniciar y falla si discrepan. Que estas pruebas lleguen a ejecutarse ya
 * demuestra que {@code Permission} y {@code V2__create_permissions.sql} coinciden.
 */
@Transactional(readOnly = true)
class PermissionIT extends IntegrationTestBase {

  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("el mapeo lee una fila sembrada con sus seis columnas de negocio")
  void leeUnaFilaSembrada() {
    Permission permiso =
        entityManager.find(
            Permission.class, UUID.fromString("01a029fc-5d80-7002-9c4f-5e7ad0000002"));

    assertThat(permiso).isNotNull();
    assertThat(permiso.getCode()).isEqualTo("roles:create");
    assertThat(permiso.getResource()).isEqualTo("roles");
    assertThat(permiso.getAction()).isEqualTo("create");
    assertThat(permiso.getName()).isEqualTo("Registrar roles");
    assertThat(permiso.getDescription()).isNotBlank();
  }

  @Test
  @DisplayName("las marcas temporales llegan pobladas por el valor por omisión de la tabla")
  void marcasTemporalesPobladas() {
    Permission permiso =
        entityManager.find(
            Permission.class, UUID.fromString("01a029fc-5d80-7018-9c4f-5e7ad0000018"));

    assertThat(permiso.getCreatedAt()).isNotNull();
    assertThat(permiso.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("el mapeo alcanza los treinta y siete permisos del catálogo")
  void alcanzaElCatalogoCompleto() {
    Long total =
        entityManager
            .createQuery("SELECT count(p) FROM Permission p", Long.class)
            .getSingleResult();

    assertThat(total).isEqualTo(37L);
  }

  @Test
  @DisplayName("la entidad no se usa como agregado: no expone constructor público")
  void noEsUnAgregado() {
    // El catálogo es inmutable por API (RN-SP-004). Si alguien añadiera un
    // constructor público con argumentos, esta prueba lo señalaría antes de
    // que apareciera el primer caso de uso que escribe aquí.
    assertThat(Permission.class.getConstructors())
        .as("Permission no debe poder construirse desde fuera de JPA")
        .isEmpty();
  }
}
