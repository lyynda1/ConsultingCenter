package com.advisora.Services;

import com.advisora.Model.Ressource;

import java.util.Map;
import java.util.List;

public interface IRessourceService extends IService<Ressource> {
    List<Ressource> getByFournisseur(int idFr);
    Ressource getById(int idRs);
    int getReservedStock(int idRs);
    int getAvailableStock(int idRs);
    Map<Integer, Integer> getReservedStockBulk(List<Integer> resourceIds);
    Map<Integer, Integer> getAvailableStockBulk(List<Ressource> resources, Map<Integer, Integer> reservedMap);
}
