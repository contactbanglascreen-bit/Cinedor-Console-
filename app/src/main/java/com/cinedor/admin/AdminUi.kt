package com.cinedor.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Frosted glass card used across the admin app (light edge on top, soft see-through fill). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

/** Soft colored light behind the whole app. */
fun Modifier.auroraBackground(): Modifier = this
    .background(AdminColors.Bg)
    .drawBehind {
        fun glow(c: Color, x: Float, y: Float, r: Float) = drawCircle(
            Brush.radialGradient(listOf(c, Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y), radius = size.minDimension * r),
            radius = size.minDimension * r,
            center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)
        )
        glow(Color(0x55FF2E55), 0.05f, 0.02f, 0.95f)
        glow(Color(0x447C3AED), 0.98f, 0.12f, 0.9f)
        glow(Color(0x2A2563EB), 0.2f, 0.85f, 1.0f)
    }

@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(title, color = AdminColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, letterSpacing = 0.2.sp)
        if (subtitle != null) Text(subtitle, color = AdminColors.Muted, fontSize = 12.5.sp)
    }
}

/** App-icon style badge: glossy gradient squircle with a white icon (like iOS / One UI icons). */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = AdminColors.RedBright, size: Dp = 40.dp) {
    val shape = RoundedCornerShape(size * 0.3f)
    val dark = Color(
        red = tint.red * 0.55f,
        green = tint.green * 0.55f,
        blue = tint.blue * 0.55f,
        alpha = 1f
    )
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint, dark))),
        contentAlignment = Alignment.Center
    ) {
        // glossy top highlight
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Transparent)))
        )
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.54f))
    }
}

@Composable
fun StatTile(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, padding = 14.dp) {
        IconBadge(icon, tint, 36.dp)
        Spacer(Modifier.height(12.dp))
        Text(value, color = AdminColors.Text, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = AdminColors.Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun StatusChip(on: Boolean, onText: String = "ON", offText: String = "OFF") {
    val c = if (on) AdminColors.Green else AdminColors.Muted
    Row(
        Modifier
            .clip(CircleShape)
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(5.dp))
        Text(if (on) onText else offText, color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, busy: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = AdminColors.Red, spotColor = AdminColors.Red)
            .clip(RoundedCornerShape(18.dp))
            .background(AdminColors.redBrush)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        else Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, AdminColors.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = AdminColors.Text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = AdminColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun adminFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AdminColors.Red,
    unfocusedBorderColor = AdminColors.Line,
    focusedContainerColor = AdminColors.Field,
    unfocusedContainerColor = AdminColors.Field,
    focusedLabelColor = AdminColors.RedBright,
    unfocusedLabelColor = AdminColors.Muted,
    cursorColor = AdminColors.Red,
    focusedTextColor = AdminColors.Text,
    unfocusedTextColor = AdminColors.Text
)

@Composable
fun AdminField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
    password: Boolean = false,
    number: Boolean = false,
    leading: ImageVector? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        leadingIcon = if (leading != null) { { Icon(leading, contentDescription = null, tint = AdminColors.Muted) } } else null,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Number
                else -> KeyboardType.Text
            }
        ),
        colors = adminFieldColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    )
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AdminColors.Text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = AdminColors.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AdminColors.Red,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = AdminColors.Field,
                uncheckedBorderColor = AdminColors.Line
            )
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else AdminColors.Text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) AdminColors.redBrush else Brush.linearGradient(listOf(AdminColors.Field, AdminColors.Field)))
            .border(1.dp, if (selected) Color.Transparent else AdminColors.Line, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}

@Composable
fun SpacedRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { content() }
}
