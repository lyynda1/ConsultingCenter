package com.advisora.GUI.Event;

import com.advisora.Model.Event;
import com.advisora.Services.EventService;
import com.advisora.Services.SessionContext;
import com.advisora.enums.UserRole;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class EventFormController {
    @FXML private TextField txtTitle;
    @FXML private TextArea txtDescription;
    @FXML private DatePicker dateStart;
    @FXML private TextField txtStartTime;
    @FXML private DatePicker dateEnd;
    @FXML private TextField txtEndTime;
    @FXML private TextField txtOrganiser;
    @FXML private TextField txtCapacity;
    @FXML private TextField txtLocation;

    private final EventService service = new EventService();
    private Event current;
    private boolean editMode;

    public void initForCreate() {
        this.editMode = false;
        this.current = null;
    }

    public void initForEdit(Event event) {
        this.editMode = true;
        this.current = event;

        txtTitle.setText(event.getTitleEv());
        txtDescription.setText(event.getDescriptionEv());
        if (event.getStartDateEv() != null) {
            dateStart.setValue(event.getStartDateEv().toLocalDate());
            txtStartTime.setText(event.getStartDateEv().toLocalTime().toString());
        }
        if (event.getEndDateEv() != null) {
            dateEnd.setValue(event.getEndDateEv().toLocalDate());
            txtEndTime.setText(event.getEndDateEv().toLocalTime().toString());
        }
        txtOrganiser.setText(event.getOrganisateurName());
        txtCapacity.setText(String.valueOf(event.getCapaciteEvnt()));
        txtLocation.setText(event.getLocalisationEv());
    }

    @FXML
    private void onSave() {
        try {
            Event e = current == null ? new Event() : current;
            e.setTitleEv(required(txtTitle.getText(), "Titre obligatoire"));
            e.setDescriptionEv(required(txtDescription.getText(), "Veuillez fournir une description"));
            e.setStartDateEv(parseDateTime(dateStart, txtStartTime, "Date/heure debut obligatoire"));
            e.setEndDateEv(parseDateTime(dateEnd, txtEndTime, "Date/heure fin obligatoire"));
            e.setOrganisateurName(required(txtOrganiser.getText(), "Nom organisateur requis"));
            e.setCapaciteEvnt(parsePositiveInt(txtCapacity.getText(), "Capacite invalide"));
            e.setLocalisationEv(required(txtLocation.getText(), "veuillez fournir une localisation"));

            UserRole role = SessionContext.getCurrentRole();
            if (role == UserRole.ADMIN || role == UserRole.GERANT) {
                e.setIdGerant(SessionContext.getCurrentUserId());
            }

            if (editMode) {
                service.update(e);
            } else {
                service.add(e);
            }
            close();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        close();
    }

    private LocalDateTime parseDateTime(DatePicker datePicker, TextField timeField, String msg) {
        LocalDate date = datePicker.getValue();
        if (date == null) {
            throw new IllegalArgumentException(msg);
        }
        String timeText = timeField.getText();
        if (timeText == null || timeText.trim().isEmpty()) {
            throw new IllegalArgumentException(msg);
        }
        LocalTime time = parseTime(timeText.trim());
        return LocalDateTime.of(date, time);
    }

    private LocalTime parseTime(String value) {
        DateTimeFormatter shortFmt = DateTimeFormatter.ofPattern("H:mm");
        DateTimeFormatter longFmt = DateTimeFormatter.ofPattern("H:mm:ss");
        try {
            return LocalTime.parse(value, longFmt);
        } catch (DateTimeParseException ex) {
            try {
                return LocalTime.parse(value, shortFmt);
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("Format heure invalide (HH:mm).");
            }
        }
    }

    private String required(String value, String msg) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(msg);
        }
        return value.trim();
    }

    private String safe(String value) {
        return value == null ? null : value.trim();
    }

    private int parsePositiveInt(String value, String msg) {
        try {
            int v = Integer.parseInt(required(value, msg));
            if (v <= 0) {
                throw new IllegalArgumentException(msg);
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(msg);
        }
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Form evenement");
        a.showAndWait();
    }

    private void close() {
        Stage stage = (Stage) txtTitle.getScene().getWindow();
        stage.close();
    }
}
