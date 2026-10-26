package dev.codesgy.perimeter.scan;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    Optional<Scan> findFirstByDomainIdOrderByCreatedAtDesc(Long domainId);
}
