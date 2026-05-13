package com.wrbug.polymarketbot.enums

enum class ErrorCode(
    val code: Int,
    val message: String,
    val messageKey: String
) {
    PARAM_ERROR(1001, "Parameter error", "error.param.error"),
    NOT_FOUND(3001, "Resource not found", "error.not_found"),
    BUSINESS_ERROR(4001, "Business error", "error.business.error"),
    SERVER_ERROR(5001, "Internal server error", "error.server.error");

    companion object {
        fun fromCode(code: Int): ErrorCode? = entries.find { it.code == code }
    }
}
