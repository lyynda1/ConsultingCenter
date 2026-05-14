package com.advisora.Model.event;

import com.advisora.enums.BookingStatus;

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
    private BookingStatus bookingStatus;
    private String paymentReference;
    private Double refundAmountBk;
    private LocalDateTime refundDateBk;
    private String cancelReasonBk;
    private boolean notificationSentBk;
    private String clientEmail;
    private String eventCurrencyCode;
    private String qrTokenBk;
    private String qrImagePathBk;

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

    public BookingStatus getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(BookingStatus bookingStatus) {
        this.bookingStatus = bookingStatus;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public Double getRefundAmountBk() {
        return refundAmountBk;
    }

    public void setRefundAmountBk(Double refundAmountBk) {
        this.refundAmountBk = refundAmountBk;
    }

    public LocalDateTime getRefundDateBk() {
        return refundDateBk;
    }

    public void setRefundDateBk(LocalDateTime refundDateBk) {
        this.refundDateBk = refundDateBk;
    }

    public String getCancelReasonBk() {
        return cancelReasonBk;
    }

    public void setCancelReasonBk(String cancelReasonBk) {
        this.cancelReasonBk = cancelReasonBk;
    }

    public boolean isNotificationSentBk() {
        return notificationSentBk;
    }

    public void setNotificationSentBk(boolean notificationSentBk) {
        this.notificationSentBk = notificationSentBk;
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public void setClientEmail(String clientEmail) {
        this.clientEmail = clientEmail;
    }

    public String getEventCurrencyCode() {
        return eventCurrencyCode;
    }

    public void setEventCurrencyCode(String eventCurrencyCode) {
        this.eventCurrencyCode = eventCurrencyCode;
    }

    public String getQrTokenBk() {
        return qrTokenBk;
    }

    public void setQrTokenBk(String qrTokenBk) {
        this.qrTokenBk = qrTokenBk;
    }

    public String getQrImagePathBk() {
        return qrImagePathBk;
    }

    public void setQrImagePathBk(String qrImagePathBk) {
        this.qrImagePathBk = qrImagePathBk;
    }
}

