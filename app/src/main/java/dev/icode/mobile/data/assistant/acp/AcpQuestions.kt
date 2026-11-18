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

/**
 * An agent's questions to the user (docs/assistant-and-mcp.md), as the MCP servers' are asked: on the
 * same card, one at a time — a form of flat fields, or a page of the agent's own, opened only when the
 * user chooses to. One iCode cannot show is cancelled, never answered for the user.
 */
@OptIn(UnstableApi::class)
internal object AcpQuestions {
    /** [request] from [agent], numbered [id], as the card asks it; null for one it cannot. */
    fun questionOf(id: Long, agent: String, request: CreateElicitationRequest): McpQuestion? = when (val mode = request.mode) {
        is ElicitationMode.Url -> McpQuestion(id, agent, request.message, url = mode.url)
        is ElicitationMode.Form -> {
            val schema = mode.requestedSchema
            val required = schema.required.orEmpty().toSet()
            McpQuestion(id, agent, request.message, schema.properties.orEmpty().map { (name, property) -> fieldOf(name, property, name in required) })
        }
        else -> null
    }

    /** The user's [answer] to [question], as the agent reads it: a form's values each as its field's type. */
    fun responseOf(answer: McpAnswer, question: McpQuestion): CreateElicitationResponse = CreateElicitationResponse(
        when (answer.action) {
            McpAnswer.Action.Decline -> ElicitationAction.Decline
            McpAnswer.Action.Cancel -> ElicitationAction.Cancel
            McpAnswer.Action.Accept -> ElicitationAction.Accept(
                question.fields.mapNotNull { field -> answer.values[field.name]?.let { field.name to valueOf(field, it) } }.toMap(),
            )
        },
    )

    /** A question iCode could not ask: the agent goes on without an answer. */
    val CANCELLED = CreateElicitationResponse(ElicitationAction.Cancel)

    private fun fieldOf(name: String, property: ElicitationPropertySchema, required: Boolean): McpField = when (property) {
        is ElicitationPropertySchema.StringProperty -> {
            val choices = property.oneOf?.map { it.value to (it.title ?: it.value) } ?: property.enumValues?.map { it to it }
            if (choices != null) McpField(name, property.title ?: name, property.description.orEmpty(), McpField.Kind.Choice, required, choices, property.default)
            else McpField(name, property.title ?: name, property.description.orEmpty(), kindOf(property.format), required, default = property.default)
        }
        is ElicitationPropertySchema.IntegerProperty -> McpField(name, property.title ?: name, property.description.orEmpty(), McpField.Kind.Integer, required, default = property.default?.toString())
        is ElicitationPropertySchema.NumberProperty -> McpField(name, property.title ?: name, property.description.orEmpty(), McpField.Kind.Number, required, default = property.default?.toString())
        is ElicitationPropertySchema.BooleanProperty -> McpField(name, property.title ?: name, property.description.orEmpty(), McpField.Kind.Flag, required, default = property.default?.toString())
        is ElicitationPropertySchema.ArrayProperty -> McpField(
            name, property.title ?: name, property.description.orEmpty(), McpField.Kind.Choices, required,
            when (val items = property.items) {
                is MultiSelectItems.Titled -> items.items.options.map { it.value to (it.title ?: it.value) }
                is MultiSelectItems.Untitled -> items.items.values.map { it to it }
                else -> emptyList()
            },
            property.default?.joinToString("\n"),
        )
        else -> McpField(name, name, "", McpField.Kind.Text, required)
    }

    private fun kindOf(format: StringFormat?): McpField.Kind = when (format) {
        StringFormat.EMAIL -> McpField.Kind.Email
        StringFormat.URI -> McpField.Kind.Uri
        StringFormat.DATE -> McpField.Kind.Date
        StringFormat.DATE_TIME -> McpField.Kind.DateTime
        null -> McpField.Kind.Text
    }

    /** A value as the agent reads its field's type: several choices come joined by new lines. */
    private fun valueOf(field: McpField, value: String): ElicitationContentValue = when (field.kind) {
        McpField.Kind.Integer -> value.trim().toLongOrNull()?.let(ElicitationContentValue::IntegerValue) ?: ElicitationContentValue.StringValue(value)
        McpField.Kind.Number -> value.trim().toDoubleOrNull()?.let(ElicitationContentValue::NumberValue) ?: ElicitationContentValue.StringValue(value)
        McpField.Kind.Flag -> ElicitationContentValue.BooleanValue(value == "true")
        McpField.Kind.Choices -> ElicitationContentValue.StringArrayValue(value.lines().filter(String::isNotEmpty))
        else -> ElicitationContentValue.StringValue(value)
    }
}
