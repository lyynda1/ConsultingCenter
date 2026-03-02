package com.advisora.GUI.Project;

import com.advisora.Model.projet.Top10CompanyItem;
import com.advisora.Model.projet.Top10Response;
import com.advisora.Services.projet.Top10AiService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Locale;

public class Top10DialogController {
    private static final String DEFAULT_DISCLAIMER =
            "Classement indicatif - chiffres estimatifs, non contractuels.";

    @FXML private TextField txtCategory;
    @FXML private Button btnGenerate;
    @FXML private Label lblCategory;
    @FXML private Label lblDisclaimer;
    @FXML private ListView<Top10CompanyItem> listTop10;

    private Top10AiService top10AiService;

    @FXML
    public void initialize() {
        lblDisclaimer.setText(DEFAULT_DISCLAIMER);
        listTop10.setPlaceholder(new Label("Aucun resultat."));
        listTop10.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Top10CompanyItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText("#" + item.getRank() + " - " + item.getName() +
                        "\nCA approx.: " + formatRevenueBillions(item.getRevenueUsdBillions()) + " | Annee: " + item.getYear() +
                        "\n" + item.getDescription());
            }
        });
    }

    @FXML
    private void onGenerateTop10() {
        String category = normalizeCategoryInput(txtCategory.getText());
        if (category == null) {
            showError("Validation", "Veuillez saisir un secteur.");
            clearResults();
            return;
        }

        try {
            Top10Response response = getTop10Service().generateTop10(category);
            List<Top10CompanyItem> rows = response.getTop10() == null ? List.of() : response.getTop10();
            listTop10.setItems(FXCollections.observableArrayList(rows));
            lblCategory.setText(buildInfoLine(response));

            String disclaimer = response.getDisclaimer();
            lblDisclaimer.setText(disclaimer == null || disclaimer.isBlank() ? DEFAULT_DISCLAIMER : disclaimer);
        } catch (Exception ex) {
            clearResults();
            showError("Top 10", ex.getMessage());
        }
    }

    String normalizeCategoryInput(String input) {
        if (input == null) return null;
        String out = input.trim();
        return out.isBlank() ? null : out;
    }

    String buildInfoLine(Top10Response response) {
        if (response == null || response.getCategory() == null || response.getCategory().isBlank()) {
            return "Categorie: -";
        }
        return "Categorie: " + response.getCategory().trim();
    }

    String formatRevenueBillions(double value) {
        return String.format(Locale.US, "%.1f Md USD", value);
    }

    private void clearResults() {
        listTop10.setItems(FXCollections.observableArrayList());
        lblCategory.setText("Categorie: -");
        lblDisclaimer.setText(DEFAULT_DISCLAIMER);
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(message == null || message.isBlank() ? "Erreur inconnue." : message);
        alert.showAndWait();
    }

    private Top10AiService getTop10Service() {
        if (top10AiService == null) {
            top10AiService = new Top10AiService();
        }
        return top10AiService;
    }
}

