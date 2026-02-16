/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: Service layer: business logic and SQL orchestration
*/
package com.advisora.Services;

import java.util.List;

public interface IService<T> {
    public void ajouter(T t);
    public List<T> afficher();
    public void modifier(T t);
    public void supprimer(T t);

}