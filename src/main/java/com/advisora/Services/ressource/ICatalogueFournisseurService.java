package com.advisora.Services.ressource;

import com.advisora.Model.ressource.CatalogueFournisseur;
import com.advisora.Services.IService;

public interface ICatalogueFournisseurService extends IService<CatalogueFournisseur> {
    CatalogueFournisseur getById(int idFr);
    boolean existsByName(String name, Integer excludeId);
}

