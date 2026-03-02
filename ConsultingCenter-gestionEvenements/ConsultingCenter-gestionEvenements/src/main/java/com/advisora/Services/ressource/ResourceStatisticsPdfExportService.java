package com.advisora.Services.ressource;

import com.advisora.enums.UserRole;
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

public class ResourceStatisticsPdfExportService {
    private static final SimpleDateFormat VIEW_DF = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public File exportDashboardReport(
            ResourceStatisticsService.DashboardData data,
            ResourceStatisticsService.ResourceStatsFilter filter,
            UserRole role,
            File outputFile
    ) {
        if (data == null) {
            throw new IllegalArgumentException("Dashboard vide.");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Fichier de sortie requis.");
        }

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Dossier sortie invalide: " + parent.getAbsolutePath());
        }

        Document doc = new Document(PageSize.A4);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            doc.add(new Paragraph("Rapport Statistiques Ressource", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)));
            doc.add(new Paragraph("Date export: " + VIEW_DF.format(new Date()), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph("Role: " + (role == null ? "-" : role.name()), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph("Scope: " + safe(data.scopeLabel), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph("Filtres: " + describeFilter(filter), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph(" "));

            writeKpiTable(doc, data);
            writeStatusTable(doc, data.statusBreakdown);
            writeTopResourcesTable(doc, data.topResources);
            writeProvidersTable(doc, data.paymentProviders);
            writeMonthlyTable(doc, "Tendance mensuelle ventes", data.monthlyRevenue);
            writeMonthlyTable(doc, "Tendance mensuelle reservations", data.monthlyReservations);

            doc.close();
            return outputFile;
        } catch (Exception e) {
            throw new RuntimeException("Erreur export PDF stats ressource: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
    }

    private void writeKpiTable(Document doc, ResourceStatisticsService.DashboardData data) throws Exception {
        doc.add(new Paragraph("Indicateurs cles", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f)));
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100f);
        t.setSpacingBefore(8f);
        t.setSpacingAfter(12f);

        addCell(t, "Ventes (nb)");
        addCell(t, String.valueOf(data.salesCount));
        addCell(t, "Revenus ventes (coins)");
        addCell(t, formatCoins(data.salesCoins));
        addCell(t, "Reservations (lignes / qte)");
        addCell(t, data.reservationLines + " / " + data.reservedQty);
        addCell(t, "Paiements (nb / coins)");
        addCell(t, data.paymentCount + " / " + formatCoins(data.paymentCoins));
        addCell(t, "Avis (moyenne / nb)");
        addCell(t, String.format(Locale.US, "%.2f / %d", data.reviewAverage, data.reviewCount));
        addCell(t, "Comparaison mois");
        addCell(t, formatCoins(data.currentMonthRevenue) + " vs " + formatCoins(data.previousMonthRevenue)
                + " (" + String.format(Locale.US, "%.1f%%", data.monthlyRevenueDeltaPercent) + ")");
        doc.add(t);
    }

    private void writeStatusTable(Document doc, List<ResourceStatisticsService.StatusPoint> rows) throws Exception {
        doc.add(new Paragraph("Statuts ventes", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)));
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100f);
        t.setSpacingBefore(6f);
        t.setSpacingAfter(10f);
        addCell(t, "Statut");
        addCell(t, "Total");
        if (rows == null || rows.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("Aucune donnee"));
            c.setColspan(2);
            t.addCell(c);
        } else {
            for (ResourceStatisticsService.StatusPoint row : rows) {
                addCell(t, row.status);
                addCell(t, String.valueOf(row.count));
            }
        }
        doc.add(t);
    }

    private void writeTopResourcesTable(Document doc, List<ResourceStatisticsService.ResourcePoint> rows) throws Exception {
        doc.add(new Paragraph("Top ressources vendues", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)));
        PdfPTable t = new PdfPTable(new float[]{0.8f, 2.8f, 1.2f, 1.4f});
        t.setWidthPercentage(100f);
        t.setSpacingBefore(6f);
        t.setSpacingAfter(10f);
        addCell(t, "ID");
        addCell(t, "Ressource");
        addCell(t, "Qte");
        addCell(t, "Coins");
        if (rows == null || rows.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("Aucune donnee"));
            c.setColspan(4);
            t.addCell(c);
        } else {
            for (ResourceStatisticsService.ResourcePoint row : rows) {
                addCell(t, String.valueOf(row.id));
                addCell(t, row.name);
                addCell(t, String.valueOf(row.quantity));
                addCell(t, formatCoins(row.coins));
            }
        }
        doc.add(t);
    }

    private void writeProvidersTable(Document doc, List<ResourceStatisticsService.ProviderPoint> rows) throws Exception {
        doc.add(new Paragraph("Paiements par provider", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)));
        PdfPTable t = new PdfPTable(new float[]{2.2f, 1.0f, 1.4f});
        t.setWidthPercentage(100f);
        t.setSpacingBefore(6f);
        t.setSpacingAfter(10f);
        addCell(t, "Provider");
        addCell(t, "Total");
        addCell(t, "Coins");
        if (rows == null || rows.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("Aucune donnee"));
            c.setColspan(3);
            t.addCell(c);
        } else {
            for (ResourceStatisticsService.ProviderPoint row : rows) {
                addCell(t, row.provider);
                addCell(t, String.valueOf(row.count));
                addCell(t, formatCoins(row.coins));
            }
        }
        doc.add(t);
    }

    private void writeMonthlyTable(Document doc, String title, List<ResourceStatisticsService.MonthPoint> rows) throws Exception {
        doc.add(new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)));
        PdfPTable t = new PdfPTable(new float[]{2.0f, 1.0f});
        t.setWidthPercentage(100f);
        t.setSpacingBefore(6f);
        t.setSpacingAfter(10f);
        addCell(t, "Mois");
        addCell(t, "Valeur");
        if (rows == null || rows.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("Aucune donnee"));
            c.setColspan(2);
            t.addCell(c);
        } else {
            for (ResourceStatisticsService.MonthPoint row : rows) {
                addCell(t, row.label);
                addCell(t, String.format(Locale.US, "%.3f", row.value));
            }
        }
        doc.add(t);
    }

    private void addCell(PdfPTable table, String text) {
        table.addCell(new PdfPCell(new Phrase(safe(text))));
    }

    private String describeFilter(ResourceStatisticsService.ResourceStatsFilter f) {
        if (f == null) {
            return "Aucun";
        }
        return "from=" + (f.fromDate == null ? "-" : f.fromDate)
                + ", to=" + (f.toDate == null ? "-" : f.toDate)
                + ", user=" + (f.userId == null ? "TOUS" : f.userId)
                + ", resource=" + (f.resourceId == null ? "TOUS" : f.resourceId)
                + ", project=" + (f.projectId == null ? "TOUS" : f.projectId)
                + ", status=" + (f.status == null || f.status.isBlank() ? "TOUS" : f.status)
                + ", search=" + (f.searchText == null || f.searchText.isBlank() ? "-" : f.searchText);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String formatCoins(double coins) {
        return String.format(Locale.US, "%.3f coins", coins);
    }
}
