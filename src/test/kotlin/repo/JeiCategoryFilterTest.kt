package dev.ashtonite365.apex.test.repo

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.recipes.JeiCategoryFilter

class JeiCategoryFilterTest {
	@Test fun onlyApexCategoriesStay() {
		for (ns in listOf("minecraft", "jei")) assertTrue(JeiCategoryFilter.hide(ns, "apex"), ns)
		for (uid in listOf("apex:box_contents", "apex:npc_shopping", "apex:mob_drop_recipe")) assertFalse(JeiCategoryFilter.hide(uid.substringBefore(':'), "apex"), uid)
	}
}
