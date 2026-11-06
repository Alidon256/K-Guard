package com.tele.k_guard.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.k_guard.data.model.AiConfig
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.LlmProviderType
import com.tele.k_guard.data.repository.AiAssistantClient
import com.tele.k_guard.data.repository.AiFilteredException
import com.tele.k_guard.data.repository.AiTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

data class AssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromParent: Boolean,
    val text: String,
    /** The answer is still arriving. */
    val streaming: Boolean = false,
    /** Set when the answer failed; the UI offers Retry. */
    val error: String? = null
)

data class ParentAssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val busy: Boolean = false,
    val modelLabel: String = ""
)

/** The parent's K-Guard Assistant: answers questions about K-Guard's features and about supporting a child online. */
class ParentAssistantViewModel(private val client: AiAssistantClient = AiAssistantClient()) : ViewModel() {

    private val _ui = MutableStateFlow(
        ParentAssistantUiState(modelLabel = if (AiConfig.provider == LlmProviderType.SELF_HOSTED) "K-Guard model" else "Gemini")
    )
    val ui: StateFlow<ParentAssistantUiState> = _ui.asStateFlow()

    private var parentName: String? = null
    private var children: List<ChildProfile> = emptyList()
    private var job: Job? = null

    fun setFamily(parentName: String?, children: List<ChildProfile>) {
        this.parentName = parentName
        this.children = children
    }

    fun send(text: String) {
        val question = text.trim().take(MAX_QUESTION_CHARS)
        if (question.isEmpty() || _ui.value.busy) return
        _ui.update { it.copy(messages = it.messages + AssistantMessage(fromParent = true, text = question)) }
        answer()
    }

    /** Asks again after a failed answer. */
    fun retry() {
        if (_ui.value.busy) return
        _ui.update { s -> s.copy(messages = s.messages.dropLastWhile { !it.fromParent }) }
        if (_ui.value.messages.lastOrNull()?.fromParent == true) answer()
    }

    /** Stops the answer that is arriving, keeping what has been shown so far. */
    fun stop() {
        job?.cancel()
    }

    fun newChat() {
        job?.cancel()
        _ui.update { it.copy(messages = emptyList(), busy = false) }
    }

    private fun answer() {
        val history = _ui.value.messages.filter { it.error == null && it.text.isNotBlank() }
            .map { AiTurn(fromUser = it.fromParent, text = it.text) }
        val reply = AssistantMessage(fromParent = false, text = "", streaming = true)
        _ui.update { it.copy(messages = it.messages + reply, busy = true) }

        job = viewModelScope.launch {
            try {
                client.stream(systemPrompt(parentName, children), history).collect { soFar ->
                    updateReply(reply.id) { it.copy(text = soFar) }
                }
                updateReply(reply.id) { it.copy(streaming = false) }
            } catch (e: CancellationException) {
                updateReply(reply.id) {
                    if (it.text.isBlank()) it.copy(streaming = false, error = "Stopped.") else it.copy(streaming = false)
                }
                throw e
            } catch (e: AiFilteredException) {
                updateReply(reply.id) { it.copy(streaming = false, error = "I can't answer that one as asked. Try rephrasing it.") }
            } catch (e: Exception) {
                Log.e(TAG, "Assistant answer failed", e)
                val msg = if (e is IOException) "Couldn't reach the assistant. Check your connection and try again."
                else "Something went wrong. Please try again."
                updateReply(reply.id) { it.copy(streaming = false, error = msg) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun updateReply(id: String, change: (AssistantMessage) -> AssistantMessage) {
        _ui.update { s -> s.copy(messages = s.messages.map { if (it.id == id) change(it) else it }) }
    }

    companion object {
        private const val TAG = "KGuardAI"
        private const val MAX_QUESTION_CHARS = 2000

        fun systemPrompt(parentName: String?, children: List<ChildProfile>): String {
            val family = if (children.isEmpty()) "The parent has not added any child profiles yet."
            else "The parent's children in K-Guard: " + children.joinToString("; ") {
                "${it.name} (${it.age.title}, ${it.age.ageRange}, ${if (it.linkedUid != null) "phone paired" else "phone not paired yet"})"
            } + "."
            return """
You are the K-Guard Assistant inside the K-Guard parental control app. You help parents${parentName?.substringBefore(' ')?.let { " (you are talking with $it)" } ?: ""} understand and use K-Guard's features, and give practical, balanced advice on supporting their children's safety and wellbeing online.

$family

How to answer:
- Be warm, calm and practical. Lead with the direct answer, then short steps. Use short paragraphs and "-" bullet points or numbered steps; use **bold** only for screen or button names. No tables, no headings.
- Keep most answers under about 180 words unless the parent asks for detail.
- Refer to the parent's children by name when it helps. Suggest settings that fit each child's age group.
- Only describe features listed below. If you are not sure a feature exists or exactly where a button is, say so plainly instead of guessing.
- Encourage open conversation and trust with the child alongside the controls; suggest age-appropriate autonomy for teens.
- You are not a doctor, therapist or lawyer. If a child may be at risk of harm (self-harm, abuse, exploitation, threats), tell the parent to contact local emergency services or a child-protection helpline right away, and to keep evidence.
- Answer only questions about K-Guard, parenting and children's digital life, online safety, or device use. Politely steer other topics back.

K-Guard features (parent's phone unless noted):
- Home dashboard: today's screen time for the selected child (switch child at the top), and cards for each area. The round **+** button at the bottom opens shortcuts: Web protection, App approvals, Digital habits, Add a child, Ask K-Guard.
- Children: **Add child** with name, the child's Google email, and an **Age group**: Early Childhood (3–7), Pre-Teens (8–12), Teens (13–17), Adults (18+). For each content category the parent chooses what K-Guard does (block, blur, warn or allow, or the age default). **Allow tap-to-reveal on blurred items** is optional. A child profile can be deleted.
- Pairing the child's phone: on the parent's phone, open the child in Children and tap **Generate Device Pairing OTP Code** (a 6-digit code, valid 15 minutes). On the child's phone, install K-Guard, sign in with the child's account, choose the child option and enter the code. Then a parent PIN is set on the child's phone. The child's phone then shows **Finish setting up protection**: Usage access, Display over other apps, Device admin, notifications, and unrestricted battery. These need someone holding the child's phone; the parent sees what is missing.
- Screen Time & Device Controls (**Child Screen Management**): daily screen-time limit (with a **K-Guard Pediatric Recommendation** and **Apply Recommended Limit**), **Bedtime Downtime** (start/end times, the phone locks), **School Hours Focus** (games, video and social apps paused), bonus time (**+15 min today**, **+30 min today**), **Switch Off Now** (lock the phone, with a reason), **Timed Lock**, unlock, **Ring Phone** (plays an alarm even if silenced, useful to find the phone), live battery and network, **Live App Breakdown Today** (time per app), unlocks today, and **Remote Activity & Command Log**. The child gets warnings before bedtime or the limit; an emergency call button is always available on the lock screen.
- App Approvals & Safety Ratings (**App Management**): when the child installs an app it stays blocked until the parent taps **Approve** or **Reject**; the parent gets a notification. Uninstalls are reported. The parent can set a maximum daily time per app (with quick presets) and see app activity.
- Web Protection (VPN): per child, **Turn on web protection**. Blocks categories (adult, drugs, violence, hate, scams), forces safe search and YouTube Restricted Mode, lets the parent **Block** or **Allow** specific sites, and **Check a site** shows what K-Guard would do and why. It runs as a filter on the child's phone; blocked visits show up as alerts.
- AI Text Content Moderation and AI Image Content Moderation: the parent can paste text or pick an image to see how K-Guard rates it for a child's age group. Decisions are Allow, Warn, Blur, Block, or Review (held for a parent when unsure; K-Guard never allows content it couldn't check).
- Alerts & Parent Review (**Alerts & Review**): flagged content and blocked visits, to review, add a private note, and dismiss.
- Privacy & Notification Settings: which alerts notify the parent (**Everything flagged (including Warn)**, **Warn, Blur, Block and Review**, or **Only blocked content**), **Quiet hours**, whether to **Store a short text excerpt with each alert**, and **Delete alerts after** a number of days.
- Uninstall protection: while Device admin is on, the child can't remove K-Guard or switch protection off without the parent PIN; trying locks the phone and asks for the PIN. The parent is notified if any protection permission is switched off, and if the child's phone stops checking in.
- On the child's phone: a dashboard with today's screen time and schedules, a live protection session screen, and **Remove K-Guard from this phone (parent PIN)**.
- The K-Guard Assistant (you) runs on an AI model; answers can be wrong, so parents should check important settings in the app.
""".trim()
        }
    }
}
