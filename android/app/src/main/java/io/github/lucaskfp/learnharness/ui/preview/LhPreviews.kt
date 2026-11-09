package io.github.lucaskfp.learnharness.ui.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

private const val PHONE = "spec:width=411dp,height=891dp,dpi=420"
private const val TABLET = "spec:width=1280dp,height=800dp,dpi=240"
private const val TALL_PHONE = "spec:width=411dp,height=2000dp,dpi=420"

/**
 * Screens with a tablet layout are reviewed in light and dark, on phone and tablet, in English
 * and Brazilian Portuguese. Roborazzi renders each combination to PNG and checks it as a
 * screenshot test.
 */
@Preview(name = "phone-light-en", device = PHONE, uiMode = UI_MODE_NIGHT_NO, locale = "en")
@Preview(name = "phone-dark-en", device = PHONE, uiMode = UI_MODE_NIGHT_YES, locale = "en")
@Preview(name = "phone-light-ptBR", device = PHONE, uiMode = UI_MODE_NIGHT_NO, locale = "pt-rBR")
@Preview(name = "phone-dark-ptBR", device = PHONE, uiMode = UI_MODE_NIGHT_YES, locale = "pt-rBR")
@Preview(name = "tablet-light-en", device = TABLET, uiMode = UI_MODE_NIGHT_NO, locale = "en")
@Preview(name = "tablet-dark-en", device = TABLET, uiMode = UI_MODE_NIGHT_YES, locale = "en")
@Preview(name = "tablet-light-ptBR", device = TABLET, uiMode = UI_MODE_NIGHT_NO, locale = "pt-rBR")
@Preview(name = "tablet-dark-ptBR", device = TABLET, uiMode = UI_MODE_NIGHT_YES, locale = "pt-rBR")
annotation class LhPreviews

/** Screens reviewed on phone only: light and dark, English and Brazilian Portuguese. */
@Preview(name = "phone-light-en", device = PHONE, uiMode = UI_MODE_NIGHT_NO, locale = "en")
@Preview(name = "phone-dark-en", device = PHONE, uiMode = UI_MODE_NIGHT_YES, locale = "en")
@Preview(name = "phone-light-ptBR", device = PHONE, uiMode = UI_MODE_NIGHT_NO, locale = "pt-rBR")
@Preview(name = "phone-dark-ptBR", device = PHONE, uiMode = UI_MODE_NIGHT_YES, locale = "pt-rBR")
annotation class LhPhonePreviews

/**
 * Brazilian Portuguese, the longer locale, at a large font scale. The device is extra tall so a
 * scrolling screen fits in one PNG and every part of it can be reviewed.
 */
@Preview(name = "phone-light-ptBR-font150", device = TALL_PHONE, uiMode = UI_MODE_NIGHT_NO, locale = "pt-rBR", fontScale = 1.5f)
annotation class LhLargeFontPreview
