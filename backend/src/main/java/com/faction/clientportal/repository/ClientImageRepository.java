package com.faction.clientportal.repository;

import com.faction.clientportal.model.ClientImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientImageRepository extends JpaRepository<ClientImage, String> {

    List<ClientImage> findByOrganizationIdOrderByNameAsc(String organizationId);

    /** Slot names are unique within an organization, not globally. */
    Optional<ClientImage> findByOrganizationIdAndName(String organizationId, String name);
}
