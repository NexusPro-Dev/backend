package com.factech.nexus.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.factech.nexus.shared.audit.AuditEvents.ErrorEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionSystemException;

/**
 * Traducciones del manejador global que <b>no se pueden alcanzar por la API</b>.
 *
 * <p>No es un capricho de nivel: son rutas reales cuyo disparo depende de un estado que el propio
 * diseño impide producir desde fuera. La más clara es `EX-003`: la violación de {@code
 * uq_memberships_parent} salta en el {@code COMMIT}, y el bloqueo de la cadena hace que ninguna
 * petición pueda llegar hasta ahí mientras funcione. Comprobar la traducción aquí es la única forma
 * de que deje de ser código escrito y nunca ejecutado.
 */
class GlobalExceptionHandlerTest {

  private final AuditWriter auditoria = mock(AuditWriter.class);
  private final GlobalExceptionHandler manejador = new GlobalExceptionHandler(auditoria);
  private final MockHttpServletRequest peticion = peticion("POST", "/api/v1/memberships");

  @Test
  @DisplayName("una violación de restricción diferida conocida se traduce a 409 con EX-003")
  void empateConcurrente() {
    ProblemDetail detalle =
        manejador.deConfirmacionFallida(alConfirmar("uq_memberships_parent"), peticion);

    assertThat(detalle.getStatus()).isEqualTo(409);
    assertThat(detalle.getType().toString()).endsWith("/errors/regla-de-negocio");
    // El mensaje pide REINTENTAR, no dice que el dato sea inválido: la misma
    // petición, repetida, es correcta.
    assertThat(detalle.getDetail()).contains("cadena cambió").contains("intentarlo");
  }

  @Test
  @DisplayName("el empate se audita como regla de negocio con severidad MEDIA, no como fallo")
  void elEmpateSeAudita() {
    manejador.deConfirmacionFallida(alConfirmar("uq_memberships_level"), peticion);

    ArgumentCaptor<ErrorEvent> evento = ArgumentCaptor.forClass(ErrorEvent.class);
    verify(auditoria).recordError(evento.capture());

    assertThat(evento.getValue().errorCode()).isEqualTo("EX-003");
    assertThat(evento.getValue().httpStatus()).isEqualTo(409);
    assertThat(evento.getValue().errorType())
        .isEqualTo(com.factech.nexus.shared.audit.AuditEnums.ErrorType.BUSINESS_RULE);
    assertThat(evento.getValue().severity())
        .isEqualTo(com.factech.nexus.shared.audit.AuditEnums.Severity.MEDIA);
  }

  @Test
  @DisplayName("una restricción diferida DESCONOCIDA no se disfraza de empate: sale como 500")
  void restriccionNoReconocida() {
    // Es la mitad que importa de la lista blanca. Darle a toda confirmación
    // fallida la respuesta de un empate escondería defectos reales detrás de un
    // mensaje que invita a reintentar algo que nunca va a funcionar.
    ProblemDetail detalle =
        manejador.deConfirmacionFallida(alConfirmar("uq_de_otra_cosa"), peticion);

    assertThat(detalle.getStatus()).isEqualTo(500);
    assertThat(detalle.getType().toString()).endsWith("/errors/interno");
  }

  @Test
  @DisplayName("un fallo al confirmar sin violación de restricción tampoco es un empate")
  void confirmacionFallidaSinRestriccion() {
    ProblemDetail detalle =
        manejador.deConfirmacionFallida(
            new TransactionSystemException("no se pudo confirmar"), peticion);

    assertThat(detalle.getStatus()).isEqualTo(500);
  }

  @Test
  @DisplayName("un argumento no convertible es 400 con VAL-001, y NO se audita")
  void argumentoNoConvertible() throws Exception {
    var fallo =
        new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
            "abc", java.util.UUID.class, "id", null, new IllegalArgumentException("no es un UUID"));

    ProblemDetail detalle = manejador.deTipoIncorrecto(fallo, peticion("GET", "/api/v1/x/abc"));

    assertThat(detalle.getStatus()).isEqualTo(400);
    // Ruido de formulario: `ck_audit_error_log_status` rechazaría la fila.
    verify(auditoria, never()).recordError(any());
  }

  @Test
  @DisplayName("un verbo no admitido es 405 y una ruta inexistente es 404, y ninguno se audita")
  void metodoYRutaInexistente() {
    ProblemDetail sinMetodo =
        manejador.deMetodoNoPermitido(
            new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE"),
            peticion("DELETE", "/api/v1/currencies"));
    assertThat(sinMetodo.getStatus()).isEqualTo(405);
    assertThat(sinMetodo.getDetail()).contains("DELETE");

    ProblemDetail sinRuta =
        manejador.deRutaInexistente(
            new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.PATCH, "/api/v1/countries/x"),
            peticion("PATCH", "/api/v1/countries/x"));
    assertThat(sinRuta.getStatus()).isEqualTo(404);

    verify(auditoria, never()).recordError(any());
  }

  // ---------------------------------------------------------------------------

  /**
   * Reproduce lo que Spring lanza cuando una restricción <b>diferida</b> salta al confirmar: el
   * interceptor transaccional envuelve la excepción de Hibernate, que a su vez lleva el nombre de
   * la restricción — que es por lo único que el manejador puede distinguirla.
   */
  private static TransactionSystemException alConfirmar(String restriccion) {
    var violacion =
        new org.hibernate.exception.ConstraintViolationException(
            "duplicate key value violates unique constraint",
            new SQLException("23505", "23505"),
            restriccion);
    return new TransactionSystemException("No se pudo confirmar la transacción", violacion);
  }

  private static MockHttpServletRequest peticion(String metodo, String ruta) {
    MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
    peticion.setRequestURI(ruta);
    return peticion;
  }
}
