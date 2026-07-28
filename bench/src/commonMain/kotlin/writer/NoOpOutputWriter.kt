package writer

object NoOpOutputWriter : OutputWriter {

    override fun append(result: ResultEntry) {
        // nothing to do
    }

    override fun writeReport(report: String) {
        // nothing to do
    }

    override fun close() {
        // nothing to do
    }

}
