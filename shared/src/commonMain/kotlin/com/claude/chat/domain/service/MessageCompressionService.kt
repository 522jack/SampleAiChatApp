package com.claude.chat.domain.service

import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Service for compressing chat history by summarizing old messages
 */
@OptIn(ExperimentalUuidApi::class)
class MessageCompressionService(
    private val apiClient: ClaudeApiClient
) {
    companion object {
        private const val COMPRESSION_TOKEN_THRESHOLD = 800 // Compress when messages exceed this token count
        private const val MAX_TOKENS = 4096
        private const val SUMMARIZATION_MODEL = "claude-3-5-haiku-20241022" // Fast and cheap model for summarization
        private const val CHARS_PER_TOKEN = 4 // Approximate characters per token

        private const val SUMMARIZATION_PROMPT = """You are a conversation summarization assistant. Your task is to create a concise but informative summary of the conversation history provided.

                    The summary should:
                    1. Capture the main topics discussed
                    2. Preserve important context and decisions made
                    3. Note any key facts, preferences, or constraints mentioned
                    4. Be written in third person
                    5. Be concise but comprehensive (2-4 paragraphs maximum)
                    
                    Please summarize the following conversation:"""

        /**
         * Estimates token count for a message
         * Uses actual token counts if available, otherwise estimates from content length
         */
        private fun estimateTokenCount(message: Message): Int {
            // If we have actual token counts from API, use them
            val actualTokens = (message.inputTokens ?: 0) + (message.outputTokens ?: 0)
            if (actualTokens > 0) {
                return actualTokens
            }

            // Otherwise estimate: ~4 characters per token
            return message.content.length / CHARS_PER_TOKEN
        }
    }

    /**
     * Checks if compression is needed based on token count since last summary
     */
    fun shouldCompress(messages: List<Message>): Boolean {
        // Find the last summary
        val lastSummaryIndex = messages.indexOfLast { it.isSummary }

        // Get messages after the last summary
        val messagesAfterLastSummary = if (lastSummaryIndex >= 0) {
            messages.drop(lastSummaryIndex + 1)
        } else {
            messages
        }

        // Count tokens in user and assistant messages (not summaries)
        val conversationMessages = messagesAfterLastSummary.filter {
            !it.isSummary && it.role in listOf(MessageRole.USER, MessageRole.ASSISTANT)
        }

        val totalTokens = conversationMessages.sumOf { estimateTokenCount(it) }

        return totalTokens >= COMPRESSION_TOKEN_THRESHOLD
    }

    /**
     * Compresses message history by creating a summary and deleting original messages
     * Returns a new list with summary replacing the summarized messages
     */
    suspend fun compressMessages(
        messages: List<Message>,
        apiKey: String
    ): Result<List<Message>> {
        try {
            if (!shouldCompress(messages)) {
                return Result.success(messages)
            }

            Napier.d("Starting message compression for ${messages.size} messages")

            // Find the last summary
            val lastSummaryIndex = messages.indexOfLast { it.isSummary }

            // Get the starting point for finding messages to summarize
            val startIndex = if (lastSummaryIndex >= 0) lastSummaryIndex + 1 else 0

            // Collect messages until we reach the token threshold
            val messagesToSummarize = mutableListOf<Message>()
            var accumulatedTokens = 0

            for (i in startIndex until messages.size) {
                val msg = messages[i]
                if (!msg.isSummary && msg.role in listOf(MessageRole.USER, MessageRole.ASSISTANT)) {
                    val msgTokens = estimateTokenCount(msg)
                    messagesToSummarize.add(msg)
                    accumulatedTokens += msgTokens

                    // Stop after reaching threshold
                    if (accumulatedTokens >= COMPRESSION_TOKEN_THRESHOLD) {
                        break
                    }
                }
            }

            if (messagesToSummarize.isEmpty()) {
                Napier.d("No messages to compress")
                return Result.success(messages)
            }

            Napier.d("Compressing ${messagesToSummarize.size} messages (~$accumulatedTokens tokens)")

            // Create summary
            val summary = createSummary(messagesToSummarize, apiKey)

            // Calculate token savings
            val summaryTokens = summary.length / CHARS_PER_TOKEN
            val tokensSaved = accumulatedTokens - summaryTokens

            // Create summary message
            val summaryMessage = Message(
                id = Uuid.random().toString(),
                content = summary,
                role = MessageRole.SYSTEM,
                timestamp = Clock.System.now(),
                isSummary = true,
                summarizedMessageCount = messagesToSummarize.size,
                summarizedTokens = accumulatedTokens,
                tokensSaved = tokensSaved
            )

            // Build new message list:
            // 1. Keep messages before startIndex (includes previous summaries)
            // 2. Add new summary
            // 3. Keep remaining messages (excluding the ones we summarized)
            val messagesBeforeCompression = messages.take(startIndex)

            // Create a set of IDs of messages that were summarized
            val summarizedIds = messagesToSummarize.map { it.id }.toSet()

            // Keep all messages after startIndex that were NOT summarized
            val remainingMessages = messages.drop(startIndex).filter { msg ->
                msg.id !in summarizedIds
            }

            val compressedMessages = messagesBeforeCompression + listOf(summaryMessage) + remainingMessages

            Napier.d("Token savings: ~$tokensSaved tokens (${messagesToSummarize.size} messages → 1 summary)")

            Napier.d("Compression complete: Replaced ${messagesToSummarize.size} messages with summary")
            Napier.d("Total messages: ${messages.size} -> ${compressedMessages.size} (saved ${messages.size - compressedMessages.size - 1} messages)")

            return Result.success(compressedMessages)

        } catch (e: Exception) {
            Napier.e("Error compressing messages", e)
            return Result.failure(e)
        }
    }

    /**
     * Creates a summary of messages using Claude API
     */
    private suspend fun createSummary(messages: List<Message>, apiKey: String): String {
        // Format conversation for summarization
        val conversationText = messages.joinToString("\n\n") { message ->
            val role = when (message.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.SYSTEM -> "System"
            }
            "$role: ${message.content}"
        }

        // Prepare API request
        val claudeMessages = listOf(
            ClaudeMessage(
                role = "user",
                content = "$SUMMARIZATION_PROMPT\n\n$conversationText"
            )
        )

        val request = ClaudeMessageRequest(
            model = SUMMARIZATION_MODEL,
            messages = claudeMessages,
            maxTokens = MAX_TOKENS,
            stream = false,
            system = "You are a helpful conversation summarization assistant.",
            temperature = 0.3 // Lower temperature for more focused summaries
        )

        // Call API
        val result = apiClient.sendMessageNonStreaming(request, apiKey)
        val response = result.getOrThrow()

        val summary = response.content.firstOrNull()?.text
            ?: throw IllegalStateException("No summary generated")

        Napier.d("Generated summary: ${summary.take(100)}...")

        return "📝 Previous conversation summary:\n\n$summary"
    }
}