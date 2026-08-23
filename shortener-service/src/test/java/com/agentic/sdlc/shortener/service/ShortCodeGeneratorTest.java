package com.agentic.sdlc.shortener.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void defaultLengthIsSeven() {
        assertThat(generator.generate()).hasSize(7);
    }

    @Test
    void respectsCustomLength() {
        assertThat(generator.generate(12)).hasSize(12);
    }

    @Test
    void onlyProducesBase62Characters() {
        assertThat(generator.generate(50)).matches("[A-Za-z0-9]+");
    }

    @Test
    void repeatedCallsAreEffectivelyUnique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes).hasSize(1000);
    }
}
