package com.comet.pet

import java.util.regex.Pattern

data class ClipboardReaction(
    val state: PetState,
    val phrase: String
)

object ClipboardBrain {

    private val URL_PATTERN = Pattern.compile(
        "^(https?://|www\\.)[a-zA-Z0-9\\-_]+(\\.[a-zA-Z0-9\\-_]+)+.*",
        Pattern.CASE_INSENSITIVE
    )

    private val NUMBER_PATTERN = Pattern.compile("^\\+?[0-9\\s\\-.,()]{3,20}$")

    private val CODE_KEYWORDS = setOf(
        "function", "def ", "class ", "val ", "var ", "fun ", "import ",
        "return ", "const ", "let ", "public ", "private ", "if (", "for ("
    )

    private val PHRASES_LINK = listOf(
        "О, ссылка! Куда полетим?",
        "Надеюсь, там есть вкусная рыбка!",
        "Любопытная ссылка... Кликнуть или не стоит?",
        "Ух ты, интернет-путешествия!"
    )

    private val PHRASES_NUMBER = listOf(
        "Ого, циферки! Считаем запасы печенек?",
        "Столько вкусняшек мне точно не съесть!",
        "Это секретный код от холодильника?",
        "Один, два, три, четыре, пять... лисичка пошла считать!"
    )

    private val PHRASES_CODE = listOf(
        "Хвостом чую — тут нужен рефакторинг!",
        "Код? Главное, чтобы багов не было!",
        "Меньше багов, больше обнимашек!",
        "Я тоже программирую, но пока только лапками!"
    )

    private val PHRASES_LONG = listOf(
        "Ого, целая поэма! Я устала читать...",
        "Много букв! Перескажи лисичке в двух словах?",
        "Мурр... давай лучше поиграем, хватит читать!",
        "Какая длинная мысль... Ты точно всё запомнил?"
    )

    private val PHRASES_DEFAULT = listOf(
        "Я запомнила!",
        "Фыр-фыр, скопировано!",
        "Интересненько...",
        "Ты лучший хозяин на свете!",
        "Мурр-мяу!"
    )

    fun analyze(rawText: String?): ClipboardReaction {
        val text = rawText?.trim() ?: return ClipboardReaction(PetState.IDLE, "Пусто в буфере!")

        if (URL_PATTERN.matcher(text).matches()) {
            return ClipboardReaction(PetState.REACT_LINK, PHRASES_LINK.random())
        }

        if (NUMBER_PATTERN.matcher(text).matches()) {
            return ClipboardReaction(PetState.REACT_NUMBER, PHRASES_NUMBER.random())
        }

        val hasCodeSigns = CODE_KEYWORDS.any { text.contains(it, ignoreCase = false) } ||
                (text.contains("{") && text.contains("}")) ||
                (text.contains(";") && text.contains("\n"))

        if (hasCodeSigns) {
            return ClipboardReaction(PetState.REACT_CODE, PHRASES_CODE.random())
        }

        if (text.length > 100) {
            return ClipboardReaction(PetState.REACT_LONGTEXT, PHRASES_LONG.random())
        }

        return ClipboardReaction(PetState.IDLE, PHRASES_DEFAULT.random())
    }
}
