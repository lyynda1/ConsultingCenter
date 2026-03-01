package com.advisora.Services.ressource;

import com.advisora.Model.ressource.Booking;

import java.util.List;

public interface IReservationService {
    void reserveForClient(int clientId, int idRs, int quantity, Integer projectIdOrNull);
    void updateReservationForClient(int clientId, int idProj, int idRs, int newQuantity);
    void updateReservationAsManager(int idProj, int idRs, int newQuantity);
    void deleteReservationForClient(int clientId, int idProj, int idRs);
    void deleteReservationAsManager(int idProj, int idRs);
    List<Booking> listClientReservations(int clientId);
    List<Booking> listAllReservations();
}

