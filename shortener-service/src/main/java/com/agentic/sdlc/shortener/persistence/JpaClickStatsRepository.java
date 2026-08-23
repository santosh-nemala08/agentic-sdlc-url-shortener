package com.agentic.sdlc.shortener.persistence;

import com.agentic.sdlc.shortener.domain.ClickStats;
import com.agentic.sdlc.shortener.domain.ClickStatsRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaClickStatsRepository implements ClickStatsRepository {

    private final ClickStatsJpaRepository jpaRepository;

    public JpaClickStatsRepository(ClickStatsJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Tries the atomic increment first; a return of 0 means this is the first click for the
     * code, so falls back to inserting a fresh row. Two genuinely concurrent first-clicks can
     * both see 0 and both attempt the insert -- the second one's unique-key violation is caught
     * and retried as an increment, so the click is still counted rather than lost or thrown.
     */
    @Override
    @Transactional
    public void recordClick(String shortCode) {
        Instant now = Instant.now();
        int updated = jpaRepository.incrementClickCount(shortCode, now);
        if (updated == 0) {
            try {
                jpaRepository.save(new ClickStatsEntity(shortCode, 1, now));
            } catch (DataIntegrityViolationException concurrentFirstClick) {
                jpaRepository.incrementClickCount(shortCode, now);
            }
        }
    }

    @Override
    public Optional<ClickStats> findByShortCode(String shortCode) {
        return jpaRepository.findById(shortCode)
                .map(e -> new ClickStats(e.getShortCode(), e.getTotalClicks(), e.getLastClickedAt()));
    }
}
