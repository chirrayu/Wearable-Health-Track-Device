package com.example.healthmonitor

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LiveMapScreen(viewModel: LiveMapViewModel = viewModel()) {
    val positions by viewModel.positions.collectAsState()

    var pageReady by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07111F))
    ) {
        Text(
            text = "LIVE MAP",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode =
                        android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            pageReady = true
                        }
                    }

                    loadUrl("file:///android_asset/live_map.html")
                }
            },
            update = { webView ->
                if (!pageReady) return@AndroidView
                positions.values.forEach { p ->
                    webView.evaluateJavascript(
                        "if (typeof updatePosition === 'function') { " +
                                "updatePosition('${p.id}', ${p.lat}, ${p.lng}); " +
                                "updateStatus('${p.id}', '${p.status}'); }",
                        null
                    )
                }
            }
        )
    }
}