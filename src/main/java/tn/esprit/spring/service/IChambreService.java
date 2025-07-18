package tn.esprit.spring.service;

import tn.esprit.spring.entity.Chambre;

import java.util.List;

public interface IChambreService {
    List<Chambre> retrieveAllChambres();
    Chambre addChambre(Chambre chambre);
    Chambre retrieveChambre(Long idChambre);
}

