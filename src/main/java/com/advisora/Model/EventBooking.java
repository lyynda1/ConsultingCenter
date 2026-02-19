package com.advisora.Model;

import java.time.LocalDateTime;

public class EventBooking {
    private int idBk;
    private LocalDateTime bookingDate;
    private int numTicketBk;
    private double totalPrixBk;
    private int idEv;
    private String eventTitle;
    private LocalDateTime eventStart;
    private LocalDateTime eventEnd;
    private int idUser;
    private String clientName;

    public int getIdBk() {
        return idBk;
    }

    public void setIdBk(int idBk) {
        this.idBk = idBk;
    }

    public LocalDateTime getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDateTime bookingDate) {
        this.bookingDate = bookingDate;
    }

    public int getNumTicketBk() {
        return numTicketBk;
    }

    public void setNumTicketBk(int numTicketBk) {
        this.numTicketBk = numTicketBk;
    }

    public double getTotalPrixBk() {
        return totalPrixBk;
    }

    public void setTotalPrixBk(double totalPrixBk) {
        this.totalPrixBk = totalPrixBk;
    }

    public int getIdEv() {
        return idEv;
    }

    public void setIdEv(int idEv) {
        this.idEv = idEv;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public LocalDateTime getEventStart() {
        return eventStart;
    }

    public void setEventStart(LocalDateTime eventStart) {
        this.eventStart = eventStart;
    }

    public LocalDateTime getEventEnd() {
        return eventEnd;
    }

    public void setEventEnd(LocalDateTime eventEnd) {
        this.eventEnd = eventEnd;
    }

    public int getIdUser() {
        return idUser;
    }

    public void setIdUser(int idUser) {
        this.idUser = idUser;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
}
