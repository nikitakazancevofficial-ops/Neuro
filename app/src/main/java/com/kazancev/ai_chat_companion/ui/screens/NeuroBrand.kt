package com.kazancev.ai_chat_companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazancev.ai_chat_companion.R

@Composable
fun NeuroBrandMark(
    modifier: Modifier = Modifier,
    size: Int = 38
) {
    Image(
        painter = painterResource(R.drawable.neuro_logo),
        contentDescription = null,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(13.dp))
    )
}

@Composable
fun NeuroWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        NeuroBrandMark()
        Text(
            text = "Neuro",
            color = AppColors.text,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}
