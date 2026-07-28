package com.example.countdown.ui

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countdown.theme.Black
import com.example.countdown.theme.InactiveGray
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * Circular wheel picker: scrolling past the min wraps to max (and vice versa),
 * matching system NumberPicker wrap behavior (e.g. hours 00 ↑ → 99).
 */
@Composable
fun WheelPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: Int = 3,
    itemHeight: Dp = 52.dp,
    selectedTextSize: TextUnit = 36.sp,
    unselectedTextSize: TextUnit = 28.sp,
    selectedColor: Color = Black,
    unselectedColor: Color = InactiveGray,
    enabled: Boolean = true,
) {
    val items = remember(range) { range.toList() }
    val itemCount = items.size
    require(itemCount > 0)
    require(visibleItems % 2 == 1) { "visibleItems must be odd so a center row exists" }

    // Large repeating virtual list so the wheel feels infinite in both directions.
    val repeatCount = 10_000
    val totalItems = itemCount * repeatCount
    val middleBase = (repeatCount / 2) * itemCount
    val centerOffset = visibleItems / 2

    fun valueAt(index: Int): Int = items[((index % itemCount) + itemCount) % itemCount]

    fun indexOfValueNear(targetValue: Int, nearIndex: Int): Int {
        val valueIndex = items.indexOf(targetValue).coerceAtLeast(0)
        val base = nearIndex - (nearIndex % itemCount)
        val candidates = listOf(
            base + valueIndex - itemCount,
            base + valueIndex,
            base + valueIndex + itemCount,
        )
        return candidates
            .filter { it in 0 until totalItems }
            .minByOrNull { abs(it - nearIndex) }
            ?: (middleBase + valueIndex)
    }

    fun centeredIndex(listState: LazyListState): Int {
        val layoutInfo = listState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        return if (visible.isEmpty()) {
            listState.firstVisibleItemIndex + centerOffset
        } else {
            val viewportCenter = layoutInfo.viewportStartOffset +
                (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
            visible.minByOrNull { item ->
                abs((item.offset + item.size / 2f) - viewportCenter)
            }?.index ?: (listState.firstVisibleItemIndex + centerOffset)
        }.coerceIn(0, totalItems - 1)
    }

    suspend fun scrollToCentered(listState: LazyListState, centerIndex: Int) {
        val firstIndex = (centerIndex - centerOffset).coerceIn(0, totalItems - 1)
        listState.scrollToItem(firstIndex)
    }

    val initialCenter = middleBase + items.indexOf(value).coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialCenter - centerOffset).coerceAtLeast(0),
    )
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center,
    )
    val pickerHeight = itemHeight * visibleItems
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val enabledState = rememberUpdatedState(enabled)

    var lastEmittedValue by remember { mutableIntStateOf(value) }

    val selectedIndex by remember {
        derivedStateOf { centeredIndex(listState) }
    }

    // Sync external value changes (e.g. presets) without fighting user scroll.
    LaunchedEffect(value, itemCount) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        val current = centeredIndex(listState)
        if (valueAt(current) == value) {
            lastEmittedValue = value
            return@LaunchedEffect
        }
        scrollToCentered(listState, indexOfValueNear(value, current))
        lastEmittedValue = value
    }

    // Emit selection when scrolling settles; recenter if near virtual edges.
    LaunchedEffect(listState, itemCount) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .map { centeredIndex(listState) }
            .distinctUntilChanged()
            .collect { index ->
                val selected = valueAt(index)
                if (enabledState.value && selected != lastEmittedValue) {
                    lastEmittedValue = selected
                    onValueChangeState.value(selected)
                }

                val edgeMargin = itemCount * 50
                if (index < edgeMargin || index > totalItems - edgeMargin) {
                    val recentered = middleBase + items.indexOf(selected).coerceAtLeast(0)
                    scrollToCentered(listState, recentered)
                }
            }
    }

    Box(
        modifier = modifier.height(pickerHeight),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(pickerHeight),
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            userScrollEnabled = enabled,
        ) {
            items(totalItems) { index ->
                val itemValue = valueAt(index)
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d".format(itemValue),
                        color = if (isSelected) selectedColor else unselectedColor,
                        fontSize = if (isSelected) selectedTextSize else unselectedTextSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
