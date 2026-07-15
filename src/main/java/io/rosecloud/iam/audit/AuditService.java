package io.rosecloud.iam.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

  public static final String OPERATOR_SETUP_TOKEN_ISSUED = "operator.setup_token_issued";
  public static final String OPERATOR_SETUP_BEGUN = "operator.setup_begun";
  public static final String OPERATOR_SETUP_COMPLETED = "operator.setup_completed";
  public static final String OPERATOR_SETUP_REJECTED = "operator.setup_rejected";
  public static final String OPERATOR_LOGIN_SUCCEEDED = "operator.login_succeeded";
  public static final String OPERATOR_LOGIN_FAILED = "operator.login_failed";
  public static final String USER_LOGIN_SUCCEEDED = "user.login_succeeded";
  public static final String USER_LOGIN_FAILED = "user.login_failed";
  public static final String USER_REFRESH_SUCCEEDED = "user.refresh_succeeded";
  public static final String USER_REFRESH_REUSE_REVOKED = "user.refresh_reuse_revoked";
  public static final String USER_LOGOUT = "user.logout";
  public static final String USER_TENANT_CONTEXT_SELECTED = "user.tenant_context_selected";

  private final AuditEventRepository auditEventRepository;

  public AuditService(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void append(String eventType, UUID actorId, String details) {
    auditEventRepository.save(new AuditEvent(eventType, actorId, details));
  }
}
