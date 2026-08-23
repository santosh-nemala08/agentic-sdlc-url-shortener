package com.agentic.sdlc.shortener.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's auto-implemented repository for {@link LinkEntity}. Not exposed outside this package. */
interface LinkJpaRepository extends JpaRepository<LinkEntity, String> {
}
