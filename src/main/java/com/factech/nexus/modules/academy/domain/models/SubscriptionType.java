package com.factech.nexus.modules.academy.domain.models;

/**
 * Quién ve una señal (`RN-AC-035`): {@code FREE}, cualquiera con membresía vigente; {@code
 * PREMIUM}, una membresía por encima de BECA; {@code PERSONALIZED}, solo las membresías elegidas.
 */
public enum SubscriptionType {
  FREE,
  PREMIUM,
  PERSONALIZED
}
