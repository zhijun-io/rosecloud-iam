package io.rosecloud.iam.api;

public record OperatorSetupBeginResponse(String totpSecret, String otpauthUrl) {}
