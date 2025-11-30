package com.claude.chat.domain.manager

import com.claude.chat.domain.prompts.TechSpecPrompts
import io.github.aakira.napier.Napier

/**
 * Manager for Tech Spec Mode state machine
 * Manages the multi-step conversation flow for gathering technical specifications
 */
class TechSpecManager {
    /**
     * State of the Tech Spec conversation
     */
    data class TechSpecState(
        val initialRequest: String? = null,
        val questionsAsked: Int = 0
    ) {
        val isActive: Boolean get() = initialRequest != null && questionsAsked > 0
        val isComplete: Boolean get() = questionsAsked >= MAX_QUESTIONS

        companion object {
            const val MAX_QUESTIONS = 5
        }
    }

    /**
     * Build system prompt based on current Tech Spec state
     *
     * @param userText The user's current message
     * @param state Current Tech Spec state
     * @return System prompt for this conversation turn
     */
    fun buildSystemPrompt(userText: String, state: TechSpecState): String {
        return when {
            // First message: initiate questions
            state.initialRequest == null -> {
                Napier.d("Tech Spec: Building initial prompt")
                TechSpecPrompts.getInitialPrompt(userText)
            }
            // Questions 2-5: continue asking
            state.questionsAsked < TechSpecState.MAX_QUESTIONS -> {
                val questionNumber = state.questionsAsked + 1
                Napier.d("Tech Spec: Building continuation prompt for question $questionNumber")
                TechSpecPrompts.getContinuationPrompt(
                    initialRequest = state.initialRequest,
                    questionsAsked = state.questionsAsked,
                    questionNumber = questionNumber
                )
            }
            // All questions collected: create specification
            else -> {
                Napier.d("Tech Spec: Building final specification prompt")
                TechSpecPrompts.getFinalSpecificationPrompt(
                    initialRequest = state.initialRequest
                )
            }
        }
    }

    /**
     * Update Tech Spec state after processing user message
     *
     * @param userText The user's message text
     * @param currentState Current Tech Spec state
     * @return Updated Tech Spec state
     */
    fun updateState(userText: String, currentState: TechSpecState): TechSpecState {
        return when {
            // First message: save initial request and increment counter
            currentState.initialRequest == null -> {
                Napier.d("Tech Spec: Saved initial request and asked first question")
                TechSpecState(
                    initialRequest = userText,
                    questionsAsked = 1
                )
            }
            // Questions 2-5: increment counter
            currentState.questionsAsked < TechSpecState.MAX_QUESTIONS -> {
                val newCount = currentState.questionsAsked + 1
                Napier.d("Tech Spec: Asked question $newCount of ${TechSpecState.MAX_QUESTIONS}")
                currentState.copy(questionsAsked = newCount)
            }
            // All questions collected: reset state for next session
            else -> {
                Napier.d("Tech Spec: Created final specification, resetting state")
                TechSpecState()
            }
        }
    }

    /**
     * Reset Tech Spec state (used when clearing history)
     */
    fun resetState(): TechSpecState {
        Napier.d("Tech Spec: Resetting state")
        return TechSpecState()
    }

    /**
     * Check if we should use Tech Spec prompts for this turn
     */
    fun shouldUseTechSpecPrompt(state: TechSpecState, isTechSpecModeEnabled: Boolean): Boolean {
        return isTechSpecModeEnabled
    }
}