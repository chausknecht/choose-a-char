package com.nundp.fritz2.chooseachar

import dev.fritz2.binding.*
import dev.fritz2.dom.html.*
import dev.fritz2.dom.mount
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun initialCharacterFactory(size: Int) =
    CharacterFactory(
        allNames().zip(allItems()).map { (name, product) -> Character(name, product) }.take(size).toList(),
        true
    )

object ProposalStore : RootStore<CharacterFactory>(initialCharacterFactory(10)) {

    val append =
        handle<Character> { factory, character ->
            if (factory.producing) {
                factory.copy(
                    characters = listOf(character) + factory.characters.dropLast(1)
                )
            } else
                factory
        }

    val toggleProduce = handle { factory -> factory.copy(producing = !factory.producing) }

    val markAsFavorite = handleAndOffer<String, Character> { factory, character ->
        val person = factory.characters.find { it.race == character }
        if (person != null) offer(person)
        factory
    }
}

object FavoriteCharacterStore : RootStore<List<Character>>(listOf()) {

    val append = handleAndOffer<Character, Character> { characters, character ->
        if (!characters.contains(character)) {
            offer(character)
            characters + character
        } else
            characters
    }

    val up = handle<String> { characters, id ->
        val splitPosition = characters.indexOfFirst { it.race == id }
        if (splitPosition > 0) {
            val front = characters.subList(0, splitPosition - 1)
            val tail = characters.subList(splitPosition + 1, characters.size)
            front + listOf(characters[splitPosition], characters[splitPosition - 1]) + tail
        } else
            characters
    }

    val down = handle<String> { characters, id ->
        val splitPosition = characters.indexOfFirst { it.race == id }
        if (splitPosition < characters.size) {
            val front = characters.subList(0, splitPosition)
            val tail = characters.subList(splitPosition + 2, characters.size)
            front + listOf(characters[splitPosition + 1], characters[splitPosition]) + tail
        } else
            characters
    }

    val delete = handle<String> { characters, id -> characters.filter { it.race != id } }

    val count = data.map { it.count() }.distinctUntilChanged()

    fun upable(character: Character) = data.map { it.indexOf(character) > 0 }
    fun downable(character: Character) = data.map { it.last() != character }
    fun ranked(character: Character) = data.map { it.indexOf(character) + 1 }
}


object NewFavoriteCharacterStore : RootStore<Character>(Character("", "")) {

    fun isNew(character: Character) = data.map {
        it == character
    }
}

enum class SplitMode {
    ALL, RACES, ITEMS
}

object SplittingStore : RootStore<SplitMode>(SplitMode.ALL)

class FavoriteStore(initialValue: List<String>) : RootStore<List<String>>(initialValue) {

    fun append(filter: (SplitMode) -> Boolean): Handler<Pair<SplitMode, String>> = handle { favorites, (mode, value) ->
        console.log(mode, value)
        if (!favorites.contains(value) && filter(mode))
            favorites + value
        else
            favorites
    }

    val delete: Handler<String> = handle { favorites, favorite -> favorites.filter { it != favorite } }

    val count = data.map { it.count() }.distinctUntilChanged()
}

fun HtmlElements.favoriteItems(store: FavoriteStore, title: String): Div {
    fun HtmlElements.favoriteItem(item: String, store: FavoriteStore): Tr {
        return tr {
            td {
                text(item)
            }
            td("text-right") {
                button("btn tooltip") {
                    attr("data-tooltip", "drop from favorites")
                    i("icon icon-delete") { }
                    clicks.events.map { item } handledBy store.delete
                }
            }
        }
    }

    return div {
        store.count.map {
            h3("text-center") {
                text(
                    when (it) {
                        1 -> "$it $title"
                        else -> "$it ${title}s"
                    }
                )
            }
            table("table table-striped table-hover") {
                tbody {
                    store.data.each().render { favoriteItem(it, store) }.bind()
                }
            }
        }.bind()
    }
}

@ExperimentalCoroutinesApi
fun HtmlElements.favoriteCharacter(
    character: Character,
    favoriteRaceStore: FavoriteStore,
    favoriteItemStore: FavoriteStore
): Tr {
    return tr {
        td {
            span { FavoriteCharacterStore.ranked(character).map { "$it" }.bind() }
            span {
                attr("style", "margin-left: 0.5em;")
                className =
                    NewFavoriteCharacterStore.isNew(character)
                        .map { if (it) "label label-rounded label-primary" else "" }
                NewFavoriteCharacterStore.isNew(character).map { if (it) "new" else "" }.bind()
            }
        }
        td { text(character.race) }
        td { text(character.item) }
        td {
            button("btn tooltip") {
                SplittingStore.data.map {
                    when (it) {
                        SplitMode.ALL -> "Add race and item as favorite"
                        SplitMode.RACES -> "add race as favorite"
                        SplitMode.ITEMS -> "add item as favorite"
                    }
                }.bindAttr("data-tooltip")
                i("icon icon-plus") { }
                SplittingStore.data.flatMapLatest { x -> clicks.events.map { x } }
                    .map {
                        Pair(it, character.race)
                    } handledBy favoriteRaceStore.append { it == SplitMode.ALL || it == SplitMode.RACES }
                SplittingStore.data.flatMapLatest { x -> clicks.events.map { x } }
                    .map {
                        Pair(it, character.item)
                    } handledBy favoriteItemStore.append { it == SplitMode.ALL || it == SplitMode.ITEMS }
            }
            button("btn tooltip") {
                className = FavoriteCharacterStore.upable(character).map { if (it) "" else "d-invisible" }
                attr("data-tooltip", "move up")
                i("icon icon-arrow-up") { }
                clicks.events.map { character.race } handledBy FavoriteCharacterStore.up
            }
            button("btn tooltip") {
                className = FavoriteCharacterStore.downable(character).map { if (it) "" else "d-invisible" }
                attr("data-tooltip", "move down")
                i("icon icon-arrow-down") { }
                clicks.events.map { character.race } handledBy FavoriteCharacterStore.down
            }
            button("btn tooltip") {
                attr("data-tooltip", "drop from favorites")
                i("icon icon-delete") { }
                clicks.events.map { character.race } handledBy FavoriteCharacterStore.delete
            }
        }
    }
}

@ExperimentalCoroutinesApi
fun HtmlElements.favoriteCharacters(favoriteNameStore: FavoriteStore, favoriteProductStore: FavoriteStore) {
    div {
        attr("style", "margin-bottom: 2em")
        h2("text-center") {
            //text("${PersonStore.count} Person${if (PersonStore.count > 1) "en" else ""}")
            FavoriteCharacterStore.count.map { "$it Favorite${if (it != 1) "s" else ""}" }.bind()
        }
        table("table table-striped table-hover") {
            thead {
                tr {
                    th { text("Rank") }
                    th { text("Race") }
                    th { text("Item") }
                    th { text("") }
                }
            }
            tbody {
                FavoriteCharacterStore.data.each()
                    .render { favoriteCharacter(it, favoriteNameStore, favoriteProductStore) }
                    .bind()
            }
        }
    }
}

fun HtmlElements.proposal(p: Character): Tr {
    return tr {
        td { text(p.race) }
        td { text(p.item) }
        td {
            button("btn tooltip") {
                attr("data-tooltip", "add to favorites")
                i("icon icon-plus") { }
                clicks.events.map { p.race } handledBy ProposalStore.markAsFavorite
            }
        }
    }
}

fun HtmlElements.proposals() {
    val persons = ProposalStore.sub(L.CharacterFactory.characters)
    val producing = ProposalStore.sub(L.CharacterFactory.producing)

    div {
        div("container columns") {
            div("column col-1") {
                className = producing.data.map { if (it) "loading loading-lg" else "" }
            }
            div("column col-11 text-center") {
                h2 {
                    text("Proposals")
                }
            }
        }
        table("table table-striped table-hover") {
            thead {
                tr {
                    th { text("Race") }
                    th { text("Item") }
                    th {
                        button("btn tooltip") {
                            producing.data.map {
                                if (it) {
                                    attr("data-tooltip", "pause")
                                    i("icon icon-shutdown") {}
                                } else {
                                    attr("data-tooltip", "make proposals")
                                    i("icon icon-download") {}
                                }
                            }.bind()
                            clicks.events.map { Unit } handledBy ProposalStore.toggleProduce
                        }
                    }
                }
            }
            tbody {
                persons.data.each().render { proposal(it) }.bind()
            }
        }
    }
}

@ExperimentalCoroutinesApi
fun main() {
    NewFavoriteCharacterStore.data.watch()
    SplittingStore.data.watch()

    ProposalStore.markAsFavorite handledBy FavoriteCharacterStore.append
    FavoriteCharacterStore.append.map { it } handledBy NewFavoriteCharacterStore.update

    val favoriteNameStore = FavoriteStore(listOf())
    val favoriteProductStore = FavoriteStore(listOf())

    allNames().zip(allItems())
        .asFlow()
        .map { (name, product) -> Character(name, product) }
        .onEach { delay(2000) } handledBy ProposalStore.append

    render {
        div("container columns") {
            h1("column col-11 text-center") { text("Choose a Char!") }
            div("container columns column col-5 p-centered") {
                proposals()
            }
            div("divider-vert columns col-1") {}
            div("container columns column col-6 p-centered") {
                favoriteCharacters(favoriteNameStore, favoriteProductStore)
                div("container columns form-group bg-gray") {
                    div("column col-5 text-right") {
                        h4 { text("split into...") }
                    }
                    div("column col-7 text-left") {
                        label("form-radio form-inline") {
                            input {
                                type = const("radio")
                                name = const("components")
                                checked = SplittingStore.data.map { it == SplitMode.ALL }
                            }
                            i("form-icon") {}
                            text("Both")
                            inputs.events.map { SplitMode.ALL } handledBy SplittingStore.update
                        }
                        label("form-radio form-inline") {
                            input {
                                type = const("radio")
                                name = const("components")
                                checked = SplittingStore.data.map { it == SplitMode.RACES }
                            }
                            i("form-icon") {}
                            text("Race")
                            inputs.events.map { SplitMode.RACES } handledBy SplittingStore.update
                        }
                        label("form-radio form-inline") {
                            input {
                                type = const("radio")
                                name = const("components")
                                checked = SplittingStore.data.map { it == SplitMode.ITEMS }
                            }
                            i("form-icon") {}
                            text("Item")
                            inputs.events.map { SplitMode.ITEMS } handledBy SplittingStore.update
                        }
                    }
                }
                div("container columns") {
                    div("column col-5") {
                        favoriteNameStore.count.map {
                            if (it > 0) {
                                favoriteItems(favoriteNameStore, "best race")
                            } else {
                                div { comment("leer") }
                            }
                        }.bind()
                    }
                    div("divider-vert column col-2") {}
                    div("column col-5") {
                        favoriteProductStore.count.map {
                            if (it > 0) {
                                favoriteItems(favoriteProductStore, "best item")
                            } else {
                                div { comment("empty") }
                            }
                        }.bind()
                    }
                }
            }
        }
    }.mount("target")

}