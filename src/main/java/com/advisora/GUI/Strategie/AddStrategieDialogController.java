package com.advisora.GUI.Strategie;

import com.advisora.Model.projet.Project;
import com.advisora.Model.strategie.ExternalEvent;
import com.advisora.Model.strategie.LlmDecision;
import com.advisora.Model.strategie.NewsItem;
import com.advisora.Model.strategie.Strategie;
import com.advisora.Services.projet.ProjectService;
import com.advisora.Services.strategie.NotificationManager;
import com.advisora.Services.strategie.RiskContext;
import com.advisora.Services.strategie.RiskService;
import com.advisora.Services.strategie.ServiceStrategie;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.Severity;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.TypeStrategie;
import com.advisora.utils.news.NewsJsonStore;
import com.advisora.utils.news.OllamaClient;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;


import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AddStrategieDialogController {
    @FXML private Label titleLabel;
    @FXML private TextField nomField;
    @FXML private ComboBox<Project> projetCombo;
    @FXML private ComboBox<StrategyStatut> statutCombo;
    @FXML private ComboBox<TypeStrategie> typeCombo;
    @FXML private TextField budgetTotalField;
    @FXML private TextField gainEstimeField;
    @FXML private StackPane overlay;


    @FXML private HBox dragHandle;
    @FXML private TextField terme;
    @FXML private Button saveBtn;

    private final ServiceStrategie serviceStrategie = new ServiceStrategie();
    private final ProjectService projectService = new ProjectService();
    private final NotificationManager notificationManager = new NotificationManager();
    private Runnable onSaved = () -> {};
    private Runnable onClose = () -> {};
    private Strategie editingStrategie;
    private final ProgressIndicator Spinner = new ProgressIndicator();
    private boolean load = false;

    @FXML
    public void initialize() {
        statutCombo.setItems(FXCollections.observableArrayList(StrategyStatut.values()));

        if (saveBtn != null) {
            saveBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> load));
        }

        if (overlay != null) {
            overlay.setVisible(false);
            overlay.setManaged(false);
        }
        ObservableList<TypeStrategie> filteredList =
                FXCollections.observableArrayList(TypeStrategie.values())
                        .filtered(item -> item != TypeStrategie.NULL); // Replace NULL with your actual enum value

// Set the filtered list to the ComboBox
        typeCombo.setItems(filteredList);        typeCombo.setValue(TypeStrategie.NULL);
        budgetTotalField.setText("0");
        gainEstimeField.setText("0");



        projetCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Project p) {
               return p == null ? "" : "#" + p.getIdProj() + " - " + p.getTitleProj();
            }

            @Override
            public Project fromString(String string) {
                return null;
            }
        });

        List<Project> projects = serviceStrategie.listProjets();
        projetCombo.setItems(FXCollections.observableArrayList(projects));
    }

    private void setLoading(boolean v) {
        load = v;
        if (overlay != null) {
            overlay.setVisible(v);
            overlay.setManaged(v);
        }
    }

    public Node getDragHandle() {
        return dragHandle;
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void initForCreate(Runnable onSaved) {
        setOnSaved(onSaved);
        setEditingStrategie(null);
        resetForm();
    }

    public void initForEdit(Strategie strategie, Runnable onSaved) {
        setOnSaved(onSaved);
        setEditingStrategie(strategie);
    }

    public void setEditingStrategie(Strategie strategie) {
        this.editingStrategie = strategie;

        if (strategie == null) {
            resetForm();
            return;
        }

        nomField.setText(strategie.getNomStrategie());
        statutCombo.setValue(strategie.getStatut());
        typeCombo.setValue(strategie.getTypeStrategie());
        selectProjectById(strategie.getProjet() == null ? 0 : strategie.getProjet().getIdProj());
        budgetTotalField.setText(String.valueOf(strategie.getBudgetTotal()));
        gainEstimeField.setText(String.valueOf(strategie.getGainEstime()));
        terme.setText(strategie.getDureeTerme());
        if (titleLabel != null) {
            titleLabel.setText("Modifier Stratégie");
        }
        if (saveBtn != null) {
            saveBtn.setText("Enregistrer");
        }
    }

    public void resetForm() {
        editingStrategie = null;
        nomField.clear();

        statutCombo.setValue(StrategyStatut.EN_COURS);
        projetCombo.getSelectionModel().clearSelection();
        budgetTotalField.setText("0");
        gainEstimeField.setText("0");
        if (titleLabel != null) {
            titleLabel.setText("Nouvelle Stratégie");
        }
        if (saveBtn != null) {
            saveBtn.setText("Ajouter");
        }
    }

    @FXML
    private void save() {
        setLoading(true);
        try {
            StrategyStatut statut = statutCombo.getValue();
            if (statut == null) {
                statut = StrategyStatut.EN_COURS;
            }


            Project selectedProject = projetCombo.getValue();


            Strategie s = editingStrategie == null ? new Strategie() : editingStrategie;


            String validatedName = validateStrategyName(nomField.getText());
            validatedName = UniqueStrategie(validatedName, s.getId());
            s.setNomStrategie(validatedName);

            s.setStatut(statut);
            s.setProjet(selectedProject);
            s.setIdUser(SessionContext.getCurrentUserId());
            s.setTypeStrategie(typeCombo.getValue());
            s.setBudgetTotal(parsePositiveDouble(budgetTotalField.getText(), "Budget total invalide."));
            s.setGainEstime(parsePositiveDouble(gainEstimeField.getText(), "Gain estime invalide."));
            s.setDureeTerme(required(terme.getText(), "Durée/terme obligatoire."));
            if (s.getCreatedAt() == null) {
                s.setCreatedAt(LocalDateTime.now());
            }
            if (s.getBudgetTotal()> s.getGainEstime()){
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Le gain estimé est inférieur au budget total, il faut s'assurer de la viabilité de la strategie . Etes vous sur de votre choix ?", ButtonType.YES, ButtonType.NO);
                a.setHeaderText("Validation gain vs budget");
                Optional<ButtonType> result = a.showAndWait();
                if (result.isEmpty() || result.get() == ButtonType.NO) {
                    return;
                }
            }

            if (editingStrategie == null) {

                String title = nomField.getText().trim();

                // 1) Macro (keywords + events active)
                RiskService riskService = RiskContext.getRiskService();
                RiskService.RiskResult rr = riskService.checkTitle(title);
                System.out.println("[CHECKTITLE] title=" + title);
                System.out.println("[CHECKTITLE] maxSeverity=" + rr.maxSeverity);
                System.out.println("[CHECKTITLE] msg=" + rr.message);
                System.out.println("[CHECKTITLE] activeEventsCount=" + riskService.getActiveEvents().size());

                String improvedMsg = "Analyse macro : " + rr.maxSeverity;
                Severity finalSeverity = rr.maxSeverity;
                ProgressIndicator pi = new ProgressIndicator();
                pi.setMaxSize(100, 100);
                // Will be styled by your CSS
                overlay.getChildren().add(pi);
                overlay.setManaged(true);
                overlay.setVisible(true);



                // 2) LLM decision only if macro is WARNING+
                if (rr.maxSeverity == Severity.WARNING || rr.maxSeverity == Severity.DANGER || rr.maxSeverity == Severity.CRITICAL) {
                    try {
                        // Load active events
                        List<ExternalEvent> activeEvents = RiskContext.getRiskService().getActiveEvents();
                        // ⬆️ If you don't have getActiveEvents(), use: store.getActiveEvents() inside RiskService and expose it

                        // Load recent news
                        NewsJsonStore ns = new NewsJsonStore(Path.of("data", "news_items.json"));
                        List<NewsItem> items = ns.readAll();
                        Collections.reverse(items); // newest first
                        if (items.size() > 10) items = items.subList(0, 10);

                        // LLM decision (you already wired gemini through OllamaClient)
                        LlmDecision dec = riskService.decideWithLLM(title, activeEvents, items);

                        // Parse severity returned by LLM
                        Severity llmSeverity;
                        try {
                            llmSeverity = Severity.valueOf(dec.finalSeverity.toUpperCase());
                        } catch (Exception ignored) {
                            llmSeverity = rr.maxSeverity; // fallback
                        }

                        finalSeverity = llmSeverity;

                        improvedMsg += "\n\nAnalyse IA : " + (dec.summary_fr == null ? "" : dec.summary_fr)
                                + "\n" + (dec.why_fr == null ? "" : dec.why_fr);

                        // Add matched news links if available
                        if (dec.matchedNewsLinks != null && !dec.matchedNewsLinks.isEmpty()) {
                            improvedMsg += "\n\nNews pertinentes :";
                            for (String link : dec.matchedNewsLinks) {
                                improvedMsg += "\n- " + link;
                            }
                        }

                        improvedMsg += "\n\nDecision finale = " + finalSeverity;

                    } catch (Exception ex) {
                        improvedMsg += "\n\n(Analyse IA indisponible: " + ex.getClass().getSimpleName()
                                + (ex.getMessage() != null ? (": " + ex.getMessage()) : "") + ")";
                        finalSeverity = rr.maxSeverity;
                    }
                }


                // 3) Decision uses finalSeverity
                switch (finalSeverity) {
                    case INFO, WARNING -> {
                        serviceStrategie.ajouter(s);
                        showInfo("Enregistrée (Risque: " + finalSeverity + ")", improvedMsg);
                    }
                    case DANGER -> {
                        String justification = askJustification(improvedMsg);
                        if (justification == null || justification.isBlank()) return;
                        serviceStrategie.ajouter(s);
                        showInfo("Enregistrée avec justification (Risque: DANGER)", improvedMsg);
                    }
                    case CRITICAL -> {
                        // Create a custom dialog for critical risk
                        Dialog<ButtonType> criticalDialog = new Dialog<>();
                        criticalDialog.setTitle("Risque Critique - Approbation Admin Requise");
                        criticalDialog.setHeaderText("Cette stratégie présente un risque critique lié aux événements externes.");

                        // Add content to the dialog
                        TextArea riskDetails = new TextArea(improvedMsg);
                        riskDetails.setEditable(false);
                        riskDetails.setWrapText(true);
                        riskDetails.setPrefRowCount(10);

                        VBox content = new VBox(10,
                                new Label("Détails du risque:"),
                                riskDetails,
                                new Label("Êtes-vous certain que cette stratégie ne sera pas affectée par ces événements externes?")
                        );

                        criticalDialog.getDialogPane().setContent(content);

                        // Add buttons
                        ButtonType confirmBtn = new ButtonType("Confirmer (Approbation Admin Requise)", ButtonBar.ButtonData.OK_DONE);
                        ButtonType cancelBtn = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
                        criticalDialog.getDialogPane().getButtonTypes().addAll(confirmBtn, cancelBtn);

                        // Show dialog and handle response
                        Optional<ButtonType> result = criticalDialog.showAndWait();
                        if (result.isPresent() && result.get() == confirmBtn) {
                            // Save the strategy
                            serviceStrategie.ajouter(s);
                            s.setApprobation(false); // Mark as pending approval
                            serviceStrategie.ValidationStrategie(s);

                            // Show confirmation message
                            Alert approvalAlert = new Alert(Alert.AlertType.INFORMATION);
                            approvalAlert.setTitle("Demande d'approbation envoyée");
                            approvalAlert.setHeaderText("Votre demande d'approbation a été envoyée à l'administrateur");
                            approvalAlert.setContentText("Une notification a été envoyée pour confirmer l'approbation de cette stratégie malgré le risque critique.");

                            approvalAlert.showAndWait();
                            // Close the dialog
                            onSaved.run();
                            notificationManager.addNotification(new com.advisora.Model.strategie.Notification(
                                    "Approbation requise pour stratégie à risque critique",
                                    "La stratégie '" + s.getNomStrategie() + "' a été enregistrée malgré un risque critique. Veuillez réviser et approuver."));

                            close();
                        }
                        // If user cancels, just return without saving
                        return;
                    }

                }
            } else {
                serviceStrategie.modifier(s);
            }

            onSaved.run();
            close();
        } catch (Exception e) {
            Alert a = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            a.setHeaderText("Strategie");
            a.showAndWait();
        }finally {
            setLoading(false);
        }
    }

    private Severity combineMacroWithLLM(Severity macro, int bestScore) {
        if (bestScore < 50) {
            if (macro == Severity.CRITICAL || macro == Severity.DANGER) return Severity.WARNING;
            return macro;
        }
        if (bestScore >= 80) return macro;

        if (macro == Severity.CRITICAL) return Severity.DANGER;
        if (macro == Severity.DANGER) return Severity.WARNING;

        return macro;
    }

    private String validateStrategyName(String name) {
        if (name == null) throw new IllegalArgumentException("Nom stratégie obligatoire.");
        String n = name.trim();

        if (n.length() < 5)
            throw new IllegalArgumentException("Nom stratégie trop court (min 5 caractères).");

        if (!n.matches("[\\p{L}0-9\\s\\-_'’]+"))
            throw new IllegalArgumentException("Nom stratégie contient des caractères invalides.");

        if (n.matches("(?i).*([\\p{L}0-9])\\1{4,}.*")) {
            throw new IllegalArgumentException("Nom stratégie non valide (trop répétitif).");
        }
        // blocks any letter repeated 5+ times in a row anywhere: "lioussssss"
        if (n.matches("(?i).*([\\p{L}])\\1{4,}.*")) {
            throw new IllegalArgumentException("Nom stratégie non valide (trop répétitif).");
        }
        // blocks any sequence of 3+ letters repeated 2+ times anywhere: "abcabcabc"
        if (n.matches("(?i).*(\\p{L}{2,3})\\1{2,}.*")) {
            throw new IllegalArgumentException("Nom stratégie non valide (trop répétitif).");
        }

        String lettersOnly = n.replaceAll("[^\\p{L}]", "").toLowerCase();
        long distinct = lettersOnly.chars().distinct().count();
        if (lettersOnly.length() >= 6 && distinct <= 2)
            throw new IllegalArgumentException("Nom stratégie non valide (trop aléatoire).");

        if (!n.toLowerCase().matches(".*[aeiouyàâäéèêëîïôöùûü].*"))
            throw new IllegalArgumentException("Nom stratégie non valide (doit ressembler à un mot).");

        return n; // ✅ return cleaned valid name
    }

    private String UniqueStrategie(String nomStrategie, int id) {
        Strategie existing = serviceStrategie.getStrategieByNom(nomStrategie);
        if (existing != null && existing.getId() != id) {
            return nomStrategie + " (doublon)";
        }
        return nomStrategie;
    }

    private double parsePositiveDouble(String text, String s) {
        try {
            double v = Double.parseDouble(required(text, s + " obligatoire."));
            if (v < 0) {
                throw new IllegalArgumentException(s + " doit etre >= 0.");
            }
            if (v > 1_000_000_000) {
                throw new IllegalArgumentException(s + " c'est exagéré veuillez verifier.");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(s + " invalide.");
        }
    }

    @FXML
    private void close() {
        if (onClose != null) {
            onClose.run();
        }
        if (nomField != null && nomField.getScene() != null && nomField.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }

    private void selectProjectById(int projectId) {
        if (projectId <= 0) {
            projetCombo.getSelectionModel().clearSelection();
            return;
        }
        ObservableList<Project> projects = projetCombo.getItems();
        for (Project p : projects) {
            if (p.getIdProj() == projectId) {

                projetCombo.setValue(p);
                break;
            }
        }
    }

    private String required(String value, String msg) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(msg);
        }

        return value.trim();

    }

    private int parsePositiveInt(String value, String field) {
        try {
            int v = Integer.parseInt(required(value, field + " obligatoire."));
            if (v <= 0) {
                throw new IllegalArgumentException(field + " doit etre > 0.");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " invalide.");
        }
    }



    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
    private void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
    private String askJustification(String riskMessage) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Risk Warning - Justification Required");
        dialog.setHeaderText("This strategy may be impacted by external events.\nPlease provide a justification to continue.");

        ButtonType okBtn = new ButtonType("Save with justification", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        // Risk details (read-only)
        TextArea riskArea = new TextArea(riskMessage);
        riskArea.setEditable(false);
        riskArea.setWrapText(true);
        riskArea.setPrefRowCount(10);

        // Justification input
        TextArea justArea = new TextArea();
        justArea.setPromptText("Explain why this strategy is still safe + mitigation steps...");
        justArea.setWrapText(true);
        justArea.setPrefRowCount(6);

        VBox content = new VBox(10,
                new Label("Detected risks:"),
                riskArea,
                new Label("Your justification:"),
                justArea
        );
        VBox.setVgrow(riskArea, Priority.ALWAYS);
        VBox.setVgrow(justArea, Priority.ALWAYS);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> btn == okBtn ? justArea.getText() : null);

        return dialog.showAndWait().orElse(null);
    }
}
