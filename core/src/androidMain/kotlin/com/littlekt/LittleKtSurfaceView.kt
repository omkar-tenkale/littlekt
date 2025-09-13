package com.littlekt

import android.content.Context as AndroidContext
import com.littlekt.Context as LittleKtContext
import com.littlekt.AndroidContext as LittleKtAndroidContext
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.littlekt.async.KtScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class LittleKtSurfaceView @JvmOverloads constructor(
    context: AndroidContext, attrs: AttributeSet? = null, defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {
    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var app: LittleKtApp? = null
    private var renderJob: Job? = null
    private val androidGraphics get() = app?.context?.graphics as? AndroidGraphics

    init {
        KtScope.initiate()
        holder.setFormat(PixelFormat.RGBA_8888)
        holder.addCallback(this)
    }

    var game: ((app: LittleKtContext) -> ContextListener)? = null
        set(value) {
            check(value != null) { "game must not be null" }
            check(field == null) { "game can be set only once per view instance" }
            field = value
            if (holder.surface.isValid) {
                startApp(value)
            }
        }

    private fun startApp(game: ((app: LittleKtContext) -> ContextListener)) {
        viewScope.launch {
            app = createLittleKtApp { surfaceView = this@LittleKtSurfaceView }.apply {
                start(game)
                startRendering()
            }
        }
    }

    private fun LittleKtApp.startRendering() {
        suspend fun awaitFrame(choreographer: Choreographer): Long = suspendCancellableCoroutine { continuation ->
            val callback = Choreographer.FrameCallback { frameTimeNanos ->
                continuation.resume(frameTimeNanos) { cause, _, _ -> throw cause }
            }
            choreographer.postFrameCallback(callback)
            continuation.invokeOnCancellation {
                choreographer.removeFrameCallback(callback)
            }
        }
        renderJob = viewScope.launch {
            val choreographer = Choreographer.getInstance()
            while (coroutineContext.isActive) {
                awaitFrame(choreographer)
                (context as? LittleKtAndroidContext)?.let {
                    val nextFrameRequested = it.update()
                    if (nextFrameRequested.not()) {
                        release()
                        break
                    }
                }
            }
        }
    }

    private fun stopRendering() {
        renderJob?.cancel()
        renderJob = null
    }

    fun release() {
        stopRendering()
        app?.context?.close()
        androidGraphics?.release()
        app = null
        viewScope.cancel()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        viewScope.launch {
            androidGraphics?.handleSurfaceCreated(holder.surface)
            app?.startRendering() ?: game?.let(::startApp)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        app?.context?.let { (it as? LittleKtAndroidContext)?.dispatchResize() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        androidGraphics?.handleSurfaceDestroyed()
        stopRendering()
        // if we do not set app = null and keep the same app instance to be reused later when new surface is created,
        // the wgpu is crashing with: Fatal signal 6 (SIGABRT), code -1 (SI_QUEUE) in tid 11850,
        // probably due to to old window referenced by the adapter, see `wgpu.requestAdapter(nativeSurface)` in AndroidGraphics.handleSurfaceCreated
        // but this is bad because new app will be recreated (in surfaceCreated) everytime user presses home button and comes back
        // TODO: find a way to reuse same app instance across android.view.Surface changes
        app = null
    }
}