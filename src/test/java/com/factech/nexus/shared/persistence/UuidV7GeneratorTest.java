package com.factech.nexus.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RF-SP-001` · `T-05` — generación del identificador en la aplicación. */
class UuidV7GeneratorTest {

  private final UuidV7Generator generador = new UuidV7Generator();

  @Test
  @DisplayName("el valor generado es versión 7 y variante RFC 9562")
  void versionSiete() {
    UUID id = generador.next();

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  @DisplayName("mil identificadores seguidos no repiten ninguno")
  void unicos() {
    Set<UUID> generados = new HashSet<>();
    IntStream.range(0, 1_000).forEach(i -> generados.add(generador.next()));

    assertThat(generados).hasSize(1_000);
  }

  @Test
  @DisplayName("crecen de forma monótona, que es la razón de usar v7 y no v4")
  void monotonos() {
    // Se comparan como cadenas y no con compareTo(): UUID.compareTo trata los
    // dos bloques de 64 bits como enteros CON SIGNO, de modo que un valor con
    // el bit más alto activo sale "menor" que uno sin él. El índice B-tree de
    // PostgreSQL ordena por bytes sin signo, que es lo que esta prueba
    // pretende reflejar.
    List<String> ids = IntStream.range(0, 500).mapToObj(i -> generador.next().toString()).toList();

    assertThat(ids).isSorted();
  }
}
