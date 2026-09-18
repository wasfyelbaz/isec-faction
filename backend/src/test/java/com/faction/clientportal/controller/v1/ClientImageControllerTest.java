package com.faction.clientportal.controller.v1;

import com.faction.clientportal.config.TestContainersConfig;
import com.faction.clientportal.model.LoginOption;
import com.faction.clientportal.model.Organization;
import com.faction.clientportal.model.Role;
import com.faction.clientportal.model.User;
import com.faction.clientportal.repository.ApplicationRepository;
import com.faction.clientportal.repository.ClientImageRepository;
import com.faction.clientportal.repository.OrganizationRepository;
import com.faction.clientportal.repository.RoleRepository;
import com.faction.clientportal.repository.UserRepository;
import com.faction.clientportal.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The client-image endpoints, and the gates on them: reading takes any organization read scope,
 * writing takes the organization edit permission — anyone who may change a client's details may
 * change its logo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientImageControllerTest extends TestContainersConfig {

    /** A one-pixel PNG — real bytes, so the streamed response can be compared to them. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private ClientImageRepository clientImageRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User superAdminUser;
    private String orgId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
        applicationRepository.deleteAll();
        clientImageRepository.deleteAll();
        organizationRepository.deleteAll();

        Role superAdminRole = roleRepository.save(Role.builder()
                .name("SuperAdmin").description("Super Administrator with full access")
                .permissions(List.of("super_admin")).build());
        superAdminUser = userRepository.save(User.builder()
                .username("superadmin").password(passwordEncoder.encode("password"))
                .loginOption(LoginOption.NATIVE).roleIds(List.of(superAdminRole.getId()))
                .isInternal(true).createdAt(LocalDateTime.now()).failedLoginAttempts(0).build());

        orgId = organizationRepository.save(Organization.builder().name("Acme").build()).getId();
    }

    private String adminToken() {
        return jwtService.generateToken(superAdminUser.getUsername(),
                List.of(new SimpleGrantedAuthority("super_admin")));
    }

    /** A token carrying exactly the listed authorities, for the permission-gate tests. */
    private String tokenWith(String... authorities) {
        return jwtService.generateToken(superAdminUser.getUsername(),
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static MockMultipartFile png(String fileName) {
        return new MockMultipartFile("file", fileName, "image/png", PNG);
    }

    private void uploadLogo() throws Exception {
        mockMvc.perform(multipart("/api/v1/organizations/{id}/images", orgId)
                        .file(png("acme.png")).param("name", "logo")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void uploadThenListThenFetchTheBytes() throws Exception {
        mockMvc.perform(multipart("/api/v1/organizations/{id}/images", orgId)
                        .file(png("acme.png")).param("name", "logo")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("logo"))
                .andExpect(jsonPath("$.data.originalFileName").value("acme.png"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.fileSize").value(PNG.length))
                // The storage key is never disclosed — the /content route is the only way in.
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());

        mockMvc.perform(get("/api/v1/organizations/{id}/images", orgId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("logo"));

        byte[] served = mockMvc.perform(get("/api/v1/organizations/{id}/images/logo/content", orgId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                // A PNG is on the inline allowlist, so it renders rather than downloading.
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PNG);
    }

    @Test
    void theNameDefaultsToLogoWhenNoneIsGiven() throws Exception {
        mockMvc.perform(multipart("/api/v1/organizations/{id}/images", orgId)
                        .file(png("acme.png"))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("logo"));
    }

    @Test
    void anUploadedSvgIsServedAsADownloadRatherThanRendered() throws Exception {
        // SVG is allowed in — a logo very often is one — but it is active content, so it must
        // never render in this origin. FileStreamResponse forces it to download.
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><rect width='1' height='1'/></svg>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/v1/organizations/{id}/images", orgId)
                        .file(new MockMultipartFile("file", "acme.svg", "image/svg+xml", svg))
                        .param("name", "logo")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/organizations/{id}/images/logo/content", orgId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void deletingASlotEmptiesIt() throws Exception {
        uploadLogo();

        mockMvc.perform(delete("/api/v1/organizations/{id}/images/logo", orgId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/organizations/{id}/images", orgId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        assertThat(clientImageRepository.findByOrganizationIdAndName(orgId, "logo")).isEmpty();
    }

    @Test
    void anUnknownSlotOrOrganizationIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{id}/images/logo/content", orgId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/organizations/{id}/images", "no-such-org")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/organizations/{id}/images/logo", orgId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnsupportedTypeOrBadSlotNameIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/v1/organizations/{id}/images", orgId)
                        .file(new MockMultipartFile("file", "payload.pdf", "application/pdf", PNG))
                        .param("name", "logo")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/v1/organizations/{id}/images", orgId)
                        .file(png("acme.png")).param("name", "my logo")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());

        assertThat(clientImageRepository.findByOrganizationIdOrderByNameAsc(orgId)).isEmpty();
    }

    // ── Permission gates ──────────────────────────────────────────────────────────

    @Test
    void readingTakesAnyOrganizationReadScope() throws Exception {
        uploadLogo();

        for (String scope : new String[] {
                "organizations:read:all", "organizations:read:owned", "organizations:read:org"}) {
            mockMvc.perform(get("/api/v1/organizations/{id}/images", orgId)
                            .header("Authorization", "Bearer " + tokenWith(scope)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/organizations/{id}/images/logo/content", orgId)
                            .header("Authorization", "Bearer " + tokenWith(scope)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void aReadOnlyCallerCannotUploadOrDelete() throws Exception {
        uploadLogo();
        String readOnly = tokenWith("organizations:read:all");

        mockMvc.perform(multipart("/api/v1/organizations/{id}/images", orgId)
                        .file(png("sneaky.png")).param("name", "logo")
                        .header("Authorization", "Bearer " + readOnly))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/organizations/{id}/images/logo", orgId)
                        .header("Authorization", "Bearer " + readOnly))
                .andExpect(status().isForbidden());

        // Still the original file: a refused write must not have half-happened.
        assertThat(clientImageRepository.findByOrganizationIdAndName(orgId, "logo")
                .orElseThrow().getOriginalFileName()).isEqualTo("acme.png");
    }

    @Test
    void anOrganizationEditorCanUploadAndDelete() throws Exception {
        String editor = tokenWith("organizations:edit:all", "organizations:read:all");

        mockMvc.perform(multipart("/api/v1/organizations/{id}/images", orgId)
                        .file(png("acme.png")).param("name", "cover")
                        .header("Authorization", "Bearer " + editor))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/organizations/{id}/images/cover", orgId)
                        .header("Authorization", "Bearer " + editor))
                .andExpect(status().isOk());
    }

    @Test
    void aCallerWithNoOrganizationPermissionIsRefusedEverything() throws Exception {
        uploadLogo();
        String stranger = tokenWith("applications:read:all");

        mockMvc.perform(get("/api/v1/organizations/{id}/images", orgId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/organizations/{id}/images/logo/content", orgId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
    }

    @Test
    void theBytesAreNotServedWithoutAToken() throws Exception {
        uploadLogo();

        // Unlike an inline image, this route is not permit-all: these are a specific client's
        // marks, so the interface fetches them as an authenticated blob rather than via <img src>.
        // Forbidden rather than unauthorized is this application's answer to an anonymous request
        // on every protected route — see ReportControllerTest and the other …WithoutToken tests.
        mockMvc.perform(get("/api/v1/organizations/{id}/images/logo/content", orgId))
                .andExpect(status().isForbidden());
    }
}
