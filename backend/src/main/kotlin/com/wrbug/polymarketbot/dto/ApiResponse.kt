package com.wrbug.polymarketbot.dto

import com.wrbug.polymarketbot.enums.ErrorCode

data class ApiResponse<T>(
    val code: Int,
    val data: T?,
    val msg: String
) {
    companion object {
        fun <T> success(data: T?): ApiResponse<T> {
            return ApiResponse(code = 0, data = data, msg = "")
        }

        fun <T> error(
            errorCode: ErrorCode,
            customMsg: String? = null
        ): ApiResponse<T> {
            val msg = customMsg ?: errorCode.message
            return ApiResponse(code = errorCode.code, data = null, msg = msg)
        }

        fun <T> error(code: Int, msg: String): ApiResponse<T> {
            return ApiResponse(code = code, data = null, msg = msg)
        }

        fun <T> paramError(msg: String): ApiResponse<T> {
            return error(ErrorCode.PARAM_ERROR, msg)
        }

        fun <T> notFound(msg: String): ApiResponse<T> {
            return error(ErrorCode.NOT_FOUND, msg)
        }

        fun <T> businessError(msg: String): ApiResponse<T> {
            return error(ErrorCode.BUSINESS_ERROR, msg)
        }

        fun <T> serverError(msg: String): ApiResponse<T> {
            return error(ErrorCode.SERVER_ERROR, msg)
        }
    }
}
