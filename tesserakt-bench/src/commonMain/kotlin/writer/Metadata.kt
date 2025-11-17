package writer

import Path


interface Metadata {
    fun MetadataWriteContext.write()
}

object NoMetadata : Metadata {
    override fun MetadataWriteContext.write() {
        // nothing to do
    }
}

class MetadataWriteContext(private val root: Path) {
    fun file(filename: String, block: FileWriter.() -> Unit) {
        val path = Path(root, filename)
        if (path.exists()) {
            return
        }
        val writer = FileWriter(path)
        writer.use {
            block(it)
        }
    }
}

fun writeMetadata(parent: Path, metadata: Metadata) {
    val ctx = MetadataWriteContext(root = parent)
    with (metadata) {
        ctx.write()
    }
}
