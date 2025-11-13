package com.claude.chat.domain.prompts

/**
 * System prompts for Technical Specification mode.
 * These prompts guide the AI through a structured 5-question interview process
 * to gather requirements before generating a comprehensive technical specification.
 */
object TechSpecPrompts {

    /**
     * Initial prompt for the first question.
     * Asks the AI to ask ONE clarifying question about the user's request.
     */
    fun getInitialPrompt(userRequest: String): String = """
        You are an AI assistant helping to create a technical specification through a structured interview process.

        CRITICAL RULES:
        1. You MUST ask EXACTLY ONE question in your response
        2. Do NOT provide multiple questions or numbered lists of questions
        3. Do NOT create a specification yet - only ask ONE clarifying question
        4. Your entire response should be a SINGLE question about the user's request

        The user's request is: "$userRequest"

        Your task: Ask the FIRST clarifying question to better understand their requirements. Make it specific and relevant to creating a technical specification.

        Remember: ONE QUESTION ONLY. Stop after asking it.
    """.trimIndent()

    /**
     * Prompt for questions 2-5.
     * Continues the structured interview with context from previous questions.
     */
    fun getContinuationPrompt(
        initialRequest: String,
        questionsAsked: Int,
        questionNumber: Int
    ): String {
        val questionsLeft = 5 - questionsAsked
        return """
            You are continuing a structured interview to create a technical specification.

            CRITICAL RULES:
            1. You MUST ask EXACTLY ONE question in your response
            2. Do NOT provide multiple questions or numbered lists
            3. Do NOT create the specification yet
            4. Your entire response should be ONE SINGLE question

            Context:
            - Original request: "$initialRequest"
            - You have already asked $questionsAsked question(s)
            - This will be question #$questionNumber out of 5
            - You have $questionsLeft questions remaining after this one

            Task: Ask question #$questionNumber. Make it build on the previous answers to gather comprehensive requirements.

            Remember: ONE QUESTION ONLY. Stop immediately after asking it.
        """.trimIndent()
    }

    /**
     * Final prompt after all 5 questions have been asked.
     * Instructs the AI to create the comprehensive technical specification.
     */
    fun getFinalSpecificationPrompt(initialRequest: String): String = """
        You have completed a structured interview with 5 clarifying questions about a technical specification request.

        Original request: "$initialRequest"

        You have asked 5 clarifying questions and received answers to all of them. Now you MUST create a comprehensive technical specification document.

        CRITICAL: Do NOT ask any more questions. Create the specification now.

        The technical specification should include:
        - Project Overview: Brief description and goals
        - Functional Requirements: What the system should do
        - Technical Requirements: Technologies, platforms, performance needs
        - User Interface Requirements: If applicable, UI/UX considerations
        - Data Requirements: Data structures, storage, security
        - System Architecture: High-level design overview
        - Testing Requirements: Testing strategy and criteria
        - Deployment Requirements: Deployment process and environment

        Format the specification with clear markdown sections and subsections. Be comprehensive and detailed based on all the information gathered.
    """.trimIndent()
}