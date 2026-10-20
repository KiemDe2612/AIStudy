package com.aistudy.aistudy.ask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AskFragment : Fragment() {

    companion object {
        private const val TAG = "AskFragment"
        private const val XP_PER_QUESTION = 50

        fun extractJson(raw: String): String {
            var trimmed = raw.trim()
            if (trimmed.startsWith("```json")) {
                trimmed = trimmed.removePrefix("```json").trim()
            } else if (trimmed.startsWith("```")) {
                trimmed = trimmed.removePrefix("```").trim()
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.removeSuffix("```").trim()
            }
            return trimmed
        }
    }

    private lateinit var editQuestion: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var progressLoading: ProgressBar
    private var executor: ExecutorService? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ask, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        executor = Executors.newSingleThreadExecutor()

        editQuestion = view.findViewById(R.id.edit_question)
        btnSend = view.findViewById(R.id.btn_send)
        progressLoading = view.findViewById(R.id.progress_loading)

        val btnSug1: MaterialButton = view.findViewById(R.id.btn_suggestion_1)
        val btnSug2: MaterialButton = view.findViewById(R.id.btn_suggestion_2)
        val btnSug3: MaterialButton = view.findViewById(R.id.btn_suggestion_3)
        val btnSug4: MaterialButton = view.findViewById(R.id.btn_suggestion_4)

        btnSug1.setOnClickListener { editQuestion.setText(btnSug1.text) }
        btnSug2.setOnClickListener { editQuestion.setText(btnSug2.text) }
        btnSug3.setOnClickListener { editQuestion.setText(btnSug3.text) }
        btnSug4.setOnClickListener { editQuestion.setText(btnSug4.text) }

        btnSend.setOnClickListener { submitQuestion() }
    }

    private fun setLoading(loading: Boolean) {
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !loading
        editQuestion.isEnabled = !loading
        btnSend.text = if (loading) getString(R.string.ask_sending) else getString(R.string.ask_send)
    }

    private fun submitQuestion() {
        val question = editQuestion.text.toString().trim()
        if (question.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ask_error_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_REAL_GEMINI_API_KEY") {
            Toast.makeText(requireContext(), R.string.ask_error_api_key, Toast.LENGTH_LONG).show()
            Log.w(TAG, "submit blocked: api key missing or placeholder")
            return
        }

        setLoading(true)
        val activity = requireActivity()
        val prefs = activity.getSharedPreferences(LoginActivity.PREFS_AUTH, Context.MODE_PRIVATE)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1L)

        val db = AppDatabase.getInstance(activity)
        val profile = if (userId != -1L) db.profileDao().getByUserId(userId) else null
        val level = profile?.level ?: "high"
        val style = profile?.style ?: "stepwise"

        val prompt = """
            You are an AI study assistant.
            Academic level: $level
            Explanation style: $style
            Question: "$question"

            Explain clearly and accurately. Return ONLY a raw JSON object (no markdown, or inside ```json ```) with this structure:
            {
              "subject": "math|science|code|history|language|other",
              "steps": [
                {
                  "title": "Step title",
                  "body": "Step explanation",
                  "expr": "Formula or code (optional)"
                }
              ],
              "finalAnswer": "Summary of answer"
            }
        """.trimIndent()

        executor?.execute {
            try {
                Log.d(TAG, "submit start with prompt length=${prompt.length}")
                val rawResponse = GeminiClient.ask(apiKey, prompt)
                val jsonString = extractJson(rawResponse)
                val jsonObject = JSONObject(jsonString)

                val subject = jsonObject.optString("subject", "other")
                val stepsArray = jsonObject.optJSONArray("steps")
                val stepsStr = stepsArray?.toString() ?: "[]"
                val finalAnswer = jsonObject.optString("finalAnswer", "")

                val qa = QuestionAnswer().apply {
                    this.userId = userId
                    this.subject = subject
                    this.question = question
                    this.steps = stepsStr
                    this.finalAnswer = finalAnswer
                    this.createdAt = System.currentTimeMillis()
                }

                val qaId = db.questionAnswerDao().insert(qa)
                Log.d(TAG, "saved qaId=$qaId")

                if (profile != null) {
                    profile.xp += XP_PER_QUESTION
                    db.profileDao().update(profile)
                    Log.d(TAG, "profile updated xp=${profile.xp}")
                }

                activity.runOnUiThread {
                    if (isAdded) {
                        setLoading(false)
                        editQuestion.setText("")
                        val intent = Intent(activity, AnswerActivity::class.java).apply {
                            putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
                        }
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "submit failed", e)
                activity.runOnUiThread {
                    if (isAdded) {
                        setLoading(false)
                        Toast.makeText(
                            activity,
                            "${getString(R.string.ask_error_failed)}: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor?.shutdown()
        executor = null
    }
}
