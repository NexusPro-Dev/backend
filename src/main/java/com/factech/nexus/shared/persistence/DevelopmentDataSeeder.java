package com.factech.nexus.shared.persistence;

import com.factech.nexus.shared.config.RuntimeEnvironment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

/**
 * Aplica la semilla de datos de prueba al arrancar, <b>salvo en producción</b>.
 *
 * <h2>Qué siembra, y por qué esto no puede llegar a producción</h2>
 *
 * <p>Quince personas de prueba con sus roles y tres membresías. Las quince <b>comparten el hash de
 * contraseña del superadministrador</b> y nacen <b>sin marca de cambio obligatorio</b>, al revés
 * que cualquier alta real por la API. En un entorno de desarrollo eso es exactamente lo que se
 * quiere; en el sistema real serían quince cuentas con una credencial que alguien más conoce y que
 * nadie está obligado a cambiar.
 *
 * <h2>El guardia es un dominio cerrado, no una comparación de cadenas</h2>
 *
 * <p>La condición es «el entorno no es {@code production}», y esa frase es peligrosa escrita sobre
 * una cadena suelta: {@code Production}, {@code prod}, el vacío y la variable sin declarar también
 * son «distintos de production». Por eso {@link RuntimeEnvironment} traduce el valor a uno de los
 * tres del Art. IX.4 y <b>tumba el arranque si no lo reconoce</b>: aquí ya no queda un cuarto
 * estado en el que fallar abierto.
 *
 * <p><b>Se siembra en {@code development} y también en {@code testing}</b>, por decisión del
 * responsable del proyecto el 31-08-2026. {@code testing} es un entorno desplegado y alcanzable, de
 * modo que queda escrito lo que eso implica: nombres de usuario adivinables —{@code admin1}, {@code
 * cliente1}— expuestos a un host público. Lo que NO añade es una credencial nueva: al compartir el
 * hash del superadministrador, quien pueda entrar con ellas ya podía entrar como SUPERADMIN.
 *
 * <h2>Apagable, como la purga y el límite de tasa</h2>
 *
 * <p>{@code DEV_SEED_ENABLED} existe por lo mismo que {@code TOKEN_PURGE_ENABLED} y {@code
 * RATE_LIMIT_ENABLED}: <b>la suite lo apaga</b>. Sus pruebas corren sobre {@code testing} y cuentan
 * personas y roles; quince cuentas apareciendo por su cuenta harían fallar decenas de ellas por
 * algo que no tiene que ver con lo que comprueban.
 *
 * <p>El interruptor <b>no puede reabrir producción</b>: el entorno se comprueba primero y por
 * separado, de modo que {@code DEV_SEED_ENABLED=true} en producción no siembra nada.
 *
 * <h2>Detalles de ejecución</h2>
 *
 * <p>Es un {@link ApplicationRunner}, con lo que corre <b>después de que Flyway haya migrado</b> y
 * <b>antes de que la instancia se declare lista</b>: nadie recibe tráfico contra una base a medio
 * sembrar.
 *
 * <p><b>El guion es idempotente y va en una sola transacción.</b> Lo primero permite reiniciar sin
 * duplicar a nadie —que es lo que ocurre en cada arranque—; lo segundo evita que un fallo a mitad
 * deje personas sin rol, que es un estado que {@code RN-SP-023} prohíbe y que ninguna operación de
 * la API sabría corregir.
 */
@Component
public class DevelopmentDataSeeder implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DevelopmentDataSeeder.class);

  /** El guion vive en el classpath porque tiene que viajar dentro del artefacto que se ejecuta. */
  private static final String GUION = "db/dev-seed/semilla-desarrollo.sql";

  private final RuntimeEnvironment entorno;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transacciones;
  private final boolean habilitada;

  public DevelopmentDataSeeder(
      RuntimeEnvironment entorno,
      JdbcTemplate jdbc,
      PlatformTransactionManager transacciones,
      @Value("${nexus.dev-seed.enabled:true}") boolean habilitada) {
    this.entorno = entorno;
    this.jdbc = jdbc;
    this.transacciones = new TransactionTemplate(transacciones);
    this.habilitada = habilitada;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (entorno.esProduccion()) {
      // Se registra el hecho y no solo la ausencia: en el log de un arranque de
      // producción tiene que verse que la decisión se tomó, y no que nadie la
      // planteó. Es la línea que se busca el día que aparezca un `cliente1`.
      LOG.info("Entorno de producción: la semilla de datos de prueba NO se aplica.");
      return;
    }
    if (!habilitada) {
      LOG.info("Semilla de datos de prueba desactivada por configuración (DEV_SEED_ENABLED).");
      return;
    }

    transacciones.executeWithoutResult(estado -> jdbc.execute(leerGuion()));

    LOG.warn(
        "SEMILLA DE DESARROLLO APLICADA en el entorno '{}': hay {} personas con la contraseña del"
            + " superadministrador y sin cambio obligatorio. Esto no debe ocurrir en producción.",
        entorno.valor().name().toLowerCase(),
        cuantasPersonas());
  }

  private String leerGuion() {
    try {
      return StreamUtils.copyToString(
          new ClassPathResource(GUION).getInputStream(), StandardCharsets.UTF_8);
    } catch (IOException fallo) {
      // Tumba el arranque a propósito: el guion viaja dentro del artefacto, de
      // modo que no poder leerlo significa que el artefacto está mal construido
      // y no que falte un dato del entorno.
      throw new UncheckedIOException(
          "No se pudo leer la semilla de desarrollo del classpath: " + GUION, fallo);
    }
  }

  private Integer cuantasPersonas() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE deleted_at IS NULL", Integer.class);
  }
}
