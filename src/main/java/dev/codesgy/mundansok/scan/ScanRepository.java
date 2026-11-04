package dev.codesgy.mundansok.scan;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    Optional<Scan> findFirstByDomainIdOrderByCreatedAtDesc(Long domainId);

    /** before 이전에 만들어졌는데 아직 끝나지 않은 점검을 한 번에 실패로 바꾼다. 바꾼 건수를 돌려준다 */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Scan s SET s.status = :failed, s.error = :reason, s.finishedAt = :at
            WHERE s.status IN :unfinished AND s.createdAt < :before""")
    int failUnfinishedBefore(@Param("unfinished") Collection<ScanStatus> unfinished, @Param("before") Instant before,
            @Param("failed") ScanStatus failed, @Param("reason") String reason, @Param("at") Instant at);
}
