package com.advisora.Services.ressource;

import com.lowagie.text.Document;
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

public class ResourceCatalogPdfExportService {
    private static final SimpleDateFormat VIEW_DF = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static final class CatalogPdfRow {
        public final int id;
        public final String resourceName;
        public final String supplierName;
        public final double price;
        public final int totalQty;
        public final int reservedQty;
        public final int availableQty;
        public final String status;

        public CatalogPdfRow(
                int id,
                String resourceName,
                String supplierName,
                double price,
                int totalQty,
                int reservedQty,
                int availableQty,
                String status
        ) {
            this.id = id;
            this.resourceName = resourceName;
            this.supplierName = supplierName;
            this.price = price;
            this.totalQty = totalQty;
            this.reservedQty = reservedQty;
            this.availableQty = availableQty;
            this.status = status;
        }
    }

    public File exportCatalogReport(
            List<CatalogPdfRow> rows,
            String supplierFilter,
            String searchText,
            File outputFile
    ) {
        if (rows == null) {
            throw new IllegalArgumentException("Liste catalogue invalide.");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Fichier de sortie requis.");
        }

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Dossier sortie invalide: " + parent.getAbsolutePath());
        }

        Document doc = new Document(PageSize.A4.rotate());
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            doc.add(new Paragraph("Rapport Catalogue Ressources", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)));
            doc.add(new Paragraph("Date export: " + VIEW_DF.format(new Date()), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph("Filtre fournisseur: " + safe(supplierFilter), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph("Recherche dynamique: " + safe(searchText), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph("Resultats: " + rows.size(), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph(" "));

            PdfPTable t = new PdfPTable(new float[]{0.8f, 2.1f, 2.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.2f});
            t.setWidthPercentage(100f);
            t.setSpacingBefore(6f);
            t.setSpacingAfter(8f);

            addCell(t, "ID");
            addCell(t, "Ressource");
            addCell(t, "Fournisseur");
            addCell(t, "Prix");
            addCell(t, "Stock");
            addCell(t, "Reserve");
            addCell(t, "Dispo");
            addCell(t, "Statut");

            if (rows.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("Aucune donnee"));
                empty.setColspan(8);
                t.addCell(empty);
            } else {
                for (CatalogPdfRow row : rows) {
                    addCell(t, String.valueOf(row.id));
                    addCell(t, row.resourceName);
                    addCell(t, row.supplierName);
                    addCell(t, String.format(Locale.US, "%.2f", row.price));
                    addCell(t, String.valueOf(row.totalQty));
                    addCell(t, String.valueOf(row.reservedQty));
                    addCell(t, String.valueOf(row.availableQty));
                    addCell(t, row.status);
                }
            }
            doc.add(t);

            doc.close();
            return outputFile;
        } catch (Exception e) {
            throw new RuntimeException("Erreur export PDF catalogue: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
    }

    private void addCell(PdfPTable table, String text) {
        table.addCell(new PdfPCell(new Phrase(safe(text))));
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }
}

