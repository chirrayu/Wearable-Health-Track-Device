package com.example.healthmonitor

import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.viewinterop.AndroidView


@Composable
fun LiveMapScreen() {

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
                    settings.blockNetworkLoads = false
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.loadsImagesAutomatically = true
                    settings.mixedContentMode =
                        android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient =
                        object : WebViewClient() {

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?
                            ) {

                                super.onPageFinished(
                                    view,
                                    url
                                )

                                view?.postDelayed({

                                    view.evaluateJavascript(
                                        """
                                        if(window.map){
                                        map.invalidateSize();
                                        }
                                        """,
                                        null
                                    )

                                },1000)

                            }

                        }

                    loadUrl(
                        "file:///android_asset/live_map.html"
                    )
                }
            },
            update = { webView ->
                // Call JS functions here to update the map
                // Examples:
                // webView.evaluateJavascript("updatePosition('P1', 28.6210, 77.2110)", null)
                // webView.evaluateJavascript("updateStatus('P6', 'stable')", null)
            }
        )
    }
}