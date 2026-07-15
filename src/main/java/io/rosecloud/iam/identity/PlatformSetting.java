package io.rosecloud.iam.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_setting")
class PlatformSetting {

  @Id
  @Column(name = "setting_key", length = 64)
  private String settingKey;

  @Column(name = "value_bool", nullable = false)
  private boolean valueBool;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PlatformSetting() {}

  PlatformSetting(String settingKey, boolean valueBool) {
    this.settingKey = settingKey;
    this.valueBool = valueBool;
  }

  String settingKey() {
    return settingKey;
  }

  boolean valueBool() {
    return valueBool;
  }

  void setValueBool(boolean valueBool) {
    this.valueBool = valueBool;
  }

  @PrePersist
  @PreUpdate
  void touch() {
    this.updatedAt = Instant.now();
  }
}
