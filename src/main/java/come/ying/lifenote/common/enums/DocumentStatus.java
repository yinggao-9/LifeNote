package come.ying.lifenote.common.enums;

/**
 * Lifecycle of a document. There is no PROCESSING state because extraction is
 * synchronous: by the time the upload request returns, the LLM call has finished.
 */
public enum DocumentStatus {
    /** Extraction finished, waiting for the user to confirm the drafts. */
    PENDING_CONFIRM,
    /** User confirmed; tasks were created from the drafts. */
    CONFIRMED,
    /** User cancelled; the drafts were physically deleted. */
    CANCELLED,
    /** LLM call or PDF parsing failed; the user may retry. */
    FAILED
}
