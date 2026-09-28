package com.factech.nexus.modules.movements.domain.models;

/**
 * Las cuentas de la etapa 6 (`RN-MV-041`, `requirements/mv.md` §4.3). Las tres primeras son de una
 * persona y no bajan de cero; las tres últimas son contrapartidas de la empresa y lo normal es que
 * estén en negativo. Es lo mismo que declara {@code ck_accounts_kind}.
 */
public enum AccountKind {
  BILLETERA("Billetera", false),
  RETENIDO("Retenido", false),
  PUNTOS("Puntos", false),
  COMISIONES("Comisiones por pagar", true),
  BONOS("Bonos", true),
  RETIROS("Retiros pagados", true);

  private final String nombre;
  private final boolean deLaEmpresa;

  AccountKind(String nombre, boolean deLaEmpresa) {
    this.nombre = nombre;
    this.deLaEmpresa = deLaEmpresa;
  }

  public String nombre() {
    return nombre;
  }

  public boolean deLaEmpresa() {
    return deLaEmpresa;
  }
}
