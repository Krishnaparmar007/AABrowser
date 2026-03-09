package com.kododake.aabrowser.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.model.UserAgentProfile

object SettingsViews {
    fun createSettingsContent(context: Context, includeDragHandle: Boolean = true, onClose: () -> Unit = {}): View {
        fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

        fun getColorFromAttr(attrResId: Int): Int {
            val tv = TypedValue()
            context.theme.resolveAttribute(attrResId, tv, true)
            return tv.data
        }

        val smallIconSize = context.resources.getDimensionPixelSize(R.dimen.icon_size_small)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), 0, dp(16), dp(24))
        }

        if (includeDragHandle) {
            val handleFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dp(12), 0, dp(16))
            }
            handleFrame.addView(View(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(48), dp(5)).apply { gravity = Gravity.CENTER }
                setBackgroundResource(R.drawable.drag_handle_background)
            })
            container.addView(handleFrame)
        }

        val brightOnPrimary = getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary)
        val onSurfaceColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val primaryColor = getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)

        // --- Header Section ---
        val headerCard = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorPrimaryContainer))
            strokeWidth = 0
        }
        val headerInner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(context).apply {
            id = R.id.settingsHeaderTitle
            text = context.getString(R.string.settings_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTextColor(brightOnPrimary)
            typeface = Typeface.DEFAULT_BOLD
        })
        titleCol.addView(TextView(context).apply {
            text = "Browser Preferences"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(brightOnPrimary)
            alpha = 0.8f
        })
        val backBtn = MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            id = R.id.buttonSettingsBack
            text = context.getString(R.string.menu_back)
            setTextColor(brightOnPrimary)
            setIconResource(R.drawable.arrow_back_24px)
            iconTint = ColorStateList.valueOf(brightOnPrimary)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconSize = smallIconSize
            iconPadding = dp(8)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
        headerInner.addView(titleCol)
        headerInner.addView(backBtn)
        headerCard.addView(headerInner)
        container.addView(headerCard)

        fun createStyledCard(): MaterialCardView = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow))
        }

        fun createSectionTitle(
            titleText: String,
            iconRes: Int,
            iconWidthDp: Int = 20,
            iconHeightDp: Int = 20,
            tintIcon: Boolean = true,
            bottomPaddingDp: Int = 0
        ): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                if (bottomPaddingDp > 0) {
                    setPadding(0, 0, 0, dp(bottomPaddingDp))
                }

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(iconWidthDp), dp(iconHeightDp)).apply {
                        marginEnd = dp(10)
                    }
                    setImageResource(iconRes)
                    if (tintIcon) {
                        imageTintList = ColorStateList.valueOf(primaryColor)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                })

                addView(TextView(context).apply {
                    text = titleText
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    typeface = Typeface.DEFAULT_BOLD
                })
            }
        }

        // --- User Agent ---
        val uaCard = createStyledCard()
        val uaInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        uaInner.addView(createSectionTitle(context.getString(R.string.settings_user_agent), R.drawable.devices_other_24px, bottomPaddingDp = 4))
        val uaGroup = RadioGroup(context).apply {
            id = R.id.userAgentGroup
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        uaGroup.addView(com.google.android.material.radiobutton.MaterialRadioButton(context).apply {
            id = R.id.userAgentAndroid
            text = context.getString(R.string.settings_user_agent_android)
        })
        uaGroup.addView(com.google.android.material.radiobutton.MaterialRadioButton(context).apply {
            id = R.id.userAgentSafari
            text = context.getString(R.string.settings_user_agent_safari)
        })
        uaInner.addView(uaGroup)
        uaCard.addView(uaInner)
        container.addView(uaCard)

        // --- Donate ---
        val donateCard = createStyledCard()
        val donateInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        donateInner.addView(createSectionTitle(context.getString(R.string.settings_donate), R.drawable.volunteer_activism_24px, bottomPaddingDp = 4))
        donateInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_donate_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, 0)
        })
        val donateRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        donateRow.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100))
            setImageResource(R.drawable.bitcoin_qr)
            setPadding(dp(1), dp(1), dp(1), dp(1))
            setBackgroundColor(Color.WHITE)
        })
        val donateCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            }
        }
        donateCol.addView(TextView(context).apply {
            id = R.id.bitcoinAddress
            text = context.getString(R.string.donate_bitcoin_address_value)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        })

        val copyBtn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            id = R.id.copyBitcoinButton
            text = context.getString(R.string.donate_copy)
            setIconResource(R.drawable.content_copy_24px)
            iconSize = smallIconSize
            iconPadding = dp(8)
            iconTintMode = android.graphics.PorterDuff.Mode.SRC_IN
            backgroundTintList = ColorStateList.valueOf(getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer))
            setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
            iconTint = ColorStateList.valueOf(getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
            isClickable = true
            isFocusable = true
        }
        donateCol.addView(copyBtn)
        donateRow.addView(donateCol)
        donateInner.addView(donateRow)
        donateCard.addView(donateInner)
        container.addView(donateCard)

        //Doner

        val donorsCard = createStyledCard()
        val donorsInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        donorsInner.addView(createSectionTitle(context.getString(R.string.settings_donors), R.drawable.favorite_24px, bottomPaddingDp = 4))
        donorsInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_donors_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, 0)
        })
        donorsCard.addView(donorsInner)
        container.addView(donorsCard)

        fun createListButton(idRes: Int, textStr: String, iconRes: Int): MaterialButton {
            return MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                id = idRes
                text = textStr
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(onSurfaceColor)
                setIconResource(iconRes)
                iconSize = smallIconSize
                iconPadding = dp(12)
                iconTint = ColorStateList.valueOf(primaryColor)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconTintMode = android.graphics.PorterDuff.Mode.SRC_IN
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                alpha = 1.0f
                isClickable = true
                isFocusable = true
            }
        }
        //SiteData
        fun showSuccessDialog(title: String, message: String) {
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        fun showConfirmationDialog(
            title: String,
            message: String,
            onConfirm: () -> Unit
        ) {
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_action_delete) { _, _ -> onConfirm() }
                .show()
        }

        val siteDataCard = createStyledCard()
        val siteDataInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        siteDataInner.addView(createSectionTitle(context.getString(R.string.settings_site_data_title), R.drawable.security_24px, bottomPaddingDp = 4))
        siteDataInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_site_data_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, dp(4), 0, dp(8))
        })
        siteDataInner.addView(
            createListButton(
                R.id.buttonClearSitePermissions,
                context.getString(R.string.settings_clear_site_permissions),
                R.drawable.lock_reset_24px
            )
        )
        siteDataInner.addView(
            createListButton(
                R.id.buttonClearCookies,
                context.getString(R.string.settings_clear_cookies),
                R.drawable.delete_forever_24px
            )
        )
        siteDataCard.addView(siteDataInner)
        container.addView(siteDataCard)


        // --- License ---
        val licenseCard = createStyledCard()
        val licenseInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        licenseInner.addView(createSectionTitle(context.getString(R.string.settings_license), R.drawable.gplv3, iconWidthDp = 48, iconHeightDp = 24, tintIcon = false, bottomPaddingDp = 8))
        licenseInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_license_description)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, 0, 0, dp(8))
        })
        licenseInner.addView(createListButton(R.id.ViewKododakeButton, context.getString(R.string.kododake_name), R.drawable.ic_github))
        licenseInner.addView(createListButton(R.id.viewLicenseButton, context.getString(R.string.settings_license), R.drawable.info_24px))
        licenseInner.addView(createListButton(R.id.viewOssLicensesButton, context.getString(R.string.open_source_view_licenses), R.drawable.search_24px))
        licenseCard.addView(licenseInner)
        container.addView(licenseCard)

        backBtn.setOnClickListener { onClose() }

        val currentProfile = BrowserPreferences.getUserAgentProfile(context)
        val androidId = R.id.userAgentAndroid
        val safariId = R.id.userAgentSafari
        if (currentProfile == UserAgentProfile.SAFARI) uaGroup.check(safariId) else uaGroup.check(androidId)
        uaGroup.setOnCheckedChangeListener { _, checkedId ->
            val profile = if (checkedId == safariId) UserAgentProfile.SAFARI else UserAgentProfile.ANDROID_CHROME
            BrowserPreferences.setUserAgentProfile(context, profile)
        }

        copyBtn.setOnClickListener {
            val addr = container.findViewById<TextView>(R.id.bitcoinAddress)?.text.toString()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin Address", addr))
            Toast.makeText(context, R.string.donate_copied, Toast.LENGTH_SHORT).show()
        }

        fun openUrl(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
            }
        }

        container.findViewById<View>(R.id.ViewKododakeButton)?.setOnClickListener {
            openUrl("https://github.com/kododake")
        }

        container.findViewById<View>(R.id.viewLicenseButton)?.setOnClickListener {
            openUrl("https://www.gnu.org/licenses/gpl-3.0.html")
        }

        container.findViewById<View>(R.id.viewOssLicensesButton)?.setOnClickListener {
            try {
                val activityClass = Class.forName("com.google.android.gms.oss.licenses.OssLicensesMenuActivity")
                val intent = Intent(context, activityClass)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
            }
        }

        container.findViewById<View>(R.id.buttonClearSitePermissions)?.setOnClickListener {
            showConfirmationDialog(
                title = context.getString(R.string.settings_clear_site_permissions_title),
                message = context.getString(R.string.settings_clear_site_permissions_message)
            ) {
                BrowserPreferences.clearSavedSitePermissions(context)
                showSuccessDialog(
                    title = context.getString(R.string.settings_clear_site_permissions_success_title),
                    message = context.getString(R.string.settings_clear_site_permissions_success_message)
                )
            }
        }

        container.findViewById<View>(R.id.buttonClearCookies)?.setOnClickListener {
            showConfirmationDialog(
                title = context.getString(R.string.settings_clear_cookies_title),
                message = context.getString(R.string.settings_clear_cookies_message)
            ) {
                WebStorage.getInstance().deleteAllData()
                WebViewDatabase.getInstance(context).apply {
                    clearHttpAuthUsernamePassword()
                }
                runCatching { context.deleteDatabase("webview.db") }
                runCatching { context.deleteDatabase("webviewCache.db") }
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    showSuccessDialog(
                        title = context.getString(R.string.settings_clear_cookies_success_title),
                        message = context.getString(R.string.settings_clear_cookies_success_message)
                    )
                }
            }
        }

        return container
    }

    fun createSettingsActivityView(context: Context): View = createSettingsContent(context, false)
}