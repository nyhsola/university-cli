package university.cli.config

data class DatabaseConfig(
    val jdbcUrl: String,
) {
    companion object {
        private const val DATABASE_FILE = "university.db"

        fun from(directoryConfig: DirectoryConfig): DatabaseConfig {
            val databasePath = directoryConfig.dataDirectory.resolve(DATABASE_FILE)
                .toString()
                .replace('\\', '/')

            return DatabaseConfig("jdbc:sqlite:$databasePath")
        }
    }
}
