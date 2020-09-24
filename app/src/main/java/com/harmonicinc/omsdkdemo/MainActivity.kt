package com.harmonicinc.omsdkdemo

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.harmonicinc.omsdkdemo.util.AdSessionUtil
import com.iab.omid.library.harmonicinc.adsession.AdEvents
import com.iab.omid.library.harmonicinc.adsession.AdSession
import com.iab.omid.library.harmonicinc.adsession.CreativeType
import com.iab.omid.library.harmonicinc.adsession.media.MediaEvents
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val TAG = "harmonic_omsdk_demo"
    private lateinit var player: SimpleExoPlayer
    private var adSession: AdSession? = null
    private var mediaEvents: MediaEvents? = null
    private var adEvents: AdEvents? = null

    private val PLAYER_VOLUME = 1
    private val CUSTOM_REFERENCE_DATA = "{\"user\":\"me\" }"

    private var curEvent: Event = Event.UNKNOWN
    private var loaded = false
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = true

    lateinit var eventJson : JSONArray
    val eventUrl = URL("https://harmonic-player-sdk-assets.s3-us-west-2.amazonaws.com/omsdk-resource/multi-period/event.json")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        Thread(Runnable {
            try {
                val url = eventUrl
                val urlConnection = url.openConnection()
                eventJson = JSONArray(urlConnection.getInputStream().bufferedReader().readText())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }).start()



        //Init Player
        player = SimpleExoPlayer.Builder(applicationContext).build()
        exoplayer_view.requestFocus()
        exoplayer_view.player = player

        //Progress Runnable
        player.addListener(
            object : Player.EventListener {
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    if (playbackState == ExoPlayer.STATE_READY) {
                        updatePlayPause()
                        postProgress()
                    }
                }
            }
        )

        //Load asset
        val assetUrl =
            "https://harmonic-player-sdk-assets.s3-us-west-2.amazonaws.com/omsdk-resource/multi-period/manifest.mpd"

        val dataSourceFactory = DefaultDataSourceFactory(
            applicationContext,
            Util.getUserAgent(applicationContext, "harmonic.omsdk.demo")
        )
        val mediaSourceFactory = DashMediaSource.Factory(dataSourceFactory)
        val mediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(assetUrl))

        player.playWhenReady = true
        player.setMediaSource(mediaSource)
        player.prepare()
    }

    private fun createSession() {
        adSession = try {
            AdSessionUtil.getNativeAdSession(
                applicationContext,
                CUSTOM_REFERENCE_DATA,
                CreativeType.VIDEO
            )
        } catch (e: MalformedURLException) {
            Log.d(TAG, "setupAdSession failed", e)
            throw java.lang.UnsupportedOperationException(e)
        }
        mediaEvents = MediaEvents.createMediaEvents(adSession)
        adEvents = AdEvents.createAdEvents(adSession)
        adSession!!.registerAdView(exoplayer_view)
        adSession!!.start()
    }

    private fun destroySession() {
        mediaEvents = null
        adSession?.finish()
        adSession = null
//        loaded = false
    }


    private fun onStateReady() {
//        if (!loaded) {
//            val vastProperties =
//                VastProperties.createVastPropertiesForNonSkippableMedia(false, Position.STANDALONE)
//            Log.d(TAG, "mediaEvents.loaded() VastProperties = [$vastProperties]")
//            adEvents!!.loaded(vastProperties)
//            loaded = true
//        }
    }


    private fun postProgress() {
        handler.removeCallbacks(progressRunnable)
        handler.postDelayed(progressRunnable, 100)
    }

    private val progressRunnable = Runnable {
        onProgress()
    }

    private fun onProgress() {
        val position = player.currentPosition
        var newEvent: Pair<Long, Event>? = null

        //Create Session before Impression
        for (idx in 0 until eventJson.length()){
            val eventTime = eventJson.getJSONObject(idx).getLong("time")
            val event = stringToEvent(eventJson.getJSONObject(idx).getString("event"))

            if (adSession == null &&
                eventTime > position &&
                (eventTime - position) < 2000 &&
                event == Event.IMPRESSION) {
                createSession()
                onStateReady()
                break
            }
        }
        for (idx in 0 until eventJson.length()){
            val eventTime = eventJson.getJSONObject(idx).getLong("time")

            val event = stringToEvent(eventJson.getJSONObject(idx).getString("event"))
            if (eventTime < position) {
                // last event
                if (idx == eventJson.length() - 1) {
                    newEvent = Pair(eventTime,event)
                    break
                }
                if (eventJson.getJSONObject(idx+1).getLong("time") < position) {
                    continue
                }
                newEvent = Pair(eventTime,event)
                break
            }
        }

        newEvent?.let {
            if (it.second != curEvent) {
                println("Got event update at position $position current Event: $curEvent new Event: ${newEvent.second} ")
                postEvent(it.second)
            }
        }
        updatePlayPause()
        postProgress()
    }

    private fun updatePlayPause() {
        val playing: Boolean =
            player.playWhenReady && (player.playbackState == ExoPlayer.STATE_READY)
        if (playing != this.isPlaying) {
            if (playing) {
                Log.d(TAG, "mediaEvents.resume()")
                mediaEvents?.resume()
            } else {
                Log.d(TAG, "mediaEvents.pause()")
                mediaEvents?.pause()
            }
            this.isPlaying = playing
        }
    }

    private fun postEvent(event: Event) {
        println("postEvent: $event")
        exoplayer_view.useController = false
        if (adSession == null){
            createSession()
            onStateReady()
        }
        when (event) {
            Event.IMPRESSION -> {
                adEvents!!.impressionOccurred()
            }
            Event.FIRST -> {
                Log.d(TAG, "mediaEvents.firstQuartile()")
                mediaEvents!!.firstQuartile()
            }
            Event.MID -> {
                Log.d(TAG, "mediaEvents.midpoint()")
                mediaEvents!!.midpoint()
            }
            Event.THIRD -> {
                Log.d(TAG, "mediaEvents.thirdQuartile()")
                mediaEvents!!.thirdQuartile()
            }
            Event.COMPLETE -> {
                exoplayer_view.useController = true
                mediaEvents!!.complete()
                destroySession()
            }
        }
        curEvent = event

    }

    override fun onDestroy() {
        super.onDestroy()
        adSession?.finish()
        adSession = null
        handler.removeCallbacksAndMessages(null)
        player.release()
    }

    private fun stringToEvent(eventString: String):Event{
        return when(eventString){
            "IMPRESSION" -> Event.IMPRESSION
            "FIRST" -> Event.FIRST
            "MID" -> Event.MID
            "THIRD" -> Event.THIRD
            "COMPLETE" -> Event.COMPLETE
            else -> Event.UNKNOWN
        }
    }
    enum class Event {
        IMPRESSION, FIRST, MID, THIRD, COMPLETE, UNKNOWN
    }

}