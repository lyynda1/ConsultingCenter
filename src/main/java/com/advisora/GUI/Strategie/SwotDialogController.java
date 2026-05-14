package com.advisora.GUI.Strategie;



import com.advisora.Model.strategie.LlmDecision;
import com.advisora.Model.strategie.SWOTItem;
import com.advisora.Model.strategie.Strategie;
import com.advisora.Services.strategie.serviceSWOT;
import com.advisora.enums.SWOTType;
import com.advisora.utils.news.OllamaClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class SwotDialogController {

    @FXML private Label dragHandle;
    @FXML private Label lblStrategieTitle;

    @FXML private ComboBox<SWOTType> cmbType;
    @FXML private TextField txtDesc;
    @FXML private ComboBox<Integer> cmbWeight;

    @FXML private FlowPane flowStrengths;
    @FXML private FlowPane flowWeaknesses;
    @FXML private FlowPane flowOpportunities;
    @FXML private FlowPane flowThreats;

    @FXML private Label lblCountS, lblCountW, lblCountO, lblCountT;
    private static final ObjectMapper OM = new ObjectMapper();

    private final serviceSWOT swotService = new serviceSWOT();
    private Strategie strategie;
    private final OllamaClient llm = new OllamaClient(
            System.getenv("GEMINI_API_KEY"),  // ou ton SecretConfig.get(...)
            "gemini-1.5-flash"
    );


    private Runnable onClose = () -> {};
    private Runnable onSaved = () -> {};



    public void setOnClose(Runnable r) { this.onClose = (r == null) ? () -> {} : r; }
    public void setOnSaved(Runnable r) { this.onSaved = (r == null) ? () -> {} : r; }
    public Node getDragHandle() { return dragHandle; }

    public void setStrategie(Strategie s) {
        this.strategie = s;
        lblStrategieTitle.setText("Stratégie : " + (s == null ? "-" : s.getNomStrategie()));

        reload();
    }

    @FXML
    private void onClearAll() {
        if (strategie == null) return;

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Supprimer tous les éléments SWOT de cette stratégie ?\nCette action est irréversible.",
                ButtonType.OK, ButtonType.CANCEL
        );
        confirm.setHeaderText("Vider SWOT");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            swotService.deleteAllByStrategie(strategie.getId());
            reload();       // recharge les chips/cards (devient vide)
            onSaved.run();  // pour refresh la liste principale (SWOT count etc.)
        } catch (Exception e) {
            e.printStackTrace();
            showError("Impossible de vider SWOT: " + e.getMessage());
        }
    }
    static class SwotGenResponse {
        public List<Item> strengths;
        public List<Item> weaknesses;
        public List<Item> opportunities;
        public List<Item> threats;

        static class Item {
            public String desc;
            public Integer weight;
        }
    }
    @FXML
    private void onAutoGenerate() {
        if (strategie == null) return;

        // 1) confirmer si on écrase ou on ajoute
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Générer automatiquement une SWOT.\n\n" +
                        "OK = Remplacer (vider puis générer)\n" +
                        "Annuler = Abandon",
                ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText("Auto SWOT");

        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        // 2) Task async
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {

                // ---- Build prompt with strategy info ----
                String prompt = buildSwotPrompt(strategie);

                // ---- Call LLM ----
                // TODO: adapte à ton client IA (Gemini/Ollama/etc)
                // Exemple pseudo:
                String rawJson = callLLM(prompt);

                // ---- Parse JSON strictly ----
                ObjectMapper om = new ObjectMapper();
                SwotGenResponse resp = om.readValue(rawJson, SwotGenResponse.class);

                // ---- Convert to SWOTItem list ----
                List<SWOTItem> items = new ArrayList<>();
                items.addAll(toItems(resp.strengths, SWOTType.STRENGTH));
                items.addAll(toItems(resp.weaknesses, SWOTType.WEAKNESS));
                items.addAll(toItems(resp.opportunities, SWOTType.OPPORTUNITY));
                items.addAll(toItems(resp.threats, SWOTType.THREAT));

                // ---- Replace: delete all then insert many ----
                swotService.deleteAllByStrategie(strategie.getId());
                swotService.addMany(items);

                return null;
            }
        };

        task.setOnSucceeded(ev -> {
            reload();
            onSaved.run();
            new Alert(Alert.AlertType.INFORMATION, "SWOT générée ✅", ButtonType.OK).showAndWait();
        });

        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            ex.printStackTrace();
            showError("Génération impossible: " + (ex == null ? "Erreur inconnue" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    private String buildSwotPrompt(Strategie s) {
        String proj = (s.getProjet() == null) ? "-" : s.getProjet().getTitleProj();
        String type = (s.getTypeStrategie() == null) ? "-" : s.getTypeStrategie().name();
        String justif = (s.getJustification() == null) ? "" : s.getJustification().trim();

        return """
Tu es un consultant stratégie. Génère une analyse SWOT pertinente.

Contexte:
- Nom: %s
- Projet: %s
- Type: %s
- Budget: %s
- Gain estimé: %s
- Justification: %s

Réponds UNIQUEMENT avec un JSON valide, sans texte autour, sans ```.

Format EXACT:
{
  "strengths":[{"desc":"...","weight":1}],
  "weaknesses":[{"desc":"...","weight":1}],
  "opportunities":[{"desc":"...","weight":1}],
  "threats":[{"desc":"...","weight":1}]
}

Contraintes:
- 3 à 6 items par section.
- desc <= 14 mots.
- weight entre 1 et 5.
""".formatted(
                safe(s.getNomStrategie()),
                safe(proj),
                safe(type),
                s.getBudgetTotal(),
                s.getGainEstime(),
                justif.replace("\n", " ")
        );
    }
    private static String extractJsonObjectOrArray(String text) {
        if (text == null) return null;
        String s = text.trim();

        // cas 1: fenced code block ```json ... ```
        int fence = s.indexOf("```");
        if (fence >= 0) {
            // prendre le contenu entre le premier et le dernier ```
            int lastFence = s.lastIndexOf("```");
            if (lastFence > fence) {
                String inside = s.substring(fence + 3, lastFence).trim();
                // enlever "json" si présent au début
                if (inside.toLowerCase().startsWith("json")) {
                    inside = inside.substring(4).trim();
                }
                s = inside;
            }
        }

        // cas 2: trouver premier { ou [ et extraire jusqu’au JSON complet
        int startObj = s.indexOf('{');
        int startArr = s.indexOf('[');

        int start;
        char open;
        char close;

        if (startObj == -1 && startArr == -1) return s; // pas de JSON visible
        if (startObj == -1 || (startArr != -1 && startArr < startObj)) {
            start = startArr;
            open = '[';
            close = ']';
        } else {
            start = startObj;
            open = '{';
            close = '}';
        }

        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            } else {
                if (c == '"') {
                    inString = true;
                    continue;
                }
                if (c == open) depth++;
                if (c == close) depth--;

                if (depth == 0) {
                    return s.substring(start, i + 1).trim();
                }
            }
        }

        // fallback
        return s.substring(start).trim();
    }

    private String callLLM(String prompt) throws Exception {
        String out = llm.generate(prompt); // ✅ comme tu fais déjà
        System.out.println("[LLM RAW]\n" + out);

        // Gemini renvoie parfois du texte autour du JSON → on extrait uniquement le JSON
        String jsonOnly = extractJsonObjectOrArray(out);
        System.out.println("[LLM JSON ONLY]\n" + jsonOnly);

        // Optionnel: valider que c'est bien du JSON (pour lever une erreur claire si non)
        OM.readTree(jsonOnly);

        return jsonOnly;
    }

    private List<SWOTItem> toItems(List<SwotGenResponse.Item> src, SWOTType type) {
        if (src == null) return List.of();
        List<SWOTItem> out = new ArrayList<>();
        for (SwotGenResponse.Item x : src) {
            if (x == null) continue;
            String d = (x.desc == null) ? "" : x.desc.trim();
            if (d.isBlank()) continue;

            SWOTItem it = new SWOTItem();
            it.setStrategieId(strategie.getId());
            it.setType(type);
            it.setDescription(d);
            Integer w = x.weight;
            if (w != null) {
                w = Math.max(1, Math.min(5, w));
            }
            it.setWeight(w);
            out.add(it);
        }
        return out;
    }

    private String safe(String v) {
        return v == null ? "-" : v.trim();
    }

    @FXML
    public void initialize() {
        cmbType.getItems().setAll(SWOTType.values());
        cmbWeight.getItems().setAll(1,2,3,4,5);
        cmbType.setValue(SWOTType.STRENGTH);

    }

    @FXML
    private void onAdd() {
        if (strategie == null) return;

        SWOTType type = cmbType.getValue();
        String desc = txtDesc.getText() == null ? "" : txtDesc.getText().trim();
        Integer weight = cmbWeight.getValue();

        if (type == null) {
            showWarn("Choisis un type SWOT.");
            return;
        }
        if (desc.isBlank()) {
            showWarn("Écris une description.");
            return;
        }

        SWOTItem item = new SWOTItem();
        item.setStrategieId(strategie.getId());
        item.setType(type);
        item.setDescription(desc);
        item.setWeight(weight);

        try {
            swotService.add(item);
            txtDesc.clear();
            reload();
            onSaved.run();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Ajout impossible: " + e.getMessage());
        }
    }

    private void reload() {
        if (strategie == null) return;

        Map<SWOTType, List<SWOTItem>> grouped = swotService.getGroupedByStrategie(strategie.getId());

        render(flowStrengths, grouped.get(SWOTType.STRENGTH), lblCountS);
        render(flowWeaknesses, grouped.get(SWOTType.WEAKNESS), lblCountW);
        render(flowOpportunities, grouped.get(SWOTType.OPPORTUNITY), lblCountO);
        render(flowThreats, grouped.get(SWOTType.THREAT), lblCountT);
    }

    private void render(FlowPane target, List<SWOTItem> items, Label countLabel) {
        target.getChildren().clear();

        int count = (items == null) ? 0 : items.size();
        countLabel.setText(String.valueOf(count));

        if (items == null || items.isEmpty()) {
            Label empty = new Label("Aucun élément");
            empty.getStyleClass().add("swot-empty");
            target.getChildren().add(empty);
            return;
        }

        for (SWOTItem it : items) {
            VBox chip = buildChip(it);
            target.getChildren().add(chip);
            animateIn(chip); // ✅ transition here
        }
    }
    private void animateIn(Node node) {
        // Fade in
        FadeTransition ft = new FadeTransition(Duration.millis(220), node);
        ft.setFromValue(0);
        ft.setToValue(1);

        // Slight slide up
        TranslateTransition tt = new TranslateTransition(Duration.millis(220), node);
        tt.setFromY(6);
        tt.setToY(0);

        ft.play();
        tt.play();
    }

    private VBox buildChip(SWOTItem it) {
        // chip container
        VBox chip = new VBox(6);
        chip.getStyleClass().add("swot-chip");

        switch (it.getType()) {
            case STRENGTH -> chip.getStyleClass().add("swot-strength");
            case WEAKNESS -> chip.getStyleClass().add("swot-weakness");
            case OPPORTUNITY -> chip.getStyleClass().add("swot-opportunity");
            case THREAT -> chip.getStyleClass().add("swot-threat");
        }

        Label text = new Label(it.getDescription());
        text.setWrapText(true);
        text.getStyleClass().add("swot-chip-text");

        HBox row = new HBox(8);
        row.getStyleClass().add("swot-chip-actions");

        Label w = new Label(it.getWeight() == null ? "" : ("Poids " + it.getWeight()));
        w.getStyleClass().add("swot-chip-weight");

        Button edit = new Button("✎");
        edit.getStyleClass().add("chip-btn");
        edit.setOnAction(e -> editItem(it));

        Button del = new Button("🗑");
        del.getStyleClass().add("chip-btn-danger");
        del.setOnAction(e -> deleteItem(it));

        row.getChildren().addAll(w, edit, del);

        chip.getChildren().addAll(text, row);
        return chip;
    }

    private void editItem(SWOTItem it) {
        TextInputDialog d = new TextInputDialog(it.getDescription());
        d.setTitle("Modifier SWOT");
        d.setHeaderText(it.getType().labelFR());
        d.setContentText("Description:");

        String newDesc = d.showAndWait().orElse("").trim();
        if (newDesc.isBlank()) return;

        it.setDescription(newDesc);

        try {
            swotService.update(it);
            reload();
            onSaved.run();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Modification impossible: " + e.getMessage());
        }
    }

    private void deleteItem(SWOTItem it) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Supprimer cet élément SWOT ?",
                ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText("Confirmation");

        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            swotService.delete(it.getId());
            reload();
            onSaved.run();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Suppression impossible: " + e.getMessage());
        }
    }

    @FXML
    private void onClose() {
        onClose.run();
    }

    private void showWarn(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText("SWOT");
        a.showAndWait();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("SWOT");
        a.showAndWait();
    }
}