package university.cli.util

import university.cli.command.CommandMessageType
import university.cli.command.CommandOutputLine
import university.cli.model.ResearchResult
import university.cli.model.ResearchStopReason

object ResearchResultFormatter {
    fun format(result: ResearchResult): List<CommandOutputLine> = buildList {
        add(CommandOutputLine("Answer", CommandMessageType.ACCENT))
        if (result.answer.claims.isEmpty()) {
            add(CommandOutputLine("No supported answer could be established.", CommandMessageType.MUTED))
        }
        result.answer.claims.forEach { claim ->
            val citations = claim.sourceIds.distinct().joinToString(", ", "[", "]")
            add(CommandOutputLine("${claim.text.trim()} $citations", CommandMessageType.SUCCESS))
        }
        if (result.answer.unresolved.isNotEmpty()) {
            add(CommandOutputLine(""))
            add(CommandOutputLine("Unresolved", CommandMessageType.ACCENT))
            result.answer.unresolved.forEach { add(CommandOutputLine(it.trim(), CommandMessageType.MUTED)) }
        }
        val citedIds = result.answer.claims.flatMap { it.sourceIds }.toSet()
        if (citedIds.isNotEmpty()) {
            add(CommandOutputLine(""))
            add(CommandOutputLine("Sources", CommandMessageType.ACCENT))
            result.trace.sources.filter { it.id in citedIds }.forEach { source ->
                add(CommandOutputLine("[${source.id}] ${source.fileName} · index ${source.indexId} · chunk ${source.chunkId}"))
            }
        }
        val stop = when (result.trace.stopReason) {
            ResearchStopReason.FINISHED -> "Research finished"
            ResearchStopReason.SEARCH_LIMIT -> "Search limit reached"
            ResearchStopReason.REPEATED_QUERY -> "Repeated query prevented"
            ResearchStopReason.NO_PROGRESS -> "No new sources found"
            ResearchStopReason.NO_EVIDENCE -> "No supporting evidence found"
        }
        add(CommandOutputLine(""))
        add(CommandOutputLine(
            "$stop · ${result.trace.steps.size} searches · ${result.trace.sources.size} sources · ${result.trace.tokenUsage.totalTokens} tokens",
            CommandMessageType.MUTED,
        ))
    }
}
