import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import java.io.File

class CLI : NoOpCliktCommand(name = "tesserakt-bench") {

    class CommonOptions: OptionGroup("Benchmark options") {

        val engine: File by option("-e", "--engine")
            .file(mustExist = true, mustBeReadable = true)
            .help("`path/to/engine.so`")
            .required()

        val output: File by option("-o", "--output")
            .file(mustExist = false, mustBeWritable = true)
            .help("`path/to/output_dir`, defaults to `./output`")
            .default(File("output"))

        val iterations: Int by option("--iterations")
            .int()
            .help("The number of iterations for every configuration, defaults to 5")
            .default(5)

    }

    class Replay : CliktCommand(name = "replay") {

        private val common by CommonOptions()

        private val input: List<File> by argument("input", "`path/to/replay_benchmark.ttl` (multiple allowed)")
            .file(mustExist = true, mustBeReadable = true)
            .multiple(required = true)

        override fun run() {
            common.output.mkdirs()
            evaluateReplay(
                lib = common.engine,
                inputs = input,
                output = common.output,
                iterations = common.iterations,
            )
        }
    }

    class Stream : CliktCommand(name = "stream") {

        private val common by CommonOptions()

        private val updateSize: Int by option("-s", "--size")
            .int()
            .help("The number of triples to add per iteration, defaults to 512")
            .default(512)

        private val query: String by option("-q", "--query")
            .help("The query to evaluate")
            .required()

        private val input: List<File> by argument("input", "`path/to/dataset.ttl` (multiple allowed)")
            .file(mustExist = true, mustBeReadable = true)
            .multiple(required = true)

        override fun run() {
            common.output.mkdirs()
            evaluateStream(
                lib = common.engine,
                inputs = input,
                output = common.output,
                query = query,
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
