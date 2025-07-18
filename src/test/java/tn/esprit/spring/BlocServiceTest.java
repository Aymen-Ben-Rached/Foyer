package tn.esprit.spring;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.spring.entity.Chambre;
import tn.esprit.spring.repository.ChambreRepository;
import tn.esprit.spring.service.ChambreServiceImpl;

import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ChambreServiceTest {

    @Mock
    private ChambreRepository chambreRepository;

    @InjectMocks
    private ChambreServiceImpl chambreService;

    private Chambre chambre1;
    private Chambre chambre2;

    @BeforeEach
    void setUp() {
        chambre1 = new Chambre();
        chambre1.setIdChambre(1L);
        chambre1.setNumeroChambre("101");
        chambre1.setTypeChambre("SIMPLE");

        chambre2 = new Chambre();
        chambre2.setIdChambre(2L);
        chambre2.setNumeroChambre("102");
        chambre2.setTypeChambre("DOUBLE");
    }

    @Test
    @DisplayName("Test retrieveAllChambres - Success")
    void testRetrieveAllChambres() {
        // Arrange
        when(chambreRepository.findAll()).thenReturn(Arrays.asList(chambre1, chambre2));

        // Act
        List<Chambre> chambres = chambreService.retrieveAllChambres();

        // Assert
        Assertions.assertEquals(2, chambres.size(), "Should return 2 chambres");
        verify(chambreRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Test addChambre - Success")
    void testAddChambre() {
        // Arrange
        when(chambreRepository.save(any(Chambre.class))).thenReturn(chambre1);

        // Act
        Chambre savedChambre = chambreService.addChambre(chambre1);

        // Assert
        Assertions.assertNotNull(savedChambre, "Saved chambre should not be null");
        Assertions.assertEquals("101", savedChambre.getNumeroChambre());
        verify(chambreRepository, times(1)).save(chambre1);
    }}
