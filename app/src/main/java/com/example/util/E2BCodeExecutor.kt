package com.example.util

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class CreateSandboxRequest(
    @Json(name = "templateID") val templateID: String = "code-interpreter-v1",
    @Json(name = "templateId") val templateId: String = "code-interpreter-v1"
)

@JsonClass(generateAdapter = true)
data class CreateSandboxResponse(
    @Json(name = "sandboxID") val sandboxID: String? = null,
    @Json(name = "sandboxId") val sandboxId: String? = null,
    @Json(name = "sandbox_id") val sandbox_id: String? = null,
    @Json(name = "instanceID") val instanceID: String? = null,
    @Json(name = "instanceId") val instanceId: String? = null,
    @Json(name = "instance_id") val instance_id: String? = null
)

@JsonClass(generateAdapter = true)
data class ExecCommandRequest(
    @Json(name = "command") val command: String,
    @Json(name = "cmd") val cmd: String = command
)

@JsonClass(generateAdapter = true)
data class ExecProcessRequest(
    @Json(name = "cmd") val cmd: String,
    @Json(name = "command") val command: String = cmd
)

@JsonClass(generateAdapter = true)
data class ExecCommandResponse(
    @Json(name = "stdout") val stdout: String? = null,
    @Json(name = "stderr") val stderr: String? = null,
    @Json(name = "exitCode") val exitCode: Int? = null,
    @Json(name = "exit_code") val exit_code: Int? = null
)

interface E2BApi {
    @POST("sandboxes")
    suspend fun createSandbox(
        @HeaderMap headers: Map<String, String>,
        @Body request: CreateSandboxRequest
    ): CreateSandboxResponse

    @POST("sandboxes/{sandboxId}/commands")
    suspend fun executeCommand(
        @HeaderMap headers: Map<String, String>,
        @Path("sandboxId") sandboxId: String,
        @Body request: ExecCommandRequest
    ): ExecCommandResponse

    @POST("sandboxes/{sandboxId}/processes")
    suspend fun executeProcess(
        @HeaderMap headers: Map<String, String>,
        @Path("sandboxId") sandboxId: String,
        @Body request: ExecProcessRequest
    ): ExecCommandResponse
}

data class ExecutionResult(
    val isSuccess: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val error: String? = null
)

object E2BCodeExecutor {
    private const val TAG = "E2BCodeExecutor"
    private const val BASE_URL = "https://api.e2b.dev/"

    private fun shellQuote(s: String): String {
        return "'" + s.replace("'", "'\\''") + "'"
    }

    private val moshi: Moshi by lazy {
        val builder = Moshi.Builder()
        try {
            builder.addLast(KotlinJsonAdapterFactory())
        } catch (t: Throwable) {
            Log.w(TAG, "Moshi KotlinJsonAdapterFactory not available, reflection fallback skipped", t)
        }
        builder.build()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: E2BApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(E2BApi::class.java)
    }

    suspend fun executeCode(
        language: String,
        code: String,
        apiKey: String,
        arguments: String = "",
        envVars: String = "",
        stdin: String = ""
    ): ExecutionResult = withContext(Dispatchers.IO) {
        // Sanitize the key to clean any accidental quotes, single quotes, or hidden whitespace/newlines from clipboard
        val trimmedKey = apiKey
            .replace("\"", "")
            .replace("'", "")
            .replace("\\s".toRegex(), "")
            .trim()

        val isPlaceholder = trimmedKey.isBlank() || 
                trimmedKey == "e2b_2b91458a51c8952f307f05ebe72cb6de30614bc4" ||
                trimmedKey.startsWith("MY_", ignoreCase = true) || 
                trimmedKey.contains("placeholder", ignoreCase = true) || 
                trimmedKey.contains("insert_your", ignoreCase = true) || 
                trimmedKey.contains("your_api", ignoreCase = true)

        if (isPlaceholder) {
            return@withContext ExecutionResult(
                isSuccess = false,
                stdout = "",
                stderr = "",
                exitCode = null,
                error = "E2B-API-Schlüssel fehlt oder es wird der Standard-Platzhalter verwendet.\n\n" +
                        "Bitte öffne das linke Menü, klicke auf das Zahnrad-Symbol (AI Provider Settings) und " +
                        "trage unter 'E2B SANDBOX CODE-AUSFÜHRUNG' einen echten, aktiven E2B-API-Schlüssel ein. " +
                        "Anschließend auf 'SPEICHERN' klicken."
            )
        }

        val safeKeyPrefix = if (trimmedKey.length > 6) trimmedKey.take(6) else trimmedKey
        val safeKeySuffix = if (trimmedKey.length > 4) trimmedKey.takeLast(4) else trimmedKey
        Log.d(TAG, "Executing with sanitized key (length: ${trimmedKey.length}, prefix: '$safeKeyPrefix', suffix: '$safeKeySuffix')")

        // Adaptive credentials strategies to overcome strict Cloudflare gateway/proxy and legacy compatibility issues
        val strategies = listOf(
            // Strategy 1: Canonical uppercase X-API-KEY only (standard developer API key, avoids Supabase JWT parser interference)
            mapOf("X-API-KEY" to trimmedKey),
            // Strategy 2: Camelcase X-API-Key only
            mapOf("X-API-Key" to trimmedKey),
            // Strategy 3: Lowercase x-api-key only
            mapOf("x-api-key" to trimmedKey),
            // Strategy 4: Standard camel-cased X-API-Key and Authorization Bearer
            mapOf("X-API-Key" to trimmedKey, "Authorization" to "Bearer $trimmedKey"),
            // Strategy 5: Uppercase X-API-KEY and Authorization Bearer
            mapOf("X-API-KEY" to trimmedKey, "Authorization" to "Bearer $trimmedKey"),
            // Strategy 6: Only modern Authorization Bearer format
            mapOf("Authorization" to "Bearer $trimmedKey")
        )

        try {
            Log.d(TAG, "Creating E2B sandbox for language: $language")
            var sandboxRes: CreateSandboxResponse? = null
            var successfulHeaders: Map<String, String>? = null
            var lastError: Throwable? = null
            val errorAccumulator = StringBuilder()

            for ((index, headers) in strategies.withIndex()) {
                try {
                    Log.d(TAG, "Trying sandbox creation strategy ${index + 1}: Keys present = ${headers.keys}")
                    sandboxRes = api.createSandbox(headers, CreateSandboxRequest())
                    successfulHeaders = headers
                    Log.d(TAG, "Sandbox creation strategy ${index + 1} succeeded!")
                    break
                } catch (e: Throwable) {
                    val code = if (e is retrofit2.HttpException) e.code() else null
                    val body = if (e is retrofit2.HttpException) {
                        e.response()?.errorBody()?.string() ?: e.message()
                    } else {
                        e.localizedMessage ?: e.javaClass.simpleName
                    }
                    val codeDesc = if (code != null) " (HTTP $code)" else ""
                    val msg = "Strategy ${index + 1}$codeDesc: $body"
                    Log.w(TAG, msg)
                    if (errorAccumulator.isNotEmpty()) errorAccumulator.append("\n")
                    errorAccumulator.append(msg)
                    lastError = e
                }
            }

            if (sandboxRes == null || successfulHeaders == null) {
                val e = lastError ?: Exception("Sandbox activation credentials validation failed.")
                val detailedError = if (e is retrofit2.HttpException) {
                    val code = e.code()
                    val body = e.response()?.errorBody()?.string() ?: ""
                    val fullText = "${e.message()} | $body"
                    if (fullText.contains("invalid auth provider token", ignoreCase = true) || code == 401) {
                        "E2B API Error (HTTP 401): Invalid Clerk/E2B API Key.\n\n" +
                        "Aktuell gesendeter Schlüssel: Länge ${trimmedKey.length} Zeichen, Anfang: '$safeKeyPrefix', Ende: '$safeKeySuffix'\n\n" +
                        "Dies bedeutet, dass dein E2B-API-Schlüssel ungültig, abgelaufen oder falsch eingetragen ist (bspw. noch ein Platzhalterwert oder unvollständig kopiert).\n\n" +
                        "Lösung: Bitte konfiguriere einen gültigen, aktiven E2B-API-Schlüssel in den AI Provider Settings oder stelle sicher, dass die Secrets im AI Studio korrekt übergeben werden.\n\n" +
                        "Diagnose-Details aller Verbindungsversuche:\n$errorAccumulator"
                    } else {
                        "E2B API Error (HTTP $code): ${body.ifBlank { e.message() }}\n\nVerbindungs-Profil:\n$errorAccumulator"
                    }
                } else {
                    "${e.localizedMessage ?: "Sandbox connection failed."}\n\nVerbindungs-Profil:\n$errorAccumulator"
                }
                return@withContext ExecutionResult(
                    isSuccess = false,
                    stdout = "",
                    stderr = "",
                    exitCode = null,
                    error = detailedError
                )
            }

            val id = sandboxRes.sandboxID 
                ?: sandboxRes.sandboxId 
                ?: sandboxRes.sandbox_id 
                ?: sandboxRes.instanceID 
                ?: sandboxRes.instanceId 
                ?: sandboxRes.instance_id
            if (id.isNullOrBlank()) {
                return@withContext ExecutionResult(
                    isSuccess = false,
                    stdout = "",
                    stderr = "",
                    exitCode = null,
                    error = "Failed to create E2B sandbox: API ID not found in response template."
                )
            }

            Log.d(TAG, "E2B Sandbox created successfully: $id")

            val fileExtension = when (language.lowercase().trim()) {
                "python", "py" -> "py"
                "javascript", "js" -> "js"
                "typescript", "ts" -> "ts"
                "bash", "sh", "shell" -> "sh"
                else -> "txt"
            }

            val filename = "/tmp/exec_$id.$fileExtension"
            val stdinFilename = "/tmp/stdin_$id.txt"

            // Perfect base64 encoding pattern to safely write contents to the isolated container without shell delimiter conflicts
            val base64Code = android.util.Base64.encodeToString(code.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            val createCodeBlock = "echo '$base64Code' | base64 -d > $filename"
            
            val createStdinBlock = if (stdin.isNotBlank()) {
                val base64Stdin = android.util.Base64.encodeToString(stdin.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                "echo '$base64Stdin' | base64 -d > $stdinFilename"
            } else {
                ""
            }

            val executorCmd = when (language.lowercase().trim()) {
                "python", "py" -> "python3"
                "javascript", "js" -> "node"
                "typescript", "ts" -> "npx -y tsx"
                "bash", "sh", "shell" -> "bash"
                else -> "cat"
            }

            // Put it all together
            val runCommandStr = buildString {
                if (envVars.isNotBlank()) {
                    append(envVars.trim()).append(" ")
                }
                append(executorCmd).append(" ").append(filename)
                if (arguments.isNotBlank()) {
                    append(" ").append(arguments.trim())
                }
                if (stdin.isNotBlank()) {
                    append(" < ").append(stdinFilename)
                }
            }

            val createAndRunCmd = buildString {
                append(createCodeBlock).append("\n")
                if (createStdinBlock.isNotBlank()) {
                    append(createStdinBlock).append("\n")
                }
                append(runCommandStr)
            }

            val wrappedCmd = "bash -c ${shellQuote(createAndRunCmd)}"

            Log.d(TAG, "Executing script in sandbox: $id")
            var execRes: ExecCommandResponse? = null
            var errorMsg: String? = null

            try {
                // Route 1: executeCommand (/commands) using the single-property payload helper class
                execRes = api.executeCommand(successfulHeaders, id, ExecCommandRequest(command = wrappedCmd))
            } catch (e: Throwable) {
                Log.w(TAG, "executeCommand failed, trying fallback: executeProcess", e)
                val firstErrDetails = if (e is retrofit2.HttpException) {
                    e.response()?.errorBody()?.string() ?: e.message()
                } else {
                    e.localizedMessage ?: e.javaClass.simpleName
                }
                try {
                    // Route 2: executeProcess (/processes) using the single-property fallback payload helper class
                    execRes = api.executeProcess(successfulHeaders, id, ExecProcessRequest(cmd = wrappedCmd))
                } catch (ex: Throwable) {
                    Log.e(TAG, "All execution routes failed", ex)
                    val secondErrDetails = if (ex is retrofit2.HttpException) {
                        ex.response()?.errorBody()?.string() ?: ex.message()
                    } else {
                        ex.localizedMessage ?: ex.javaClass.simpleName
                    }
                    errorMsg = "Sandbox command execution route failed.\n\n" +
                            "First route (/commands) error: $firstErrDetails\n\n" +
                            "Second route (/processes) error: $secondErrDetails"
                }
            }

            if (execRes != null) {
                val out = execRes.stdout ?: ""
                val err = execRes.stderr ?: ""
                val codeNum = execRes.exitCode ?: execRes.exit_code ?: 0
                ExecutionResult(
                    isSuccess = (codeNum == 0),
                    stdout = out,
                    stderr = err,
                    exitCode = codeNum
                )
            } else {
                ExecutionResult(
                    isSuccess = false,
                    stdout = "",
                    stderr = "",
                    exitCode = null,
                    error = errorMsg ?: "Execution response was empty."
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "E2B execution network error", e)
            val detailedError = if (e is retrofit2.HttpException) {
                val code = e.code()
                val body = e.response()?.errorBody()?.string() ?: ""
                "E2B API Error (HTTP $code): ${body.ifBlank { e.message() }}"
            } else {
                e.localizedMessage ?: e.javaClass.simpleName ?: "Sandbox connection failed."
            }
            ExecutionResult(
                isSuccess = false,
                stdout = "",
                stderr = "",
                exitCode = null,
                error = detailedError
            )
        }
    }
}
