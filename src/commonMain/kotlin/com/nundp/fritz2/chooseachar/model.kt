package com.nundp.fritz2.chooseachar

import dev.fritz2.lenses.Lenses

@Lenses
data class Character(val race: String, val item: String)

@Lenses
data class CharacterFactory(val characters: List<Character>, val producing: Boolean)
