package com.advisora.Services;

import com.advisora.Model.CatalogueFournisseur;

public interface ICatalogueFournisseurService extends IService<CatalogueFournisseur> {
    CatalogueFournisseur getById(int idFr);
    boolean existsByName(String name, Integer excludeId);
}
