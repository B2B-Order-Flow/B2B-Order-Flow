package com.b2borderflow.core.request;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferRepository extends JpaRepository<ServiceOffer, UUID> {

    List<ServiceOffer> findByRequestIdOrderByPositionAsc(UUID requestId);

    List<ServiceOffer> findByRequestIdInOrderByRequestIdAscPositionAsc(Collection<UUID> requestIds);

    Optional<ServiceOffer> findFirstByRequestIdAndStatus(UUID requestId, OfferStatus status);
}
