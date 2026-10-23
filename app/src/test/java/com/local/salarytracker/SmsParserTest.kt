package com.local.salarytracker

import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnType
import com.local.salarytracker.parse.Categorizer
import com.local.salarytracker.parse.ParsedTxn
import com.local.salarytracker.parse.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private fun debit(
        sender: String, body: String, paise: Long,
        merchant: String? = null, last4: String? = null, ref: String? = null, excluded: Boolean = false,
    ): ParsedTxn {
        val p = SmsParser.parse(sender, body)
        assertNotNull("expected a txn for: $body", p)
        p!!
        assertEquals("amount: $body", paise, p.amountPaise)
        assertEquals("type: $body", TxnType.DEBIT, p.type)
        if (merchant != null) assertEquals("merchant: $body", merchant, p.merchant)
        if (last4 != null) assertEquals("last4: $body", last4, p.accountLast4)
        if (ref != null) assertEquals("ref: $body", ref, p.upiRef)
        assertEquals("excluded: $body", excluded, p.excluded)
        return p
    }

    private fun credit(
        sender: String, body: String, paise: Long,
        merchant: String? = null, salary: Boolean = false, excluded: Boolean = false,
    ): ParsedTxn {
        val p = SmsParser.parse(sender, body)
        assertNotNull("expected a txn for: $body", p)
        p!!
        assertEquals("amount: $body", paise, p.amountPaise)
        assertEquals("type: $body", TxnType.CREDIT, p.type)
        if (merchant != null) assertEquals("merchant: $body", merchant, p.merchant)
        assertEquals("salary: $body", salary, p.isSalary)
        assertEquals("excluded: $body", excluded, p.excluded)
        return p
    }

    private fun ignored(sender: String, body: String) =
        assertNull("should be ignored: $body", SmsParser.parse(sender, body))

    // ------------------------------------------------------------------ debits
    @Test fun hdfcUpiSent() {
        debit(
            "VM-HDFCBK",
            "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 23/10/26 Ref 429612345678 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808",
            25000, "Swiggy", "1234", "429612345678",
        )
    }

    @Test fun sbiUpiTransferToPerson() {
        val p = debit(
            "AX-SBIUPI",
            "Dear UPI user A/C X1234 debited by 150.0 on date 23Oct26 trf to RAMESH KUMAR Refno 429612345679. If not u? call 1800111109. -SBI",
            15000, "Ramesh Kumar", "1234", "429612345679",
        )
        assertEquals(Category.PEOPLE, Categorizer.categorize(p.merchant, "trf to RAMESH KUMAR UPI", emptyMap()))
    }

    @Test fun iciciCardSpend() {
        debit(
            "JD-ICICIT-S",
            "INR 1,299.00 spent on ICICI Bank Card XX5678 on 22-Oct-26 at AMAZON PAY IN. Avl Lmt: INR 45,000.00",
            129900, "Amazon Pay In", "5678",
        )
    }

    @Test fun axisUpiP2m() {
        debit(
            "AD-AXISBK",
            "INR 500.00 debited A/c no. XX1234 22-10-26 12:30:11 UPI/P2M/412345678901/SWIGGY SMS BLOCKUPI Cust ID to 919951860002 Not you? Call 18004195959 -Axis Bank",
            50000, "Swiggy", "1234", "412345678901",
        )
    }

    @Test fun iciciUpiDebitWithNameBeforeCredited() {
        debit(
            "VM-ICICIT",
            "ICICI Bank Acct XX123 debited for Rs 200.00 on 05-Oct-26; RAMESH KUMAR credited. UPI:429612345680. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.",
            20000, "Ramesh Kumar", null, "429612345680",
        )
    }

    @Test fun kotakUpiToVpa() {
        debit(
            "BZ-KOTAKB",
            "Sent Rs.75.00 from Kotak Bank AC X4321 to swiggy@icici on 22-10-26.UPI Ref 429612345681. Not you? Call 1800 batch",
            7500, "Swiggy", "4321", "429612345681",
        )
    }

    @Test fun bobDrCr() {
        debit(
            "VM-BOBSMS",
            "Rs.250.00 Dr. from A/C XXXXXX1234 and Cr. to ramesh@upi. Ref:429612345682. AvlBal Rs12345.00 (2026:10:23 10:21:33) -BOB",
            25000, "Ramesh", "1234", "429612345682",
        )
    }

    @Test fun canaraDebitedUppercase() {
        debit(
            "AX-CANBNK-S",
            "An amount of INR 250.00 has been DEBITED to your account XXX123 on 22/10/2026 Total Avail.bal INR 5,000.00 - Canara Bank",
            25000,
        )
    }

    @Test fun pnbUpiSlashForm() {
        debit(
            "VM-PNBSMS",
            "A/c 1234 debited INR 500.00 on 22-10-2026 by UPI/P2M/412345678903/ZOMATO. Bal INR 1000. -PNB",
            50000, "Zomato", "1234", "412345678903",
        )
    }

    @Test fun indusindTowards() {
        val p = debit(
            "VK-INDUSB",
            "Rs 1250.00 debited from your IndusInd Bank A/c XX4455 on 22-Oct-26 towards ELECTRICITY BOARD. Ref 12345678901234.",
            125000, "Electricity Board", "4455",
        )
        assertEquals(Category.BILLS, Categorizer.categorize(p.merchant, "", emptyMap()))
    }

    @Test fun paytmPaymentsBank() {
        val p = debit(
            "VM-PAYTMB",
            "Paid Rs.120 to Chai Point from Paytm Payments Bank a/c. UPI Ref 429612345683.",
            12000, "Chai Point", null, "429612345683",
        )
        assertEquals(Category.FOOD, Categorizer.categorize(p.merchant, "", emptyMap()))
    }

    @Test fun airtelPaymentsBank() {
        val p = debit(
            "AD-AIRBNK",
            "Rs.299.00 debited from your Airtel Payments Bank a/c XX9876 towards Airtel recharge on 22-10-26. Txn ID 123456789012",
            29900, "Airtel Recharge", "9876",
        )
        assertEquals(Category.BILLS, Categorizer.categorize(p.merchant, "", emptyMap()))
    }

    @Test fun hdfcCardPos() {
        debit(
            "VM-HDFCBK",
            "Spent Rs.500 On HDFC Bank Card 1234 At SWIGGY On 2026-10-22:12:30:11.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 1234 to 7308080808",
            50000, "Swiggy", "1234",
        )
    }

    @Test fun atmWithdrawal() {
        val p = debit(
            "VM-HDFCBK",
            "Rs.5000.00 withdrawn from HDFC Bank A/c XX1234 at ATM on 22-10-26. Avl Bal Rs.12000.00",
            500000, null, "1234",
        )
        assertEquals(Category.CASH, Categorizer.categorize(p.merchant, "withdrawn from HDFC ATM", emptyMap()))
    }

    @Test fun sbiAtmDebitedBy() {
        debit("VM-SBIINB", "Your a/c no. XXXXXXXX4821 is debited by Rs.2000.00 on 22Oct26 ATM WDL. -SBI", 200000, null, "4821")
    }

    @Test fun neftDebit() {
        debit(
            "AX-HDFCBK",
            "INR 10,000.00 debited from A/c XX1234 on 22-10-26 and credited to beneficiary RAJESH via NEFT. UTR: SBIN2610221234",
            1000000, null, "1234",
        )
    }

    @Test fun impsSentToPerson() {
        debit(
            "VM-ICICIB",
            "Rs.2,500.00 sent via IMPS to AMIT SHARMA Ref No 429612345690 from A/c XX1234. -ICICI",
            250000, "Amit Sharma", "1234", "429612345690",
        )
    }

    @Test fun federalVpa() {
        val p = debit(
            "VM-FEDBNK",
            "Rs 80.00 debited via UPI on 22-10-2026 12:00:00 to VPA tea.point@ybl.Ref No 429612345691 -Federal Bank",
            8000, "Tea Point", null, "429612345691",
        )
        assertEquals(Category.FOOD, Categorizer.categorize(p.merchant, "", emptyMap()))
    }

    @Test fun debitCardEnding() {
        debit(
            "VM-SBICRD",
            "Rs.1,500.00 spent on your SBI Debit Card ending 4821 at BIG BAZAAR on 22/10/26.",
            150000, "Big Bazaar", "4821",
        )
    }

    @Test fun axisCardNetflix() {
        val p = debit(
            "AX-AXISBK",
            "Spent INR 799 on Axis Bank Card no. XX7777 on 22-10-2026 at NETFLIX. Avl limit INR 20,000",
            79900, "Netflix", "7777",
        )
        assertEquals(Category.ENTERTAINMENT, Categorizer.categorize(p.merchant, "", emptyMap()))
    }

    @Test fun cardTransactionOf() {
        debit(
            "VM-HDFCBK",
            "Transaction of Rs.2,100.00 done on your HDFC Bank Credit Card ending 4321 at FLIPKART on 22-Oct-26.",
            210000, "Flipkart", "4321",
        )
    }

    @Test fun sbiPurchaseAtCroma() {
        debit(
            "VM-SBIINB",
            "Dear Customer, Rs.1,000.00 has been debited from your A/c XXXXXXXX4821 on 22Oct26 for purchase at CROMA. -SBI",
            100000, "Croma", "4821",
        )
    }

    @Test fun upiLiteToStall() {
        val p = debit(
            "VM-YESBNK",
            "Rs.50 paid to MUNIYAPPAN TEA STALL via UPI Lite. Ref 429612345694 -YesBank",
            5000, "Muniyappan Tea Stall", null, "429612345694",
        )
        assertEquals(Category.FOOD, Categorizer.categorize(p.merchant, "", emptyMap()))
    }

    // ------------------------------------------------------------------ real-phone formats (SBI, no currency symbol)
    @Test fun sbiUpiNoCurrencySymbolPerson() {
        val p = debit(
            "JD-SBIUPI-S",
            "Dear UPI user A/C X4821 debited by 30.00 on date 15May26 trf to M Lakshmi Refno 002283701111 If not u? call-1800111109 for other services-18001234-SBI",
            3000, "M Lakshmi", "4821", "002283701111",
        )
        assertEquals(
            Category.PEOPLE,
            Categorizer.categorize(p.merchant, "Dear UPI user A/C X4821 debited by 30.00 trf to M Lakshmi", emptyMap()),
        )
    }

    @Test fun sbiUpiNoCurrencySymbolShop() {
        debit(
            "VA-SBIUPI-S",
            "Dear UPI user A/C X4821 debited by 25.00 on date 26Apr26 trf to NEW ROYAL CORNE Refno 611617180000 If not u? call-1800111109 for other services-18001234-SBI",
            2500, "New Royal Corne", "4821", "611617180000",
        )
    }

    @Test fun sbiCreditTransfer() {
        credit(
            "AD-SBIINB-S",
            "Dear SBI User, your A/c X4821-credited by Rs.120 on 19May26 transfer from K  RAVI Ref No 613955070000 -SBI",
            12000, "K Ravi", salary = false,
        )
    }

    // ------------------------------------------------------------------ excluded
    @Test fun creditCardBillPaymentReceived() {
        credit(
            "VM-HDFCBK",
            "Payment of Rs.15,000.00 received towards your HDFC Bank Credit Card ending 4321 on 22-Oct-26. Thank you.",
            1500000, excluded = true,
        )
    }

    @Test fun creditCardBillViaCred() {
        debit(
            "VM-HDFCBK",
            "Sent Rs.15000.00 From HDFC Bank A/C *1234 To CRED Club On 22/10/26 Ref 429612345695",
            1500000, "Cred Club", "1234", "429612345695", excluded = true,
        )
    }

    @Test fun creditCardBillNetBanking() {
        debit(
            "VM-ICICIB",
            "Rs.20,000.00 debited from A/c XX1234 for Credit Card bill payment of card ending 4321.",
            2000000, excluded = true,
        )
    }

    @Test fun selfTransfer() {
        debit(
            "AX-SBIUPI",
            "Dear UPI user A/C X1234 debited by 5000.00 on date 22Oct26 trf to SELF Refno 429612345696. If not u? call 1800111109. -SBI",
            500000, excluded = true,
        )
    }

    @Test fun ownAccountTransfer() {
        debit(
            "VM-HDFCBK",
            "Rs.10,000.00 debited from A/c XX1234 and transferred to own account XX9999 via IMPS",
            1000000, excluded = true,
        )
    }

    // ------------------------------------------------------------------ credits
    @Test fun salaryNeft() {
        credit(
            "VM-HDFCBK",
            "Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 25/10/26 for NEFT Cr-ACME0001234-ACME TECHNOLOGIES PVT LTD SALARY-REF123. Avl bal INR 90,000.00",
            8500000, salary = true,
        )
    }

    @Test fun salaryShortSal() {
        credit(
            "AX-SBIINB",
            "Dear Customer, Rs.62,500.00 credited to your A/c XX4821 on 01-10-26 by NEFT-ABCD-SAL. Avl Bal Rs.70000",
            6250000, salary = true,
        )
    }

    @Test fun refundCredit() {
        credit(
            "VM-HDFCBK",
            "Refund of Rs.499.00 for your order has been credited to your A/c XX1234 on 22-10-26. -HDFC Bank",
            49900,
        )
    }

    @Test fun upiCreditHdfc() {
        val p = credit(
            "VM-HDFCBK",
            "Rs. 500.00 credited to a/c XXXXXX1234 on 22-10-26 by VPA ramesh@ybl (UPI Ref No 429612345692)",
            50000, "Ramesh",
        )
        assertEquals("429612345692", p.upiRef)
    }

    @Test fun iciciCreditFromPerson() {
        credit(
            "VM-ICICIT",
            "ICICI Bank Acct XX123 credited with Rs 1,000.00 on 22-Oct-26 from RAJ KUMAR. UPI Ref 429612345693.",
            100000, "Raj Kumar",
        )
    }

    @Test fun cashbackCredited() {
        credit("VM-PAYTMB", "Cashback of Rs.20.00 credited to your Paytm Payments Bank account", 2000)
    }

    // ------------------------------------------------------------------ negatives
    @Test fun otpIgnored() {
        ignored("VM-HDFCBK", "123456 is your OTP for txn of Rs.500.00 at AMAZON. Do not share it with anyone. -HDFC Bank")
        ignored("VM-ICICIB", "Dear Customer, use OTP 556677 to authorise transaction of Rs 2,000 at FLIPKART. Valid for 5 min. -ICICI Bank")
        ignored("AX-SBIINB-S", "837185 is OTP to login to SBI Internet banking(Personal) . Do not share with anyone. -SBI")
    }

    @Test fun promosIgnored() {
        ignored("VM-HDFCBK", "Congratulations! You are pre-approved for a personal loan of Rs 5,00,000. Apply now: http://bit.ly/x -HDFC Bank")
        ignored("VM-PAYTMB", "Get up to Rs.500 cashback on your first UPI txn. Use code SAVE. T&C apply. -Paytm")
        ignored("AD-AXISBK", "Dear Customer, you are eligible for an instant loan up to Rs 2,00,000. Click here to avail.")
        ignored("VM-SWIGGY-S", "Flat 50% OFF up to Rs.100 on your next order! Use code WELCOME")
    }

    @Test fun balanceOnlyIgnored() {
        ignored("AX-SBIINB", "Your A/c XX1234 balance is Rs.12,345.67 as on 23-Oct-26. -SBI")
        ignored("VM-ICICIB", "Avl Bal in A/c XX1234: INR 5,000.00. -ICICI")
    }

    @Test fun failedIgnored() {
        ignored("VM-HDFCBK", "Your UPI txn of Rs.500.00 to SWIGGY has failed. Amount debited, if any, will be refunded in 3-5 days. -HDFC")
        ignored("VM-ICICIB", "Transaction of Rs 2,000 declined on card XX1234 due to insufficient funds")
        ignored("VM-SWIGGY-S", "Your payment for Swiggy order #224784052563947 was not completed. Any amount if debited from UPI will get refunded within 4-7 days.")
    }

    @Test fun futureAndDueIgnored() {
        ignored("VM-HDFCBK", "Rs.999.00 will be debited from your A/c XX1234 on 25-Oct-26 for auto-pay of NETFLIX.")
        ignored("VM-HDFCBK", "Your credit card bill of Rs.12,345.00 is due on 28-Oct-26. Pay now to avoid late fee. -HDFC")
        ignored("VM-HDFCBK", "Statement for card XX1234: Total Amt Due Rs.12,000, Min Amt Due Rs.600 by 05-Nov")
        ignored("VM-HDFCBK", "Reminder: EMI of Rs 5,000 due on 05-Nov for your loan account")
    }

    @Test fun collectRequestIgnored() {
        ignored("VM-HDFCBK", "ramesh@ybl has requested money Rs.500.00 on UPI. Approve using your UPI app")
    }

    @Test fun mandateIgnored() {
        ignored("VM-SBIUPI-S", "Your UPI-Mandate for Rs.15000.00 is successfully created towards AWS India from A/c No: XXXXXX4821. UMN:4edb24f5a1f4@oksbi. If not you, kindly report on 18001234. -SBI")
        ignored("VM-SBIUPI-S", "Your UPI-Mandate is successfully cancelled towards AWS India for 15000.00 from A/c No.XXXXXX4821. UMN:4e6c3c9f9109@oksbi -SBI")
    }

    @Test fun deliveryCodeAndMisc() {
        ignored("VM-SWIGGY-S", "PLEASE SHARE THIS CODE AFTER CHECKING ALL THE ITEMS IN THE DELIVERED ORDER : 9763. Swiggy Order 229530201378221 from Shawarmajaan")
        ignored("JX-JioPay-S", "உங்கள் ஜியோ ரீசார்ஜ் வெற்றிகரமாக முடிந்தது. தொகை 859.00 - Jio")
        ignored("JK-GPAY-S", "UPI Registration Initiated! By Google Pay for your Bank A/c.")
        ignored("AX-SBIINB", "Your UPI PIN has been changed successfully. -SBI")
    }

    @Test fun phoneNumberSendersIgnored() {
        val body = "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 23/10/26 Ref 429612345678"
        ignored("9876543210", body)
        ignored("+919812345678", body)
        ignored("Mom", body)
    }

    // ------------------------------------------------------------------ sender filter
    @Test fun senderFilter() {
        for (s in listOf("VM-HDFCBK", "AX-SBIUPI", "JD-ICICIT-S", "BZ-KOTAKB", "AD-AXISBK", "JK-SBIUPI-S", "AX-SBYONO-T", "JX-HDFCBK-S", "AD-SBIINB-S", "VA-SBIUPI-S", "HDFCBK")) {
            assertTrue(s, SmsParser.isBankSender(s))
        }
        for (s in listOf("9876543210", "+919876543210", "Mom", "", "12345")) assertFalse(s, SmsParser.isBankSender(s))
    }

    @Test fun amountPaiseExactness() {
        val p = SmsParser.parse("VM-HDFCBK", "Rs.1,00,000.50 debited from A/c XX1234 at SOME STORE on 22-10-26")
        assertEquals(10000050L, p!!.amountPaise)
        val q = SmsParser.parse("VM-HDFCBK", "Rs 99.5 debited from A/c XX1234 at SOME STORE on 22-10-26")
        assertEquals(9950L, q!!.amountPaise)
    }
}
