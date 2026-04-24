package com.banking.statement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.LocalStrings
import com.banking.statement.ui.components.EyebrowLabel
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing
import com.banking.statement.ui.theme.ThemeMode

// ============================================================
// Settings cards shown on the Settings tab, restyled to match
// the MoneyLupe MLSettingsScreen mockup — white surface,
// 16dp radius, xs shadow, eyebrow section titles.
// ============================================================

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)) {
        EyebrowLabel(
            text = title,
            modifier = Modifier.padding(start = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
                .clip(RoundedCornerShape(AppRadii.lg))
                .background(AppColors.CardBackground)
                .padding(AppSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
        ) {
            content()
        }
    }
}

@Composable
internal fun ThemeSettingsCard(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val strings = LocalStrings.current

    SettingsGroup(title = strings.settings) {
        Text(
            text = strings.theme,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)
        ) {
            ThemeOption(
                label = strings.themeLight,
                selected = currentThemeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                label = strings.themeDark,
                selected = currentThemeMode == ThemeMode.DARK || currentThemeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeChange(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) AppColors.Primary else AppColors.SurfaceTint
    val fg = if (selected) AppColors.ButtonPrimaryText else AppColors.TextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.sm))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = AppSpacing.s2)
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
internal fun SecuritySettingsCard(
    biometricLockEnabled: Boolean,
    onBiometricLockChange: (Boolean) -> Unit
) {
    val strings = LocalStrings.current
    SettingsGroup(title = strings.security) {
        SettingRow(
            title = strings.biometricLock,
            subtitle = strings.biometricLockDescription,
            trailing = {
                Switch(
                    checked = biometricLockEnabled,
                    onCheckedChange = onBiometricLockChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = AppColors.Primary,
                        uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                        uncheckedTrackColor = AppColors.Disabled
                    )
                )
            }
        )
    }
}

@Composable
internal fun ContactSettingsCard(
    onEmailClick: (String) -> Unit
) {
    val strings = LocalStrings.current
    SettingsGroup(title = strings.contactTitle) {
        Text(
            text = strings.contactHint,
            fontSize = 13.sp,
            color = AppColors.TextSecondary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadii.sm))
                .background(AppColors.SurfaceTint)
                .clickable { onEmailClick(strings.contactEmail) }
                .padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s3),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.contactEmail,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.Primary
            )
        }
    }
}

@Composable
internal fun ReminderSettingsCard(
    remindersEnabled: Boolean,
    onRemindersEnabledChange: (Boolean) -> Unit
) {
    val strings = LocalStrings.current
    SettingsGroup(title = strings.reminders) {
        SettingRow(
            title = strings.remindersEnabled,
            subtitle = strings.remindersDescription,
            trailing = {
                Switch(
                    checked = remindersEnabled,
                    onCheckedChange = onRemindersEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = AppColors.Primary,
                        uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                        uncheckedTrackColor = AppColors.Disabled
                    )
                )
            }
        )
    }
}

@Composable
internal fun ShareAppCard(
    onShareApp: () -> Unit
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
            .clip(RoundedCornerShape(AppRadii.lg))
            .background(AppColors.CardBackground)
            .padding(AppSpacing.s4)
    ) {
        Button(
            onClick = onShareApp,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadii.md),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            Text(
                text = strings.shareApp,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
        }
        trailing()
    }
}
