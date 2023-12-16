package com.scribblej.tapstrapapp

object MetaKeysManager {
    private var _metaKeys: Int = 0
    val metaKeys: Int
        get() = _metaKeys
    private var _useOnce: Boolean = false
    val useOnce: Boolean
        get() = _useOnce

    private val metaKeysObservers = mutableListOf<(Int, Boolean) -> Unit>()

    fun activateMetaKeys(newMetaKeys: Int, newUseOnce: Boolean = false) {
        _metaKeys = _metaKeys or newMetaKeys
        _useOnce = newUseOnce
        notifyObservers()
    }

    fun setMetaKeys(newMetaKeys: Int, newUseOnce: Boolean = false) {
        _metaKeys = newMetaKeys
        _useOnce = newUseOnce
        notifyObservers()
    }

    fun resetMetaKeys() {
        setMetaKeys(0,false)
    }

    fun toggleMetaKey(mK: Int) {
        _metaKeys = _metaKeys xor mK
        notifyObservers()
    }

    private fun notifyObservers() {
        metaKeysObservers.forEach { it(_metaKeys, _useOnce) }
    }

    fun addObserver(observer: (Int, Boolean) -> Unit) {
        metaKeysObservers.add(observer)
    }

    fun removeObserver(observer: (Int, Boolean) -> Unit) {
        metaKeysObservers.remove(observer)
    }
}
