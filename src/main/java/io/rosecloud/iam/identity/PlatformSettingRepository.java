package io.rosecloud.iam.identity;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {}
