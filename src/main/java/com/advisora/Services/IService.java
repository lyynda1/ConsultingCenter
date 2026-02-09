package com.advisora.Services;

import java.util.List;

public interface IService<T> {
    public void add(T t);
    public List<T> afficher();
    public void modifier(T t);
    public void supprimer(T t);

}