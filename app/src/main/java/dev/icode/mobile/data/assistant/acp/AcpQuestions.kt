package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.CreateElicitationRequest
import com.agentclientprotocol.model.CreateElicitationResponse
import com.agentclientprotocol.model.ElicitationAction
import com.agentclientprotocol.model.ElicitationContentValue
import com.agentclientprotocol.model.ElicitationMode
import com.agentclientprotocol.model.ElicitationPropertySchema
import com.agentclientprotocol.model.MultiSelectItems
import com.agentclientprotocol.model.StringFormat
import dev.icode.mobile.domain.McpAnswer
import dev.icode.mobile.domain.McpField
import dev.icode.mobile.domain.McpQuestion
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An agent's questions to the user (ACP elicitation; docs/assistant-and-mcp.md): one at a time, on the
 * card MCP servers' questions use — a form of flat fields, or a page of the agent's own, opened only when
 * the user chooses to. The same [McpQuestion] the card shows; only where its answer goes differs.
 */
@OptIn(UnstableApi::class)
internal class AcpQuestions(private val publish: (McpQuestion?) -> Unit) {
    private val turn = Mutex()
    private val ids = AtomicLong()
    @Volatile private var waiting: Pair<Long, CompletableDeferred<McpAnswer>>? = null

    /** [agent]'s [request], asked; what the user answered, as the agent reads it. */
    suspend fun ask(agent: String, request: CreateElicitationRequest): CreateElicitationResponse = turn.withLock {
        val question = question(ids.incrementAndGet(), agent, request)
        val answer = CompletableDeferred<McpAnswer>()
        waiting = question.id to answer
        publish(question)
        try {
            CreateElicitationResponse(action(answer.await(), question))
        } finally {
            waiting = null
            publish(null)
        }
    }

    fun answer(answer: McpAnswer) {
        waiting?.takeIf { it.first == answer.id }?.second?.complete(answer)
    }

    /** The turn stopped: the question goes, answered Cancel. */
    fun cancel() {
        waiting?.let { (id, answer) -> answer.complete(McpAnswer(id, McpAnswer.Action.Cancel)) }
    }

    companion object {
        fun question(id: Long, agent: String, request: CreateElicitationRequest): McpQuestion = when (val mode = request.mode) {
            is ElicitationMode.Url -> McpQuestion(id, agent, request.message, url = mode.url)
            is ElicitationMode.Form -> McpQuestion(
                id, agent, request.message,
                mode.requestedSchema.properties.map { (name, schema) -> field(name, schema, name in mode.requestedSchema.required.orEmpty()) },
            )
        }

        fun action(answer: McpAnswer, question: McpQuestion): ElicitationAction = when (answer.action) {
            McpAnswer.Action.Decline -> ElicitationAction.Decline
            McpAnswer.Action.Cancel -> ElicitationAction.Cancel
            McpAnswer.Action.Accept -> ElicitationAction.Accept(
                if (question.url != null) null
                else question.fields.mapNotNull { field -> answer.values[field.name]?.let { field.name to value(field, it) } }.toMap(),
            )
        }

        /** A form's value as its field's type: multiple choices come joined by new lines. */
        private fun value(field: McpField, value: String): ElicitationContentValue = when (field.kind) {
            McpField.Kind.Integer -> value.trim().toLongOrNull()?.let(ElicitationContentValue::IntegerValue) ?: ElicitationContentValue.StringValue(value)
            McpField.Kind.Number -> value.trim().toDoubleOrNull()?.let(ElicitationContentValue::NumberValue) ?: ElicitationContentValue.StringValue(value)
            McpField.Kind.Flag -> ElicitationContentValue.BooleanValue(value == "true")
            McpField.Kind.Choices -> ElicitationContentValue.StringArrayValue(value.lines().filter(String::isNotEmpty))
            else -> ElicitationContentValue.StringValue(value)
        }

        private fun field(name: String, schema: ElicitationPropertySchema, required: Boolean): McpField = when (schema) {
            is ElicitationPropertySchema.StringProperty -> when {
                schema.oneOf != null -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Choice, required, schema.oneOf!!.map { it.value to it.title }, schema.default)
                schema.enumValues != null -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Choice, required, schema.enumValues!!.map { it to it }, schema.default)
                else -> McpField(name, schema.title ?: name, schema.description.orEmpty(), kind(schema.format), required, default = schema.default)
            }
            is ElicitationPropertySchema.IntegerProperty -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Integer, required, default = schema.default?.toString())
            is ElicitationPropertySchema.NumberProperty -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Number, required, default = schema.default?.toString())
            is ElicitationPropertySchema.BooleanProperty -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Flag, required, default = schema.default?.toString())
            is ElicitationPropertySchema.ArrayProperty -> McpField(
                name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Choices, required,
                when (val items = schema.items) {
                    is MultiSelectItems.Untitled -> items.items.values.map { it to it }
                    is MultiSelectItems.Titled -> items.items.options.map { it.value to it.title }
                },
                schema.default?.joinToString("\n"),
            )
        }

        private fun kind(format: StringFormat?): McpField.Kind = when (format) {
            StringFormat.EMAIL -> McpField.Kind.Email
            StringFormat.URI -> McpField.Kind.Uri
            StringFormat.DATE -> McpField.Kind.Date
            StringFormat.DATE_TIME -> McpField.Kind.DateTime
            null -> McpField.Kind.Text
        }
    }
}
