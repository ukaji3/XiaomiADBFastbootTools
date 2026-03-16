interface CommandRunner {
    suspend fun check(printErr: Boolean = false): Boolean
    suspend fun exec(vararg args: MutableList<String>, redirectErrorStream: Boolean = true): String
}
