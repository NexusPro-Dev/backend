package com.factech.nexus.shared.observability;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolución de la dirección de origen de una petición (`architecture.md` §6.6.1, Art. V.15).
 *
 * <p><b>El problema.</b> Detrás de un proxy inverso, la IP del socket es la del proxy; la real
 * llega en {@code X-Forwarded-For}, que es una cabecera <b>provista por el cliente</b> y por tanto
 * falsificable. Sin ninguna comprobación, cualquiera puede escribir en su propia auditoría la IP
 * que quiera, y el campo deja de ser evidencia — que es justo para lo que existe.
 *
 * <p><b>La resolución.</b> Se declara en configuración la lista de proxies confiables y se descarta
 * todo salto no confiable de la cadena:
 *
 * <ol>
 *   <li>Si el par inmediato —la IP del socket— <b>no</b> es un proxy confiable, se ignora {@code
 *       X-Forwarded-For} por completo y se usa la IP del socket. Quien nos habla no es un proxy
 *       nuestro, de modo que nada de lo que afirme sobre saltos anteriores es verificable.
 *   <li>Si lo es, se recorre la cadena <b>de derecha a izquierda</b> saltando proxies confiables.
 *       El primer valor no confiable es el cliente: todo lo que haya a su izquierda lo escribió él
 *       y puede ser inventado.
 *   <li>Si la cadena entera es de proxies confiables, se devuelve el salto más a la izquierda.
 * </ol>
 *
 * <p><b>Sin lista configurada no se confía en nadie</b>, que es lo seguro: en un despliegue sin
 * proxy inverso la IP del socket ya es la real, y en uno con proxy mal configurado la auditoría
 * registra la del proxy —un dato incompleto pero cierto— en lugar de uno que el atacante elige.
 */
@Component
public class ClientIpResolver {

  private static final String CABECERA = "X-Forwarded-For";

  private final Set<String> proxiesConfiables;

  public ClientIpResolver(@Value("${nexus.security.trusted-proxies:}") String configurados) {
    this.proxiesConfiables =
        Arrays.stream(configurados.split(","))
            .map(String::trim)
            .filter(valor -> !valor.isEmpty())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Dirección de origen de la petición.
   *
   * @param peticion petición en curso
   * @return la IP resuelta, o {@code null} si el contenedor no la publica
   */
  public String resolve(HttpServletRequest peticion) {
    String delSocket = peticion.getRemoteAddr();
    if (delSocket == null || !proxiesConfiables.contains(delSocket)) {
      return delSocket;
    }

    String cadena = peticion.getHeader(CABECERA);
    if (cadena == null || cadena.isBlank()) {
      return delSocket;
    }

    List<String> saltos =
        Arrays.stream(cadena.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

    for (int i = saltos.size() - 1; i >= 0; i--) {
      if (!proxiesConfiables.contains(saltos.get(i))) {
        return saltos.get(i);
      }
    }
    return saltos.isEmpty() ? delSocket : saltos.get(0);
  }
}
