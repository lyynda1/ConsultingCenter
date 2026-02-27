package com.advisora.GUI.Project;

import com.advisora.Model.projet.NewsArticle;
import com.advisora.Model.projet.Project;
import com.advisora.Services.projet.NewsService;
import com.advisora.Services.projet.NewsService.NewsErrorType;
import com.advisora.Services.projet.NewsService.NewsServiceException;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

public class NewsController {

    @FXML
    private Label lblProjectTitle;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblEmpty;
    @FXML
    private ProgressIndicator loadingIndicator;
    @FXML
    private ListView<NewsArticle> newsList;
    @FXML
    private Button btnRefresh;

    private Project project;
    private NewsService newsService;
    private Runnable onClose;

    @FXML
    private void initialize() {
        newsList.setCellFactory(list -> new NewsArticleCell(this::openArticle));
        showIdleState();
    }

    public void initWithProject(Project project, NewsService newsService) {
        this.project = project;
        this.newsService = newsService == null ? new NewsService() : newsService;
        lblProjectTitle.setText("News for: " + safe(project == null ? null : project.getTitleProj()));
        loadNews(false);
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @FXML
    private void onRefresh() {
        loadNews(true);
    }

    @FXML
    private void onClose() {
        if (onClose != null) {
            onClose.run();
        }
    }

    private void loadNews(boolean forceRefresh) {
        if (project == null || newsService == null) {
            showError("Cannot load news: missing project context.");
            return;
        }

        Task<List<NewsArticle>> task = new Task<>() {
            @Override
            protected List<NewsArticle> call() {
                return newsService.fetchNews(project, forceRefresh);
            }
        };

        task.setOnRunning(evt -> showLoading());
        task.setOnSucceeded(evt -> showArticles(task.getValue()));
        task.setOnFailed(evt -> {
            Throwable ex = task.getException();
            if (ex instanceof NewsServiceException nse) {
                showNewsError(nse);
            } else {
                showError("Unexpected error while loading news.");
            }
        });

        Thread worker = new Thread(task, "news-loader-" + project.getIdProj());
        worker.setDaemon(true);
        worker.start();
    }

    private void showLoading() {
        loadingIndicator.setVisible(true);
        loadingIndicator.setManaged(true);
        lblEmpty.setVisible(false);
        lblEmpty.setManaged(false);
        lblStatus.setVisible(false);
        lblStatus.setManaged(false);
        newsList.setDisable(true);
        btnRefresh.setDisable(true);
    }

    private void showArticles(List<NewsArticle> articles) {
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        btnRefresh.setDisable(false);
        newsList.setDisable(false);

        List<NewsArticle> safe = articles == null ? List.of() : articles;
        newsList.setItems(FXCollections.observableArrayList(safe));

        boolean empty = safe.isEmpty();
        lblEmpty.setVisible(empty);
        lblEmpty.setManaged(empty);
        lblStatus.setVisible(false);
        lblStatus.setManaged(false);
    }

    private void showError(String message) {
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        newsList.setDisable(false);
        btnRefresh.setDisable(false);
        newsList.setItems(FXCollections.observableArrayList());
        lblEmpty.setVisible(false);
        lblEmpty.setManaged(false);
        lblStatus.setText(safe(message));
        lblStatus.setVisible(true);
        lblStatus.setManaged(true);
    }

    private void showNewsError(NewsServiceException ex) {
        if (ex.getType() == NewsErrorType.NO_RESULTS) {
            loadingIndicator.setVisible(false);
            loadingIndicator.setManaged(false);
            newsList.setItems(FXCollections.observableArrayList());
            newsList.setDisable(false);
            btnRefresh.setDisable(false);
            lblStatus.setVisible(false);
            lblStatus.setManaged(false);
            lblEmpty.setText("No related news found for this project/category.");
            lblEmpty.setVisible(true);
            lblEmpty.setManaged(true);
            return;
        }
        if (ex.getType() == NewsErrorType.RATE_LIMIT) {
            showError("News API rate limit reached. Please try again shortly.");
            return;
        }
        if (ex.getType() == NewsErrorType.NETWORK) {
            showError("Network error while loading news. Check connection and retry.");
            return;
        }
        if (ex.getType() == NewsErrorType.CONFIG) {
            showError("Configuration missing: GNEWS_API_KEY (or JVM -Dgnews.api.key).");
            return;
        }
        if (ex.getType() == NewsErrorType.UNAUTHORIZED) {
            showError("Unauthorized News API request. Check GNEWS_API_KEY.");
            return;
        }
        showError(safe(ex.getMessage()));
    }

    private void showIdleState() {
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        lblStatus.setVisible(false);
        lblStatus.setManaged(false);
        lblEmpty.setVisible(false);
        lblEmpty.setManaged(false);
    }

    private void openArticle(NewsArticle article) {
        if (article == null || !article.hasValidUrl()) {
            showAlert("Unable to open article link.");
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                showAlert("Unable to open article link.");
                return;
            }
            Desktop.getDesktop().browse(URI.create(article.getUrl()));
        } catch (Exception e) {
            showAlert("Unable to open article link.");
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private static final class NewsArticleCell extends ListCell<NewsArticle> {
        private final ArticleOpener opener;

        private NewsArticleCell(ArticleOpener opener) {
            this.opener = opener;
        }

        @Override
        protected void updateItem(NewsArticle item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label title = new Label(item.getDisplayTitle());
            title.getStyleClass().add("news-title");
            title.setWrapText(true);

            Label source = new Label("Source: " + item.getDisplaySource());
            source.getStyleClass().add("news-meta");

            Label date = new Label("Published: " + item.getDisplayPublishedAt());
            date.getStyleClass().add("news-meta");

            Label desc = new Label(item.getDisplayDescription());
            desc.getStyleClass().add("news-description");
            desc.setWrapText(true);

            Button open = new Button("Open Article");
            open.getStyleClass().addAll("btn-ghost", "news-open-btn");
            open.setOnAction(e -> opener.open(item));

            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            HBox footer = new HBox(8, source, spacer, open);
            footer.getStyleClass().add("news-footer");

            VBox card = new VBox(6, title, date, desc, footer);
            card.getStyleClass().add("news-item-card");
            setText(null);
            setGraphic(card);
        }
    }

    @FunctionalInterface
    private interface ArticleOpener {
        void open(NewsArticle article);
    }
}
