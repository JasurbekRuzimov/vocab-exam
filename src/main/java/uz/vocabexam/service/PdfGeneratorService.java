package uz.vocabexam.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.*;
import com.itextpdf.kernel.font.*;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.vocabexam.entity.Word;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @Slf4j
public class PdfGeneratorService {

    @Value("${app.pdf-dir:pdfs}")
    private String pdfDir;

    @Value("${app.school-name:VocabExam}")
    private String schoolName;

    private static final float LINE_WIDTH = 450f;
    private static final DeviceRgb DARK    = new DeviceRgb(30, 30, 30);
    private static final DeviceRgb BLUE    = new DeviceRgb(30, 58, 95);
    private static final DeviceRgb GRAY    = new DeviceRgb(100, 100, 100);

    public String generateVariant(String examTitle, int variantNumber,
                                   List<Word> words, UUID examId) throws IOException {
        Files.createDirectories(Paths.get(pdfDir));
        String fileName = examId + "_v" + variantNumber + ".pdf";
        String filePath = pdfDir + "/" + fileName;

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        try (PdfWriter writer = new PdfWriter(filePath);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(50, 50, 50, 50);

            // ── Header ──────────────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{1,1})).useAllAvailableWidth();

            Cell left = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
            left.add(new Paragraph(schoolName).setFont(bold).setFontSize(13).setFontColor(BLUE));
            left.add(new Paragraph("Vocabulary Exam").setFont(regular).setFontSize(10).setFontColor(GRAY));
            header.addCell(left);

            Cell right = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT);
            right.add(new Paragraph("Variant: " + variantNumber).setFont(bold).setFontSize(12).setFontColor(BLUE));
            right.add(new Paragraph("Sana: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                .setFont(regular).setFontSize(10).setFontColor(GRAY));
            header.addCell(right);
            doc.add(header);

            // ── Title ────────────────────────────────────────────────────────
            doc.add(new Paragraph(examTitle)
                .setFont(bold).setFontSize(16).setFontColor(DARK)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10).setMarginBottom(5));

            // ── Divider line ─────────────────────────────────────────────────
            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1f))
                .setMarginBottom(12));

            // ── Student info ─────────────────────────────────────────────────
            Table info = new Table(UnitValue.createPercentArray(new float[]{1,2,0.5f,1})).useAllAvailableWidth();
            info.addCell(labelCell("Ism Familiya:", regular));
            info.addCell(blankCell(regular));
            info.addCell(labelCell("Guruh:", regular));
            info.addCell(blankCell(regular));
            doc.add(info);
            doc.add(new Paragraph(" ").setMarginBottom(10));

            // ── Instructions ─────────────────────────────────────────────────
            doc.add(new Paragraph("Topshiriq: Har bir so'zdan foydalanib, to'liq inglizcha gap tuzing.")
                .setFont(regular).setFontSize(10).setFontColor(GRAY)
                .setItalic().setMarginBottom(15));

            // ── Words ────────────────────────────────────────────────────────
            for (int i = 0; i < words.size(); i++) {
                Table row = new Table(UnitValue.createPercentArray(new float[]{0.08f, 0.22f, 0.70f}))
                    .useAllAvailableWidth().setMarginBottom(12);

                // Number
                row.addCell(new Cell().add(new Paragraph((i+1) + ".").setFont(bold).setFontSize(11))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));

                // Word (bold)
                row.addCell(new Cell().add(new Paragraph(words.get(i).getText()).setFont(bold).setFontSize(12).setFontColor(BLUE))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));

                // Writing line
                Cell lineCell = new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(GRAY, 0.5f));
                lineCell.add(new Paragraph(" ").setFont(regular).setFontSize(11));
                row.addCell(lineCell);

                doc.add(row);
            }
        }
        return filePath;
    }

    private Cell labelCell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(10))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
    }
    private Cell blankCell(PdfFont font) {
        return new Cell().add(new Paragraph(" ").setFont(font).setFontSize(10))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(GRAY, 0.5f));
    }
}
