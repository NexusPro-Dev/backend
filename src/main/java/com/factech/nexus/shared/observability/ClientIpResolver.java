package com.factech.nexus.shared.observability;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
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
 *
 * <h2>Rangos, y por qué hicieron falta (D-21, 27-08-2026)</h2>
 *
 * <p>Hasta hoy la confianza era por <b>coincidencia exacta</b>, y eso bastaba mientras el proxy
 * tuviera una dirección fija que alguien pudiera escribir en una variable. Al aterrizar sobre
 * Railway ([`ADR-002`]) resultó que <b>no hay ninguna que poner</b>: la dirección con la que el
 * borde habla con el contenedor no es fija ni publicada, y la red privada del proveedor es <b>solo
 * IPv6</b>. Con la lista vacía el resolvedor hacía lo correcto —registrar la IP del socket—, pero
 * eso deja el Art. V.15 sin cumplir: la auditoría apunta al proxy y no a la persona.
 *
 * <p>De ahí que una entrada pueda ser ahora <b>una dirección o un bloque CIDR</b> —{@code
 * 10.0.0.0/8}, {@code fd00::/8}—, que es lo único con lo que se puede declarar «confío en la red
 * privada del proveedor» sin conocer la dirección concreta. Una dirección suelta se trata como su
 * bloque de un solo elemento, de modo que la configuración anterior sigue significando lo mismo.
 *
 * <p><b>Confiar en un rango es confiar en todo lo que salga de él</b>, y por eso la declaración
 * debe ser lo más estrecha que la plataforma permita: quien pueda emitir peticiones desde dentro de
 * ese rango puede escribir la IP que quiera en la auditoría. Es un compromiso deliberado y no una
 * comodidad — sin él, en esta plataforma no hay IP de cliente en absoluto.
 *
 * <h2>Dos cosas que este resolvedor NO hace, a propósito</h2>
 *
 * <p><b>No resuelve nombres.</b> Un valor de {@code X-Forwarded-For} que no sea un literal de IP se
 * trata como no confiable y no se le pregunta a ningún DNS: la cabecera la escribe el cliente, y
 * una consulta de nombres a partir de ella sería una petición saliente que un extraño elige.
 *
 * <p><b>No arranca con una lista que no entiende.</b> Una entrada malformada tumba el arranque
 * (Art. IX.5) en lugar de ignorarse. Ignorarla dejaría un despliegue que <b>cree</b> tener
 * configurada la confianza y no la tiene, y el síntoma —una auditoría que apunta al proxy— no
 * menciona nunca la variable mal escrita.
 */
@Component
public class ClientIpResolver {

  private static final String CABECERA = "X-Forwarded-For";

  private final List<BloqueDeConfianza> confiables;

  public ClientIpResolver(@Value("${nexus.security.trusted-proxies:}") String configurados) {
    this.confiables =
        Arrays.stream(configurados.split(","))
            .map(String::trim)
            .filter(valor -> !valor.isEmpty())
            .map(BloqueDeConfianza::parsear)
            .toList();
  }

  /**
   * Dirección de origen de la petición.
   *
   * @param peticion petición en curso
   * @return la IP resuelta, o {@code null} si el contenedor no la publica
   */
  public String resolve(HttpServletRequest peticion) {
    String delSocket = peticion.getRemoteAddr();
    if (delSocket == null || !esConfiable(delSocket)) {
      return delSocket;
    }

    String cadena = peticion.getHeader(CABECERA);
    if (cadena == null || cadena.isBlank()) {
      return delSocket;
    }

    List<String> saltos =
        Arrays.stream(cadena.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

    for (int i = saltos.size() - 1; i >= 0; i--) {
      if (!esConfiable(saltos.get(i))) {
        return saltos.get(i);
      }
    }
    return saltos.isEmpty() ? delSocket : saltos.get(0);
  }

  private boolean esConfiable(String direccion) {
    InetAddress candidato = literal(direccion);
    // Lo que no es un literal de IP no puede ser un proxy nuestro. No se
    // resuelve por nombre: ver la cabecera de la clase.
    if (candidato == null) {
      return false;
    }
    return confiables.stream().anyMatch(bloque -> bloque.contiene(candidato));
  }

  /**
   * La dirección, o {@code null} si el texto no es un literal de IP.
   *
   * <p>Se filtra <b>antes</b> de llamar a {@link InetAddress}, porque esa clase resuelve por nombre
   * lo que no reconoce como literal. Un {@code X-Forwarded-For} con {@code
   * interno.atacante.example} provocaría una consulta DNS elegida por quien manda la petición.
   */
  private static InetAddress literal(String texto) {
    // Los dos puntos solo aparecen en IPv6; un nombre de máquina no puede
    // llevarlos. El resto tiene que ser IPv4 en notación decimal.
    boolean pareceLiteral = texto.indexOf(':') >= 0 || texto.matches("[0-9.]+");
    if (!pareceLiteral) {
      return null;
    }
    try {
      return InetAddress.getByName(texto);
    } catch (UnknownHostException malformada) {
      return null;
    }
  }

  /**
   * Una dirección o un bloque CIDR en los que se confía.
   *
   * <p>La comparación es <b>por familia</b>: una dirección IPv4 nunca cae dentro de un bloque IPv6
   * ni al revés. Java normaliza las IPv4 mapeadas en IPv6 —{@code ::ffff:10.0.0.1}— a IPv4, de modo
   * que no hay una tercera familia que atender.
   */
  private record BloqueDeConfianza(byte[] red, int bits) {

    static BloqueDeConfianza parsear(String entrada) {
      int barra = entrada.indexOf('/');
      String direccion = barra < 0 ? entrada : entrada.substring(0, barra);

      InetAddress base = literal(direccion);
      if (base == null) {
        throw new IllegalArgumentException(
            "nexus.security.trusted-proxies: «"
                + entrada
                + "» no es una dirección IP ni un bloque CIDR. Se admiten literales IPv4 e IPv6,"
                + " con máscara opcional (10.0.0.0/8, fd00::/8).");
      }

      byte[] red = base.getAddress();
      int maximo = red.length * 8;
      if (barra < 0) {
        // Una dirección suelta es su propio bloque: así la configuración
        // anterior a los rangos sigue significando exactamente lo mismo.
        return new BloqueDeConfianza(red, maximo);
      }

      int bits = mascara(entrada, barra, maximo);
      return new BloqueDeConfianza(red, bits);
    }

    private static int mascara(String entrada, int barra, int maximo) {
      int bits;
      try {
        bits = Integer.parseInt(entrada.substring(barra + 1).trim());
      } catch (NumberFormatException noEsUnNumero) {
        throw new IllegalArgumentException(
            "nexus.security.trusted-proxies: la máscara de «" + entrada + "» no es un número.",
            noEsUnNumero);
      }
      if (bits < 0 || bits > maximo) {
        throw new IllegalArgumentException(
            "nexus.security.trusted-proxies: la máscara de «"
                + entrada
                + "» está fuera de rango. Para esa familia debe estar entre 0 y "
                + maximo
                + ".");
      }
      return bits;
    }

    boolean contiene(InetAddress candidato) {
      byte[] suyo = candidato.getAddress();
      if (suyo.length != red.length) {
        return false;
      }

      int bytesEnteros = bits / 8;
      for (int i = 0; i < bytesEnteros; i++) {
        if (suyo[i] != red[i]) {
          return false;
        }
      }

      int sobrantes = bits % 8;
      if (sobrantes == 0) {
        return true;
      }
      // Los bits que quedan por comparar son los MÁS significativos del byte
      // siguiente: la máscara los deja a uno y anula el resto.
      int mascara = (0xFF << (8 - sobrantes)) & 0xFF;
      return (suyo[bytesEnteros] & mascara) == (red[bytesEnteros] & mascara);
    }
  }
}
