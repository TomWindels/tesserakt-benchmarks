package writer

import Path

fun inputToOutputDir(
    outputFolder: Path?,
    inputFile: Path,
    implementation: Path,
    code: String,
    metadata: Metadata,
): Path? {
    outputFolder ?: return null
    val base = inputToOutputDir(outputFolder, inputFile, implementation, NoMetadata)
    val root = Path(
        base.parentPath.parentPath,
        base.parentPath.name,
        code,
    )
    writeMetadata(root, metadata)
    return Path(
        root,
        base.name
    )
}

fun inputToOutputDir(
    outputFolder: Path,
    inputFile: Path,
    implementation: Path,
    metadata: Metadata,
): Path {
    check(outputFolder.isDirectory) { "Output location should be a valid directory!" }
    val base = Path(outputFolder, inputFile.nameWithoutExtension)
    writeMetadata(parent = base, metadata = metadata)
    return Path(base, implementation.nameWithoutExtension + ".csv")
}

private fun Path(parent: Path, vararg paths: String): Path {
    var result = parent
    paths.forEach {
        result = Path(result, it)
    }
    return result
}
