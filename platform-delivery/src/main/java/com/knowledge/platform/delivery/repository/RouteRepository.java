package com.knowledge.platform.delivery.repository;

import com.knowledge.platform.delivery.model.dto.RouteTarget;
import com.knowledge.platform.delivery.model.entity.Route;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for materialized public routes. */
public interface RouteRepository extends JpaRepository<Route, String> {

    List<Route> findByTargetTypeAndTargetId(RouteTarget.TargetType targetType, UUID targetId);

    Optional<Route> findByTargetTypeAndTargetSlug(RouteTarget.TargetType targetType, String targetSlug);

    /**
     * Removes an entity's routes before they are rewritten.
     *
     * <p>Necessary rather than merely tidy: a renamed article's old path must stop resolving, and an
     * upsert keyed on path alone would leave the previous path pointing at it forever.
     */
    @Modifying
    @Query("delete from Route r where r.targetType = :targetType and r.targetId = :targetId")
    int deleteByTarget(
            @Param("targetType") RouteTarget.TargetType targetType, @Param("targetId") UUID targetId);
}
