package com.factech.nexus.shared.security.ratelimit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Una petición cuyo cuerpo puede leerse <b>más de una vez</b>.
 *
 * <p>Existe por una limitación del servlet y no por gusto: {@code getInputStream()} devuelve un
 * flujo de un solo uso, de modo que quien lo lea en un filtro <b>se lo quita al controlador</b>. El
 * síntoma sería un cuerpo vacío en el caso de uso y un {@code 400} por campos obligatorios que el
 * cliente sí había enviado — un fallo desconcertante y difícil de atribuir al filtro.
 *
 * <p><b>Solo se envuelven las rutas que lo necesitan</b> y con un tope de tamaño: cargar en memoria
 * el cuerpo de cualquier petición del sistema sería regalar un consumo que quien llama controla.
 * Superado el tope, se conserva lo leído y el resto se descarta — el límite de tasa no necesita el
 * cuerpo entero, solo el identificador, y el rechazo por tamaño le corresponde a otra capa.
 */
final class CachedBodyRequest extends HttpServletRequestWrapper {

  private final byte[] cuerpo;

  CachedBodyRequest(HttpServletRequest original, int tope) throws IOException {
    super(original);
    this.cuerpo = original.getInputStream().readNBytes(tope);
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream fuente = new ByteArrayInputStream(cuerpo);

    return new ServletInputStream() {
      @Override
      public boolean isFinished() {
        return fuente.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener oyente) {
        // Lectura no bloqueante: no aplica sobre un arreglo ya en memoria.
        throw new UnsupportedOperationException("El cuerpo ya está leído por completo");
      }

      @Override
      public int read() {
        return fuente.read();
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }
}
