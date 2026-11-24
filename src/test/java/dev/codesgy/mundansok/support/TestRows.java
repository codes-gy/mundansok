package dev.codesgy.mundansok.support;

import dev.codesgy.mundansok.domain.OwnerTokens;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 테스트용으로 도메인 · 등록 · 점검 행을 바로 넣는다 (상태 · 시각을 마음대로 정하려고) */
public final class TestRows {

    private TestRows() {}

    /**
     * 소유 인증을 마친 등록 하나.
     *
     * @param txtName  인증 TXT 레코드 이름 — 점검 요청 때 다시 확인하므로 가짜 DNS에 넣어 둬야 한다
     * @param txtValue 그 레코드에 넣을 값
     */
    public record TestClaim(Long domainId, Long claimId, UUID publicId, String ownerToken, String txtName, String txtValue) {}

    public static TestClaim verifiedClaim(JdbcTemplate jdbc, String name) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO domain (name, created_at) VALUES (?, ?)", name, now);
        Long domainId = jdbc.queryForObject("SELECT id FROM domain WHERE name = ?", Long.class, name);
        UUID publicId = UUID.randomUUID();
        String ownerToken = OwnerTokens.generate();
        jdbc.update("""
                INSERT INTO domain_claim (public_id, domain_id, owner_secret_hash, verification_token, verified_at, created_at)
                VALUES (?, ?, ?, 'token', ?, ?)""", publicId, domainId, OwnerTokens.hash(ownerToken), now, now);
        Long claimId = jdbc.queryForObject("SELECT id FROM domain_claim WHERE public_id = ?", Long.class, publicId);
        return new TestClaim(domainId, claimId, publicId, ownerToken, "_mundansok-verify." + name, "mundansok-verify=token");
    }

    public static Long scan(JdbcTemplate jdbc, Long domainId, String status, Instant createdAt) {
        jdbc.update("INSERT INTO scan (domain_id, status, created_at) VALUES (?, ?, ?)",
                domainId, status, Timestamp.from(createdAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM scan WHERE domain_id = ?", Long.class, domainId);
    }
}
