package io.github.lucaskfp.learnharness.ui.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

private const val PHONE = "spec:width=411dp,height=891dp,dpi=420"
private const val TABLET = "spec:width=1280dp,height=800dp,dpi=240"

/**
 * Every mockup is reviewed in light and dark, on phone and tablet, in English and Brazilian
 * Portuguese. Roborazzi renders each combination to PNG and checks it as a screenshot test.
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
