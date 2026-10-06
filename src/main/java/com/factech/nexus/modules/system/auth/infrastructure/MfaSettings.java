package com.factech.nexus.modules.system.auth.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Los ajustes del segundo factor (`security.md` §3.3), bajo {@code nexus.security.mfa}.
 *
 * <p>{@code encryptionKey} <b>no tiene valor por defecto</b> (Art. IX.5): lo comprueba {@link
 * MfaSecrets} al arrancar. El resto sí, porque son decisiones de diseño y no secretos.
 */
@ConfigurationProperties(prefix = "nexus.security.mfa")
public record MfaSettings(
    String encryptionKey,
    String issuer,
    Duration recentWindow,
    Duration pendingTtl,
    Duration challengeTtl) {

  public MfaSettings {
    issuer = issuer == null || issuer.isBlank() ? "NEXUS" : issuer;
    recentWindow = recentWindow == null ? Duration.ofMinutes(5) : recentWindow;
    pendingTtl = pendingTtl == null ? Duration.ofMinutes(10) : pendingTtl;
    challengeTtl = challengeTtl == null ? Duration.ofMinutes(5) : challengeTtl;
  }
}
