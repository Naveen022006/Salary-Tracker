package com.local.salarytracker.parse

import com.local.salarytracker.model.Category

/** Kind of app; used for the label in Settings and to decide whether the app itself is the merchant. */
enum class AppKind(val label: String) {
    UPI("UPI app"), WALLET("Wallet"), BANK("Bank"), CARD("Card"), SHOPPING("Shopping"), FOOD("Food"),
    GROCERY("Grocery"), TRAVEL("Travel"), ENTERTAINMENT("Entertainment"), BILLS("Bills"), GATEWAY("Business payments"),
}

/**
 * One known payment-related app.
 * @param name display name (Settings)
 * @param merchantName name stored as merchant when the app itself is the shop (shopping / food / travel ...)
 * @param defaultCategory category hint used only when nothing better is known
 * @param isMerchantSide shop-owner app: payments shown are money RECEIVED
 */
data class PaymentApp(
    val pkg: String,
    val name: String,
    val kind: AppKind,
    val defaultCategory: Category? = null,
    val isMerchantSide: Boolean = false,
    val merchantName: String = name,
) {
    /** Shopping-like apps are themselves the payee of a payment shown in them. */
    val appIsMerchant: Boolean
        get() = kind in setOf(AppKind.SHOPPING, AppKind.FOOD, AppKind.GROCERY, AppKind.TRAVEL, AppKind.ENTERTAINMENT, AppKind.BILLS)
}

/** Catalog of payment apps, wallets, bank apps and gateways used in India. */
object PaymentApps {
    private fun upi(p: String, n: String) = PaymentApp(p, n, AppKind.UPI)
    private fun wallet(p: String, n: String) = PaymentApp(p, n, AppKind.WALLET)
    private fun bank(p: String, n: String) = PaymentApp(p, n, AppKind.BANK)
    private fun shop(p: String, n: String, m: String = n, c: Category = Category.SHOPPING) = PaymentApp(p, n, AppKind.SHOPPING, c, merchantName = m)
    private fun food(p: String, n: String, m: String = n) = PaymentApp(p, n, AppKind.FOOD, Category.FOOD, merchantName = m)
    private fun grocery(p: String, n: String, m: String = n) = PaymentApp(p, n, AppKind.GROCERY, Category.GROCERIES, merchantName = m)
    private fun travel(p: String, n: String, m: String = n) = PaymentApp(p, n, AppKind.TRAVEL, Category.TRAVEL, merchantName = m)
    private fun fun_(p: String, n: String, m: String = n) = PaymentApp(p, n, AppKind.ENTERTAINMENT, Category.ENTERTAINMENT, merchantName = m)
    private fun gateway(p: String, n: String) = PaymentApp(p, n, AppKind.GATEWAY, null, isMerchantSide = true)

    val ALL: List<PaymentApp> = listOf(
        // UPI / wallets
        upi("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        upi("com.phonepe.app", "PhonePe"),
        upi("net.one97.paytm", "Paytm"),
        upi("in.org.npci.upiapp", "BHIM"),
        upi("com.dreamplug.androidapp", "CRED"),
        PaymentApp("in.amazon.mShop.android.shopping", "Amazon / Amazon Pay", AppKind.SHOPPING, Category.SHOPPING, merchantName = "Amazon"),
        wallet("com.mobikwik_new", "MobiKwik"),
        wallet("com.freecharge.android", "Freecharge"),
        PaymentApp("com.myairtelapp", "Airtel Thanks", AppKind.BILLS, Category.BILLS, merchantName = "Airtel"),
        PaymentApp("com.jio.myjio", "MyJio", AppKind.BILLS, Category.BILLS, merchantName = "Jio"),
        upi("com.naviapp", "Navi"),
        upi("com.epifi.paisa", "Fi"),
        upi("money.jupiter", "Jupiter"),
        PaymentApp("indwin.c3.shareapp", "slice", AppKind.CARD),
        upi("money.super.payments", "super.money"),
        upi("com.nextbillion.groww", "Groww UPI"),
        wallet("com.samsung.android.spay", "Samsung Wallet"),
        wallet("com.enstage.wibmo.hdfc", "PayZapp"),
        upi("com.sbi.upi", "BHIM SBI Pay"),
        wallet("com.google.android.apps.walletnfcrel", "Google Wallet"),
        // Banks
        bank("com.sbi.lotusintouch", "YONO SBI"),
        bank("com.sbi.SBIFreedomPlus", "YONO Lite SBI"),
        bank("com.snapwork.hdfc", "HDFC Bank"),
        bank("com.csam.icici.bank.imobile", "iMobile (ICICI)"),
        bank("com.axis.mobile", "Axis Mobile"),
        bank("com.msf.kbank.mobile", "Kotak"),
        bank("com.bankofbaroda.mconnect", "bob World"),
        bank("com.Version1", "PNB ONE"),
        bank("com.canarabank.mobility", "Canara ai1"),
        bank("com.infrasoft.uboi", "Union Vyom"),
        bank("com.idfcfirstbank.optimus", "IDFC FIRST Bank"),
        bank("com.fss.indus", "IndusInd"),
        bank("com.yesbank", "YES Bank"),
        bank("com.fedmobile", "FedMobile"),
        bank("com.aubank.aubbanking", "AU Bank"),
        // Shopping
        shop("com.flipkart.android", "Flipkart"),
        shop("com.myntra.android", "Myntra"),
        shop("com.meesho.supply", "Meesho"),
        shop("com.ril.ajio", "AJIO"),
        shop("com.fsn.nykaa", "Nykaa"),
        shop("com.tatadigital.tcp", "Tata Neu", "Tata Neu"),
                shop("com.lenskart.app", "Lenskart"),
        // Food / grocery
        food("in.swiggy.android", "Swiggy"),
        grocery("in.swiggy.android.instamart", "Swiggy Instamart"),
        food("com.application.zomato", "Zomato"),
        food("com.Dominos", "Domino's", "Dominos"),
        food("com.mcdonalds.mobileapp", "McDonald's", "McDonalds"),
        grocery("com.zeptoconsumerapp", "Zepto"),
        grocery("com.grofers.customerapp", "Blinkit"),
        grocery("com.bigbasket.mobileapp", "BigBasket"),
        grocery("com.jpl.jiomart", "JioMart"),
        // Travel
        travel("com.ubercab", "Uber"),
        travel("com.olacabs.customer", "Ola"),
        travel("com.rapido.passenger", "Rapido"),
        travel("cris.org.in.prs.ima", "IRCTC Rail Connect", "IRCTC"),
        travel("com.makemytrip", "MakeMyTrip"),
        travel("com.goibibo", "Goibibo"),
        travel("in.redbus.android", "redBus"),
        travel("com.cleartrip.android", "Cleartrip"),
        travel("com.ixigo.train.ixitrain", "ixigo trains", "Ixigo"),
        // Entertainment
        fun_("com.bt.bms", "BookMyShow"),
        fun_("com.net.pvr", "PVR INOX", "PVR"),
        fun_("in.startv.hotstar", "Hotstar"),
        fun_("com.netflix.mediaclient", "Netflix"),
        fun_("com.spotify.music", "Spotify"),
        fun_("com.amazon.avod.thirdpartyclient", "Prime Video"),
        fun_("com.google.android.youtube", "YouTube", "YouTube Premium"),
        // Gateways / shop-owner apps (money received)
        gateway("com.razorpay.payments.app", "Razorpay"),
        gateway("com.phonepe.app.business", "PhonePe Business"),
        gateway("com.paytm.business", "Paytm for Business"),
        gateway("com.bharatpe.app", "BharatPe"),
        gateway("com.instamojo.app", "Instamojo"),
    )

    private val BY_PKG: Map<String, PaymentApp> = ALL.associateBy { it.pkg }

    fun byPackage(pkg: String?): PaymentApp? = if (pkg == null) null else BY_PKG[pkg]
    val PACKAGES: List<String> get() = ALL.map { it.pkg }
}
