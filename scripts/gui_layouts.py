# SPDX-License-Identifier: GPL-3.0-or-later
"""Apex GUI layouts, one entry per XML file under assets/apex/gui. Rendered by gen_gui_layouts.py."""
from gen_gui_layouts import A, M, button, label

LAYOUTS = {
	"anniversary_pig.xml": (
		M.Column()[
			M.Array(data="@pigs")[
				M.Row()[
					label("Pig: "),
					A.Bar(progress="@timeLeft", total=1, fillColor="#ffb6c1", emptyColor="#db7093"),
				],
			],
			label("Profits:"),
			M.Array(data="@rewards")[
				M.When(condition="@isKnown")[
					M.Row()[
						label("@count"),
						label("x "),
						label("@name"),
					],
					M.Row()[
						label("Unknown reward: "),
						label("@name"),
					],
				],
			],
		]
	),
	"button_editor_fragment.xml": (
		M.Panel(background="VANILLA", insets=10)[
			M.Column()[
				M.Row()[
					M.Hover(lines="Click to pick an icon from every item")[
						button("@pickIcon",
							M.ItemStack(value="@getItemIcon"),
							apex=False,
						),
					],
					label("Icon"),
					button("@pickIcon", "Pick icon…", apex=False),
				],
				M.Hover(lines="Put your display item in here.;Can be any SkyBlock item id.;;Alternatively you can paste in a /give command")[
					M.TextField(value="@icon", width=180),
				],
				M.Row()[
					M.Switch(value="@isGigantic"),
					label("Big Button"),
				],
				label("Command"),
				M.Hover(lines="Put the command in here.;The text box should not start with a /")[
					M.Row()[
						label("/"),
						M.TextField(value="@command", width=180),
					],
				],
				button("@delete", "Delete", apex=False),
			],
		]
	),
	"carnival/minesweeper_tutorial.xml": (
		M.Center()[
			M.Panel(background="VANILLA", insets=10)[
				M.Column()[
					M.Scale(scale=2)[
						label("§aFruit§7 Digging"),
					],
					label("The goal of the fruit digging minigame is to find as many§a fruits§r as possible on a §b7x7§e sand§r grid.", width=300),
					label("To do so, you break§e sand blocks§r to reveal what is hidden underneath: a §afruit§r, a §cbomb, or §erum§r.", width=300),
					label("When you break a block, you can also get some extra information based on your §7dowsing mode§r.", width=300),
					M.Scale(scale="1.5")[
						label("§7Dowsing Modes"),
					],
					M.Array(data="@modes")[
						M.Row()[
							M.ItemStack(value="@itemType"),
							label("@feature", width=80),
							label("@description", width=220),
						],
					],
					M.Scale(scale="1.5")[
						label("§aTiles"),
					],
					M.ScrollPanel(width=300, height=120)[
						M.Array(data="@pieces")[
							M.Row()[
								M.Center()[
									M.ItemStack(value="@getIcon"),
								],
								label("@pieceLabel", width=80),
								label("@description", width=145),
								M.Spacer(width=5),
								label("@boardLabel", width=50),
							],
						],
					],
				],
			],
		]
	),
	"config/category.xml": (
		M.Center()[
			M.Panel(background="VANILLA", insets=10)[
				M.Column()[
					M.Row()[
						M.Align(vertical="CENTER")[
							button("@close", "Back"),
						],
						M.Align(vertical="CENTER")[
							M.Row()[
								label(" "),
								label("@name"),
							],
						],
					],
					M.Spacer(height=5),
					M.ScrollPanel(width=400, height=300)[
						M.Array(data="@configs")[
							A.Fixed(width=380, height=30)[
								M.Panel(background="VANILLA")[
									M.Center()[
										M.Row()[
											M.Center()[
												label("@name"),
											],
											button("@openEditor", "Edit"),
										],
									],
								],
							],
						],
					],
				],
			],
		]
	),
	"config/customchatcommands/editor_wheel.xml": (
		M.Center()[
			M.Panel(background="VANILLA", insets=10)[
				M.Column()[
					M.Row()[
						button("@back", "←"),
						label("Editing radial menu (one chat command per slice)"),
					],
					M.Row()[
						label("Menu key (hold to open):"),
						M.Align(horizontal="RIGHT")[
							A.Fixed(width=160)[
								M.Indirect(value="@button"),
							],
						],
					],
					M.Row()[
						label("Menu Options:"),
						M.Align(horizontal="RIGHT")[
							button("@addOption", "+"),
						],
					],
					M.Array(data="@editableCommands")[
						M.Row()[
							label("/"),
							M.TextField(value="@text", width=160),
							M.Align(horizontal="RIGHT")[
								button("@delete", "Delete"),
							],
						],
					],
				],
			],
		]
	),
	"config/customchatcommands/index.xml": (
		M.Center()[
			M.Row()[
				M.Tabs()[
					M.Tab()[
						M.Tab.Header()[
							label("Custom Chat Commands"),
						],
						M.Tab.Body()[
							M.Column()[
								label("This could be potentially bannable, use at your own risk.", width=300),
								M.Fragment(value="apex:gui/config/customchatcommands/wheel.xml", bind="@wheels"),
							],
						],
					],
				],
				M.Meta(beforeClose="@beforeClose"),
			],
		]
	),
	"config/customchatcommands/wheel.xml": (
		M.Panel(background="TRANSPARENT", insets=10)[
			M.Column()[
				M.ScrollPanel(width=380, height=300)[
					M.Align(horizontal="CENTER")[
						M.Array(data="@wheels")[
							M.Panel(background="TRANSPARENT", insets=3)[
								M.Panel(background="VANILLA", insets=6)[
									M.Column()[
										M.Row()[
											label("@keyCombo", width=250),
											M.Align(horizontal="RIGHT")[
												M.Row()[
													button("@edit", "Edit"),
													M.Spacer(width=12),
													button("@delete", "Delete"),
												],
											],
										],
										M.Array(data="@commands")[
											label("@textR", width=280),
										],
									],
								],
							],
						],
					],
				],
				M.Align(horizontal="RIGHT")[
					M.Row()[
						button("@discard", "Discard Changes"),
						button("@saveAndClose", "Save & Close"),
						button("@save", "Save"),
						button("@addWheel", "Add Radial Menu"),
					],
				],
			],
		]
	),
	"config/main.xml": (
		M.Center()[
			M.Panel(background="VANILLA", insets=10)[
				M.Column()[
					label("SkyBlock Apex Config"),
					M.ScrollPanel(width=400, height=300)[
						M.Array(data="@categories")[
							A.Fixed(width=380, height=30)[
								M.Panel(background="VANILLA")[
									M.Center()[
										M.Row()[
											M.Center()[
												label("@name"),
											],
											button("@open", "Open"),
										],
									],
								],
							],
						],
					],
				],
			],
		]
	),
	"license_viewer/index.xml": (
		M.Center()[
			M.Panel(background="VANILLA")[
				M.Column()[
					M.Center()[
						M.Scale(scale=2)[
							label("SkyBlock Apex Licenses"),
						],
					],
					M.ScrollPanel(width=306, height=250)[
						M.Panel(insets=3, background="TRANSPARENT")[
							M.Array(data="@softwares")[
								M.Center()[
									A.Fixed(width=300)[
										M.Panel(background="VANILLA", insets=8)[
											M.Column()[
												M.Scale(scale="1.2")[
													label("@projectName"),
												],
												M.When(condition="@hasWebPresence")[
													M.Row()[
														button("@open", "Navigate to WebSite"),
													],
													M.Spacer(),
												],
												label("@projectDescription", width=280),
												M.Array(data="@developers")[
													M.Row()[
														label("by "),
														label("@name"),
													],
												],
												M.Array(data="@licenses")[
													M.When(condition="@hasUrl")[
														button("@open",
															M.Center()[
																M.Row()[
																	label("License: "),
																	label("@name"),
																],
															],
														),
														M.Row()[
															label("License: "),
															label("@name"),
														],
													],
												],
											],
										],
									],
								],
							],
						],
					],
				],
			],
		]
	),
	"npc_waypoints.xml": (
		M.Center()[
			M.Panel(background="VANILLA", insets=5)[
				M.Column()[
					M.Center()[
						M.TextField(value="@search", width=200),
					],
					A.Tick(tick="@tick"),
					M.Spacer(height=5),
					M.Panel(background="TRANSPARENT", insets=4)[
						M.ScrollPanel(width=200, height=300)[
							M.Array(data="@results")[
								M.Row()[
									label("@name", width=180),
									A.Hover(lines="Click to set this waypoint as your destination;Shift-Click to warp to the nearest warp point and set this as your destination")[
										A.Button(onClick="@click", noBackground="true")[
											M.When(condition="@isSelected")[
												A.Image(resource="apex:textures/gui/waypoint_selected.png", width=16, height=16),
												A.Image(resource="apex:textures/gui/waypoint_unselected.png", width=16, height=16),
											],
										],
									],
								],
							],
						],
					],
				],
			],
		]
	),
	"pristine_profit.xml": (
		M.Column()[
			M.Row()[
				label("@moneyText", width=300),
			],
			M.Row()[
				label("@collectionText", width=300),
			],
		]
	),
	"repo_reload.xml": (
		M.Row()[
			A.Bar(progress="@current", total="@max", emptyColor="#30ff30", fillColor="#30aa30"),
			label("@label"),
		]
	),
}
