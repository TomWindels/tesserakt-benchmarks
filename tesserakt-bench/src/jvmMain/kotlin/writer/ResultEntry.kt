package writer

interface ResultEntry {

    fun toCsv(): String

    interface Type {

        val CSV_HEADER: String

    }

}
