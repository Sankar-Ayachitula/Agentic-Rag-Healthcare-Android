package ai.studio1119.medassist.data

// Mirrors the backend contract:
//   POST /chat   request  { "message": "..." }
//                response { answer, intent, disease?, symptoms?, sources? }

data class ChatRequest(
    val message: String
)

data class ChatResponse(
    val answer: String,
    val intent: String?,
    val disease: String?,
    val symptoms: List<String>?,
    val sources: List<Source>?
)

// The backend returns rich metadata per source; we only need the page number.
// Moshi ignores the other keys automatically.
data class Source(
    val page: Int?
)
