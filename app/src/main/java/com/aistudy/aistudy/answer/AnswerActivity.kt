package com.aistudy.aistudy.answer

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import org.json.JSONArray

class AnswerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QA_ID = "extra_qa_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_answer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnBack: ImageButton = findViewById(R.id.btn_back)
        val tvSubject: TextView = findViewById(R.id.tv_subject)
        val tvQuestion: TextView = findViewById(R.id.tv_question)
        val tvFinalAnswer: TextView = findViewById(R.id.tv_final_answer)
        val layoutSteps: LinearLayout = findViewById(R.id.layout_steps)

        btnBack.setOnClickListener { finish() }

        val qaId = intent.getLongExtra(EXTRA_QA_ID, -1L)
        if (qaId == -1L) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val qaDao = AppDatabase.getInstance(this).questionAnswerDao()
        val item = qaDao.getById(qaId)
        if (item == null) {
            Toast.makeText(this, R.string.answer_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvSubject.text = item.subject?.replaceFirstChar { it.uppercase() } ?: "General"
        tvQuestion.text = item.question
        tvFinalAnswer.text = item.finalAnswer

        renderSteps(layoutSteps, item.steps)
    }

    private fun renderSteps(container: LinearLayout, stepsJsonString: String?) {
        container.removeAllViews()
        if (stepsJsonString.isNullOrBlank()) return

        try {
            val stepsArray = JSONArray(stepsJsonString)
            val dp14 = (14 * resources.displayMetrics.density).toInt()
            val dp10 = (10 * resources.displayMetrics.density).toInt()
            val dp8 = (8 * resources.displayMetrics.density).toInt()
            val dp6 = (6 * resources.displayMetrics.density).toInt()

            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                val title = stepObj.optString("title", "")
                val body = stepObj.optString("body", "")
                val expr = stepObj.optString("expr", "")

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_auth_shell_panel)
                    setPadding(dp14, dp14, dp14, dp14)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp10
                    }
                    layoutParams = params
                }

                val headerText = if (title.isNotBlank()) "Step ${i + 1}: $title" else "Step ${i + 1}"
                val tvTitle = TextView(this).apply {
                    text = headerText
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.auth_brand))
                }
                card.addView(tvTitle)

                if (body.isNotBlank()) {
                    val tvBody = TextView(this).apply {
                        text = body
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(context, R.color.auth_foreground))
                        setPadding(0, dp6, 0, 0)
                    }
                    card.addView(tvBody)
                }

                if (expr.isNotBlank()) {
                    val tvExpr = TextView(this).apply {
                        text = expr
                        textSize = 13f
                        typeface = Typeface.MONOSPACE
                        setBackgroundResource(R.drawable.bg_auth_input)
                        setPadding(dp8, dp6, dp8, dp6)
                        setTextColor(ContextCompat.getColor(context, R.color.auth_foreground))
                        val exprParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp8
                        }
                        layoutParams = exprParams
                    }
                    card.addView(tvExpr)
                }

                container.addView(card)
            }
        } catch (e: Exception) {
            val tvRaw = TextView(this).apply {
                text = stepsJsonString
                setTextColor(ContextCompat.getColor(context, R.color.auth_foreground))
            }
            container.addView(tvRaw)
        }
    }
}
