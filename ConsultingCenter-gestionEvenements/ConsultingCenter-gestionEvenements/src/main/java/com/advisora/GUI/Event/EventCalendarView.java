package com.advisora.GUI.Event;

import com.advisora.Model.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventCalendarView {
    
    private final Stage stage;
    private final GridPane calendarGrid;
    private final Label lblMonth;
    private YearMonth currentMonth;
    private List<Event> events;
    private final Map<LocalDate, Integer> eventCountByDate = new HashMap<>();
    
    public EventCalendarView() {
        stage = new Stage();
        stage.setTitle("📅 Calendrier des evenements");
        stage.setWidth(1100);
        stage.setHeight(750);
        stage.setResizable(true);
        
        currentMonth = YearMonth.now();
        calendarGrid = new GridPane();
        calendarGrid.setStyle("-fx-padding: 15px; -fx-hgap: 2px; -fx-vgap: 2px; -fx-background-color: #f8f9fa;");
        
        // Header with month navigation and title
        lblMonth = new Label();
        lblMonth.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        Button btnPrev = new Button("◀");
        btnPrev.setStyle("-fx-font-size: 14px; -fx-padding: 8px 12px; -fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-border-radius: 4;");
        btnPrev.setOnAction(e -> {
            currentMonth = currentMonth.minusMonths(1);
            refreshCalendar();
        });
        
        Button btnNext = new Button("▶");
        btnNext.setStyle("-fx-font-size: 14px; -fx-padding: 8px 12px; -fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-border-radius: 4;");
        btnNext.setOnAction(e -> {
            currentMonth = currentMonth.plusMonths(1);
            refreshCalendar();
        });
        
        Button btnToday = new Button("Aujourd'hui");
        btnToday.setStyle("-fx-font-size: 12px; -fx-padding: 8px 16px; -fx-background-color: #3498db; -fx-text-fill: white; -fx-border-radius: 4; -fx-font-weight: bold;");
        btnToday.setOnAction(e -> {
            currentMonth = YearMonth.now();
            refreshCalendar();
        });
        
        HBox navigation = new HBox(15, btnPrev, lblMonth, btnNext, btnToday);
        navigation.setStyle("-fx-padding: 15px 20px; -fx-alignment: center-left; -fx-background-color: #ecf0f1; -fx-border-color: #bdc3c7; -fx-border-width: 0 0 1 0;");
        
        ScrollPane scrollPane = new ScrollPane(calendarGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background-color: #f8f9fa;");
        
        // Footer
        Label lblInfo = new Label("Les evenements sont surlignes en bleu. Cliquez sur \"Fermer\" pour quitter.");
        lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d; -fx-padding: 10px;");
        
        Button btnClose = new Button("Fermer");
        btnClose.setStyle("-fx-font-size: 12px; -fx-padding: 8px 20px; -fx-background-color: #95a5a6; -fx-text-fill: white; -fx-border-radius: 4; -fx-font-weight: bold;");
        btnClose.setOnAction(e -> stage.close());
        
        HBox footer = new HBox(20, lblInfo, btnClose);
        footer.setStyle("-fx-padding: 10px 20px; -fx-alignment: center-right; -fx-background-color: #ecf0f1;");
        
        // Layout
        BorderPane root = new BorderPane();
        root.setTop(navigation);
        root.setCenter(scrollPane);
        root.setBottom(footer);
        
        Scene scene = new Scene(root);
        stage.setScene(scene);
    }
    
    public void showCalendar(List<Event> eventList) {
        this.events = eventList;
        indexEvents();
        refreshCalendar();
        stage.show();
    }
    
    private void indexEvents() {
        eventCountByDate.clear();
        if (events == null) return;
        for (Event e : events) {
            if (e.getStartDateEv() != null) {
                LocalDate date = e.getStartDateEv().toLocalDate();
                eventCountByDate.put(date, eventCountByDate.getOrDefault(date, 0) + 1);
            }
        }
    }
    
    private void refreshCalendar() {
        lblMonth.setText(currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH).substring(0, 1).toUpperCase() + 
                        currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH).substring(1) + 
                        " " + currentMonth.getYear());
        
        calendarGrid.getChildren().clear();
        
        // Day headers (Mon-Sun)
        String[] days = {"Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"};
        for (int i = 0; i < 7; i++) {
            Label dayLabel = new Label(days[i]);
            String headerStyle = "-fx-font-weight: bold; -fx-padding: 12px; -fx-text-alignment: center; " +
                    "-fx-font-size: 13px; -fx-text-fill: white; -fx-background-color: #34495e;";
            if (i >= 5) { // Saturday and Sunday
                headerStyle = "-fx-font-weight: bold; -fx-padding: 12px; -fx-text-alignment: center; " +
                        "-fx-font-size: 13px; -fx-text-fill: white; -fx-background-color: #e74c3c;";
            }
            dayLabel.setStyle(headerStyle);
            dayLabel.setPrefWidth(150);
            dayLabel.setPrefHeight(35);
            calendarGrid.add(dayLabel, i, 0);
        }
        
        // Calculate grid positioning
        LocalDate firstDay = currentMonth.atDay(1);
        int startDayOfWeek = firstDay.getDayOfWeek().getValue() - 1; // Monday = 0
        int daysInMonth = currentMonth.lengthOfMonth();
        
        int row = 1;
        int col = startDayOfWeek;
        
        // Add day cells
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentMonth.atDay(day);
            VBox dayCell = createDayCell(date);
            
            calendarGrid.add(dayCell, col, row);
            col++;
            
            if (col == 7) {
                col = 0;
                row++;
            }
        }
    }
    
    private VBox createDayCell(LocalDate date) {
        VBox cell = new VBox(4);
        cell.setPrefHeight(120);
        cell.setPrefWidth(150);
        cell.setPadding(new Insets(8));
        cell.setStyle("-fx-border-color: #bdc3c7; -fx-border-width: 1; -fx-background-color: #ffffff;");
        
        boolean isWeekend = date.getDayOfWeek().getValue() > 5;
        boolean isToday = date.equals(LocalDate.now());
        boolean hasEvents = eventCountByDate.getOrDefault(date, 0) > 0;
        
        // Day number with styling
        Label dayNumber = new Label(String.valueOf(date.getDayOfMonth()));
        dayNumber.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");
        
        // Apply highlighting
        if (isToday) {
            cell.setStyle("-fx-border-color: #3498db; -fx-border-width: 2; -fx-background-color: #ebf5fb; -fx-padding: 8; -fx-border-radius: 4;");
            dayNumber.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #3498db; -fx-background-color: #d6eaf8; -fx-padding: 4 8; -fx-border-radius: 2;");
        } else if (isWeekend) {
            cell.setStyle("-fx-border-color: #bdc3c7; -fx-border-width: 1; -fx-background-color: #f5f5f5;");
        } else if (hasEvents) {
            cell.setStyle("-fx-border-color: #27ae60; -fx-border-width: 2; -fx-background-color: #eafaf1;");
        }
        
        cell.getChildren().add(dayNumber);
        
        // Add event count badge
        int eventCount = eventCountByDate.getOrDefault(date, 0);
        if (eventCount > 0) {
            Label eventCountLabel = new Label(eventCount + " evenement" + (eventCount > 1 ? "s" : ""));
            eventCountLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
            cell.getChildren().add(eventCountLabel);
            
            // Add event titles (max 2)
            int count = 0;
            if (events != null) {
                for (Event event : events) {
                    if (count >= 2) break;
                    if (event.getStartDateEv() != null && event.getStartDateEv().toLocalDate().equals(date)) {
                        Label eventLabel = new Label("• " + shortenText(event.getTitleEv(), 18));
                        eventLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: white; " +
                                "-fx-background-color: #3498db; -fx-padding: 3 6; -fx-border-radius: 2; -fx-wrap-text: true;");
                        eventLabel.setWrapText(true);
                        cell.getChildren().add(eventLabel);
                        count++;
                    }
                }
            }
        }
        
        cell.setAlignment(Pos.TOP_LEFT);
        return cell;
    }
    
    private String shortenText(String text, int maxLength) {
        if (text == null || text.isEmpty()) return "";
        return text.length() > maxLength ? text.substring(0, maxLength - 1) + "..." : text;
    }
}

