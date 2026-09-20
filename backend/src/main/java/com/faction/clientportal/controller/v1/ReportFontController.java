package com.faction.clientportal.controller.v1;

import com.faction.clientportal.dto.ReportFontDto;
import com.faction.clientportal.dto.common.JsonApiResponse;
import com.faction.clientportal.model.Permission;
import com.faction.clientportal.security.RequiresPermission;
import com.faction.clientportal.service.ReportFontService;
import com.faction.clientportal.util.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * The fonts installed on the server for the PDF step, managed from the Report Designer.
 *
 * <p>Gated like the report templates they serve: anyone who may read templates may see which fonts
 * exist, anyone who may edit templates may add or remove one. The bytes are never served back —
 * nothing in the interface needs them, and a font file is often licensed to this install alone.
 */
@RestController
@RequestMapping("/api/v1/report-fonts")
@RequiredArgsConstructor
@Tag(name = "Report Fonts", description = "Fonts installed on the server for PDF generation")
@SecurityRequirement(name = "bearerAuth")
public class ReportFontController {

    private final ReportFontService reportFontService;

    @GetMapping
    @RequiresPermission(Permission.REPORT_TEMPLATES_READ_ALL)
    @Operation(
        summary = "List the uploaded report fonts",
        description = "One entry per uploaded file, with the family and style the font declares.",
        responses = {@ApiResponse(responseCode = "200", description = "Fonts retrieved successfully")}
    )
    public ResponseEntity<JsonApiResponse<List<ReportFontDto>>> list() {
        return ResponseUtil.success("Fonts retrieved successfully", reportFontService.list());
    }

    @GetMapping("/installed")
    @RequiresPermission(Permission.REPORT_TEMPLATES_READ_ALL)
    @Operation(
        summary = "List every font family the server can render PDFs in",
        description = "Bundled and uploaded fonts alike, as fontconfig reports them. A template's "
                + "Report Font that is not in this list is substituted in the PDF.",
        responses = {@ApiResponse(responseCode = "200", description = "Families retrieved successfully")}
    )
    public ResponseEntity<JsonApiResponse<List<String>>> installed() {
        return ResponseUtil.success("Families retrieved successfully", reportFontService.installedFamilies());
    }

    @PostMapping(consumes = "multipart/form-data")
    @RequiresPermission(Permission.REPORT_TEMPLATES_EDIT_ALL)
    @Operation(
        summary = "Upload and install one or more font files",
        description = "TrueType or OpenType files (.ttf, .otf), up to 25 MB each. The family and "
                + "style are read from each file; uploading a style that already exists replaces it. "
                + "The fonts are installed at once and the report engine is restarted so they apply "
                + "to the next report.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Fonts uploaded and installed"),
            @ApiResponse(responseCode = "400", description = "Missing file, not a font, a font "
                    + "collection, or too large"),
        }
    )
    public ResponseEntity<JsonApiResponse<List<ReportFontDto>>> upload(
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) {
        return ResponseUtil.success("Fonts uploaded and installed",
                reportFontService.upload(files, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permission.REPORT_TEMPLATES_EDIT_ALL)
    @Operation(
        summary = "Delete an uploaded font",
        description = "Removes the stored file and uninstalls it from the server.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Font deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Font not found"),
        }
    )
    public ResponseEntity<JsonApiResponse<Void>> delete(@PathVariable String id) {
        reportFontService.delete(id);
        return ResponseUtil.success("Font deleted successfully");
    }
}
