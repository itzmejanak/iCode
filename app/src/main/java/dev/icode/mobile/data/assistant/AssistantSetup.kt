package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AssistantAnswerer
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantSettings

/** What a turn works with, as set when it starts: a change made while it runs is for the next one. */
internal class AssistantSetup(
    val settings: AssistantSettings,
    val connection: AssistantConnection,
    val model: AssistantModel,
    val place: AssistantWorkspace,
    val choice: AssistantChoice,
) {
    companion object {
        /**
         * The chat's own model, [answerer], while its provider still offers it, else what a new chat starts
         * with; refused, in the page's words, while nothing is set up. Then its [place].
         */
        fun of(settings: AssistantSettings, answerer: AssistantAnswerer?, providers: AssistantProviderStore, place: () -> AssistantWorkspace): AssistantSetup {
            val choice = (answerer as? AssistantChoice)?.takeIf { settings.modelOf(it) != null } ?: settings.choice
            val model = settings.modelOf(choice)
            val connection = settings.providerOf(choice)?.let { providers.connection(it.id) }
            if (choice == null || model == null || connection == null) throw AssistantTurnException(NOT_SET_UP)
            return AssistantSetup(settings, connection, model, place(), choice)
        }

        private const val NOT_SET_UP = "Set up the assistant first: add a provider and choose a model"
    }
}
