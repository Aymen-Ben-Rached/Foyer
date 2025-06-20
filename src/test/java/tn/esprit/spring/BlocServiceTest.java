package tn.esprit.spring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.spring.DAO.Entities.*;
import tn.esprit.spring.DAO.Repositories.*;
import tn.esprit.spring.Services.Bloc.BlocService;

@ExtendWith(MockitoExtension.class)
class BlocServiceTest {

    @Mock BlocRepository      blocRepository;
    @Mock ChambreRepository   chambreRepository;
    @Mock FoyerRepository     foyerRepository;

    @InjectMocks BlocService  blocService;

    @Test
    @DisplayName("addOrUpdate saves the bloc and assigns its chambres")
    void addOrUpdate_should_save_bloc_and_chambres() {
        // ── Arrange ──────────────────────────────────────────────────────
        Bloc bloc = new Bloc();
        bloc.setNomBloc("B‑1");

        Chambre ch = new Chambre();
        ch.setNumeroChambre(101L);
        bloc.setChambres(List.of(ch));

        when(blocRepository.save(any(Bloc.class)))
                .thenAnswer(inv -> {
                    Bloc saved = inv.getArgument(0);
                    saved.setIdBloc(1L);
                    return saved;
                });

        Bloc result = blocService.addOrUpdate(bloc);

        Assertions.assertEquals(1L, result.getIdBloc());
        verify(blocRepository).save(bloc);   // bloc persisted
        verify(chambreRepository).save(ch);  // chambre persisted & linked
        verifyNoMoreInteractions(chambreRepository, blocRepository);
    }
}