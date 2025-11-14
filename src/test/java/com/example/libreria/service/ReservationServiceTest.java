package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    
    @Mock
    private ReservationRepository reservationRepository;
    
    @Mock
    private BookRepository bookRepository;
    
    @Mock
    private BookService bookService;
    
    @Mock
    private UserService userService;
    
    @InjectMocks
    private ReservationService reservationService;
    
    private User testUser;
    private Book testBook;
    private Reservation testReservation;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Juan Pérez");
        testUser.setEmail("juan@example.com");
        
        testBook = new Book();
        testBook.setExternalId(258027L);
        testBook.setTitle("The Lord of the Rings");
        testBook.setPrice(new BigDecimal("15.99"));
        testBook.setStockQuantity(10);
        testBook.setAvailableQuantity(5);
        
        testReservation = new Reservation();
        testReservation.setId(1L);
        testReservation.setUser(testUser);
        testReservation.setBook(testBook);
        testReservation.setRentalDays(7);
        testReservation.setStartDate(LocalDate.now());
        testReservation.setExpectedReturnDate(LocalDate.now().plusDays(7));
        testReservation.setDailyRate(new BigDecimal("15.99"));
        testReservation.setTotalFee(new BigDecimal("111.93"));
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        testReservation.setCreatedAt(LocalDateTime.now());
    }
    
    @Test
    void testCreateReservation_Success() {
        // TOD: Implementar el test de creación de reserva exitosa
        ReservationRequestDTO reservacion = new ReservationRequestDTO();
        reservacion.setUserId(testUser.getId());
        reservacion.setBookExternalId(testBook.getExternalId());
        reservacion.setRentalDays(5);
        reservacion.setStartDate(LocalDate.now());

        when(userService.getUserEntity(testUser.getId())).thenReturn(testUser);
        when(bookRepository.findById(testBook.getExternalId())).thenReturn(Optional.of(testBook));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            r.setId(100L);
            return r;
        });

        ReservationResponseDTO result = reservationService.createReservation(reservacion);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(Reservation.ReservationStatus.ACTIVE, result.getStatus());
        BigDecimal expectedDailyRate = testBook.getPrice().multiply(new BigDecimal("0.10"));
        assertEquals(0, expectedDailyRate.compareTo(result.getDailyRate()));
        BigDecimal expectedTotalFee = expectedDailyRate.multiply(new BigDecimal(reservacion.getRentalDays())).setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(0, expectedTotalFee.compareTo(result.getTotalFee()));
        verify(bookService, times(1)).decreaseAvailableQuantity(testBook.getExternalId());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }
    
    @Test
    void testCreateReservation_BookNotAvailable() {
        // TOD: Implementar el test de creación de reserva cuando el libro no está disponible
        testBook.setAvailableQuantity(0);

        ReservationRequestDTO reserva = new ReservationRequestDTO();
        reserva.setUserId(testUser.getId());
        reserva.setBookExternalId(testBook.getExternalId());
        reserva.setRentalDays(3);
        reserva.setStartDate(LocalDate.now());

        when(userService.getUserEntity(testUser.getId())).thenReturn(testUser);
        when(bookRepository.findById(testBook.getExternalId())).thenReturn(Optional.of(testBook));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> reservationService.createReservation(reserva));
        assertTrue(ex.getMessage().contains("No hay copias disponibles"));
        verify(reservationRepository, never()).save(any());
        verify(bookService, never()).decreaseAvailableQuantity(anyLong());
    }
    
    @Test
    void testReturnBook_OnTime() {
        // TOD: Implementar el test de devolución de libro en tiempo
        LocalDate expectedReturn = LocalDate.now().plusDays(7);
        testReservation.setExpectedReturnDate(expectedReturn);
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);

        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(expectedReturn);

        when(reservationRepository.findById(testReservation.getId())).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO result = reservationService.returnBook(testReservation.getId(), returnRequest);

        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.RETURNED, result.getStatus());
        assertEquals(expectedReturn, result.getActualReturnDate());
        assertNull(result.getLateFee());
        verify(bookService, times(1)).increaseAvailableQuantity(testBook.getExternalId());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }
    
    @Test
    void testReturnBook_Overdue() {
        // TOD: Implementar el test de devolución de libro con retraso
        LocalDate expectedReturn = LocalDate.now().minusDays(3);
        LocalDate actualReturn = LocalDate.now();
        testReservation.setExpectedReturnDate(expectedReturn);
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);

        long daysLate = java.time.temporal.ChronoUnit.DAYS.between(expectedReturn, actualReturn);
        BigDecimal expectedLateFee = testBook.getPrice()
                .multiply(new BigDecimal("0.15"))
                .multiply(new BigDecimal(daysLate))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(actualReturn);

        when(reservationRepository.findById(testReservation.getId())).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO result = reservationService.returnBook(testReservation.getId(), returnRequest);

        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.RETURNED, result.getStatus());
        assertEquals(actualReturn, result.getActualReturnDate());
        assertEquals(0, expectedLateFee.compareTo(result.getLateFee()));
        verify(bookService, times(1)).increaseAvailableQuantity(testBook.getExternalId());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }
    
    @Test
    void testGetReservationById_Success() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        
        ReservationResponseDTO result = reservationService.getReservationById(1L);
        
        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
    }
    
    @Test
    void testGetAllReservations() {
        Reservation reservation2 = new Reservation();
        reservation2.setId(2L);
        
        when(reservationRepository.findAll()).thenReturn(Arrays.asList(testReservation, reservation2));
        
        List<ReservationResponseDTO> result = reservationService.getAllReservations();
        
        assertNotNull(result);
        assertEquals(2, result.size());
    }
    
    @Test
    void testGetReservationsByUserId() {
        when(reservationRepository.findByUserId(1L)).thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getReservationsByUserId(1L);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testGetActiveReservations() {
        when(reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE))
                .thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getActiveReservations();
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}

