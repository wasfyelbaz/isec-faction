package com.faction.clientportal.service.reporting;

import com.faction.clientportal.model.AssessmentChecklist;
import com.faction.clientportal.model.ChecklistResponse;
import com.faction.clientportal.model.ChecklistResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Renders an assessment's checklists as HTML tables for the DOCX report.
 *
 * <p>A template asks for one with {@code ${checklist-<name>}}, where the name is the
 * checklist's title lowercased with spaces replaced by hyphens — so "OWASP Mobile Top 10
 * Android" is written {@code ${checklist-owasp-mobile-top-10-android}}. That spelling is
 * kept from the App Store extension this replaces, so existing templates need no edits.
 *
 * <p>Unlike that extension, the tag takes no arguments: the published docs showed
 * {@code columns=[…]} but the extension's own matcher closed immediately after the name,
 * so any argument silently failed to match and printed the tag into the delivered report.
 * Columns are chosen by {@link ChecklistRenderOptions#showComments()} instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecklistTableRenderer {

    /** {@code ${checklist-} …} — the prefix a template placeholder starts with. */
    public static final String TOKEN_PREFIX = "checklist-";

    /**
     * The variable name a checklist answers to: its title, lowercased, spaces to hyphens.
     * Kept identical to the extension's derivation so templates carry over unchanged.
     */
    public static String variableNameFor(String checklistName) {
        return checklistName == null ? "" : checklistName.toLowerCase().replace(' ', '-');
    }

    /**
     * The HTML for {@code token}, or null when no checklist on this assessment claims it.
     *
     * <p>Returning null rather than an empty string matters: the caller leaves an
     * unclaimed placeholder alone so it can be offered elsewhere, and a template asking
     * for a checklist the assessment does not have should not quietly render a blank gap.
     *
     * @param token the placeholder body, without {@code ${}} — e.g. {@code checklist-owasp-api-top-10}
     */
    public String render(String token, List<AssessmentChecklist> checklists,
                         ChecklistRenderOptions options) {
        if (token == null || !token.startsWith(TOKEN_PREFIX) || checklists == null) return null;
        String wanted = token.substring(TOKEN_PREFIX.length()).trim();
        if (wanted.isEmpty()) return null;

        for (AssessmentChecklist checklist : checklists) {
            if (wanted.equals(variableNameFor(checklist.getTemplateName()))) {
                return toTable(checklist, options.forChecklist(wanted));
            }
        }
        return null;
    }

    private String toTable(AssessmentChecklist checklist, ChecklistRenderOptions options) {
        List<ChecklistResponse> rows = checklist.getResponses() == null
                ? List.of()
                : checklist.getResponses().stream()
                        .sorted(Comparator.comparingInt(ChecklistResponse::getOrder))
                        .toList();

        StringBuilder html = new StringBuilder(256 + rows.size() * 160);
        html.append("<table class=\"faction-checklist-table\"><thead><tr>")
            .append("<th>#</th><th>").append(escape(options.questionHeader())).append("</th>");
        if (options.showDone()) {
            html.append("<th>").append(escape(options.doneHeader())).append("</th>");
        }
        html.append("<th>").append(escape(options.statusHeader())).append("</th>");
        if (options.showComments()) {
            html.append("<th>").append(escape(options.commentHeader())).append("</th>");
        }
        html.append("</tr></thead><tbody>");

        int number = 1;
        for (ChecklistResponse row : rows) {
            String label = options.label(row.getResult());
            html.append("<tr><td>").append(number++).append(".</td>")
                .append("<td>").append(escape(row.getQuestionText())).append("</td>");
            if (options.showDone()) {
                html.append("<td>").append(escape(options.done(row.getResult()))).append("</td>");
            }
            html
                // The fill goes on the cell, not on a wrapper inside it: a background on
                // an inner element leaves the cell's own padding unpainted, which reads as
                // a white frame around the colour.
                .append("<td style=\"background-color:").append(options.cellColour(row.getResult()))
                .append("\"><span style=\"color:").append(options.fontColour(row.getResult()))
                .append("\">").append(escape(label)).append("</span></td>");
            if (options.showComments()) {
                String comment = row.getComment() == null ? "" : row.getComment().trim();
                // A genuinely empty cell collapses in Word; a non-breaking space keeps the
                // row the same height as its neighbours.
                html.append("<td>").append(comment.isEmpty() ? "&nbsp;" : escape(comment)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    /** Question text and comments are user-entered and land in XHTML that must parse. */
    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /**
     * The per-template rendering options, read from {@code ReportTemplate.checklistConfig}.
     *
     * <p>Every key is optional. An unset key takes the default below, so a template that
     * has never been configured renders exactly as it did before this existed.
     */
    /**
     * How the checklist tables look, from the template's checklist config.
     *
     * <p>Every key may also be set for one checklist alone by prefixing it with that checklist's
     * token name — {@code owasp-web-top-10.passText = Passed} — which wins over the plain key for
     * that table only. A template carrying two checklist tables that word their results
     * differently (the Web template's 4.1 says "Not Vulnerable", its 4.2 "Passed") needs exactly
     * that, and the config stays a flat map with no schema change.
     *
     * <p>{@code showDone} adds the iSec "Done" column between the question and its status: "Done"
     * for an item answered Pass or Fail, the N/A text for one answered N/A. Off by default, so a
     * template that never asks for it renders exactly as before.
     */
    public record ChecklistRenderOptions(
            String passText, String passCellColour, String passFontColour,
            String failText, String failCellColour, String failFontColour,
            String naText, String naCellColour, String naFontColour,
            String questionHeader, String statusHeader, String commentHeader,
            boolean showComments,
            boolean showDone, String doneHeader, String doneText,
            Map<String, String> config) {

        public static ChecklistRenderOptions from(Map<String, String> config) {
            return from(config, null);
        }

        /** The settings for one checklist: its own prefixed keys first, then the plain ones. */
        static ChecklistRenderOptions from(Map<String, String> config, String checklistName) {
            Map<String, String> c = config == null ? Map.of() : config;
            String prefix = checklistName == null || checklistName.isBlank() ? null : checklistName.trim() + ".";
            java.util.function.BiFunction<String, String, String> v = (key, fallback) -> {
                if (prefix != null) {
                    String own = c.get(prefix + key);
                    if (own != null && !own.isBlank()) return own;
                }
                return value(c, key, fallback);
            };
            return new ChecklistRenderOptions(
                    v.apply("passText", "Not Vulnerable"),
                    v.apply("passCellColour", "#92D050"),
                    v.apply("passFontColour", "#FFFFFF"),
                    v.apply("failText", "Vulnerable"),
                    v.apply("failCellColour", "#C00000"),
                    v.apply("failFontColour", "#FFFFFF"),
                    v.apply("naText", "N/A"),
                    v.apply("naCellColour", "#D9D9D9"),
                    v.apply("naFontColour", "#000000"),
                    v.apply("questionHeader", "Attack Type"),
                    v.apply("statusHeader", "Status"),
                    v.apply("commentHeader", "Comment"),
                    !"false".equalsIgnoreCase(v.apply("showComments", "true")),
                    "true".equalsIgnoreCase(v.apply("showDone", "false")),
                    v.apply("doneHeader", "Done"),
                    v.apply("doneText", "Done"),
                    c);
        }

        /** These settings, with any keys prefixed for {@code checklistName} applied on top. */
        public ChecklistRenderOptions forChecklist(String checklistName) {
            return from(config, checklistName);
        }

        private static String value(Map<String, String> config, String key, String fallback) {
            String v = config.get(key);
            return v == null || v.isBlank() ? fallback : v;
        }

        /** The Done column's text: "Done" once an item has an outcome, the N/A text when it was N/A. */
        String done(ChecklistResult result) {
            if (result == ChecklistResult.NA) return naText;
            if (result == ChecklistResult.PASS || result == ChecklistResult.FAIL) return doneText;
            return "";
        }

        String label(ChecklistResult result) {
            if (result == ChecklistResult.PASS) return passText;
            if (result == ChecklistResult.FAIL) return failText;
            if (result == ChecklistResult.NA) return naText;
            return "";
        }

        String cellColour(ChecklistResult result) {
            if (result == ChecklistResult.PASS) return passCellColour;
            if (result == ChecklistResult.FAIL) return failCellColour;
            if (result == ChecklistResult.NA) return naCellColour;
            return "transparent";
        }

        String fontColour(ChecklistResult result) {
            if (result == ChecklistResult.PASS) return passFontColour;
            if (result == ChecklistResult.FAIL) return failFontColour;
            if (result == ChecklistResult.NA) return naFontColour;
            return "inherit";
        }
    }
}
