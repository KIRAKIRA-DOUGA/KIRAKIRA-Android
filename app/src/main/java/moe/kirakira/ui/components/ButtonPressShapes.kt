package moe.kirakira.ui.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
internal fun pressToRoundButtonShapes(buttonHeight: Dp = ButtonDefaults.MinHeight): ButtonShapes {
    val shapes = ButtonDefaults.shapesFor(buttonHeight)
    return shapes.copy(shape = shapes.pressedShape, pressedShape = shapes.shape)
}

@Composable
internal fun pressToRoundToggleButtonShapes(buttonSize: ToggleButtonSize): ToggleButtonShapes {
    val shapes = ToggleButtonDefaults.shapesFor(buttonSize)
    return shapes.copy(shape = shapes.pressedShape, pressedShape = shapes.shape)
}
