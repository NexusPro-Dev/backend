package com.factech.nexus.modules.system.auth.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.auth.application.PasswordRecoveryConfirmation;
import com.factech.nexus.modules.system.auth.application.PasswordRecoveryRequest;
import com.factech.nexus.modules.system.auth.domain.models.OpaqueToken;
import com.factech.nexus.modules.system.auth.domain.service.ConfirmPasswordRecoveryService;
import com.factech.nexus.modules.system.auth.domain.service.RequestPasswordRecoveryService;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Los dos casos límite concurrentes de `RF-SP-040`, que son los que el plan §11 llama los más
 * importantes.
 *
 * <p><b>Por qué son los más importantes.</b> Los dos protegen la misma cosa —que no haya más de una
 * vía de entrada abierta a la vez— y los dos fallan de la manera que no se nota: el sistema
 * responde correctamente a las dos peticiones y deja detrás un estado que ninguna regla contempla.
 * Sin estas pruebas, la garantía existe en el código y nadie sabe si se cumple.
 *
 * <p>Se ejercitan sobre los casos de uso y no por HTTP: cada tarea necesita su propia transacción
 * real, y es la transacción —el índice único y el bloqueo de fila— lo que aquí se verifica.
 */
class PasswordRecoveryConcurrencyIT extends IntegrationTestBase {

  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String NUEVA = "OtraClaveLargaDistinta2026";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordHasher hasher;
  @Autowired private RequestPasswordRecoveryService solicitud;
  @Autowired private ConfirmPasswordRecoveryService confirmacion;

  private UUID persona;

  @BeforeEach
  void preparar() {
    limpiar();
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, 'JPerez', 'juan@factech.co', 'Juan', 'Pérez', ?, false, 'ACTIVO')
        """,
        persona,
        hasher.hash(CLAVE));
  }

  @AfterEach
  void limpiarDespues() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM password_reset_permits");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM audit_security_log");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
  }

  @Test
  @DisplayName("dos solicitudes a la vez dejan UN permiso vivo, no dos puertas abiertas")
  void dosSolicitudesConcurrentes() {
    List<Outcome<String>> resultados =
        runTogether(2, indice -> solicitud.solicitar(new PasswordRecoveryRequest("JPerez")));

    // Que una de las dos choque es un resultado legítimo: el índice único
    // parcial es lo que impide el estado imposible, y chocar es cómo lo impide.
    // Lo que NO puede ocurrir es que las dos pasen y queden dos permisos vivos.
    long vivos =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM password_reset_permits
             WHERE consumed_at IS NULL AND superseded_at IS NULL
            """,
            Long.class);

    assertThat(vivos)
        .as("quedaron %d permisos vivos: son %d vías de entrada abiertas a la vez", vivos, vivos)
        .isEqualTo(1);
    assertThat(resultados).hasSize(2);
  }

  @Test
  @DisplayName("dos confirmaciones con el MISMO permiso: la primera lo consume, la segunda cae")
  void dosConfirmacionesConcurrentes() {
    String permiso = emitirPermisoAMano();

    List<Outcome<Void>> resultados =
        runTogether(
            2,
            indice -> {
              confirmacion.confirmar(new PasswordRecoveryConfirmation(permiso, NUEVA));
              return null;
            });

    // Exactamente una. Sin el `SELECT ... FOR UPDATE` las dos leen el permiso
    // vigente y las dos sustituyen la credencial: la segunda pisa a la primera,
    // y quien eligió la primera contraseña no sabe que quedó descartada.
    long exitosas = resultados.stream().filter(r -> r.failure() == null).count();
    assertThat(exitosas).as("el permiso sirvió %d veces y debía servir una", exitosas).isEqualTo(1);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_permits WHERE consumed_at IS NOT NULL",
                Long.class))
        .isEqualTo(1);
  }

  /**
   * Un permiso sembrado directamente, sin pasar por la solicitud.
   *
   * <p>La solicitud entrega el valor en claro por el canal de envío, y aquí lo que se prueba es el
   * consumo: hacerlo pasar por el correo añadiría un doble y una espera a una prueba que ya tiene
   * bastante con coordinar dos transacciones.
   */
  private String emitirPermisoAMano() {
    String enClaro = OpaqueToken.generar();
    jdbc.update(
        """
        INSERT INTO password_reset_permits (id, user_id, permit_hash, expires_at, created_at)
        VALUES (?, ?, ?, now() + interval '30 minutes', now())
        """,
        UUID.randomUUID(),
        persona,
        OpaqueToken.resumen(enClaro));
    return enClaro;
  }
}
