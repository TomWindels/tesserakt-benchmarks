package writer

import java.io.File

fun inputToOutputDir(outputFolder: File, inputFile: File, index: Int): File {
    val base = inputToOutputDir(outputFolder, inputFile)
    return File(base.parentFile, base.nameWithoutExtension + "_$index.csv")
}

fun inputToOutputDir(outputFolder: File, inputFile: File): File {
    check(outputFolder.isDirectory) { "Output location should be a valid directory!" }
    return File(outputFolder, inputFile.nameWithoutExtension + ".csv")
}
