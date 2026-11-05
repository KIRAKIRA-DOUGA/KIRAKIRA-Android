package moe.kirakira.ui.components

import android.annotation.SuppressLint
import android.content.res.Resources
import androidx.annotation.DrawableRes
import org.xmlpull.v1.XmlPullParser

/** Reads every VectorDrawable path in source order; callers own transforms and contour semantics. */
@SuppressLint("ResourceType")
internal fun readVectorPathData(resources: Resources, @DrawableRes resource: Int): List<String> =
    resources.getXml(resource).use { parser ->
        buildList {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {
                    add(checkNotNull(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData")))
                }
                parser.next()
            }
        }
    }

private val contourPattern = Regex("M[^M]+")

/** These source icons use absolute M commands for their independently animated contours. */
internal fun vectorPathContours(data: String): List<String> = contourPattern.findAll(data).map { it.value }.toList()
