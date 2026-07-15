package io.rosecloud.iam.api;

public record InvitationAcceptBeginResponse(String totpSecret, String otpauthUrl) {}
