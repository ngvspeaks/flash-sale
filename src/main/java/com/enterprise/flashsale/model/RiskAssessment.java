package com.enterprise.flashsale.model;

public record RiskAssessment(
    String targetIdentifier,
    double botProbability,
    boolean shouldQuarantine,
    String rationale
) {}
