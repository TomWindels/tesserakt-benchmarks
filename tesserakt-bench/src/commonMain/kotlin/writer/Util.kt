package writer

import Path

fun inputToOutputDir(
    outputFolder: Path?,
    inputFile: Path,
    implementation: Path,
    index: Int,
): Path? {
    outputFolder ?: return null
    val base = inputToOutputDir(outputFolder, inputFile, implementation)
    return Path(base.parentPath.parentPath, base.parentPath.name + "_$index", base.name)
}

fun inputToOutputDir(
    outputFolder: Path?,
    inputFile: Path,
    implementation: Path,
    code: String,
): Path? {
    outputFolder ?: return null
    val base = inputToOutputDir(outputFolder, inputFile, implementation)
    return Path(
        base.parentPath.parentPath,
        base.parentPath.name,
        code,
        base.name
    )
}

fun inputToOutputDir(
    outputFolder: Path,
    inputFile: Path,
    implementation: Path,
): Path {
    check(outputFolder.isDirectory) { "Output location should be a valid directory!" }
    return Path(outputFolder, inputFile.nameWithoutExtension, implementation.nameWithoutExtension + ".csv")
}

private fun Path(parent: Path, vararg paths: String): Path {
    var result = parent
    paths.forEach {
        result = Path(result, it)
    }
    return result
}
