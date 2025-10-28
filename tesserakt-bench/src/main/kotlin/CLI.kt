import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import java.io.File

class CLI : CliktCommand(name = "tesserakt-bench") {

    private val engine: File by option("-e", "--engine")
        .file(mustExist = true, mustBeReadable = true)
        .help("path/to/implementation.so")
        .required()

    private val input: File by option("-i", "--input")
        .file(mustExist = true, mustBeReadable = true)
        .help("path/to/dataset.ttl")
        .required()

    private val output: File by option("-o", "--output")
        .file(mustExist = false, mustBeWritable = true)
        .help("path/to/output")
        .default(File("output"))

    override fun run() {
        output.mkdirs()
        evaluate(
            lib = engine,
            replay = input,
            output = output,
        )
    }
}

fun main(args: Array<String>) = CLI().main(args)
