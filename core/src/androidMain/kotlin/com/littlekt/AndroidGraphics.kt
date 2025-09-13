package com.littlekt

import com.littlekt.graphics.Cursor
import com.littlekt.graphics.SystemCursor
import com.littlekt.graphics.webgpu.Adapter
import com.littlekt.graphics.webgpu.AlphaMode
import com.littlekt.graphics.webgpu.Device
import com.littlekt.graphics.webgpu.PresentMode
import com.littlekt.graphics.webgpu.Surface
import com.littlekt.graphics.webgpu.SurfaceConfiguration
import com.littlekt.graphics.webgpu.TextureFormat
import com.littlekt.graphics.webgpu.TextureUsage
import io.ygdrasil.nativeHelper.Helper
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPUInstanceBackend
import io.ygdrasil.wgpu.wgpuInstanceRelease

internal class AndroidGraphics(
    val androidContext: AndroidContext,
) : Graphics {
    override val width: Int get() = androidContext.configuration.surfaceView.width
    override val height: Int get() = androidContext.configuration.surfaceView.height
    override val backBufferWidth get() = width
    override val backBufferHeight get() = height
    override lateinit var surface: Surface
    override lateinit var adapter: Adapter
    override lateinit var device: Device
    override val preferredFormat by lazy { surface.getPreferredFormat(adapter) }
    override val surfaceCapabilities by lazy { surface.getCapabilities(adapter) }
    private var wgpu = WGPU.createInstance(WGPUInstanceBackend.Vulkan) ?: error("Failed to create WGPU instance")

    override fun configureSurface(
        usage: TextureUsage, format: TextureFormat, presentMode: PresentMode, alphaMode: AlphaMode
    ) {
        surface.configure(
            SurfaceConfiguration(device, usage, format, presentMode, alphaMode, width, height)
        )
    }

    override fun supportsExtension(extension: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun setCursor(cursor: Cursor) {
        TODO("Not yet implemented")
    }

    override fun setCursor(cursor: SystemCursor) {
        TODO("Not yet implemented")
    }

    fun handleSurfaceDestroyed() {
        releaseSurfaceDependencies()
    }

    suspend fun handleSurfaceCreated(androidSurface: android.view.Surface) {
        val window = Helper.nativeWindowFromSurface(androidSurface).let { ffi.NativeAddress(it) }
        val nativeSurface = wgpu.getSurfaceFromAndroidWindow(window) ?: error("Can't create Surface")
        surface = Surface(nativeSurface.handler)
        if (::adapter.isInitialized.not()) {
            adapter = Adapter((wgpu.requestAdapter(nativeSurface) ?: error("Can't create Adapter")).handler)
            device = Device(adapter.requestDevice(null).segment)
        }
    }

    private fun releaseSurfaceDependencies() {
        if (::device.isInitialized) {
            device.queue.release()
            device.release()
        }
        if (::adapter.isInitialized) {
            adapter.release()
        }
        if (::surface.isInitialized) {
            surface.release()
        }
    }

    fun release() {
        releaseSurfaceDependencies()
        wgpuInstanceRelease(wgpu.handler)
    }
}