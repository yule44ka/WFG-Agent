package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.cache.model.PromptCache
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A CodePromptExecutor that caches responses from a nested executor.
 *
 * @param cache The cache implementation to use
 * @param nested The nested executor to use for cache misses
 */
public class CachedPromptExecutor(
    private val cache: PromptCache,
    private val nested: PromptExecutor
) : PromptExecutor {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        return getOrPut(prompt, tools, model)
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> =
        flow { emit(getOrPut(prompt, model).content) }

    private suspend fun getOrPut(prompt: Prompt, model: LLModel): Message.Assistant {
        return cache.get(prompt)
            ?.first() as Message.Assistant?
            ?: nested
                .execute(prompt, model, emptyList()).first()
                .let { it as Message.Assistant }
                .also { cache.put(prompt, emptyList(), listOf(it)) }
    }

    private suspend fun getOrPut(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel): List<Message.Response> {
        return cache.get(prompt, tools) ?: nested.execute(prompt, model, tools).also { cache.put(prompt, tools, it) }
    }
}
