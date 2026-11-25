package com.bhupendra.disciplineengine.pw

// Classifies incoming Physics Wallah status-bar notifications into 4 blueprint categories
enum class PwItemType {
    LIVE_CLASS_ALERT,
    DPP_UPLOAD,
    TEST_SCHEDULED,
    BATCH_NOTICE
}

// Structured result parsed from a Physics Wallah notification
data class ParsedPwNotice(
    val itemType: PwItemType,
    val title: String,
    val body: String
)

object PwNoticeParser {

    private val LIVE_REGEX = Regex("""(live\s+now|class\s+starting|started\s+live|join\s+class)""", RegexOption.IGNORE_CASE)
    private val DPP_REGEX = Regex("""(dpp|homework|notes\s+uploaded|pdf\s+uploaded|worksheet)""", RegexOption.IGNORE_CASE)
    private val TEST_REGEX = Regex("""(test|aits|practice\s+test|mock|syllabus|exam\s+alert)""", RegexOption.IGNORE_CASE)

    // Classifies raw notification title and body text without any network calls
    fun parse(title: String?, text: String?): ParsedPwNotice {
        val cleanTitle = title?.trim().orEmpty().ifEmpty { "PW Batch Update" }
        val cleanBody = text?.trim().orEmpty()
        val combined = "$cleanTitle $cleanBody"

        val type = when {
            TEST_REGEX.containsMatchIn(combined) -> PwItemType.TEST_SCHEDULED
            DPP_REGEX.containsMatchIn(combined) -> PwItemType.DPP_UPLOAD
            LIVE_REGEX.containsMatchIn(combined) -> PwItemType.LIVE_CLASS_ALERT
            else -> PwItemType.BATCH_NOTICE
        }
        return ParsedPwNotice(itemType = type, title = cleanTitle, body = cleanBody)
    }
}
