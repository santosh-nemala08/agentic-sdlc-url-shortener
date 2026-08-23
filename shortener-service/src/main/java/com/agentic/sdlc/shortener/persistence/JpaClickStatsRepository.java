package com.agentic.sdlc.shortener.persistence;

import com.agentic.sdlc.shortener.domain.ClickStats;
import com.agentic.sdlc.shortener.domain.ClickStatsRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaClickStatsRepository implements ClickStatsRepository {

    private final ClickStatsJpaRepository jpaRepository;
    private final FirstClickInserter firstClickInserter;

    public JpaClickStatsRepository(ClickStatsJpaRepository jpaRepository, FirstClickInserter firstClickInserter) {
        this.jpaRepository = jpaRepository;
        this.firstClickInserter = firstClickInserter;
    }

    /**
     * Tries the atomic increment first; a return of 0 means this is the first click for the
     * code, so falls back to inserting a fresh row via {@link FirstClickInserter}, isolated in
     * its own {@code REQUIRES_NEW} transaction. Two genuinely concurrent first-clicks can both
     * see 0 and both attempt the insert -- the loser's failure is caught here, in this method's
     * own transaction, and retried as a plain increment, so the click is still counted rather
     * than lost.
     *
     * Getting this right took three attempts, each caught by writing a real concurrency test
     * ({@link ClickTrackerConcurrencyTest}) rather than reasoning it through, not by guessing:
     * <ol>
     *   <li>A plain {@code save()} on the insert path let a lost race go completely unnoticed --
     *       Hibernate defers the INSERT to the next flush, so the violation surfaced too late
     *       to catch at all.</li>
     *   <li>{@code saveAndFlush} made the violation catchable, but catching it in the SAME
     *       transaction that hit it still failed at commit with {@code UnexpectedRollbackException}
     *       -- Spring marks a transaction rollback-only the instant any
     *       {@code DataAccessException} occurs in it, regardless of whether application code
     *       catches it afterward.</li>
     *   <li>Moving the insert into its own {@code REQUIRES_NEW} transaction ({@link
     *       FirstClickInserter}) and catching the exception <em>here instead</em> -- one
     *       transaction away from where it happened -- was what actually fixed it. Catching it
     *       inside the {@code REQUIRES_NEW} method itself reproduced the identical failure one
     *       level deeper, since the same rule applies to that transaction too.</li>
     * </ol>
     */
    @Override
    @Transactional
    public void recordClick(String shortCode) {
        Instant now = Instant.now();
        int updated = jpaRepository.incrementClickCount(shortCode, now);
        if (updated == 0) {
            try {
                firstClickInserter.insertFirstClick(shortCode, now);
            } catch (DataAccessException lostTheRaceToAnotherFirstClick) {
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
