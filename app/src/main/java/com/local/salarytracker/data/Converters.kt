package com.local.salarytracker.data

import androidx.room.TypeConverter
import com.local.salarytracker.model.Category
import com.local.salarytracker.model.TxnSource
import com.local.salarytracker.model.TxnType

class Converters {
    @TypeConverter fun catToString(c: Category): String = c.name
    @TypeConverter fun stringToCat(s: String): Category = Category.fromName(s)
    @TypeConverter fun typeToString(t: TxnType): String = t.name
    @TypeConverter fun stringToType(s: String): TxnType = TxnType.valueOf(s)
    @TypeConverter fun srcToString(t: TxnSource): String = t.name
    @TypeConverter fun stringToSrc(s: String): TxnSource = TxnSource.valueOf(s)
}
