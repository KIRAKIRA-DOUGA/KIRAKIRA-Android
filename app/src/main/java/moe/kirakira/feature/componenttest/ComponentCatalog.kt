package moe.kirakira.feature.componenttest

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import moe.kirakira.R

// Preserve the old category values so Navigation 3 can decode a saved ComponentSamplesRoute.
@Serializable
internal enum class ComponentCategory { BUTTONS, CONTROLS, CONTENT, LISTS, OVERLAYS }

internal enum class GalleryGroup(@param:StringRes val title: Int) {
    BUTTONS(R.string.component_gallery_group_buttons),
    CONTROLS(R.string.component_gallery_group_controls),
    CONTENT(R.string.component_gallery_group_content),
    LISTS(R.string.component_gallery_group_lists),
    OVERLAYS(R.string.component_gallery_group_overlays),
}

/** Entry names are stable IDs for navigation and grid state. */
@Serializable
internal enum class GalleryComponent(val group: GalleryGroup, @param:StringRes val title: Int) {
    TEXT_BUTTON(GalleryGroup.BUTTONS, R.string.component_gallery_text_button),
    ICON_BUTTON(GalleryGroup.BUTTONS, R.string.component_gallery_icon_button),
    FAB(GalleryGroup.BUTTONS, R.string.component_gallery_fab),
    TOGGLE_BUTTON(GalleryGroup.BUTTONS, R.string.component_gallery_toggle_button),
    FOLLOW_BUTTON(GalleryGroup.BUTTONS, R.string.component_gallery_follow_button),

    SEGMENTED(GalleryGroup.CONTROLS, R.string.component_gallery_segmented),
    SWITCH(GalleryGroup.CONTROLS, R.string.component_gallery_switch),
    CHECKBOX(GalleryGroup.CONTROLS, R.string.component_gallery_checkbox),
    RADIO(GalleryGroup.CONTROLS, R.string.component_gallery_radio),
    SLIDER(GalleryGroup.CONTROLS, R.string.component_gallery_slider),
    TEXT_FIELD(GalleryGroup.CONTROLS, R.string.component_gallery_text_field),
    PASSWORD(GalleryGroup.CONTROLS, R.string.component_gallery_password),
    OTP(GalleryGroup.CONTROLS, R.string.component_gallery_otp),
    COLOR_PICKER(GalleryGroup.CONTROLS, R.string.component_gallery_color_picker),

    PROGRESS(GalleryGroup.CONTENT, R.string.component_gallery_progress),
    CONTENT_STATE(GalleryGroup.CONTENT, R.string.component_gallery_content_state),
    NUMBER(GalleryGroup.CONTENT, R.string.component_gallery_number),
    EXPANDABLE_TEXT(GalleryGroup.CONTENT, R.string.component_gallery_expandable_text),
    AVATAR(GalleryGroup.CONTENT, R.string.component_gallery_avatar),
    BADGE(GalleryGroup.CONTENT, R.string.component_gallery_badge),

    CONNECTED_LIST(GalleryGroup.LISTS, R.string.component_gallery_connected_list),
    CHIP(GalleryGroup.LISTS, R.string.component_gallery_chip),
    PAGER_TABS(GalleryGroup.LISTS, R.string.component_gallery_pager_tabs),

    DIALOG(GalleryGroup.OVERLAYS, R.string.component_gallery_dialog),
    MENU(GalleryGroup.OVERLAYS, R.string.component_gallery_menu),
    SHEET(GalleryGroup.OVERLAYS, R.string.component_gallery_sheet),
    SNACKBAR(GalleryGroup.OVERLAYS, R.string.component_gallery_snackbar),
}

internal val galleryComponentsByGroup = GalleryComponent.entries.groupBy { it.group }
