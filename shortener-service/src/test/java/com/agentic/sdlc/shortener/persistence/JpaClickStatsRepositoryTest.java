package com.agentic.sdlc.shortener.persistence;

import com.agentic.sdlc.shortener.domain.ClickStats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaClickStatsRepository.class)
class JpaClickStatsRepositoryTest {

    @Autowired
    private JpaClickStatsRepository repository;

    @Test
    void findByShortCodeIsEmptyBeforeAnyClickIsRecorded() {
        assertThat(repository.findByShortCode("nevr0000")).isEmpty();
    }

    @Test
    void firstClickCreatesARowWithCountOne() {
        repository.recordClick("abc1234");

        ClickStats stats = repository.findByShortCode("abc1234").orElseThrow();
        assertThat(stats.totalClicks()).isEqualTo(1);
        assertThat(stats.lastClickedAt()).isNotNull();
    }

    @Test
    void subsequentClicksIncrementTheExistingRowRatherThanReplacingIt() {
        repository.recordClick("abc1234");
        repository.recordClick("abc1234");
        repository.recordClick("abc1234");

        assertThat(repository.findByShortCode("abc1234").orElseThrow().totalClicks()).isEqualTo(3);
    }

    @Test
    void lastClickedAtAdvancesWithEachClick() throws InterruptedException {
        repository.recordClick("abc1234");
        var first = repository.findByShortCode("abc1234").orElseThrow().lastClickedAt();

        Thread.sleep(5);
        repository.recordClick("abc1234");
        var second = repository.findByShortCode("abc1234").orElseThrow().lastClickedAt();

        assertThat(second).isAfter(first);
    }

    @Test
    void clicksOnDifferentCodesAreTrackedIndependently() {
        repository.recordClick("code0001");
        repository.recordClick("code0001");
        repository.recordClick("code0002");

        assertThat(repository.findByShortCode("code0001").orElseThrow().totalClicks()).isEqualTo(2);
        assertThat(repository.findByShortCode("code0002").orElseThrow().totalClicks()).isEqualTo(1);
    }
}
