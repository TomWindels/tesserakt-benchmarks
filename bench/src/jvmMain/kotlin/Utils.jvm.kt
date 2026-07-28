
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import kotlin.io.path.readText
import java.nio.file.Path as JavaPath

actual fun List<String>.readContents(): List<String> {
    return flatMap { potentialPath ->
        try {
            Paths.get(potentialPath)
        } catch (_: InvalidPathException) {
            return@flatMap listOf(potentialPath)
        }
        // based on https://javapapers.com/java/glob-with-java-nio/
        val matcher = FileSystems.getDefault().getPathMatcher("glob:${potentialPath}")
        val location = potentialPath.substringBefore('*').substringBeforeLast('/')
        val paths = mutableListOf<JavaPath>()
        Files.walkFileTree(Paths.get(location), object : SimpleFileVisitor<JavaPath>() {
            override fun visitFile(path: JavaPath, attrs: BasicFileAttributes): FileVisitResult {
                if (matcher.matches(path)) {
                    paths.add(path)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: JavaPath, exc: IOException): FileVisitResult {
                return FileVisitResult.CONTINUE
            }
        })
        paths.map { it.readText() }
    }
}

actual fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return md.digest(encodeToByteArray()).toHexString()
}
