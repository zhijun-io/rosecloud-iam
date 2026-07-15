package io.rosecloud.iam.api;

public record FactorEnrollmentBeginResponse(String totpSecret, String otpauthUrl) {}
