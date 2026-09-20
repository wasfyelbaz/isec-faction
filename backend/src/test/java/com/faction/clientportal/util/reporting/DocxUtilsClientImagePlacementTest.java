package com.faction.clientportal.util.reporting;

import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
import org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.Body;
import org.docx4j.wml.FooterReference;
import org.docx4j.wml.Ftr;
import org.docx4j.wml.Hdr;
import org.docx4j.wml.HdrFtrRef;
import org.docx4j.wml.HeaderReference;
import org.docx4j.wml.Jc;
import org.docx4j.wml.JcEnumeration;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.SectPr;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Tr;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where {@code ${clientImage <name>}} may sit and how it is sized.
 *
 * <p>The iSec templates carry the client's logo on the cover (a text box in the body) and in the
 * footer band of every page. A footer is a part of its own with its own relationships, so the
 * picture must be registered there, not on the main document; and logos come in every shape,
 * so {@code width=… height=…} fits each one into the same box.
 */
class DocxUtilsClientImagePlacementTest {

    private static final long EMU_PER_PX = 9525L;
    private static final long PAGE_WIDTH_CAP = 9000L * 635L;

    /** A solid blue PNG of the given size. */
    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static ReportData clientWithLogo(byte[] logo) {
        return ReportData.builder()
                .vulnerabilities(new ArrayList<>())
                .clientName("BDC")
                .clientImageBytes(Map.of("logo", logo))
                .clientImageContentTypes(Map.of("logo", "image/png"))
                .build();
    }

    private static SectPr sectPr(WordprocessingMLPackage pkg) {
        Body body = pkg.getMainDocumentPart().getJaxbElement().getBody();
        if (body.getSectPr() == null) body.setSectPr(Context.getWmlObjectFactory().createSectPr());
        return body.getSectPr();
    }

    private static FooterPart addFooter(WordprocessingMLPackage pkg, String text) throws Exception {
        ObjectFactory f = Context.getWmlObjectFactory();
        FooterPart footer = new FooterPart();
        Ftr ftr = f.createFtr();
        ftr.getContent().add(pkg.getMainDocumentPart().createParagraphOfText(text));
        footer.setJaxbElement(ftr);
        Relationship rel = pkg.getMainDocumentPart().addTargetPart(footer);
        FooterReference ref = f.createFooterReference();
        ref.setId(rel.getId());
        ref.setType(HdrFtrRef.DEFAULT);
        sectPr(pkg).getEGHdrFtrReferences().add(ref);
        return footer;
    }

    private static HeaderPart addHeader(WordprocessingMLPackage pkg, String text) throws Exception {
        ObjectFactory f = Context.getWmlObjectFactory();
        HeaderPart header = new HeaderPart();
        Hdr hdr = f.createHdr();
        hdr.getContent().add(pkg.getMainDocumentPart().createParagraphOfText(text));
        header.setJaxbElement(hdr);
        Relationship rel = pkg.getMainDocumentPart().addTargetPart(header);
        HeaderReference ref = f.createHeaderReference();
        ref.setId(rel.getId());
        ref.setType(HdrFtrRef.DEFAULT);
        sectPr(pkg).getEGHdrFtrReferences().add(ref);
        return header;
    }

    private static String xml(Object jaxbElement) {
        return XmlUtils.marshaltoString(jaxbElement, true, false);
    }

    private static List<BinaryPartAbstractImage> imagePartsOf(Part part) {
        List<BinaryPartAbstractImage> images = new ArrayList<>();
        if (part.getRelationshipsPart() == null) return images;
        for (Relationship rel : part.getRelationshipsPart().getRelationships().getRelationship()) {
            if (Namespaces.IMAGE.equals(rel.getType())) {
                images.add((BinaryPartAbstractImage) part.getRelationshipsPart().getPart(rel));
            }
        }
        return images;
    }

    private static long extent(String xml, String axis) {
        Matcher m = Pattern.compile("<wp:extent[^>]*\\b" + axis + "=\"(\\d+)\"").matcher(xml);
        assertThat(m.find()).as("an inline picture with an extent").isTrue();
        return Long.parseLong(m.group(1));
    }

    /** The main document after generation, plus the image parts it owns. */
    private record Generated(String bodyXml, List<BinaryPartAbstractImage> imageParts) { }

    private static Generated generateBody(String tag, byte[] logo) throws Exception {
        return generateBody(tag, logo, null, false);
    }

    private static Generated generateBody(String tag, byte[] logo, JcEnumeration alignment, boolean inCell,
                                          String... moreParagraphs) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart main = pkg.getMainDocumentPart();
        main.addParagraphOfText("before");
        P paragraph = main.createParagraphOfText(tag);
        if (alignment != null) {
            ObjectFactory factory = Context.getWmlObjectFactory();
            PPr pPr = factory.createPPr();
            Jc jc = factory.createJc();
            jc.setVal(alignment);
            pPr.setJc(jc);
            paragraph.setPPr(pPr);
        }
        if (inCell) {
            ObjectFactory factory = Context.getWmlObjectFactory();
            Tbl tbl = factory.createTbl();
            Tr tr = factory.createTr();
            Tc tc = factory.createTc();
            tc.getContent().add(paragraph);
            tr.getContent().add(tc);
            tbl.getContent().add(tr);
            main.getContent().add(tbl);
        } else {
            main.getContent().add(paragraph);
        }
        for (String more : moreParagraphs) main.addParagraphOfText(more);
        main.addParagraphOfText("after");
        new DocxUtils(pkg, clientWithLogo(logo)).generateDocx("");
        return new Generated(xml(main.getJaxbElement()), imagePartsOf(main));
    }

    @Test
    void aFooterSlotGetsThePictureRegisteredOnTheFooter() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addParagraphOfText("body text");
        FooterPart footer = addFooter(pkg, "${clientImage logo width=53}");

        new DocxUtils(pkg, clientWithLogo(png(400, 150))).generateDocx("");

        String footerXml = xml(footer.getJaxbElement());
        assertThat(footerXml).contains("<w:drawing").doesNotContain("clientImage");
        assertThat(extent(footerXml, "cx")).isEqualTo(53 * EMU_PER_PX);
        assertThat(extent(footerXml, "cy")).isEqualTo(Math.round(150 * (53 / 400.0)) * EMU_PER_PX);
        assertThat(imagePartsOf(footer)).as("the footer owns its picture").hasSize(1);
        assertThat(imagePartsOf(pkg.getMainDocumentPart())).isEmpty();
        assertThat(xml(pkg.getMainDocumentPart().getJaxbElement())).doesNotContain("<w:drawing");
    }

    @Test
    void aHeaderSlotIsHonouredToo() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addParagraphOfText("body text");
        HeaderPart header = addHeader(pkg, "${clientImage logo}");

        new DocxUtils(pkg, clientWithLogo(png(40, 20))).generateDocx("");

        String headerXml = xml(header.getJaxbElement());
        assertThat(headerXml).contains("<w:drawing").doesNotContain("clientImage");
        assertThat(extent(headerXml, "cx")).isEqualTo(40 * EMU_PER_PX);
        assertThat(imagePartsOf(header)).hasSize(1);
    }

    @Test
    void aBoxFitsAndPadsTheLogoSoEveryClientGetsTheSameSpace() throws Exception {
        Generated wide = generateBody("${clientImage logo width=139 height=54}", png(1200, 300));
        Generated square = generateBody("${clientImage logo width=139 height=54}", png(500, 500));

        for (Generated copy : List.of(wide, square)) {
            assertThat(extent(copy.bodyXml(), "cx")).isEqualTo(139 * EMU_PER_PX);
            assertThat(extent(copy.bodyXml(), "cy")).isEqualTo(54 * EMU_PER_PX);
            BufferedImage stored = ImageIO.read(new ByteArrayInputStream(copy.imageParts().get(0).getBytes()));
            assertThat(stored.getWidth()).as("oversampled canvas width").isEqualTo(139 * 4);
            assertThat(stored.getHeight()).as("oversampled canvas height").isEqualTo(54 * 4);
        }
        // the square logo is letterboxed: transparent padding at the sides, blue in the middle
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(square.imageParts().get(0).getBytes()));
        assertThat(new Color(stored.getRGB(2, stored.getHeight() / 2), true).getAlpha()).isZero();
        assertThat(new Color(stored.getRGB(stored.getWidth() / 2, stored.getHeight() / 2), true)).isEqualTo(Color.BLUE);
    }

    @Test
    void emptyMarginsAreTrimmedBeforeFittingSoTheMarkFillsTheBox() throws Exception {
        // a small blue mark in the middle of a large transparent canvas, like a padded logo file
        BufferedImage padded = new BufferedImage(1200, 630, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = padded.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(500, 290, 200, 50);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(padded, "png", out);

        Generated copy = generateBody("${clientImage logo width=76 height=25}", out.toByteArray());
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(copy.imageParts().get(0).getBytes()));
        // the 4:1 mark fills the 76x25 box's width: blue right up to the sides, transparent above and below
        assertThat(new Color(stored.getRGB(2, stored.getHeight() / 2), true)).isEqualTo(Color.BLUE);
        assertThat(new Color(stored.getRGB(stored.getWidth() - 3, stored.getHeight() / 2), true)).isEqualTo(Color.BLUE);
        assertThat(new Color(stored.getRGB(stored.getWidth() / 2, 1), true).getAlpha()).isZero();
    }

    @Test
    void aWhiteBackgroundCountsAsMarginToo() throws Exception {
        BufferedImage onWhite = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = onWhite.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 400);
        g.setColor(Color.BLUE);
        g.fillRect(100, 150, 200, 100);
        g.dispose();
        BufferedImage trimmed = DocxUtils.trimBorders(onWhite);
        assertThat(trimmed.getWidth()).isEqualTo(200);
        assertThat(trimmed.getHeight()).isEqualTo(100);
        assertThat(DocxUtils.trimBorders(ImageIO.read(new ByteArrayInputStream(png(30, 10)))).getWidth())
                .as("a mark with no margin is left alone").isEqualTo(30);
    }

    @Test
    void heightAloneScalesProportionally() throws Exception {
        Generated copy = generateBody("${clientImage logo height=20}", png(400, 100));
        assertThat(extent(copy.bodyXml(), "cy")).isEqualTo(20 * EMU_PER_PX);
        assertThat(extent(copy.bodyXml(), "cx")).isEqualTo(80 * EMU_PER_PX);
    }

    @Test
    void anUnsizedWideImageIsCappedAtThePageWidth() throws Exception {
        Generated copy = generateBody("${clientImage logo}", png(2000, 100));
        assertThat(extent(copy.bodyXml(), "cx")).isEqualTo(PAGE_WIDTH_CAP);
        assertThat(extent(copy.bodyXml(), "cy"))
                .isEqualTo(Math.round(100 * EMU_PER_PX * (PAGE_WIDTH_CAP / (2000.0 * EMU_PER_PX))));
    }

    @Test
    void theParagraphKeepsItsOwnAlignment() throws Exception {
        Generated copy = generateBody("${clientImage logo width=100}", png(50, 50), JcEnumeration.RIGHT, false);
        int jc = copy.bodyXml().indexOf("<w:jc w:val=\"right\"/>");
        int drawing = copy.bodyXml().indexOf("<w:drawing");
        assertThat(jc).isPositive();
        assertThat(drawing).isGreaterThan(jc);
        assertThat(copy.bodyXml().substring(jc, drawing)).as("same paragraph").doesNotContain("</w:p>");
    }

    @Test
    void anEmptySlotInATableCellLeavesTheCellValid() throws Exception {
        Generated copy = generateBody("${clientImage cover width=100}", png(50, 50), null, true);
        assertThat(copy.bodyXml()).doesNotContain("clientImage").doesNotContain("<w:drawing");
        assertThat(copy.bodyXml()).contains("<w:tc>").contains("<w:p");
    }

    @Test
    void anEmptySlotBetweenParagraphsIsRemovedOutright() throws Exception {
        Generated copy = generateBody("${clientImage cover width=100}", png(50, 50));
        assertThat(copy.bodyXml()).doesNotContain("clientImage").doesNotContain("<w:drawing");
        assertThat(copy.bodyXml()).contains("before").contains("after");
    }

    @Test
    void theSameSlotTwiceInAPartSharesOneImagePart() throws Exception {
        Generated copy = generateBody("${clientImage logo width=100}", png(50, 50), null, false,
                "${clientImage logo width=100}");
        assertThat(copy.bodyXml().split("<w:drawing", -1).length - 1).isEqualTo(2);
        assertThat(copy.imageParts()).hasSize(1);
    }
}
