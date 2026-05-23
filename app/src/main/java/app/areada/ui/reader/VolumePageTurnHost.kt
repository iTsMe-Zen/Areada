package app.areada.ui.reader

interface VolumePageTurnHost {
    fun setVolumePageTurnHandler(handler: ((volumeUp: Boolean) -> Boolean)?)
}

