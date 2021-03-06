package com.jetug.gallery.pro.models

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type


@Entity(tableName = "settings", indices = [Index(value = ["path"], unique = true)])
data class FolderSettings (
    @PrimaryKey(autoGenerate = true) var id: Long? = null,
    @ColumnInfo(name = "path") var path: String = "",
    @ColumnInfo(name = "group") var group: String = "",
    @ColumnInfo(name = "order") var order: ArrayList<String> = arrayListOf(),
    @ColumnInfo(name = "sorting") var sorting: Int = 0
) {
    constructor() : this(null, "", "", arrayListOf(), 0)
}

class JsonToStringConverter {
    @TypeConverter
    fun fromArrayList(list: ArrayList<String>): String {
        val gson = Gson()
        return gson.toJson(list)
    }
    @TypeConverter
    fun fromString(value: String): ArrayList<String> {
        val listType: Type = object : TypeToken<ArrayList<String?>?>(){}.type
        return Gson().fromJson(value, listType)
    }
}
