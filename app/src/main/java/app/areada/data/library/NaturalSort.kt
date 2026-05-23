package app.areada.data.library

object NaturalSort {
    fun <T> comparator(selector: (T) -> String): Comparator<T> =
        Comparator { left, right -> compare(selector(left), selector(right)) }

    fun compare(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0

        while (leftIndex < left.length && rightIndex < right.length) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]

            if (leftChar.isDigit() && rightChar.isDigit()) {
                val numberCompare = compareNumberRun(
                    left = left,
                    leftStart = leftIndex,
                    right = right,
                    rightStart = rightIndex,
                )
                if (numberCompare.result != 0) {
                    return numberCompare.result
                }
                leftIndex = numberCompare.nextLeftIndex
                rightIndex = numberCompare.nextRightIndex
            } else {
                val charCompare = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
                if (charCompare != 0) {
                    return charCompare
                }
                leftIndex++
                rightIndex++
            }
        }

        return (left.length - leftIndex).compareTo(right.length - rightIndex)
    }

    private fun compareNumberRun(
        left: String,
        leftStart: Int,
        right: String,
        rightStart: Int,
    ): NumberRunCompare {
        var leftEnd = leftStart
        var rightEnd = rightStart
        while (leftEnd < left.length && left[leftEnd].isDigit()) {
            leftEnd++
        }
        while (rightEnd < right.length && right[rightEnd].isDigit()) {
            rightEnd++
        }

        val leftDigits = left.substring(leftStart, leftEnd)
        val rightDigits = right.substring(rightStart, rightEnd)
        val leftTrimmed = leftDigits.trimStart('0').ifEmpty { "0" }
        val rightTrimmed = rightDigits.trimStart('0').ifEmpty { "0" }

        val lengthCompare = leftTrimmed.length.compareTo(rightTrimmed.length)
        if (lengthCompare != 0) {
            return NumberRunCompare(lengthCompare, leftEnd, rightEnd)
        }

        val digitCompare = leftTrimmed.compareTo(rightTrimmed)
        if (digitCompare != 0) {
            return NumberRunCompare(digitCompare, leftEnd, rightEnd)
        }

        return NumberRunCompare(leftDigits.length.compareTo(rightDigits.length), leftEnd, rightEnd)
    }

    private data class NumberRunCompare(
        val result: Int,
        val nextLeftIndex: Int,
        val nextRightIndex: Int,
    )
}
