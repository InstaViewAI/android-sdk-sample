package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

/** Columns to draw when the camera's cluster does not describe a grid of its own. */
const val DEFAULT_ZONE_GRID_COLUMNS: Int = 8

/** Rows to draw when the camera's cluster does not describe a grid of its own. */
const val DEFAULT_ZONE_GRID_ROWS: Int = 4

/** Width of the hairline that separates one cell from the next, in pixels. */
private const val BORDER_STROKE = 2f

/** Opacity of a watched cell, chosen so the picture behind it still reads through. */
private const val SELECTED_ALPHA = 0.5f

/** Opacity of an ignored cell, which darkens the picture rather than tinting it. */
private const val UNSELECTED_ALPHA = 0.45f

/** Sentinel for "no cell painted yet in this drag", since a cell index is never negative. */
internal const val NO_CELL = -1

/**
 * The block grid the camera divides its frame into, drawn over whatever backdrop the caller puts
 * behind it. Fully controlled: [selected] holds the indices the camera watches and every gesture
 * reports the whole new list through [onSelectedChange].
 *
 * A cell index is `row * columns + col`, which is the numbering both zone APIs speak. Tapping
 * flips one cell; dragging paints, with the cell the finger starts on deciding whether the stroke
 * fills or erases.
 */
@Composable
fun ActivityZoneGrid(
  selected: List<Int>,
  onSelectedChange: (List<Int>) -> Unit,
  modifier: Modifier = Modifier,
  rows: Int = DEFAULT_ZONE_GRID_ROWS,
  columns: Int = DEFAULT_ZONE_GRID_COLUMNS,
  enabled: Boolean = true,
) {
  val current by rememberUpdatedState(selected)
  var painting by remember { mutableStateOf(true) }
  var lastPainted by remember { mutableIntStateOf(NO_CELL) }
  val selectedColor = AppTheme.colors.accent.copy(alpha = SELECTED_ALPHA)
  val unselectedColor = MaterialTheme.colorScheme.scrim.copy(alpha = UNSELECTED_ALPHA)
  val borderColor = AppTheme.colors.outline

  Canvas(
    modifier = modifier.then(
      if (!enabled) {
        Modifier
      } else {
        Modifier
          .pointerInput(rows, columns) {
            detectTapGestures { offset ->
              val cell = cellAt(offset, size.width.toFloat(), size.height.toFloat(), rows, columns)
              if (cell != NO_CELL) onSelectedChange(current.withCell(cell, cell !in current))
            }
          }
          .pointerInput(rows, columns) {
            detectDragGestures(
              onDragStart = { offset ->
                val cell =
                  cellAt(offset, size.width.toFloat(), size.height.toFloat(), rows, columns)
                painting = cell !in current
                lastPainted = NO_CELL
              },
              onDrag = { change, _ ->
                val cell = cellAt(
                  change.position,
                  size.width.toFloat(),
                  size.height.toFloat(),
                  rows,
                  columns,
                )
                if (cell != NO_CELL && cell != lastPainted) {
                  lastPainted = cell
                  onSelectedChange(current.withCell(cell, painting))
                }
              },
            )
          }
      },
    ),
  ) {
    val cellWidth = size.width / columns
    val cellHeight = size.height / rows
    for (row in 0 until rows) {
      for (col in 0 until columns) {
        val topLeft = Offset(col * cellWidth, row * cellHeight)
        val cellSize = Size(cellWidth, cellHeight)
        drawRect(
          color = if (row * columns + col in selected) selectedColor else unselectedColor,
          topLeft = topLeft,
          size = cellSize,
        )
        drawRect(
          color = borderColor,
          topLeft = topLeft,
          size = cellSize,
          style = Stroke(width = BORDER_STROKE),
        )
      }
    }
  }
}

/** The cell index under [offset] in a [width] by [height] grid, or [NO_CELL] when outside it. */
internal fun cellAt(
  offset: Offset,
  width: Float,
  height: Float,
  rows: Int,
  columns: Int,
): Int {
  if (width <= 0f || height <= 0f) return NO_CELL
  val col = (offset.x / (width / columns)).toInt()
  val row = (offset.y / (height / rows)).toInt()
  if (row !in 0 until rows || col !in 0 until columns) return NO_CELL
  return row * columns + col
}

/** This selection with [cell] added or removed, kept ascending so saves are order-stable. */
internal fun List<Int>.withCell(cell: Int, selected: Boolean): List<Int> =
  if (selected) (this + cell).distinct().sorted() else this - cell
