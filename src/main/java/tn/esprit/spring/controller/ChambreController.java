package tn.esprit.spring.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.spring.entity.Chambre;
import tn.esprit.spring.service.IChambreService;

import java.util.List;

@RestController
@RequestMapping("/chambres")
@RequiredArgsConstructor
public class ChambreController {
    private final IChambreService chambreService;

    @GetMapping
    public List<Chambre> getAllChambres() {
        return chambreService.retrieveAllChambres();
    }


    @PostMapping
    public Chambre addChambre(@RequestBody Chambre chambre) {
        return chambreService.addChambre(chambre);
    }
}

