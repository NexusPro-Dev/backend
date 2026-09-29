package com.factech.nexus.modules.commissions.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.commissions.domain.models.RateSource;
import com.factech.nexus.modules.commissions.domain.service.AfftrackTierPicker.Pick;
import com.factech.nexus.modules.commissions.domain.service.AfftrackTierPicker.Tier;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RN-CM-041` sin base de datos: las cuatro filas de `RF-CM-020` `spec.md` §2.1 y los bordes. */
class AfftrackTierPickerTest {

  private static final Tier CINCUENTA = escalon(50, "8000");
  private static final Tier SESENTA = escalon(60, "9000");

  @Test
  @DisplayName("55 con 50 y 60: paga el de 50 y quedan 5 — el ejemplo del responsable del proyecto")
  void cincuentaYCinco() {
    Pick p = AfftrackTierPicker.elegir(55, List.of(CINCUENTA, SESENTA));
    assertThat(p.tier()).contains(CINCUENTA);
    assertThat(p.pagados()).isEqualTo(50);
    assertThat(p.remanente()).isEqualTo(5);
    assertThat(p.tier().orElseThrow().importe()).isEqualByComparingTo("400000");
  }

  @Test
  @DisplayName("130 con 50 y 60: paga UNA vez el de 60 y quedan 70")
  void unaSolaVez() {
    Pick p = AfftrackTierPicker.elegir(130, List.of(CINCUENTA, SESENTA));
    assertThat(p.tier()).contains(SESENTA);
    assertThat(p.remanente()).isEqualTo(70);
  }

  @Test
  @DisplayName("40 con 50 y 60: no paga nada y quedan los 40")
  void noAlcanza() {
    Pick p = AfftrackTierPicker.elegir(40, List.of(CINCUENTA, SESENTA));
    assertThat(p.tier()).isEmpty();
    assertThat(p.pagados()).isZero();
    assertThat(p.remanente()).isEqualTo(40);
  }

  @Test
  @DisplayName("sin escala: no paga y todo queda")
  void sinEscala() {
    assertThat(AfftrackTierPicker.elegir(12, List.of()).remanente()).isEqualTo(12);
  }

  @Test
  @DisplayName("el límite exacto se alcanza y no queda nada")
  void exacto() {
    Pick p = AfftrackTierPicker.elegir(60, List.of(CINCUENTA, SESENTA));
    assertThat(p.tier()).contains(SESENTA);
    assertThat(p.remanente()).isZero();
  }

  @Test
  @DisplayName("el mayor alcanzado gana aunque pague menos por FTD")
  void elMayorAunquePagueMenos() {
    Tier caroPequeno = escalon(50, "9000");
    Tier baratoGrande = escalon(60, "7000");
    assertThat(AfftrackTierPicker.elegir(60, List.of(caroPequeno, baratoGrande)).tier())
        .contains(baratoGrande);
  }

  @Test
  @DisplayName("un escalón de cero se alcanza, no paga, y consume los FTD")
  void escalonDeCero() {
    Pick p = AfftrackTierPicker.elegir(55, List.of(escalon(50, "0")));
    assertThat(p.pagados()).isEqualTo(50);
    assertThat(p.remanente()).isEqualTo(5);
    assertThat(p.tier().orElseThrow().importe()).isEqualByComparingTo("0");
  }

  private static Tier escalon(int limite, String valor) {
    return new Tier(UUID.randomUUID(), RateSource.ROL, limite, new BigDecimal(valor));
  }
}
