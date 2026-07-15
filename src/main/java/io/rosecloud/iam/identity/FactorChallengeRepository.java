package io.rosecloud.iam.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FactorChallengeRepository extends JpaRepository<FactorChallenge, UUID> {}
