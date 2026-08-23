package com.agentic.sdlc.shortener.persistence;

import com.agentic.sdlc.shortener.domain.Link;
import com.agentic.sdlc.shortener.domain.LinkRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * The production {@link LinkRepository}: durable storage via
 * {@link LinkJpaRepository}, translating between the domain's {@link Link}
 * record and the JPA-shaped {@link LinkEntity} at the boundary so neither
 * the domain model nor the service layer above it ever needs to know JPA
 * exists.
 */
@Repository
public class JpaLinkRepository implements LinkRepository {

    private final LinkJpaRepository jpaRepository;

    public JpaLinkRepository(LinkJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Link save(Link link) {
        jpaRepository.save(toEntity(link));
        return link;
    }

    @Override
    public Optional<Link> findByShortCode(String shortCode) {
        return jpaRepository.findById(shortCode).map(JpaLinkRepository::toDomain);
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        return jpaRepository.existsById(shortCode);
    }

    private static LinkEntity toEntity(Link link) {
        return new LinkEntity(link.shortCode(), link.originalUrl(), link.createdAt(), link.expiresAt());
    }

    private static Link toDomain(LinkEntity entity) {
        return new Link(entity.getShortCode(), entity.getOriginalUrl(), entity.getCreatedAt(), entity.getExpiresAt());
    }
}
