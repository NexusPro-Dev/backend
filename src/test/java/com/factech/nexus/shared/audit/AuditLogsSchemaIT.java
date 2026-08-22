package com.factech.nexus.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de {@code V4__create_audit_logs.sql} (`RF-SP-001` · `T-01`).
 *
 * <p>Cada prueba ejercita una restricción con un {@code INSERT} que debe fallar. Una restricción
 * que nadie intenta violar es una restricción que nadie sabe si funciona — y en estas tablas eso
 * importa más que en ninguna otra, porque son la evidencia.
 */
class AuditLogsSchemaIT extends IntegrationTestBase {

  @Autowired private JdbcTemplate jdbc;

  // ---------------------------------------------------------------------------
  // audit_error_log
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("ck_audit_error_log_status rechaza 400, 401, 403 y 404")
  void estadosProhibidosEnElRegistroDeErrores() {
    // La frontera que más importa es el 403: una denegación no es un fallo del
    // sistema, es el sistema funcionando, y va a audit_security_log. Con esta
    // restricción, escribirla aquí por descuido deja de ser un dato incorrecto
    // que nadie nota y pasa a ser un INSERT que falla.
    for (int estado : List.of(400, 401, 403, 404)) {
      assertThatThrownBy(() -> insertarError(estado))
          .as("http_status %d debe rechazarse", estado)
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  @DisplayName("los estados legítimos no se enumeran: 409, 422, 500 y 503 se admiten")
  void estadosAdmitidos() {
    // Enumerarlos obligaría a alterar la restricción cada vez que un
    // requerimiento estrenara un estado legítimo.
    for (int estado : List.of(409, 422, 500, 503)) {
      assertThatCode(() -> insertarError(estado))
          .as("http_status %d debe admitirse", estado)
          .doesNotThrowAnyException();
    }
  }

  @Test
  @DisplayName("error_type y severity son dominios cerrados")
  void dominiosDelRegistroDeErrores() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO audit_error_log (id, occurred_at, resource, operation,
                                                 error_code, error_type, http_status, severity, message)
                    VALUES (gen_random_uuid(), now(), 'roles', 'POST /api/v1/roles',
                            'RN-SEG-003', 'INVENTADO', 409, 'ALTA', 'x')
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);

    // audit_error_log no admite INFORMATIVA: un error nunca es informativo.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO audit_error_log (id, occurred_at, resource, operation,
                                                 error_code, error_type, http_status, severity, message)
                    VALUES (gen_random_uuid(), now(), 'roles', 'POST /api/v1/roles',
                            'RN-SEG-003', 'BUSINESS_RULE', 409, 'INFORMATIVA', 'x')
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------------------------------------------------------------------------
  // audit_deletion_log
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("ck_deletion_reason: un motivo de tres caracteres se acepta")
  void motivoCorto() {
    // La restricción exige CONTENIDO, no longitud: se decidió no elevar el
    // mínimo para no imponer fricción a quien sí redacta un motivo útil.
    assertThatCode(() -> insertarEliminacion("LOGICAL", "abc")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("ck_deletion_reason: en blanco, solo espacios o nulo se rechazan")
  void motivoVacio() {
    assertThatThrownBy(() -> insertarEliminacion("LOGICAL", ""))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertarEliminacion("LOGICAL", "   "))
        .isInstanceOf(DataIntegrityViolationException.class);
    // El caso que un OR con NULL dejaría pasar: FALSE OR NULL es NULL, y un
    // CHECK que evalúa a NULL acepta la fila.
    assertThatThrownBy(() -> insertarEliminacion("PHYSICAL", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("ck_deletion_reason: ASSOCIATION no exige motivo (Art. V.13)")
  void asociacionSinMotivo() {
    assertThatCode(() -> insertarEliminacion("ASSOCIATION", null)).doesNotThrowAnyException();
  }

  // ---------------------------------------------------------------------------
  // audit_security_log
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("event_type admite los diecinueve literales del catálogo y ninguno más")
  void catalogoCerradoDeEventos() {
    List<String> catalogo =
        List.of(
            "LOGIN_SUCCESS",
            "LOGIN_FAILURE",
            "ACCOUNT_LOCKED",
            "REFRESH_TOKEN_REUSE",
            "LOGOUT",
            "AUTHORIZATION_DENIED",
            "ROLE_CREATED",
            "ROLE_UPDATED",
            "ROLE_DELETED",
            "ROLE_PERMISSIONS_CHANGED",
            "USER_CREATED",
            "EMAIL_CHANGED",
            "USER_ROLES_ASSIGNED",
            "USER_ROLES_REVOKED",
            "USER_STATUS_CHANGED",
            "USER_DELETED",
            "PASSWORD_CHANGED",
            "PASSWORD_RESET",
            "SECURITY_AUDIT_READ");

    assertThat(catalogo).hasSize(19);
    catalogo.forEach(
        evento ->
            assertThatCode(() -> insertarSeguridad(evento, "ALTA", "SUCCESS"))
                .as("el catálogo debe admitir %s", evento)
                .doesNotThrowAnyException());

    // El caso que motivó enumerarlos: sin literales, cada requerimiento
    // inventaría su propia forma de escribirlo y el filtro de RF-SP-014
    // devolvería resultados incompletos sin que nada fallara.
    assertThatThrownBy(() -> insertarSeguridad("LOGIN_FAILED", "MEDIA", "FAILURE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("severity admite INFORMATIVA aquí, y outcome solo SUCCESS o FAILURE")
  void dominiosDelRegistroDeSeguridad() {
    assertThatCode(() -> insertarSeguridad("LOGIN_SUCCESS", "INFORMATIVA", "SUCCESS"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> insertarSeguridad("LOGIN_SUCCESS", "CRITICA", "SUCCESS"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertarSeguridad("LOGIN_SUCCESS", "ALTA", "PARCIAL"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------------------------------------------------------------------------
  // Núcleo común
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("las tres columnas de origen viajan juntas o las tres en nulo")
  void correspondenciaDeOrigen() {
    // Una fila sin IP significa inequívocamente «no vino de la red», y nunca
    // «se olvidó registrarla» (Art. V.15).
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO audit_change_log (id, occurred_at, correlation_id, ip_address,
                                                  module, entity, entity_id, action, changes)
                    VALUES (gen_random_uuid(), now(), gen_random_uuid(), NULL,
                            'SP', 'roles', gen_random_uuid(), 'CREATE', '{}'::jsonb)
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO audit_change_log (id, occurred_at, correlation_id, ip_address,
                                                  module, entity, entity_id, action, changes)
                    VALUES (gen_random_uuid(), now(), NULL, '10.0.0.1'::inet,
                            'SP', 'roles', gen_random_uuid(), 'CREATE', '{}'::jsonb)
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("action de audit_change_log es un dominio cerrado")
  void accionesDeCambio() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO audit_change_log (id, occurred_at, module, entity, entity_id, action, changes)
                    VALUES (gen_random_uuid(), now(), 'SP', 'roles', gen_random_uuid(), 'DELETE', '{}'::jsonb)
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("v_audit_timeline cruza los cuatro registros")
  void vistaTransversal() {
    // Las cuatro tablas están separadas para escribir; para leer hay dos
    // preguntas frecuentes que las cruzan.
    List<String> tipos =
        jdbc.queryForList("SELECT DISTINCT audit_type FROM v_audit_timeline", String.class);

    assertThat(tipos).isSubsetOf("CHANGE", "DELETION", "ERROR", "SECURITY");
    // V7 sembró siete filas de cambio, de modo que la vista nunca está vacía.
    assertThat(tipos).contains("CHANGE");
  }

  // ---------------------------------------------------------------------------

  private void insertarError(int estado) {
    jdbc.update(
        """
        INSERT INTO audit_error_log (id, occurred_at, resource, operation,
                                     error_code, error_type, http_status, severity, message)
        VALUES (gen_random_uuid(), now(), 'roles', 'POST /api/v1/roles',
                'RN-SEG-003', 'BUSINESS_RULE', ?, 'ALTA', 'Mensaje saneado.')
        """,
        estado);
  }

  private void insertarEliminacion(String tipo, String motivo) {
    jdbc.update(
        """
        INSERT INTO audit_deletion_log (id, occurred_at, module, entity, entity_id,
                                        deletion_type, reason, snapshot)
        VALUES (gen_random_uuid(), now(), 'SP', 'roles', gen_random_uuid(), ?, ?, '{}'::jsonb)
        """,
        tipo,
        motivo);
  }

  private void insertarSeguridad(String evento, String severidad, String resultado) {
    jdbc.update(
        """
        INSERT INTO audit_security_log (id, occurred_at, event_type, severity, outcome)
        VALUES (gen_random_uuid(), now(), ?, ?, ?)
        """,
        evento,
        severidad,
        resultado);
  }
}
