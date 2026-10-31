package dev.ashtonite365.apex.features.items.recipes

object ViewerGridRows {

	@JvmStatic
	@Volatile
	var stripActive: Boolean = false

	private var layingOutItemList = false

	@JvmStatic
	fun beginLayout(isItemList: Boolean) {
		layingOutItemList = isItemList
	}

	@JvmStatic
	fun endLayout() {
		layingOutItemList = false
	}

	const val ITEM_LIST_GRID = "IngredientListOverlay"

	@JvmStatic
	fun isItemListGrid(name: String?): Boolean = name == ITEM_LIST_GRID

	@JvmStatic
	fun rows(fitRows: Int, maxRows: Int): Int = rows(fitRows, maxRows, layingOutItemList && stripActive && fillSetting())

	@JvmStatic var fillSetting: () -> Boolean = { true }

	fun rows(fitRows: Int, maxRows: Int, fill: Boolean): Int =
		if (fill && fitRows > maxRows) fitRows else minOf(fitRows, maxRows)
}
