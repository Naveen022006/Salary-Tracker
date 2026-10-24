package com.local.salarytracker.data

import androidx.room.TypeConverter
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.LoanKind
import com.local.salarytracker.model.LoanMethod
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType

class Converters {
    @TypeConverter fun catToString(c: Category): String = c.name
    @TypeConverter fun stringToCat(s: String): Category = Category.fromName(s)
    @TypeConverter fun typeToString(t: TxnType): String = t.name
    @TypeConverter fun stringToType(s: String): TxnType = TxnType.valueOf(s)
    @TypeConverter fun srcToString(t: TxnSource): String = t.name
    @TypeConverter fun stringToSrc(s: String): TxnSource = TxnSource.valueOf(s)
    @TypeConverter fun kindToString(k: LoanKind): String = k.name
    @TypeConverter fun stringToKind(s: String): LoanKind = LoanKind.fromName(s) ?: LoanKind.GAVE
    @TypeConverter fun methodToString(m: LoanMethod): String = m.name
    @TypeConverter fun stringToMethod(s: String): LoanMethod = LoanMethod.fromName(s)
}
