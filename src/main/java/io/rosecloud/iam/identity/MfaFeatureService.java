package io.rosecloud.iam.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaFeatureService {

  static final String KEY = "mfa_feature";

  private final PlatformSettingRepository platformSettingRepository;

  public MfaFeatureService(PlatformSettingRepository platformSettingRepository) {
    this.platformSettingRepository = platformSettingRepository;
  }

  @Transactional(readOnly = true)
  public boolean isEnabled() {
    return platformSettingRepository
        .findById(KEY)
        .map(PlatformSetting::valueBool)
        .orElse(false);
  }

  @Transactional
  public void setEnabled(boolean enabled) {
    PlatformSetting setting =
        platformSettingRepository.findById(KEY).orElseGet(() -> new PlatformSetting(KEY, false));
    setting.setValueBool(enabled);
    platformSettingRepository.save(setting);
  }
}
