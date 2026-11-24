package dev.codesgy.mundansok.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainClaimRepository extends JpaRepository<DomainClaim, Long> {

    Optional<DomainClaim> findByPublicId(UUID publicId);
}
