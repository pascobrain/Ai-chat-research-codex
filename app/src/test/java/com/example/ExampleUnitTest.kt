package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testBuildConfigKeys() {
    println("DEBUG_KEYS_START")
    println("GEMINI API KEY: " + BuildConfig.GEMINI_API_KEY)
    println("GROQ API KEY: " + BuildConfig.GROQ_API_KEY)
    println("TAVILY API KEY: " + BuildConfig.TAVILY_API_KEY)
    println("BRAVE API KEY: " + BuildConfig.BRAVE_API_KEY)
    println("E2B API KEY: " + BuildConfig.E2B_API_KEY)
    println("DEBUG_KEYS_END")
  }
}
