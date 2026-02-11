package com.advisora;

import com.advisora.Model.Strategie;
import com.advisora.Services.ServiceStrategie;
import com.advisora.Util.DB;
import com.advisora.enums.StrategyStatut;

import java.time.LocalDateTime;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        // TEST DATABASE CONNECTION
        DB.testConnectionOrExit();

        ServiceStrategie ss = new ServiceStrategie();

        // 1) ADD STRATEGIES
        Strategie s1 = new Strategie(
                "Growth Strategy 2024",
                1,
                StrategyStatut.En_cours,
                LocalDateTime.now(),
                null,
                15000.50,
                "Market expansion plan for Q1"
        );

        Strategie s2 = new Strategie(
                "Cost Reduction Initiative",
                1,
                StrategyStatut.En_cours,
                LocalDateTime.now(),
                null,
                8500.00,
                "Optimize operational costs"
        );

        ss.ajouter(s1);
        ss.ajouter(s2);
        System.out.println("✅ Added strategy 1 with id=" + s1.getId());
        System.out.println("✅ Added strategy 2 with id=" + s2.getId());

        // 2) DISPLAY ALL STRATEGIES
        System.out.println("\n=== LIST AFTER ADD ===");
        List<Strategie> list1 = ss.afficher();
        list1.forEach(System.out::println);

        // 3) MODIFY STRATEGY
        s1.setNomStrategie("Updated Growth Strategy 2024");
        s1.setStatut(StrategyStatut.Acceptée);
        s1.setLockedAt(LocalDateTime.now());
        s1.setCost(18000.75);
        s1.setNews("Strategy approved and budget increased");
        System.out.println("\nDEBUG s1.id = " + s1.getId());

        ss.modifier(s1);

        System.out.println("\n=== LIST AFTER MODIFY ===");
        List<Strategie> list2 = ss.afficher();
        list2.forEach(System.out::println);

        // 4) GET BY STATUS
        System.out.println("\n=== STRATEGIES WITH STATUS 'accepte' ===");
        List<Strategie> acceptedStrategies = ss.getByStatut(StrategyStatut.Acceptée);
        acceptedStrategies.forEach(System.out::println);

        // 5) DELETE STRATEGY
         ss.supprimer(s2);
         System.out.println("\n✅ Deleted strategy id=" + s2.getId());

         System.out.println("\n=== LIST AFTER DELETE ===");
         List<Strategie> list3 = ss.afficher();
         list3.forEach(System.out::println);
    }
}
