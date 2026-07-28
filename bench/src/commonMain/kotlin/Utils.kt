
/**
 * Reads [this] list as a potential set of filepaths (or mix of filepaths and regular strings), replacing the
 *  filepaths with their contents. May throw an exception for a filepath (like) string with no contents. The
 *  size of the returned list is at least equal to this list's size, but may increase in case of globbing.
 */
expect fun List<String>.readContents(): List<String>

expect fun String.md5(): String
