package io.github.lucaskfp.learnharness.engine

import android.content.res.AssetManager

/** Read access to the app's bundled assets: the upstream prompts and tool contracts. */
interface AssetSource {
    fun read(path: String): String

    /** Names of the files directly inside [directory], sorted. */
    fun list(directory: String): List<String>
}

class AndroidAssetSource(private val assets: AssetManager) : AssetSource {
    override fun read(path: String): String = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    override fun list(directory: String): List<String> = assets.list(directory).orEmpty().sorted()
}
