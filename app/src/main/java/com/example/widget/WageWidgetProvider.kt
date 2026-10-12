package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WageWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.widget.UPDATE_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WageWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            updateAllWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val database = AppDatabase.getDatabase(context)
        val wageDao = database.wageDao()

        CoroutineScope(Dispatchers.IO).launch {
            val unpaidEntries = wageDao.getUnpaidEntriesFlow().first()
            val hasPending = unpaidEntries.isNotEmpty()
            val totalPending = unpaidEntries.sumOf { it.wageAmount }

            CoroutineScope(Dispatchers.Main).launch {
                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.wage_tracker_widget)

                    if (hasPending) {
                        views.setTextViewText(R.id.widget_label, "Pending")
                        views.setTextColor(R.id.widget_label, "#E0B355".toColorInt())
                        
                        views.setViewVisibility(R.id.widget_amount, View.VISIBLE)
                        views.setTextViewText(R.id.widget_amount, "₹$totalPending")
                        
                        views.setTextViewText(R.id.widget_subtext, "From previous work days")
                    } else {
                        views.setTextViewText(R.id.widget_label, "All settled")
                        views.setTextColor(R.id.widget_label, "#6FCB8E".toColorInt())
                        
                        views.setViewVisibility(R.id.widget_amount, View.GONE)
                        views.setTextViewText(R.id.widget_subtext, "No pending wages")
                    }

                    // Tap on widget opens MainActivity
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    companion object {
        fun sendUpdateBroadcast(context: Context) {
            val updateIntent = Intent(context, WageWidgetProvider::class.java).apply {
                action = "com.example.widget.UPDATE_WIDGET"
            }
            context.sendBroadcast(updateIntent)
        }
    }
}
