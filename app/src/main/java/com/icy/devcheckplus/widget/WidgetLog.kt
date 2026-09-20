package com.icy.devcheckplus.widget

import android.util.Log

/**
 * Controlled widget diagnostics.
 *
 * Widget failures used to be swallowed by empty `catch (_: Throwable) {}`
 * blocks, which made "Can't load widget" undebuggable. Every failure path now
 * reports through this one tag with the provider name, widget id, layout and
 * the exception type/message — enough to diagnose, without ever leaking a
 * stack trace into user-facing UI.
 */
internal object WidgetLog {

    const val TAG = "IcyCheakWidget"

    fun renderFailure(provider: String, widgetId: Int, layout: String, t: Throwable) {
        Log.w(TAG, "$provider #$widgetId full render failed (layout=$layout): ${describe(t)}")
    }

    fun partialRenderFailure(provider: String, layout: String, section: String, t: Throwable) {
        Log.w(TAG, "$provider section '$section' failed (layout=$layout), field left at placeholder: ${describe(t)}")
    }

    fun updateFailure(provider: String, widgetId: Int, t: Throwable) {
        Log.w(TAG, "$provider #$widgetId updateAppWidget failed: ${describe(t)}")
    }

    fun snapshotFailure(provider: String, t: Throwable) {
        Log.w(TAG, "$provider telemetry snapshot failed, using empty snapshot: ${describe(t)}")
    }

    fun schedulerNote(message: String) {
        Log.i(TAG, message)
    }

    private fun describe(t: Throwable): String =
        "${t.javaClass.simpleName}: ${t.message ?: "no message"}"
}
