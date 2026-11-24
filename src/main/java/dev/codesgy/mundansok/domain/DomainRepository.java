package dev.codesgy.mundansok.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DomainRepository extends JpaRepository<Domain, Long> {

    Optional<Domain> findByName(String name);

    /**
     * 쿨다운이 지났을 때만 마지막 점검 요청 시각을 now로 바꾼다. 바꾼 건수(0 또는 1)를 돌려준다.
     * 확인과 기록을 UPDATE 한 문장으로 하므로, 동시에 여러 요청이 와도 하나만 1을 받는다(행 잠금).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Domain d SET d.lastScanRequestedAt = :now
            WHERE d.id = :id AND (d.lastScanRequestedAt IS NULL OR d.lastScanRequestedAt <= :notAfter)""")
    int acquireScanSlot(@Param("id") Long id, @Param("now") Instant now, @Param("notAfter") Instant notAfter);

    /** 받아 둔 점검 자리를 되돌린다. 그사이 다른 요청이 자리를 가져갔으면(값이 taken이 아니면) 건드리지 않는다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Domain d SET d.lastScanRequestedAt = :previous
            WHERE d.id = :id AND d.lastScanRequestedAt = :taken""")
    int releaseScanSlot(@Param("id") Long id, @Param("taken") Instant taken, @Param("previous") Instant previous);

    @Query("SELECT d.lastScanRequestedAt FROM Domain d WHERE d.id = :id")
    Optional<Instant> findLastScanRequestedAt(@Param("id") Long id);
}
