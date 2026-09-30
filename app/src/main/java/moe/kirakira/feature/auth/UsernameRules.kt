package moe.kirakira.feature.auth

// Character policy from Cerasus assets/pomsky/username.pom; its inputs allow 20 UTF-16 code units.
private const val EXTRA_NAME_CHARACTERS = "-_〇﨎﨏﨑﨓﨔﨟﨡﨣﨤﨧﨨﨩ー " +
    "ÀÁÃẠẢĂẮẰẲẴẶÂẤẦẨẪẬÈÉẸẺẼÊỀẾỂỄỆĐÌÍĨỈỊÒÓÕỌỎÔỐỒỔỖỘƠỚỜỞỠỢÙÚŨỤỦƯỨỪỬỮỰỲỴỶỸÝ" +
    "àáãạảăắằẳẵặâấầẩẫậèéẹẻẽêềếểễệđìíĩỉịòóõọỏôốồổỗộơớờởỡợùúũụủưứừửữựỳỵỷỹý"

private val excludedNameCodePoints =
    "㬵䶺𦢫胐脁䑃䶻䶼𫜶䶽𦛩䐠䏓肦胊𱼋脧膧𦙿𣍷𦝲𦠈𦠅㬺𱢹䎛𠑗𤈎𧺯𠓲𤦼𣟃𦡦𤯒".codePoints().toArray().toSet()

internal fun isValidAuthName(value: String): Boolean {
    if (value.isBlank() || value.length > 20 || value != value.trim() || "  " in value) return false
    return value.codePoints().toArray().all { code ->
        code !in excludedNameCodePoints && (
            code in 65..90 || code in 97..122 || code in 48..57 ||
                code in 0x3041..0x3096 || code in 0x30A1..0x30FA || code in 0xAC00..0xD7A3 ||
                code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF || code in 0x20000..0x2EE5D ||
                code in 0x30000..0x323AF || (code <= 0xFFFF && code.toChar() in EXTRA_NAME_CHARACTERS)
            )
    }
}
