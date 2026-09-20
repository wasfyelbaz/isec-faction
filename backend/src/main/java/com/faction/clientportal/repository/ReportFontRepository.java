package com.faction.clientportal.repository;

import com.faction.clientportal.model.ReportFont;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportFontRepository extends JpaRepository<ReportFont, String> {

    List<ReportFont> findAllByOrderByFamilyAscStyleAsc();

    /**
     * Case-insensitive, because fontconfig matches family names that way too: a second "calibri"
     * would not be a second font to LibreOffice, only to this table.
     */
    Optional<ReportFont> findByFamilyIgnoreCaseAndStyleIgnoreCase(String family, String style);
}
