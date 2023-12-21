package com.scribblej.tapstrapapp

// Each holds a string that is presumed to represent a KeyCommand we know,
// e.g. "RETURN", "ALTONCE", etc.  Full list in actionMap in TapInputMethodService (for now).
// If it's NOT one of those, then it's considered an actual sequence of literal characters to "type"
typealias KeyCommand = String
typealias CommandList = List<KeyCommand>
// TapPattern is what we've decided to call a particular configuration of tapped fingers.
typealias TapPattern = Int
// Mapping of TapPattern to keypresses:
typealias PatternMap = Map<TapPattern, CommandList>
// Name of each possible TapMap in the app.
typealias MapID = String
// Number of multitaps for this TapMap
typealias TapCount = Int
//
typealias TapMap = Map<TapPattern, List<CommandList>>

// used for returns and calls in the TapInputMethodService -> TapController -> MultiTapHandler call stack.
data class TapData( var tapPattern: TapPattern = 0,
                    var tapCount: TapCount = 0,
                    var metaKeys: Int = 0,
                    var modOnce: Boolean = false,
                    var potentialCommandLists: List<CommandList> = listOf(),
                    var executableCommandList: CommandList = listOf())
