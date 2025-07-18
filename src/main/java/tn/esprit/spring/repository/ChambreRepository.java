package tn.esprit.spring.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.spring.entity.Chambre;

public interface ChambreRepository extends JpaRepository<Chambre, Long> {
}

