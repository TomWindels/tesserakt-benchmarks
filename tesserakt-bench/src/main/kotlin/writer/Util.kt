package writer

import java.io.File

fun inputToOutputDir(
    outputFolder: File,
    inputFile: File,
    implementation: File,
    index: Int,
): File {
    val base = inputToOutputDir(outputFolder, inputFile, implementation)
    return File(base.parentFile, base.nameWithoutExtension + "_$index.csv")
}

fun inputToOutputDir(
    outputFolder: File,
    inputFile: File,
    implementation: File,
): File {
    check(outputFolder.isDirectory) { "Output location should be a valid directory!" }
    return File(outputFolder, implementation.nameWithoutExtension, inputFile.nameWithoutExtension + ".csv")
}

private fun File(parent: File, vararg paths: String): File {
    var result = parent
    paths.forEach {
        result = File(result, it)
    }
    return result
}
