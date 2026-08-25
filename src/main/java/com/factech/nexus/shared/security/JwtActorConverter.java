package com.factech.nexus.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Convierte el token de acceso en el actor autenticado.
 *
 * <p><b>Las autoridades salen de la BASE, no del token</b>, y esa es la decisión que más
 * consecuencias tiene de este archivo. El token lleva los <b>códigos de rol</b>; los permisos se
 * resuelven aquí recorriendo los roles vigentes de la persona.
 *
 * <p>El motivo es `security.md` §4.5: <b>conceder acceso puede esperar, retirarlo no</b>. Con los
 * permisos dentro del token, quitarle un rol a alguien no surte efecto hasta que su token expire
 * —hasta quince minutos de privilegios que ya no le corresponden—. Resolviéndolos aquí, el corte es
 * inmediato.
 *
 * <p><b>Lo que cuesta, y queda declarado:</b> una consulta por petición. `security.md` §4.5 admite
 * una caché para esta resolución precisamente por eso, y **no está implementada**: es una
 * optimización con su propia decisión de invalidación, y hacerla mal reintroduce el problema que
 * este diseño evita. Queda anotado en `tasks.md` de `RF-SP-034`.
 *
 * <p><b>El nombre del actor es el identificador de la persona</b>, no su nombre de usuario: es lo
 * que {@code CurrentActor} necesita para resolver el {@code actor_id} de la auditoría, y lo que
 * hace que cada evento apunte a una fila real de {@code users}.
 */
@Component
public class JwtActorConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final EffectivePermissions permisos;

  public JwtActorConverter(EffectivePermissions permisos) {
    this.permisos = permisos;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt token) {
    UUID usuario = UUID.fromString(token.getSubject());

    // Un conjunto vacío es una respuesta legítima —una persona sin roles— y
    // también lo que recibe quien fue eliminado: en los dos casos, el token
    // sigue siendo válido pero no abre ninguna puerta.
    Set<String> codigos = permisos.forUser(usuario).orElseGet(Set::of);

    Collection<GrantedAuthority> autoridades =
        codigos.stream()
            .map(SimpleGrantedAuthority::new)
            .map(GrantedAuthority.class::cast)
            .toList();

    return new JwtAuthenticationToken(token, List.copyOf(autoridades), usuario.toString());
  }
}
