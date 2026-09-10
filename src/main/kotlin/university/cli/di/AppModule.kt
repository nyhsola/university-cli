package university.cli.di

import com.github.ajalt.mordant.terminal.Terminal
import org.koin.dsl.module
import university.cli.command.CommandDispatcher
import university.cli.command.ExitCommand
import university.cli.command.HelpCommand
import university.cli.command.IndexCommand
import university.cli.command.ListCommand
import university.cli.command.QuestionCommand
import university.cli.command.RelevantCommand
import university.cli.database.FlywayMigrator
import university.cli.config.DatabaseConfig
import university.cli.config.DirectoryConfig
import university.cli.config.OllamaConfig
import university.cli.database.SqliteVecDataSource
import university.cli.database.SqliteVecExtension
import university.cli.launcher.AppLauncher
import university.cli.service.chat.ChatOutputService
import university.cli.service.chat.ChatService
import university.cli.service.chat.ChatStatusService
import university.cli.service.cli.CliService
import university.cli.service.indexing.ChunkFileReaderService
import university.cli.service.indexing.ChunkFileWriterService
import university.cli.service.indexing.ChunkerService
import university.cli.service.indexing.DocumentIndexingService
import university.cli.service.indexing.FixedSizeChunkerService
import university.cli.service.indexing.ProjectIndexingService
import university.cli.service.llm.OllamaService
import university.cli.service.operation.OperationCancellationService
import university.cli.service.retrieval.QuestionService
import university.cli.service.retrieval.RelevantService
import university.cli.service.retrieval.VectorService
import university.cli.repository.JdbcDocumentRepository
import university.cli.repository.JdbcIndexingConfigurationRepository
import university.cli.repository.JdbcVectorRepository
import javax.sql.DataSource

val appModule = module {
    single { Terminal() }
    single { DirectoryConfig.fromWorkingDirectory() }
    single { DatabaseConfig.from(get()) }
    single { OllamaConfig.localhost() }

    single { SqliteVecExtension() }
    single<DataSource> { SqliteVecDataSource(get<DatabaseConfig>().jdbcUrl, get()) }
    single { FlywayMigrator(get(), get()) }
    single { JdbcDocumentRepository(get()) }
    single { JdbcIndexingConfigurationRepository(get()) }
    single { JdbcVectorRepository(get()) }

    single<ChunkerService> { FixedSizeChunkerService() }
    single { ChunkFileWriterService(get()) }
    single { ChunkFileReaderService(get()) }

    single { OllamaService(get()) }
    single { VectorService(get()) }
    single { OperationCancellationService() }
    single { DocumentIndexingService(getAll(), get(), get(), get(), get(), get(), get()) }
    single { ProjectIndexingService(get(), get()) }
    single { RelevantService(get(), get(), get(), get(), get(), get()) }
    single { QuestionService(get(), get(), get()) }

    single { ChatStatusService() }
    single { ChatOutputService() }

    single { IndexCommand(get(), get(), get(), get()) }
    single { ListCommand(get(), get()) }
    single { QuestionCommand(get(), get(), get()) }
    single { RelevantCommand(get(), get(), get()) }
    single { ExitCommand() }
    single { HelpCommand(listOf(get<IndexCommand>(), get<ListCommand>(), get<QuestionCommand>(), get<RelevantCommand>(), get<ExitCommand>())) }

    single { CommandDispatcher(listOf(get<IndexCommand>(), get<ListCommand>(), get<QuestionCommand>(), get<RelevantCommand>(), get<ExitCommand>(), get<HelpCommand>())) }

    single { ChatService(get(), get(), get(), get(), get()) }
    single { CliService(get(), get(), get(), get()) }

    factory { AppLauncher(get(), get()) }
}
