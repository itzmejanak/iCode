package dev.icode.mobile.domain

/**
 * An open file's changes against its version in Git's index, as a checkout writes it: what the
 * gutter marks and the change sheet shows (docs/editor-and-language-services.md).
 */
data class DocumentLineChanges(
    /** The index version, decoded as the file is. */
    val base: String,
    /** [LineDiff.hunks] of the file's text against [base]. */
    val changes: List<GitDiffHunk>,
)

/** Stages only [change] of the open file at [location], as VS Code's Stage Change does. */
data class StageLineChange(val location: String, val change: GitDiffHunk) : ICodeIntent
