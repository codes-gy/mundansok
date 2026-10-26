package dev.codesgy.mundansok.scan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FindingRepository extends JpaRepository<Finding, Long> {

    List<Finding> findByScanId(Long scanId);
}
