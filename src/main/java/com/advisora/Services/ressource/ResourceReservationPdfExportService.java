package com.advisora.Services.ressource;

import com.advisora.Model.ressource.Booking;
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

public class ResourceReservationPdfExportService {
    private static final SimpleDateFormat VIEW_DF = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public File exportReservationReport(List<Booking> rows, UserRole role, String searchText, File outputFile) {
        if (rows == null) {
            throw new IllegalArgumentException("Liste reservations invalide.");
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

            doc.add(new Paragraph("Rapport Reservations Ressources", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f)));
            doc.add(new Paragraph("Date export: " + VIEW_DF.format(new Date()), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph("Role: " + (role == null ? "-" : role.name()), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph("Recherche dynamique: " + safe(searchText), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph("Resultats: " + rows.size(), FontFactory.getFont(FontFactory.HELVETICA, 10f)));
            doc.add(new Paragraph(" "));

            boolean includeClient = role != UserRole.CLIENT;
            PdfPTable t = includeClient
                    ? new PdfPTable(new float[]{1.0f, 2.2f, 2.2f, 2.0f, 1.0f, 1.8f})
                    : new PdfPTable(new float[]{1.1f, 2.5f, 2.5f, 2.2f, 1.1f});
            t.setWidthPercentage(100f);
            t.setSpacingBefore(6f);
            t.setSpacingAfter(8f);

            addCell(t, "Proj.ID");
            addCell(t, "Projet");
            addCell(t, "Ressource");
            addCell(t, "Fournisseur");
            addCell(t, "Qte");
            if (includeClient) {
                addCell(t, "Client");
            }

            if (rows.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("Aucune donnee"));
                empty.setColspan(includeClient ? 6 : 5);
                t.addCell(empty);
            } else {
                for (Booking row : rows) {
                    addCell(t, String.valueOf(row.getIdProj()));
                    addCell(t, row.getProjectTitle());
                    addCell(t, row.getResourceName());
                    addCell(t, row.getFournisseurName());
                    addCell(t, String.valueOf(row.getQuantity()));
                    if (includeClient) {
                        addCell(t, row.getClientName());
                    }
                }
            }

            doc.add(t);
            doc.close();
            return outputFile;
        } catch (Exception e) {
            throw new RuntimeException("Erreur export PDF reservations: " + e.getMessage(), e);
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

