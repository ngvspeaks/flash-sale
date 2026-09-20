package com.enterprise.flashsale.model;

import java.io.Serializable;

public record StockReleaseEvent(
    String skuId,
    int quantityReleased,
    long timestamp,
    String reason
) implements Serializable {}
