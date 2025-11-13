expect class Path {

    constructor(absolutePath: String)
    constructor(parent: Path, child: String)

    val absolutePath: String
    val parentPath: Path
    val isDirectory: Boolean
    val name: String
    val nameWithoutExtension: String

    fun canWrite(): Boolean

    fun exists(): Boolean

    fun mkdirs()

}
