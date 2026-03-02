package com.advisora.Services.event;

import com.advisora.Model.event.EventBooking;
import com.advisora.Services.ressource.ShopPaymentGatewayService;

public class EventPaymentService {
    private static final String DEFAULT_PROVIDER = "STRIPE";

    private final EventBookingService bookingService;
    private final ShopPaymentGatewayService gateway;

    public EventPaymentService() {
        this(new EventBookingService(), new ShopPaymentGatewayService());
    }

    EventPaymentService(EventBookingService bookingService, ShopPaymentGatewayService gateway) {
        this.bookingService = bookingService;
        this.gateway = gateway;
    }

    public PaymentFlowResult initiateCheckout(int bookingId) {
        EventBooking booking = requireBooking(bookingId);
        if (booking.getTotalPrixBk() <= 0) {
            bookingService.markBookingPaid(bookingId, "FREE-" + bookingId);
            return PaymentFlowResult.free(bookingId);
        }

        ShopPaymentGatewayService.PaymentInitResult init = gateway.createPayment(
                DEFAULT_PROVIDER,
                booking.getTotalPrixBk(),
                "EVBK-" + bookingId + "-" + System.currentTimeMillis()
        );

        if (init.getExternalRef() != null && !init.getExternalRef().isBlank()) {
            bookingService.updatePaymentReference(bookingId, init.getExternalRef());
        }

        return PaymentFlowResult.pending(
                bookingId,
                init.getExternalRef(),
                init.getPaymentUrl(),
                init.getNote(),
                init.isApiMode()
        );
    }

    public boolean confirmCheckoutPayment(int bookingId, String externalRef, String paymentUrl) {
        if (externalRef != null && externalRef.startsWith("LOCAL-")) {
            bookingService.markBookingPaid(bookingId, externalRef);
            return true;
        }
        boolean paid = gateway.verifyPayment(DEFAULT_PROVIDER, externalRef, paymentUrl);
        if (paid) {
            bookingService.markBookingPaid(bookingId, externalRef);
        }
        return paid;
    }

    public boolean refundBooking(int bookingId, String reason) {
        EventBooking booking = requireBooking(bookingId);
        if (booking.getTotalPrixBk() <= 0) {
            bookingService.markBookingRefunded(bookingId, reason == null ? "Free booking cancelled" : reason);
            return true;
        }

        String paymentRef = booking.getPaymentReference();
        boolean refunded = gateway.refundPayment(DEFAULT_PROVIDER, paymentRef, booking.getTotalPrixBk());
        if (!refunded && paymentRef != null && paymentRef.startsWith("LOCAL-")) {
            refunded = true;
        }
        if (refunded) {
            bookingService.markBookingRefunded(bookingId, reason == null ? "Refund approved" : reason);
        }
        return refunded;
    }

    private EventBooking requireBooking(int bookingId) {
        EventBooking booking = bookingService.getById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Reservation introuvable.");
        }
        return booking;
    }

    public static final class PaymentFlowResult {
        private final int bookingId;
        private final String externalRef;
        private final String paymentUrl;
        private final String note;
        private final boolean apiMode;
        private final boolean paymentRequired;
        private final boolean alreadyConfirmed;

        private PaymentFlowResult(int bookingId,
                                  String externalRef,
                                  String paymentUrl,
                                  String note,
                                  boolean apiMode,
                                  boolean paymentRequired,
                                  boolean alreadyConfirmed) {
            this.bookingId = bookingId;
            this.externalRef = externalRef;
            this.paymentUrl = paymentUrl;
            this.note = note;
            this.apiMode = apiMode;
            this.paymentRequired = paymentRequired;
            this.alreadyConfirmed = alreadyConfirmed;
        }

        public static PaymentFlowResult free(int bookingId) {
            return new PaymentFlowResult(bookingId, null, null, "Reservation gratuite confirmee.", false, false, true);
        }

        public static PaymentFlowResult pending(int bookingId, String externalRef, String paymentUrl, String note, boolean apiMode) {
            return new PaymentFlowResult(bookingId, externalRef, paymentUrl, note, apiMode, true, false);
        }

        public int getBookingId() {
            return bookingId;
        }

        public String getExternalRef() {
            return externalRef;
        }

        public String getPaymentUrl() {
            return paymentUrl;
        }

        public String getNote() {
            return note;
        }

        public boolean isApiMode() {
            return apiMode;
        }

        public boolean isPaymentRequired() {
            return paymentRequired;
        }

        public boolean isAlreadyConfirmed() {
            return alreadyConfirmed;
        }
    }
}
