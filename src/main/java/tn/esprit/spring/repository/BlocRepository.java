package tn.esprit.spring.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.spring.entity.Bloc;

public interface BlocRepository extends JpaRepository<Bloc, Long> {
}

