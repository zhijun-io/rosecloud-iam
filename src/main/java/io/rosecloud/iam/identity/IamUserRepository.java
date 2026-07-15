package io.rosecloud.iam.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamUserRepository extends JpaRepository<IamUser, UUID> {

  Optional<IamUser> findByEmailIgnoreCase(String email);
}
