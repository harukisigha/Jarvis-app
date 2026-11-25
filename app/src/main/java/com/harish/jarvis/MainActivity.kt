package com.harish.jarvis

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private val history = JSONArray()
    private lateinit var chat: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 90, 24, 24)
        }
        val keyBox = EditText(this).apply {
            hint = "API key (sirf ek baar)"
            setText(prefs.getString("key", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        chat = TextView(this).apply { textSize = 16f }
        scroll = ScrollView(this).apply { addView(chat) }
        val input = EditText(this).apply { hint = "Jarvis se kuch bolo..." }
        val send = Button(this).apply { text = "Send" }

        root.addView(keyBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(input)
        root.addView(send)
        setContentView(root)

        send.setOnClickListener {
            val text = input.text.toString().trim()
            val key = keyBox.text.toString().trim()
            if (text.isEmpty() || key.isEmpty()) return@setOnClickListener
            prefs.edit().putString("key", key).apply()
            input.setText("")
            append("Tum: $text")
            history.put(JSONObject().put("role", "user").put("content", text))
            Thread {
                try {
                    val reply = callClaude(key)
                    history.put(JSONObject().put("role", "assistant").put("content", reply))
                    runOnUiThread { append("Jarvis: $reply") }
                } catch (e: Exception) {
                    runOnUiThread { append("Error: ${e.message}") }
                }
            }.start()
        }
    }

    private fun append(s: String) {
        chat.append(s + "\n\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun callClaude(key: String): String {
        val conn = URL("https://api.anthropic.com/v1/messages")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("x-api-key", key)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.setRequestProperty("content-type", "application/json")
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.doOutput = true

        val body = JSONObject()
            .put("model", "claude-sonnet-4-6")
            .put("max_tokens", 1000)
            .put("system", "You are Jarvis, a personal assistant. Reply in casual Hinglish, short and helpful.")
            .put("messages", history)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream.bufferedReader().readText()
        if (code !in 200..299) throw Exception("$code: $resp")
        return JSONObject(resp).getJSONArray("content").getJSONObject(0).getString("text")
    }
}
