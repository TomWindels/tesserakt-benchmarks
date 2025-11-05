import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parsers.OptionInvocation
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.readText


class CLI : NoOpCliktCommand(name = "tesserakt-bench") {

    class CommonOptions(name: String) : OptionGroup("Benchmark options") {

        val engine: File by option("-e", "--engine")
            .file(mustExist = true, mustBeReadable = true)
            .help("`path/to/engine.so`")
            .required()

        private val _dryRun: Boolean by option("--dry-run")
            .flag(default = false)
            .help("Disables output writing")

        private val _output: File by option("-o", "--output")
            .file(mustExist = false, mustBeWritable = false) // writable check happens in `fin!_dryRunalize()`
            .help("`path/to/output_dir`, defaults to `./output/$name`")
            .default(File("output/$name"))

        val output: File? get() = if (_dryRun) null else _output

        val iterations: Int by option("--iterations")
            .int()
            .help("The number of iterations for every configuration, defaults to 5")
            .default(5)

        override fun finalize(context: Context, invocationsByOption: Map<Option, List<OptionInvocation>>) {
            super.finalize(context, invocationsByOption)
            if (!_dryRun) {
                check(_output.canWrite())
            }
        }

    }

    class Replay : CliktCommand(name = "replay") {

        private val common by CommonOptions(commandName)

        private val input: List<File> by argument("input", "`path/to/replay_benchmark.ttl` (multiple allowed)")
            .file(mustExist = true, mustBeReadable = true)
            .multiple(required = true)

        override fun run() {
            common.output?.mkdirs()
            evaluateReplay(
                lib = common.engine,
                inputs = input,
                output = common.output,
                iterations = common.iterations,
            )
        }
    }

    class Stream : CliktCommand(name = "stream") {

        private val common by CommonOptions(commandName)

        private val updateSize: Int by option("-s", "--size")
            .int()
            .help("The number of triples to add per iteration, defaults to 512")
            .default(512)

        private val queries: List<String> by option("-q", "--query")
            .help("The query to evaluate, can be a file (multiple allowed)")
            .multiple(required = true)

        private val input: List<File> by argument("input", "`path/to/dataset.ttl` (multiple allowed)")
            .file(mustExist = true, mustBeReadable = true)
            .multiple(required = true)

        override fun run() {
            common.output?.mkdirs()
            val queries = queries.readContents()
            evaluateStream(
                lib = common.engine,
                inputs = input,
                output = common.output,
                queries = queries,
                iterations = common.iterations,
                updateSize = updateSize,
            )
        }

    }

}

fun main(args: Array<String>) = CLI().subcommands(
    CLI.Replay(),
    CLI.Stream(),
).main(args)

/**
 * Reads [this] list as a potential set of filepaths (or mix of filepaths and regular strings), replacing the
 *  filepaths with their contents. May throw an exception for a filepath (like) string with no contents. The
 *  size of the returned list is at least equal to this list's size, but may increase in case of globbing.
 */
private fun List<String>.readContents(): List<String> {
    return flatMap { potentialPath ->
        try {
            Paths.get(potentialPath)
        } catch (_: InvalidPathException) {
            return@flatMap listOf(potentialPath)
        }
        // based on https://javapapers.com/java/glob-with-java-nio/
        val matcher = FileSystems.getDefault().getPathMatcher("glob:${potentialPath}")
        val location = potentialPath.substringBefore('*').substringBeforeLast('/')
        val paths = mutableListOf<Path>()
        Files.walkFileTree(Paths.get(location), object : SimpleFileVisitor<Path>() {
            override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (matcher.matches(path)) {
                    paths.add(path)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                return FileVisitResult.CONTINUE
            }
        })
        paths.map { it.readText() }
    }
}
