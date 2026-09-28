package com.factech.nexus.modules.commissions.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * El cierre programado se apaga por configuración (`CA-CM-179`), y la suite lo apaga: con él
 * encendido, un cierre podría pasar a {@code PENDIENTE} los lotes que una prueba acaba de abrir.
 */
class CommissionClosingJobIT extends IntegrationTestBase {

  @Autowired private ApplicationContext contexto;

  @Test
  @DisplayName("CA-CM-179 — apagado por configuración, la tarea no existe")
  void apagado() {
    assertThat(contexto.getBeansOfType(CommissionClosingJob.class)).isEmpty();
  }
}
