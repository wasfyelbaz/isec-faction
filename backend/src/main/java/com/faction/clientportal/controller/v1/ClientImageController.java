package com.faction.clientportal.controller.v1;

import com.faction.clientportal.dto.ClientImageDto;
import com.faction.clientportal.dto.common.JsonApiResponse;
import com.faction.clientportal.model.Permission;
import com.faction.clientportal.security.RequiresPermission;
import com.faction.clientportal.service.ClientImageService;
import com.faction.clientportal.service.StorageService;
import com.faction.clientportal.util.FileStreamResponse;
import com.faction.clientportal.util.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * A client's images, in named slots.
 *
 * <p>Nested under the organization because an image has no meaning without the client it belongs
 * to, and gated exactly as the organization itself is: reading takes any organization read scope,
 * writing takes the organization edit permission. Anyone who may change a client's details may
 * change its logo.
 *
 * <p>Unlike the inline-image route, serving the bytes is <em>not</em> permit-all. These are the
 * client's own marks, and the interface fetches them through the authenticated API client as a
 * blob rather than pointing an {@code <img>} tag at the endpoint.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/images")
@RequiredArgsConstructor
@Tag(name = "Client Images", description = "Logos and other images belonging to an organization")
@SecurityRequirement(name = "bearerAuth")
public class ClientImageController {

    private final ClientImageService clientImageService;

    @GetMapping
    @RequiresPermission({Permission.ORGANIZATIONS_READ_ALL, Permission.ORGANIZATIONS_READ_OWNED,
            Permission.ORGANIZATIONS_READ_ORG})
    @Operation(
        summary = "List an organization's images",
        description = "Returns one entry per filled slot, with its metadata. The bytes are served "
                + "separately, from the /content route.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Images retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
        }
    )
    public ResponseEntity<JsonApiResponse<List<ClientImageDto>>> list(
            @PathVariable String organizationId) {
        return ResponseUtil.success("Images retrieved successfully",
                clientImageService.list(organizationId));
    }

    @PostMapping(consumes = "multipart/form-data")
    @RequiresPermission(Permission.ORGANIZATIONS_EDIT_ALL)
    @Operation(
        summary = "Upload an organization image",
        description = "Stores the file in the named slot (logo, cover, signature…), replacing "
                + "whatever was there. PNG, JPEG, GIF, WebP or SVG, up to 5 MB.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Image uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Missing file, bad slot name, "
                    + "unsupported type or too large"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
        }
    )
    public ResponseEntity<JsonApiResponse<ClientImageDto>> upload(
            @PathVariable String organizationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", defaultValue = "logo") String name,
            Authentication authentication) {
        return ResponseUtil.success("Image uploaded successfully",
                clientImageService.upload(organizationId, name, file, authentication.getName()));
    }

    @GetMapping("/{name}/content")
    @RequiresPermission({Permission.ORGANIZATIONS_READ_ALL, Permission.ORGANIZATIONS_READ_OWNED,
            Permission.ORGANIZATIONS_READ_ORG})
    @Operation(
        summary = "Serve an organization image's bytes",
        description = "Streams the stored file. Requires an authenticated caller — the interface "
                + "fetches this as a blob rather than pointing an <img> tag at it.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Image streamed"),
            @ApiResponse(responseCode = "404", description = "Organization or slot not found"),
        }
    )
    public ResponseEntity<Resource> content(@PathVariable String organizationId,
            @PathVariable String name) {
        StorageService.StoredFile file = clientImageService.open(organizationId, name);
        // inlineImage, not attachment: a logo is meant to be rendered. Anything off its allowlist
        // — an SVG, for one — is downgraded to a download there, which is what stops an uploaded
        // SVG's script running in this origin.
        return FileStreamResponse.inlineImage(file.stream(), file.fileName());
    }

    @DeleteMapping("/{name}")
    @RequiresPermission(Permission.ORGANIZATIONS_EDIT_ALL)
    @Operation(
        summary = "Delete an organization image",
        description = "Empties the slot and removes the stored file.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Image deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Organization or slot not found"),
        }
    )
    public ResponseEntity<JsonApiResponse<Void>> delete(@PathVariable String organizationId,
            @PathVariable String name) {
        clientImageService.delete(organizationId, name);
        return ResponseUtil.success("Image deleted successfully");
    }
}
