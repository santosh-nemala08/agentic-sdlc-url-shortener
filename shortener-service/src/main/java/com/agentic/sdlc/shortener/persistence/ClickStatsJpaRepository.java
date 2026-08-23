package com.agentic.sdlc.shortener.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface ClickStatsJpaRepository extends JpaRepository<ClickStatsEntity, String> {

    /**
     * A single atomic {@code UPDATE ... SET x = x + 1}, not a read-modify-write in Java --
     * correct under concurrent clicks on the same link without needing a lock. Returns 0 (no
     * row updated) the first time a code is ever clicked, which the caller uses to fall back
     * to an insert.
     *
     * Both {@code Modifying} flags matter, and in this order of reasoning:
     * {@code clearAutomatically = true} is needed because a bulk JPQL UPDATE executes directly
     * against the database and bypasses Hibernate's first-level cache, so a later
     * {@code findById} for the same code in the same transaction would otherwise return the
     * stale pre-update entity. But clearing the persistence context discards any of ITS pending,
     * not-yet-flushed changes too -- and {@code @Modifying} queries do not auto-flush by
     * default. Without {@code flushAutomatically = true} as well, a Link {@code persist()}ed
     * moments earlier in the same transaction (e.g. by a create-then-redirect-immediately test)
     * would be silently discarded by the clear before it ever reached the database, rather than
     * merely becoming stale in the cache -- a real bug this project's own tests caught: a
     * second redirect within one transaction started 404ing right after the first one recorded
     * a click.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ClickStatsEntity c SET c.totalClicks = c.totalClicks + 1, c.lastClickedAt = :now "
            + "WHERE c.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode, @Param("now") Instant now);
}
