package com.advisora.test;

import com.advisora.Util.DB;
import com.advisora.Services.ServiceStrategie;
import com.advisora.Model.Strategie;
import com.advisora.enums.StrategyStatut;

import java.time.LocalDateTime;
import java.util.List;

public class Main {
    public static void main(String[] args) {
       DB.testConnectionOrExit();
       ServiceStrategie ss = new ServiceStrategie();
         Strategie s = new Strategie();
         s.setNomStrategie("Test");
         s.setVersion(1);
         s.setStatut(StrategyStatut.En_cours);
         s.setCreatedAt(LocalDateTime.now());
         s.setLockedAt(null);
         s.setCost(100.0);
         s.setNews("Test news");
         ss.ajouter(s);
        System.out.println("\n=== LIST AFTER ADD ===");
        // Print the list returned by afficher()
        List<Strategie> allAfterAdd = ss.afficher();
        System.out.println(allAfterAdd);
        System.out.println("\n=== STRATEGY BY ID ===");
        System.out.println(ss.getById("Test"));
        System.out.println("\n=== STRATEGIES BY STATUS ===");
        // Print the list returned by getByStatut()
        System.out.println(ss.getByStatut(StrategyStatut.En_cours));
        System.out.println("\n=== LIST AFTER UPDATE ===");
        s.setCost(150.0);
        ss.modifier(s);
        List<Strategie> allAfterUpdate = ss.afficher();
        System.out.println(allAfterUpdate);
        System.out.println("\n=== LIST AFTER DELETE ===");
        ss.supprimer(s);
        List<Strategie> allAfterDelete = ss.afficher();
        System.out.println(allAfterDelete);

    }
}
