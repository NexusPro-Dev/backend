package com.factech.nexus.modules.system.auth.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Permiso temporal para recuperar la contraseña olvidada (`RF-SP-040`).
 *
 * <p><b>Del permiso solo se guarda su hash</b>, igual que del refresh token y por lo mismo: quien
 * lea esta tabla no puede tomar la cuenta de nadie. El valor no existe en el servidor más allá del
 * instante en que se entrega al canal de envío.
 *
 * <p><b>Deja de servir por dos motivos distintos y se guardan aparte.</b> {@code consumedAt} dice
 * que alguien <b>completó</b> el flujo; {@code supersededAt}, que <b>pidió otro</b>. Una sola
 * columna de estado los haría indistinguibles, y son justo las dos cosas que la auditoría necesita
 * separar: una es alguien recuperando su cuenta y la otra puede ser alguien probando.
 *
 * <p><b>La caducidad no la limpia ningún proceso</b>: se evalúa al consultar, comparando con el
 * momento del intento. Mismo criterio que `RF-SP-032` y `RF-SP-038`.
 */
@Entity
@Table(name = "password_reset_permits")
public class PasswordResetPermit {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "permit_hash", nullable = false, length = 255, updatable = false)
  private String permitHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "consumed_at")
  private OffsetDateTime consumedAt;

  @Column(name = "superseded_at")
  private OffsetDateTime supersededAt;

  /**
   * {@code inet} y no {@code varchar}, igual que en la auditoría: valida el formato, admite IPv4 e
   * IPv6 sin decidir longitudes, y permite investigar una ráfaga <b>por rango de red</b> — que
   * sobre texto obliga a recorrer la tabla entera.
   */
  @JdbcTypeCode(SqlTypes.INET)
  @Column(name = "requested_ip", updatable = false)
  private InetAddress requestedIp;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** Exigido por JPA. */
  protected PasswordResetPermit() {}

  /** Emite un permiso nuevo. El valor en claro lo conserva quien llama, y solo él. */
  public static PasswordResetPermit emitir(
      UUID id,
      UUID userId,
      String permitHash,
      OffsetDateTime expiresAt,
      InetAddress requestedIp,
      OffsetDateTime ahora) {

    PasswordResetPermit permiso = new PasswordResetPermit();
    permiso.id = id;
    permiso.userId = userId;
    permiso.permitHash = permitHash;
    permiso.expiresAt = expiresAt;
    permiso.requestedIp = requestedIp;
    permiso.createdAt = ahora;
    return permiso;
  }

  /**
   * ¿Sirve todavía?
   *
   * <p>Las tres razones por las que no sirve —caducado, consumido, sustituido— <b>no se distinguen
   * hacia fuera</b>: quien prueba permisos al azar no debe enterarse de cuál estuvo a punto de
   * acertar. La distinción vive aquí y en la auditoría, no en la respuesta.
   */
  public boolean vigente(OffsetDateTime ahora) {
    return consumedAt == null && supersededAt == null && expiresAt.isAfter(ahora);
  }

  public void consumir(OffsetDateTime ahora) {
    consumedAt = ahora;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getConsumedAt() {
    return consumedAt;
  }

  public OffsetDateTime getSupersededAt() {
    return supersededAt;
  }
}
