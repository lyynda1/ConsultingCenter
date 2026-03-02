
/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: Application bootstrap/entrypoint
*/
package com.advisora;

import javafx.application.Application;

public class Main {
    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}