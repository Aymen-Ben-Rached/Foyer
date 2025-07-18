package tn.esprit.spring.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.spring.entity.Universite;

public interface UniversiteRepository extends JpaRepository<Universite, Long> {
}

