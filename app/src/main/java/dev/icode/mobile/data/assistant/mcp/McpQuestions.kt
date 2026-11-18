package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.domain.McpAnswer
import dev.icode.mobile.domain.McpField
import dev.icode.mobile.domain.McpQuestion
import io.modelcontextprotocol.kotlin.sdk.types.BooleanSchema
import io.modelcontextprotocol.kotlin.sdk.types.DoubleSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestFormParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestURLParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.IntegerSchema
import io.modelcontextprotocol.kotlin.sdk.types.LegacyTitledEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.PrimitiveSchemaDefinition
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.StringSchemaFormat
import io.modelcontextprotocol.kotlin.sdk.types.TitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.TitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A server's or an agent's questions to the user (docs/assistant-and-mcp.md, elicitation): one at a time,
 * as a card in the chat; a form of the flat fields the spec allows, or a page of the asker's own, opened
 * only when the user chooses to. While one waits, a call's own time stands still ([asking]).
 */
internal class McpQuestions(private val publish: (McpQuestion?) -> Unit) {
    private val turn = Mutex()
    private val ids = AtomicLong()
    @Volatile private var waiting: Pair<Long, CompletableDeferred<McpAnswer>>? = null

    /** A question is on the card now. */
    val asking: Boolean get() = waiting != null

    suspend fun ask(server: String, request: ElicitRequest): ElicitResult {
        lateinit var asked: McpQuestion
        val answer = ask { id ->
            when (val params = request.params) {
                is ElicitRequestURLParams -> McpQuestion(id, server, params.message, url = params.url)
                is ElicitRequestFormParams -> McpQuestion(id, server, params.message, fields(params.requestedSchema.properties, params.requestedSchema.required.orEmpty().toSet()))
            }.also { asked = it }
        }
        return result(answer, asked)
    }

    /** The question [make] makes of its ID, on the card until the user answers it, after any asked before it. */
    suspend fun ask(make: (Long) -> McpQuestion): McpAnswer = turn.withLock {
        val id = ids.incrementAndGet()
        val question = make(id)
        val answer = CompletableDeferred<McpAnswer>()
        waiting = id to answer
        publish(question)
        try {
            answer.await()
        } finally {
            waiting = null
            publish(null)
        }
    }

    /** The user's answer to the question on the card; one to another question is not taken. */
    fun answer(answer: McpAnswer) {
        waiting?.takeIf { it.first == answer.id }?.second?.complete(answer)
    }

    /** The turn stopped: the question goes, answered Cancel. */
    fun cancel() {
        waiting?.let { (id, answer) -> answer.complete(McpAnswer(id, McpAnswer.Action.Cancel)) }
    }

    private fun result(answer: McpAnswer, question: McpQuestion): ElicitResult = when (answer.action) {
        McpAnswer.Action.Decline -> ElicitResult(ElicitResult.Action.Decline)
        McpAnswer.Action.Cancel -> ElicitResult(ElicitResult.Action.Cancel)
        McpAnswer.Action.Accept -> ElicitResult(
            ElicitResult.Action.Accept,
            if (question.url != null) null else JsonObject(question.fields.mapNotNull { field -> answer.values[field.name]?.let { field.name to json(field, it) } }.toMap()),
        )
    }

    /** A form's value as its field's type: multiple choices come joined by new lines. */
    private fun json(field: McpField, value: String): JsonElement = when (field.kind) {
        McpField.Kind.Integer -> value.trim().toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
        McpField.Kind.Number -> value.trim().toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value)
        McpField.Kind.Flag -> JsonPrimitive(value == "true")
        McpField.Kind.Choices -> JsonArray(value.lines().filter(String::isNotEmpty).map(::JsonPrimitive))
        else -> JsonPrimitive(value)
    }

    private fun fields(properties: Map<String, PrimitiveSchemaDefinition>, required: Set<String>): List<McpField> =
        properties.map { (name, schema) -> field(name, schema, name in required) }

    private fun field(name: String, schema: PrimitiveSchemaDefinition, required: Boolean): McpField = when (schema) {
        is StringSchema -> McpField(name, schema.title ?: name, schema.description.orEmpty(), kind(schema.format), required, default = schema.default)
        is IntegerSchema -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Integer, required, default = schema.default?.toString())
        is DoubleSchema -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Number, required, default = schema.default?.toString())
        is BooleanSchema -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Flag, required, default = schema.default?.toString())
        is UntitledSingleSelectEnumSchema -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Choice, required, schema.enumValues.map { it to it }, schema.default)
        is TitledSingleSelectEnumSchema -> McpField(name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Choice, required, schema.oneOf.map { it.const to it.title }, schema.default)
        is LegacyTitledEnumSchema -> McpField(
            name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Choice, required,
            schema.enumValues.mapIndexed { index, value -> value to (schema.enumNames?.getOrNull(index) ?: value) }, schema.default,
        )
        is UntitledMultiSelectEnumSchema -> McpField(
            name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Choices, required,
            schema.items.enumValues.map { it to it }, schema.default?.joinToString("\n"),
        )
        is TitledMultiSelectEnumSchema -> McpField(
            name, schema.title ?: name, schema.description.orEmpty(), McpField.Kind.Choices, required,
            schema.items.anyOf.map { it.const to it.title }, schema.default?.joinToString("\n"),
        )
        else -> McpField(name, name, "", McpField.Kind.Text, required)
    }

    private fun kind(format: StringSchemaFormat?): McpField.Kind = when (format) {
        StringSchemaFormat.Email -> McpField.Kind.Email
        StringSchemaFormat.Uri -> McpField.Kind.Uri
        StringSchemaFormat.Date -> McpField.Kind.Date
        StringSchemaFormat.DateTime -> McpField.Kind.DateTime
        null -> McpField.Kind.Text
    }
}
