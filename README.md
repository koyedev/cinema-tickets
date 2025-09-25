# 🎟️ Cinema Tickets – Coding Exercise

## Overview
This project is an implementation of the **Cinema Ticket Service** coding exercise.  
It enforces all specified business rules for purchasing tickets, calculates correct totals,  
and interacts with external payment and seat reservation services.  

Built with **Java 21** and **Maven**.  

---

## ✅ Business Rules Implemented
- A maximum of **25 tickets** can be purchased in a single transaction.
- **Infants**:
  - Do not pay for a ticket (£0).
  - Do not occupy a seat (sit on an adult’s lap).
- **Children and Infants** cannot be purchased without at least **one Adult**.
- **Prices**:
  - Adult: £25  
  - Child: £15  
  - Infant: £0
- Only valid account IDs (`> 0`) are accepted.
- Requests that are `null`, empty, or contain invalid values are rejected.

---

## 🛠️ Implementation Details
- `TicketTypeRequest` is implemented as a **Java 21 record** (immutable with validation).
- `TicketServiceImpl`:
  - Validates input requests.
  - Applies business rules.
  - Calculates payment and seats.
  - Delegates calls to:
    - `TicketPaymentService`
    - `SeatReservationService`
- External services are assumed to always succeed (as per exercise brief).

---

## 🧪 Testing
- Tests are written using **JUnit 5** and **Mockito**.
- Cover both:
  - **Happy path scenarios** (valid purchases).
  - **Invalid scenarios** (business rule violations).
- Includes **boundary tests** (e.g., exactly 25 tickets vs. >25 tickets).
