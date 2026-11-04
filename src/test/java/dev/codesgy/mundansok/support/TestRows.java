package dev.codesgy.mundansok.support;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

/** 테스트용으로 도메인 · 점검 행을 바로 넣는다 (상태 · 시각을 마음대로 정하려고) */
public final class TestRows {

    private TestRows() {}

    public static Long verifiedDomain(JdbcTemplate jdbc, String name) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO domain (name, verification_token, verified_at, created_at) VALUES (?, 'token', ?, ?)",
                name, now, now);
        return jdbc.queryForObject("SELECT id FROM domain WHERE name = ?", Long.class, name);
    }

    public static Long scan(JdbcTemplate jdbc, Long domainId, String status, Instant createdAt) {
        jdbc.update("INSERT INTO scan (domain_id, status, created_at) VALUES (?, ?, ?)",
                domainId, status, Timestamp.from(createdAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM scan WHERE domain_id = ?", Long.class, domainId);
    }
}
