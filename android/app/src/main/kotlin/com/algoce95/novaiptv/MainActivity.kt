package com.algoce95.novaiptv

import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
	override fun onCreate(savedInstanceState: android.os.Bundle?) {
		getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
			.edit()
			.remove("flutter.cache_live_channels")
			.remove("flutter.cache_movies")
			.remove("flutter.cache_series")
			.remove("flutter.cache_live_categories")
			.remove("flutter.cache_vod_categories")
			.remove("flutter.cache_series_categories")
			.apply()
		super.onCreate(savedInstanceState)
	}
}
