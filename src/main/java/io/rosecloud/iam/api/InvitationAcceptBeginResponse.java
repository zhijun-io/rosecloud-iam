package io.rosecloud.iam.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvitationAcceptBeginResponse(String totpSecret, String otpauthUrl) {}
