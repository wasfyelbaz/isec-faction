package com.faction.clientportal.repository;

import com.faction.clientportal.model.ChecklistTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, String> {

    List<ChecklistTemplate> findByAssessmentTypeIdAndActiveTrue(String assessmentTypeId);

    /**
     * Whether this assessment type already has a template under this name — active or not.
     * The seeded Web checklists key off this, so a template an admin deactivated still counts
     * as present and is not silently recreated on the next restart.
     */
    boolean existsByAssessmentTypeIdAndName(String assessmentTypeId, String name);

    List<ChecklistTemplate> findAllByOrderByCreatedAtDesc();
}
