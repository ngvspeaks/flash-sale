package com.enterprise.flashsale.model;

import java.io.Serializable;

public record OrderEvent(
    String orderId,
    String reservationId,
    String skuId,
    String userId,
    int quantity,
    long timestamp,
    String ipAddress,
    String userAgent,
    double riskScore
) implements Serializable {}
