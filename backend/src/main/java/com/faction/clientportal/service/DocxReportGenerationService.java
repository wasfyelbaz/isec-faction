package com.faction.clientportal.service;

import com.faction.clientportal.exception.BusinessRuleException;
import com.faction.clientportal.dto.AssessmentDto;
import com.faction.clientportal.edition.EditionPolicy;
import com.faction.clientportal.edition.Feature;
import com.faction.clientportal.exception.ResourceNotFoundException;
import com.faction.clientportal.model.*;
import com.faction.clientportal.repository.*;
import com.faction.clientportal.service.reporting.ChecklistTableRenderer;
import com.faction.clientportal.service.reporting.SeverityBarChartRenderer;
import com.faction.clientportal.util.LibreOfficeConverter;
import com.faction.clientportal.util.reporting.DocxUtils;
import com.faction.clientportal.util.reporting.ReportData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import com.sun.star.beans.PropertyValue;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.frame.XController;
import com.sun.star.frame.XDispatchHelper;
import com.sun.star.frame.XDispatchProvider;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.lang.XComponent;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.XCloseable;
import com.sun.star.frame.XStorable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements report generation using docx4j template processing.
 *
 * <p>Ships in the core: producing a report is what the open source edition is for.
 * Only the password-protected variant is paid, and that goes through
 * {@link ReportEncryptor}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Load assessment + vulnerabilities + related data from MongoDB</li>
 *   <li>Download the DOCX template from MinIO</li>
 *   <li>Build a {@link ReportData} container</li>
 *   <li>Run {@link DocxUtils#generateDocx} to fill the template</li>
 *   <li>Upload the resulting DOCX to MinIO</li>
 *   <li>Store the MinIO key in {@code assessment.generatedReportFileId}</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocxReportGenerationService implements ReportGenerationService {

    private final AssessmentRepository          assessmentRepository;
    private final EditionPolicy                 editionPolicy;
    private final ReportTemplateRepository      reportTemplateRepository;
    private final VulnerabilityRepository       vulnerabilityRepository;
    private final com.faction.clientportal.repository.VulnerabilityStageCompletionRepository stageCompletionRepository;
    private final UserRepository                userRepository;
    private final AssessmentTypeRepository      assessmentTypeRepository;
    private final VulnerabilityCategoryRepository vulnCategoryRepository;
    private final InlineImageRepository         inlineImageRepository;
    private final StorageService                storageService;
    private final ReportDocumentService         reportDocumentService;
    private final LibreOfficeConverter          libreOfficeConverter;
    private final LibreOfficeServerManager      libreOfficeServer;
    private final ReportEncryptor               reportEncryptor;
    private final ChecklistTableRenderer        checklistTableRenderer;
    private final SeverityBarChartRenderer      severityBarChartRenderer;
    private final TerminologyConfigService      terminologyConfigService;
    private final OrganizationRepository        organizationRepository;
    private final EntityFieldConfigRepository   entityFieldConfigRepository;
    private final ClientImageRepository         clientImageRepository;
    private final AssessmentChecklistRepository assessmentChecklistRepository;
    private final RetestReportService           retestReportService;
    private final NotificationService           notificationService;

    /**
     * The order findings appear in a report: the assessment's display order, exactly as the
     * assessment screen shows it.
     *
     * <p>Severity is not a sort key here because it is already built into the display order: a
     * new finding is placed at the end of its severity group when it is created
     * ({@code VulnerabilityService.placeInSeverityGroup}), and existing data was renumbered the
     * same way by migration. That leaves the tester free to move a finding by hand — including,
     * rarely, a High up among the Criticals — and have the report follow, which a severity-first
     * sort here would silently undo. Id breaks the (now unexpected) tie so the order is stable.
     */
    private static final Comparator<Vulnerability> REPORT_ORDER =
            Comparator.<Vulnerability>comparingInt(v -> v.getOrder() == null ? 0 : v.getOrder())
                    .thenComparing(Vulnerability::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final String REPORT_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    // ── ReportGenerationService ──────────────────────────────────────────────

    @Override
    public AssessmentDto generateReport(String assessmentId, String userId) {
        log.info("Starting report generation for assessment {} by user {}", assessmentId, userId);

        // 1. Load assessment
        Assessment assessment = assessmentRepository.findByIdAndDeletedAtIsNull(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assessment not found: " + assessmentId));

        // The snapshot on the assessment can be older than the template it came from, so
        // re-read the live styling before anything reads it — see applyLiveTemplateStyling.
        // The template also configures the in-process checklist tables and charts (renderDocx).
        ReportTemplate reportTemplate =
                ReportTemplateResolution.forReport(reportTemplateRepository, assessment).orElse(null);
        applyLiveTemplateStyling(assessment, reportTemplate);

        if (assessment.getTemplateFileId() == null) {
            throw new BusinessRuleException(
                    "The report template has no DOCX file attached. "
                    + "Upload a DOCX template in the report designer first.");
        }

        // 2–10. Download the template and fill it
        byte[] reportBytes = renderDocx(assessment, reportTemplate, assessment.getTemplateFileId(), Map.of());

        // 11. Upload to MinIO
        long   runTimestamp = System.currentTimeMillis();
        String reportKey    = buildReportKey(assessmentId, runTimestamp, "docx");
        storageService.uploadBytes(reportKey, reportBytes, REPORT_CONTENT_TYPE);
        log.info("Uploaded report for assessment {} to key: {}", assessmentId, reportKey);

        // 12. Update assessment with report metadata
        assessment.setGeneratedReportFileId(reportKey);
        assessment.setReportGeneratedAt(LocalDateTime.now());
        assessment.setLastUpdatedBy(userId);
        assessment.setUpdatedAt(LocalDateTime.now());
        assessmentRepository.save(assessment);

        // DOCX is ready — mark it downloadable before the slower PDF stages run
        reportDocumentService.markCompleted(assessmentId, ReportDocumentType.DOCX, reportKey);

        // 13. Convert to PDF, then produce the password-protected variant
        generatePdfVariants(assessment, reportBytes, runTimestamp, false);

        log.info("Report generation complete for assessment {}", assessmentId);

        // Return a minimal DTO with just the updated report fields
        return AssessmentDto.builder()
                .id(assessment.getId())
                .generatedReportFileId(reportKey)
                .reportGeneratedAt(assessment.getReportGeneratedAt())
                .build();
    }

    /**
     * Loads everything the report shows about the assessment and fills the DOCX template stored
     * at {@code templateFileId} with it, returning the populated DOCX. Shared by the main and the
     * retest report.
     *
     * @param retests vulnerability id to the retest whose result fills that finding's
     *                {@code ${retest*}} variables; empty for the main report
     */
    private byte[] renderDocx(Assessment assessment, ReportTemplate reportTemplate, String templateFileId,
                              Map<String, Retest> retests) {
        String assessmentId = assessment.getId();

        // 2. Load vulnerabilities, most severe first
        List<Vulnerability> vulns = vulnerabilityRepository
                .findByAssessmentIdAndDeletedAtIsNull(assessmentId)
                .stream()
                .sorted(REPORT_ORDER)
                .collect(Collectors.toList());

        // 3. Load assessors
        List<User> assessors = loadUsers(assessment.getAssessorIds());

        // 4. Load remediation manager
        User remediationManager = assessment.getRemediationManagerId() != null
                ? userRepository.findById(assessment.getRemediationManagerId()).orElse(null)
                : null;

        // 5. Look up assessment-type name
        String assessmentTypeName = assessment.getAssessmentTypeId() != null
                ? assessmentTypeRepository.findById(assessment.getAssessmentTypeId())
                        .map(AssessmentType::getName).orElse("")
                : "";

        // 6. Build category lookup map
        Map<String, String> categoryNames = buildCategoryNames(vulns);

        // 7. Load inline images for all rich-text content
        Map<String, byte[]>   imageBytes        = new HashMap<>();
        Map<String, String>   imageContentTypes = new HashMap<>();
        loadInlineImages(assessmentId, imageBytes, imageContentTypes);

        // 8. Download template DOCX from MinIO
        byte[] templateBytes = storageService.downloadBytes(templateFileId);

        // 9. Build ReportData
        ReportData reportData = buildReportData(
                assessment, assessors, remediationManager, assessmentTypeName,
                vulns, categoryNames, imageBytes, imageContentTypes);
        applyRetests(reportData, retests);

        // 9b. A resolver for the placeholders rendered in-process — the checklist tables
        //     and the severity bar chart
        DocxUtils.TokenResolver renderedTokens =
                reportTokenResolver(reportTemplate, assessment.getId(), vulns);

        // 10. Generate the populated DOCX
        return generateDocxBytes(templateBytes, reportData,
                assessment.getTemplateCss() == null ? "" : assessment.getTemplateCss(),
                assessment.getTemplateFont(), renderedTokens);
    }

    /**
     * Fills each finding's {@code ${retestEvidence}}, {@code ${retestResult}},
     * {@code ${retestDate}} and {@code ${retestedBy}} from its retest in {@code retests}.
     * A finding with none keeps them blank.
     */
    private void applyRetests(ReportData reportData, Map<String, Retest> retests) {
        if (retests.isEmpty() || reportData.getVulnerabilities() == null) return;
        Map<String, String> displayNames = new HashMap<>();
        for (ReportData.ReportVulnerability vuln : reportData.getVulnerabilities()) {
            Retest retest = retests.get(vuln.getId());
            if (retest == null) continue;
            vuln.setRetestEvidence(retest.getEvidence());
            vuln.setRetestResult("PASSED".equals(retest.getStatus()) ? "Passed" : "Failed");
            vuln.setRetestDate(retest.getClosedDate());
            String tester = retest.getCompletedBy();
            vuln.setRetestedBy(tester == null ? null
                    : displayNames.computeIfAbsent(tester, this::displayName));
        }
    }

    /** A user's full name, or the username itself when the user is gone or has no name. */
    private String displayName(String username) {
        return userRepository.findByUsername(username)
                .map(u -> ((u.getFirstName() == null ? "" : u.getFirstName())
                        + " " + (u.getLastName() == null ? "" : u.getLastName())).trim())
                .filter(name -> !name.isEmpty())
                .orElse(username);
    }

    /**
     * Produces the PDF and encrypted-PDF variants of an already generated DOCX.
     * Each stage records its own success/failure so the Finalize panel can show
     * per-document state; a PDF failure also fails the encrypted variant since
     * it can't be produced without the plain PDF.
     */
    private void generatePdfVariants(Assessment assessment, byte[] docxBytes, long runTimestamp,
                                     boolean retest) {
        String assessmentId = assessment.getId();
        ReportDocumentType pdfType = retest ? ReportDocumentType.RETEST_PDF : ReportDocumentType.PDF;
        ReportDocumentType encryptedType =
                retest ? ReportDocumentType.RETEST_ENCRYPTED_PDF : ReportDocumentType.ENCRYPTED_PDF;

        byte[] pdfBytes;
        try {
            pdfBytes = libreOfficeConverter.convertToPdf(docxBytes);
            String pdfKey = buildReportKey(assessmentId, runTimestamp, "pdf", retest);
            storageService.uploadBytes(pdfKey, pdfBytes, PDF_CONTENT_TYPE);
            reportDocumentService.markCompleted(assessmentId, pdfType, pdfKey);
            log.info("Uploaded PDF report for assessment {} to key: {}", assessmentId, pdfKey);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("PDF conversion failed for assessment {}: {}", assessmentId, e.getMessage(), e);
            reportDocumentService.markFailed(assessmentId, pdfType,
                    "PDF conversion failed: " + e.getMessage());
            reportDocumentService.markFailed(assessmentId, encryptedType,
                    "Skipped — PDF conversion failed");
            return;
        }

        encryptAndStorePdf(assessment, pdfBytes, runTimestamp, retest);
    }

    /**
     * Encrypts an already-produced PDF and stores it as the ENCRYPTED_PDF
     * variant. Shared by the DOCX-driven generation flow and by a direct
     * PDF upload, which skips straight to this step.
     */
    private void encryptAndStorePdf(Assessment assessment, byte[] pdfBytes, long runTimestamp,
                                    boolean retest) {
        String assessmentId = assessment.getId();
        ReportDocumentType encryptedType =
                retest ? ReportDocumentType.RETEST_ENCRYPTED_PDF : ReportDocumentType.ENCRYPTED_PDF;

        // Skipped outright in the open source edition rather than left to fail. The DOCX
        // and plain PDF are the deliverable there, and marking the encrypted variant
        // "failed" would report a broken report run for a document that was never coming.
        if (!editionPolicy.enabled(Feature.ENCRYPTED_PDF)) {
            return;
        }

        try {
            String password  = reportDocumentService.ensureReportPassword(assessment);
            byte[] encrypted = reportEncryptor.encrypt(pdfBytes, password);
            String encryptedKey = String.format("reports/%s/%s-%d-encrypted.pdf",
                    assessmentId, retest ? "retest" : "report", runTimestamp);
            storageService.uploadBytes(encryptedKey, encrypted, PDF_CONTENT_TYPE);
            reportDocumentService.markCompleted(assessmentId, encryptedType, encryptedKey);
            log.info("Uploaded encrypted PDF report for assessment {} to key: {}", assessmentId, encryptedKey);
        } catch (Exception e) {
            log.error("Encrypted PDF generation failed for assessment {}: {}",
                    assessmentId, e.getMessage(), e);
            reportDocumentService.markFailed(assessmentId, encryptedType,
                    "Encrypted PDF generation failed: " + e.getMessage());
        }
    }

    /**
     * Points the assessment at its template's current styling — CSS, font, and the DOCX file.
     *
     * <p>An assessment snapshots those when it is created, and the snapshot was otherwise only
     * refreshed when the assessment happened to be loaded through {@code AssessmentService}.
     * Editing the CSS in the report designer and generating straight afterwards therefore
     * produced a report in whichever CSS the assessment was still carrying, while the same edit
     * appeared to take effect as soon as anything reloaded that assessment — which is what made
     * it look like the designer had not saved.
     *
     * <p>Styling always tracks the live template (only field definitions are version-gated, so
     * that existing field values keep their keys), so it is re-read here: the point that decides
     * what the report actually looks like. A null on the template means "not set", and leaves
     * the snapshot alone rather than clearing it.
     */
    static void applyLiveTemplateStyling(Assessment assessment, ReportTemplate template) {
        if (template == null) return;
        if (template.getTemplateFileId() != null) assessment.setTemplateFileId(template.getTemplateFileId());
        if (template.getCss() != null) assessment.setTemplateCss(template.getCss());
        if (template.getFont() != null) assessment.setTemplateFont(template.getFont());
        if (template.getReportPalette() != null) {
            // Copied, not shared: the template entity is managed, and handing its palette to the
            // assessment would let a later edit on either mutate the other.
            assessment.setTemplatePalette(template.getReportPalette().copy());
        }
    }

    // ── ReportGenerationService — retest report ──────────────────────────────

    @Override
    public void generateRetestReport(String assessmentId, String userId) {
        // Taken before anything is read: a retest that finishes while this runs is not in this
        // report, so it must still count as new against the stamp recorded below.
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("Starting retest report generation for assessment {} by user {}", assessmentId, userId);

        Assessment assessment = assessmentRepository.findByIdAndDeletedAtIsNull(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assessment not found: " + assessmentId));
        ReportTemplate template =
                ReportTemplateResolution.forReport(reportTemplateRepository, assessment).orElse(null);
        applyLiveTemplateStyling(assessment, template);
        if (template == null || template.getRetestTemplateFileId() == null) {
            throw new BusinessRuleException("The report template has no retest template attached. "
                    + "Upload a retest template in the report designer first.");
        }

        Map<String, Retest> retests = retestReportService.latestCompletedByVulnerability(assessmentId);
        byte[] reportBytes = renderDocx(assessment, template, template.getRetestTemplateFileId(), retests);

        // The main report's generatedReportFileId / reportGeneratedAt are deliberately left alone
        long   runTimestamp = System.currentTimeMillis();
        String reportKey    = buildReportKey(assessmentId, runTimestamp, "docx", true);
        storageService.uploadBytes(reportKey, reportBytes, REPORT_CONTENT_TYPE);
        reportDocumentService.markCompleted(assessmentId, ReportDocumentType.RETEST_DOCX, reportKey);
        log.info("Uploaded retest report for assessment {} to key: {}", assessmentId, reportKey);

        retestReportService.recordGeneration(assessmentId, startedAt,
                retests.values().stream().map(Retest::getId).collect(Collectors.toSet()));
        // recordGeneration saved its own copy. Mirror the stamp onto this in-memory entity too:
        // the encrypted-PDF step's ensureReportPassword saves it when it mints the first report
        // password, and a stale null stamp there would revert the one just recorded.
        assessment.setRetestReportGeneratedAt(startedAt);

        generatePdfVariants(assessment, reportBytes, runTimestamp, true);
        notifyRetestReportReady(assessment, userId);
        log.info("Retest report generation complete for assessment {}", assessmentId);
    }

    /** Tells the user who asked for the retest report that it can be downloaded. Never fails the run. */
    private void notifyRetestReportReady(Assessment assessment, String userId) {
        try {
            notificationService.send(userId, "Retest report ready",
                    "The retest report for " + assessment.getName() + " is ready to download.",
                    "RETEST_REPORT", "/assessments/" + assessment.getId());
        } catch (Exception e) {
            log.warn("Could not notify {} that the retest report for assessment {} is ready: {}",
                    userId, assessment.getId(), e.getMessage());
        }
    }

    // ── ReportGenerationService — uploaded report ────────────────────────────


    /**
     * Processes a manually uploaded DOCX or PDF report, replacing the
     * corresponding generated artifacts.
     *
     * <ul>
     *   <li>DOCX upload — stores it as the DOCX artifact, then converts it to
     *       PDF and encrypts that PDF, exactly like {@link #generateReport}.</li>
     *   <li>PDF upload — stores it as the PDF artifact directly (no
     *       conversion) and encrypts it; the DOCX artifact is left as-is.</li>
     * </ul>
     */
    @Override
    public void uploadReport(String assessmentId, byte[] fileBytes, ReportDocumentType uploadedType, String userId) {
        log.info("Processing uploaded {} report for assessment {} by user {}",
                uploadedType, assessmentId, userId);

        Assessment assessment = assessmentRepository.findByIdAndDeletedAtIsNull(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assessment not found: " + assessmentId));

        long runTimestamp = System.currentTimeMillis();

        assessment.setReportGeneratedAt(LocalDateTime.now());
        assessment.setLastUpdatedBy(userId);
        assessment.setUpdatedAt(LocalDateTime.now());

        switch (uploadedType) {
            case DOCX -> {
                String docxKey = buildReportKey(assessmentId, runTimestamp, "docx");
                storageService.uploadBytes(docxKey, fileBytes, REPORT_CONTENT_TYPE);
                assessment.setGeneratedReportFileId(docxKey);
                assessmentRepository.save(assessment);
                reportDocumentService.markCompleted(assessmentId, ReportDocumentType.DOCX, docxKey);
                log.info("Uploaded DOCX report for assessment {} to key: {}", assessmentId, docxKey);

                generatePdfVariants(assessment, fileBytes, runTimestamp, false);
            }
            case PDF -> {
                assessmentRepository.save(assessment);

                String pdfKey = buildReportKey(assessmentId, runTimestamp, "pdf");
                storageService.uploadBytes(pdfKey, fileBytes, PDF_CONTENT_TYPE);
                reportDocumentService.markCompleted(assessmentId, ReportDocumentType.PDF, pdfKey);
                log.info("Uploaded PDF report for assessment {} to key: {}", assessmentId, pdfKey);

                encryptAndStorePdf(assessment, fileBytes, runTimestamp, false);
            }
            default -> throw new IllegalArgumentException("Unsupported uploaded report type: " + uploadedType);
        }

        log.info("Report upload processing complete for assessment {}", assessmentId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<User> loadUsers(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return userIds.stream()
                .map(id -> userRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<String, String> buildCategoryNames(List<Vulnerability> vulns) {
        Set<String> categoryIds = vulns.stream()
                .map(Vulnerability::getVulnerabilityCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        for (String catId : categoryIds) {
            vulnCategoryRepository.findByIdAndDeletedAtIsNull(catId)
                    .ifPresent(cat -> names.put(catId, cat.getName()));
        }
        return names;
    }

    private void loadInlineImages(String assessmentId,
                                   Map<String, byte[]> imageBytes,
                                   Map<String, String> imageContentTypes) {
        List<InlineImage> images = inlineImageRepository.findByAssessmentId(assessmentId);
        for (InlineImage img : images) {
            try {
                byte[] bytes = storageService.downloadBytes(img.getStorageKey());
                imageBytes.put(img.getId(), bytes);
                imageContentTypes.put(img.getId(),
                        img.getContentType() != null ? img.getContentType() : "image/png");
            } catch (Exception e) {
                log.warn("Could not download inline image {} for assessment {}: {}",
                        img.getId(), assessmentId, e.getMessage());
            }
        }
    }

    private ReportData buildReportData(
            Assessment assessment,
            List<User> assessors,
            User remediationManager,
            String assessmentTypeName,
            List<Vulnerability> vulns,
            Map<String, String> categoryNames,
            Map<String, byte[]> imageBytes,
            Map<String, String> imageContentTypes) {

        // Assessment-level UDF maps (variableName → value / type)
        Map<String, String>    asmtFieldValues = new HashMap<>();
        Map<String, FieldType> asmtFieldTypes  = new HashMap<>();
        buildFieldMaps(assessment.getFieldDefinitions(), assessment.getFieldValues(),
                asmtFieldValues, asmtFieldTypes);

        // Vulnerabilities. The ${closedInDevAt}/${closedInStagingAt} template variables survive the
        // move to configurable remediation stages: they resolve from the completion events recorded
        // against the default "development"/"staging" stage ids, batch-fetched for the whole report.
        Map<String, Map<String, java.time.LocalDateTime>> stageDatesByVuln = new HashMap<>();
        List<String> vulnIds = vulns.stream().map(Vulnerability::getId).collect(Collectors.toList());
        if (!vulnIds.isEmpty()) {
            for (var completion : stageCompletionRepository.findByVulnerabilityIdIn(vulnIds)) {
                stageDatesByVuln
                        .computeIfAbsent(completion.getVulnerabilityId(), k -> new HashMap<>())
                        .put(completion.getStageId(), completion.getCompletedAt());
            }
        }
        List<ReportData.ReportVulnerability> reportVulns = vulns.stream()
                .map(v -> buildReportVuln(v, categoryNames,
                        stageDatesByVuln.getOrDefault(v.getId(), Map.of())))
                .collect(Collectors.toList());

        // Assessor DTOs
        List<ReportData.ReportUser> reportAssessors = assessors.stream()
                .map(u -> ReportData.ReportUser.builder()
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .email(u.getEmail())
                        .build())
                .collect(Collectors.toList());

        String remName = remediationManager != null
                ? ((remediationManager.getFirstName() == null ? "" : remediationManager.getFirstName())
                        + " " + (remediationManager.getLastName() == null ? "" : remediationManager.getLastName())).trim()
                : "";

        // The client: the organization the assessment belongs to, with its organization-scoped
        // custom fields flattened to variableName → value exactly like the assessment's own. An
        // assessment with no organization, or one whose organization is gone, simply has no client.
        String clientName = null;
        Map<String, String>    clientFieldValues = new HashMap<>();
        Map<String, FieldType> clientFieldTypes  = new HashMap<>();
        List<ReportData.ReportContact> clientContacts = new ArrayList<>();
        Map<String, byte[]>  clientImageBytes        = new HashMap<>();
        Map<String, String>  clientImageContentTypes = new HashMap<>();
        Organization client = assessment.getOrganizationId() == null ? null
                : organizationRepository.findById(assessment.getOrganizationId()).orElse(null);
        if (client != null) {
            clientName = client.getName();
            List<UserDefinedField> clientFieldDefinitions = entityFieldConfigRepository
                    .findByScope(FieldScope.ORGANIZATION)
                    .map(EntityFieldConfig::getFieldDefinitions)
                    .orElse(List.of());
            buildFieldMaps(clientFieldDefinitions, client.getFieldValues(), clientFieldValues, clientFieldTypes);
            if (client.getDistributionList() != null) {
                for (ClientContact contact : client.getDistributionList()) {
                    clientContacts.add(ReportData.ReportContact.builder()
                            .name(contact.getName()).title(contact.getTitle()).email(contact.getEmail())
                            .build());
                }
            }
            // Every image the client has, keyed by its slot name, so ${clientImage logo} can be
            // resolved without a second round trip. One unreadable object costs that image only.
            for (ClientImage image : clientImageRepository.findByOrganizationIdOrderByNameAsc(client.getId())) {
                try {
                    clientImageBytes.put(image.getName(), storageService.downloadBytes(image.getStorageKey()));
                    clientImageContentTypes.put(image.getName(),
                            image.getContentType() != null ? image.getContentType() : "image/png");
                } catch (Exception e) {
                    log.warn("Could not download client image '{}' for organization {}: {}",
                            image.getName(), client.getId(), e.getMessage());
                }
            }
        }

        // Checklist outcomes for the charts. ${chartData checklist} takes the totals, PASS / FAIL / NA
        // summed across every checklist attached; ${chartData checklist:<name>} takes one checklist's
        // own counts, keyed exactly as its ${checklist-<name>} table is.
        int checklistPassed = 0, checklistFailed = 0, checklistNotApplicable = 0;
        Map<String, ReportData.ChecklistCounts> checklistCountsByName = new HashMap<>();
        for (AssessmentChecklist checklist : assessmentChecklistRepository.findByAssessmentId(assessment.getId())) {
            if (checklist.getResponses() == null) continue;
            int passed = 0, failed = 0, notApplicable = 0;
            for (ChecklistResponse response : checklist.getResponses()) {
                if (response.getResult() == null) continue;
                switch (response.getResult()) {
                    case PASS -> passed++;
                    case FAIL -> failed++;
                    case NA -> notApplicable++;
                }
            }
            checklistPassed += passed;
            checklistFailed += failed;
            checklistNotApplicable += notApplicable;
            // Two checklists with the same title would share a key; add rather than overwrite, so
            // neither silently disappears from its chart.
            checklistCountsByName.merge(ChecklistTableRenderer.variableNameFor(checklist.getTemplateName()),
                    new ReportData.ChecklistCounts(passed, failed, notApplicable),
                    (a, b) -> new ReportData.ChecklistCounts(a.passed() + b.passed(), a.failed() + b.failed(),
                            a.notApplicable() + b.notApplicable()));
        }

        return ReportData.builder()
                .clientName(clientName)
                .checklistPassed(checklistPassed)
                .checklistFailed(checklistFailed)
                .checklistNotApplicable(checklistNotApplicable)
                .checklistCountsByName(checklistCountsByName)
                .clientFieldValues(clientFieldValues)
                .clientFieldTypes(clientFieldTypes)
                .clientContacts(clientContacts)
                .clientImageBytes(clientImageBytes)
                .clientImageContentTypes(clientImageContentTypes)
                .assessmentId(assessment.getId())
                .assessmentName(assessment.getName())
                .applicationId(assessment.getApplicationId())
                .startDate(assessment.getStartDate())
                .endDate(assessment.getPlannedEndDate())
                .scoringType(assessment.getScoringType())
                .remediationManagerName(remName)
                .assessmentTypeName(assessmentTypeName)
                .assessors(reportAssessors)
                .fieldValues(asmtFieldValues)
                .fieldTypes(asmtFieldTypes)
                .reportPalette(assessment.getTemplatePalette())
                .vulnerabilities(reportVulns)
                .sections(editionPolicy.enabled(Feature.REPORT_SECTIONS) && assessment.getSections() != null
                        ? new ArrayList<>(assessment.getSections())
                        : List.of())
                .inlineImageBytes(imageBytes)
                .inlineImageContentTypes(imageContentTypes)
                .build();
    }

    private ReportData.ReportVulnerability buildReportVuln(
            Vulnerability v, Map<String, String> categoryNames,
            Map<String, java.time.LocalDateTime> stageDates) {

        Map<String, String>    vFieldValues = new HashMap<>();
        Map<String, FieldType> vFieldTypes  = new HashMap<>();
        buildFieldMaps(v.getFieldDefinitions(), v.getFieldValues(), vFieldValues, vFieldTypes);

        return ReportData.ReportVulnerability.builder()
                .id(v.getId())
                .name(v.getName())
                .severity(severityDisplayName(v.getSeverity()))
                .severityKey(v.getSeverity() == null ? "" : v.getSeverity().name())
                // Likelihood and impact hold a severity by name, so they follow the same rename
                // as the severity itself; anything else passes through untouched.
                .likelihood(terminologyConfigService.severityLabelForName(v.getLikelihood()))
                .impact(terminologyConfigService.severityLabelForName(v.getImpact()))
                .cvssScore(v.getCvssScore())
                .cvssString(v.getCvssString())
                .assetLocation(v.getAssetLocation())
                .description(v.getDescription())
                .recommendation(v.getRecommendation())
                .details(v.getDetails())
                .trackingId(v.getTrackingId())
                .openedAt(v.getOpenedAt())
                .closedAt(v.getClosedAt())
                .closedInDevAt(stageDates.get("development"))
                .closedInStagingAt(stageDates.get("staging"))
                .categoryName(v.getVulnerabilityCategoryId() != null
                        ? categoryNames.get(v.getVulnerabilityCategoryId())
                        : null)
                .section(v.getSection())
                .checklistItems(checklistItemsByName(v))
                .fieldValues(vFieldValues)
                .fieldTypes(vFieldTypes)
                .build();
    }

    /** A finding's checklist items grouped by checklist, keyed the way the checklist tables are. */
    static Map<String, List<String>> checklistItemsByName(Vulnerability v) {
        Map<String, List<String>> byName = new java.util.LinkedHashMap<>();
        if (v.getChecklistItems() == null) return byName;
        for (var item : v.getChecklistItems()) {
            if (item == null || item.getQuestionText() == null) continue;
            byName.computeIfAbsent(ChecklistTableRenderer.variableNameFor(item.getChecklistName()),
                    k -> new ArrayList<>()).add(item.getQuestionText());
        }
        return byName;
    }

    /**
     * Converts the stored field definitions + values (keyed by field ID)
     * into variableName-keyed maps for DocxUtils.
     */
    private void buildFieldMaps(List<UserDefinedField> definitions,
                                 Map<String, String> storedValues,
                                 Map<String, String> outValues,
                                 Map<String, FieldType> outTypes) {
        if (definitions == null) return;
        for (UserDefinedField field : definitions) {
            String varName = field.getVariableName();
            if (varName == null) continue;
            // Values are keyed by field ID in some editors and by variableName
            // in others (the vulnerability editor) — accept either.
            String value = "";
            if (storedValues != null) {
                value = storedValues.getOrDefault(field.getId(),
                        storedValues.getOrDefault(varName, ""));
            }
            // Fall back to default value if no user input stored
            if (value.isEmpty() && field.getDefaultValue() != null) {
                value = field.getDefaultValue();
            }
            outValues.put(varName, value);
            outTypes.put(varName,
                    field.getFieldType() != null ? field.getFieldType() : FieldType.STRING);
        }
    }

    /**
     * Resolves the placeholders this application renders itself.
     *
     * <p>{@code ${checklist-<name>}} and {@code ${faction-bar-chart}} were both served by
     * App Store extensions until that mechanism was removed: installing one meant
     * uploading a JAR that the server then loaded and executed, which is remote code
     * execution with an approval step in front of it. Both are ordinary parts of a report,
     * so they are rendered in-process and configured per template instead.
     *
     * <p>Each renderer returns null for a token it does not own, so the first one that
     * claims it wins and an unclaimed placeholder is left alone rather than blanked.
     */
    private DocxUtils.TokenResolver reportTokenResolver(ReportTemplate template,
                                                        String assessmentId,
                                                        List<Vulnerability> vulns) {
        var checklistOptions = ChecklistTableRenderer.ChecklistRenderOptions.from(
                template == null ? null : template.getChecklistConfig());
        var chartOptions = SeverityBarChartRenderer.BarChartOptions.from(
                template == null ? null : template.getBarChartConfig());
        // Read once: a template may hold several checklist placeholders, and the resolver
        // is called for each one.
        List<AssessmentChecklist> checklists =
                assessmentChecklistRepository.findByAssessmentId(assessmentId);

        return renderedTokenResolver(checklistTableRenderer, checklists, checklistOptions,
                severityBarChartRenderer, vulns, chartOptions);
    }

    /**
     * The resolver itself, separated from the repository lookups so a test can run the real
     * renderers through a real document.
     *
     * <p>{@link DocxUtils} hands a resolver the <em>whole</em> placeholder, braces included —
     * {@code ${checklist-owasp-api-top-10}} — which is the shape the App Store extensions
     * matched on. The in-process renderers that replaced them take the bare name. Without the
     * translation here neither renderer ever recognised its own tag, and every checklist table
     * and severity chart printed into live reports as literal text, while the tests on each
     * side of this seam stayed green because each tested against a stand-in for the other.
     */
    static DocxUtils.TokenResolver renderedTokenResolver(
            ChecklistTableRenderer checklistRenderer,
            List<AssessmentChecklist> checklists,
            ChecklistTableRenderer.ChecklistRenderOptions checklistOptions,
            SeverityBarChartRenderer chartRenderer,
            List<Vulnerability> vulns,
            SeverityBarChartRenderer.BarChartOptions chartOptions) {
        return token -> {
            String name = placeholderName(token);
            String checklist = checklistRenderer.render(name, checklists, checklistOptions);
            if (checklist != null) return checklist;
            return chartRenderer.render(name, vulns, chartOptions);
        };
    }

    private static final java.util.regex.Pattern PLACEHOLDER =
            java.util.regex.Pattern.compile("^\\$\\{(.+)}$");

    /** {@code ${checklist-x}} → {@code checklist-x}; a name already bare is returned as is. */
    static String placeholderName(String token) {
        if (token == null) return null;
        String trimmed = token.trim();
        java.util.regex.Matcher m = PLACEHOLDER.matcher(trimmed);
        return m.matches() ? m.group(1).trim() : trimmed;
    }

    /**
     * This installation's word for the severity — "Critical" unless the terminology config renames
     * it. Only what a reader sees; {@code ReportVulnerability.severityKey} carries the enum name
     * that the template tokens match on.
     */
    private String severityDisplayName(VulnerabilitySeverity severity) {
        return terminologyConfigService.severityLabel(severity);
    }

    private byte[] generateDocxBytes(byte[] templateBytes, ReportData data,
                                      String customCss, String font,
                                      DocxUtils.TokenResolver tokenResolver) {
        try {
            WordprocessingMLPackage mlp = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(templateBytes));
            DocxUtils utils = new DocxUtils(mlp, data);
            if (font != null && !font.isBlank()) {
                utils.FONT = font.trim();
            }
            WordprocessingMLPackage populated = utils.generateDocx(customCss, tokenResolver);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            populated.save(baos);
            return refreshTocWithLibreOffice(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate DOCX report: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the DOCX into a running LibreOffice server via the Java UNO connection pool,
     * dispatches {@code .uno:UpdateAllIndexes} to recalculate TOC page numbers, then saves
     * in place. Falls back to the original bytes if LibreOffice is unavailable.
     */
    private byte[] refreshTocWithLibreOffice(byte[] docxBytes) {
        File tempFile = null;
        File outFile  = null;
        XComponent xDoc = null;
        LibreOfficeConnectionPool.PooledConnection pooledConn = null;
        LibreOfficeConnectionPool pool = LibreOfficeConnectionPool.getInstance();
        libreOfficeServer.ensureRunning();

        try {
            tempFile = File.createTempFile("report-toc-", ".docx");
            Files.write(tempFile.toPath(), docxBytes);

            pooledConn = pool.borrowConnection();
            log.debug("Borrowed LibreOffice connection from pool. {}", pool.getPoolStats());

            XComponentLoader loader = UnoRuntime.queryInterface(
                    XComponentLoader.class, pooledConn.getDesktop());

            String docUrl = "file:///" + tempFile.getAbsolutePath().replace("\\", "/");
            xDoc = loader.loadComponentFromURL(docUrl, "_blank", 0, new PropertyValue[0]);

            if (xDoc == null) {
                log.warn("LibreOffice failed to open DOCX — falling back to CLI round-trip");
                pool.invalidateConnection(pooledConn);
                pooledConn = null;
                return normalizeDocxViaCli(docxBytes);
            }

            // Dispatch UpdateAllIndexes to recalculate TOC page numbers
            XModel      xModel      = UnoRuntime.queryInterface(XModel.class, xDoc);
            XController xController = xModel.getCurrentController();
            XFrame      xFrame      = xController.getFrame();

            XDispatchHelper dispatchHelper = UnoRuntime.queryInterface(
                    XDispatchHelper.class,
                    pooledConn.getContext().getServiceManager()
                              .createInstanceWithContext(
                                      "com.sun.star.frame.DispatchHelper",
                                      pooledConn.getContext()));

            XDispatchProvider dispatchProvider =
                    UnoRuntime.queryInterface(XDispatchProvider.class, xFrame);

            dispatchHelper.executeDispatch(
                    dispatchProvider, ".uno:UpdateAllIndexes", "", 0, new PropertyValue[0]);
            log.debug("Dispatched UpdateAllIndexes to LibreOffice");

            // Save, naming the filter. A plain store() reuses the filter the file was
            // detected as, "MS Word 2007 XML", which stamps compatibilityMode 12 into the
            // settings whatever the template said: Word then opens every report in Compatibility
            // Mode and lays it out by Word 2007's rules, so text wraps differently from the
            // template it came from. DOCX_FILTER keeps the template's mode.
            XStorable xStorable = UnoRuntime.queryInterface(XStorable.class, xDoc);
            PropertyValue filter = new PropertyValue();
            filter.Name  = "FilterName";
            filter.Value = LibreOfficeConverter.DOCX_FILTER;
            PropertyValue overwrite = new PropertyValue();
            overwrite.Name  = "Overwrite";
            overwrite.Value = Boolean.TRUE;
            // createTempFile has already made the (empty) file the save goes to
            outFile = File.createTempFile("report-toc-out-", ".docx");
            String outUrl = "file:///" + outFile.getAbsolutePath().replace("\\", "/");
            xStorable.storeToURL(outUrl, new PropertyValue[] { filter, overwrite });

            byte[] refreshed = Files.readAllBytes(outFile.toPath());
            log.info("TOC page numbers refreshed via LibreOffice UNO connection pool");
            return refreshed;

        } catch (Exception e) {
            log.warn("LibreOffice TOC refresh failed: {} — falling back to CLI round-trip", e.getMessage());
            if (pooledConn != null) {
                pool.invalidateConnection(pooledConn);
                pooledConn = null;
            }
            return normalizeDocxViaCli(docxBytes);
        } finally {
            if (xDoc != null) {
                try {
                    XCloseable xCloseable = UnoRuntime.queryInterface(XCloseable.class, xDoc);
                    if (xCloseable != null) xCloseable.close(true);
                    else                   xDoc.dispose();
                } catch (Exception ignored) {}
            }
            if (pooledConn != null) {
                pool.returnConnection(pooledConn);
                log.debug("Returned LibreOffice connection to pool. {}", pool.getPoolStats());
            }
            if (tempFile != null) {
                tempFile.delete();
            }
            if (outFile != null) {
                outFile.delete();
            }
        }
    }

    /**
     * Normalizes raw docx4j output by round-tripping it through the LibreOffice
     * CLI (docx → docx). Raw docx4j output contains constructs Microsoft Word
     * rejects as corrupt; a LibreOffice re-save produces a spec-compliant file.
     * Used when the UNO pool (which re-saves as part of the TOC refresh) is
     * unavailable. TOC page numbers are not refreshed on this path.
     */
    private byte[] normalizeDocxViaCli(byte[] docxBytes) {
        try {
            byte[] normalized = libreOfficeConverter.convertToDocx(docxBytes);
            log.info("DOCX normalized via LibreOffice CLI round-trip");
            return normalized;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("LibreOffice CLI round-trip failed: {} — returning raw DOCX "
                    + "(may not open in Microsoft Word)", e.getMessage());
            return docxBytes;
        }
    }

    private String buildReportKey(String assessmentId, long timestamp, String extension) {
        return buildReportKey(assessmentId, timestamp, extension, false);
    }

    private String buildReportKey(String assessmentId, long timestamp, String extension, boolean retest) {
        return String.format("reports/%s/%s-%d.%s", assessmentId, retest ? "retest" : "report",
                timestamp, extension);
    }
}
