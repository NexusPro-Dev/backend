package com.factech.nexus.modules.indicators.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * El resumen de cuentas de broker y de mi personal (`RF-IN-010`, `RN-IN-015`).
 *
 * <p>Cada persona cuenta <b>lo que originó ella</b>, no su red: la columna de {@code people} se
 * puede sumar. Solo cuentan las cuentas {@code CONSUMIDOR}.
 */
public record BrokerAccountsSummaryResponse(
    @Schema(description = "El día del calendario del negocio que se tomó como hoy.")
        LocalDate today,
    Totals totals,
    @Schema(description = "El personal del alcance, un grupo por rol de la cúspide hacia abajo.")
        List<RoleGroup> staff) {

  @Schema(name = "BrokerAccountsSummaryTotals")
  public record Totals(
      @Schema(description = "Todas las cuentas de consumidor.") long accounts,
      @Schema(description = "Las que están en FIRST_DEPOSIT.") long ftd,
      @Schema(description = "Las que no tienen titular.") long withoutHolder,
      @JsonInclude(JsonInclude.Include.ALWAYS)
          @Schema(
              description =
                  "Las sin vendedor de origen, o con origen fuera de la fuerza comercial. Solo con"
                      + " el alcance entero; nulo en otro caso.")
          Long withoutSeller,
      @Schema(description = "Las creadas hoy.") long accountsToday,
      @Schema(description = "Las de primer depósito llegado hoy.") long ftdToday) {}

  @Schema(name = "BrokerAccountsSummaryRoleGroup")
  public record RoleGroup(
      String roleCode,
      long accounts,
      long ftd,
      long accountsToday,
      long ftdToday,
      List<Person> people) {}

  @Schema(name = "BrokerAccountsSummaryPerson")
  public record Person(
      User user,
      @Schema(description = "Las que originó su afftrack.") long accounts,
      long ftd,
      long accountsToday,
      long ftdToday) {}

  @Schema(name = "BrokerAccountsSummaryUser")
  public record User(UUID id, String username, String firstName, String lastName) {}
}
