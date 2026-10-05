package com.faction.clientportal.util.reporting;

import com.faction.clientportal.model.FieldType;
import org.docx4j.TextUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.P;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The finding's Impact is a built-in field: {@code ${impactNarrative}}, and {@code ${impact_narrative}}
 * for templates written when it was a template field, both fill from it.
 */
class DocxUtilsImpactNarrativeTest {

    private static WordprocessingMLPackage template(String token) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        var mdp = pkg.getMainDocumentPart();
        mdp.addParagraphOfText("${fiBegin}");
        mdp.addParagraphOfText("${vulnName}");
        mdp.addParagraphOfText(token);
        mdp.addParagraphOfText("${fiEnd}");
        return pkg;
    }

    private static List<String> lines(WordprocessingMLPackage pkg) {
        List<String> out = new ArrayList<>();
        for (Object o : pkg.getMainDocumentPart().getContent()) {
            if (!(XmlUtils.unwrap(o) instanceof P p)) continue;
            StringWriter sw = new StringWriter();
            try {
                TextUtils.extractText(p, sw);
            } catch (Exception ignored) {
            }
            String text = sw.toString().trim();
            if (!text.isEmpty()) out.add(text);
        }
        return out;
    }

    private static List<String> render(String token, ReportData.ReportVulnerability... findings) throws Exception {
        WordprocessingMLPackage pkg = template(token);
        new DocxUtils(pkg, ReportData.builder()
                .vulnerabilities(List.of(findings))
                .sections(List.of())
                .build())
                .generateDocx("");
        return lines(pkg);
    }

    @Test
    void impactNarrativeFillsEachFindingsBlock() throws Exception {
        List<String> out = render("${impactNarrative}",
                ReportData.ReportVulnerability.builder().id("a").name("Alpha").severity("High")
                        .impactNarrative("<p>alpha impact</p>").build(),
                ReportData.ReportVulnerability.builder().id("b").name("Beta").severity("Low")
                        .impactNarrative("<p>beta impact</p>").build());

        assertThat(out).noneMatch(s -> s.contains("${"));
        assertThat(out).containsSubsequence("Alpha", "alpha impact", "Beta", "beta impact");
    }

    @Test
    void theOldTemplateFieldTokenFillsFromTheBuiltInField() throws Exception {
        List<String> out = render("${impact_narrative}",
                ReportData.ReportVulnerability.builder().id("a").name("Alpha").severity("High")
                        .impactNarrative("<p>built-in impact</p>").build());

        assertThat(out).noneMatch(s -> s.contains("${"));
        assertThat(out).contains("built-in impact");
    }

    @Test
    void theBuiltInFieldWinsOverALeftoverTemplateFieldValue() throws Exception {
        List<String> out = render("${impact_narrative}",
                ReportData.ReportVulnerability.builder().id("a").name("Alpha").severity("High")
                        .impactNarrative("<p>current impact</p>")
                        .fieldValues(Map.of("impact_narrative", "<p>stale impact</p>"))
                        .fieldTypes(Map.of("impact_narrative", FieldType.RICH_TEXT))
                        .build());

        assertThat(out).contains("current impact").noneMatch(s -> s.contains("stale impact"));
    }

    @Test
    void aFindingWithoutAnImpactLeavesNoPlaceholder() throws Exception {
        List<String> out = render("${impactNarrative}",
                ReportData.ReportVulnerability.builder().id("a").name("Alpha").severity("High").build());

        assertThat(out).noneMatch(s -> s.contains("${"));
    }
}
