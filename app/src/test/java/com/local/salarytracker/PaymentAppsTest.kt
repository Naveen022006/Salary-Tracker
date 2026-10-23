package com.local.salarytracker

import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.AppKind
import com.local.salarytracker.parse.DEFAULT_ALLOWED_PACKAGES
import com.local.salarytracker.parse.Dedupe
import com.local.salarytracker.parse.MerchantPick
import com.local.salarytracker.parse.NotificationParser
import com.local.salarytracker.parse.PaymentApps
import com.local.salarytracker.parse.SmartDetect
import com.local.salarytracker.parse.SmsParser
import com.local.salarytracker.parse.TxnKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog, notification phrasings of Indian payment apps, gateway merchant cleanup, smart mode, cross-app dedupe. */
class PaymentAppsTest {
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val paytm = "net.one97.paytm"
    private val cred = "com.dreamplug.androidapp"
    private val amazon = "in.amazon.mShop.android.shopping"
    private val mobikwik = "com.mobikwik_new"
    private val bhim = "in.org.npci.upiapp"
    private val sbi = "com.sbi.lotusintouch"

    private fun check(pkg: String, title: String?, text: String?, paise: Long, type: TxnType, merchant: String?) {
        val p = NotificationParser.parse(pkg, title, text)
        assertNotNull("expected txn: $title / $text", p)
        assertEquals("amount: $title / $text", paise, p!!.amountPaise)
        assertEquals("type: $title / $text", type, p.type)
        assertEquals("merchant: $title / $text", merchant, p.merchant)
    }

    private fun nul(pkg: String, title: String?, text: String?) =
        assertNull("expected null: $title / $text", NotificationParser.parse(pkg, title, text))

    // ------------------------------------------------------------ catalog
    @Test fun catalogHasKeyApps() {
        for (p in listOf(gpay, phonepe, paytm, bhim, cred, amazon, mobikwik, sbi, "money.jupiter", "com.bharatpe.app", "com.ubercab", "com.zeptoconsumerapp")) {
            assertNotNull(p, PaymentApps.byPackage(p))
        }
    }

    @Test fun catalogHasNoDuplicatePackages() = assertEquals(PaymentApps.ALL.size, PaymentApps.ALL.map { it.pkg }.toSet().size)

    @Test fun defaultAllowedIsWholeCatalog() {
        assertEquals(PaymentApps.ALL.map { it.pkg }, DEFAULT_ALLOWED_PACKAGES)
        assertTrue(DEFAULT_ALLOWED_PACKAGES.size >= 70)
        assertFalse("com.whatsapp" in DEFAULT_ALLOWED_PACKAGES)
    }

    @Test fun gatewaysAreMerchantSide() {
        for (p in listOf("com.razorpay.payments.app", "com.phonepe.app.business", "com.paytm.business", "com.bharatpe.app")) {
            assertTrue(p, PaymentApps.byPackage(p)!!.isMerchantSide)
            assertEquals(AppKind.GATEWAY, PaymentApps.byPackage(p)!!.kind)
        }
        assertFalse(PaymentApps.byPackage(phonepe)!!.isMerchantSide)
    }

    @Test fun kindsAndCategories() {
        assertEquals(AppKind.BANK, PaymentApps.byPackage(sbi)!!.kind)
        assertEquals(Category.FOOD, PaymentApps.byPackage("in.swiggy.android")!!.defaultCategory)
        assertEquals(Category.TRAVEL, PaymentApps.byPackage("com.ubercab")!!.defaultCategory)
        assertNull(PaymentApps.byPackage(gpay)!!.defaultCategory)
    }

    // ------------------------------------------------------------ notification phrasings
    @Test fun gpayPaidTo() = check(gpay, "Google Pay", "₹250 paid to Swiggy", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun gpayPaymentToWasSuccessful() =
        check(gpay, "Google Pay", "Payment to Swiggy was successful · ₹250", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun gpayYouPaidRamesh() = check(gpay, "Google Pay", "You paid ₹250 to Ramesh", 25000, TxnType.DEBIT, "Ramesh")
    @Test fun phonepePaidToSwiggy() = check(phonepe, "PhonePe", "Paid ₹250 to Swiggy", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun phonepeMoneySent() = check(phonepe, "PhonePe", "Money sent successfully ₹500 to Ramesh", 50000, TxnType.DEBIT, "Ramesh")
    @Test fun phonepeAutoPay() = check(phonepe, "PhonePe", "₹199 debited for Netflix AutoPay", 19900, TxnType.DEBIT, "Netflix")
    @Test fun paytmFromPaytmUpi() = check(paytm, "Paytm", "Paid ₹120 to Chai Point from Paytm UPI", 12000, TxnType.DEBIT, "Chai Point")
    @Test fun paytmPaidSuccessfully() = check(paytm, "Paytm", "₹120 paid successfully to Chai Point", 12000, TxnType.DEBIT, "Chai Point")
    @Test fun credCardBillIsExcluded() {
        val p = NotificationParser.parse(cred, "CRED", "payment of ₹2,000 to HDFC credit card successful")
        assertNotNull(p)
        assertEquals(200000L, p!!.amountPaise)
        assertTrue(p.excluded)
    }
    @Test fun credUpiPayment() = check(cred, "CRED", "₹450 paid to Blue Tokai via CRED UPI", 45000, TxnType.DEBIT, "Blue Tokai")
    @Test fun amazonPayPaid() = check(amazon, "Amazon Pay", "₹499 paid to Amazon", 49900, TxnType.DEBIT, "Amazon")
    @Test fun mobikwikPayment() = check(mobikwik, "MobiKwik", "Payment of ₹100 to Ravi Stores successful", 10000, TxnType.DEBIT, "Ravi Stores")
    @Test fun bhimSent() = check(bhim, "BHIM", "₹50 sent to Suresh", 5000, TxnType.DEBIT, "Suresh")
    @Test fun samsungWalletStore() =
        check("com.samsung.android.spay", "Samsung Wallet", "Payment of ₹120.00 to ABC Store was successful", 12000, TxnType.DEBIT, "Abc Store")
    @Test fun bankAppDebitToVpa() =
        check(sbi, "YONO SBI", "₹1,250 debited from A/c XX1234 to VPA x@ybl", 125000, TxnType.DEBIT, "X")
    @Test fun swiggyOrderPlacedPaid() =
        check("in.swiggy.android", "Swiggy", "Order placed! ₹349 paid", 34900, TxnType.DEBIT, "Swiggy")
    @Test fun zomatoOrderConfirmed() =
        check("com.application.zomato", "Zomato", "Your order of ₹349 is confirmed", 34900, TxnType.DEBIT, "Zomato")
    @Test fun uberTripPaid() = check("com.ubercab", "Uber", "₹180 paid for your trip", 18000, TxnType.DEBIT, "Uber")
    @Test fun irctcBooking() = check("cris.org.in.prs.ima", "IRCTC", "Booking confirmed. ₹1,245 paid", 124500, TxnType.DEBIT, "IRCTC")
    @Test fun netflixCharged() = check("com.netflix.mediaclient", "Netflix", "₹199 charged for your plan", 19900, TxnType.DEBIT, "Netflix")
    @Test fun spotifyCharged() = check("com.spotify.music", "Spotify", "₹119 charged for your plan", 11900, TxnType.DEBIT, "Spotify")
    @Test fun razorpayMerchantReceived() =
        check("com.razorpay.payments.app", "Razorpay", "₹500 received from Rahul", 50000, TxnType.CREDIT, "Rahul")
    @Test fun bharatpeMerchantPaidBy() =
        check("com.bharatpe.app", "BharatPe", "₹300 paid by Rahul", 30000, TxnType.CREDIT, "Rahul")
    @Test fun shoppingAppUsesCatalogName() =
        check("com.lenskart.app", "Lenskart", "Order placed! ₹1,999 paid", 199900, TxnType.DEBIT, "Lenskart")
    @Test fun categoryHintFromCatalog() {
        val p = NotificationParser.parse("com.lenskart.app", "Lenskart", "Order placed! ₹1,999 paid")!!
        assertEquals(Category.SHOPPING, p.categoryHint)
    }

    // ------------------------------------------------------------ must be null
    @Test fun nullRequestOf() = nul(gpay, "Google Pay", "Request of ₹500 from Ramesh")
    @Test fun nullXRequested() = nul(gpay, "Google Pay", "Ramesh requested ₹500")
    @Test fun nullPayToComplete() = nul("in.swiggy.android", "Swiggy", "Pay ₹349 to complete your order")
    @Test fun nullBillDue() = nul(paytm, "Paytm", "Your bill of ₹799 is due")
    @Test fun nullGetCashback() = nul(phonepe, "PhonePe", "Get ₹100 cashback")
    @Test fun nullWonCashback() = nul(phonepe, "PhonePe", "You won ₹50 cashback")
    @Test fun nullFlatOff() = nul("com.myntra.android", "Myntra", "Flat ₹200 off on your next order")
    @Test fun nullFailed() = nul(phonepe, "PhonePe", "Payment of ₹250 failed")
    @Test fun nullPending() = nul(phonepe, "PhonePe", "Payment of ₹250 pending")
    @Test fun nullRefundInitiated() = nul("com.flipkart.android", "Flipkart", "Refund of ₹349 initiated")
    @Test fun refundCreditedIsCredit() = check("com.flipkart.android", "Flipkart", "Refund of ₹349 credited to your account", 34900, TxnType.CREDIT, "Flipkart")
    @Test fun nullAddToWalletPrompt() = nul(paytm, "Paytm", "Add ₹500 to wallet")
    @Test fun nullWalletBalance() = nul(paytm, "Paytm", "Your wallet balance is ₹120")
    @Test fun nullOtp() = nul(paytm, "Paytm", "123456 is your OTP for payment of ₹250 to Swiggy. Do not share.")
    @Test fun nullOtpLoose() = nul(bhim, "BHIM", "Use OTP 4821 to verify ₹500 transaction")
    @Test fun walletTopUpIsExcludedTransfer() {
        val p = NotificationParser.parse(paytm, "Paytm", "₹250 added to your wallet")
        assertNotNull(p)
        assertTrue(p!!.excluded)
        assertEquals(25000L, p.amountPaise)
    }
    @Test fun cashbackAddedToWalletIsNull() = nul(paytm, "Paytm", "₹50 cashback added to your wallet")

    // ------------------------------------------------------------ gateway merchant cleanup
    private fun smsMerchant(body: String) = SmsParser.parse("VM-HDFCBK", body)?.merchant

    @Test fun razorpayStar() =
        assertEquals("Swiggy", smsMerchant("Rs.250.00 debited from A/c XX1234 on 23-Oct-26 to RAZORPAY*SWIGGY. UPI Ref 123456789012"))
    @Test fun payuStar() =
        assertEquals("Myntra", smsMerchant("Rs.899.00 debited from A/c XX1234 on 23-Oct-26 to PAYU*MYNTRA. UPI Ref 123456789012"))
    @Test fun cashfreeStar() =
        assertEquals("Zomato", smsMerchant("Rs.300.00 debited from A/c XX1234 on 23-Oct-26 to CASHFREE*ZOMATO. UPI Ref 123456789012"))
    @Test fun bharatpeStar() = check(phonepe, "PhonePe", "Paid ₹40 to BHARATPE*RAMESH TEA STALL", 4000, TxnType.DEBIT, "Ramesh Tea Stall")
    @Test fun razorpayStarNotification() = check(gpay, "GPay", "Paid ₹250 to RAZORPAY*SWIGGY", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun vpaRzpSuffix() = check(gpay, "GPay", "Paid ₹250 to swiggy.rzp@icici", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun vpaRazorpayPrefix() = check(gpay, "GPay", "Paid ₹250 to razorpay.swiggy@axisbank", 25000, TxnType.DEBIT, "Swiggy")
    @Test fun vpaPaytmQrHasNoMerchant() = check(paytm, "Paytm", "Paid ₹60 to paytmqr28100505010111xyz@paytm", 6000, TxnType.DEBIT, null)
    @Test fun vpaBharatpeNumberHasNoRealMerchant() {
        val m = NotificationParser.parse(phonepe, "PhonePe", "Paid ₹60 to bharatpe.90123456@icici")!!.merchant
        assertTrue("got $m", m == null || com.local.salarytracker.parse.MerchantCleaner.isGatewayName(m))
    }
    @Test fun plainVpaStillWorks() = check(gpay, "GPay", "Paid ₹99 to zomato@paytm", 9900, TxnType.DEBIT, "Zomato")
    @Test fun gatewayOnlyNameIsDetected() {
        assertTrue(com.local.salarytracker.parse.MerchantCleaner.isGatewayName("Razorpay Software"))
        assertTrue(com.local.salarytracker.parse.MerchantCleaner.isGatewayName("PhonePe Merchant"))
        assertFalse(com.local.salarytracker.parse.MerchantCleaner.isGatewayName("Swiggy"))
    }
    @Test fun gatewayNameBeatsNothingLosesToShop() {
        assertEquals("Swiggy", MerchantPick.better("Razorpay Software", "Swiggy"))
        assertEquals("Swiggy", MerchantPick.better("Swiggy", "Razorpay Software"))
    }

    // ------------------------------------------------------------ gateway => business, never PEOPLE
    private fun gwCat(m: String?, text: String, via: Boolean) =
        com.local.salarytracker.parse.Categorizer.categorize(m, text, emptyMap(), via)

    @Test fun paytmRazorpayTestcafeNotPeople() {
        val p = NotificationParser.parse(paytm, "Paytm", "Paid ₹61 to RAZORPAY*TESTCAFE from Paytm UPI")!!
        assertEquals("Testcafe", p.merchant)
        assertTrue(p.viaGateway)
        assertEquals(Category.OTHER, gwCat(p.merchant, "Paid ₹61 to RAZORPAY*TESTCAFE from Paytm UPI", p.viaGateway))
    }
    @Test fun sbiRzpVpaNotPeople() {
        val t = "₹1,251 debited from A/c XX1234 to VPA testshop.rzp@icici"
        val p = NotificationParser.parse(sbi, "YONO SBI", t)!!
        assertEquals("Testshop", p.merchant)
        assertTrue(p.viaGateway)
        assertEquals(Category.OTHER, gwCat(p.merchant, t, p.viaGateway))
    }
    @Test fun payuSingleWordNotPeople() {
        val t = "Rs.300.00 debited from A/c XX1234 on 23-Oct-26 to PAYU*BOLTCO. UPI Ref 123456789012"
        val p = SmsParser.parse("VM-HDFCBK", t)!!
        assertTrue(p.viaGateway)
        assertEquals(Category.OTHER, gwCat(p.merchant, t, p.viaGateway))
    }
    @Test fun cashfreeSingleWordNotPeople() {
        val t = "Paid ₹80 to CASHFREE*KUMAR via UPI"
        val p = NotificationParser.parse(phonepe, "PhonePe", t)!!
        assertTrue(p.viaGateway)
        assertEquals(Category.OTHER, gwCat(p.merchant, t, p.viaGateway))
    }
    @Test fun p2mSmsNotPeople() {
        val t = "Rs.150.00 debited from A/c XX1234 on 23-Oct-26. UPI/P2M/123456789012/Lakshmi. Not you? Call 18002586161"
        val p = SmsParser.parse("VM-HDFCBK", t)!!
        assertTrue(p.viaGateway)
        assertEquals(Category.OTHER, gwCat(p.merchant, t, p.viaGateway))
    }
    @Test fun bharatpeTeaStallIsFood() {
        val t = "Paid ₹40 to BHARATPE*RAMESH TEA STALL"
        val p = NotificationParser.parse(phonepe, "PhonePe", t)!!
        assertTrue(p.viaGateway)
        assertEquals(Category.FOOD, gwCat(p.merchant, t, p.viaGateway))
    }
    @Test fun plainPersonStillPeople() {
        val t = "Paid ₹250 to Ramesh"
        val p = NotificationParser.parse(gpay, "GPay", t)!!
        assertFalse(p.viaGateway)
    }
    @Test fun groceryAppsAreGroceryKind() {
        for (pkg in listOf("in.swiggy.android.instamart", "com.zeptoconsumerapp", "com.grofers.customerapp", "com.bigbasket.mobileapp", "com.jpl.jiomart")) {
            val a = PaymentApps.byPackage(pkg)!!
            assertEquals(pkg, AppKind.GROCERY, a.kind)
            assertEquals(pkg, Category.GROCERIES, a.defaultCategory)
        }
        assertEquals(AppKind.FOOD, PaymentApps.byPackage("in.swiggy.android")!!.kind)
    }

    // ------------------------------------------------------------ smart mode
    private fun smart(title: String?, text: String?, pkg: String = "com.some.shop") = NotificationParser.parseSmart(pkg, title, text)

    @Test fun smartAcceptsPaid() = assertEquals(25000L, smart("Order update", "You paid ₹250 to Cafe One")!!.amountPaise)
    @Test fun smartAcceptsDebited() = assertNotNull(smart("Account", "₹1,250 debited from A/c XX1234 to VPA x@ybl"))
    @Test fun smartAcceptsPaymentSuccessful() = assertNotNull(smart("Payment successful", "Payment of ₹499 to Acme was successful"))
    @Test fun smartAcceptsSentRupees() = assertNotNull(smart("Wallet", "You sent ₹500 to Ramesh"))
    @Test fun smartAcceptsSpent() = assertNotNull(smart("Card", "You spent ₹800 at Decathlon"))
    @Test fun smartAcceptsPurchaseOf() = assertNotNull(smart("Card", "Purchase of ₹1,100 at Reliance Trends"))
    @Test fun smartAcceptsAutopayDebited() = assertNotNull(smart("Autopay", "Auto-debit of ₹299 for Gym successful"))
    @Test fun smartAcceptsMoneySent() = assertNotNull(smart("Money sent", "Money sent ₹150 to Anil"))
    @Test fun smartNeedsCurrencyAmount() = assertNull(smart("Done", "You paid 250 to Cafe One"))
    @Test fun smartNeedsPaymentPhrase() = assertNull(smart("Offer", "Your order of ₹349 is confirmed"))
    @Test fun smartRejectsRequest() = assertNull(smart("Pay", "Ramesh requested ₹500 from you"))
    @Test fun smartRejectsPromo() = assertNull(smart("Sale", "Paid users get ₹100 cashback"))
    @Test fun smartRejectsFailed() = assertNull(smart("Payment", "Payment of ₹250 failed"))
    @Test fun smartRejectsFuture() = assertNull(smart("Reminder", "₹799 will be debited on 5 Nov"))
    @Test fun smartRejectsOtp() = assertNull(smart("Bank", "OTP is 123456 for payment of ₹250"))
    @Test fun smartRejectsThirdPartyPaid() = assertNull(smart("Splitwise", "Ramesh paid ₹500 for Dinner"))
    @Test fun smartRejectsPaidYou() = assertNull(smart("Wallet", "Ramesh paid you ₹500"))
    @Test fun smartRejectsCredit() = assertNull(smart("Bank", "₹500 credited to your account"))
    @Test fun smartRejectsSmsApp() = assertNull(smart("HDFCBK", "Rs.250 debited from A/c XX1234", "com.google.android.apps.messaging"))
    @Test fun smartRejectsWhatsapp() = assertNull(smart("Ramesh", "I paid ₹500 to the shop, you paid ₹250 to me", "com.whatsapp"))
    @Test fun blocklistContents() {
        for (p in listOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.instagram.android", "com.facebook.katana",
            "com.facebook.orca", "com.snapchat.android", "com.twitter.android", "com.linkedin.android", "com.google.android.gm",
            "com.microsoft.office.outlook", "com.truecaller", "com.discord", "com.google.android.apps.messaging", "android",
            "com.android.systemui", "com.google.android.gms", "com.samsung.android.app.smartcapture")) {
            assertTrue(p, SmartDetect.isBlockedPackage(p))
        }
        assertFalse(SmartDetect.isBlockedPackage("com.some.shop"))
    }

    // ------------------------------------------------------------ cross-app dedupe
    private val t0 = 1_700_000_000_000L
    private fun k(src: TxnSource, ts: Long = t0, amount: Long = 25000, ref: String? = null, type: TxnType = TxnType.DEBIT) =
        TxnKey(amount, type, ts, ref, src)

    @Test fun notifNotifSameAmountWithin10Min() =
        assertTrue(Dedupe.isDuplicate(k(TxnSource.NOTIFICATION), k(TxnSource.NOTIFICATION, t0 + 9 * 60_000)))
    @Test fun notifNotifOutsideWindow() =
        assertFalse(Dedupe.isDuplicate(k(TxnSource.NOTIFICATION), k(TxnSource.NOTIFICATION, t0 + 11 * 60_000)))
    @Test fun notifNotifDifferentAmount() =
        assertFalse(Dedupe.isDuplicate(k(TxnSource.NOTIFICATION), k(TxnSource.NOTIFICATION, amount = 25100)))
    @Test fun notifNotifDifferentType() =
        assertFalse(Dedupe.isDuplicate(k(TxnSource.NOTIFICATION), k(TxnSource.NOTIFICATION, type = TxnType.CREDIT)))
    @Test fun smsSmsWithoutRefStaySeparate() =
        assertFalse(Dedupe.isDuplicate(k(TxnSource.SMS), k(TxnSource.SMS, t0 + 60_000)))
    @Test fun smsSmsSharedRefMerge() =
        assertTrue(Dedupe.isDuplicate(k(TxnSource.SMS, ref = "429612345678"), k(TxnSource.SMS, t0 + 3_600_000, ref = "429612345678")))
    @Test fun notifNotifDifferentRefsSeparate() =
        assertFalse(Dedupe.isDuplicate(k(TxnSource.NOTIFICATION, ref = "111111111111"), k(TxnSource.NOTIFICATION, ref = "222222222222")))
    @Test fun manualNeverMergesWithNotif() =
        assertFalse(Dedupe.isDuplicate(k(TxnSource.MANUAL), k(TxnSource.NOTIFICATION)))

    // ------------------------------------------------------------ merchant pick
    @Test fun shopBeatsGateway() = assertEquals("Swiggy", MerchantPick.better("Razorpay", "Swiggy"))
    @Test fun nameBeatsVpa() = assertEquals("Ramesh Kumar", MerchantPick.better("ramesh@ybl", "Ramesh Kumar"))
    @Test fun nameBeatsPhone() = assertEquals("Ramesh", MerchantPick.better("9876543210", "Ramesh"))
    @Test fun nullKeepsOld() = assertEquals("Swiggy", MerchantPick.better("Swiggy", null))
    @Test fun missingOldTakesNew() = assertEquals("Swiggy", MerchantPick.better(null, "Swiggy"))
    @Test fun tieKeepsLonger() = assertEquals("Swiggy Instamart", MerchantPick.better("Swiggy", "Swiggy Instamart"))
}
