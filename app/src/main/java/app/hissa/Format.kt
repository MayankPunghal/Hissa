package app.hissa

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

fun symbol(cur: String) = when (cur) {
    "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> "$cur "
}

/** 1234567 -> 12,34,567 (Indian grouping). */
private fun indian(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    var rest = s.dropLast(3)
    val sb = StringBuilder("," + s.takeLast(3))
    while (rest.length > 2) { sb.insert(0, "," + rest.takeLast(2)); rest = rest.dropLast(2) }
    return rest + sb
}

/** Amount with currency symbol. INR uses Indian grouping; the sign is dropped (callers say owe / owed in words). */
fun money(cents: Long, cur: String = "INR"): String {
    val a = abs(cents)
    val whole = if (cur == "INR") indian(a / 100) else "%,d".format(Locale.US, a / 100)
    return "${symbol(cur)}$whole.${"%02d".format(a % 100)}"
}

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

fun firstName(name: String) = name.trim().substringBefore(' ').ifEmpty { name }

/** "Today", "Yesterday", "12 Nov", "12 Nov 2025". */
fun dayLabel(iso: String): String = try {
    val d = LocalDate.parse(iso.take(10))
    val today = LocalDate.now()
    when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.year == today.year -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        else -> d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    }
} catch (e: Exception) { iso }

fun agoLabel(iso: String): String = try {
    val t = OffsetDateTime.parse(iso).toInstant()
    val now = java.time.Instant.now()
    val m = ChronoUnit.MINUTES.between(t, now)
    when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 60 * 24 -> "${m / 60}h ago"
        m < 60 * 24 * 7 -> "${m / (60 * 24)}d ago"
        else -> dayLabel(iso)
    }
} catch (e: Exception) { "" }

/** Short, human error text for a snackbar. */
fun friendly(e: Throwable): String {
    val m = e.message.orEmpty().lineSequence().firstOrNull().orEmpty()
    return when {
        m.contains("check constraint", true) || m.contains("violates", true) -> "Please fill in every field and try again."
        m.contains("schema cache", true) || m.contains("Could not find the function", true) ->
            "This isn't available right now. Please try again later."
        m.contains("Invalid login", true) -> "Wrong email or password."
        m.contains("not confirmed", true) -> "Please confirm your email first."
        m.contains("already registered", true) -> "That email already has an account. Try signing in."
        m.contains("Unable to resolve host", true) || m.contains("timeout", true) || m.contains("Failed to connect", true) ->
            "No internet connection."
        m.contains("invalid invite code", true) -> "That invite code doesn't match any group."
        m.contains("expired", true) || m.contains("invalid", true) && m.contains("token", true) -> "That code is wrong or has expired."
        m.isBlank() || m.length > 90 -> "Something went wrong. Please try again."
        else -> m
    }
}

/** Finds the invite code in whatever was pasted: a link, a "Code: abc12345" line, or the bare code. */
fun extractInviteCode(text: String): String {
    val t = text.trim()
    Regex("(?:join/|code=)([A-Za-z0-9]{6,16})").find(t)?.let { return it.groupValues[1].lowercase() }
    Regex("(?i)code[^a-z0-9]+([a-z0-9]{6,16})").find(t)?.let { return it.groupValues[1].lowercase() }
    return t.filter { it.isLetterOrDigit() }.lowercase().take(12)
}
