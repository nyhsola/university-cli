package university.cli.di

import com.github.ajalt.mordant.terminal.Terminal
import org.koin.dsl.bind
import org.koin.dsl.module
import university.cli.command.AskCommand
import university.cli.command.CommandDispatcher
import university.cli.command.ExitCommand
import university.cli.command.FilesCommand
import university.cli.command.HelpCommand
import university.cli.command.IndexCommand
import university.cli.command.ProfilesCommand
import university.cli.command.ReportCommand
import university.cli.command.SearchCommand
import university.cli.command.UnindexCommand
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
import university.cli.service.indexing.ChunkerService
import university.cli.service.configuration.IndexProfileService
import university.cli.service.configuration.QueryProfileService
import university.cli.service.evaluation.JudgeService
import university.cli.service.evaluation.ReportComparisonService
import university.cli.service.evaluation.ReportDatasetService
import university.cli.service.evaluation.ReportMetricsService
import university.cli.service.evaluation.ReportService
import university.cli.service.evaluation.ReportWriter
import university.cli.service.indexing.DocumentIndexingService
import university.cli.service.indexing.FixedSizeChunkerService
import university.cli.service.indexing.IndexRemovalService
import university.cli.service.indexing.ProjectIndexingService
import university.cli.service.indexing.ProjectFileService
import university.cli.service.indexing.ProjectFileStatusService
import university.cli.service.indexing.RecursiveChunkerService
import university.cli.service.indexing.SearchIndexService
import university.cli.service.llm.EmbedService
import university.cli.service.llm.OllamaService
import university.cli.service.operation.OperationCancellationService
import university.cli.service.operation.OperationLogService
import university.cli.service.retrieval.AskService
import university.cli.service.retrieval.ContextFormatter
import university.cli.service.retrieval.ContextSelectionService
import university.cli.service.retrieval.LexicalSearchService
import university.cli.service.retrieval.RankFusionService
import university.cli.service.retrieval.SearchService
import university.cli.service.retrieval.VectorService
import university.cli.repository.JdbcDocumentRepository
import university.cli.repository.JdbcChunkRepository
import university.cli.repository.JdbcIndexingConfigurationRepository
import university.cli.repository.JdbcLexicalRepository
import university.cli.repository.JdbcSearchIndexRepository
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
    single { JdbcChunkRepository(get()) }
    single { JdbcIndexingConfigurationRepository(get()) }
    single { JdbcLexicalRepository(get()) }
    single { JdbcSearchIndexRepository(get()) }
    single { JdbcVectorRepository(get()) }

    single { FixedSizeChunkerService() } bind ChunkerService::class
    single { RecursiveChunkerService() } bind ChunkerService::class
    single { IndexProfileService() }

    single { OllamaService(get()) }
    single { EmbedService(get()) }
    single { SearchIndexService(get()) }
    single { VectorService(get()) }
    single { LexicalSearchService(get()) }
    single { RankFusionService() }
    single { ContextFormatter() }
    single { ContextSelectionService(get()) }
    single { OperationCancellationService() }
    single { OperationLogService(get()) }
    single { DocumentIndexingService(getAll(), get(), get(), get(), get(), get()) }
    single { ProjectFileService(get()) }
    single { ProjectIndexingService(get(), get()) }
    single { IndexRemovalService(get(), get(), get()) }
    single { ProjectFileStatusService(get(), get(), get()) }
    single { SearchService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { QueryProfileService() }
    single { AskService(get(), get(), get(), get()) }
    single { JudgeService(get(), get()) }
    single { ReportDatasetService(get(), get()) }
    single { ReportMetricsService() }
    single { ReportComparisonService(get()) }
    single { ReportWriter(get()) }
    single { ReportService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    single { ChatStatusService() }
    single { ChatOutputService() }

    single { FilesCommand(get(), get()) }
    single { IndexCommand(get(), get(), get(), get(), get(), get(), get()) }
    single { UnindexCommand(get(), get(), get(), get()) }
    single { ProfilesCommand(get(), get()) }
    single { SearchCommand(get(), get(), get(), get(), get(), get()) }
    single { AskCommand(get(), get(), get(), get(), get(), get()) }
    single { ReportCommand(get(), get()) }
    single { ExitCommand() }
    single { HelpCommand(listOf(get<FilesCommand>(), get<IndexCommand>(), get<UnindexCommand>(), get<ProfilesCommand>(), get<SearchCommand>(), get<AskCommand>(), get<ReportCommand>(), get<ExitCommand>())) }

    single {
        CommandDispatcher(listOf(get<FilesCommand>(), get<IndexCommand>(), get<UnindexCommand>(), get<ProfilesCommand>(), get<SearchCommand>(), get<AskCommand>(), get<ReportCommand>(), get<HelpCommand>(), get<ExitCommand>()))
    }

    single { ChatService(get(), get(), get(), get(), get()) }
    single { CliService(get(), get(), get(), get()) }

    factory { AppLauncher(get(), get()) }
}
