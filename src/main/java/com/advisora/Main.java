package com.advisora;

import com.advisora.Services.ServiceUser;
import com.advisora.entity.*;

import java.util.List;

public class Main {
    public static void main(String[] args) {

        ServiceUser us = new ServiceUser();

        // 1) ADD CLIENT
        Client c = new Client(
                0,
                "loulezeouuu@.com",
                "123",
                "Ali",
                "Salah",
                "2222",
                "2000-01-01",
                5000,
                "desc"
        );
        Client c1 = new Client(
                0,
                "lalaezuuu@.com",
                "123",
                "Ali",
                "Salah",
                "2222",
                "2000-01-01",
                5000,
                "desc"
        );

        us.ajouter(c);
        us.ajouter(c1);
        System.out.println("✅ Added client id=" + c.getId());

        // 2) AFFICHER
        System.out.println("\n=== LIST AFTER ADD ===");
        List<User> list1 = us.afficher();
        list1.forEach(System.out::println);

        // 3) MODIFY SAME ROLE (Client)
        c.setEmail("liliezeyy2@gmail.com");
        c.setDescription("updated description");
        c.setBudget(9999);
        System.out.println("DEBUG c.id = " + c.getId());

        us.modifier(c);

        System.out.println("\n=== LIST AFTER MODIFY CLIENT ===");
        List<User> list2 = us.afficher();
        list2.forEach(System.out::println);

        // 4) CHANGE ROLE: Client -> Gerant (Manager)
        Gerant g = new Gerant(
                c.getId(),            // IMPORTANT: same id
                c.getEmail(),
                c.getPassword(),
                c.getName(),
                c.getFirstName(),
                c.getPhoneNumber(),
                c.getDateN(),
                "Finance"             // expertise_area
        );
        System.out.println("DEBUG g.id = " + g.getId());
        Admin a = new Admin(
                c.getId(),
                c.getEmail(),
                c.getPassword(),
                c.getName(),
                c.getFirstName(),
                c.getPhoneNumber(),
                c.getDateN()
        );
        us.modifier(a);

        System.out.println("\n=== LIST AFTER CHANGE ROLE TO MANAGER ===");
        List<User> list3 = us.afficher();
        list3.forEach(System.out::println);

        // 5) DELETE
       // us.supprimer(g); // or us.supprimer(c); same id
        //System.out.println("\n✅ Deleted user id=" + g.getId());

        //System.out.println("\n=== LIST AFTER DELETE ===");
        //List<User> list4 = us.afficher();
        //list4.forEach(System.out::println);
    }
}
