package moe.kirakira.feature.main

import android.graphics.Path as AndroidPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.core.graphics.PathParser

/** Matching Material Symbols path commands; see third_party/material-symbols/README.md. */
internal class TabIconMorph(val outline: String, val filled: String)

/** Each icon owns its mutable drawing buffers. Paths are parsed once, never during a frame. */
internal class TabIconMorphPath(morph: TabIconMorph) {
    private val outline = PathParser.createNodesFromPathData(morph.outline)
    private val filled = PathParser.createNodesFromPathData(morph.filled)
    private val interpolated = PathParser.deepCopyNodes(outline)
    private val androidPath = AndroidPath()
    private val path = androidPath.asComposePath()

    init {
        check(PathParser.canMorph(outline, filled))
    }

    fun pathAt(progress: Float): Path {
        PathParser.interpolatePathDataNodes(interpolated, progress.coerceIn(0f, 1f), outline, filled)
        androidPath.rewind()
        PathParser.nodesToPath(interpolated, androidPath)
        return path
    }
}

// Official FILL 0 / FILL 1 geometry, with zero-area contours normalized for interpolation.
// Home's inner contour collapses to (480, 490); its outer contour opens to the filled doorway.
// Person's body and head holes collapse to (480, 660) and (480, 320), respectively.
internal val homeIconMorph = TabIconMorph(
    outline = "M240,760L360,760L360,560Q360,543 371.5,531.5Q383,520 400,520L560,520Q577,520 588.5,531.5" +
        "Q600,543 600,560L600,760L720,760L720,400Q720,400 720,400Q720,400 720,400L480,220Q480,220 480,220" +
        "Q480,220 480,220L240,400Q240,400 240,400Q240,400 240,400L240,760ZM160,760L160,400" +
        "Q160,381 168.5,364Q177,347 192,336L432,156Q453,140 480,140Q507,140 528,156L768,336" +
        "Q783,347 791.5,364Q800,381 800,400L800,760Q800,793 776.5,816.5Q753,840 720,840L560,840" +
        "Q543,840 531.5,828.5Q520,817 520,800L520,600Q520,600 520,600Q520,600 520,600L440,600" +
        "Q440,600 440,600Q440,600 440,600L440,800Q440,817 428.5,828.5Q417,840 400,840L240,840" +
        "Q207,840 183.5,816.5Q160,793 160,760Z",
    filled = "M480,490L480,490L480,490Q480,490,480,490Q480,490,480,490L480,490Q480,490,480,490Q480,490,480,490" +
        "L480,490L480,490L480,490Q480,490,480,490Q480,490,480,490L480,490Q480,490,480,490Q480,490,480,490" +
        "L480,490Q480,490,480,490Q480,490,480,490L480,490ZM160,760L160,400Q160,381 168.5,364" +
        "Q177,347 192,336L432,156Q453,140 480,140Q507,140 528,156L768,336Q783,347 791.5,364" +
        "Q800,381 800,400L800,760Q800,793 776.5,816.5Q753,840 720,840L600,840Q583,840 571.5,828.5" +
        "Q560,817 560,800L560,600Q560,583 548.5,571.5Q537,560 520,560L440,560Q423,560 411.5,571.5" +
        "Q400,583 400,600L400,800Q400,817 388.5,828.5Q377,840 360,840L240,840Q207,840 183.5,816.5" +
        "Q160,793 160,760Z",
)

internal val personIconMorph = TabIconMorph(
    outline = "M480,480Q414,480 367,433Q320,386 320,320Q320,254 367,207Q414,160 480,160Q546,160 593,207" +
        "Q640,254 640,320Q640,386 593,433Q546,480 480,480ZM160,720L160,688Q160,654 177.5,625.5" +
        "Q195,597 224,582Q286,551 350,535.5Q414,520 480,520Q546,520 610,535.5Q674,551 736,582" +
        "Q765,597 782.5,625.5Q800,654 800,688L800,720Q800,753 776.5,776.5Q753,800 720,800L240,800" +
        "Q207,800 183.5,776.5Q160,753 160,720ZM240,720L720,720L720,688Q720,677 714.5,668Q709,659 700,654" +
        "Q646,627 591,613.5Q536,600 480,600Q424,600 369,613.5Q314,627 260,654Q251,659 245.5,668" +
        "Q240,677 240,688L240,720ZM480,400Q513,400 536.5,376.5Q560,353 560,320Q560,287 536.5,263.5" +
        "Q513,240 480,240Q447,240 423.5,263.5Q400,287 400,320Q400,353 423.5,376.5Q447,400 480,400Z",
    filled = "M480,480Q414,480 367,433Q320,386 320,320Q320,254 367,207Q414,160 480,160Q546,160 593,207" +
        "Q640,254 640,320Q640,386 593,433Q546,480 480,480ZM160,720L160,688Q160,654 177.5,625.5" +
        "Q195,597 224,582Q286,551 350,535.5Q414,520 480,520Q546,520 610,535.5Q674,551 736,582" +
        "Q765,597 782.5,625.5Q800,654 800,688L800,720Q800,753 776.5,776.5Q753,800 720,800L240,800" +
        "Q207,800 183.5,776.5Q160,753 160,720ZM480,660L480,660L480,660Q480,660,480,660Q480,660,480,660" +
        "Q480,660,480,660Q480,660,480,660Q480,660,480,660Q480,660,480,660Q480,660,480,660Q480,660,480,660" +
        "L480,660ZM480,320Q480,320,480,320Q480,320,480,320Q480,320,480,320Q480,320,480,320" +
        "Q480,320,480,320Q480,320,480,320Q480,320,480,320Q480,320,480,320Z",
)
