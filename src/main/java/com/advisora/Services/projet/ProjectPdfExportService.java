package com.advisora.Services.projet;

import com.advisora.Model.projet.Project;
import com.advisora.Model.projet.ProjectClientStat;
import com.advisora.Model.projet.ProjectDashboardData;
import com.advisora.Model.projet.ProjectStatsSummary;
import com.advisora.Model.projet.ProjectTypeStat;
import com.advisora.enums.UserRole;
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
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProjectPdfExportService {
    private static final SimpleDateFormat FILE_DF = new SimpleDateFormat("yyyyMMdd_HHmm");
    private static final SimpleDateFormat VIEW_DF = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public File exportVisibleProjectsReport(List<Project> visibleProjects,
                                            ProjectDashboardData dashboard,
                                            UserRole role,
                                            String appliedFilterLabel,
                                            String appliedSearchLabel) {
        String filename = "projets_" + (role == null ? "UNKNOWN" : role.name()) + "_" + FILE_DF.format(new Date()) + ".pdf";
        File defaultFile = new File(System.getProperty("user.home"), filename);
        return exportVisibleProjectsReport(visibleProjects, dashboard, role, appliedFilterLabel, appliedSearchLabel, defaultFile);
    }

    public File exportVisibleProjectsReport(List<Project> visibleProjects,
                                            ProjectDashboardData dashboard,
                                            UserRole role,
                                            String appliedFilterLabel,
                                            String appliedSearchLabel,
                                            File outputFile) {
        if (outputFile == null) {
            throw new IllegalArgumentException("Fichier de sortie requis");
        }
        if (dashboard == null) {
            throw new IllegalArgumentException("Dashboard requis");
        }
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Dossier de sortie invalide: " + parent.getAbsolutePath());
        }

        Document doc = new Document(PageSize.A4.rotate());
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            PdfWriter writer = PdfWriter.getInstance(doc, fos);
            writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);
            doc.open();

            writeHeader(doc, role, appliedFilterLabel, appliedSearchLabel);
            writeSummary(doc, dashboard.getSummary());
            writeProjectsTable(doc, visibleProjects);

            boolean managerView = role == UserRole.GERANT || role == UserRole.ADMIN;
            if (managerView) {
                writeTypeStats(doc, dashboard.getTypeStats());
                writeClientStats(doc, dashboard.getClientStats());
            }

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Document informatif - ne remplace pas la decision finale.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9f)));
            doc.close();
            // Some Windows/cloud folders do not support FileDescriptor#sync.
            // PDF is already flushed by close(); ignore sync failures.
            try {
                fos.getFD().sync();
            } catch (Exception ignored) {
                // no-op
            }
            return outputFile;
        } catch (Exception e) {
            throw new RuntimeException("Erreur export PDF projets: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
    }

    private void writeHeader(Document doc, UserRole role, String filter, String search) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f);
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10f);

        doc.add(new Paragraph("Rapport Projets", titleFont));
        doc.add(new Paragraph("Date export: " + VIEW_DF.format(new Date()), metaFont));
        doc.add(new Paragraph("Role: " + (role == null ? "-" : role.name()), metaFont));
        doc.add(new Paragraph("Filtre: " + safe(filter), metaFont));
        doc.add(new Paragraph("Recherche: " + safe(search), metaFont));
        doc.add(new Paragraph(" "));
    }

    private void writeSummary(Document doc, ProjectStatsSummary s) throws DocumentException {
        if (s == null) {
            return;
        }
        doc.add(new Paragraph("KPI", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f)));

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(8f);
        table.setSpacingAfter(10f);

        addCell(table, "Total");
        addCell(table, "Pending");
        addCell(table, "Acceptes");
        addCell(table, "Refuses");
        addCell(table, "Taux acceptation");
        addCell(table, "Avancement moyen");

        addCell(table, String.valueOf(s.getTotal()));
        addCell(table, String.valueOf(s.getPending()));
        addCell(table, String.valueOf(s.getAccepted()));
        addCell(table, String.valueOf(s.getRefused()));
        addCell(table, pct(s.getAcceptanceRatePercent()));
        addCell(table, pct(s.getAvgProgressPercent()));

        doc.add(table);
    }

    private void writeProjectsTable(Document doc, List<Project> projects) throws DocumentException {
        doc.add(new Paragraph("Projets visibles", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f)));

        PdfPTable table = new PdfPTable(new float[]{1.0f, 2.7f, 1.6f, 1.4f, 1.3f, 1.3f, 1.8f});
        table.setWidthPercentage(100f);
        table.setSpacingBefore(8f);
        table.setSpacingAfter(10f);

        addCell(table, "ID");
        addCell(table, "Titre");
        addCell(table, "Type");
        addCell(table, "Budget");
        addCell(table, "Statut");
        addCell(table, "Avancement");
        addCell(table, "Creation");

        if (projects == null || projects.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("Aucun projet visible."));
            cell.setColspan(7);
            table.addCell(cell);
        } else {
            for (Project p : projects) {
                addCell(table, String.valueOf(p.getIdProj()));
                addCell(table, safe(p.getTitleProj()));
                addCell(table, safe(p.getTypeProj()));
                addCell(table, String.format(Locale.US, "%.2f", p.getBudgetProj()));
                addCell(table, p.getStateProj() == null ? "-" : p.getStateProj().name());
                addCell(table, pct(p.getAvancementProj()));
                addCell(table, formatTs(p.getCreatedAtProj()));
            }
        }

        doc.add(table);
    }

    private void writeTypeStats(Document doc, List<ProjectTypeStat> rows) throws DocumentException {
        doc.add(new Paragraph("Stats par type", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)));
        PdfPTable table = new PdfPTable(new float[]{2.8f, 1.2f, 1.2f, 1.6f});
        table.setWidthPercentage(100f);
        table.setSpacingBefore(6f);
        table.setSpacingAfter(8f);

        addCell(table, "Type");
        addCell(table, "Total");
        addCell(table, "Acceptes");
        addCell(table, "Taux");

        if (rows == null || rows.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Aucune donnee."));
            empty.setColspan(4);
            table.addCell(empty);
        } else {
            for (ProjectTypeStat r : rows) {
                addCell(table, safe(r.getType()));
                addCell(table, String.valueOf(r.getTotal()));
                addCell(table, String.valueOf(r.getAccepted()));
                addCell(table, pct(r.getAcceptanceRatePercent()));
            }
        }
        doc.add(table);
    }

    private void writeClientStats(Document doc, List<ProjectClientStat> rows) throws DocumentException {
        doc.add(new Paragraph("Stats par client", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)));
        PdfPTable table = new PdfPTable(new float[]{1.1f, 2.8f, 1.2f, 1.2f, 1.6f});
        table.setWidthPercentage(100f);
        table.setSpacingBefore(6f);
        table.setSpacingAfter(8f);

        addCell(table, "ID");
        addCell(table, "Client");
        addCell(table, "Total");
        addCell(table, "Acceptes");
        addCell(table, "Taux");

        if (rows == null || rows.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Aucune donnee."));
            empty.setColspan(5);
            table.addCell(empty);
        } else {
            for (ProjectClientStat r : rows) {
                addCell(table, String.valueOf(r.getClientId()));
                addCell(table, safe(r.getClientName()));
                addCell(table, String.valueOf(r.getTotal()));
                addCell(table, String.valueOf(r.getAccepted()));
                addCell(table, pct(r.getAcceptanceRatePercent()));
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

    private String pct(double value) {
        return String.format(Locale.US, "%.0f%%", clamp(value));
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    private String formatTs(Timestamp ts) {
        if (ts == null) {
            return "-";
        }
        return VIEW_DF.format(new Date(ts.getTime()));
    }
}
