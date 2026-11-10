package com.m3rd.app

import android.content.Context

object MangoLicenseManager {
    const val STORE_PURCHASE_URL = "https://apk3rade.mangoad.online/app/com.mangodrive.unlim"
    const val LITE_VAULT_MAX_ITEMS = Int.MAX_VALUE
    const val LITE_DAILY_AI_LIMIT = Int.MAX_VALUE

    fun isPro(ctx: Context): Boolean = true
    fun getEditionName(ctx: Context): String = "مانجو درايف Unlim Cloud (غير محدود للأبد)"
    fun getTierBadge(ctx: Context): String = "UNLIM"
    fun setProLicensed(ctx: Context, enabled: Boolean, key: String = "") {}
    fun activateWithKey(ctx: Context, enteredKey: String): Boolean = true
    fun openStorePurchasePage(ctx: Context) {}
    fun getRemainingAiEraserRuns(ctx: Context): Int = Int.MAX_VALUE
    fun consumeAiEraserRun(ctx: Context): Boolean = true
    fun canAddToVault(ctx: Context, currentVaultSize: Int, incomingCount: Int): Boolean = true
}
