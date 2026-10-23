package com.local.salarytracker

import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.DEFAULT_ALLOWED_PACKAGES
import com.local.salarytracker.parse.NotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val paytm = "net.one97.paytm"

    private fun check(pkg: String, title: String?, text: String?, paise: Long, type: TxnType, merchant: String?) {
        val p = NotificationParser.parse(pkg, title, text)
        assertNotNull("expected txn: $title / $text", p)
        assertEquals(paise, p!!.amountPaise)
        assertEquals(type, p.type)
        if (merchant != null) assertEquals(merchant, p.merchant)
    }

    @Test fun gpayYouPaid() = check(gpay, "Google Pay", "You paid ₹250 to Swiggy", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun gpayPaidTo() = check(gpay, "Payment successful", "₹250 paid to Ramesh Kumar", 25000, TxnType.DEBIT, "Ramesh Kumar")
    @Test fun gpayPaymentSuccessfulTitle() = check(gpay, "Payment successful", "₹1,200 to Big Bazaar", 120000, TxnType.DEBIT, "Big Bazaar")
    @Test fun phonepePaid() = check(phonepe, "PhonePe", "Paid ₹250 to Chai Wala", 25000, TxnType.DEBIT, "Chai Wala")
    @Test fun phonepeSentWithSpace() = check(phonepe, "PhonePe", "Sent ₹ 500 to Amit", 50000, TxnType.DEBIT, "Amit")
    @Test fun paytmPaid() = check(paytm, "Paytm", "Paid Rs.250 to Zomato", 25000, TxnType.DEBIT, "Zomato")
    @Test fun amazonPayment() =
        check("in.amazon.mShop.android.shopping", "Amazon", "Your payment of ₹499 for order #123 was successful", 49900, TxnType.DEBIT, "Amazon")
    @Test fun swiggyOrder() = check("in.swiggy.android", "Swiggy", "Order placed! ₹349 paid", 34900, TxnType.DEBIT, "Swiggy")
    @Test fun instamartOrder() =
        check("in.swiggy.android.instamart", "Instamart", "Order placed! ₹349 paid", 34900, TxnType.DEBIT, "Swiggy Instamart")
    @Test fun zomatoOrder() = check("com.application.zomato", "Zomato", "Order confirmed. ₹520 paid via UPI", 52000, TxnType.DEBIT, "Zomato")
    @Test fun samsungWallet() =
        check("com.samsung.android.spay", "Samsung Wallet", "Payment of ₹120.00 to ABC Store was successful", 12000, TxnType.DEBIT, "Abc Store")
    @Test fun razorpayReceived() =
        check("com.razorpay.payments.app", "Razorpay", "₹500 received from Rahul", 50000, TxnType.CREDIT, "Rahul")
    @Test fun bankAppDebit() =
        check("com.sbi.lotusintouch", "YONO SBI", "Rs.1,000.00 debited from A/c XX1234 at CROMA", 100000, TxnType.DEBIT, "Croma")

    @Test fun ignoresRequestMoney() = assertNull(NotificationParser.parse(gpay, "Google Pay", "Ramesh requested ₹500 from you"))
    @Test fun ignoresNoAmount() = assertNull(NotificationParser.parse(gpay, "Payment successful", "Tap to view details"))
    @Test fun ignoresPromo() = assertNull(NotificationParser.parse(gpay, "Google Pay", "Get up to ₹100 cashback on your next payment"))
    @Test fun ignoresPaytmCashbackOffer() = assertNull(NotificationParser.parse(paytm, "Paytm", "Win assured cashback up to ₹500"))
    @Test fun ignoresFailed() = assertNull(NotificationParser.parse(phonepe, "PhonePe", "Payment of ₹250 failed"))
    @Test fun ignoresSmsApps() {
        assertNull(NotificationParser.parse("com.google.android.apps.messaging", "HDFCBK", "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY"))
        assertNull(NotificationParser.parse("com.samsung.android.messaging", "HDFCBK", "Rs.250 debited from A/c XX1234"))
    }

    @Test fun defaultAllowList() {
        assertTrue("com.phonepe.app" in DEFAULT_ALLOWED_PACKAGES)
        assertTrue("in.swiggy.android.instamart" in DEFAULT_ALLOWED_PACKAGES)
        assertTrue("com.samsung.android.spay" in DEFAULT_ALLOWED_PACKAGES)
        assertFalse("com.whatsapp" in DEFAULT_ALLOWED_PACKAGES)
    }
}
