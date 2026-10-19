package com.lycosp.utils

fun String.toPositiveIntOrNull(): Int? =
    this.toIntOrNull().takeIf { it != null && it > 0 }
