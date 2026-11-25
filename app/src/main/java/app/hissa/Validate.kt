package app.hissa

/** Field checks shared by every form. Each returns a short message, or null when the value is fine. */
object Check {
    private val email = Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$""")

    fun email(v: String): String? = when {
        v.isBlank() -> null
        v.trim().length > 254 || !email.matches(v.trim()) -> "Enter a valid email, like name@example.com."
        else -> null
    }

    /** Sign-up and change-password rule: 8+ characters with a letter and a digit. */
    fun password(v: String): String? = when {
        v.isEmpty() -> null
        v.length < 8 -> "Use at least 8 characters."
        v.length > 72 -> "Use 72 characters or fewer."
        v.none(Char::isLetter) || v.none(Char::isDigit) -> "Mix letters and numbers."
        else -> null
    }

    fun personName(v: String): String? = when {
        v.isBlank() -> null
        v.trim().length < 2 -> "Enter your full name."
        v.none(Char::isLetter) -> "A name needs letters."
        else -> null
    }

    fun groupName(v: String): String? = if (v.isNotBlank() && v.trim().length < 2) "Use at least 2 characters." else null
}
