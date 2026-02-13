package com.advisora;

import com.advisora.Util.DB;
import com.advisora.Model.Investment;
import com.advisora.Model.Transaction;
import com.advisora.Services.InvestmentService;
import com.advisora.Services.TransactionService;
import com.advisora.enums.transactionStatut;

import java.sql.Date;
import java.sql.Time;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        InvestmentService investmentService = new InvestmentService();
        TransactionService transactionService = new TransactionService();

        System.out.println("=== Test Investment Module ===\n");

        // Test 1: Add investments
        System.out.println("1. Adding investments...");
        Investment inv1 = new Investment(
                "Tech Startup Investment",
                Time.valueOf("12:00:00"),
                50000.0,
                100000.0,
                "TND",
                1,
                1
        );
        investmentService.ajouter(inv1);

        Investment inv2 = new Investment(
                "Real Estate Investment",
                Time.valueOf("18:00:00"),
                80000.0,
                150000.0,
                "TND",
                1,
                1
        );
        investmentService.ajouter(inv2);

        System.out.println("✓ Investments added!\n");

        // Test 2: Display all investments
        System.out.println("2. Displaying all investments:");
        List<Investment> investments = investmentService.afficher();

        for (Investment inv : investments) {
            System.out.println(inv);
        }

        // Test 3: Add transactions
        System.out.println("\n3. Adding transactions...");
        Transaction t1 = new Transaction(
                Date.valueOf("2025-02-13"),
                25000.0,
                "Initial Funding",
                transactionStatut.SUCCESS,
                inv1.getIdInv()
        );
        transactionService.ajouter(t1);

        Transaction t2 = new Transaction(
                Date.valueOf("2025-02-14"),
                30000.0,
                "Second Round",
                transactionStatut.PENDING,
                inv1.getIdInv()
        );
        transactionService.ajouter(t2);

        System.out.println("✓ Transactions added!\n");

        // Test 4: Display all transactions
        System.out.println("4. Displaying all transactions:");
        List<Transaction> transactions = transactionService.afficher();

        for (Transaction t : transactions) {
            System.out.println(t);
        }

        // Test 5: Get last investment
        System.out.println("\n5. Last investment:");
        System.out.println(investments.get(investments.size() - 1));

        System.out.println("\n=== Test Complete ===");
    }
}