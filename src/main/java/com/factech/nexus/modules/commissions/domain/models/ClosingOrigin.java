package com.factech.nexus.modules.commissions.domain.models;

/** Quién disparó un cierre del periodo (`RN-CM-035`). */
public enum ClosingOrigin {
  /** El reloj, con la frecuencia de configuración. Sin actor. */
  PROGRAMADO,
  /** Una persona con {@code commission-batches:settle}, para relanzar el que no corrió. */
  MANUAL
}
