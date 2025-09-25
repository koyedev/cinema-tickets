package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.Objects;

/**
 * Implementation of the TicketService interface.
 * <p>
 * This class enforces all business rules around ticket purchases:
 * <ul>
 *   <li>Only valid account IDs (>0) are accepted.</li>
 *   <li>At least one adult must be present when purchasing child or infant tickets.</li>
 *   <li>A maximum of 25 tickets may be purchased in one transaction.</li>
 *   <li>Infants do not require a ticket or a seat.</li>
 * </ul>
 * <p>
 * Once validated, the class calculates the correct total payment and
 * number of seats to reserve, and delegats to the external
 * {@link TicketPaymentService} and {@link SeatReservationService}.
 */

public class TicketServiceImpl implements TicketService {

    /** Maximum number of tickets allowed in a single purchase */
    private static final int MAX_TICKETS = 25;

    /** Ticket price for adults */
    private static final int ADULT_PRICE = 25;

    /** Ticket price for children */
    private static final int CHILD_PRICE = 15;

    /** External payment service for handling ticket payments */
    private final TicketPaymentService paymentService;

    /** External reservation service for handling seat reservations */
    private final SeatReservationService reservationService;

    /**
     * Constructor used for dependency injection, primarily in tests.
     *
     * @param paymentService     the external payment service
     * @param reservationService the external reservation service
     */
    public TicketServiceImpl(TicketPaymentService paymentService,
                             SeatReservationService reservationService) {
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService");
        this.reservationService = Objects.requireNonNull(reservationService, "reservationService");
    }

    /**
     * Validates the purchase request against all business rules,
     * calculates the total payment and seats, and delegates to the
     * external services.
     *
     * @param accountId           the account making the purchase
     * @param ticketTypeRequests  one or more ticket requests
     * @throws InvalidPurchaseException if validation fails
     */
    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {

        // ---- Validation ----
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException();
        }
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException();
        }

        int adults = 0;
        int children = 0;
        int infants = 0;

        // Count tickets by type
        for (TicketTypeRequest req : ticketTypeRequests) {
            if (req == null) {
                throw new InvalidPurchaseException();
            }

            Type type = req.ticketType();
            int count = req.noOfTickets();

            switch (type) {
                case ADULT -> adults += count;
                case CHILD -> children += count;
                case INFANT -> infants += count;
                default -> throw new InvalidPurchaseException();
            }
        }

        int totalTickets = adults + children + infants;
        if (totalTickets > MAX_TICKETS) {
            throw new InvalidPurchaseException();
        }

        // Child/Infant tickets require at least one adult
        if (adults == 0 && (children > 0 || infants > 0)) {
            throw new InvalidPurchaseException();
        }

        // ---- Calculations ----
        int totalToPay = (adults * ADULT_PRICE) + (children * CHILD_PRICE);
        int seatsToReserve = adults + children; // infants sit on laps

        // ---- External service calls ----
        paymentService.makePayment(accountId, totalToPay);
        reservationService.reserveSeat(accountId, seatsToReserve);
    }
}
