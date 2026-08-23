package com.agentic.sdlc.shortener.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Isolated on purpose: once a {@code DataAccessException} occurs inside a
 * transaction, Spring marks that whole transaction rollback-only --
 * catching the exception does not undo that. {@code REQUIRES_NEW} gives
 * the insert attempt its own, separate transaction, so if a concurrent
 * first-click loses the race, only this transaction is affected -- the
 * caller's transaction is never touched.
 *
 * Deliberately does NOT catch the exception here, even though this class
 * exists specifically to handle a losing race -- that was tried and
 * proven wrong by {@link ClickTrackerConcurrencyTest}: catching inside
 * this {@code REQUIRES_NEW} method still marks THIS transaction
 * rollback-only, so returning normally afterward makes ITS OWN commit
 * fail with {@code UnexpectedRollbackException} instead. Letting the
 * exception propagate lets Spring perform a normal, clean rollback of
 * just this nested transaction and rethrow the original exception --
 * {@link JpaClickStatsRepository#recordClick}, a different transaction
 * entirely, catches it there instead, where doing so is safe.
 *
 * This is a separate Spring bean, not a private method on
 * {@link JpaClickStatsRepository}, because {@code @Transactional}'s
 * propagation only takes effect through the Spring AOP proxy -- a
 * same-class {@code this.method()} call bypasses it entirely.
 */
@Component
class FirstClickInserter {

    private final ClickStatsJpaRepository jpaRepository;

    FirstClickInserter(ClickStatsJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insertFirstClick(String shortCode, Instant now) {
        jpaRepository.saveAndFlush(new ClickStatsEntity(shortCode, 1, now));
    }
}
