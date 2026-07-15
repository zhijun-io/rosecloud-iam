package io.rosecloud.iam.operator;

import io.rosecloud.iam.identity.OperatorTotpBindingPort;
import io.rosecloud.iam.identity.TotpService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class OperatorTotpBindingAdapter implements OperatorTotpBindingPort {

  private final PlatformOperatorRepository platformOperatorRepository;
  private final TotpService totpService;

  OperatorTotpBindingAdapter(
      PlatformOperatorRepository platformOperatorRepository, TotpService totpService) {
    this.platformOperatorRepository = platformOperatorRepository;
    this.totpService = totpService;
  }

  @Override
  public boolean hasBinding(UUID operatorId) {
    return platformOperatorRepository.findById(operatorId).filter(PlatformOperator::hasTotpBinding).isPresent();
  }

  @Override
  public Optional<String> ciphertextHint(UUID operatorId) {
    return platformOperatorRepository
        .findById(operatorId)
        .filter(PlatformOperator::hasTotpBinding)
        .map(PlatformOperator::totpSecretCiphertext);
  }

  @Override
  public boolean verifyTotp(UUID operatorId, String totpCode) {
    return platformOperatorRepository
        .findById(operatorId)
        .filter(PlatformOperator::hasTotpBinding)
        .map(
            op ->
                totpService.verify(op.totpSecretKeyId(), op.totpSecretCiphertext(), totpCode))
        .orElse(false);
  }
}
