package io.rosecloud.iam.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "rosecloud.iam.mail", name = "enabled", havingValue = "true")
class DeliverySchedulingConfiguration {}
