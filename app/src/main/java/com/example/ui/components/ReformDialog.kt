package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal

@Composable
fun ReformDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "WHAT'S NEW · V${com.example.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = BrandGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lepší Penzijko Reform",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column {
                ReformBulletPoint(
                    title = "40% Doubled Youth Subsidy (<30 yrs)",
                    desc = "Automatically models doubled state subsidy up to 680 CZK/mo for eligible deposits under the youth age limit."
                )
                Spacer(modifier = Modifier.height(12.dp))
                ReformBulletPoint(
                    title = "Statutory 0.5% Fee Cap",
                    desc = "Models reduced fund fee drag and 0% performance fees per new Czech reform law."
                )
                Spacer(modifier = Modifier.height(12.dp))
                ReformBulletPoint(
                    title = "Age 36 Penalty-Free Partial Draw",
                    desc = "Highlights 1/3 penalty-free withdrawal capability after 10 years of saving."
                )
                Spacer(modifier = Modifier.height(12.dp))
                ReformBulletPoint(
                    title = "Full Mobile Native Experience",
                    desc = "Optimized touch targets, Room database offline persistence, and Material 3 design."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dismiss_reform_dialog")
            ) {
                Text("Got it — let's go!", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun ReformBulletPoint(
    title: String,
    desc: String
) {
    Column {
        Text(
            text = "• $title",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}
