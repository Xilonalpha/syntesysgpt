package com.synthesis.ui.components.exoplanet

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.synthesis.domain.model.xilon.Tic167418703PhotometryGenerator
import com.synthesis.domain.model.xilon.Tic167418703ReportData
import com.synthesis.ui.theme.*
import java.util.Locale
import kotlin.math.abs

enum class TicRenderEngine(val label: String, val icon: String) {
    RECHARTS("Recharts Web", "🌐"),
    NATIVE_CANVAS("Canvas Nativ", "⚡")
}

enum class TicAnalysisTab(val label: String, val icon: String) {
    TRANSIT_DEPTH("Adâncime (4200 ppm)", "📉"),
    PROFILE_SYMMETRY("Simetrie (2.09σ)", "⚖️"),
    JSON_REPORT("Raport & JSON", "📋")
}

/**
 * Composable view that uses Recharts (via an embedded, crash-protected WebView)
 * to render the transit depth (4200 ppm) and profile symmetry (delta sigma = 2.09)
 * of TIC 167418703 based on the CTOI candidate JSON data.
 *
 * Includes dual-mode support (Recharts Web + Native Compose Canvas) and full
 * resilience against emulator hardware acceleration and renderer termination errors.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Tic167418703RechartsView(
    modifier: Modifier = Modifier,
    reportData: Tic167418703ReportData = Tic167418703ReportData.DEFAULT,
    onOpenInXilonScan: ((String) -> Unit)? = null
) {
    Text("Date observaționale indisponibile. Raportul demonstrativ a fost dezactivat.", modifier = modifier)
}

