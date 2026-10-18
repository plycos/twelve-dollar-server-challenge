package com.lycosp.exceptions

data class TokenException(val error: String) : SecurityException(error)
