package com.advisora.Services.investment;

import com.advisora.Model.investment.Transaction;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionPdfExportService {

    private static final SimpleDateFormat FILE_DF = new SimpleDateFormat("yyyyMMdd_HHmm");
    private static final SimpleDateFormat VIEW_DF = new SimpleDateFormat("yyyy-MM-dd");

    public File exportTransactions(List<Transaction> transactions) {
        String filename = "transactions_" + FILE_DF.format(new Date()) + ".pdf";
        File defaultFile = new File(System.getProperty("user.home"), filename);
        return exportTransactions(transactions, defaultFile, true);
    }

    public File exportTransactions(List<Transaction> transactions, File outputFile) {
        return exportTransactions(transactions, outputFile, true);
    }

    public File exportTransactions(List<Transaction> transactions, File outputFile, boolean includeId) {
        if (outputFile == null) {
            throw new IllegalArgumentException("Fichier de sortie requis");
        }
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Dossier de sortie invalide: " + parent.getAbsolutePath());
        }

        Document doc = new Document(PageSize.A4.rotate());
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            writeHeader(doc);
            writeTransactionsTable(doc, transactions, includeId);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Document gÃ©nÃ©rÃ© automatiquement - historique des transactions.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9f)));
            doc.close();
            try {
                fos.getFD().sync();
            } catch (Exception ignored) {
                // no-op
            }
            return outputFile;
        } catch (Exception e) {
            throw new RuntimeException("Erreur export PDF transactions: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
    }

    private void writeHeader(Document doc) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f);
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10f);

        doc.add(new Paragraph("Historique des Transactions", titleFont));
        doc.add(new Paragraph("Date export: " + VIEW_DF.format(new Date()), metaFont));
        doc.add(new Paragraph(" "));
    }

    private void writeTransactionsTable(Document doc, List<Transaction> transactions, boolean includeId) throws DocumentException {
        PdfPTable table = includeId
                ? new PdfPTable(new float[]{0.8f, 1.4f, 1.2f, 1.6f, 1.2f, 1.2f})
                : new PdfPTable(new float[]{1.4f, 1.2f, 1.6f, 1.2f, 1.2f});
        table.setWidthPercentage(100f);
        table.setSpacingBefore(8f);
        table.setSpacingAfter(10f);

        if (includeId) {
            addCell(table, "ID");
        }
        addCell(table, "Date");
        addCell(table, "Montant");
        addCell(table, "Type");
        addCell(table, "Statut");
        addCell(table, "ID Invest.");

        if (transactions == null || transactions.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("Aucune transaction."));
            cell.setColspan(includeId ? 6 : 5);
            table.addCell(cell);
        } else {
            for (Transaction t : transactions) {
                if (includeId) {
                    addCell(table, String.valueOf(t.getIdTransac()));
                }
                addCell(table, t.getDateTransac() == null ? "-" : VIEW_DF.format(t.getDateTransac()));
                addCell(table, String.format(Locale.US, "%.2f", t.getMontantTransac()));
                addCell(table, safe(t.getType()));
                addCell(table, t.getStatut() == null ? "-" : t.getStatut().name());
                addCell(table, String.valueOf(t.getIdInv()));
            }
        }

        doc.add(table);
    }

    private void addCell(PdfPTable table, String text) {
        table.addCell(new PdfPCell(new Phrase(safe(text))));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}


