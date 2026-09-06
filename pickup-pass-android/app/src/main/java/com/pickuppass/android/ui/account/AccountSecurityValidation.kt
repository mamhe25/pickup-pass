package com.pickuppass.android.ui.account

internal object AccountSecurityValidation {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun emailError(
        currentEmail: String,
        newEmail: String,
        currentPassword: String
    ): String? {
        val normalizedEmail = newEmail.trim()
        return when {
            normalizedEmail.isBlank() ->
                "Enter your new email address."
            !emailPattern.matches(normalizedEmail) ->
                "Enter a valid email address."
            normalizedEmail.equals(currentEmail.trim(), ignoreCase = true) ->
                "Enter an email address different from your current one."
            currentPassword.isBlank() ->
                "Enter your current password to confirm this change."
            else -> null
        }
    }

    fun passwordError(
        currentPassword: String,
        newPassword: String,
        confirmation: String
    ): String? =
        when {
            currentPassword.isBlank() ->
                "Enter your current password."
            newPassword.length < 8 ->
                "Use at least 8 characters for your new password."
            newPassword == currentPassword ->
                "Choose a password different from your current password."
            confirmation.isBlank() ->
                "Confirm your new password."
            newPassword != confirmation ->
                "The new passwords do not match."
            else -> null
        }
}
