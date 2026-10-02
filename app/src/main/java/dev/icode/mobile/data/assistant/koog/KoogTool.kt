package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.typeToken
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject
import ai.koog.serialization.JSONObject as KoogJSONObject

/**
 * An iCode tool as Koog runs one (§4.2): its descriptor made from the tool's own parameters (no
 * reflection), its arguments handed over as the JSON text the model wrote, and its refusals turned
 * into the error result the model reads. Koog's standard agent runs it (§12 R1).
 */
internal class KoogTool(private val tool: AssistantTool) : ToolBase<String, String>(
    argsType = typeToken<String>(),
    resultType = typeToken<String>(),
    descriptor = ToolDescriptor(
        name = tool.name,
        description = tool.description,
        requiredParameters = tool.parameters.filter { it.required }.map(::descriptor),
        optionalParameters = tool.parameters.filterNot { it.required }.map(::descriptor),
    ),
) {
    override fun decodeArgs(rawArgs: KoogJSONObject, serializer: JSONSerializer): String =
        serializer.encodeJSONElementToString(rawArgs)

    override suspend fun execute(args: String, metadata: ToolCallMetadata): String = try {
        // Arguments that were not an object never run, nor ask the user first: the model is told why.
        KoogArguments.unreadable(args)?.let { throw ToolException.ValidationFailure(KoogArguments.refusal(it)) }
        tool.run(JSONObject(args))
    } catch (stopped: CancellationException) {
        throw stopped
    } catch (invalid: ToolException) {
        throw invalid
    } catch (refused: AssistantToolException) {
        throw ToolException.ValidationFailure(refused.message ?: "${tool.name} refused")
    } catch (failure: Exception) {
        throw ToolException.ValidationFailure("${tool.name} failed: ${failure.message ?: failure.javaClass.simpleName}")
    }

    /** The output as the model reads it: the text itself, not a JSON string of it. */
    override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result

    /** The output as Koog's events carry it: the same text, read back with [JSONPrimitive.content]. */
    override fun encodeResult(result: String, serializer: JSONSerializer): JSONElement = JSONPrimitive(result)

    private companion object {
        fun descriptor(parameter: dev.icode.mobile.data.assistant.AssistantParameter) = ToolParameterDescriptor(
            name = parameter.name,
            description = parameter.description,
            type = when (parameter.type) {
                AssistantParameterType.Text -> ToolParameterType.String
                AssistantParameterType.Integer -> ToolParameterType.Integer
                AssistantParameterType.Boolean -> ToolParameterType.Boolean
            },
        )
    }
}
