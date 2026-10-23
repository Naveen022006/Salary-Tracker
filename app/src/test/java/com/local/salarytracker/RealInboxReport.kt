package com.local.salarytracker

import com.local.salarytracker.parse.Categorizer
import com.local.salarytracker.parse.SmsParser
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Manual check against a real exported inbox (sender<TAB>epochMs<TAB>body per line). Skipped unless
 * the env var REAL_INBOX points to such a file; writes the parse result next to it as *.parsed.tsv.
 */
class RealInboxReport {
    @Test
    fun parseRealInbox() {
        val path = System.getenv("REAL_INBOX")
        assumeTrue(path != null && File(path).exists())
        val input = File(path!!)
        val out = StringBuilder()
        input.readLines().forEach { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size < 3) return@forEach
            val (sender, _, body) = parts
            val p = SmsParser.parse(sender, body)
            val bank = SmsParser.isBankSender(sender)
            val result = if (p == null) "-" else
                "${p.type}\t${p.amountPaise / 100.0}\t${p.merchant}\t${Categorizer.categorize(p.merchant, body, emptyMap())}\tex=${p.excluded}\tsal=${p.isSalary}"
            out.append("${if (bank) "B" else "."}\t$sender\t$result\t${body.take(160)}\n")
        }
        File(input.parentFile, input.nameWithoutExtension + ".parsed.tsv").writeText(out.toString())
    }
}
