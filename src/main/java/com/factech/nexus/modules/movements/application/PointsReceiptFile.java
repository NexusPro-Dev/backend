package com.factech.nexus.modules.movements.application;

/** El archivo del comprobante de un ajuste, para descargarlo (`RN-MV-077`). */
public record PointsReceiptFile(String fileName, String contentType, byte[] content) {}
