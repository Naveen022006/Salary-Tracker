package com.local.salarytracker

import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.AppKind
import com.local.salarytracker.parse.Categorizer
import com.local.salarytracker.parse.Dedupe
import com.local.salarytracker.parse.NotificationParser
import com.local.salarytracker.parse.ParsedTxn
import com.local.salarytracker.parse.PaymentApps
import com.local.salarytracker.parse.TxnKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Realistic title + text pairs, as Android shows them, for the UPI apps the user asked for. */
class PaymentAppsUpiTest {
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val paytm = "net.one97.paytm"
    private val navi = "com.naviapp"
    private val bhim = "in.org.npci.upiapp"
    private val cred = "com.dreamplug.androidapp"
    private val amazon = "in.amazon.mShop.android.shopping"
    private val superMoney = "money.super.payments"
    private val groww = "com.nextbillion.groww"
    private val kotak = "com.msf.kbank.mobile"
    private val yono = "com.sbi.lotusintouch"

    private fun check(pkg: String, title: String?, text: String?, paise: Long, type: TxnType, merchant: String?) {
        val p = NotificationParser.parse(pkg, title, text)
        assertNotNull("expected txn: $title / $text", p)
        assertEquals("amount: $title / $text", paise, p!!.amountPaise)
        assertEquals("type: $title / $text", type, p.type)
        assertEquals("merchant: $title / $text", merchant, p.merchant)
        assertFalse("excluded: $title / $text", p.excluded)
    }

    private fun nul(pkg: String, title: String?, text: String?) =
        assertNull("expected null: $title / $text", NotificationParser.parse(pkg, title, text))

    // ---------------------------------------------------------------- Google Pay
    @Test fun gpayPaidToSwiggy() = check(gpay, "Google Pay", "₹250 paid to Swiggy", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun gpayPaymentSuccessfulYouPaid() =
        check(gpay, "Payment successful", "You paid ₹250 to Ramesh Kumar", 25000, TxnType.DEBIT, "Ramesh Kumar")
    @Test fun gpaySentToPriya() = check(gpay, "Google Pay", "₹1,500 sent to Priya", 150000, TxnType.DEBIT, "Priya")
    @Test fun gpayPaidGooglePlay() = check(gpay, "Google Pay", "Paid ₹99 to Google Play", 9900, TxnType.DEBIT, "Google Play")
    @Test fun gpayAutopayNetflix() =
        check(gpay, "Google Pay", "₹199 paid to Netflix via UPI AutoPay", 19900, TxnType.DEBIT, "Netflix")
    @Test fun gpayReceivedPaidYou() = check(gpay, "Google Pay", "Ramesh paid you ₹500", 50000, TxnType.CREDIT, "Ramesh")
    @Test fun gpayReceivedFrom() = check(gpay, "Google Pay", "₹500 received from Ramesh", 50000, TxnType.CREDIT, "Ramesh")
    @Test fun gpayRequestedIsNull() = nul(gpay, "Google Pay", "Ramesh requested ₹300")
    @Test fun gpayPayPromptIsNull() = nul(gpay, "Google Pay", "Pay ₹250 to Swiggy")
    @Test fun gpayRewardIsNull() = nul(gpay, "Google Pay", "Reward unlocked")
    @Test fun gpayScratchCardIsNull() = nul(gpay, "Google Pay", "You've got a scratch card")
    @Test fun gpayScratchCardWithAmountIsNull() = nul(gpay, "Google Pay", "You've got a scratch card worth up to ₹1,000")
    @Test fun gpayEmojiTitle() = check(gpay, "✅ Payment successful", "₹250 paid to Swiggy", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun gpayYouPaidWithDecimals() =
        check(gpay, "Payment successful", "You paid ₹1,250.50 to Ravi Stores", 125050, TxnType.DEBIT, "Ravi Stores")
    @Test fun gpayFailedIsNull() = nul(gpay, "Payment failed", "Your payment of ₹250 to Swiggy failed")
    @Test fun gpayPendingIsNull() = nul(gpay, "Payment pending", "₹250 to Swiggy is pending")

    // ---------------------------------------------------------------- PhonePe
    @Test fun phonepePaid() = check(phonepe, "PhonePe", "Paid ₹250 to Swiggy", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun phonepeMoneySentPriya() = check(phonepe, "PhonePe", "Money sent ₹500 to Priya", 50000, TxnType.DEBIT, "Priya")
    @Test fun phonepeAutopay() = check(phonepe, "PhonePe", "₹199 debited for Netflix AutoPay", 19900, TxnType.DEBIT, "Netflix")
    @Test fun phonepePaymentOfIsSuccessful() =
        check(phonepe, "PhonePe", "Payment of ₹120 to Chai Point is successful", 12000, TxnType.DEBIT, "Chai Point")
    @Test fun phonepeReceived() = check(phonepe, "PhonePe", "Received ₹500 from Ramesh", 50000, TxnType.CREDIT, "Ramesh")
    @Test fun phonepeWalletTopUpIsExcluded() {
        val p = NotificationParser.parse(phonepe, "PhonePe", "₹500 added to PhonePe Wallet")
        assertNotNull(p)
        assertTrue(p!!.excluded)
        assertEquals(50000L, p.amountPaise)
    }
    @Test fun phonepeCollectRequestIsNull() = nul(phonepe, "PhonePe", "Collect request of ₹300 from Ravi")
    @Test fun phonepeBillDueIsNull() = nul(phonepe, "PhonePe", "Your bill of ₹799 is due")
    @Test fun phonepeEmojiTitle() = check(phonepe, "✅ Payment Successful", "Paid ₹250 to Swiggy", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun phonepeRsPrefix() = check(phonepe, "PhonePe", "Paid Rs.250 to Swiggy", 25000, TxnType.DEBIT, "Swiggy")

    // ---------------------------------------------------------------- Paytm
    @Test fun paytmPaidFromPaytmUpi() = check(paytm, "Paytm", "Paid ₹120 to Chai Point from Paytm UPI", 12000, TxnType.DEBIT, "Chai Point")
    @Test fun paytmPaidSuccessfully() = check(paytm, "Paytm", "₹120 paid successfully to Chai Point", 12000, TxnType.DEBIT, "Chai Point")
    @Test fun paytmRsSent() = check(paytm, "Paytm", "Rs.120 sent to Priya", 12000, TxnType.DEBIT, "Priya")
    @Test fun paytmReceivedInUpi() =
        check(paytm, "Paytm", "Received ₹500 from Ramesh in your Paytm UPI", 50000, TxnType.CREDIT, "Ramesh")
    @Test fun paytmFailedIsNull() = nul(paytm, "Paytm", "Payment of ₹90 failed")
    @Test fun paytmCashbackCreditedIsCreditNotExcluded() {
        val p = NotificationParser.parse(paytm, "Paytm", "Cashback of ₹10 credited")
        assertNotNull(p)
        assertEquals(TxnType.CREDIT, p!!.type)
        assertFalse(p.excluded)
        assertEquals(1000L, p.amountPaise)
        assertEquals(Category.OTHER, Categorizer.categorize(p.merchant, "Cashback of ₹10 credited", emptyMap(), p.viaGateway))
    }
    @Test fun paytmInrPrefix() = check(paytm, "Paytm", "INR 250 paid to Swiggy", 25000, TxnType.DEBIT, "Swiggy")

    // ---------------------------------------------------------------- Navi
    @Test fun naviPaidUsingNaviUpi() = check(navi, "Navi UPI", "₹250 paid to Swiggy using Navi UPI", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun naviPaymentSuccessful() =
        check(navi, "Navi UPI", "Payment successful ₹250 to Ramesh", 25000, TxnType.DEBIT, "Ramesh")
    @Test fun naviReceived() = check(navi, "Navi UPI", "₹500 received from Ramesh", 50000, TxnType.CREDIT, "Ramesh")
    @Test fun naviLoanEmiDueIsNull() = nul(navi, "Navi", "Your Navi loan EMI of ₹2,000 is due")
    @Test fun naviEmojiTitle() = check(navi, "✅ Payment successful", "₹250 paid to Swiggy using Navi UPI", 25000, TxnType.DEBIT, "Swiggy")

    // ---------------------------------------------------------------- other UPI apps
    @Test fun bhimSentSuccessfully() = check(bhim, "BHIM", "₹50 sent to Suresh successfully", 5000, TxnType.DEBIT, "Suresh")
    @Test fun credUpiBlueTokai() = check(cred, "CRED", "₹450 paid to Blue Tokai via CRED UPI", 45000, TxnType.DEBIT, "Blue Tokai")
    @Test fun credCardBillExcluded() {
        val p = NotificationParser.parse(cred, "CRED", "payment of ₹2,000 to HDFC credit card successful")
        assertNotNull(p)
        assertTrue(p!!.excluded)
    }
    @Test fun amazonPayPaid() = check(amazon, "Amazon Pay", "₹499 paid to Amazon", 49900, TxnType.DEBIT, "Amazon")
    @Test fun amazonPayUpiSuccessful() = check(amazon, "Amazon Pay", "Payment of ₹299 successful using Amazon Pay UPI", 29900, TxnType.DEBIT, "Amazon")
    @Test fun superMoneyPaid() = check(superMoney, "super.money", "₹80 paid to Tea Stall", 8000, TxnType.DEBIT, "Tea Stall")
    @Test fun growwUpiPaid() = check(groww, "Groww UPI", "₹100 paid to Ravi via Groww UPI", 10000, TxnType.DEBIT, "Ravi")
    @Test fun kotakDebitAlert() =
        check(kotak, "Kotak Bank", "₹1,250 debited from A/c XX1234 to VPA x@ybl", 125000, TxnType.DEBIT, "X")
    @Test fun yonoDebitAlert() {
        val p = NotificationParser.parse(yono, "YONO SBI", "Rs.1,250.00 debited from A/c XX1234 to VPA ramesh@okaxis (UPI Ref 412345678901)")
        assertNotNull(p)
        assertEquals(125000L, p!!.amountPaise)
        assertEquals("Ramesh", p.merchant)
        assertEquals("1234", p.accountLast4)
        assertEquals("412345678901", p.upiRef)
    }

    // ---------------------------------------------------------------- catalog
    @Test fun growwIsUpiKind() {
        val a = PaymentApps.byPackage(groww)
        assertNotNull(a)
        assertEquals(AppKind.UPI, a!!.kind)
        assertNull(a.defaultCategory)
    }
    @Test fun requestedAppsAreUpiKind() {
        for (p in listOf(gpay, phonepe, paytm, navi, bhim, cred, superMoney)) {
            assertEquals(p, AppKind.UPI, PaymentApps.byPackage(p)!!.kind)
        }
    }

    // ---------------------------------------------------------------- amount / name edge cases
    @Test fun amountWithCommasAndDecimals() = check(gpay, "GPay", "Paid ₹1,25,000.75 to Acme Traders", 12500075, TxnType.DEBIT, "Acme Traders")
    @Test fun amountRsDotNoSpace() = check(gpay, "GPay", "Paid Rs.99 to Google Play", 9900, TxnType.DEBIT, "Google Play")
    @Test fun amountRsSpace() = check(gpay, "GPay", "Paid Rs 99 to Google Play", 9900, TxnType.DEBIT, "Google Play")
    @Test fun amountInrNoSpace() = check(gpay, "GPay", "Paid INR99 to Google Play", 9900, TxnType.DEBIT, "Google Play")
    @Test fun amountSingleDecimal() = check(gpay, "GPay", "Paid ₹99.5 to Google Play", 9950, TxnType.DEBIT, "Google Play")
    @Test fun nameWithAmpersand() = check(gpay, "GPay", "₹300 paid to Sharma & Sons", 30000, TxnType.DEBIT, "Sharma & Sons")
    @Test fun nameWithApostrophe() = check(phonepe, "PhonePe", "Paid ₹300 to Domino's Pizza", 30000, TxnType.DEBIT, "Domino's Pizza")
    @Test fun merchantCutAtUsing() = check(navi, "Navi", "₹250 paid to Swiggy using Navi UPI", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun merchantCutAtVia() = check(gpay, "GPay", "₹250 paid to Swiggy via Google Pay", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun merchantCutAtFrom() = check(paytm, "Paytm", "Paid ₹250 to Swiggy from Paytm Payments Bank", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun merchantCutAtOn() = check(phonepe, "PhonePe", "Paid ₹250 to Swiggy on PhonePe", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun merchantCutAtIn() = check(paytm, "Paytm", "Received ₹250 from Ramesh in your Paytm UPI", 25000, TxnType.CREDIT, "Ramesh")
    @Test fun merchantCutAtUsingCred() = check(cred, "CRED", "Paid ₹250 to Swiggy using CRED UPI", 25000, TxnType.DEBIT, "Swiggy")

    // ---------------------------------------------------------------- dedupe across apps
    private val t0 = 1_700_000_000_000L

    private fun key(p: ParsedTxn, ts: Long) = TxnKey(p.amountPaise, p.type, ts, p.upiRef, TxnSource.NOTIFICATION)

    @Test fun gpayAndBankAppAreOnePayment() {
        val a = NotificationParser.parse(gpay, "Google Pay", "₹250 paid to Swiggy")!!
        val b = NotificationParser.parse(kotak, "Kotak Bank", "₹250 debited from A/c XX1234 to VPA swiggy@icici")!!
        assertEquals("Swiggy", b.merchant)
        assertTrue(Dedupe.isDuplicate(key(a, t0), key(b, t0 + 4 * 60_000)))
    }
    @Test fun gpayAndBankAppOutsideWindowAreTwo() {
        val a = NotificationParser.parse(gpay, "Google Pay", "₹250 paid to Swiggy")!!
        val b = NotificationParser.parse(kotak, "Kotak Bank", "₹250 debited from A/c XX1234 to VPA swiggy@icici")!!
        assertFalse(Dedupe.isDuplicate(key(a, t0), key(b, t0 + 11 * 60_000)))
    }
    @Test fun creditAndDebitSameAmountAreNotDuplicates() {
        val a = NotificationParser.parse(gpay, "Google Pay", "₹500 received from Ramesh")!!
        val b = NotificationParser.parse(gpay, "Google Pay", "₹500 paid to Ramesh")!!
        assertFalse(Dedupe.isDuplicate(key(a, t0), key(b, t0 + 60_000)))
    }
}
