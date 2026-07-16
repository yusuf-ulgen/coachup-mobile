package com.app.coachup.app.theme

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import com.app.coachup.app.R

/**
 * Aktif temaya göre doğru CoachUp wordmark'ını döndürür.
 *
 * Asset adları rengi belirtir:
 *   - `black_logo` = SİYAH "coach" yazısı → AÇIK zeminde okunur
 *   - `coach_logo` = BEYAZ "coach" yazısı → KOYU zeminde okunur
 *
 * Yalnızca adaptif (tema rengiyle değişen) bir zemin üzerinde duran logolar için
 * kullanılır (ör. Home başlığı). Sabit koyu/marka zemini üzerindeki logolar
 * (Splash'taki turuncu zemin, Login/Register'daki koyu hero) zaten her iki temada
 * doğru göründüğü için `coach_logo`'yu doğrudan kullanmaya devam eder.
 *
 * iOS karşılığı: `colorScheme == .dark ? "coachLogo" : "blackLogo"`.
 */
@DrawableRes
fun coachUpLogoForBackground(isLightBackground: Boolean): Int =
    if (isLightBackground) {
        R.drawable.black_logo   // siyah logo — açık zemin
    } else {
        R.drawable.coach_logo   // beyaz logo — koyu zemin
    }

@DrawableRes
@Composable
fun coachUpLogoRes(): Int =
    coachUpLogoForBackground(MaterialTheme.colorScheme.background.luminance() >= 0.5f)
