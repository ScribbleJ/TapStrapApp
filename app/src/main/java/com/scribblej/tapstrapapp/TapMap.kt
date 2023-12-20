package com.scribblej.tapstrapapp

import android.content.Context
import android.util.Log
import org.apache.commons.csv.CSVFormat
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

private var currentMap: TapMap = mapOf()
private var allTapMaps: Map<MapID, TapMap> = mapOf()

fun stringToTapPattern(tapIn: String) : TapPattern {
    return try {
        tapIn.reversed().toInt(2)
    } catch (e: NumberFormatException) {
        Log.d("TapMap", "$tapIn is not a Tap Pattern.")
        throw IllegalArgumentException("$tapIn is not a Tap Pattern.")
    }
}

fun tapPatternToString(tapIn: TapPattern) : String {
    return String.format("%5s", Integer.toBinaryString(tapIn).reversed()).replace(' ', '0')
}

fun lookupTap(tapCount: TapCount, tapPattern: TapPattern) : CommandList {
    return currentMap[tapPattern]?.getOrNull(tapCount - 1) ?: listOf()
}

fun getCommandListsForTapPattern(tapPattern: TapPattern) : List<CommandList> {
    return currentMap[tapPattern] ?: listOf()
}

fun getCommandList(commandLists: List<CommandList>, tapCount: TapCount): CommandList {
    val tapIndex = tapCount - 1
    val wrappedIndex = tapIndex % commandLists.size // Wrap around
    Log.d("foo", "getCommandList: input count $tapCount output index: $wrappedIndex")
    return commandLists.getOrNull(wrappedIndex) ?: listOf()
}

fun getCommandList(tapPattern: TapPattern, tapCount: TapCount): CommandList {
    val commandLists = getCommandListsForTapPattern(tapPattern)

    return getCommandList(commandLists, tapCount)
}

fun BooleanArray.toBinaryString(): String = this.joinToString("") { if (it) "1" else "0" }

fun BooleanArray.toBinaryInt(): Int {
    var result = 0
    for (bit in this) {
        result = (result shl 1) + if (bit) 1 else 0
    }
    return result
}

fun setCurrentMap(mapID: MapID) {
    if(!allTapMaps.containsKey(mapID)) {
        Log.e("foo","Requested map $mapID does not exist.")
        return
    }

    currentMap = allTapMaps[mapID] ?: throw IllegalStateException("Map not found. Application cannot proceed.")
}

fun getResourceEntryName(context: Context, resourceId: Int): String {
    return context.resources.getResourceEntryName(resourceId)
}

fun initializeMaps(context: Context) {

    val files = listCsvFiles(getAppSpecificExternalDirPath(context))
    val tempLookUpTable = mutableMapOf<String, PatternMap>()

    // Load our internal smaps first, the user's will overwrite them if the user has provided any.
    listOf(R.raw.default_1, R.raw.default_2, R.raw.default_3, R.raw.shiftmap_1, R.raw.switchmap_1).forEach {
        val fileName = getResourceEntryName(context, it).uppercase()
        tempLookUpTable[fileName] = loadCsvAsMap(context, it)
    }

    files.forEach { file ->
        tempLookUpTable[file.nameWithoutExtension] = loadCsvAsMap(file.path)
        Log.d("foo","Loaded file: ${file.nameWithoutExtension}")
    }

    val tempAllTapMaps = mutableMapOf<MapID, MutableMap<TapPattern, MutableList<CommandList>>>()

    tempLookUpTable.forEach { (fileName, patternMap) ->
        val mapId: MapID

        val tapCount: Int

        try {
            val splitValues = fileName.split("_")
            mapId = splitValues[0]
            tapCount = splitValues[1].toInt()
        } catch (e: Exception) {
            Log.e("foo","Unable to parse filename: $fileName required format is MAP_COUNT e.g. 'DEFAULT_1'")
            return@forEach
        }

        val tapMap = tempAllTapMaps.getOrPut(mapId) { mutableMapOf() }
        patternMap.forEach { (tapPattern, commandList) ->
            val commandLists = tapMap.getOrPut(tapPattern) { mutableListOf() }
            while (commandLists.size < tapCount) {
                commandLists.add(emptyList()) // Fill with empty lists for missing tap counts
            }
            commandLists[tapCount - 1] = commandList
        }
    }

    // Check and prune
    pruneTapMaps(tempAllTapMaps)

    // Convert to immutable structure
    allTapMaps = tempAllTapMaps.mapValues { (_, tapMap) ->
        tapMap.mapValues { (_, commandLists) ->
            commandLists.toList() // Convert MutableList to List
        }.toMap() // Convert MutableMap to Map
    }

    if (!allTapMaps.containsKey("DEFAULT")) {
        Log.e("foo", "MISSING DEFAULT MAP, PANIC")
        throw IllegalStateException("DEFAULT map not found. Application cannot proceed.")
        // TODO: Panic.
    }

    setCurrentMap("DEFAULT") // TODO:  Setting?
}

private fun pruneTapMaps(allTapMaps: MutableMap<MapID, MutableMap<TapPattern, MutableList<CommandList>>>) {
    allTapMaps.forEach { (mapId, tapMap) ->

        Log.d("foo", "pruning $mapId")
        val patternsToRemove = mutableListOf<TapPattern>()

        tapMap.forEach { (tapPattern, commandLists) ->
            var previousListWasEmpty = false

            commandLists.removeAll { commandList ->
                    val isEmptyOrOnlyEmptyStrings = commandList.isNullOrEmpty() || commandList.all { it.isEmpty() }
                if(isEmptyOrOnlyEmptyStrings) {
                    Log.d("foo", "Map $mapId: Empty CommandList for pattern $tapPattern")
                    previousListWasEmpty = true
                    true
                } else {
                    val charCodes = commandList[0].map { it.code }.joinToString(" ")
                    Log.d(
                        "foo",
                        "Not removing '$commandList' from $mapId because it has a length of ${commandList.size} and contains $charCodes"
                    )
                    if (previousListWasEmpty) {
                        Log.e(
                            "foo",
                            "Error in map $mapId: Empty CommandList followed by populated CommandList for pattern $tapPattern"
                        )
                    }
                    previousListWasEmpty = false
                    false
                }
            }

            if (commandLists.isEmpty()) {
                patternsToRemove.add(tapPattern)
                Log.d("foo", "Map $mapId has no commandlists for $tapPattern")
            }
        }
        patternsToRemove.forEach { tapPattern ->
            tapMap.remove(tapPattern)
        }
    }
}

fun loadCsvAsMap(filePath: String): PatternMap {
    val map: MutableMap<TapPattern, CommandList> = mutableMapOf()

    FileReader(filePath).use { reader ->
        val csvParser = CSVFormat.DEFAULT
            .withCommentMarker('#')
            .parse(reader)
        for (csvRecord in csvParser) {
            val keyString = csvRecord.get(0)
            val keyInt = stringToTapPattern(keyString) // Convert binary string to Int
            val values = csvRecord.toList().subList(1, csvRecord.size())
            map[keyInt] = values
        }
    }

    return map.toMap()
}

fun loadCsvAsMap(context: Context, resourceId: Int): PatternMap {
    val map: MutableMap<TapPattern, CommandList> = mutableMapOf()

    val inputStream = context.resources.openRawResource(resourceId)
    InputStreamReader(inputStream).use { reader ->
        val csvParser = CSVFormat.DEFAULT
            .withCommentMarker('#')
            .parse(reader)
        for (csvRecord in csvParser) {
            val keyString = csvRecord.get(0)
            val keyInt = keyString.reversed().toInt(2) // Convert binary string to Int
            val values = csvRecord.toList().subList(1, csvRecord.size())
            map[keyInt] = values
        }
    }

    return map.toMap()
}

fun getAppSpecificExternalDirPath(context: Context): String {
    val appSpecificExternalDir = context.getExternalFilesDir(null)
    return appSpecificExternalDir?.absolutePath ?: "not available, sorry."
}

fun listCsvFiles(directoryPath: String): List<File> {
    val directory = File(directoryPath)
    return if (directory.exists() && directory.isDirectory) {
        directory.listFiles { _, name -> name.endsWith(".csv", ignoreCase = true) }?.toList() ?: emptyList()
    } else {
        emptyList()
    }
}

fun doesFileExist(context: Context, fileName: String): Boolean {
    val file = File(context.getExternalFilesDir(null), fileName)
    return file.exists()
}

fun printStringCharacterCodes(str: String) {
    str.forEach { char ->
        print("${char.code} ")
    }
}
