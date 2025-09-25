package uk.gov.dwp.uc.pairtest.domain;

/**
 * Immutable Object implemented as a Java record.
 */

public record TicketTypeRequest(Type ticketType, int noOfTickets) {

    /**
     * Compact constructor for validation.
     * Runs automatically whenever a new TicketTypeRequest is created.
     */

    public TicketTypeRequest {
        if (ticketType == null) {
            throw new IllegalArgumentException("Ticket type is required");
        }
        if (noOfTickets <= 0) {
            throw new IllegalArgumentException("Number of tickets must be greater than 0");
        }
    }

    /**
     * Enum representing the supported ticket types.
     */
    public enum Type {
        ADULT, CHILD, INFANT
    }
}
