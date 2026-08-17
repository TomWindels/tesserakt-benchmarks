import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parsers.OptionInvocation


class CLI : SuspendingNoOpCliktCommand(name = "tesserakt-bench") {

    class CommonOptions(name: String) : OptionGroup("Benchmark options") {

        private val _engine: String by option("-e", "--engine")
            .help("`path/to/engine.{so,dll,jar,mjs}`")
            .required()

        val engine get() = Path(_engine)

        private val _dryRun: Boolean by option("--dry-run")
            .flag(default = false)
            .help("Disables output writing")

        val failFast: Boolean by option("--fail-fast")
            .flag(default = false)
            .help("Stops evaluation upon first failure")

        private val _output: String by option("-o", "--output")
            .help("`path/to/output_dir`, defaults to `./output/$name`")
            .default("./output/$name")

        val output: Path? get() = if (_dryRun) null else Path(_output)

        val iterations: Int by option("--iterations")
            .int()
            .help("The number of iterations for every configuration, defaults to 5")
            .default(5)

        override fun finalize(context: Context, invocationsByOption: Map<Option, List<OptionInvocation>>) {
            super.finalize(context, invocationsByOption)
            output?.let { location ->
                if (!location.canWrite()) {
                    context.fail("Cannot write to the destination $output")
                }
            }
        }

    }

    class Replay : SuspendingCliktCommand(name = "replay") {

        private val common by CommonOptions(commandName)

        private val _input: List<String> by argument("input", "`path/to/replay_benchmark.ttl` (multiple allowed)")
            .multiple(required = true)

        val input get() = _input.map { Path(it) }

        override suspend fun run() {
            val firstInvalidInput = input.firstOrNull { !it.exists() }
            if (firstInvalidInput != null) {
                currentContext.fail("Failed to find input file $firstInvalidInput")
            }
            common.output?.mkdirs()
            evaluateReplay(
                lib = common.engine,
                inputs = input,
                output = common.output,
                iterations = common.iterations,
                failFast = common.failFast,
            )
        }
    }

    class Stream : SuspendingCliktCommand(name = "stream") {

        private val common by CommonOptions(commandName)

        private val updateSize: Int by option("-s", "--size")
            .int()
            .help("The number of triples to add per iteration, defaults to 512")
            .default(512)

        private val queries: List<String> by option("-q", "--query")
            .help("The query to evaluate, can be a file (multiple allowed)")
            .multiple(required = true)

        private val _input: List<String> by argument("input", "`path/to/dataset.ttl` (multiple allowed)")
            .multiple(required = true)

        val input get() = _input.map { Path(it) }

        override suspend fun run() {
            val firstInvalidInput = input.firstOrNull { !it.exists() }
            if (firstInvalidInput != null) {
                currentContext.fail("Failed to find input file $firstInvalidInput")
            }
            common.output?.mkdirs()
            val queries = queries.readContents()
            evaluateStream(
                lib = common.engine,
                inputs = input,
                output = common.output,
                queries = queries,
                iterations = common.iterations,
                updateSize = updateSize,
                failFast = common.failFast,
            )
        }

    }

    class Query : SuspendingCliktCommand(name = "query") {

        private val common by CommonOptions(commandName)

        private val queries: List<String> by option("-q", "--query")
            .help("The query to evaluate, can be a file (multiple allowed)")
            .multiple(required = true)

        private val _input: List<String> by argument("input", "`path/to/dataset.ttl` (multiple allowed)")
            .multiple(required = true)

        val input get() = _input.map { Path(it) }

        override suspend fun run() {
            val firstInvalidInput = input.firstOrNull { !it.exists() }
            if (firstInvalidInput != null) {
                currentContext.fail("Failed to find input file $firstInvalidInput")
            }
            common.output?.mkdirs()
            val queries = queries.readContents()
            evaluateRegular(
                lib = common.engine,
                inputs = input,
                output = common.output,
                queries = queries,
                iterations = common.iterations,
                failFast = common.failFast,
            )
        }

    }

}

suspend fun main(args: Array<String>) = CLI().subcommands(
    CLI.Replay(),
    CLI.Stream(),
    CLI.Query(),
).main(args)
