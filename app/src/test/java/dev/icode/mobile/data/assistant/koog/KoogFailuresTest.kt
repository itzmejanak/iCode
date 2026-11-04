package dev.icode.mobile.data.assistant.koog

import ai.koog.http.client.KoogHttpClientException
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.domain.AssistantProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A server's refusals, said in plain words (owner's request, 2026-11-04), never as its JSON. */
class KoogFailuresTest {
    private val nvidia = AssistantConnection(AssistantProviderKind.OpenAICompatible, "https://integrate.api.nvidia.com/v1", null)

    private fun refusal(status: Int, body: String) = KoogHttpClientException("OpenAI", status, body)

    private fun said(status: Int, body: String, model: String? = "mercury-3") = KoogFailures.explain(refusal(status, body), nvidia, model).message

    /** The body the owner's screenshot showed: FastAPI's list of errors, Pydantic's prefix and all. */
    private val noPictures = """[{"type":"value_error","loc":["body"],"msg":"Value error, Image inputs are not supported for this model","ctx":{"error":"Image inputs are not supported for this model"}}]"""

    @Test fun `a picture refused is told as what to change, and known for the step to go on without it`() {
        assertEquals("“mercury-3” does not see pictures — turn off Reads Images for it in the provider's settings", said(400, noPictures))
        assertTrue(KoogFailures.refusesPictures(RuntimeException("wrapped", refusal(400, noPictures))))
        assertFalse(KoogFailures.refusesPictures(refusal(400, """{"error":{"message":"This model's maximum context length is 8192 tokens"}}""")))
        assertFalse(KoogFailures.refusesPictures(refusal(500, noPictures)))
    }

    @Test fun `each kind of refusal says what happened and what to do`() {
        assertEquals(
            "integrate.api.nvidia.com does not serve “deepseek-ai/deepseek-coder-6.7b-instruct” for chat — it may be listed and still not answer: choose another model",
            said(404, """{"status":404,"title":"Not Found","detail":"Function not found"}""", "deepseek-ai/deepseek-coder-6.7b-instruct"),
        )
        assertEquals("The provider's account has no credit left for this — add some with the provider", said(402, "{}"))
        assertEquals("The provider's account has no credit left for this — add some with the provider", said(429, """{"error":{"code":"insufficient_quota","message":"You exceeded your current quota"}}"""))
        assertEquals("“mercury-3” cannot call tools, which the assistant needs — choose another model", said(400, """{"error":{"message":"This model does not support tools"}}"""))
        assertEquals("integrate.api.nvidia.com had a problem on its side (503) — try again shortly", said(503, "<html>Service Unavailable</html>"))
        assertEquals("integrate.api.nvidia.com took too long to answer — try again", said(504, ""))
        assertEquals("This request is larger than the provider takes — remove a file, or compact the chat", said(413, ""))
        assertEquals(
            "integrate.api.nvidia.com takes no reasoning effort for “mercury-3” — choose Auto for its reasoning in the model picker",
            said(400, """{"error":{"message":"Unrecognized request argument supplied: reasoning_effort"}}"""),
        )
    }

    @Test fun `the server's own words are read whatever shape its error takes`() {
        assertEquals("The provider refused the request: Unknown parameter: seed", said(400, """{"error":{"message":"Unknown parameter: seed"}}"""))
        assertEquals("The provider refused the request: Bad body", said(400, """{"error":"Bad body"}"""))
        assertEquals("The provider refused the request: Temperature too high", said(422, """{"detail":[{"loc":["body","temperature"],"msg":"Temperature too high"}]}"""))
        assertEquals("The provider refused the request: Request body is not JSON", said(400, """{"type":"about:blank","title":"Bad Request","detail":"Request body is not JSON"}"""))
        assertEquals("The provider refused the request (400)", said(400, "not json at all"))
    }
}
