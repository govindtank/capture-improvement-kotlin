package com.capturekit.core.model

/**
 * Represents the overall quality level of a capture frame.
 */
enum class QualityLevel(val displayName: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    ACCEPTABLE("Acceptable"),
    POOR("Poor"),
    REJECTED("Rejected");

    /**
     * Returns true if the quality is ACCEPTABLE or better.
     */
    val isAcceptable: Boolean get() = this != POOR && this != REJECTED

    /**
     * Returns true if the quality is GOOD or EXCELLENT.
     */
    val isGood: Boolean get() = this == EXCELLENT || this == GOOD
}
