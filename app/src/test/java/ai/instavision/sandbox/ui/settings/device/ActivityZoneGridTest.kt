package ai.instavision.sandbox.ui.settings.device

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the cell numbering the zone editor and both zone APIs share, `row * columns + col`.
 * Getting the columns wrong here is what shifts every saved zone sideways.
 */
class ActivityZoneGridTest {

  /** A hit in the second row's third column of an 8-wide grid is cell 10, not cell 2 or 3. */
  @Test
  fun cellAtNumbersRowsBeforeColumns() {
    val cell = cellAt(
      offset = Offset(x = 250f, y = 150f),
      width = 800f,
      height = 400f,
      rows = 4,
      columns = 8,
    )
    assertEquals(10, cell)
  }

  /** The same point in a grid with a different column count lands on a different cell. */
  @Test
  fun cellAtFollowsTheGivenColumnCount() {
    val cell = cellAt(
      offset = Offset(x = 250f, y = 150f),
      width = 800f,
      height = 400f,
      rows = 4,
      columns = 4,
    )
    assertEquals(5, cell)
  }

  /** A point outside the grid, and a grid with no size yet, both report no cell. */
  @Test
  fun cellAtRejectsPointsOutsideTheGrid() {
    assertEquals(
      NO_CELL,
      cellAt(offset = Offset(x = 900f, y = 10f), width = 800f, height = 400f, rows = 4, columns = 8),
    )
    assertEquals(
      NO_CELL,
      cellAt(offset = Offset(x = 10f, y = 10f), width = 0f, height = 0f, rows = 4, columns = 8),
    )
  }

  /** Painting a cell keeps the list ascending and never lets a cell appear twice. */
  @Test
  fun withCellAddsAndRemovesWithoutDuplicates() {
    assertEquals(listOf(1, 3, 5), listOf(3, 1).withCell(cell = 5, selected = true))
    assertEquals(listOf(1, 3), listOf(3, 1).withCell(cell = 3, selected = true).sorted())
    assertEquals(listOf(1), listOf(3, 1).withCell(cell = 3, selected = false))
  }
}
