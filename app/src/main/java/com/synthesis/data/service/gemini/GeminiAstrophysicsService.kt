package com.synthesis.data.service.gemini

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.synthesis.BuildConfig
import com.synthesis.domain.model.Anomaly
import com.synthesis.domain.model.Hypothesis
import com.synthesis.domain.model.HypothesisStatus
import com.synthesis.domain.model.Observation
import com.synthesis.domain.model.xilon.XilonDetectionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "gemini_prefs")

@Singleton
class GeminiAstrophysicsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MODEL_GEMINI_3_8_FLASH = "gemini-3.8-flash"
        const val MODEL_GEMINI_3_5_FLASH = "gemini-3.5-flash"
        const val MODEL_GEMINI_FLASH_LATEST = "gemini-flash-latest"
        const val MODEL_GEMINI_3_1_PRO = "gemini-3.1-pro-preview"

        val AVAILABLE_MODELS = listOf(
            MODEL_GEMINI_3_8_FLASH,
            MODEL_GEMINI_3_5_FLASH,
            MODEL_GEMINI_FLASH_LATEST,
            MODEL_GEMINI_3_1_PRO
        )
    }

    private val gson = Gson()
    private val geminiKeyPref = stringPreferencesKey("gemini_user_api_key")
    private val geminiModelPref = stringPreferencesKey("gemini_selected_model")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun getActiveModel(): String {
        val savedModel = context.dataStore.data.map { it[geminiModelPref] ?: "" }.first().trim()
        return if (savedModel.isNotBlank()) savedModel else MODEL_GEMINI_3_8_FLASH
    }

    fun getActiveModelFlow(): kotlinx.coroutines.flow.Flow<String> {
        return context.dataStore.data.map { it[geminiModelPref] ?: MODEL_GEMINI_3_8_FLASH }
    }

    suspend fun setActiveModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[geminiModelPref] = model.trim()
        }
    }

    suspend fun getEffectiveApiKey(): String {
        val userSavedKey = context.dataStore.data.map { it[geminiKeyPref] ?: "" }.first().trim()
        if (userSavedKey.isNotBlank()) return userSavedKey

        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun saveUserApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[geminiKeyPref] = key.trim()
        }
    }

    suspend fun isAiKeyConfigured(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    /**
     * Executes real Gemini AI Astrobiology & Vetting analysis for a detected candidate.
     */
    suspend fun analyzeCandidateWithGemini(result: XilonDetectionResult): GeminiAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()

        if (apiKey.isBlank()) {
            return@withContext generateLocalHeuristicFallback(result)
        }

        val prompt = buildCandidatePrompt(result)
        val systemInstructionText = """
            Ești XILON-AI, un astrofizician cercetător de elită specializat în exoplanetologie, fotometrie stelară (Kepler/TESS/PLATO) și habitabilitate planetară.
            Analizează datele empirice fotometrice primite și oferă un raport științific de nivel academic în limba română (cu termeni astrofizici recunoscuți internațional).
            Structurează răspunsul clar în 4 secțiuni:
            1. NATURA ASTROFIZICĂ & VALIDAREA SEMNALULUI (Planetă reală vs binară eclipsantă/fals pozitiv)
            2. COMPOZIȚIE INTERNĂ & TIP PLANETAR (Densitate estimată, atmosferă primară/secundară)
            3. DETECTABILITATE SPECTROSCOPICĂ JWST (Benzi potențiale H2O, CH4, CO2)
            4. VERDICT ȘTIINȚIFIC FINAL & RECOMANDĂRI DE URMARE (Observații de viteză radială, spectroscopie)
        """.trimIndent()

        val model = getActiveModel()
        try {
            val responseText = callGeminiApi(apiKey, systemInstructionText, prompt)
            GeminiAnalysisResult(
                isLiveAiGenerated = true,
                modelName = model,
                analysisDossier = "Interpretare AI neverificată, nu validare observațională.\n$responseText",
                confidenceScorePercent = 0
            )
        } catch (e: Exception) {
            generateLocalHeuristicFallback(result, errorNotice = "Eroare conexiune Gemini API (${e.localizedMessage}). S-a utilizat sinteza euristică locală.")
        }
    }

    /**
     * Multi-turn chat with the Gemini AI Astrophysicist regarding the target star and candidate.
     */
    suspend fun askAstrophysicist(
        contextSummary: String,
        userQuestion: String,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext "⚠️ Cheia Gemini API nu este configurată încă. Te rugăm să adaugi cheia ta Gemini (gratuită din Google AI Studio) în meniul de setări AI de mai sus pentru a discuta în timp real cu asistentul astrofizic neuronal."
        }

        val systemPrompt = """
            Ești XILON AI, asistentul astrofizician inteligent din suita SYNTHESIS.
            Răspunde întrebărilor utilizatorului cu precizie științifică, rigurozitate astrofizică și claritate pedagogică.
            Folosește ecuații sau legi fizice dacă este relevant (Kepler, Stefan-Boltzmann, Roche, Schwarzschild).
            Răspunde în limba română.
            Contextul astronomic curent este:
            $contextSummary
        """.trimIndent()

        val fullPrompt = buildString {
            if (chatHistory.isNotEmpty()) {
                appendLine("Istoric discuție:")
                chatHistory.forEach { (sender, text) ->
                    appendLine("$sender: $text")
                }
                appendLine()
            }
            appendLine("Întrebare utilizator: $userQuestion")
        }

        try {
            callGeminiApi(apiKey, systemPrompt, fullPrompt)
        } catch (e: Exception) {
            "Eroare comunicare cu Gemini AI: ${e.message}"
        }
    }

    /**
     * Synthesizes real scientific hypotheses for Investigation mode using Gemini.
     */
    suspend fun generateScientificHypotheses(
        query: String,
        observations: List<Observation>,
        anomalies: List<Anomaly>
    ): List<Hypothesis> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext emptyList()
        }

        val prompt = """
            Generează 3 ipoteze științifice fundamentate pentru următoarea investigație astrofizică:
            Întrebare: "$query"
            Observații disponibile (${observations.size}): ${observations.take(5).joinToString { "${it.sourceName}: ${it.entity}" }}
            Anomalii detectate (${anomalies.size}): ${anomalies.take(3).joinToString { "${it.severity} (dev: ${it.deviationPercent}%)" }}
            
            Pentru fiecare ipoteză returnează exact în formatul:
            TITLU: [Titlu scurt]
            DESCRIERE: [Descriere fizică detaliată]
            Nu atribui scoruri sau dovezi inexistente.
            ---
        """.trimIndent()

        try {
            val response = callGeminiApi(apiKey, "Ești un motor de raționament științific ipotetico-deductiv.", prompt)
            parseHypothesesFromGemini(response, observations.firstOrNull()?.investigationId ?: "")
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun callGeminiApi(apiKey: String, systemInstruction: String, userPrompt: String): String {
        val model = getActiveModel()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestBodyMap = mapOf(
            "systemInstruction" to mapOf(
                "parts" to listOf(mapOf("text" to systemInstruction))
            ),
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to userPrompt))
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.35,
                "topP" to 0.9,
                "maxOutputTokens" to 1500
            )
        )

        val jsonBody = gson.toJson(requestBodyMap)
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw RuntimeException("HTTP ${response.code}: $errBody")
            }

            val respString = response.body?.string() ?: throw RuntimeException("Empty response from Gemini")
            val root = gson.fromJson(respString, Map::class.java)
            val candidates = root["candidates"] as? List<*>
            val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
            val content = firstCandidate?.get("content") as? Map<*, *>
            val parts = content?.get("parts") as? List<*>
            val firstPart = parts?.firstOrNull() as? Map<*, *>
            val text = firstPart?.get("text") as? String

            return text?.trim()?.takeIf { it.isNotEmpty() } ?: error("Răspuns AI fără text")
        }
    }

    private fun buildCandidatePrompt(res: XilonDetectionResult): String {
        fun value(v: Number?): String = v?.takeIf { it.toDouble().isFinite() }?.toString() ?: "necunoscut"
        return """
            Candidat preliminar, nu descoperire confirmată. TIC ${res.star.ticId}
            Teff: ${value(res.star.teff)} K; raza stelei: ${value(res.star.radiusSun)} R_sun
            Magnitudine: ${value(res.star.tMag)}
            Perioada: ${value(res.orbitalPeriodDays)} zile; durata: ${value(res.transitDurationHours)} ore
            Adâncime: ${value(res.transitDepthPpm)} ppm
            Raza derivată: ${value(res.derivedPlanetRadiusEarth)} R_Earth
            Semiaxa derivată: ${value(res.derivedSemiMajorAxisAu)} AU
            Teq derivată: ${value(res.derivedEquilibriumTempK)} K
            SNR raportat: ${value(res.signalToNoiseRatio)}
            Odd/Even, eclipsa secundară și confirmarea independentă: neverificate.
            Nu completa valori lipsă și nu atribui probabilități sau confirmări.
        """.trimIndent()
    }

    private fun generateLocalHeuristicFallback(res: XilonDetectionResult, errorNotice: String? = null): GeminiAnalysisResult {
        return GeminiAnalysisResult(
            isLiveAiGenerated = false,
            modelName = "Offline — unverified",
            analysisDossier = "Analiză AI indisponibilă. ${errorNotice.orEmpty()}",
            confidenceScorePercent = 0
        )
    }

    private fun parseHypothesesFromGemini(text: String, investigationId: String): List<Hypothesis> {
        val list = mutableListOf<Hypothesis>()
        val blocks = text.split("---")
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            val titleLine = lines.firstOrNull { it.startsWith("TITLU:", ignoreCase = true) }?.removePrefix("TITLU:")?.trim()
            val descLine = lines.firstOrNull { it.startsWith("DESCRIERE:", ignoreCase = true) }?.removePrefix("DESCRIERE:")?.trim()

            if (titleLine != null && descLine != null) {
                list.add(
                    Hypothesis(
                        id = UUID.randomUUID().toString(),
                        investigationId = investigationId,
                        title = titleLine,
                        description = "Ipoteză AI neverificată; scor indisponibil. $descLine",
                        evidenceCount = 0,
                        contradictionCount = 0,
                        confidence = 0f,
                        discriminatingTests = emptyList(),
                        status = HypothesisStatus.PENDING
                    )
                )
            }
        }
        return list
    }
}

data class GeminiAnalysisResult(
    val isLiveAiGenerated: Boolean,
    val modelName: String,
    val analysisDossier: String,
    val confidenceScorePercent: Int
)
