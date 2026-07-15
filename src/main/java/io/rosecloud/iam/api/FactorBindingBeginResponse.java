package io.rosecloud.iam.api;

public record FactorBindingBeginResponse(String totpSecret, String otpauthUrl) {}
