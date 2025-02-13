package dev.schlaubi.mikmusic.autocomplete

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.SingleConverter
import com.kotlindiscord.kord.extensions.commands.converters.impl.StringConverter
import dev.kord.core.behavior.interaction.suggestString

/**
 * Creates a `query` argument with [description] supporting YouTube Auto-complete.
 */
fun Arguments.autoCompletedYouTubeQuery(description: String): SingleConverter<String> {
    return arg(
        displayName = AUTOCOMPLETE_QUERY_OPTION,
        description = description,

        converter = StringConverter().apply {
            autoCompleter = { _, input ->
                if (!input.isNullOrBlank()) {
                    val youtubeResult = requestYouTubeAutoComplete(input)
                    suggestString {
                        youtubeResult.take(25).forEach { choice(it, it) }
                    }
                }
            }
        }
    )
}
