package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TicketServiceImpl}.
 * <p>
 * These tests validate that all business rules are enforced,
 * and that the correct interactions are made with the external
 * {@link TicketPaymentService} and {@link SeatReservationService}.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    /** Mocked payment service dependency */
    @Mock private TicketPaymentService paymentService;

    /** Mocked seat reservation service dependency */
    @Mock private SeatReservationService reservationService;

    /** System under test (TicketServiceImpl with mocked dependencies) */
    private TicketServiceImpl sut;

    /**
     * Create a fresh instance of the service before each test,
     * injecting the mocked dependencies.
     */
    @BeforeEach
    void setUp() {
        sut = new TicketServiceImpl(paymentService, reservationService);
    }

    /**
     * Helper method to construct a {@link TicketTypeRequest}.
     * Keeps test code concise and readable.
     */
    private static TicketTypeRequest req(Type type, int n) {
        return new TicketTypeRequest(type, n);
    }

    /**
     * Happy-path scenario:
     * Valid purchase with 2 adults, 1 child, and 1 infant.
     * Expects correct total payment (£65) and seat reservation (3 seats).
     */
    @Test
    @DisplayName("Happy path: 2 ADULT, 1 CHILD, 1 INFANT -> pay £65 and reserve 3 seats")
    void purchaseTickets_happyPath_makesCorrectCalls() {
        long accountId = 123L;
        var r1 = req(Type.ADULT, 2);
        var r2 = req(Type.CHILD, 1);
        var r3 = req(Type.INFANT, 1);

        sut.purchaseTickets(accountId, r1, r2, r3);

        verify(paymentService, times(1))
                .makePayment(eq(accountId), eq(65));
        verify(reservationService, times(1))
                .reserveSeat(eq(accountId), eq(3));

        verifyNoMoreInteractions(paymentService, reservationService);
    }

    /**
     * Group of tests covering invalid request scenarios.
     * Each test ensures that the service rejects invalid inputs
     * by throwing {@link InvalidPurchaseException} or
     * {@link IllegalArgumentException}.
     */

    @Nested
    class InvalidRequests {

        /** Rejects purchase when accountId <= 0 */
        @Test
        @DisplayName("Reject: accountId <= 0 (service-level validation)")
        void rejectInvalidAccount() {
            assertThrows(InvalidPurchaseException.class,
                    () -> sut.purchaseTickets(0L, req(Type.ADULT, 1)),
                    "Expected InvalidPurchaseException when accountId <= 0");
            verifyNoInteractions(paymentService, reservationService);
        }

        /** Rejects construction of request with zero or negative quantities */
        @Test
        @DisplayName("Reject at construction: zero or negative quantities (immutability validation)")
        void rejectNonPositiveQuantitiesAtConstruction() {
            assertThrows(IllegalArgumentException.class,
                    () -> req(Type.ADULT, 0),
                    "TicketTypeRequest should reject zero tickets");
            assertThrows(IllegalArgumentException.class,
                    () -> req(Type.CHILD, -1),
                    "TicketTypeRequest should reject negative tickets");
            verifyNoInteractions(paymentService, reservationService);
        }

        /** Rejects child tickets without any accompanying adult */
        @Test
        @DisplayName("Reject: children without adults (service-level validation)")
        void rejectChildrenWithoutAdults() {
            assertThrows(InvalidPurchaseException.class,
                    () -> sut.purchaseTickets(10L, req(Type.CHILD, 2)),
                    "Expected InvalidPurchaseException when children purchased without adults");
            verifyNoInteractions(paymentService, reservationService);
        }

        /** Rejects infant tickets without any accompanying adult */
        @Test
        @DisplayName("Reject: infants without adults (service-level validation)")
        void rejectInfantsWithoutAdults() {
            assertThrows(InvalidPurchaseException.class,
                    () -> sut.purchaseTickets(10L, req(Type.INFANT, 1)),
                    "Expected InvalidPurchaseException when infants purchased without adults");
            verifyNoInteractions(paymentService, reservationService);
        }

        /** Rejects purchases where total tickets exceed 25 */
        @Test
        @DisplayName("Reject: more than 25 total tickets (service-level validation)")
        void rejectMoreThan25() {
            assertThrows(InvalidPurchaseException.class,
                    () -> sut.purchaseTickets(10L, req(Type.ADULT, 26)),
                    "Expected InvalidPurchaseException when trying to buy 26 adults (over 25 max)");
            assertThrows(InvalidPurchaseException.class,
                    () -> sut.purchaseTickets(10L, req(Type.ADULT, 24), req(Type.CHILD, 2)),
                    "Expected InvalidPurchaseException when total tickets > 25");
            verifyNoInteractions(paymentService, reservationService);
        }

        /** Rejects when the request list is null or empty */
        @Test
        @DisplayName("Reject: null or empty request list (service-level validation)")
        void rejectNullOrEmpty() {
            assertThrows(InvalidPurchaseException.class,
                    () -> sut.purchaseTickets(10L, (TicketTypeRequest[]) null),
                    "Expected InvalidPurchaseException when request list is null");
            assertThrows(InvalidPurchaseException.class,
                    () -> sut.purchaseTickets(10L),
                    "Expected InvalidPurchaseException when request list is empty");
            verifyNoInteractions(paymentService, reservationService);
        }
    }

    /**
     * Edge case:
     * Exactly 25 tickets purchased (20 adults, 5 children).
     * Should be valid and result in correct payment and seat reservations.
     */
    @Test
    @DisplayName("Edge: exactly 25 total tickets passes (adults present)")
    void exactly25TicketsIsValid() {
        sut.purchaseTickets(10L, req(Type.ADULT, 20), req(Type.CHILD, 5));
        verify(paymentService)
                .makePayment(10L, 20 * 25 + 5 * 15);
        verify(reservationService)
                .reserveSeat(10L, 20 + 5);
    }

    /**
     * Additional boundary tests for validating the 25-ticket cap.
     * These confirm the maximum limit behaves correctly with different combinations.
     */

    /** Boundary: 25 adults is valid */
    @Test
    @DisplayName("Boundary: 25 adults is valid (pay 25*£25, reserve 25 seats)")
    void twentyFiveAdults_valid() {
        sut.purchaseTickets(10L, req(Type.ADULT, 25));

        verify(paymentService)
                .makePayment(10L, 25 * 25);
        verify(reservationService)
                .reserveSeat(10L, 25);
    }

    /** Boundary: 10 adults + 15 children = 25 is valid */
    @Test
    @DisplayName("Boundary: 10 adults + 15 children = 25 is valid")
    void tenAdultsFifteenChildren_valid() {
        sut.purchaseTickets(10L, req(Type.ADULT, 10), req(Type.CHILD, 15));

        int expectedPayment = 10 * 25 + 15 * 15; // £625
        int expectedSeats   = 10 + 15;           // 25 seats
        verify(paymentService)
                .makePayment(10L, expectedPayment);
        verify(reservationService)
                .reserveSeat(10L, expectedSeats);
    }

    /** Boundary: mix of adults, children, and infants totalling 25 */
    @Test
    @DisplayName("Boundary: adults + children + infants = 25 is valid (infants add no seats)")
    void mixWithInfants_total25_valid() {
        sut.purchaseTickets(10L,
                req(Type.ADULT, 8),
                req(Type.CHILD, 12),
                req(Type.INFANT, 5));

        int expectedPayment = 8 * 25 + 12 * 15; // £380
        int expectedSeats   = 8 + 12;           // 20 seats
        verify(paymentService)
                .makePayment(10L, expectedPayment);
        verify(reservationService)
                .reserveSeat(10L, expectedSeats);
    }

    /** Rejects when total tickets exceed the 25-ticket limit */
    @Test
    @DisplayName("Reject: total > 25 (e.g., 15 adults + 11 children = 26)")
    void overTwentyFive_rejected() {
        assertThrows(InvalidPurchaseException.class,
                () -> sut.purchaseTickets(10L, req(Type.ADULT, 15), req(Type.CHILD, 11)),
                "Expected InvalidPurchaseException when total tickets exceed 25");
        verifyNoInteractions(paymentService, reservationService);
    }
}
