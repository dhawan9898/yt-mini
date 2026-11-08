package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.ui.components.OriginalColors

/** The sheet the "+" in the bottom bar opens, like YouTube's Create menu. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(onDismiss: () -> Unit, onUpload: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OriginalColors.Sheet,
        contentColor = OriginalColors.TextPrimary
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = "Create",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = OriginalColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Upload a video", onClick = onUpload)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.VideoCall, contentDescription = null, tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(20.dp))
                Text(text = "Upload a video", fontSize = 16.sp, color = OriginalColors.TextPrimary)
            }
        }
    }
}
