package com.example.materialtransform

/**
 * Each material option: a Persian display label plus the English prompt fragment
 * used to instruct the image-generation model (English prompts currently give the
 * most reliable, high-fidelity results with Gemini's image model).
 */
enum class MaterialType(
    val displayNameFa: String,
    val emoji: String,
    private val promptFragment: String
) {
    CANDLE(
        displayNameFa = "شمع",
        emoji = "🕯️",
        promptFragment = "a realistic wax candle sculpture. Give it a smooth, semi-translucent " +
            "candle-wax texture in a warm ivory or matching color tone, with a visible wick where " +
            "appropriate (e.g. on top of the head), subtle drips of melted wax along edges, and soft " +
            "warm studio lighting as if it were a decorative candle art piece"
    ),
    STONE_STATUE(
        displayNameFa = "مجسمه سنگی",
        emoji = "🗿",
        promptFragment = "a carved stone/marble statue. Give it a realistic weathered or polished " +
            "stone/marble texture with natural veining, a monochrome stone color palette (gray, beige, " +
            "or white marble), and classical sculptural lighting, as if it were a museum-quality marble " +
            "or granite sculpture"
    ),
    WOOD_STATUE(
        displayNameFa = "مجسمه چوبی",
        emoji = "🪵",
        promptFragment = "a hand-carved wooden statue. Give it a realistic wood grain texture, warm " +
            "brown wood tones, visible carving/chisel marks, and a natural or lightly varnished wooden " +
            "finish, as if it were an artisan hand-carved wood sculpture"
    ),
    ACTION_FIGURE(
        displayNameFa = "اکشن فیگور",
        emoji = "🤖",
        promptFragment = "a collectible plastic action figure toy. Give it visible joint segments, " +
            "glossy or matte injection-molded plastic texture, vivid toy-like colors, and place it " +
            "standing on a small round display stand/base, photographed like a professional product " +
            "shot of a poseable collectible action figure, still inside a blister-pack style studio setting"
    ),
    DECORATIVE_STATUE(
        displayNameFa = "مجسمه دکوری",
        emoji = "🏺",
        promptFragment = "a decorative ceramic or resin figurine statue. Give it a smooth painted " +
            "ceramic/resin finish, soft glossy highlights, and elegant home-decor styling, as if it " +
            "were a decorative collectible figurine displayed on a shelf"
    );

    /** Builds the final instruction sent to the image model. */
    fun buildPrompt(): String =
        "Transform the main subject in this photo into $promptFragment. " +
            "Keep the subject's pose, proportions, facial features/shape and overall composition " +
            "clearly recognizable and unchanged, only change the material/texture/appearance as " +
            "described. Keep a clean, simple, softly blurred or studio-style background that " +
            "complements the new material. Output a single, photorealistic, high-detail image."

    /**
     * The exact label string expected by the "material_label" parameter of
     * our Hugging Face Space's /transform function. This MUST match the
     * choices defined in the Space's app.py (gr.Radio choices), including
     * spacing and emoji, e.g. "شمع 🕯️".
     */
    fun gradioLabel(): String = "$displayNameFa $emoji"
}
