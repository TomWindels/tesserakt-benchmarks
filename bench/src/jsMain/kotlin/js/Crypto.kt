package js

@JsModule("crypto")
external object Crypto {

    fun createHash(type: String): HashInstance

    class HashInstance {

        fun update(value: String): HashInstance

        fun digest(args: dynamic): String

    }

}